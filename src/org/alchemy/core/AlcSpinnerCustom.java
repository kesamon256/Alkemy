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
package org.alchemy.core;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;

/**
 *
 * AlcSpinnerCustom.java
 */
public class AlcSpinnerCustom extends JComponent implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener, AlcNumberDialogInterface, AlcConstants {

    int value, dragY, dragValue;
    int min, max, step;
    /** The numbers to step through when they are not whole numbers 1, 2, 3... - the value is then a place in this list */
    float[] ladder;
    boolean mouseDown;
    private boolean upPressed = false;
    private boolean downPressed = false;
    private boolean drag = false;
    //
    private final int width,  height,  halfHeight,  textAreaWidth,  stringY;
    private final FontMetrics metrics;
    private final Font font;
    private final Image spinner,  spinnerUp,  spinnerDown;
    //
    private javax.swing.Timer repeatTimer;
    private final int repeatInterval = 65;
    private final int repeatInitialDelay = 500;
    //
    private javax.swing.Timer keyOffTimer;
    private final int keyOffDelay = 2000;
    private boolean textInput = false;
    private String text;
    //
    /** The ChangeEvent that is passed to all listeners of this slider. */
    protected transient ChangeEvent changeEvent;
    private AlcNumberDialog dialog;
    private String title;

    /** Alchemy Custon Spinner
     * 
     * @param sub   If this is a sub-spinner (smaller size) or not
     * @param value The initial value of the spinner
     * @param min   The minimum value of the spinner
     * @param max   The maximum value of the spinner
     * @param step  The difference between elements of the sequence
     */
    AlcSpinnerCustom(String title, boolean sub, int value, int min, int max, int step) {
        this.value = value;
        this.step = step;
        this.title = title;

        // SUB SPINNER - smaller spinner
        if (sub) {
            width = 38;
            height = 15;
            textAreaWidth = 24;
            stringY = 11;
            metrics = getFontMetrics(FONT_SMALLER);
            font = FONT_SMALLER;
            spinner = AlcUtil.getImageIcon("sub-spinner.png").getImage();
            spinnerUp = AlcUtil.getImageIcon("sub-spinner-up.png").getImage();
            spinnerDown = AlcUtil.getImageIcon("sub-spinner-down.png").getImage();

        // NORMAL SPINNER
        } else {
            width = 43;
            height = 24;
            textAreaWidth = 27;
            stringY = 16;
            metrics = getFontMetrics(FONT_MEDIUM);
            font = FONT_MEDIUM;
            spinner = AlcUtil.getImageIcon("spinner.png").getImage();
            spinnerUp = AlcUtil.getImageIcon("spinner-up.png").getImage();
            spinnerDown = AlcUtil.getImageIcon("spinner-down.png").getImage();
        }
        halfHeight = height / 2;

        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        this.addMouseWheelListener(this);
        this.addKeyListener(this);
        this.setFocusable(true);
        this.setOpaque(false);
        this.setPreferredSize(new Dimension(width, height));
        dialog = new AlcNumberDialog(this);

        // Override the number keys to do nothing when the slider is in focus
        Action doNothing = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                //System.out.println("Do Nothing Called");
            }
        };
        this.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_1, 0), "doNothing");
        this.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_2, 0), "doNothing");
        this.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_3, 0), "doNothing");
        this.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_4, 0), "doNothing");
        this.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_5, 0), "doNothing");
        this.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_6, 0), "doNothing");
        this.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_7, 0), "doNothing");
        this.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_8, 0), "doNothing");
        this.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_9, 0), "doNothing");
        this.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_0, 0), "doNothing");
        this.getActionMap().put("doNothing", doNothing);

        setup(min, max);
    }

    public void setup(int min, int max) {
        // Calculate the new value
//        int oldSplit = this.max - this.max;
//        int newSplit = max - min;
//        int divider = (int) ((float) oldSplit / (float) newSplit);
//        int newValue = (int) ((float) value / (float) divider);
        // Set the Values
        this.min = min;
        this.max = max;
        setValue(value);
    }

    public String getTitle() {
        return this.title;
    }

    @Override
    public void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Rounded field with the value and the up/down arrows on the right
        int w = width - 1;
        int h = height - 1;
        g2.setColor((textInput || drag) ? new Color(24, 25, 28) : new Color(30, 32, 35));
        g2.fillRoundRect(0, 0, w, h, 8, 8);
        int half = h / 2;
        g2.setColor(new Color(255, 255, 255, 30));
        if (upPressed) {
            g2.fillRect(textAreaWidth, 1, w - textAreaWidth - 1, half);
        } else if (downPressed) {
            g2.fillRect(textAreaWidth, half, w - textAreaWidth - 1, h - half - 1);
        }
        g2.setColor(new Color(72, 76, 82));
        g2.drawRoundRect(0, 0, w, h, 8, 8);
        g2.drawLine(textAreaWidth, 3, textAreaWidth, h - 3);
        g2.setColor(new Color(190, 193, 198));
        g2.setStroke(new BasicStroke(1.2F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float ax = textAreaWidth + (w - textAreaWidth) / 2F;
        float ay = h / 4F + 0.5F;
        g2.draw(new java.awt.geom.Line2D.Float(ax - 2.5F, ay + 1.2F, ax, ay - 1.2F));
        g2.draw(new java.awt.geom.Line2D.Float(ax, ay - 1.2F, ax + 2.5F, ay + 1.2F));
        ay = h * 3 / 4F;
        g2.draw(new java.awt.geom.Line2D.Float(ax - 2.5F, ay - 1.2F, ax, ay + 1.2F));
        g2.draw(new java.awt.geom.Line2D.Float(ax, ay + 1.2F, ax + 2.5F, ay - 1.2F));

        g2.setColor(COLOR_UI_TEXT);

        String valueString = (ladder != null) ? format(ladder[value]) : Integer.toString(value);

        int stringWidth = metrics.stringWidth(valueString);
        // Centre the text in the middle of the text area
        int stringX = (textAreaWidth - stringWidth) >> 1;

        g2.setFont(font);
        g2.drawString(valueString, stringX, stringY);

    }

    /** Step through these numbers instead of whole numbers - like 0.1 ... 0.9, 1, 2 ... 75 */
    void setLadder(float[] ladder, float value) {
        this.ladder = ladder;
        this.step = 1;
        this.min = 0;
        this.max = ladder.length - 1;
        this.value = nearest(value);
        repaint();
    }

    /** The place in the ladder nearest to a number */
    int nearest(float number) {
        int best = 0;
        for (int i = 1; i < ladder.length; i++) {
            if (Math.abs(ladder[i] - number) < Math.abs(ladder[best] - number)) {
                best = i;
            }
        }
        return best;
    }

    /** The number shown - from the ladder when there is one */
    float getNumber() {
        return (ladder != null) ? ladder[value] : value;
    }

    void setNumber(float number) {
        setValue((ladder != null) ? nearest(number) : Math.round(number));
    }

    /** 0.3 as .3 so it fits the box, whole numbers without a point */
    static String format(float v) {
        if (v == Math.round(v)) {
            return Integer.toString(Math.round(v));
        }
        String s = Float.toString(Math.round(v * 100) / 100F);
        return s.startsWith("0.") ? s.substring(1) : s;
    }

    boolean getValueIsAdjusting() {
        return mouseDown;
    }

    int getValue() {
        return value;
    }

    void setValue(int newValue) {
        if (newValue > max) {
            this.value = max;
        } else if (newValue < min) {
            this.value = min;
        } else {
            this.value = newValue;
        }
        fireStateChanged();
    }

    int getNextValue() {
        if (value + step > max) {
            return max;
        } else {
            return value + step;
        }
    }

    void setNextValue() {
        if (value == max) {
            return;
        } else if (value + step > max) {
            value = max;
            fireStateChanged();
        } else {
            value += step;
            fireStateChanged();
        }
    }

    int getPreviousValue() {
        if (value - step < min) {
            return min;
        } else {
            return value - step;
        }
    }

    void setPreviousValue() {
        if (value == min) {
            return;
        } else if (value - min < min) {
            value = min;
            fireStateChanged();
        } else {
            value -= step;
            fireStateChanged();
        }
    }

    /** Repaint only the left hand number section */
    private void repaintNumber() {
        this.repaint(0, 0, textAreaWidth, height);
    }

    void addChangeListener(ChangeListener listener) {
        listenerList.add(ChangeListener.class, listener);
    }

    void removeChangeListener(ChangeListener listener) {
        listenerList.remove(ChangeListener.class, listener);
    }

    /**
     * This method is called whenever the model fires a ChangeEvent. It should
     * propagate the ChangeEvent to its listeners with a new ChangeEvent that
     * identifies the slider as the source.
     */
    void fireStateChanged() {
        // Repaint here incase the UI element is triggered by a shortcut
        this.repaint();
        Object[] changeListeners = listenerList.getListenerList();
        if (changeEvent == null) {
            changeEvent = new ChangeEvent(this);
        }
        for (int i = changeListeners.length - 2; i >= 0; i -= 2) {
            if (changeListeners[i] == ChangeListener.class) {
                ((ChangeListener) changeListeners[i + 1]).stateChanged(changeEvent);
            }
        }
    }

    private void startTimer(final boolean direction) {
        repeatTimer = new javax.swing.Timer(repeatInterval, new ActionListener() {

            public void actionPerformed(
                    ActionEvent e) {
                if (direction) {
                    setNextValue();
                    repaint();
                } else {
                    setPreviousValue();
                    repaint();
                }
            }
        });
        repeatTimer.setInitialDelay(repeatInitialDelay);
        repeatTimer.start();
    }

    private void stopTimer() {
        if (repeatTimer != null) {
            if (repeatTimer.isRunning()) {
                repeatTimer.stop();
            }
            repeatTimer = null;
        }
    }

    public void mouseClicked(MouseEvent e) {

        // Check for a CTRL click
        boolean showDialog = false;
        if (Alchemy.OS == OS_MAC) {
            if (e.isMetaDown()) {
                showDialog = true;
            }
        } else {
            if (e.isControlDown()) {
                showDialog = true;
            }
        }

        // SHOW THE NUMBER DIALOG
        if (showDialog) {
            if (!Alchemy.preferences.simpleToolBar && ladder == null) {
                dialog.show(this.min, this.max);
            }

        // TEXT INPUT            
        } else if (e.getX() < textAreaWidth) {

            this.requestFocusInWindow(true);
            //System.out.println(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner().getClass().getName());

            textInput = true;
            // Intialise the String to store the text
            text = new String();
            this.repaint();
            // Start a timer to time out if no key input
            keyOffTimer = new javax.swing.Timer(keyOffDelay, new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    textInput = false;
                    repaint();
                }
            });
            keyOffTimer.setRepeats(false);
            keyOffTimer.start();

        }
    }

    public void mousePressed(MouseEvent e) {
        // UP
        if (e.getX() >= textAreaWidth && e.getY() <= halfHeight) {
            setNextValue();
            startTimer(true);
            upPressed = true;
            this.repaint();

        // DOWN    
        } else if (e.getX() >= textAreaWidth && e.getY() > halfHeight) {
            setPreviousValue();
            startTimer(false);
            downPressed = true;
            this.repaint();

        // DRAG
        } else if (e.getX() < textAreaWidth) {
        }
    }

    public void mouseReleased(MouseEvent e) {
        if (upPressed || downPressed) {
            stopTimer();
            downPressed = false;
            upPressed = false;
            this.repaint();
        } else if (drag) {
            drag = false;
            this.repaintNumber();
        }
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mouseDragged(MouseEvent e) {
        // CLICK & DRAG INPUT
        if (drag) {
            // Amount to change by - 4 pixels for one step
            int change = (dragY - e.getY()) / 4;
            this.setValue(dragValue + change * step);
            this.repaintNumber();

        } else {
            if (e.getX() < textAreaWidth) {
                drag = true;
                dragY = e.getY();
                dragValue = value;
            }
        }
    }

    public void mouseMoved(MouseEvent e) {
    }

    public void keyTyped(KeyEvent e) {

        if (textInput) {
            if (keyOffTimer != null) {
                keyOffTimer.restart();
            }
            char c = e.getKeyChar();
            if (ladder != null) {
                // Numbers like 0.5 - typed with a point or a comma
                if (Character.isDigit(c) || ((c == '.' || c == ',') && text.indexOf('.') < 0)) {
                    String typed = text + (Character.isDigit(c) ? Character.toString(c) : ".");
                    try {
                        float number = Float.parseFloat(typed.startsWith(".") ? "0" + typed : typed);
                        if (!typed.endsWith(".")) {
                            setValue(nearest(number));
                        }
                        text = typed;
                        repaintNumber();
                    } catch (NumberFormatException ex) {
                        // ignore
                    }
                }
                return;
            }
            // Check if this is a digit
            if (Character.isDigit(c)) {
                try {
                    String s = Character.toString(c);
                    String numberString = text + s;
                    // Parse the new combined number to an int
                    // If it is not an int, the exception will be caught below
                    int number = Integer.parseInt(numberString);
                    this.setValue(number);
                    // Check the number, incase it was too big or too small
                    // Store it back in the text String to be added to by the next key press
                    text = Integer.toString(value);
                    this.repaintNumber();

                } catch (NumberFormatException ex) {
                    // ignore
                }
            }
        }
    }

    public void keyPressed(KeyEvent e) {
    }

    public void keyReleased(KeyEvent e) {
    }

    public void mouseWheelMoved(MouseWheelEvent e) {
        int scrollAmount = e.getUnitsToScroll() * -1;
        if (scrollAmount != 1) {
            if (scrollAmount != -1) {
                scrollAmount = scrollAmount / 2;
            }
        }
        this.setValue(value + scrollAmount * step);
        this.repaintNumber();
    }
}
