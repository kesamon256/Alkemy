package org.alchemy.core;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/** A long menu splits into columns that fit on the screen instead of scrolling. */
public class MenuColumnsTest {

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1400, 760);
                Alchemy.toolBar.setToolBarVisible(true);
            }
        });
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.toolBar.setToolBarVisible(true);
                Alchemy.toolBar.calculateTotalHeight();
                Alchemy.toolBar.resizeToolBar();
            }
        });
        Thread.sleep(600);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Field b = AlcToolBar.class.getDeclaredField("createButton");
                    b.setAccessible(true);
                    AlcPopupButton create = (AlcPopupButton) b.get(Alchemy.toolBar);
                    Field p = AlcPopupButton.class.getDeclaredField("popup");
                    p.setAccessible(true);
                    JPopupMenu menu = (JPopupMenu) p.get(create);
                    menu.show(create, 0, AlcPopupMenu.uiPopupMenuY);
                    GraphicsConfiguration gc = create.getGraphicsConfiguration();
                    Rectangle screen = gc.getBounds();
                    int bottom = screen.y + screen.height - Toolkit.getDefaultToolkit().getScreenInsets(gc).bottom;
                    Rectangle shown = new Rectangle(menu.getLocationOnScreen(), menu.getSize());
                    System.out.println("menu " + shown + " screen bottom " + bottom + " items " + menu.getComponentCount());
                    UndoSmokeTest.check("the whole menu fits above the bottom of the screen", shown.y + shown.height <= bottom);
                    UndoSmokeTest.check("every item is in the menu itself - no scroll arrows",
                            menu.getComponent(0) instanceof AlcRadioButtonMenuItem && menu.getComponent(menu.getComponentCount() - 1).isShowing());
                    BufferedImage img = new BufferedImage(menu.getWidth(), menu.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = img.createGraphics();
                    menu.printAll(g);
                    g.dispose();
                    ImageIO.write(img, "png", new java.io.File(SketchTest.dir, "menu-columns.png"));
                    menu.setVisible(false);
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
