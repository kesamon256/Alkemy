package org.alchemy.core;

import java.lang.reflect.Method;
import javax.swing.SwingUtilities;

/** Brushes -> Shapes through the Create menu, then draw with mouse and pen. */
public class SwitchTest {

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1280, 760);
                Alchemy.window.validate();
            }
        });
        Thread.sleep(600);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    AlcCanvas c = Alchemy.canvas;
                    c.setLineWidth(6);
                    c.setPressureEnabled(true);
                    MarkerDisplaceTest.click("createButton", "Brushes");
                    AlcModule brushes = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
                    Method select = brushes.getClass().getDeclaredMethod("selectBrush", int.class);
                    select.setAccessible(true);
                    select.invoke(brushes, 0);
                    BrushTest.stroke(100, 500, 200, false);
                    MarkerDisplaceTest.click("createButton", "Shapes");
                    AlcModule m = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
                    UndoSmokeTest.check("switched to Shapes (now " + m.getName() + ")", m.getName().equals("Shapes"));
                    int before = c.shapes.size();
                    BrushTest.stroke(100, 500, 350, false);
                    int mouse = c.shapes.size() - before;
                    before = c.shapes.size();
                    BrushTest.stroke(100, 500, 500, true);
                    int pen = c.shapes.size() - before;
                    UndoSmokeTest.check("Shapes draws one shape per stroke (mouse " + mouse + ", pen " + pen + ")", mouse == 1 && pen == 1);
                    Alchemy.toolBar.setToolBarVisible(true); Alchemy.toolBar.calculateTotalHeight(); Alchemy.toolBar.resizeToolBar(); SketchTest.shot("switch");
                } catch (Throwable t) {
                    t.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
