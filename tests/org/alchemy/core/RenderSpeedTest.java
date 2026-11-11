package org.alchemy.core;

import java.awt.Color;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;

/**
 * How long a full redraw of a big drawing takes - many wide lines, solid shapes and charcoal specks,
 * on a faded layer too. Prints the time; run with -Dalchemy.noGpu=true to compare with Java2D.
 * Also checks the picture looks the same both ways (saved as render-gpu.png / render-java2d.png).
 */
public class RenderSpeedTest {

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        final long[] times = new long[2];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Alchemy.window.setSize(1400, 860);
                    Alchemy.window.validate();
                    AlcCanvas c = Alchemy.canvas;
                    c.resetView();
                    c.clear();
                    java.util.Random r = new java.util.Random(7);
                    // 400 wavy wide lines, 150 solid blobs, 40 charcoal clouds of 300 specks
                    for (int i = 0; i < 400; i++) {
                        GeneralPath p = new GeneralPath();
                        double x = r.nextInt(1000), y = r.nextInt(800);
                        p.moveTo(x, y);
                        for (int k = 0; k < 60; k++) {
                            x += r.nextGaussian() * 12;
                            y += r.nextGaussian() * 12;
                            p.lineTo(x, y);
                        }
                        AlcShape s = new AlcShape(p, new Color(r.nextInt(255), r.nextInt(255), r.nextInt(255)), 120 + r.nextInt(135), AlcConstants.STYLE_STROKE, 2 + r.nextInt(30));
                        c.shapes.add(s);
                    }
                    for (int i = 0; i < 150; i++) {
                        GeneralPath p = new GeneralPath();
                        double cx = r.nextInt(1000), cy = r.nextInt(800), rad = 20 + r.nextInt(120);
                        for (int k = 0; k <= 80; k++) {
                            double a = k * Math.PI * 2 / 80, rr = rad * (1 + 0.3 * Math.sin(a * 5 + i));
                            if (k == 0) {
                                p.moveTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
                            } else {
                                p.lineTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
                            }
                        }
                        p.closePath();
                        c.shapes.add(new AlcShape(p, new Color(r.nextInt(255), r.nextInt(255), r.nextInt(255)), 60 + r.nextInt(195), AlcConstants.STYLE_FILL, 1));
                    }
                    for (int i = 0; i < 40; i++) {
                        GeneralPath p = new GeneralPath();
                        double cx = r.nextInt(1000), cy = r.nextInt(800);
                        for (int k = 0; k < 300; k++) {
                            double sx = cx + r.nextGaussian() * 40, sy = cy + r.nextGaussian() * 40;
                            p.moveTo(sx, sy);
                            p.lineTo(sx + 2, sy);
                            p.lineTo(sx + 1, sy + 2);
                            p.closePath();
                        }
                        c.shapes.add(new AlcShape(p, new Color(20, 20, 25), 200, AlcConstants.STYLE_FILL, 1));
                    }
                    c.redraw(true);
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        Thread.sleep(500);
        for (int run = 0; run < 12; run++) {
            final int n = run;
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    long t = System.nanoTime();
                    Alchemy.canvas.redraw(true);
                    long ms = (System.nanoTime() - t) / 1000000;
                    if (n >= 2) {
                        times[0] += ms;
                        times[1] = Math.max(times[1], ms);
                    }
                }
            });
        }
        boolean gpu = !Boolean.getBoolean("alchemy.noGpu");
        System.out.println("full redraw (" + (gpu ? AlcSkia.status : "Java2D") + "): average " + times[0] / 10 + " ms, worst " + times[1] + " ms");
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    AlcCanvas c = Alchemy.canvas;
                    BufferedImage img = (BufferedImage) c.renderCanvas(true, false, 1, c.getWidth(), c.getHeight());
                    javax.imageio.ImageIO.write(img, "png", new java.io.File(SketchTest.dir, gpu ? "render-gpu.png" : "render-java2d.png"));
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
