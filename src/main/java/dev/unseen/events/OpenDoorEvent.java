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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * You closed the door when you left. It's open now. Works on iron doors too.
 */
public class OpenDoorEvent implements HauntEvent {
	/** Same flags vanilla uses when a door opens: update clients, update immediately. */
	private static final int DOOR_FLAGS = 2 | 8;

	@Override
	public String id() {
		return "door";
	}

	@Override
	public int minDread() {
		return 1;
	}

	@Override
	public long cooldown() {
		return Tuning.GAME_DAY;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home)) {
			return false;
		}
		if (player.distanceToSqr(Vec3.atCenterOf(home)) < 20 * 20) {
			return false;
		}

		List<BlockPos> closedDoors = new ArrayList<>();
		int r = Tuning.HOME_RADIUS;
		int h = Tuning.HOME_HEIGHT;
		for (int x = -r; x <= r; x++) {
			for (int y = -h; y <= h; y++) {
				for (int z = -r; z <= r; z++) {
					BlockPos pos = home.offset(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (state.getBlock() instanceof DoorBlock
							&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
							&& !state.getValue(BlockStateProperties.OPEN)
							&& !Senses.canSee(player, pos)) {
						closedDoors.add(pos);
					}
				}
			}
		}
		if (closedDoors.isEmpty()) {
			return false;
		}

		BlockPos lower = closedDoors.get(random.nextInt(closedDoors.size()));
		BlockPos upper = lower.above();
		level.setBlock(lower, level.getBlockState(lower).setValue(BlockStateProperties.OPEN, true), DOOR_FLAGS);
		BlockState upperState = level.getBlockState(upper);
		if (upperState.getBlock() instanceof DoorBlock) {
			level.setBlock(upper, upperState.setValue(BlockStateProperties.OPEN, true), DOOR_FLAGS);
		}
		return true;
	}
}
