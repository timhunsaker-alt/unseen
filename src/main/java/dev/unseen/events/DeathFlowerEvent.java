package dev.unseen.events;

import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where you died, there's one flower. You didn't plant it. It only appears once you've walked away.
 */
public class DeathFlowerEvent implements HauntEvent {
	@Override
	public String id() {
		return "deathflower";
	}

	@Override
	public int minDread() {
		return Tuning.DEATH_FLOWER_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.DEATH_FLOWER_COOLDOWN;
	}

	@Override
	public int weight() {
		return Tuning.DEATH_FLOWER_WEIGHT;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		if (record.deathPos == null || !level.dimension().identifier().toString().equals(record.deathDimension)) {
			return false;
		}
		BlockPos death = BlockPos.of(record.deathPos);
		if (!level.hasChunkAt(death) || !Senses.farFrom(player, death, Tuning.DEATH_FLOWER_MIN_DISTANCE)) {
			return false;
		}

		BlockState flower = Blocks.POPPY.defaultBlockState();
		for (int dy = 2; dy >= -4; dy--) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockPos spot = death.offset(dx, dy, dz);
					if (!level.hasChunkAt(spot) || !level.getBlockState(spot).isAir()
							|| !Senses.isGround(level.getBlockState(spot.below())) || !flower.canSurvive(level, spot)
							|| Senses.canSee(player, spot)) {
						continue;
					}
					level.setBlockAndUpdate(spot, flower);
					record.deathPos = null;
					record.deathDimension = null;
					return true;
				}
			}
		}
		return false;
	}
}
