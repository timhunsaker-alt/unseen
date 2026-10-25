package dev.unseen.events;

import dev.unseen.director.Director;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Your own footsteps, half a second late, right where you are. It stops when you stop.
 */
public class EchoEvent implements HauntEvent {
	private static final int WALK_STEP = 9;
	private static final int SPRINT_STEP = 6;

	private final Set<UUID> active = new HashSet<>();

	@Override
	public String id() {
		return "echo";
	}

	@Override
	public int minDread() {
		return Tuning.ECHO_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.ECHO_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		if (this.active.contains(player.getUUID()) || player.isInWater() || !player.onGround() || player.isSleeping()) {
			return false;
		}
		this.active.add(player.getUUID());
		this.step(player, player.position(), Tuning.ECHO_LENGTH, WALK_STEP);
		return true;
	}

	/** One tick of the echo; schedules itself for the next tick until they stop. */
	private void step(ServerPlayer player, Vec3 last, int ticksLeft, int stepTimer) {
		UUID who = player.getUUID();
		if (!player.isAlive() || player.isRemoved() || player.isSleeping() || ticksLeft <= 0) {
			this.active.remove(who);
			return;
		}
		Vec3 now = player.position();
		boolean walking = now.subtract(last).horizontalDistance() > 0.05 && player.onGround();
		if (!walking) {
			this.active.remove(who); // they stopped; so does it
			return;
		}
		if (--stepTimer <= 0) {
			stepTimer = player.isSprinting() ? SPRINT_STEP : WALK_STEP;
			Director.get().later(Tuning.ECHO_LAG, () -> this.sound(player));
		}
		int timer = stepTimer;
		Director.get().later(1, () -> this.step(player, now, ticksLeft - 1, timer));
	}

	private void sound(ServerPlayer player) {
		// Only while they're still moving; if they stopped in the meantime, nothing plays.
		if (!this.active.contains(player.getUUID()) || !player.isAlive()) {
			return;
		}
		ServerLevel level = Senses.level(player);
		BlockState floor = level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ()));
		if (floor.isAir()) {
			return;
		}
		SoundType type = floor.getSoundType();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), type.getStepSound(), SoundSource.PLAYERS,
				type.getVolume() * Tuning.ECHO_VOLUME, type.getPitch() * 0.8F);
	}
}
