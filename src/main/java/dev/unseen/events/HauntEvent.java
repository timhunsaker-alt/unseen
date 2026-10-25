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
	 * A plain sentence for the log of what it has done, in second person ("Someone knocked on your door.").
	 * Return null to use the built-in text for this event's id, or just the id if there isn't one.
	 */
	default String description() {
		return null;
	}

	/** Same as weight(), but an event can be picked more often at a higher dread level. */
	default int weight(int dread) {
		return weight();
	}

	/**
	 * Try to do the thing. Return true only if something actually happened.
	 * Returning false is fine (wrong conditions); the director will try again later.
	 */
	boolean run(ServerPlayer player, PlayerRecord record, Random random);

	/**
	 * Called about once a second for every watched player, whether or not this event is running.
	 * For events that need to clean up after themselves later.
	 */
	default void tickPlayer(ServerPlayer player, PlayerRecord record, long now) {
	}
}
