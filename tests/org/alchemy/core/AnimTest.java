package org.alchemy.core;

import javax.swing.SwingUtilities;

/** Samples the toolbar position while it slides in and out. */
public class AnimTest {

    public static void main(String[] args) throws Exception {
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        final StringBuilder in = new StringBuilder(), out = new StringBuilder();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.toolBar.setToolBarVisible(true);
            }
        });
        for (int i = 0; i < 12; i++) {
            Thread.sleep(20);
            in.append(Alchemy.toolBar.getY()).append(' ');
        }
        boolean shown = Alchemy.toolBar.isVisible() && Alchemy.toolBar.getY() == 0;
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.toolBar.setToolBarVisible(false);
            }
        });
        for (int i = 0; i < 10; i++) {
            Thread.sleep(20);
            out.append(Alchemy.toolBar.getY()).append(' ');
        }
        System.out.println("slide in : " + in);
        System.out.println("slide out: " + out);
        UndoSmokeTest.check("toolbar ends fully shown", shown);
        UndoSmokeTest.check("toolbar ends hidden", !Alchemy.toolBar.isVisible());
        System.exit(0);
    }
}
