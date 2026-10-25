package dev.unseen.events;

import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * One of your chests is sorted. Every item, in order, nothing missing.
 * You were never that tidy.
 */
public class SortedChestEvent implements HauntEvent {
	@Override
	public String id() {
		return "sorted";
	}

	@Override
	public int minDread() {
		return Tuning.SORTED_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.SORTED_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home) || !Senses.farFrom(player, home, Tuning.SORTED_MIN_PLAYER_DISTANCE)) {
			return false;
		}

		List<Container> candidates = new ArrayList<>();
		for (Container container : Senses.containersNear(level, home)) {
			if (count(container) >= Tuning.SORTED_MIN_ITEMS && !isSorted(container)) {
				candidates.add(container);
			}
		}
		if (candidates.isEmpty()) {
			return false;
		}

		Container chest = candidates.get(random.nextInt(candidates.size()));
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (!chest.getItem(i).isEmpty()) {
				stacks.add(chest.getItem(i));
			}
		}
		stacks.sort(ORDER);
		chest.clearContent();
		for (int i = 0; i < stacks.size(); i++) {
			chest.setItem(i, stacks.get(i));
		}
		chest.setChanged();
		return true;
	}

	private static final Comparator<ItemStack> ORDER = Comparator
			.comparing((ItemStack s) -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
			.thenComparing(ItemStack::getCount, Comparator.reverseOrder());

	private static int count(Container container) {
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (!container.getItem(i).isEmpty()) {
				n++;
			}
		}
		return n;
	}

	/** Already tidy: packed from slot 0 with no gaps, in order. Nothing to do. */
	private static boolean isSorted(Container container) {
		List<ItemStack> stacks = new ArrayList<>();
		boolean gap = false;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.isEmpty()) {
				gap = true;
			} else if (gap) {
				return false;
			} else {
				stacks.add(stack);
			}
		}
		for (int i = 1; i < stacks.size(); i++) {
			if (ORDER.compare(stacks.get(i - 1), stacks.get(i)) > 0) {
				return false;
			}
		}
		return true;
	}
}
