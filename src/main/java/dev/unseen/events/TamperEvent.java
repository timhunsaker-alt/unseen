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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Small things in your base are different when you come back: an item frame
 * turned a notch, a campfire you left lit is out (or one you left out is lit).
 */
public class TamperEvent implements HauntEvent {
	@Override
	public String id() {
		return "tamper";
	}

	@Override
	public int minDread() {
		return Tuning.TAMPER_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.TAMPER_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home) || !Senses.farFrom(player, home, Tuning.TAMPER_MIN_PLAYER_DISTANCE)) {
			return false;
		}

		int r = Tuning.HOME_RADIUS;
		AABB box = new AABB(home).inflate(r, Tuning.HOME_HEIGHT, r);
		List<ItemFrame> frames = new ArrayList<>(level.getEntitiesOfClass(ItemFrame.class, box,
				f -> !f.getItem().isEmpty() && !Senses.canSee(player, f.blockPosition())));
		List<BlockPos> campfires = Senses.scanHome(level, home, s -> (s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE)));
		campfires.removeIf(pos -> Senses.canSee(player, pos));

		// Pick one kind of change among whatever is actually there.
		List<Runnable> options = new ArrayList<>();
		if (!frames.isEmpty()) {
			ItemFrame frame = frames.get(random.nextInt(frames.size()));
			options.add(() -> frame.setRotation((frame.getRotation() + 1 + random.nextInt(3)) % 8));
		}
		if (!campfires.isEmpty()) {
			BlockPos pos = campfires.get(random.nextInt(campfires.size()));
			options.add(() -> flipCampfire(level, pos));
		}
		if (options.isEmpty()) {
			return false;
		}
		options.get(random.nextInt(options.size())).run();
		return true;
	}

	private static void flipCampfire(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		boolean lit = state.getValue(CampfireBlock.LIT);
		level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, !lit));
		if (lit) {
			level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.0F);
		}
	}
}
