package dev.unseen.events;

import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Footsteps a few blocks behind you, in step with yours.
 * They stop the instant you stop. Sometimes there's one more.
 */
public class FootstepsEvent implements HauntEvent {
	private static final double BEHIND = 3.5;
	private static final int MAX_LENGTH = 12 * Tuning.SECOND;
	private static final int WALK_STEP = 9;
	private static final int SPRINT_STEP = 6;
	private static final double ONE_MORE_STEP_CHANCE = 0.35;
	private static final int ONE_MORE_STEP_DELAY = 30;

	private static class Session {
		int ticksLeft = MAX_LENGTH;
		int stepTimer = WALK_STEP;
		Vec3 lastPosition;
		/** Counts down to the "one more step" after they stop. -1 = not planned. */
		int oneMoreStep = -1;
		Vec3 lastStepSpot;
	}

	private final Map<UUID, Session> sessions = new HashMap<>();
	private final Random random = new Random();

	@Override
	public String id() {
		return "footsteps";
	}

	@Override
	public int minDread() {
		return 2;
	}

	@Override
	public long cooldown() {
		return Tuning.GAME_DAY / 2;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		if (this.sessions.containsKey(player.getUUID()) || player.isInWater() || !player.onGround()) {
			return false;
		}
		Session session = new Session();
		session.lastPosition = player.position();
		this.sessions.put(player.getUUID(), session);
		return true;
	}

	/** Called every server tick by the director. */
	public void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Session>> it = this.sessions.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Session> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.isSleeping()) {
				it.remove();
				continue;
			}
			if (!this.tickSession(player, entry.getValue())) {
				it.remove();
			}
		}
	}

	/** Returns false when the session is over. */
	private boolean tickSession(ServerPlayer player, Session session) {
		Vec3 now = player.position();
		double moved = now.subtract(session.lastPosition).horizontalDistance();
		session.lastPosition = now;

		// After they've stopped: maybe one last step, then silence.
		if (session.oneMoreStep >= 0) {
			if (moved > 0.05) {
				return false; // they moved again; the moment is gone
			}
			if (--session.oneMoreStep == 0) {
				this.stepAt(player, session.lastStepSpot);
				return false;
			}
			return true;
		}

		if (--session.ticksLeft <= 0) {
			return false;
		}

		boolean walking = moved > 0.05 && player.onGround();
		if (!walking) {
			// They stopped. So does it.
			if (session.lastStepSpot != null && this.random.nextDouble() < ONE_MORE_STEP_CHANCE) {
				session.oneMoreStep = ONE_MORE_STEP_DELAY;
				return true;
			}
			return false;
		}

		if (--session.stepTimer <= 0) {
			session.stepTimer = player.isSprinting() ? SPRINT_STEP : WALK_STEP;
			Vec3 look = player.getLookAngle();
			Vec3 back = new Vec3(-look.x, 0, -look.z);
			if (back.lengthSqr() > 1.0E-4) {
				Vec3 spot = now.add(back.normalize().scale(BEHIND));
				session.lastStepSpot = spot;
				this.stepAt(player, spot);
			}
		}
		return true;
	}

	private void stepAt(ServerPlayer player, Vec3 spot) {
		if (spot == null) {
			return;
		}
		ServerLevel level = Senses.level(player);
		BlockState floor = level.getBlockState(BlockPos.containing(spot.x, spot.y - 0.2, spot.z));
		if (floor.isAir()) {
			return;
		}
		SoundType sound = floor.getSoundType();
		float pitch = sound.getPitch() * (0.85F + this.random.nextFloat() * 0.1F);
		level.playSound(null, spot.x, spot.y, spot.z, sound.getStepSound(), SoundSource.PLAYERS,
				sound.getVolume() * 0.3F, pitch);
	}
}
