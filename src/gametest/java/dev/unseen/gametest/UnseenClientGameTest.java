package dev.unseen.gametest;

import dev.unseen.UnseenBlocks;
import dev.unseen.UnseenItems;
import dev.unseen.director.Tuning;
import dev.unseen.item.SuspiciousSteakItem;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
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
			suspiciousSteak(context, server);
		}
	}

	/**
	 * An empty furnace in front of the player fills itself, lights, cooks, and gives a Suspicious Steak.
	 * Then each thing that can happen when it is eaten is checked.
	 */
	private void suspiciousSteak(ClientGameTestContext context, TestServerContext server) {
		int free = server.computeOnServer(srv -> srv.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 40, 40));
		BlockPos furnacePos = new BlockPos(42, free, 34);
		server.runCommand("fill 36 " + free + " 28 48 " + free + " 46 minecraft:air"); // a clear patch of ground
		server.runCommand("setblock 42 " + free + " 34 minecraft:furnace[facing=south]");
		server.runCommand("tp @p 40 " + free + " 40 180 0"); // looking north, at the furnace six blocks away
		context.waitTicks(20);

		boolean happened = server.computeOnServer(srv -> {
			ServerPlayer player = srv.getPlayerList().getPlayers().get(0);
			PlayerRecord record = Director.get().record(player);
			for (HauntEvent event : Director.get().events()) {
				if (event.id().equals("steak")) {
					return Director.get().runEvent(player, record, event, Director.get().now());
				}
			}
			throw new AssertionError("no steak event registered");
		});
		check(happened, "the steak event did not run (an empty furnace is in plain view)");

		context.waitTicks(40);
		boolean lit = server.computeOnServer(srv -> srv.overworld().getBlockState(furnacePos).getValue(AbstractFurnaceBlock.LIT));
		check(lit, "the furnace should be lit and cooking");
		context.takeScreenshot("steak-cooking");

		context.waitTicks(220); // a furnace takes 200 ticks to cook; 40 have passed
		ItemStack output = server.computeOnServer(srv -> ((AbstractFurnaceBlockEntity) srv.overworld().getBlockEntity(furnacePos)).getItem(2).copy());
		check(output.is(UnseenItems.SUSPICIOUS_STEAK) && output.getCount() == 1,
				"the furnace should have made one Suspicious Steak but its output is " + output);

		// Everything that can happen when it's eaten.
		String problems = server.computeOnServer(srv -> {
			ServerPlayer player = srv.getPlayerList().getPlayers().get(0);
			StringBuilder bad = new StringBuilder();

			player.setHealth(4.0F);
			player.getFoodData().setFoodLevel(20);
			SuspiciousSteakItem.apply(player, SuspiciousSteakItem.Outcome.FULL_HEARTS);
			if (player.getHealth() < player.getMaxHealth()) {
				bad.append("full hearts left health at ").append(player.getHealth()).append("; ");
			}
			int hunger = player.getFoodData().getFoodLevel();
			if (hunger < Tuning.STEAK_HUNGER_MIN || hunger > Tuning.STEAK_HUNGER_MAX) {
				bad.append("full hearts left hunger at ").append(hunger).append("; ");
			}

			SuspiciousSteakItem.apply(player, SuspiciousSteakItem.Outcome.NAUSEA);
			if (!player.hasEffect(MobEffects.NAUSEA)) {
				bad.append("nausea was not applied; ");
			}

			player.removeAllEffects();
			player.setHealth(10.0F);
			player.getFoodData().setFoodLevel(10);
			SuspiciousSteakItem.apply(player, SuspiciousSteakItem.Outcome.NORMAL);
			if (player.getHealth() != 10.0F || player.getFoodData().getFoodLevel() != 10 || player.hasEffect(MobEffects.NAUSEA)) {
				bad.append("the normal outcome changed something; ");
			}

			// Eating it for real, from a held stack: must not fail, whichever way it rolls.
			ItemStack steak = new ItemStack(UnseenItems.SUSPICIOUS_STEAK);
			player.getFoodData().setFoodLevel(10);
			steak.finishUsingItem(srv.overworld(), player);
			return bad.toString();
		});
		check(problems.isEmpty(), "eating the steak: " + problems);

		// The log should have remembered it.
		int logged = server.computeOnServer(srv -> {
			PlayerRecord record = Director.get().record(srv.getPlayerList().getPlayers().get(0));
			return (int) record.history.stream().filter(e -> e.id.equals("steak")).count();
		});
		check(logged >= 4, "expected the steak scare and each time it was eaten in the log, but found " + logged + " entries");
		System.out.println("[unseen-test] steak: lit, cooked into a Suspicious Steak, all three outcomes behave, " + logged + " log entries");
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
