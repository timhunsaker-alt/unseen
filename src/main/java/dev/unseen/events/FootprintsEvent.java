package dev.unseen.events;

import dev.unseen.UnseenBlocks;
import dev.unseen.block.FootprintBlock;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A line of footprints in the snow. Two versions:
 *  - Toward the house: walks in from the trees and stops at your house. None lead away.
 *  - Behind you: walks up to where you're standing and stops three blocks back.
 * Snow only. If there isn't enough snow, nothing happens.
 */
public class FootprintsEvent implements HauntEvent {
	private static final int TRAIL_LENGTH = 22;
	private static final double MIN_SNOWY_FRACTION = 0.7;

	@Override
	public String id() {
		return "footprints";
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
	public int weight() {
		return 12;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);

		BlockPos home = homeIn(level, record);
		if (home != null) {
			double distance = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(home)));
			// Far enough that they'll come home and find it, close enough that the house is loaded.
			if (distance >= 40 && distance <= 120 && random.nextBoolean() && towardHome(player, level, home, random)) {
				return true;
			}
		}
		return behindPlayer(player, level);
	}

	public static BlockPos homeIn(ServerLevel level, PlayerRecord record) {
		if (record.homePos == null || !level.dimension().identifier().toString().equals(record.homeDimension)) {
			return null;
		}
		return BlockPos.of(record.homePos);
	}

	private boolean towardHome(ServerPlayer player, ServerLevel level, BlockPos home, Random random) {
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dx = Math.cos(angle);
			double dz = Math.sin(angle);

			// Walk outward from the bed until we find snowy ground: that's where the prints stop.
			BlockPos end = null;
			for (int r = 3; r <= 12 && end == null; r++) {
				end = Senses.snowSurface(level, home.getX() + (int) Math.round(dx * r), home.getY(),
						home.getZ() + (int) Math.round(dz * r));
			}
			if (end == null) {
				continue;
			}

			Vec3 to = Vec3.atBottomCenterOf(end);
			Vec3 from = to.add(dx * TRAIL_LENGTH, 0, dz * TRAIL_LENGTH);
			if (placeTrail(player, level, from, to, end.getY())) {
				return true;
			}
		}
		return false;
	}

	private boolean behindPlayer(ServerPlayer player, ServerLevel level) {
		BlockPos feet = player.blockPosition();
		if (Senses.snowSurface(level, feet.getX(), feet.getY(), feet.getZ()) == null) {
			return false;
		}
		Vec3 look = player.getLookAngle();
		Vec3 back = new Vec3(-look.x, 0, -look.z);
		if (back.lengthSqr() < 1.0E-4) {
			return false; // looking straight up or down: no clear "behind"
		}
		back = back.normalize();

		Vec3 here = player.position();
		Vec3 to = here.add(back.scale(3));
		Vec3 from = here.add(back.scale(3 + TRAIL_LENGTH));
		return placeTrail(player, level, from, to, feet.getY());
	}

	/** Lays prints from {@code from} to {@code to}. All or nothing. */
	private boolean placeTrail(ServerPlayer player, ServerLevel level, Vec3 from, Vec3 to, int refY) {
		Vec3 path = to.subtract(from);
		double length = path.horizontalDistance();
		if (length < 1) {
			return false;
		}
		Vec3 step = new Vec3(path.x / length, 0, path.z / length);
		Direction facing = Math.abs(step.x) > Math.abs(step.z)
				? (step.x > 0 ? Direction.EAST : Direction.WEST)
				: (step.z > 0 ? Direction.SOUTH : Direction.NORTH);

		BlockPos playerColumn = player.blockPosition();
		List<BlockPos> prints = new ArrayList<>();
		Set<Long> columns = new HashSet<>();
		int columnsTried = 0;
		int y = refY;

		for (int i = 0; i <= (int) length; i++) {
			int x = (int) Math.floor(from.x + step.x * i);
			int z = (int) Math.floor(from.z + step.z * i);
			if (!columns.add(BlockPos.asLong(x, 0, z))) {
				continue;
			}
			if (x == playerColumn.getX() && z == playerColumn.getZ()) {
				continue;
			}
			columnsTried++;
			BlockPos spot = Senses.snowSurface(level, x, y, z);
			if (spot == null) {
				continue;
			}
			if (Senses.canSee(player, spot)) {
				return false; // never while they're looking
			}
			y = spot.getY(); // follow the ground up and down hills
			prints.add(spot);
		}

		if (columnsTried == 0 || prints.size() < columnsTried * MIN_SNOWY_FRACTION) {
			return false;
		}

		BlockState base = UnseenBlocks.FOOTPRINT.defaultBlockState().setValue(FootprintBlock.FACING, facing);
		boolean left = true;
		for (BlockPos spot : prints) {
			level.setBlockAndUpdate(spot, base.setValue(FootprintBlock.LEFT, left));
			left = !left;
		}
		return true;
	}
}
