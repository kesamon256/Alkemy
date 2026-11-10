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

import java.awt.Component;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.Border;
import java.awt.event.*;

class AlcPopupMenu extends JPopupMenu implements AlcConstants {

    final static int uiPopupMenuY = 47;
    boolean inside = false;
    boolean clickOk = true;

    /** Creates a new instance of AlcPopupMenu */
    AlcPopupMenu() {

        // Set the color for the bg
        this.setBackground(COLOR_UI_HIGHLIGHT);
        // Make sure the popup is opaque
        this.setOpaque(true);
        // Theme border (rounded on Windows 11) with a little room around the rows
        Border theme = UIManager.getBorder("PopupMenu.border");
        this.setBorder(BorderFactory.createCompoundBorder(theme, BorderFactory.createEmptyBorder(3, 0, 3, 0)));

        this.addMouseListener(createMouseListener(this));
        this.addPopupMenuListener(createPopupMenuListener());

    }

    @Override
    public void show(Component invoker, int x, int y) {
        // A long menu is split into columns so it fits on the screen - no scroll arrows
        int available = Integer.MAX_VALUE;
        try {
            java.awt.GraphicsConfiguration gc = invoker.getGraphicsConfiguration();
            java.awt.Rectangle screen = gc.getBounds();
            java.awt.Insets insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc);
            int top = invoker.getLocationOnScreen().y + y;
            available = Math.max(200, screen.y + screen.height - insets.bottom - top - 16);
        } catch (RuntimeException notOnScreen) {
            // Not on the screen yet - one column
        }
        setLayout(new ColumnLayout(available));
        super.show(invoker, x, y);
        // Get rid of the window shadow on Mac
        if (Alchemy.OS == OS_MAC) {
            this.getRootPane().putClientProperty("Window.shadow", Boolean.FALSE);
        }
    }

    /** Items top to bottom, starting a new column when the next item would not fit in the height */
    private static class ColumnLayout implements java.awt.LayoutManager {

        private final int maxHeight;

        ColumnLayout(int maxHeight) {
            this.maxHeight = maxHeight;
        }

        public void addLayoutComponent(String name, Component comp) {
        }

        public void removeLayoutComponent(Component comp) {
        }

        /** Place the items, or only measure them when place is false */
        private java.awt.Dimension arrange(java.awt.Container parent, boolean place) {
            java.awt.Insets in = parent.getInsets();
            int limit = maxHeight - in.top - in.bottom;
            // Split into columns first
            java.util.ArrayList<java.util.ArrayList<Component>> columns = new java.util.ArrayList<java.util.ArrayList<Component>>();
            java.util.ArrayList<Component> column = new java.util.ArrayList<Component>();
            int height = 0;
            for (Component c : parent.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                int h = c.getPreferredSize().height;
                if (!column.isEmpty() && height + h > limit) {
                    columns.add(column);
                    column = new java.util.ArrayList<Component>();
                    height = 0;
                }
                column.add(c);
                height += h;
            }
            if (!column.isEmpty()) {
                columns.add(column);
            }
            // With more than one column, share the items out evenly
            if (columns.size() > 1) {
                java.util.ArrayList<Component> all = new java.util.ArrayList<Component>();
                for (java.util.ArrayList<Component> col : columns) {
                    all.addAll(col);
                }
                int perColumn = (int) Math.ceil(all.size() / (double) columns.size());
                columns.clear();
                for (int i = 0; i < all.size(); i += perColumn) {
                    columns.add(new java.util.ArrayList<Component>(all.subList(i, Math.min(all.size(), i + perColumn))));
                }
            }
            // Then lay them out, every column as wide as its widest item
            int x = in.left, tallest = 0;
            for (java.util.ArrayList<Component> col : columns) {
                int width = 0;
                for (Component c : col) {
                    width = Math.max(width, c.getPreferredSize().width);
                }
                int yy = in.top;
                for (Component c : col) {
                    int h = c.getPreferredSize().height;
                    if (place) {
                        c.setBounds(x, yy, width, h);
                    }
                    yy += h;
                }
                tallest = Math.max(tallest, yy - in.top);
                x += width;
            }
            return new java.awt.Dimension(x + in.right, tallest + in.top + in.bottom);
        }

        public java.awt.Dimension preferredLayoutSize(java.awt.Container parent) {
            return arrange(parent, false);
        }

        public java.awt.Dimension minimumLayoutSize(java.awt.Container parent) {
            return arrange(parent, false);
        }

        public void layoutContainer(java.awt.Container parent) {
            arrange(parent, true);
        }
    }

    PopupMenuListener createPopupMenuListener() {
        return new PopupMenuListener() {

            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                // Buggy hack here but 
                // When the popup is clicked on then clicked off again
                // It is first set to invisible then mousePressed() is called
                // Use a timer to delay the reseting of the clickOk variable
                javax.swing.Timer initialDelay = new javax.swing.Timer(50, new ActionListener() {

                    public void actionPerformed(ActionEvent evt) {

                        clickOk = true;
                        //System.out.println("clickOK = True");
                    }
                });

                initialDelay.setRepeats(false);
                initialDelay.start();
            }

            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        };
    }

    MouseListener createMouseListener(final AlcPopupMenu popup) {
        return new MouseListener() {

            public void mouseClicked(MouseEvent e) {
            }

            public void mousePressed(MouseEvent e) {
            }

            public void mouseReleased(MouseEvent e) {
            }

            public void mouseEntered(MouseEvent e) {
                inside = popup.contains(e.getPoint());
            }

            public void mouseExited(MouseEvent e) {
                inside = popup.contains(e.getPoint());
            }
        };
    }
}
