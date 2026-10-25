package dev.unseen.events;

import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Once, after the dragon, while you're far from home: your house is arranged. A candle lit by the bed,
 * a door standing open, a flower on the doorstep. Someone was waiting. You find it when you get back.
 */
public class ReturnEvent implements HauntEvent {
	private final List<HauntEvent> parts = List.of(new CandleEvent(), new OpenDoorEvent(), new DoorstepEvent());

	@Override
	public String id() {
		return "return";
	}

	@Override
	public int minDread() {
		return Tuning.RETURN_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return 0;
	}

	@Override
	public int weight() {
		return Tuning.RETURN_WEIGHT;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		if (record.returnDone) {
			return false;
		}
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home) || !Senses.farFrom(player, home, Tuning.RETURN_MIN_PLAYER_DISTANCE)) {
			return false;
		}

		int done = 0;
		for (HauntEvent part : this.parts) {
			if (part.run(player, record, random)) {
				done++;
			}
		}
		if (done == 0) {
			return false; // nothing could be arranged (no door, no room); try again later
		}
		record.returnDone = true;
		return true;
	}
}
