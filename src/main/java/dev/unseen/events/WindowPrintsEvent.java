package dev.unseen.events;

import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Footprints in the snow that walk up to one of your windows and stop.
 * Not the door. The window.
 */
public class WindowPrintsEvent implements HauntEvent {
	private static final int TRAIL_LENGTH = 20;

	private record Approach(BlockPos end, Direction outward) {
	}

	@Override
	public String id() {
		return "windowprints";
	}

	@Override
	public int minDread() {
		return Tuning.WINDOW_PRINTS_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.WINDOW_PRINTS_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home) || !Senses.farFrom(player, home, Tuning.WINDOW_PRINTS_MIN_PLAYER_DISTANCE)) {
			return false;
		}

		List<BlockPos> windows = Senses.scanHome(level, home,
				s -> BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().contains("glass"));
		List<Approach> approaches = new ArrayList<>();
		for (BlockPos window : windows) {
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos outside = window.relative(dir);
				if (!level.getBlockState(outside).isAir()) {
					continue;
				}
				BlockPos end = Senses.snowSurface(level, outside.getX(), window.getY(), outside.getZ());
				if (end != null) {
					approaches.add(new Approach(end, dir));
				}
			}
		}
		if (approaches.isEmpty()) {
			return false;
		}

		FootprintsEvent trail = new FootprintsEvent();
		for (int attempt = 0; attempt < 6; attempt++) {
			Approach approach = approaches.get(random.nextInt(approaches.size()));
			Vec3 to = Vec3.atBottomCenterOf(approach.end());
			Vec3 from = to.add(approach.outward().getStepX() * TRAIL_LENGTH, 0, approach.outward().getStepZ() * TRAIL_LENGTH);
			if (trail.placeTrail(player, level, from, to, approach.end().getY())) {
				return true;
			}
		}
		return false;
	}
}
