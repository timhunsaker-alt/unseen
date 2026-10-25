package dev.unseen.events;

import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * While you're out, one torch in your house goes missing.
 * The torch is put away neatly in one of your chests.
 */
public class MissingTorchEvent implements HauntEvent {
	@Override
	public String id() {
		return "torch";
	}

	@Override
	public int minDread() {
		return 1;
	}

	@Override
	public long cooldown() {
		return 2L * Tuning.GAME_DAY;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home)) {
			return false;
		}
		// Only while they're away from the house.
		if (player.distanceToSqr(Vec3.atCenterOf(home)) < 24 * 24) {
			return false;
		}

		List<BlockPos> torches = new ArrayList<>();
		int r = Tuning.HOME_RADIUS;
		int h = Tuning.HOME_HEIGHT;
		for (int x = -r; x <= r; x++) {
			for (int y = -h; y <= h; y++) {
				for (int z = -r; z <= r; z++) {
					BlockPos pos = home.offset(x, y, z);
					BlockState state = level.getBlockState(pos);
					if ((state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) && !Senses.canSee(player, pos)) {
						torches.add(pos);
					}
				}
			}
		}
		// Leave them at least a couple of torches. It's cruel, not *that* cruel.
		if (torches.size() < 3) {
			return false;
		}

		BlockPos victim = torches.get(random.nextInt(torches.size()));
		level.removeBlock(victim, false);
		Senses.tuckInto(Senses.containersNear(level, home), new ItemStack(Items.TORCH), random);
		return true;
	}
}
