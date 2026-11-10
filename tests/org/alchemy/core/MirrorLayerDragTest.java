package org.alchemy.core;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import javax.swing.AbstractButton;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/** Dragging layers in the Layers panel, and placing the Mirror axis on the endless canvas. */
public class MirrorLayerDragTest {

    public static void main(String[] args) throws Exception {
        final String dir = args[0];
        SketchTest.dir = dir;
        PartsShot.dir = dir;
        PartsShot.sc = Double.parseDouble(System.getProperty("sun.java2d.uiScale", "1"));
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
                    layers();
                    mirror();
                } catch (Throwable t) {
                    t.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    SketchTest.shot("mirror-axis");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    static void layers() throws Exception {
        AlcCanvas c = Alchemy.canvas;
        c.renameLayer(c.getActiveLayer(), "A");
        c.addLayer();
        c.renameLayer(c.getActiveLayer(), "B");
        c.addLayer();
        c.renameLayer(c.getActiveLayer(), "C");
        AlcLayersPanel.showPanel();
        Field pf = AlcLayersPanel.class.getDeclaredField("panel");
        pf.setAccessible(true);
        AlcLayersPanel panel = (AlcLayersPanel) pf.get(null);
        panel.validate();
        Field rf = AlcLayersPanel.class.getDeclaredField("rows");
        rf.setAccessible(true);
        Container rows = (Container) rf.get(panel);
        // The rows live in the dock on the right of the main window
        Alchemy.window.getRootPane().validate();
        // The list shows C, B, A - drag the top row (C) below the bottom row (A)
        Component top = rows.getComponent(0);
        int h = top.getHeight();
        long t = System.currentTimeMillis();
        top.dispatchEvent(new MouseEvent(top, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, 60, h / 2, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 10; i++) {
            top.dispatchEvent(new MouseEvent(top, MouseEvent.MOUSE_DRAGGED, t + i, InputEvent.BUTTON1_DOWN_MASK, 60, h / 2 + i * h * 3 / 10, 0, false, MouseEvent.NOBUTTON));
        }

        top.dispatchEvent(new MouseEvent(top, MouseEvent.MOUSE_RELEASED, t + 20, 0, 60, h / 2 + 3 * h, 1, false, MouseEvent.BUTTON1));
        String order = c.getLayers().get(0).name + c.getLayers().get(1).name + c.getLayers().get(2).name;
        UndoSmokeTest.check("dragging C to the bottom gives order bottom-up " + order, order.equals("CAB"));
        UndoSmokeTest.check("dragged layer stays selected", c.getActiveLayer().name.equals("C"));
        Alchemy.history.undo();
        order = c.getLayers().get(0).name + c.getLayers().get(1).name + c.getLayers().get(2).name;
        UndoSmokeTest.check("undo the drag (" + order + ")", order.equals("ABC"));
        // Drag B one step up (above C)
        UndoSmokeTest.check("drop target maths: middle row up to the top", panel.targetIndex(1, 0) == 2);
        UndoSmokeTest.check("drop target maths: top row to the bottom", panel.targetIndex(2, 3) == 0);
        UndoSmokeTest.check("drop target maths: same gap is no move", panel.targetIndex(1, 1) == 1 && panel.targetIndex(1, 2) == 1);
        // A short click only selects
        Component bottom = rows.getComponent(2);
        t = System.currentTimeMillis();
        bottom.dispatchEvent(new MouseEvent(bottom, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, 60, 10, 1, false, MouseEvent.BUTTON1));
        bottom.dispatchEvent(new MouseEvent(bottom, MouseEvent.MOUSE_RELEASED, t + 5, 0, 60, 10, 1, false, MouseEvent.BUTTON1));
        UndoSmokeTest.check("click selects the layer", c.getActiveLayer().name.equals("A"));
        c.newDrawing();
    }

    static void mirror() throws Exception {
        AlcCanvas c = Alchemy.canvas;
        // Switch the Mirror affect on
        Field b = AlcToolBar.class.getDeclaredField("affectButton");
        b.setAccessible(true);
        Field p = AlcPopupButton.class.getDeclaredField("popup");
        p.setAccessible(true);
        JPopupMenu affects = (JPopupMenu) p.get(b.get(Alchemy.toolBar));
        for (Component item : affects.getComponents()) {
            if (item instanceof AbstractButton && "Mirror".equals(((AbstractButton) item).getText())) {
                ((AbstractButton) item).doClick();
            }
        }
        Field ax = AlcCanvas.class.getDeclaredField("axisX");
        ax.setAccessible(true);
        double start = ax.getDouble(c);
        UndoSmokeTest.check("mirror shows its axis in the middle of the window (" + Math.round(start) + ")", Math.abs(start - c.getWidth() / 2.0) < 2);

        // Pan and zoom, then drag the axis line to a new place on screen
        c.zoomAt(1.5, 400, 300);
        c.panBy(-300, 0);
        int sx = (int) Math.round(start * c.getViewScale() + c.getViewTransform().getTranslateX());
        long t = System.currentTimeMillis();
        // Drawing across the line in the middle draws - it does not grab the axis
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, sx, 400, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 10; i++) {
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i, InputEvent.BUTTON1_DOWN_MASK, sx + i * 20, 400, 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 20, 0, sx + 200, 400, 1, false, MouseEvent.BUTTON1));
        UndoSmokeTest.check("a stroke across the axis line leaves the axis where it is", Math.abs(ax.getDouble(c) - start) < 0.01 && c.hasAnyShapes());
        Alchemy.history.undo();
        // The handle at the bottom moves it
        int hy = c.getVisibleRect().height - 18;
        t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, sx, hy, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 10; i++) {
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i, InputEvent.BUTTON1_DOWN_MASK, sx + i * 20, hy, 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 20, 0, sx + 200, hy, 1, false, MouseEvent.BUTTON1));
        double moved = ax.getDouble(c);
        double expected = c.toWorld(sx + 200, hy).x;
        UndoSmokeTest.check(String.format("dragging the axis moves it (%.0f -> %.0f, expected %.0f)", start, moved, expected), Math.abs(moved - expected) < 1);
        UndoSmokeTest.check("dragging the axis draws nothing", !c.hasAnyShapes());

        // Draw left of the axis: the copy is mirrored around the new axis
        int axisScreen = (int) Math.round(moved * c.getViewScale() + c.getViewTransform().getTranslateX());
        UndoSmokeTest.stroke(axisScreen - 300, 300, axisScreen - 100, 320);
        UndoSmokeTest.check("stroke and its mirror image (" + c.shapes.size() + " shapes)", c.shapes.size() == 2);
        Rectangle original = c.shapes.get(0).getBounds();
        Rectangle mirrored = c.shapes.get(1).getBounds();
        double left = moved - original.getMaxX(), right = mirrored.getMinX() - moved;
        UndoSmokeTest.check(String.format("mirror copy is reflected around the axis (gaps %.0f / %.0f)", left, right), Math.abs(left - right) < 3 && right > 0);
        c.resetView();
    }
}
