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
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One whole row of your ripe crops is harvested and replanted. The harvest is
 * put away in a chest, neatly. You didn't do this.
 */
public class CropsEvent implements HauntEvent {
	@Override
	public String id() {
		return "crops";
	}

	@Override
	public int minDread() {
		return Tuning.CROPS_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.CROPS_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.farFrom(player, home, Tuning.CROPS_MIN_PLAYER_DISTANCE)) {
			return false;
		}

		List<BlockPos> ripe = Senses.scan(level, home, Tuning.CROPS_RADIUS, 3,
				s -> s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s));
		ripe.removeIf(pos -> Senses.canSee(player, pos));
		if (ripe.isEmpty()) {
			return false;
		}

		BlockPos seed = ripe.get(random.nextInt(ripe.size()));
		List<BlockPos> alongX = row(level, seed, Direction.EAST);
		List<BlockPos> alongZ = row(level, seed, Direction.SOUTH);
		List<BlockPos> chosen = alongX.size() >= alongZ.size() ? alongX : alongZ;
		if (chosen.size() < Tuning.CROPS_MIN_ROW) {
			return false;
		}
		for (BlockPos pos : chosen) {
			if (Senses.canSee(player, pos)) {
				return false;
			}
		}

		List<Container> chests = Senses.containersNear(level, home);
		for (BlockPos pos : chosen) {
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof CropBlock crop)) {
				continue;
			}
			for (ItemStack drop : Block.getDrops(state, level, pos, null)) {
				Senses.tuckInto(chests, drop, random); // put away if there's room; otherwise it's just gone
			}
			level.setBlockAndUpdate(pos, crop.getStateForAge(0));
		}
		return true;
	}

	/** The unbroken line of ripe crops of the same kind through {@code seed}, along one axis. */
	private static List<BlockPos> row(ServerLevel level, BlockPos seed, Direction axis) {
		Block kind = level.getBlockState(seed).getBlock();
		List<BlockPos> row = new ArrayList<>();
		row.add(seed);
		for (Direction dir : new Direction[]{axis, axis.getOpposite()}) {
			BlockPos pos = seed.relative(dir);
			while (level.hasChunkAt(pos) && level.getBlockState(pos).is(kind)
					&& level.getBlockState(pos).getBlock() instanceof CropBlock crop && crop.isMaxAge(level.getBlockState(pos))) {
				row.add(pos);
				pos = pos.relative(dir);
			}
		}
		return row;
	}
}
