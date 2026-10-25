package dev.unseen.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.unseen.Unseen;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Where voices are kept, in <world>/data/unseen_voice/. Nothing leaves this folder except
 * the short reference recording sent to your own voice server. Delete the folder to forget everyone.
 *
 *   players.json            who opted in, nicknames, which cloned voice is theirs
 *   <uuid>/clips/*.wav      short things they said
 *   <uuid>/bank/*.wav       phrases generated in their voice
 */
public class VoiceStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type TYPE = new TypeToken<Map<String, Player>>() {
	}.getType();

	/** What's remembered about one player. Plain fields for Gson. */
	public static class Player {
		public boolean optedIn;
		public long optedInAt;
		/** How to say their name, if it isn't how it's spelled. */
		public String nickname;
		/** Name of their cloned voice on the voice server, once made. */
		public String voice;
		public String profileId;
		/** Extra sentences added with /unseenvoice phrase, said in addition to the configured ones. */
		public List<String> phrases = new ArrayList<>();
	}

	private final Path root;
	private Map<String, Player> players = new HashMap<>();

	public VoiceStore(Path root) {
		this.root = root;
		this.load();
	}

	// ---------------------------------------------------------------- who

	public synchronized boolean isOptedIn(UUID id) {
		Player p = this.players.get(id.toString());
		return p != null && p.optedIn;
	}

	public synchronized Player player(UUID id) {
		return this.players.computeIfAbsent(id.toString(), k -> new Player());
	}

	public synchronized void optIn(UUID id) {
		Player p = this.player(id);
		p.optedIn = true;
		p.optedInAt = System.currentTimeMillis();
		this.save();
	}

	/** Stops recording and deletes everything kept for this player. */
	public synchronized void optOutAndWipe(UUID id) {
		this.players.remove(id.toString());
		this.save();
		this.deleteTree(this.root.resolve(id.toString()));
	}

	public synchronized void setNickname(UUID id, String nickname) {
		this.player(id).nickname = nickname == null || nickname.isBlank() ? null : nickname.strip();
		this.save();
	}

	/** Adds a sentence for this player. False if it's a duplicate or they have too many already. */
	public synchronized boolean addPhrase(UUID id, String phrase) {
		Player p = this.player(id);
		if (p.phrases == null) {
			p.phrases = new ArrayList<>();
		}
		String text = phrase.strip();
		if (text.isEmpty() || text.length() > 200 || p.phrases.size() >= 40 || p.phrases.contains(text)) {
			return false;
		}
		p.phrases.add(text);
		this.save();
		return true;
	}

	public synchronized List<UUID> optedInPlayers() {
		List<UUID> ids = new ArrayList<>();
		this.players.forEach((k, v) -> {
			if (v.optedIn) {
				ids.add(UUID.fromString(k));
			}
		});
		return ids;
	}

	public synchronized void saveNow() {
		this.save();
	}

	// ---------------------------------------------------------------- files

	public Path clipsDir(UUID id) {
		return this.root.resolve(id.toString()).resolve("clips");
	}

	public Path bankDir(UUID id) {
		return this.root.resolve(id.toString()).resolve("bank");
	}

	/** Oldest first. */
	public List<Path> clips(UUID id) {
		return this.wavs(this.clipsDir(id));
	}

	public List<Path> bank(UUID id) {
		return this.wavs(this.bankDir(id));
	}

	private List<Path> wavs(Path dir) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(p -> p.toString().endsWith(".wav")).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	public long bytesIn(Path dir) {
		long total = 0;
		for (Path p : this.wavs(dir)) {
			try {
				total += Files.size(p);
			} catch (IOException ignored) {
			}
		}
		return total;
	}

	// ---------------------------------------------------------------- saving

	private void load() {
		Path file = this.root.resolve("players.json");
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			Map<String, Player> loaded = GSON.fromJson(reader, TYPE);
			if (loaded != null) {
				this.players = loaded;
			}
		} catch (IOException | RuntimeException e) {
			Unseen.LOGGER.warn("Couldn't read {}", file, e);
		}
	}

	private void save() {
		try {
			Files.createDirectories(this.root);
			try (Writer writer = Files.newBufferedWriter(this.root.resolve("players.json"))) {
				GSON.toJson(this.players, TYPE, writer);
			}
		} catch (IOException e) {
			Unseen.LOGGER.warn("Couldn't save voice data", e);
		}
	}

	private void deleteTree(Path dir) {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					Unseen.LOGGER.warn("Couldn't delete {}", p, e);
				}
			});
		} catch (IOException e) {
			Unseen.LOGGER.warn("Couldn't delete {}", dir, e);
		}
	}
}
