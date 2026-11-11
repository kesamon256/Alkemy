package org.alchemy.core;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Export area selection: choose a frame on the canvas, export only that part. */
public class ExportTest {

    static String dir;
    static File png;
    static int expectW, expectH;

    public static void main(String[] args) throws Exception {
        dir = args[0];
        SketchTest.dir = dir;
        png = new File(dir, "export-area.png");
        png.delete();
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
                    AlcCanvas c = Alchemy.canvas;
                    c.setLineWidth(4);
                    // The checks look for dark ink - not whatever colour was last picked in the app
                    c.setColor(java.awt.Color.BLACK);
                    UndoSmokeTest.stroke(200, 200, 500, 220);
                    // A second stroke far outside the original window
                    c.panBy(-1600, 0);
                    UndoSmokeTest.stroke(300, 300, 600, 320);
                    System.out.println("shapes before export: " + c.shapes.size());
                    c.centerView();
                    Rectangle2D drawing = c.getDrawingBounds();
                    c.startExportSelection(new Runnable() {

                        public void run() {
                            c.saveBitmap(png);
                        }
                    });
                    AlcExportSelector selector = selector();
                    Rectangle2D initial = selector.getArea();
                    UndoSmokeTest.check("selection starts around the whole drawing", initial.contains(drawing));
                    SketchTest.shot("export-1-selection");

                    // Drag a new frame around the right hand stroke only (screen coordinates)
                    java.awt.geom.Point2D p1 = c.getViewTransform().transform(new java.awt.geom.Point2D.Double(1600 + 280, 260), null);
                    java.awt.geom.Point2D p2 = c.getViewTransform().transform(new java.awt.geom.Point2D.Double(1600 + 640, 360), null);
                    long t = System.currentTimeMillis();
                    // Start outside the current frame so a new one is drawn
                    c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, 5, 5, 1, false, MouseEvent.BUTTON1));
                    c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 5, 0, 5, 5, 1, false, MouseEvent.BUTTON1));
                    UndoSmokeTest.check("a click does not destroy the frame", selector.getArea().equals(initial));
                    // Shrink the frame out of the way, then drag a new one around the far stroke
                    selector.setArea(new Rectangle2D.Double(-5000, -5000, 10, 10));
                    t = System.currentTimeMillis();
                    c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, (int) p1.getX(), (int) p1.getY(), 1, false, MouseEvent.BUTTON1));
                    for (int i = 1; i <= 10; i++) {
                        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i, InputEvent.BUTTON1_DOWN_MASK,
                                (int) (p1.getX() + (p2.getX() - p1.getX()) * i / 10), (int) (p1.getY() + (p2.getY() - p1.getY()) * i / 10), 0, false, MouseEvent.NOBUTTON));
                    }
                    c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 20, 0, (int) p2.getX(), (int) p2.getY(), 1, false, MouseEvent.BUTTON1));
                    Rectangle2D chosen = selector.getArea();
                    UndoSmokeTest.check(String.format("dragged a new frame (%.0f x %.0f)", chosen.getWidth(), chosen.getHeight()),
                            Math.abs(chosen.getWidth() - 360) < 10 && Math.abs(chosen.getHeight() - 100) < 10);
                    // Type an exact size into the bar
                    java.lang.reflect.Field wf = AlcExportSelector.class.getDeclaredField("widthField");
                    java.lang.reflect.Field hf = AlcExportSelector.class.getDeclaredField("heightField");
                    wf.setAccessible(true);
                    hf.setAccessible(true);
                    javax.swing.JTextField w = (javax.swing.JTextField) wf.get(selector);
                    javax.swing.JTextField h = (javax.swing.JTextField) hf.get(selector);
                    UndoSmokeTest.check("fields show the dragged size (" + w.getText() + " x " + h.getText() + ")", w.getText().equals("" + (int) Math.ceil(chosen.getWidth())));
                    w.setText("400");
                    h.setText("120");
                    w.postActionEvent();
                    chosen = selector.getArea();
                    UndoSmokeTest.check("typed size is applied, top left stays", chosen.getWidth() == 400 && chosen.getHeight() == 120);
                    w.setText("abc");
                    selector.applyTypedSize();
                    UndoSmokeTest.check("invalid number is put back", w.getText().equals("400"));
                    expectW = (int) Math.ceil(chosen.getWidth());
                    expectH = (int) Math.ceil(chosen.getHeight());
                    SketchTest.shot("export-2-chosen");
                    UndoSmokeTest.check("no stroke drawn while choosing (" + c.shapes.size() + " shapes)", c.shapes.size() == 2);
                    selector.export();
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        Thread.sleep(500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    BufferedImage img = ImageIO.read(png);
                    UndoSmokeTest.check("export has the size of the frame (" + img.getWidth() + "x" + img.getHeight() + ")", img.getWidth() == expectW && img.getHeight() == expectH);
                    int dark = 0;
                    for (int x = 0; x < img.getWidth(); x++) {
                        for (int y = 0; y < img.getHeight(); y++) {
                            if ((img.getRGB(x, y) & 0xFF) < 100) {
                                dark++;
                            }
                        }
                    }
                    UndoSmokeTest.check("the far away stroke is in the export (" + dark + " ink pixels)", dark > 500);
                    UndoSmokeTest.check("selection closed after export", !Alchemy.canvas.isSelectingExportArea());
                    UndoSmokeTest.check("export area cleared afterwards", Alchemy.canvas.getExportArea() == null);
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    static AlcExportSelector selector() throws Exception {
        Field f = AlcCanvas.class.getDeclaredField("exportSelector");
        f.setAccessible(true);
        return (AlcExportSelector) f.get(Alchemy.canvas);
    }
}
