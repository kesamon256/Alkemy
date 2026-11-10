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
import java.util.ArrayList;
import java.util.IdentityHashMap;

/**
 * Lasso selection on the active layer - it selects an area, like a bitmap selection.
 * <p>
 * Drag a loop to select what is inside it. Shapes wholly inside are selected
 * as they are; shapes crossed by the loop are cut along it the first time the
 * selection is moved, scaled, rotated or deleted, so only the part inside
 * changes (it becomes a filled shape that looks the same). Shift adds to the selection.
 * The selection gets a frame: drag inside it to move, drag a corner to
 * scale (Shift for free scaling) and drag the handle above it to rotate
 * (Shift snaps to 15 degrees). Delete removes the selected shapes and
 * Escape clears the selection. Every change is one undo step.
 * All positions are kept in drawing coordinates, so zoom and pan work.
 */
class AlcSelectionTool implements AlcConstants {

    private static final int HANDLE = 8;
    private static final int ROTATE_DISTANCE = 26;
    private static final int NONE = -1, LASSO = 10, MOVE = 11, ROTATE = 12;
    // 0 to 3 are the corners: top left, top right, bottom right, bottom left
    private final AlcCanvas canvas;
    private final ArrayList<AlcShape> selected = new ArrayList<AlcShape>();
    /** The loop being drawn, in drawing coordinates */
    private Path2D.Float lasso;
    private int drag = NONE;
    private Point2D.Float dragStart;
    private Rectangle2D boundsAtStart;
    private boolean addToSelection;
    /** The shapes as they were when the gesture started */
    private final IdentityHashMap<AlcShape, Original> originals = new IdentityHashMap<AlcShape, Original>();
    /** The selected area of the drawing, in drawing coordinates - like a bitmap selection */
    private Area region;
    /** Shapes only partly inside the region - cut along its edge the first time the selection is changed */
    private final ArrayList<Piece> pieces = new ArrayList<Piece>();
    /** The transform of the current move / scale / rotate gesture */
    private AffineTransform gestureTransform;

    AlcSelectionTool(AlcCanvas canvas) {
        this.canvas = canvas;
    }

    //////////////////////////////////////////////////////////////
    // SELECTION
    //////////////////////////////////////////////////////////////
    /** Drop shapes that are no longer on the active layer - after undo, clear or a layer change */
    private void validate() {
        for (int i = selected.size() - 1; i >= 0; i--) {
            if (!canvas.shapes.contains(selected.get(i))) {
                selected.remove(i);
            }
        }
        for (int i = pieces.size() - 1; i >= 0; i--) {
            if (!canvas.shapes.contains(pieces.get(i).shape)) {
                pieces.remove(i);
            }
        }
        if (selected.isEmpty() && pieces.isEmpty()) {
            region = null;
        }
    }

    boolean hasSelection() {
        validate();
        return !selected.isEmpty() || !pieces.isEmpty();
    }

    /** The shapes that are wholly selected - parts that still have to be cut are not listed */
    java.util.List<AlcShape> getSelected() {
        validate();
        return java.util.Collections.unmodifiableList(selected);
    }

    /** Shapes cut by the edge of the selection, not yet split */
    int getPartlySelectedCount() {
        validate();
        return pieces.size();
    }

    void clearSelection() {
        selected.clear();
        pieces.clear();
        region = null;
        canvas.repaint();
    }

    void selectAll() {
        clearSelection();
        Rectangle2D all = canvas.getDrawingBounds();
        if (all != null) {
            region = new Area(new Rectangle2D.Double(all.getX() - 10, all.getY() - 10, all.getWidth() + 20, all.getHeight() + 20));
            rebuildFromRegion();
        }
        canvas.repaint();
    }

    /** Remove what is selected - whole shapes, and the selected parts of cut shapes. One undo step. */
    void deleteSelected() {
        if (!hasSelection() || !canEdit()) {
            return;
        }
        Alchemy.history.beginEdit();
        cutPieces();
        canvas.shapes.removeAll(selected);
        clearSelection();
        canvas.forceFullRedraw();
        Alchemy.history.endEdit();
    }

    //////////////////////////////////////////////////////////////
    // REGION - selecting parts of shapes
    //////////////////////////////////////////////////////////////
    /** The area a shape covers on the canvas - strokes as their outline, drawn as the canvas draws them */
    static Area shapeArea(AlcShape shape) {
        if (!shape.isFilled()) {
            return new Area(shape.getStroke().createStrokedShape(shape.getPath()));
        }
        Area area = new Area(shape.getPath());
        if (shape.isStroked()) {
            area.add(new Area(shape.getStroke().createStrokedShape(shape.getPath())));
        }
        return area;
    }

    /** Find what the region covers: whole shapes are selected as they are, cut shapes wait as pieces */
    private void rebuildFromRegion() {
        selected.clear();
        pieces.clear();
        if (region == null || region.isEmpty()) {
            return;
        }
        Rectangle2D regionBounds = region.getBounds2D();
        for (AlcShape shape : canvas.shapes) {
            Rectangle2D b = shape.getPath().getBounds2D();
            double grow = shape.getLineWidth();
            if (!regionBounds.intersects(b.getX() - grow, b.getY() - grow, b.getWidth() + grow * 2, b.getHeight() + grow * 2)) {
                continue;
            }
            Area area = shapeArea(shape);
            Area inside = new Area(area);
            inside.intersect(region);
            if (inside.isEmpty()) {
                continue;
            }
            Area outside = new Area(area);
            outside.subtract(region);
            if (outside.isEmpty()) {
                selected.add(shape);
            } else {
                pieces.add(new Piece(shape, inside, outside));
            }
        }
    }

    /** Cut every partly selected shape along the edge of the region:
     *  the part outside stays, the part inside becomes a selected shape of its own.
     *  Called inside an undo step, so undo puts the whole shapes back. */
    private void cutPieces() {
        for (Piece piece : pieces) {
            int index = canvas.shapes.indexOf(piece.shape);
            if (index < 0) {
                continue;
            }
            canvas.shapes.remove(index);
            canvas.shapes.add(index, piece.copy(piece.inside));
            canvas.shapes.add(index, piece.copy(piece.outside));
            selected.add(canvas.shapes.get(index + 1));
        }
        pieces.clear();
    }

    /** The selection can only change shapes on a visible, unlocked layer */
    private boolean canEdit() {
        if (canvas.canDrawOnActiveLayer()) {
            return true;
        }
        canvas.showMessage(Alchemy.bundle.getString(canvas.getActiveLayer().locked ? "layerLockedMessage" : "layerHiddenMessage"));
        return false;
    }

    /** Bounds of the selected shapes including their line widths */
    private Rectangle2D selectionBounds() {
        Rectangle2D bounds = null;
        for (AlcShape shape : selected) {
            Rectangle2D b = shape.getPath().getBounds2D();
            double grow = shape.isStroked() ? shape.getLineWidth() / 2 : 0;
            b = new Rectangle2D.Double(b.getX() - grow, b.getY() - grow, b.getWidth() + grow * 2, b.getHeight() + grow * 2);
            bounds = (bounds == null) ? b : bounds.createUnion(b);
        }
        for (Piece piece : pieces) {
            Rectangle2D b = piece.inside.getBounds2D();
            bounds = (bounds == null) ? b : bounds.createUnion(b);
        }
        return bounds;
    }

    //////////////////////////////////////////////////////////////
    // HANDLES - screen coordinates
    //////////////////////////////////////////////////////////////
    private Rectangle2D screenBounds() {
        Rectangle2D b = selectionBounds();
        return b == null ? null : canvas.getViewTransform().createTransformedShape(b).getBounds2D();
    }

    private Point2D[] corners(Rectangle2D r) {
        return new Point2D[]{
                    new Point2D.Double(r.getMinX(), r.getMinY()),
                    new Point2D.Double(r.getMaxX(), r.getMinY()),
                    new Point2D.Double(r.getMaxX(), r.getMaxY()),
                    new Point2D.Double(r.getMinX(), r.getMaxY())};
    }

    private Point2D rotateHandle(Rectangle2D r) {
        return new Point2D.Double(r.getCenterX(), r.getMinY() - ROTATE_DISTANCE);
    }

    /** What is under a screen point */
    private int hit(Point p) {
        if (!hasSelection()) {
            return NONE;
        }
        Rectangle2D r = screenBounds();
        if (rotateHandle(r).distance(p) <= HANDLE) {
            return ROTATE;
        }
        Point2D[] c = corners(r);
        for (int i = 0; i < c.length; i++) {
            if (c[i].distance(p) <= HANDLE) {
                return i;
            }
        }
        Rectangle2D grown = new Rectangle2D.Double(r.getX() - 3, r.getY() - 3, r.getWidth() + 6, r.getHeight() + 6);
        return grown.contains(p) ? MOVE : NONE;
    }

    Cursor getCursor(Point p) {
        switch (hit(p)) {
            case 0:
            case 2:
                return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case 1:
            case 3:
                return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            case MOVE:
                return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
            case ROTATE:
                return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
            default:
                return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
    }

    //////////////////////////////////////////////////////////////
    // MOUSE - screen coordinates in, drawing coordinates kept
    //////////////////////////////////////////////////////////////
    void mousePressed(MouseEvent e) {
        dragStart = canvas.toWorld(e.getX(), e.getY());
        int part = hit(e.getPoint());
        if (part == NONE) {
            // A new loop
            drag = LASSO;
            addToSelection = e.isShiftDown();
            lasso = new Path2D.Float();
            lasso.moveTo(dragStart.x, dragStart.y);
            return;
        }
        if (!canEdit()) {
            drag = NONE;
            return;
        }
        drag = part;
        Alchemy.history.beginEdit();
        // Cut the shapes along the selection edge now - in the same undo step as the change
        cutPieces();
        boundsAtStart = selectionBounds();
        gestureTransform = null;
        originals.clear();
        for (AlcShape shape : selected) {
            originals.put(shape, new Original(shape));
        }
    }

    void mouseDragged(MouseEvent e) {
        Point2D.Float p = canvas.toWorld(e.getX(), e.getY());
        if (drag == LASSO) {
            lasso.lineTo(p.x, p.y);
            canvas.repaint();
            return;
        }
        if (drag == NONE) {
            return;
        }
        AffineTransform t = new AffineTransform();
        double widthScale = 1;
        Rectangle2D b = boundsAtStart;
        if (drag == MOVE) {
            t.translate(p.x - dragStart.x, p.y - dragStart.y);
        } else if (drag == ROTATE) {
            double cx = b.getCenterX(), cy = b.getCenterY();
            double angle = Math.atan2(p.y - cy, p.x - cx) - Math.atan2(dragStart.y - cy, dragStart.x - cx);
            if (e.isShiftDown()) {
                double step = Math.toRadians(15);
                angle = Math.round(angle / step) * step;
            }
            t.rotate(angle, cx, cy);
        } else {
            // Scale from the opposite corner
            Point2D[] c = corners(b);
            Point2D anchor = c[(drag + 2) % 4];
            Point2D corner = c[drag];
            double sx = (p.x - anchor.getX()) / nonZero(corner.getX() - anchor.getX());
            double sy = (p.y - anchor.getY()) / nonZero(corner.getY() - anchor.getY());
            if (!e.isShiftDown()) {
                // Keep the proportions
                double s = Math.max(Math.abs(sx), Math.abs(sy));
                sx = s * Math.signum(sx == 0 ? 1 : sx);
                sy = s * Math.signum(sy == 0 ? 1 : sy);
            }
            sx = limit(sx);
            sy = limit(sy);
            t.translate(anchor.getX(), anchor.getY());
            t.scale(sx, sy);
            t.translate(-anchor.getX(), -anchor.getY());
            widthScale = Math.sqrt(Math.abs(sx * sy));
        }
        for (AlcShape shape : selected) {
            Original o = originals.get(shape);
            if (o != null) {
                o.applyTo(shape, t, widthScale);
            }
        }
        gestureTransform = t;
        canvas.forceFullRedraw();
    }

    void mouseReleased(MouseEvent e) {
        if (drag == LASSO) {
            lasso.closePath();
            Area loop = new Area(lasso);
            if (addToSelection && region != null) {
                region.add(loop);
            } else {
                region = loop;
            }
            rebuildFromRegion();
            lasso = null;
        } else if (drag != NONE) {
            // The selected area moves with what was moved
            if (gestureTransform != null && region != null) {
                region.transform(gestureTransform);
            }
            originals.clear();
            Alchemy.history.endEdit();
        }
        drag = NONE;
        canvas.repaint();
    }

    private static double nonZero(double d) {
        return Math.abs(d) < 0.001 ? 0.001 : d;
    }

    /** Never scale down to nothing */
    private static double limit(double s) {
        return Math.abs(s) < 0.02 ? 0.02 * Math.signum(s == 0 ? 1 : s) : s;
    }

    //////////////////////////////////////////////////////////////
    // PAINTING - on top of the canvas, in screen coordinates
    //////////////////////////////////////////////////////////////
    /** Dark ink of the overlays - neutral, readable on light and dark paper together with white */
    static final Color OVERLAY_DARK = new Color(24, 25, 28);

    /** Marching ants - a white line with black dashes running along it, readable on any paper.
     *  The dashes move with the time so the line seems alive, like a selection in Photoshop. */
    static void drawAnts(Graphics2D g2, Shape shape, boolean moving) {
        float phase = moving ? (System.currentTimeMillis() / 60L % 8L) : 0F;
        g2.setStroke(new BasicStroke(1F));
        g2.setColor(new Color(255, 255, 255, 230));
        g2.draw(shape);
        g2.setStroke(new BasicStroke(1F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10F, new float[]{4F, 4F}, phase));
        g2.setColor(OVERLAY_DARK);
        g2.draw(shape);
    }

    /** A small handle - white with a dark rim */
    static void drawHandle(Graphics2D g2, Shape handle, boolean active) {
        g2.setStroke(new BasicStroke(1F));
        g2.setColor(active ? OVERLAY_DARK : Color.WHITE);
        g2.fill(handle);
        g2.setColor(active ? Color.WHITE : OVERLAY_DARK);
        g2.draw(handle);
    }

    void paint(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        AffineTransform view = canvas.getViewTransform();
        if (lasso != null) {
            drawAnts(g2, view.createTransformedShape(lasso), true);
        }
        if (!hasSelection()) {
            return;
        }
        // Outline of every selected shape - faint, so the drawing still reads
        g2.setStroke(new BasicStroke(1F));
        g2.setColor(new Color(128, 130, 136, 150));
        for (AlcShape shape : selected) {
            g2.draw(view.createTransformedShape(shape.getPath()));
        }
        // Edge of the selected area - where the shapes are cut
        if (region != null) {
            drawAnts(g2, view.createTransformedShape(region), true);
        }
        // Frame with handles
        Rectangle2D r = screenBounds();
        g2.setStroke(new BasicStroke(1F));
        g2.setColor(new Color(255, 255, 255, 120));
        g2.draw(r);
        g2.setStroke(new BasicStroke(1F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10F, new float[]{2F, 3F}, 0F));
        g2.setColor(new Color(24, 25, 28, 200));
        g2.draw(r);
        Point2D rot = rotateHandle(r);
        g2.setStroke(new BasicStroke(1F));
        g2.setColor(new Color(24, 25, 28, 200));
        g2.draw(new Line2D.Double(r.getCenterX(), r.getMinY(), rot.getX(), rot.getY() + HANDLE / 2.0));
        for (Point2D c : corners(r)) {
            drawHandle(g2, new Rectangle2D.Double(c.getX() - HANDLE / 2.0, c.getY() - HANDLE / 2.0, HANDLE, HANDLE), false);
        }
        drawHandle(g2, new Ellipse2D.Double(rot.getX() - HANDLE / 2.0, rot.getY() - HANDLE / 2.0, HANDLE, HANDLE), false);
    }

    /** The ants move only while there is something to show */
    boolean needsAnimation() {
        return lasso != null || (hasSelection() && region != null);
    }

    /** A shape cut by the edge of the selection: the area inside and the area outside it */
    private static class Piece {

        final AlcShape shape;
        final Area inside;
        final Area outside;

        Piece(AlcShape shape, Area inside, Area outside) {
            this.shape = shape;
            this.inside = inside;
            this.outside = outside;
        }

        /** A filled shape of one part, with the colour, transparency and gradient of the original */
        AlcShape copy(Area part) {
            AlcShape s = new AlcShape(new GeneralPath(part), shape.getColor(), shape.getAlpha(), STYLE_FILL, shape.getLineWidth());
            s.setAlphaColor(shape.getColor());
            s.setGradientPaint(shape.getGradientPaint());
            return s;
        }
    }

    /** What a shape looked like when a gesture started */
    private static class Original {

        private final GeneralPath path;
        private final float lineWidth;
        private final GradientPaint gradient;

        Original(AlcShape shape) {
            path = shape.getPath();
            lineWidth = shape.getLineWidth();
            gradient = shape.getGradientPaint();
        }

        void applyTo(AlcShape shape, AffineTransform t, double widthScale) {
            shape.setPath(new GeneralPath(t.createTransformedShape(path)));
            shape.setLineWidth((float) (lineWidth * widthScale));
            if (gradient != null) {
                Point2D p1 = t.transform(gradient.getPoint1(), null);
                Point2D p2 = t.transform(gradient.getPoint2(), null);
                shape.setGradientPaint(new GradientPaint(p1, gradient.getColor1(), p2, gradient.getColor2(), gradient.isCyclic()));
            }
        }
    }
}
