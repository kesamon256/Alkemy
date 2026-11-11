package org.alchemy.core;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Zoom and pan: drawing while zoomed, Ctrl+wheel, Ctrl+drag, middle-button pan, exports and undo. */
public class ViewTest {

    static String dir;

    public static void main(String[] args) throws Exception {
        dir = args[0];
        SketchTest.dir = dir;
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1280, 760);
                Alchemy.window.validate();
            }
        });
        Thread.sleep(800);
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

    static void drag(int button, int mods, int x1, int y1, int x2, int y2) {
        AlcCanvas c = Alchemy.canvas;
        long t = System.currentTimeMillis();
        int down = (button == MouseEvent.BUTTON2 ? InputEvent.BUTTON2_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK) | mods;
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, down, x1, y1, 1, false, button));
        for (int i = 1; i <= 20; i++) {
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i, down, x1 + (x2 - x1) * i / 20, y1 + (y2 - y1) * i / 20, 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 30, mods, x2, y2, 1, false, button));
    }

    static void wheel(int x, int y, double rotation, boolean ctrl) {
        AlcCanvas c = Alchemy.canvas;
        c.dispatchEvent(new MouseWheelEvent(c, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), ctrl ? InputEvent.CTRL_DOWN_MASK : 0,
                x, y, x, y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, (int) Math.signum(rotation), rotation));
    }

    static void test() throws Exception {
        AlcCanvas c = Alchemy.canvas;
        c.setLineWidth(3);
        // The checks look for dark ink - not whatever colour was last picked in the app
        c.setColor(java.awt.Color.BLACK);
        c.setPaper(AlcCanvas.PAPER_GRID);
        // A reference stroke at 100%
        UndoSmokeTest.stroke(200, 300, 600, 300);
        java.awt.Rectangle ref = c.shapes.get(0).getBounds();

        // Ctrl + wheel zooms in around the mouse - the point under the mouse stays put
        java.awt.geom.Point2D.Float before = c.toWorld(400, 300);
        wheel(400, 300, -3, true);
        java.awt.geom.Point2D.Float after = c.toWorld(400, 300);
        UndoSmokeTest.check(String.format("ctrl+wheel zooms in (%.0f %%)", c.getViewScale() * 100), c.getViewScale() > 1.4);
        UndoSmokeTest.check("point under the mouse stays put", before.distance(after) < 0.5);
        UndoSmokeTest.check("shapes are not changed by zoom", c.shapes.get(0).getBounds().equals(ref));

        // Drawing while zoomed lands where the pen is
        double s = c.getViewScale();
        UndoSmokeTest.stroke(300, 450, 700, 450);
        java.awt.Rectangle drawn = c.shapes.get(1).getBounds();
        java.awt.geom.Point2D.Float expect = c.toWorld(300, 450);
        UndoSmokeTest.check(String.format("stroke drawn at the pen in drawing coordinates (%d,%d ~ %.0f,%.0f)", drawn.x, drawn.y, expect.x, expect.y),
                Math.abs(drawn.x - expect.x) < 3 && Math.abs(drawn.y - expect.y) < 20);
        UndoSmokeTest.check("zoomed stroke is shorter in the drawing", drawn.width < 400 / s + 10);
        SketchTest.shot("view-1-zoomed");

        // Middle button pans
        java.awt.geom.Point2D.Float p0 = c.toWorld(500, 500);
        drag(MouseEvent.BUTTON2, 0, 500, 500, 400, 450);
        java.awt.geom.Point2D.Float p1 = c.toWorld(400, 450);
        UndoSmokeTest.check("middle button pans the view", p0.distance(p1) < 0.5 && c.shapes.size() == 2);

        // Ctrl + left drag to the left zooms out, without drawing
        double s0 = c.getViewScale();
        drag(MouseEvent.BUTTON1, InputEvent.CTRL_DOWN_MASK, 600, 400, 400, 400);
        UndoSmokeTest.check(String.format("ctrl+drag zooms out (%.0f%% -> %.0f%%)", s0 * 100, c.getViewScale() * 100), c.getViewScale() < s0 && c.shapes.size() == 2);

        // Exports ignore the view
        File png = new File(dir, "view-export.png");
        c.saveBitmap(png);
        java.awt.image.BufferedImage ex = ImageIO.read(png);
        boolean dark = false;
        for (int x = ref.x; x < ref.x + ref.width && !dark; x++) {
            for (int y = ref.y; y <= ref.y + ref.height && !dark; y++) {
                dark = (ex.getRGB(x, y) & 0xFF) < 100;
            }
        }
        UndoSmokeTest.check("export draws the stroke at its real place", dark);

        // Undo still works while zoomed
        Alchemy.history.undo();
        UndoSmokeTest.check("undo while zoomed", c.shapes.size() == 1);
        Alchemy.history.redo();

        // Plain wheel scrolls, zoom button / Z resets
        c.zoomCanvas(true);
        UndoSmokeTest.check("zoom shortcut goes back to 100%", !c.isCanvasZoomed());
        c.zoomCanvas(true);
        SketchTest.shot("view-2-z-key");
        c.startZoomMousing();
        UndoSmokeTest.check("zoom button resets", !c.isCanvasZoomed());
        c.setPaper(AlcCanvas.PAPER_BLANK);
    }
}
