package dev.unseen.events;

import dev.unseen.director.Director;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * At night, lit candles appear in your house and drift around the rooms, hopping a block or two at a time,
 * even while you watch. The moment you come close, they are gone, and your heartbeat starts.
 *
 * This is the one scare that deliberately works in plain view, and only after the dragon.
 * (Their light moving around the walls is what makes the shadows move.)
 */
public class WanderingCandlesEvent implements HauntEvent {
	private static final BlockState LIT_CANDLE = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true);

	@Override
	public String id() {
		return "wander";
	}

	@Override
	public int minDread() {
		return Tuning.WANDER_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.WANDER_COOLDOWN;
	}

	@Override
	public int weight() {
		return Tuning.WANDER_WEIGHT;
	}

	/** One candle's state while it wanders. */
	private static final class Candle {
		BlockPos pos;
		long hopAt;
		final long endsAt;

		Candle(BlockPos pos, long hopAt, long endsAt) {
			this.pos = pos;
			this.hopAt = hopAt;
			this.endsAt = endsAt;
		}
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home) || !level.isDarkOutside()
				|| !record.wanderCandles.isEmpty()
				|| player.distanceToSqr(Vec3.atCenterOf(home)) > Tuning.WANDER_PLAYER_RADIUS * Tuning.WANDER_PLAYER_RADIUS) {
			return false;
		}

		// Indoor spots at a distance from the player, so the first one is seen across the room.
		double minSq = Tuning.WANDER_MIN_START_DISTANCE * Tuning.WANDER_MIN_START_DISTANCE;
		List<BlockPos> spots = new ArrayList<>();
		int r = Tuning.HOME_RADIUS;
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				for (int dy = Tuning.HOME_HEIGHT; dy >= -Tuning.HOME_HEIGHT; dy--) {
					BlockPos pos = home.offset(x, dy, z);
					if (indoorSpot(level, pos)) {
						if (pos.distSqr(player.blockPosition()) >= minSq) {
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

		int count = Tuning.WANDER_MIN_CANDLES + random.nextInt(Tuning.WANDER_MAX_CANDLES - Tuning.WANDER_MIN_CANDLES + 1);
		long now = Director.get().now();
		UUID who = player.getUUID();
		int placed = 0;
		for (int i = 0; i < count && !spots.isEmpty(); i++) {
			BlockPos spot = spots.remove(random.nextInt(spots.size()));
			if (!LIT_CANDLE.canSurvive(level, spot)) {
				continue;
			}
			level.setBlockAndUpdate(spot, LIT_CANDLE);
			record.wanderCandles.put(key(level, spot), now);
			Candle candle = new Candle(spot, now + hop(random), now + Tuning.WANDER_LIFETIME);
			Director.get().later(Tuning.WANDER_CHECK_TICKS, () -> step(player, who, record, candle, random));
			placed++;
		}
		return placed > 0;
	}

	/** Runs every few ticks for each candle: vanish if you're near, hop now and then, fade eventually. */
	private static void step(ServerPlayer player, UUID who, PlayerRecord record, Candle candle, Random random) {
		ServerLevel level = Senses.level(player);
		long now = Director.get().now();
		boolean gone = !player.isAlive() || player.isRemoved() || !player.getUUID().equals(who)
				|| !level.hasChunkAt(candle.pos) || !level.getBlockState(candle.pos).is(Blocks.CANDLE);
		if (gone) {
			forget(level, record, candle.pos, false);
			return;
		}

		double distance = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(candle.pos)));
		if (distance < Tuning.WANDER_VANISH_DISTANCE) {
			forget(level, record, candle.pos, true);
			// Just before you reach it: it's gone, and your heartbeat starts.
			Heartbeat.play(player, Tuning.WANDER_VANISH_BEATS, Tuning.HEARTBEAT_BPM_START + 10, Tuning.HEARTBEAT_BPM_END,
					Tuning.HEARTBEAT_VOLUME_START, Tuning.HEARTBEAT_VOLUME_END);
			return;
		}
		if (now >= candle.endsAt) {
			forget(level, record, candle.pos, true);
			return;
		}
		if (now >= candle.hopAt) {
			BlockPos next = nextSpot(level, candle.pos, random);
			if (next != null) {
				level.removeBlock(candle.pos, false);
				record.wanderCandles.remove(key(level, candle.pos));
				level.setBlockAndUpdate(next, LIT_CANDLE);
				record.wanderCandles.put(key(level, next), now);
				candle.pos = next;
			}
			candle.hopAt = now + hop(random);
		}
		Director.get().later(Tuning.WANDER_CHECK_TICKS, () -> step(player, who, record, candle, random));
	}

	private static BlockPos nextSpot(ServerLevel level, BlockPos from, Random random) {
		int range = Tuning.WANDER_HOP_RANGE;
		List<BlockPos> options = new ArrayList<>();
		for (int dx = -range; dx <= range; dx++) {
			for (int dz = -range; dz <= range; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				for (int dy = 1; dy >= -1; dy--) {
					BlockPos pos = from.offset(dx, dy, dz);
					if (indoorSpot(level, pos) && LIT_CANDLE.canSurvive(level, pos)) {
						options.add(pos);
						break;
					}
				}
			}
		}
		return options.isEmpty() ? null : options.get(random.nextInt(options.size()));
	}

	/** Floor space with a roof over it. */
	private static boolean indoorSpot(ServerLevel level, BlockPos pos) {
		return Senses.canStandAt(level, pos) && !level.canSeeSky(pos);
	}

	private static int hop(Random random) {
		return Tuning.WANDER_HOP_MIN + random.nextInt(Tuning.WANDER_HOP_MAX - Tuning.WANDER_HOP_MIN + 1);
	}

	private static void forget(ServerLevel level, PlayerRecord record, BlockPos pos, boolean removeBlock) {
		if (removeBlock && level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.CANDLE)) {
			level.removeBlock(pos, false);
		}
		record.wanderCandles.remove(key(level, pos));
	}

	private static String key(ServerLevel level, BlockPos pos) {
		return level.dimension().identifier() + "|" + pos.asLong();
	}

	/** Cleans up after a restart or a player leaving, which would otherwise strand a candle. */
	@Override
	public void tickPlayer(ServerPlayer player, PlayerRecord record, long now) {
		if (record.wanderCandles.isEmpty()) {
			return;
		}
		ServerLevel level = Senses.level(player);
		String dimension = level.dimension().identifier().toString();
		Iterator<Map.Entry<String, Long>> it = record.wanderCandles.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Long> entry = it.next();
			String[] parts = entry.getKey().split("\\|", 2);
			if (parts.length != 2 || !parts[0].equals(dimension) || now - entry.getValue() < Tuning.WANDER_FORGET_AFTER) {
				continue;
			}
			BlockPos pos = BlockPos.of(Long.parseLong(parts[1]));
			if (!level.hasChunkAt(pos)) {
				continue;
			}
			if (level.getBlockState(pos).is(Blocks.CANDLE)) {
				level.removeBlock(pos, false);
			}
			it.remove();
		}
	}
}
