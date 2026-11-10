package org.alchemy.core;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Renders the Create/Affect menus, the module bar with several affects on, and the preferences window. */
public class PartsShot {

    static String dir;
    static double sc;

    public static void main(String[] args) throws Exception {
        dir = args[0];
        sc = Double.parseDouble(System.getProperty("sun.java2d.uiScale", "1"));
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
                    JPopupMenu create = popup("createButton");
                    JPopupMenu affect = popup("affectButton");
                    render(create, "part-create-menu");
                    render(affect, "part-affect-menu");
                    // Switch on a few affects so their options show in the module bar
                    for (Component c : affect.getComponents()) {
                        if (c instanceof AbstractButton) {
                            String t = ((AbstractButton) c).getText();
                            if ("Mirror".equals(t) || "Random".equals(t) || "Repeat".equals(t)) {
                                ((AbstractButton) c).doClick();
                            }
                        }
                    }
                    Alchemy.toolBar.setToolBarVisible(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        Thread.sleep(800);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    render(Alchemy.window.getRootPane(), "part-modules");
                    Alchemy.preferences.showWindow();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        Thread.sleep(1200);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Field f = AlcPreferences.class.getDeclaredField("prefsWindow");
                    f.setAccessible(true);
                    render(((RootPaneContainer) f.get(Alchemy.preferences)).getRootPane(), "part-preferences");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        System.exit(0);
    }

    static JPopupMenu popup(String buttonField) throws Exception {
        Field b = AlcToolBar.class.getDeclaredField(buttonField);
        b.setAccessible(true);
        Object button = b.get(Alchemy.toolBar);
        Field p = AlcPopupButton.class.getDeclaredField("popup");
        p.setAccessible(true);
        return (JPopupMenu) p.get(button);
    }

    static void layout(Component c) {
        if (c instanceof Container) {
            ((Container) c).doLayout();
            for (Component k : ((Container) c).getComponents()) {
                layout(k);
            }
        }
    }

    static void render(JComponent c, String name) throws Exception {
        if (c.getWidth() == 0) {
            c.setSize(c.getPreferredSize());
        }
        layout(c);
        BufferedImage img = new BufferedImage((int) (c.getWidth() * sc), (int) (c.getHeight() * sc), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(sc, sc);
        c.printAll(g);
        g.dispose();
        ImageIO.write(img, "png", new File(dir, name + ".png"));
        System.out.println("saved " + name + " " + img.getWidth() + "x" + img.getHeight());
    }
}
