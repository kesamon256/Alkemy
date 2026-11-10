/*
 *  This file is part of the Alchemy project - http://al.chemy.org
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
 */
package org.alchemy.core;

/**
 * Kinds of noise for modules that distort shapes.
 * <p>
 * Every kind returns a value from 0 to 1 for a point in space (x, y) and time (z),
 * so modules can swap one kind for another:
 * <ul>
 * <li>PERLIN - soft rolling waves</li>
 * <li>TURBULENCE - rough, cloud-like billows</li>
 * <li>RANDOM - jittery grains that change all the time - the size sets the grain</li>
 * <li>RIDGED - sharp creases like cracks or lightning</li>
 * <li>CELLULAR - cells like plates or scales</li>
 * <li>GLITCH - blocky steps that jump between values</li>
 * </ul>
 */
public final class AlcNoise {

    public static final int PERLIN = 0, TURBULENCE = 1, RANDOM = 2, RIDGED = 3, CELLULAR = 4, GLITCH = 5;
    /** The user's own image, its brightness used as the noise - repeated like tiles */
    public static final int CUSTOM = 6;
    public static final String[] NAMES = {"Perlin", "Turbulence", "Random", "Ridged", "Cellular", "Glitch"};
    private static final int[] P = new int[512];
    /** Brightness of the custom image from 0 to 1, row by row */
    private static float[] custom;
    private static int customWidth, customHeight;

    static {
        // Ken Perlin's reference permutation
        int[] permutation = {151, 160, 137, 91, 90, 15, 131, 13, 201, 95, 96, 53, 194, 233, 7, 225, 140, 36, 103, 30, 69, 142,
            8, 99, 37, 240, 21, 10, 23, 190, 6, 148, 247, 120, 234, 75, 0, 26, 197, 62, 94, 252, 219, 203, 117, 35, 11, 32, 57, 177,
            33, 88, 237, 149, 56, 87, 174, 20, 125, 136, 171, 168, 68, 175, 74, 165, 71, 134, 139, 48, 27, 166, 77, 146, 158, 231, 83,
            111, 229, 122, 60, 211, 133, 230, 220, 105, 92, 41, 55, 46, 245, 40, 244, 102, 143, 54, 65, 25, 63, 161, 1, 216, 80, 73,
            209, 76, 132, 187, 208, 89, 18, 169, 200, 196, 135, 130, 116, 188, 159, 86, 164, 100, 109, 198, 173, 186, 3, 64, 52, 217,
            226, 250, 124, 123, 5, 202, 38, 147, 118, 126, 255, 82, 85, 212, 207, 206, 59, 227, 47, 16, 58, 17, 182, 189, 28, 42, 223,
            183, 170, 213, 119, 248, 152, 2, 44, 154, 163, 70, 221, 153, 101, 155, 167, 43, 172, 9, 129, 22, 39, 253, 19, 98, 108, 110,
            79, 113, 224, 232, 178, 185, 112, 104, 218, 246, 97, 228, 251, 34, 242, 193, 238, 210, 144, 12, 191, 179, 162, 241, 81, 51,
            145, 235, 249, 14, 239, 107, 49, 192, 214, 31, 181, 199, 106, 157, 184, 84, 204, 176, 115, 121, 50, 45, 127, 4, 150, 254,
            138, 236, 205, 93, 222, 114, 67, 29, 24, 72, 243, 141, 128, 195, 78, 66, 215, 61, 156, 180};
        for (int i = 0; i < 256; i++) {
            P[i] = permutation[i];
            P[i + 256] = permutation[i];
        }
    }

    private AlcNoise() {
    }

    /** Noise of the given kind at (x, y, z) - from 0 to 1 */
    public static double noise(int kind, double x, double y, double z) {
        switch (kind) {
            case TURBULENCE:
                return turbulence(x, y, z);
            case RANDOM:
                // Grains a quarter of a noise unit wide - each its own random value, rolled again as time moves on
                return hash((int) Math.floor(x * 4), (int) Math.floor(y * 4), (int) Math.floor(z * 67), 11);
            case RIDGED:
                return ridged(x, y, z);
            case CELLULAR:
                return cellular(x, y, z);
            case GLITCH:
                return glitch(x, y, z);
            case CUSTOM:
                return image(x, y);
            default:
                return perlin(x, y, z) * 0.5 + 0.5;
        }
    }

    //////////////////////////////////////////////////////////////
    // PERLIN - improved noise, from -1 to 1
    //////////////////////////////////////////////////////////////
    public static double perlin(double x, double y, double z) {
        int xi = (int) Math.floor(x) & 255, yi = (int) Math.floor(y) & 255, zi = (int) Math.floor(z) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);
        double u = fade(x), v = fade(y), w = fade(z);
        int a = P[xi] + yi, aa = P[a] + zi, ab = P[a + 1] + zi;
        int b = P[xi + 1] + yi, ba = P[b] + zi, bb = P[b + 1] + zi;
        double n = lerp(w,
                lerp(v, lerp(u, grad(P[aa], x, y, z), grad(P[ba], x - 1, y, z)),
                        lerp(u, grad(P[ab], x, y - 1, z), grad(P[bb], x - 1, y - 1, z))),
                lerp(v, lerp(u, grad(P[aa + 1], x, y, z - 1), grad(P[ba + 1], x - 1, y, z - 1)),
                        lerp(u, grad(P[ab + 1], x, y - 1, z - 1), grad(P[bb + 1], x - 1, y - 1, z - 1))));
        // The reference noise stays within about -0.7 to 0.7 - stretch it to the full range
        return Math.max(-1, Math.min(1, n * 1.4));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    //////////////////////////////////////////////////////////////
    // TURBULENCE AND RIDGED - layers of Perlin noise
    //////////////////////////////////////////////////////////////
    private static double turbulence(double x, double y, double z) {
        double sum = 0, amplitude = 1, total = 0;
        for (int octave = 0; octave < 4; octave++) {
            sum += Math.abs(perlin(x, y, z)) * amplitude;
            total += amplitude;
            x *= 2.03;
            y *= 2.03;
            z *= 2.03;
            amplitude *= 0.5;
        }
        return Math.min(1, sum / total * 1.6);
    }

    private static double ridged(double x, double y, double z) {
        double sum = 0, amplitude = 1, total = 0;
        for (int octave = 0; octave < 3; octave++) {
            double r = 1 - Math.abs(perlin(x, y, z));
            sum += r * r * r * amplitude;
            total += amplitude;
            x *= 2.1;
            y *= 2.1;
            z *= 2.1;
            amplitude *= 0.5;
        }
        return sum / total;
    }

    //////////////////////////////////////////////////////////////
    // CELLULAR - distance to the nearest of scattered points
    //////////////////////////////////////////////////////////////
    private static double cellular(double x, double y, double z) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
        double nearest = 10;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    int gx = cx + i, gy = cy + j, gz = cz + k;
                    double px = gx + hash(gx, gy, gz, 1), py = gy + hash(gx, gy, gz, 2), pz = gz + hash(gx, gy, gz, 3);
                    double dx = px - x, dy = py - y, dz = pz - z;
                    nearest = Math.min(nearest, dx * dx + dy * dy + dz * dz);
                }
            }
        }
        return Math.min(1, Math.sqrt(nearest));
    }

    //////////////////////////////////////////////////////////////
    // GLITCH - flat blocks of random value that jump from one to the next
    //////////////////////////////////////////////////////////////
    private static double glitch(double x, double y, double z) {
        // Wide bands with smaller blocks inside some of them
        int bx = (int) Math.floor(x * 0.5), by = (int) Math.floor(y * 2), bz = (int) Math.floor(z * 3);
        double band = hash(bx, by, bz, 7);
        if (band > 0.6) {
            return hash((int) Math.floor(x * 2), by, bz, 8);
        }
        return Math.floor(band * 4) / 3.0 * 0.999;
    }

    //////////////////////////////////////////////////////////////
    // CUSTOM - the brightness of an image
    //////////////////////////////////////////////////////////////
    /** Use an image as the custom noise - large images are scaled down to keep it quick */
    public static void setCustomImage(java.awt.image.BufferedImage source) {
        int w = source.getWidth(), h = source.getHeight();
        double scale = Math.min(1, 512.0 / Math.max(w, h));
        int sw = Math.max(1, (int) Math.round(w * scale)), sh = Math.max(1, (int) Math.round(h * scale));
        java.awt.image.BufferedImage small = new java.awt.image.BufferedImage(sw, sh, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = small.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, sw, sh, null);
        g.dispose();
        float[] values = new float[sw * sh];
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int argb = small.getRGB(x, y);
                double a = ((argb >>> 24) & 255) / 255.0;
                double lum = (0.299 * ((argb >> 16) & 255) + 0.587 * ((argb >> 8) & 255) + 0.114 * (argb & 255)) / 255.0;
                // See-through parts count as white
                values[y * sw + x] = (float) (lum * a + (1 - a));
            }
        }
        custom = values;
        customWidth = sw;
        customHeight = sh;
    }

    public static boolean hasCustomImage() {
        return custom != null;
    }

    /** The image repeats every 4 noise units, read smoothly between its pixels */
    private static double image(double x, double y) {
        if (custom == null) {
            return 0.5;
        }
        double px = x / 4 * customWidth, py = y / 4 * customWidth;
        int x0 = (int) Math.floor(px), y0 = (int) Math.floor(py);
        double fx = px - x0, fy = py - y0;
        double a = pixel(x0, y0), b = pixel(x0 + 1, y0), c = pixel(x0, y0 + 1), d = pixel(x0 + 1, y0 + 1);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }

    private static double pixel(int x, int y) {
        x = ((x % customWidth) + customWidth) % customWidth;
        y = ((y % customHeight) + customHeight) % customHeight;
        return custom[y * customWidth + x];
    }

    /** A repeatable random number from 0 to 1 for a grid cell */
    private static double hash(int x, int y, int z, int seed) {
        long h = x * 374761393L + y * 668265263L + z * 2147483647L + seed * 144665L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h = h ^ (h >>> 16);
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
}
