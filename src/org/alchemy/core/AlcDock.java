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
import java.awt.geom.*;
import javax.swing.*;

/**
 * The panel column on the right of the window, as in Photoshop.
 * <p>
 * It holds the colour and layers panels one above the other. Each panel can
 * be collapsed to its title, popped out into a floating window and docked
 * again, or closed. Where every panel was is remembered for the next start.
 */
final class AlcDock extends JPanel implements AlcConstants {

    static final int HIDDEN = 0, DOCKED = 1, FLOATING = 2;
    private static final Color DOCK_BG = new Color(24, 25, 28);
    private static final Color HEADER_BG = new Color(33, 35, 39);
    private static final Color EDGE = new Color(46, 48, 53);
    private static final Color GLYPH = new Color(190, 194, 200);
    private static AlcDock dock;
    static final int MIN_WIDTH = 240, MAX_WIDTH = 640;
    final Section colour;
    final Section layers;
    /** The panels, beside the grip on the left edge */
    private final JPanel inner = new JPanel(new BorderLayout());

    private AlcDock() {
        super(new BorderLayout());
        setBackground(DOCK_BG);
        inner.setOpaque(false);
        add(new Grip(), BorderLayout.WEST);
        add(inner, BorderLayout.CENTER);
        colour = new Section("colorPanelTitle", "Colour Panel");
        layers = new Section("layersTitle", "Layers Panel");
        setVisible(false);
    }

    /** The left edge of the dock - drag it to make the panels wider or narrower */
    private final class Grip extends JComponent {

        private int startX, startWidth;
        private boolean hover, dragging;

        Grip() {
            setPreferredSize(new Dimension(5, 10));
            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            setToolTipText("Drag to make the panels wider or narrower");
            MouseAdapter drag = new MouseAdapter() {

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    dragging = true;
                    startX = e.getXOnScreen();
                    startWidth = AlcDock.this.getWidth();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    // The right edge stays put, the left edge follows the mouse
                    setDockWidth(startWidth - (e.getXOnScreen() - startX), false);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragging = false;
                    setDockWidth(startWidth - (e.getXOnScreen() - startX), true);
                    repaint();
                }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(DOCK_BG);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor((hover || dragging) ? COLOR_UI_ACCENT : EDGE);
            g.fillRect(0, 0, (hover || dragging) ? 3 : 1, getHeight());
        }
    }

    /** Set the width of the dock - remembered for the next start */
    void setDockWidth(int width, boolean finished) {
        int w = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
        AlcPreferences.prefs.putInt("Dock Width", w);
        if (Alchemy.window != null) {
            Alchemy.window.layoutDock(finished);
        }
    }

    static AlcDock get() {
        if (dock == null) {
            dock = new AlcDock();
        }
        return dock;
    }

    /** True when the dock takes room on the right of the window */
    static boolean isShown() {
        return dock != null && dock.isVisible();
    }

    /** Width of the column - as the user set it, at first wide enough for the widest docked panel */
    int dockWidth() {
        int saved = AlcPreferences.prefs.getInt("Dock Width", -1);
        if (saved > 0) {
            return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, saved));
        }
        return 290;
    }

    /** Lay the docked panels out from the top - the layers take the room that is left */
    private void relayout() {
        inner.removeAll();
        // Panels at the top keep their full height, an open layers panel takes the room that is left
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        int count = 0;
        for (Section s : new Section[]{colour, layers}) {
            if (s.mode == DOCKED && s.content != null) {
                count++;
                if (s == layers && !s.collapsed) {
                    inner.add(s, BorderLayout.CENTER);
                } else {
                    s.setAlignmentX(Component.LEFT_ALIGNMENT);
                    top.add(s);
                }
            }
        }
        inner.add(top, BorderLayout.NORTH);
        boolean show = count > 0;
        setVisible(show);
        revalidate();
        repaint();
        if (Alchemy.window != null) {
            Alchemy.window.layoutDock(true);
        }
    }

    //////////////////////////////////////////////////////////////
    // SECTION - one panel with its title bar
    //////////////////////////////////////////////////////////////
    final class Section extends JPanel {

        private final String titleKey;
        private final String prefKey;
        private JDialog dialog;
        private JComponent content;
        private int mode = DOCKED;
        private boolean collapsed = false;
        private final JPanel body = new JPanel(new BorderLayout());
        private final JButton collapseButton;
        private JPanel floatingHolder;

        Section(String titleKey, String prefKey) {
            super(new BorderLayout());
            this.titleKey = titleKey;
            this.prefKey = prefKey;
            setOpaque(false);
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, EDGE));
            mode = AlcPreferences.prefs.getInt(prefKey + " Mode", DOCKED);
            collapsed = AlcPreferences.prefs.getBoolean(prefKey + " Collapsed", false);

            // TITLE BAR - click it to collapse, buttons to pop out and close
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(HEADER_BG);
            header.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 2));
            collapseButton = glyphButton(collapsed ? "closed" : "open", "Collapse or expand the panel", new Runnable() {

                public void run() {
                    setCollapsed(!collapsed);
                }
            });
            JLabel title = new JLabel(Alchemy.bundle.getString(titleKey));
            title.setFont(FONT_MEDIUM_BOLD);
            title.setForeground(COLOR_UI_TEXT);
            title.addMouseListener(new MouseAdapter() {

                @Override
                public void mouseClicked(MouseEvent e) {
                    setCollapsed(!collapsed);
                }
            });
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            left.setOpaque(false);
            left.add(collapseButton);
            left.add(title);
            header.add(left, BorderLayout.WEST);
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            right.setOpaque(false);
            right.add(glyphButton("float", "Pop the panel out into its own window", new Runnable() {

                public void run() {
                    setMode(FLOATING);
                }
            }));
            right.add(glyphButton("close", "Close the panel - open it again from the View menu", new Runnable() {

                public void run() {
                    setMode(HIDDEN);
                }
            }));
            header.add(right, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);
            body.setOpaque(false);
            body.setVisible(!collapsed);
            add(body, BorderLayout.CENTER);
        }

        /** The panel hands over its window and its content - shown where it was last time */
        void attach(JDialog dialog, JComponent content) {
            this.dialog = dialog;
            this.content = content;
            dialog.addWindowListener(new WindowAdapter() {

                @Override
                public void windowClosing(WindowEvent e) {
                    setMode(HIDDEN);
                }
            });
        }

        boolean isOpen() {
            return content != null && mode != HIDDEN;
        }

        boolean isDocked() {
            return content != null && mode == DOCKED;
        }

        /** Open the panel where it was last open - docked unless it was floating */
        void open() {
            int last = AlcPreferences.prefs.getInt(prefKey + " Last", DOCKED);
            setMode(last == FLOATING ? FLOATING : DOCKED);
        }

        void toggle() {
            if (isOpen()) {
                setMode(HIDDEN);
            } else {
                open();
            }
        }

        /** Show the panel as it was left - at start up */
        void restore() {
            setMode(mode);
        }

        void setMode(int newMode) {
            if (content == null) {
                return;
            }
            mode = newMode;
            AlcPreferences.prefs.putInt(prefKey + " Mode", mode);
            if (mode != HIDDEN) {
                AlcPreferences.prefs.putInt(prefKey + " Last", mode);
            }
            if (mode == FLOATING) {
                body.removeAll();
                if (floatingHolder == null) {
                    floatingHolder = new JPanel(new BorderLayout());
                    floatingHolder.setBackground(COLOR_UI_BG);
                    JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
                    bar.setBackground(HEADER_BG);
                    bar.add(glyphButton("dock", "Dock the panel on the right of the window", new Runnable() {

                        public void run() {
                            setMode(DOCKED);
                        }
                    }));
                    floatingHolder.add(bar, BorderLayout.NORTH);
                }
                floatingHolder.add(content, BorderLayout.CENTER);
                dialog.setContentPane(floatingHolder);
                dialog.pack();
                dialog.setVisible(true);
            } else {
                dialog.setVisible(false);
                if (mode == DOCKED) {
                    if (floatingHolder != null) {
                        floatingHolder.remove(content);
                    }
                    body.removeAll();
                    body.add(content, BorderLayout.CENTER);
                }
            }
            relayout();
            Alchemy.canvas.requestFocusInWindow();
        }

        private void setCollapsed(boolean c) {
            collapsed = c;
            AlcPreferences.prefs.putBoolean(prefKey + " Collapsed", collapsed);
            body.setVisible(!collapsed);
            collapseButton.setIcon(new Glyph(collapsed ? "closed" : "open"));
            relayout();
        }
    }

    private JButton glyphButton(String glyph, String tip, final Runnable action) {
        JButton b = new JButton(new Glyph(glyph));
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setMargin(new Insets(2, 2, 2, 2));
        b.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
        return b;
    }

    /** Small line icons for the title bars */
    private static class Glyph implements Icon {

        private final String name;

        Glyph(String name) {
            this.name = name;
        }

        public int getIconWidth() {
            return 16;
        }

        public int getIconHeight() {
            return 16;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(GLYPH);
            g2.setStroke(new BasicStroke(1.4F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Double();
            if (name.equals("open")) {
                p.moveTo(4, 6);
                p.lineTo(8, 10);
                p.lineTo(12, 6);
            } else if (name.equals("closed")) {
                p.moveTo(6, 4);
                p.lineTo(10, 8);
                p.lineTo(6, 12);
            } else if (name.equals("close")) {
                p.moveTo(4.5, 4.5);
                p.lineTo(11.5, 11.5);
                p.moveTo(11.5, 4.5);
                p.lineTo(4.5, 11.5);
            } else if (name.equals("float")) {
                // A window with an arrow out of it
                g2.draw(new RoundRectangle2D.Double(2.5, 5.5, 8, 8, 2, 2));
                p.moveTo(7.5, 8.5);
                p.lineTo(13.5, 2.5);
                p.moveTo(9.5, 2.5);
                p.lineTo(13.5, 2.5);
                p.lineTo(13.5, 6.5);
            } else if (name.equals("dock")) {
                // An arrow into the right edge
                g2.draw(new Line2D.Double(13.5, 2.5, 13.5, 13.5));
                p.moveTo(2.5, 8);
                p.lineTo(10.5, 8);
                p.moveTo(7.5, 5);
                p.lineTo(10.5, 8);
                p.lineTo(7.5, 11);
            }
            g2.draw(p);
            g2.dispose();
        }
    }
}
