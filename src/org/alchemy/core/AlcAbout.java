/*
 * This file is part of the Alchemy project - http://al.chemy.org
 * 
 * Copyright (c) 2007-2010 Karl D.D. Willis
 * 
 * Alchemy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alchemy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Alchemy.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.alchemy.core;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The Alkemy about window
 */
class AlcAbout extends Window implements AlcConstants {

    private final Image logo;
    private final Dimension size = new Dimension(600, 340);

    AlcAbout(Window owner) {
        super(owner);

        logo = AlcUtil.getImage("alkemy-logo-white.png");

        this.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                setVisible(false);
                dispose();
            }
        });

        this.setSize(size);
        Point loc = AlcUtil.calculateCenter(this);
        this.setBounds(loc.x, loc.y, size.width, size.height);
        this.setVisible(true);

    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setColor(new Color(24, 25, 28));
        g2.fillRect(0, 0, size.width, size.height);
        g2.setColor(new Color(54, 56, 61));
        g2.drawRect(0, 0, size.width - 1, size.height - 1);

        // Logo and name on the left
        if (logo != null) {
            g2.drawImage(logo, 70, 62, 140, 140, null);
        }
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 44));
        FontMetrics big = g2.getFontMetrics();
        g2.drawString("Alkemy", 140 - big.stringWidth("Alkemy") / 2, 262);

        // About on the right
        int x = 290, y = 78;
        g2.setFont(FONT_MEDIUM);
        String[] lines = {
            "A sketching app for quick, dynamic drawing -",
            "brushes, pen pressure, layers and mirror.",
            "",
            "Built on Alchemy, the open drawing project",
            "initiated by Karl D.D. Willis & Jacob Hina.",
            "",
            "Source code released under the",
            "GNU General Public License.",
            "Alchemy copyright \u00A9 2007-2010 Karl D.D. Willis"};
        FontMetrics m = g2.getFontMetrics();
        for (String line : lines) {
            g2.setColor(line.startsWith("Built") || line.startsWith("initiated") || line.startsWith("Source")
                    || line.startsWith("GNU") || line.startsWith("Alchemy") ? COLOR_UI_TEXT_DIM : COLOR_UI_TEXT);
            g2.drawString(line, x, y);
            y += m.getHeight() + 2;
        }
        g2.setFont(FONT_SMALL);
        g2.setColor(COLOR_UI_TEXT_DIM);
        g2.drawString(Alchemy.bundle.getString("version.string"), x, size.height - 30);
    }
}