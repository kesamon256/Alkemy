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

import java.awt.Point;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import org.alchemy.core.*;

/**
 * Smooth Module
 * @author Karl D.D. Willis 
 */
public class Smooth extends AlcModule {

    private int spacing = 75;
    private long time;
    private boolean repeat = false;
    private AlcToolBarSubSection subToolBarSection;
    private AlcSubToggleButton repeatButton;

    @Override
    protected void setup() {
        createSubToolBarSection();
        toolBar.addSubToolBarSection(subToolBarSection);
    }


    @Override
    public void reselect() {
        toolBar.addSubToolBarSection(subToolBarSection);
    }

    public void createSubToolBarSection() {
        subToolBarSection = new AlcToolBarSubSection(this);

        // Repeat button
        repeatButton = new AlcSubToggleButton("Repeat", AlcUtil.getUrlPath("repeat.png", getClassLoader()));
        repeatButton.setSelected(repeat);
        repeatButton.setToolTipText("Toggle repeat on/off");

        repeatButton.addActionListener(
                new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        repeat = !repeat;
                    }
                });
        subToolBarSection.add(repeatButton);

    }

    private void smoothShape(Point currentLoc, int index) {
        AlcShape shape = canvas.shapes.get(index);
        AlcShape smoothedShape = smoothShape(shape.getPoints(), currentLoc);
        if (repeat) {
            canvas.shapes.add(smoothedShape);
        } else {
            shape.setPath(smoothedShape.getPath());
        }
    }

    private AlcShape smoothShape(ArrayList<Point2D.Float> points, Point currentLoc) {
        //shape.setPath(null);

        GeneralPath path = new GeneralPath(GeneralPath.WIND_NON_ZERO, points.size());
        for (int i = 0; i < points.size(); i++) {

            // FIRST
            if (i == 0) {
//                shape.addLinePoint(points.get(i));
                Point2D.Float pt = points.get(i);
                path.moveTo(pt.x, pt.y);

            // LAST
            } else if (i == points.size() - 1) {
//                shape.addLinePoint(points.get(i));
                Point2D.Float pt = points.get(i);
                path.lineTo(pt.x, pt.y);

            } else {
                Point2D.Float p0 = points.get(i - 1);
                Point2D.Float p1 = points.get(i);
                Point2D.Float p2 = points.get(i + 1);

//                if (p0.distance(currentLoc) < 25) {

                // Average the 3 points
                float x = p0.x * 0.25F + p1.x * 0.5F + p2.x * 0.25F;
                float y = p0.y * 0.25F + p1.y * 0.5F + p2.y * 0.25F;

                // Setup for curves
                Point2D.Float pt = new Point2D.Float();
                pt.x = (p0.x + x) / 2F;
                pt.y = (p0.y + y) / 2F;

                // Add the Quadratic curve - control point x1, y1 and actual point x2, y2
                path.quadTo(p0.x, p0.y, pt.x, pt.y);
            }
        }
        AlcShape shape = new AlcShape(path);
        return shape;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        // Only while Q is held - moving the pen over the drawing otherwise leaves it alone
        if (!canvas.isPenDown() && canvas.isMotionActive()) {
            if (System.currentTimeMillis() - time >= spacing) {
                Point pt = e.getPoint();
                // The newest shape the pen is over
                int target = -1;
                for (int i = canvas.shapes.size() - 1; i >= 0; i--) {
                    if (canvas.shapes.get(i).getPath().contains(pt)) {
                        target = i;
                        break;
                    }
                }
                if (target >= 0) {
                    // With Mirror on, its mirror images are smoothed too - found by their outline,
                    // as the shapes may overlap where a stroke crosses the axis
                    java.util.ArrayList<Integer> todo = new java.util.ArrayList<Integer>();
                    todo.add(target);
                    AlcShape shape = canvas.shapes.get(target);
                    int points = shape.getPoints().size();
                    java.util.List<java.awt.geom.AffineTransform> mirrors = canvas.symmetryTransforms();
                    for (int m = 1; m < mirrors.size(); m++) {
                        java.awt.geom.Rectangle2D expected = mirrors.get(m).createTransformedShape(shape.getPath()).getBounds2D();
                        int best = -1;
                        double bestError = 2.0;
                        for (int i = 0; i < canvas.shapes.size(); i++) {
                            if (todo.contains(i)) {
                                continue;
                            }
                            AlcShape other = canvas.shapes.get(i);
                            java.awt.geom.Rectangle2D b = other.getPath().getBounds2D();
                            double error = Math.abs(b.getMinX() - expected.getMinX()) + Math.abs(b.getMinY() - expected.getMinY())
                                    + Math.abs(b.getMaxX() - expected.getMaxX()) + Math.abs(b.getMaxY() - expected.getMaxY());
                            if (error < bestError && other.getPoints().size() == points) {
                                best = i;
                                bestError = error;
                            }
                        }
                        if (best >= 0) {
                            todo.add(best);
                        }
                    }
                    // All the smoothing while Q is held is one undo step
                    canvas.beginMotionEdit();
                    for (int index : todo) {
                        smoothShape(pt, index);
                    }
                    canvas.redraw(true);
                }
                time = System.currentTimeMillis();
            }
        }
    }
}