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
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import javax.swing.*;

/**
 * Colour wheel in the style of Coolorus / Corel Painter.
 * <p>
 * The outer ring picks the hue. The triangle inside turns with the hue:
 * its corners are the pure hue, white and black, so saturation and
 * brightness are picked in one place. Above the wheel the current colour is
 * shown with the previous one (click it to swap back), next to the colour
 * harmony choice that marks matching hues on the ring. Below are the most
 * recently used colours and the hex code, which can be typed in.
 * The pen colour changes live while dragging.
 */
class AlcColorWheel extends JPanel implements AlcConstants {

    private static final int SIZE = 220;
    private static final int RING = 20;
    /** Colour harmonies - the extra hues marked on the ring, as fractions of a turn */
    private static final float[][] HARMONIES = {{}, {0.5F}, {1 / 3F, 2 / 3F}, {-1 / 12F, 1 / 12F}};
    private static final String[] HARMONY_KEYS = {"harmonyNone", "harmonyComplementary", "harmonyTriad", "harmonyAnalogous"};
    /** Recently used colours, shared by every wheel - newest first */
    private static final ArrayList<Color> recent = new ArrayList<Color>();
    private static final int RECENT_COUNT = 10;
    private static int harmony = 0;
    //
    private final Wheel wheel = new Wheel();
    private final JTextField hexField = new JTextField(7);
    private final JPanel recentPanel = new JPanel(new GridLayout(1, RECENT_COUNT, 3, 0));
    private final JButton harmonyButton = new JButton();
    private float hue, saturation, brightness;
    /** The colour before the current one - click its circle to swap back */
    private Color previous;
    /** This wheel is setting the pen colour - ignore the update coming back */
    private boolean updating = false;
    /** The user has typed in the hex field since it last showed the colour */
    private boolean hexEdited = false;

    /**
     * @param pinAction     Shown as a pin button that keeps the wheel open as a panel - or null
     */
    AlcColorWheel(final Runnable pinAction) {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // TOP - current / previous colour, harmony, pin
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        top.add(new ColorCircles(), BorderLayout.WEST);
        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        topRight.setOpaque(false);
        harmonyButton.setFont(FONT_SMALL);
        harmonyButton.setFocusable(false);
        harmonyButton.setToolTipText(Alchemy.bundle.getString("harmonyDescription"));
        harmonyButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                harmony = (harmony + 1) % HARMONIES.length;
                updateHarmonyButton();
                repaint();
            }
        });
        updateHarmonyButton();
        topRight.add(harmonyButton);
        if (pinAction != null) {
            JButton pin = new JButton(pinIcon());
            pin.setFocusable(false);
            pin.setToolTipText(Alchemy.bundle.getString("colorPanelPin"));
            pin.putClientProperty("JButton.buttonType", "toolBarButton");
            pin.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    pinAction.run();
                }
            });
            topRight.add(pin);
        }
        top.add(topRight, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        // CENTRE - the wheel
        add(wheel, BorderLayout.CENTER);

        // BOTTOM - recent colours and hex code
        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        recentPanel.setOpaque(false);
        bottom.add(recentPanel, BorderLayout.NORTH);
        hexField.setFont(FONT_MEDIUM);
        hexField.setHorizontalAlignment(JTextField.CENTER);
        hexField.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                applyHex();
            }
        });
        hexField.addFocusListener(new FocusAdapter() {

            @Override
            public void focusLost(FocusEvent e) {
                applyHex();
            }
        });
        // Remember when the user types, so only a typed code is ever applied
        hexField.addKeyListener(new KeyAdapter() {

            @Override
            public void keyTyped(KeyEvent e) {
                hexEdited = true;
            }
        });
        bottom.add(hexField, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        // Follow the pen colour whenever the wheel is shown
        addHierarchyListener(new HierarchyListener() {

            public void hierarchyChanged(HierarchyEvent e) {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                    syncFromCanvas();
                }
            }
        });
        AlcColorPanel.register(this);
        syncFromCanvas();
    }

    private void updateHarmonyButton() {
        harmonyButton.setText(Alchemy.bundle.getString(HARMONY_KEYS[harmony]));
    }

    /** Take the hue, saturation and brightness from the current pen colour */
    void syncFromCanvas() {
        if (updating) {
            return;
        }
        setFromColor(Alchemy.canvas.getColor());
        updateText();
        rebuildRecent();
        repaint();
    }

    private void setFromColor(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        // Keep the hue when the colour is grey, so the ring does not jump
        if (hsb[1] > 0.001F && hsb[2] > 0.001F) {
            hue = hsb[0];
        }
        saturation = hsb[1];
        brightness = hsb[2];
    }

    private Color currentColor() {
        return Color.getHSBColor(hue, saturation, brightness);
    }

    /** Show the current colour in the hex field - unless the user is typing a code there */
    private void updateText() {
        if (!hexEdited) {
            hexField.setText(hex(currentColor()));
        }
    }

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Use a typed hex code. Only text the user typed is applied - an old code left
     *  in the field must not undo a colour picked on the wheel meanwhile. */
    private void applyHex() {
        if (!hexEdited) {
            return;
        }
        hexEdited = false;
        String text = hexField.getText().trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        try {
            if (text.length() == 6) {
                Color c = new Color(Integer.parseInt(text, 16));
                if (!c.equals(currentColor())) {
                    rememberPrevious();
                    setFromColor(c);
                    setPenColor();
                    finishPick();
                }
            }
        } catch (NumberFormatException ex) {
            // Ignore - the field shows the current colour again below
        }
        hexField.setText(hex(currentColor()));
    }

    /** Send the colour to the canvas - transparency stays as set by its own slider */
    private void setPenColor() {
        updating = true;
        try {
            Alchemy.canvas.setColor(currentColor());
            Alchemy.toolBar.refreshColorButton();
        } finally {
            updating = false;
        }
        updateText();
        repaint();
    }

    private void rememberPrevious() {
        previous = currentColor();
        // A pick on the wheel replaces a half typed hex code
        hexEdited = false;
    }

    /** A pick is done - add it to the recent colours */
    private void finishPick() {
        Color c = currentColor();
        recent.remove(c);
        recent.add(0, c);
        while (recent.size() > RECENT_COUNT) {
            recent.remove(recent.size() - 1);
        }
        rebuildRecent();
        AlcColorPanel.refreshAll(this);
        AlcColorPanel.returnFocusToCanvas(this);
    }

    private void rebuildRecent() {
        recentPanel.removeAll();
        for (int i = 0; i < RECENT_COUNT; i++) {
            final Color c = (i < recent.size()) ? recent.get(i) : null;
            JComponent swatch = new JComponent() {

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (c != null) {
                        g2.setColor(c);
                        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                    }
                    g2.setColor(new Color(255, 255, 255, c == null ? 25 : 50));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                    g2.dispose();
                }
            };
            swatch.setPreferredSize(new Dimension(18, 18));
            if (c != null) {
                swatch.setToolTipText(hex(c));
                swatch.addMouseListener(new MouseAdapter() {

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        rememberPrevious();
                        setFromColor(c);
                        setPenColor();
                        finishPick();
                    }
                });
            }
            recentPanel.add(swatch);
        }
        recentPanel.revalidate();
        recentPanel.repaint();
    }

    /** Set the colour as if picked with the wheel - used by tests */
    void pick(float h, float s, float b) {
        rememberPrevious();
        hue = h;
        saturation = s;
        brightness = b;
        setPenColor();
        finishPick();
    }

    private Icon pinIcon() {
        return new Icon() {

            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(206, 209, 214));
                g2.setStroke(new BasicStroke(1.5F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.translate(x, y);
                // A drawing pin
                g2.draw(new Line2D.Double(5, 10, 13, 10));
                g2.draw(new Line2D.Double(7, 4, 11, 4));
                g2.draw(new Line2D.Double(7.5, 4, 7, 10));
                g2.draw(new Line2D.Double(10.5, 4, 11, 10));
                g2.draw(new Line2D.Double(9, 10, 9, 15));
                g2.dispose();
            }

            public int getIconWidth() {
                return 18;
            }

            public int getIconHeight() {
                return 18;
            }
        };
    }

    /** Current colour as a big circle with the previous one as a small circle */
    private class ColorCircles extends JComponent {

        ColorCircles() {
            setPreferredSize(new Dimension(52, 38));
            setToolTipText(Alchemy.bundle.getString("colorPreviousDescription"));
            addMouseListener(new MouseAdapter() {

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (previous != null && e.getX() > 30) {
                        Color swap = previous;
                        rememberPrevious();
                        setFromColor(swap);
                        setPenColor();
                        finishPick();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (previous != null) {
                circle(g2, 30, 16, 20, previous);
            }
            circle(g2, 1, 1, 34, currentColor());
            g2.dispose();
        }

        private void circle(Graphics2D g2, double x, double y, double d, Color c) {
            Ellipse2D e = new Ellipse2D.Double(x, y, d, d);
            g2.setColor(c);
            g2.fill(e);
            g2.setStroke(new BasicStroke(2F));
            g2.setColor(new Color(43, 45, 49));
            g2.draw(e);
            g2.setStroke(new BasicStroke(1F));
            g2.setColor(new Color(255, 255, 255, 70));
            g2.draw(new Ellipse2D.Double(x + 1, y + 1, d - 2, d - 2));
        }
    }

    /** The ring and triangle */
    private class Wheel extends JComponent implements MouseListener, MouseMotionListener {

        private static final int NONE = 0, RING_DRAG = 1, TRIANGLE_DRAG = 2;
        private int drag = NONE;
        /** The ring drag started from a colour without a hue (grey, black or white) */
        private boolean greyStart = false;
        private BufferedImage ringImage;
        private int ringImagePixels = -1;
        private int ringImageWidth = -1;
        private int ringImageHeight = -1;
        private BufferedImage triangleImage;
        private float triangleHue = -1;
        private int trianglePixels = -1;

        Wheel() {
            setPreferredSize(new Dimension(SIZE, SIZE));
            addMouseListener(this);
            addMouseMotionListener(this);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }

        private double cx() {
            return getWidth() / 2.0;
        }

        private double cy() {
            return getHeight() / 2.0;
        }

        private double outerRadius() {
            return Math.min(getWidth(), getHeight()) / 2.0 - 2;
        }

        private double innerRadius() {
            return outerRadius() - RING;
        }

        /** Corners of the triangle - it stays still: pure hue on the right,
         *  white at the top and black at the bottom */
        private Point2D[] corners() {
            double r = innerRadius() - 5;
            double[] angles = {0, -120, 120};
            Point2D[] p = new Point2D[3];
            for (int i = 0; i < 3; i++) {
                double angle = Math.toRadians(angles[i]);
                p[i] = new Point2D.Double(cx() + Math.cos(angle) * r, cy() + Math.sin(angle) * r);
            }
            return p;
        }

        /** Barycentric weights of a point for the hue, white and black corners */
        private double[] weights(Point2D p, Point2D[] c) {
            double d = (c[1].getY() - c[2].getY()) * (c[0].getX() - c[2].getX()) + (c[2].getX() - c[1].getX()) * (c[0].getY() - c[2].getY());
            double a = ((c[1].getY() - c[2].getY()) * (p.getX() - c[2].getX()) + (c[2].getX() - c[1].getX()) * (p.getY() - c[2].getY())) / d;
            double b = ((c[2].getY() - c[0].getY()) * (p.getX() - c[2].getX()) + (c[0].getX() - c[2].getX()) * (p.getY() - c[2].getY())) / d;
            return new double[]{a, b, 1 - a - b};
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double cx = cx(), cy = cy();
            double outer = outerRadius();
            double inner = innerRadius();
            // Hue ring - red at the top, going clockwise - rendered per pixel with smooth edges
            double scale = g2.getTransform().getScaleX();
            int ringPixels = (int) Math.ceil(getWidth() * scale);
            if (ringImage == null || ringPixels != ringImagePixels || getWidth() != ringImageWidth || getHeight() != ringImageHeight) {
                renderRing(ringPixels, (int) Math.ceil(getHeight() * scale), scale);
            }
            g2.drawImage(ringImage, 0, 0, getWidth(), getHeight(), null);

            // Triangle, rendered at screen resolution
            Point2D[] c = corners();
            double r = innerRadius() - 5;
            int pixels = (int) Math.ceil(r * 2 * scale);
            if (triangleImage == null || triangleHue != hue || trianglePixels != pixels) {
                renderTriangle(pixels, scale, c);
            }
            g2.drawImage(triangleImage, (int) Math.round(cx - r), (int) Math.round(cy - r), (int) Math.round(r * 2), (int) Math.round(r * 2), null);

            // Harmony hues
            for (float offset : HARMONIES[harmony]) {
                float h = ((hue + offset) % 1F + 1F) % 1F;
                Point2D p = ringPoint(h);
                marker(g2, p.getX(), p.getY(), 5, Color.getHSBColor(h, 1F, 1F), false);
            }
            // Current hue and colour
            Point2D hp = ringPoint(hue);
            marker(g2, hp.getX(), hp.getY(), RING / 2.0 - 1, Color.getHSBColor(hue, 1F, 1F), true);
            double wHue = saturation * brightness, wWhite = brightness * (1 - saturation);
            double x = wHue * c[0].getX() + wWhite * c[1].getX() + (1 - wHue - wWhite) * c[2].getX();
            double y = wHue * c[0].getY() + wWhite * c[1].getY() + (1 - wHue - wWhite) * c[2].getY();
            marker(g2, x, y, 6, currentColor(), true);
            g2.dispose();
        }

        private void renderRing(int pixels, int pixelsHigh, double scale) {
            ringImage = new BufferedImage(pixels, pixelsHigh, BufferedImage.TYPE_INT_ARGB);
            double outer = outerRadius() * scale;
            double inner = innerRadius() * scale;
            double centreX = cx() * scale, centreY = cy() * scale;
            for (int py = 0; py < pixelsHigh; py++) {
                for (int px = 0; px < pixels; px++) {
                    double dx = px + 0.5 - centreX, dy = py + 0.5 - centreY;
                    double d = Math.sqrt(dx * dx + dy * dy);
                    // Coverage of this pixel by the ring - a soft one pixel edge on both sides
                    double coverage = Math.min(Math.min(d - inner + 0.5, outer - d + 0.5), 1);
                    if (coverage <= 0) {
                        continue;
                    }
                    double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
                    float h = (float) (((angle % 360) + 360) % 360 / 360.0);
                    int rgb = Color.HSBtoRGB(h, 1F, 1F) & 0xFFFFFF;
                    ringImage.setRGB(px, py, ((int) Math.round(coverage * 255) << 24) | rgb);
                }
            }
            ringImagePixels = pixels;
            ringImageWidth = getWidth();
            ringImageHeight = getHeight();
        }

        private void renderTriangle(int pixels, double scale, Point2D[] screenCorners) {
            triangleImage = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
            double r = innerRadius() - 5;
            double ox = cx() - r, oy = cy() - r;
            for (int py = 0; py < pixels; py++) {
                for (int px = 0; px < pixels; px++) {
                    Point2D p = new Point2D.Double(ox + (px + 0.5) / scale, oy + (py + 0.5) / scale);
                    double[] w = weights(p, screenCorners);
                    double min = Math.min(w[0], Math.min(w[1], w[2]));
                    if (min < -1.5 / (r * scale)) {
                        continue;
                    }
                    double wh = Math.max(0, w[0]), ww = Math.max(0, w[1]);
                    double v = Math.min(1, wh + ww);
                    double s = v > 0 ? wh / v : 0;
                    int rgb = Color.HSBtoRGB(hue, (float) s, (float) v);
                    // Soft edge
                    int alpha = min >= 0 ? 255 : (int) (255 * (1 + min * r * scale / 1.5));
                    triangleImage.setRGB(px, py, (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF));
                }
            }
            triangleHue = hue;
            trianglePixels = pixels;
        }

        private Point2D ringPoint(float h) {
            double angle = Math.toRadians(h * 360 - 90);
            double mid = (outerRadius() + innerRadius()) / 2;
            return new Point2D.Double(cx() + Math.cos(angle) * mid, cy() + Math.sin(angle) * mid);
        }

        private void marker(Graphics2D g2, double x, double y, double r, Color fill, boolean strong) {
            Ellipse2D dot = new Ellipse2D.Double(x - r, y - r, r * 2, r * 2);
            g2.setColor(fill);
            g2.fill(dot);
            g2.setStroke(new BasicStroke(strong ? 2.5F : 1.5F));
            g2.setColor(Color.WHITE);
            g2.draw(dot);
            g2.setStroke(new BasicStroke(1F));
            g2.setColor(new Color(0, 0, 0, 120));
            g2.draw(new Ellipse2D.Double(x - r - 1.5, y - r - 1.5, r * 2 + 3, r * 2 + 3));
        }

        private void update(Point p) {
            if (drag == RING_DRAG) {
                double angle = Math.toDegrees(Math.atan2(p.y - cy(), p.x - cx())) + 90;
                hue = (float) (((angle % 360) + 360) % 360 / 360.0);
                // The ring changes only the hue - the point chosen in the triangle stays.
                // A grey, black or white colour has no hue to change, so start from the full colour.
                if (greyStart) {
                    saturation = 1F;
                    brightness = 1F;
                }
            } else if (drag == TRIANGLE_DRAG) {
                double[] w = weights(p, corners());
                // Keep the point inside the triangle
                double wh = Math.max(0, w[0]), ww = Math.max(0, w[1]), wb = Math.max(0, w[2]);
                double sum = wh + ww + wb;
                wh /= sum;
                ww /= sum;
                brightness = (float) Math.min(1, wh + ww);
                saturation = brightness > 0 ? (float) (wh / brightness) : 0;
            }
            setPenColor();
        }

        public void mousePressed(MouseEvent e) {
            if (Boolean.getBoolean("alchemy.debugColor")) {
                System.out.println(String.format("[wheel] press %d,%d of %dx%d button %d in %s", e.getX(), e.getY(), getWidth(), getHeight(), e.getButton(),
                        SwingUtilities.getWindowAncestor(this).getClass().getSimpleName()));
            }
            double distance = e.getPoint().distance(cx(), cy());
            rememberPrevious();
            if (distance >= innerRadius() - 2 && distance <= outerRadius() + 4) {
                // A click on a harmony marker picks that hue
                for (float offset : HARMONIES[harmony]) {
                    float h = ((hue + offset) % 1F + 1F) % 1F;
                    if (ringPoint(h).distance(e.getPoint()) < 9) {
                        // The matching hue, as light and saturated as the current colour
                        hue = h;
                        setPenColor();
                        drag = NONE;
                        finishPick();
                        return;
                    }
                }
                drag = RING_DRAG;
                greyStart = saturation < 0.03F || brightness < 0.03F;
            } else if (distance < innerRadius()) {
                drag = TRIANGLE_DRAG;
            } else {
                drag = NONE;
                return;
            }
            update(e.getPoint());
        }

        public void mouseDragged(MouseEvent e) {
            if (drag != NONE) {
                update(e.getPoint());
                if (Boolean.getBoolean("alchemy.debugColor")) {
                    Point2D marker = markerPoint();
                    System.out.println(String.format("[wheel] drag mouse %d,%d marker %.0f,%.0f sat %.2f bri %.2f", e.getX(), e.getY(), marker.getX(), marker.getY(), saturation, brightness));
                }
            }
        }

        /** Where the saturation / brightness marker is drawn */
        private Point2D markerPoint() {
            Point2D[] c = corners();
            double wHue = saturation * brightness, wWhite = brightness * (1 - saturation);
            return new Point2D.Double(wHue * c[0].getX() + wWhite * c[1].getX() + (1 - wHue - wWhite) * c[2].getX(),
                    wHue * c[0].getY() + wWhite * c[1].getY() + (1 - wHue - wWhite) * c[2].getY());
        }

        public void mouseReleased(MouseEvent e) {
            if (Boolean.getBoolean("alchemy.debugColor")) {
                System.out.println(String.format("[wheel] release %d,%d hue %.3f sat %.3f bri %.3f = %s", e.getX(), e.getY(), hue, saturation, brightness, hex(currentColor())));
            }
            if (drag != NONE) {
                drag = NONE;
                finishPick();
            }
        }

        public void mouseClicked(MouseEvent e) {
        }

        public void mouseEntered(MouseEvent e) {
        }

        public void mouseExited(MouseEvent e) {
        }

        public void mouseMoved(MouseEvent e) {
        }
    }
}
