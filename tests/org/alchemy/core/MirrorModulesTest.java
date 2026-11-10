package org.alchemy.core;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;

/** Which create modules leave a mirror image when Mirror is on - a stroke left of the axis must
 *  leave shapes right of it too. */
public class MirrorModulesTest {

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1500, 860);
                Alchemy.window.validate();
            }
        });
        Thread.sleep(500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                AlcCanvas c = Alchemy.canvas;
                try {
                    c.setColor(new Color(30, 30, 40));
                    c.setLineWidth(4);
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    MarkerDisplaceTest.click("affectButton", "Mirror");
                    java.lang.reflect.Field ax = AlcCanvas.class.getDeclaredField("axisX");
                    ax.setAccessible(true);
                    int axis = (int) Math.round(ax.getDouble(c));
                    for (AlcModule m : Alchemy.plugins.creates) {
                        String name = m.getName();
                        if (name.contains("Mic") || name.contains("Type") || name.contains("Trace") || name.contains("Pitch")) {
                            continue;
                        }
                        try {
                            MarkerDisplaceTest.click("createButton", name);
                        } catch (Throwable e) {
                            System.out.println("cannot pick " + name);
                            continue;
                        }
                        c.clear();
                        long t = System.currentTimeMillis();
                        int y = 300;
                        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, 150, y, 1, false, MouseEvent.BUTTON1));
                        for (int i = 1; i <= 40; i++) {
                            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i * (i % 3 == 0 ? 25 : 8), InputEvent.BUTTON1_DOWN_MASK,
                                    150 + i * 7, y + (int) (40 * Math.sin(i / 5.0)), 0, false, MouseEvent.NOBUTTON));
                        }
                        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 900, 0, 430, y, 1, false, MouseEvent.BUTTON1));
                        int left = 0, right = 0;
                        java.util.List<AlcShape> all = new java.util.ArrayList<AlcShape>(c.shapes);
                        all.addAll(c.createShapes);
                        all.addAll(c.affectShapes);
                        for (AlcShape s : all) {
                            Rectangle b = s.getBounds();
                            if (b.getCenterX() < axis) {
                                left++;
                            } else {
                                right++;
                            }
                        }
                        System.out.println(String.format("%-14s left %3d right %3d %s", name, left, right, left > 0 && right == 0 ? "<-- NO MIRROR" : ""));
                        if (name.startsWith("Ribbon")) {
                            for (AlcShape s : all) {
                                System.out.println("   " + s.getClass().getSimpleName() + " " + s.getBounds() + " spine " + (s.getSpine() == null ? "null" : s.getSpine().size()) + " in " + (c.shapes.contains(s) ? "shapes" : c.createShapes.contains(s) ? "create" : "affect"));
                            }
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.exit(0);
    }
}
