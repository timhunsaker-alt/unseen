package dev.unseen.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.unseen.Unseen;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Everything you can change, in config/unseen-voice.json. Written with defaults the first time it runs.
 * Plain public fields so Gson can read and write it.
 */
public class VoiceConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// --- The voice server (VoiceStudio). Leave the URL empty to skip AI voices and only replay recorded clips. ---

	/** For example http://192.168.1.50:3900 */
	public String voiceStudioUrl = "";
	/** The OMNIVOICE_API_KEY you gave the server. Sent as a Bearer token. */
	public String apiKey = "";
	public String model = "omnivoice";
	/** As VoiceStudio names it, for example "English". */
	public String language = "English";
	/**
	 * How hard the voice server works on each phrase. Time grows with it: on a busy 4-CPU server about
	 * 26 s at 4, 48 s at 8, 96 s at 16 (VoiceStudio's own default), and phrases get cleaner as it goes up.
	 */
	public int numStep = 8;
	/** Build each player's phrase bank on its own once enough clips are saved. */
	public boolean autoBuildBank = true;
	/** Seconds of recorded speech needed (and used at most) as the reference for cloning. */
	public int minReferenceSeconds = 6;
	public int maxReferenceSeconds = 14;
	/** {name} becomes the player's nickname, or their name with underscores turned into spaces. */
	public List<String> phrases = List.of("hey", "hey, over here", "over here", "is that you?", "come here",
			"{name}", "{name}?", "hey {name}");

	// --- Recording (only for players who typed /unseenvoice optin) ---

	public int maxClipsPerPlayer = 60;
	public long maxBytesPerPlayer = 25_000_000L;
	public int minClipMillis = 500;
	public int maxClipMillis = 4000;
	/** How loud a moment has to be to count as speech. Raise it if clips are mostly background noise. */
	public int speechRmsThreshold = 700;

	// --- Playback ---

	public double playbackVolume = 0.8;
	/** Below 1 is slower and lower. 0.94 is just wrong enough. */
	public double playbackSpeed = 0.94;
	public double minDistance = 10.0;
	public double maxDistance = 18.0;
	/** Players within this many blocks of the sound can hear it; only the target player ever does. */
	public float hearingDistance = 40.0F;
	/** How much more often a generated phrase is picked than a recorded clip. */
	public int phraseWeight = 3;

	public static VoiceConfig load(Path file) {
		VoiceConfig config = new VoiceConfig();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				VoiceConfig loaded = GSON.fromJson(reader, VoiceConfig.class);
				if (loaded != null) {
					config = loaded;
				}
			} catch (IOException | RuntimeException e) {
				Unseen.LOGGER.warn("Couldn't read {}, using defaults", file, e);
			}
		}
		config.save(file); // writes new settings in too, so the file always shows every option
		return config;
	}

	private void save(Path file) {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			Unseen.LOGGER.warn("Couldn't write {}", file, e);
		}
	}

	public boolean hasVoiceServer() {
		return this.voiceStudioUrl != null && !this.voiceStudioUrl.isBlank();
	}
}
