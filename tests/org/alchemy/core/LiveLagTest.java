package org.alchemy.core;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;

/**
 * How long each frame of a long stroke takes - the pen event and painting the canvas - with
 * several brushes, at the start of the stroke and near its end. A stroke that gets slower as it
 * grows, or single slow frames, are what make the line lag behind the pen or jerk.
 * Run with -Dalchemy.noGpu=true to compare with Java2D. -Dbrushes=0,3,8 / -Dpoints=600.
 */
public class LiveLagTest {

    static String result(String name, long[] frames, int n) {
        int part = Math.max(1, n / 5);
        long first = 0, last = 0, worst = 0;
        int over16 = 0, over33 = 0;
        for (int i = 0; i < n; i++) {
            long us = frames[i];
            if (i < part) {
                first += us;
            }
            if (i >= n - part) {
                last += us;
            }
            worst = Math.max(worst, us);
            over16 += (us > 16000) ? 1 : 0;
            over33 += (us > 33000) ? 1 : 0;
        }
        return String.format("%-10s start %5.1f ms, end %5.1f ms, worst %5.1f ms, frames >16 ms: %d, >33 ms: %d",
                name, first / 1000.0 / part, last / 1000.0 / part, worst / 1000.0, over16, over33);
    }

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        final int points = Integer.getInteger("points", 600);
        String[] list = System.getProperty("brushes", "0,1,3,8,shapes").split(",");
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1400, 860);
                Alchemy.window.validate();
                Alchemy.canvas.resetView();
            }
        });
        StringBuilder report = new StringBuilder();
        final String[] liveCount = new String[1];
        final String[] changes = new String[1];
        final boolean[] gradientOn = new boolean[1];
        @SuppressWarnings("unchecked")
        final java.util.Map<AlcShape, String>[] snapshot = new java.util.Map[1];
        for (final String brush : list) {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    try {
                        AlcCanvas c = Alchemy.canvas;
                        c.clear();
                        c.setColor(new Color(25, 25, 30));
                        c.setLineWidth(6);
                        c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                        if (brush.equals("shapes")) {
                            MarkerDisplaceTest.click("createButton", "Shapes");
                        } else {
                            MarkerDisplaceTest.click("createButton", "Brushes");
                            AlcModule brushes = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
                            Method select = brushes.getClass().getDeclaredMethod("selectBrush", int.class);
                            select.setAccessible(true);
                            select.invoke(brushes, Integer.parseInt(brush));
                        }
                        // Something already drawn underneath, like a real sketch
                        java.util.Random r = new java.util.Random(3);
                        for (int i = 0; i < 150; i++) {
                            java.awt.geom.GeneralPath p = new java.awt.geom.GeneralPath();
                            double x = r.nextInt(1000), y = r.nextInt(800);
                            p.moveTo(x, y);
                            for (int k = 0; k < 40; k++) {
                                x += r.nextGaussian() * 10;
                                y += r.nextGaussian() * 10;
                                p.lineTo(x, y);
                            }
                            c.shapes.add(new AlcShape(p, new Color(r.nextInt(200), r.nextInt(200), r.nextInt(200)), 200, AlcConstants.STYLE_STROKE, 1 + r.nextInt(8)));
                        }
                        c.redraw(true);
                        // With the gradient along the stroke switched on
                        if (Boolean.getBoolean("gradient") && !gradientOn[0]) {
                            MarkerDisplaceTest.click("affectButton", "Gradient");
                            gradientOn[0] = true;
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            });
            final long[] frames = new long[points];
            final long[] events = new long[points];
            final long t0 = System.currentTimeMillis();
            for (int i = 0; i <= points; i++) {
                final int n = i;
                SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() {
                        AlcCanvas c = Alchemy.canvas;
                        // A loopy scribble over the canvas, like quick sketching
                        double a = n * 0.05;
                        int x = (int) (520 + 380 * Math.sin(a * 0.7) + 60 * Math.sin(a * 5));
                        int y = (int) (420 + 300 * Math.sin(a * 0.45 + 1) + 60 * Math.cos(a * 4));
                        float pressure = 0.3F + 0.6F * (float) Math.abs(Math.sin(a));
                        AlcWindowsInk.update(true, false, n > 0, pressure, 0, 0);
                        long start = System.nanoTime();
                        if (n == 0) {
                            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, t0, 0, x, y, 0, false, MouseEvent.NOBUTTON));
                            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t0, InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
                        } else {
                            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t0 + n * 8, InputEvent.BUTTON1_DOWN_MASK, x, y, 0, false, MouseEvent.NOBUTTON));
                        }
                        long mid = System.nanoTime();
                        if (n == points - 50) {
                            // Which live shapes change from one move to the next
                            java.util.List<AlcShape> live = c.createShapes;
                            java.util.Map<AlcShape, String> before = new java.util.IdentityHashMap<AlcShape, String>();
                            for (AlcShape s : live) {
                                before.put(s, s.getPath().getBounds2D() + "" + s.getPath().getCurrentPoint() + s.getAlpha() + s.getLineWidth());
                            }
                            snapshot[0] = before;
                        } else if (n == points - 49 && snapshot[0] != null) {
                            java.util.List<AlcShape> live = c.createShapes;
                            int changed = 0, added = 0, first = -1;
                            for (int k = 0; k < live.size(); k++) {
                                AlcShape s = live.get(k);
                                String old = snapshot[0].get(s);
                                if (old == null) {
                                    added++;
                                } else if (!old.equals(s.getPath().getBounds2D() + "" + s.getPath().getCurrentPoint() + s.getAlpha() + s.getLineWidth())) {
                                    changed++;
                                } else {
                                    continue;
                                }
                                if (first < 0) {
                                    first = k;
                                }
                            }
                            java.util.Set<Integer> colors = new java.util.HashSet<Integer>();
                            for (AlcShape s : live) {
                                colors.add(s.getColor().getRGB() & 0xFFFFFF);
                            }
                            changes[0] = "restarts " + AlcSkia.liveRestarts + ", kept as a picture " + AlcSkia.liveKept + ", " + live.size() + " shapes, " + changed + " changed, " + added + " new, first change at " + first + ", " + colors.size() + " colours";
                        }
                        c.paintImmediately(c.getVisibleRect());
                        if (n > 0) {
                            frames[n - 1] = (System.nanoTime() - start) / 1000;
                            if (frames[n - 1] > 16000 && Boolean.getBoolean("slowFrames")) {
                                System.out.println("  slow frame " + n + ": " + frames[n - 1] / 1000 + " ms, restarts " + AlcSkia.liveRestarts + ", kept " + AlcSkia.liveKept + ", live " + c.createShapes.size());
                            }
                            events[n - 1] = (mid - start) / 1000;
                        }
                    }
                });
            }
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    AlcCanvas c = Alchemy.canvas;
                    // The picture made with the kept start must be the same as the stroke drawn in full
                    if (!Boolean.getBoolean("alchemy.noGpu")) {
                        java.awt.geom.AffineTransform t = c.getViewTransform();
                        int w = c.getWidth(), h = c.getHeight();
                        java.awt.image.BufferedImage kept = AlcSkia.drawLive(w, h, t, c.activeShapeList, true);
                        int[] a = kept.getRGB(0, 0, w, h, null, 0, w);
                        java.awt.Rectangle ra = new java.awt.Rectangle(AlcSkia.liveRegion);
                        AlcSkia.forgetLive();
                        java.awt.image.BufferedImage full = AlcSkia.drawLive(w, h, t, c.activeShapeList, true);
                        int[] b = full.getRGB(0, 0, w, h, null, 0, w);
                        int worst = 0, off = 0;
                        for (int y = ra.y; y < ra.y + ra.height; y++) {
                            for (int x = ra.x; x < ra.x + ra.width; x++) {
                                int p = a[y * w + x], q = b[y * w + x];
                                int d = 0;
                                for (int sh = 0; sh < 32; sh += 8) {
                                    d = Math.max(d, Math.abs(((p >> sh) & 255) - ((q >> sh) & 255)));
                                }
                                worst = Math.max(worst, d);
                                off += (d > 8) ? 1 : 0;
                            }
                        }
                        UndoSmokeTest.check(brush + ": kept start looks the same as the full stroke (worst " + worst + ", pixels off " + off + ")", off < 50);
                    }
                    liveCount[0] = c.createShapes.size() + "+" + c.affectShapes.size() + " shapes, " + segments(c.createShapes) + " segments";
                    c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t0 + points * 8 + 10, 0, 500, 400, 1, false, MouseEvent.BUTTON1));
                    AlcWindowsInk.update(false, false, false, 0, 0, 0);
                }
            });
            Thread.sleep(300);
            String line = result(brush.equals("shapes") ? "Shapes" : brushName(Integer.parseInt(brush)), frames, points)
                    + "\n" + result("  (event)", events, points) + "\n  live shapes at the end: " + liveCount[0] + "\n  one move: " + changes[0];
            System.out.println(line);
            report.append(line).append('\n');
        }
        if (gradientOn[0]) {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    try {
                        MarkerDisplaceTest.click("affectButton", "Gradient");
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        System.out.println((Boolean.getBoolean("alchemy.noGpu") ? "Java2D" : AlcSkia.status) + "\n" + report);
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    static int segments(java.util.List<AlcShape> list) {
        int n = 0;
        for (AlcShape s : list) {
            for (java.awt.geom.PathIterator it = s.getPath().getPathIterator(null); !it.isDone(); it.next()) {
                n++;
            }
        }
        return n;
    }

    static String brushName(int i) {
        String[] names = {"Pencil", "Ink", "Marker", "Charcoal", "Liner", "Brush pen", "Sketchy", "Hatching", "Flow"};
        return names[i];
    }
}
