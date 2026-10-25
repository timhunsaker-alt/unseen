package dev.unseen.events;

import dev.unseen.director.Director;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.Random;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Far away, at night: slow wingbeats, passing overhead and fading. No roar. Nothing is there.
 * Only after the dragon is gone.
 */
public class WingbeatsEvent implements HauntEvent {
	@Override
	public String id() {
		return "wingbeats";
	}

	@Override
	public int minDread() {
		return Tuning.WINGBEATS_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.WINGBEATS_COOLDOWN;
	}

	@Override
	public int weight() {
		return 12;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		if (!level.isDarkOutside() || !level.canSeeSky(player.blockPosition())) {
			return false;
		}

		// It passes by at a distance, in a straight line, ending on the far side of you.
		double angle = random.nextDouble() * Math.PI * 2;
		double sx = Math.cos(angle);
		double sz = Math.sin(angle);
		double px = -sz; // sideways
		double pz = sx;
		double distance = Tuning.WINGBEATS_DISTANCE;
		UUID who = player.getUUID();
		for (int i = 0; i < Tuning.WINGBEATS_COUNT; i++) {
			double along = -1.0 + 2.0 * i / Math.max(1, Tuning.WINGBEATS_COUNT - 1);
			double x = player.getX() + sx * distance + px * along * distance;
			double z = player.getZ() + sz * distance + pz * along * distance;
			double y = player.getY() + 25;
			float volume = (float) (Tuning.WINGBEATS_VOLUME * (1.0 - 0.15 * i));
			Director.get().later((long) i * Tuning.WINGBEATS_GAP, () -> {
				if (player.isAlive() && !player.isRemoved() && player.getUUID().equals(who)) {
					level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.AMBIENT, volume, Tuning.WINGBEATS_PITCH);
				}
			});
		}
		return true;
	}
}
