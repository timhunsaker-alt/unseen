package dev.unseen.client;

import java.util.Random;

/**
 * Turns a screenshot into a creepy 128x128 black-and-white "found photo" that
 * fits on a Minecraft map. Pure Java with no Minecraft classes, so it's easy to test.
 *
 * Steps: crop the middle, shrink, add grain and a dark vignette, then dither
 * into the 32 shades of gray a map can show.
 */
public final class Darkroom {
	public static final int SIZE = 128;

	/** Reads one pixel of the source image as packed 0xAARRGGBB (or 0xAABBGGRR; we only use the average). */
	@FunctionalInterface
	public interface PixelSource {
		int pixel(int x, int y);
	}

	/**
	 * Gray map colors: base color id and its gray value. Map color byte = id * 4 + shade.
	 * WOOL, METAL, SNOW, STONE, COLOR_GRAY, COLOR_LIGHT_GRAY, COLOR_BLACK, DEEPSLATE.
	 */
	private static final int[][] GRAY_BASES = {
			{3, 199}, {6, 167}, {8, 255}, {11, 112}, {21, 76}, {22, 153}, {29, 25}, {59, 100}};
	/** Map shade multipliers, out of 255, for shade 0..3. */
	private static final int[] SHADES = {180, 220, 255, 135};

	private static final byte[] PALETTE_BYTES;
	private static final float[] PALETTE_GRAY;

	static {
		PALETTE_BYTES = new byte[GRAY_BASES.length * SHADES.length];
		PALETTE_GRAY = new float[PALETTE_BYTES.length];
		int i = 0;
		for (int[] base : GRAY_BASES) {
			for (int shade = 0; shade < SHADES.length; shade++) {
				PALETTE_BYTES[i] = (byte) (base[0] * 4 + shade);
				PALETTE_GRAY[i] = base[1] * SHADES[shade] / 255.0F;
				i++;
			}
		}
	}

	private Darkroom() {
	}

	public static byte[] develop(PixelSource source, int width, int height, long seed) {
		float[] gray = cropAndShrink(source, width, height);
		treat(gray, new Random(seed));
		return dither(gray);
	}

	/** Square from the middle of the screen, nudged up to stay clear of the bed screen's button. */
	private static float[] cropAndShrink(PixelSource source, int width, int height) {
		int side = (int) (Math.min(width, height) * 0.8);
		int left = (width - side) / 2;
		int top = Math.max(0, (height - side) / 2 - height / 12);

		float[] out = new float[SIZE * SIZE];
		for (int oy = 0; oy < SIZE; oy++) {
			int y0 = top + oy * side / SIZE;
			int y1 = Math.max(y0 + 1, top + (oy + 1) * side / SIZE);
			for (int ox = 0; ox < SIZE; ox++) {
				int x0 = left + ox * side / SIZE;
				int x1 = Math.max(x0 + 1, left + (ox + 1) * side / SIZE);
				float sum = 0;
				int count = 0;
				for (int y = y0; y < y1 && y < height; y++) {
					for (int x = x0; x < x1 && x < width; x++) {
						int p = source.pixel(x, y);
						sum += (((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF)) / 3.0F;
						count++;
					}
				}
				out[oy * SIZE + ox] = count == 0 ? 0 : sum / count;
			}
		}
		return out;
	}

	/** Contrast, a camera-flash falloff, film grain, and dark corners. */
	private static void treat(float[] gray, Random random) {
		float min = 255;
		float max = 0;
		for (float g : gray) {
			min = Math.min(min, g);
			max = Math.max(max, g);
		}
		float range = Math.max(1, max - min);

		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				int i = y * SIZE + x;
				// Stretch to full range, with a slight lift like a camera flash.
				float v = (gray[i] - min) / range;
				v = (float) Math.pow(v, 0.85);

				// Vignette: corners fall off to black like an old flash photo.
				float dx = (x - SIZE / 2.0F) / (SIZE / 2.0F);
				float dy = (y - SIZE / 2.0F) / (SIZE / 2.0F);
				float vignette = 1.0F - 0.6F * (float) Math.pow(Math.min(1.0, Math.sqrt(dx * dx + dy * dy) / 1.25), 2.2);
				v *= vignette;

				// Grain.
				v += (float) (random.nextGaussian() * 0.06);

				gray[i] = Math.max(0, Math.min(255, v * 255));
			}
		}
	}

	/** Floyd-Steinberg dithering into the gray map palette. */
	private static byte[] dither(float[] gray) {
		byte[] out = new byte[SIZE * SIZE];
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				int i = y * SIZE + x;
				float wanted = gray[i];
				int best = nearest(wanted);
				out[i] = PALETTE_BYTES[best];
				float error = wanted - PALETTE_GRAY[best];
				if (x + 1 < SIZE) {
					gray[i + 1] += error * 7 / 16;
				}
				if (y + 1 < SIZE) {
					if (x > 0) {
						gray[i + SIZE - 1] += error * 3 / 16;
					}
					gray[i + SIZE] += error * 5 / 16;
					if (x + 1 < SIZE) {
						gray[i + SIZE + 1] += error * 1 / 16;
					}
				}
			}
		}
		return out;
	}

	private static int nearest(float value) {
		int best = 0;
		float bestDistance = Float.MAX_VALUE;
		for (int i = 0; i < PALETTE_GRAY.length; i++) {
			float d = Math.abs(PALETTE_GRAY[i] - value);
			if (d < bestDistance) {
				bestDistance = d;
				best = i;
			}
		}
		return best;
	}

	/** Gray value a map color byte will show as (for previews and tests). */
	public static int grayOf(byte mapColor) {
		for (int i = 0; i < PALETTE_BYTES.length; i++) {
			if (PALETTE_BYTES[i] == mapColor) {
				return Math.round(PALETTE_GRAY[i]);
			}
		}
		return -1;
	}
}
