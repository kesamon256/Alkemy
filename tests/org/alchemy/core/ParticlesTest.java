package org.alchemy.core;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;

/** Particles: every kind falls, lands, is kept as shapes, works with Mirror and is one undo step. */
public class ParticlesTest {

    static void stroke(final int x1, final int x2, final int y) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                AlcCanvas c = Alchemy.canvas;
                long t = System.currentTimeMillis();
                c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, t, 0, x1, y, 0, false, MouseEvent.NOBUTTON));
                c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, x1, y, 1, false, MouseEvent.BUTTON1));
                for (int i = 1; i <= 40; i++) {
                    c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i * (i > 30 ? 3 : 14), InputEvent.BUTTON1_DOWN_MASK,
                            x1 + (x2 - x1) * i / 40, y + (int) (12 * Math.sin(i / 5.0)), 0, false, MouseEvent.NOBUTTON));
                }
                c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 700, 0, x2, y, 1, false, MouseEvent.BUTTON1));
            }
        });
    }

    static int[] counts() throws Exception {
        final int[] n = new int[2];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                n[0] = Alchemy.canvas.shapes.size();
                n[1] = Alchemy.canvas.createShapes.size();
            }
        });
        return n;
    }

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        final Method[] select = new Method[1];
        final AlcModule[] module = new AlcModule[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Alchemy.window.setSize(1500, 900);
                    Alchemy.window.validate();
                    AlcCanvas c = Alchemy.canvas;
                    c.resetView();
                    c.setColor(new Color(20, 20, 30));
                    c.setLineWidth(8);
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    MarkerDisplaceTest.click("createButton", "Particles");
                    module[0] = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
                    UndoSmokeTest.check("Particles is in the Create menu (" + module[0].getName() + ")", module[0].getName().equals("Particles"));
                    StringBuilder on = new StringBuilder();
                    for (int i = 0; i < Alchemy.plugins.affects.length; i++) { if (Alchemy.plugins.currentAffects[i]) on.append(Alchemy.plugins.affects[i].getName()).append(" "); }
                    System.out.println("affects on: " + on);
                    select[0] = module[0].getClass().getDeclaredMethod("selectKind", int.class);
                    select[0].setAccessible(true);
                    c.clear();
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        String[] names = {"glitter", "sparks", "drips", "sand"};
        for (int k = 0; k < 4; k++) {
            final int kind = k;
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    try {
                        select[0].invoke(module[0], kind);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            int before = counts()[0];
            stroke(120 + (k % 2) * 650, 520 + (k % 2) * 650, 250 + (k / 2) * 330);
            int[] during = counts();
            Thread.sleep(3500);
            int[] after = counts();
            UndoSmokeTest.check(String.format("%s: falling after the pen lets go (%d moving), then kept (%d shapes, %d left moving)",
                    names[k], during[1], after[0] - before, after[1]), after[0] > before && after[1] == 0);
        }
        // One undo for a whole particle stroke, and Mirror doubles it
        final int[] base = counts();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.history.undo();
            }
        });
        int[] undone = counts();
        UndoSmokeTest.check("undo takes the last particle stroke back (" + base[0] + " -> " + undone[0] + ")", undone[0] < base[0]);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.history.redo();
                try {
                    select[0].invoke(module[0], 0);
                    MarkerDisplaceTest.click("affectButton", "Mirror");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        int before = counts()[0];
        stroke(560, 700, 760);
        Thread.sleep(3500);
        int added = counts()[0] - before;
        UndoSmokeTest.check("glitter with Mirror - every shape is doubled (" + added + ")", added >= 6 && added % 2 == 0);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    SketchTest.shot("particles");
                    MarkerDisplaceTest.click("affectButton", "Mirror");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
