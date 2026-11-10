package org.alchemy.core;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/** The noise shows in a small swatch by the noise menu, with a larger tile when it changes. */
public class NoisePreviewTest {

    static AlcNoiseSwatch findSwatch(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof AlcNoiseSwatch) {
                return (AlcNoiseSwatch) child;
            }
            if (child instanceof Container) {
                AlcNoiseSwatch s = findSwatch((Container) child);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    static void shootTile(AlcNoiseSwatch swatch, String name) throws Exception {
        Field f = AlcNoiseSwatch.class.getDeclaredField("popup");
        f.setAccessible(true);
        JWindow popup = (JWindow) f.get(swatch);
        UndoSmokeTest.check(name + ": the large tile opens by the swatch", popup != null && popup.isVisible()
                && Math.abs(popup.getX() - swatch.getLocationOnScreen().x) <= 1);
        if (popup != null) {
            BufferedImage img = new BufferedImage(popup.getWidth(), popup.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            popup.getContentPane().printAll(g);
            g.dispose();
            ImageIO.write(img, "png", new java.io.File(SketchTest.dir, name + ".png"));
        }
    }

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1800, 760);
                Alchemy.window.validate();
                try {
                    MarkerDisplaceTest.click("createButton", "Shapes");
                    MarkerDisplaceTest.click("affectButton", "Displace");
                    Alchemy.toolBar.setToolBarVisible(true);
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        Thread.sleep(900);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Alchemy.toolBar.setToolBarVisible(true);
                    Alchemy.toolBar.calculateTotalHeight();
                    Alchemy.toolBar.resizeToolBar();
                    AlcNoiseSwatch swatch = findSwatch(Alchemy.toolBar);
                    System.out.println("dock " + AlcDock.get().getBounds() + " want " + AlcDock.get().dockWidth() + " window " + Alchemy.window.getSize() + " insets " + Alchemy.window.getInsets() + " canvas " + Alchemy.canvas.getBounds() + " pref " + AlcPreferences.prefs.getInt("Dock Width", -1));
                    for (java.awt.Component a = swatch; a != null; a = a.getParent()) { System.out.println("  up " + a.getClass().getSimpleName() + " visible=" + a.isVisible() + " " + a.getBounds()); }
                    System.out.println("swatch " + swatch + " showing " + (swatch != null && swatch.isShowing()) + " toolbar visible " + Alchemy.toolBar.isToolBarVisible() + " " + Alchemy.toolBar.getBounds());
                    UndoSmokeTest.check("Displace has a noise swatch", swatch != null && swatch.isShowing());
                    DisplaceNoiseTest.findNoise(Alchemy.toolBar).setSelectedIndex(4);
                    shootTile(swatch, "noise-tile-ridged");
                    SketchTest.shot("noise-swatch");
                    // An own image: rings, like a target
                    BufferedImage rings = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = rings.createGraphics();
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, 256, 256);
                    for (int r = 120; r > 0; r -= 24) {
                        g.setColor(((r / 24) % 2 == 0) ? Color.BLACK : Color.WHITE);
                        g.fillOval(128 - r, 128 - r, r * 2, r * 2);
                    }
                    g.dispose();
                    AlcNoise.setCustomImage(rings);
                    DisplaceNoiseTest.findNoise(Alchemy.toolBar).setSelectedIndex(7);
                    shootTile(swatch, "noise-tile-image");
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        // The tile closes by itself
        Thread.sleep(2200);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Field f = AlcNoiseSwatch.class.getDeclaredField("popup");
                    f.setAccessible(true);
                    JWindow popup = (JWindow) f.get(findSwatch(Alchemy.toolBar));
                    UndoSmokeTest.check("the tile closes after a moment", popup == null || !popup.isVisible());
                    DisplaceNoiseTest.findNoise(Alchemy.toolBar).setSelectedIndex(0);
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
