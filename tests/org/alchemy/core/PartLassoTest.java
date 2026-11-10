package org.alchemy.core;

import java.awt.Color;
import java.awt.event.KeyEvent;
import javax.swing.SwingUtilities;

/** Lasso selecting part of a stroke: cut, move, delete a gap, undo. */
public class PartLassoTest {

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
                    test();
                } catch (Throwable t) {
                    t.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    static boolean red(int x, int y) {
        Color c = LayerTest.pixel(x, y);
        return c.getRed() > 200 && c.getGreen() < 80;
    }

    static boolean white(int x, int y) {
        return LayerTest.pixel(x, y).equals(Color.WHITE);
    }

    static void test() throws Exception {
        AlcCanvas c = Alchemy.canvas;
        AlcHistory h = Alchemy.history;
        AlcSelectionTool tool = c.getSelectionTool();
        c.setLineWidth(20);
        c.setColor(Color.RED);
        LayerTest.line(200, 300, 600, 300);
        UndoSmokeTest.check("one red stroke", c.shapes.size() == 1 && red(300, 300) && red(500, 300));

        c.setSelecting(true);
        LassoTest.loop(400, 250, 700, 350, 0);
        UndoSmokeTest.check("loop over half the stroke selects a part (" + tool.getPartlySelectedCount() + " cut, " + tool.getSelected().size() + " whole)",
                tool.getPartlySelectedCount() == 1 && tool.getSelected().isEmpty());
        UndoSmokeTest.check("selecting alone does not change the drawing", c.shapes.size() == 1);
        SketchTest.shot("part-lasso-1-selected");

        // Move the right half down
        LassoTest.drag(500, 300, 500, 400, 0);
        UndoSmokeTest.check("moving cuts the stroke in two (" + c.shapes.size() + " shapes)", c.shapes.size() == 2);
        UndoSmokeTest.check("left half stays", red(300, 300));
        UndoSmokeTest.check("right half moved away", white(500, 300) && red(500, 400));
        UndoSmokeTest.check("cut is at the selection edge", red(390, 300) && white(410, 300) && red(410, 400));
        SketchTest.shot("part-lasso-2-moved");
        h.undo();
        UndoSmokeTest.check("undo puts the whole stroke back", c.shapes.size() == 1 && red(500, 300) && white(500, 400));

        // Delete a gap in the middle
        LassoTest.loop(350, 250, 450, 350, 0);
        LassoTest.key(KeyEvent.VK_DELETE);
        UndoSmokeTest.check("Delete cuts a gap", white(400, 300) && red(300, 300) && red(500, 300) && c.shapes.size() == 1);
        h.undo();
        UndoSmokeTest.check("undo fills the gap", red(400, 300) && c.shapes.size() == 1);

        // A loop around the whole stroke still selects it whole, with no cutting
        LassoTest.loop(150, 250, 650, 350, 0);
        LassoTest.drag(400, 300, 400, 350, 0);
        UndoSmokeTest.check("whole stroke moves without being cut", c.shapes.size() == 1 && c.shapes.get(0).getStyle() == AlcConstants.STYLE_STROKE && red(400, 350));
        c.setSelecting(false);
    }
}
