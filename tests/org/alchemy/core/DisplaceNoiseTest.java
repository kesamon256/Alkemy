package org.alchemy.core;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

/** Displace with every noise kind: the same slow stroke, one row each. */
public class DisplaceNoiseTest implements AlcConstants {

    static AlcSubComboBox findNoise(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof AlcSubComboBox) {
                for (Component inner : ((AlcSubComboBox) child).getComponents()) {
                    if (inner instanceof JComboBox && ((JComboBox) inner).getItemCount() >= 7) {
                        return (AlcSubComboBox) child;
                    }
                }
            }
            if (child instanceof Container) {
                AlcSubComboBox b = findNoise((Container) child);
                if (b != null) {
                    return b;
                }
            }
        }
        return null;
    }

    static AlcSubComboBox findNoise2(Container c) {
        return findNoise(c);
    }

    static void stroke(int y) {
        AlcCanvas c = Alchemy.canvas;
        long t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, t, 0, 140, y, 0, false, MouseEvent.NOBUTTON));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, 140, y, 1, false, MouseEvent.BUTTON1));
        int x = 140, yy = y;
        for (int i = 1; i <= 140; i++) {
            x = 140 + i * 6;
            yy = y + (int) Math.round(10 * Math.sin(i / 14.0));
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i * 16, InputEvent.BUTTON1_DOWN_MASK, x, yy, 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 2000, 0, x, yy, 1, false, MouseEvent.BUTTON1));
    }

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1280, 800);
                Alchemy.window.validate();
            }
        });
        Thread.sleep(600);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    AlcCanvas c = Alchemy.canvas;
                    c.setColor(new Color(40, 50, 70));
                    c.setLineWidth(2);
                    MarkerDisplaceTest.click("createButton", "Shapes");
                    MarkerDisplaceTest.click("affectButton", "Displace");
                    AlcSubComboBox noise = findNoise(Alchemy.toolBar);
                    UndoSmokeTest.check("Displace has a noise menu", noise != null);
                    java.util.ArrayList<java.awt.Rectangle> bounds = new java.util.ArrayList<java.awt.Rectangle>();
                    for (int k = 0; k < 7; k++) {
                        noise.setSelectedIndex(k);
                        int before = c.shapes.size();
                        stroke(200 + k * 82);
                        UndoSmokeTest.check("noise " + k + " draws", c.shapes.size() == before + 1);
                        bounds.add(c.shapes.get(c.shapes.size() - 1).getBounds());
                    }
                    java.util.HashSet<String> distinct = new java.util.HashSet<String>();
                    for (java.awt.Rectangle r : bounds) {
                        distinct.add(r.width + "x" + r.height);
                    }
                    UndoSmokeTest.check("the kinds give different strokes (" + distinct + ")", distinct.size() >= 5);
                    noise.setSelectedIndex(0);
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    SketchTest.shot("displace-noise");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
