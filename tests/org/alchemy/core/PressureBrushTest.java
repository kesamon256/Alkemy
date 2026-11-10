package org.alchemy.core;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.List;
import javax.swing.SwingUtilities;

/** Every brush with a light and a firm pen - a firm press must lay down clearly more. */
public class PressureBrushTest implements AlcConstants {

    static void penStroke(int x1, int x2, int y, float pressure) {
        AlcCanvas c = Alchemy.canvas;
        long t = System.currentTimeMillis();
        AlcWindowsInk.update(true, false, true, pressure, 0, 0);
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, t, 0, x1, y, 0, false, MouseEvent.NOBUTTON));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, x1, y, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 60; i++) {
            AlcWindowsInk.update(true, false, true, pressure, 0, 0);
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i * 10, InputEvent.BUTTON1_DOWN_MASK,
                    x1 + (x2 - x1) * i / 60, y + (int) Math.round(6 * Math.sin(i / 8.0)), 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 700, 0, x2, y, 1, false, MouseEvent.BUTTON1));
        AlcWindowsInk.update(false, false, false, 0, 0, 0);
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, t + 710, 0, x2 + 5, y, 0, false, MouseEvent.NOBUTTON));
    }

    /** How much ink the shapes put down - the sum of their coverage */
    static double ink(List<AlcShape> shapes) {
        BufferedImage img = new BufferedImage(1600, 1000, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (AlcShape s : shapes) {
            if (s.isFilled()) {
                g.setPaint(s.getFillPaint());
                g.fill(s.getPath());
            }
            if (s.isStroked()) {
                g.setStroke(s.getStroke());
                g.setPaint(s.getPaint());
                g.draw(s.getPath());
            }
        }
        g.dispose();
        double sum = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                sum += (img.getRGB(x, y) >>> 24) / 255.0;
            }
        }
        return sum;
    }

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1500, 900);
                Alchemy.window.validate();
            }
        });
        Thread.sleep(500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    AlcCanvas c = Alchemy.canvas;
                    c.setColor(new Color(30, 30, 40));
                    c.setLineWidth(8);
                    c.setPressureEnabled(true);
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    MarkerDisplaceTest.click("createButton", "Brushes");
                    AlcModule brushes = Alchemy.plugins.creates[Alchemy.plugins.currentCreate];
                    Method select = brushes.getClass().getDeclaredMethod("selectBrush", int.class);
                    select.setAccessible(true);
                    String[] names = {"pencil", "ink", "marker", "charcoal", "liner", "brush pen", "sketchy", "hatching"};
                    for (int i = 0; i < names.length; i++) {
                        select.invoke(brushes, i);
                        int a = c.shapes.size();
                        penStroke(100, 500, 120 + i * 90, 0.15F);
                        int b = c.shapes.size();
                        penStroke(600, 1000, 120 + i * 90, 1F);
                        int e = c.shapes.size();
                        double light = ink(c.shapes.subList(a, b)), firm = ink(c.shapes.subList(b, e));
                        UndoSmokeTest.check(String.format("%s follows the pressure (light %.0f, firm %.0f)", names[i], light, firm), firm > light * 1.6);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    SketchTest.shot("brush-pressure");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
