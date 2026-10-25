package dev.unseen.voice;

import dev.unseen.Unseen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Builds each opted-in player's bank of phrases in their own cloned voice, ahead of time,
 * so nothing slow ever happens mid-game (on a CPU-only server a phrase takes seconds to make).
 *
 * Steps for one player: stitch their saved clips into a reference recording, ask the voice
 * server to clone it, record consent, then generate every configured phrase one at a time.
 */
public class PhraseBank implements AutoCloseable {
	/** Don't retry a failed build for this long. */
	private static final long RETRY_AFTER_MILLIS = 10 * 60 * 1000L;

	private final VoiceStore store;
	private final VoiceConfig config;
	private final VoiceStudioClient client;
	private final Set<UUID> inProgress = new HashSet<>();
	private final Map<UUID, Long> lastFailure = new HashMap<>();
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "unseen-voice-bank");
		t.setDaemon(true);
		return t;
	});

	public PhraseBank(VoiceStore store, VoiceConfig config) {
		this.store = store;
		this.config = config;
		this.client = new VoiceStudioClient(config);
	}

	/** Queues a build for this player. Safe to call again; it does nothing if one is already running. */
	public synchronized boolean request(UUID player, String playerName) {
		if (!this.config.hasVoiceServer() || !this.store.isOptedIn(player) || !this.inProgress.add(player)) {
			return false;
		}
		this.worker.execute(() -> {
			try {
				this.build(player, playerName);
			} finally {
				synchronized (this) {
					this.inProgress.remove(player);
				}
			}
		});
		return true;
	}

	/** Called every few minutes: builds anyone who has enough clips and is missing phrases. */
	public void autoBuild(java.util.function.Function<UUID, String> nameOf) {
		if (!this.config.autoBuildBank || !this.config.hasVoiceServer()) {
			return;
		}
		for (UUID player : this.store.optedInPlayers()) {
			String name = nameOf.apply(player);
			long failedAt = this.lastFailure.getOrDefault(player, 0L);
			if (name != null && System.currentTimeMillis() - failedAt > RETRY_AFTER_MILLIS
					&& this.missing(player, name) > 0 && this.referenceSeconds(player) >= this.config.minReferenceSeconds) {
				this.request(player, name);
			}
		}
	}

	public int missing(UUID player, String playerName) {
		int missing = 0;
		for (String phrase : this.phrases(player, playerName)) {
			if (!Files.exists(this.store.bankDir(player).resolve(slug(phrase) + ".wav"))) {
				missing++;
			}
		}
		return missing;
	}

	public double referenceSeconds(UUID player) {
		long samples = 0;
		for (Path clip : this.store.clips(player)) {
			try {
				samples += Files.size(clip) / 2; // close enough: 16-bit mono, header is tiny
			} catch (IOException ignored) {
			}
		}
		return (double) samples / Wav.VOICE_RATE;
	}

	public List<String> phrases(UUID player, String playerName) {
		String name = this.store.player(player).nickname;
		if (name == null || name.isBlank()) {
			name = playerName.replace('_', ' ');
		}
		List<String> out = new ArrayList<>();
		for (String phrase : this.config.phrases) {
			String text = phrase.replace("{name}", name).strip();
			if (!text.isEmpty() && !out.contains(text)) {
				out.add(text);
			}
		}
		return out;
	}

	private void build(UUID player, String playerName) {
		try {
			VoiceStore.Player entry = this.store.player(player);
			if (entry.voice == null) {
				byte[] reference = this.reference(player);
				if (reference == null) {
					return; // not enough speech saved yet
				}
				if (!this.client.healthy()) {
					throw new IOException("The voice server at " + this.config.voiceStudioUrl + " isn't answering");
				}
				String voice = "unseen-" + player.toString().substring(0, 8);
				String profileId = this.client.createProfile(voice, reference);
				if (profileId != null) {
					this.client.attest(profileId, "Speaker opted in with /unseenvoice optin on this server.");
				}
				entry.voice = voice;
				entry.profileId = profileId;
				this.store.saveNow();
				Unseen.LOGGER.info("Made a voice for {}", playerName);
			}

			for (String phrase : this.phrases(player, playerName)) {
				Path file = this.store.bankDir(player).resolve(slug(phrase) + ".wav");
				if (Files.exists(file) || !this.store.isOptedIn(player)) {
					continue;
				}
				short[] speech = Wav.resample(this.client.speak(entry.voice, phrase), 24000, Wav.VOICE_RATE);
				Wav.write(file, speech, Wav.VOICE_RATE);
			}
			this.lastFailure.remove(player);
		} catch (IOException | RuntimeException e) {
			this.lastFailure.put(player, System.currentTimeMillis());
			Unseen.LOGGER.warn("Couldn't build the phrase bank for {}: {}", playerName, e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** The most recent clips, stitched into one recording of up to maxReferenceSeconds. Null if too short. */
	private byte[] reference(UUID player) throws IOException {
		List<Path> clips = new ArrayList<>(this.store.clips(player));
		List<short[]> chosen = new ArrayList<>();
		int samples = 0;
		int limit = this.config.maxReferenceSeconds * Wav.VOICE_RATE;
		for (int i = clips.size() - 1; i >= 0 && samples < limit; i--) {
			short[] clip = Wav.read(clips.get(i)).samples();
			chosen.add(0, clip);
			samples += clip.length;
		}
		if (samples < this.config.minReferenceSeconds * Wav.VOICE_RATE) {
			return null;
		}
		return Wav.toBytes(Wav.concat(chosen), Wav.VOICE_RATE);
	}

	static String slug(String phrase) {
		String clean = phrase.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
		if (clean.length() > 40) {
			clean = clean.substring(0, 40);
		}
		return clean + "-" + Integer.toHexString(phrase.hashCode());
	}

	@Override
	public void close() {
		this.worker.shutdownNow();
	}
}
