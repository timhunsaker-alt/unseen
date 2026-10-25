package dev.unseen.director;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the entity writes down: roughly where you are and what you're doing, once a minute.
 * The same thing in the same place just makes the last entry longer. Used by the logbook.
 */
public final class ActivityLog {
	private ActivityLog() {
	}

	/** One stretch of doing one thing. Plain fields so Gson can save it. */
	public static class Entry {
		public long day;
		/** Minutes since midnight on the in-game clock (the game's day starts at 6:00). */
		public int minute;
		public String dimension;
		public int x;
		public int y;
		public int z;
		public String biome;
		/** sleeping, hurt, swimming, underground, inside, outside, nether, end */
		public String note;
		/** How long it lasted, in real minutes of play. */
		public int minutes;
	}

	public static void sample(ServerPlayer player, PlayerRecord record) {
		ServerLevel level = Senses.level(player);
		BlockPos pos = player.blockPosition();
		String dimension = level.dimension().identifier().getPath();
		String note = describe(player, level, pos, dimension);

		if (record.activity == null) {
			record.activity = new ArrayList<>();
		}
		List<Entry> log = record.activity;
		if (!log.isEmpty()) {
			Entry last = log.get(log.size() - 1);
			double dx = last.x - pos.getX();
			double dz = last.z - pos.getZ();
			if (last.dimension.equals(dimension) && last.note.equals(note)
					&& Math.sqrt(dx * dx + dz * dz) < Tuning.ACTIVITY_SAME_PLACE) {
				last.minutes++; // same thing, a little longer
				return;
			}
		}

		Entry entry = new Entry();
		entry.day = HauntLog.dayOf(level);
		entry.minute = HauntLog.minuteOf(level);
		entry.dimension = dimension;
		entry.x = pos.getX();
		entry.y = pos.getY();
		entry.z = pos.getZ();
		entry.biome = level.getBiome(pos).unwrapKey().map(key -> key.identifier().getPath()).orElse("unknown");
		entry.note = note;
		entry.minutes = 1;
		log.add(entry);
		while (log.size() > Tuning.ACTIVITY_MAX_ENTRIES) {
			log.remove(0);
		}
	}

	private static String describe(ServerPlayer player, ServerLevel level, BlockPos pos, String dimension) {
		if (player.isSleeping()) {
			return "sleeping";
		}
		if (player.getHealth() <= 6.0F) {
			return "hurt";
		}
		if (player.isInWater()) {
			return "swimming";
		}
		if (dimension.equals("the_nether")) {
			return "nether";
		}
		if (dimension.equals("the_end")) {
			return "end";
		}
		if (!level.canSeeSky(pos)) {
			return pos.getY() < 50 ? "underground" : "inside";
		}
		return "outside";
	}
}
