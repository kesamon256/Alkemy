package org.alchemy.core;

import java.awt.Color;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.lang.reflect.Method;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

/** Polygon strokes with Alt and Solid cuts with Shift. */
public class PolyCutTest implements AlcConstants {

    static long t = System.currentTimeMillis();

    static void ev(int id, int x, int y, int mods, int button, int clicks) {
        AlcCanvas c = Alchemy.canvas;
        t += 12;
        c.dispatchEvent(new MouseEvent(c, id, t, mods, x, y, clicks, false, button));
    }

    static void press(int x, int y, int mods) {
        ev(MouseEvent.MOUSE_PRESSED, x, y, mods | InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1, 1);
    }

    static void release(int x, int y, int mods) {
        ev(MouseEvent.MOUSE_RELEASED, x, y, mods, MouseEvent.BUTTON1, 1);
    }

    static void drag(int x0, int y0, int x1, int y1, int mods) {
        for (int i = 1; i <= 10; i++) {
            ev(MouseEvent.MOUSE_DRAGGED, x0 + (x1 - x0) * i / 10, y0 + (y1 - y0) * i / 10, mods | InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON, 0);
        }
    }

    /** Click with Alt held: press, release */
    static void altClick(int x, int y, int mods) {
        ev(MouseEvent.MOUSE_MOVED, x, y, mods | InputEvent.ALT_DOWN_MASK, MouseEvent.NOBUTTON, 0);
        press(x, y, mods | InputEvent.ALT_DOWN_MASK);
        release(x, y, mods | InputEvent.ALT_DOWN_MASK);
    }

    static boolean letGoOfAlt() {
        KeyEvent k = new KeyEvent(Alchemy.canvas, KeyEvent.KEY_RELEASED, t += 10, 0, KeyEvent.VK_ALT, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchEvent(k);
        return true;
    }

    /** Draw an outline: a short drag, then straight pieces to the corners, then let go of Alt */
    static void polygon(int[][] pts, int mods) {
        press(pts[0][0], pts[0][1], mods);
        drag(pts[0][0], pts[0][1], pts[1][0], pts[1][1], mods);
        release(pts[1][0], pts[1][1], mods | InputEvent.ALT_DOWN_MASK);
        for (int i = 2; i < pts.length; i++) {
            altClick(pts[i][0], pts[i][1], mods);
        }
        letGoOfAlt();
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
                    SketchTest.shot("polycut");
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    static void test() throws Exception {
        AlcCanvas c = Alchemy.canvas;
        c.setColor(new Color(50, 60, 75));
        c.setLineWidth(3);
        c.setStyleMode(AlcCanvas.STYLE_MODE_FILL);
        MarkerDisplaceTest.click("createButton", "Shapes");

        // A mech torso as a polygon with Solid: a short freehand start, then straight sides
        int before = c.shapes.size();
        polygon(new int[][]{{120, 200}, {160, 180}, {420, 170}, {470, 300}, {400, 520}, {180, 520}, {100, 330}}, 0);
        UndoSmokeTest.check("the polygon is one shape (" + (c.shapes.size() - before) + ")", c.shapes.size() == before + 1);
        AlcShape torso = c.shapes.get(c.shapes.size() - 1);
        java.awt.Rectangle b = torso.getBounds();
        UndoSmokeTest.check("the polygon reaches all its corners (" + b + ")",
                Math.abs(b.x - 100) <= 4 && Math.abs(b.y - 170) <= 4 && Math.abs(b.x + b.width - 470) <= 4 && Math.abs(b.y + b.height - 520) <= 4);
        UndoSmokeTest.check("letting go of Alt does not open the menu bar", MenuSelectionManager.defaultManager().getSelectedPath().length == 0);

        // Solid cut with Shift - a freehand cut out of the side
        Area full = new Area(torso.getPath());
        press(380, 380, InputEvent.SHIFT_DOWN_MASK);
        int[][] loop = {{440, 380}, {440, 460}, {380, 460}, {380, 380}};
        int lx = 380, ly = 380;
        for (int[] p : loop) {
            drag(lx, ly, p[0], p[1], InputEvent.SHIFT_DOWN_MASK);
            lx = p[0];
            ly = p[1];
        }
        release(380, 380, InputEvent.SHIFT_DOWN_MASK);
        AlcShape cutTorso = c.shapes.get(c.shapes.size() - 1);
        UndoSmokeTest.check("the cut keeps one shape", c.shapes.size() == before + 1);
        UndoSmokeTest.check("the cut area is gone", !cutTorso.getPath().contains(410, 420) && full.contains(410, 420));
        UndoSmokeTest.check("the rest stays", cutTorso.getPath().contains(250, 350));

        // A polygon cut - a vent slot with straight sides
        press(200, 250, InputEvent.SHIFT_DOWN_MASK);
        drag(200, 250, 330, 250, InputEvent.SHIFT_DOWN_MASK);
        release(330, 250, InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
        altClick(330, 275, InputEvent.SHIFT_DOWN_MASK);
        altClick(200, 275, InputEvent.SHIFT_DOWN_MASK);
        letGoOfAlt();
        AlcShape vented = c.shapes.get(c.shapes.size() - 1);
        UndoSmokeTest.check("the polygon cut makes a slot", !vented.getPath().contains(265, 262) && vented.getPath().contains(265, 300));

        // Undo brings the slot back
        Alchemy.history.undo();
        UndoSmokeTest.check("undo fills the slot again", c.shapes.get(c.shapes.size() - 1).getPath().contains(265, 262));
        Alchemy.history.redo();

        // With Mirror on, a cut on the right also cuts the left: a symmetric block, cut once
        c.setSymmetryAxes(true, 285, false, 0, null);
        before = c.shapes.size();
        polygon(new int[][]{{150, 600}, {200, 590}, {420, 590}, {420, 720}, {150, 720}}, 0);
        press(380, 620, InputEvent.SHIFT_DOWN_MASK);
        drag(380, 620, 400, 620, InputEvent.SHIFT_DOWN_MASK);
        release(400, 620, InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
        altClick(400, 690, InputEvent.SHIFT_DOWN_MASK);
        altClick(380, 690, InputEvent.SHIFT_DOWN_MASK);
        letGoOfAlt();
        AlcShape block = c.shapes.get(c.shapes.size() - 1);
        UndoSmokeTest.check("the mirrored cut goes through both sides",
                !block.getPath().contains(390, 650) && !block.getPath().contains(180, 650) && block.getPath().contains(285, 650));
        c.clearSymmetryAxes();

        // A brush with Alt: straight pieces drawn by the brush itself
        c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
        MarkerDisplaceTest.click("createButton", "Brushes");
        AlcModule brushes = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
        Method select = brushes.getClass().getDeclaredMethod("selectBrush", int.class);
        select.setAccessible(true);
        select.invoke(brushes, 5);
        c.setLineWidth(6);
        before = c.shapes.size();
        polygon(new int[][]{{620, 520}, {660, 470}, {760, 260}, {900, 220}, {1000, 330}, {1120, 300}}, 0);
        UndoSmokeTest.check("a brush pen polygon is one stroke (" + (c.shapes.size() - before) + ")", c.shapes.size() == before + 1);
        java.awt.Rectangle pb = c.shapes.get(c.shapes.size() - 1).getBounds();
        UndoSmokeTest.check("the brush reaches the last corner (" + pb + ")", pb.x + pb.width >= 1110 && pb.y <= 228);

        // And Solid hatching around a polygon
        select.invoke(brushes, 7);
        c.setStyleMode(AlcCanvas.STYLE_MODE_FILL);
        c.setLineWidth(4);
        before = c.shapes.size();
        polygon(new int[][]{{640, 600}, {700, 580}, {1000, 560}, {1060, 700}, {660, 720}}, 0);
        UndoSmokeTest.check("solid hatching fills the polygon", c.shapes.size() > before);
        c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
    }
}
