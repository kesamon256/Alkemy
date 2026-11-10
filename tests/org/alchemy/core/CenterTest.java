package org.alchemy.core;

import java.awt.geom.Rectangle2D;
import javax.swing.SwingUtilities;

/** The Center button shows the whole drawing in the middle of the window. */
public class CenterTest {

    public static void main(String[] args) throws Exception {
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    AlcCanvas c = Alchemy.canvas;
                    c.centerView();
                    UndoSmokeTest.check("empty canvas -> 100%", !c.isCanvasZoomed());
                    // Two strokes far apart: draw one, pan far away, draw another
                    UndoSmokeTest.stroke(100, 100, 300, 120);
                    c.panBy(-1500, -900);
                    UndoSmokeTest.stroke(400, 400, 600, 420);
                    c.centerView();
                    Rectangle2D all = c.shapes.get(0).path.getBounds2D().createUnion(c.shapes.get(1).path.getBounds2D());
                    Rectangle2D onScreen = c.getViewTransform().createTransformedShape(all).getBounds2D();
                    UndoSmokeTest.check(String.format("whole drawing fits the window at %.0f %%", c.getViewScale() * 100),
                            onScreen.getMinX() >= 0 && onScreen.getMinY() >= 0 && onScreen.getMaxX() <= c.getWidth() && onScreen.getMaxY() <= c.getHeight());
                    UndoSmokeTest.check("drawing is centred", Math.abs(onScreen.getCenterX() - c.getWidth() / 2.0) < 2 && Math.abs(onScreen.getCenterY() - c.getHeight() / 2.0) < 2);
                    // A small drawing is not blown up past 100%
                    c.clear();
                    UndoSmokeTest.stroke(200, 200, 260, 210);
                    c.centerView();
                    UndoSmokeTest.check("small drawing stays at 100%", Math.abs(c.getViewScale() - 1) < 0.001);
                } catch (Throwable t) {
                    t.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }
}
