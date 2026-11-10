package org.alchemy.core;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.SwingUtilities;

/** Layers: order, visibility, lock, opacity, eraser, clear, undo, sketch files, export. */
public class LayerTest {

    static String dir;

    public static void main(String[] args) throws Exception {
        dir = args[0];
        PartsShot.dir = dir;
        PartsShot.sc = Double.parseDouble(System.getProperty("sun.java2d.uiScale", "1"));
        SketchTest.dir = dir;
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
        Thread.sleep(500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    java.lang.reflect.Field pf = AlcLayersPanel.class.getDeclaredField("panel");
                    pf.setAccessible(true);
                    javax.swing.JDialog panel = (javax.swing.JDialog) pf.get(null);
                    PartsShot.render(panel.getRootPane(), "layers-panel");
                    SketchTest.shot("layers-canvas");
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    /** A thick straight stroke */
    static void line(int x1, int y1, int x2, int y2) {
        AlcCanvas c = Alchemy.canvas;
        long t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, x1, y1, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 20; i++) {
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i, InputEvent.BUTTON1_DOWN_MASK, x1 + (x2 - x1) * i / 20, y1 + (y2 - y1) * i / 20, 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 30, 0, x2, y2, 1, false, MouseEvent.BUTTON1));
    }

    static Color pixel(int x, int y) {
        Alchemy.canvas.setGuide(false);
        BufferedImage img = (BufferedImage) Alchemy.canvas.renderCanvas(true);
        Alchemy.canvas.setGuide(true);
        return new Color(img.getRGB(x, y));
    }

    static void test() throws Exception {
        AlcCanvas c = Alchemy.canvas;
        AlcHistory h = Alchemy.history;
        c.setLineWidth(24);
        // Layer 1: red horizontal line
        c.setColor(Color.RED);
        line(200, 300, 700, 300);
        // Layer 2: blue vertical line crossing it
        c.addLayer();
        UndoSmokeTest.check("new layer added and active", c.getLayers().size() == 2 && c.getActiveLayerIndex() == 1);
        c.setColor(Color.BLUE);
        line(450, 150, 450, 450);
        UndoSmokeTest.check("each layer has its own stroke", c.getLayers().get(0).shapes.size() == 1 && c.getLayers().get(1).shapes.size() == 1);
        UndoSmokeTest.check("upper layer covers the lower one", pixel(450, 300).getBlue() > 200 && pixel(450, 300).getRed() < 60);

        // Move the blue layer below
        c.moveLayer(-1);
        UndoSmokeTest.check("moved layer goes under", pixel(450, 300).getRed() > 200 && pixel(450, 300).getBlue() < 60);
        h.undo();
        UndoSmokeTest.check("undo the move", pixel(450, 300).getBlue() > 200);

        // Hide the blue layer
        AlcLayer blue = c.getLayers().get(1);
        c.setLayerVisible(blue, false);
        UndoSmokeTest.check("hidden layer is not drawn", pixel(450, 200).equals(Color.WHITE));
        // Drawing on a hidden layer is refused
        int before = blue.shapes.size();
        line(100, 600, 400, 600);
        UndoSmokeTest.check("no drawing on a hidden layer", blue.shapes.size() == before);
        h.undo();
        UndoSmokeTest.check("undo shows it again", !pixel(450, 200).equals(Color.WHITE));

        // Lock it
        c.setLayerLocked(blue, true);
        line(100, 600, 400, 600);
        UndoSmokeTest.check("no drawing on a locked layer", blue.shapes.size() == before);
        c.setLayerLocked(blue, false);

        // Opacity
        c.setLayerOpacity(blue, 0.5F, true);
        Color half = pixel(450, 200);
        UndoSmokeTest.check("half opacity blends with white (" + WheelAccuracyTest.hex(half) + ")", half.getRed() > 100 && half.getRed() < 160 && half.getBlue() > 240);
        c.setLayerOpacity(blue, 1F, true);

        // The eraser only removes shapes of the active layer
        long t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON3_DOWN_MASK, 440, 300, 1, false, MouseEvent.BUTTON3));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + 1, InputEvent.BUTTON3_DOWN_MASK, 460, 300, 0, false, MouseEvent.NOBUTTON));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 2, 0, 460, 300, 1, false, MouseEvent.BUTTON3));
        UndoSmokeTest.check("eraser removes only the active layer's shape", blue.shapes.isEmpty() && c.getLayers().get(0).shapes.size() == 1);
        h.undo();

        // Clear empties every layer, one undo brings both back
        c.clear();
        UndoSmokeTest.check("clear empties every layer", blue.shapes.isEmpty() && c.getLayers().get(0).shapes.isEmpty());
        h.undo();
        UndoSmokeTest.check("undo clear", blue.shapes.size() == 1 && c.getLayers().get(0).shapes.size() == 1);

        // Delete and undo
        c.deleteLayer();
        UndoSmokeTest.check("delete layer", c.getLayers().size() == 1);
        h.undo();
        UndoSmokeTest.check("undo delete brings the layer back", c.getLayers().size() == 2 && c.getLayers().get(1).shapes.size() == 1);

        // Duplicate, rename
        c.duplicateLayer();
        UndoSmokeTest.check("duplicate copies the shapes", c.getLayers().size() == 3 && c.getActiveLayer().shapes.size() == 1
                && c.getActiveLayer().shapes.get(0) != blue.shapes.get(0));
        c.renameLayer(c.getActiveLayer(), "Detail");
        c.setLayerOpacity(c.getActiveLayer(), 0.7F, true);
        c.setLayerVisible(c.getLayers().get(0), false);

        // Sketch file keeps the layers
        File f = new File(dir, "layers.alchemy");
        AlcSketchIO.save(f, c);
        c.newDrawing();
        UndoSmokeTest.check("new drawing has one empty layer", c.getLayers().size() == 1 && !c.hasAnyShapes());
        c.loadSketch(AlcSketchIO.open(f));
        UndoSmokeTest.check("sketch file keeps layers, names, opacity and visibility",
                c.getLayers().size() == 3 && c.getLayers().get(2).name.equals("Detail")
                && Math.abs(c.getLayers().get(2).opacity - 0.7F) < 0.001 && !c.getLayers().get(0).visible
                && c.getActiveLayerIndex() == 2);
        // Export leaves out hidden layers
        File png = new File(dir, "layers-export.png");
        c.saveBitmap(png);
        BufferedImage ex = javax.imageio.ImageIO.read(png);
        Color redSpot = new Color(ex.getRGB(250, 300));
        UndoSmokeTest.check("export leaves out the hidden layer (" + WheelAccuracyTest.hex(redSpot) + ")", redSpot.equals(Color.WHITE));
        c.setLayerVisible(c.getLayers().get(0), true);
        c.addLayer();
        AlcLayersPanel.showPanel();
    }
}
