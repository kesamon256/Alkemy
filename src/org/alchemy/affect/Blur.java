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
 * A shape is softened into a soft halo of see-through copies around where it was; every pass
 * over it spreads them a little further, so it blurs more and more without piling up shapes.
 * With Mirror on the mirror image blurs the same way. A burst of blurring is one undo step.
 */
public class Blur extends AlcModule implements AlcConstants {

    /** Copies around the ring, plus one in the middle */
    private static final int RING = 8, RINGS = 3;
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

    @Override
    public void mouseMoved(MouseEvent e) {
        // Only while Q is held - moving the pen over the drawing otherwise leaves it alone
        if (canvas.isPenDown() || !canvas.isMotionActive() || System.currentTimeMillis() - time < spacing) {
            return;
        }
        time = System.currentTimeMillis();
        Point pt = e.getPoint();
        int target = -1;
        for (int i = canvas.shapes.size() - 1; i >= 0; i--) {
            if (canvas.shapes.get(i).getPath().contains(pt) || nearStroke(canvas.shapes.get(i), pt)) {
                target = i;
                break;
            }
        }
        if (target < 0) {
            return;
        }
        AlcShape shape = canvas.shapes.get(target);
        Blurred group = blurs.get(shape);
        AlcShape original = (group != null) ? group.original : shape;
        // With Mirror on, the mirror image blurs too - found by its outline, as in Smooth
        List<AlcShape> targets = new ArrayList<AlcShape>();
        targets.add(shape);
        List<AffineTransform> mirrors = canvas.symmetryTransforms();
        for (int m = 1; m < mirrors.size(); m++) {
            Rectangle2D expected = mirrors.get(m).createTransformedShape(original.getPath()).getBounds2D();
            AlcShape twin = findTwin(expected, targets);
            if (twin != null) {
                targets.add(twin);
            }
        }
        canvas.beginMotionEdit();
        for (AlcShape t : targets) {
            blur(t);
        }
        canvas.redraw(true);
    }

    /** A thin line counts as under the pen when the pen is close to it */
    private static boolean nearStroke(AlcShape shape, Point pt) {
        if (shape.getStyle() != STYLE_STROKE) {
            return false;
        }
        float reach = Math.max(8F, shape.getLineWidth() + 6F);
        if (!shape.getPath().getBounds2D().intersects(pt.x - reach, pt.y - reach, reach * 2, reach * 2)) {
            return false;
        }
        return new java.awt.BasicStroke(reach).createStrokedShape(shape.getPath()).contains(pt);
    }
    /** The shape (or blurred group) whose unblurred outline matches the mirrored one */
    private AlcShape findTwin(Rectangle2D expected, List<AlcShape> skip) {
        AlcShape best = null;
        double bestError = 2.0;
        for (AlcShape other : canvas.shapes) {
            if (skip.contains(other)) {
                continue;
            }
            Blurred g = blurs.get(other);
            AlcShape look = (g != null) ? g.original : other;
            if (g != null && skip.contains(g.copies.get(0))) {
                continue;
            }
            Rectangle2D b = look.getPath().getBounds2D();
            double error = Math.abs(b.getMinX() - expected.getMinX()) + Math.abs(b.getMinY() - expected.getMinY())
                    + Math.abs(b.getMaxX() - expected.getMaxX()) + Math.abs(b.getMaxY() - expected.getMaxY());
            if (error < bestError) {
                best = other;
                bestError = error;
            }
        }
        return best;
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
        // Three rings of copies inside each other - where they all overlap (the middle) they add up
        // to the full colour, towards the edge fewer overlap and it fades out, like a real blur
        int count = 1 + RINGS * RING;
        double each = 1 - Math.pow(1 - 0.94, 1.0 / count);
        int copyAlpha = Math.max(1, (int) Math.round(o.getAlpha() * each));
        group.copies.add(copy(o, 0, 0, Math.max(1, (int) Math.round(o.getAlpha() * Math.min(1, each * 2.5)))));
        for (int ring = 1; ring <= RINGS; ring++) {
            double r = radius * ring / RINGS;
            for (int k = 0; k < RING; k++) {
                double a = (k + ring * 0.37 + (group.level % 2) * 0.5) * Math.PI * 2 / RING;
                group.copies.add(copy(o, Math.cos(a) * r, Math.sin(a) * r, copyAlpha));
            }
        }
        canvas.shapes.addAll(index, group.copies);
        for (AlcShape c : group.copies) {
            blurs.put(c, group);
        }
    }

    private static AlcShape copy(AlcShape o, double dx, double dy, int alpha) {
        GeneralPath moved = (GeneralPath) o.getPath().createTransformedShape(AffineTransform.getTranslateInstance(dx, dy));
        AlcShape c = o.customClone(moved);
        c.setAlpha(alpha);
        c.setSpine(null);
        return c;
    }
}
