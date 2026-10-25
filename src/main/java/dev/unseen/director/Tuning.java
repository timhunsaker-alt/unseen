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
	/** Time alone only gets you to this level. Level 6 is earned by defeating the dragon. */
	public static final int TIME_DREAD_CAP = 5;
	public static final int MAX_DREAD = 6;

	// --- How often the director considers doing something ---

	/** How often (per player) the director rolls the dice. */
	public static final int CHECK_INTERVAL = 30 * SECOND;

	/** Chance that a roll turns into an event, by dread level 0..5. */
	public static final double[] EVENT_CHANCE = {0.0, 0.15, 0.22, 0.30, 0.38, 0.45, 0.65};

	/** Minimum quiet time between any two events, by dread level 0..5. */
	public static final int[] QUIET_TIME = {
			Integer.MAX_VALUE, 12 * MINUTE, 9 * MINUTE, 7 * MINUTE, 5 * MINUTE, 4 * MINUTE, 2 * MINUTE};

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

	// --- Animals that all look at the same empty spot ---

	public static final int WITNESS_MIN_DREAD = 2;
	public static final long WITNESS_COOLDOWN = GAME_DAY / 2;
	public static final double WITNESS_RADIUS = 24.0;
	public static final int WITNESS_MIN_ANIMALS = 2;
	/** How far behind the player the animals stare. */
	public static final double WITNESS_SPOT_DISTANCE = 14.0;
	public static final int WITNESS_DURATION = 5 * SECOND;

	// --- Chest creak (a chest opens and closes in your house while you're outside) ---

	public static final int CREAK_MIN_DREAD = 1;
	public static final long CREAK_COOLDOWN = GAME_DAY / 2;
	public static final double CREAK_MIN_DISTANCE = 9.0;
	public static final double CREAK_MAX_DISTANCE = 18.0;
	public static final float CREAK_VOLUME = 1.4F;
	public static final int CREAK_OPEN_TICKS = 50;

	// --- Far bell (one bell, far away, at night) ---

	public static final int BELL_MIN_DREAD = 3;
	public static final long BELL_COOLDOWN = 100L * GAME_DAY;
	public static final int BELL_MIN_DISTANCE = 40;
	public static final int BELL_MAX_DISTANCE = 55;
	public static final float BELL_VOLUME = 6.0F;
	public static final float BELL_PITCH = 0.55F;

	// --- Echo (your own footsteps, a half-second late) ---

	public static final int ECHO_MIN_DREAD = 2;
	public static final long ECHO_COOLDOWN = GAME_DAY / 2;
	public static final int ECHO_LENGTH = 10 * SECOND;
	public static final int ECHO_LAG = 10;
	public static final float ECHO_VOLUME = 0.35F;

	// --- Flower where you died ---

	public static final int DEATH_FLOWER_MIN_DREAD = 1;
	public static final long DEATH_FLOWER_COOLDOWN = 0;
	public static final int DEATH_FLOWER_WEIGHT = 30;
	public static final double DEATH_FLOWER_MIN_DISTANCE = 20.0;

	// --- Signs that quote you ---

	public static final int SIGN_MIN_DREAD = 3;
	public static final long SIGN_COOLDOWN = 2L * GAME_DAY;
	public static final double SIGN_MIN_PLAYER_DISTANCE = 24.0;
	public static final int SIGN_MIN_RADIUS = 3;
	public static final int SIGN_MAX_RADIUS = 7;
	public static final int CHAT_MEMORY = 12;
	public static final int CHAT_MAX_LENGTH = 60;

	// --- A lantern in the trees (gone when you look away and come closer) ---

	public static final int LIGHT_MIN_DREAD = 3;
	public static final long LIGHT_COOLDOWN = 2L * GAME_DAY;
	public static final int LIGHT_MIN_DISTANCE = 30;
	public static final int LIGHT_MAX_DISTANCE = 42;
	/** It disappears (when unseen) once you get this close. */
	public static final double LIGHT_VANISH_DISTANCE = 14.0;
	public static final long LIGHT_LIFETIME = 4L * MINUTE;
	/** If you walk right up to it, it is left alone; the record is dropped after this long. */
	public static final long LIGHT_FORGET_AFTER = 10L * MINUTE;

	// --- Things turned or relit in your base ---

	public static final int TAMPER_MIN_DREAD = 2;
	public static final long TAMPER_COOLDOWN = GAME_DAY;
	public static final double TAMPER_MIN_PLAYER_DISTANCE = 24.0;

	// --- A map of your base with a red X on your bed ---

	public static final int MAP_MIN_DREAD = 4;
	public static final long MAP_COOLDOWN = 4L * GAME_DAY;
	public static final double MAP_MIN_PLAYER_DISTANCE = 24.0;

	// --- A row of crops harvested and replanted ---

	public static final int CROPS_MIN_DREAD = 2;
	public static final long CROPS_COOLDOWN = 2L * GAME_DAY;
	public static final int CROPS_RADIUS = 16;
	public static final int CROPS_MIN_ROW = 3;
	public static final double CROPS_MIN_PLAYER_DISTANCE = 24.0;

	// --- Footprints that end at your window ---

	public static final int WINDOW_PRINTS_MIN_DREAD = 3;
	public static final long WINDOW_PRINTS_COOLDOWN = 2L * GAME_DAY;
	public static final double WINDOW_PRINTS_MIN_PLAYER_DISTANCE = 24.0;

	// --- Chest sorted while you were gone ---

	public static final int SORTED_MIN_DREAD = 2;
	public static final long SORTED_COOLDOWN = 2L * GAME_DAY;
	public static final int SORTED_MIN_ITEMS = 6;
	public static final double SORTED_MIN_PLAYER_DISTANCE = 24.0;

	// --- A lit candle by your bed ---

	public static final int CANDLE_MIN_DREAD = 3;
	public static final long CANDLE_COOLDOWN = 2L * GAME_DAY;
	public static final double CANDLE_MIN_PLAYER_DISTANCE = 20.0;

	// --- A flower on your doorstep ---

	public static final int DOORSTEP_MIN_DREAD = 2;
	public static final long DOORSTEP_COOLDOWN = 2L * GAME_DAY;
	public static final double DOORSTEP_MIN_PLAYER_DISTANCE = 24.0;

	// --- Waking up facing the wall ---

	public static final int BED_TURN_MIN_DREAD = 3;
	public static final double BED_TURN_CHANCE = 0.3;
	/** Walls farther than this from where you wake don't count. */
	public static final int BED_TURN_WALL_RANGE = 4;

	// ================================================================================
	// After the dragon. The rules below only apply at dread 6.
	// The mod never screams. Loud, shrill or sudden sounds are not part of it; keep every sound here soft.
	// ================================================================================

	/** Silence after the dragon dies, even at maximum dread. The next thing lands harder for it. */
	public static final long POST_DRAGON_QUIET = GAME_DAY;
	/** How far from the dragon a player counts as having been in the fight. */
	public static final double DRAGON_CREDIT_RADIUS = 192.0;
	/** At dread 6 every event's cooldown is multiplied by this (smaller means more often). */
	public static final double DREAD6_COOLDOWN_FACTOR = 0.4;
	/** Sleeping photo at dread 6. */
	public static final double DREAD6_PHOTO_CHANCE = 1.0;
	public static final long DREAD6_PHOTO_COOLDOWN = GAME_DAY;
	/** Shadow figure steps through this many photos, getting closer each time. */
	public static final int PHOTO_SHADOW_STEPS = 8;

	// --- What it writes down: how often it notes where you are and what you're doing ---

	public static final int ACTIVITY_SAMPLE_SECONDS = 60;
	public static final int ACTIVITY_MAX_ENTRIES = 40;
	/** Staying within this many blocks, doing the same thing, counts as the same entry. */
	public static final double ACTIVITY_SAME_PLACE = 48.0;

	// --- Logbook (a book that lists what you did, in detail) ---

	public static final int LOGBOOK_MIN_DREAD = 6;
	public static final long LOGBOOK_COOLDOWN = GAME_DAY / 2;
	public static final double LOGBOOK_MIN_PLAYER_DISTANCE = 20.0;
	public static final int LOGBOOK_MAX_ENTRIES = 14;
	public static final int LOGBOOK_WEIGHT = 18;

	// --- Heartbeat (a slow pulse only you can hear, speeding up, then it stops) ---

	public static final int HEARTBEAT_MIN_DREAD = 3;
	public static final long HEARTBEAT_COOLDOWN = GAME_DAY / 2;
	public static final int HEARTBEAT_BEATS_MIN = 12;
	public static final int HEARTBEAT_BEATS_MAX = 20;
	/** Dread 6 runs longer and faster. */
	public static final int HEARTBEAT_BEATS_MIN_6 = 24;
	public static final int HEARTBEAT_BEATS_MAX_6 = 40;
	public static final double HEARTBEAT_BPM_START = 62;
	public static final double HEARTBEAT_BPM_END = 96;
	public static final double HEARTBEAT_BPM_END_6 = 112;
	public static final float HEARTBEAT_VOLUME_START = 0.30F;
	public static final float HEARTBEAT_VOLUME_END = 0.75F;
	/** Pitch of the thud. Lower is deeper; 0.5 is as low as the game goes. */
	public static final float HEARTBEAT_PITCH = 0.5F;

	// --- Wingbeats (soft, far away, at night, with nothing there) ---

	public static final int WINGBEATS_MIN_DREAD = 6;
	public static final long WINGBEATS_COOLDOWN = GAME_DAY / 2;
	public static final int WINGBEATS_COUNT = 4;
	public static final int WINGBEATS_GAP = 26;
	public static final int WINGBEATS_DISTANCE = 45;
	public static final float WINGBEATS_VOLUME = 2.5F;
	public static final float WINGBEATS_PITCH = 0.8F;

	// --- Wandering candles (placed in your house, they move at night, even while you watch, and vanish as you come near) ---

	public static final int WANDER_MIN_DREAD = 6;
	public static final long WANDER_COOLDOWN = 8L * MINUTE;
	public static final int WANDER_WEIGHT = 22;
	public static final int WANDER_MIN_CANDLES = 1;
	public static final int WANDER_MAX_CANDLES = 3;
	/** The player has to be this close to the bed for it to start. */
	public static final double WANDER_PLAYER_RADIUS = 20.0;
	/** Starting spots are at least this far from the player, so it is first seen across the room. */
	public static final double WANDER_MIN_START_DISTANCE = 8.0;
	/** The candle is gone once the player is this close. */
	public static final double WANDER_VANISH_DISTANCE = 7.0;
	/** Ticks between hops, and how far one hop goes. */
	public static final int WANDER_HOP_MIN = 30;
	public static final int WANDER_HOP_MAX = 80;
	public static final int WANDER_HOP_RANGE = 2;
	/** How often it checks how close you are. */
	public static final int WANDER_CHECK_TICKS = 4;
	public static final long WANDER_LIFETIME = 2L * MINUTE;
	/** Records of candles older than this are cleaned up if a restart left one behind. */
	public static final long WANDER_FORGET_AFTER = 5L * MINUTE;
	/** Heartbeat when one vanishes on you. */
	public static final int WANDER_VANISH_BEATS = 8;

	// --- The return (while you were away: candles by the bed, the door open, a flower on the step) ---

	public static final int RETURN_MIN_DREAD = 6;
	public static final double RETURN_MIN_PLAYER_DISTANCE = 24.0;
	public static final int RETURN_WEIGHT = 40;

	// ================================================================================
	// The record of what it has done
	// ================================================================================

	/** How many actions are kept per player, oldest dropped first. */
	public static final int LOG_MAX_ENTRIES = 300;
	/** Lets any player look up their own log with /unseenlog. Set to false and only operators can read it. */
	public static final boolean PLAYER_CAN_LOOK_UP_LOG = true;
	public static final int LOG_DEFAULT_LINES = 10;
	public static final int LOG_MAX_LINES = 50;
}
