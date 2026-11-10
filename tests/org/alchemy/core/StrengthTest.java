package org.alchemy.core;

import java.awt.Color;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;

/** The pressure strength sliders: 0 % no change, more % a stronger change of size and opacity. */
public class StrengthTest {

    /** Width of the ink line at the light start and at the firm middle of a ramp stroke */
    static double[] inkWidths(int y) {
        AlcCanvas c = Alchemy.canvas;
        int before = c.shapes.size();
        OpacityPressureTest.rampStroke(80, 1000, y);
        double light = 0, firm = 0;
        for (AlcShape s : c.shapes.subList(before, c.shapes.size())) {
            light = Math.max(light, ShapesPressureTest.height(s, 110));
            firm = Math.max(firm, ShapesPressureTest.height(s, 540));
        }
        return new double[]{light, firm};
    }

    static int[] alphas(int y) {
        AlcCanvas c = Alchemy.canvas;
        int before = c.shapes.size();
        OpacityPressureTest.rampStroke(80, 1000, y);
        int lo = 255, hi = 0;
        for (AlcShape s : c.shapes.subList(before, c.shapes.size())) {
            lo = Math.min(lo, s.getAlpha());
            hi = Math.max(hi, s.getAlpha());
        }
        return new int[]{lo, hi};
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
                boolean oldSize = c.isPressureSize(), oldOp = c.isPressureOpacity();
                float oldSS = c.getPressureSizeStrength(), oldOS = c.getPressureOpacityStrength();
                try {
                    c.setColor(new Color(20, 20, 30));
                    c.setLineWidth(12);
                    c.setPressureEnabled(true);
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    MarkerDisplaceTest.click("createButton", "Brushes");
                    AlcModule brushes = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
                    Method select = brushes.getClass().getDeclaredMethod("selectBrush", int.class);
                    select.setAccessible(true);
                    select.invoke(brushes, 1);
                    c.clear();
                    c.setPressureEffects(true, false);
                    c.setPressureSizeStrength(0F);
                    double[] none = inkWidths(150);
                    c.setPressureSizeStrength(1F);
                    double[] normal = inkWidths(250);
                    c.setPressureSizeStrength(2.5F);
                    double[] strong = inkWidths(350);
                    UndoSmokeTest.check(String.format("size 0%%: same width (%.0f / %.0f)", none[0], none[1]), Math.abs(none[0] - none[1]) <= 3);
                    UndoSmokeTest.check(String.format("size 100%%: %.0f -> %.0f, 250%%: %.0f -> %.0f", normal[0], normal[1], strong[0], strong[1]),
                            strong[1] / Math.max(1, strong[0]) > normal[1] / Math.max(1, normal[0]) && strong[1] > normal[1]);
                    c.setPressureEffects(false, true);
                    c.setPressureOpacityStrength(0F);
                    int[] flat = alphas(500);
                    c.setPressureOpacityStrength(1F);
                    int[] mid = alphas(580);
                    c.setPressureOpacityStrength(2.5F);
                    int[] fade = alphas(660);
                    UndoSmokeTest.check(String.format("opacity 0%%: %d..%d, 100%%: %d..%d, 250%%: %d..%d", flat[0], flat[1], mid[0], mid[1], fade[0], fade[1]),
                            flat[0] == flat[1] && fade[0] <= mid[0] && fade[1] == 255);
                    c.setPressureSizeStrength(1.6F);
                    c.setPressureOpacityStrength(1.5F);
                    c.setPressureEffects(true, true);
                    OpacityPressureTest.rampStroke(80, 1000, 780);
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                } finally {
                    c.setPressureEffects(oldSize, oldOp);
                    c.setPressureSizeStrength(oldSS);
                    c.setPressureOpacityStrength(oldOS);
                }
            }
        });
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1900, 900);
                Alchemy.window.validate();
                Alchemy.toolBar.setToolBarVisible(true);
            }
        });
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    SketchTest.shot("pressure-strength");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
