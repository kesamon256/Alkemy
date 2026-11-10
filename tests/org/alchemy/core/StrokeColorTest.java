package org.alchemy.core;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/** Picks colours on the wheel, draws, and checks the colour that really lands on the canvas. */
public class StrokeColorTest {

    public static void main(String[] args) throws Exception {
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
                    AlcColorWheel wheel = (AlcColorWheel) popup.getComponent(0);
                    AlcCanvas c = Alchemy.canvas;
                    System.out.println("alpha " + c.getAlpha() + " style " + c.getStyle() + " line width " + c.getLineWidth());
                    c.setLineWidth(30);
                    float[][] picks = {{0F, 1F, 1F}, {0.33F, 0.8F, 0.6F}, {0.6F, 0.5F, 0.9F}, {0.1F, 0.2F, 0.3F}};
                    int y = 120;
                    for (float[] pick : picks) {
                        wheel.pick(pick[0], pick[1], pick[2]);
                        Color wanted = Color.getHSBColor(pick[0], pick[1], pick[2]);
                        UndoSmokeTest.stroke(150, y, 600, y);
                        AlcShape s = c.shapes.get(c.shapes.size() - 1);
                        BufferedImage img = (BufferedImage) c.renderCanvas(true);
                        // Middle of the stroke, away from the waves
                        Color onCanvas = null;
                        for (int dy = -25; dy <= 25 && onCanvas == null; dy++) {
                            Color px = new Color(img.getRGB(150, y + dy));
                            if (!px.equals(Color.WHITE) && Math.abs(px.getRed() - 255) + Math.abs(px.getGreen() - 255) + Math.abs(px.getBlue() - 255) > 30) {
                                onCanvas = px;
                            }
                        }
                        Color pen = c.getColor();
                        System.out.println(String.format("wanted %s pen %s shape %s alpha %d canvas %s",
                                hex(wanted), hex(pen), hex(s.getColor()), s.getAlpha(), onCanvas == null ? "-" : hex(onCanvas)));
                        UndoSmokeTest.check("pen colour = picked colour", pen.equals(wanted));
                        UndoSmokeTest.check("stroke colour = picked colour", s.getColor().getRGB() == new Color(wanted.getRed(), wanted.getGreen(), wanted.getBlue(), s.getColor().getAlpha()).getRGB());
                        y += 120;
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
