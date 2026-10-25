package dev.unseen.events;

import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A single candle, lit, on the floor beside your bed. It was dark when you left.
 */
public class CandleEvent implements HauntEvent {
	@Override
	public String id() {
		return "candle";
	}

	@Override
	public int minDread() {
		return Tuning.CANDLE_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.CANDLE_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home) || !Senses.farFrom(player, home, Tuning.CANDLE_MIN_PLAYER_DISTANCE)) {
			return false;
		}

		List<BlockPos> spots = new ArrayList<>();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = 1; dy >= -2; dy--) {
					BlockPos pos = home.offset(dx, dy, dz);
					if (Senses.canStandAt(level, pos) && !Senses.canSee(player, pos)) {
						spots.add(pos);
						break;
					}
				}
			}
		}
		if (spots.isEmpty()) {
			return false;
		}

		BlockPos spot = spots.get(random.nextInt(spots.size()));
		BlockState candle = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true);
		if (!candle.canSurvive(level, spot)) {
			return false;
		}
		level.setBlockAndUpdate(spot, candle);
		return true;
	}
}
