package dev.unseen.events;

import dev.unseen.director.Director;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

/**
 * You're outside, near your house. Inside, a chest opens. A few seconds later
 * it closes. Nothing is different when you go in and look.
 */
public class CreakEvent implements HauntEvent {
	@Override
	public String id() {
		return "creak";
	}

	@Override
	public int minDread() {
		return Tuning.CREAK_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.CREAK_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home)) {
			return false;
		}

		List<BlockPos> chests = Senses.scanHome(level, home, s -> s.is(Blocks.CHEST) || s.is(Blocks.BARREL));
		chests.removeIf(pos -> {
			double distance = Math.sqrt(player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
			return distance < Tuning.CREAK_MIN_DISTANCE || distance > Tuning.CREAK_MAX_DISTANCE || Senses.canSee(player, pos);
		});
		if (chests.isEmpty()) {
			return false;
		}

		BlockPos chest = chests.get(random.nextInt(chests.size()));
		boolean barrel = level.getBlockState(chest).is(Blocks.BARREL);
		play(level, chest, barrel ? SoundEvents.BARREL_OPEN : SoundEvents.CHEST_OPEN);
		Director.get().later(Tuning.CREAK_OPEN_TICKS, () -> play(level, chest, barrel ? SoundEvents.BARREL_CLOSE : SoundEvents.CHEST_CLOSE));
		return true;
	}

	private static void play(ServerLevel level, BlockPos pos, SoundEvent sound) {
		level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS,
				Tuning.CREAK_VOLUME, 0.9F);
	}
}
