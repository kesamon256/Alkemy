/*
 * This file is part of the Alchemy project - http://al.chemy.org
 * 
 * Copyright (c) 2007-2010 Karl D.D. Willis
 * 
 * Alchemy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alchemy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Alchemy.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.alchemy.affect;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.awt.geom.Point2D.Float;
import java.util.ArrayList;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.alchemy.core.*;

/**
 * Displace
 * @author Karl D.D. Willis
 */
public class Displace extends AlcModule implements AlcConstants {

    private AlcToolBarSubSection subToolBarSection;
    private Point oldP;
    private int speed;
    private int displacement = 7;
    private boolean mouseDown = false;
    /** -1 pushes evenly away from the pen, otherwise one of the {@link AlcNoise} kinds */
    private int noiseKind = -1;
    /** Size of the noise features in pixels */
    private double noiseSize = 80;
    /** The noise slowly changes while drawing */
    private double noiseTime = 0;

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
    public void cleared() {
    }

    public void createSubToolBarSection() {
        subToolBarSection = new AlcToolBarSubSection(this);

        // Stable: each point moves once, the same every time - Wild: the original push that builds up as the pen moves
        final AlcSubComboBox modeBox = new AlcSubComboBox("Mode");
        modeBox.addItem("Stable");
        modeBox.addItem("Wild");
        modeBox.setSelectedIndex(stable ? 0 : 1);
        modeBox.setToolTipText("Stable - every point moves once, sideways, by the noise where it was drawn: Noise Size is the wave length, "
                + "Displacement how far it reaches, and the same stroke always looks the same. "
                + "Wild - the original Alchemy push that keeps building up while you draw");
        modeBox.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                stable = modeBox.getSelectedIndex() == 0;
                prefs().putBoolean("Stable", stable);
            }
        });
        subToolBarSection.add(modeBox);

        final AlcSubSlider speedSlider = new AlcSubSlider("Displacement", 1, 15, displacement);
        speedSlider.setToolTipText("Change the amount of displacement");
        speedSlider.addChangeListener(
                new ChangeListener() {

                    public void stateChanged(ChangeEvent e) {
                        if (!speedSlider.getValueIsAdjusting()) {
                            displacement = speedSlider.getValue();
                        }
                    }
                });
        subToolBarSection.add(speedSlider);

        // Noise - how the push varies over the drawing
        final AlcSubComboBox noiseBox = new AlcSubComboBox("Noise");
        noiseBox.addItem("Even");
        for (String name : AlcNoise.NAMES) {
            noiseBox.addItem(name);
        }
        noiseBox.addItem(CUSTOM_ITEM);
        noiseBox.addItem(LOAD_ITEM);
        noiseBox.setToolTipText("Even pushes the same everywhere - noise makes the push uneven: "
                + "Perlin soft waves, Turbulence rough billows, Random jitter, Ridged cracks, Cellular plates, Glitch blocky steps, "
                + "or your own image - its dark and light parts push less and more");
        noiseBox.addActionListener(
                new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        int index = noiseBox.getSelectedIndex();
                        int customIndex = AlcNoise.NAMES.length + 1;
                        if (index > customIndex || (index == customIndex && !AlcNoise.hasCustomImage())) {
                            // Pick an image - or go back to the noise used before
                            if (loadImage()) {
                                index = customIndex;
                            } else {
                                index = (noiseKind < 0) ? 0 : (noiseKind == AlcNoise.CUSTOM ? customIndex : noiseKind + 1);
                            }
                            if (index != noiseBox.getSelectedIndex()) {
                                noiseBox.setSelectedIndex(index);
                                return;
                            }
                        }
                        noiseKind = (index == customIndex) ? AlcNoise.CUSTOM : index - 1;
                        preview();
                    }
                });
        subToolBarSection.add(noiseBox);
        // A small preview of the noise - a larger tile opens when it changes or the mouse is over it
        swatch.setToolTipText(null);
        JPanel swatchHolder = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 4, 3));
        swatchHolder.setOpaque(false);
        swatchHolder.add(swatch);
        subToolBarSection.add(swatchHolder);

        // Noise size - small for fine grain, large for big sweeps - shown on the canvas while it changes
        final AlcSubSlider sizeSlider = new AlcSubSlider("Noise Size", 1, 100, 30);
        sizeSlider.setToolTipText("Change the size of the noise - small for fine grain, large for big sweeps");
        sizeSlider.addChangeListener(
                new ChangeListener() {

                    public void stateChanged(ChangeEvent e) {
                        noiseSize = 10 + Math.pow(sizeSlider.getValue() / 100.0, 1.5) * 390;
                        preview();
                    }
                });
        subToolBarSection.add(sizeSlider);
        noiseSize = 10 + Math.pow(sizeSlider.getValue() / 100.0, 1.5) * 390;
        swatch.setNoise(noiseKind, noiseSize, "Even");
        // The last image used comes back
        String last = prefs().get("NoiseImage", null);
        if (last != null) {
            try {
                java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.File(last));
                if (image != null) {
                    AlcNoise.setCustomImage(image);
                    imageName = new java.io.File(last).getName();
                }
            } catch (Exception ignored) {
            }
        }
    }
    private static final String CUSTOM_ITEM = "My image";
    private static final String LOAD_ITEM = "Load image...";
    private String imageName;

    private static java.util.prefs.Preferences prefs() {
        return java.util.prefs.Preferences.userNodeForPackage(Displace.class);
    }

    private final AlcNoiseSwatch swatch = new AlcNoiseSwatch();

    /** Show the noise in the small preview by the noise menu */
    private void preview() {
        String name = (noiseKind < 0) ? "Even" : (noiseKind == AlcNoise.CUSTOM)
                ? (imageName != null ? imageName : CUSTOM_ITEM) : AlcNoise.NAMES[noiseKind];
        swatch.setNoise(noiseKind, noiseSize, name);
    }

    /** Let the user pick an image to use as noise */
    private boolean loadImage() {
        javax.swing.JFileChooser chooser = new org.alchemy.core.AlcFileChooser(prefs().get("NoiseFolder", null));
        chooser.setDialogTitle("Load an image as noise");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images (PNG, JPG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (chooser.showOpenDialog(window) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return false;
        }
        java.io.File file = chooser.getSelectedFile();
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(file);
            if (image == null) {
                canvas.showMessage("That file is not an image Alkemy can read");
                return false;
            }
            AlcNoise.setCustomImage(image);
            imageName = file.getName();
            prefs().put("NoiseImage", file.getAbsolutePath());
            prefs().put("NoiseFolder", file.getParent());
            return true;
        } catch (java.io.IOException ex) {
            canvas.showMessage("The image could not be read");
            return false;
        }
    }

    /** Push a point away from the centre - evenly, or varied by the noise */
    private float[] push(Point2D.Float p, Point2D.Float centre) {
        if (noiseKind < 0) {
            return getAngle(p, centre, speed);
        }
        double f = 1.0 / noiseSize;
        // Two samples: how far the point goes and how much its direction turns
        double amount = AlcNoise.noise(noiseKind, p.x * f, p.y * f, noiseTime);
        double turn = AlcNoise.noise(noiseKind, p.x * f + 31.7, p.y * f - 17.3, noiseTime + 5.1);
        double distance = speed * amount * 2;
        if (noiseKind == AlcNoise.GLITCH) {
            // Machine-like jumps - straight along the axes, in steps
            double angle = Math.atan2(p.y - centre.y, p.x - centre.x) + (turn - 0.5) * Math.PI * 0.9;
            angle = Math.round(angle / (Math.PI / 2)) * (Math.PI / 2);
            distance = speed * Math.round(amount * 3) / 3.0 * 2;
            return new float[]{(float) (p.x + Math.cos(angle) * distance), (float) (p.y + Math.sin(angle) * distance)};
        }
        // The direction away from the pen, turned by the noise
        double dx = p.x - centre.x, dy = p.y - centre.y;
        double length = Math.sqrt(dx * dx + dy * dy);
        double ux = 1, uy = 0;
        if (length > 0) {
            ux = dx / length;
            uy = dy / length;
        }
        double t = (turn - 0.5) * Math.PI * 0.9;
        double cos = Math.cos(t), sin = Math.sin(t);
        double rx = ux * cos - uy * sin, ry = ux * sin + uy * cos;
        return new float[]{(float) (p.x + rx * distance), (float) (p.y + ry * distance)};
    }

    //////////////////////////////////////////////////////////////
    // STABLE MODE - every point of the stroke is moved once, sideways to the line, by the noise
    // at the place it was drawn. The same stroke always gives the same result, however fast or
    // long it is drawn: Noise Size is the length of the waves, Displacement how far they reach.
    //////////////////////////////////////////////////////////////
    /** Stable (predictable) or the wild original push that builds up as the pen moves */
    private boolean stable = prefs().getBoolean("Stable", true);
    /** The points of each line as they were drawn, before they were moved */
    private final java.util.IdentityHashMap<AlcShape, ArrayList<Point2D.Float>> drawnSpines = new java.util.IdentityHashMap<AlcShape, ArrayList<Point2D.Float>>();
    private final java.util.IdentityHashMap<AlcShape, ArrayList<float[]>> drawnPaths = new java.util.IdentityHashMap<AlcShape, ArrayList<float[]>>();
    /** Where each line was moved to last - to see if the drawing tool changed it since */
    private final java.util.IdentityHashMap<AlcShape, float[]> movedFirst = new java.util.IdentityHashMap<AlcShape, float[]>();
    /** The noise pattern stays the same for the whole session */
    private static final double STABLE_Z = 3.7;
    /** For shapes that keep moving on their own (Particles) the noise drifts in time from a new place
     *  every stroke - so each stroke turns out differently */
    private double liveSeed, liveZ;

    private double noiseZ() {
        return canvas.isCreateAnimating() ? STABLE_Z + liveSeed + liveZ : STABLE_Z;
    }

    /** How far a point is moved sideways - by the noise at its place, or a regular wave along the line for Even */
    private double offset(double x, double y, double along) {
        double reach = displacement * 3.0;
        if (noiseKind < 0) {
            return reach * Math.sin(along / noiseSize * Math.PI * 2);
        }
        double n = AlcNoise.noise(noiseKind, x / noiseSize, y / noiseSize, noiseZ());
        if (noiseKind == AlcNoise.GLITCH) {
            n = Math.round(n * 3) / 3.0;
        }
        return reach * (n - 0.5) * 2;
    }

    /** The drawn points moved sideways - the side is found from the neighbours of each point */
    private float[] moveAll(float[] xs, float[] ys) {
        int n = xs.length;
        float[] out = new float[n * 2];
        double along = 0;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                along += Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
            }
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            double tx = xs[b] - xs[a], ty = ys[b] - ys[a];
            double len = Math.hypot(tx, ty);
            double nx = 0, ny = 0;
            if (len > 0.0001) {
                nx = -ty / len;
                ny = tx / len;
            }
            double d = offset(xs[i], ys[i], along);
            out[i * 2] = (float) (xs[i] + nx * d);
            out[i * 2 + 1] = (float) (ys[i] + ny * d);
        }
        return out;
    }

    private void stableSpine(AlcShape shape, ArrayList<Point2D.Float> spine) {
        ArrayList<Point2D.Float> drawn = drawnSpines.get(shape);
        float[] first = movedFirst.get(shape);
        boolean changed = drawn == null || spine.size() < drawn.size()
                || (first != null && (Math.abs(spine.get(0).x - first[0]) > 0.01 || Math.abs(spine.get(0).y - first[1]) > 0.01));
        if (changed) {
            drawn = new ArrayList<Point2D.Float>();
            drawnSpines.put(shape, drawn);
            // A piece that carries on a line (the opacity pressure pieces) starts where the last one
            // was moved to - start it from where that one was drawn, so the pieces stay joined
            if (!spine.isEmpty()) {
                Point2D.Float start = spine.get(0);
                for (java.util.Map.Entry<AlcShape, ArrayList<Point2D.Float>> e : drawnSpines.entrySet()) {
                    ArrayList<Point2D.Float> otherSpine = e.getKey().getSpine();
                    if (e.getKey() != shape && otherSpine != null && !otherSpine.isEmpty() && !e.getValue().isEmpty()
                            && otherSpine.get(otherSpine.size() - 1).distance(start) < 0.01) {
                        Point2D.Float d = e.getValue().get(e.getValue().size() - 1);
                        drawn.add(new Point2D.Float(d.x, d.y));
                        break;
                    }
                }
            }
        }
        for (int j = drawn.size(); j < spine.size(); j++) {
            Point2D.Float p = spine.get(j);
            drawn.add(new Point2D.Float(p.x, p.y));
        }
        int n = drawn.size();
        float[] xs = new float[n], ys = new float[n];
        for (int j = 0; j < n; j++) {
            xs[j] = drawn.get(j).x;
            ys[j] = drawn.get(j).y;
        }
        float[] moved = moveAll(xs, ys);
        for (int j = 0; j < n; j++) {
            spine.set(j, new Point2D.Float(moved[j * 2], moved[j * 2 + 1]));
        }
        movedFirst.put(shape, new float[]{moved[0], moved[1]});
        shape.createSpine();
    }

    private void stablePath(AlcShape shape) {
        // The segments of the path as they are now: type and coordinates
        ArrayList<float[]> now = new ArrayList<float[]>();
        PathIterator it = shape.getPath().getPathIterator(null);
        float[] c = new float[6];
        while (!it.isDone()) {
            int type = it.currentSegment(c);
            int count = (type == PathIterator.SEG_QUADTO) ? 4 : (type == PathIterator.SEG_CUBICTO) ? 6 : (type == PathIterator.SEG_CLOSE) ? 0 : 2;
            float[] seg = new float[1 + count];
            seg[0] = type;
            System.arraycopy(c, 0, seg, 1, count);
            now.add(seg);
            it.next();
        }
        if (now.isEmpty()) {
            return;
        }
        ArrayList<float[]> drawn = drawnPaths.get(shape);
        float[] first = movedFirst.get(shape);
        boolean changed = drawn == null || now.size() < drawn.size() || first == null || now.get(0).length < 3
                || Math.abs(now.get(0)[1] - first[0]) > 0.01 || Math.abs(now.get(0)[2] - first[1]) > 0.01;
        if (changed) {
            // New, or made again from scratch by the drawing tool - all of it is as drawn
            drawn = new ArrayList<float[]>(now);
            drawnPaths.put(shape, drawn);
        } else {
            // The tool added to the end - those segments are new and not moved yet
            for (int i = drawn.size(); i < now.size(); i++) {
                drawn.add(now.get(i));
            }
        }
        // Every coordinate in order, moved sideways along the path
        int points = 0;
        for (float[] seg : drawn) {
            points += (seg.length - 1) / 2;
        }
        float[] xs = new float[points], ys = new float[points];
        int k = 0;
        for (float[] seg : drawn) {
            for (int i = 1; i < seg.length; i += 2) {
                xs[k] = seg[i];
                ys[k] = seg[i + 1];
                k++;
            }
        }
        float[] moved = moveAll(xs, ys);
        GeneralPath path = new GeneralPath(shape.getPath().getWindingRule());
        k = 0;
        for (float[] seg : drawn) {
            switch ((int) seg[0]) {
                case PathIterator.SEG_MOVETO:
                    path.moveTo(moved[k * 2], moved[k * 2 + 1]);
                    k += 1;
                    break;
                case PathIterator.SEG_LINETO:
                    path.lineTo(moved[k * 2], moved[k * 2 + 1]);
                    k += 1;
                    break;
                case PathIterator.SEG_QUADTO:
                    path.quadTo(moved[k * 2], moved[k * 2 + 1], moved[k * 2 + 2], moved[k * 2 + 3]);
                    k += 2;
                    break;
                case PathIterator.SEG_CUBICTO:
                    path.curveTo(moved[k * 2], moved[k * 2 + 1], moved[k * 2 + 2], moved[k * 2 + 3], moved[k * 2 + 4], moved[k * 2 + 5]);
                    k += 3;
                    break;
                default:
                    path.closePath();
            }
        }
        movedFirst.put(shape, new float[]{moved[0], moved[1]});
        shape.setPath(path);
    }

    private void forgetStroke() {
        drawnSpines.clear();
        drawnPaths.clear();
        movedFirst.clear();
    }

    @Override
    protected void affect() {
        if (stable) {
            // Nothing builds up, so the last points added as the pen lets go are moved too - but a line
            // the tool turns into a finished outline then is not moved a second time
            boolean animating = canvas.isCreateAnimating();
            if (mouseDown || animating) {
                boolean penDown = canvas.isPenDown();
                if (animating) {
                    liveZ += 0.02;
                }
                for (int i = 0; i < canvas.createShapes.size(); i++) {
                    AlcShape shape = canvas.createShapes.get(i);
                    if (!penDown && !animating && !movedFirst.containsKey(shape)) {
                        continue;
                    }
                    ArrayList<Point2D.Float> spine = shape.getSpine();
                    if (spine != null && !spine.isEmpty()) {
                        stableSpine(shape, spine);
                    } else {
                        stablePath(shape);
                    }
                }
            }
            return;
        }
        // Only while the pen is on the canvas - the tool redraws once more as it lets go,
        // before this module hears about it, and that must not push the line again
        if ((mouseDown && canvas.isPenDown() && !canvas.isSettling()) || canvas.isCreateAnimating()) {
            noiseTime += 0.015;
            for (int i = 0; i < canvas.createShapes.size(); i++) {
                AlcShape shape = canvas.createShapes.get(i);
                GeneralPath originalPath = shape.getPath();
                Point2D.Float lastPt = (Float) originalPath.getCurrentPoint();
                //Point2D.Float lastPt = new Point2D.Float(oldP.x, oldP.y);


                if (shape.hasSpine()) {
                    ArrayList<Point2D.Float> spine = shape.getSpine();
                    if (spine.size() > 1) {
                        for (int j = 0; j < spine.size(); j++) {
                            Point2D.Float p = spine.get(j);
                            float[] displacedMove = push(p, lastPt);
                            spine.set(j, new Point2D.Float(displacedMove[0], displacedMove[1]));

                        }
                        shape.createSpine();
                    }
                } else {

                    GeneralPath newPath = new GeneralPath();
                    PathIterator iterator = originalPath.getPathIterator(null);
                    float[] currentPoints = new float[6];
                    int currentPointType;

                    while (!iterator.isDone()) {
                        currentPointType = iterator.currentSegment(currentPoints);

                        switch (currentPointType) {
                            case PathIterator.SEG_MOVETO:
                                float[] displacedMove = push(new Point2D.Float(currentPoints[0], currentPoints[1]), lastPt);
                                //System.out.println("MOVE");
                                newPath.moveTo(displacedMove[0], displacedMove[1]);
                                break;
                            case PathIterator.SEG_LINETO:
                                //System.out.println("LINE");
                                float[] displacedLine = push(new Point2D.Float(currentPoints[0], currentPoints[1]), lastPt);
                                newPath.lineTo(displacedLine[0], displacedLine[1]);
                                break;
                            case PathIterator.SEG_QUADTO:
                                //System.out.println("QUAD");
                                float[] displacedQuad1 = push(new Point2D.Float(currentPoints[0], currentPoints[1]), lastPt);
                                float[] displacedQuad2 = push(new Point2D.Float(currentPoints[2], currentPoints[3]), lastPt);
                                newPath.quadTo(displacedQuad1[0], displacedQuad1[1], displacedQuad2[0], displacedQuad2[1]);
                                break;
                            case PathIterator.SEG_CUBICTO:
                                newPath.curveTo(currentPoints[0], currentPoints[1], currentPoints[2], currentPoints[3], currentPoints[4], currentPoints[5]);
                                break;
                            case PathIterator.SEG_CLOSE:
                                newPath.closePath();
                                break;
                        }
                        iterator.next();
                    }
                    shape.setPath(newPath);
                }
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        mouseDown = true;
        forgetStroke();
        liveSeed = Math.random() * 1000;
        liveZ = 0;
        Point p = e.getPoint();
        canvas.redraw();
        oldP = p;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        Point p = e.getPoint();
        speed = displacement - getCursorSpeed(p, oldP);
        oldP = p;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        mouseDown = false;
        // Shapes that still move keep their record, so they are not moved twice
        if (!canvas.isCreateAnimating()) {
            forgetStroke();
        }
        oldP = null;
    }

    private static int getCursorSpeed(Point p1, Point p2) {
        int diffX = Math.abs(p1.x - p2.x);
        int diffY = Math.abs(p1.y - p2.y);
        return diffX + diffY;
    }

    private float[] getAngle(Point2D.Float p1, Point2D.Float p2, double distance) {
        // Move along the direction from the last point to this one - the same as the
        // angle with cos and sin, without the slow trigonometry for every point
        double dx = p1.x - p2.x, dy = p1.y - p2.y;
        double length = Math.sqrt(dx * dx + dy * dy);
        double ux = 1, uy = 0;
        if (length > 0) {
            ux = dx / length;
            uy = dy / length;
        }
        float[] points = {(float) (p1.getX() + distance * ux), (float) (p1.getY() + distance * uy)};
        return points;
    }
}
