package org.alchemy.core;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;

/** The Flow brush: a swarm of fine trails around the pen, kept as shapes, mirrored and undoable. */
public class FlowBrushTest {

    /** A looping stroke like a quick gesture */
    static void gesture(int cx, int cy, int r, boolean pen) {
        AlcCanvas c = Alchemy.canvas;
        long t = System.currentTimeMillis();
        if (pen) {
            AlcWindowsInk.update(true, false, true, 0.2F, 0, 0);
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, cx, cy, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 90; i++) {
            double a = i / 90.0 * Math.PI * 3;
            int x = cx + (int) Math.round(Math.sin(a) * r * (0.4 + i / 150.0));
            int y = cy + (int) Math.round(Math.sin(a * 1.7) * r * 0.6);
            if (pen) {
                float s = (float) Math.sin(Math.PI * i / 90.0);
                AlcWindowsInk.update(true, false, true, 0.1F + 0.9F * s * s, 0, 0);
            }
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i * 12, InputEvent.BUTTON1_DOWN_MASK, x, y, 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 1200, 0, cx, cy, 1, false, MouseEvent.BUTTON1));
        if (pen) {
            AlcWindowsInk.update(false, false, false, 0, 0, 0);
        }
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
        Thread.sleep(600);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                AlcCanvas c = Alchemy.canvas;
                try {
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
                    long start = System.nanoTime();
                    gesture(350, 420, 160, false);
                    long ms = (System.nanoTime() - start) / 1000000;
                    int first = c.shapes.size();
                    UndoSmokeTest.check("a flow stroke leaves many fine trails (" + first + " shapes, " + ms + " ms)", first > 60 && c.createShapes.isEmpty());
                    gesture(800, 420, 140, true);
                    UndoSmokeTest.check("with a pen too (" + (c.shapes.size() - first) + " shapes)", c.shapes.size() - first > 60);
                    Alchemy.history.undo();
                    UndoSmokeTest.check("one undo takes the whole stroke back", c.shapes.size() == first);
                    Alchemy.history.redo();
                    // Mirror: every trail gets its mirror image
                    MarkerDisplaceTest.click("affectButton", "Mirror");
                    int before = c.shapes.size();
                    gesture(1050, 650, 80, false);
                    int added = c.shapes.size() - before;
                    UndoSmokeTest.check("with mirror every trail is doubled (" + added + ")", added > 60 && added % 2 == 0);
                    MarkerDisplaceTest.click("affectButton", "Mirror");
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
                    SketchTest.shot("flow-brush");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
