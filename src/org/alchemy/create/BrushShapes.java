/*
 *  This file is part of the Alchemy project - http://al.chemy.org
 *
 *  Copyright (c) 2007-2010 Karl D.D. Willis
 *
 *  Alchemy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Alchemy is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Alchemy.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.alchemy.create;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Random;
import org.alchemy.core.*;

/**
 * Sketching brushes - pencil, ink pen, marker, charcoal, and the speed
 * sensitive liner, brush pen, sketchy lines and hatching for quick dynamic strokes.
 * <p>
 * Every brush is made of vector shapes, so strokes stay sharp at any zoom
 * and in exports, and work with undo, layers, the lasso and the affects.
 * The size comes from the line weight, the colour and transparency from the
 * canvas, and the pen pressure shapes the stroke when a tablet is used.
 */
public class BrushShapes extends AlcModule implements AlcConstants {

    private static final int PENCIL = 0, INK = 1, MARKER = 2, CHARCOAL = 3,
            LINER = 4, BRUSHPEN = 5, SKETCHY = 6, HATCHING = 7, FLOW = 8;
    private static final String[] NAMES = {"Pencil", "Ink", "Marker", "Charcoal", "Liner", "Brush pen", "Sketchy", "Hatching", "Flow"};
    private static final String[] ICONS = {"pencil.png", "ink.png", "marker.png", "charcoal.png", "liner.png", "brushpen.png", "sketchy.png", "hatching.png", "flow.png"};
    private static final String[] TIPS = {
        "Pencil - fine grainy strands, darker with more pressure",
        "Ink pen - smooth line that tapers at the ends and follows the pressure",
        "Marker - flat see-through tip, overlapping strokes get darker",
        "Charcoal - rough grainy texture",
        "Liner - quick confident lines, thinner the faster you draw, with sharp flicks",
        "Brush pen - bold dynamic line, swells when slow or pressed, snaps thin on fast flicks",
        "Sketchy - a few loose overlapping lines like a quick gesture sketch",
        "Hatching - parallel diagonal strokes for shading",
        "Flow - a swarm of fine trails that drift on a hidden current and circle the pen, like ink in water"};
    private AlcToolBarSubSection subToolBarSection;
    private final AlcSubToggleButton[] buttons = new AlcSubToggleButton[NAMES.length];
    private int brush = PENCIL;
    //
    // The stroke being drawn
    private Random random;
    private Point2D.Float last;
    private float travelled;
    private AlcShape ink;
    private final ArrayList<AlcShape> strands = new ArrayList<AlcShape>();
    private final ArrayList<Float> strandOffsets = new ArrayList<Float>();
    private AlcShape pencilGrain;
    private float segmentStart;
    private float segmentPressure;
    private AlcShape marker;
    private final AlcShape[] grain = new AlcShape[3];
    private final ArrayList<AlcShape> ribbons = new ArrayList<AlcShape>();
    private float[][] phases;
    private AlcShape lines;
    private float hatchCarry;
    private long lastTime;
    private float velocity;
    private float dynWidth;

    public BrushShapes() {
    }

    @Override
    protected void setup() {
        createSubToolBarSection();
        toolBar.addSubToolBarSection(subToolBarSection);
    }

    @Override
    protected void reselect() {
        AlcPressureSliders.refresh(subToolBarSection, canvas);
        toolBar.addSubToolBarSection(subToolBarSection);
    }

    private void createSubToolBarSection() {
        subToolBarSection = new AlcToolBarSubSection(this);
        for (int i = 0; i < NAMES.length; i++) {
            final int index = i;
            buttons[i] = new AlcSubToggleButton(NAMES[i], AlcUtil.getUrlPath(ICONS[i], getClassLoader()));
            buttons[i].setToolTipText(TIPS[i]);
            buttons[i].addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    selectBrush(index);
                }
            });
            subToolBarSection.add(buttons[i]);
        }
        AlcPressureSliders.add(subToolBarSection, canvas);
        selectBrush(PENCIL);
    }

    private void selectBrush(int index) {
        brush = index;
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setSelected(i == index);
        }
    }

    //////////////////////////////////////////////////////////////
    // PEN
    //////////////////////////////////////////////////////////////
    /** Pen pressure for the shape of the brush, from 0 to 1 - a medium press for the mouse,
     *  and a steady firm press when the pressure sets the opacity instead */
    private float pressure() {
        if (canvas.getPenType() == PEN_CURSOR) {
            return 0.6F;
        }
        // Size not following the pressure - a steady firm press
        if (canvas.isPressureEnabled() && !canvas.isPressureSize()) {
            return 0.75F;
        }
        return canvas.sizePressure(rawPressure());
    }

    /** The pen pressure from 0 to 1 - the canvas squares it, this undoes that so a medium press is in the middle */
    private float rawPressure() {
        return (float) Math.sqrt(Math.max(0, Math.min(1, canvas.getPenPressure())));
    }

    /** The pressure sets how opaque the brush is - faint with a light touch, full when pressed hard */
    private boolean opacityMode() {
        return canvas.isPressureEnabled() && canvas.isPressureOpacity() && canvas.getPenType() != PEN_CURSOR;
    }

    /** The opacity for the pen pressure, in tenths so a stroke is made of a few pieces */
    private float opacityLevel() {
        if (!opacityMode()) {
            return 1F;
        }
        return Math.max(0.03F, Math.round(canvas.opacityPressure(rawPressure()) * 10) / 10F);
    }

    private float size() {
        return Math.max(1F, canvas.getLineWidth());
    }

    /** Below a line weight of 1 the brushes keep their look and get finer - every width and spread
     *  is scaled down by this. At 1 and above it is 1 and changes nothing */
    private float thin() {
        return Math.max(0.1F, Math.min(1F, canvas.getLineWidth()));
    }

    /** Transparency of a new shape - the canvas setting, the brush's own share and the pressure opacity */
    private int alpha(double factor) {
        return Math.max(1, Math.min(255, (int) Math.round(canvas.getAlpha() * factor * opacity)));
    }

    //////////////////////////////////////////////////////////////
    // OPACITY PIECES - in the opacity mode a stroke is made of pieces, each as opaque as the pen was pressed
    //////////////////////////////////////////////////////////////
    /** Opacity of the piece being drawn */
    private float opacity = 1F;
    /** How far along the stroke the piece started */
    private float pieceStart;

    /** Carry on the stroke in new shapes with the current opacity, joined to where the old ones ended */
    private void newPiece() {
        switch (brush) {
            case PENCIL:
                startPencilSegment(new ArrayList<AlcShape>(strands), last);
                pencilGrain = new AlcShape(new GeneralPath(), canvas.getColor(), alpha(0.4), STYLE_FILL, size());
                canvas.createShapes.add(pencilGrain);
                break;
            case SKETCHY:
                startSketchySegment(new ArrayList<AlcShape>(strands), last);
                break;
            case INK:
                if (ink != null) {
                    AlcShape next = new BandShape(canvas.getColor(), alpha(1), size(), true);
                    continueSpine(ink, next);
                    ink = next;
                    canvas.createShapes.add(ink);
                }
                break;
            case MARKER:
                if (marker != null) {
                    MarkerShape next = new MarkerShape(canvas.getColor(), alpha(0.55), size());
                    next.continued = true;
                    continueSpine(marker, next);
                    marker = next;
                    canvas.createShapes.add(marker);
                }
                break;
            case CHARCOAL:
                double[] shades = {0.6, 0.32, 0.14};
                for (int i = 0; i < grain.length; i++) {
                    grain[i] = new AlcShape(new GeneralPath(), canvas.getColor(), alpha(shades[i]), STYLE_FILL, size());
                    canvas.createShapes.add(grain[i]);
                }
                break;
            case LINER:
            case BRUSHPEN:
                if (!ribbons.isEmpty()) {
                    AlcShape next = ribbon(alpha(1), true);
                    continueSpine(ribbons.get(0), next);
                    next.createSpine();
                    ribbons.set(0, next);
                }
                break;
            case HATCHING:
                lines = new AlcShape(new GeneralPath(), canvas.getColor(), alpha(0.85), STYLE_STROKE, Math.max(1F, size() / 4) * thin());
                canvas.createShapes.add(lines);
                break;
        }
    }

    /** Start a spine where another one ended, with the same width, so the pieces meet */
    private static void continueSpine(AlcShape from, AlcShape to) {
        ArrayList<Point2D.Float> spine = new ArrayList<Point2D.Float>();
        ArrayList<Float> widths = new ArrayList<Float>();
        if (from.getSpine() != null && !from.getSpine().isEmpty()) {
            Point2D.Float end = from.getSpine().get(from.getSpine().size() - 1);
            spine.add(new Point2D.Float(end.x, end.y));
            widths.add(from.getSpineWidth().get(from.getSpineWidth().size() - 1));
        }
        to.setSpine(spine);
        to.setSpineWidth(widths);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        random = new Random();
        last = canvas.getPenLocation();
        travelled = 0;
        opacity = opacityLevel();
        pieceStart = 0;
        switch (brush) {
            case PENCIL:
                startPencil(last);
                break;
            case INK:
                if (opacityMode()) {
                    // Band pieces that join cleanly as the opacity changes
                    ink = ribbon(alpha(1));
                    ribbonTo(ink, last, inkWidth());
                } else {
                    ink = new AlcShape();
                    ink.setStyle(STYLE_FILL);
                    ink.setAlpha(alpha(1));
                    ink.spineTo(last, inkWidth());
                    canvas.createShapes.add(ink);
                }
                break;
            case MARKER:
                marker = new MarkerShape(canvas.getColor(), alpha(0.55), size());
                canvas.createShapes.add(marker);
                markerSegment(last, last);
                break;
            case CHARCOAL:
                double[] shades = {0.6, 0.32, 0.14};
                for (int i = 0; i < grain.length; i++) {
                    grain[i] = new AlcShape(new GeneralPath(), canvas.getColor(), alpha(shades[i]), STYLE_FILL, size());
                    canvas.createShapes.add(grain[i]);
                }
                charcoalSegment(last, last);
                break;
            case LINER:
            case BRUSHPEN:
                ribbons.clear();
                ribbons.add(ribbon(alpha(1)));
                dynWidth = 0;
                dynamicTo(last);
                break;
            case SKETCHY:
                startSketchy(last);
                break;
            case HATCHING:
                lines = new AlcShape(new GeneralPath(), canvas.getColor(), alpha(0.85), STYLE_STROKE, Math.max(1F, size() / 4) * thin());
                canvas.createShapes.add(lines);
                hatchCarry = hatchGap();
                hatchTo(last, last);
                break;
            case FLOW:
                startFlow(last);
                break;
        }
        lastTime = e.getWhen();
        velocity = 0;
        canvas.redraw();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (last == null) {
            return;
        }
        Point2D.Float p = canvas.getPenLocation();
        float distance = (float) p.distance(last);
        if (distance < 0.5F) {
            return;
        }
        travelled += distance;
        // Smoothed drawing speed in pixels per millisecond
        long dt = Math.max(1, Math.min(100, e.getWhen() - lastTime));
        lastTime = e.getWhen();
        velocity += (distance / dt - velocity) * 0.35F;
        // Opacity mode: a new piece when the pressure has moved to another tenth
        if (opacityMode()) {
            float level = opacityLevel();
            if (level != opacity && travelled - pieceStart >= 3) {
                opacity = level;
                pieceStart = travelled;
                newPiece();
            }
        }
        switch (brush) {
            case PENCIL:
                pencilTo(p);
                break;
            case INK:
                if (ink instanceof BandShape) {
                    ribbonTo(ink, p, inkWidth());
                } else if (ink != null) {
                    ink.spineTo(p, inkWidth());
                }
                break;
            case MARKER:
                markerSegment(last, p);
                break;
            case CHARCOAL:
                charcoalSegment(last, p);
                break;
            case LINER:
            case BRUSHPEN:
                dynamicTo(p);
                break;
            case SKETCHY:
                sketchyTo(last, p);
                break;
            case HATCHING:
                hatchTo(last, p);
                break;
            case FLOW:
                flowTo(last, p);
                break;
        }
        last = p;
        canvas.redraw();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (last == null) {
            return;
        }
        if (brush == INK && ink != null) {
            taperInkEnd();
        }
        if (brush == FLOW) {
            finishFlow();
        }
        // A fast release flicks out into a long thin tail
        int tail = 5 + (int) Math.min(14, velocity * 4);
        for (AlcShape r : ribbons) {
            taperEnd(r, tail);
        }
        ribbons.clear();
        lines = null;
        last = null;
        ink = null;
        marker = null;
        strands.clear();
        strandOffsets.clear();
        canvas.redraw();
        // Markers are committed as their finished outline, so nothing rebuilds them later
        for (int i = 0; i < canvas.createShapes.size(); i++) {
            if (canvas.createShapes.get(i) instanceof MarkerShape) {
                canvas.createShapes.set(i, ((MarkerShape) canvas.createShapes.get(i)).flatten());
            } else if (canvas.createShapes.get(i) instanceof BandShape) {
                canvas.createShapes.set(i, ((BandShape) canvas.createShapes.get(i)).flatten());
            }
        }
        canvas.commitShapes();
    }

    //////////////////////////////////////////////////////////////
    // FLOW - a swarm of particles leaving fine trails. Most drift on a smooth noise current,
    // some are tied to the pen by a spring and swing around it in loops - precise rules
    // and chance together, building a dark core with wisps around it like ink in water.
    //////////////////////////////////////////////////////////////
    private static class FlowParticle {

        float x, y, vx, vy;
        int age, life;
        boolean tied;
        AlcShape trail;
    }
    private final ArrayList<FlowParticle> flow = new ArrayList<FlowParticle>();
    /** Moves through the noise slowly, so the current turns while drawing */
    private float flowTime;
    private static final int FLOW_MAX = 260;

    private void startFlow(Point2D.Float p) {
        flow.clear();
        flowTime = random.nextFloat() * 50;
        spawnFlow(p, 0, 0, 6);
    }

    /** New particles at the pen - more with a firmer press */
    private void spawnFlow(Point2D.Float p, float penVx, float penVy, int count) {
        float scale = Math.max(1F, size() / 6F);
        for (int i = 0; i < count && flow.size() < FLOW_MAX; i++) {
            FlowParticle f = new FlowParticle();
            double a = random.nextDouble() * Math.PI * 2;
            double speed = (0.4 + random.nextDouble() * 2.2) * scale;
            f.x = p.x + (float) (random.nextGaussian() * scale);
            f.y = p.y + (float) (random.nextGaussian() * scale);
            // A little of the pen's movement - capped, so a fast flick does not fling them away
            float push = Math.min(1F, 8F * scale / Math.max(1F, (float) Math.hypot(penVx, penVy)));
            f.vx = penVx * 0.25F * push + (float) (Math.cos(a) * speed);
            f.vy = penVy * 0.25F * push + (float) (Math.sin(a) * speed);
            f.tied = random.nextDouble() < 0.3;
            boolean core = random.nextDouble() < 0.18;
            f.life = core ? 6 + random.nextInt(10) : (f.tied ? 50 : 30) + random.nextInt(60);
            float width = (float) ((core ? 1.2 : 0.3) + random.nextDouble() * (core ? 1.4 : 0.8)) * scale * thin();
            double shade = core ? 0.35 + random.nextDouble() * 0.4 : 0.07 + random.nextDouble() * 0.3;
            GeneralPath path = new GeneralPath();
            path.moveTo(f.x, f.y);
            f.trail = new AlcShape(path, canvas.getColor(), alpha(shade), STYLE_STROKE, width);
            canvas.createShapes.add(f.trail);
            flow.add(f);
        }
    }

    /** One step of the swarm - the pen pulls the tied particles, the current carries all of them */
    private void stepFlow(Point2D.Float pen) {
        float scale = Math.max(1F, size() / 6F);
        double noiseScale = 1.0 / (70 + size() * 5);
        for (int i = flow.size() - 1; i >= 0; i--) {
            FlowParticle f = flow.get(i);
            double turn = AlcNoise.noise(AlcNoise.PERLIN, f.x * noiseScale, f.y * noiseScale, flowTime) * Math.PI * 4;
            float ax = (float) Math.cos(turn) * 0.42F * scale, ay = (float) Math.sin(turn) * 0.42F * scale;
            // Once the pen has gone they slow down quickly - no trail shoots off in a straight line
            float damping = (pen == null) ? 0.86F : 0.93F;
            if (f.tied && pen != null) {
                ax += (pen.x - f.x) * 0.014F;
                ay += (pen.y - f.y) * 0.014F;
                damping = 0.965F;
            }
            f.vx = f.vx * damping + ax;
            f.vy = f.vy * damping + ay;
            float speed = (float) Math.hypot(f.vx, f.vy), limit = 5F * scale;
            if (speed > limit) {
                f.vx *= limit / speed;
                f.vy *= limit / speed;
            }
            f.x += f.vx;
            f.y += f.vy;
            f.trail.getPath().lineTo(f.x, f.y);
            if (++f.age >= f.life) {
                flow.remove(i);
            }
        }
        flowTime += 0.006F;
    }

    private void flowTo(Point2D.Float from, Point2D.Float to) {
        float dx = to.x - from.x, dy = to.y - from.y;
        int count = Math.round((1 + 5 * pressure()) * Math.min(3F, (float) Math.hypot(dx, dy) / 6F + 0.5F));
        spawnFlow(to, dx, dy, count);
        stepFlow(to);
    }

    /** Let go - the particles drift on until their trails are finished */
    private void finishFlow() {
        for (int step = 0; step < 40 && !flow.isEmpty(); step++) {
            stepFlow(null);
        }
        flow.clear();
    }

    //////////////////////////////////////////////////////////////
    // PENCIL - fine strands side by side, each a little wobbly
    //////////////////////////////////////////////////////////////
    private void startPencil(Point2D.Float p) {
        strands.clear();
        strandOffsets.clear();
        float size = size();
        int count = Math.min(10, 4 + (int) (size / 2.5F));
        float spread = Math.max(1.2F, size * 0.5F) * thin();
        for (int i = 0; i < count; i++) {
            strandOffsets.add((float) ((random.nextDouble() * 2 - 1) * spread));
        }
        pencilGrain = new AlcShape(new GeneralPath(), canvas.getColor(), alpha(0.4), STYLE_FILL, size);
        canvas.createShapes.add(pencilGrain);
        startPencilSegment(null, p);
    }

    /** Start new strand shapes from where the old ones ended - each piece takes the pressure of its moment */
    private void startPencilSegment(ArrayList<AlcShape> previous, Point2D.Float p) {
        float size = size();
        float pr = pressure();
        segmentPressure = pr;
        segmentStart = travelled;
        ArrayList<AlcShape> next = new ArrayList<AlcShape>(strandOffsets.size());
        for (int i = 0; i < strandOffsets.size(); i++) {
            Point2D.Float start = (previous == null) ? new Point2D.Float(p.x, p.y) : previous.get(i).getLastPoint();
            if (start == null) {
                start = new Point2D.Float(p.x, p.y);
            }
            // Pressing harder makes the strands thicker and darker
            float width = (float) ((0.45 + random.nextDouble() * 0.45) * Math.max(1, size / 6) * (0.2 + 1.3 * pr)) * thin();
            AlcShape strand = new AlcShape(start, canvas.getColor(),
                    alpha((0.3 + random.nextDouble() * 0.35) * (0.2 + 0.8 * pr)), STYLE_STROKE, width);
            next.add(strand);
            canvas.createShapes.add(strand);
        }
        strands.clear();
        strands.addAll(next);
    }

    private void pencilTo(Point2D.Float p) {
        float dx = p.x - last.x, dy = p.y - last.y;
        float length = (float) Math.max(0.001, Math.hypot(dx, dy));
        // Perpendicular to the direction of the stroke
        float nx = -dy / length, ny = dx / length;
        float pr = pressure();
        // A new piece only when the pressure has really changed - keeps the number of shapes low
        if (Math.abs(pr - segmentPressure) > 0.1F && travelled - segmentStart > 8) {
            startPencilSegment(new ArrayList<AlcShape>(strands), p);
        }
        for (int i = 0; i < strands.size(); i++) {
            // A light touch draws a narrow line, a firm one spreads the strands wider
            float offset = strandOffsets.get(i) * (0.45F + 0.55F * pr) + (float) (random.nextGaussian() * 0.3 * thin());
            // Straight pieces join exactly where the next pressure piece starts
            strands.get(i).lineTo(new Point2D.Float(p.x + nx * offset, p.y + ny * offset));
        }
        // Graphite grain - a few tiny specks along the stroke
        float size = size();
        int specks = (int) Math.ceil(length * (0.4 + size * 0.08) * (0.3 + 0.7 * pr));
        for (int i = 0; i < specks; i++) {
            float t = random.nextFloat();
            float offset = (float) ((random.nextDouble() * 2 - 1) * Math.max(1.5, size * 0.6) * thin());
            float x = last.x + dx * t + nx * offset, y = last.y + dy * t + ny * offset;
            pencilGrain.append(speck(x, y, (float) (0.35 + random.nextDouble() * 0.45) * thin()), false);
        }
    }

    //////////////////////////////////////////////////////////////
    // INK - variable width line with tapered ends
    //////////////////////////////////////////////////////////////
    /** Half the line width at this point of the stroke */
    private float inkWidth() {
        float size = size();
        float base = size * 0.5F * (0.08F + 1.03F * pressure());
        // Start thin and swell over the first part of the stroke
        float taperIn = Math.min(1F, 0.2F + travelled / (size * 3 + 18));
        return Math.max(0.3F, base * taperIn) * thin();
    }

    private void taperInkEnd() {
        taperEnd(ink, 8);
    }

    /** Narrow the last few points of a spine shape to a point */
    private static void taperEnd(AlcShape shape, int points) {
        ArrayList<Float> widths = shape.getSpineWidth();
        if (widths == null || widths.size() < 3) {
            return;
        }
        int n = widths.size();
        int k = Math.min(n - 1, points);
        for (int j = 0; j < k; j++) {
            int index = n - 1 - j;
            widths.set(index, Math.max(Math.min(0.3F, widths.get(index)), widths.get(index) * (j + 1) / (k + 1)));
        }
        shape.createSpine();
    }

    //////////////////////////////////////////////////////////////
    // RIBBONS - spine shapes used by the airbrush and the watercolor
    //////////////////////////////////////////////////////////////
    /** A filled spine shape - affects like Displace and Mirror treat it like the ink line */
    private AlcShape ribbon(int alpha) {
        return ribbon(alpha, false);
    }

    /** In the opacity pressure mode the line is made of band pieces that join cleanly */
    private AlcShape ribbon(int alpha, boolean continued) {
        AlcShape s;
        if (opacityMode()) {
            s = new BandShape(canvas.getColor(), alpha, size(), continued);
        } else {
            s = new AlcShape(new GeneralPath(), canvas.getColor(), alpha, STYLE_FILL, size());
            s.setSpine(new ArrayList<Point2D.Float>());
            s.setSpineWidth(new ArrayList<Float>());
        }
        canvas.createShapes.add(s);
        return s;
    }

    private static void ribbonTo(AlcShape s, Point2D.Float p, float halfWidth) {
        ArrayList<Point2D.Float> spine = s.getSpine();
        if (spine.isEmpty() || p.distance(spine.get(spine.size() - 1)) >= 2) {
            spine.add(new Point2D.Float(p.x, p.y));
            s.getSpineWidth().add(halfWidth);
            s.createSpine();
        }
    }

    //////////////////////////////////////////////////////////////
    // LINER and BRUSH PEN - the width follows the drawing speed:
    // slow and pressed is wide, a fast flick snaps thin
    //////////////////////////////////////////////////////////////
    private void dynamicTo(Point2D.Float p) {
        float size = size();
        float pr = pressure();
        float target, smoothing, taper;
        if (brush == LINER) {
            target = size * 0.5F * (0.12F + 1.03F * pr) * clamp(1.25F - 0.35F * velocity, 0.3F, 1.25F);
            smoothing = 0.3F;
            taper = Math.min(1F, 0.15F + travelled / (size + 10));
        } else {
            target = size * 1.1F * (0.05F + 1.02F * pr) * clamp(1.4F - 0.55F * velocity, 0.15F, 1.4F);
            smoothing = 0.22F;
            taper = Math.min(1F, 0.1F + travelled / (size * 2 + 16));
        }
        // Ease towards the new width so the line breathes instead of jumping
        dynWidth = (dynWidth == 0) ? target : dynWidth + (target - dynWidth) * smoothing;
        ribbonTo(ribbons.get(0), p, Math.max(0.3F, dynWidth * taper) * thin());
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    //////////////////////////////////////////////////////////////
    // SKETCHY - a few light loose lines drifting around the stroke
    //////////////////////////////////////////////////////////////
    private void startSketchy(Point2D.Float p) {
        strands.clear();
        strandOffsets.clear();
        float size = size();
        int count = 3;
        phases = new float[count][2];
        for (int i = 0; i < count; i++) {
            phases[i][0] = (float) (random.nextDouble() * Math.PI * 2);
            phases[i][1] = (float) (random.nextDouble() * Math.PI * 2);
            strandOffsets.add((float) (random.nextGaussian() * (size * 0.25 + 0.8) * thin()));
        }
        startSketchySegment(null, p);
    }

    /** New pieces of the lines from where the old ones ended - each piece takes the pressure of its moment */
    private void startSketchySegment(ArrayList<AlcShape> previous, Point2D.Float p) {
        float size = size();
        float pr = pressure();
        segmentPressure = pr;
        segmentStart = travelled;
        ArrayList<AlcShape> next = new ArrayList<AlcShape>(strandOffsets.size());
        for (int i = 0; i < strandOffsets.size(); i++) {
            Point2D.Float start = (previous == null) ? null : previous.get(i).getLastPoint();
            if (start == null) {
                start = new Point2D.Float(p.x, p.y);
            }
            float width = (float) (Math.max(0.8, size / 3) * (0.7 + random.nextDouble() * 0.5) * (0.2 + 1.3 * pr)) * thin();
            AlcShape s = new AlcShape(start, canvas.getColor(),
                    alpha((0.3 + random.nextDouble() * 0.3) * (0.25 + 0.75 * pr)), STYLE_STROKE, width);
            next.add(s);
            canvas.createShapes.add(s);
        }
        strands.clear();
        strands.addAll(next);
    }

    private void sketchyTo(Point2D.Float a, Point2D.Float b) {
        if (Math.abs(pressure() - segmentPressure) > 0.1F && travelled - segmentStart > 8) {
            startSketchySegment(new ArrayList<AlcShape>(strands), a);
        }
        float dx = b.x - a.x, dy = b.y - a.y;
        float length = (float) Math.max(0.001, Math.hypot(dx, dy));
        float nx = -dy / length, ny = dx / length;
        // The lines spread apart a little when drawing fast, like a loose hand
        float spread = (size() * 0.5F + 1.8F) * (0.6F + Math.min(1.2F, velocity * 0.4F)) * thin();
        for (int i = 0; i < strands.size(); i++) {
            float drift = strandOffsets.get(i) + spread * (float) (Math.sin(travelled / (28 + i * 9) + phases[i][0]) * 0.7
                    + Math.sin(travelled / (11 + i * 3) + phases[i][1]) * 0.3);
            // Fade the drift in so the lines start together
            drift *= Math.min(1F, travelled / 25F);
            strands.get(i).lineTo(new Point2D.Float(b.x + nx * drift, b.y + ny * drift));
        }
    }

    //////////////////////////////////////////////////////////////
    // HATCHING - short parallel lines at an even spacing along the stroke
    //////////////////////////////////////////////////////////////
    private float hatchGap() {
        return Math.max(3F, size() * 0.8F + 2.5F) * Math.max(0.35F, thin());
    }

    private void hatchTo(Point2D.Float a, Point2D.Float b) {
        // Pressing harder packs the lines closer - darker shading
        float gap = hatchGap() * (1.5F - 0.8F * pressure());
        float d = (float) a.distance(b);
        float pos = gap - hatchCarry;
        while (pos <= d) {
            float t = d > 0 ? pos / d : 0;
            float cx = a.x + (b.x - a.x) * t, cy = a.y + (b.y - a.y) * t;
            double angle = Math.toRadians(-50 + random.nextGaussian() * 3);
            float half = (size() * 2F + 5F) * (0.12F + 1.0F * pressure()) * (0.85F + random.nextFloat() * 0.3F) * Math.max(0.35F, thin());
            float ux = (float) Math.cos(angle) * half, uy = (float) Math.sin(angle) * half;
            float shift = (float) (random.nextGaussian() * half * 0.12);
            GeneralPath line = new GeneralPath();
            line.moveTo(cx - ux + shift, cy - uy);
            line.lineTo(cx + ux + shift, cy + uy);
            lines.append(line, false);
            pos += gap;
        }
        hatchCarry = d - (pos - gap);
    }


    //////////////////////////////////////////////////////////////
    // MARKER - flat chisel tip at 45 degrees
    //////////////////////////////////////////////////////////////
    private void markerSegment(Point2D.Float a, Point2D.Float b) {
        float width = Math.max(6F, size() * 3F) * (0.15F + 0.98F * pressure()) * thin();
        // The marker keeps its centre line as a spine - affects like Displace move the spine
        // and the outline is rebuilt from it, so the stroke stays one clean ribbon
        ArrayList<Point2D.Float> spine = marker.getSpine();
        if (spine == null) {
            marker.setSpine(spine = new ArrayList<Point2D.Float>());
            marker.setSpineWidth(new ArrayList<Float>());
        }
        if (spine.isEmpty() || b.distance(spine.get(spine.size() - 1)) >= 1.5) {
            spine.add(new Point2D.Float(b.x, b.y));
            marker.getSpineWidth().add(width);
        }
        marker.createSpine();
    }

    /** Swept outline of the chisel tip along the spine - one convex piece per step, all turning the same way */
    private static GeneralPath markerOutline(ArrayList<Point2D.Float> spine, ArrayList<Float> widths, boolean continued) {
        GeneralPath path = new GeneralPath(GeneralPath.WIND_NON_ZERO);
        // A piece that carries on from another leaves out the tip where the other one ended - no double layer
        for (int i = continued ? 1 : 0; i < spine.size(); i++) {
            Point2D.Float a = spine.get(Math.max(0, i - 1)), b = spine.get(i);
            float width = widths.get(i);
            // Long side of the tip and its short thickness
            float lx = (float) Math.cos(Math.toRadians(-45)) * width / 2, ly = (float) Math.sin(Math.toRadians(-45)) * width / 2;
            float tx = -ly * 0.22F, ty = lx * 0.22F;
            // The tip is a thin rectangle - the piece is everything it sweeps from a to b
            float[][] corners = new float[8][];
            int k = 0;
            for (Point2D.Float c : new Point2D.Float[]{a, b}) {
                corners[k++] = new float[]{c.x + lx + tx, c.y + ly + ty};
                corners[k++] = new float[]{c.x - lx + tx, c.y - ly + ty};
                corners[k++] = new float[]{c.x - lx - tx, c.y - ly - ty};
                corners[k++] = new float[]{c.x + lx - tx, c.y + ly - ty};
            }
            if (continued && i == 1) {
                // The first sweep without the tip it starts from - that part belongs to the piece before
                float aw = widths.get(0);
                float alx = (float) Math.cos(Math.toRadians(-45)) * aw / 2, aly = (float) Math.sin(Math.toRadians(-45)) * aw / 2;
                // A little thinner than the tip, so the pieces overlap by a hair - meeting edge to edge
                // would leave a light seam where the smoothing of both edges lets the paper through
                float thin = (float) Math.max(0, 0.22 - 0.4 / Math.max(0.5, Math.hypot(alx, aly)));
                float atx = -aly * thin, aty = alx * thin;
                GeneralPath tip = new GeneralPath();
                tip.moveTo(a.x + alx + atx, a.y + aly + aty);
                tip.lineTo(a.x - alx + atx, a.y - aly + aty);
                tip.lineTo(a.x - alx - atx, a.y - aly - aty);
                tip.lineTo(a.x + alx - atx, a.y + aly - aty);
                tip.closePath();
                GeneralPath whole = hull(corners);
                java.awt.geom.Area sweep = new java.awt.geom.Area(whole);
                sweep.subtract(new java.awt.geom.Area(tip));
                // Turn it the same way as the other sweeps - turning the other way, the overlap
                // with the next sweep would cancel out and leave a hole
                appendTurning(path, sweep, Math.signum(turning(whole)));
            } else {
                path.append(hull(corners), false);
            }
        }
        return path;
    }

    //////////////////////////////////////////////////////////////
    // OPACITY RIBBON - ink, liner and brush pen pieces in the opacity pressure mode
    //////////////////////////////////////////////////////////////
    /** A line of the given half widths along the spine: a band for every step with a round joint
     *  between them - no pointed start, so pieces that carry on from each other meet cleanly */
    private static GeneralPath bandOutline(ArrayList<Point2D.Float> spine, ArrayList<Float> widths, boolean continued) {
        GeneralPath path = new GeneralPath(GeneralPath.WIND_NON_ZERO);
        int n = spine.size();
        for (int i = 1; i < n; i++) {
            Point2D.Float a = spine.get(i - 1), b = spine.get(i);
            double dx = b.x - a.x, dy = b.y - a.y;
            double length = Math.sqrt(dx * dx + dy * dy);
            if (length < 0.001) {
                continue;
            }
            double nx = -dy / length, ny = dx / length;
            float wa = widths.get(i - 1), wb = widths.get(i);
            // A piece carrying on from another starts half a pixel back - overlapping by a hair,
            // as edge to edge the smoothing of both edges would leave a light seam
            double ax = a.x, ay = a.y;
            if (continued && i == 1) {
                ax -= dx / length * 0.5;
                ay -= dy / length * 0.5;
            }
            path.moveTo((float) (ax + nx * wa), (float) (ay + ny * wa));
            path.lineTo((float) (b.x + nx * wb), (float) (b.y + ny * wb));
            path.lineTo((float) (b.x - nx * wb), (float) (b.y - ny * wb));
            path.lineTo((float) (ax - nx * wa), (float) (ay - ny * wa));
            path.closePath();
        }
        // Joints inside the piece: a wedge on each side fills the gap where the bands turn. The wedges
        // stay beside the joint - they never reach back over a piece before - and turn the same way as
        // the bands, so the fill never cancels out
        for (int i = 1; i < n - 1; i++) {
            Point2D.Float a = spine.get(i - 1), b = spine.get(i), c = spine.get(i + 1);
            double d1x = b.x - a.x, d1y = b.y - a.y, d2x = c.x - b.x, d2y = c.y - b.y;
            double l1 = Math.sqrt(d1x * d1x + d1y * d1y), l2 = Math.sqrt(d2x * d2x + d2y * d2y);
            if (l1 < 0.001 || l2 < 0.001) {
                continue;
            }
            double n1x = -d1y / l1, n1y = d1x / l1, n2x = -d2y / l2, n2y = d2x / l2;
            float w = widths.get(i);
            for (int side = -1; side <= 1; side += 2) {
                double px = b.x + side * n1x * w, py = b.y + side * n1y * w;
                double qx = b.x + side * n2x * w, qy = b.y + side * n2y * w;
                // Same turning as the bands: their signed area is negative
                double area = (px - b.x) * (qy - b.y) - (qx - b.x) * (py - b.y);
                path.moveTo(b.x, b.y);
                if (area < 0) {
                    path.lineTo((float) px, (float) py);
                    path.lineTo((float) qx, (float) qy);
                } else {
                    path.lineTo((float) qx, (float) qy);
                    path.lineTo((float) px, (float) py);
                }
                path.closePath();
            }
        }
        return path;
    }

    /** A line piece drawn as bands - a spine shape, so Displace and Mirror work on it as on any line */
    private static class BandShape extends AlcShape {

        final boolean continued;

        BandShape(Color color, int alpha, float lineWidth, boolean continued) {
            super(new GeneralPath(), color, alpha, STYLE_FILL, lineWidth);
            this.continued = continued;
            setSpine(new ArrayList<Point2D.Float>());
            setSpineWidth(new ArrayList<Float>());
        }

        @Override
        public void createSpine() {
            if (getSpine() != null && !getSpine().isEmpty()) {
                setPath(bandOutline(getSpine(), getSpineWidth(), continued));
            }
        }

        @Override
        public AlcShape customClone(ArrayList<Point2D.Float> spine, ArrayList<Float> spineWidth) {
            BandShape copy = new BandShape(getColor(), getAlpha(), getLineWidth(), continued);
            copy.setGradientPaint(getGradientPaint());
            copy.setSpine(spine);
            copy.setSpineWidth(spineWidth);
            copy.createSpine();
            return copy;
        }

        AlcShape flatten() {
            AlcShape flat = new AlcShape(getPath(), getColor(), getAlpha(), STYLE_FILL, getLineWidth());
            flat.setGradientPaint(getGradientPaint());
            return flat;
        }
    }

    /** A marker stroke - a spine shape whose outline is the swept chisel tip */
    private static class MarkerShape extends AlcShape {

        /** Carries on from another piece of the same stroke - see the opacity pressure mode */
        boolean continued = false;

        MarkerShape(Color color, int alpha, float lineWidth) {
            super(new GeneralPath(), color, alpha, STYLE_FILL, lineWidth);
        }

        @Override
        public void createSpine() {
            if (getSpine() != null && !getSpine().isEmpty()) {
                setPath(markerOutline(getSpine(), getSpineWidth(), continued));
            }
        }

        @Override
        public AlcShape customClone(ArrayList<Point2D.Float> spine, ArrayList<Float> spineWidth) {
            // Mirror copies stay markers too
            MarkerShape copy = new MarkerShape(getColor(), getAlpha(), getLineWidth());
            copy.continued = continued;
            copy.setGradientPaint(getGradientPaint());
            copy.setSpine(spine);
            copy.setSpineWidth(spineWidth);
            copy.createSpine();
            return copy;
        }

        /** A plain copy with the finished outline, for committing to the canvas */
        AlcShape flatten() {
            AlcShape flat = new AlcShape(getPath(), getColor(), getAlpha(), STYLE_FILL, getLineWidth());
            flat.setGradientPaint(getGradientPaint());
            return flat;
        }
    }

    /** Signed area of the first outline of a shape - its sign tells which way the outline turns */
    private static double turning(java.awt.Shape s) {
        double area = 0;
        float[] c = new float[6];
        float sx = 0, sy = 0, px = 0, py = 0;
        for (java.awt.geom.PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) {
            int type = it.currentSegment(c);
            if (type == java.awt.geom.PathIterator.SEG_MOVETO) {
                if (area != 0) {
                    break;
                }
                sx = px = c[0];
                sy = py = c[1];
            } else if (type == java.awt.geom.PathIterator.SEG_LINETO) {
                area += px * c[1] - c[0] * py;
                px = c[0];
                py = c[1];
            } else if (type == java.awt.geom.PathIterator.SEG_CLOSE) {
                area += px * sy - sx * py;
                px = sx;
                py = sy;
            }
        }
        return area / 2;
    }

    /** Append the straight-sided outlines of a shape, each turned to the given way */
    private static void appendTurning(GeneralPath target, java.awt.Shape s, double sign) {
        java.util.ArrayList<float[]> outline = new java.util.ArrayList<float[]>();
        float[] c = new float[6];
        for (java.awt.geom.PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) {
            int type = it.currentSegment(c);
            if (type == java.awt.geom.PathIterator.SEG_MOVETO) {
                addOutline(target, outline, sign);
                outline.clear();
                outline.add(new float[]{c[0], c[1]});
            } else if (type == java.awt.geom.PathIterator.SEG_LINETO) {
                outline.add(new float[]{c[0], c[1]});
            } else if (type == java.awt.geom.PathIterator.SEG_CLOSE) {
                addOutline(target, outline, sign);
                outline.clear();
            }
        }
        addOutline(target, outline, sign);
    }

    private static void addOutline(GeneralPath target, java.util.ArrayList<float[]> pts, double sign) {
        if (pts.size() < 3) {
            return;
        }
        double area = 0;
        for (int i = 0; i < pts.size(); i++) {
            float[] p = pts.get(i), q = pts.get((i + 1) % pts.size());
            area += p[0] * q[1] - q[0] * p[1];
        }
        boolean reverse = Math.signum(area) != sign && sign != 0;
        for (int i = 0; i < pts.size(); i++) {
            float[] p = pts.get(reverse ? pts.size() - 1 - i : i);
            if (i == 0) {
                target.moveTo(p[0], p[1]);
            } else {
                target.lineTo(p[0], p[1]);
            }
        }
        target.closePath();
    }

    /** Convex hull of the points as a closed path (monotone chain) */
    private static GeneralPath hull(float[][] pts) {
        float[][] p = pts.clone();
        java.util.Arrays.sort(p, new java.util.Comparator<float[]>() {

            public int compare(float[] u, float[] v) {
                return u[0] != v[0] ? Float.compare(u[0], v[0]) : Float.compare(u[1], v[1]);
            }
        });
        float[][] h = new float[p.length * 2][];
        int k = 0;
        for (int i = 0; i < p.length; i++) {
            while (k >= 2 && cross(h[k - 2], h[k - 1], p[i]) <= 0) {
                k--;
            }
            h[k++] = p[i];
        }
        for (int i = p.length - 2, low = k + 1; i >= 0; i--) {
            while (k >= low && cross(h[k - 2], h[k - 1], p[i]) <= 0) {
                k--;
            }
            h[k++] = p[i];
        }
        GeneralPath path = new GeneralPath();
        for (int i = 0; i < k - 1; i++) {
            if (i == 0) {
                path.moveTo(h[i][0], h[i][1]);
            } else {
                path.lineTo(h[i][0], h[i][1]);
            }
        }
        path.closePath();
        return path;
    }

    private static float cross(float[] o, float[] a, float[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    //////////////////////////////////////////////////////////////
    // CHARCOAL - small rough specks, densest in the middle
    //////////////////////////////////////////////////////////////
    private void charcoalSegment(Point2D.Float a, Point2D.Float b) {
        float size = size();
        float pr = pressure();
        // A firm press lays down a wider, denser band of charcoal
        float radius = (size * 1.25F + 3F) * (0.25F + 1.02F * pr) * thin();
        float distance = (float) a.distance(b);
        int steps = Math.max(1, (int) (distance / 2));
        int perStep = Math.max(2, (int) ((4 + size * 1.3F) * (0.2F + 0.9F * pr)));
        for (int s = 0; s < steps; s++) {
            float t = (s + 1) / (float) steps;
            float cx = a.x + (b.x - a.x) * t, cy = a.y + (b.y - a.y) * t;
            for (int i = 0; i < perStep; i++) {
                double r = Math.abs(random.nextGaussian()) * radius * 0.45;
                double angle = random.nextDouble() * Math.PI * 2;
                float x = (float) (cx + Math.cos(angle) * r), y = (float) (cy + Math.sin(angle) * r);
                float speck = (float) ((0.5 + random.nextDouble() * 1.4) * Math.max(1, size / 8)) * thin();
                // Darker specks near the middle of the stroke
                int shade = (r < radius * 0.25) ? 0 : (r < radius * 0.55 ? 1 : 2);
                grain[shade].append(speck(x, y, speck), false);
            }
        }
    }

    /** A small irregular four sided speck */
    private GeneralPath speck(float x, float y, float s) {
        GeneralPath p = new GeneralPath();
        double turn = random.nextDouble() * Math.PI;
        for (int i = 0; i < 4; i++) {
            double angle = turn + i * Math.PI / 2 + random.nextGaussian() * 0.25;
            double r = s * (0.6 + random.nextDouble() * 0.6);
            float px = (float) (x + Math.cos(angle) * r), py = (float) (y + Math.sin(angle) * r);
            if (i == 0) {
                p.moveTo(px, py);
            } else {
                p.lineTo(px, py);
            }
        }
        p.closePath();
        return p;
    }
}
