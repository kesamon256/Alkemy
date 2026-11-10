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
package org.alchemy.create;

import org.alchemy.core.*;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;

/**
 * Shape.java
 * @author  Karl D.D. Willis
 */
public class Shapes extends AlcModule implements AlcConstants {

    private boolean straightShapes = false;
    private boolean firstClick = true;
    private boolean outlineMode = false;
    private AlcToolBarSubSection subToolBarSection;
    private boolean secondClick;
    private Point lastPt;
    private int guideSize = -1;
    private GeneralPath secondPath = null;
    private Color guideColor = new Color(0, 255, 255);
    /** The current stroke is drawn with pen pressure */
    private boolean pressureStroke = false;


    public Shapes() {
    }

    @Override
    protected void setup() {
        createSubToolBarSection();
        toolBar.addSubToolBarSection(subToolBarSection);
    }

    @Override
    protected void cleared() {
        reset();
    }

    @Override
    protected void reselect() {
        AlcPressureSliders.refresh(subToolBarSection, canvas);
        toolBar.addSubToolBarSection(subToolBarSection);
        reset();
    }

    private void reset() {
        lastPt = null;
        firstClick = true;
        if (guideSize > 0) {
//            if (canvas.guideShapes.get(guideSize - 1) != null) {
//                canvas.guideShapes.remove(guideSize - 1);
//            }
            canvas.guideShapes.clear();
        }
        guideSize = -1;
    }

    public void createSubToolBarSection() {
        subToolBarSection = new AlcToolBarSubSection(this);

        // Line Type Button
        AlcSubToggleButton lineTypeButton = new AlcSubToggleButton("Line Type", AlcUtil.getUrlPath("linetype.png", getClassLoader()));
        lineTypeButton.setToolTipText("Change the line type to freeform or straight lines");
        lineTypeButton.addActionListener(
                new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        straightShapes = !straightShapes;
                        reset();
                        canvas.redraw();
                    }
                });
        subToolBarSection.add(lineTypeButton);


        // Outline Mode
        AlcSubToggleButton outlineButton = new AlcSubToggleButton("Outline", AlcUtil.getUrlPath("outline.png", getClassLoader()));
        outlineButton.setToolTipText("Draw in outline mode until the shape is finished");
        outlineButton.addActionListener(
                new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        outlineMode = !outlineMode;
                    }
                });
        subToolBarSection.add(outlineButton);
        AlcPressureSliders.add(subToolBarSection, canvas);

    }

    /** Draw a variable width line following the pen pressure */
    private boolean usePressure() {
        return canvas.isPressureEnabled() && canvas.getPenType() != PEN_CURSOR
                && canvas.getStyle() == STYLE_STROKE && !straightShapes
                && (canvas.isPressureSize() || canvas.isPressureOpacity());
    }

    /** The pen pressure from 0 to 1 - the canvas squares it, this undoes that */
    private float rawPressure() {
        return (float) Math.sqrt(Math.max(0, Math.min(1, canvas.getPenPressure())));
    }

    /** The pressure sets how opaque the line is instead of how wide */
    private boolean opacityMode() {
        return canvas.isPressureOpacity();
    }

    /** Half the line width at the current pen pressure - from a hair with a light touch
     *  to about one and a half times the line weight pressed hard */
    private float pressureWidth() {
        if (!canvas.isPressureSize()) {
            return Math.max(0.5F, canvas.getLineWidth() / 2F);
        }
        float p = canvas.sizePressure(rawPressure());
        return Math.max(0.3F, canvas.getLineWidth() * (0.1F + 1.3F * (float) Math.pow(p, 0.9)));
    }

    /** The opacity for the pen pressure in tenths, so a stroke is made of a few pieces */
    private float opacityLevel() {
        return Math.max(0.03F, Math.round(canvas.opacityPressure(rawPressure()) * 10) / 10F);
    }

    /** Opacity of the piece being drawn, and how far along the stroke it started */
    private float pieceOpacity = 1F;
    private float travelled, pieceStart;
    private java.awt.geom.Point2D.Float lastPen;

    private AlcBandShape newBand(boolean continued) {
        int alpha = Math.max(1, Math.round(canvas.getAlpha() * pieceOpacity));
        return new AlcBandShape(canvas.getColor(), alpha, canvas.getLineWidth(), continued);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        pressureStroke = usePressure();
        if (pressureStroke) {
            lastPen = canvas.getPenLocation();
            travelled = 0;
            pieceStart = 0;
            pieceOpacity = opacityMode() ? opacityLevel() : 1F;
            AlcBandShape shape = newBand(false);
            shape.bandTo(lastPen, pressureWidth());
            canvas.createShapes.add(shape);
            canvas.redraw();
        } else if (!straightShapes) {
            Point p = e.getPoint();
            AlcShape shape = new AlcShape(p);
            canvas.createShapes.add(shape);
            if(outlineMode && canvas.getStyle() == STYLE_FILL){
                shape.setStyle(STYLE_STROKE);
            }
            canvas.redraw();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // Detect a doubleclick
        if (!e.isConsumed() && e.getButton() == 1 && e.getClickCount() > 1) {
            reset();
            canvas.redraw();
            canvas.commitShapes();
            e.consume();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        // If in freeform mode, not on the first click and if there is a valid lastPoint
        // then draw a guide line
        if (straightShapes && !firstClick && lastPt != null) {

            GeneralPath line = new GeneralPath(new Line2D.Float(lastPt.x, lastPt.y, e.getX(), e.getY()));
            AlcShape guide = new AlcShape(line, guideColor, 100, STYLE_STROKE, 1);

            if (guideSize == canvas.guideShapes.size()) {
                if (secondPath != null) {
                    // If there is a secondPath defined then append it to the new shape
                    guide.getPath().append(secondPath, false);
                }
                canvas.setCurrentGuideShape(guide);
            } else if (guideSize == -1) {
                canvas.guideShapes.add(guide);
                guideSize = canvas.guideShapes.size();
            } else {
                // Clause here to determine if the guideshape array has increased
                // i.e. it is being used by another module
                reset();
            }
            canvas.redraw();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (pressureStroke) {
            if (canvas.hasCreateShapes() && canvas.getCurrentCreateShape() instanceof AlcBandShape) {
                java.awt.geom.Point2D.Float p = canvas.getPenLocation();
                travelled += (float) p.distance(lastPen);
                lastPen = p;
                AlcBandShape band = (AlcBandShape) canvas.getCurrentCreateShape();
                if (opacityMode()) {
                    // A new piece each time the pressure moves to another tenth of opacity
                    float level = opacityLevel();
                    if (level != pieceOpacity && travelled - pieceStart >= 3) {
                        pieceOpacity = level;
                        pieceStart = travelled;
                        band = band.carryOn(Math.max(1, Math.round(canvas.getAlpha() * pieceOpacity)));
                        canvas.createShapes.add(band);
                    }
                }
                band.bandTo(p, pressureWidth());
                canvas.redraw();
            }
        } else if (!straightShapes) {

           //System.out.println(canvas.getPenType() + " " + canvas.getPenPressure());
            Point p = e.getPoint();
            // Need to test if it is null incase the shape has been auto-cleared
            if (canvas.hasCreateShapes()) {
                canvas.getCurrentCreateShape().curveTo(p);
                canvas.redraw();
            }
        } else {
            // If in freeform shape mode then draw the guide line using the mousemoved method
            mouseMoved(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {

        Point p = e.getPoint();
        if (pressureStroke) {
            pressureStroke = false;
            // Keep plain filled shapes once the stroke is finished
            for (int i = 0; i < canvas.createShapes.size(); i++) {
                if (canvas.createShapes.get(i) instanceof AlcBandShape) {
                    canvas.createShapes.set(i, ((AlcBandShape) canvas.createShapes.get(i)).flatten());
                }
            }
            canvas.redraw();
            canvas.commitShapes();
            return;
        }
        // Only if this is a single click
        if (e.getClickCount() <= 1) {
            if (straightShapes) {
                if (firstClick) {
                    canvas.createShapes.add(new AlcShape(p));
                    firstClick = false;
                    // If the style is solid we need to initially keep two guides
                    if (canvas.getStyle() == STYLE_FILL) {
                        secondClick = true;
                    }
                } else if (secondClick) {
                    // Only if is is not the same as the last point
                    if (!p.equals(lastPt)) {
                        secondClick = false;
                        AlcShape secondShape = (canvas.guideShapes.get(guideSize - 1));
                        if (secondShape != null) {
                            secondShape.lineTo(p);
                        }
                        // Get the current shape and keep it as secondPath
                        secondPath = new GeneralPath(canvas.getCurrentGuideShape().getPath());
                        if (canvas.hasCreateShapes()) {
                            canvas.getCurrentCreateShape().lineTo(p);
                        }
                    }
                } else {
                    secondPath = null;
                    if (canvas.hasCreateShapes()) {
                        canvas.getCurrentCreateShape().lineTo(p);
                    }
                }
                lastPt = p;
            } else {
                // Need to test if it is null incase the shape has been auto-cleared
                if (canvas.hasCreateShapes()) {
                    canvas.getCurrentCreateShape().lineTo(p);
                }
            }
            canvas.redraw();
            if (!straightShapes) {
                if (outlineMode && canvas.getStyle() == STYLE_FILL) {
                    canvas.getCurrentCreateShape().setStyle(STYLE_FILL);
                }
                canvas.redraw();
                canvas.commitShapes();
            }
        }
    }
}
