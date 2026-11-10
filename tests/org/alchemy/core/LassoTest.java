package org.alchemy.core;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import javax.swing.SwingUtilities;

/** Lasso selection: select, add, move, scale, rotate, delete, escape, locked layers, zoom, undo. */
public class LassoTest {

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

    static void drag(int x1, int y1, int x2, int y2, int mods) {
        AlcCanvas c = Alchemy.canvas;
        long t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK | mods, x1, y1, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 10; i++) {
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i, InputEvent.BUTTON1_DOWN_MASK | mods, x1 + (x2 - x1) * i / 10, y1 + (y2 - y1) * i / 10, 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 20, mods, x2, y2, 1, false, MouseEvent.BUTTON1));
    }

    /** A rectangular loop drawn with the lasso */
    static void loop(int x1, int y1, int x2, int y2, int mods) {
        AlcCanvas c = Alchemy.canvas;
        long t = System.currentTimeMillis();
        int[][] pts = {{x1, y1}, {x2, y1}, {x2, y2}, {x1, y2}, {x1, y1 + 2}};
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK | mods, x1, y1, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i < pts.length; i++) {
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i, InputEvent.BUTTON1_DOWN_MASK | mods, pts[i][0], pts[i][1], 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 9, mods, x1, y1 + 2, 1, false, MouseEvent.BUTTON1));
    }

    static void key(int code) {
        AlcCanvas c = Alchemy.canvas;
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchEvent(
                new KeyEvent(c, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED));
    }

    static Point screen(double x, double y) {
        Point2D p = Alchemy.canvas.getViewTransform().transform(new Point2D.Double(x, y), null);
        return new Point((int) Math.round(p.getX()), (int) Math.round(p.getY()));
    }

    static void test() throws Exception {
        AlcCanvas c = Alchemy.canvas;
        AlcHistory h = Alchemy.history;
        AlcSelectionTool tool = c.getSelectionTool();
        c.setLineWidth(4);
        UndoSmokeTest.stroke(200, 200, 400, 210);   // A
        UndoSmokeTest.stroke(200, 350, 400, 360);   // B
        UndoSmokeTest.stroke(800, 350, 1000, 360);  // C
        AlcShape a = c.shapes.get(0), b = c.shapes.get(1), cc = c.shapes.get(2);

        c.setSelecting(true);
        int before = c.shapes.size();
        drag(100, 500, 300, 600, 0);
        UndoSmokeTest.check("select mode draws nothing", c.shapes.size() == before);
        loop(150, 150, 450, 420, 0);
        UndoSmokeTest.check("loop selects A and B (" + tool.getSelected().size() + ")", tool.getSelected().size() == 2 && tool.getSelected().contains(a) && tool.getSelected().contains(b));
        loop(750, 300, 1050, 420, InputEvent.SHIFT_DOWN_MASK);
        UndoSmokeTest.check("shift adds C (" + tool.getSelected().size() + ")", tool.getSelected().size() == 3);
        loop(150, 150, 450, 260, 0);
        UndoSmokeTest.check("a new loop replaces the selection", tool.getSelected().size() == 1 && tool.getSelected().get(0) == a);
        SketchTest.shot("lasso-selection");

        // Move A
        Rectangle start = a.getBounds();
        drag(300, 205, 400, 255, 0);
        Rectangle moved = a.getBounds();
        UndoSmokeTest.check("drag inside moves the selection (" + (moved.x - start.x) + "," + (moved.y - start.y) + ")", Math.abs(moved.x - start.x - 100) <= 1 && Math.abs(moved.y - start.y - 50) <= 1);
        h.undo();
        UndoSmokeTest.check("undo the move", a.getBounds().equals(start));

        // Scale A from its bottom right corner to double size
        Rectangle2D sb = selectionScreenBounds(a);
        float width = a.getLineWidth();
        drag((int) Math.round(sb.getMaxX()), (int) Math.round(sb.getMaxY()),
                (int) Math.round(sb.getMinX() + sb.getWidth() * 2), (int) Math.round(sb.getMinY() + sb.getHeight() * 2), 0);
        Rectangle scaled = a.getBounds();
        UndoSmokeTest.check(String.format("corner drag scales (%d -> %d wide, line %.1f -> %.1f)", start.width, scaled.width, width, a.getLineWidth()),
                Math.abs(scaled.width - start.width * 2) < 8 && Math.abs(a.getLineWidth() - width * 2) < 0.3);
        h.undo();
        UndoSmokeTest.check("undo the scale", a.getBounds().equals(start) && a.getLineWidth() == width);

        // Rotate A by 90 degrees with the handle above the frame
        sb = selectionScreenBounds(a);
        double cx = sb.getCenterX(), cy = sb.getCenterY();
        int hx = (int) Math.round(cx), hy = (int) Math.round(sb.getMinY() - 26);
        double r = cy - hy;
        drag(hx, hy, (int) Math.round(cx + r), (int) Math.round(cy), 0);
        Rectangle rotated = a.getBounds();
        UndoSmokeTest.check(String.format("rotate handle turns it (%dx%d -> %dx%d)", start.width, start.height, rotated.width, rotated.height),
                rotated.height > start.width * 0.8 && rotated.width < start.height * 2 + 10);
        h.undo();

        // Delete key, undo
        key(KeyEvent.VK_DELETE);
        UndoSmokeTest.check("Delete removes the selected shape", !c.shapes.contains(a) && c.shapes.size() == 2);
        h.undo();
        UndoSmokeTest.check("undo the delete", c.shapes.contains(a));

        // Select all, Escape twice
        c.selectAll();
        UndoSmokeTest.check("Ctrl+A selects the whole layer", tool.getSelected().size() == 3);
        key(KeyEvent.VK_ESCAPE);
        UndoSmokeTest.check("Escape clears the selection", !tool.hasSelection() && c.isSelecting());
        key(KeyEvent.VK_ESCAPE);
        UndoSmokeTest.check("second Escape leaves the tool", !c.isSelecting());

        // Locked layer: can select, cannot change
        c.setSelecting(true);
        loop(150, 150, 450, 260, 0);
        c.setLayerLocked(c.getActiveLayer(), true);
        start = a.getBounds();
        drag(300, 205, 400, 255, 0);
        UndoSmokeTest.check("a locked layer is not changed", a.getBounds().equals(start));
        c.setLayerLocked(c.getActiveLayer(), false);

        // Zoomed in: the loop works in drawing coordinates
        c.zoomAt(2, 640, 380);
        Point p1 = screen(150, 300), p2 = screen(450, 420);
        loop(p1.x, p1.y, p2.x, p2.y, 0);
        UndoSmokeTest.check("loop works zoomed in (" + tool.getSelected().size() + ")", tool.getSelected().size() == 1 && tool.getSelected().get(0) == b);
        c.resetView();
        c.setSelecting(false);
    }

    static Rectangle2D selectionScreenBounds(AlcShape s) {
        Rectangle2D b = s.getPath().getBounds2D();
        double g = s.getLineWidth() / 2;
        b = new Rectangle2D.Double(b.getX() - g, b.getY() - g, b.getWidth() + g * 2, b.getHeight() + g * 2);
        return Alchemy.canvas.getViewTransform().createTransformedShape(b).getBounds2D();
    }
}
