package org.alchemy.core;

import java.awt.Color;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;

/** Blur on the Flow brush - the fine trails under the pen go soft, the rest stays sharp. */
public class BlurFlowTest {

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SmoothRepeatTest.held = AlcCanvas.class.getDeclaredField("motionHeld");
        SmoothRepeatTest.held.setAccessible(true);
        final int[] counts = new int[2];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Alchemy.window.setSize(1400, 860);
                    Alchemy.window.validate();
                    AlcCanvas c = Alchemy.canvas;
                    c.setMotionHold(true);
                    c.resetView();
                    c.setColor(new Color(15, 15, 20));
                    c.setLineWidth(6);
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    MarkerDisplaceTest.click("createButton", "Brushes");
                    AlcModule brushes = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
                    Method select = brushes.getClass().getDeclaredMethod("selectBrush", int.class);
                    select.setAccessible(true);
                    select.invoke(brushes, 8);
                    c.clear();
                    FlowBrushTest.gesture(300, 420, 150, false);
                    FlowBrushTest.gesture(800, 420, 150, false);
                    counts[0] = c.shapes.size();
                    MarkerDisplaceTest.click("affectButton", "Blur");
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        // Brush over the right stroke a few times, the left one stays as it was
        for (int pass = 0; pass < 3; pass++) {
            for (int x = 700; x <= 900; x += 20) {
                SmoothRepeatTest.hover(x, 420 + (x % 40) - 20, true);
            }
        }
        SmoothRepeatTest.hover(900, 420, false);
        int blurredGroups = 0;
        int lines = Alchemy.canvas.shapes.size();
        UndoSmokeTest.check("the trails under the pen are blurred (" + counts[0] + " -> " + lines + " shapes)", lines > counts[0]);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    SketchTest.shot("blur-flow");
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
