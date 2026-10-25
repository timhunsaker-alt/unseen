package dev.unseen.events;

import dev.unseen.director.Director;
import dev.unseen.director.HauntLog;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * You wake up and you're facing the wall, not the room.
 * Not a random scare: it hooks into getting out of bed (see Director.onWake).
 */
public final class BedTurn {
	private BedTurn() {
	}

	public static void apply(ServerPlayer player) {
		if (!player.isAlive() || player.isRemoved() || player.isSleeping()) {
			return;
		}
		ServerLevel level = Senses.level(player);
		BlockPos feet = player.blockPosition();

		Direction nearest = null;
		int nearestDistance = Integer.MAX_VALUE;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			for (int d = 1; d <= Tuning.BED_TURN_WALL_RANGE && d < nearestDistance; d++) {
				BlockPos pos = feet.relative(dir, d).above();
				if (!level.hasChunkAt(pos)) {
					break;
				}
				if (level.getBlockState(pos).isSolidRender()) {
					nearest = dir;
					nearestDistance = d;
					break;
				}
			}
		}
		if (nearest == null) {
			return; // open room: nothing to face
		}
		player.connection.teleport(player.getX(), player.getY(), player.getZ(), nearest.toYRot(), 5.0F);
		PlayerRecord record = Director.get().record(player);
		if (record != null) {
			HauntLog.add(player, record, "bedturn");
		}
	}
}
