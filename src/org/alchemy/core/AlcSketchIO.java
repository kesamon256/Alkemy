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
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.io.*;
import java.util.ArrayList;

/**
 * Saves and opens editable sketches (*.alchemy).
 * <p>
 * Unlike the PDF and bitmap exports, a sketch keeps every shape as a vector
 * so drawing can continue later. The format is a small binary file:
 * a header, the background colour and the layers with their shapes and paths.
 */
class AlcSketchIO {

    static final String EXTENSION = "alchemy";
    private static final String MAGIC = "ALCHEMY-SKETCH";
    /** 1 - one list of shapes, 2 - layers, 3 - gradients along the stroke */
    private static final int VERSION = 3;

    /** Save the layers and committed shapes of the canvas */
    static void save(File file, AlcCanvas canvas) throws IOException {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        try {
            out.writeUTF(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(canvas.getBackgroundColor().getRGB());
            out.writeInt(canvas.getLayers().size());
            out.writeInt(canvas.getActiveLayerIndex());
            for (AlcLayer layer : canvas.getLayers()) {
                out.writeUTF(layer.name);
                out.writeBoolean(layer.visible);
                out.writeBoolean(layer.locked);
                out.writeFloat(layer.opacity);
                writeShapes(out, layer.shapes);
            }
        } finally {
            out.close();
        }
    }

    private static void writeShapes(DataOutputStream out, java.util.List<AlcShape> shapes) throws IOException {
        out.writeInt(shapes.size());
        for (AlcShape shape : shapes) {
            // The line pattern rides in the upper bits of the style
            out.writeInt(shape.style | (shape.dash << 8));
            out.writeFloat(shape.lineWidth);
            out.writeInt(shape.alpha);
            out.writeInt(shape.color.getRGB());
            GradientPaint gp = shape.getGradientPaint();
            out.writeBoolean(gp != null);
            if (gp != null) {
                writePoint(out, gp.getPoint1());
                out.writeInt(gp.getColor1().getRGB());
                writePoint(out, gp.getPoint2());
                out.writeInt(gp.getColor2().getRGB());
            }
            out.writeBoolean(shape.alongColor != null);
            if (shape.alongColor != null) {
                out.writeInt(shape.alongColor.getRGB());
                out.writeFloat(shape.alongFrom);
                out.writeFloat(shape.alongTo);
            }
            writePath(out, shape.path);
        }
    }

    /** Read the layers, shapes and background colour of a sketch */
    static Sketch open(File file) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
        try {
            if (!MAGIC.equals(in.readUTF())) {
                throw new IOException("Not an Alchemy sketch: " + file.getName());
            }
            int version = in.readInt();
            if (version > VERSION) {
                throw new IOException("This sketch was made with a newer version of Alchemy");
            }
            Sketch sketch = new Sketch();
            sketch.background = new Color(in.readInt());
            sketch.layers = new ArrayList<AlcLayer>();
            if (version == 1) {
                // Sketches from before layers become one layer
                sketch.layers.add(new AlcLayer(Alchemy.bundle.getString("layerName") + " 1", readShapes(in, version)));
                sketch.activeLayer = 0;
            } else {
                int count = in.readInt();
                sketch.activeLayer = in.readInt();
                for (int i = 0; i < count; i++) {
                    String name = in.readUTF();
                    boolean visible = in.readBoolean();
                    boolean locked = in.readBoolean();
                    float opacity = in.readFloat();
                    AlcLayer layer = new AlcLayer(name, readShapes(in, version));
                    layer.visible = visible;
                    layer.locked = locked;
                    layer.opacity = opacity;
                    sketch.layers.add(layer);
                }
            }
            return sketch;
        } finally {
            in.close();
        }
    }

    private static ArrayList<AlcShape> readShapes(DataInputStream in, int version) throws IOException {
        int count = in.readInt();
        ArrayList<AlcShape> shapes = new ArrayList<AlcShape>(count);
        for (int i = 0; i < count; i++) {
            int styleBits = in.readInt();
            int style = styleBits & 0xFF;
            float lineWidth = in.readFloat();
            int alpha = in.readInt();
            Color color = new Color(in.readInt(), true);
            GradientPaint gp = null;
            if (in.readBoolean()) {
                Point2D.Float p1 = readPoint(in);
                Color c1 = new Color(in.readInt(), true);
                Point2D.Float p2 = readPoint(in);
                Color c2 = new Color(in.readInt(), true);
                gp = new GradientPaint(p1, c1, p2, c2);
            }
            Color along = null;
            float alongFrom = 0F, alongTo = 1F;
            if (version >= 3 && in.readBoolean()) {
                along = new Color(in.readInt(), true);
                alongFrom = in.readFloat();
                alongTo = in.readFloat();
            }
            int winding = in.readByte();
            GeneralPath path = readPath(in);
            AlcShape shape = new AlcShape(path, color, alpha, style, lineWidth);
            // The constructor resets these
            path.setWindingRule(winding);
            shape.setAlphaColor(color);
            shape.setGradientPaint(gp);
            shape.setDash(styleBits >> 8);
            shape.setAlongGradient(along, alongFrom, alongTo);
            shapes.add(shape);
        }
        return shapes;
    }

    private static void writePoint(DataOutputStream out, Point2D p) throws IOException {
        out.writeFloat((float) p.getX());
        out.writeFloat((float) p.getY());
    }

    private static Point2D.Float readPoint(DataInputStream in) throws IOException {
        return new Point2D.Float(in.readFloat(), in.readFloat());
    }

    private static void writePath(DataOutputStream out, GeneralPath path) throws IOException {
        out.writeByte(path.getWindingRule());
        float[] coords = new float[6];
        for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
            int type = it.currentSegment(coords);
            out.writeByte(type);
            for (int i = 0; i < pointCount(type) * 2; i++) {
                out.writeFloat(coords[i]);
            }
        }
        out.writeByte(-1);
    }

    private static GeneralPath readPath(DataInputStream in) throws IOException {
        GeneralPath path = new GeneralPath();
        float[] c = new float[6];
        int type;
        while ((type = in.readByte()) != -1) {
            for (int i = 0; i < pointCount(type) * 2; i++) {
                c[i] = in.readFloat();
            }
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    path.moveTo(c[0], c[1]);
                    break;
                case PathIterator.SEG_LINETO:
                    path.lineTo(c[0], c[1]);
                    break;
                case PathIterator.SEG_QUADTO:
                    path.quadTo(c[0], c[1], c[2], c[3]);
                    break;
                case PathIterator.SEG_CUBICTO:
                    path.curveTo(c[0], c[1], c[2], c[3], c[4], c[5]);
                    break;
                case PathIterator.SEG_CLOSE:
                    path.closePath();
                    break;
                default:
                    throw new IOException("Damaged sketch file");
            }
        }
        return path;
    }

    private static int pointCount(int segmentType) {
        switch (segmentType) {
            case PathIterator.SEG_MOVETO:
            case PathIterator.SEG_LINETO:
                return 1;
            case PathIterator.SEG_QUADTO:
                return 2;
            case PathIterator.SEG_CUBICTO:
                return 3;
            default:
                return 0;
        }
    }

    /** The contents of a sketch file */
    static class Sketch {

        Color background;
        ArrayList<AlcLayer> layers;
        int activeLayer;
    }
}
