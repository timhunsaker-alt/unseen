package dev.unseen.director;

import dev.unseen.events.HauntEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What it has done to each player, in plain words, kept with its memory of them. Nothing here is ever shown
 * on its own: a player sees it only if they ask (/unseenlog).
 */
public final class HauntLog {
	private HauntLog() {
	}

	/** One thing that happened. Plain fields so Gson can save it. */
	public static class Entry {
		public long day;
		/** Minutes since midnight on the in-game clock. */
		public int minute;
		public String id;
		public String text;
		/** Where the player was when it happened. */
		public String dimension;
		public int x;
		public int y;
		public int z;
	}

	/** What each scare means, by id, for the log. Other mods' scares can describe themselves. */
	private static final Map<String, String> TEXT = Map.ofEntries(
			Map.entry("footprints", "Footprints were pressed into the snow."),
			Map.entry("circle", "Footprints walked a slow circle around your house."),
			Map.entry("windowprints", "Footprints walked up to one of your windows and stopped."),
			Map.entry("torch", "A torch in your house was taken down and put away in a chest."),
			Map.entry("torchtrail", "The torches behind you went out, one at a time."),
			Map.entry("door", "A door at your house was found open."),
			Map.entry("knock", "Someone knocked on your door three times."),
			Map.entry("creak", "A chest in your house opened, then closed."),
			Map.entry("footsteps", "Footsteps followed you and stopped when you stopped."),
			Map.entry("echo", "Your own footsteps came back a moment late."),
			Map.entry("journal", "A book of notes about you was left in a chest."),
			Map.entry("map", "A map of your base with an X on your bed was left in a chest."),
			Map.entry("sign", "A sign with something you said on it was put up near your house."),
			Map.entry("deathflower", "A flower was left where you died."),
			Map.entry("doorstep", "A flower was left on your doorstep."),
			Map.entry("candle", "A candle was lit by your bed."),
			Map.entry("tamper", "Something in your base was turned or relit."),
			Map.entry("sorted", "One of your chests was sorted."),
			Map.entry("crops", "A row of your crops was harvested and replanted."),
			Map.entry("light", "A lantern was lit far away in the dark."),
			Map.entry("witness", "The animals nearby all stared at the same empty spot."),
			Map.entry("bell", "A bell rang far away."),
			Map.entry("heartbeat", "You heard your own heartbeat."),
			Map.entry("logbook", "A book listing what you did, and when, was left in a chest."),
			Map.entry("wander", "Candles drifted around your house."),
			Map.entry("return", "Your house was arranged while you were away."),
			Map.entry("wingbeats", "Wingbeats passed far overhead."),
			Map.entry("voice", "Your own voice spoke from behind you."),
			Map.entry("steak", "A steak appeared in a furnace near you and started to cook."),
			Map.entry("photo", "A photo of you sleeping was left in a chest."),
			Map.entry("bedturn", "You woke up facing the wall."),
			Map.entry("dread", "It grew bolder."),
			Map.entry("quiet", "Everything went quiet."));

	public static String describe(HauntEvent event) {
		String own = event.description();
		return own != null ? own : TEXT.getOrDefault(event.id(), "Something happened (" + event.id() + ").");
	}

	/** Things that aren't scares from the list, like a photo, the quiet, or dread rising. */
	public static void add(ServerPlayer player, PlayerRecord record, String id) {
		add(player, record, id, TEXT.getOrDefault(id, id));
	}

	public static void add(ServerPlayer player, PlayerRecord record, String id, String text) {
		ServerLevel level = Senses.level(player);
		BlockPos pos = player.blockPosition();
		Entry entry = new Entry();
		entry.day = dayOf(level);
		entry.minute = minuteOf(level);
		entry.id = id;
		entry.text = text;
		entry.dimension = level.dimension().identifier().getPath();
		entry.x = pos.getX();
		entry.y = pos.getY();
		entry.z = pos.getZ();

		if (record.history == null) {
			record.history = new ArrayList<>();
		}
		record.history.add(entry);
		while (record.history.size() > Tuning.LOG_MAX_ENTRIES) {
			record.history.remove(0);
		}
	}

	/** The last {@code count} entries, oldest first, one per line, for showing to a player. */
	public static String format(PlayerRecord record, int count) {
		List<Entry> history = record.history == null ? List.of() : record.history;
		if (history.isEmpty()) {
			return "Nothing has happened that it has written down.";
		}
		int from = Math.max(0, history.size() - count);
		StringBuilder out = new StringBuilder();
		out.append("What has happened (").append(history.size() - from).append(" of ").append(history.size()).append("):");
		for (int i = from; i < history.size(); i++) {
			Entry e = history.get(i);
			out.append('\n').append(String.format("Day %d, %02d:%02d  %s  (%d %d %d%s)", e.day, e.minute / 60, e.minute % 60,
					e.text, e.x, e.y, e.z, e.dimension.equals("overworld") ? "" : ", " + e.dimension.replace('_', ' ')));
		}
		return out.toString();
	}

	public static long dayOf(ServerLevel level) {
		return level.getOverworldClockTime() / 24000 + 1;
	}

	/** Minutes since midnight on the game's clock (its day begins at 6:00). */
	public static int minuteOf(ServerLevel level) {
		long ticks = level.getOverworldClockTime() % 24000;
		return (int) (((ticks / 1000 + 6) % 24) * 60 + (ticks % 1000) * 60 / 1000);
	}
}
