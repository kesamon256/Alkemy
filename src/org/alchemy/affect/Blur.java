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

    /** Is the shape within reach of the pen - its area, or for a line the line itself */
    private static boolean touches(AlcShape shape, java.awt.geom.Point2D p, double reach) {
        double half = shape.getLineWidth() / 2.0 + reach;
        Rectangle2D b = shape.getPath().getBounds2D();
        if (p.getX() < b.getMinX() - half || p.getX() > b.getMaxX() + half || p.getY() < b.getMinY() - half || p.getY() > b.getMaxY() + half) {
            return false;
        }
        if (shape.getStyle() != STYLE_STROKE && shape.getPath().contains(p)) {
            return true;
        }
        // Closer to the outline (or the line) than the reach
        return new java.awt.BasicStroke((float) (half * 2)).createStrokedShape(shape.getPath()).contains(p);
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
            // A solid shape keeps its body and gets a soft edge - its outline drawn ever wider and
            // fainter: close to the edge all the layers overlap and it is dark, further out it fades
            group.copies.add(copy(o, o.getStyle(), o.getLineWidth(), Math.max(1, (int) Math.round(base * Math.max(0.45, 1 - 0.06 * group.level)))));
            int haloAlpha = Math.max(1, (int) Math.round(base * (1 - Math.pow(1 - 0.6, 1.0 / HALOS))));
            for (int k = 1; k <= HALOS; k++) {
                group.copies.add(copy(o, STYLE_STROKE, Math.max(0.5F, (float) (2 * radius * k / HALOS)), haloAlpha));
            }
        }
        canvas.shapes.addAll(index, group.copies);
        for (AlcShape c : group.copies) {
            blurs.put(c, group);
        }
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
