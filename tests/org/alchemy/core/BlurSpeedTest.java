package org.alchemy.core;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;

/** Blur over charcoal (thousands of specks in a few shapes) must stay quick - time per pass and per hover. */
public class BlurSpeedTest {

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SmoothRepeatTest.held = AlcCanvas.class.getDeclaredField("motionHeld");
        SmoothRepeatTest.held.setAccessible(true);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Alchemy.window.setSize(1400, 860);
                    Alchemy.window.validate();
                    AlcCanvas c = Alchemy.canvas;
                    c.setMotionHold(true);
                    c.resetView();
                    c.setColor(new Color(20, 20, 25));
                    c.setLineWidth(10);
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    MarkerDisplaceTest.click("createButton", "Brushes");
                    AlcModule brushes = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
                    Method select = brushes.getClass().getDeclaredMethod("selectBrush", int.class);
                    select.setAccessible(true);
                    select.invoke(brushes, 3);
                    c.clear();
                    for (int k = 0; k < 4; k++) {
                        BrushTest.stroke(150, 1000, 200 + k * 120, false);
                    }
                    MarkerDisplaceTest.click("affectButton", "Blur");
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        // Time single hover passes over the charcoal, straight on the event thread
        final long[] worst = new long[1];
        final long[] total = new long[1];
        for (int i = 0; i < 12; i++) {
            final int x = 300 + i * 50;
            Thread.sleep(90);
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    try {
                        SmoothRepeatTest.held.setBoolean(Alchemy.canvas, true);
                        AlcCanvas c = Alchemy.canvas;
                        long t = System.nanoTime();
                        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, 330, 0, false, MouseEvent.NOBUTTON));
                        long ms = (System.nanoTime() - t) / 1000000;
                        // The first pass also warms Java up - it is left out
                        if (x > 300) {
                            worst[0] = Math.max(worst[0], ms);
                            total[0] += ms;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        SmoothRepeatTest.hover(900, 330, false);
        System.out.println("blur over charcoal: worst " + worst[0] + " ms, average " + (total[0] / 11) + " ms per hover");
        UndoSmokeTest.check("blur over charcoal is quick (average " + (total[0] / 11) + " ms, worst " + worst[0] + " ms)", total[0] / 11 < 120 && worst[0] < 250);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    SketchTest.shot("blur-charcoal");
                    MarkerDisplaceTest.click("affectButton", "Blur");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
