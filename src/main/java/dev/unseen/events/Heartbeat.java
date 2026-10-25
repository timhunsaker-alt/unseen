package dev.unseen.events;

import dev.unseen.director.Director;
import dev.unseen.director.Tuning;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * A heartbeat only the target player hears: two soft, low thuds (lub-dub), speeding up and getting a little louder,
 * then it just stops. Quiet by design. The mod never screams.
 */
public final class Heartbeat {
	private Heartbeat() {
	}

	public static void play(ServerPlayer player, int beats, double startBpm, double endBpm, float startVolume, float endVolume) {
		long at = 0;
		for (int i = 0; i < beats; i++) {
			double progress = beats <= 1 ? 1.0 : (double) i / (beats - 1);
			double bpm = startBpm + (endBpm - startBpm) * progress;
			int period = (int) Math.round(20.0 * 60.0 / bpm); // ticks from one beat to the next
			float volume = (float) (startVolume + (endVolume - startVolume) * progress);
			long beatAt = at;
			Director.get().later(beatAt, () -> thud(player, volume));
			Director.get().later(beatAt + Math.max(4, period / 3), () -> thud(player, volume * 0.7F)); // the "dub"
			at += period;
		}
	}

	private static void thud(ServerPlayer player, float volume) {
		if (!player.isAlive() || player.isRemoved()) {
			return;
		}
		// A sound packet to this player's connection alone, at their own position: nobody else hears it.
		player.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_BASEDRUM, SoundSource.AMBIENT,
				player.getX(), player.getY(), player.getZ(), Math.min(volume, 0.9F), Tuning.HEARTBEAT_PITCH,
				player.getRandom().nextLong()));
	}
}
