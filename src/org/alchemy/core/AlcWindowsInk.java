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

import java.awt.Window;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pen tablet input through Windows Ink (WM_POINTER messages).
 * <p>
 * Windows Ink works with practically every pen on Windows 8 and newer:
 * Wacom, Huion, XP-Pen, Surface and other pen displays. It complements the
 * JPen library, which reads tablets through the older Wintab interface.
 * <p>
 * The window procedure of the Alchemy window is wrapped: pen messages are
 * read for pressure, tilt and the eraser, then passed on unchanged, so
 * Windows still turns them into the usual mouse events.
 */
final class AlcWindowsInk {

    private static final int GWLP_WNDPROC = -4;
    private static final int WM_POINTERUPDATE = 0x0245;
    private static final int WM_POINTERDOWN = 0x0246;
    private static final int WM_POINTERUP = 0x0247;
    private static final int WM_POINTERENTER = 0x0249;
    private static final int WM_POINTERLEAVE = 0x024A;
    private static final int PT_PEN = 3;
    private static final int PEN_FLAG_INVERTED = 0x2;
    private static final int PEN_FLAG_ERASER = 0x4;
    private static final int PEN_MASK_PRESSURE = 0x1;
    /** Offsets in POINTER_PEN_INFO (64 bit) - it starts with a 96 byte POINTER_INFO */
    private static final long PEN_INFO_SIZE = 120;
    private static final long OFFSET_PEN_FLAGS = 96;
    private static final long OFFSET_PEN_MASK = 100;
    private static final long OFFSET_PRESSURE = 104;
    private static final long OFFSET_TILT_X = 112;
    private static final long OFFSET_TILT_Y = 116;
    //
    private static boolean available = false;
    private static MethodHandle findWindowEx, getWindowThreadProcessId, getWindowText;
    private static MethodHandle setWindowLongPtr, callWindowProc, getPointerType, getPointerPenInfo;
    private static MemorySegment wndProcStub;
    /** Original window procedure of every window we have wrapped */
    private static final ConcurrentHashMap<Long, MemorySegment> originalProcs = new ConcurrentHashMap<Long, MemorySegment>();
    //
    /** Pen state - written on the AWT toolkit thread, read on the event thread */
    private static volatile boolean penInRange = false;
    private static volatile boolean eraser = false;
    private static volatile boolean hasPressure = false;
    private static volatile float pressure = 0F;
    private static volatile float tiltX = 0F;
    private static volatile float tiltY = 0F;
    /** Time of the last pen message - a pen that has gone quiet is treated as out of range */
    private static volatile long lastPenTime = 0;
    /** Has any Windows Ink pen been seen since start up */
    private static volatile boolean penSeen = false;

    private AlcWindowsInk() {
    }

    /** Wrap the window procedure of the given window so pen data is received.
     *  Safe to call repeatedly - for example after switching to fullscreen,
     *  which creates a new native window.
     * @return true if pen messages are being received for the window
     */
    static synchronized boolean install(Window window) {
        if (Alchemy.OS != AlcConstants.OS_WINDOWS || !window.isDisplayable()) {
            return false;
        }
        try {
            if (wndProcStub == null) {
                setup();
            }
            MemorySegment hwnd = findWindow(window);
            if (hwnd == null) {
                AlcCanvas.penLog("Windows Ink: window not found");
            }
            if (hwnd == null || originalProcs.containsKey(hwnd.address())) {
                return hwnd != null;
            }
            MemorySegment original = (MemorySegment) setWindowLongPtr.invokeExact(hwnd, GWLP_WNDPROC, wndProcStub);
            if (original.address() == 0) {
                return false;
            }
            originalProcs.put(hwnd.address(), original);
            available = true;
            AlcCanvas.penLog("Windows Ink hook installed on window " + Long.toHexString(hwnd.address()));
            return true;
        } catch (Throwable t) {
            System.err.println("Windows Ink pen support is not available: " + t);
            AlcCanvas.penLog("Windows Ink hook FAILED: " + t);
            return false;
        }
    }

    private static void setup() throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
        findWindowEx = linker.downcallHandle(user32.find("FindWindowExW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        getWindowThreadProcessId = linker.downcallHandle(user32.find("GetWindowThreadProcessId").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        getWindowText = linker.downcallHandle(user32.find("GetWindowTextW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        setWindowLongPtr = linker.downcallHandle(user32.find("SetWindowLongPtrW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        callWindowProc = linker.downcallHandle(user32.find("CallWindowProcW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        getPointerType = linker.downcallHandle(user32.find("GetPointerType").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        getPointerPenInfo = linker.downcallHandle(user32.find("GetPointerPenInfo").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        MethodHandle proc = MethodHandles.lookup().findStatic(AlcWindowsInk.class, "windowProc",
                MethodType.methodType(long.class, MemorySegment.class, int.class, long.class, long.class));
        wndProcStub = linker.upcallStub(proc,
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG),
                Arena.global());
    }

    /** Find the native frame window of this process with the window's title */
    private static MemorySegment findWindow(Window window) throws Throwable {
        long pid = ProcessHandle.current().pid();
        String title = (window instanceof java.awt.Frame) ? ((java.awt.Frame) window).getTitle() : null;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment className = arena.allocateFrom("SunAwtFrame", java.nio.charset.StandardCharsets.UTF_16LE);
            MemorySegment pidOut = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment text = arena.allocate(512);
            MemorySegment hwnd = MemorySegment.NULL;
            MemorySegment fallback = null;
            while (true) {
                hwnd = (MemorySegment) findWindowEx.invokeExact(MemorySegment.NULL, hwnd, className, MemorySegment.NULL);
                if (hwnd.address() == 0) {
                    return fallback;
                }
                int ignored = (int) getWindowThreadProcessId.invokeExact(hwnd, pidOut);
                if (pidOut.get(ValueLayout.JAVA_INT, 0) != pid) {
                    continue;
                }
                int length = (int) getWindowText.invokeExact(hwnd, text, 256);
                String windowTitle = new String(text.asSlice(0, length * 2L).toArray(ValueLayout.JAVA_BYTE), java.nio.charset.StandardCharsets.UTF_16LE);
                if (title != null && title.equals(windowTitle)) {
                    return MemorySegment.ofAddress(hwnd.address());
                }
                if (fallback == null) {
                    fallback = MemorySegment.ofAddress(hwnd.address());
                }
            }
        }
    }

    /** The wrapped window procedure - called by Windows on the AWT toolkit thread */
    private static long windowProc(MemorySegment hwnd, int msg, long wParam, long lParam) {
        try {
            if (msg >= WM_POINTERUPDATE && msg <= WM_POINTERLEAVE) {
                readPointer(msg, (int) (wParam & 0xFFFF));
            }
        } catch (Throwable t) {
            // Never let an exception escape into native code
        }
        MemorySegment original = originalProcs.get(hwnd.address());
        try {
            return (long) callWindowProc.invokeExact(original, hwnd, msg, wParam, lParam);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Diagnostics - counts of pointer messages seen, by pointer type */
    static volatile int pointerMessages, penMessages, lastPointerType = -1, lastRawPressure = -1, lastMask = -1;

    private static void readPointer(int msg, int pointerId) throws Throwable {
        pointerMessages++;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment type = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) getPointerType.invokeExact(pointerId, type);
            lastPointerType = ok == 0 ? -2 : type.get(ValueLayout.JAVA_INT, 0);
            if (ok == 0 || type.get(ValueLayout.JAVA_INT, 0) != PT_PEN) {
                return;
            }
            penMessages++;
            if (msg == WM_POINTERLEAVE) {
                penInRange = false;
                return;
            }
            MemorySegment info = arena.allocate(PEN_INFO_SIZE, 8);
            if ((int) getPointerPenInfo.invokeExact(pointerId, info) == 0) {
                return;
            }
            int flags = info.get(ValueLayout.JAVA_INT, OFFSET_PEN_FLAGS);
            int mask = info.get(ValueLayout.JAVA_INT, OFFSET_PEN_MASK);
            lastMask = mask;
            lastRawPressure = info.get(ValueLayout.JAVA_INT, OFFSET_PRESSURE);
            update(true,
                    (flags & (PEN_FLAG_ERASER | PEN_FLAG_INVERTED)) != 0,
                    (mask & PEN_MASK_PRESSURE) != 0,
                    info.get(ValueLayout.JAVA_INT, OFFSET_PRESSURE) / 1024F,
                    info.get(ValueLayout.JAVA_INT, OFFSET_TILT_X) / 90F,
                    info.get(ValueLayout.JAVA_INT, OFFSET_TILT_Y) / 90F);
        }
    }

    /** Store the pen state - also used by tests to simulate a pen */
    static void update(boolean inRange, boolean isEraser, boolean pressureKnown, float pressureValue, float tiltXValue, float tiltYValue) {
        eraser = isEraser;
        hasPressure = pressureKnown;
        pressure = Math.max(0F, Math.min(1F, pressureValue));
        tiltX = tiltXValue;
        tiltY = tiltYValue;
        penInRange = inRange;
        lastPenTime = System.currentTimeMillis();
        if (inRange) {
            penSeen = true;
        }
    }

    /** Has the Windows Ink hook been installed */
    static boolean isAvailable() {
        return available;
    }

    /** Is a Windows Ink pen currently over the window */
    static boolean isPenInRange() {
        return penInRange && System.currentTimeMillis() - lastPenTime < 400;
    }

    /** Has a Windows Ink pen been used since start up */
    static boolean isPenSeen() {
        return penSeen;
    }

    static boolean isEraser() {
        return eraser;
    }

    static boolean hasPressure() {
        return hasPressure;
    }

    /** Pressure from 0 to 1 */
    static float getPressure() {
        return pressure;
    }

    /** Tilt from -1 to 1 */
    static float getTiltX() {
        return tiltX;
    }

    static float getTiltY() {
        return tiltY;
    }
}
