package dev.unseen.director;

/**
 * Every knob that controls how scary (and how often) the mod is.
 * Change numbers here, rebuild, and play-test. 20 ticks = 1 second.
 */
public final class Tuning {
	private Tuning() {
	}

	public static final int SECOND = 20;
	public static final int MINUTE = 60 * SECOND;
	/** One Minecraft day/night cycle is 20 real minutes. */
	public static final int GAME_DAY = 20 * MINUTE;

	// --- Dread: how far the haunting has escalated (0 = nothing yet, 5 = max) ---

	/** Dread goes up by one for every this-many ticks a player has spent in the world. */
	public static final long TICKS_PER_DREAD = GAME_DAY;
	public static final int MAX_DREAD = 5;

	// --- How often the director considers doing something ---

	/** How often (per player) the director rolls the dice. */
	public static final int CHECK_INTERVAL = 30 * SECOND;

	/** Chance that a roll turns into an event, by dread level 0..5. */
	public static final double[] EVENT_CHANCE = {0.0, 0.15, 0.22, 0.30, 0.38, 0.45};

	/** Minimum quiet time between any two events, by dread level 0..5. */
	public static final int[] QUIET_TIME = {
			Integer.MAX_VALUE, 12 * MINUTE, 9 * MINUTE, 7 * MINUTE, 5 * MINUTE, 4 * MINUTE};

	// --- Rules that keep it unseen ---

	/** Anything within this angle of where the player is looking counts as "seen". */
	public static final double VIEW_HALF_ANGLE_DEGREES = 75.0;
	/** Beyond this distance the player can't really make anything out. */
	public static final double VIEW_DISTANCE = 96.0;
	/** Only haunt players who have nobody else within this many blocks. */
	public static final double ALONE_RADIUS = 32.0;
	public static final boolean REQUIRE_ALONE = true;

	// --- The house ---

	/** How far around the bed counts as "home" when looking for torches, doors and chests. */
	public static final int HOME_RADIUS = 10;
	public static final int HOME_HEIGHT = 4;

	// --- Sleeping photo ---

	public static final int PHOTO_MIN_DREAD = 2;
	public static final double PHOTO_CHANCE = 0.5;
	public static final long PHOTO_COOLDOWN = 3L * GAME_DAY;
	/** Wait this long after lying down before the shutter clicks. */
	public static final int PHOTO_DELAY = 12;
	/** Give up on a photo the client never sent back. */
	public static final int PHOTO_TIMEOUT = 30 * SECOND;
}
