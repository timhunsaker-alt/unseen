package dev.unseen.events;

import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * One flower, set carefully on your doorstep, outside the door.
 */
public class DoorstepEvent implements HauntEvent {
	private static final Block[] FLOWERS = {Blocks.POPPY, Blocks.AZURE_BLUET, Blocks.LILY_OF_THE_VALLEY, Blocks.WHITE_TULIP};

	@Override
	public String id() {
		return "doorstep";
	}

	@Override
	public int minDread() {
		return Tuning.DOORSTEP_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.DOORSTEP_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home) || !Senses.farFrom(player, home, Tuning.DOORSTEP_MIN_PLAYER_DISTANCE)) {
			return false;
		}

		List<BlockPos> steps = new ArrayList<>();
		for (BlockPos door : Senses.scanHome(level, home, s -> s.getBlock() instanceof DoorBlock
				&& s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER)) {
			Direction facing = level.getBlockState(door).getValue(DoorBlock.FACING);
			// Doors can face either way; the doorstep is whichever side is open to the sky.
			for (Direction side : new Direction[]{facing, facing.getOpposite()}) {
				BlockPos step = door.relative(side);
				if (level.getBlockState(step).isAir() && level.canSeeSky(step) && !Senses.canSee(player, step)) {
					steps.add(step);
				}
			}
		}

		BlockState flower = FLOWERS[random.nextInt(FLOWERS.length)].defaultBlockState();
		while (!steps.isEmpty()) {
			BlockPos step = steps.remove(random.nextInt(steps.size()));
			if (flower.canSurvive(level, step)) {
				level.setBlockAndUpdate(step, flower);
				return true;
			}
		}
		return false;
	}
}
