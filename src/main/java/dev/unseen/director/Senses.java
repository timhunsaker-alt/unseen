package dev.unseen.director;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
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
	 * Finds where a footprint could go in this column: snow lying on real ground. {@code y} is the height the
	 * trail is already at; a spot much higher or lower than that (a cliff, a hilltop) is skipped.
	 * Returns the position to put the print at, or null if this spot isn't snowy ground.
	 *
	 * The ground is found by ignoring leaves. Tree canopies hold snow of their own, so looking for "the first
	 * snow from above" puts footprints in the treetops.
	 */
	public static BlockPos snowSurface(ServerLevel level, int x, int y, int z) {
		if (!level.hasChunkAt(new BlockPos(x, y, z))) {
			return null; // never load or generate a chunk just to leave a print
		}
		// The first free block above the ground, ignoring leaves.
		BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		BlockPos found = snowAt(level, top);
		return found != null && Math.abs(found.getY() - y) <= 10 ? found : null;
	}

	private static BlockPos snowAt(ServerLevel level, BlockPos top) {
		BlockState here = level.getBlockState(top);
		BlockState below = level.getBlockState(top.below());

		// One thin layer of snow (it has no collision, so the ground is just under it).
		if (here.is(Blocks.SNOW) && here.getValue(SnowLayerBlock.LAYERS) <= 2 && isGround(below)) {
			return top;
		}
		// Two layers do have collision, so the snow is the top block and the free space is above it.
		if (below.is(Blocks.SNOW) && below.getValue(SnowLayerBlock.LAYERS) <= 2
				&& isGround(level.getBlockState(top.below(2)))) {
			return top.below();
		}
		// A full snow block with air above it.
		if (here.isAir() && below.is(Blocks.SNOW_BLOCK)) {
			return top;
		}
		return null; // grass, bare dirt, water, a treetop, a roof...: no print here
	}

	/**
	 * Earth, sand, stone and snow count as ground. Leaves, logs, planks and the like do not.
	 * Note the dirt tag does not include grass blocks (or podzol, mycelium, mud, moss), which is most of what snow lies on.
	 */
	public static boolean isGround(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.GRASS_BLOCKS) || state.is(Blocks.PODZOL)
				|| state.is(Blocks.MYCELIUM) || state.is(Blocks.MUD) || state.is(Blocks.MOSS_BLOCK)
				|| state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY);
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
