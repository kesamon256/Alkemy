package org.alchemy.core;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;

/** The opacity pressure mode: a light touch fades out, a firm one is full - the size stays the same. */
public class OpacityPressureTest implements AlcConstants {

    /** A stroke whose pressure rises from faint to full and falls again */
    static void rampStroke(int x1, int x2, int y) {
        AlcCanvas c = Alchemy.canvas;
        long t = System.currentTimeMillis();
        AlcWindowsInk.update(true, false, true, 0.02F, 0, 0);
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, t, 0, x1, y, 0, false, MouseEvent.NOBUTTON));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, x1, y, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 100; i++) {
            float s = (float) Math.sin(Math.PI * i / 100.0);
            AlcWindowsInk.update(true, false, true, 0.02F + 0.98F * s * s, 0, 0);
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i * 10, InputEvent.BUTTON1_DOWN_MASK,
                    x1 + (x2 - x1) * i / 100, y + (int) Math.round(8 * Math.sin(i / 9.0)), 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 1100, 0, x2, y, 1, false, MouseEvent.BUTTON1));
        AlcWindowsInk.update(false, false, false, 0, 0, 0);
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, t + 1110, 0, x2 + 5, y, 0, false, MouseEvent.NOBUTTON));
    }

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1500, 900);
                Alchemy.window.validate();
            }
        });
        Thread.sleep(500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                AlcCanvas c = Alchemy.canvas;
                boolean oldSize = c.isPressureSize(), oldOp = c.isPressureOpacity(); float oldSS = c.getPressureSizeStrength(), oldOS = c.getPressureOpacityStrength();
                try {
                    c.setColor(new Color(20, 20, 30));
                    c.setLineWidth(8);
                    c.setPressureEnabled(true);
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    MarkerDisplaceTest.click("createButton", "Brushes");
                    AlcModule brushes = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
                    Method select = brushes.getClass().getDeclaredMethod("selectBrush", int.class);
                    select.setAccessible(true);
                    String[] names = {"pencil", "ink", "marker", "charcoal", "liner", "brush pen", "sketchy", "hatching"};
                    for (int i = 0; i < names.length; i++) {
                        select.invoke(brushes, i);
                        // Size mode on the left, opacity mode on the right
                        c.setPressureMode(AlcCanvas.PRESSURE_SIZE);
                        rampStroke(80, 520, 170 + i * 82);
                        c.setPressureMode(AlcCanvas.PRESSURE_OPACITY);
                        int before = c.shapes.size();
                        rampStroke(600, 1040, 170 + i * 82);
                        int lowest = 255, highest = 0;
                        for (AlcShape s : c.shapes.subList(before, c.shapes.size())) {
                            lowest = Math.min(lowest, s.getAlpha());
                            highest = Math.max(highest, s.getAlpha());
                        }
                        UndoSmokeTest.check(String.format("%s fades with a light touch (opacity %d .. %d in %d shapes)", names[i], lowest, highest, c.shapes.size() - before),
                                c.shapes.size() - before >= 3 && lowest * 3 < highest);
                    }
                    // Ink keeps its width in the opacity mode
                    select.invoke(brushes, 1);
                    int before = c.shapes.size();
                    rampStroke(80, 520, 840);
                    float wMin = 99, wMax = 0;
                    for (AlcShape s : c.shapes.subList(before, c.shapes.size())) {
                        java.util.ArrayList<Float> w = s.getSpineWidth();
                        if (w != null) {
                            for (int k = 0; k < w.size(); k++) {
                                // The middle of the stroke - the tapered ends aside
                                float x = s.getSpine().get(k).x;
                                if (x > 200 && x < 400) {
                                    wMin = Math.min(wMin, w.get(k));
                                    wMax = Math.max(wMax, w.get(k));
                                }
                            }
                        }
                    }
                    UndoSmokeTest.check(String.format("the size stays the same in the opacity mode (ink %.1f .. %.1f)", wMin, wMax), wMax - wMin < 0.6);
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                } finally {
                    c.setPressureEffects(oldSize, oldOp); c.setPressureSizeStrength(oldSS); c.setPressureOpacityStrength(oldOS);
                }
            }
        });
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    SketchTest.shot("pressure-modes");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
