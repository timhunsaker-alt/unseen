package dev.unseen.events;

import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A single lantern burning in the dark, far off between the trees.
 * When you look away and walk toward it, it's gone. It never goes out while you're watching.
 */
public class StrayLightEvent implements HauntEvent {
	@Override
	public String id() {
		return "light";
	}

	@Override
	public int minDread() {
		return Tuning.LIGHT_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.LIGHT_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		if (!record.strayLights.isEmpty() || !level.isDarkOutside() || !level.canSeeSky(player.blockPosition())) {
			return false;
		}

		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Tuning.LIGHT_MIN_DISTANCE + random.nextInt(Tuning.LIGHT_MAX_DISTANCE - Tuning.LIGHT_MIN_DISTANCE + 1);
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
				continue;
			}
			// First free block above the ground, ignoring leaves, so it sits at the foot of the trees.
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos spot = new BlockPos(x, y, z);
			if (!Senses.canStandAt(level, spot) || Senses.canSee(player, spot)) {
				continue;
			}
			level.setBlockAndUpdate(spot, Blocks.LANTERN.defaultBlockState());
			record.strayLights.put(key(level, spot), now(level));
			return true;
		}
		return false;
	}

	@Override
	public void tickPlayer(ServerPlayer player, PlayerRecord record, long now) {
		if (record.strayLights.isEmpty()) {
			return;
		}
		ServerLevel level = Senses.level(player);
		String dimension = level.dimension().identifier().toString();
		Iterator<Map.Entry<String, Long>> it = record.strayLights.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Long> entry = it.next();
			String[] parts = entry.getKey().split("\\|", 2);
			if (parts.length != 2 || !parts[0].equals(dimension)) {
				continue; // they're elsewhere right now
			}
			BlockPos pos = BlockPos.of(Long.parseLong(parts[1]));
			long age = now - entry.getValue();
			if (!level.hasChunkAt(pos)) {
				continue;
			}
			if (!level.getBlockState(pos).is(Blocks.LANTERN) || age > Tuning.LIGHT_FORGET_AFTER) {
				it.remove(); // already gone, or they found it and it was left alone
				continue;
			}

			double distance = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(pos)));
			boolean close = distance < Tuning.LIGHT_VANISH_DISTANCE;
			boolean stale = age > Tuning.LIGHT_LIFETIME;
			// Only ever vanishes unseen.
			if ((close || stale) && !Senses.canSee(player, pos)) {
				level.removeBlock(pos, false);
				it.remove();
			}
		}
	}

	private static String key(ServerLevel level, BlockPos pos) {
		return level.dimension().identifier() + "|" + pos.asLong();
	}

	private static long now(ServerLevel level) {
		return dev.unseen.director.Director.get().now();
	}
}
