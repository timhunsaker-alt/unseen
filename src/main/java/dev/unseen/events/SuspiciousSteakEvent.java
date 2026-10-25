package dev.unseen.events;

import dev.unseen.UnseenItems;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An empty furnace (or smoker) you're looking at, a little way off, fills itself: a raw steak, a bit of fuel.
 * It lights and starts to cook while you watch. When it's done, it's a Suspicious Steak.
 *
 * Like the wandering candles, this one deliberately happens in plain view.
 */
public class SuspiciousSteakEvent implements HauntEvent {
	@Override
	public String id() {
		return "steak";
	}

	@Override
	public int minDread() {
		return Tuning.STEAK_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.STEAK_COOLDOWN;
	}

	@Override
	public int weight() {
		return Tuning.STEAK_WEIGHT;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		int radius = (int) Math.ceil(Tuning.STEAK_MAX_DISTANCE);

		List<AbstractFurnaceBlockEntity> furnaces = new ArrayList<>();
		for (BlockPos pos : Senses.scan(level, player.blockPosition(), radius, 4,
				state -> state.is(Blocks.FURNACE) || state.is(Blocks.SMOKER))) {
			double distance = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(pos)));
			if (distance >= Tuning.STEAK_MIN_DISTANCE && distance <= Tuning.STEAK_MAX_DISTANCE
					&& level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace
					&& furnace.isEmpty() && inView(player, level, pos)) {
				furnaces.add(furnace);
			}
		}
		if (furnaces.isEmpty()) {
			return false;
		}

		AbstractFurnaceBlockEntity furnace = furnaces.get(random.nextInt(furnaces.size()));
		furnace.setItem(0, new ItemStack(UnseenItems.RAW_SUSPICIOUS_STEAK)); // what is cooked
		furnace.setItem(1, new ItemStack(Items.OAK_PLANKS)); // just enough fuel to finish it
		furnace.setChanged();
		return true;
	}

	/** In front of the player and nothing in the way: they will see it. */
	private static boolean inView(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!Senses.canSee(player, pos)) {
			return false;
		}
		BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(), Vec3.atCenterOf(pos),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getBlockPos().equals(pos);
	}
}
