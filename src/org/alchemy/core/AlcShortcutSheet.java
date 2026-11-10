/*
 *  This file is part of the Alkemy project, based on Alchemy - http://al.chemy.org
 *
 *  Alchemy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package org.alchemy.core;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import javax.swing.*;

/**
 * An overview of every shortcut - F1 shows it over the window, F1, Esc or a click closes it.
 * The pen and mouse gestures are listed with the keyboard shortcuts as they are set now,
 * so a shortcut changed in the shortcut settings shows up here as it is.
 */
class AlcShortcutSheet extends JDialog implements AlcConstants {

    private static AlcShortcutSheet sheet;
    private static final Color BG = new Color(24, 25, 28, 246);
    private static final Color KEY_BG = new Color(48, 50, 55);
    private static final Color KEY_EDGE = new Color(70, 73, 79);

    /** Show the overview, or close it when it is already open */
    static void toggle() {
        if (sheet != null && sheet.isVisible()) {
            sheet.close();
            return;
        }
        sheet = new AlcShortcutSheet(Alchemy.window);
        sheet.setVisible(true);
    }

    static boolean isOpen() {
        return sheet != null && sheet.isVisible();
    }

    private AlcShortcutSheet(Window owner) {
        super(owner, Alchemy.bundle.getString("shortcutSheetTitle"), Dialog.ModalityType.MODELESS);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        JPanel content = new JPanel(new BorderLayout(0, 14)) {

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(new Color(60, 62, 68));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.dispose();
            }
        };
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel(Alchemy.bundle.getString("shortcutSheetTitle"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17F));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);
        JLabel hint = new JLabel(Alchemy.bundle.getString("shortcutSheetHint"));
        hint.setFont(FONT_SMALL);
        hint.setForeground(COLOR_UI_TEXT_DIM);
        header.add(hint, BorderLayout.EAST);
        content.add(header, BorderLayout.NORTH);

        // Columns: gestures, then the keyboard shortcuts split evenly
        JPanel columns = new JPanel(new GridLayout(1, 0, 28, 0));
        columns.setOpaque(false);

        JPanel gestures = column();
        group(gestures, "Drawing");
        row(gestures, "Right drag", "Erase whole shapes");
        row(gestures, "Alt + clicks", "Straight lines, let go of Alt to end");
        row(gestures, "Shift + drag", "Solid style: cut out of shapes");
        row(gestures, "Hold Q", "Smooth / Repeat / Blur work while held");
        row(gestures, "Pen pressure", "Sketch menu: size / opacity");
        group(gestures, "Canvas");
        row(gestures, "Space + drag", "Move the canvas");
        row(gestures, "Middle drag", "Move the canvas");
        row(gestures, "Ctrl + wheel", "Zoom");
        row(gestures, "Ctrl + drag", "Zoom");
        row(gestures, "Mirror tab", "Drag to move the mirror axis");
        group(gestures, "Selection (lasso)");
        row(gestures, "Shift + loop", "Add to the selection");
        row(gestures, "Drag frame", "Move, scale, rotate by the knob");
        row(gestures, "Shift", "Rotate in 15 degree steps");
        row(gestures, "Delete", "Delete what is selected");
        row(gestures, "Esc", "Drop the selection");
        columns.add(gestures);

        // Keyboard shortcuts as they are set now, without repeats
        ArrayList<String[]> keys = new ArrayList<String[]>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        // Runs of numbered shortcuts (swatch colour quick keys 0-9) become one line
        java.util.LinkedHashMap<String, ArrayList<String>> numbered = new java.util.LinkedHashMap<String, ArrayList<String>>();
        for (AlcShortcutMapper s : Alchemy.shortcuts.getUserShortcuts()) {
            String key = shortKey(AlcShortcuts.getShortcutString(s.key, s.modifier));
            String name = readable(s.title);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*Quick-Key)\\s*\\d+$").matcher(name);
            if (m.matches()) {
                if (!numbered.containsKey(m.group(1))) {
                    numbered.put(m.group(1), new ArrayList<String>());
                    keys.add(new String[]{null, m.group(1)});
                }
                numbered.get(m.group(1)).add(key);
                continue;
            }
            if (seen.add(key + name)) {
                keys.add(new String[]{key, name});
            }
        }
        for (String[] k : keys) {
            if (k[0] == null) {
                ArrayList<String> all = numbered.get(k[1]);
                k[0] = all.get(0) + " \u2026 " + all.get(all.size() - 1);
                k[1] = k[1].replace("Quick-Key", "quick keys");
            }
        }
        int perColumn = (keys.size() + 2) / 3;
        for (int c = 0; c < 3; c++) {
            JPanel col = column();
            group(col, c == 0 ? "Keyboard" : " ");
            for (int i = c * perColumn; i < Math.min(keys.size(), (c + 1) * perColumn); i++) {
                row(col, keys.get(i)[0], keys.get(i)[1]);
            }
            columns.add(col);
        }
        JScrollPane scroll = new JScrollPane(columns, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        content.add(scroll, BorderLayout.CENTER);
        setContentPane(content);

        // Close with F1, Esc, a click, or when another window is used
        AbstractAction close = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                close();
            }
        };
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeSheet");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "closeSheet");
        getRootPane().getActionMap().put("closeSheet", close);
        content.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                close();
            }
        });
        addWindowFocusListener(new WindowAdapter() {

            @Override
            public void windowLostFocus(WindowEvent e) {
                close();
            }
        });

        pack();
        // Never larger than the window it is shown over
        Rectangle area = owner.getBounds();
        Dimension size = getSize();
        size.width = Math.min(size.width, area.width - 40);
        size.height = Math.min(size.height, area.height - 40);
        setSize(size);
        setLocation(area.x + (area.width - size.width) / 2, area.y + (area.height - size.height) / 2);
    }

    private void close() {
        setVisible(false);
        dispose();
    }

    private static JPanel column() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private static void group(JPanel column, String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_SMALL_BOLD);
        l.setForeground(COLOR_UI_TEXT_DIM);
        l.setBorder(BorderFactory.createEmptyBorder(column.getComponentCount() == 0 ? 0 : 12, 0, 5, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(l);
    }

    private static void row(JPanel column, String key, String what) {
        JPanel r = new JPanel(new BorderLayout(10, 0));
        r.setOpaque(false);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        JLabel k = new JLabel(key) {

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Dimension text = getPreferredSize();
                int w = Math.min(getWidth(), text.width);
                g2.setColor(KEY_BG);
                g2.fillRoundRect(0, 1, w, getHeight() - 2, 8, 8);
                g2.setColor(KEY_EDGE);
                g2.drawRoundRect(0, 1, w - 1, getHeight() - 3, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        k.setFont(FONT_SMALL_BOLD);
        k.setForeground(Color.WHITE);
        k.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
        JPanel keyHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        keyHolder.setOpaque(false);
        keyHolder.add(k);
        keyHolder.setPreferredSize(new Dimension(118, k.getPreferredSize().height));
        r.add(keyHolder, BorderLayout.WEST);
        JLabel w = new JLabel(what);
        w.setFont(FONT_SMALL);
        w.setForeground(COLOR_UI_TEXT);
        r.add(w, BorderLayout.CENTER);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, r.getPreferredSize().height));
        column.add(r);
    }

    /** Short key names - "OPEN BRACKET" is "[", "NUMPAD-1" is "Num 1" */
    private static String shortKey(String key) {
        return key.replace("OPEN BRACKET", "[").replace("CLOSE BRACKET", "]").replace("SEMICOLON", ";")
                .replace("QUOTE", "'").replace("BACK SPACE", "Backspace").replace("BACKSPACE", "Backspace")
                .replace("NUMPAD-", "Num ").replace("NUMPAD ", "Num ").replace("TAB", "Tab").replace("HOME", "Home")
                .replace("DELETE", "Delete").replace("SPACE", "Space").replace("ESCAPE", "Esc")
                .replace("RIGHT", "\u2192").replace("LEFT", "\u2190").replace("UP", "\u2191").replace("DOWN", "\u2193");
    }

    /** "toggleToolBar" -> "Toggle tool bar" for shortcut names that are not translated */
    private static String readable(String title) {
        if (title == null || title.contains(" ") || title.isEmpty()) {
            return title;
        }
        String spaced = title.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
