package dev.unseen.events;

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
import net.minecraft.world.phys.Vec3;

/**
 * A written book turns up in one of your chests. No author. It lists small,
 * true things about you, in the order you did them, like someone taking notes.
 * It's an item, not chat: you only find it if you open the chest.
 */
public class JournalEvent implements HauntEvent {
	@Override
	public String id() {
		return "journal";
	}

	@Override
	public int minDread() {
		return Tuning.JOURNAL_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.JOURNAL_COOLDOWN;
	}

	@Override
	public int weight() {
		return 6;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home)) {
			return false;
		}
		if (player.distanceToSqr(Vec3.atCenterOf(home)) < Tuning.JOURNAL_MIN_PLAYER_DISTANCE * Tuning.JOURNAL_MIN_PLAYER_DISTANCE) {
			return false;
		}

		long day = record.activeTicks / Tuning.GAME_DAY + 1;
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
				Filterable.passThrough("Day " + day), "", 0, pages(record, home, day, random), true));
		return Senses.tuckInto(Senses.containersNear(level, home), book, random);
	}

	private static List<Filterable<Component>> pages(PlayerRecord record, BlockPos home, long day, Random random) {
		String bed = home.getX() + " " + home.getY() + " " + home.getZ();
		String slept = record.timesSlept <= 1 ? "once" : record.timesSlept + " times";

		List<String> facts = new ArrayList<>(List.of(
				"You have slept " + slept + ". I counted.",
				"Your bed is at " + bed + ".",
				"You close the door when you leave. I don't mind.",
				"You check the chest before you go to bed. You never check behind it.",
				"You light every torch. You never count them.",
				"You stop when you hear something. Then you keep walking.",
				"You look up when it gets quiet. Never behind."));
		java.util.Collections.shuffle(facts, random);

		List<Filterable<Component>> pages = new ArrayList<>();
		pages.add(page("Day " + day + "."));
		int count = 3 + random.nextInt(2);
		for (int i = 0; i < count; i++) {
			pages.add(page(facts.get(i)));
		}
		return pages;
	}

	private static Filterable<Component> page(String text) {
		return Filterable.passThrough(Component.literal(text));
	}
}
