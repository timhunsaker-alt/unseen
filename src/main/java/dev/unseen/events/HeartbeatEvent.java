package dev.unseen.events;

import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Tuning;
import java.util.Random;
import net.minecraft.server.level.ServerPlayer;

/**
 * Your own heartbeat, slow at first, only you can hear it. It speeds up. Then it stops.
 * Soft and low: the mod never screams.
 */
public class HeartbeatEvent implements HauntEvent {
	@Override
	public String id() {
		return "heartbeat";
	}

	@Override
	public int minDread() {
		return Tuning.HEARTBEAT_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.HEARTBEAT_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		boolean aftermath = record.dread() >= Tuning.MAX_DREAD;
		int min = aftermath ? Tuning.HEARTBEAT_BEATS_MIN_6 : Tuning.HEARTBEAT_BEATS_MIN;
		int max = aftermath ? Tuning.HEARTBEAT_BEATS_MAX_6 : Tuning.HEARTBEAT_BEATS_MAX;
		int beats = min + random.nextInt(max - min + 1);
		double end = aftermath ? Tuning.HEARTBEAT_BPM_END_6 : Tuning.HEARTBEAT_BPM_END;
		Heartbeat.play(player, beats, Tuning.HEARTBEAT_BPM_START, end, Tuning.HEARTBEAT_VOLUME_START, Tuning.HEARTBEAT_VOLUME_END);
		return true;
	}
}
