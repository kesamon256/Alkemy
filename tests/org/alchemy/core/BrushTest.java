package org.alchemy.core;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.AbstractButton;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/** Draws a sample of every sketching brush with the mouse and with a simulated pen. */
public class BrushTest implements AlcConstants {

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
                    boolean os = Alchemy.canvas.isPressureSize(), oo = Alchemy.canvas.isPressureOpacity();
                    Alchemy.canvas.setPressureEffects(true, false);
                    try {
                        test();
                    } finally {
                        Alchemy.canvas.setPressureEffects(os, oo);
                    }
                    Alchemy.toolBar.setToolBarVisible(true);
                }
                catch (Throwable t) {
                    t.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        Thread.sleep(800);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Alchemy.toolBar.calculateTotalHeight();
                    Alchemy.toolBar.resizeToolBar();
                    SketchTest.shot("brushes");
                } catch (Throwable t) {
                    t.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    /** A wavy stroke - with a pen, the pressure swells and fades along it */
    static void stroke(int x1, int x2, int y, boolean pen) {
        AlcCanvas c = Alchemy.canvas;
        long t = System.currentTimeMillis();
        if (pen) {
            AlcWindowsInk.update(true, false, true, 0.1F, 0, 0);
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, t, 0, x1, y, 0, false, MouseEvent.NOBUTTON));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, x1, y, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 80; i++) {
            if (pen) {
                AlcWindowsInk.update(true, false, true, 0.1F + 0.9F * (float) Math.sin(Math.PI * i / 80.0), 0, 0);
            }
            int x = x1 + (x2 - x1) * i / 80;
            int yy = y + (int) Math.round(22 * Math.sin(i / 10.0));
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i * 8, InputEvent.BUTTON1_DOWN_MASK, x, yy, 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 650, 0, x2, y, 1, false, MouseEvent.BUTTON1));
        if (pen) {
            AlcWindowsInk.update(false, false, false, 0, 0, 0);
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, t + 655, 0, x2 + 5, y, 0, false, MouseEvent.NOBUTTON));
        }
    }

    static void test() throws Exception {
        AlcCanvas c = Alchemy.canvas;
        // Choose the Brushes module from the Create menu
        Field b = AlcToolBar.class.getDeclaredField("createButton");
        b.setAccessible(true);
        Field p = AlcPopupButton.class.getDeclaredField("popup");
        p.setAccessible(true);
        JPopupMenu creates = (JPopupMenu) p.get(b.get(Alchemy.toolBar));
        boolean found = false;
        for (Component item : creates.getComponents()) {
            if (item instanceof AbstractButton && "Brushes".equals(((AbstractButton) item).getText())) {
                ((AbstractButton) item).doClick();
                found = true;
            }
        }
        UndoSmokeTest.check("Brushes is in the Create menu", found);
        AlcModule module = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
        UndoSmokeTest.check("Brushes is the current tool (" + module.getName() + ")", module.getName().equals("Brushes"));
        Method select = module.getClass().getDeclaredMethod("selectBrush", int.class);
        select.setAccessible(true);
        c.setColor(new Color(30, 30, 40));
        c.setLineWidth(8);
        c.setPressureEnabled(true);
        String[] names = {"pencil", "ink", "marker", "charcoal", "liner", "brush pen", "sketchy", "hatching"};
        for (int i = 0; i < 8; i++) {
            select.invoke(module, i);
            int before = c.shapes.size();
            stroke(80, 560, 125 + i * 74, false);
            int afterMouse = c.shapes.size();
            stroke(700, 1180, 125 + i * 74, true);
            int afterPen = c.shapes.size();
            UndoSmokeTest.check(names[i] + " draws with the mouse (" + (afterMouse - before) + " shapes) and the pen (" + (afterPen - afterMouse) + ")",
                    afterMouse > before && afterPen > afterMouse);
        }
        // Undo removes a whole brush stroke in one step
        int count = c.shapes.size();
        Alchemy.history.undo();
        UndoSmokeTest.check("one undo removes the last brush stroke", c.shapes.size() < count);
        Alchemy.history.redo();
        // Ink follows the pressure: thin at the ends, wide in the middle
        AlcShape ink = null;
        for (AlcShape s : c.shapes) {
            if (s.hasSpine() && s.getBounds().x > 650 && s.getBounds().y > 160 && s.getBounds().y < 250) {
                ink = s;
            }
        }
        if (ink != null) {
            java.util.ArrayList<Float> w = ink.getSpineWidth();
            float first = w.get(0), mid = w.get(w.size() / 2), end = w.get(w.size() - 1);
            UndoSmokeTest.check(String.format("ink tapers and follows the pen (%.1f / %.1f / %.1f)", first, mid, end), mid > first * 2 && mid > end * 2);
        } else {
            UndoSmokeTest.check("ink pen stroke found", false);
        }
        // The liner follows the drawing speed while Q is held - slow is wide, fast is thin
        java.lang.reflect.Field held = AlcCanvas.class.getDeclaredField("motionHeld");
        held.setAccessible(true);
        held.setBoolean(c, true);
        select.invoke(module, 4);
        long t = System.currentTimeMillis(), when = t;
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, 100, 700, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 100; i++) {
            when += (i <= 50) ? 20 : 1;
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, when, InputEvent.BUTTON1_DOWN_MASK, 100 + i * 5, 700, 0, false, MouseEvent.NOBUTTON));
        }
        AlcShape liner = c.createShapes.get(0);
        java.util.ArrayList<Float> lw = liner.getSpineWidth();
        float slow = lw.get(lw.size() * 2 / 5), fast = lw.get(lw.size() * 9 / 10);
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, when + 1, 0, 600, 700, 1, false, MouseEvent.BUTTON1));
        held.setBoolean(c, false);
        UndoSmokeTest.check(String.format("liner is thinner when fast (slow %.1f / fast %.1f)", slow, fast), slow > fast * 1.8);
    }
}