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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * The file browser of Alchemy - a roomy, resizable dialog in the dark look of the app:
 * details view, places on the left (with Pictures and Downloads), no hidden dot folders,
 * and a preview of the picture picked when opening images.
 */
public class AlcFileChooser extends JFileChooser implements AlcConstants {

    private static final Preferences PREFS = Preferences.userNodeForPackage(AlcFileChooser.class);
    private static final Color LIST_BG = new Color(28, 29, 32);
    private static final Color PLACES_BG = new Color(23, 24, 27);

    static {
        // Wider places on the left so their names are not cut, with larger icons
        UIManager.put("FileChooser.shortcuts.buttonSize", new Dimension(104, 58));
        UIManager.put("FileChooser.shortcuts.iconSize", new Dimension(26, 26));
        UIManager.put("FileChooser.shortcuts.filesFunction", new java.util.function.Function<File[], File[]>() {

            public File[] apply(File[] files) {
                return withPlaces(files);
            }
        });
    }

    public AlcFileChooser() {
        super();
        setup();
    }

    public AlcFileChooser(String currentDirectoryPath) {
        super(currentDirectoryPath);
        setup();
    }

    public AlcFileChooser(File currentDirectory) {
        super(currentDirectory);
        setup();
    }

    private void setup() {
        setFileHidingEnabled(true);
        // Picture preview on the right when opening images
        final Preview preview = new Preview();
        setAccessory(preview);
        addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent e) {
                String name = e.getPropertyName();
                if (SELECTED_FILE_CHANGED_PROPERTY.equals(name)) {
                    preview.show(getSelectedFile());
                } else if (DIALOG_TYPE_CHANGED_PROPERTY.equals(name) || FILE_FILTER_CHANGED_PROPERTY.equals(name)) {
                    preview.setVisible(wantsPreview());
                }
            }
        });
    }

    /** Only pictures get a preview - and only when opening them */
    private boolean wantsPreview() {
        if (getDialogType() != OPEN_DIALOG || getFileSelectionMode() == DIRECTORIES_ONLY) {
            return false;
        }
        javax.swing.filechooser.FileFilter filter = getFileFilter();
        if (filter instanceof FileNameExtensionFilter) {
            for (String ext : ((FileNameExtensionFilter) filter).getExtensions()) {
                if (Preview.isImageName("x." + ext)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /** Desktop, Documents, Pictures, Downloads... - the system places with the picture folders added */
    static File[] withPlaces(File[] files) {
        ArrayList<File> list = new ArrayList<File>();
        if (files != null) {
            for (File f : files) {
                list.add(f);
            }
        }
        String home = System.getProperty("user.home");
        String[] extra = {"Pictures", "Downloads"};
        int at = Math.min(list.size(), 3);
        for (String name : extra) {
            File folder = new File(home, name);
            if (folder.isDirectory() && !list.contains(folder)) {
                list.add(at++, folder);
            }
        }
        return list.toArray(new File[0]);
    }

    /** Dot folders (.config, .cache...) are hidden like the hidden files of the system */
    @Override
    public boolean accept(File f) {
        if (f != null && isFileHidingEnabled() && f.getName().startsWith(".")) {
            return false;
        }
        return super.accept(f);
    }

    @Override
    protected JDialog createDialog(Component parent) throws HeadlessException {
        final JDialog dialog = super.createDialog(parent);
        preview().setVisible(wantsPreview());
        // The details view - name, size, type and date like in the Explorer
        Action details = getActionMap().get("viewTypeDetails");
        if (details != null) {
            details.actionPerformed(null);
        }
        darken(this);
        dialog.setResizable(true);
        dialog.setMinimumSize(new Dimension(560, 380));
        dialog.setSize(PREFS.getInt("Chooser Width", 880), PREFS.getInt("Chooser Height", 560));
        Point p = AlcUtil.calculateCenter(dialog);
        dialog.setLocation(p.x, p.y);
        dialog.addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent e) {
                PREFS.putInt("Chooser Width", dialog.getWidth());
                PREFS.putInt("Chooser Height", dialog.getHeight());
            }
        });
        return dialog;
    }

    private Preview preview() {
        return (Preview) getAccessory();
    }

    /** Darker lists and places, matching the panels of the app */
    private static void darken(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JList || c instanceof JTable || c instanceof JViewport) {
                c.setBackground(LIST_BG);
            } else if (c.getClass().getName().contains("ShortcutsPanel")) {
                c.setBackground(PLACES_BG);
                if (c instanceof JComponent) {
                    ((JComponent) c).setOpaque(true);
                }
            }
            if (c instanceof Container) {
                darken((Container) c);
            }
        }
    }

    /** The dialog as it would be shown - for the tests */
    JDialog createDialogForTest(Component parent) {
        return createDialog(parent);
    }

    /** Display a warning dialog when overwriting a file */
    @Override
    public void approveSelection() {

        if (this.getDialogType() == SAVE_DIALOG) {

            // The extension of the selected format
            String ext;
            javax.swing.filechooser.FileFilter filter = this.getFileFilter();
            if (filter instanceof FileNameExtensionFilter) {
                ext = ((FileNameExtensionFilter) filter).getExtensions()[0];
            } else {
                // Use the first three letters as the extension
                ext = filter.getDescription().substring(0, 3).toLowerCase();
            }
            // Check the extension is properly added
            File fileWithExtension = AlcUtil.addFileExtension(this.getSelectedFile(), ext);

            if (fileWithExtension.exists()) {

                String title, message;
                Object[] options = new Object[2];
                if (Alchemy.OS == OS_MAC) {
                    title = "";
                    message =
                            "<html>" + UIManager.get("OptionPane.css") +
                            "<b>\"" + fileWithExtension.getName() + "\" " + Alchemy.bundle.getString("existsMacDialogTitle") + "</b>" +
                            "<p>" + Alchemy.bundle.getString("existsMacDialogMessage");
                    options[0] = Alchemy.bundle.getString("replace");
                    options[1] = Alchemy.bundle.getString("cancel");
                } else {
                    title = Alchemy.bundle.getString("existsWinDialogTitle");
                    message = "\"" + fileWithExtension.getName() + "\" " + Alchemy.bundle.getString("existsWinDialogMessage");
                    options[0] = Alchemy.bundle.getString("yes");
                    options[1] = Alchemy.bundle.getString("no");
                }


                int result = JOptionPane.showOptionDialog(
                        this,
                        message,
                        title,
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        options[0]);

                if (result != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            this.setSelectedFile(fileWithExtension);
        }
        super.approveSelection();
    }

    /** A small preview of the selected picture with its size in pixels */
    private static class Preview extends JComponent {

        private BufferedImage thumb;
        private String info;
        private javax.swing.SwingWorker<Void, Void> loading;

        Preview() {
            setPreferredSize(new Dimension(190, 190));
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        }

        static boolean isImageName(String name) {
            String n = name.toLowerCase();
            return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") || n.endsWith(".bmp");
        }

        void show(final File file) {
            thumb = null;
            info = null;
            if (loading != null) {
                loading.cancel(true);
            }
            if (file == null || !file.isFile() || !isImageName(file.getName()) || !isVisible()) {
                repaint();
                return;
            }
            // Read in the background - big pictures must not stop the dialog
            loading = new javax.swing.SwingWorker<Void, Void>() {
                BufferedImage small;
                String text;

                @Override
                protected Void doInBackground() throws Exception {
                    BufferedImage image = javax.imageio.ImageIO.read(file);
                    if (image == null || isCancelled()) {
                        return null;
                    }
                    double s = Math.min(1, Math.min(170.0 / image.getWidth(), 150.0 / image.getHeight()));
                    int w = Math.max(1, (int) Math.round(image.getWidth() * s)), h = Math.max(1, (int) Math.round(image.getHeight() * s));
                    small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = small.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(image, 0, 0, w, h, null);
                    g.dispose();
                    text = image.getWidth() + " × " + image.getHeight() + " px";
                    return null;
                }

                @Override
                protected void done() {
                    if (!isCancelled()) {
                        thumb = small;
                        info = text;
                        repaint();
                    }
                }
            };
            loading.execute();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int x = 10, w = getWidth() - 10, h = getHeight();
            g2.setColor(LIST_BG);
            g2.fillRoundRect(x, 0, w, h, 10, 10);
            if (thumb != null) {
                int tx = x + (w - thumb.getWidth()) / 2, ty = (h - 24 - thumb.getHeight()) / 2;
                // Checkers behind transparent pictures
                g2.setClip(tx, ty, thumb.getWidth(), thumb.getHeight());
                for (int cy = ty; cy < ty + thumb.getHeight(); cy += 8) {
                    for (int cx = tx; cx < tx + thumb.getWidth(); cx += 8) {
                        g2.setColor((((cx - tx) / 8 + (cy - ty) / 8) % 2 == 0) ? new Color(70, 72, 76) : new Color(54, 56, 60));
                        g2.fillRect(cx, cy, 8, 8);
                    }
                }
                g2.setClip(null);
                g2.drawImage(thumb, tx, ty, null);
            }
            g2.setFont(FONT_SMALL);
            g2.setColor(COLOR_UI_TEXT_DIM);
            String text = info != null ? info : Alchemy.bundle.getString("chooserNoPreview");
            FontMetrics m = g2.getFontMetrics();
            g2.drawString(text, x + (w - m.stringWidth(text)) / 2, info != null ? h - 10 : h / 2);
            g2.dispose();
        }
    }
}
