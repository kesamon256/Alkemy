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
package org.alchemy.affect;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import org.alchemy.core.*;

/**
 * Gradient.java
 * Every stroke starts in the current colour and turns into a second colour by its end,
 * following the line around every curve. Brushes made of many shapes (Flow, charcoal, pencil)
 * shade over the whole stroke too - each shape takes the colours of the moments it was drawn in.
 */
public class Gradient extends AlcModule {

    private static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userNodeForPackage(Gradient.class);
    /** The colour at the end of every stroke */
    private Color end = new Color(PREFS.getInt("Gradient End Colour", new Color(230, 70, 60).getRGB()));
    /** Fade out to nothing instead of turning into the end colour */
    private boolean fadeOut = PREFS.getBoolean("Gradient Fade Out", false);
    private AlcToolBarSubSection subToolBarSection;

    /** While drawing, the gradient is stretched over the stroke in steps of this much longer */
    private static final double STEP = 1.06;
    /** How far the pen has gone in this stroke */
    private float travelled;
    private Point2D.Float lastPen;

    /** When a shape of the stroke appeared and when it last changed - as distances the pen had gone */
    private static final class Mark {
        float from, to;
        int version;
        double x, y, w, h;
    }
    private final Map<AlcShape, Mark> marks = new IdentityHashMap<AlcShape, Mark>();

    public Gradient() {
    }

    @Override
    public void setup() {
        createSubToolBarSection();
        toolBar.addSubToolBarSection(subToolBarSection);
    }

    @Override
    public void reselect() {
        toolBar.addSubToolBarSection(subToolBarSection);
    }

    private void createSubToolBarSection() {
        subToolBarSection = new AlcToolBarSubSection(this);

        // The start colour - the current colour of the colour wheel
        final AlcSubButton startButton = new AlcSubButton("Start");
        startButton.setIcon(new Swatch(true));
        startButton.setToolTipText("The colour every stroke starts with - the current colour");
        startButton.setFocusable(false);
        subToolBarSection.add(startButton);

        // The end colour
        final AlcSubButton endButton = new AlcSubButton("End");
        endButton.setIcon(new Swatch(false));
        endButton.setToolTipText("The colour every stroke turns into by its end - click to choose");
        endButton.setFocusable(false);
        endButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                colorSelector.show(new ActionListener() {

                    public void actionPerformed(ActionEvent event) {
                        setEnd(colorSelector.getColor());
                        endButton.repaint();
                    }
                }, null, end);
            }
        });
        subToolBarSection.add(endButton);

        // Swap the two colours
        final AlcSubButton swapButton = new AlcSubButton("Swap");
        swapButton.setToolTipText("Swap the start and end colours");
        swapButton.setFocusable(false);
        swapButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                Color start = canvas.getColor();
                canvas.setColor(new Color(end.getRed(), end.getGreen(), end.getBlue()));
                canvas.refreshColorButtonRelay();
                setEnd(start);
                startButton.repaint();
                endButton.repaint();
            }
        });
        subToolBarSection.add(swapButton);

        // Fade out instead of a second colour
        final AlcSubToggleButton fadeButton = new AlcSubToggleButton("Fade Out", null);
        fadeButton.setToolTipText("Fade every stroke out to nothing instead of turning it into the end colour");
        fadeButton.setSelected(fadeOut);
        fadeButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                fadeOut = fadeButton.isSelected();
                PREFS.putBoolean("Gradient Fade Out", fadeOut);
                endButton.repaint();
            }
        });
        subToolBarSection.add(fadeButton);
    }

    private void setEnd(Color c) {
        if (c != null) {
            end = new Color(c.getRed(), c.getGreen(), c.getBlue());
            PREFS.putInt("Gradient End Colour", end.getRGB());
        }
    }

    /** The colour at the end of a stroke of this shape */
    private Color endColor(AlcShape shape) {
        if (fadeOut) {
            Color c = shape.getColor();
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), 0);
        }
        return end;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        marks.clear();
        travelled = 0;
        lastPen = canvas.getPenLocation();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        Point2D.Float p = canvas.getPenLocation();
        if (lastPen != null && p != null) {
            travelled += (float) p.distance(lastPen);
        }
        lastPen = p;
    }

    @Override
    protected void affect() {
        // The stroke so far runs from the start colour to the end colour - it stretches as the stroke grows.
        // While drawing it stretches in small steps, so what is drawn keeps its colours most of the time
        // and is not painted again on every move; when the pen lifts the end is exactly the end colour
        float total = Math.max(1F, travelled);
        if (canvas.isPenDown()) {
            // At least 40 px at a time - at the start of a stroke 6 % would be every move
            total = (total < 40 / (STEP - 1)) ? (float) (Math.ceil(total / 40) * 40)
                    : (float) Math.pow(STEP, Math.ceil(Math.log(total) / Math.log(STEP)));
        }
        mark(canvas.createShapes, total, true);
        // Copies made by affects before this one (Mirror runs after and copies the gradient itself)
        mark(canvas.affectShapes, total, false);
    }

    private void mark(List<AlcShape> shapes, float total, boolean all) {
        for (int i = 0; i < shapes.size(); i++) {
            AlcShape s = shapes.get(i);
            Mark m = marks.get(s);
            if (m == null && !all && s.getAlongColor() != null) {
                continue;
            }
            Rectangle2D b = s.getCachedBounds();
            if (m == null) {
                m = new Mark();
                m.from = m.to = travelled;
                marks.put(s, m);
            } else if (m.version != version(s) || m.x != b.getX() || m.y != b.getY() || m.w != b.getWidth() || m.h != b.getHeight()) {
                // Still growing - it reaches to where the pen is now
                m.to = travelled;
            }
            m.version = version(s);
            m.x = b.getX();
            m.y = b.getY();
            m.w = b.getWidth();
            m.h = b.getHeight();
            s.setAlongGradient(endColor(s), m.from / total, m.to / total);
        }
    }

    /** Changes with the shape's points - its number of points is enough to see it grow */
    private static int version(AlcShape s) {
        return s.getTotalPoints() * 31 + (s.getSpine() == null ? 0 : s.getSpine().size());
    }

    /** A small colour swatch for the buttons - the start one shows the current colour */
    private class Swatch implements Icon {

        private final boolean start;

        Swatch(boolean start) {
            this.start = start;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color colour = start ? canvas.getColor() : end;
            if (!start && fadeOut) {
                // A checkerboard shows it fades to nothing
                g2.setColor(new Color(200, 200, 200));
                g2.fillRoundRect(x, y, 14, 14, 4, 4);
                g2.setColor(new Color(130, 130, 130));
                g2.fillRect(x + 2, y + 2, 5, 5);
                g2.fillRect(x + 7, y + 7, 5, 5);
            } else {
                g2.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue()));
                g2.fillRoundRect(x, y, 14, 14, 4, 4);
            }
            g2.setColor(new Color(255, 255, 255, 90));
            g2.drawRoundRect(x, y, 13, 13, 4, 4);
            g2.dispose();
        }

        public int getIconWidth() {
            return 14;
        }

        public int getIconHeight() {
            return 14;
        }
    }
}
