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
import javax.swing.*;

/**
 * Lets the user choose which part of the (endless) drawing to export.
 * <p>
 * While active, the canvas shows a frame over the drawing with the outside
 * dimmed. Drag on the canvas to draw a new frame, drag inside it to move it
 * and drag its corners to resize it. A small bar offers the whole drawing or
 * the visible area as quick choices. Enter exports, Escape cancels.
 * The frame is kept in drawing coordinates, so zooming and panning still work.
 */
class AlcExportSelector implements AlcConstants {

    private static final int HANDLE = 9;
    private static final int NONE = -1, MOVE = 4, CREATE = 5;
    private final AlcCanvas canvas;
    private final Runnable onExport;
    /** The chosen area in drawing coordinates */
    private Rectangle2D area;
    private int dragMode = NONE;
    private Point2D.Float dragStart;
    private Rectangle2D areaAtDragStart;
    private JPanel bar;
    private JTextField widthField, heightField;
    /** Largest export side that can be typed in */
    private static final int MAX_SIZE = 20000;
    private KeyEventDispatcher keys;

    AlcExportSelector(AlcCanvas canvas, Rectangle2D initialArea, Runnable onExport) {
        this.canvas = canvas;
        this.onExport = onExport;
        this.area = initialArea;
        createBar();
        keys = new KeyEventDispatcher() {

            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() != KeyEvent.KEY_PRESSED) {
                    return false;
                }
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // Enter in a size field only applies the typed size
                    Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                    if (focus == widthField || focus == heightField) {
                        return false;
                    }
                    export();
                    return true;
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    cancel();
                    return true;
                }
                return false;
            }
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keys);
        updateSizeLabel();
    }

    Rectangle2D getArea() {
        return area;
    }

    //////////////////////////////////////////////////////////////
    // CONTROL BAR
    //////////////////////////////////////////////////////////////
    private void createBar() {
        bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6)) {

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(30, 32, 35, 240));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(new Color(72, 76, 82));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        JLabel title = new JLabel(Alchemy.bundle.getString("exportAreaTitle"));
        title.setFont(FONT_MEDIUM_BOLD);
        title.setForeground(COLOR_UI_TEXT);
        title.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 4));
        bar.add(title);
        bar.add(button("exportAreaDrawing", new Runnable() {

            public void run() {
                Rectangle2D drawing = canvas.getDrawingBounds();
                if (drawing != null) {
                    double pad = 20;
                    setArea(new Rectangle2D.Double(drawing.getX() - pad, drawing.getY() - pad, drawing.getWidth() + pad * 2, drawing.getHeight() + pad * 2));
                }
            }
        }, false));
        bar.add(button("exportAreaVisible", new Runnable() {

            public void run() {
                setArea(canvas.getVisibleDrawingArea());
            }
        }, false));
        // Size in pixels - can be typed in, the top left corner stays in place
        widthField = sizeField();
        heightField = sizeField();
        bar.add(Box.createHorizontalStrut(4));
        bar.add(widthField);
        bar.add(dimLabel("×"));
        bar.add(heightField);
        bar.add(dimLabel("px"));
        bar.add(Box.createHorizontalStrut(4));
        bar.add(button("cancel", new Runnable() {

            public void run() {
                cancel();
            }
        }, false));
        bar.add(button("exportAreaExport", new Runnable() {

            public void run() {
                export();
            }
        }, true));
        layeredPane().add(bar, Integer.valueOf(3));
        layoutBar();
    }

    private JTextField sizeField() {
        final JTextField field = new JTextField(5);
        field.setFont(FONT_MEDIUM);
        field.setHorizontalAlignment(JTextField.CENTER);
        field.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                applyTypedSize();
            }
        });
        field.addFocusListener(new FocusAdapter() {

            @Override
            public void focusGained(FocusEvent e) {
                field.selectAll();
            }

            @Override
            public void focusLost(FocusEvent e) {
                applyTypedSize();
            }
        });
        return field;
    }

    private JLabel dimLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_SMALL);
        label.setForeground(COLOR_UI_TEXT_DIM);
        return label;
    }

    /** Resize the frame to the typed width and height - invalid numbers are put back */
    void applyTypedSize() {
        try {
            int w = Integer.parseInt(widthField.getText().trim());
            int h = Integer.parseInt(heightField.getText().trim());
            if (w >= 1 && h >= 1 && w <= MAX_SIZE && h <= MAX_SIZE) {
                setArea(new Rectangle2D.Double(area.getX(), area.getY(), w, h));
                return;
            }
        } catch (NumberFormatException ex) {
            // Fall through and show the current size again
        }
        updateSizeLabel();
    }

    private JButton button(String key, final Runnable action, boolean primary) {
        JButton b = new JButton(Alchemy.bundle.getString(key));
        b.setFont(FONT_MEDIUM);
        b.setFocusable(false);
        if (primary) {
            b.putClientProperty("JButton.buttonType", "default");
            b.setBackground(COLOR_UI_ACCENT);
            b.setForeground(Color.WHITE);
        }
        b.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
        return b;
    }

    private JLayeredPane layeredPane() {
        return (JLayeredPane) Alchemy.window.getContentPane();
    }

    /** Keep the bar centred at the bottom of the canvas */
    void layoutBar() {
        Dimension size = bar.getPreferredSize();
        // The canvas can be larger than the inside of the window - use the visible area
        JLayeredPane pane = layeredPane();
        int x = (pane.getWidth() - size.width) / 2;
        int y = pane.getHeight() - size.height - 20;
        if (bar.getX() != x || bar.getY() != y || bar.getWidth() != size.width) {
            bar.setBounds(x, y, size.width, size.height);
            bar.validate();
        }
    }

    private void updateSizeLabel() {
        widthField.setText(Integer.toString((int) Math.ceil(area.getWidth())));
        heightField.setText(Integer.toString((int) Math.ceil(area.getHeight())));
        layoutBar();
    }

    void setArea(Rectangle2D newArea) {
        area = newArea;
        updateSizeLabel();
        canvas.repaint();
    }

    //////////////////////////////////////////////////////////////
    // FINISH
    //////////////////////////////////////////////////////////////
    private void close() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keys);
        layeredPane().remove(bar);
        layeredPane().repaint();
        canvas.requestFocusInWindow();
        canvas.endExportSelection();
    }

    void cancel() {
        close();
    }

    void export() {
        // Take a size that was typed but not yet confirmed
        applyTypedSize();
        if (area.getWidth() < 2 || area.getHeight() < 2) {
            return;
        }
        close();
        onExport.run();
    }

    //////////////////////////////////////////////////////////////
    // PAINTING
    //////////////////////////////////////////////////////////////
    /** Area on screen */
    private Rectangle2D screenArea() {
        return canvas.getViewTransform().createTransformedShape(area).getBounds2D();
    }

    void paint(Graphics2D g2, int w, int h) {
        layoutBar();
        Rectangle2D r = screenArea();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Dim everything outside the frame
        Area outside = new Area(new Rectangle(0, 0, w, h));
        outside.subtract(new Area(r));
        g2.setColor(new Color(0, 0, 0, 110));
        g2.fill(outside);
        // Frame
        g2.setStroke(new BasicStroke(1.5F));
        g2.setColor(COLOR_UI_ACCENT);
        g2.draw(r);
        // Corner handles
        for (Point2D corner : corners(r)) {
            Rectangle2D handle = new Rectangle2D.Double(corner.getX() - HANDLE / 2.0, corner.getY() - HANDLE / 2.0, HANDLE, HANDLE);
            g2.setColor(Color.WHITE);
            g2.fill(handle);
            g2.setColor(COLOR_UI_ACCENT);
            g2.draw(handle);
        }
    }

    private Point2D[] corners(Rectangle2D r) {
        return new Point2D[]{
                    new Point2D.Double(r.getMinX(), r.getMinY()),
                    new Point2D.Double(r.getMaxX(), r.getMinY()),
                    new Point2D.Double(r.getMaxX(), r.getMaxY()),
                    new Point2D.Double(r.getMinX(), r.getMaxY())};
    }

    //////////////////////////////////////////////////////////////
    // MOUSE - screen coordinates
    //////////////////////////////////////////////////////////////
    private int hit(Point p) {
        Rectangle2D r = screenArea();
        Point2D[] c = corners(r);
        for (int i = 0; i < c.length; i++) {
            if (c[i].distance(p) <= HANDLE) {
                return i;
            }
        }
        return r.contains(p) ? MOVE : CREATE;
    }

    Cursor getCursor(Point p) {
        switch (hit(p)) {
            case 0:
                return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case 1:
                return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            case 2:
                return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
            case 3:
                return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
            case MOVE:
                return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
            default:
                return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
    }

    void mousePressed(MouseEvent e) {
        dragMode = hit(e.getPoint());
        dragStart = canvas.toWorld(e.getX(), e.getY());
        areaAtDragStart = (Rectangle2D) area.clone();
    }

    void mouseDragged(MouseEvent e) {
        if (dragMode == NONE) {
            return;
        }
        Point2D.Float p = canvas.toWorld(e.getX(), e.getY());
        Rectangle2D a = areaAtDragStart;
        double x1 = a.getMinX(), y1 = a.getMinY(), x2 = a.getMaxX(), y2 = a.getMaxY();
        switch (dragMode) {
            case MOVE:
                double dx = p.x - dragStart.x, dy = p.y - dragStart.y;
                x1 += dx;
                x2 += dx;
                y1 += dy;
                y2 += dy;
                break;
            case CREATE:
                x1 = dragStart.x;
                y1 = dragStart.y;
                x2 = p.x;
                y2 = p.y;
                break;
            case 0:
                x1 = p.x;
                y1 = p.y;
                break;
            case 1:
                x2 = p.x;
                y1 = p.y;
                break;
            case 2:
                x2 = p.x;
                y2 = p.y;
                break;
            case 3:
                x1 = p.x;
                y2 = p.y;
                break;
        }
        setArea(new Rectangle2D.Double(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1)));
    }

    void mouseReleased(MouseEvent e) {
        // A click without dragging does not leave an empty frame
        if (dragMode == CREATE && (area.getWidth() < 2 || area.getHeight() < 2)) {
            setArea(areaAtDragStart);
        }
        dragMode = NONE;
    }
}
