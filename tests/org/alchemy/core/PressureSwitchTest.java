package org.alchemy.core;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.SwingUtilities;

/** The size / opacity pressure switches in the tool bar, in step with the Sketch menu. */
public class PressureSwitchTest {

    static void find(Container c, ArrayList<AlcPressureSliders.PressureSwitch> out) {
        for (Component child : c.getComponents()) {
            if (child instanceof AlcPressureSliders.PressureSwitch && child.isShowing()) {
                out.add((AlcPressureSliders.PressureSwitch) child);
            }
            if (child instanceof Container) {
                find((Container) child, out);
            }
        }
    }

    static JCheckBoxMenuItem menuItem(String title) {
        for (int m = 0; m < Alchemy.menuBar.getMenuCount(); m++) {
            JMenu menu = Alchemy.menuBar.getMenu(m);
            if (menu == null) {
                continue;
            }
            for (Component item : menu.getMenuComponents()) {
                if (item instanceof JCheckBoxMenuItem && title.equals(((JCheckBoxMenuItem) item).getText())) {
                    return (JCheckBoxMenuItem) item;
                }
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Alchemy.window.setSize(1900, 760);
                    Alchemy.window.validate();
                    MarkerDisplaceTest.click("createButton", "Brushes");
                    Alchemy.toolBar.setToolBarVisible(true);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                AlcCanvas c = Alchemy.canvas;
                boolean oldOn = c.isPressureEnabled(), oldSize = c.isPressureSize(), oldOp = c.isPressureOpacity();
                try {
                    ArrayList<AlcPressureSliders.PressureSwitch> sw = new ArrayList<AlcPressureSliders.PressureSwitch>();
                    find(Alchemy.toolBar, sw);
                    UndoSmokeTest.check("two switches in the Brushes bar (" + sw.size() + ")", sw.size() == 2);
                    JCheckBoxMenuItem sizeItem = menuItem(Alchemy.bundle.getString("pressureSizeTitle"));
                    JCheckBoxMenuItem opItem = menuItem(Alchemy.bundle.getString("pressureOpacityTitle"));
                    UndoSmokeTest.check("menu items found", sizeItem != null && opItem != null);
                    c.setPressureEnabled(true);
                    c.setPressureEffects(true, true);
                    UndoSmokeTest.check("both switches show on", sw.get(0).isSelected() && sw.get(1).isSelected());
                    sw.get(0).doClick();
                    UndoSmokeTest.check("size switch off - size off, opacity stays, menu follows",
                            !c.isPressureSize() && c.isPressureOpacity() && !sizeItem.isSelected() && opItem.isSelected());
                    opItem.doClick();
                    UndoSmokeTest.check("menu turns opacity off - the switch follows", !c.isPressureOpacity() && !sw.get(1).isSelected());
                    c.setPressureEnabled(false);
                    sw.get(1).doClick();
                    UndoSmokeTest.check("switching on also turns the pen pressure on", c.isPressureEnabled() && c.isPressureOpacity() && sw.get(1).isSelected());
                    c.setPressureEffects(true, false);
                    SketchTest.shot("pressure-switches");
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                } finally {
                    c.setPressureEnabled(oldOn);
                    c.setPressureEffects(oldSize, oldOp);
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
