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
import java.util.List;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Floating Layers panel - the layers top first, each with show / hide,
 * lock and its name (double click to rename). Below are the opacity of the
 * active layer and buttons to add, copy, delete and reorder layers.
 * Like the colour panel it remembers whether it was open and where.
 */
class AlcLayersPanel extends JDialog implements AlcConstants {

    private static AlcLayersPanel panel;
    private static final Color ROW_ACTIVE = new Color(77, 156, 255, 60);
    private static final Color ICON = new Color(206, 209, 214);
    private static final Color ICON_DIM = new Color(110, 114, 120);
    /** The rows, with the drop line painted while a layer is dragged */
    private final JPanel rows = new JPanel() {

        @Override
        protected void paintChildren(Graphics g) {
            super.paintChildren(g);
            if (dropSlot >= 0 && getComponentCount() > 0) {
                int rowHeight = getComponent(0).getHeight();
                int y = Math.min(getHeight() - 2, dropSlot * rowHeight);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(COLOR_UI_ACCENT);
                g2.fillRoundRect(6, y - 1, getWidth() - 12, 3, 3, 3);
                g2.fillOval(3, y - 3, 7, 7);
                g2.dispose();
            }
        }
    };
    /** Layer being dragged and the gap it would be dropped into, -1 when not dragging */
    private int dragFrom = -1;
    private int dropSlot = -1;
    private final JSlider opacity = new JSlider(0, 100, 100);
    private final JLabel opacityValue = new JLabel();
    /** Ignore slider events while the panel itself sets the value */
    private boolean refreshing = false;

    private AlcLayersPanel() {
        super(Alchemy.window, Alchemy.bundle.getString("layersTitle"), false);
        setType(Window.Type.UTILITY);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBackground(COLOR_UI_BG);
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // OPACITY of the active layer
        JPanel opacityRow = new JPanel(new BorderLayout(6, 0));
        opacityRow.setOpaque(false);
        JLabel opacityLabel = new JLabel(Alchemy.bundle.getString("layerOpacity"));
        opacityLabel.setFont(FONT_SMALL);
        opacityLabel.setForeground(COLOR_UI_TEXT_DIM);
        opacityRow.add(opacityLabel, BorderLayout.WEST);
        opacity.setFocusable(false);
        opacity.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                opacityValue.setText(opacity.getValue() + " %");
                if (!refreshing) {
                    AlcCanvas canvas = Alchemy.canvas;
                    canvas.setLayerOpacity(canvas.getActiveLayer(), opacity.getValue() / 100F, !opacity.getValueIsAdjusting());
                }
            }
        });
        opacityRow.add(opacity, BorderLayout.CENTER);
        opacityValue.setFont(FONT_SMALL);
        opacityValue.setForeground(COLOR_UI_TEXT);
        opacityValue.setPreferredSize(new Dimension(40, 16));
        opacityRow.add(opacityValue, BorderLayout.EAST);
        content.add(opacityRow, BorderLayout.NORTH);

        // LAYER ROWS
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBackground(new Color(26, 27, 30));
        JPanel rowsHolder = new JPanel(new BorderLayout());
        rowsHolder.setBackground(rows.getBackground());
        rowsHolder.add(rows, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(rowsHolder);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(48, 50, 55)));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(240, 240));
        content.add(scroll, BorderLayout.CENTER);

        // BUTTONS
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        buttons.setOpaque(false);
        buttons.add(button("plus", "layerNewDescription", new Runnable() {

            public void run() {
                Alchemy.canvas.addLayer();
            }
        }));
        buttons.add(button("copy", "layerDuplicateDescription", new Runnable() {

            public void run() {
                Alchemy.canvas.duplicateLayer();
            }
        }));
        buttons.add(button("up", "layerUpDescription", new Runnable() {

            public void run() {
                Alchemy.canvas.moveLayer(1);
            }
        }));
        buttons.add(button("down", "layerDownDescription", new Runnable() {

            public void run() {
                Alchemy.canvas.moveLayer(-1);
            }
        }));
        buttons.add(button("trash", "layerDeleteDescription", new Runnable() {

            public void run() {
                Alchemy.canvas.deleteLayer();
            }
        }));
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        rebuild();
        pack();

        int x = AlcPreferences.prefs.getInt("Layers Panel X", Integer.MIN_VALUE);
        int y = AlcPreferences.prefs.getInt("Layers Panel Y", Integer.MIN_VALUE);
        if (x == Integer.MIN_VALUE || !onScreen(x, y)) {
            Rectangle main = Alchemy.window.getBounds();
            x = main.x + main.width - getWidth() - 24;
            y = main.y + main.height - getHeight() - 60;
        }
        setLocation(x, y);
        addComponentListener(new ComponentAdapter() {

            @Override
            public void componentMoved(ComponentEvent e) {
                AlcPreferences.prefs.putInt("Layers Panel X", getX());
                AlcPreferences.prefs.putInt("Layers Panel Y", getY());
            }
        });
        // The dock on the right holds the content unless the panel floats
        setContentPane(new JPanel());
        AlcDock.get().layers.attach(this, content);
    }

    private static void create() {
        if (panel == null) {
            panel = new AlcLayersPanel();
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

    //////////////////////////////////////////////////////////////
    // OPEN / CLOSE
    //////////////////////////////////////////////////////////////
    static void showPanel() {
        create();
        panel.rebuild();
        AlcDock.get().layers.open();
        returnFocusToCanvas();
    }

    static void togglePanel() {
        create();
        panel.rebuild();
        AlcDock.get().layers.toggle();
    }

    static boolean isPanelOpen() {
        return panel != null && AlcDock.get().layers.isOpen();
    }

    /** At start up - docked on the right unless it was closed or floating last time */
    static void restore() {
        create();
        AlcDock.get().layers.restore();
    }

    /** The layers changed - show them again */
    static void refreshPanel() {
        if (panel != null) {
            panel.rebuild();
        }
    }

    /** Hand the keyboard back to the canvas, so the drawing shortcuts keep working */
    private static void returnFocusToCanvas() {
        Alchemy.window.toFront();
        Alchemy.canvas.requestFocusInWindow();
    }

    //////////////////////////////////////////////////////////////
    // CONTENT
    //////////////////////////////////////////////////////////////
    private void rebuild() {
        AlcCanvas canvas = Alchemy.canvas;
        List<AlcLayer> layers = canvas.getLayers();
        rows.removeAll();
        // Top layer first, as in every drawing application
        for (int i = layers.size() - 1; i >= 0; i--) {
            rows.add(new Row(layers.get(i), i, i == canvas.getActiveLayerIndex()));
        }
        refreshing = true;
        opacity.setValue(Math.round(canvas.getActiveLayer().opacity * 100));
        opacityValue.setText(opacity.getValue() + " %");
        refreshing = false;
        rows.revalidate();
        rows.repaint();
    }

    /** The gap between rows nearest to y - 0 is above the top row */
    private int slotAt(int y) {
        int count = rows.getComponentCount();
        if (count == 0) {
            return 0;
        }
        int rowHeight = Math.max(1, rows.getComponent(0).getHeight());
        return Math.max(0, Math.min(count, Math.round(y / (float) rowHeight)));
    }

    /** The layer index a layer dropped into a gap ends up at. The list shows the top layer first. */
    int targetIndex(int from, int slot) {
        int count = Alchemy.canvas.getLayers().size();
        int listPosition = count - 1 - from;
        int newListPosition = (slot > listPosition) ? slot - 1 : slot;
        return count - 1 - newListPosition;
    }

    private JButton button(final String icon, String tooltipKey, final Runnable action) {
        JButton b = new JButton(new GlyphIcon(icon, false));
        b.setToolTipText(Alchemy.bundle.getString(tooltipKey));
        b.setFocusable(false);
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setMargin(new Insets(3, 3, 3, 3));
        b.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                action.run();
                returnFocusToCanvas();
            }
        });
        return b;
    }

    /** One layer in the list */
    private class Row extends JPanel {

        private final AlcLayer layer;
        private final boolean active;

        Row(final AlcLayer layer, final int index, boolean active) {
            super(new BorderLayout(4, 0));
            this.layer = layer;
            this.active = active;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 8));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

            JPanel toggles = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            toggles.setOpaque(false);
            toggles.add(toggle(layer.visible ? "eye" : "eye-off", "layerVisibleDescription", new Runnable() {

                public void run() {
                    Alchemy.canvas.setLayerVisible(layer, !layer.visible);
                }
            }));
            toggles.add(toggle(layer.locked ? "lock" : "unlock", "layerLockDescription", new Runnable() {

                public void run() {
                    Alchemy.canvas.setLayerLocked(layer, !layer.locked);
                }
            }));
            add(toggles, BorderLayout.WEST);

            JLabel name = new JLabel(layer.name);
            name.setFont(active ? FONT_MEDIUM_BOLD : FONT_MEDIUM);
            name.setForeground(layer.visible ? COLOR_UI_TEXT : COLOR_UI_TEXT_DIM);
            add(name, BorderLayout.CENTER);

            JLabel count = new JLabel(Integer.toString(layer.shapes.size()));
            count.setFont(FONT_SMALL);
            count.setForeground(COLOR_UI_TEXT_DIM);
            count.setToolTipText(Alchemy.bundle.getString("layerShapeCount"));
            add(count, BorderLayout.EAST);

            // Click selects, double click renames, dragging moves the layer like in Photoshop
            MouseAdapter select = new MouseAdapter() {

                private Point start;

                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        String newName = (String) JOptionPane.showInputDialog(Alchemy.window,
                                Alchemy.bundle.getString("layerRename"), Alchemy.bundle.getString("layersTitle"),
                                JOptionPane.PLAIN_MESSAGE, null, null, layer.name);
                        Alchemy.canvas.renameLayer(layer, newName);
                        returnFocusToCanvas();
                        return;
                    }
                    start = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), rows);
                    dragFrom = index;
                    dropSlot = -1;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragFrom < 0 || start == null) {
                        return;
                    }
                    Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), rows);
                    if (dropSlot < 0 && Math.abs(p.y - start.y) < 5) {
                        return;
                    }
                    dropSlot = slotAt(p.y);
                    rows.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    rows.repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (dragFrom < 0) {
                        return;
                    }
                    int from = dragFrom;
                    int slot = dropSlot;
                    dragFrom = -1;
                    dropSlot = -1;
                    rows.setCursor(Cursor.getDefaultCursor());
                    rows.repaint();
                    if (slot < 0) {
                        Alchemy.canvas.setActiveLayer(from);
                    } else {
                        Alchemy.canvas.moveLayerTo(from, targetIndex(from, slot));
                    }
                    returnFocusToCanvas();
                }
            };
            addMouseListener(select);
            addMouseMotionListener(select);
            name.addMouseListener(select);
            name.addMouseMotionListener(select);
            count.addMouseListener(select);
            count.addMouseMotionListener(select);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (active) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ROW_ACTIVE);
                g2.fillRoundRect(2, 1, getWidth() - 4, getHeight() - 2, 8, 8);
                g2.setColor(COLOR_UI_ACCENT);
                g2.fillRoundRect(2, 7, 3, getHeight() - 14, 3, 3);
                g2.dispose();
            }
            super.paintComponent(g);
        }

        private JButton toggle(String icon, String tooltipKey, final Runnable action) {
            boolean off = icon.equals("eye-off") || icon.equals("unlock");
            JButton b = new JButton(new GlyphIcon(icon, off));
            b.setToolTipText(Alchemy.bundle.getString(tooltipKey));
            b.setFocusable(false);
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.setMargin(new Insets(2, 3, 2, 3));
            b.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    action.run();
                    returnFocusToCanvas();
                }
            });
            return b;
        }
    }

    /** Small line icons for the panel */
    private static class GlyphIcon implements Icon {

        private final String name;
        private final boolean dim;

        GlyphIcon(String name, boolean dim) {
            this.name = name;
            this.dim = dim;
        }

        public int getIconWidth() {
            return 18;
        }

        public int getIconHeight() {
            return 18;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            g2.setColor(dim ? ICON_DIM : ICON);
            g2.setStroke(new BasicStroke(1.4F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (name.startsWith("eye")) {
                Path2D eye = new Path2D.Double();
                eye.moveTo(2, 9);
                eye.quadTo(9, 2, 16, 9);
                eye.quadTo(9, 16, 2, 9);
                g2.draw(eye);
                g2.fill(new Ellipse2D.Double(6.8, 6.8, 4.4, 4.4));
                if (name.equals("eye-off")) {
                    g2.draw(new Line2D.Double(3, 15, 15, 3));
                }
            } else if (name.equals("lock") || name.equals("unlock")) {
                g2.draw(new RoundRectangle2D.Double(4, 8.5, 10, 7, 2, 2));
                Path2D shackle = new Path2D.Double();
                shackle.moveTo(6, 8.5);
                shackle.lineTo(6, 6);
                shackle.curveTo(6, 2.5, 12, 2.5, 12, 6);
                if (name.equals("lock")) {
                    shackle.lineTo(12, 8.5);
                }
                g2.draw(shackle);
            } else if (name.equals("plus")) {
                g2.draw(new Line2D.Double(9, 3.5, 9, 14.5));
                g2.draw(new Line2D.Double(3.5, 9, 14.5, 9));
            } else if (name.equals("copy")) {
                g2.draw(new RoundRectangle2D.Double(6, 6, 9, 9, 2, 2));
                Path2D back = new Path2D.Double();
                back.moveTo(3, 12);
                back.lineTo(3, 3);
                back.lineTo(12, 3);
                g2.draw(back);
            } else if (name.equals("up") || name.equals("down")) {
                if (name.equals("down")) {
                    g2.rotate(Math.PI, 9, 9);
                }
                g2.draw(new Line2D.Double(9, 15, 9, 3.5));
                Path2D head = new Path2D.Double();
                head.moveTo(4.5, 8);
                head.lineTo(9, 3.5);
                head.lineTo(13.5, 8);
                g2.draw(head);
            } else if (name.equals("trash")) {
                g2.draw(new Line2D.Double(3, 5, 15, 5));
                g2.draw(new Line2D.Double(7, 5, 7.5, 3));
                g2.draw(new Line2D.Double(11, 5, 10.5, 3));
                Path2D bin = new Path2D.Double();
                bin.moveTo(4.5, 5);
                bin.lineTo(5.5, 15);
                bin.lineTo(12.5, 15);
                bin.lineTo(13.5, 5);
                g2.draw(bin);
            }
            g2.dispose();
        }
    }
}
