package org.alchemy.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A colour gradient that follows the stroke - from the shape's own colour to its "along" colour,
 * around every curve, not in a straight line across. The shape is painted in slices along its
 * centre line (the spine of a variable width line, or the line itself); each slice may only paint
 * its own part of the shape and shades from the colour where it starts to the colour where it ends.
 * Slices meet edge to edge across the line, so every pixel is painted once.
 */
final class AlcAlong implements AlcConstants {

    private AlcAlong() {
    }

    /** One slice of a shape: the area it may paint (null for the whole shape) and its colours at both ends */
    static final class Slice {
        Shape clip;
        float x1, y1, x2, y2;
        Color c1, c2;
        /** The clip as a Skia path, made when first needed */
        Object skia;
    }

    /** Slices worked out for a shape, and how the shape looked then */
    private static final class Cached {
        GeneralPath path;
        int version, style, rgb, alpha, along, spine;
        float width, from, to;
        double bx, by, bw, bh;
        List<Slice> slices;

        boolean same(AlcShape s) {
            java.awt.geom.Rectangle2D b = s.path.getBounds2D();
            return s.path == path && s.version == version && s.style == style && s.color.getRGB() == rgb && s.alpha == alpha
                    && s.alongColor.getRGB() == along && s.alongColor.getAlpha() == alongAlpha(s) && spineSize(s) == spine
                    && s.lineWidth == width && s.alongFrom == from && s.alongTo == to
                    && b.getX() == bx && b.getY() == by && b.getWidth() == bw && b.getHeight() == bh;
        }

        private static int alongAlpha(AlcShape s) {
            return s.alongColor.getAlpha();
        }
    }

    private static final java.util.Map<AlcShape, Cached> cache = new java.util.WeakHashMap<AlcShape, Cached>();

    /** Does the shape change colour along its length */
    static boolean has(AlcShape s) {
        return s.alongColor != null;
    }

    private static int spineSize(AlcShape s) {
        return (s.getSpine() == null) ? 0 : s.getSpine().size();
    }

    /** The colour at a place along the stroke, 0 = start, 1 = end */
    static Color colorAt(AlcShape s, float t, boolean unused) {
        t = Math.max(0F, Math.min(1F, t));
        Color a = s.color, b = s.alongColor;
        float alpha = s.alpha * (1F - t + t * b.getAlpha() / 255F);
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.max(0, Math.min(255, Math.round(alpha))));
    }

    /** The slices of a shape - worked out again only when the shape has changed */
    static synchronized List<Slice> slices(AlcShape s) {
        Cached c = cache.get(s);
        if (c != null && c.same(s)) {
            return c.slices;
        }
        c = new Cached();
        c.path = s.path;
        c.version = s.version;
        c.style = s.style;
        c.rgb = s.color.getRGB();
        c.alpha = s.alpha;
        c.along = s.alongColor.getRGB();
        c.spine = spineSize(s);
        c.width = s.lineWidth;
        c.from = s.alongFrom;
        c.to = s.alongTo;
        java.awt.geom.Rectangle2D b = s.path.getBounds2D();
        c.bx = b.getX();
        c.by = b.getY();
        c.bw = b.getWidth();
        c.bh = b.getHeight();
        c.slices = build(s);
        cache.put(s, c);
        return c.slices;
    }

    /** Draw a shape with its gradient along it, with Java2D */
    static void draw(Graphics2D g2, AlcShape s) {
        Shape oldClip = g2.getClip();
        java.awt.Paint oldPaint = g2.getPaint();
        for (Slice slice : slices(s)) {
            if (slice.clip != null) {
                g2.clip(slice.clip);
            }
            if (s.isFilled()) {
                g2.setPaint(paint(slice, true, s));
                g2.fill(s.path);
            }
            if (s.isStroked()) {
                g2.setStroke(s.getStroke());
                g2.setPaint(paint(slice, false, s));
                g2.draw(s.path);
            }
            g2.setClip(oldClip);
        }
        g2.setPaint(oldPaint);
    }

    /** The paint of a slice - for the tinted inside the colours are made lighter the same way */
    static java.awt.Paint paint(Slice slice, boolean fill, AlcShape s) {
        Color c1 = tint(slice.c1, fill, s), c2 = tint(slice.c2, fill, s);
        if (slice.x1 == slice.x2 && slice.y1 == slice.y2) {
            return c1;
        }
        return new GradientPaint(slice.x1, slice.y1, c1, slice.x2, slice.y2, c2);
    }

    /** The inside under an outline is only a light tint */
    static Color tint(Color c, boolean fill, AlcShape s) {
        if (fill && s.style == STYLE_BOTH) {
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(c.getAlpha() * 0.3F));
        }
        return c;
    }

    //////////////////////////////////////////////////////////////
    // WORKING OUT THE SLICES
    //////////////////////////////////////////////////////////////
    private static List<Slice> build(AlcShape s) {
        float[] line;
        float reach;
        ArrayList<Point2D.Float> spine = s.getSpine();
        if (spine != null && spine.size() >= 2) {
            // A variable width line - along its spine, as wide as its widest point
            line = new float[spine.size() * 2];
            for (int i = 0; i < spine.size(); i++) {
                line[i * 2] = spine.get(i).x;
                line[i * 2 + 1] = spine.get(i).y;
            }
            float widest = 0;
            for (Float w : s.getSpineWidth()) {
                widest = Math.max(widest, w);
            }
            // With room to spare - the outline bulges a little past the spine on a bend, and the
            // soft edge must not be cut, or the line looks jagged there
            reach = widest + Math.max(4F, widest * 0.5F);
        } else if (!s.isFilled()) {
            // A line - along itself
            line = points(s.path, false);
            reach = s.lineWidth / 2F + Math.max(4F, s.lineWidth * 0.25F);
        } else {
            int parts = parts(s.path);
            if (parts > 1) {
                // Specks or pieces - from the middle of one to the next, in the order they were drawn
                line = points(s.path, true);
                reach = partSize(s.path) + 4;
            } else {
                // One solid outline - straight across, from where it starts to its far side
                return single(s, far(s.path));
            }
        }
        line = clean(line);
        double total = length(line);
        int count = (int) Math.max(1, Math.min(48, Math.round(total / 8)));
        if (Math.abs(s.alongTo - s.alongFrom) < 0.02F || line.length < 4) {
            count = 1;
        }
        if (count == 1) {
            return single(s, line.length >= 4 ? new float[]{line[0], line[1], line[line.length - 2], line[line.length - 1]} : null);
        }
        List<Slice> slices = new ArrayList<Slice>(count);
        BasicStroke band = new BasicStroke(reach * 2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
        for (int k = 0; k < count; k++) {
            double a = total * k / count, b = total * (k + 1) / count;
            GeneralPath piece = piece(line, a, b, k == 0 ? reach : 0, k == count - 1 ? reach : 0);
            Slice slice = new Slice();
            slice.clip = band.createStrokedShape(piece);
            float[] pa = pointAt(line, a), pb = pointAt(line, b);
            slice.x1 = pa[0];
            slice.y1 = pa[1];
            slice.x2 = pb[0];
            slice.y2 = pb[1];
            slice.c1 = colorAt(s, s.alongFrom + (s.alongTo - s.alongFrom) * k / count, false);
            slice.c2 = colorAt(s, s.alongFrom + (s.alongTo - s.alongFrom) * (k + 1) / count, false);
            slices.add(slice);
        }
        return slices;
    }

    /** The whole shape as one slice, shading from one point to another */
    private static List<Slice> single(AlcShape s, float[] ends) {
        Slice slice = new Slice();
        if (ends != null) {
            slice.x1 = ends[0];
            slice.y1 = ends[1];
            slice.x2 = ends[2];
            slice.y2 = ends[3];
        }
        slice.c1 = colorAt(s, s.alongFrom, false);
        slice.c2 = colorAt(s, s.alongTo, false);
        List<Slice> one = new ArrayList<Slice>(1);
        one.add(slice);
        return one;
    }

    /** The points of a path in order - or the middle of each of its parts */
    private static float[] points(GeneralPath path, boolean middles) {
        float[] out = new float[64];
        int n = 0;
        float[] q = new float[6];
        float sx = 0, sy = 0;
        int count = 0;
        for (PathIterator it = path.getPathIterator(null, 1.0); !it.isDone(); it.next()) {
            int type = it.currentSegment(q);
            if (middles) {
                if (type == PathIterator.SEG_MOVETO && count > 0) {
                    if (n + 2 > out.length) {
                        out = java.util.Arrays.copyOf(out, out.length * 2);
                    }
                    out[n++] = sx / count;
                    out[n++] = sy / count;
                    sx = sy = 0;
                    count = 0;
                }
                if (type != PathIterator.SEG_CLOSE) {
                    sx += q[0];
                    sy += q[1];
                    count++;
                }
            } else if (type != PathIterator.SEG_CLOSE) {
                if (n + 2 > out.length) {
                    out = java.util.Arrays.copyOf(out, out.length * 2);
                }
                out[n++] = q[0];
                out[n++] = q[1];
            }
        }
        if (middles && count > 0) {
            if (n + 2 > out.length) {
                out = java.util.Arrays.copyOf(out, out.length * 2);
            }
            out[n++] = sx / count;
            out[n++] = sy / count;
        }
        return java.util.Arrays.copyOf(out, n);
    }

    private static int parts(GeneralPath path) {
        int parts = 0;
        float[] q = new float[6];
        for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
            if (it.currentSegment(q) == PathIterator.SEG_MOVETO) {
                parts++;
            }
        }
        return parts;
    }

    /** About how far a part reaches from its middle */
    private static float partSize(GeneralPath path) {
        float[] q = new float[6];
        float biggest = 0, minX = 0, minY = 0, maxX = 0, maxY = 0;
        boolean open = false;
        for (PathIterator it = path.getPathIterator(null, 1.0); !it.isDone(); it.next()) {
            int type = it.currentSegment(q);
            if (type == PathIterator.SEG_MOVETO) {
                if (open) {
                    biggest = Math.max(biggest, Math.max(maxX - minX, maxY - minY));
                }
                minX = maxX = q[0];
                minY = maxY = q[1];
                open = true;
            } else if (type != PathIterator.SEG_CLOSE) {
                minX = Math.min(minX, q[0]);
                maxX = Math.max(maxX, q[0]);
                minY = Math.min(minY, q[1]);
                maxY = Math.max(maxY, q[1]);
            }
        }
        if (open) {
            biggest = Math.max(biggest, Math.max(maxX - minX, maxY - minY));
        }
        return biggest;
    }

    /** The start of an outline and its point furthest from the start */
    private static float[] far(GeneralPath path) {
        float[] p = points(path, false);
        if (p.length < 4) {
            return null;
        }
        int best = 2;
        double bestD = -1;
        for (int i = 2; i < p.length; i += 2) {
            double d = Math.hypot(p[i] - p[0], p[i + 1] - p[1]);
            if (d > bestD) {
                bestD = d;
                best = i;
            }
        }
        return new float[]{p[0], p[1], p[best], p[best + 1]};
    }

    /** Without points on top of each other - a cut must not fall where the line has no direction */
    private static float[] clean(float[] p) {
        float[] out = new float[p.length];
        int n = 0;
        for (int i = 0; i < p.length; i += 2) {
            if (n == 0 || Math.hypot(p[i] - out[n - 2], p[i + 1] - out[n - 1]) > 0.05) {
                out[n++] = p[i];
                out[n++] = p[i + 1];
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    private static double length(float[] p) {
        double total = 0;
        for (int i = 2; i < p.length; i += 2) {
            total += Math.hypot(p[i] - p[i - 2], p[i + 1] - p[i - 1]);
        }
        return total;
    }

    /** The point a given distance along the line */
    private static float[] pointAt(float[] p, double at) {
        double done = 0;
        for (int i = 2; i < p.length; i += 2) {
            double seg = Math.hypot(p[i] - p[i - 2], p[i + 1] - p[i - 1]);
            if (done + seg >= at && seg > 0) {
                double t = (at - done) / seg;
                return new float[]{(float) (p[i - 2] + (p[i] - p[i - 2]) * t), (float) (p[i - 1] + (p[i + 1] - p[i - 1]) * t)};
            }
            done += seg;
        }
        return new float[]{p[p.length - 2], p[p.length - 1]};
    }

    /**
     * The part of the line between two distances along it. The ends of the whole line are carried
     * on straight a little, so the round end of a line or the tip of a taper is covered too.
     */
    private static GeneralPath piece(float[] p, double a, double b, float extendStart, float extendEnd) {
        GeneralPath out = new GeneralPath();
        float[] start = pointAt(p, a);
        double done = 0;
        int i = 2;
        // Find the segment the piece starts in
        for (; i < p.length; i += 2) {
            double seg = Math.hypot(p[i] - p[i - 2], p[i + 1] - p[i - 1]);
            if (done + seg >= a) {
                break;
            }
            done += seg;
        }
        if (extendStart > 0 && i < p.length) {
            double dx = p[i] - p[i - 2], dy = p[i + 1] - p[i - 1], len = Math.hypot(dx, dy);
            out.moveTo((float) (start[0] - dx / len * extendStart), (float) (start[1] - dy / len * extendStart));
            out.lineTo(start[0], start[1]);
        } else {
            out.moveTo(start[0], start[1]);
        }
        // The corners inside the piece
        for (; i < p.length; i += 2) {
            double seg = Math.hypot(p[i] - p[i - 2], p[i + 1] - p[i - 1]);
            if (done + seg >= b) {
                break;
            }
            done += seg;
            out.lineTo(p[i], p[i + 1]);
        }
        float[] end = pointAt(p, b);
        out.lineTo(end[0], end[1]);
        if (extendEnd > 0) {
            int j = Math.min(i, p.length - 2);
            double dx = p[j] - p[j - 2], dy = p[j + 1] - p[j - 1], len = Math.hypot(dx, dy);
            if (len > 0) {
                out.lineTo((float) (end[0] + dx / len * extendEnd), (float) (end[1] + dy / len * extendEnd));
            }
        }
        return out;
    }
}
