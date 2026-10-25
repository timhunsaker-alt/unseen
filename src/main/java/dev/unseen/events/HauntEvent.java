package dev.unseen.events;

import dev.unseen.director.PlayerRecord;
import java.util.Random;
import net.minecraft.server.level.ServerPlayer;

/**
 * One thing the entity can do. To add a new scare: implement this,
 * then add it to the list in Director.
 */
public interface HauntEvent {
	/** Short name, used by the debug command and the save file. */
	String id();

	/** The lowest dread level at which this can happen. */
	int minDread();

	/** Ticks before this same event can happen again for a player. */
	long cooldown();

	/** How likely the director is to pick this over other events (higher = more often). */
	default int weight() {
		return 10;
	}

	/**
	 * Try to do the thing. Return true only if something actually happened.
	 * Returning false is fine (wrong conditions); the director will try again later.
	 */
	boolean run(ServerPlayer player, PlayerRecord record, Random random);
}
