package org.alchemy.core;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Space + drag hand tool with glide, and the Tab / Shift+Tab shortcuts. */
public class HandTest {

    static float glideStartX, glideEndX;

    public static void main(String[] args) throws Exception {
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    AlcCanvas c = Alchemy.canvas;
                    c.requestFocusInWindow();
                    UndoSmokeTest.stroke(200, 300, 500, 300);
                    int shapes = c.shapes.size();
                    key(KeyEvent.KEY_PRESSED);
                    // Slow drag, mouse stops before release - no glide
                    java.awt.geom.Point2D.Float grab = c.toWorld(300, 300);
                    long t = System.currentTimeMillis();
                    c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, 300, 300, 1, false, MouseEvent.BUTTON1));
                    for (int i = 1; i <= 10; i++) {
                        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i * 20, InputEvent.BUTTON1_DOWN_MASK, 300 + i * 10, 300 + i * 5, 0, false, MouseEvent.NOBUTTON));
                    }
                    c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 500, 0, 400, 350, 1, false, MouseEvent.BUTTON1));
                    UndoSmokeTest.check("space + drag pans without drawing", c.shapes.size() == shapes);
                    UndoSmokeTest.check("grabbed point stays under the mouse", grab.distance(c.toWorld(400, 350)) < 0.5);
                    // Quick flick - the canvas glides on after release
                    t = System.currentTimeMillis();
                    c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, 400, 350, 1, false, MouseEvent.BUTTON1));
                    for (int i = 1; i <= 6; i++) {
                        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i * 10, InputEvent.BUTTON1_DOWN_MASK, 400 + i * 25, 350, 0, false, MouseEvent.NOBUTTON));
                    }
                    c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 65, 0, 550, 350, 1, false, MouseEvent.BUTTON1));
                    glideStartX = c.toWorld(0, 0).x;
                    key(KeyEvent.KEY_RELEASED);
                    UndoSmokeTest.check("Tab shows the toolbar", bound(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "Toggle Tool Bar"));
                    UndoSmokeTest.check("Shift+Tab flips tools and swatch", bound(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_MASK), "FlipToolBar"));
                    UndoSmokeTest.check("space no longer bound to the toolbar", Alchemy.window.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)) == null);
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        Thread.sleep(600);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                glideEndX = Alchemy.canvas.toWorld(0, 0).x;
                // Dragging right moves the drawing right, so the left edge shows smaller drawing x
                UndoSmokeTest.check(String.format("flick glides on after release (%.0f px further)", glideStartX - glideEndX), glideStartX - glideEndX > 30);
                // Normal drawing again after space is released
                int before = Alchemy.canvas.shapes.size();
                UndoSmokeTest.stroke(200, 500, 500, 500);
                UndoSmokeTest.check("drawing works again without space", Alchemy.canvas.shapes.size() == before + 1);
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    static void key(int id) {
        AlcCanvas c = Alchemy.canvas;
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchEvent(
                new KeyEvent(c, id, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
    }

    static boolean bound(KeyStroke stroke, String title) {
        Object name = Alchemy.window.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke);
        return title.equals(name);
    }
}
