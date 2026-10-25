package dev.unseen.events;

import dev.unseen.director.Director;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * You're inside, alone. Three slow knocks on your own front door.
 * The door doesn't open. Nobody is there.
 */
public class KnockEvent implements HauntEvent {
	@Override
	public String id() {
		return "knock";
	}

	@Override
	public int minDread() {
		return Tuning.KNOCK_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.KNOCK_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home)) {
			return false;
		}
		// Only when they're indoors at home. The knock is for someone sitting in the room.
		if (level.canSeeSky(player.blockPosition()) || player.distanceToSqr(Vec3.atCenterOf(home)) > Tuning.HOME_RADIUS * Tuning.HOME_RADIUS) {
			return false;
		}

		double min = Tuning.KNOCK_MIN_DOOR_DISTANCE;
		double max = Tuning.KNOCK_MAX_DOOR_DISTANCE;
		List<BlockPos> doors = new ArrayList<>();
		int r = Tuning.HOME_RADIUS;
		int h = Tuning.HOME_HEIGHT;
		for (int x = -r; x <= r; x++) {
			for (int y = -h; y <= h; y++) {
				for (int z = -r; z <= r; z++) {
					BlockPos pos = home.offset(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (!(state.getBlock() instanceof DoorBlock)
							|| state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER
							|| state.getValue(BlockStateProperties.OPEN)) {
						continue;
					}
					double distance = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(pos)));
					if (distance >= min && distance <= max && !Senses.canSee(player, pos)) {
						doors.add(pos);
					}
				}
			}
		}
		if (doors.isEmpty()) {
			return false;
		}

		BlockPos door = doors.get(random.nextInt(doors.size()));
		UUID who = player.getUUID();
		for (int i = 0; i < Tuning.KNOCK_COUNT; i++) {
			Director.get().later((long) i * Tuning.KNOCK_GAP, () -> knock(player, who, door));
		}
		return true;
	}

	private static void knock(ServerPlayer player, UUID who, BlockPos door) {
		if (!player.isAlive() || player.isRemoved() || !player.getUUID().equals(who)) {
			return;
		}
		ServerLevel level = Senses.level(player);
		BlockState state = level.getBlockState(door);
		if (!(state.getBlock() instanceof DoorBlock) || state.getValue(BlockStateProperties.OPEN)) {
			return; // someone opened it, or it's gone; stop
		}
		level.playSound(null, door.getX() + 0.5, door.getY() + 1.0, door.getZ() + 0.5,
				state.getSoundType().getHitSound(), SoundSource.BLOCKS, Tuning.KNOCK_VOLUME, Tuning.KNOCK_PITCH);
	}
}
