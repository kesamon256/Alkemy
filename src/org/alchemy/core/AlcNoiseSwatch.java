/*
 *  This file is part of the Alchemy project - http://al.chemy.org
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
 */
package org.alchemy.core;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/**
 * A small preview of a noise for a module's toolbar.
 * <p>
 * The swatch in the toolbar shows a strip of the noise. When the noise changes,
 * or the mouse is over the swatch, a larger tile opens below it for a moment,
 * with the noise at its real size in drawing pixels.
 */
public class AlcNoiseSwatch extends JComponent implements AlcConstants {

    private static final int TILE = 200;
    private int kind = -1;
    private double size = 80;
    private String name = "Even";
    private BufferedImage strip, tile;
    private JWindow popup;
    private Timer hideTimer;
    private boolean hover = false;

    public AlcNoiseSwatch() {
        setPreferredSize(new Dimension(48, 20));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
        setToolTipText(null);
        addMouseListener(new MouseAdapter() {

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                showTile();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                hideSoon(300);
            }
        });
    }

    /** Show a noise
     * @param kind  One of the {@link AlcNoise} kinds, or -1 for an even push
     * @param size  Size of one noise unit in drawing pixels
     * @param name  Name shown with the large tile
     */
    public void setNoise(int kind, double size, String name) {
        this.kind = kind;
        this.size = size;
        this.name = name;
        strip = render(getPreferredSize().width, getPreferredSize().height);
        tile = null;
        repaint();
        if (isShowing()) {
            showTile();
            if (!hover) {
                hideSoon(1600);
            }
        }
    }

    /** The noise at its real size - one image pixel is one drawing pixel */
    private BufferedImage render(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v;
                if (kind < 0) {
                    v = 128;
                } else {
                    double n = AlcNoise.noise(kind, x / size, y / size, 0);
                    v = (int) Math.round(Math.max(0, Math.min(1, n)) * 255);
                }
                image.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return image;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape round = new java.awt.geom.RoundRectangle2D.Float(0.5F, 0.5F, getWidth() - 1, getHeight() - 1, 6, 6);
        if (strip == null) {
            strip = render(getPreferredSize().width, getPreferredSize().height);
        }
        g2.setClip(round);
        g2.drawImage(strip, 0, 0, getWidth(), getHeight(), null);
        g2.setClip(null);
        g2.setColor(hover ? COLOR_UI_ACCENT : new Color(80, 84, 90));
        g2.draw(round);
        g2.dispose();
    }

    //////////////////////////////////////////////////////////////
    // LARGE TILE
    //////////////////////////////////////////////////////////////
    private void showTile() {
        if (!isShowing()) {
            return;
        }
        if (hideTimer != null) {
            hideTimer.stop();
        }
        if (tile == null) {
            tile = render(TILE, TILE);
        }
        if (popup == null) {
            popup = new JWindow(SwingUtilities.getWindowAncestor(this));
            popup.setFocusableWindowState(false);
            popup.setContentPane(new JComponent() {

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(new Color(22, 23, 26));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.drawImage(tile, 6, 6, null);
                    g2.setColor(new Color(80, 84, 90));
                    g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    // Name and size of the noise, with a bar as long as one noise unit
                    g2.setFont(FONT_SMALL);
                    g2.setColor(COLOR_UI_TEXT);
                    String label = (kind < 0) ? "Even - no noise" : name + "  " + Math.round(size) + " px";
                    g2.drawString(label, 8, TILE + 22);
                    if (kind >= 0) {
                        int bar = (int) Math.min(TILE - 90, Math.round(size));
                        int bx = TILE + 6 - bar;
                        g2.setColor(COLOR_UI_ACCENT);
                        g2.fillRect(bx, TILE + 15, bar, 3);
                        g2.fillRect(bx, TILE + 12, 1, 9);
                        g2.fillRect(bx + bar - 1, TILE + 12, 1, 9);
                    }
                    g2.dispose();
                }
            });
            popup.setSize(TILE + 12, TILE + 32);
        }
        Point p = getLocationOnScreen();
        popup.setLocation(p.x, p.y + getHeight() + 4);
        popup.repaint();
        popup.setVisible(true);
    }

    private void hideSoon(int delay) {
        if (popup == null) {
            return;
        }
        if (hideTimer != null) {
            hideTimer.stop();
        }
        hideTimer = new Timer(delay, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                if (!hover && popup != null) {
                    popup.setVisible(false);
                }
            }
        });
        hideTimer.setRepeats(false);
        hideTimer.start();
    }
}
