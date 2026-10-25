package dev.unseen.voice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Just enough audio handling: 16-bit mono WAV files, and the small conversions
 * needed to get speech into and out of Simple Voice Chat (48 kHz, 960-sample frames).
 */
public final class Wav {
	/** Simple Voice Chat's sample rate. */
	public static final int VOICE_RATE = 48000;
	/** Samples in one 20 ms voice frame. */
	public static final int FRAME = 960;

	private Wav() {
	}

	public record Audio(short[] samples, int rate) {
	}

	public static void write(Path file, short[] samples, int rate) throws IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, toBytes(samples, rate));
	}

	public static byte[] toBytes(short[] samples, int rate) {
		int dataLength = samples.length * 2;
		ByteBuffer buf = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
		buf.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + dataLength).put(new byte[]{'W', 'A', 'V', 'E'});
		buf.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) 1)
				.putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
		buf.put(new byte[]{'d', 'a', 't', 'a'}).putInt(dataLength);
		for (short s : samples) {
			buf.putShort(s);
		}
		return buf.array();
	}

	/** Reads a 16-bit PCM WAV. If it has more than one channel, keeps the first. */
	public static Audio read(Path file) throws IOException {
		ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
		if (buf.remaining() < 12 || buf.getInt() != 0x46464952 /* RIFF */) {
			throw new IOException("Not a WAV file: " + file);
		}
		buf.getInt();
		buf.getInt(); // WAVE
		int rate = VOICE_RATE;
		int channels = 1;
		while (buf.remaining() >= 8) {
			int id = buf.getInt();
			int size = buf.getInt();
			if (id == 0x20746d66 /* fmt  */) {
				buf.getShort(); // format
				channels = buf.getShort();
				rate = buf.getInt();
				buf.position(buf.position() + size - 8);
			} else if (id == 0x61746164 /* data */) {
				int count = Math.min(size, buf.remaining()) / 2 / channels;
				short[] samples = new short[count];
				for (int i = 0; i < count; i++) {
					samples[i] = buf.getShort();
					buf.position(buf.position() + (channels - 1) * 2);
				}
				return new Audio(samples, rate);
			} else {
				buf.position(Math.min(buf.limit(), buf.position() + size));
			}
		}
		throw new IOException("No audio in " + file);
	}

	/** Raw little-endian 16-bit mono, as the voice server's {@code pcm} format. */
	public static short[] fromPcm(byte[] pcm) {
		ByteBuffer buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
		short[] out = new short[pcm.length / 2];
		for (int i = 0; i < out.length; i++) {
			out[i] = buf.getShort();
		}
		return out;
	}

	public static short[] resample(short[] in, int from, int to) {
		if (from == to || in.length == 0) {
			return in;
		}
		// Going to a higher rate needs more samples, which stretch() calls a speed below 1.
		return stretch(in, (double) from / to);
	}

	/**
	 * Changes length by interpolating: {@code speed} below 1 plays slower and lower,
	 * above 1 faster and higher. The same trick resamples between rates.
	 */
	public static short[] stretch(short[] in, double speed) {
		if (in.length == 0 || speed <= 0) {
			return in;
		}
		int length = (int) Math.max(1, Math.round(in.length / speed));
		short[] out = new short[length];
		for (int i = 0; i < length; i++) {
			double pos = i * speed;
			int a = (int) pos;
			int b = Math.min(a + 1, in.length - 1);
			double frac = pos - a;
			out[i] = (short) Math.round(in[Math.min(a, in.length - 1)] * (1 - frac) + in[b] * frac);
		}
		return out;
	}

	public static short[] gain(short[] in, double gain) {
		short[] out = new short[in.length];
		for (int i = 0; i < in.length; i++) {
			out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(in[i] * gain)));
		}
		return out;
	}

	public static short[] concat(java.util.List<short[]> parts) {
		int total = 0;
		for (short[] part : parts) {
			total += part.length;
		}
		short[] out = new short[total];
		int at = 0;
		for (short[] part : parts) {
			System.arraycopy(part, 0, out, at, part.length);
			at += part.length;
		}
		return out;
	}

	public static double rms(short[] samples) {
		if (samples.length == 0) {
			return 0;
		}
		double sum = 0;
		for (short s : samples) {
			sum += (double) s * s;
		}
		return Math.sqrt(sum / samples.length);
	}

	/** Pads to whole voice frames, so the last bit isn't cut off. */
	public static short[] padToFrames(short[] in) {
		int remainder = in.length % FRAME;
		if (remainder == 0) {
			return in;
		}
		short[] out = new short[in.length + FRAME - remainder];
		System.arraycopy(in, 0, out, 0, in.length);
		return out;
	}

	public static byte[] readAll(java.io.InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		in.transferTo(out);
		return out.toByteArray();
	}
}
