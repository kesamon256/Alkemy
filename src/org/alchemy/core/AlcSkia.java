package org.alchemy.core;

import io.github.humbleui.skija.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

/**
 * Draws the shapes of the layers on the graphics card (Skia over OpenGL) instead of the processor.
 * The finished picture is read back into an image, so everything around it (the background, the
 * pen, the overlays, the exports) stays as it is. If the graphics card cannot be used, nothing
 * changes - the canvas draws with Java2D as before.
 */
final class AlcSkia {

    /** Off to draw with Java2D only - for comparing, or on a machine where the card misbehaves */
    static boolean enabled = !Boolean.getBoolean("alchemy.noGpu")
            && java.util.prefs.Preferences.userNodeForPackage(AlcSkia.class).getBoolean("GPU Drawing", true);
    /** Set once the graphics card could not be started - never tried again */
    private static boolean failed = false;
    private static long window;
    private static DirectContext context;
    /** Biggest picture drawn on the card - bigger exports are drawn with Java2D */
    private static final int MAX_SIZE = 8192;
    private static final boolean TIMING = Boolean.getBoolean("alchemy.gpuTiming");
    /** Multisampling - the card smooths the edges itself, every shape stays on the card */
    private static final int SAMPLES = Integer.getInteger("alchemy.gpuSamples", 0);
    private static String renderer = "GPU";
    /** Shown in the menu and the logs */
    static String status = "not started";

    private AlcSkia() {
    }

    /** Switch drawing on the graphics card on or off - remembered for the next start */
    static void setEnabled(boolean on) {
        enabled = on;
        java.util.prefs.Preferences.userNodeForPackage(AlcSkia.class).putBoolean("GPU Drawing", on);
        if (!on && !failed) {
            status = "Java2D";
        } else if (on && context != null) {
            status = renderer;
        }
    }

    /** Can the card draw now, on this thread, a picture of this size */
    static boolean ready(int width, int height) {
        if (!enabled || failed || width <= 0 || height <= 0 || width > MAX_SIZE || height > MAX_SIZE) {
            return false;
        }
        if (context == null) {
            start();
        }
        return context != null;
    }

    private static void start() {
        try {
            if (!GLFW.glfwInit()) {
                throw new IllegalStateException("GLFW could not start");
            }
            GLFW.glfwDefaultWindowHints();
            // A hidden window only to own the OpenGL context - it is never shown
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            window = GLFW.glfwCreateWindow(16, 16, "Alkemy GPU", 0, 0);
            if (window == 0) {
                throw new IllegalStateException("no OpenGL window");
            }
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            String renderer = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER);
            context = DirectContext.makeGL();
            GLFW.glfwMakeContextCurrent(0);
            AlcSkia.renderer = "GPU: " + renderer;
            status = AlcSkia.renderer;
            System.out.println("Alkemy drawing on the " + status);
        } catch (Throwable t) {
            failed = true;
            context = null;
            status = "Java2D (GPU unavailable: " + t + ")";
            System.out.println("Alkemy draws with Java2D - " + t);
        }
    }

    /** A picture on the card and its copy in memory - one for the layers, one for the stroke being drawn */
    private static final class Target {
        Surface surface;
        int width, height;
        ByteBuffer pixels;
        Pixmap pixmap;
        BufferedImage image;
    }

    private static final Target layersTarget = new Target(), liveTarget = new Target();

    /** What to draw onto the card's canvas */
    private interface Drawing {
        void draw(Canvas canvas, Paint paint);
    }

    /**
     * Draw the visible layers into a picture of the given size, through the given transform.
     * Returns null if the card could not do it - then it is drawn the old way.
     */
    static BufferedImage drawLayers(int width, int height, AffineTransform transform, final List<AlcLayer> layers,
            final int activeLayer, final List<AlcShape> createShapes, final List<AlcShape> affectShapes, boolean smoothing) {
        return draw(layersTarget, width, height, transform, smoothing, new Drawing() {
            public void draw(Canvas canvas, Paint paint) {
                for (int l = 0; l < layers.size(); l++) {
                    AlcLayer layer = layers.get(l);
                    if (!layer.visible) {
                        continue;
                    }
                    // Like a layer in Photoshop: drawn together, then faded as a whole
                    boolean faded = layer.opacity < 1F;
                    if (faded) {
                        try (Paint fade = new Paint()) {
                            fade.setAlphaf(Math.max(0F, layer.opacity));
                            canvas.saveLayer(null, fade);
                        }
                    }
                    drawShapes(canvas, paint, layer.shapes);
                    if (l == activeLayer) {
                        drawShapes(canvas, paint, createShapes);
                        drawShapes(canvas, paint, affectShapes);
                    }
                    if (faded) {
                        canvas.restore();
                    }
                }
            }
        });
    }

    /** How a live shape looked - if it looks the same next time, it is finished and can be kept as a picture */
    private static final class Key {
        AlcShape shape;
        GeneralPath path;
        int version, style, rgb, alpha;
        float width;
        Object gradient;
        double x, y, w, h, cx, cy;

        Key(AlcShape s) {
            shape = s;
            path = s.path;
            version = s.version;
            style = s.getStyle();
            rgb = s.getColor().getRGB();
            alpha = s.getAlpha();
            width = s.getLineWidth();
            gradient = s.getGradientPaint();
            java.awt.geom.Rectangle2D b = path.getBounds2D();
            x = b.getX();
            y = b.getY();
            w = b.getWidth();
            h = b.getHeight();
            java.awt.geom.Point2D c = path.getCurrentPoint();
            cx = (c == null) ? Double.NaN : c.getX();
            cy = (c == null) ? Double.NaN : c.getY();
        }

        boolean same(Key k) {
            return k != null && k.shape == shape && k.path == path && k.version == version && k.style == style && k.rgb == rgb
                    && k.alpha == alpha && k.width == width && k.gradient == gradient && k.x == x && k.y == y && k.w == w
                    && k.h == h && Double.compare(k.cx, cx) == 0 && Double.compare(k.cy, cy) == 0;
        }
    }

    /** The finished start of the stroke being drawn, kept on the card - only its growing end is drawn again */
    private static Surface liveBase;
    private static final java.util.ArrayList<Key> baseKeys = new java.util.ArrayList<Key>();
    private static AffineTransform baseTransform;
    private static boolean baseSmoothing;
    private static Key[] lastKeys = new Key[0];
    private static int lastStable = 0;
    /** The part of the live picture with something in it, in its pixels - only that is copied back and laid over */
    static final java.awt.Rectangle liveRegion = new java.awt.Rectangle();
    /** How much of the stroke was drawn from the kept picture last time - for the tests */
    static int liveKept;
    /** How many times a kept shape changed after all and the picture was started again - for the tests */
    static int liveRestarts;

    /** Draw the shapes being made right now into a see-through picture laid over the canvas */
    static BufferedImage drawLive(int width, int height, final AffineTransform transform, final List[] lists, final boolean smoothing) {
        // All the live shapes in drawing order, and how each looks now
        final java.util.ArrayList<AlcShape> all = new java.util.ArrayList<AlcShape>();
        for (List list : lists) {
            for (Object o : list) {
                all.add((AlcShape) o);
            }
        }
        final Key[] keys = new Key[all.size()];
        java.awt.geom.Rectangle2D area = null;
        double edge = 0;
        for (int i = 0; i < keys.length; i++) {
            keys[i] = new Key(all.get(i));
            java.awt.geom.Rectangle2D.Double b = new java.awt.geom.Rectangle2D.Double(keys[i].x, keys[i].y, keys[i].w, keys[i].h);
            area = (area == null) ? b : area.createUnion(b);
            if (all.get(i).isStroked()) {
                edge = Math.max(edge, all.get(i).getLineWidth());
            }
        }
        // Where on the picture the shapes are - a line is wider than its path by half its width
        java.awt.Rectangle region = new java.awt.Rectangle();
        if (area != null) {
            area.setRect(area.getX() - edge, area.getY() - edge, area.getWidth() + edge * 2, area.getHeight() + edge * 2);
            region = transform.createTransformedShape(area).getBounds();
            region.grow(2, 2);
            region = region.intersection(new java.awt.Rectangle(0, 0, width, height));
        }
        final java.awt.Rectangle readRegion = region.isEmpty() ? new java.awt.Rectangle() : region;
        BufferedImage image = draw(liveTarget, width, height, transform, smoothing, new Drawing() {
            public void draw(Canvas canvas, Paint paint) {
                updateBase(paint, all, keys, transform, smoothing);
                // The kept start laid in pixel for pixel, then the rest of the stroke over it
                if (baseKeys.size() > 0) {
                    canvas.save();
                    canvas.resetMatrix();
                    try (io.github.humbleui.skija.Image kept = liveBase.makeImageSnapshot()) {
                        canvas.drawImage(kept, 0, 0);
                    }
                    canvas.restore();
                }
                drawShapes(canvas, paint, all, baseKeys.size(), all.size());
            }
        }, readRegion);
        if (image != null) {
            liveRegion.setBounds(readRegion);
        }
        return image;
    }

    /** Start the kept picture again - the next live picture is drawn in full */
    static synchronized void forgetLive() {
        baseKeys.clear();
        baseTransform = null;
        lastKeys = new Key[0];
        lastStable = 0;
    }

    /** Add the shapes that stopped changing to the kept picture - or start it again if one of its shapes changed */
    private static void updateBase(Paint paint, List<AlcShape> all, Key[] keys, AffineTransform transform, boolean smoothing) {
        int width = liveTarget.width, height = liveTarget.height;
        if (liveBase == null || liveBase.getWidth() != width || liveBase.getHeight() != height) {
            if (liveBase != null) {
                liveBase.close();
            }
            liveBase = Surface.makeRenderTarget(context, false, new ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL));
            baseKeys.clear();
        }
        boolean valid = transform.equals(baseTransform) && smoothing == baseSmoothing && baseKeys.size() <= keys.length;
        for (int i = 0; valid && i < baseKeys.size(); i++) {
            valid = baseKeys.get(i).same(keys[i]);
        }
        if (!valid) {
            if (baseKeys.size() > 0) {
                liveRestarts++;
            }
            liveBase.getCanvas().clear(0);
            baseKeys.clear();
            baseTransform = new AffineTransform(transform);
            baseSmoothing = smoothing;
        }
        // Shapes that looked the same the last two times are taken as finished
        int stable = 0;
        while (stable < keys.length && stable < lastKeys.length && keys[stable].same(lastKeys[stable])) {
            stable++;
        }
        int finished = Math.min(stable, lastStable);
        lastKeys = keys;
        lastStable = stable;
        if (finished > baseKeys.size()) {
            Canvas base = liveBase.getCanvas();
            base.setMatrix(matrix(transform));
            drawShapes(base, paint, all, baseKeys.size(), finished);
            for (int i = baseKeys.size(); i < finished; i++) {
                baseKeys.add(keys[i]);
            }
        }
        liveKept = baseKeys.size();
    }

    private static BufferedImage draw(Target target, int width, int height, AffineTransform transform, boolean smoothing, Drawing drawing) {
        return draw(target, width, height, transform, smoothing, drawing, null);
    }

    private static synchronized BufferedImage draw(Target target, int width, int height, AffineTransform transform, boolean smoothing, Drawing drawing,
            java.awt.Rectangle region) {
        if (!ready(width, height)) {
            return null;
        }
        // The card is used from whichever thread draws - the screen, an export, a thumbnail - one at a time
        GLFW.glfwMakeContextCurrent(window);
        try {
            if (target.surface == null || target.width != width || target.height != height) {
                if (target.surface != null) {
                    target.surface.close();
                }
                ImageInfo info = new ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL);
                target.surface = (SAMPLES > 0) ? Surface.makeRenderTarget(context, false, info, SAMPLES, null) : Surface.makeRenderTarget(context, false, info);
                if (target.surface == null) {
                    throw new IllegalStateException("no surface " + width + "x" + height);
                }
                target.width = width;
                target.height = height;
                target.pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
                target.pixmap = Pixmap.make(info, target.pixels, width * 4);
                target.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            }
            // Only the screen reuses its picture - anything else gets its own
            BufferedImage out = javax.swing.SwingUtilities.isEventDispatchThread() ? target.image : new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            long t0 = System.nanoTime();
            Canvas canvas = target.surface.getCanvas();
            canvas.clear(0);
            canvas.setMatrix(matrix(transform));
            try (Paint paint = new Paint()) {
                paint.setAntiAlias(smoothing);
                drawing.draw(canvas, paint);
            }
            long t1 = System.nanoTime();
            context.flushAndSubmit(target.surface, true);
            long t2 = System.nanoTime();
            int[] data = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
            if (region == null) {
                target.surface.readPixels(target.pixmap, 0, 0);
                target.pixels.rewind();
                target.pixels.asIntBuffer().get(data);
            } else if (!region.isEmpty()) {
                // Only the part with something in it - straight into its place in the picture
                int offset = (region.y * width + region.x) * 4;
                ByteBuffer part = target.pixels.duplicate().position(offset).slice().order(ByteOrder.LITTLE_ENDIAN);
                try (Pixmap partMap = Pixmap.make(new ImageInfo(region.width, region.height, ColorType.BGRA_8888, ColorAlphaType.PREMUL), part, width * 4)) {
                    target.surface.readPixels(partMap, region.x, region.y);
                }
                java.nio.IntBuffer ints = target.pixels.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
                for (int row = region.y; row < region.y + region.height; row++) {
                    ints.position(row * width + region.x);
                    ints.get(data, row * width + region.x, region.width);
                }
            }
            if (TIMING) {
                System.out.println(String.format("gpu %s: record %d ms, draw %d ms, read back %d ms",
                        target == liveTarget ? "live" : "layers", (t1 - t0) / 1000000, (t2 - t1) / 1000000, (System.nanoTime() - t2) / 1000000));
            }
            return out;
        } catch (Throwable t) {
            // Something the card cannot do - from now on everything is drawn with Java2D
            failed = true;
            status = "Java2D (GPU failed: " + t + ")";
            System.out.println("Alkemy GPU drawing failed, back to Java2D - " + t);
            t.printStackTrace();
            return null;
        } finally {
            GLFW.glfwMakeContextCurrent(0);
        }
    }

    private static Matrix33 matrix(AffineTransform t) {
        return new Matrix33((float) t.getScaleX(), (float) t.getShearX(), (float) t.getTranslateX(),
                (float) t.getShearY(), (float) t.getScaleY(), (float) t.getTranslateY(), 0F, 0F, 1F);
    }

    private static void drawShapes(Canvas canvas, Paint paint, List<AlcShape> list) {
        drawShapes(canvas, paint, list, 0, list.size());
    }

    private static void drawShapes(Canvas canvas, Paint paint, List<AlcShape> list, int from, int to) {
        for (int i = from; i < to; i++) {
            AlcShape shape = list.get(i);
            {
                Path path = path(shape.path, shape.version);
                // Solid under the outline when both are drawn
                if (shape.isFilled()) {
                    setPaint(paint, shape.getFillPaint());
                    paint.setMode(PaintMode.FILL);
                    paint.setPathEffect(null);
                    canvas.drawPath(path, paint);
                }
                if (shape.isStroked()) {
                    setPaint(paint, shape.getPaint());
                    BasicStroke stroke = shape.getStroke();
                    paint.setMode(PaintMode.STROKE);
                    paint.setStrokeWidth(stroke.getLineWidth());
                    paint.setStrokeCap(stroke.getEndCap() == BasicStroke.CAP_ROUND ? PaintStrokeCap.ROUND
                            : stroke.getEndCap() == BasicStroke.CAP_SQUARE ? PaintStrokeCap.SQUARE : PaintStrokeCap.BUTT);
                    paint.setStrokeJoin(stroke.getLineJoin() == BasicStroke.JOIN_ROUND ? PaintStrokeJoin.ROUND
                            : stroke.getLineJoin() == BasicStroke.JOIN_BEVEL ? PaintStrokeJoin.BEVEL : PaintStrokeJoin.MITER);
                    paint.setStrokeMiter(stroke.getMiterLimit());
                    float[] dash = stroke.getDashArray();
                    if (dash != null) {
                        try (PathEffect effect = PathEffect.makeDash(dash, stroke.getDashPhase())) {
                            paint.setPathEffect(effect);
                            canvas.drawPath(path, paint);
                        }
                        paint.setPathEffect(null);
                    } else {
                        paint.setPathEffect(null);
                        canvas.drawPath(path, paint);
                    }
                }
            }
            paint.setShader(null);
        }
    }

    private static void setPaint(Paint paint, java.awt.Paint awt) {
        if (awt instanceof GradientPaint) {
            GradientPaint g = (GradientPaint) awt;
            paint.setColor(0xFF000000);
            GradientStyle style = GradientStyle.DEFAULT.withTileMode(g.isCyclic() ? FilterTileMode.MIRROR : FilterTileMode.CLAMP);
            Shader shader = Shader.makeLinearGradient((float) g.getPoint1().getX(), (float) g.getPoint1().getY(),
                    (float) g.getPoint2().getX(), (float) g.getPoint2().getY(),
                    new int[]{g.getColor1().getRGB(), g.getColor2().getRGB()}, null, style);
            paint.setShader(shader);
        } else {
            paint.setShader(null);
            paint.setColor(((Color) awt).getRGB());
        }
    }

    /** A Skia path made from a Java2D path, and how that path looked when it was made */
    private static final class Cached {
        Path path;
        int rule, version;
        java.awt.geom.Point2D current;
        java.awt.geom.Rectangle2D bounds;
    }

    /** Skia paths kept for the shapes, so an unchanged shape is not converted again on every redraw */
    private static final java.util.WeakHashMap<GeneralPath, Cached> paths = new java.util.WeakHashMap<GeneralPath, Cached>();

    /** The Skia path for a Java2D path - made again only if the path has changed */
    private static Path path(GeneralPath awt, int version) {
        Cached c = paths.get(awt);
        java.awt.geom.Point2D current = awt.getCurrentPoint();
        java.awt.geom.Rectangle2D bounds = awt.getBounds2D();
        int rule = awt.getWindingRule();
        if (c != null && c.version == version && c.rule == rule && java.util.Objects.equals(c.current, current) && c.bounds.equals(bounds)) {
            return c.path;
        }
        if (c == null) {
            c = new Cached();
            paths.put(awt, c);
        } else if (c.path != null) {
            c.path.close();
        }
        c.path = convert(awt);
        c.version = version;
        c.rule = rule;
        c.current = current;
        c.bounds = bounds;
        return c.path;
    }

    private static float[] points = new float[256];
    private static int[] verbs = new int[128];

    /** Convert in one go - the whole path handed over at once, not point by point */
    private static Path convert(GeneralPath awt) {
        PathIterator it = awt.getPathIterator(null);
        float[] q = new float[6];
        int np = 0, nv = 0;
        float moveX = 0, moveY = 0;
        boolean closed = false;
        while (!it.isDone()) {
            int type = it.currentSegment(q);
            if (np + 8 > points.length) {
                points = java.util.Arrays.copyOf(points, points.length * 2);
            }
            if (nv + 2 > verbs.length) {
                verbs = java.util.Arrays.copyOf(verbs, verbs.length * 2);
            }
            // After a close Java2D goes on from where the part began - Skia wants that said
            if (closed && type != PathIterator.SEG_MOVETO && type != PathIterator.SEG_CLOSE) {
                verbs[nv++] = 0;
                points[np++] = moveX;
                points[np++] = moveY;
            }
            closed = false;
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    verbs[nv++] = 0;
                    moveX = q[0];
                    moveY = q[1];
                    points[np++] = q[0];
                    points[np++] = q[1];
                    break;
                case PathIterator.SEG_LINETO:
                    verbs[nv++] = 1;
                    points[np++] = q[0];
                    points[np++] = q[1];
                    break;
                case PathIterator.SEG_QUADTO:
                    verbs[nv++] = 2;
                    System.arraycopy(q, 0, points, np, 4);
                    np += 4;
                    break;
                case PathIterator.SEG_CUBICTO:
                    verbs[nv++] = 4;
                    System.arraycopy(q, 0, points, np, 6);
                    np += 6;
                    break;
                default:
                    verbs[nv++] = 5;
                    closed = true;
            }
            it.next();
        }
        int fill = (awt.getWindingRule() == PathIterator.WIND_EVEN_ODD ? PathFillMode.EVEN_ODD : PathFillMode.WINDING).ordinal();
        return new Path(Path._nMakeRaw(java.util.Arrays.copyOf(points, np), java.util.Arrays.copyOf(verbs, nv), new float[0], fill, false));
    }
}
