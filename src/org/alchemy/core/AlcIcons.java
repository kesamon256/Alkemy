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
import java.awt.geom.*;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import javax.swing.ImageIcon;

/**
 * Vector versions of the main toolbar icons.
 * <p>
 * The original icons are small bitmaps that blur on high resolution screens.
 * These are drawn in code at every display scale, keeping the shapes of the
 * original Alchemy icons. The state variants follow the original file naming:
 * name.png, name-over.png, name-down.png, name-on.png and name-on-over.png.
 */
final class AlcIcons implements AlcConstants {

    private static final int SIZE = 24;
    private static final double[] SCALES = {1, 1.25, 1.5, 1.75, 2, 2.5, 3};
    private static final Color NORMAL = new Color(206, 209, 214);
    private static final Color OVER = Color.WHITE;
    private static final Color DOWN = new Color(150, 154, 160);
    private static final Color ON = new Color(98, 170, 255);
    private static final Color ON_OVER = new Color(140, 194, 255);
    private static final HashMap<String, ImageIcon> cache = new HashMap<String, ImageIcon>();

    private AlcIcons() {
    }

    /** Get the vector icon for a file name such as "undo-over.png", or null if there is none */
    static ImageIcon get(String fileName) {
        if (!fileName.endsWith(".png")) {
            return null;
        }
        String name = fileName.substring(0, fileName.length() - 4);
        boolean on = false;
        Color color = NORMAL;
        if (name.endsWith("-on-over")) {
            name = name.substring(0, name.length() - 8);
            on = true;
            color = ON_OVER;
        } else if (name.endsWith("-on")) {
            name = name.substring(0, name.length() - 3);
            on = true;
            color = ON;
        } else if (name.endsWith("-over")) {
            name = name.substring(0, name.length() - 5);
            color = OVER;
        } else if (name.endsWith("-down")) {
            name = name.substring(0, name.length() - 5);
            color = DOWN;
        }
        if (!isVector(name)) {
            return null;
        }
        ImageIcon icon = cache.get(fileName);
        if (icon == null) {
            BufferedImage[] variants = new BufferedImage[SCALES.length];
            for (int i = 0; i < SCALES.length; i++) {
                variants[i] = render(name, on, color, SCALES[i]);
            }
            icon = new ImageIcon(new BaseMultiResolutionImage(variants));
            cache.put(fileName, icon);
        }
        return icon;
    }

    private static boolean isVector(String name) {
        return name.equals("switch-tools") || name.equals("switch-swatch") || name.startsWith("style")
                || name.equals("underOver") || name.equals("create") || name.equals("affect")
                || name.equals("zoom") || name.equals("center") || name.equals("select") || name.equals("undo") || name.equals("redo") || name.equals("clear")
                || name.equals("add") || name.equals("remove")
                || name.equals("preferences-general") || name.equals("preferences-session");
    }

    private static BufferedImage render(String name, boolean on, Color color, double scale) {
        int px = (int) Math.ceil(SIZE * scale);
        BufferedImage image = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(scale, scale);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.6F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        paint(g, name, on);
        g.dispose();
        return image;
    }

    private static void paint(Graphics2D g, String name, boolean on) {
        if (name.equals("create")) {
            // Sparkle - something new appears
            sparkle(g, 12, 12, 7.5);
        } else if (name.equals("affect")) {
            // Arrows in both directions - something changes
            arrow(g, 9, 19, 9, 4);
            arrow(g, 15, 4, 15, 19);
        } else if (name.equals("style")) {
            // Line or solid shapes
            RoundRectangle2D shape = new RoundRectangle2D.Double(6, 5, 12, 14, 3, 3);
            if (on) {
                g.fill(shape);
            } else {
                g.draw(shape);
            }
        } else if (name.startsWith("style")) {
            // The Style menu: styleStroke, styleFill, styleBoth, styleDashed, styleDotted -
            // with a chevron on the toolbar button, without in the menu (styleItem...)
            boolean item = name.startsWith("styleItem");
            String kind = name.substring(item ? 9 : 5);
            RoundRectangle2D shape = new RoundRectangle2D.Double(6, 5, 12, 14, 3, 3);
            Stroke solid = g.getStroke();
            Color c = g.getColor();
            if (kind.equals("Fill")) {
                g.fill(shape);
            } else if (kind.equals("Both")) {
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 90));
                g.fill(shape);
                g.setColor(c);
                g.draw(shape);
            } else if (kind.equals("Dashed")) {
                g.setStroke(new BasicStroke(1.6F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10F, new float[]{3.4F, 2.6F}, 0F));
                g.draw(shape);
            } else if (kind.equals("Dotted")) {
                g.setStroke(new BasicStroke(1.9F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10F, new float[]{0.01F, 3.1F}, 0F));
                g.draw(shape);
            } else {
                g.draw(shape);
            }
            g.setStroke(solid);
        } else if (name.equals("underOver")) {
            // Draw over or under the existing shapes
            RoundRectangle2D layer = new RoundRectangle2D.Double(5, on ? 7 : 14, 14, 5, 2, 2);
            g.fill(layer);
            if (on) {
                arrow(g, 12, 21, 12, 14);
            } else {
                arrow(g, 12, 3, 12, 10.5);
            }
        } else if (name.equals("zoom")) {
            g.draw(new Ellipse2D.Double(4.5, 4.5, 11, 11));
            g.draw(new Line2D.Double(14, 14, 19.5, 19.5));
            if (on) {
                g.draw(new Line2D.Double(7.5, 10, 12.5, 10));
                g.draw(new Line2D.Double(10, 7.5, 10, 12.5));
            }
        } else if (name.equals("select")) {
            // A dashed lasso loop with its tail
            Stroke solid = g.getStroke();
            g.setStroke(new BasicStroke(1.6F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10F, new float[]{3.2F, 2.4F}, 0F));
            g.draw(new Ellipse2D.Double(4, 4, 16, 11.5));
            g.setStroke(solid);
            Path2D tail = new Path2D.Double();
            tail.moveTo(8, 14.5);
            tail.curveTo(6, 17, 9, 19, 7, 21);
            g.draw(tail);
            if (on) {
                g.fill(new Ellipse2D.Double(10, 8, 4, 4));
            }
        } else if (name.equals("center")) {
            // Corners of a frame around a centre point - fit the drawing in the window
            double a = 4.5, b = 19.5, l = 4.5;
            Path2D corners = new Path2D.Double();
            corners.moveTo(a, a + l);
            corners.lineTo(a, a);
            corners.lineTo(a + l, a);
            corners.moveTo(b - l, a);
            corners.lineTo(b, a);
            corners.lineTo(b, a + l);
            corners.moveTo(b, b - l);
            corners.lineTo(b, b);
            corners.lineTo(b - l, b);
            corners.moveTo(a + l, b);
            corners.lineTo(a, b);
            corners.lineTo(a, b - l);
            g.draw(corners);
            g.fill(new Ellipse2D.Double(9.5, 9.5, 5, 5));
        } else if (name.equals("undo") || name.equals("redo")) {
            if (name.equals("redo")) {
                g.transform(new AffineTransform(-1, 0, 0, 1, SIZE, 0));
            }
            Path2D curve = new Path2D.Double();
            curve.moveTo(6, 10);
            curve.lineTo(14, 10);
            curve.curveTo(20.5, 10, 20.5, 19, 14, 19);
            curve.lineTo(10, 19);
            g.draw(curve);
            Path2D head = new Path2D.Double();
            head.moveTo(9.5, 6.5);
            head.lineTo(6, 10);
            head.lineTo(9.5, 13.5);
            g.draw(head);
        } else if (name.equals("clear")) {
            g.draw(new Line2D.Double(6, 6, 18, 18));
            g.draw(new Line2D.Double(18, 6, 6, 18));
        } else if (name.equals("add")) {
            g.draw(new Line2D.Double(12, 6, 12, 18));
            g.draw(new Line2D.Double(6, 12, 18, 12));
        } else if (name.equals("remove")) {
            g.draw(new Line2D.Double(6, 12, 18, 12));
        } else if (name.equals("preferences-general")) {
            // Sliders
            double[] knobs = {8, 15, 10};
            for (int i = 0; i < 3; i++) {
                double y = 6 + i * 6;
                g.draw(new Line2D.Double(4, y, 20, y));
                g.fill(new Ellipse2D.Double(knobs[i] - 2.5, y - 2.5, 5, 5));
            }
        } else if (name.equals("preferences-session")) {
            // Stopwatch - the session records the canvas over time
            g.draw(new Ellipse2D.Double(4.5, 6, 15, 15));
            g.draw(new Line2D.Double(12, 13.5, 12, 9.5));
            g.draw(new Line2D.Double(12, 13.5, 15, 15.5));
            g.draw(new Line2D.Double(10, 3, 14, 3));
        } else if (name.equals("switch-tools") || name.equals("switch-swatch")) {
            // Swap between the tools and the colour swatch
            Path2D top = new Path2D.Double();
            top.moveTo(5, 9);
            top.lineTo(18, 9);
            top.moveTo(15, 5.5);
            top.lineTo(18.5, 9);
            top.lineTo(15, 12.5);
            g.draw(top);
            Path2D bottom = new Path2D.Double();
            bottom.moveTo(19, 16);
            bottom.lineTo(6, 16);
            bottom.moveTo(9, 12.5);
            bottom.lineTo(5.5, 16);
            bottom.lineTo(9, 19.5);
            g.draw(bottom);
        }
    }

    /** A thin vertical divider line between toolbar groups */
    static javax.swing.Icon separator(final int width, final int height, final int lineHeight) {
        return new javax.swing.Icon() {

            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setColor(new Color(255, 255, 255, 28));
                int top = y + (height - lineHeight) / 2;
                g.fillRect(x + width / 2, top, 1, lineHeight);
            }

            public int getIconWidth() {
                return width;
            }

            public int getIconHeight() {
                return height;
            }
        };
    }

    /** Paint the accent tint behind the selected (active) module in the Create / Affect menus */
    static void paintSelectedRow(Graphics g, Component item) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(77, 156, 255, 70));
        g2.fill(new RoundRectangle2D.Double(3, 1, item.getWidth() - 6, item.getHeight() - 2, 10, 10));
        g2.setColor(ON);
        g2.fill(new RoundRectangle2D.Double(3, 6, 3, item.getHeight() - 12, 3, 3));
        g2.dispose();
    }

    /** Paint the rounded hover / pressed / selected background behind a toolbar button */
    static void paintButtonBackground(Graphics g, javax.swing.AbstractButton button) {
        javax.swing.ButtonModel model = button.getModel();
        Color fill = null;
        if (!button.isEnabled()) {
            return;
        } else if (model.isPressed() && model.isArmed()) {
            fill = new Color(255, 255, 255, 18);
        } else if (model.isRollover()) {
            fill = new Color(255, 255, 255, 26);
        }
        if (fill == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fill(new RoundRectangle2D.Double(1, 1, button.getWidth() - 2, button.getHeight() - 2, 10, 10));
        g2.dispose();
    }

    /** Four pointed star */
    private static void sparkle(Graphics2D g, double cx, double cy, double r) {
        Path2D star = new Path2D.Double();
        double k = r * 0.22;
        star.moveTo(cx, cy - r);
        star.quadTo(cx + k, cy - k, cx + r, cy);
        star.quadTo(cx + k, cy + k, cx, cy + r);
        star.quadTo(cx - k, cy + k, cx - r, cy);
        star.quadTo(cx - k, cy - k, cx, cy - r);
        star.closePath();
        g.fill(star);
    }


    /** Line with an arrow head at the end */
    private static void arrow(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.draw(new Line2D.Double(x1, y1, x2, y2));
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double len = 3.6;
        Path2D head = new Path2D.Double();
        head.moveTo(x2 - len * Math.cos(angle - 0.7), y2 - len * Math.sin(angle - 0.7));
        head.lineTo(x2, y2);
        head.lineTo(x2 - len * Math.cos(angle + 0.7), y2 - len * Math.sin(angle + 0.7));
        g.draw(head);
    }
}
