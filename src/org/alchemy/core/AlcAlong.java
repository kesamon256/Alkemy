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
 * centre line. Each slice paints only its own part and shades from the colour where it starts to
 * the colour where it ends:
 * <ul>
 * <li>a shape made of many parts (specks of grain, hatching lines) - the parts are shared out
 *     between the slices in the order they were drawn, each part painted once;</li>
 * <li>a line - each slice draws its piece of the line, limited to a band across it so the pieces
 *     meet edge to edge with no seam;</li>
 * <li>a variable width line (with a spine) - the whole outline limited to each band in turn.</li>
 * </ul>
 * How many slices a shape gets follows how much its colour changes - a shape that is a small
 * part of a long stroke is painted in one go with a straight gradient.
 */
final class AlcAlong implements AlcConstants {

    private AlcAlong() {
    }

    /** One slice: what it draws, where it may draw, and where its two colours are */
    static final class Slice {
        /** Only inside this - null for anywhere */
        Shape clip;
        /** Draw this part instead of the whole shape - null for the whole shape */
        Shape part;
        /** Its ends along the shape, 0 to 1 */
        float t1, t2;
        /** Where its two colours are - the same point for one colour */
        float x1, y1, x2, y2;
        /** The clip and the part as Skia paths, made when first needed */
        Object skiaClip, skiaPart;
    }

    /** Slices worked out for how a shape looked */
    private static final class Cached {
        GeneralPath path;
        int version, style, dash, spine, count;
        float width;
        double bx, by, bw, bh;
        List<Slice> slices;
        /** Made in a hurry while drawing - one straight gradient for now, worked out properly later */
        boolean quick;
    }

    /** While a stroke is drawn, slices are worked out only for this long in a frame */
    private static final long BUDGET = 4000000L;
    private static long budgetEnd;

    /** A new frame of the stroke being drawn starts - its time for working out slices starts again */
    static void liveFrame() {
        budgetEnd = System.nanoTime() + BUDGET;
    }

    private static final java.util.Map<AlcShape, Cached> cache = new java.util.WeakHashMap<AlcShape, Cached>();
    /** Most slices for a shape that runs the whole stroke */
    private static final int MAX_SLICES = 32;

    /** Does the shape change colour along its length */
    static boolean has(AlcShape s) {
        return s.alongColor != null;
    }

    private static int spineSize(AlcShape s) {
        return (s.getSpine() == null) ? 0 : s.getSpine().size();
    }

    /** The colour at a place along the whole stroke, 0 = start, 1 = end */
    static Color colorAt(AlcShape s, float t) {
        t = Math.max(0F, Math.min(1F, t));
        Color a = s.color, b = s.alongColor;
        float alpha = s.alpha * (1F - t + t * b.getAlpha() / 255F);
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.max(0, Math.min(255, Math.round(alpha))));
    }

    /** The inside under an outline is only a light tint */
    private static Color tint(Color c, boolean fill, AlcShape s) {
        if (fill && s.style == STYLE_BOTH) {
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(c.getAlpha() * 0.3F));
        }
        return c;
    }

    /** The paint of a slice, for the inside or the outline */
    static java.awt.Paint paint(Slice slice, boolean fill, AlcShape s) {
        float range = s.alongTo - s.alongFrom;
        Color c1 = tint(colorAt(s, s.alongFrom + range * slice.t1), fill, s);
        if (slice.x1 == slice.x2 && slice.y1 == slice.y2) {
            return c1;
        }
        Color c2 = tint(colorAt(s, s.alongFrom + range * slice.t2), fill, s);
        return new GradientPaint(slice.x1, slice.y1, c1, slice.x2, slice.y2, c2);
    }

    /**
     * How many slices - enough that the colour changes only a little within one, but none shorter
     * than about 20 px: within a slice the colour already shades smoothly, so a short shape (a trail
     * of the Flow brush) is painted in one go, however much of the gradient it covers
     */
    private static int count(AlcShape s, java.awt.geom.Rectangle2D bounds) {
        float range = Math.abs(s.alongTo - s.alongFrom);
        // A shape covering little of the gradient - one straight shade across it is enough
        if (range < 0.15F) {
            return 1;
        }
        // In steps of 4, so the count stays the same while the stroke stretches and nothing is worked out again
        int byColour = Math.min(MAX_SLICES, (int) Math.ceil(range * MAX_SLICES / 4) * 4);
        int byLength = (int) Math.ceil((bounds.getWidth() + bounds.getHeight()) / 20);
        int n = Math.min(byColour, byLength);
        return (n <= 2) ? 1 : n;
    }

    static List<Slice> slices(AlcShape s) {
        return slices(s, false);
    }

    /**
     * The slices of a shape - worked out again only when its shape has changed. While drawing
     * (live) there is a time budget a frame: past it a changed shape gets one straight gradient for
     * now, and its proper slices in a later frame - many trails changing at once stay quick
     */
    static synchronized List<Slice> slices(AlcShape s, boolean live) {
        Cached c = cache.get(s);
        java.awt.geom.Rectangle2D b = s.getCachedBounds();
        int wanted = count(s, b);
        if (c != null && c.path == s.path && c.version == s.version && c.style == s.style && c.dash == s.dash
                && c.spine == spineSize(s) && c.width == s.lineWidth && c.bx == b.getX() && c.by == b.getY()
                && c.bw == b.getWidth() && c.bh == b.getHeight() && c.count == wanted) {
            if (!c.quick || (live && System.nanoTime() > budgetEnd)) {
                return c.slices;
            }
        }
        boolean hurry = live && wanted > 1 && System.nanoTime() > budgetEnd;
        c = new Cached();
        c.path = s.path;
        c.version = s.version;
        c.style = s.style;
        c.dash = s.dash;
        c.spine = spineSize(s);
        c.width = s.lineWidth;
        c.bx = b.getX();
        c.by = b.getY();
        c.bw = b.getWidth();
        c.bh = b.getHeight();
        c.count = wanted;
        c.quick = hurry;
        c.slices = hurry ? single(ends(s.path)) : build(s, wanted);
        cache.put(s, c);
        return c.slices;
    }

    /** Draw a shape with its gradient along it, with Java2D */
    static void draw(Graphics2D g2, AlcShape s) {
        draw(g2, s, false);
    }

    static void draw(Graphics2D g2, AlcShape s, boolean live) {
        Shape oldClip = g2.getClip();
        java.awt.Paint oldPaint = g2.getPaint();
        for (Slice slice : slices(s, live)) {
            if (slice.clip != null) {
                g2.clip(slice.clip);
            }
            Shape what = (slice.part != null) ? slice.part : s.path;
            if (s.isFilled()) {
                g2.setPaint(paint(slice, true, s));
                g2.fill(what);
            }
            if (s.isStroked()) {
                g2.setStroke(s.getStroke());
                g2.setPaint(paint(slice, false, s));
                g2.draw(what);
            }
            if (slice.clip != null) {
                g2.setClip(oldClip);
            }
        }
        g2.setPaint(oldPaint);
    }

    //////////////////////////////////////////////////////////////
    // WORKING OUT THE SLICES
    //////////////////////////////////////////////////////////////
    /** The parts of a path, each as its own path, with the middle of each */
    private static final class Parts {
        ArrayList<GeneralPath> paths = new ArrayList<GeneralPath>();
        ArrayList<float[]> middles = new ArrayList<float[]>();
    }

    private static List<Slice> build(AlcShape s, int count) {
        ArrayList<Point2D.Float> spine = s.getSpine();
        if (spine != null && spine.size() >= 2) {
            // A variable width line - its outline limited to bands across the spine
            float[] line = new float[spine.size() * 2];
            for (int i = 0; i < spine.size(); i++) {
                line[i * 2] = spine.get(i).x;
                line[i * 2 + 1] = spine.get(i).y;
            }
            float widest = 0;
            for (Float w : s.getSpineWidth()) {
                widest = Math.max(widest, w);
            }
            // With room to spare - the outline bulges a little past the spine on a bend
            return banded(s, clean(line), widest + Math.max(4F, widest * 0.5F), count, false);
        }
        if (count == 1) {
            return single(ends(s.path));
        }
        Parts parts = parts(s.path);
        if (parts.paths.size() > 1) {
            return grouped(s, parts, count);
        }
        if (!s.isFilled()) {
            // A line - each slice draws its own piece, limited to its band
            float[] line = clean(points(s.path));
            return banded(s, line, s.lineWidth / 2F + Math.max(4F, s.lineWidth * 0.25F), count, s.dash == DASH_NONE);
        }
        // One solid outline - straight across, from where it starts to its far side
        return single(far(s.path));
    }

    /** The whole shape as one slice */
    private static List<Slice> single(float[] ends) {
        Slice slice = new Slice();
        slice.t1 = 0F;
        slice.t2 = 1F;
        if (ends != null) {
            slice.x1 = ends[0];
            slice.y1 = ends[1];
            slice.x2 = ends[2];
            slice.y2 = ends[3];
        }
        List<Slice> one = new ArrayList<Slice>(1);
        one.add(slice);
        return one;
    }

    /** Where a path starts and where it ends - quick, without going through it twice */
    private static float[] ends(GeneralPath path) {
        PathIterator it = path.getPathIterator(null);
        if (it.isDone()) {
            return null;
        }
        float[] q = new float[6];
        it.currentSegment(q);
        Point2D last = path.getCurrentPoint();
        if (last == null) {
            return null;
        }
        return new float[]{q[0], q[1], (float) last.getX(), (float) last.getY()};
    }

    /** Slices along a centre line, each limited to a band across the line */
    private static List<Slice> banded(AlcShape s, float[] line, float reach, int count, boolean pieces) {
        if (line.length < 4) {
            return single(null);
        }
        double total = length(line);
        count = Math.max(1, Math.min(count, (int) Math.ceil(total / 6)));
        if (count == 1) {
            return single(new float[]{line[0], line[1], line[line.length - 2], line[line.length - 1]});
        }
        List<Slice> slices = new ArrayList<Slice>(count);
        BasicStroke band = new BasicStroke(reach * 2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
        // A piece of the line reaches a little past its band, so its own round end is cut off cleanly
        double over = s.lineWidth + 2;
        for (int k = 0; k < count; k++) {
            double a = total * k / count, b = total * (k + 1) / count;
            Slice slice = new Slice();
            slice.clip = band.createStrokedShape(piece(line, a, b, k == 0 ? reach : 0, k == count - 1 ? reach : 0));
            if (pieces) {
                slice.part = piece(line, Math.max(0, a - over), Math.min(total, b + over), 0, 0);
            }
            float[] pa = pointAt(line, a), pb = pointAt(line, b);
            slice.x1 = pa[0];
            slice.y1 = pa[1];
            slice.x2 = pb[0];
            slice.y2 = pb[1];
            slice.t1 = (float) k / count;
            slice.t2 = (float) (k + 1) / count;
            slices.add(slice);
        }
        return slices;
    }

    /** The parts shared out between the slices in the order they were drawn - each painted once */
    private static List<Slice> grouped(AlcShape s, Parts parts, int count) {
        int n = parts.paths.size();
        // How far along the parts each one is - from one middle to the next
        double[] at = new double[n];
        for (int i = 1; i < n; i++) {
            float[] p = parts.middles.get(i - 1), q = parts.middles.get(i);
            at[i] = at[i - 1] + Math.hypot(q[0] - p[0], q[1] - p[1]);
        }
        double total = Math.max(1e-6, at[n - 1]);
        count = Math.max(1, Math.min(count, n));
        List<Slice> slices = new ArrayList<Slice>(count);
        int i = 0;
        for (int k = 0; k < count && i < n; k++) {
            double limit = total * (k + 1) / count;
            GeneralPath group = new GeneralPath(s.path.getWindingRule());
            int first = i;
            while (i < n && (at[i] <= limit || k == count - 1)) {
                group.append(parts.paths.get(i), false);
                i++;
            }
            if (i == first) {
                continue;
            }
            Slice slice = new Slice();
            slice.part = group;
            float[] p = parts.middles.get(first), q = parts.middles.get(i - 1);
            slice.x1 = p[0];
            slice.y1 = p[1];
            slice.x2 = q[0];
            slice.y2 = q[1];
            slice.t1 = (float) (at[first] / total);
            slice.t2 = (float) (at[i - 1] / total);
            slices.add(slice);
        }
        return slices;
    }

    /** Split a path into its parts - each starts where the path moves to a new place */
    private static Parts parts(GeneralPath path) {
        Parts parts = new Parts();
        GeneralPath current = null;
        float sx = 0, sy = 0;
        int count = 0;
        float[] q = new float[6];
        for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
            int type = it.currentSegment(q);
            if (type == PathIterator.SEG_MOVETO) {
                if (current != null) {
                    parts.paths.add(current);
                    parts.middles.add(new float[]{sx / count, sy / count});
                }
                current = new GeneralPath(path.getWindingRule());
                current.moveTo(q[0], q[1]);
                sx = q[0];
                sy = q[1];
                count = 1;
                continue;
            }
            if (current == null) {
                current = new GeneralPath(path.getWindingRule());
                current.moveTo(0, 0);
                count = 1;
            }
            switch (type) {
                case PathIterator.SEG_LINETO:
                    current.lineTo(q[0], q[1]);
                    sx += q[0];
                    sy += q[1];
                    count++;
                    break;
                case PathIterator.SEG_QUADTO:
                    current.quadTo(q[0], q[1], q[2], q[3]);
                    sx += q[2];
                    sy += q[3];
                    count++;
                    break;
                case PathIterator.SEG_CUBICTO:
                    current.curveTo(q[0], q[1], q[2], q[3], q[4], q[5]);
                    sx += q[4];
                    sy += q[5];
                    count++;
                    break;
                default:
                    current.closePath();
            }
        }
        if (current != null) {
            parts.paths.add(current);
            parts.middles.add(new float[]{sx / count, sy / count});
        }
        return parts;
    }

    /** The points of a path in order, curves flattened */
    private static float[] points(GeneralPath path) {
        float[] out = new float[64];
        int n = 0;
        float[] q = new float[6];
        for (PathIterator it = path.getPathIterator(null, 0.5); !it.isDone(); it.next()) {
            if (it.currentSegment(q) != PathIterator.SEG_CLOSE) {
                if (n + 2 > out.length) {
                    out = java.util.Arrays.copyOf(out, out.length * 2);
                }
                out[n++] = q[0];
                out[n++] = q[1];
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /** The start of an outline and its point furthest from the start */
    private static float[] far(GeneralPath path) {
        float[] p = points(path);
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
