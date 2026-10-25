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

	// --- Knock (three slow knocks on your own door while you're inside) ---

	public static final int KNOCK_MIN_DREAD = 2;
	public static final long KNOCK_COOLDOWN = GAME_DAY;
	public static final int KNOCK_COUNT = 3;
	/** Ticks between knocks. */
	public static final int KNOCK_GAP = 28;
	public static final float KNOCK_VOLUME = 1.6F;
	public static final float KNOCK_PITCH = 0.65F;
	public static final double KNOCK_MIN_DOOR_DISTANCE = 3.0;
	public static final double KNOCK_MAX_DOOR_DISTANCE = 16.0;

	// --- Torch trail (torches behind you go out, one by one, coming closer) ---

	public static final int TORCH_TRAIL_MIN_DREAD = 2;
	public static final long TORCH_TRAIL_COOLDOWN = GAME_DAY;
	public static final int TORCH_TRAIL_RADIUS = 20;
	public static final int TORCH_TRAIL_HEIGHT = 8;
	/** Torches closer than this are left alone. */
	public static final double TORCH_TRAIL_MIN_DISTANCE = 5.0;
	public static final int TORCH_TRAIL_MIN_TORCHES = 4;
	public static final int TORCH_TRAIL_MAX_TORCHES = 6;
	public static final int TORCH_TRAIL_STEP_MIN = 30;
	public static final int TORCH_TRAIL_STEP_MAX = 60;

	// --- Circling footprints (prints walk around your house while you're inside) ---

	public static final int CIRCLE_MIN_DREAD = 3;
	public static final long CIRCLE_COOLDOWN = 2L * GAME_DAY;
	/** How far from the bed the ring of prints runs; tried in order. */
	public static final int[] CIRCLE_RADII = {5, 6, 7, 8};
	/** How much of the ring is walked, in degrees. */
	public static final int CIRCLE_ARC_MIN = 180;
	public static final int CIRCLE_ARC_MAX = 240;
	public static final double CIRCLE_MIN_SNOWY_FRACTION = 0.6;
	/** The player must be this close to the bed (and under a roof) for it to happen. */
	public static final double CIRCLE_PLAYER_RADIUS = 12.0;

	// --- Journal (a book in your chest that knows things) ---

	public static final int JOURNAL_MIN_DREAD = 3;
	public static final long JOURNAL_COOLDOWN = 3L * GAME_DAY;
	public static final double JOURNAL_MIN_PLAYER_DISTANCE = 24.0;
}
