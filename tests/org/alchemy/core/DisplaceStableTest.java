package org.alchemy.core;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Field;
import javax.swing.SwingUtilities;

/** Displace in the Stable mode: the result depends on where and what is drawn, not on how fast or with how many moves. */
public class DisplaceStableTest {

    /** A straight stroke drawn with the given number of moves */
    static Rectangle2D stroke(int x1, int x2, int y, int steps) {
        AlcCanvas c = Alchemy.canvas;
        int before = c.shapes.size();
        long t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, x1, y, 1, false, MouseEvent.BUTTON1));
        for (int i = 1; i <= steps; i++) {
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + i * (600 / steps), InputEvent.BUTTON1_DOWN_MASK,
                    x1 + (x2 - x1) * i / steps, y, 0, false, MouseEvent.NOBUTTON));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 700, 0, x2, y, 1, false, MouseEvent.BUTTON1));
        Rectangle2D b = null;
        for (AlcShape s : c.shapes.subList(before, c.shapes.size())) {
            b = (b == null) ? s.getPath().getBounds2D() : b.createUnion(s.getPath().getBounds2D());
        }
        return b;
    }

    public static void main(String[] args) throws Exception {
        SketchTest.dir = args[0];
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                AlcCanvas c = Alchemy.canvas;
                try {
                    Alchemy.window.setSize(1400, 860);
                    Alchemy.window.validate();
                    c.setColor(new Color(30, 30, 40));
                    c.setLineWidth(3);
                    c.setStyleMode(AlcCanvas.STYLE_MODE_STROKE);
                    c.resetView();
                    MarkerDisplaceTest.click("createButton", "Shapes");
                    MarkerDisplaceTest.click("affectButton", "Displace");
                    AlcModule displace = null;
                    for (int i = 0; i < Alchemy.plugins.affects.length; i++) {
                        if (Alchemy.plugins.affects[i].getName().equals("Displace")) {
                            displace = Alchemy.plugins.affects[i];
                        }
                    }
                    Field stable = displace.getClass().getDeclaredField("stable");
                    stable.setAccessible(true);
                    boolean oldStable = stable.getBoolean(displace);
                    Field kind = displace.getClass().getDeclaredField("noiseKind");
                    kind.setAccessible(true);
                    int oldKind = kind.getInt(displace);
                    c.clear();
                    try {
                        // STABLE, Perlin noise: few or many moves, the same line
                        stable.setBoolean(displace, true);
                        kind.setInt(displace, 0);
                        Rectangle2D few = stroke(100, 700, 200, 30);
                        Rectangle2D many = stroke(100, 700, 200, 150);
                        UndoSmokeTest.check(String.format("stable: 30 or 150 moves give the same line (height %.1f / %.1f, top %.1f / %.1f)",
                                few.getHeight(), many.getHeight(), few.getMinY(), many.getMinY()),
                                Math.abs(few.getHeight() - many.getHeight()) < 3 && Math.abs(few.getMinY() - many.getMinY()) < 3);
                        UndoSmokeTest.check("stable: the line is moved by the noise (" + Math.round(few.getHeight()) + " px high)", few.getHeight() > 6);
                        // STABLE, Even: a regular wave - the same anywhere
                        kind.setInt(displace, -1);
                        Rectangle2D a = stroke(100, 700, 400, 60);
                        Rectangle2D b = stroke(100, 700, 520, 60);
                        UndoSmokeTest.check(String.format("stable even: the same wave anywhere (%.1f / %.1f)", a.getHeight(), b.getHeight()),
                                Math.abs(a.getHeight() - b.getHeight()) < 1.5 && a.getHeight() > 10);
                        // WILD: the number of moves changes the result
                        stable.setBoolean(displace, false);
                        kind.setInt(displace, 0);
                        Rectangle2D wf = stroke(100, 700, 650, 30);
                        Rectangle2D wm = stroke(100, 700, 760, 150);
                        System.out.println(String.format("wild: 30 moves %.1f px high, 150 moves %.1f px high", wf.getHeight(), wm.getHeight()));
                        SketchTest.shot("displace-stable");
                    } finally {
                        stable.setBoolean(displace, oldStable);
                        kind.setInt(displace, oldKind);
                        MarkerDisplaceTest.click("affectButton", "Displace");
                    }
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
