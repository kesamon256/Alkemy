package org.alchemy.core;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.AbstractButton;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/** The Style menu: every style on dragged shapes, and the fill styles on brushes. */
public class StyleTest implements AlcConstants {

    static AlcPopupButton find(Container c, String text) {
        for (Component child : c.getComponents()) {
            if (child instanceof AlcPopupButton && text.equals(((AlcPopupButton) child).getText())) {
                return (AlcPopupButton) child;
            }
            if (child instanceof Container) {
                AlcPopupButton b = find((Container) child, text);
                if (b != null) {
                    return b;
                }
            }
        }
        return null;
    }

    static void pickStyle(String label) throws Exception {
        AlcPopupButton b = find(Alchemy.toolBar, "Style");
        Field pf = AlcPopupButton.class.getDeclaredField("popup");
        pf.setAccessible(true);
        JPopupMenu menu = (JPopupMenu) pf.get(b);
        for (Component item : menu.getComponents()) {
            if (item instanceof AbstractButton && label.equals(((AbstractButton) item).getText())) {
                ((AbstractButton) item).doClick();
                return;
            }
        }
        throw new Exception("no style " + label);
    }

    /** A loop around a centre point */
    static void loop(int cx, int cy, int r) {
        AlcCanvas c = Alchemy.canvas;
        long t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, t, 0, cx + r, cy, 0, false, MouseEvent.NOBUTTON));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, cx + r, cy, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 60; i++) {
            double a = i * Math.PI * 2 / 60;
            double rr = r * (1 + 0.12 * Math.sin(a * 3));
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i * 10, InputEvent.BUTTON1_DOWN_MASK,
                    cx + (int) Math.round(Math.cos(a) * rr), cy + (int) Math.round(Math.sin(a) * rr * 0.8), 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 620, 0, cx + r, cy, 1, false, MouseEvent.BUTTON1));
    }

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
                    Alchemy.toolBar.setToolBarVisible(true);
                } catch (Throwable t) {
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
                    SketchTest.shot("styles");
                } catch (Throwable t) {
                    t.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    static void test() throws Exception {
        AlcCanvas c = Alchemy.canvas;
        c.setColor(new Color(40, 70, 170));
        c.setLineWidth(4);
        UndoSmokeTest.check("Style is a menu button", find(Alchemy.toolBar, "Style") != null);
        try {
            pickStyle("Dashed");
            UndoSmokeTest.check("the menu has no Dashed style", false);
        } catch (Exception e) {
            UndoSmokeTest.check("the menu offers only Outline and Solid", true);
        }
        // Shapes - a freehand loop in each style
        MarkerDisplaceTest.click("createButton", "Shapes");
        String[] labels = {"Outline", "Solid"};
        for (int i = 0; i < labels.length; i++) {
            pickStyle(labels[i]);
            UndoSmokeTest.check("picked " + labels[i] + " (mode " + c.getStyleMode() + ")", c.getStyleMode() == i);
            loop(140 + i * 240, 260, 85);
            AlcShape s = c.shapes.get(c.shapes.size() - 1);
            UndoSmokeTest.check(labels[i] + " rectangle has style " + s.getStyle() + " dash " + s.getDash(),
                    s.getStyle() == new int[]{STYLE_STROKE, STYLE_FILL}[i]
                    && s.getDash() == DASH_NONE);
        }
        // Undo keeps the style
        Alchemy.history.undo();
        Alchemy.history.redo();
        UndoSmokeTest.check("redo keeps the solid shape", c.shapes.get(c.shapes.size() - 1).getStyle() == STYLE_FILL);
        // Brushes - loops filled with the brush in Solid, kept with a tint in Outline + Tint
        MarkerDisplaceTest.click("createButton", "Brushes");
        AlcModule brushes = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
        Method brush = brushes.getClass().getDeclaredMethod("selectBrush", int.class);
        brush.setAccessible(true);
        int[] picks = {0, 3, 7, 2, 4};
        String[] names = {"pencil", "charcoal", "hatching", "marker", "liner"};
        for (int row = 0; row < 2; row++) {
            pickStyle(row == 0 ? "Solid" : "Outline");
            for (int i = 0; i < picks.length; i++) {
                brush.invoke(brushes, picks[i]);
                int before = c.shapes.size();
                loop(140 + i * 240, 440 + row * 170, 85);
                int added = c.shapes.size() - before;
                UndoSmokeTest.check((row == 0 ? "solid " : "outline ") + names[i] + " draws (" + added + " shapes)", added >= 1);
            }
        }
        // A straight brush line in Solid still draws the line
        pickStyle("Solid");
        brush.invoke(brushes, 1);
        int before = c.shapes.size();
        BrushTest.stroke(900, 1200, 140, false);
        UndoSmokeTest.check("a brush line that goes around nothing stays a line", c.shapes.size() > before);
        pickStyle("Outline");
    }
}
