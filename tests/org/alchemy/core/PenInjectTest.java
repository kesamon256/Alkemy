package org.alchemy.core;

import java.awt.Point;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import javax.swing.SwingUtilities;

/** End-to-end Windows Ink check: injects a synthetic pen (with pressure) through Windows itself. */
public class PenInjectTest implements AlcConstants {

    static final int PT_PEN = 3;
    static final int INRANGE = 0x2, INCONTACT = 0x4, PRIMARY = 0x2000, DOWN = 0x10000, UPDATE = 0x20000, UP = 0x40000;
    static final long TYPE_INFO_SIZE = 152, PEN = 8;
    static MethodHandle inject;
    static MemorySegment device;
    static int before;

    public static void main(String[] args) throws Throwable {
        Alchemy.main(new String[0]);
        Thread.sleep(3500);
        final Point[] origin = new Point[1];
        final double[] scale = new double[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                Alchemy.window.setSize(1100, 650);
                Alchemy.window.setLocation(100, 100);
                Alchemy.window.setAlwaysOnTop(true);
                Alchemy.window.toFront();
                Alchemy.canvas.setPressureEnabled(true);
                Alchemy.canvas.setLineWidth(6);
            }
        });
        Thread.sleep(1000);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                origin[0] = Alchemy.canvas.getLocationOnScreen();
                scale[0] = Alchemy.canvas.getGraphicsConfiguration().getDefaultTransform().getScaleX();
                before = Alchemy.canvas.shapes.size();
            }
        });
        System.out.println("canvas at " + origin[0] + " scale " + scale[0]);

        Linker linker = Linker.nativeLinker();
        SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
        MethodHandle create = linker.downcallHandle(user32.find("CreateSyntheticPointerDevice").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        inject = linker.downcallHandle(user32.find("InjectSyntheticPointerInput").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        MethodHandle destroy = linker.downcallHandle(user32.find("DestroySyntheticPointerDevice").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        device = (MemorySegment) create.invokeExact(PT_PEN, 1, 1);
        System.out.println("synthetic pen device: " + (device.address() != 0));

        int x0 = (int) ((origin[0].x + 150) * scale[0]), x1 = (int) ((origin[0].x + 900) * scale[0]), y = (int) ((origin[0].y + 300) * scale[0]);
        send(x0, y, INRANGE | UPDATE | PRIMARY, 0);
        Thread.sleep(50);
        send(x0, y, INRANGE | INCONTACT | DOWN | PRIMARY, 50);
        for (int i = 1; i <= 60; i++) {
            Thread.sleep(12);
            int pressure = (int) (50 + 950 * Math.sin(Math.PI * i / 60.0));
            send(x0 + (x1 - x0) * i / 60, y + (int) (50 * Math.sin(i / 8.0)), INRANGE | INCONTACT | UPDATE | PRIMARY, pressure);
        }
        send(x1, y, INRANGE | UP | PRIMARY, 0);
        Thread.sleep(50);
        send(x1, y, UPDATE | PRIMARY, 0);
        Thread.sleep(800);
        destroy.invokeExact(device);

        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                AlcCanvas c = Alchemy.canvas;
                UndoSmokeTest.check("Windows saw the pen (pen seen by the hook)", AlcWindowsInk.isPenSeen());
                UndoSmokeTest.check("a stroke was drawn (" + (c.shapes.size() - before) + ")", c.shapes.size() > before);
                if (c.shapes.size() > before) {
                    AlcShape s = c.shapes.get(c.shapes.size() - 1);
                    UndoSmokeTest.check("stroke used pressure", s.hasSpine());
                    if (s.hasSpine()) {
                        float min = Float.MAX_VALUE, max = 0;
                        for (Float w : s.getSpineWidth()) {
                            min = Math.min(min, w);
                            max = Math.max(max, w);
                        }
                        UndoSmokeTest.check(String.format("width varied with real pressure (%.1f .. %.1f px)", min * 2, max * 2), max > min * 2);
                    }
                }
                try {
                    SketchTest.dir = "C:\\Temp\\claude\\2ee0fcfd91fa3ad1\\86238dcf-1bcd-4f7f-818a-9b90cd57f7a4\\scratchpad\\shots";
                    SketchTest.shot("pen-inject");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(UndoSmokeTest.failures == 0 ? "ALL PASSED" : ("FAILURES: " + UndoSmokeTest.failures));
        System.exit(0);
    }

    static void send(int x, int y, int flags, int pressure) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(TYPE_INFO_SIZE, 8);
            info.set(ValueLayout.JAVA_INT, 0, PT_PEN);
            // POINTER_INFO inside POINTER_PEN_INFO
            info.set(ValueLayout.JAVA_INT, PEN + 0, PT_PEN);
            info.set(ValueLayout.JAVA_INT, PEN + 4, 0);
            info.set(ValueLayout.JAVA_INT, PEN + 12, flags);
            info.set(ValueLayout.JAVA_INT, PEN + 32, x);
            info.set(ValueLayout.JAVA_INT, PEN + 36, y);
            // pen fields
            info.set(ValueLayout.JAVA_INT, PEN + 100, 1); // PEN_MASK_PRESSURE
            info.set(ValueLayout.JAVA_INT, PEN + 104, pressure);
            int ok = (int) inject.invokeExact(device, info, 1);
            if (ok == 0) {
                System.out.println("inject failed at flags " + Integer.toHexString(flags));
            }
        }
    }
}
