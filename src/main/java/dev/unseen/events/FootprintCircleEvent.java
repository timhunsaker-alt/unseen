package dev.unseen.events;

import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * While you're inside, something walks a slow circle around your house in the
 * snow. The last print turns to face the building. It is on the side you
 * weren't looking at.
 */
public class FootprintCircleEvent implements HauntEvent {
	/** Blocks between prints along the ring. */
	private static final double STRIDE = 1.1;

	private record Print(BlockPos pos, Direction facing) {
	}

	@Override
	public String id() {
		return "circle";
	}

	@Override
	public int minDread() {
		return Tuning.CIRCLE_MIN_DREAD;
	}

	@Override
	public long cooldown() {
		return Tuning.CIRCLE_COOLDOWN;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home)) {
			return false;
		}
		// Indoors and near the bed: this is for someone sitting inside, listening.
		if (level.canSeeSky(player.blockPosition())
				|| player.distanceToSqr(Vec3.atCenterOf(home)) > Tuning.CIRCLE_PLAYER_RADIUS * Tuning.CIRCLE_PLAYER_RADIUS) {
			return false;
		}

		// Centre the walk on the side of the house behind where they're looking.
		Vec3 look = player.getLookAngle();
		double behind = Math.atan2(-look.z, -look.x);
		double arc = Math.toRadians(Tuning.CIRCLE_ARC_MIN + random.nextInt(Tuning.CIRCLE_ARC_MAX - Tuning.CIRCLE_ARC_MIN + 1));
		boolean counterClockwise = random.nextBoolean();

		for (int radius : Tuning.CIRCLE_RADII) {
			List<Print> prints = this.ring(player, level, home, radius, behind, arc, counterClockwise);
			if (prints != null) {
				boolean left = true;
				for (Print print : prints) {
					FootprintsEvent.press(level, print.pos(), print.facing(), left);
					left = !left;
				}
				return true;
			}
		}
		return false;
	}

	/** The prints for one ring size, or null if it can't be done (not enough snow, or any of it in view). */
	private List<Print> ring(ServerPlayer player, ServerLevel level, BlockPos home, int radius,
			double centre, double arc, boolean counterClockwise) {
		int count = Math.max(4, (int) (arc * radius / STRIDE));
		double direction = counterClockwise ? 1.0 : -1.0;
		double start = centre - direction * arc / 2.0;

		List<Print> prints = new ArrayList<>();
		int y = home.getY();
		int tried = 0;
		for (int i = 0; i <= count; i++) {
			double angle = start + direction * arc * i / count;
			int x = home.getX() + (int) Math.round(Math.cos(angle) * radius);
			int z = home.getZ() + (int) Math.round(Math.sin(angle) * radius);
			tried++;
			BlockPos spot = Senses.snowSurface(level, x, y, z);
			if (spot == null) {
				continue;
			}
			if (Senses.canSee(player, spot)) {
				return null; // never in view; try a bigger ring or give up
			}
			y = spot.getY();
			// Heading is along the ring: the tangent at this angle.
			Direction facing = Direction.getApproximateNearest(-Math.sin(angle) * direction, 0, Math.cos(angle) * direction);
			if (prints.isEmpty() || !prints.get(prints.size() - 1).pos().equals(spot)) {
				prints.add(new Print(spot, facing));
			}
		}
		if (prints.size() < tried * Tuning.CIRCLE_MIN_SNOWY_FRACTION || prints.size() < 6) {
			return null;
		}

		// The last print turns toward the house.
		Print last = prints.remove(prints.size() - 1);
		Direction toHome = Direction.getApproximateNearest(home.getX() - last.pos().getX(), 0, home.getZ() - last.pos().getZ());
		prints.add(new Print(last.pos(), toHome));
		return prints;
	}
}
