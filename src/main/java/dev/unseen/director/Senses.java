package dev.unseen.director;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What the entity needs to know about the world: can the player see this spot,
 * is anyone else around, where's the snow, where are the chests.
 */
public final class Senses {
	private static final double COS_VIEW = Math.cos(Math.toRadians(Tuning.VIEW_HALF_ANGLE_DEGREES));

	private Senses() {
	}

	/**
	 * True if the player could plausibly be looking at this spot right now.
	 * Deliberately paranoid: ignores walls, so anything in front of them counts.
	 */
	public static boolean canSee(ServerPlayer player, Vec3 spot) {
		Vec3 eye = player.getEyePosition();
		Vec3 toSpot = spot.subtract(eye);
		double distance = toSpot.length();
		if (distance > Tuning.VIEW_DISTANCE) {
			return false;
		}
		if (distance < 1.5) {
			return true;
		}
		Vec3 look = player.getLookAngle();
		return look.dot(toSpot.scale(1.0 / distance)) > COS_VIEW;
	}

	public static boolean canSee(ServerPlayer player, BlockPos pos) {
		return canSee(player, Vec3.atCenterOf(pos));
	}

	public static boolean isAlone(ServerPlayer player) {
		if (!Tuning.REQUIRE_ALONE) {
			return true;
		}
		for (ServerPlayer other : level(player).getServer().getPlayerList().getPlayers()) {
			if (other != player && other.level() == player.level()
					&& other.distanceToSqr(player) < Tuning.ALONE_RADIUS * Tuning.ALONE_RADIUS) {
				return false;
			}
		}
		return true;
	}

	/** True if the whole area around the house is loaded (so we don't force chunks to load). */
	public static boolean homeLoaded(ServerLevel level, BlockPos home) {
		int r = Tuning.HOME_RADIUS;
		return level.hasChunkAt(home.offset(-r, 0, -r)) && level.hasChunkAt(home.offset(r, 0, -r))
				&& level.hasChunkAt(home.offset(-r, 0, r)) && level.hasChunkAt(home.offset(r, 0, r));
	}

	public static ServerLevel level(ServerPlayer player) {
		return (ServerLevel) player.level();
	}

	/**
	 * Finds where a footprint could go in this column, searching around height y.
	 * Returns the position to put the print at, or null if this spot isn't snowy ground.
	 */
	public static BlockPos snowSurface(ServerLevel level, int x, int y, int z) {
		for (int dy = 6; dy >= -10; dy--) {
			BlockPos pos = new BlockPos(x, y + dy, z);
			if (!level.hasChunkAt(pos)) {
				return null;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			// A thin snow layer: press the print into it.
			if (state.is(Blocks.SNOW) && state.getValue(SnowLayerBlock.LAYERS) <= 2) {
				return pos;
			}
			// A full snow block with air above: press the print on top.
			if (state.is(Blocks.SNOW_BLOCK) && level.getBlockState(pos.above()).isAir()) {
				return pos.above();
			}
			// Hit something that isn't snow (grass, leaves, water...): no print here.
			return null;
		}
		return null;
	}

	/** Every block around the bed (the house) whose state passes {@code test}. */
	public static List<BlockPos> scanHome(ServerLevel level, BlockPos home, java.util.function.Predicate<BlockState> test) {
		return scan(level, home, Tuning.HOME_RADIUS, Tuning.HOME_HEIGHT, test);
	}

	/** Every block in a box around {@code center} whose state passes {@code test}. Never loads chunks. */
	public static List<BlockPos> scan(ServerLevel level, BlockPos center, int radius, int height,
			java.util.function.Predicate<BlockState> test) {
		List<BlockPos> found = new ArrayList<>();
		for (int x = -radius; x <= radius; x++) {
			for (int y = -height; y <= height; y++) {
				for (int z = -radius; z <= radius; z++) {
					BlockPos pos = center.offset(x, y, z);
					if (level.hasChunkAt(pos) && test.test(level.getBlockState(pos))) {
						found.add(pos);
					}
				}
			}
		}
		return found;
	}

	/** True if the player is at least {@code distance} blocks from {@code pos}. */
	public static boolean farFrom(ServerPlayer player, BlockPos pos, double distance) {
		return player.distanceToSqr(Vec3.atCenterOf(pos)) >= distance * distance;
	}

	/** Standing room: air to stand in, air above, and a solid top face below. */
	public static boolean canStandAt(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return false;
		}
		BlockPos below = pos.below();
		return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
				&& level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)
				&& level.getFluidState(below).isEmpty();
	}

	/** Chests and barrels around a spot, nearest first. */
	public static List<Container> containersNear(ServerLevel level, BlockPos center) {
		List<BlockPos> found = new ArrayList<>();
		int r = Tuning.HOME_RADIUS;
		int h = Tuning.HOME_HEIGHT;
		for (int x = -r; x <= r; x++) {
			for (int y = -h; y <= h; y++) {
				for (int z = -r; z <= r; z++) {
					BlockPos pos = center.offset(x, y, z);
					if (!level.hasChunkAt(pos)) {
						continue;
					}
					BlockEntity be = level.getBlockEntity(pos);
					if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
						found.add(pos);
					}
				}
			}
		}
		found.sort((a, b) -> Double.compare(a.distSqr(center), b.distSqr(center)));
		List<Container> containers = new ArrayList<>();
		for (BlockPos pos : found) {
			if (level.getBlockEntity(pos) instanceof Container container) {
				containers.add(container);
			}
		}
		return containers;
	}

	/**
	 * Tucks an item into a random empty slot of the first container with room.
	 * Random slot, not the first one: it should look like it was placed, not dropped.
	 */
	public static boolean tuckInto(List<Container> containers, ItemStack stack, java.util.Random random) {
		for (Container container : containers) {
			List<Integer> empty = new ArrayList<>();
			for (int i = 0; i < container.getContainerSize(); i++) {
				if (container.getItem(i).isEmpty()) {
					empty.add(i);
				}
			}
			if (!empty.isEmpty()) {
				container.setItem(empty.get(random.nextInt(empty.size())), stack);
				container.setChanged();
				return true;
			}
		}
		return false;
	}
}
