package dev.unseen.voice;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import dev.unseen.Unseen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Listens to the microphone of players who opted in, and keeps short clips of them speaking.
 * Nothing is kept for anyone who hasn't typed /unseenvoice optin.
 *
 * It decodes each 20 ms voice packet, treats loud-enough frames as speech, and when
 * the player pauses saves the phrase as a small WAV file.
 */
public class ClipRecorder implements AutoCloseable {
	/** A pause this long (20 ms frames) ends a clip. */
	private static final int SILENCE_FRAMES_TO_END = 18;
	/** A little quiet before the first loud frame, so words don't start abruptly. */
	private static final int LEAD_IN_FRAMES = 4;

	private static class Session {
		final OpusDecoder decoder;
		final List<short[]> frames = new ArrayList<>();
		final List<short[]> leadIn = new ArrayList<>();
		int silence;
		boolean speaking;

		Session(OpusDecoder decoder) {
			this.decoder = decoder;
		}
	}

	private final VoiceStore store;
	private final VoiceConfig config;
	private final Map<UUID, Session> sessions = new HashMap<>();
	private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "unseen-voice-io");
		t.setDaemon(true);
		return t;
	});

	public ClipRecorder(VoiceStore store, VoiceConfig config) {
		this.store = store;
		this.config = config;
	}

	/** Called for every microphone packet, on the voice chat's own thread. */
	public void onMicrophone(VoicechatServerApi api, UUID player, byte[] opus) {
		if (!this.store.isOptedIn(player)) {
			this.forget(player);
			return;
		}
		Session session;
		synchronized (this.sessions) {
			session = this.sessions.computeIfAbsent(player, k -> new Session(api.createDecoder()));
		}
		synchronized (session) {
			// An empty packet means they let go of the key or stopped talking.
			if (opus == null || opus.length == 0) {
				this.finish(player, session);
				return;
			}
			short[] frame = session.decoder.decode(opus);
			boolean loud = Wav.rms(frame) >= this.config.speechRmsThreshold;

			if (loud) {
				if (!session.speaking) {
					session.speaking = true;
					session.frames.addAll(session.leadIn);
					session.leadIn.clear();
				}
				session.silence = 0;
				session.frames.add(frame);
				if (session.frames.size() * 20 >= this.config.maxClipMillis) {
					this.finish(player, session); // a long ramble: save it in pieces
				}
			} else if (session.speaking) {
				session.frames.add(frame);
				if (++session.silence >= SILENCE_FRAMES_TO_END) {
					this.finish(player, session);
				}
			} else {
				session.leadIn.add(frame);
				if (session.leadIn.size() > LEAD_IN_FRAMES) {
					session.leadIn.remove(0);
				}
			}
		}
	}

	/** Saves the clip if it's a sensible length, then starts fresh. Caller holds the session lock. */
	private void finish(UUID player, Session session) {
		int speechFrames = session.frames.size() - session.silence;
		boolean keep = session.speaking && speechFrames * 20 >= this.config.minClipMillis;
		short[] audio = keep ? Wav.concat(trimTail(session)) : null;
		session.frames.clear();
		session.leadIn.clear();
		session.silence = 0;
		session.speaking = false;
		session.decoder.resetState();
		if (audio != null) {
			this.io.execute(() -> this.save(player, audio));
		}
	}

	private static List<short[]> trimTail(Session session) {
		List<short[]> frames = new ArrayList<>(session.frames);
		int drop = Math.max(0, session.silence - 3); // keep a breath of quiet at the end
		for (int i = 0; i < drop && frames.size() > 1; i++) {
			frames.remove(frames.size() - 1);
		}
		return frames;
	}

	private void save(UUID player, short[] audio) {
		try {
			// Names sort oldest-first, so pruning always drops the oldest.
			Path file = this.store.clipsDir(player).resolve(String.format("%013d.wav", System.currentTimeMillis()));
			Wav.write(file, audio, Wav.VOICE_RATE);
			this.prune(player);
		} catch (IOException e) {
			Unseen.LOGGER.warn("Couldn't save a clip", e);
		}
	}

	private void prune(UUID player) throws IOException {
		List<Path> clips = new ArrayList<>(this.store.clips(player));
		while (!clips.isEmpty()
				&& (clips.size() > this.config.maxClipsPerPlayer || this.store.bytesIn(this.store.clipsDir(player)) > this.config.maxBytesPerPlayer)) {
			Files.deleteIfExists(clips.remove(0));
		}
	}

	public void forget(UUID player) {
		Session session;
		synchronized (this.sessions) {
			session = this.sessions.remove(player);
		}
		if (session != null) {
			synchronized (session) {
				session.decoder.close();
			}
		}
	}

	@Override
	public void close() {
		this.io.shutdown();
		synchronized (this.sessions) {
			this.sessions.values().forEach(s -> s.decoder.close());
			this.sessions.clear();
		}
	}
}
