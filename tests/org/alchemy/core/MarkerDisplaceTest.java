package org.alchemy.core;

import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.AbstractButton;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/** Marker brush with the Displace affect: the stroke must keep its displaced look when the pen is lifted. */
public class MarkerDisplaceTest {

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
                    SketchTest.shot("marker-displace");
                } catch (Throwable t) {
                    t.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    static void click(String buttonField, String name) throws Exception {
        Field b = AlcToolBar.class.getDeclaredField(buttonField);
        b.setAccessible(true);
        Field p = AlcPopupButton.class.getDeclaredField("popup");
        p.setAccessible(true);
        JPopupMenu menu = (JPopupMenu) p.get(b.get(Alchemy.toolBar));
        for (Component item : menu.getComponents()) {
            if (item instanceof AbstractButton && name.equals(((AbstractButton) item).getText())) {
                ((AbstractButton) item).doClick();
            }
        }
    }

    static void test() throws Exception {
        AlcCanvas c = Alchemy.canvas;
        click("createButton", "Brushes");
        AlcModule module = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
        Method select = module.getClass().getDeclaredMethod("selectBrush", int.class);
        select.setAccessible(true);
        select.invoke(module, 2);
        c.setColor(new Color(40, 60, 160));
        c.setLineWidth(8);
        // Plain marker first, for comparison
        BrushTest.stroke(100, 560, 200, false);
        Rectangle plain = c.shapes.get(c.shapes.size() - 1).getBounds();
        // Switch Displace on and draw again - check the shape just before and after lifting the pen
        click("affectButton", "Displace");
        long t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, 100, 450, 1, false, MouseEvent.BUTTON1));
        int x = 100, y = 450;
        for (int i = 1; i <= 240; i++) {
            x = 100 + i * 2; y = 450 + (int) Math.round(22 * Math.sin(i / 30.0));
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i, InputEvent.BUTTON1_DOWN_MASK, x, y, 0, false, MouseEvent.NOBUTTON));
        }
        Rectangle beforeRelease = c.createShapes.get(0).getBounds();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 250, 0, x, y, 1, false, MouseEvent.BUTTON1));
        Rectangle afterRelease = c.shapes.get(c.shapes.size() - 1).getBounds();
        UndoSmokeTest.check("displace changes the marker stroke (" + plain.height + " -> " + beforeRelease.height + " px tall)", beforeRelease.height > plain.height + 4);
        UndoSmokeTest.check("lifting the pen keeps the displaced shape (" + beforeRelease + " vs " + afterRelease + ")",
                Math.abs(beforeRelease.width - afterRelease.width) <= 3 && Math.abs(beforeRelease.height - afterRelease.height) <= 3);
    }
}

