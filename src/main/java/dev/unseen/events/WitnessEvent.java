package dev.unseen.events;

import dev.unseen.director.Director;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.List;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Every animal nearby stops and looks at the same empty spot behind you.
 * A pet may growl or hiss at it. There is nothing there.
 */
public class WitnessEvent implements HauntEvent {
	@Override
	public String id() {
		return "witness";
	}

	@Override
	public int minDread() {
		return Tuning.WITNESS_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.WITNESS_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		double r = Tuning.WITNESS_RADIUS;
		AABB box = player.getBoundingBox().inflate(r, r / 2, r);
		List<Animal> animals = level.getEntitiesOfClass(Animal.class, box, Animal::isAlive);
		if (animals.size() < Tuning.WITNESS_MIN_ANIMALS) {
			return false;
		}

		Vec3 look = player.getLookAngle();
		Vec3 back = new Vec3(-look.x, 0, -look.z);
		if (back.lengthSqr() < 1.0E-4) {
			return false;
		}
		Vec3 spot = player.getEyePosition().add(back.normalize().scale(Tuning.WITNESS_SPOT_DISTANCE));

		for (Animal animal : animals) {
			if (animal instanceof Wolf wolf && wolf.isTame()) {
				level.playSound(null, animal.getX(), animal.getY(), animal.getZ(), SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.CLASSIC).adultSounds().growlSound().value(),
						SoundSource.NEUTRAL, 0.8F, 1.0F);
			} else if (animal instanceof Cat cat && cat.isTame()) {
				cat.hiss();
			}
		}
		this.stare(level, animals, spot, Tuning.WITNESS_DURATION);
		return true;
	}

	/** Re-aims every animal each tick, since their own AI would look away on its own. */
	private void stare(ServerLevel level, List<Animal> animals, Vec3 spot, int ticksLeft) {
		for (Animal animal : animals) {
			if (animal.isAlive()) {
				animal.getNavigation().stop();
				animal.getLookControl().setLookAt(spot.x, spot.y, spot.z);
			}
		}
		if (ticksLeft > 0) {
			Director.get().later(1, () -> this.stare(level, animals, spot, ticksLeft - 1));
		}
	}
}
