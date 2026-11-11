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

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.event.ActionEvent;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.LinkedList;
import javax.swing.AbstractAction;

/**
 * Undo / redo history for the canvas.
 * <p>
 * Before the canvas changes, a snapshot of the committed shapes is taken.
 * A snapshot is cheap: it stores references to the shapes and to their
 * paths and styles. Modules never edit a path in place - transforms
 * always replace the path - so restoring those references restores the
 * shapes exactly.
 * <p>
 * Changes are grouped into edits: {@link #beginEdit()} remembers the state
 * at the start (a pen press, a clear...) and {@link #endEdit()} records it
 * only if something actually changed.
 */
class AlcHistory {

    /** Maximum number of undo steps kept in memory */
    static final int MAX_STEPS = 200;
    private final LinkedList<Snapshot> undoStack = new LinkedList<Snapshot>();
    private final LinkedList<Snapshot> redoStack = new LinkedList<Snapshot>();
    /** State captured at the start of the current edit, or null */
    private Snapshot pending;
    /** Ignore edits while restoring a snapshot */
    private boolean restoring = false;
    /** Shared actions for menu items, toolbar buttons and shortcuts */
    final AbstractAction undoAction = new AbstractAction() {

        public void actionPerformed(ActionEvent e) {
            undo();
        }
    };
    final AbstractAction redoAction = new AbstractAction() {

        public void actionPerformed(ActionEvent e) {
            redo();
        }
    };

    AlcHistory() {
        undoAction.setEnabled(false);
        redoAction.setEnabled(false);
    }

    /** Remember the current canvas state as the start of an edit.
     *  Does nothing if an edit is already in progress. */
    void beginEdit() {
        if (pending == null && !restoring) {
            pending = new Snapshot(Alchemy.canvas);
        }
    }

    /** Finish the current edit - recorded only if the canvas changed */
    void endEdit() {
        if (pending == null) {
            return;
        }
        Snapshot before = pending;
        pending = null;
        if (!before.matches(Alchemy.canvas)) {
            undoStack.addLast(before);
            if (undoStack.size() > MAX_STEPS) {
                undoStack.removeFirst();
            }
            redoStack.clear();
            updateButtons();
        }
    }

    /** Is an edit in progress */
    boolean isEditing() {
        return pending != null;
    }

    boolean canUndo() {
        return !undoStack.isEmpty();
    }

    boolean canRedo() {
        return !redoStack.isEmpty();
    }

    void undo() {
        // Commit anything still being drawn so it can be redone
        endEdit();
        if (canUndo()) {
            redoStack.addLast(new Snapshot(Alchemy.canvas));
            restore(undoStack.removeLast());
        }
    }

    void redo() {
        endEdit();
        if (canRedo()) {
            undoStack.addLast(new Snapshot(Alchemy.canvas));
            restore(redoStack.removeLast());
        }
    }

    /** Forget the whole history */
    void reset() {
        undoStack.clear();
        redoStack.clear();
        pending = null;
        updateButtons();
    }

    private void restore(Snapshot snapshot) {
        restoring = true;
        try {
            snapshot.apply(Alchemy.canvas);
        } finally {
            restoring = false;
        }
        updateButtons();
    }

    private void updateButtons() {
        // Buttons and menu items follow the enabled state of their action
        undoAction.setEnabled(canUndo());
        redoAction.setEnabled(canRedo());
    }

    /** The state of the canvas at a point in time - every layer and its shapes */
    private static class Snapshot {

        private final ArrayList<LayerState> layers;
        private final int activeLayer;
        private final Color background;

        Snapshot(AlcCanvas canvas) {
            layers = new ArrayList<LayerState>(canvas.getLayers().size());
            for (AlcLayer layer : canvas.getLayers()) {
                layers.add(new LayerState(layer));
            }
            activeLayer = canvas.getActiveLayerIndex();
            background = canvas.getBackgroundColor();
        }

        boolean matches(AlcCanvas canvas) {
            if (layers.size() != canvas.getLayers().size() || activeLayer != canvas.getActiveLayerIndex()
                    || !background.equals(canvas.getBackgroundColor())) {
                return false;
            }
            for (int i = 0; i < layers.size(); i++) {
                if (!layers.get(i).matches(canvas.getLayers().get(i))) {
                    return false;
                }
            }
            return true;
        }

        void apply(AlcCanvas canvas) {
            ArrayList<AlcLayer> restored = new ArrayList<AlcLayer>(layers.size());
            for (LayerState state : layers) {
                restored.add(state.restore());
            }
            canvas.replaceLayers(restored, activeLayer);
            canvas.restoreBackgroundColor(background);
            canvas.forceFullRedraw();
        }
    }

    /** A layer, its settings and its shapes */
    private static class LayerState {

        private final AlcLayer layer;
        private final String name;
        private final boolean visible;
        private final boolean locked;
        private final float opacity;
        private final ArrayList<Entry> entries;

        LayerState(AlcLayer layer) {
            this.layer = layer;
            this.name = layer.name;
            this.visible = layer.visible;
            this.locked = layer.locked;
            this.opacity = layer.opacity;
            entries = new ArrayList<Entry>(layer.shapes.size());
            for (AlcShape shape : layer.shapes) {
                entries.add(new Entry(shape));
            }
        }

        boolean matches(AlcLayer l) {
            if (l != layer || !name.equals(l.name) || visible != l.visible || locked != l.locked
                    || opacity != l.opacity || entries.size() != l.shapes.size()) {
                return false;
            }
            for (int i = 0; i < entries.size(); i++) {
                if (!entries.get(i).matches(l.shapes.get(i))) {
                    return false;
                }
            }
            return true;
        }

        AlcLayer restore() {
            layer.name = name;
            layer.visible = visible;
            layer.locked = locked;
            layer.opacity = opacity;
            layer.shapes.clear();
            for (Entry entry : entries) {
                layer.shapes.add(entry.restore());
            }
            return layer;
        }
    }

    /** A shape and the parts of it that modules may replace */
    private static class Entry {

        private final AlcShape shape;
        private final GeneralPath path;
        private final Color color;
        private final int alpha;
        private final int style;
        private final int dash;
        private final float lineWidth;
        private final GradientPaint gradient;
        private final java.awt.Color along;
        private final float alongFrom, alongTo;

        Entry(AlcShape shape) {
            this.shape = shape;
            this.path = shape.path;
            this.color = shape.color;
            this.alpha = shape.alpha;
            this.style = shape.style;
            this.dash = shape.dash;
            this.lineWidth = shape.lineWidth;
            this.gradient = shape.getGradientPaint();
            this.along = shape.alongColor;
            this.alongFrom = shape.alongFrom;
            this.alongTo = shape.alongTo;
        }

        boolean matches(AlcShape s) {
            return s == shape && s.path == path && s.color == color && s.alpha == alpha &&
                    s.style == style && s.dash == dash && s.lineWidth == lineWidth && s.getGradientPaint() == gradient &&
                    s.alongColor == along && s.alongFrom == alongFrom && s.alongTo == alongTo;
        }

        AlcShape restore() {
            shape.path = path;
            shape.color = color;
            shape.alpha = alpha;
            shape.style = style;
            shape.dash = dash;
            shape.lineWidth = lineWidth;
            shape.setGradientPaint(gradient);
            shape.setAlongGradient(along, alongFrom, alongTo);
            return shape;
        }
    }
}
