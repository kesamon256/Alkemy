package org.alchemy.core;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import javax.swing.JDialog;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** The user's flow: pick in the triangle, then turn the ring - with the floating panel open. */
public class WheelFlowTest {

    public static void main(String[] args) throws Exception {
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                AlcColorPanel.showPanel();
            }
        });
        Thread.sleep(800);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Field pf = AlcColorPanel.class.getDeclaredField("panel");
                    pf.setAccessible(true);
                    JDialog panel = (JDialog) pf.get(null);
                    Component w = WheelTest.find(panel.getContentPane(), "Wheel");
                    int cx = w.getWidth() / 2, cy = w.getHeight() / 2;
                    // Give the pen a colour first: click the ring at the bottom (cyan)
                    drag(w, cx, cy + w.getWidth() / 2 - 12, cx, cy + w.getWidth() / 2 - 12);
                    // Pick a muted colour in the middle of the triangle, with a little drag
                    drag(w, cx - 10, cy + 10, cx - 8, cy + 12);
                    Color picked = Alchemy.canvas.getColor();
                    float[] p = Color.RGBtoHSB(picked.getRed(), picked.getGreen(), picked.getBlue(), null);
                    UndoSmokeTest.check(String.format("triangle pick is a muted colour (s %.2f b %.2f)", p[1], p[2]), p[1] > 0.1F && p[1] < 0.9F && p[2] > 0.1F && p[2] < 0.9F);
                    // The reported bug: the hex field had the keyboard, and losing it put the old colour back
                    JTextField hexField = (JTextField) WheelTest.find(panel.getContentPane(), "JTextField");
                    hexField.dispatchEvent(new java.awt.event.FocusEvent(hexField, java.awt.event.FocusEvent.FOCUS_LOST));
                    UndoSmokeTest.check("hex field shows the picked colour (" + hexField.getText() + ")", hexField.getText().equals(WheelAccuracyTest.hex(picked)));
                    // Nothing changes the colour after the pick (other wheels, focus, toolbar)
                    Thread.sleep(300);
                    UndoSmokeTest.check("colour stays after release (" + WheelAccuracyTest.hex(picked) + " -> " + WheelAccuracyTest.hex(Alchemy.canvas.getColor()) + ")", Alchemy.canvas.getColor().equals(picked));
                    // Turn the ring from the right round to the left
                    int r = w.getWidth() / 2 - 12;
                    drag(w, cx + r, cy, cx - r, cy);
                    Color turned = Alchemy.canvas.getColor();
                    float[] t = Color.RGBtoHSB(turned.getRed(), turned.getGreen(), turned.getBlue(), null);
                    UndoSmokeTest.check(String.format("ring keeps saturation and brightness (%.2f/%.2f -> %.2f/%.2f)", p[1], p[2], t[1], t[2]),
                            Math.abs(t[1] - p[1]) < 0.03F && Math.abs(t[2] - p[2]) < 0.03F);
                    UndoSmokeTest.check(String.format("ring changed the hue (%.2f -> %.2f)", p[0], t[0]), Math.abs(t[0] - p[0]) > 0.2F);
                    // A black pen: the ring gives the full colour
                    Alchemy.canvas.setColor(Color.BLACK);
                    Alchemy.toolBar.refreshColorButton();
                    drag(w, cx + r, cy, cx + r, cy);
                    Color fromBlack = Alchemy.canvas.getColor();
                    UndoSmokeTest.check("black pen + ring gives a bright colour (" + WheelAccuracyTest.hex(fromBlack) + ")", Math.max(fromBlack.getRed(), Math.max(fromBlack.getGreen(), fromBlack.getBlue())) > 240);
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    static void drag(Component c, int x1, int y1, int x2, int y2) {
        long t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, x1, y1, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= 10; i++) {
            // Go round the ring through the top when the ends are opposite
            double a1 = Math.atan2(y1 - c.getHeight() / 2.0, x1 - c.getWidth() / 2.0);
            int x = x1 + (x2 - x1) * i / 10, y = y1 + (y2 - y1) * i / 10;
            if (x1 != x2 && y1 == y2 && Math.abs(x1 - x2) > c.getWidth() / 2) {
                double r = Math.hypot(x1 - c.getWidth() / 2.0, y1 - c.getHeight() / 2.0);
                double a = a1 - Math.PI * i / 10;
                x = (int) Math.round(c.getWidth() / 2.0 + Math.cos(a) * r);
                y = (int) Math.round(c.getHeight() / 2.0 + Math.sin(a) * r);
            }
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i, InputEvent.BUTTON1_DOWN_MASK, x, y, 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 20, 0, x2, y2, 1, false, MouseEvent.BUTTON1));
    }
}
