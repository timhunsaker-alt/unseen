package dev.unseen.events;

import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A sign stands near your house with something you said in chat written on it.
 * It was never you. You didn't place it.
 */
public class SignEvent implements HauntEvent {
	private static final int LINE_WIDTH = 15;

	@Override
	public String id() {
		return "sign";
	}

	@Override
	public int minDread() {
		return Tuning.SIGN_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.SIGN_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		if (record.chat.isEmpty()) {
			return false;
		}
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home) || !Senses.farFrom(player, home, Tuning.SIGN_MIN_PLAYER_DISTANCE)) {
			return false;
		}

		List<BlockPos> spots = new ArrayList<>();
		int r = Tuning.SIGN_MAX_RADIUS;
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				double ring = Math.sqrt(x * x + z * z);
				if (ring < Tuning.SIGN_MIN_RADIUS || ring > r) {
					continue;
				}
				for (int dy = 3; dy >= -3; dy--) {
					BlockPos pos = home.offset(x, dy, z);
					if (Senses.canStandAt(level, pos)) {
						if (!Senses.canSee(player, pos)) {
							spots.add(pos);
						}
						break;
					}
				}
			}
		}
		if (spots.isEmpty()) {
			return false;
		}

		BlockPos spot = spots.get(random.nextInt(spots.size()));
		BlockState sign = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, random.nextInt(16));
		if (!sign.canSurvive(level, spot)) {
			return false;
		}
		level.setBlockAndUpdate(spot, sign);

		BlockEntity entity = level.getBlockEntity(spot);
		if (!(entity instanceof SignBlockEntity signEntity)) {
			level.removeBlock(spot, false);
			return false;
		}
		String quote = record.chat.get(random.nextInt(record.chat.size()));
		signEntity.setText(text(quote), SignTextSlot.FRONT);
		return true;
	}

	/** Breaks a line of chat into the four short lines a sign has room for. */
	private static SignText text(String quote) {
		List<String> lines = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String word : quote.split("\\s+")) {
			while (word.length() > LINE_WIDTH) {
				if (current.length() > 0) {
					lines.add(current.toString());
					current.setLength(0);
				}
				lines.add(word.substring(0, LINE_WIDTH));
				word = word.substring(LINE_WIDTH);
			}
			if (current.length() > 0 && current.length() + 1 + word.length() > LINE_WIDTH) {
				lines.add(current.toString());
				current.setLength(0);
			}
			if (current.length() > 0) {
				current.append(' ');
			}
			current.append(word);
		}
		if (current.length() > 0) {
			lines.add(current.toString());
		}

		List<Component> messages = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			messages.add(Component.literal(i < lines.size() ? lines.get(i) : ""));
		}
		return new SignText(messages, messages, DyeColor.BLACK, false);
	}
}
