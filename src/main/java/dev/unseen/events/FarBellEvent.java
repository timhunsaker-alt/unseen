package dev.unseen.events;

import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * One bell, far away, at night. There's no village. It rings once and doesn't ring again.
 */
public class FarBellEvent implements HauntEvent {
	@Override
	public String id() {
		return "bell";
	}

	@Override
	public int minDread() {
		return Tuning.BELL_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.BELL_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		// Night, and out in the open: it should be heard, and it should be dark.
		if (!level.isDarkOutside() || !level.canSeeSky(player.blockPosition())) {
			return false;
		}

		double angle = random.nextDouble() * Math.PI * 2;
		double distance = Tuning.BELL_MIN_DISTANCE + random.nextInt(Tuning.BELL_MAX_DISTANCE - Tuning.BELL_MIN_DISTANCE + 1);
		double x = player.getX() + Math.cos(angle) * distance;
		double z = player.getZ() + Math.sin(angle) * distance;
		BlockPos at = BlockPos.containing(x, player.getY(), z);
		if (!level.hasChunkAt(at)) {
			return false;
		}

		level.playSound(null, x, player.getY() + 3, z, SoundEvents.BELL_BLOCK, SoundSource.AMBIENT,
				Tuning.BELL_VOLUME, Tuning.BELL_PITCH);
		return true;
	}
}
