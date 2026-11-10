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

import java.util.ArrayList;

/**
 * A layer of the drawing - its own list of committed shapes.
 * <p>
 * The canvas keeps {@link AlcCanvas#shapes} pointing at the shape list of
 * the active layer, so every module draws into the active layer without
 * knowing about layers.
 */
public class AlcLayer {

    /** The committed shapes of this layer, bottom first */
    final ArrayList<AlcShape> shapes;
    String name;
    boolean visible = true;
    boolean locked = false;
    /** Opacity of the whole layer, 0 to 1 */
    float opacity = 1F;

    AlcLayer(String name) {
        this(name, new ArrayList<AlcShape>(100));
    }

    AlcLayer(String name, ArrayList<AlcShape> shapes) {
        this.name = name;
        this.shapes = shapes;
    }

    public String getName() {
        return name;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isLocked() {
        return locked;
    }

    public float getOpacity() {
        return opacity;
    }

    /** A copy with its own shape list - the shapes themselves are cloned */
    AlcLayer duplicate(String newName) {
        AlcLayer copy = new AlcLayer(newName);
        for (AlcShape shape : shapes) {
            AlcShape clone = (AlcShape) shape.clone();
            // clone() keeps the path - give the copy its own
            clone.setPath(new java.awt.geom.GeneralPath(shape.getPath()));
            copy.shapes.add(clone);
        }
        copy.visible = visible;
        copy.opacity = opacity;
        return copy;
    }
}
