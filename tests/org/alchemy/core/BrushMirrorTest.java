package org.alchemy.core;

import java.awt.Color;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;

/** Brushes with Mirror on, as outline and as Solid - the mirrored side must match. */
public class BrushMirrorTest {

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1500, 860);
                Alchemy.window.validate();
            }
        });
        Thread.sleep(500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    AlcCanvas c = Alchemy.canvas;
                    c.setColor(new Color(40, 60, 150));
                    c.setLineWidth(5);
                    MarkerDisplaceTest.click("createButton", "Brushes");
                    MarkerDisplaceTest.click("affectButton", "Mirror");
                    AlcModule brushes = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
                    Method select = brushes.getClass().getDeclaredMethod("selectBrush", int.class);
                    select.setAccessible(true);
                    int[] picks = {0, 2, 3, 5, 7};
                    for (int row = 0; row < 2; row++) {
                        c.setStyleMode(row == 0 ? AlcCanvas.STYLE_MODE_STROKE : AlcCanvas.STYLE_MODE_FILL);
                        for (int i = 0; i < picks.length; i++) {
                            select.invoke(brushes, picks[i]);
                            int before = c.shapes.size();
                            StyleTest.loop(120 + i * 110, 220 + row * 300, 45);
                            System.out.println((row == 0 ? "outline" : "solid") + " brush " + picks[i] + " added " + (c.shapes.size() - before) + " shapes");
                        }
                    }
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    SketchTest.shot("brush-mirror");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.exit(0);
    }
}
