package org.alchemy.core;

import java.awt.Color;
import javax.swing.SwingUtilities;

/** The colour and layers panels docked on the right, popped out and closed. */
public class DockTest {

    static void step(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
        Thread.sleep(400);
    }

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        step(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1400, 820);
                Alchemy.window.validate();
                // Start from the default: both docked
                AlcDock.get().colour.setMode(AlcDock.DOCKED);
                AlcDock.get().layers.setMode(AlcDock.DOCKED);
            }
        });
        step(new Runnable() {
            public void run() {
                try {
                    AlcDock dock = AlcDock.get();
                    AlcCanvas c = Alchemy.canvas;
                    UndoSmokeTest.check("the dock is shown", AlcDock.isShown() && dock.getWidth() > 200);
                    UndoSmokeTest.check("the canvas ends where the dock starts (" + (c.getX() + c.getWidth()) + " / " + dock.getX() + ")",
                            Math.abs(c.getX() + c.getWidth() - dock.getX()) <= 1);
                    UndoSmokeTest.check("both panels are docked", dock.colour.isDocked() && dock.layers.isDocked());
                    UndoSmokeTest.check("the menu knows they are open", AlcColorPanel.isPanelOpen() && AlcLayersPanel.isPanelOpen());
                    // Draw something beside the dock
                    c.setColor(new Color(60, 70, 160));
                    c.setLineWidth(5);
                    MarkerDisplaceTest.click("createButton", "Brushes");
                    BrushTest.stroke(120, 700, 400, false);
                    Alchemy.canvas.addLayer();
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        step(new Runnable() {
            public void run() {
                // Dragging the edge makes the dock wider - the canvas gives way
                AlcDock.get().setDockWidth(420, true);
            }
        });
        step(new Runnable() {
            public void run() {
                AlcDock dock = AlcDock.get();
                AlcCanvas c = Alchemy.canvas;
                UndoSmokeTest.check("the dock is 420 wide (" + dock.getWidth() + ")", dock.getWidth() == 420);
                UndoSmokeTest.check("the canvas still ends at the dock", Math.abs(c.getX() + c.getWidth() - dock.getX()) <= 1);
                try {
                    SketchTest.shot("dock-wide");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                dock.setDockWidth(100, true);
            }
        });
        step(new Runnable() {
            public void run() {
                UndoSmokeTest.check("it does not get narrower than " + AlcDock.MIN_WIDTH, AlcDock.get().getWidth() == AlcDock.MIN_WIDTH);
                // Back to the width the panels need
                AlcPreferences.prefs.remove("Dock Width");
                Alchemy.window.layoutDock(true);
                AlcDock.get().layers.setMode(AlcDock.FLOATING);
            }
        });
        step(new Runnable() {
            public void run() {
                AlcDock dock = AlcDock.get();
                UndoSmokeTest.check("layers float in their own window", dock.layers.isOpen() && !dock.layers.isDocked());
                UndoSmokeTest.check("the colours stay docked", AlcDock.isShown() && dock.colour.isDocked());
                AlcDock.get().layers.setMode(AlcDock.DOCKED);
                AlcColorPanel.togglePanel();
            }
        });
        step(new Runnable() {
            public void run() {
                UndoSmokeTest.check("F6 closes the colours", !AlcColorPanel.isPanelOpen() && AlcDock.isShown());
                AlcLayersPanel.togglePanel();
            }
        });
        step(new Runnable() {
            public void run() {
                AlcCanvas c = Alchemy.canvas;
                UndoSmokeTest.check("with both closed the dock goes and the canvas fills the window",
                        !AlcDock.isShown() && c.getWidth() >= Alchemy.window.getContentPane().getWidth());
                AlcColorPanel.togglePanel();
                AlcLayersPanel.togglePanel();
            }
        });
        step(new Runnable() {
            public void run() {
                UndoSmokeTest.check("both come back docked", AlcDock.get().colour.isDocked() && AlcDock.get().layers.isDocked());
                try {
                    SketchTest.shot("dock");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
