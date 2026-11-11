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
import java.awt.print.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.Graphics2D;

// ITEXT
import com.lowagie.text.pdf.*;

import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.print.Printable;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import java.io.*;

// PDF READER
import com.sun.pdfview.*;

// JPEN
import jpen.*;
import jpen.event.PenListener;

/** 
 * The Alchemy canvas <br>
 * Stores all shapes created and handles all graphics related stuff<br>
 * Think saving pdfs, printing, and of course displaying! 
 */
public class AlcCanvas extends JPanel implements AlcConstants, MouseListener, MouseMotionListener, PenListener, Printable {
    //////////////////////////////////////////////////////////////
    // GLOBAL SHAPE SETTINGS
    //////////////////////////////////////////////////////////////
//  /** Global Shape Foreground color */
//    private Color color;
    /** Global Shape Foreground Alpha */
    private int alpha = 255;
    private boolean alphaLocked = false;
    
    // The current color is stored here.  An array is used so that
    // mulitple "states" can be stored (Stylus/Eraser, etc)
    private ArrayList<Color> currentColorSet;
    private int[] currentAlphaSet = {255,255};
    private int currentColorIndex;
    
    //  This is not currently used, but may be implemented for
    //  recent color switching a la mypaint.
    public Color previousColor;
    
    // Swatch stored here
    public ArrayList<Color> swatch;
    public int activeSwatchIndex;
    
    /** Global Shape Style - (1) LINE or (2) SOLID FILL */
    private int style = STYLE_STROKE;
    /** Line pattern for new shapes - solid, dashed or dotted */
    private int dash = DASH_NONE;
    /** Globl Shape Line Weight (if the style is line) */
    private float lineWidth = 1F;
    //////////////////////////////////////////////////////////////
    // GLOBAL SETTINGS
    ////////////////////////////////////////////////////////////// 
    /** Background color */
    private Color bgColor;
    /** 'Redraw' on or off **/
    private boolean redraw = true;
    /** Smoothing on or off */
    boolean smoothing;
    /** Boolean used by the timer to determine if there has been canvas activity */
    private boolean canvasChanged = false;
    /** Draw under the other shapes on the canvas */
    private boolean drawUnder = false;
    /** View - zoom level and position of the drawing on screen */
    private double viewScale = 1;
    private double viewX = 0;
    private double viewY = 0;
    /** The view the cached canvas image was drawn for */
    private AffineTransform canvasImageView;
    /** How long drawing every shape took the last time - heavy drawings are not redrawn on every zoom / pan step */
    private long lastFullRenderNanos;
    private static final long VIEW_FAST_NANOS = 12000000L;
    private static final int VIEW_SETTLE_MS = 160;
    /** The view moved but the cached picture is only moved / scaled - drawn sharply once the view settles */
    private boolean viewPending;
    private javax.swing.Timer viewSettle;
    static final double MIN_ZOOM = 0.1;
    static final double MAX_ZOOM = 32;
    /** Navigation with the mouse - Ctrl + drag zooms, the middle button pans */
    private static final int NAV_NONE = 0, NAV_PAN = 1, NAV_ZOOM = 2;
    private int navigation = NAV_NONE;
    /** Space is held down - the left button drags the canvas like the Photoshop hand tool */
    private boolean spaceDown = false;
    //////////////////////////////////////////////////////////////
    // POLYGON STROKES (Alt) AND SOLID CUTS (Shift)
    //////////////////////////////////////////////////////////////
    /** Letting go with Alt held keeps the stroke open - each click adds a straight piece */
    private boolean polyActive = false;
    /** The stabilized line is catching up after the pen was lifted */
    private boolean settling = false;
    /** Resolution the stroke being drawn is worked out at - lowered when it takes too long to draw */
    private double liveScale = 1;
    private BufferedImage liveImage;
    /** Time one screen update of the stroke being drawn may take - about one frame at 60 Hz */
    private static final long LIVE_FRAME_NANOS = 16000000L;
    /** Where the next straight piece starts, in drawing coordinates */
    private Point2D.Float polyAnchor;
    /** The mouse, in screen coordinates, for the guide line */
    private Point polyCursor;
    /** Alt was used for a polygon - its release must not open the menu bar */
    private boolean altUsed = false;
    /** A Shift stroke with the Solid style cuts its area out of the shapes */
    private boolean cutting = false;
    private final ArrayList<Point2D.Float> cutPoints = new ArrayList<Point2D.Float>();
    /** Pan speed in pixels per millisecond, used to let the canvas glide after a quick drag */
    private double panVelocityX, panVelocityY;
    private long lastPanTime;
    private Point lastPanPoint;
    private javax.swing.Timer glideTimer;
    /** The canvas is painting into its own cached image - leave out screen only overlays */
    private boolean renderingBuffer = false;
    /** Part of the drawing to export, in drawing coordinates - null for the original window area */
    private Rectangle2D exportArea;
    /** Layers of the drawing, bottom first. {@link #shapes} is the list of the active one */
    private final ArrayList<AlcLayer> layers = new ArrayList<AlcLayer>();
    private int activeLayer = 0;
    /** Numbering of new layer names */
    private int layerCounter = 0;
    /** The current pen stroke was refused - the active layer is hidden or locked */
    private boolean blockedStroke = false;
    /** A short message shown on the canvas, and until when */
    private String message;
    private long messageUntil;
    /** Draggable lines of symmetry, in drawing coordinates */
    private boolean showAxisX, showAxisY;
    private double axisX, axisY;
    private AxisListener axisListener;
    private static final int AXIS_NONE = 0, AXIS_X = 1, AXIS_Y = 2, AXIS_BOTH = 3;
    /** The axis being dragged */
    private int axisDrag = AXIS_NONE;
    /** The cursor shows that an axis can be dragged */
    private boolean axisHover = false;
    /** Lasso selection - while on, the pen selects and transforms shapes instead of drawing */
    private AlcSelectionTool selectionTool;
    private boolean selecting = false;
    /** The current pen stroke belongs to the selection tool */
    private boolean selectStroke = false;
    /** Choosing the export area on the canvas - null when not choosing */
    private AlcExportSelector exportSelector;
    /** The area chosen for the last export, offered again next time */
    private Rectangle2D lastExportArea;
    private Point navStart;
    private double navStartScale;
    private double navStartX;
    private double navStartY;

    //////////////////////////////////////////////////////////////
    // PEN SETTINGS
    //////////////////////////////////////////////////////////////
    /** Events on or off - stop mouse/pen events to the modules when inside the UI */
    private boolean events = true;
    private boolean createEvents = true;
    private boolean affectEvents = true;
    /** The Pen manager used by JPen*/
    private PenManager pm;
    /** Pen down or up */
    private boolean penDown = false;
    /** The type of pen - PEN_STYLUS / PEN_ERASER / PEN_CURSOR */
    private int penType = PEN_CURSOR;
    /** Pen Pressure if available */
    private float penPressure = 0F;
    /** Pen Tilt if available */
    private Point2D.Float penTilt = new Point2D.Float();
    /** Pen Location - if a pen is available this will be a float otherwise int */
    private Point2D.Float penLocation = new Point2D.Float();
    /** Pen location has changed or not */
    private boolean penLocationChanged = true;
    /** Log every pen colour change with its caller - for tracking down colour problems */
    private static final boolean DEBUG_COLOR = Boolean.getBoolean("alchemy.debugColor");
    /** Pen pressure used by the tools - when off, tools get a constant medium pressure */
    private boolean pressureEnabled = true;
    /** A Windows Ink pen is currently being used */
    private boolean inkPenActive = false;
    //////////////////////////////////////////////////////////////
    // SHAPES
    //////////////////////////////////////////////////////////////
    /** Array list containing shapes that have been commited.
     *  Shapes in this list are generally rendered to the image buffer
     *  to improve performance */
    public ArrayList<AlcShape> shapes;
    /** Array list containing shapes currently in use by create modules */
    public ArrayList<AlcShape> createShapes;
    /** Array list containing shapes currently in use by affect modules */
    public ArrayList<AlcShape> affectShapes;
    /** Array list containing shapes used as visual guides - not actual geometry */
    public ArrayList<AlcShape> guideShapes;
    /** Full shape array of each array list */
    ArrayList[] fullShapeList = new ArrayList[3];
    /** Active shape list plus guides */
    ArrayList[] activeShapeList = new ArrayList[2];
    //////////////////////////////////////////////////////////////
    // IMAGE
    //////////////////////////////////////////////////////////////
    /** An image of the canvas drawn behind active shapes */
    private Image canvasImage;
    /** Image than can be drawn on the canvas */
    private BufferedImage image;
    /** Display the Image or not */
    private boolean imageDisplay = false;
    /** Position to display the image */
    private Point imageLocation = new Point(0, 0);
    /** An image used to fake transparency in fullscreen mode */
    private Image transparentImage;
    //////////////////////////////////////////////////////////////
    // DISPLAY
    //////////////////////////////////////////////////////////////
    /** Record indicator on/off */
    private boolean recordIndicator = false;
    /** Draw guides */
    private boolean guides = true;
    /** Graphics Envrionment - updated everytime the volatile buffImage is refreshed */
    private GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    /** Graphics Configuration - updated everytime the volatile buffImage is refreshed */
    private GraphicsConfiguration gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
    /** A Vector based canvas for full redrawing */
    VectorCanvas vectorCanvas;
    /** Previous cursor */
    Cursor oldCursor;
    /** Automatic toggling of the toolbar */
    private boolean autoToggleToolBar;
    //////////////////////////////////////////////////////////////
    // SKETCHING AIDS
    //////////////////////////////////////////////////////////////
    /** Stabilizer strength, 0 (off) to below 1 - smooths the pen path for every create module */
    private float stabilizer;
    /** The stabilized pen position while drawing, or null */
    private Point2D.Float stabilizedLocation;
    /** Paper pattern drawn behind the shapes on screen - one of the PAPER constants */
    private int paper;
    /** Shape eraser - active while dragging with the right mouse button */
    private boolean erasing = false;
    /** Last location of the shape eraser, or null when not erasing */
    private Point2D.Float eraserLocation;
    /** Radius of the shape eraser */
    static final int ERASER_RADIUS = 10;
    static final int PAPER_BLANK = 0;
    static final int PAPER_GRID = 1;
    static final int PAPER_DOTS = 2;
    static final int PAPER_LINES = 3;

    /** Creates a new instance of AlcCanvas*/
    AlcCanvas() {
        currentColorSet = new ArrayList<Color>(2);
        currentColorSet.add(Color.WHITE);
        currentColorSet.add(Color.WHITE);
        currentColorIndex = 0;
        
        this.smoothing = Alchemy.preferences.smoothing;
        this.bgColor = new Color(Alchemy.preferences.bgColor);
        currentColorSet.set(currentColorIndex, new Color(Alchemy.preferences.color));

        this.autoToggleToolBar = !Alchemy.preferences.paletteAttached;
        this.stabilizer = AlcPreferences.prefs.getFloat("Stabilizer", 0F);
        this.paper = AlcPreferences.prefs.getInt("Paper", PAPER_BLANK);
        this.pressureEnabled = AlcPreferences.prefs.getBoolean("Pen Pressure", true);
        // Size and opacity can both follow the pressure - the old single choice is the starting point
        int oldMode = AlcPreferences.prefs.getInt("Pen Pressure Mode", PRESSURE_SIZE);
        this.pressureSize = AlcPreferences.prefs.getBoolean("Pen Pressure Size", oldMode == PRESSURE_SIZE);
        this.pressureOpacity = AlcPreferences.prefs.getBoolean("Pen Pressure Opacity", oldMode == PRESSURE_OPACITY);
        
        /** Holds saved swatch colors */
        swatch = new ArrayList<Color>();
        /** Keeps track of which swatch color is active */
        activeSwatchIndex = -1;

        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        selectionTool = new AlcSelectionTool(this);
        // Holding space turns the left button into the hand tool
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {

            public boolean dispatchKeyEvent(KeyEvent e) {
                // Q held - the effects that follow the speed of the hand are on
                // It works whenever the pen is over the canvas - even if a text field (line weight, colour)
                // still has the keyboard - and is then kept out of that field
                boolean overCanvas = isShowing() && getMousePosition() != null;
                boolean inText = e.getComponent() instanceof javax.swing.text.JTextComponent;
                boolean qKey = e.getKeyCode() == MOTION_KEY || (e.getID() == KeyEvent.KEY_TYPED && Character.toLowerCase(e.getKeyChar()) == 'q');
                if (qKey && (overCanvas || !inText || (e.getID() == KeyEvent.KEY_RELEASED && motionHeld))) {
                    if (e.getID() == KeyEvent.KEY_TYPED) {
                        return overCanvas && inText;
                    }
                    boolean held = e.getID() == KeyEvent.KEY_PRESSED;
                    penLog("Q " + (held ? "down" : "up") + " overCanvas=" + overCanvas + " focus=" + (e.getComponent() == null ? null : e.getComponent().getClass().getSimpleName()));
                    if (held != motionHeld) {
                        motionHeld = held;
                        if (motionHold) {
                            setMotionCursorHint(held);
                        }
                        if (!held) {
                            endMotionEdit();
                        }
                    }
                    // Over the canvas the Q does not end up in a text field that still has the keyboard
                    if (overCanvas && inText) {
                        return true;
                    }
                }
                // Letting go of Alt finishes a polygon stroke - and is kept from opening the menu bar
                if (e.getKeyCode() == KeyEvent.VK_ALT && e.getID() == KeyEvent.KEY_RELEASED) {
                    if (polyActive && !penDown) {
                        finishPoly();
                        altUsed = false;
                        return true;
                    }
                    if (altUsed) {
                        altUsed = false;
                        return true;
                    }
                }
                if (polyActive && e.getID() == KeyEvent.KEY_PRESSED
                        && (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_ESCAPE)) {
                    if (!penDown) {
                        finishPoly();
                    }
                    return true;
                }
                // Keys of the selection tool - only in the main window, not while typing elsewhere
                if (selecting && e.getID() == KeyEvent.KEY_PRESSED && e.getComponent() != null && SwingUtilities.getWindowAncestor(e.getComponent()) == Alchemy.window
                        && !(e.getComponent() instanceof javax.swing.text.JTextComponent)) {
                    int key = e.getKeyCode();
                    if ((key == KeyEvent.VK_DELETE || key == KeyEvent.VK_BACK_SPACE) && e.getModifiersEx() == 0 && selectionTool.hasSelection()) {
                        selectionTool.deleteSelected();
                        return true;
                    } else if (key == KeyEvent.VK_ESCAPE) {
                        if (selectionTool.hasSelection()) {
                            selectionTool.clearSelection();
                        } else {
                            setSelecting(false);
                        }
                        return true;
                    }
                }
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    if (e.getID() == KeyEvent.KEY_PRESSED && !spaceDown) {
                        spaceDown = true;
                        if (navigation == NAV_NONE) {
                            setHandCursor(Cursor.HAND_CURSOR);
                        }
                    } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                        spaceDown = false;
                        if (navigation == NAV_NONE) {
                            restoreCursor();
                        }
                    }
                }
                // Let the key through - Type Shapes still types spaces
                return false;
            }
        });
        // Ctrl + wheel zooms at the mouse, the wheel alone scrolls (Shift for sideways)
        this.addMouseWheelListener(new MouseWheelListener() {

            public void mouseWheelMoved(MouseWheelEvent e) {
                double rotation = e.getPreciseWheelRotation();
                if (e.isControlDown()) {
                    zoomAt(Math.pow(1.15, -rotation), e.getX(), e.getY());
                } else if (e.isShiftDown()) {
                    panBy(-rotation * 40, 0);
                } else {
                    panBy(0, -rotation * 40);
                }
            }
        });

        shapes = new ArrayList<AlcShape>(100);
        shapes.ensureCapacity(100);
        layers.add(new AlcLayer(newLayerName(), shapes));
        createShapes = new ArrayList<AlcShape>(25);
        createShapes.ensureCapacity(25);
        affectShapes = new ArrayList<AlcShape>(25);
        affectShapes.ensureCapacity(25);
        guideShapes = new ArrayList<AlcShape>(25);
        guideShapes.ensureCapacity(25);

        fullShapeList[0] = shapes;
        fullShapeList[1] = createShapes;
        fullShapeList[2] = affectShapes;
        
        activeShapeList[0] = createShapes;
        activeShapeList[1] = affectShapes;

        vectorCanvas = new VectorCanvas();

        pm = new PenManager(this);
        pm.pen.addListener(this);
        pm.pen.setFrequencyLater(200);

        this.setCursor(CURSOR_CROSS);
    }

    /** Bitmap Canvas
     *  Draws all current shapes on top of the buffered image
     * @param g Graphics Object to draw on
     */
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        java.awt.Rectangle visibleRect = this.getVisibleRect();
        int w = visibleRect.width;
        int h = visibleRect.height;

        // Draw the 'fake' Transparent Image
        if (Alchemy.window.isTransparent() && transparentImage != null) {
            g2.drawImage(transparentImage, 0, 0, null);

            // Draw the image if present
            if (imageDisplay && image != null) {
                Point p = getImageLocation();
               g2.drawImage(getImage(), (int)p.getX(), (int)p.getY(), null);
            }
        } else {
            // Draw the image if present
            if (imageDisplay && image != null) {
                Point p = getImageLocation();
                g2.drawImage(getImage(), (int)p.getX(), (int)p.getY(), null);
            }
            // Paint background.
            g2.setColor(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue()));
            g2.fillRect(0, 0, w, h);
            if (guides) {
                paintPaper(g2, w, h);
            }
        }
        // The screen transform - shapes are drawn through the view on top of it
        AffineTransform screen = g2.getTransform();
        AffineTransform view = new AffineTransform(screen);
        view.concatenate(getViewTransform());


        if (smoothing) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        } else {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }

        // Draw the flattened buffImage
        if (canvasImage != null && !drawUnder) {
            drawCanvasImage(g2);
        }
        if (redraw) {
            g2.setTransform(view);
            // The shape being drawn takes the opacity of its layer
            Composite oldComposite = g2.getComposite();
            float layerOpacity = getActiveLayer().opacity;
            if (layerOpacity < 1F) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, layerOpacity));
            }
            // A stroke too heavy to draw in time is worked out at a lower resolution until the pen
            // lifts - it is drawn at full quality into the picture when it is finished, so the result is the same
            long liveStart = System.nanoTime();
            // On the graphics card the stroke is drawn as one piece at full resolution and laid over
            // the canvas - faded as a whole on a faded layer, like the layer once the stroke is finished
            BufferedImage gpuLive = null;
            if (AlcSkia.enabled && hasLiveShapes()) {
                double device = Math.abs(screen.getScaleX());
                AffineTransform t = AffineTransform.getScaleInstance(device, device);
                t.concatenate(getViewTransform());
                gpuLive = AlcSkia.drawLive(Math.max(1, (int) Math.ceil(w * device)), Math.max(1, (int) Math.ceil(h * device)), t, activeShapeList, smoothing);
            }
            if (gpuLive != null) {
                g2.setTransform(screen);
                g2.drawImage(gpuLive, 0, 0, w, h, null);
            } else if ((penDown && liveScale < 1) || layerOpacity < 1F) {
                // On a faded layer the stroke is drawn as one piece first and then faded as a whole,
                // the same way the layer is drawn once the stroke is finished
                double device = Math.abs(screen.getScaleX());
                double factor = device * (penDown ? Math.min(1, liveScale) : 1);
                int iw = Math.max(1, (int) Math.ceil(w * factor)), ih = Math.max(1, (int) Math.ceil(h * factor));
                if (liveImage == null || liveImage.getWidth() != iw || liveImage.getHeight() != ih) {
                    liveImage = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB_PRE);
                }
                Graphics2D lg = liveImage.createGraphics();
                lg.setComposite(AlphaComposite.Clear);
                lg.fillRect(0, 0, iw, ih);
                lg.setComposite(AlphaComposite.SrcOver);
                lg.setRenderingHints(g2.getRenderingHints());
                lg.scale(factor, factor);
                lg.transform(getViewTransform());
                drawLiveShapes(lg);
                lg.dispose();
                g2.setTransform(screen);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(liveImage, 0, 0, w, h, null);
            } else {
                drawLiveShapes(g2);
            }
            // Too slow for a frame - work at a lower resolution for the rest of the stroke
            if (penDown && System.nanoTime() - liveStart > LIVE_FRAME_NANOS && liveScale > 0.2) {
                liveScale = (liveScale >= 1) ? 0.5 : (liveScale > 0.3 ? 0.3 : 0.2);
            }
            g2.setComposite(oldComposite);
            g2.setTransform(screen);
        }

        // Draw the image on top of the current shapes
        if (drawUnder) {
            drawCanvasImage(g2);
        }

        // Draw a red circle when saving a frame
        if (recordIndicator) {
            Ellipse2D.Double recordCircle = new Ellipse2D.Double(5, h - 12, 7, 7);
            g2.setColor(Color.RED);
            g2.fill(recordCircle);
        }

        // Screen only overlays - not part of the cached canvas image
        if (!renderingBuffer) {
            // The shape eraser
            if (eraserLocation != null) {
                Point2D screenPoint = getViewTransform().transform(eraserLocation, null);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Ellipse2D.Double eraserCircle = new Ellipse2D.Double(screenPoint.getX() - ERASER_RADIUS, screenPoint.getY() - ERASER_RADIUS, ERASER_RADIUS * 2, ERASER_RADIUS * 2);
                g2.setStroke(new BasicStroke(3F));
                g2.setColor(new Color(255, 255, 255, 180));
                g2.draw(eraserCircle);
                g2.setStroke(new BasicStroke(1.2F));
                g2.setColor(new Color(220, 50, 70));
                g2.draw(eraserCircle);
            }
            paintSymmetryAxes(g2, w, h);
            paintPolyAndCut(g2);
            if (selecting) {
                selectionTool.paint(g2);
            }
            animateAnts(selecting && selectionTool.needsAnimation());
            paintZoomLabel(g2, h);
            paintMessage(g2, w, h);
            if (exportSelector != null) {
                exportSelector.paint(g2, w, h);
            }
        }

        // Draw the guides as required
        g2.setTransform(view);
        if (guides) {
            for (int i = 0; i < guideShapes.size(); i++) {
                AlcShape currentShape = guideShapes.get(i);
                // LINE
                if (currentShape.style == STYLE_STROKE) {
                    //g2.setStroke(new BasicStroke(currentShape.lineWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL));
                    g2.setStroke(new BasicStroke(currentShape.lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL));
                    g2.setColor(currentShape.color);
                    g2.draw(currentShape.path);
                // SOLID
                } else {
                    g2.setColor(currentShape.color);
                    g2.fill(currentShape.path);
                }
            }
        }

        g2.dispose();

    // Hints that don't seem to offer any extra performance on OSX
    //g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
    //g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
    }

    //////////////////////////////////////////////////////////////
    // CANVAS FUNCTIONALITY
    //////////////////////////////////////////////////////////////
    /** Redraw the canvas */
    public void redraw() {
        redraw(false);
    }

    /** Redraw the canvas
     *  @param fullRedraw   Specify if the full set of vector shapes should be redrawn
     *                  or just add the new shape to the existing buffer image
     */
    public void redraw(boolean fullRedraw) {
        applyAffects();
        if (redraw) {
            if (fullRedraw) {
                // If the window is transparent
                if (Alchemy.window.isTransparent()) {
                    canvasImage = renderScreenImage(true, true);
                } else {
                    canvasImage = renderScreenImage(true);
                }
            }
            this.repaint();
            // Something has happened on the canvas and the user is still active
            canvasChanged = true;
        }
    }

    /** Force the canvas to redraw regardless of the current redraw setting */
    public void forceRedraw() {
        this.setRedraw(true);
        this.redraw(true);
        this.setRedraw(false);
    }

    /** Set the canvas redraw state
     * @param redraw    Redraw state 
     */
    public void setRedraw(boolean redraw) {
        this.redraw = redraw;
    }

    /** Get the canvas redraw state
     * @return  Redraw state on or off
     */
    public boolean isRedraw() {
        return redraw;
    }

    /** Set the draw under state to draw under existing shapes 
     * @param drawUnder
     */
    void setDrawUnder(boolean drawUnder) {
        this.drawUnder = drawUnder;
        updateCanvasImage(true);
    }

    /** Get the draw under state
     * @return 
     */
    boolean getDrawUnder() {
        return this.drawUnder;
    }

    /** Update the canvasImage with transparency if required 
     * 
     * @param transparency
     */
    void updateCanvasImage(boolean transparency) {
        canvasImage = renderScreenImage(true, true);
    }

    /** Set Smoothing (AntiAliasing) on or off
     * @param smoothing     Smoothing on or off
     */
    void setSmoothing(boolean smoothing) {
        this.smoothing = smoothing;
        if (redraw) {
            this.redraw(true);
        // If redraw is off, just update the canvas image
        } else {
            canvasImage = renderScreenImage(true);
        }
    }

    /** Get Antialiasing
     * @return 
     */
    boolean isSmoothing() {
        return this.smoothing;
    }

    /** Return if there has been activity on the canvas since the last time the timer checked */
    boolean canvasChanged() {
        return this.canvasChanged;
    }

    /** Reset the activity flag - called by the timer */
    void resetCanvasChanged() {
        this.canvasChanged = false;
    }
    //////////////////////////////////////////////////////////////
    // PEN EVENTS
    //////////////////////////////////////////////////////////////
    /** Turn on/off mouse/pen events being sent to modules
     * @param events 
     */
    public void setEvents(boolean events) {
        this.events = events;
    }

    /** Turn on/off mouse/pen events  being sent to create modules
     * @param createEvents 
     */
    public void setCreateEvents(boolean createEvents) {
        this.createEvents = createEvents;
    }

    /** Turn on/off mouse/pen events  being sent to affect modules
     * @param affectEvents 
     */
    public void setAffectEvents(boolean affectEvents) {
        this.affectEvents = affectEvents;
    }

    /** Pen (or mouse) down or up
     * @return  The state of the pen or mouse
     */
    public boolean isPenDown() {
        return penDown;
    }

    /** True while the pen has been lifted and the stabilized line is catching up with it -
     *  the line is still being drawn, but affects that react to the pen should keep still */
    public boolean isSettling() {
        return settling;
    }

    /** Pen (or mouse) down or up
     * @return  The state of the pen or mouse
     */
    public boolean isMouseDown() {
        return penDown;
    }

    /** Get the pen pressure (if available)
     * @return
     */
    public float getPenPressure() {
        // With pressure switched off every tool behaves as with a medium pressure
        if (!pressureEnabled) {
            return 0.5F;
        }
        return penPressure;
    }

    /** Pen Tilt  if available 
     * @return  Point2D.Float with tilt information
     */
    public Point2D.Float getPenTilt() {
        return penTilt;
    }

    /** Pen Location as a new Point2D.Float object. <br>
     *  If a pen tablet is available, this method will return more accurate
     *  information on the pen location than the standard {@link MouseEvent}
     * @return  Point2D.Float with pen location information
     */
    public Point2D.Float getPenLocation() {
        // While drawing with the stabilizer on, modules follow the smoothed location
        if (penDown && stabilizedLocation != null) {
            return new Point2D.Float(stabilizedLocation.x, stabilizedLocation.y);
        }
        // The pen location is kept in screen coordinates - modules work in drawing coordinates
        return toWorld(penLocation.x, penLocation.y);
    }

    /** Set the pen location - set internally by mouse events */
    private void setPenLocation(MouseEvent event) {
        // A Windows Ink pen moves through the mouse events, a Wintab pen through JPen
        if (penType == PEN_CURSOR || inkPenActive) {
            penLocationChanged = true;
            penLocation.x = event.getX();
            penLocation.y = event.getY();
        //System.out.println("Mouse: " + penLocation + " " + penLocationChanged);
        }
    }

    /** Set the pen location - set internally by pen events */
    private void setPenLocation(PLevelEvent ev) {
        for (PLevel level : ev.levels) {
            PLevel.Type levelType = level.getType();
            switch (levelType) {
                case X:
                    penLocation.x = level.value;
                    break;
                case Y:
                    penLocation.y = level.value;
                    break;
            }
        }
    }

    /** Has the pen location changed - useful for filtering out repeats
     * @return Boolean indicating if the pen location has changed or not
     */
    public boolean isPenLocationChanged() {
        return penLocationChanged;
    }

    /** The type of pen being used
     * @return  {@link AlcConstants#PEN_STYLUS}, {@link AlcConstants#PEN_ERASER}, {@link AlcConstants#PEN_CURSOR} or ZERO for unknown
     */
    public int getPenType() {
        return penType;
    }

    /** Set the pen type - Default is PEN_CURSOR 
     * @param ev PenEvent from JPen pen tablet library
     */
    private void setPenType() {
        switch (pm.pen.getKind().getType()) {
            case CUSTOM:
                penType = 0;
                break;
            case STYLUS:
                setPenType(PEN_STYLUS);
                break;
            case ERASER:
                setPenType(PEN_ERASER);
                break;
            case CURSOR:
                setPenType(PEN_CURSOR);
        }
    }

    /** Switch the pen type - the stylus and the eraser end each keep their own colour
     * @param newPenType    PEN_STYLUS, PEN_ERASER or PEN_CURSOR
     */
    private void setPenType(int newPenType) {
        switch (newPenType) {
            case PEN_STYLUS:
                currentColorIndex = 0;
                if(!alphaLocked){
                    setAlpha(currentAlphaSet[currentColorIndex]);
                }
                setColor(currentColorSet.get(currentColorIndex));                    
                Alchemy.toolBar.refreshColorButton();
                penType = PEN_STYLUS;
                break;
            case PEN_ERASER:
                currentColorIndex = 1;
                if(!alphaLocked){
                    setAlpha(currentAlphaSet[currentColorIndex]);
                }
                setColor(currentColorSet.get(currentColorIndex));
                Alchemy.toolBar.refreshColorButton();
                penType = PEN_ERASER;
                break;
            case PEN_CURSOR:
                penType = PEN_CURSOR;
        }
    }

    /** Is a Wintab pen (stylus or eraser end) being used right now, according to JPen */
    private boolean wintabPenActive() {
        try {
            PKind.Type kind = pm.pen.getKind().getType();
            return kind == PKind.Type.STYLUS || kind == PKind.Type.ERASER;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Pen diagnostics - written to pen-log.txt when started with -Dalchemy.debugPen=true */
    private static final boolean PEN_DEBUG = Boolean.getBoolean("alchemy.debugPen");
    private static java.io.PrintWriter penLogWriter;
    private static long lastPenLog;

    static synchronized void penLog(String text) {
        if (!PEN_DEBUG) {
            return;
        }
        try {
            if (penLogWriter == null) {
                penLogWriter = new java.io.PrintWriter(new java.io.FileWriter("pen-log.txt", false), true);
            }
            penLogWriter.println(System.currentTimeMillis() % 100000 + " " + text);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    /** Log the pen state at most every 40 ms (always for presses and releases) */
    private void penLogState(String where, boolean always) {
        if (!PEN_DEBUG) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!always && now - lastPenLog < 40) {
            return;
        }
        lastPenLog = now;
        penLog(where + " down=" + penDown + " type=" + penType + " pressureOn=" + pressureEnabled + " size=" + pressureSize + " opacity=" + pressureOpacity
                + " penPressure=" + penPressure + " | ink inRange=" + AlcWindowsInk.isPenInRange() + " seen=" + AlcWindowsInk.isPenSeen()
                + " hasP=" + AlcWindowsInk.hasPressure() + " p=" + AlcWindowsInk.getPressure() + " raw=" + AlcWindowsInk.lastRawPressure
                + " mask=" + AlcWindowsInk.lastMask + " ptrMsgs=" + AlcWindowsInk.pointerMessages + " penMsgs=" + AlcWindowsInk.penMessages
                + " lastPtrType=" + AlcWindowsInk.lastPointerType + " | jpen kind=" + pm.pen.getKind().getType()
                + " p=" + pm.pen.getLevelValue(PLevel.Type.PRESSURE)
                + " | redraw=" + redraw + " createEvents=" + createEvents + " create=" + createShapes.size() + " affect=" + affectShapes.size()
                + " shapes=" + shapes.size() + " layer=" + activeLayer + "/" + layers.size() + " op=" + getActiveLayer().opacity
                + " liveScale=" + liveScale + " viewPending=" + viewPending + " zoom=" + viewScale
                + " module=" + Alchemy.plugins.creates[Alchemy.plugins.currentCreate].getName() + " selecting=" + selecting
                + " | axisX=" + Math.round(axisX * viewScale + viewX) + " axisDrag=" + axisDrag + " nav=" + navigation + " poly=" + polyActive
                + " cutting=" + cutting + " erasing=" + erasing + " blocked=" + blockedStroke + " events=" + events + " pen=" + penLocation
                + " | Q=" + motionHeld + " hold=" + motionHold + " affects=" + activeAffectNames()
                + " focus=" + (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == null ? null
                        : KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner().getClass().getSimpleName()));
    }

    private String activeAffectNames() {
        StringBuilder names = new StringBuilder();
        if (Alchemy.plugins.hasCurrentAffects()) {
            for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                if (Alchemy.plugins.currentAffects[i]) {
                    names.append(Alchemy.plugins.affects[i].getName()).append(',');
                }
            }
        }
        return names.toString();
    }

    /** Take the pen type, pressure and tilt from Windows Ink when such a pen is in use.
     *  Called for every mouse event, which Windows also generates for the pen. */
    private void updateFromWindowsInk() {
        // A Wintab tablet (through JPen) knows its pen better - some drivers also send a few
        // Windows Ink messages with no pressure, which must not take over the pen
        if (wintabPenActive()) {
            if (inkPenActive) {
                inkPenActive = false;
            }
            if (penType == PEN_CURSOR) {
                setPenType();
            }
            return;
        }
        if (AlcWindowsInk.isPenInRange()) {
            int inkType = AlcWindowsInk.isEraser() ? PEN_ERASER : PEN_STYLUS;
            if (penType != inkType) {
                setPenType(inkType);
            }
            if (penDown) {
                // Same parabolic sensitivity as the Wintab pressure below
                float pressure = AlcWindowsInk.hasPressure() ? AlcWindowsInk.getPressure() : 0.5F;
                penPressure = pressure * pressure;
                penTilt.x = AlcWindowsInk.getTiltX();
                penTilt.y = AlcWindowsInk.getTiltY();
            }
            inkPenActive = true;
        } else if (inkPenActive) {
            // The Windows Ink pen has gone - back to the mouse
            inkPenActive = false;
            setPenType(PEN_CURSOR);
        }
    }

    /** Is pen pressure used by the tools */
    public boolean isPressureEnabled() {
        return pressureEnabled;
    }

    /** Switch pen pressure on or off for all tools */
    /** What the pen pressure changes: the size of the brush, or how opaque it is */
    public static final int PRESSURE_SIZE = 0, PRESSURE_OPACITY = 1;
    private boolean pressureSize = true, pressureOpacity = false;

    /** Does the pen pressure change the size of the line */
    public boolean isPressureSize() {
        return pressureSize;
    }

    /** Does the pen pressure change how opaque the line is - faint with a light touch */
    public boolean isPressureOpacity() {
        return pressureOpacity;
    }

    /** Choose what the pressure changes - size, opacity, both or (both off) nothing */
    void setPressureEffects(boolean size, boolean opacity) {
        pressureSize = size;
        pressureOpacity = opacity;
        AlcPreferences.prefs.putBoolean("Pen Pressure Size", size);
        AlcPreferences.prefs.putBoolean("Pen Pressure Opacity", opacity);
        firePressureChanged();
    }

    /** Told when the pressure settings change - the menu and the tool bar switches show them */
    private final java.util.List<Runnable> pressureListeners = new java.util.concurrent.CopyOnWriteArrayList<Runnable>();

    void addPressureListener(Runnable listener) {
        pressureListeners.add(listener);
    }

    private void firePressureChanged() {
        for (Runnable r : pressureListeners) {
            r.run();
        }
    }

    //////////////////////////////////////////////////////////////
    // MOTION EFFECTS - what follows the speed of the hand (liner, brush pen, speed / x / ribbon shapes)
    //////////////////////////////////////////////////////////////
    static final int MOTION_KEY = KeyEvent.VK_Q;
    /** Motion effects only while Q is held - otherwise lines are calm and predictable */
    private boolean motionHold = AlcPreferences.prefs.getBoolean("Motion Hold", true);
    private volatile boolean motionHeld = false;

    /** Should the tools follow the speed of the hand right now */
    public boolean isMotionActive() {
        return !motionHold || motionHeld;
    }

    boolean isMotionHold() {
        return motionHold;
    }

    void setMotionHold(boolean hold) {
        motionHold = hold;
        AlcPreferences.prefs.putBoolean("Motion Hold", hold);
    }

    private javax.swing.Timer motionEditEnd;

    /** A tool changes finished shapes as the pen moves over them (Smooth, Repeat) - all of it
     *  until the pen rests for a moment is one undo step */
    public void beginMotionEdit() {
        if (penDown) {
            return;
        }
        Alchemy.history.beginEdit();
        motionEditOpen = true;
        if (motionEditEnd == null) {
            motionEditEnd = new javax.swing.Timer(700, new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    endMotionEdit();
                }
            });
            motionEditEnd.setRepeats(false);
        }
        motionEditEnd.restart();
    }

    private boolean motionEditOpen;

    private void endMotionEdit() {
        if (motionEditEnd != null) {
            motionEditEnd.stop();
        }
        if (motionEditOpen && !penDown) {
            motionEditOpen = false;
            Alchemy.history.endEdit();
        }
    }

    /** A short note on the canvas when the motion effects start, so it is clear what Q does */
    private void setMotionCursorHint(boolean on) {
        if (!on || !Alchemy.plugins.hasCurrentAffects()) {
            return;
        }
        // Only when a tool that uses it is on
        for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
            String name = Alchemy.plugins.affects[i].getName();
            if (Alchemy.plugins.currentAffects[i] && (name.equals("Smooth") || name.equals("Repeat") || name.equals("Blur"))) {
                showMessage(Alchemy.bundle.getString("motionOnMessage"));
                return;
            }
        }
    }

    /** How strongly the pressure changes the size and the opacity - 1 is the plain response,
     *  0 none, up to 3 very strong. Shared by the brushes and the shapes. */
    private float sizeStrength = AlcPreferences.prefs.getFloat("Pressure Size Strength", 1.6F);
    private float opacityStrength = AlcPreferences.prefs.getFloat("Pressure Opacity Strength", 1.5F);

    public float getPressureSizeStrength() {
        return sizeStrength;
    }

    public float getPressureOpacityStrength() {
        return opacityStrength;
    }

    public void setPressureSizeStrength(float strength) {
        sizeStrength = Math.max(0F, Math.min(3F, strength));
        AlcPreferences.prefs.putFloat("Pressure Size Strength", sizeStrength);
    }

    public void setPressureOpacityStrength(float strength) {
        opacityStrength = Math.max(0F, Math.min(3F, strength));
        AlcPreferences.prefs.putFloat("Pressure Opacity Strength", opacityStrength);
    }

    /** A pen pressure (0 to 1) spread out by the size strength around a medium press of 0.6 -
     *  a strong setting makes light touches thinner and firm presses wider, up to 1.6 */
    public float sizePressure(float pressure) {
        return Math.max(0F, Math.min(1.6F, 0.6F + (pressure - 0.6F) * sizeStrength));
    }

    /** The opacity for a pen pressure (0 to 1) - a strong setting fades light touches much more */
    public float opacityPressure(float pressure) {
        return (float) Math.pow(Math.max(0F, Math.min(1F, pressure)), opacityStrength);
    }

    /** Older single choice - kept for the tests: size only or opacity only */
    public int getPressureMode() {
        return pressureOpacity && !pressureSize ? PRESSURE_OPACITY : PRESSURE_SIZE;
    }

    void setPressureMode(int mode) {
        setPressureEffects(mode == PRESSURE_SIZE, mode == PRESSURE_OPACITY);
    }

    void setPressureEnabled(boolean pressureEnabled) {
        this.pressureEnabled = pressureEnabled;
        AlcPreferences.prefs.putBoolean("Pen Pressure", pressureEnabled);
        firePressureChanged();
    }

    /** Which tablet interface is delivering pen data - for display in the menu */
    String getTabletStatus() {
        if (AlcWindowsInk.isPenSeen()) {
            return "Windows Ink";
        }
        for (PenDevice device : pm.getDevices()) {
            if (device.isDigitizer() && device.getProvider().getConstructor().getName().equals("Wintab")) {
                return "Wintab";
            }
        }
        return null;
    }

    /** Resize the canvas - called when the window is resized
     * @param windowSize    The new window size
     */
    public void resizeCanvas(Dimension windowSize) {
        // Allow for the left hand toolbar if in 'simple' mode
        int x = 0;
        if (Alchemy.preferences.simpleToolBar) {
            x = Alchemy.toolBar.toolBarWidth;
            windowSize.width -= Alchemy.toolBar.toolBarWidth;
        }
        this.setBounds(x, 0, windowSize.width, windowSize.height);
    }

    /** Clear the shapes of every layer (the layers themselves stay, locked ones keep
     *  their shapes) and then redraw the canvas. One undo step. */
    public void clear() {
        Alchemy.history.beginEdit();
        boolean keptLocked = false;
        for (AlcLayer layer : layers) {
            if (layer.locked) {
                keptLocked |= !layer.shapes.isEmpty();
            } else {
                layer.shapes.clear();
            }
        }
        createShapes.clear();
        affectShapes.clear();
        guideShapes.clear();
        if (selectionTool != null) {
            selectionTool.clearSelection();
        }

        forceFullRedraw();
        if (keptLocked) {
            showMessage(Alchemy.bundle.getString("clearLockedKept"));
        }
        // Pass this on to the currently selected modules
        Alchemy.plugins.creates[Alchemy.plugins.currentCreate].cleared();

        if (Alchemy.plugins.hasCurrentAffects()) {
            for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                if (Alchemy.plugins.currentAffects[i]) {
                    Alchemy.plugins.affects[i].cleared();
                }
            }
        }
        if (!penDown) {
            Alchemy.history.endEdit();
        }
    }

    /** Set the cursor temporarily - can be restored with {@link #restoreCursor()}
     * @param cursor    New temp cursor
     */
    public void setTempCursor(Cursor cursor) {
        if (oldCursor == null) {
            oldCursor = this.getCursor();
            this.setCursor(cursor);
        }
    }

    /** Restore the cursor */
    public void restoreCursor() {
        if (oldCursor != null) {
            this.setCursor(oldCursor);
            oldCursor = null;
        }
    }

    /** Apply affects to the current shape and redraw the canvas */
    private void applyAffects() {
        if (Alchemy.plugins.hasCurrentAffects()) {
            for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                if (Alchemy.plugins.currentAffects[i]) {
                    Alchemy.plugins.affects[i].affect();
                }
            }
        }
    }

    /** Commit all shapes to the main {@link #shapes} array and render the image buffer */
    public void commitShapes() {
        // Shapes committed outside of a pen stroke (keyboard, buttons) are an edit of their own
        Alchemy.history.beginEdit();
        // Add the createShapes and affectShapes to the main array
        // Add to the bottom if drawUnder is on
        if (drawUnder) {
            shapes.addAll(0, createShapes);
            shapes.addAll(0, affectShapes);
            // Refresh the canvasImage after the shapes have been added
            // to keep the ordering correct
            createShapes.clear();
            affectShapes.clear();
            canvasImage = renderScreenImage(true, true);

        // Visible layers above, or a see-through layer, need the whole drawing rendered again in order
        } else if (needsLayeredRender()) {
            shapes.addAll(createShapes);
            shapes.addAll(affectShapes);
            createShapes.clear();
            affectShapes.clear();
            canvasImage = renderScreenImage(true);

        // Otherwise add to the top
        } else {
            // If the window is transparent
            if (Alchemy.window.isTransparent()) {
                canvasImage = renderScreenImage(true, true);
            } else {
                canvasImage = renderScreenImage(false);
            }
            shapes.addAll(createShapes);
            shapes.addAll(affectShapes);
            createShapes.clear();
            affectShapes.clear();
        }

        if (DEBUG_COLOR && !shapes.isEmpty()) {
            AlcShape last = shapes.get(drawUnder ? 0 : shapes.size() - 1);
            Color c = last.getColor();
            System.out.println(String.format("[stroke] colour #%02X%02X%02X alpha %d style %d gradient %b", c.getRed(), c.getGreen(), c.getBlue(), last.getAlpha(), last.getStyle(), last.getGradientPaint() != null));
        }
        // Tell the modules the shapes have been commited
        if (Alchemy.plugins.currentCreate >= 0) {
            Alchemy.plugins.creates[Alchemy.plugins.currentCreate].commited();
        }
        if (Alchemy.plugins.hasCurrentAffects()) {
            for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                if (Alchemy.plugins.currentAffects[i]) {
                    Alchemy.plugins.affects[i].commited();
                }
            }
        }
        if (!penDown) {
            Alchemy.history.endEdit();
        }
    }

    /** Get a normalized array of shapes with the top-left corner set to 0,0
     *  and the size set to the given value
     *
     * @param inputShapes   The shapes to normalize
     * @param size          The size to scale the shapes to
     * @return              The normailzed array list of shapes
     */
    public ArrayList<AlcShape> normailzeShapes(ArrayList<AlcShape> inputShapes, int size){
        ArrayList<AlcShape> outputShapes = new ArrayList<AlcShape>(inputShapes.size());

        for (AlcShape shape : inputShapes) {

            // Scale to size
            Rectangle bounds = shape.getBounds();
            // Figure out the longest side
            int longestSize = (bounds.width > bounds.height) ? bounds.width : bounds.height;
            // Create the scaling factor
            double scale = (float) size / longestSize;
            AffineTransform scaleTransform = new AffineTransform();
            scaleTransform.scale(scale, scale);
            GeneralPath gp = (GeneralPath) shape.getPath().createTransformedShape(scaleTransform);
            bounds = gp.getBounds();

            // Reset to 0, 0
            AffineTransform transform = new AffineTransform();
            transform.translate(0 - bounds.x, 0 - bounds.y);
            GeneralPath transformedPath = (GeneralPath) gp.createTransformedShape(transform);
            outputShapes.add(shape.customClone(transformedPath));
        }


        return outputShapes;
    }

    //////////////////////////////////////////////////////////////
    // SHAPES
    //////////////////////////////////////////////////////////////
    /** Check if there are {@link #shapes} available
     * @return True if there are shapes available, else false
     */
    public boolean hasShapes(){
        return shapes.size() > 0;
    }

    /** Returns the most recently added shape
     * @return The current shape
     */
    public AlcShape getCurrentShape() {
        if (shapes.size() > 0) {
            return shapes.get(shapes.size() - 1);
        } else {
            return null;
        }
    }

    /** Sets the most recently added shape
     * @param shape (@link AlcShape} to become the current shape
     */
    public void setCurrentShape(AlcShape shape) {
        if (shapes.size() > 0) {
            shapes.set(shapes.size() - 1, shape);
        }
    }

    /** Removes the most recently added shape */
    public void removeCurrentShape() {
        if (shapes.size() > 0) {
            shapes.remove(shapes.size() - 1);
        }
    }
    /** Removes a shape */
    public void removeShape(int s) {
        if (shapes.size() > 0 && s<shapes.size()) {
            shapes.remove(s);
        }
    }
    
    /** Adds the current color, to the swatch array, sets it active */
    public void addCurrentColorToSwatch(){
        swatch.add(activeSwatchIndex+1, new Color(currentColorSet.get(currentColorIndex).getRed(),
                                                  currentColorSet.get(currentColorIndex).getGreen(),
                                                  currentColorSet.get(currentColorIndex).getBlue(),
                                                  currentAlphaSet[currentColorIndex]));
        activeSwatchIndex+=1;
    }

    //////////////////////////////////////////////////////////////
    // CREATE SHAPES
    //////////////////////////////////////////////////////////////
    /** Check if there are {@link #createShapes} available
     * @return True if there are create shapes available, else false
     */
    public boolean hasCreateShapes(){
        return createShapes.size() > 0;
    }

    /** Returns the most recently added create shape
     * @return The current create shape
     */
    public AlcShape getCurrentCreateShape() {
        if (createShapes.size() > 0) {
            return createShapes.get(createShapes.size() - 1);
        }
        return null;
    }

    /** Sets the most recently added create shape
     * @param shape     (@link AlcShape} to become the current create shape
     */
    public void setCurrentCreateShape(AlcShape shape) {
        if (createShapes.size() > 0) {
            createShapes.set(createShapes.size() - 1, shape);
        } else {
            createShapes.add(shape);
        }
    }

    /** Removes the most recently added create shape */
    public void removeCurrentCreateShape() {
        if (createShapes.size() > 0) {
            createShapes.remove(createShapes.size() - 1);
        }
    }

    /** Commit all create shapes to the main shapes array */
    public void commitCreateShapes() {
        boolean layered = needsLayeredRender();
        if (!layered) {
            canvasImage = renderScreenImage(false);
        }
        for (int i = 0; i < createShapes.size(); i++) {
            shapes.add(createShapes.get(i));
        }
        createShapes.clear();
        if (layered) {
            canvasImage = renderScreenImage(true);
        }
    }

    //////////////////////////////////////////////////////////////
    // AFFECT SHAPES
    //////////////////////////////////////////////////////////////
    /** Check if there are {@link #affectShapes} available
     * @return True if there are affect shapes available, else false
     */
    public boolean hasAffectShapes(){
        return affectShapes.size() > 0;
    }

    /** Returns the most recently added affect shape
     * @return The current create shape
     */
    public AlcShape getCurrentAffectShape() {
        if (affectShapes.size() > 0) {
            return affectShapes.get(affectShapes.size() - 1);
        } else {
            return null;
        }
    }

    /** Sets the most recently added affect shape
     * @param shape     (@link AlcShape} to become the current affect shape
     */
    public void setCurrentAffectShape(AlcShape shape) {
        if (affectShapes.size() > 0) {
            affectShapes.set(affectShapes.size() - 1, shape);
        } else {
            affectShapes.add(shape);
        }
    }

    /** Removes the most recently added affect shape */
    public void removeCurrentAffectShape() {
        if (affectShapes.size() > 0) {
            affectShapes.remove(affectShapes.size() - 1);
        }
    }

    /** Commit all affect shapes to the main shapes array */
    public void commitAffectShapes() {
        boolean layered = needsLayeredRender();
        if (!layered) {
            canvasImage = renderScreenImage(false);
        }

        for (int i = 0; i < affectShapes.size(); i++) {
            shapes.add(affectShapes.get(i));
        }
        affectShapes.clear();
        if (layered) {
            canvasImage = renderScreenImage(true);
        }
    }

    //////////////////////////////////////////////////////////////
    // GUIDE SHAPES
    //////////////////////////////////////////////////////////////
    /** Returns the most recently added guide shape
     * @return The current guide shape
     */
    public AlcShape getCurrentGuideShape() {
        if (guideShapes.size() > 0) {
            return guideShapes.get(guideShapes.size() - 1);
        } else {
            return null;
        }
    }

    /** Sets the most recently added guide shape
     * @param shape     Shape to become the current guide shape
     */
    public void setCurrentGuideShape(AlcShape shape) {
        if (guideShapes.size() > 0) {
            guideShapes.set(guideShapes.size() - 1, shape);
        }
    }

    /** Removes the most recently added guide shape */
    public void removeCurrentGuideShape() {
        if (guideShapes.size() > 0) {
            guideShapes.remove(guideShapes.size() - 1);
        }
    }

    //////////////////////////////////////////////////////////////
    // SHAPE/COLOR SETTINGS
    //////////////////////////////////////////////////////////////
    /** Get the current color
     * @return      The current color
     */
    public Color getColor() {
            return currentColorSet.get(currentColorIndex);
    }

    /** Set the current color
     * @param color
     */
    public void setColor(Color color) {
        // Diagnostics: java -Dalchemy.debugColor=true logs who changes the pen colour
        if (DEBUG_COLOR) {
            StackTraceElement[] stack = new Throwable().getStackTrace();
            StringBuilder from = new StringBuilder();
            for (int i = 1; i < Math.min(stack.length, 7); i++) {
                from.append(" < ").append(stack[i].getClassName().replace("org.alchemy.", "")).append('.').append(stack[i].getMethodName()).append(':').append(stack[i].getLineNumber());
            }
            System.out.println(String.format("[colour] %tT.%<tL #%02X%02X%02X index %d pen %d%s", System.currentTimeMillis(),
                    color.getRed(), color.getGreen(), color.getBlue(), currentColorIndex, penType, from));
        }
        try {
            previousColor = currentColorSet.get(currentColorIndex);
            currentColorSet.set(currentColorIndex, new Color(color.getRed(), color.getGreen(), color.getBlue()));
            if(!alphaLocked){
                currentAlphaSet[currentColorIndex] = alpha;
            }

            if (Alchemy.preferences.paletteAttached || Alchemy.preferences.simpleToolBar) {
                Alchemy.toolBar.refreshColorButton();
            }
        } catch (IllegalArgumentException ex) {
            // Ignore the color out of range exception caused by out of bounds slider settings
        }
    }
    
    // relay method to allow modules access to the toolbar color button
    public void refreshColorButtonRelay(){
        Alchemy.toolBar.refreshColorButton();
    }

    /** Get the background color
     * @return Color object of the background color
     */
    public Color getBackgroundColor() {
        return bgColor;
    }

    /** Set the background Color
     * @param color
     */
    public void setBackgroundColor(Color color) {
        Alchemy.history.beginEdit();
        this.bgColor = new Color(color.getRed(), color.getGreen(), color.getBlue());
        redraw(true);
        Alchemy.history.endEdit();
    }

    /** Set the background color without recording an undo step - used by the history */
    void restoreBackgroundColor(Color color) {
        this.bgColor = color;
    }

    /** Re-render every shape even if redrawing is switched off */
    void forceFullRedraw() {
        if (redraw) {
            redraw(true);
        } else {
            forceRedraw();
        }
    }

    /** Get the current alpha value
     * @return 
     */
    public int getAlpha() {
            return alpha;
    }

    /** Set the current alpha value
     * @param alpha 
     */
    public void setAlpha(int alpha) {
            this.alpha = alpha;
            setColor(this.getColor());
            Alchemy.toolBar.refreshTransparencySlider();
    }

    /** Returns the state of alpha lock */
    public boolean isAlphaLocked(){
        return alphaLocked;
    }
    
    /** Toggles the state of alpha lock */
    public void toggleAlphaLocked(){
        if(alphaLocked){
            alphaLocked=false;
            setAlpha(currentAlphaSet[currentColorIndex]);
        }else{
            alphaLocked=true;
        }
    }
    
    /** Get the current style
     * @return 
     */
    public int getStyle() {
        return style;
    }

    /** Set the current style
     * @param style 
     */
    public void setStyle(int style) {
        this.style = style;
    }

    /** Toggle the style between line and solid */
    public void toggleStyle() {
        setStyleMode(getStyleMode() == STYLE_MODE_STROKE ? STYLE_MODE_FILL : STYLE_MODE_STROKE);
    }

    /** The styles offered in the Style menu, in order */
    public static final int STYLE_MODE_STROKE = 0, STYLE_MODE_FILL = 1, STYLE_MODE_BOTH = 2,
            STYLE_MODE_DASHED = 3, STYLE_MODE_DOTTED = 4;

    /** Pick one of the Style menu styles - sets the shape style and the line pattern */
    public void setStyleMode(int mode) {
        switch (mode) {
            case STYLE_MODE_FILL:
                style = STYLE_FILL;
                dash = DASH_NONE;
                break;
            case STYLE_MODE_BOTH:
                style = STYLE_BOTH;
                dash = DASH_NONE;
                break;
            case STYLE_MODE_DASHED:
                style = STYLE_STROKE;
                dash = DASH_DASHED;
                break;
            case STYLE_MODE_DOTTED:
                style = STYLE_STROKE;
                dash = DASH_DOTTED;
                break;
            default:
                style = STYLE_STROKE;
                dash = DASH_NONE;
                break;
        }
    }

    public int getStyleMode() {
        if (style == STYLE_FILL) {
            return STYLE_MODE_FILL;
        } else if (style == STYLE_BOTH) {
            return STYLE_MODE_BOTH;
        } else if (dash == DASH_DASHED) {
            return STYLE_MODE_DASHED;
        } else if (dash == DASH_DOTTED) {
            return STYLE_MODE_DOTTED;
        }
        return STYLE_MODE_STROKE;
    }

    /** The current line pattern for new shapes */
    public int getDash() {
        return dash;
    }

    public void setDash(int dash) {
        this.dash = dash;
    }

    /** Get the current line width
     * @return 
     */
    public float getLineWidth() {
        return lineWidth;
    }

    /** Set the current line width
     * @param lineWidth 
     */
    public void setLineWidth(float lineWidth) {
        this.lineWidth = lineWidth;
    }

    //////////////////////////////////////////////////////////////
    // DISPLAY
    //////////////////////////////////////////////////////////////
    boolean isGuideEnabled() {
        return guides;
    }

    void setGuide(boolean guides) {
        this.guides = guides;
    }

    //////////////////////////////////////////////////////////////
    // SKETCHING AIDS
    //////////////////////////////////////////////////////////////
    /** Get the stabilizer strength, 0 when switched off */
    float getStabilizer() {
        return stabilizer;
    }

    /** Set the stabilizer strength
     * @param stabilizer    0 (off) to below 1 - higher values give smoother, slower lines
     */
    void setStabilizer(float stabilizer) {
        this.stabilizer = Math.max(0F, Math.min(0.95F, stabilizer));
        AlcPreferences.prefs.putFloat("Stabilizer", this.stabilizer);
    }

    /** Get the paper pattern - one of the PAPER constants */
    int getPaper() {
        return paper;
    }

    /** Set the paper pattern drawn behind the shapes - one of the PAPER constants */
    void setPaper(int paper) {
        this.paper = paper;
        AlcPreferences.prefs.putInt("Paper", paper);
        forceFullRedraw();
    }

    /** Draw the paper pattern with a colour that suits the background */
    void paintPaper(Graphics2D g2, int w, int h) {
        if (paper == PAPER_BLANK) {
            return;
        }
        boolean lightBackground = (bgColor.getRed() + bgColor.getGreen() + bgColor.getBlue()) / 3 > 128;
        Color ink = lightBackground ? new Color(40, 90, 160, 38) : new Color(255, 255, 255, 30);
        AffineTransform oldTransform = g2.getTransform();
        Object oldAntialias = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        // The paper moves and scales with the drawing - lines are snapped to whole screen pixels
        Point2D.Float topLeft = toWorld(0, 0);
        Point2D.Float bottomRight = toWorld(w, h);
        // Spread the pattern out when zoomed far out so it stays readable
        double spacing = (paper == PAPER_DOTS) ? 24 : 32;
        while (spacing * viewScale < 8) {
            spacing *= 2;
        }
        g2.setColor(ink);
        g2.setStroke(new BasicStroke(1F));
        double startX = Math.floor(topLeft.x / spacing) * spacing;
        double startY = Math.floor(topLeft.y / spacing) * spacing;
        switch (paper) {
            case PAPER_GRID:
                for (double x = startX; x < bottomRight.x; x += spacing) {
                    int sx = screenX(x);
                    g2.drawLine(sx, 0, sx, h);
                }
                for (double y = startY; y < bottomRight.y; y += spacing) {
                    int sy = screenY(y);
                    g2.drawLine(0, sy, w, sy);
                }
                break;
            case PAPER_DOTS:
                g2.setColor(lightBackground ? new Color(40, 60, 90, 70) : new Color(255, 255, 255, 60));
                for (double x = startX; x < bottomRight.x; x += spacing) {
                    for (double y = startY; y < bottomRight.y; y += spacing) {
                        g2.fillRect(screenX(x) - 1, screenY(y) - 1, 2, 2);
                    }
                }
                break;
            case PAPER_LINES:
                for (double y = startY + 8; y < bottomRight.y; y += spacing) {
                    int sy = screenY(y);
                    g2.drawLine(0, sy, w, sy);
                }
                g2.setColor(lightBackground ? new Color(220, 60, 60, 60) : new Color(255, 120, 120, 50));
                g2.drawLine(screenX(64), 0, screenX(64), h);
                break;
        }
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialias);
        g2.setTransform(oldTransform);
    }

    /** Drawing x coordinate to the nearest screen pixel */
    private int screenX(double x) {
        return (int) Math.round(x * viewScale + viewX);
    }

    /** Drawing y coordinate to the nearest screen pixel */
    private int screenY(double y) {
        return (int) Math.round(y * viewScale + viewY);
    }

    /** Move the stabilized pen location towards the real one */
    private Point2D.Float stabilize(Point2D.Float raw) {
        if (stabilizedLocation == null) {
            stabilizedLocation = new Point2D.Float(raw.x, raw.y);
        } else {
            float follow = 1F - stabilizer;
            stabilizedLocation.x += (raw.x - stabilizedLocation.x) * follow;
            stabilizedLocation.y += (raw.y - stabilizedLocation.y) * follow;
        }
        return stabilizedLocation;
    }

    /** Copy a mouse event to a new location */
    private MouseEvent moveEvent(MouseEvent event, Point2D.Float location) {
        return new MouseEvent(this, event.getID(), event.getWhen(), event.getModifiersEx(),
                Math.round(location.x), Math.round(location.y), event.getClickCount(), event.isPopupTrigger(), event.getButton());
    }

    /** Erase every committed shape touched along the line from the last eraser location to p
     * @param p     Location in drawing coordinates
     */
    private void eraseTo(Point2D.Float p) {
        Point2D.Float from = (eraserLocation == null) ? p : eraserLocation;
        // The eraser keeps the same size on screen at any zoom
        double radius = ERASER_RADIUS / viewScale;
        double distance = from.distance(p);
        int steps = Math.max(1, (int) Math.ceil(distance / radius));
        boolean removed = false;
        for (int s = 1; s <= steps; s++) {
            double x = from.x + (p.x - from.x) * s / (double) steps;
            double y = from.y + (p.y - from.y) * s / (double) steps;
            Rectangle2D.Double area = new Rectangle2D.Double(x - radius, y - radius, radius * 2, radius * 2);
            for (int i = shapes.size() - 1; i >= 0; i--) {
                AlcShape shape = shapes.get(i);
                Rectangle2D bounds = shape.path.getBounds2D();
                double grow = shape.lineWidth / 2 + 1;
                if (!area.intersects(bounds.getX() - grow, bounds.getY() - grow, bounds.getWidth() + grow * 2, bounds.getHeight() + grow * 2)) {
                    continue;
                }
                java.awt.Shape outline = (!shape.isFilled())
                        ? new BasicStroke(Math.max(1F, shape.lineWidth)).createStrokedShape(shape.path)
                        : shape.path;
                if (outline.intersects(area)) {
                    shapes.remove(i);
                    removed = true;
                }
            }
        }
        eraserLocation = p;
        if (removed) {
            redraw(true);
        } else {
            repaint();
        }
    }

    /** Replace the drawing with the contents of a sketch file - can be undone */
    void loadSketch(AlcSketchIO.Sketch sketch) {
        Alchemy.history.beginEdit();
        layerCounter = sketch.layers.size();
        replaceLayers(sketch.layers, sketch.activeLayer);
        bgColor = sketch.background;
        forceFullRedraw();
        Alchemy.history.endEdit();
    }

    /** Returns if the record indicator (used with session auto-saving) is enabled 
     * @return
     */
    public boolean isRecordIndicatorEnabled() {
        return recordIndicator;
    }

    /** Set the display of the record indicator (used with session auto-saving)
     * @param recordIndicator   On or off
     */
    public void setRecordIndicator(boolean recordIndicator) {
        this.recordIndicator = recordIndicator;
    }

    /** Manage the automatic toggling of the toolbar */
    void setAutoToggleToolBar(boolean manageToolBar) {
        if (!Alchemy.preferences.paletteAttached) {
            this.autoToggleToolBar = manageToolBar;
        }
    }

    /** If the toolbar is being automatically toggled on/off or not */
    boolean isAutoToggleToolBar() {
        if (Alchemy.preferences.paletteAttached) {
            return false;
        } else {
            return this.autoToggleToolBar;
        }
    }

    //////////////////////////////////////////////////////////////
    // TRANSFORM
    //////////////////////////////////////////////////////////////
    /** Flip the shapes on the canvas horizontally */
    void flipHorizontal(){
        AffineTransform horizontalReflection = new AffineTransform();
        // Move the reflection into place
        horizontalReflection.translate(this.getWidth(), 0);
        // Reflect it using a negative scale
        horizontalReflection.scale(-1, 1);
        Alchemy.history.beginEdit();
        // Apply to every shape
        for (AlcLayer layer : layers) for (AlcShape shape : layer.shapes) {
            GeneralPath reflectedPath = (GeneralPath) shape.getPath().createTransformedShape(horizontalReflection);
            shape.setPath(reflectedPath);
            shape.setGradientPaint(makeHorizontalReflectedGradientPaint(shape.getGradientPaint()));
        }
        redraw(true);
        Alchemy.history.endEdit();
    }
    
    /** Make a GradientPaint reflected through the horizontal axis */
    private GradientPaint makeHorizontalReflectedGradientPaint(GradientPaint gp) {
        if(gp == null){
            return null;
        }
        float horizontalAxis = this.getWidth() / 2;
        float x1 = horizontalAxis - ((float) gp.getPoint1().getX() - horizontalAxis);
        float x2 = horizontalAxis - ((float) gp.getPoint2().getX() - horizontalAxis);
        GradientPaint newGp = new GradientPaint(
                x1,
                (float) gp.getPoint1().getY(),
                gp.getColor1(),
                x2,
                (float) gp.getPoint2().getY(),
                gp.getColor2());
        return newGp;
    }


    /** Flip the shapes on the canvas Vertically */
    void flipVertical(){
        AffineTransform verticalReflection = new AffineTransform();
        // Move the reflection into place
        verticalReflection.translate(0, this.getHeight());
        // Reflect it using a negative scale
        verticalReflection.scale(1, -1);
        Alchemy.history.beginEdit();
        // Apply to every shape
        for (AlcLayer layer : layers) for (AlcShape shape : layer.shapes) {
            GeneralPath reflectedPath = (GeneralPath) shape.getPath().createTransformedShape(verticalReflection);
            shape.setPath(reflectedPath);
            shape.setGradientPaint(makeVerticalReflectedGradientPaint(shape.getGradientPaint()));
        }
        redraw(true);
        Alchemy.history.endEdit();
    }

    /** Make a GradientPaint reflected through the vertical axis */
    private GradientPaint makeVerticalReflectedGradientPaint(GradientPaint gp) {
        if(gp == null){
            return null;
        }
        float verticalAxis = this.getHeight() / 2;
        float y1 = verticalAxis - ((float) gp.getPoint1().getY() - verticalAxis);
        float y2 = verticalAxis - ((float) gp.getPoint2().getY() - verticalAxis);
        GradientPaint newGp = new GradientPaint(
                (float) gp.getPoint1().getX(),
                y1,
                gp.getColor1(),
                (float) gp.getPoint2().getX(),
                y2,
                gp.getColor2());
        return newGp;
    }
    
    //////////////////////////////////////////////////////////////
    // VIEW - ZOOM AND PAN
    //////////////////////////////////////////////////////////////
    // The view only changes how the drawing is shown on screen.
    // Shapes keep their coordinates, so exports, printing and undo are not affected.

    /** The transform from drawing coordinates to the screen */
    AffineTransform getViewTransform() {
        AffineTransform view = AffineTransform.getTranslateInstance(viewX, viewY);
        view.scale(viewScale, viewScale);
        return view;
    }

    /** The current zoom - 1 is 100% */
    public double getViewScale() {
        return viewScale;
    }

    /** Convert a point on the screen to drawing coordinates */
    Point2D.Float toWorld(double x, double y) {
        return new Point2D.Float((float) ((x - viewX) / viewScale), (float) ((y - viewY) / viewScale));
    }

    /** Copy a mouse event into drawing coordinates for the modules */
    private MouseEvent toWorldEvent(MouseEvent event) {
        if (viewScale == 1 && viewX == 0 && viewY == 0) {
            return event;
        }
        return moveEvent(event, toWorld(event.getX(), event.getY()));
    }

    /** Zoom by a factor, keeping the drawing under the given screen point in place */
    void zoomAt(double factor, double screenX, double screenY) {
        double newScale = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, viewScale * factor));
        // Snap to 100% when passing close by it
        if (Math.abs(newScale - 1) < 0.04) {
            newScale = 1;
        }
        double ratio = newScale / viewScale;
        setView(newScale, screenX - (screenX - viewX) * ratio, screenY - (screenY - viewY) * ratio);
    }

    /** Move the view by a distance in screen pixels */
    void panBy(double dx, double dy) {
        setView(viewScale, viewX + dx, viewY + dy);
    }

    /** Keep the canvas moving after a flick, slowing down like on a real table */
    private void startGlide() {
        if (Math.hypot(panVelocityX, panVelocityY) < 0.3) {
            return;
        }
        stopGlide();
        final long[] last = {System.currentTimeMillis()};
        glideTimer = new javax.swing.Timer(15, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                long now = System.currentTimeMillis();
                long dt = Math.max(1, now - last[0]);
                last[0] = now;
                panBy(panVelocityX * dt, panVelocityY * dt);
                double friction = Math.pow(0.9, dt / 16.0);
                panVelocityX *= friction;
                panVelocityY *= friction;
                if (Math.hypot(panVelocityX, panVelocityY) < 0.02) {
                    stopGlide();
                }
            }
        });
        glideTimer.start();
    }

    private void stopGlide() {
        if (glideTimer != null) {
            glideTimer.stop();
            glideTimer = null;
        }
    }

    /** Show a navigation cursor, remembering the drawing cursor to restore later
     * @param type  A java.awt.Cursor type, or -1 for the zoom cursor
     */
    private void setHandCursor(int type) {
        if (oldCursor == null) {
            oldCursor = this.getCursor();
        }
        this.setCursor(type == -1 ? CURSOR_ZOOM : Cursor.getPredefinedCursor(type));
    }

    //////////////////////////////////////////////////////////////
    // LAYERS
    //////////////////////////////////////////////////////////////
    // Every change to the layers is an undo step of its own.

    /** The layers, bottom first - read only, change them with the methods below */
    java.util.List<AlcLayer> getLayers() {
        return java.util.Collections.unmodifiableList(layers);
    }

    int getActiveLayerIndex() {
        return activeLayer;
    }

    AlcLayer getActiveLayer() {
        return layers.get(activeLayer);
    }

    /** Make a layer the one being drawn into */
    void setActiveLayer(int index) {
        activeLayer = Math.max(0, Math.min(layers.size() - 1, index));
        // A selection belongs to one layer - keep it when undo puts the same layer back
        if (selectionTool != null && shapes != layers.get(activeLayer).shapes) {
            selectionTool.clearSelection();
        }
        shapes = layers.get(activeLayer).shapes;
        fullShapeList[0] = shapes;
        AlcLayersPanel.refreshPanel();
    }

    /** Can the pen draw into the active layer - not when it is hidden or locked */
    boolean canDrawOnActiveLayer() {
        AlcLayer layer = getActiveLayer();
        return layer.visible && !layer.locked;
    }

    /** True when any layer has shapes */
    boolean hasAnyShapes() {
        for (AlcLayer layer : layers) {
            if (!layer.shapes.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** A new committed shape can only be added on top of the cached image when nothing covers the active layer */
    private boolean needsLayeredRender() {
        if (getActiveLayer().opacity < 1F) {
            return true;
        }
        for (int i = activeLayer + 1; i < layers.size(); i++) {
            AlcLayer layer = layers.get(i);
            if (layer.visible && !layer.shapes.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String newLayerName() {
        return Alchemy.bundle.getString("layerName") + " " + (++layerCounter);
    }

    /** Run a change to the layers as one undo step and redraw */
    private void layerEdit(Runnable change) {
        Alchemy.history.beginEdit();
        change.run();
        forceFullRedraw();
        Alchemy.history.endEdit();
        AlcLayersPanel.refreshPanel();
    }

    /** Add an empty layer above the active one and draw into it */
    void addLayer() {
        layerEdit(new Runnable() {

            public void run() {
                layers.add(activeLayer + 1, new AlcLayer(newLayerName()));
                setActiveLayer(activeLayer + 1);
            }
        });
    }

    /** Copy the active layer above itself */
    void duplicateLayer() {
        layerEdit(new Runnable() {

            public void run() {
                AlcLayer active = getActiveLayer();
                layers.add(activeLayer + 1, active.duplicate(active.name + " " + Alchemy.bundle.getString("layerCopy")));
                setActiveLayer(activeLayer + 1);
            }
        });
    }

    /** Remove the active layer - there is always at least one layer */
    void deleteLayer() {
        layerEdit(new Runnable() {

            public void run() {
                if (layers.size() == 1) {
                    shapes.clear();
                    return;
                }
                layers.remove(activeLayer);
                setActiveLayer(Math.max(0, activeLayer - 1));
            }
        });
    }

    /** Move the active layer up (+1) or down (-1) */
    void moveLayer(final int direction) {
        final int target = activeLayer + direction;
        if (target < 0 || target >= layers.size()) {
            return;
        }
        layerEdit(new Runnable() {

            public void run() {
                AlcLayer layer = layers.remove(activeLayer);
                layers.add(target, layer);
                setActiveLayer(target);
            }
        });
    }

    /** Move a layer to a new position in the list (0 is the bottom) and select it */
    void moveLayerTo(final int from, final int to) {
        if (from == to || from < 0 || to < 0 || from >= layers.size() || to >= layers.size()) {
            return;
        }
        layerEdit(new Runnable() {

            public void run() {
                AlcLayer layer = layers.remove(from);
                layers.add(to, layer);
                setActiveLayer(to);
            }
        });
    }

    void setLayerVisible(final AlcLayer layer, final boolean visible) {
        layerEdit(new Runnable() {

            public void run() {
                layer.visible = visible;
            }
        });
    }

    void setLayerLocked(final AlcLayer layer, final boolean locked) {
        layerEdit(new Runnable() {

            public void run() {
                layer.locked = locked;
            }
        });
    }

    /** Change the opacity of a layer - while dragging a slider pass finished = false and true at the end,
     *  so the whole drag is one undo step */
    void setLayerOpacity(AlcLayer layer, float opacity, boolean finished) {
        Alchemy.history.beginEdit();
        layer.opacity = Math.max(0F, Math.min(1F, opacity));
        forceFullRedraw();
        if (finished) {
            Alchemy.history.endEdit();
        }
    }

    void renameLayer(final AlcLayer layer, final String name) {
        if (name == null || name.trim().isEmpty() || name.equals(layer.name)) {
            return;
        }
        layerEdit(new Runnable() {

            public void run() {
                layer.name = name.trim();
            }
        });
    }

    /** Put one shape list per layer back in place - used by undo and opening sketches */
    void replaceLayers(java.util.List<AlcLayer> newLayers, int active) {
        layers.clear();
        layers.addAll(newLayers);
        if (layers.isEmpty()) {
            layers.add(new AlcLayer(newLayerName()));
        }
        createShapes.clear();
        affectShapes.clear();
        setActiveLayer(active);
    }

    /** Start a new drawing - one empty layer. Can be undone. */
    void newDrawing() {
        layerEdit(new Runnable() {

            public void run() {
                layerCounter = 0;
                java.util.List<AlcLayer> fresh = new ArrayList<AlcLayer>();
                fresh.add(new AlcLayer(newLayerName()));
                replaceLayers(fresh, 0);
                guideShapes.clear();
            }
        });
        Alchemy.plugins.creates[Alchemy.plugins.currentCreate].cleared();
    }

    /** Set the part of the drawing that exports render - null for the original window area */
    void setExportArea(Rectangle2D exportArea) {
        this.exportArea = exportArea;
    }

    /** Let the user choose the part of the drawing to export, then run the export.
     *  The chosen area is set with {@link #setExportArea} while the export runs. */
    void startExportSelection(final Runnable export) {
        if (exportSelector != null) {
            return;
        }
        Alchemy.toolBar.setToolBarVisible(false);
        // Start with the last area, else the whole drawing, else what is on screen
        Rectangle2D initial = lastExportArea;
        if (initial == null) {
            Rectangle2D drawing = getDrawingBounds();
            initial = (drawing == null) ? getVisibleDrawingArea()
                    : new Rectangle2D.Double(drawing.getX() - 20, drawing.getY() - 20, drawing.getWidth() + 40, drawing.getHeight() + 40);
        }
        exportSelector = new AlcExportSelector(this, initial, new Runnable() {

            public void run() {
                lastExportArea = exportSelector.getArea();
                setExportArea(lastExportArea);
                try {
                    export.run();
                } finally {
                    setExportArea(null);
                }
            }
        });
        setTempCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    /** Called by the selector when it closes */
    void endExportSelection() {
        // Keep the selector until the export has read the area
        final AlcExportSelector closing = exportSelector;
        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                if (exportSelector == closing) {
                    exportSelector = null;
                }
                restoreCursor();
                repaint();
            }
        });
    }

    /** Is the export area being chosen */
    boolean isSelectingExportArea() {
        return exportSelector != null;
    }

    /** The part of the drawing exports render - null for the original window area */
    Rectangle2D getExportArea() {
        return exportArea;
    }

    /** Size of an export in pixels at 100% */
    java.awt.Rectangle getExportBounds() {
        if (exportArea == null) {
            return getVisibleRect();
        }
        return new java.awt.Rectangle(0, 0, (int) Math.ceil(exportArea.getWidth()), (int) Math.ceil(exportArea.getHeight()));
    }

    /** Bounds of everything drawn, including line widths - null when nothing is drawn */
    Rectangle2D getDrawingBounds() {
        Rectangle2D bounds = null;
        for (AlcLayer layer : layers) for (AlcShape shape : layer.shapes) {
            if (!layer.visible) {
                continue;
            }
            Rectangle2D b = shape.path.getBounds2D();
            double grow = shape.lineWidth / 2;
            b = new Rectangle2D.Double(b.getX() - grow, b.getY() - grow, b.getWidth() + grow * 2, b.getHeight() + grow * 2);
            bounds = (bounds == null) ? b : bounds.createUnion(b);
        }
        return bounds;
    }

    /** The part of the drawing currently visible in the window */
    public Rectangle2D getVisibleDrawingArea() {
        Point2D.Float topLeft = toWorld(0, 0);
        Point2D.Float bottomRight = toWorld(getWidth(), getHeight());
        return new Rectangle2D.Double(topLeft.x, topLeft.y, bottomRight.x - topLeft.x, bottomRight.y - topLeft.y);
    }

    /** Show the whole drawing in the middle of the window - never zooming in past 100%.
     *  With nothing drawn the view goes back to 100%. */
    void centerView() {
        Rectangle2D bounds = getDrawingBounds();
        if (bounds == null) {
            resetView();
            return;
        }
        double margin = 40;
        double availableWidth = Math.max(1, getWidth() - margin * 2);
        double availableHeight = Math.max(1, getHeight() - margin * 2);
        double scale = Math.min(1, Math.min(availableWidth / Math.max(1, bounds.getWidth()), availableHeight / Math.max(1, bounds.getHeight())));
        scale = Math.max(MIN_ZOOM, scale);
        stopGlide();
        setView(scale, getWidth() / 2.0 - bounds.getCenterX() * scale, getHeight() / 2.0 - bounds.getCenterY() * scale);
    }

    /** Back to 100% with the drawing in its original place */
    void resetView() {
        setView(1, 0, 0);
    }

    private void setView(double scale, double x, double y) {
        viewScale = scale;
        viewX = x;
        viewY = y;
        if (canvasImage != null && canvasImageView != null && lastFullRenderNanos > VIEW_FAST_NANOS && !penDown) {
            // A heavy drawing - move / scale the finished picture while the view is moving
            // and draw it again sharply once the view stops for a moment
            viewPending = true;
            if (viewSettle == null) {
                viewSettle = new javax.swing.Timer(VIEW_SETTLE_MS, new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        settleView();
                    }
                });
                viewSettle.setRepeats(false);
            }
            viewSettle.restart();
            repaint();
        } else {
            forceFullRedraw();
        }
        Alchemy.toolBar.refreshZoomButton();
    }

    private javax.swing.Timer antsTimer;

    /** Keep the marching ants of a selection moving - only while a selection is shown */
    private void animateAnts(boolean on) {
        if (on && antsTimer == null) {
            antsTimer = new javax.swing.Timer(120, new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    if (!penDown) {
                        repaint();
                    }
                }
            });
            antsTimer.start();
        } else if (!on && antsTimer != null) {
            antsTimer.stop();
            antsTimer = null;
        }
    }

    /** Draw the picture sharply again for the current view */
    private void settleView() {
        if (viewSettle != null) {
            viewSettle.stop();
        }
        if (viewPending) {
            viewPending = false;
            forceFullRedraw();
        }
    }

    /** Zoom shortcut key - switch between 100% and 400% at the mouse */
    public boolean zoomCanvas(boolean keyLaunch) {
        if (isCanvasZoomed()) {
            resetView();
        } else {
            Point mouse = this.getMousePosition();
            if (mouse == null) {
                mouse = new Point(getWidth() / 2, getHeight() / 2);
            }
            zoomAt(4, mouse.x, mouse.y);
        }
        return true;
    }

    /** Zoom toolbar button - zoom in 2x on the centre, or back to 100% */
    public void startZoomMousing() {
        if (isCanvasZoomed()) {
            resetView();
        } else {
            zoomAt(2, getWidth() / 2, getHeight() / 2);
        }
    }

    public void stopZoomMousing() {
    }

    public boolean isCanvasZoomed() {
        return viewScale != 1 || viewX != 0 || viewY != 0;
    }

    /** Drawing coordinates are not changed by the view - kept for modules using the old zoom */
    public Point2D.Double getZoomLocation() {
        return new Point2D.Double(0, 0);
    }

    /** Drawing coordinates are not changed by the view - kept for modules using the old zoom */
    public double calculateZoomedX(double c) {
        return c;
    }

    /** Drawing coordinates are not changed by the view - kept for modules using the old zoom */
    public double calculateZoomedY(double c) {
        return c;
    }

    //////////////////////////////////////////////////////////////
    // SELECTION
    //////////////////////////////////////////////////////////////
    /** Is the lasso selection tool on */
    boolean isSelecting() {
        return selecting;
    }

    /** Switch the lasso selection tool on or off */
    void setSelecting(boolean on) {
        if (on == selecting) {
            return;
        }
        selecting = on;
        if (on) {
            setTempCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        } else {
            selectionTool.clearSelection();
            restoreCursor();
        }
        Alchemy.toolBar.refreshSelectButton();
        repaint();
    }

    AlcSelectionTool getSelectionTool() {
        return selectionTool;
    }

    /** Select every shape of the active layer */
    void selectAll() {
        setSelecting(true);
        selectionTool.selectAll();
    }

    //////////////////////////////////////////////////////////////
    // SYMMETRY AXES
    //////////////////////////////////////////////////////////////
    /** Told when the user drags a symmetry axis on the canvas */
    public interface AxisListener {

        /** The axes moved - in drawing coordinates */
        void axesMoved(double x, double y);

        /** The user let go of the axes */
        void axesDropped();
    }

    /** Show draggable lines of symmetry over the canvas - used by modules such as Mirror
     * @param showX     Show the vertical line at x (mirroring left / right)
     * @param x         Its position in drawing coordinates
     * @param showY     Show the horizontal line at y (mirroring top / bottom)
     * @param y         Its position in drawing coordinates
     * @param listener  Told when the user drags the lines
     */
    public void setSymmetryAxes(boolean showX, double x, boolean showY, double y, AxisListener listener) {
        this.showAxisX = showX;
        this.axisX = x;
        this.showAxisY = showY;
        this.axisY = y;
        this.axisListener = listener;
        repaint();
    }

    /** Remove the lines of symmetry */
    public void clearSymmetryAxes() {
        axisListener = null;
        showAxisX = false;
        showAxisY = false;
        axisDrag = AXIS_NONE;
        repaint();
    }

    /** Which axis is under a screen point - for dragging it */
    private int axisAt(Point p) {
        if (axisListener == null) {
            return AXIS_NONE;
        }
        // Only the handles grab an axis - drawing across a line never moves it by accident
        Point[] handles = axisHandles();
        final double reach = 10;
        if (handles[0] != null && handles[0].distance(p) <= reach) {
            return AXIS_X;
        }
        if (handles[1] != null && handles[1].distance(p) <= reach) {
            return AXIS_Y;
        }
        return AXIS_NONE;
    }

    /** Where the axis handles are on the screen - at the edges, away from the drawing:
     *  the vertical line's at the bottom (the top belongs to the toolbar), the horizontal line's at the left */
    private Point[] axisHandles() {
        int h = getVisibleRect().height;
        int sx = (int) Math.round(axisX * viewScale + viewX);
        int sy = (int) Math.round(axisY * viewScale + viewY);
        Point x = showAxisX ? new Point(sx, h - 18) : null;
        Point y = showAxisY ? new Point(18, sy) : null;
        return new Point[]{x, y};
    }

    private void dragAxis(MouseEvent event) {
        Point2D.Float p = toWorld(event.getX(), event.getY());
        if (axisDrag == AXIS_X || axisDrag == AXIS_BOTH) {
            axisX = p.x;
        }
        if (axisDrag == AXIS_Y || axisDrag == AXIS_BOTH) {
            axisY = p.y;
        }
        axisListener.axesMoved(axisX, axisY);
        repaint();
    }

    /** Dashed lines across the window where the drawing is mirrored */
    private void paintSymmetryAxes(Graphics2D g2, int w, int h) {
        if (axisListener == null || (!showAxisX && !showAxisY)) {
            return;
        }
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        boolean dragging = axisDrag != AXIS_NONE;
        int sx = (int) Math.round(axisX * viewScale + viewX);
        int sy = (int) Math.round(axisY * viewScale + viewY);
        // A quiet line - white with long dark dashes, so it reads on any paper without shouting
        Stroke under = new BasicStroke(1F);
        Stroke dash = new BasicStroke(1F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10F, new float[]{10F, 6F}, 0F);
        for (int pass = 0; pass < 2; pass++) {
            g2.setStroke(pass == 0 ? under : dash);
            g2.setColor(pass == 0 ? new Color(255, 255, 255, dragging ? 230 : 150) : new Color(24, 25, 28, dragging ? 230 : 150));
            if (showAxisX) {
                g2.drawLine(sx, 0, sx, h);
            }
            if (showAxisY) {
                g2.drawLine(0, sy, w, sy);
            }
        }
        // The handles - small dark tabs with a white rim and grip lines,
        // the only places that grab the lines
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Point[] handles = axisHandles();
        for (int i = 0; i < handles.length; i++) {
            Point handle = handles[i];
            if (handle == null) {
                continue;
            }
            // The vertical line's tab stands upright, the horizontal line's lies flat
            boolean upright = i == 0;
            int tw = upright ? 12 : 22, th = upright ? 22 : 12;
            java.awt.geom.RoundRectangle2D tab = new java.awt.geom.RoundRectangle2D.Float(
                    handle.x - tw / 2F, handle.y - th / 2F, tw, th, 7, 7);
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fill(new java.awt.geom.RoundRectangle2D.Float(handle.x - tw / 2F, handle.y - th / 2F + 1.5F, tw, th, 7, 7));
            g2.setColor(dragging ? Color.WHITE : new Color(34, 36, 40));
            g2.fill(tab);
            g2.setStroke(new BasicStroke(1.2F));
            g2.setColor(dragging ? new Color(34, 36, 40) : new Color(235, 236, 238));
            g2.draw(tab);
            for (int k = -1; k <= 1; k++) {
                if (upright) {
                    g2.draw(new Line2D.Float(handle.x - 2.5F, handle.y + k * 3.5F, handle.x + 2.5F, handle.y + k * 3.5F));
                } else {
                    g2.draw(new Line2D.Float(handle.x + k * 3.5F, handle.y - 2.5F, handle.x + k * 3.5F, handle.y + 2.5F));
                }
            }
        }
    }

    /** Show a short message in the middle of the canvas for a moment */
    public void showMessage(String text) {
        message = text;
        messageUntil = System.currentTimeMillis() + 1600;
        repaint();
        javax.swing.Timer hide = new javax.swing.Timer(1700, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                repaint();
            }
        });
        hide.setRepeats(false);
        hide.start();
    }

    private void paintMessage(Graphics2D g2, int w, int h) {
        if (message == null || System.currentTimeMillis() > messageUntil) {
            return;
        }
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(FONT_MEDIUM);
        FontMetrics metrics = g2.getFontMetrics();
        int width = metrics.stringWidth(message) + 28;
        int height = metrics.getHeight() + 14;
        int x = (Math.min(w, getVisibleRect().width) - width) / 2;
        int y = 40;
        g2.setColor(new Color(30, 32, 35, 230));
        g2.fillRoundRect(x, y, width, height, height, height);
        g2.setColor(COLOR_UI_TEXT);
        g2.drawString(message, x + 14, y + 7 + metrics.getAscent());
    }

    /** Small zoom level label in the bottom left corner while zoomed */
    private void paintZoomLabel(Graphics2D g2, int h) {
        if (!isCanvasZoomed()) {
            return;
        }
        String text = Math.round(viewScale * 100) + " %";
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(FONT_SMALL);
        FontMetrics metrics = g2.getFontMetrics();
        int width = metrics.stringWidth(text) + 16;
        int height = metrics.getHeight() + 6;
        int x = 10;
        int y = h - height - 10;
        g2.setColor(new Color(30, 32, 35, 210));
        g2.fillRoundRect(x, y, width, height, height, height);
        g2.setColor(COLOR_UI_TEXT);
        g2.drawString(text, x + 8, y + 3 + metrics.getAscent());
    }

    //////////////////////////////////////////////////////////////
    // IMAGE
    //////////////////////////////////////////////////////////////
    /** Set the Image to be drawn on the canvas
     * 
     * @param image Image to be drawn
     */
    public void setImage(BufferedImage image) {
        this.image = image;
        canvasImage = renderScreenImage(true);
        if (image != null) {
            Alchemy.menuBar.unloadBackgroundImageItem.setEnabled(true);
        } else {
            Alchemy.menuBar.unloadBackgroundImageItem.setEnabled(false);
        }
    }
    
    /** Get the current image
     * 
     * @return  The current image
     */
    public Image getImage() {
        return this.image;
    }

    /** Check if an Image is defined or not
     * 
     * @return Image display on or off
     */
    public boolean isImageSet() {
        return image == null ? false : true;
    }

    /** Set image display to on or off
     * 
     * @param imageDisplay Image display on or off
     */
    public void setImageDisplay(boolean imageDisplay) {
        this.imageDisplay = imageDisplay;
        canvasImage = renderScreenImage(true);
    }

    /** Check if image display is enabled
     * @return 
     */
    public boolean isImageDisplayEnabled() {
        return imageDisplay;
    }

    /** Set the location for the image to be displayed on the canvas
     * 
     * @param p
     */
    public void setImageLocation(Point p) {
        this.imageLocation = p;
    }

    /** Set the location for the image to be displayed on the canvas
     * 
     * @param x
     * @param y
     */
    public void setImageLocation(int x, int y) {
        this.imageLocation.x = x;
        this.imageLocation.y = y;
    }
    
    /** Get the location where the image is displayed on the canvas
     * 
     * @return  Point - x & y location
     */
    public Point getImageLocation() {
        return imageLocation;
    }

    /** Reset the image location back to zero */
    public void resetImageLocation() {
        this.imageLocation.x = 0;
        this.imageLocation.y = 0;
    }

    /** Set the transparent image to be drawn behind the canvas
     * 
     * @param transparentImage
     */
    void setTransparentImage(Image transparentImage) {
        this.transparentImage = transparentImage;
    }

    /** Create an image from the canvas
     * 
     * @param vectorMode    In vector mode all shapes are rendered from scratch.
     *                      Otherwise the active shapes are rendered on top of the current canvas image
     * @return
     */
    Image renderCanvas(boolean vectorMode) {
        return renderCanvas(vectorMode, false, 1, -1, -1);
    }

    /** Render the on-screen buffer at the full resolution of the display.
     *  With Windows display scaling (125%, 150%...) a buffer at the logical
     *  size would be stretched by the screen and lines would look jagged.
     */
    private Image renderScreenImage(boolean vectorMode) {
        return renderScreenImage(vectorMode, false);
    }

    private Image renderScreenImage(boolean vectorMode, boolean transparent) {
        // The cached picture is only moved for a view still settling - start again from the shapes
        if (viewPending) {
            viewPending = false;
            if (viewSettle != null) {
                viewSettle.stop();
            }
            vectorMode = true;
        }
        double scale = getDisplayScale();
        java.awt.Rectangle visibleRect = this.getVisibleRect();
        vectorCanvas.applyView = true;
        long start = System.nanoTime();
        try {
            return renderCanvas(vectorMode, transparent, scale,
                    (int) Math.ceil(visibleRect.width * scale), (int) Math.ceil(visibleRect.height * scale));
        } finally {
            vectorCanvas.applyView = false;
            canvasImageView = getViewTransform();
            if (vectorMode) {
                lastFullRenderNanos = System.nanoTime() - start;
            }
        }
    }

    /** Draw the on-screen buffer at the logical canvas size */
    private void drawCanvasImage(Graphics2D g2) {
        if (canvasImage == null) {
            return;
        }
        if (viewPending && canvasImageView != null) {
            // The picture drawn for the old view, moved and scaled to the new one
            try {
                AffineTransform move = new AffineTransform(getViewTransform());
                move.concatenate(canvasImageView.createInverse());
                double deviceScale = canvasImage.getWidth(null) / (double) Math.max(1, getVisibleRect().width);
                move.scale(1 / deviceScale, 1 / deviceScale);
                Object oldInterpolation = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(canvasImage, move, null);
                if (oldInterpolation != null) {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
                }
                return;
            } catch (java.awt.geom.NoninvertibleTransformException e) {
                // Fall through and draw it as it is
            }
        }
        double scale = canvasImage.getWidth(null) / (double) Math.max(1, getVisibleRect().width);
        if (Math.abs(scale - 1) < 0.01) {
            g2.drawImage(canvasImage, 0, 0, null);
        } else {
            AffineTransform at = AffineTransform.getScaleInstance(1 / scale, 1 / scale);
            g2.drawImage(canvasImage, at, null);
        }
    }

    /** The scale of the screen the canvas is on - 1.25 for 125% Windows scaling */
    private double getDisplayScale() {
        GraphicsConfiguration config = getGraphicsConfiguration();
        if (config == null) {
            return 1;
        }
        return config.getDefaultTransform().getScaleX();
    }

    /** Create an image from the canvas
     * 
     * @param vectorMode    In vector mode all shapes are rendered from scratch.
     *                      Otherwise the active shapes are rendered on top of the current canvas image
     * @param transparent   Ignore the background and create a transparent image with only shapes
     * @return
     */
    Image renderCanvas(boolean vectorMode, boolean transparent) {
        return renderCanvas(vectorMode, transparent, 1, -1, -1);
    }

    /** Create an image from the canvas
     * 
     * @param vectorMode    In vector mode all shapes are rendered from scratch.
     *                      Otherwise the active shapes are rendered on top of the current canvas image
     * @param scale         Scale setting to scale the canvas up or down
     * @return
     */
    Image renderCanvas(boolean vectorMode, double scale) {
        return renderCanvas(vectorMode, false, scale, -1, -1);
    }

    /** Create an image from the canvas
     * 
     * @param vectorMode    In vector mode all shapes are rendered from scratch.
     *                      Otherwise the active shapes are rendered on top of the current canvas image
     * @param transparent   Ignore the background and create a transparent image with only shapes
     * @param scale         Scale setting to scale the canvas up or down
     * @return
     */
    Image renderCanvas(boolean vectorMode, boolean transparent, double scale, int width, int height) {
        if (width == -1 || height == -1) {
            // The canvas size without the frame/decorations - or the chosen export area
            java.awt.Rectangle visibleRect = getExportBounds();
            width = visibleRect.width;
            height = visibleRect.height;
        }
        ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
        BufferedImage buffImage;
        if (transparent) {
            buffImage = gc.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
        } else {
            buffImage = gc.createCompatibleImage(width, height, Transparency.OPAQUE);
        }
        // Paint the buffImage with the canvas
        Graphics2D g2 = buffImage.createGraphics();
        // Make sure the record indicator is off
        recordIndicator = false;

        if (scale != 1) {
            g2.scale(scale, scale);
        }
        vectorCanvas.targetWidth = width;
        vectorCanvas.targetHeight = height;

        if (transparent) {
            vectorCanvas.transparent = true;
            vectorCanvas.paintComponent(g2);
            vectorCanvas.transparent = false;
        } else {
//            vectorCanvas.transparent = false;
            if (vectorMode) {
                vectorCanvas.paintComponent(g2);
            } else {
                renderingBuffer = true;
                try {
                    this.paintComponent(g2);
                } finally {
                    renderingBuffer = false;
                }
            }
        }
        g2.dispose();
        vectorCanvas.targetWidth = 0;
        vectorCanvas.targetHeight = 0;
        return buffImage;
    }
    //////////////////////////////////////////////////////////////
    // SAVE BITMAP
    //////////////////////////////////////////////////////////////
    /** Save the canvas to a bitmap file
     * 
     * @param file  The file object to save the bitmap to
     * @return      True if save worked, otherwise false
     */
    boolean saveBitmap(File file) {
        return saveBitmap(file, "png", false);
    }

    /** Save the canvas to a bitmap file
     * 
     * @param file          The file object to save the bitmap to
     * @param transparent   An image with transparency or not
     * @return              True if save worked, otherwise false
     */
    boolean saveBitmap(File file, boolean transparent) {
        return saveBitmap(file, "png", transparent);
    }

    /** Save the canvas to a bitmap file
     * 
     * @param file          The file object to save the bitmap to
     * @param format        The file format to save in
     * @return              True if save worked, otherwise false
     */
    boolean saveBitmap(File file, String format) {
        return saveBitmap(file, format, false);
    }

    // TODO - Scaleable image export
    /** Save the canvas to a bitmap file
     * 
     * @param file          The file object to save the bitmap to
     * @param format        The file format to save in
     * @param transparent   An image with transparency or not
     * @return              True if save worked, otherwise false
     */
    boolean saveBitmap(File file, String format, boolean transparent) {
        try {
            setGuide(false);
            Image bitmapImage = renderCanvas(true, transparent);
            setGuide(true);
            ImageIO.write((BufferedImage) bitmapImage, format, file);
            return true;
        } catch (IOException ex) {
            System.err.println(ex);
            return false;
        }
    }
    //////////////////////////////////////////////////////////////
    // PRINT STUFF
    //////////////////////////////////////////////////////////////
    /**
     * This is the method defined by the Printable interface.  It prints the
     * canvas to the specified Graphics object, respecting the paper size
     * and margins specified by the PageFormat.  If the specified pdfReadPage number
     * is not pdfReadPage 0, it returns a code saying that printing is complete.  The
     * method must be prepared to be called multiple times per printing request
     * 
     * This code is based on code from the book Java Examples in a Nutshell, 2nd Edition. Copyright (c) 2000 David Flanagan. 
     *
     * @param g
     * @param format
     * @param pageIndex
     * @return
     * @throws PrinterException 
     */
    public int print(Graphics g, PageFormat format, int pageIndex) throws PrinterException {
        // We are only one pdfReadPage long; reject any other pdfReadPage numbers
        if (pageIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        }

        // The Java 1.2 printing API passes us a Graphics object, but we
        // can always cast it to a Graphics2D object
        Graphics2D g2p = (Graphics2D) g;

        // Translate to accomodate the requested top and left margins.
        g2p.translate(format.getImageableX(), format.getImageableY());


        // Figure out how big the drawing is, and how big the pdfReadPage (excluding margins) is
        Dimension size = this.getSize();                  // Canvas size
        double pageWidth = format.getImageableWidth();    // Page width
        double pageHeight = format.getImageableHeight();  // Page height

        // If the canvas is too wide or tall for the pdfReadPage, scale it down
        if (size.width > pageWidth) {
            double factor = pageWidth / size.width;  // How much to scale
            System.out.println("Width Scale: " + factor);
            g2p.scale(factor, factor);              // Adjust coordinate system
            pageWidth /= factor;                   // Adjust pdfReadPage size up
            pageHeight /= factor;
        }

        if (size.height > pageHeight) {   // Do the same thing for height
            double factor = pageHeight / size.height;
            System.out.println("Height Scale: " + factor);
            g2p.scale(factor, factor);
            pageWidth /= factor;
            pageHeight /= factor;

        }

        // Now we know the canvas will fit on the pdfReadPage.  Center it by translating as necessary.
        g2p.translate((pageWidth - size.width) / 2, (pageHeight - size.height) / 2);

        // Draw a line around the outside of the drawing area
        //g2.drawRect(-1, -1, size.width + 2, size.height + 2);

        // Set a clipping region so the canvas doesn't go out of bounds
        g2p.setClip(0, 0, size.width, size.height);

        // Finally, print the component by calling the paintComponent() method.
        // Or, call paint() to paint the component, its background, border, and
        // children, including the Print JButton
        vectorCanvas.paintComponent(g);

        // Tell the PrinterJob that the pdfReadPage number was valid
        return Printable.PAGE_EXISTS;

    }

    //////////////////////////////////////////////////////////////
    // MOUSE EVENTS
    //////////////////////////////////////////////////////////////
    public void mouseMoved(MouseEvent event) {
        updateFromWindowsInk();
        penLogState("moved", false);
        setPenLocation(event);
        if (exportSelector != null) {
            if (!spaceDown) {
                setCursor(exportSelector.getCursor(event.getPoint()));
            }
            return;
        }
        // An open polygon stroke follows the mouse with a guide line
        if (polyActive) {
            polyCursor = event.getPoint();
            repaint();
            return;
        }
        // The selection tool shows what a drag would do - the modules do not see the mouse
        if (selecting) {
            if (!spaceDown) {
                setCursor(selectionTool.getCursor(event.getPoint()));
            }
            if (isAutoToggleToolBar()) {
                Alchemy.toolBar.toggleToolBar(event.getY());
            }
            return;
        }
        // Show that a symmetry axis can be grabbed
        int axis = axisAt(event.getPoint());
        if (axis != AXIS_NONE && !spaceDown) {
            setHandCursor(axis == AXIS_X ? Cursor.E_RESIZE_CURSOR : (axis == AXIS_Y ? Cursor.N_RESIZE_CURSOR : Cursor.MOVE_CURSOR));
            axisHover = true;
        } else if (axisHover) {
            axisHover = false;
            restoreCursor();
        }
        if (isAutoToggleToolBar()) {
            Alchemy.toolBar.toggleToolBar(event.getY());
        }
        event = toWorldEvent(event);
        if (events) {
            // Pass to the current create module
            if (createEvents) {
                Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mouseMoved(event);
            }
            // Pass to all active affect modules
            if (affectEvents) {
                if (Alchemy.plugins.hasCurrentAffects()) {
                    for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                        if (Alchemy.plugins.currentAffects[i]) {
                            Alchemy.plugins.affects[i].mouseMoved(event);
                        }
                    }
                }
            }
        }
        if (penType != PEN_CURSOR) {
            penLocationChanged = false;
        }
    }

    public void mousePressed(MouseEvent event) {
        penDown = true;
        liveScale = 1;
        updateFromWindowsInk();
        penLogState("PRESSED at " + event.getX() + "," + event.getY(), true);
        // Hide the toolbar when clicking on the canvas
        if (!Alchemy.preferences.paletteAttached && Alchemy.toolBar.isToolBarVisible() &&
                !Alchemy.preferences.simpleToolBar && event.getY() >= Alchemy.toolBar.getTotalHeight()) {
            Alchemy.toolBar.setToolBarVisible(false);
        }

        // View navigation - space + left button or the middle button pans, Ctrl + left button zooms
        boolean pan = SwingUtilities.isMiddleMouseButton(event) || (spaceDown && SwingUtilities.isLeftMouseButton(event));
        boolean zoom = SwingUtilities.isLeftMouseButton(event) && event.isControlDown();
        if (pan || zoom) {
            penDown = false;
            stopGlide();
            navigation = pan ? NAV_PAN : NAV_ZOOM;
            navStart = event.getPoint();
            navStartScale = viewScale;
            navStartX = viewX;
            navStartY = viewY;
            lastPanPoint = event.getPoint();
            lastPanTime = event.getWhen();
            panVelocityX = 0;
            panVelocityY = 0;
            setHandCursor(pan ? Cursor.MOVE_CURSOR : -1);
            return;
        }
        stopGlide();
        if (exportSelector != null) {
            penDown = false;
            exportSelector.mousePressed(event);
            return;
        }

        // An open polygon stroke - a click adds a straight piece, any other button finishes it
        if (polyActive) {
            if (SwingUtilities.isLeftMouseButton(event)) {
                polyLineTo(toWorld(event.getX(), event.getY()), event.getWhen());
                polyCursor = event.getPoint();
            } else {
                penDown = false;
                finishPoly();
            }
            return;
        }

        // The lasso selection tool takes the pen instead of the modules
        if (selecting) {
            penDown = false;
            selectStroke = SwingUtilities.isLeftMouseButton(event);
            if (selectStroke) {
                selectionTool.mousePressed(event);
            }
            return;
        }

        // Grab a symmetry axis instead of drawing
        if (SwingUtilities.isLeftMouseButton(event)) {
            int axis = axisAt(event.getPoint());
            if (axis != AXIS_NONE) {
                penDown = false;
                axisDrag = axis;
                repaint();
                return;
            }
        }

        // Nothing can be drawn or erased on a hidden or locked layer
        blockedStroke = events && !canDrawOnActiveLayer();
        if (blockedStroke) {
            penDown = false;
            AlcLayer layer = getActiveLayer();
            showMessage(Alchemy.bundle.getString(layer.locked ? "layerLockedMessage" : "layerHiddenMessage"));
            return;
        }

        if (events) {
            if (SwingUtilities.isRightMouseButton(event)) {
                // The right button erases whole shapes
                erasing = true;
                eraserLocation = null;
                Alchemy.history.beginEdit();
                eraseTo(toWorld(event.getX(), event.getY()));

            } else if (style == STYLE_FILL && event.isShiftDown() && SwingUtilities.isLeftMouseButton(event)) {
                // Solid with Shift cuts the area out of the shapes instead of drawing
                Alchemy.history.beginEdit();
                cutting = true;
                cutPoints.clear();
                cutPoints.add(toWorld(event.getX(), event.getY()));
                polyCursor = event.getPoint();
                repaint();
            } else {
                Alchemy.history.beginEdit();
                stabilizedLocation = null;
                if (stabilizer > 0) {
                    stabilize(currentRawLocation(event));
                }
                event = toWorldEvent(event);
                // Pass to the current create module
                if (createEvents) {
                    Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mousePressed(event);
                }
                // Pass to all active affect modules
                if (affectEvents) {
                    if (Alchemy.plugins.hasCurrentAffects()) {
                        for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                            if (Alchemy.plugins.currentAffects[i]) {
                                Alchemy.plugins.affects[i].mousePressed(event);
                            }
                        }
                    }
                }
    
            }      
        }
    }
    
    public void mouseClicked(MouseEvent event) {
        if (exportSelector != null || navigation != NAV_NONE) {
            return;
        }
        event = toWorldEvent(event);
        if (events) {
            // Pass to the current create module
            if (createEvents) {
                Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mouseClicked(event);
            }
            // Pass to all active affect modules
            if (affectEvents) {
                if (Alchemy.plugins.hasCurrentAffects()) {
                    for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                        if (Alchemy.plugins.currentAffects[i]) {
                            Alchemy.plugins.affects[i].mouseClicked(event);
                        }
                    }
                }
            }           
        }
    }

    public void mouseEntered(MouseEvent event) {
        event = toWorldEvent(event);
        if (events) {
            // Pass to the current create module
            if (createEvents) {
                Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mouseEntered(event);
            }
            // Pass to all active affect modules
            if (affectEvents) {
                if (Alchemy.plugins.hasCurrentAffects()) {
                    for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                        if (Alchemy.plugins.currentAffects[i]) {
                            Alchemy.plugins.affects[i].mouseEntered(event);
                        }
                    }
                }
            }
        }
    }

    public void mouseExited(MouseEvent event) {
        event = toWorldEvent(event);
        if (events) {
            // Pass to the current create module
            if (createEvents) {
                Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mouseExited(event);
            }
            // Pass to all active affect modules
            if (affectEvents) {
                if (Alchemy.plugins.hasCurrentAffects()) {
                    for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                        if (Alchemy.plugins.currentAffects[i]) {
                            Alchemy.plugins.affects[i].mouseExited(event);
                        }
                    }
                }
            }
        }
    }

    public void mouseReleased(MouseEvent event) {
        penLogState("RELEASED at " + event.getX() + "," + event.getY() + " before", true);
        penDown = false;
        if (blockedStroke) {
            blockedStroke = false;
            return;
        }
        if (selectStroke && navigation == NAV_NONE) {
            selectStroke = false;
            selectionTool.mouseReleased(event);
            return;
        }
        if (axisDrag != AXIS_NONE) {
            axisDrag = AXIS_NONE;
            if (axisListener != null) {
                axisListener.axesDropped();
            }
            repaint();
            return;
        }
        if (navigation != NAV_NONE) {
            // A quick flick lets the canvas glide on - unless the mouse stopped before letting go
            if (navigation == NAV_PAN && event.getWhen() - lastPanTime < 60) {
                startGlide();
            }
            navigation = NAV_NONE;
            if (spaceDown) {
                setHandCursor(Cursor.HAND_CURSOR);
            } else if (exportSelector != null) {
                setCursor(exportSelector.getCursor(event.getPoint()));
            } else {
                restoreCursor();
            }
            return;
        }
        if (exportSelector != null) {
            exportSelector.mouseReleased(event);
            return;
        }
        if (erasing) {
            erasing = false;
            eraserLocation = null;
            repaint();
            Alchemy.history.endEdit();
            return;
        }
        if (cutting) {
            if (event.isAltDown()) {
                // Keep the cut open for straight pieces
                polyActive = true;
                altUsed = true;
                polyAnchor = cutPoints.get(cutPoints.size() - 1);
                polyCursor = event.getPoint();
                repaint();
            } else {
                finishCut();
            }
            return;
        }
        if (events && event.isAltDown() && SwingUtilities.isLeftMouseButton(event)) {
            // Letting go with Alt keeps the stroke open - clicks add straight pieces until Alt is let go
            polyActive = true;
            altUsed = true;
            polyAnchor = (stabilizedLocation != null) ? new Point2D.Float(stabilizedLocation.x, stabilizedLocation.y)
                    : toWorld(event.getX(), event.getY());
            polyCursor = event.getPoint();
            repaint();
            return;
        }
        polyActive = false;
        if (stabilizedLocation != null) {
            // Let the stabilized line catch up with where the pen was lifted
            Point2D.Float target = currentRawLocation(event);
            penDown = true;
            settling = true;
            for (int i = 0; i < 30 && stabilizedLocation.distance(target) > 1; i++) {
                MouseEvent drag = moveEvent(new MouseEvent(this, MouseEvent.MOUSE_DRAGGED, event.getWhen(),
                        MouseEvent.BUTTON1_DOWN_MASK, event.getX(), event.getY(), 0, false, MouseEvent.NOBUTTON), stabilize(target));
                passDragged(drag);
            }
            settling = false;
            penDown = false;
            event = moveEvent(event, stabilizedLocation);
        } else {
            event = toWorldEvent(event);
        }
        if (events) {
            // Pass to the current create module
            if (createEvents) {
                Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mouseReleased(event);
            }
            // Pass to all active affect modules
            if (affectEvents) {
                if (Alchemy.plugins.hasCurrentAffects()) {
                    for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                        if (Alchemy.plugins.currentAffects[i]) {
                            Alchemy.plugins.affects[i].mouseReleased(event);
                        }
                    }
                }
            }
        }
        stabilizedLocation = null;
        Alchemy.history.endEdit();
    }

    /** The real (unstabilized) pen location - from the tablet if available */
    private Point2D.Float currentRawLocation(MouseEvent event) {
        if (penType == PEN_CURSOR) {
            return toWorld(event.getX(), event.getY());
        }
        return toWorld(penLocation.x, penLocation.y);
    }

    public void mouseDragged(MouseEvent event) {
        if (blockedStroke) {
            return;
        }
        if (axisDrag != AXIS_NONE) {
            dragAxis(event);
            return;
        }
        if (selectStroke && navigation == NAV_NONE) {
            selectionTool.mouseDragged(event);
            return;
        }
        if (navigation == NAV_PAN) {
            // The point grabbed stays under the mouse
            setView(viewScale, navStartX + event.getX() - navStart.x, navStartY + event.getY() - navStart.y);
            // Smoothed drag speed for the glide on release
            long dt = Math.max(1, event.getWhen() - lastPanTime);
            panVelocityX = panVelocityX * 0.5 + (event.getX() - lastPanPoint.x) / (double) dt * 0.5;
            panVelocityY = panVelocityY * 0.5 + (event.getY() - lastPanPoint.y) / (double) dt * 0.5;
            lastPanPoint = event.getPoint();
            lastPanTime = event.getWhen();
            return;
        } else if (navigation == NAV_ZOOM) {
            // Drag right to zoom in, left to zoom out - around the point where the drag started
            double newScale = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, navStartScale * Math.exp((event.getX() - navStart.x) * 0.005)));
            double ratio = newScale / navStartScale;
            setView(newScale, navStart.x - (navStart.x - navStartX) * ratio, navStart.y - (navStart.y - navStartY) * ratio);
            return;
        }
        if (exportSelector != null) {
            exportSelector.mouseDragged(event);
            return;
        }
        updateFromWindowsInk();
        penLogState("dragged", false);
        setPenLocation(event);
        if (erasing) {
            eraseTo(toWorld(event.getX(), event.getY()));
            return;
        }
        if (polyActive) {
            polyCursor = event.getPoint();
        }
        if (cutting) {
            Point2D.Float p = toWorld(event.getX(), event.getY());
            if (p.distance(cutPoints.get(cutPoints.size() - 1)) * viewScale >= 1.5) {
                cutPoints.add(p);
            }
            polyCursor = event.getPoint();
            repaint();
            return;
        }
        if (stabilizer > 0 && stabilizedLocation != null) {
            event = moveEvent(event, stabilize(currentRawLocation(event)));
        } else {
            event = toWorldEvent(event);
        }
        passDragged(event);
        if (penType != PEN_CURSOR) {
            penLocationChanged = false;
        }
    }

    /** Add a straight piece to the open polygon stroke - the modules see it as a steady drag */
    private void polyLineTo(Point2D.Float p, long when) {
        penDown = true;
        if (cutting) {
            cutPoints.add(p);
            polyAnchor = p;
            repaint();
            return;
        }
        double distance = polyAnchor.distance(p);
        int steps = Math.max(1, (int) Math.ceil(distance * viewScale / 3));
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            Point2D.Float q = new Point2D.Float(polyAnchor.x + (p.x - polyAnchor.x) * t, polyAnchor.y + (p.y - polyAnchor.y) * t);
            // Modules read the pen location - it follows the straight line
            stabilizedLocation = q;
            passDragged(new MouseEvent(this, MouseEvent.MOUSE_DRAGGED, when - 4L * (steps - i),
                    MouseEvent.BUTTON1_DOWN_MASK, Math.round(q.x), Math.round(q.y), 0, false, MouseEvent.NOBUTTON));
        }
        // With the stabilizer the line carries on smoothly from here
        stabilizedLocation = (stabilizer > 0) ? new Point2D.Float(p.x, p.y) : null;
        polyAnchor = p;
    }

    /** Finish an open polygon stroke where it is - by letting go of Alt, Enter or another button */
    private void finishPoly() {
        polyActive = false;
        repaint();
        if (cutting) {
            finishCut();
            return;
        }
        Point2D.Float end = (polyAnchor != null) ? polyAnchor : getPenLocation();
        MouseEvent release = new MouseEvent(this, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                Math.round(end.x), Math.round(end.y), 1, false, MouseEvent.BUTTON1);
        penDown = false;
        stabilizedLocation = null;
        if (events) {
            if (createEvents) {
                Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mouseReleased(release);
            }
            if (affectEvents && Alchemy.plugins.hasCurrentAffects()) {
                for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                    if (Alchemy.plugins.currentAffects[i]) {
                        Alchemy.plugins.affects[i].mouseReleased(release);
                    }
                }
            }
        }
        polyAnchor = null;
        Alchemy.history.endEdit();
    }

    /** Cut the area of the Solid cut stroke out of every shape on the active layer */
    private void finishCut() {
        cutting = false;
        polyActive = false;
        polyAnchor = null;
        if (cutPoints.size() >= 3) {
            GeneralPath outline = new GeneralPath(GeneralPath.WIND_NON_ZERO);
            outline.moveTo(cutPoints.get(0).x, cutPoints.get(0).y);
            for (int i = 1; i < cutPoints.size(); i++) {
                outline.lineTo(cutPoints.get(i).x, cutPoints.get(i).y);
            }
            outline.closePath();
            java.awt.geom.Area cut = new java.awt.geom.Area();
            // With Mirror on the cut is made on every side of the axes
            for (AffineTransform mirror : symmetryTransforms()) {
                cut.add(new java.awt.geom.Area(mirror.createTransformedShape(outline)));
            }
            Rectangle2D cutBounds = cut.getBounds2D();
            if (!cut.isEmpty()) {
                for (int i = shapes.size() - 1; i >= 0; i--) {
                    AlcShape shape = shapes.get(i);
                    Rectangle2D b = shape.path.getBounds2D();
                    double grow = shape.lineWidth;
                    if (!cutBounds.intersects(b.getX() - grow, b.getY() - grow, b.getWidth() + grow * 2, b.getHeight() + grow * 2)) {
                        continue;
                    }
                    java.awt.geom.Area area = AlcSelectionTool.shapeArea(shape);
                    java.awt.geom.Area inside = new java.awt.geom.Area(area);
                    inside.intersect(cut);
                    if (inside.isEmpty()) {
                        continue;
                    }
                    area.subtract(cut);
                    shapes.remove(i);
                    if (!area.isEmpty()) {
                        // What is left is a filled shape with the colour, transparency and gradient of the original
                        AlcShape rest = new AlcShape(new GeneralPath(area), shape.getColor(), shape.getAlpha(), STYLE_FILL, shape.getLineWidth());
                        rest.setAlphaColor(shape.getColor());
                        rest.setGradientPaint(shape.getGradientPaint());
                        shapes.add(i, rest);
                    }
                }
            }
        }
        cutPoints.clear();
        redraw(true);
        Alchemy.history.endEdit();
    }

    /** The mirror images of the symmetry axes shown by Mirror - always starting with the shape itself */
    /** The identity and one reflection for every Mirror axis shown - for tools that change finished shapes */
    public ArrayList<AffineTransform> symmetryTransforms() {
        ArrayList<AffineTransform> list = new ArrayList<AffineTransform>(4);
        list.add(new AffineTransform());
        AffineTransform flipX = null, flipY = null;
        if (showAxisX) {
            flipX = new AffineTransform(-1, 0, 0, 1, 2 * axisX, 0);
            list.add(flipX);
        }
        if (showAxisY) {
            flipY = new AffineTransform(1, 0, 0, -1, 0, 2 * axisY);
            list.add(flipY);
        }
        if (flipX != null && flipY != null) {
            AffineTransform both = new AffineTransform(flipX);
            both.concatenate(flipY);
            list.add(both);
        }
        return list;
    }

    /** The guide line of an open polygon stroke and the area of a Solid cut, on the screen */
    private void paintPolyAndCut(Graphics2D g2) {
        if (!cutting && !polyActive) {
            return;
        }
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        AffineTransform view = getViewTransform();
        if (cutting && !cutPoints.isEmpty()) {
            // The cut in drawing coordinates, then shown with its mirror images
            GeneralPath world = new GeneralPath();
            world.moveTo(cutPoints.get(0).x, cutPoints.get(0).y);
            for (int i = 1; i < cutPoints.size(); i++) {
                world.lineTo(cutPoints.get(i).x, cutPoints.get(i).y);
            }
            if (polyActive && polyCursor != null) {
                Point2D.Float c = toWorld(polyCursor.x, polyCursor.y);
                world.lineTo(c.x, c.y);
            }
            world.closePath();
            for (AffineTransform mirror : symmetryTransforms()) {
                AffineTransform t = new AffineTransform(view);
                t.concatenate(mirror);
                java.awt.Shape outline = t.createTransformedShape(world);
                g2.setColor(new Color(220, 50, 70, 55));
                g2.fill(outline);
                g2.setStroke(new BasicStroke(2.6F));
                g2.setColor(new Color(255, 255, 255, 170));
                g2.draw(outline);
                g2.setStroke(new BasicStroke(1.3F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10F, new float[]{5F, 4F}, 0F));
                g2.setColor(new Color(220, 50, 70));
                g2.draw(outline);
            }
        }
        if (polyActive && polyAnchor != null && polyCursor != null) {
            Point2D a = view.transform(polyAnchor, null);
            Line2D.Double guide = new Line2D.Double(a.getX(), a.getY(), polyCursor.x, polyCursor.y);
            g2.setStroke(new BasicStroke(3F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(255, 255, 255, 170));
            g2.draw(guide);
            g2.setStroke(new BasicStroke(1.3F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10F, new float[]{5F, 4F}, 0F));
            g2.setColor(new Color(77, 156, 255));
            g2.draw(guide);
            g2.fill(new Ellipse2D.Double(a.getX() - 3, a.getY() - 3, 6, 6));
        }
    }

    /** Is anything being drawn right now */
    private boolean hasLiveShapes() {
        for (int j = 0; j < activeShapeList.length; j++) {
            if (!activeShapeList[j].isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Draw the shapes being made - the create, affect and guide lists */
    private void drawLiveShapes(Graphics2D g2) {
        for (int j = 0; j < activeShapeList.length; j++) {
            for (int i = 0; i < activeShapeList[j].size(); i++) {
                AlcShape currentShape = (AlcShape) activeShapeList[j].get(i);
                // SOLID - under the outline when both are drawn
                if (currentShape.isFilled()) {
                    g2.setPaint(currentShape.getFillPaint());
                    g2.fill(currentShape.path);
                }
                // LINE
                if (currentShape.isStroked()) {
                    g2.setStroke(currentShape.getStroke());
                    g2.setPaint(currentShape.getPaint());
                    g2.draw(currentShape.path);
                }
            }
        }
    }

    /** Pass a drag event on to the active modules */
    private void passDragged(MouseEvent event) {
        if (events) {
            // Pass to the current create module
            if (createEvents) {
                Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mouseDragged(event);
            }
            // Pass to all active affect modules
            if (affectEvents) {
                if (Alchemy.plugins.hasCurrentAffects()) {
                    for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                        if (Alchemy.plugins.currentAffects[i]) {
                            Alchemy.plugins.affects[i].mouseDragged(event);
                        }
                    }
                }
            }
        }
    }
    //////////////////////////////////////////////////////////////
    // PEN EVENTS
    //////////////////////////////////////////////////////////////
    public void penKindEvent(PKindEvent ev) {
        penLog("jpen kind event: " + pm.pen.getKind().getType() + " devices " + pm.getDevices());
        setPenType();
    }

    public void penLevelEvent(PLevelEvent ev) {
        //setPenType();
        // Register the pen pressure, tilt and location 
        // Do this only if this is an actual pen
        // Otherwise register pen location using the mouse
        if (penType != PEN_CURSOR) {
            // Kept up to date also while the pen hovers, so a stroke does not start
            // with the pressure left over from the end of the last one
            {
                if (PEN_DEBUG && penDown) {
                    penLog("jpen level: pressure " + pm.pen.getLevelValue(PLevel.Type.PRESSURE) + " overwrites " + penPressure);
                }
                penPressure = pm.pen.getLevelValue(PLevel.Type.PRESSURE);
                // parabolic sensitivity
                penPressure *= penPressure;
                penTilt.x = pm.pen.getLevelValue(PLevel.Type.TILT_X);
                penTilt.y = pm.pen.getLevelValue(PLevel.Type.TILT_Y);
            }
            // If this event is a movement
            if (ev.isMovement()) {
                // Register the pen location even when the mouse is up
                setPenLocation(ev);
                penLocationChanged = true;
            }
        }
    }

    public void penButtonEvent(PButtonEvent arg0) {
    }

    public void penScrollEvent(PScrollEvent arg0) {
    }

    public void penTock(long arg0) {
    }

    /** Vector Canvas
     *  Draws the canvas in full, including all shapes,
     *  the background and buffImage if any.
     */
    class VectorCanvas extends JPanel implements AlcConstants {

        boolean transparent = false;
        /** Render through the zoom / pan view - only for the screen, never for exports */
        boolean applyView = false;
        /** Pixel size of the image being drawn into - 0 when not drawing into an image (PDF) */
        int targetWidth, targetHeight;
        private int width,  height;

        @Override
        public void paintComponent(Graphics g) {

            // The panel would fill its own background - not wanted for a transparent image
            if (!transparent) {
                super.paintComponent(g);
            }

            width = Alchemy.canvas.getWidth();
            height = Alchemy.canvas.getHeight();
            // Exporting a chosen part of the drawing
            Rectangle2D area = applyView ? null : Alchemy.canvas.exportArea;
            if (area != null) {
                width = (int) Math.ceil(area.getWidth());
                height = (int) Math.ceil(area.getHeight());
            }

            Graphics2D g2 = (Graphics2D) g;

            // Get the PDF Content Byte
            PdfContentByte cb = null;

            if (g2 instanceof PdfGraphics2D) {
                PdfGraphics2D g2pdf = (PdfGraphics2D) g2;
                cb = g2pdf.getContent();
            }

            if (Alchemy.canvas.smoothing) {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            } else {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            }

            // Do not draw the background when creating a transparent image
            if (!transparent) {
                // Paint background without transparency
                Color bgColor = Alchemy.canvas.getBackgroundColor();
                g2.setColor(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue()));
                g2.fillRect(0, 0, width, height);
                // Paper is only a guide - not drawn when exporting
                if (Alchemy.canvas.isGuideEnabled()) {
                    Alchemy.canvas.paintPaper(g2, width, height);
                }
            }

            // On screen the drawing is shown through the zoom / pan view
            if (applyView) {
                g2.transform(Alchemy.canvas.getViewTransform());
            } else if (area != null) {
                g2.translate(-area.getX(), -area.getY());
            }

            // PDF READER
            if (Alchemy.session.pdfReadPage != null) {

                // Remember the old transform settings
                AffineTransform at = g2.getTransform();

                int pageWidth = (int) Alchemy.session.pdfReadPage.getWidth();
                int pageHeight = (int) Alchemy.session.pdfReadPage.getHeight();
                PDFRenderer renderer = new PDFRenderer(Alchemy.session.pdfReadPage, g2, new Rectangle(0, 0, pageWidth, pageHeight), null, Alchemy.canvas.getBackgroundColor());
                try {
                    Alchemy.session.pdfReadPage.waitForFinish();
                    renderer.run();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }

                // Revert to the old transform settings
                g2.setTransform(at);

            }

            // Draw Image
            if (Alchemy.canvas.isImageDisplayEnabled() && Alchemy.canvas.isImageSet()) {
                Point p = Alchemy.canvas.getImageLocation();
                g2.drawImage(Alchemy.canvas.getImage(), p.x, p.y, null);
            }


            // Draw the visible layers bottom up - the shapes being drawn belong to the active layer
            Composite oldComposite = g2.getComposite();
            // Drawn on the graphics card when it can - into a picture laid over what is drawn so far
            BufferedImage gpu = (cb == null && targetWidth > 0 && targetHeight > 0)
                    ? AlcSkia.drawLayers(targetWidth, targetHeight, g2.getTransform(), Alchemy.canvas.layers, Alchemy.canvas.activeLayer,
                            Alchemy.canvas.createShapes, Alchemy.canvas.affectShapes, Alchemy.canvas.smoothing)
                    : null;
            if (gpu != null) {
                AffineTransform userTransform = g2.getTransform();
                g2.setTransform(new AffineTransform());
                g2.drawImage(gpu, 0, 0, null);
                g2.setTransform(userTransform);
            }
            for (int l = 0; gpu == null && l < Alchemy.canvas.layers.size(); l++) {
                AlcLayer layer = Alchemy.canvas.layers.get(l);
                if (!layer.visible) {
                    continue;
                }
                boolean active = l == Alchemy.canvas.activeLayer;
                if (layer.opacity < 1F && cb == null && targetWidth > 0 && targetHeight > 0) {
                    // Like a layer in Photoshop: the shapes are drawn together first and then the
                    // whole layer is faded - strokes crossing each other inside it do not get darker
                    BufferedImage group = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB_PRE);
                    Graphics2D lg = group.createGraphics();
                    lg.setRenderingHints(g2.getRenderingHints());
                    lg.setTransform(g2.getTransform());
                    drawShapes(lg, null, layer.shapes);
                    if (active) {
                        drawShapes(lg, null, Alchemy.canvas.createShapes);
                        drawShapes(lg, null, Alchemy.canvas.affectShapes);
                    }
                    lg.dispose();
                    AffineTransform userTransform = g2.getTransform();
                    g2.setTransform(new AffineTransform());
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, layer.opacity));
                    g2.drawImage(group, 0, 0, null);
                    g2.setTransform(userTransform);
                    g2.setComposite(oldComposite);
                    continue;
                }
                if (layer.opacity < 1F) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, layer.opacity));
                }
                drawShapes(g2, cb, layer.shapes);
                if (active) {
                    drawShapes(g2, cb, Alchemy.canvas.createShapes);
                    drawShapes(g2, cb, Alchemy.canvas.affectShapes);
                }
                g2.setComposite(oldComposite);
            }
            if (Alchemy.canvas.isGuideEnabled()) {
                for (int i = 0; i < Alchemy.canvas.guideShapes.size(); i++) {
                    AlcShape currentShape = Alchemy.canvas.guideShapes.get(i);
                    // LINE
                    if (currentShape.style == STYLE_STROKE) {
                        g2.setStroke(new BasicStroke(currentShape.lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL));
                        g2.setColor(currentShape.color);
                        g2.draw(currentShape.path);
                    // SOLID
                    } else {
                        g2.setColor(currentShape.color);
                        g2.fill(currentShape.path);
                    }
                }
            }

            g2.dispose();
        }

        /** Draw a list of shapes, with transparent gradients when making a PDF */
        private void drawShapes(Graphics2D g2, PdfContentByte cb, java.util.List<AlcShape> list) {
            {
                for (int i = 0; i < list.size(); i++) {
                    AlcShape currentShape = list.get(i);
                    Paint paint = currentShape.getPaint();

                    // SOLID - under the outline when both are drawn
                    if (currentShape.isFilled()) {
                        // If this shape is a gradient and we are making a PDF
                        if (paint instanceof GradientPaint && cb != null) {
                            drawTransparentGradient(cb, g2, (GradientPaint) paint, currentShape.path, true);
                        } else {
                            g2.setPaint(currentShape.getFillPaint());
                            g2.fill(currentShape.path);
                        }
                    }
                    // LINE
                    if (currentShape.isStroked()) {
                        g2.setStroke(currentShape.getStroke());
                        // If this shape is a gradient and we are making a PDF
                        if (paint instanceof GradientPaint && cb != null) {
                            drawTransparentGradient(cb, g2, (GradientPaint) paint, currentShape.path, false);
                        } else {
                            g2.setPaint(paint);
                            g2.draw(currentShape.path);
                        }
                    }
                }
            }
        }

        /** Draw a transparent gradient to the PDF */
        private void drawTransparentGradient(PdfContentByte cb, Graphics2D g2, GradientPaint gp, GeneralPath path, boolean fill) {

            //Create template
            PdfTemplate template = cb.createTemplate(width, height);

            //Prepare transparent group
            PdfTransparencyGroup transGroup = new PdfTransparencyGroup();
            transGroup.put(PdfName.CS, PdfName.DEVICERGB);
            transGroup.setIsolated(true);
            transGroup.setKnockout(false);
            template.setGroup(transGroup);

            //Prepare graphic state
            PdfGState gState = new PdfGState();
            PdfDictionary maskDict = new PdfDictionary();
            maskDict.put(PdfName.TYPE, PdfName.MASK);
            maskDict.put(PdfName.S, new PdfName("Luminosity"));
            maskDict.put(new PdfName("G"), template.getIndirectReference());
            gState.put(PdfName.SMASK, maskDict);
            cb.setGState(gState);

            // Create a gradient to use as the mask
            // Also flip the Y location
            PdfShading shading = PdfShading.simpleAxial(
                    cb.getPdfWriter(),
                    (float) gp.getPoint1().getX(),
                    (float) (height - gp.getPoint1().getY()),
                    (float) gp.getPoint2().getX(),
                    (float) (height - gp.getPoint2().getY()),
                    Color.WHITE,
                    Color.BLACK,
                    true,
                    true);
            template.paintShading(shading);

            // Draw the actual color under the mask
            g2.setColor(gp.getColor1());
            // SOLID
            if (fill) {
                g2.fill(path);
            // LINE    
            } else {
                g2.draw(path);
            }

        }
    }
}
