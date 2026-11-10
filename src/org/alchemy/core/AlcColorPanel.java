/*
 *  This file is part of the Alchemy project - http://al.chemy.org
 *
 *  Copyright (c) 2007-2010 Karl D.D. Willis
 *
 *  Alchemy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Alchemy is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Alchemy.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.alchemy.core;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.awt.Window;
import javax.swing.*;

/**
 * A small floating window holding the colour wheel, so it can stay open
 * while drawing. It remembers whether it was open and where it was.
 * After a colour is picked the keyboard goes back to the canvas, so the
 * drawing shortcuts keep working.
 */
class AlcColorPanel extends JDialog implements AlcConstants {

    private static AlcColorPanel panel;
    /** Every colour wheel - kept in step with the pen colour */
    private static final ArrayList<AlcColorWheel> wheels = new ArrayList<AlcColorWheel>();
    private final AlcColorWheel wheel;

    private AlcColorPanel() {
        super(Alchemy.window, Alchemy.bundle.getString("colorPanelTitle"), false);
        setType(Window.Type.UTILITY);
        setResizable(false);
        setFocusableWindowState(true);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        wheel = new AlcColorWheel(null);
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(COLOR_UI_BG);
        content.add(wheel, BorderLayout.CENTER);
        setContentPane(content);
        pack();
        // Last position, or by the right edge of the Alchemy window
        int x = AlcPreferences.prefs.getInt("Colour Panel X", Integer.MIN_VALUE);
        int y = AlcPreferences.prefs.getInt("Colour Panel Y", Integer.MIN_VALUE);
        if (x == Integer.MIN_VALUE || !onScreen(x, y)) {
            Rectangle main = Alchemy.window.getBounds();
            x = main.x + main.width - getWidth() - 24;
            y = main.y + 140;
        }
        setLocation(x, y);
        addComponentListener(new ComponentAdapter() {

            @Override
            public void componentMoved(ComponentEvent e) {
                AlcPreferences.prefs.putInt("Colour Panel X", getX());
                AlcPreferences.prefs.putInt("Colour Panel Y", getY());
            }

        });
        // The dock on the right holds the content unless the panel floats
        setContentPane(new JPanel());
        AlcDock.get().colour.attach(this, content);
    }

    private static void create() {
        if (panel == null) {
            panel = new AlcColorPanel();
        }
    }

    private static boolean onScreen(int x, int y) {
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if (device.getDefaultConfiguration().getBounds().contains(x + 20, y + 20)) {
                return true;
            }
        }
        return false;
    }

    /** Show the colour panel, creating it the first time */
    static void showPanel() {
        create();
        panel.wheel.syncFromCanvas();
        AlcDock.get().colour.open();
    }

    static void togglePanel() {
        create();
        panel.wheel.syncFromCanvas();
        AlcDock.get().colour.toggle();
    }

    static boolean isPanelOpen() {
        return panel != null && AlcDock.get().colour.isOpen();
    }

    /** At start up - docked on the right unless it was closed or floating last time */
    static void restore() {
        create();
        panel.wheel.syncFromCanvas();
        AlcDock.get().colour.restore();
    }

    static void register(AlcColorWheel wheel) {
        wheels.add(wheel);
    }

    /** The pen colour changed - bring every wheel up to date except the one being used */
    static void refreshAll(AlcColorWheel source) {
        for (AlcColorWheel w : wheels) {
            if (w != source) {
                w.syncFromCanvas();
            }
        }
    }

    /** After a pick in the floating panel, hand the keyboard back to the canvas */
    static void returnFocusToCanvas(Component from) {
        if (panel != null && (SwingUtilities.getWindowAncestor(from) == panel
                || (AlcDock.isShown() && SwingUtilities.isDescendingFrom(from, AlcDock.get())))) {
            Alchemy.window.toFront();
            Alchemy.canvas.requestFocusInWindow();
        }
    }
}
