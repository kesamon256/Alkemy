/*
 *  This file is part of the Alchemy project - http://al.chemy.org
 *
 *  Alchemy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package org.alchemy.core;

import java.awt.Component;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * The two pen pressure strength sliders - how much the pressure changes the size and the
 * opacity, from 0 % (not at all) to 300 %. Shown in the tool bar of the drawing tools,
 * all of them sharing the same setting on the canvas.
 */
public final class AlcPressureSliders {

    static final String SIZE_TITLE = "Size Pressure", OPACITY_TITLE = "Opacity Pressure";

    private AlcPressureSliders() {
    }

    /** Add the sliders to a tool bar section */
    public static void add(AlcToolBarSubSection section, final AlcCanvas canvas) {
        final AlcSubSlider size = new AlcSubSlider(SIZE_TITLE, 0, 300, Math.round(canvas.getPressureSizeStrength() * 100));
        size.setToolTipText("How much the pen pressure changes the size - 0 % not at all, 100 % normal, 300 % very strong");
        size.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                canvas.setPressureSizeStrength(size.getValue() / 100F);
            }
        });
        section.add(new PressureSwitch(canvas, true));
        section.add(size);
        final AlcSubSlider opacity = new AlcSubSlider(OPACITY_TITLE, 0, 300, Math.round(canvas.getPressureOpacityStrength() * 100));
        opacity.setToolTipText("How much the pen pressure changes the opacity - higher fades light touches more (with Pressure Changes Opacity on)");
        opacity.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                canvas.setPressureOpacityStrength(opacity.getValue() / 100F);
            }
        });
        section.add(new PressureSwitch(canvas, false));
        section.add(opacity);
    }

    /** Show the current setting - another tool may have changed it */
    public static void refresh(AlcToolBarSubSection section, AlcCanvas canvas) {
        refresh(section.panel, canvas);
    }

    private static void refresh(java.awt.Container container, AlcCanvas canvas) {
        for (Component c : container.getComponents()) {
            if (c instanceof AlcSubSlider) {
                AlcSubSlider s = (AlcSubSlider) c;
                String title = s.getTitle();
                if (SIZE_TITLE.equals(title)) {
                    s.setValue(Math.round(canvas.getPressureSizeStrength() * 100));
                } else if (OPACITY_TITLE.equals(title)) {
                    s.setValue(Math.round(canvas.getPressureOpacityStrength() * 100));
                }
            } else if (c instanceof java.awt.Container) {
                refresh((java.awt.Container) c, canvas);
            }
        }
    }

    /** A small on / off switch for what the pen pressure changes - the size or the opacity.
     *  The same setting as in the Sketch menu, kept in step with it. */
    static class PressureSwitch extends javax.swing.JToggleButton {

        private final AlcCanvas canvas;
        private final boolean size;

        PressureSwitch(final AlcCanvas canvas, final boolean size) {
            this.canvas = canvas;
            this.size = size;
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setPreferredSize(new java.awt.Dimension(30, 25));
            setToolTipText(size ? "Pen pressure changes the size - on / off" : "Pen pressure changes the opacity - on / off");
            showState(canvas);
            addActionListener(new java.awt.event.ActionListener() {

                public void actionPerformed(java.awt.event.ActionEvent e) {
                    boolean on = isSelected();
                    // Switching on also switches the pen pressure itself on
                    if (on && !canvas.isPressureEnabled()) {
                        canvas.setPressureEnabled(true);
                    }
                    canvas.setPressureEffects(size ? on : canvas.isPressureSize(), size ? canvas.isPressureOpacity() : on);
                }
            });
            canvas.addPressureListener(new Runnable() {

                public void run() {
                    showState(canvas);
                }
            });
        }

        private void showState(AlcCanvas canvas) {
            setSelected(canvas.isPressureEnabled() && (size ? canvas.isPressureSize() : canvas.isPressureOpacity()));
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int w = 24, h = 13;
            int x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
            boolean on = isSelected();
            boolean over = getModel().isRollover();
            g2.setColor(on ? new java.awt.Color(over ? 236 : 222, over ? 238 : 224, over ? 242 : 228)
                    : new java.awt.Color(over ? 62 : 52, over ? 65 : 54, over ? 71 : 60));
            g2.fillRoundRect(x, y, w, h, h, h);
            g2.setColor(new java.awt.Color(80, 83, 90));
            if (!on) {
                g2.drawRoundRect(x, y, w - 1, h - 1, h, h);
            }
            int knob = h - 4;
            int kx = on ? x + w - knob - 2 : x + 2;
            g2.setColor(on ? new java.awt.Color(24, 25, 28) : new java.awt.Color(150, 154, 160));
            g2.fillOval(kx, y + 2, knob, knob);
            g2.dispose();
        }
    }
}