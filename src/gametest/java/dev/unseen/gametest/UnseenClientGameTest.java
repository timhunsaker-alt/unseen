package dev.unseen.gametest;

import dev.unseen.UnseenBlocks;
import dev.unseen.director.Director;
import dev.unseen.director.PlayerRecord;
import dev.unseen.events.HauntEvent;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Starts a real world and checks scares against scenes built to be awkward.
 * Run with: ./gradlew runClientGameTest
 */
public class UnseenClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			context.waitTicks(40);
			footprintsStayOnTheGround(context, server);
		}
	}

	/**
	 * A snowy field with a leafy canopy over part of it, and snow lying on top of the leaves, the way it does in a
	 * snowy forest. Footprints must go on the ground, never up in the trees.
	 */
	private void footprintsStayOnTheGround(ClientGameTestContext context, TestServerContext server) {
		// Where the ground is in this world: the first free block above it.
		int free = server.computeOnServer(srv -> srv.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0));

		server.runCommand("fill -16 " + free + " -44 16 " + free + " 8 minecraft:snow[layers=1]"); // snow on the ground
		server.runCommand("fill -4 " + (free + 5) + " -36 4 " + (free + 5) + " -8 minecraft:oak_leaves[persistent=true]"); // canopy
		server.runCommand("fill -4 " + (free + 6) + " -36 4 " + (free + 6) + " -8 minecraft:snow[layers=1]"); // snow on the canopy
		server.runCommand("tp @p 0 " + free + " 0 0 0"); // yaw 0: facing south, so "behind" is north, under the canopy
		context.waitTicks(20);

		boolean happened = server.computeOnServer(srv -> {
			ServerPlayer player = srv.getPlayerList().getPlayers().get(0);
			PlayerRecord record = Director.get().record(player);
			for (HauntEvent event : Director.get().events()) {
				if (event.id().equals("footprints")) {
					return Director.get().runEvent(player, record, event, Director.get().now());
				}
			}
			throw new AssertionError("no footprints event registered");
		});
		check(happened, "the footprints event did not run at all (it should, in an open snowy field)");

		// Turn to face the prints so a screenshot shows them, then look at every print that was made.
		server.runCommand("tp @p 0 " + free + " 0 180 8");
		context.waitTicks(20);

		List<BlockPos> prints = server.computeOnServer(srv -> {
			ServerLevel level = srv.overworld();
			List<BlockPos> found = new ArrayList<>();
			for (int x = -8; x <= 8; x++) {
				for (int y = free - 3; y <= free + 10; y++) {
					for (int z = -48; z <= 8; z++) {
						if (level.getBlockState(new BlockPos(x, y, z)).is(UnseenBlocks.FOOTPRINT)) {
							found.add(new BlockPos(x, y, z));
						}
					}
				}
			}
			return found;
		});
		check(prints.size() >= 6, "expected a trail of prints but found " + prints.size());

		List<String> wrong = server.computeOnServer(srv -> {
			ServerLevel level = srv.overworld();
			List<String> problems = new ArrayList<>();
			for (BlockPos pos : prints) {
				boolean onGround = level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK); // the scene's ground
				if (pos.getY() != free || !onGround) {
					problems.add(pos.toShortString() + " on " + level.getBlockState(pos.below()).getBlock());
				}
			}
			return problems;
		});
		context.takeScreenshot("footprints-under-canopy");
		check(wrong.isEmpty(), "footprints not on the ground (ground is y=" + free + "): " + wrong);
		System.out.println("[unseen-test] footprints: " + prints.size() + " prints, all on the ground at y=" + free);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
