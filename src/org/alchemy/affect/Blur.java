/*
 *  This file is part of the Alkemy project, based on Alchemy - http://al.chemy.org
 *
 *  Alchemy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package org.alchemy.affect;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.alchemy.core.*;

/**
 * Blur - like Smooth and Repeat it works on the finished drawing under the pen while Q is held.
 * A shape is softened by a glow around it - its outline drawn ever wider and fainter - so lines and
 * solid shapes alike get soft edges; every pass spreads the glow further without piling up shapes.
 * With Mirror on the mirror image blurs the same way. A burst of blurring is one undo step.
 */
public class Blur extends AlcModule implements AlcConstants {

    /** Copies around the ring, plus one in the middle */
    /** Layers of glow around the shape */
    private static final int HALOS = 5;
    /** Copies in each ring around a blurred cloud of specks */
    private static final int SPECK_RING = 4;
    private static final int MAX_LEVEL = 30;
    private static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userNodeForPackage(Blur.class);
    private AlcToolBarSubSection subToolBarSection;
    /** How far the copies spread on each pass, in pixels */
    private int amount = PREFS.getInt("Blur Amount", 4);
    private final int spacing = 70;
    private long time;

    /** A blurred shape - how it looked before, how blurred it is and the copies that show it now */
    private static class Blurred {

        AlcShape original;
        int level;
        /** The outline of a solid shape flattened once for the blur layers - kept for the next passes */
        GeneralPath flat;
        final ArrayList<AlcShape> copies = new ArrayList<AlcShape>();
    }
    /** Every copy points to its blur, so passing over any copy blurs the whole group further */
    private final IdentityHashMap<AlcShape, Blurred> blurs = new IdentityHashMap<AlcShape, Blurred>();

    @Override
    protected void setup() {
        createSubToolBarSection();
        toolBar.addSubToolBarSection(subToolBarSection);
    }

    @Override
    public void reselect() {
        toolBar.addSubToolBarSection(subToolBarSection);
    }

    @Override
    protected void cleared() {
        blurs.clear();
    }

    private void createSubToolBarSection() {
        subToolBarSection = new AlcToolBarSubSection(this);
        final AlcSubSlider slider = new AlcSubSlider("Blur", 1, 20, amount);
        slider.setToolTipText("How much each pass over a shape blurs it - hold Q and move the pen over the drawing");
        slider.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                amount = slider.getValue();
                PREFS.putInt("Blur Amount", amount);
            }
        });
        subToolBarSection.add(slider);
    }

    /** Blurs at most this many shapes on one pass, so a dense area does not stall the pen */
    private static final int MAX_PER_PASS = 120;

    @Override
    public void mouseMoved(MouseEvent e) {
        // Only while Q is held - moving the pen over the drawing otherwise leaves it alone
        if (canvas.isPenDown() || !canvas.isMotionActive() || System.currentTimeMillis() - time < spacing) {
            return;
        }
        time = System.currentTimeMillis();
        // Like a soft brush: everything within its reach of the pen blurs - the reach follows the line weight.
        // With Mirror on, the brush also works at the mirrored places of the pen
        double reach = Math.max(12, canvas.getLineWidth() * 3);
        List<java.awt.geom.Point2D> centres = new ArrayList<java.awt.geom.Point2D>();
        for (AffineTransform mirror : canvas.symmetryTransforms()) {
            centres.add(mirror.transform(e.getPoint(), null));
        }
        // Pick the shapes first - one blur per group of copies - then blur them
        java.util.LinkedHashSet<AlcShape> targets = new java.util.LinkedHashSet<AlcShape>();
        for (AlcShape shape : canvas.shapes) {
            if (targets.size() >= MAX_PER_PASS) {
                break;
            }
            Blurred group = blurs.get(shape);
            AlcShape look = (group != null) ? group.original : shape;
            AlcShape key = (group != null) ? group.copies.get(0) : shape;
            if (targets.contains(key)) {
                continue;
            }
            for (java.awt.geom.Point2D p : centres) {
                if (touches(look, p, reach)) {
                    targets.add(key);
                    break;
                }
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        canvas.beginMotionEdit();
        for (AlcShape t : targets) {
            blur(t);
        }
        canvas.redraw(true);
    }

    /** Paths with more pieces than this (charcoal, pencil grain) skip the slow exact geometry */
    private static final int COMPLEX = 300;

    /** Is the shape within reach of the pen - its area, or for a line the line itself */
    private static boolean touches(AlcShape shape, java.awt.geom.Point2D p, double reach) {
        double half = shape.getLineWidth() / 2.0 + reach;
        Rectangle2D b = shape.getPath().getBounds2D();
        if (p.getX() < b.getMinX() - half || p.getX() > b.getMaxX() + half || p.getY() < b.getMinY() - half || p.getY() > b.getMaxY() + half) {
            return false;
        }
        // Any point of the outline within reach - quick even for thousands of specks
        double limit = half * half;
        int segments = 0;
        java.awt.geom.PathIterator it = shape.getPath().getPathIterator(null);
        double[] q = new double[6];
        while (!it.isDone()) {
            int type = it.currentSegment(q);
            int last = (type == java.awt.geom.PathIterator.SEG_CUBICTO) ? 4 : (type == java.awt.geom.PathIterator.SEG_QUADTO) ? 2 : 0;
            if (type != java.awt.geom.PathIterator.SEG_CLOSE) {
                double dx = q[last] - p.getX(), dy = q[last + 1] - p.getY();
                if (dx * dx + dy * dy <= limit) {
                    return true;
                }
            }
            segments++;
            it.next();
        }
        if (shape.getStyle() != STYLE_STROKE && shape.getPath().contains(p)) {
            return true;
        }
        // Long straight pieces can pass the pen between their points - check those exactly, if it is cheap
        return segments <= COMPLEX && new java.awt.BasicStroke((float) (half * 2)).createStrokedShape(shape.getPath()).contains(p);
    }

    /** How many pieces the outline has, and roughly how big its separate parts are (a speck, a grain) */
    private static double[] pieces(GeneralPath path) {
        java.awt.geom.PathIterator it = path.getPathIterator(null);
        double[] q = new double[6];
        int segments = 0, parts = 0;
        double sizeSum = 0, minX = 0, minY = 0, maxX = 0, maxY = 0;
        boolean open = false;
        while (!it.isDone()) {
            int type = it.currentSegment(q);
            if (type == java.awt.geom.PathIterator.SEG_MOVETO) {
                if (open && parts <= 200) {
                    sizeSum += Math.max(maxX - minX, maxY - minY);
                }
                parts++;
                minX = maxX = q[0];
                minY = maxY = q[1];
                open = true;
            } else if (type != java.awt.geom.PathIterator.SEG_CLOSE) {
                int last = (type == java.awt.geom.PathIterator.SEG_CUBICTO) ? 4 : (type == java.awt.geom.PathIterator.SEG_QUADTO) ? 2 : 0;
                minX = Math.min(minX, q[last]);
                maxX = Math.max(maxX, q[last]);
                minY = Math.min(minY, q[last + 1]);
                maxY = Math.max(maxY, q[last + 1]);
            }
            segments++;
            it.next();
        }
        if (open && parts <= 200) {
            sizeSum += Math.max(maxX - minX, maxY - minY);
        }
        return new double[]{segments, parts, parts == 0 ? 1 : sizeSum / Math.min(parts, 200)};
    }
    /** Blur a shape one step more - its copies are made again further apart, in the same place in the drawing */
    private void blur(AlcShape shape) {
        Blurred group = blurs.get(shape);
        int index;
        if (group == null) {
            group = new Blurred();
            group.original = shape;
            index = canvas.shapes.indexOf(shape);
            canvas.shapes.remove(index);
        } else {
            if (group.level >= MAX_LEVEL) {
                return;
            }
            index = canvas.shapes.indexOf(group.copies.get(0));
            for (AlcShape c : group.copies) {
                canvas.shapes.remove(c);
                blurs.remove(c);
            }
            group.copies.clear();
        }
        if (index < 0) {
            return;
        }
        group.level++;
        AlcShape o = group.original;
        double radius = group.level * amount * 0.5;
        int base = o.getAlpha();
        if (o.getStyle() == STYLE_STROKE) {
            // A line keeps its ink - spread over a wider band it gets fainter, as a real blur does.
            // Many fine lines on top of each other (the Flow brush) go soft without turning into a blot
            float core = Math.max(0.5F, o.getLineWidth());
            group.copies.add(copy(o, STYLE_STROKE, core, Math.max(1, (int) Math.round(base * Math.max(0.12, core / (core + radius))))));
            for (int k = 1; k <= HALOS; k++) {
                float width = core + (float) (2 * radius * k / HALOS);
                group.copies.add(copy(o, STYLE_STROKE, width, Math.max(1, (int) Math.round(base * 0.3 * core / width))));
            }
        } else {
            double[] info = pieces(o.getPath());
            if (info[0] > COMPLEX) {
                // Thousands of specks (charcoal, pencil grain): the specks scatter into a soft cloud -
                // copies spread in two rings around them, fainter the further they spread. Filling
                // small specks is quick to draw, unlike wide outlines around thousands of them
                float speck = (float) Math.max(0.5, info[2]);
                int coreAlpha = Math.max(1, (int) Math.round(base * Math.max(0.2, speck / (speck + radius * 0.5))));
                group.copies.add(copy(o, o.getStyle(), o.getLineWidth(), coreAlpha));
                for (int ring = 1; ring <= 2; ring++) {
                    double r = radius * ring / 2.0;
                    int ringAlpha = Math.max(1, (int) Math.round(base * 0.28 * speck / (speck + r)));
                    for (int k = 0; k < SPECK_RING; k++) {
                        double a = (k + ring * 0.5) * Math.PI * 2 / SPECK_RING;
                        AlcShape moved = o.customClone((GeneralPath) o.getPath().createTransformedShape(
                                AffineTransform.getTranslateInstance(Math.cos(a) * r, Math.sin(a) * r)));
                        moved.setAlpha(ringAlpha);
                        moved.setSpine(null);
                        group.copies.add(moved);
                    }
                }            } else {
                group.copies.addAll(blurSolid(o, radius, group));
            }
        }
        if (group.copies.isEmpty()) {
            group.copies.add(copy(o, o.getStyle(), o.getLineWidth(), base));
        }
        canvas.shapes.addAll(index, group.copies);
        for (AlcShape c : group.copies) {
            blurs.put(c, group);
        }
    }

    /** Layers inside and outside the edge of a solid shape */
    private static final int SOLID_STEPS = 3;

    /**
     * A solid shape blurred like a picture: its area shrunk and grown in steps, each filled so
     * that together they fade smoothly - full colour deep inside, about half at the edge, nothing
     * a blur radius outside. Worked out from the closed outline, so every edge gets soft.
     */
    private static List<AlcShape> blurSolid(AlcShape o, double radius, Blurred group) {
        if (group.flat == null) {
            group.flat = simplified(o.getPath(), 1.5);
        }
        GeneralPath outline = simplified(group.flat, Math.max(1.5, radius * 0.05));
        // Many thin rings around the outline (also around the straight closing edge and any holes),
        // one inside the other, so the steps between them are too small to see. Rounded corners,
        // so a jagged outline does not grow spikes
        int rings = (int) Math.max(SOLID_STEPS, Math.min(16, Math.round(radius / 2.5)));
        double opacity = o.getAlpha() / 255.0;
        // Ring k reaches k/rings of the radius out. The zone between rings k-1 and k is covered by
        // rings k..rings, so solve their opacities from the outermost in for a smooth fade
        double[] alphas = new double[rings + 2];
        double outside = 0;
        for (int k = rings; k >= 1; k--) {
            double target = opacity * 0.5 * (1 + Math.cos(Math.PI * (k - 0.5) / rings));
            alphas[k] = (outside >= 1) ? 0 : 1 - (1 - target) / (1 - outside);
            outside = target;
        }
        List<AlcShape> copies = new ArrayList<AlcShape>();
        for (int k = rings; k >= 1; k--) {
            if (alphas[k] <= 0) {
                continue;
            }
            float width = (float) (2 * radius * k / rings);
            java.awt.Shape ring = new java.awt.BasicStroke(width, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND).createStrokedShape(outline);
            GeneralPath path = new GeneralPath(GeneralPath.WIND_NON_ZERO);
            path.append(ring, false);
            AlcShape c = o.customClone(path);
            c.setStyle(STYLE_FILL);
            c.setAlpha(Math.max(1, Math.min(255, (int) Math.round(alphas[k] * 255))));
            c.setSpine(null);
            copies.add(c);
        }
        // The shape itself on top, full colour right up to its edge where the fade begins
        copies.add(copy(o, STYLE_FILL, o.getLineWidth(), o.getAlpha()));
        return copies;
    }
    /**
     * The outline as straight pieces at least step px long - curves flattened to well under a pixel.
     * Looks the same once blurred, but the area work on it is many times quicker than on curves.
     */
    private static GeneralPath simplified(GeneralPath path, double step) {
        GeneralPath out = new GeneralPath(path.getWindingRule());
        java.awt.geom.PathIterator it = path.getPathIterator(null, 0.5);
        double[] q = new double[6];
        double lastX = 0, lastY = 0;
        boolean started = false;
        while (!it.isDone()) {
            int type = it.currentSegment(q);
            if (type == java.awt.geom.PathIterator.SEG_MOVETO) {
                // Every part closed, so its ring also runs along the straight closing edge
                if (started) {
                    out.closePath();
                }
                out.moveTo(q[0], q[1]);
                lastX = q[0];
                lastY = q[1];
                started = true;
            } else if (type == java.awt.geom.PathIterator.SEG_LINETO && started) {
                double dx = q[0] - lastX, dy = q[1] - lastY;
                if (dx * dx + dy * dy >= step * step) {
                    out.lineTo(q[0], q[1]);
                    lastX = q[0];
                    lastY = q[1];
                }
            } else if (type == java.awt.geom.PathIterator.SEG_CLOSE) {
                out.closePath();
                started = false;
            }
            it.next();
        }
        if (started) {
            out.closePath();
        }
        return out;
    }
    /** The same outline, drawn filled or as a line of the given width */
    private static AlcShape copy(AlcShape o, int style, float lineWidth, int alpha) {
        AlcShape c = o.customClone((GeneralPath) o.getPath().clone());
        c.setStyle(style);
        c.setLineWidth(lineWidth);
        c.setAlpha(alpha);
        c.setSpine(null);
        return c;
    }}
