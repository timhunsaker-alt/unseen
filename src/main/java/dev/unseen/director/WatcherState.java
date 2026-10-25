package dev.unseen.director;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The entity's memory for one world, stored at <world>/data/unseen_watcher.json.
 * Delete that file to reset the haunting for that world.
 */
public class WatcherState {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type TYPE = new TypeToken<Saved>() {
	}.getType();

	/** What actually goes in the file. */
	private static class Saved {
		long worldTicks;
		Map<String, PlayerRecord> players = new HashMap<>();
	}

	private final Path file;
	private Saved saved = new Saved();

	public WatcherState(Path file) {
		this.file = file;
	}

	public long worldTicks() {
		return this.saved.worldTicks;
	}

	public void advance() {
		this.saved.worldTicks++;
	}

	public PlayerRecord player(UUID id) {
		return this.saved.players.computeIfAbsent(id.toString(), k -> new PlayerRecord());
	}

	public void load() {
		if (!Files.exists(this.file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(this.file)) {
			Saved loaded = GSON.fromJson(reader, TYPE);
			if (loaded != null) {
				if (loaded.players == null) {
					loaded.players = new HashMap<>();
				}
				this.saved = loaded;
			}
		} catch (IOException | RuntimeException e) {
			Unseen.LOGGER.warn("Couldn't read {}, starting fresh", this.file, e);
		}
	}

	public void save() {
		try {
			Files.createDirectories(this.file.getParent());
			Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temp)) {
				GSON.toJson(this.saved, TYPE, writer);
			}
			Files.move(temp, this.file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			Unseen.LOGGER.warn("Couldn't save {}", this.file, e);
		}
	}
}
