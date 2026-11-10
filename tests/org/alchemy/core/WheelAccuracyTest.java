package org.alchemy.core;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/** Clicks many points of the wheel and compares the picked colour with the pixel shown there. */
public class WheelAccuracyTest {

    public static void main(String[] args) throws Exception {
        final String dir = args[0];
        final double sc = Double.parseDouble(System.getProperty("sun.java2d.uiScale", "1"));
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Field b = AlcToolBar.class.getDeclaredField("colorWheelButton");
                    b.setAccessible(true);
                    Field p = AlcPopupButton.class.getDeclaredField("popup");
                    p.setAccessible(true);
                    JPopupMenu popup = (JPopupMenu) p.get(b.get(Alchemy.toolBar));
                    popup.setSize(popup.getPreferredSize());
                    PartsShot.layout(popup);
                    Component w = WheelTest.find((AlcColorWheel) popup.getComponent(0), "Wheel");
                    int worst = 0;
                    String worstAt = "";
                    int tested = 0;
                    // A set of hues, then points inside the triangle for each
                    for (int step = 0; step < 6; step++) {
                        // Pick a hue on the ring first
                        double angle = Math.toRadians(step * 60 - 90);
                        double mid = w.getWidth() / 2.0 - 12;
                        WheelTest.press(w, (int) Math.round(w.getWidth() / 2.0 + Math.cos(angle) * mid), (int) Math.round(w.getHeight() / 2.0 + Math.sin(angle) * mid));
                        BufferedImage img = render(w, sc);
                        for (int y = 20; y < w.getHeight() - 20; y += 13) {
                            for (int x = 20; x < w.getWidth() - 20; x += 13) {
                                double d = Math.hypot(x - w.getWidth() / 2.0, y - w.getHeight() / 2.0);
                                if (d > w.getWidth() / 2.0 - 30) {
                                    continue;
                                }
                                Color shown = new Color(img.getRGB((int) ((x + 0.5) * sc), (int) ((y + 0.5) * sc)));
                                // Skip pixels covered by the markers and the triangle edge
                                if (img.getRGB((int) ((x + 0.5) * sc), (int) ((y + 0.5) * sc)) == 0) {
                                    continue;
                                }
                                long t = System.currentTimeMillis();
                                w.dispatchEvent(new MouseEvent(w, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
                                Color picked = Alchemy.canvas.getColor();
                                w.dispatchEvent(new MouseEvent(w, MouseEvent.MOUSE_RELEASED, t, 0, x, y, 1, false, MouseEvent.BUTTON1));
                                int diff = Math.abs(shown.getRed() - picked.getRed()) + Math.abs(shown.getGreen() - picked.getGreen()) + Math.abs(shown.getBlue() - picked.getBlue());
                                tested++;
                                if (diff > worst) {
                                    worst = diff;
                                    worstAt = String.format("hue step %d at %d,%d shown %s picked %s", step, x, y, hex(shown), hex(picked));
                                }
                                // Picking changes the markers, render again
                                img = render(w, sc);
                            }
                        }
                    }
                    System.out.println("tested " + tested + " points, worst difference " + worst + " (" + worstAt + ")");
                    UndoSmokeTest.check("picked colour matches the colour under the pen (worst " + worst + ")", worst <= 24);
                    ImageIO.write(render(w, sc), "png", new File(dir, "wheel-accuracy.png"));
                } catch (Throwable t) {
                    t.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    /** Render the wheel without markers by hiding them is not possible - sample far from the markers instead */
    static BufferedImage render(Component w, double sc) {
        BufferedImage img = new BufferedImage((int) (w.getWidth() * sc), (int) (w.getHeight() * sc), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.scale(sc, sc);
        w.paint(g);
        g.dispose();
        return img;
    }

    static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
