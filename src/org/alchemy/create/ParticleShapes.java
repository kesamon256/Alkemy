/*
 *  This file is part of the Alkemy project, based on Alchemy - http://al.chemy.org
 *
 *  Alchemy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package org.alchemy.create;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.alchemy.core.*;

/**
 * Particles - the pen sets off little physical things: glitter that flutters down, sparks
 * that burst and arc, ink that drips and splashes, sand that pours and settles.
 * <p>
 * Every particle is an ordinary shape of the drawing, so the affects (Mirror, Displace,
 * Gradient...) work on them as on any line. When the pen lets go they finish falling on
 * the canvas and then become part of the drawing - one undo step for the whole stroke.
 */
public class ParticleShapes extends AlcModule implements AlcConstants {

    private static final int GLITTER = 0, SPARKS = 1, DRIPS = 2, SAND = 3;
    private static final String[] NAMES = {"Glitter", "Sparks", "Drips", "Sand"};
    private static final String[] ICONS = {"glitter.png", "sparks.png", "drips.png", "sand.png"};
    private static final String[] TIPS = {
        "Glitter - flakes and little stars burst from the pen, spin and flutter down",
        "Sparks - a burst of sparks flying out in arcs and leaving trails",
        "Drips - an ink line that runs down in drips, fast strokes splash",
        "Sand - grains pour from the pen and settle"};
    private static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userNodeForPackage(ParticleShapes.class);
    //
    private AlcToolBarSubSection subToolBarSection;
    private final AlcSubToggleButton[] buttons = new AlcSubToggleButton[NAMES.length];
    private int kind = PREFS.getInt("Particles Kind", GLITTER);
    /** How many particles a stroke sets off, and how far they fly - 1 to 100 */
    private int amount = PREFS.getInt("Particles Amount", 50), spread = PREFS.getInt("Particles Spread", 50);
    //
    private Random random = new Random();
    private Point2D.Float last;
    private long lastTime;
    /** Particles moving now - glitter and sand are drawn together in a few shapes, sparks and drips each in its own */
    private final ArrayList<Particle> live = new ArrayList<Particle>();
    private final ArrayList<Particle> resting = new ArrayList<Particle>();
    private final AlcShape[] groups = new AlcShape[3];
    private AlcShape inkLine;
    /** Lets the particles finish falling after the pen has let go */
    private javax.swing.Timer settle;

    private static class Particle {

        float x, y, vx, vy, angle, spin, size, sway;
        int age, life, group;
        boolean star;
        AlcShape shape;
        GeneralPath trail;
        ArrayList<Point2D.Float> spine;
        ArrayList<Float> widths;
    }

    public ParticleShapes() {
    }

    @Override
    protected void setup() {
        createSubToolBarSection();
        toolBar.addSubToolBarSection(subToolBarSection);
    }

    @Override
    protected void reselect() {
        toolBar.addSubToolBarSection(subToolBarSection);
    }

    @Override
    protected void deselect() {
        finishNow();
    }

    @Override
    protected void cleared() {
        stopSettling();
        canvas.setCreateAnimating(false);
        live.clear();
        resting.clear();
    }

    private void createSubToolBarSection() {
        subToolBarSection = new AlcToolBarSubSection(this);
        for (int i = 0; i < NAMES.length; i++) {
            final int index = i;
            buttons[i] = new AlcSubToggleButton(NAMES[i], AlcUtil.getUrlPath(ICONS[i], getClassLoader()));
            buttons[i].setToolTipText(TIPS[i]);
            buttons[i].addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    selectKind(index);
                }
            });
            subToolBarSection.add(buttons[i]);
        }
        final AlcSubSlider amountSlider = new AlcSubSlider("Amount", 1, 100, amount);
        amountSlider.setToolTipText("How many particles a stroke sets off - pen pressure adds more");
        amountSlider.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                amount = amountSlider.getValue();
                PREFS.putInt("Particles Amount", amount);
            }
        });
        subToolBarSection.add(amountSlider);
        final AlcSubSlider spreadSlider = new AlcSubSlider("Spread", 1, 100, spread);
        spreadSlider.setToolTipText("How far from the pen the particles fly");
        spreadSlider.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                spread = spreadSlider.getValue();
                PREFS.putInt("Particles Spread", spread);
            }
        });
        subToolBarSection.add(spreadSlider);
        selectKind(Math.max(0, Math.min(NAMES.length - 1, kind)));
    }

    private void selectKind(int index) {
        kind = index;
        PREFS.putInt("Particles Kind", index);
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setSelected(i == index);
        }
    }

    //////////////////////////////////////////////////////////////
    // SETTINGS
    //////////////////////////////////////////////////////////////
    private float size() {
        return Math.max(1F, canvas.getLineWidth());
    }

    /** Pen pressure from 0 to 1 - a medium press for the mouse */
    private float pressure() {
        if (canvas.getPenType() == PEN_CURSOR || !canvas.isPressureEnabled()) {
            return 0.6F;
        }
        return (float) Math.sqrt(Math.max(0, Math.min(1, canvas.getPenPressure())));
    }

    private int alpha(double factor) {
        return Math.max(1, Math.min(255, (int) Math.round(canvas.getAlpha() * factor)));
    }

    /** Particles for a step of the pen - more with the Amount slider, a firmer press and a longer step */
    private int count(float base, float distance) {
        float n = base * (amount / 50F) * (0.4F + pressure()) * Math.min(3F, 0.4F + distance / 10F);
        int whole = (int) n;
        return whole + (random.nextFloat() < n - whole ? 1 : 0);
    }

    private float spreadFactor() {
        return 0.25F + spread / 40F;
    }

    //////////////////////////////////////////////////////////////
    // PEN
    //////////////////////////////////////////////////////////////
    @Override
    public void mousePressed(MouseEvent e) {
        // A new stroke - the last one lands at once
        finishNow();
        random = new Random();
        last = canvas.getPenLocation();
        lastTime = e.getWhen();
        live.clear();
        resting.clear();
        for (int i = 0; i < groups.length; i++) {
            groups[i] = null;
        }
        inkLine = null;
        if (kind == GLITTER || kind == SAND) {
            double[] shades = kind == GLITTER ? new double[]{1.0, 0.7, 0.4} : new double[]{0.85, 0.55, 0.3};
            for (int i = 0; i < groups.length; i++) {
                groups[i] = new AlcShape(new GeneralPath(), canvas.getColor(), alpha(shades[i]), STYLE_FILL, size());
                canvas.createShapes.add(groups[i]);
            }
        } else if (kind == DRIPS) {
            inkLine = new AlcShape();
            inkLine.setStyle(STYLE_FILL);
            inkLine.setAlpha(alpha(1));
            inkLine.spineTo(last, inkWidth());
            canvas.createShapes.add(inkLine);
        }
        emit(last, 0, 0, 3);
        // The particles move on their own until they are kept - the affects keep working on them
        canvas.setCreateAnimating(true);
        canvas.redraw();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (last == null) {
            return;
        }
        Point2D.Float p = canvas.getPenLocation();
        float dx = p.x - last.x, dy = p.y - last.y;
        float distance = (float) Math.hypot(dx, dy);
        if (distance < 0.5F) {
            return;
        }
        long dt = Math.max(1, Math.min(100, e.getWhen() - lastTime));
        lastTime = e.getWhen();
        if (inkLine != null) {
            inkLine.spineTo(p, inkWidth());
        }
        emit(p, dx, dy, distance);
        // Fast strokes splash drops of ink
        if (kind == DRIPS && distance / dt > 1.2F) {
            splash(p, dx, dy);
        }
        step();
        last = p;
        canvas.redraw();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (last == null) {
            return;
        }
        last = null;
        if (live.isEmpty()) {
            canvas.redraw();
            keep();
            return;
        }
        // The particles finish falling on the canvas, then become part of the drawing
        settle = new javax.swing.Timer(16, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                for (int i = 0; i < 2; i++) {
                    step();
                }
                canvas.redraw();
                if (live.isEmpty()) {
                    stopSettling();
                    keep();
                }
            }
        });
        settle.start();
    }

    private void stopSettling() {
        if (settle != null) {
            settle.stop();
            settle = null;
        }
    }

    /** Land everything at once and keep it - before a new stroke or another tool */
    private void finishNow() {
        if (settle == null) {
            return;
        }
        stopSettling();
        for (int i = 0; i < 400 && !live.isEmpty(); i++) {
            step();
        }
        canvas.redraw();
        keep();
    }

    /** The stroke has landed - it becomes part of the drawing */
    private void keep() {
        canvas.setCreateAnimating(false);
        canvas.commitShapes();
    }

    //////////////////////////////////////////////////////////////
    // PARTICLES
    //////////////////////////////////////////////////////////////
    private float inkWidth() {
        return size() * 0.5F * (0.5F + 0.7F * pressure());
    }

    private void emit(Point2D.Float p, float dx, float dy, float distance) {
        float s = size(), sp = spreadFactor();
        switch (kind) {
            case GLITTER: {
                int n = count(2.2F, distance);
                for (int i = 0; i < n; i++) {
                    Particle f = new Particle();
                    double a = random.nextDouble() * Math.PI * 2;
                    double v = (0.8 + random.nextDouble() * 3) * sp;
                    f.x = p.x;
                    f.y = p.y;
                    f.vx = (float) (Math.cos(a) * v) + dx * 0.15F;
                    f.vy = (float) (Math.sin(a) * v) - 1.2F * sp + dy * 0.15F;
                    f.size = (float) (0.6 + random.nextDouble() * 0.9) * Math.max(2F, s * 0.7F);
                    f.angle = (float) (random.nextDouble() * Math.PI);
                    f.spin = (float) ((random.nextDouble() - 0.5) * 0.4);
                    f.sway = (float) (random.nextDouble() * Math.PI * 2);
                    f.star = random.nextDouble() < 0.3;
                    f.group = random.nextInt(3);
                    f.life = 50 + random.nextInt(60);
                    live.add(f);
                }
                break;
            }
            case SPARKS: {
                int n = count(1.4F, distance);
                for (int i = 0; i < n; i++) {
                    Particle f = new Particle();
                    double a = random.nextDouble() * Math.PI * 2;
                    double v = (2 + random.nextDouble() * 5) * sp;
                    f.x = p.x;
                    f.y = p.y;
                    f.vx = (float) (Math.cos(a) * v) + dx * 0.2F;
                    f.vy = (float) (Math.sin(a) * v) - 2F * sp + dy * 0.2F;
                    f.life = 18 + random.nextInt(30);
                    f.trail = new GeneralPath();
                    f.trail.moveTo(f.x, f.y);
                    f.shape = new AlcShape(f.trail, canvas.getColor(), alpha(0.35 + random.nextDouble() * 0.6),
                            STYLE_STROKE, (float) (0.4 + random.nextDouble()) * Math.max(1F, s / 5));
                    canvas.createShapes.add(f.shape);
                    live.add(f);
                }
                break;
            }
            case DRIPS: {
                // Now and then the ink gathers and runs down
                if (random.nextFloat() < Math.min(0.9F, 0.06F * (amount / 50F) * (0.5F + pressure()) * distance / 3F)) {
                    Particle f = new Particle();
                    f.x = p.x + (float) random.nextGaussian() * inkWidth() * 0.3F;
                    f.y = p.y;
                    f.vy = 0.3F;
                    f.size = inkWidth() * (float) (0.45 + random.nextDouble() * 0.5);
                    f.life = (int) ((25 + random.nextInt(70)) * sp);
                    f.spine = new ArrayList<Point2D.Float>();
                    f.widths = new ArrayList<Float>();
                    f.spine.add(new Point2D.Float(f.x, f.y));
                    f.widths.add(f.size);
                    f.shape = new AlcShape(new GeneralPath(), canvas.getColor(), alpha(1), STYLE_FILL, size());
                    f.shape.setSpine(f.spine);
                    f.shape.setSpineWidth(f.widths);
                    canvas.createShapes.add(f.shape);
                    live.add(f);
                }
                break;
            }
            case SAND: {
                int n = count(7F, distance);
                for (int i = 0; i < n; i++) {
                    Particle f = new Particle();
                    f.x = p.x + (float) random.nextGaussian() * s * 0.6F * sp;
                    f.y = p.y + (float) random.nextGaussian() * s * 0.3F;
                    f.vx = (float) random.nextGaussian() * 0.5F * sp + dx * 0.05F;
                    f.vy = (float) (random.nextDouble() * 1.5);
                    f.size = (float) (0.5 + random.nextDouble()) * Math.max(1F, s / 6);
                    f.group = random.nextInt(3);
                    // How far it falls before it settles
                    f.life = (int) ((10 + random.nextInt(50)) * sp);
                    live.add(f);
                }
                break;
            }
        }
    }

    /** Drops flung off by a fast stroke */
    private void splash(Point2D.Float p, float dx, float dy) {
        int n = 1 + random.nextInt(3);
        float len = (float) Math.hypot(dx, dy);
        for (int i = 0; i < n; i++) {
            float reach = (float) (random.nextDouble() * 4 + 1) * size() * spreadFactor();
            float side = (float) random.nextGaussian() * 0.6F;
            float x = p.x + (dx / len + side * -dy / len) * reach, y = p.y + (dy / len + side * dx / len) * reach;
            float r = (float) (0.3 + random.nextDouble() * 0.7) * inkWidth();
            AlcShape drop = new AlcShape(new GeneralPath(new Ellipse2D.Float(x - r, y - r, r * 2, r * 2)),
                    canvas.getColor(), alpha(1), STYLE_FILL, size());
            canvas.createShapes.add(drop);
        }
    }

    /** One step of physics for every moving particle */
    private void step() {
        float sp = spreadFactor();
        for (int i = live.size() - 1; i >= 0; i--) {
            Particle f = live.get(i);
            f.age++;
            switch (kind) {
                case GLITTER:
                    // Air holds flakes back - they flutter side to side as they fall
                    f.vx = f.vx * 0.94F + (float) Math.cos(f.sway + f.age * 0.18) * 0.12F;
                    f.vy = f.vy * 0.94F + 0.09F;
                    f.angle += f.spin;
                    break;
                case SPARKS:
                    f.vx *= 0.97F;
                    f.vy = f.vy * 0.97F + 0.22F;
                    break;
                case DRIPS:
                    // Runs down slowly, wobbling a little, and thins out
                    f.vy = Math.min(1.6F, f.vy + 0.05F);
                    f.vx = (float) random.nextGaussian() * 0.12F;
                    break;
                case SAND:
                    f.vx *= 0.9F;
                    f.vy = Math.min(4F * sp, f.vy + 0.35F);
                    break;
            }
            f.x += f.vx;
            f.y += f.vy;
            if (f.trail != null) {
                // The shape's own outline - an affect (Displace) may have given it a new one
                f.shape.getPath().lineTo(f.x, f.y);
            }
            if (f.spine != null) {
                float left = 1F - f.age / (float) f.life;
                f.spine.add(new Point2D.Float(f.x, f.y));
                f.widths.add(Math.max(0.3F, f.size * (0.55F + 0.45F * left)));
                f.shape.createSpine();
            }
            if (f.age >= f.life) {
                live.remove(i);
                if (kind == DRIPS && f.shape != null) {
                    // A drop gathers at the end of the run
                    GeneralPath path = f.shape.getPath();
                    float r = f.size * 1.1F;
                    path.append(new Ellipse2D.Float(f.x - r, f.y - r * 0.8F, r * 2, r * 2), false);
                } else if (kind == GLITTER || kind == SAND) {
                    resting.add(f);
                }
            }
        }
        if (kind == GLITTER || kind == SAND) {
            rebuildGroups();
        }
    }

    /** Glitter and grains are drawn as a few shapes - one for each shade - made again every step */
    private void rebuildGroups() {
        GeneralPath[] paths = new GeneralPath[groups.length];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = new GeneralPath(GeneralPath.WIND_NON_ZERO);
        }
        for (ArrayList<Particle> list : java.util.Arrays.asList(resting, live)) {
            for (Particle f : list) {
                paths[f.group].append(kind == GLITTER ? flake(f) : grain(f), false);
            }
        }
        for (int i = 0; i < groups.length; i++) {
            if (groups[i] != null) {
                groups[i].setPath(paths[i]);
            }
        }
    }

    /** A glitter flake - a thin diamond, or a four pointed star - turned by its spin */
    private static java.awt.Shape flake(Particle f) {
        GeneralPath p = new GeneralPath();
        float s = f.size;
        if (f.star) {
            float in = s * 0.22F;
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                float r = (k % 2 == 0) ? s : in;
                float x = (float) (Math.cos(a) * r), y = (float) (Math.sin(a) * r);
                if (k == 0) {
                    p.moveTo(x, y);
                } else {
                    p.lineTo(x, y);
                }
            }
        } else {
            p.moveTo(0, -s);
            p.lineTo(s * 0.45F, 0);
            p.lineTo(0, s);
            p.lineTo(-s * 0.45F, 0);
        }
        p.closePath();
        AffineTransform t = AffineTransform.getTranslateInstance(f.x, f.y);
        t.rotate(f.angle);
        return p.createTransformedShape(t);
    }

    private static java.awt.Shape grain(Particle f) {
        return new java.awt.geom.Rectangle2D.Float(f.x - f.size / 2, f.y - f.size / 2, f.size, f.size);
    }
}
