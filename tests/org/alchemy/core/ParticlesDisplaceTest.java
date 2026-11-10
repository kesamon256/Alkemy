package org.alchemy.core;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;

/** Displace keeps working while the particles fall, with a noise that drifts - every stroke differs. */
public class ParticlesDisplaceTest {

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        final Object[] held = new Object[3];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Alchemy.window.setSize(1500, 900);
                    Alchemy.window.validate();
                    AlcCanvas c = Alchemy.canvas;
                    c.resetView();
                    c.setColor(new Color(20, 20, 30));
                    c.setLineWidth(8);
                    c.clear();
                    MarkerDisplaceTest.click("createButton", "Particles");
                    AlcModule particles = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
                    Method select = particles.getClass().getDeclaredMethod("selectKind", int.class);
                    select.setAccessible(true);
                    select.invoke(particles, 1);
                    MarkerDisplaceTest.click("affectButton", "Displace");
                    for (AlcModule m : Alchemy.plugins.affects) {
                        if (m.getName().equals("Displace")) {
                            held[0] = m;
                        }
                    }
                    Field stable = held[0].getClass().getDeclaredField("stable");
                    stable.setAccessible(true);
                    stable.setBoolean(held[0], true);
                    Field kind = held[0].getClass().getDeclaredField("noiseKind");
                    kind.setAccessible(true);
                    kind.setInt(held[0], 0);
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        ParticlesTest.stroke(300, 700, 300);
        final Field liveZ = held[0].getClass().getDeclaredField("liveZ");
        liveZ.setAccessible(true);
        final double[] z = new double[2];
        final boolean[] animating = new boolean[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    z[0] = liveZ.getDouble(held[0]);
                    animating[0] = Alchemy.canvas.isCreateAnimating();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    z[1] = liveZ.getDouble(held[0]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        int[] after = ParticlesTest.counts();
        UndoSmokeTest.check(String.format("sparks are still moving after the pen lets go (%b)", animating[0]), animating[0]);
        UndoSmokeTest.check(String.format("Displace keeps working while they fall (noise moved %.2f -> %.2f)", z[0], z[1]), z[1] > z[0] + 0.1);
        UndoSmokeTest.check("then they are kept (" + after[1] + " left moving)", after[1] == 0 && !Alchemy.canvas.isCreateAnimating());
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    SketchTest.shot("particles-displace");
                    MarkerDisplaceTest.click("affectButton", "Displace");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
