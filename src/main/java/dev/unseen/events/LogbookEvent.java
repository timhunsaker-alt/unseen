package dev.unseen.events;

import dev.unseen.director.ActivityLog;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * A book in your chest that lists what you did, where and when, in detail, like someone has been
 * taking notes beside you. Written from what it actually saw (see ActivityLog). Unsigned.
 */
public class LogbookEvent implements HauntEvent {
	@Override
	public String id() {
		return "logbook";
	}

	@Override
	public int minDread() {
		return Tuning.LOGBOOK_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.LOGBOOK_COOLDOWN;
	}

	@Override
	public int weight() {
		return Tuning.LOGBOOK_WEIGHT;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		if (record.activity == null || record.activity.size() < 3) {
			return false;
		}
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home) || !Senses.farFrom(player, home, Tuning.LOGBOOK_MIN_PLAYER_DISTANCE)) {
			return false;
		}

		List<ActivityLog.Entry> recent = record.activity.subList(
				Math.max(0, record.activity.size() - Tuning.LOGBOOK_MAX_ENTRIES), record.activity.size());
		long day = recent.get(recent.size() - 1).day;

		List<Filterable<Component>> pages = new ArrayList<>();
		pages.add(page("Day " + day + ".\n\nI wrote down what you did."));
		for (int i = 0; i < recent.size(); i += 2) {
			StringBuilder text = new StringBuilder();
			for (int j = i; j < Math.min(i + 2, recent.size()); j++) {
				if (text.length() > 0) {
					text.append("\n\n");
				}
				text.append(line(recent.get(j), record.dragonSlain));
			}
			pages.add(page(text.toString()));
		}
		pages.add(page(closing(random)));

		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
				Filterable.passThrough("Log, day " + day), "", 0, pages, true));
		return Senses.tuckInto(Senses.containersNear(level, home), book, random);
	}

	/** One entry, in second person, as plainly as possible. */
	static String line(ActivityLog.Entry e, boolean dragonSlain) {
		String when = String.format("Day %d, %02d:%02d.", e.day, e.minute / 60, e.minute % 60);
		String where = e.x + " " + e.y + " " + e.z;
		String biome = e.biome.replace('_', ' ');
		String length = e.minutes <= 1 ? "" : " " + e.minutes + " minutes.";
		String what = switch (e.note) {
			case "sleeping" -> "You slept at " + where + ".";
			case "hurt" -> "You were hurt near " + where + ". You kept going.";
			case "swimming" -> "You were in the water near " + where + ".";
			case "nether" -> "You were in the Nether, at " + where + ". It was hot.";
			case "end" -> dragonSlain ? "You were on the island. It was quiet there." : "You were on the island, at " + where + ".";
			case "underground" -> "You were underground at " + where + ", under the " + biome + ".";
			case "inside" -> "You were inside, at " + where + ".";
			default -> "You were outside, at " + where + ", in the " + biome + ".";
		};
		return when + " " + what + length;
	}

	private static String closing(Random random) {
		String[] endings = {
				"You didn't look behind you.",
				"You were never alone for this.",
				"There is more. I wrote it down.",
				"You looked up once. That was all."};
		return endings[random.nextInt(endings.length)];
	}

	private static Filterable<Component> page(String text) {
		return Filterable.passThrough(Component.literal(text));
	}
}
