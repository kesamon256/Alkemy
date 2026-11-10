package org.alchemy.core;

import java.awt.Color;
import java.awt.Rectangle;
import javax.swing.SwingUtilities;

/** Pen pressure in the plain Shapes tool: a strong size change, and the opacity mode. */
public class ShapesPressureTest {

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
                    c.setLineWidth(10);
                    c.setPressureEnabled(true);
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    MarkerDisplaceTest.click("createButton", "Shapes");
                    c.clear();
                    // SIZE
                    c.setPressureMode(AlcCanvas.PRESSURE_SIZE);
                    int before = c.shapes.size();
                    OpacityPressureTest.rampStroke(80, 1000, 250);
                    AlcShape s = c.shapes.get(c.shapes.size() - 1);
                    // Height of the line near the light start vs at the firm middle
                    Rectangle start = new Rectangle(100, 150, 12, 200), middle = new Rectangle(530, 150, 12, 200);
                    double thin = height(s, 108), thick = height(s, 536);
                    UndoSmokeTest.check(String.format("size mode: one shape (%d), light %.1f px vs firm %.1f px", c.shapes.size() - before, thin, thick),
                            c.shapes.size() - before == 1 && thick > thin * 3 && thick > 20);
                    // OPACITY
                    c.setPressureMode(AlcCanvas.PRESSURE_OPACITY);
                    before = c.shapes.size();
                    OpacityPressureTest.rampStroke(80, 1000, 500);
                    int lowest = 255, highest = 0;
                    for (AlcShape p : c.shapes.subList(before, c.shapes.size())) {
                        lowest = Math.min(lowest, p.getAlpha());
                        highest = Math.max(highest, p.getAlpha());
                    }
                    UndoSmokeTest.check(String.format("opacity mode: %d pieces, opacity %d .. %d", c.shapes.size() - before, lowest, highest),
                            c.shapes.size() - before >= 5 && lowest * 4 < highest);
                    double h1 = height(c.shapes.get(before), 100), h2 = height(c.shapes.get(c.shapes.size() / 2 + before / 2), 530);
                    // BOTH - pieces that fade and a width that follows the pressure
                    c.setPressureEffects(true, true);
                    before = c.shapes.size();
                    OpacityPressureTest.rampStroke(80, 1000, 380);
                    lowest = 255;
                    highest = 0;
                    double thinBoth = 99, thickBoth = 0;
                    for (AlcShape p : c.shapes.subList(before, c.shapes.size())) {
                        lowest = Math.min(lowest, p.getAlpha());
                        highest = Math.max(highest, p.getAlpha());
                        java.awt.Rectangle b = p.getBounds();
                        double hh = height(p, (int) b.getCenterX());
                        if (hh > 0) {
                            thinBoth = Math.min(thinBoth, hh);
                            thickBoth = Math.max(thickBoth, hh);
                        }
                    }
                    UndoSmokeTest.check(String.format("both: opacity %d .. %d, width %.0f .. %.0f px", lowest, highest, thinBoth, thickBoth),
                            lowest * 4 < highest && thickBoth > thinBoth * 3);
                    // WITH MIRROR - the stroke and its mirror image are kept when the pen lifts
                    MarkerDisplaceTest.click("affectButton", "Mirror");
                    for (boolean[] m : new boolean[][]{{true, false}, {false, true}, {true, true}}) {
                        c.setPressureEffects(m[0], m[1]);
                        before = c.shapes.size();
                        OpacityPressureTest.rampStroke(560, 700, 620);
                        UndoSmokeTest.check(String.format("mirror size=%b opacity=%b: stroke kept (%d new shapes, %d still open)", m[0], m[1],
                                c.shapes.size() - before, c.createShapes.size()), c.shapes.size() - before >= 2 && c.createShapes.isEmpty());
                    }
                    MarkerDisplaceTest.click("affectButton", "Mirror");
                    // mouse still plain
                    c.setPressureMode(AlcCanvas.PRESSURE_SIZE);
                    UndoSmokeTest.stroke(80, 700, 1000, 720);
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
                    SketchTest.shot("shapes-pressure");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    /** Height of the shape's outline at a column */
    static double height(AlcShape s, int x) {
        int top = -1, bottom = -1;
        for (int y = 0; y < 900; y++) {
            if (s.getPath().contains(x + 0.5, y + 0.5)) {
                if (top < 0) {
                    top = y;
                }
                bottom = y;
            }
        }
        return top < 0 ? 0 : bottom - top + 1;
    }
}
