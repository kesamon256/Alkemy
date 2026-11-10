/*
 *  This file is part of the Alchemy project - http://al.chemy.org
 * 
 *  Copyright (c) 2007-2010 Karl D.D. Willis
 *  Copyright (c) 2009 Steren Giannini (steren.giannini@gmail.com)
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
import java.awt.event.*;
import java.awt.print.*;
import java.io.File;
import java.io.IOException;
import javax.swing.*;

/** 
 * Menubar for Alchemy
 * Housing the usual file, print etc... commands 
 */
class AlcMenuBar extends JMenuBar implements AlcConstants {

    private final static int height = 27;
    private File platformAppDir;
    private PrinterJob printer = null;
    private PageFormat page = null;
    private PageFormat defaultPage = null;
    /** Session stuff global so it can be enabled/disabled */
    private AlcMenuItem nextPageItem,  previousPageItem,  unloadSessionItem;
    /** Unload Background Image - Disabled from Canvas */
    AlcMenuItem unloadBackgroundImageItem;
    private AlcCheckBoxMenuItem linkSessionItem;
    /** Fullscreen toggle global so it can be set on startup */
    AlcCheckBoxMenuItem fullScreenItem, transparentItem;
    /** Export dialog to set image size */
    private AlcExportDialog exportDialog;

    /** Creates a new instance of AlcMenuBar */
    AlcMenuBar() {

        // Default applications directory depending on the platform
        switch (Alchemy.OS) {
            case OS_MAC:
                platformAppDir = new File(File.separator + "Applications");
                this.setBorderPainted(false);
                break;
            case OS_WINDOWS:
                platformAppDir = new File(File.separator + "Program Files");
                break;
            case OS_LINUX:
                platformAppDir = new File(File.separator + "usr" + File.separator + "bin");
                this.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
                break;
        }
        this.setOpaque(true);
        this.setBackground(COLOR_UI_MENUBAR);
        this.setBorderPainted(false);

        // Recording interval array in milliseconds
        int[] recordingInterval = {500, 1000, 5000, 15000, 30000, 60000, 120000, 300000, 600000};
        // Recording interval array in readable form
        String[] recordingIntervalString = new String[recordingInterval.length];
        // Initialise the array of recording intervals from the bundle
        for (int i = 0; i < recordingIntervalString.length; i++) {
            recordingIntervalString[i] = getS("interval" + recordingInterval[i]);
        }

        //////////////////////////////////////////////////////////////
        // FILE MENU
        //////////////////////////////////////////////////////////////
        AlcMenu fileMenu = new AlcMenu(getS("fileTitle"));

        // New
        String newTitle = getS("newTitle");
        AbstractAction newAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {

                // A new drawing starts again with one empty layer
                if (Alchemy.canvas.hasAnyShapes() || Alchemy.canvas.getLayers().size() > 1) {

                    boolean result = AlcUtil.showConfirmDialogFromBundle("newWinDialogTitle", "newWinDialogMessage", "newMacDialogTitle", "newMacDialogMessage");
                    if (result) {
                        Alchemy.canvas.newDrawing();
                    }
                }
            }
        };
        AlcMenuItem newItem = new AlcMenuItem(newAction);
        // Shortcut - Modifier n
        int newKey = Alchemy.shortcuts.setShortcut(newItem, KeyEvent.VK_N, "newTitle", newAction, KEY_MODIFIER);
        newItem.setup(newTitle, newKey);
        fileMenu.add(newItem);

        // Open / Save an editable sketch - Modifier Shift o / Modifier Shift s
        int sketchModifier = KEY_MODIFIER | InputEvent.SHIFT_MASK;
        AbstractAction openSketchAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                openSketch();
            }
        };
        AlcMenuItem openSketchItem = new AlcMenuItem(openSketchAction);
        int openSketchKey = Alchemy.shortcuts.setShortcut(openSketchItem, KeyEvent.VK_O, "openSketchTitle", openSketchAction, sketchModifier);
        openSketchItem.setup(getS("openSketchTitle"));
        openSketchItem.refreshShortcut(openSketchKey, sketchModifier);
        fileMenu.add(openSketchItem);

        AbstractAction saveSketchAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                saveSketch();
            }
        };
        AlcMenuItem saveSketchItem = new AlcMenuItem(saveSketchAction);
        int saveSketchKey = Alchemy.shortcuts.setShortcut(saveSketchItem, KeyEvent.VK_S, "saveSketchTitle", saveSketchAction, sketchModifier);
        saveSketchItem.setup(getS("saveSketchTitle"));
        saveSketchItem.refreshShortcut(saveSketchKey, sketchModifier);
        fileMenu.add(saveSketchItem);

        fileMenu.add(new JSeparator());

        // Export
        String exportTitle = getS("exportTitle");
        AbstractAction exportAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                export();
            }
        };
        AlcMenuItem exportItem = new AlcMenuItem(exportAction);
        // Shortcut - Modifier e
        int exportKey = Alchemy.shortcuts.setShortcut(exportItem, KeyEvent.VK_E, "exportTitle", exportAction, KEY_MODIFIER);
        exportItem.setup(exportTitle, exportKey);

        fileMenu.add(exportItem);

        fileMenu.add(new JSeparator());

        // Page Setup
        AbstractAction pageAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                pageSetup();
            }
        };
        AlcMenuItem pageItem = new AlcMenuItem(pageAction);
        pageItem.setup(getS("pageSetupTitle"));
        fileMenu.add(pageItem);

        // Print
        String printTitle = getS("printTitle");
        AbstractAction printAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                print();
            }
        };
        AlcMenuItem printItem = new AlcMenuItem(printAction);
        // Shortcut - Modifier p
        int printKey = Alchemy.shortcuts.setShortcut(printItem, KeyEvent.VK_P, "printTitle", printAction, KEY_MODIFIER);
        printItem.setup(printTitle, printKey);
        fileMenu.add(printItem);


        // Exit - not included on a MAC
        if (Alchemy.OS != OS_MAC) {
            fileMenu.add(new JSeparator());

            AbstractAction exitAction = new AbstractAction() {

                public void actionPerformed(ActionEvent e) {
                    Alchemy.window.exitAlchemy();
                }
            };

            AlcMenuItem exitItem = new AlcMenuItem(exitAction);
            exitItem.setup(getS("exitTitle"));
            fileMenu.add(exitItem);
        }

        this.add(fileMenu);

        //////////////////////////////////////////////////////////////
        // EDIT MENU
        //////////////////////////////////////////////////////////////

        AlcMenu editMenu = new AlcMenu(getS("editTitle"));

        // Undo - Modifier z
        AlcMenuItem undoItem = new AlcMenuItem(Alchemy.history.undoAction);
        int undoKey = Alchemy.shortcuts.setShortcut(undoItem, KeyEvent.VK_Z, "undoTitle", Alchemy.history.undoAction, KEY_MODIFIER);
        undoItem.setup(getS("undoTitle"), undoKey);
        editMenu.add(undoItem);

        // Redo - Modifier Shift z, with Modifier y as an alternative
        int redoModifier = KEY_MODIFIER | InputEvent.SHIFT_MASK;
        AlcMenuItem redoItem = new AlcMenuItem(Alchemy.history.redoAction);
        int redoKey = Alchemy.shortcuts.setShortcut(redoItem, KeyEvent.VK_Z, "redoTitle", Alchemy.history.redoAction, redoModifier);
        redoItem.setup(getS("redoTitle"));
        redoItem.refreshShortcut(redoKey, AlcPreferences.prefs.getInt(Alchemy.bundleEn.getString("redoTitle") + " Modifier", redoModifier));
        editMenu.add(redoItem);

        // Select all shapes of the active layer - Modifier a
        AbstractAction selectAllAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.canvas.selectAll();
            }
        };
        AlcMenuItem selectAllItem = new AlcMenuItem(selectAllAction);
        int selectAllKey = Alchemy.shortcuts.setShortcut(selectAllItem, KeyEvent.VK_A, "selectAllTitle", selectAllAction, KEY_MODIFIER);
        selectAllItem.setup(getS("selectAllTitle"), selectAllKey);
        editMenu.add(selectAllItem);
        KeyStroke redoAlternative = KeyStroke.getKeyStroke(KeyEvent.VK_Y, KEY_MODIFIER);
        for (JRootPane rootPane : new JRootPane[]{Alchemy.window.getRootPane(), Alchemy.palette.getRootPane()}) {
            rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(redoAlternative, "redoAlternative");
            rootPane.getActionMap().put("redoAlternative", Alchemy.history.redoAction);
        }

        editMenu.add(new JSeparator());

        // Copy
        String copyTitle = getS("copyTitle");
        AbstractAction copyAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                copy();
            }
        };
        AlcMenuItem copyItem = new AlcMenuItem(copyAction);
        // Shortcut - Modifier c
        int copyKey = Alchemy.shortcuts.setShortcut(copyItem, KeyEvent.VK_C, "copyTitle", copyAction, KEY_MODIFIER);
        copyItem.setup(copyTitle, copyKey);
        editMenu.add(copyItem);

        
        editMenu.add(new JSeparator());


        // Flip Horizontal
        String flipHorizontalTitle = getS("flipHorizontal");
        AbstractAction flipHorizontalAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.canvas.flipHorizontal();
            }
        };
        AlcMenuItem flipHorizontalItem = new AlcMenuItem(flipHorizontalAction);
        // Shortcut - Modifier ;
        int flipHorizontalKey = Alchemy.shortcuts.setShortcut(flipHorizontalItem, KeyEvent.VK_SEMICOLON, "flipHorizontal", flipHorizontalAction, KEY_MODIFIER);
        flipHorizontalItem.setup(flipHorizontalTitle, flipHorizontalKey);
        editMenu.add(flipHorizontalItem);

        // Flip Vertical
        String flipVerticalTitle = getS("flipVertical");
        AbstractAction flipVerticalAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.canvas.flipVertical();
            }
        };
        AlcMenuItem flipVerticalItem = new AlcMenuItem(flipVerticalAction);
        // Shortcut - Modifier '
        int flipVerticalKey = Alchemy.shortcuts.setShortcut(flipVerticalItem, KeyEvent.VK_QUOTE, "flipVertical", flipVerticalAction, KEY_MODIFIER);
        flipVerticalItem.setup(flipVerticalTitle, flipVerticalKey);
        editMenu.add(flipVerticalItem);

        this.add(editMenu);

        //////////////////////////////////////////////////////////////
        // SKETCH MENU
        //////////////////////////////////////////////////////////////
        AlcMenu sketchMenu = new AlcMenu(getS("sketchTitle"));

        // Pen pressure on/off and which tablet interface is in use
        final JLabel tabletLabel = (JLabel) sectionLabel("");
        sketchMenu.add(tabletLabel);
        sketchMenu.addMenuListener(new javax.swing.event.MenuListener() {

            public void menuSelected(javax.swing.event.MenuEvent e) {
                String status = Alchemy.canvas.getTabletStatus();
                tabletLabel.setText(getS("tabletTitle") + ": " + (status == null ? getS("tabletNone") : status));
            }

            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        final JCheckBoxMenuItem pressureItem = new JCheckBoxMenuItem(getS("pressureTitle"), Alchemy.canvas.isPressureEnabled());
        pressureItem.setFont(FONT_MEDIUM);
        sketchMenu.add(pressureItem);
        // What the pressure changes - the size of the brush, its opacity from faint to full, or both
        final JCheckBoxMenuItem pressureSizeItem = new JCheckBoxMenuItem(getS("pressureSizeTitle"), Alchemy.canvas.isPressureSize());
        final JCheckBoxMenuItem pressureOpacityItem = new JCheckBoxMenuItem(getS("pressureOpacityTitle"), Alchemy.canvas.isPressureOpacity());
        for (final JCheckBoxMenuItem item : new JCheckBoxMenuItem[]{pressureSizeItem, pressureOpacityItem}) {
            item.setFont(FONT_MEDIUM);
            item.setEnabled(Alchemy.canvas.isPressureEnabled());
            item.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    Alchemy.canvas.setPressureEffects(pressureSizeItem.isSelected(), pressureOpacityItem.isSelected());
                }
            });
            sketchMenu.add(item);
        }
        // Effects that follow the speed of the hand - only while Q is held, or always
        final JCheckBoxMenuItem motionItem = new JCheckBoxMenuItem(getS("motionHoldTitle"), Alchemy.canvas.isMotionHold());
        motionItem.setFont(FONT_MEDIUM);
        motionItem.setToolTipText(getS("motionHoldDescription"));
        motionItem.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.canvas.setMotionHold(motionItem.isSelected());
            }
        });
        sketchMenu.add(motionItem);
        // The tool bar switches change the same settings - show them here too
        Alchemy.canvas.addPressureListener(new Runnable() {

            public void run() {
                pressureItem.setSelected(Alchemy.canvas.isPressureEnabled());
                pressureSizeItem.setSelected(Alchemy.canvas.isPressureSize());
                pressureOpacityItem.setSelected(Alchemy.canvas.isPressureOpacity());
                pressureSizeItem.setEnabled(Alchemy.canvas.isPressureEnabled());
                pressureOpacityItem.setEnabled(Alchemy.canvas.isPressureEnabled());
            }
        });
        pressureItem.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.canvas.setPressureEnabled(pressureItem.isSelected());
                pressureSizeItem.setEnabled(pressureItem.isSelected());
                pressureOpacityItem.setEnabled(pressureItem.isSelected());
            }
        });

        // Floating colour wheel panel - F6 as in Photoshop
        final JCheckBoxMenuItem colorPanelItem = new JCheckBoxMenuItem(getS("colorPanelTitle"));
        colorPanelItem.setFont(FONT_MEDIUM);
        AbstractAction colorPanelAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                AlcColorPanel.togglePanel();
            }
        };
        colorPanelItem.addActionListener(colorPanelAction);
        int colorPanelKey = Alchemy.shortcuts.setShortcut(colorPanelItem, KeyEvent.VK_F6, "colorPanelTitle", colorPanelAction);
        colorPanelItem.setAccelerator(KeyStroke.getKeyStroke(colorPanelKey, 0));
        sketchMenu.addMenuListener(new javax.swing.event.MenuListener() {

            public void menuSelected(javax.swing.event.MenuEvent e) {
                colorPanelItem.setSelected(AlcColorPanel.isPanelOpen());
            }

            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        sketchMenu.add(colorPanelItem);

        // Floating layers panel - F7
        final JCheckBoxMenuItem layersPanelItem = new JCheckBoxMenuItem(getS("layersTitle"));
        layersPanelItem.setFont(FONT_MEDIUM);
        AbstractAction layersPanelAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                AlcLayersPanel.togglePanel();
            }
        };
        layersPanelItem.addActionListener(layersPanelAction);
        int layersPanelKey = Alchemy.shortcuts.setShortcut(layersPanelItem, KeyEvent.VK_F7, "layersTitle", layersPanelAction);
        layersPanelItem.setAccelerator(KeyStroke.getKeyStroke(layersPanelKey, 0));
        sketchMenu.addMenuListener(new javax.swing.event.MenuListener() {

            public void menuSelected(javax.swing.event.MenuEvent e) {
                layersPanelItem.setSelected(AlcLayersPanel.isPanelOpen());
            }

            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        sketchMenu.add(layersPanelItem);

        // New layer - Modifier Shift n, as in Photoshop
        AbstractAction newLayerAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.canvas.addLayer();
            }
        };
        AlcMenuItem newLayerItem = new AlcMenuItem(newLayerAction);
        int newLayerModifier = KEY_MODIFIER | InputEvent.SHIFT_MASK;
        int newLayerKey = Alchemy.shortcuts.setShortcut(newLayerItem, KeyEvent.VK_N, "layerNewTitle", newLayerAction, newLayerModifier);
        newLayerItem.setup(getS("layerNewTitle"));
        newLayerItem.refreshShortcut(newLayerKey, newLayerModifier);
        sketchMenu.add(newLayerItem);

        sketchMenu.add(new JSeparator());

        // Stabilizer strength
        sketchMenu.add(sectionLabel(getS("stabilizerTitle")));
        String[] stabilizerNames = {getS("stabilizerOff"), getS("stabilizerLow"), getS("stabilizerMedium"), getS("stabilizerHigh")};
        final float[] stabilizerValues = {0F, 0.5F, 0.75F, 0.88F};
        ButtonGroup stabilizerGroup = new ButtonGroup();
        for (int i = 0; i < stabilizerNames.length; i++) {
            final float value = stabilizerValues[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(stabilizerNames[i]);
            item.setFont(FONT_MEDIUM);
            item.setSelected(Math.abs(Alchemy.canvas.getStabilizer() - value) < 0.01F);
            item.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    Alchemy.canvas.setStabilizer(value);
                }
            });
            stabilizerGroup.add(item);
            sketchMenu.add(item);
        }

        sketchMenu.add(new JSeparator());

        // Paper pattern
        sketchMenu.add(sectionLabel(getS("paperTitle")));
        String[] paperNames = {getS("paperBlank"), getS("paperGrid"), getS("paperDots"), getS("paperLines")};
        ButtonGroup paperGroup = new ButtonGroup();
        for (int i = 0; i < paperNames.length; i++) {
            final int paper = i;
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(paperNames[i]);
            item.setFont(FONT_MEDIUM);
            item.setSelected(Alchemy.canvas.getPaper() == paper);
            item.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    Alchemy.canvas.setPaper(paper);
                }
            });
            paperGroup.add(item);
            sketchMenu.add(item);
        }

        sketchMenu.add(new JSeparator());
        sketchMenu.add(sectionLabel(getS("eraserHint")));

        this.add(sketchMenu);


        //////////////////////////////////////////////////////////////
        // VIEW MENU
        //////////////////////////////////////////////////////////////
        AlcMenu viewMenu = new AlcMenu(getS("viewTitle"));

        // Fullscreen
        String fullScreenTitle = getS("fullScreenTitle");
        fullScreenItem = new AlcCheckBoxMenuItem();
        AbstractAction fullScreenAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                String source = e.getSource().getClass().getName();
                if (!source.equals("org.alchemy.core.AlcCheckBoxMenuItem")) {
                    fullScreenItem.setState(!fullScreenItem.getState());
                }
                Alchemy.window.setFullscreen(!Alchemy.window.isFullscreen());
            }
        };

        fullScreenItem.setAction(fullScreenAction);
        // Shortcut - Modifier f
        int fullScreenKey = Alchemy.shortcuts.setShortcut(fullScreenItem, KeyEvent.VK_F, "fullScreenTitle", fullScreenAction, KEY_MODIFIER);
        fullScreenItem.setup(fullScreenTitle, fullScreenKey);
        viewMenu.add(fullScreenItem);


        // Transparent Fullscreen
        String transparentTitle = getS("transparentTitle");
        transparentItem = new AlcCheckBoxMenuItem();
        if (Alchemy.window.isTransparent()) {
            transparentItem.setSelected(true);
        }
        AbstractAction transparentAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                String source = e.getSource().getClass().getName();
                if (!source.equals("org.alchemy.core.AlcCheckBoxMenuItem")) {
                    transparentItem.setState(!transparentItem.getState());
                }
                Alchemy.window.setTransparent(!Alchemy.window.isTransparent());
            }
        };

        transparentItem.setAction(transparentAction);
        // Shortcut - Modifier t
        int transparentKey = Alchemy.shortcuts.setShortcut(transparentItem, KeyEvent.VK_T, "transparentTitle", transparentAction, KEY_MODIFIER);
        transparentItem.setup(transparentTitle, transparentKey);
        viewMenu.add(transparentItem);

        viewMenu.add(new JSeparator());

        // Load Background Image
        String loadBackgroundImageTitle = getS("loadBackgroundImageTitle");
        AlcMenuItem loadBackgroundImageItem = new AlcMenuItem(loadBackgroundImageTitle);
        AbstractAction loadBackgroundImageAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {

                File file = AlcUtil.showFileChooser(new File(DIR_DESKTOP), false);
                if (file != null && file.exists()) {
                    Image image = null;
                    try {
                        image = AlcUtil.getImage(file.toURI().toURL());
                    } catch (Exception ex) {
                        // Ignore
                        }
                    if (image != null) {
                        Rectangle visibleRect = Alchemy.canvas.getVisibleRect();
                        int x = (visibleRect.width - image.getWidth(null)) / 2;
                        int y = (visibleRect.height - image.getHeight(null)) / 2;
                        Alchemy.canvas.setImageLocation(x, y);
                        Alchemy.canvas.setImageDisplay(true);
                        Alchemy.canvas.setImage(AlcUtil.getBufferedImage(image));
                        Alchemy.canvas.redraw();

                    } else {
                        AlcUtil.showConfirmDialogFromBundle("imageErrorDialogTitle", "imageErrorDialogMessage");
                    }
                } else {
                    System.out.println("Error reading the file...");
                }

            }
        };
        loadBackgroundImageItem.setAction(loadBackgroundImageAction);
        // Shortcut - Modifier l
        int loadBackgroundImageKey = Alchemy.shortcuts.setShortcut(loadBackgroundImageItem, KeyEvent.VK_L, "loadBackgroundImageTitle", loadBackgroundImageAction, KEY_MODIFIER);
        loadBackgroundImageItem.setup(loadBackgroundImageTitle, loadBackgroundImageKey);
        viewMenu.add(loadBackgroundImageItem);

        // Unload Background Image
        AbstractAction unloadBackgroundImageAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {

                Alchemy.canvas.setImageLocation(0, 0);
                Alchemy.canvas.setImageDisplay(false);
                Alchemy.canvas.setImage(null);
                Alchemy.canvas.redraw();
            }
        };
        unloadBackgroundImageItem = new AlcMenuItem(unloadBackgroundImageAction);
        unloadBackgroundImageItem.setup(getS("unloadBackgroundImageTitle"));
        unloadBackgroundImageItem.setEnabled(false);

        viewMenu.add(unloadBackgroundImageItem);

        this.add(viewMenu);

        //////////////////////////////////////////////////////////////
        // SESSION MENU
        //////////////////////////////////////////////////////////////
        AlcMenu sessionMenu = new AlcMenu(getS("sessionTitle"));

        // Save PDF page
        String savePageTitle = getS("savePageTitle");
        AbstractAction savePageAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.session.manualSavePage();
            }
        };
        AlcMenuItem savePageItem = new AlcMenuItem(savePageAction);
        // Shortcut - Modifier s
        int savePageKey = Alchemy.shortcuts.setShortcut(savePageItem, KeyEvent.VK_S, "savePageTitle", savePageAction, KEY_MODIFIER);
        savePageItem.setup(savePageTitle, savePageKey);


        sessionMenu.add(savePageItem);

        // Save and clear PDF page
        String saveClearTitle = getS("saveClearTitle");
        AbstractAction saveClearPageAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.session.manualSaveClearPage();
            }
        };
        AlcMenuItem saveClearPageItem = new AlcMenuItem(saveClearPageAction);
        int saveClearKey = Alchemy.shortcuts.setShortcut(saveClearPageItem, KeyEvent.VK_D, "saveClearTitle", saveClearPageAction, KEY_MODIFIER);
        saveClearPageItem.setup(saveClearTitle, saveClearKey);
        sessionMenu.add(saveClearPageItem);

        sessionMenu.add(new JSeparator());

        // Recording
        String recordingTitle = getS("recordingTitle");
        final AlcCheckBoxMenuItem recordingItem = new AlcCheckBoxMenuItem();
        AbstractAction recordingAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                // If the source is from the key then we need to change the state of the menu item as well
                String source = e.getSource().getClass().getName();
                if (!source.equals("org.alchemy.core.AlcCheckBoxMenuItem")) {
                    //if (e.getActionCommand().equals("r")) {
                    recordingItem.setState(!recordingItem.getState());
                }
                Alchemy.session.setRecording(recordingItem.getState());
                System.out.println("STATE: " + recordingItem.getState());

            }
        };
        recordingItem.setAction(recordingAction);
        // Shortcut - Modifier r
        int recordingKey = Alchemy.shortcuts.setShortcut(recordingItem, KeyEvent.VK_R, "recordingTitle", recordingAction, KEY_MODIFIER);
        recordingItem.setup(recordingTitle, recordingKey);

        recordingItem.setState(Alchemy.preferences.sessionRecordingState);
        if (Alchemy.preferences.sessionRecordingState) {
            Alchemy.session.setRecording(true);
        }
        sessionMenu.add(recordingItem);

        // Interval submenu
        AlcMenu intervalMenu = new AlcMenu(getS("recordIntervalTitle"));
        // Set the opacity and color of this to overide the defaults used for the top menus
        if (Alchemy.OS != OS_LINUX) {
            intervalMenu.setOpaque(true);
            intervalMenu.setBackground(AlcToolBar.COLOR_UI_HIGHLIGHT);
        }
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < recordingIntervalString.length; i++) {
            AlcRadioButtonMenuItem intervalItem = new AlcRadioButtonMenuItem(recordingInterval[i], recordingIntervalString[i]);
            // Set the default value to selected
            if (Alchemy.preferences.sessionRecordingInterval == recordingInterval[i]) {
                intervalItem.setSelected(true);
            }
            intervalItem.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {

                    AlcRadioButtonMenuItem source = (AlcRadioButtonMenuItem) e.getSource();
                    Alchemy.session.setTimerInterval(source.getIndex());

                }
            });
            group.add(intervalItem);
            intervalMenu.add(intervalItem);
        }
        intervalMenu.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 5));
        sessionMenu.add(intervalMenu);

        //sessionMenu.add(new JSeparator());

        // Auto Clear
        AbstractAction autoClearAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                AlcCheckBoxMenuItem source = (AlcCheckBoxMenuItem) e.getSource();
                Alchemy.preferences.sessionAutoClear = source.getState();
            }
        };
        AlcCheckBoxMenuItem autoClearItem = new AlcCheckBoxMenuItem(autoClearAction);
        autoClearItem.setup(getS("autoClearTitle"));
        autoClearItem.setState(Alchemy.preferences.sessionAutoClear);
        sessionMenu.add(autoClearItem);

        sessionMenu.add(new JSeparator());

        // Load Session PDF
        final String loadSessionTitle = getS("loadSessionTitle");
        AbstractAction loadSessionAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                File file = AlcUtil.showFileChooser(new File(DIR_DESKTOP), false);
                if (file != null && file.exists()) {
                    boolean loaded = Alchemy.session.loadSessionFile(file);
                    nextPageItem.setEnabled(loaded);
                    previousPageItem.setEnabled(loaded);
                    unloadSessionItem.setEnabled(loaded);
                    linkSessionItem.setEnabled(loaded);
                }
            }
        };
        AlcMenuItem loadSessionItem = new AlcMenuItem(loadSessionAction);
        loadSessionItem.setup(loadSessionTitle);
        sessionMenu.add(loadSessionItem);

        // Next Page
        final String nextPageTitle = getS("nextPageTitle");
        AbstractAction nextPageAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.session.nextPage();
            }
        };
        nextPageItem = new AlcMenuItem(nextPageAction);
        int nextPageKey = Alchemy.shortcuts.setShortcut(nextPageItem, KeyEvent.VK_RIGHT, "nextPageTitle", nextPageAction, KEY_MODIFIER);
        nextPageItem.setup(nextPageTitle, nextPageKey);
        sessionMenu.add(nextPageItem);
        nextPageItem.setEnabled(false);

        // Previous Page
        final String previousPageTitle = getS("previousPageTitle");
        AbstractAction previousPageAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.session.previousPage();
            }
        };
        previousPageItem = new AlcMenuItem(previousPageAction);
        int previousPageKey = Alchemy.shortcuts.setShortcut(previousPageItem, KeyEvent.VK_LEFT, "previousPageTitle", previousPageAction, KEY_MODIFIER);
        previousPageItem.setup(previousPageTitle, previousPageKey);
        sessionMenu.add(previousPageItem);
        previousPageItem.setEnabled(false);

        // Unload Session PDF
        final String unloadSessionTitle = getS("unloadSessionTitle");
        AbstractAction unloadSessionAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.session.unloadSessionFile();
                nextPageItem.setEnabled(false);
                previousPageItem.setEnabled(false);
                unloadSessionItem.setEnabled(false);
                linkSessionItem.setEnabled(false);
            }
        };
        unloadSessionItem = new AlcMenuItem(unloadSessionAction);
        unloadSessionItem.setup(unloadSessionTitle);
        sessionMenu.add(unloadSessionItem);
        unloadSessionItem.setEnabled(false);


        // Link Session
        String linkSessionTitle = getS("linkSessionTitle");
        linkSessionItem = new AlcCheckBoxMenuItem();
        AbstractAction linkSessionAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                AlcCheckBoxMenuItem source = (AlcCheckBoxMenuItem) e.getSource();
                Alchemy.preferences.sessionLink = source.getState();
            }
        };

        linkSessionItem.setAction(linkSessionAction);
        linkSessionItem.setup(linkSessionTitle);
        linkSessionItem.setSelected(Alchemy.preferences.sessionLink);
        sessionMenu.add(linkSessionItem);
        linkSessionItem.setEnabled(false);

        sessionMenu.add(new JSeparator());

        // Restart Session
        String restartTitle = getS("restartTitle");
        AbstractAction restartAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.session.restartSession();
            }
        };
        AlcMenuItem restartItem = new AlcMenuItem(restartAction);
        restartItem.setup(restartTitle);
        sessionMenu.add(restartItem);

        this.add(sessionMenu);


        //////////////////////////////////////////////////////////////
        // SWITCH MENU
        //////////////////////////////////////////////////////////////
        AlcMenu switchMenu = new AlcMenu(getS("switchTitle"));

        // Switch Vector
        String switchVectorTitle = getS("switchVectorTitle");
        AbstractAction switchVectorAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                switchVector();
            }
        };
        AlcMenuItem switchVectorItem = new AlcMenuItem(switchVectorAction);
        // Shortcut - Modifier v
        int vectorKey = Alchemy.shortcuts.setShortcut(switchVectorItem, KeyEvent.VK_V, "switchVectorTitle", switchVectorAction, KEY_MODIFIER);
        switchVectorItem.setup(switchVectorTitle, vectorKey);
        switchMenu.add(switchVectorItem);

        // Switch Bitmaps
        String switchBitmapTitle = getS("switchBitmapTitle");
        AbstractAction switchBitmapAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                switchBitmap();
            }
        };
        AlcMenuItem switchBitmapItem = new AlcMenuItem(switchBitmapAction);
        // Shortcut - Modifier v
        int bitmapKey = Alchemy.shortcuts.setShortcut(switchBitmapItem, KeyEvent.VK_B, "switchBitmapTitle", switchBitmapAction, KEY_MODIFIER);
        switchBitmapItem.setup(switchBitmapTitle, bitmapKey);
        switchMenu.add(switchBitmapItem);

        switchMenu.add(new JSeparator());

        // Switch Vector App
        final String setVectorApp = getS("setVectorApp");
        AbstractAction switchVectorAppAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                File file = AlcUtil.showFileChooser(platformAppDir);
                if (file != null) {
                    System.out.println(file.toString());
                    Alchemy.preferences.switchVectorApp = file.toString();
                }
            }
        };
        AlcMenuItem switchVectorAppItem = new AlcMenuItem(switchVectorAppAction);
        switchVectorAppItem.setup(setVectorApp);
        switchMenu.add(switchVectorAppItem);

        // Format submenu
        AlcMenu formatMenu = new AlcMenu(getS("setVectorFormat"));
        if (Alchemy.OS != OS_LINUX) {
            formatMenu.setOpaque(true);
            formatMenu.setBackground(AlcToolBar.COLOR_UI_HIGHLIGHT);
        }
        ButtonGroup formatGroup = new ButtonGroup();
        //PDF
        AlcRadioButtonMenuItem pdfItem = new AlcRadioButtonMenuItem(Alchemy.preferences.FORMAT_PDF, "PDF");
        if (Alchemy.preferences.switchVectorFormat == Alchemy.preferences.FORMAT_PDF) {
            pdfItem.setSelected(true);
        }
        pdfItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                AlcRadioButtonMenuItem source = (AlcRadioButtonMenuItem) e.getSource();
                Alchemy.preferences.switchVectorFormat = source.getIndex();
            }
        });
        formatGroup.add(pdfItem);
        formatMenu.add(pdfItem);
        //SVG
        AlcRadioButtonMenuItem svgItem = new AlcRadioButtonMenuItem(Alchemy.preferences.FORMAT_SVG, "SVG");
        if (Alchemy.preferences.switchVectorFormat == Alchemy.preferences.FORMAT_SVG) {
            svgItem.setSelected(true);
        }
        svgItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                AlcRadioButtonMenuItem source = (AlcRadioButtonMenuItem) e.getSource();
                Alchemy.preferences.switchVectorFormat = source.getIndex();
            }
        });
        formatGroup.add(svgItem);
        formatMenu.add(svgItem);
        
        formatMenu.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 5));
        switchMenu.add(formatMenu);


        // Switch Bitmap App
        final String setBitmapApp = getS("setBitmapApp");
        AbstractAction switchBitmapAppAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                File file = AlcUtil.showFileChooser(platformAppDir);
                if (file != null) {
                    System.out.println(file.toString());
                    Alchemy.preferences.switchBitmapApp = file.toString();
                }
            }
        };
        AlcMenuItem switchBitmapAppItem = new AlcMenuItem(switchBitmapAppAction);
        switchBitmapAppItem.setup(setBitmapApp);
        switchMenu.add(switchBitmapAppItem);
        this.add(switchMenu);

        //////////////////////////////////////////////////////////////
        // SETTINGS MENU
        //////////////////////////////////////////////////////////////
        AlcMenu settingsMenu = new AlcMenu(getS("settingsTitle"));

        // Smoothing
        AbstractAction smoothingAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                AlcCheckBoxMenuItem source = (AlcCheckBoxMenuItem) e.getSource();
                Alchemy.canvas.setSmoothing(source.getState());
                Alchemy.canvas.redraw();
            }
        };
        AlcCheckBoxMenuItem smoothingItem = new AlcCheckBoxMenuItem(smoothingAction);
        smoothingItem.setSelected(Alchemy.preferences.smoothing);
        smoothingItem.setup(getS("smoothingTitle"));
        settingsMenu.add(smoothingItem);

        // Line Smoothing
        AbstractAction lineSmoothingAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                AlcCheckBoxMenuItem source = (AlcCheckBoxMenuItem) e.getSource();
                AlcShape.setLineSmoothing(source.getState());

            }
        };
        AlcCheckBoxMenuItem lineSmoothingItem = new AlcCheckBoxMenuItem(lineSmoothingAction);
        lineSmoothingItem.setSelected(Alchemy.preferences.lineSmoothing);
        AlcShape.setLineSmoothing(Alchemy.preferences.lineSmoothing);
        lineSmoothingItem.setup(getS("lineSmoothingTitle"));
        settingsMenu.add(lineSmoothingItem);

        settingsMenu.add(new JSeparator());

        // Keyboard Shortcuts
        AbstractAction keyboardShortcutsAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.shortcuts.showWindow();
            }
        };
        AlcMenuItem keyboardShortcutsItem = new AlcMenuItem(keyboardShortcutsAction);
        keyboardShortcutsItem.setup(getS("keyboardShortcutsTitle"));
        settingsMenu.add(keyboardShortcutsItem);

        if (Alchemy.OS != OS_MAC) {
            // Options
            AbstractAction optionsAction = new AbstractAction() {

                public void actionPerformed(ActionEvent e) {
                    Alchemy.preferences.showWindow();
                }
            };
            AlcMenuItem optionsItem = new AlcMenuItem(optionsAction);
            //optionsItem.setup(getS("optionsTitle"));
            int optionsKey = Alchemy.shortcuts.setShortcut(optionsItem, KeyEvent.VK_O, "optionsTitle", optionsAction, KEY_MODIFIER);
            optionsItem.setup(getS("optionsTitle"), optionsKey);
            settingsMenu.add(optionsItem);
        }


        this.add(settingsMenu);

        //////////////////////////////////////////////////////////////  
        // HELP MENU
        //////////////////////////////////////////////////////////////
        AlcMenu helpMenu = new AlcMenu(getS("helpTitle"));

        AbstractAction helpAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {

                File manual = new File("Alchemy.pdf");
                if (manual.exists()) {
                    AlcUtil.openPDF(manual);
                } else {
                    System.err.println("Error locating the Alchemy manual");

                    // Try now to open the Alchemy documentation website instead
                    // Check which site to send them to
                    String locale = LOCALE.getLanguage().toLowerCase();
                    if (locale.startsWith("ja")) {
                        AlcUtil.openURL("http://al.chemy.org/ja/documentation/");
                    } else {
                        AlcUtil.openURL("http://al.chemy.org/documentation/");
                    }
                }

            }
        };

        // Overview of every shortcut - F1
        AbstractAction sheetAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                AlcShortcutSheet.toggle();
            }
        };
        AlcMenuItem sheetItem = new AlcMenuItem(sheetAction);
        int sheetKey = Alchemy.shortcuts.setShortcut(sheetItem, KeyEvent.VK_F1, "shortcutSheetTitle", sheetAction);
        sheetItem.setup(getS("shortcutSheetTitle"), sheetKey);
        helpMenu.add(sheetItem);

        AlcMenuItem helpItem = new AlcMenuItem(helpAction);
        helpItem.setup(getS("alchemyHelpTitle"));
        helpMenu.add(helpItem);
        helpMenu.add(new JSeparator());


        // Link to the Alchemy Website                
        AbstractAction wwwAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                // Check which site to send them to
                String locale = LOCALE.getLanguage().toLowerCase();
                if (locale.startsWith("ja")) {
                    AlcUtil.openURL("http://al.chemy.org/ja/");
                } else {
                    AlcUtil.openURL("http://al.chemy.org/");
                }
            }
        };
        AlcMenuItem wwwItem = new AlcMenuItem(wwwAction);
        wwwItem.setup(getS("alchemyWebsiteTitle"));
        helpMenu.add(wwwItem);
        // Link to the Alchemy forum           
        AbstractAction forumAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                AlcUtil.openURL("http://al.chemy.org/forum/");
            }
        };
        AlcMenuItem forumItem = new AlcMenuItem(forumAction);
        forumItem.setup(getS("alchemyForumTitle"));
        helpMenu.add(forumItem);

        // About menuitem not included on a MAC
        if (Alchemy.OS != OS_MAC) {
            helpMenu.add(new JSeparator());

            AbstractAction aboutAction = new AbstractAction() {

                public void actionPerformed(ActionEvent e) {
                    Alchemy.window.showAbout();
                }
            };
            AlcMenuItem aboutItem = new AlcMenuItem(aboutAction);
            aboutItem.setup(getS("aboutAlchemyTitle"));
            helpMenu.add(aboutItem);
        }

        this.add(helpMenu);
    }

    /** Get a string from the resource bundle */
    private String getS(String stringName) {
        return Alchemy.bundle.getString(stringName);
    }

    /** Return the height of the menubar */
    @Override
    public int getHeight() {
        return height;
    }

    /** Print the canvas */
    private void print() {
        if (printer == null) {
            printer = PrinterJob.getPrinterJob();
        }
        if (page == null) {
            page = printer.defaultPage();
            page.setOrientation(PageFormat.LANDSCAPE);
        }

        printer.setPrintable(Alchemy.canvas, page);

        if (printer.printDialog()) {
            try {
                printer.print();
            } catch (Exception e) {
                System.err.println(e);
            }
        }

    }

    private void pageSetup() {
        if (printer == null) {
            printer = PrinterJob.getPrinterJob();
        }
        if (defaultPage == null) {
            //defaultPage = new PageFormat();
            defaultPage = printer.defaultPage();
            defaultPage.setOrientation(PageFormat.LANDSCAPE);
        } else {
            defaultPage = page;
        }
        //aset = new HashPrintRequestAttributeSet();
        page = printer.pageDialog(defaultPage);
    }

    /** Ask for a path and filename to export a PDF to */
    /** Choose the part of the drawing on the canvas first, then where to save it */
    private void export() {
        Alchemy.canvas.startExportSelection(new Runnable() {

            public void run() {
                exportChosenArea();
            }
        });
    }

    private void exportChosenArea() {

        final AlcFileChooser fc = new AlcFileChooser(Alchemy.preferences.exportDirectory);
        fc.setDialogTitle(Alchemy.bundle.getString("exportFileTitle"));
        fc.setAcceptAllFileFilterUsed(false);
        // The format used last time is chosen again - PNG the first time
        String lastFormat = AlcPreferences.prefs.get("Export Format", "PNG");
        ExportFileFilter chosen = null;
        for (String name : new String[]{"PNG", "JPG", "PDF", "SVG"}) {
            ExportFileFilter filter = new ExportFileFilter(name);
            fc.addChoosableFileFilter(filter);
            if (name.equals(lastFormat)) {
                chosen = filter;
            }
        }
        if (chosen != null) {
            fc.setFileFilter(chosen);
        }
        fc.setSelectedFile(new File(Alchemy.bundle.getString("defaultFileName")));

        // in response to a button click:
        int returnVal = fc.showSaveDialog(this);
        if (returnVal == AlcFileChooser.APPROVE_OPTION) {
            
            File file = fc.getSelectedFile();
            String parent = file.getParent();
            if(parent != null){
                Alchemy.preferences.exportDirectory = parent;
            }
            String format = fc.getFileFilter().getDescription();
            AlcPreferences.prefs.put("Export Format", format);

            if (format.equals("PDF")) {
                Alchemy.session.saveSinglePdf(file);
            } else if (format.equals("SVG")) {
                Alchemy.session.saveSVG(file);
            } else if (format.equals("JPG") || format.equals("PNG")) {
                if(exportDialog == null){
                    exportDialog = new AlcExportDialog();
                }
                exportDialog.showWindow(file, format);
            }
        }
    }

    /** Copy the canvas to the clipboard as an image */
    private void copy() {
        // TODO - Implement Vector clipboard copy function
        // http://www.java2s.com/Code/Java/Swing-JFC/MimeClipboardTest.htm

        // A bug on mac with older versions of java can scramble the clipboard
        // fixed in Java 1.5 Release 3 (4238470)
        Alchemy.canvas.setGuide(false);
        AlcUtil.setClipboard(new AlcImageTransferable(Alchemy.canvas.renderCanvas(true)), Alchemy.window);
        Alchemy.canvas.setGuide(true);
    }

    /** Make a temporary file, create a PDF, and then open it */
    private void switchVector() {
        try {
            if(Alchemy.preferences.switchVectorFormat == Alchemy.preferences.FORMAT_SVG) {
                File tempVector = File.createTempFile("AlchemyTempVectorFile", ".svg");
                tempVector.deleteOnExit();
                if (Alchemy.session.saveSVG(tempVector)) {
                    openSwitch(tempVector.toString(), Alchemy.preferences.switchVectorApp);
                } else {
                    System.out.println("Didn't save SVG");
                }
            } else {  //Otherwise, save to pdf
                File tempVector = File.createTempFile("AlchemyTempVectorFile", ".pdf");
                tempVector.deleteOnExit();
                if (Alchemy.session.saveSinglePdf(tempVector)) {
                    openSwitch(tempVector.toString(), Alchemy.preferences.switchVectorApp);
                } else {
                    System.out.println("Didn't save PDF");
                }
            }


        } catch (IOException ex) {
            System.err.println(ex);
        }

    }

    /** Make a temporary file, create a PNG, and then open it */
    private void switchBitmap() {
        try {
            File tempBitmap = File.createTempFile("AlchemyTempBitmapFile", ".png");
            tempBitmap.deleteOnExit();
            if (Alchemy.canvas.saveBitmap(tempBitmap)) {
                openSwitch(tempBitmap.toString(), Alchemy.preferences.switchBitmapApp);
            } else {
                System.out.println("Didn't save???");
            }
        } catch (IOException ex) {
            System.err.println(ex);
        }

    }

    /** Open the appropriate application with the temp file and 'switch' */
    private void openSwitch(String file, String app) {
        File path = new File(app);
        try {
            String[] commands = null;
            switch (Alchemy.OS) {
                case OS_MAC:
                    commands = new String[]{"open", "-a", path.getName(), file};
                    break;
                case OS_WINDOWS:
                    //commands = new String[]{"cmd", "/c", "start",  "\""+path.getName()+"\"", file};
                    commands = new String[]{path.getAbsolutePath(), file};
                    break;
                case OS_LINUX:
                    commands = new String[]{path.getName(), file};
                    break;
            }
            if (commands != null) {
                Runtime.getRuntime().exec(commands);
            }
        //Runtime.getRuntime().exec("open "+file);
        } catch (IOException ex) {
            System.err.println(ex);
        }
    }

    /** A small non-clickable heading inside a menu */
    private JComponent sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_SMALL_BOLD);
        label.setForeground(COLOR_UI_TEXT_DIM);
        label.setBorder(BorderFactory.createEmptyBorder(4, 10, 2, 10));
        return label;
    }

    /** Chooser for editable sketch files, starting in the last used folder */
    private JFileChooser createSketchChooser() {
        JFileChooser fc = new AlcFileChooser(AlcPreferences.prefs.get("Sketch Directory", DIR_DESKTOP));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                getS("sketchFileDescription") + " (*." + AlcSketchIO.EXTENSION + ")", AlcSketchIO.EXTENSION));
        return fc;
    }

    /** Ask for a file and save the drawing as an editable sketch */
    void saveSketch() {
        JFileChooser fc = createSketchChooser();
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), "Alkemy " + AlcUtil.dateStamp("yyyy-MM-dd HH-mm") + "." + AlcSketchIO.EXTENSION));
        if (fc.showSaveDialog(Alchemy.window) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith("." + AlcSketchIO.EXTENSION)) {
            file = new File(file.getParentFile(), file.getName() + "." + AlcSketchIO.EXTENSION);
        }
        AlcPreferences.prefs.put("Sketch Directory", file.getParent());
        try {
            AlcSketchIO.save(file, Alchemy.canvas);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(Alchemy.window, ex.getMessage(), getS("saveSketchTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Ask for a sketch file and open it - replacing the drawing, which can be undone */
    void openSketch() {
        JFileChooser fc = createSketchChooser();
        if (fc.showOpenDialog(Alchemy.window) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = fc.getSelectedFile();
        AlcPreferences.prefs.put("Sketch Directory", file.getParent());
        try {
            Alchemy.canvas.loadSketch(AlcSketchIO.open(file));
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(Alchemy.window, ex.getMessage(), getS("openSketchTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (Alchemy.OS != OS_MAC) {
            // Make sure this is not painted on Mac
            // The mac menubar is used instead
            super.paintComponent(g);
        }
    }
}

class ExportFileFilter extends javax.swing.filechooser.FileFilter {

    private final String format;

    ExportFileFilter(String format) {
        this.format = format;
    }

    public boolean accept(File f) {
        return true;
    }

    public String getDescription() {
        return format;
    }
}
