package org.alchemy.core;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;

/** Blur on a solid shape: full colour inside, soft edge all round (also the straight closing edge), no dark rim. */
public class BlurSolidTest {

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SmoothRepeatTest.held = AlcCanvas.class.getDeclaredField("motionHeld");
        SmoothRepeatTest.held.setAccessible(true);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Alchemy.window.setSize(1400, 860);
                    Alchemy.window.validate();
                    AlcCanvas c = Alchemy.canvas;
                    c.setMotionHold(true);
                    c.resetView();
                    c.setColor(new Color(30, 30, 35));
                    c.setLineWidth(8);
                    MarkerDisplaceTest.click("createButton", "Shapes");
                    c.clear();
                    c.setStyleMode(AlcCanvas.STYLE_MODE_FILL);
                    // A zigzag outline like a quick solid sketch - open at the top, closed by a straight edge
                    int[][] pts = {{200, 150}, {330, 230}, {420, 300}, {300, 420}, {230, 520}, {330, 560}, {500, 580}, {540, 520}, {420, 480}, {500, 380}, {470, 280}, {390, 180}};
                    long t = System.currentTimeMillis();
                    c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, t, 0, pts[0][0], pts[0][1], 0, false, MouseEvent.NOBUTTON));
                    c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, pts[0][0], pts[0][1], 1, false, MouseEvent.BUTTON1));
                    for (int i = 1; i < pts.length; i++) {
                        for (int s = 1; s <= 6; s++) {
                            int x = pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * s / 6, y = pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * s / 6;
                            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i * 60 + s * 10, InputEvent.BUTTON1_DOWN_MASK, x, y, 0, false, MouseEvent.NOBUTTON));
                        }
                    }
                    c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 900, 0, pts[pts.length - 1][0], pts[pts.length - 1][1], 1, false, MouseEvent.BUTTON1));
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    MarkerDisplaceTest.click("affectButton", "Blur");
                } catch (Throwable e) {
                    e.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        for (int i = 0; i < 4; i++) {
            SmoothRepeatTest.hover(400, 480, true);
        }
        SmoothRepeatTest.hover(400, 480, false);
        final BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                AlcCanvas c = Alchemy.canvas;
                img[0] = (BufferedImage) c.renderCanvas(true, false, 1, c.getWidth(), c.getHeight());
            }
        });
        int inside = img[0].getRGB(400, 480) & 0xFF, edgeOut = img[0].getRGB(400, 140) & 0xFF;
        // Brightness from the deep inside out across the left edge of the lower lobe
        StringBuilder profile = new StringBuilder();
        int last = -1;
        boolean monotone = true;
        for (int x = 330; x >= 180; x -= 6) {
            int v = img[0].getRGB(x, 540) & 0xFF;
            profile.append(v).append(' ');
            if (last >= 0 && v < last - 3) {
                monotone = false;
            }
            last = v;
        }
        UndoSmokeTest.check("inside stays full colour (" + inside + ")", inside < 60);
        UndoSmokeTest.check("from inside out it only gets lighter - no dark rim (" + profile + ")", monotone);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    SketchTest.shot("blur-solid");
                    MarkerDisplaceTest.click("affectButton", "Blur");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
