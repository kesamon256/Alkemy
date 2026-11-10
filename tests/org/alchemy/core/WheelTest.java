package org.alchemy.core;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Colour wheel: dragging on the ring / square and typing a hex code set the pen colour. */
public class WheelTest {

    public static void main(String[] args) throws Exception {
        PartsShot.dir = args[0];
        PartsShot.sc = Double.parseDouble(System.getProperty("sun.java2d.uiScale", "1"));
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1280, 760);
                Alchemy.window.validate();
                Alchemy.toolBar.setToolBarVisible(true);
            }
        });
        Thread.sleep(800);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    Field b = AlcToolBar.class.getDeclaredField("colorWheelButton");
                    b.setAccessible(true);
                    Object button = b.get(Alchemy.toolBar);
                    Field p = AlcPopupButton.class.getDeclaredField("popup");
                    p.setAccessible(true);
                    JPopupMenu popup = (JPopupMenu) p.get(button);
                    AlcColorWheel wheel = (AlcColorWheel) popup.getComponent(0);
                    popup.setSize(popup.getPreferredSize());
                    PartsShot.layout(popup);
                    Component w = find(wheel, "Wheel");
                    // Drag on the ring at the right side: hue 0.25 (green-yellow) - 90 degrees clockwise from the top red
                    int cx = w.getWidth() / 2, cy = w.getHeight() / 2;
                    int ringR = w.getWidth() / 2 - 12;
                    Alchemy.canvas.setColor(Color.BLACK);
                    wheel.syncFromCanvas();
                    press(w, cx + ringR, cy);
                    UndoSmokeTest.check("ring click on a black pen gives the ring colour (" + WheelAccuracyTest.hex(Alchemy.canvas.getColor()) + ")", Alchemy.canvas.getColor().equals(Color.getHSBColor(0.25F, 1F, 1F)) || Alchemy.canvas.getColor().getGreen() > 240);
                    Color c1 = Alchemy.canvas.getColor();
                    float[] hsb = Color.RGBtoHSB(c1.getRed(), c1.getGreen(), c1.getBlue(), null);
                    UndoSmokeTest.check(String.format("ring sets the hue (%.2f)", hsb[0]), Math.abs(hsb[0] - 0.25F) < 0.02F || hsb[1] < 0.01F);
                    // Near the pure hue corner of the triangle (it turned to the right with hue 0.25)
                    press(w, cx + 72, cy);
                    Color c2 = Alchemy.canvas.getColor();
                    hsb = Color.RGBtoHSB(c2.getRed(), c2.getGreen(), c2.getBlue(), null);
                    UndoSmokeTest.check(String.format("square sets saturation %.2f and brightness %.2f", hsb[1], hsb[2]), hsb[1] > 0.9F && hsb[2] > 0.9F && Math.abs(hsb[0] - 0.25F) < 0.03F);
                    // Typed hex code
                    JTextField hex = (JTextField) find(wheel, "JTextField");
                    type(hex, "#3366CC");
                    hex.postActionEvent();
                    UndoSmokeTest.check("hex code sets the pen colour", Alchemy.canvas.getColor().equals(new Color(0x33, 0x66, 0xCC)));
                    type(hex, "nonsense");
                    hex.postActionEvent();
                    UndoSmokeTest.check("invalid hex is put back", hex.getText().equals("#3366CC"));
                    // Strokes use the new colour
                    UndoSmokeTest.stroke(200, 400, 600, 420);
                    Color stroke = Alchemy.canvas.shapes.get(Alchemy.canvas.shapes.size() - 1).getColor();
                    UndoSmokeTest.check("new strokes use the picked colour", stroke.getRGB() == new Color(0x33, 0x66, 0xCC, stroke.getAlpha()).getRGB());
                    // Floating panel stays in step with the popup wheel
                    AlcColorPanel.showPanel();
                    UndoSmokeTest.check("colour panel opens", AlcColorPanel.isPanelOpen());
                    Field pf = AlcColorPanel.class.getDeclaredField("panel");
                    pf.setAccessible(true);
                    javax.swing.JDialog panel = (javax.swing.JDialog) pf.get(null);
                    Field wf = AlcColorPanel.class.getDeclaredField("wheel");
                    wf.setAccessible(true);
                    JTextField panelHex = (JTextField) find((java.awt.Container) wf.get(panel), "JTextField");
                    wheel.pick(0.0F, 1F, 1F);
                    UndoSmokeTest.check("panel follows a pick in the popup (" + panelHex.getText() + ")", panelHex.getText().equals("#FF0000"));
                    Alchemy.canvas.setColor(new Color(0, 128, 0));
                    Alchemy.toolBar.refreshColorButton();
                    UndoSmokeTest.check("panel follows colour changes from elsewhere (" + panelHex.getText() + ")", panelHex.getText().equals("#008000"));
                    // Recent colours were remembered
                    Field rf = AlcColorWheel.class.getDeclaredField("recent");
                    rf.setAccessible(true);
                    java.util.List<?> recent = (java.util.List<?>) rf.get(null);
                    UndoSmokeTest.check("recent colours remembered (" + recent.size() + ")", recent.size() >= 3 && recent.get(0).equals(Color.RED));
                    // Harmony markers: switch to triad
                    Field hf = AlcColorWheel.class.getDeclaredField("harmony");
                    hf.setAccessible(true);
                    hf.setInt(null, 2);
                    wheel.pick(0.0F, 1F, 1F);
                    PartsShot.render(popup, "wheel-popup");
                    PartsShot.render(panel.getRootPane(), "wheel-panel");
                    PartsShot.render(Alchemy.window.getRootPane(), "wheel-toolbar");
                } catch (Throwable t) {
                    t.printStackTrace();
                    UndoSmokeTest.failures++;
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    /** Type into a field the way a user does - key events, then the text */
    static void type(JTextField field, String text) {
        // Key events go to the focus owner, which a test window does not have - mark the typing directly
        try {
            java.lang.reflect.Field edited = AlcColorWheel.class.getDeclaredField("hexEdited");
            edited.setAccessible(true);
            edited.setBoolean(SwingUtilities.getAncestorOfClass(AlcColorWheel.class, field), true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        field.setText(text);
    }

    static void press(Component c, int x, int y) {
        long t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 5, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    static Component find(Container root, String simpleName) {
        for (Component k : root.getComponents()) {
            if (k.getClass().getSimpleName().equals(simpleName)) {
                return k;
            }
            if (k instanceof Container) {
                Component found = find((Container) k, simpleName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
