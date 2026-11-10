/*
 *  This file is part of the Alchemy project - http://al.chemy.org
 *
 *  Alchemy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package org.alchemy.core;

import java.awt.Color;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;

/**
 * A line piece drawn as bands along its spine - a band for every step and wedges at the joints,
 * with no pointed start. Pieces that carry on from each other (with a different opacity, in the
 * opacity pen pressure mode) meet cleanly. A spine shape, so Displace and Mirror work on it.
 */
public class AlcBandShape extends AlcShape {

    private final boolean continued;

    public AlcBandShape(Color color, int alpha, float lineWidth, boolean continued) {
        super(new GeneralPath(), color, alpha, STYLE_FILL, lineWidth);
        this.continued = continued;
        setSpine(new ArrayList<Point2D.Float>());
        setSpineWidth(new ArrayList<Float>());
    }

    /** Add a point to the spine - skipped when too close to the last one */
    public void bandTo(Point2D.Float p, float halfWidth) {
        ArrayList<Point2D.Float> spine = getSpine();
        if (spine.isEmpty() || p.distance(spine.get(spine.size() - 1)) >= 2) {
            spine.add(new Point2D.Float(p.x, p.y));
            getSpineWidth().add(halfWidth);
            createSpine();
        }
    }

    /** A new piece starting where this one ends, with the same width there */
    public AlcBandShape carryOn(int alpha) {
        AlcBandShape next = new AlcBandShape(getColor(), alpha, getLineWidth(), true);
        ArrayList<Point2D.Float> spine = getSpine();
        if (!spine.isEmpty()) {
            Point2D.Float end = spine.get(spine.size() - 1);
            next.getSpine().add(new Point2D.Float(end.x, end.y));
            next.getSpineWidth().add(getSpineWidth().get(getSpineWidth().size() - 1));
        }
        return next;
    }

    @Override
    public void createSpine() {
        if (getSpine() != null && !getSpine().isEmpty()) {
            setPath(outline(getSpine(), getSpineWidth(), continued));
        }
    }

    @Override
    public AlcShape customClone(ArrayList<Point2D.Float> spine, ArrayList<Float> spineWidth) {
        AlcBandShape copy = new AlcBandShape(getColor(), getAlpha(), getLineWidth(), continued);
        copy.setGradientPaint(getGradientPaint());
        copy.setSpine(spine);
        copy.setSpineWidth(spineWidth);
        copy.createSpine();
        return copy;
    }

    /** A plain filled shape of the same outline - to keep once the stroke is finished */
    public AlcShape flatten() {
        AlcShape flat = new AlcShape(getPath(), getColor(), getAlpha(), STYLE_FILL, getLineWidth());
        flat.setGradientPaint(getGradientPaint());
        return flat;
    }

    /** The outline: a band for every step with wedges filling the joints */
    public static GeneralPath outline(ArrayList<Point2D.Float> spine, ArrayList<Float> widths, boolean continued) {
        GeneralPath path = new GeneralPath(GeneralPath.WIND_NON_ZERO);
        int n = spine.size();
        if (n == 1) {
            // A dot
            Point2D.Float a = spine.get(0);
            float w = widths.get(0);
            path.append(new java.awt.geom.Ellipse2D.Float(a.x - w, a.y - w, w * 2, w * 2), false);
            return path;
        }
        for (int i = 1; i < n; i++) {
            Point2D.Float a = spine.get(i - 1), b = spine.get(i);
            double dx = b.x - a.x, dy = b.y - a.y;
            double length = Math.sqrt(dx * dx + dy * dy);
            if (length < 0.001) {
                continue;
            }
            double nx = -dy / length, ny = dx / length;
            float wa = widths.get(i - 1), wb = widths.get(i);
            // A piece carrying on from another starts half a pixel back so no light seam shows
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
}
