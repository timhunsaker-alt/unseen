package dev.unseen.events;

import dev.unseen.director.Director;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The torches you left along your path go out behind you, one at a time,
 * the farthest first. Each one a little closer than the last.
 */
public class TorchTrailEvent implements HauntEvent {
	@Override
	public String id() {
		return "torchtrail";
	}

	@Override
	public int minDread() {
		return Tuning.TORCH_TRAIL_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.TORCH_TRAIL_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos center = player.blockPosition();
		int r = Tuning.TORCH_TRAIL_RADIUS;
		int h = Tuning.TORCH_TRAIL_HEIGHT;
		double minSq = Tuning.TORCH_TRAIL_MIN_DISTANCE * Tuning.TORCH_TRAIL_MIN_DISTANCE;

		List<BlockPos> torches = new ArrayList<>();
		for (int x = -r; x <= r; x++) {
			for (int y = -h; y <= h; y++) {
				for (int z = -r; z <= r; z++) {
					BlockPos pos = center.offset(x, y, z);
					if (!level.hasChunkAt(pos)) {
						continue;
					}
					if (isTorch(level.getBlockState(pos)) && pos.distSqr(center) >= minSq && !Senses.canSee(player, pos)) {
						torches.add(pos);
					}
				}
			}
		}
		if (torches.size() < Tuning.TORCH_TRAIL_MIN_TORCHES) {
			return false;
		}

		// Farthest first, so the dark walks toward them.
		torches.sort(Comparator.comparingDouble((BlockPos p) -> p.distSqr(center)).reversed());
		List<BlockPos> chain = torches.subList(0, Math.min(Tuning.TORCH_TRAIL_MAX_TORCHES, torches.size()));

		UUID who = player.getUUID();
		long delay = 0;
		for (BlockPos pos : List.copyOf(chain)) {
			Director.get().later(delay, () -> snuff(player, who, pos));
			delay += Tuning.TORCH_TRAIL_STEP_MIN + random.nextInt(Tuning.TORCH_TRAIL_STEP_MAX - Tuning.TORCH_TRAIL_STEP_MIN + 1);
		}
		return true;
	}

	private static boolean isTorch(BlockState state) {
		return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
				|| state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH);
	}

	private static void snuff(ServerPlayer player, UUID who, BlockPos pos) {
		if (!player.isAlive() || player.isRemoved() || !player.getUUID().equals(who)) {
			return;
		}
		ServerLevel level = Senses.level(player);
		// If it's gone, or they're looking at it right now, skip it. It never happens in view.
		if (!level.hasChunkAt(pos) || !isTorch(level.getBlockState(pos)) || Senses.canSee(player, pos)) {
			return;
		}
		level.removeBlock(pos, false);
		level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.0F);
		level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 4, 0.05, 0.1, 0.05, 0.01);
	}
}
