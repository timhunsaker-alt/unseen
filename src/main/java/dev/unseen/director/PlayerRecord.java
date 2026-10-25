package dev.unseen.director;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the entity remembers about one player. Saved to JSON with the world,
 * so the haunting picks up where it left off. Plain fields so Gson can save it.
 */
public class PlayerRecord {
	/** Ticks this player has spent in the world while the mod was watching. */
	public long activeTicks;

	/** -1 means "use activeTicks"; anything else forces a dread level (debug command). */
	public int dreadOverride = -1;

	/** The bed it saw them sleep in. Learned the first time they sleep. */
	public Long homePos;
	public String homeDimension;

	/** World tick of the last event, and of the last time each event ran. */
	public long lastEventAt = Long.MIN_VALUE / 2;
	public Map<String, Long> lastRun = new HashMap<>();

	/** Photos that were taken but had no chest to go in yet. Map ids. */
	public List<Integer> undeliveredPhotos = new ArrayList<>();

	/** How many times it has watched them go to bed. */
	public int timesSlept;

	/** Where they last died (block pos) and in which dimension, until a flower has been left there. */
	public Long deathPos;
	public String deathDimension;

	/** The last things they said in chat, newest last. Used for signs. */
	public List<String> chat = new ArrayList<>();

	/** Lanterns left out in the dark: "dimension|blockpos" -> world tick it was placed. */
	public Map<String, Long> strayLights = new HashMap<>();

	/** The dragon: whether this player was there when it fell, and when the quiet afterwards ends (world tick). */
	public boolean dragonSlain;
	public long dragonQuietEndsAt;
	/** The "return" scare (home rearranged while they were away) has happened. */
	public boolean returnDone;
	/** How many shadow-in-the-photo steps have been used. */
	public int shadowStep;
	/** What it has noted about where they were and what they were doing, oldest first. */
	public List<ActivityLog.Entry> activity = new ArrayList<>();
	/** Candles wandering in the house: "dimension|blockpos" -> world tick placed. */
	public Map<String, Long> wanderCandles = new HashMap<>();

	/** What it has done to this player, oldest first. Shown only when they ask (/unseenlog). */
	public List<HauntLog.Entry> history = new ArrayList<>();
	/** The last dread level it noticed, so a rise can be written down. -1 until first seen. */
	public int lastDread = -1;

	/** Worked out every second by the director from the fields above; not saved. */
	public transient boolean quiet;
	public transient boolean aftermath;
	public transient boolean wasQuiet;

	/** Set by the debug command: take a photo the next time they sleep. */
	public boolean forcePhotoNextSleep;

	/** Set by the debug command: turn them to face a wall the next time they wake. */
	public boolean forceBedTurnNextWake;

	public int dread() {
		if (this.dreadOverride >= 0) {
			return Math.min(this.dreadOverride, Tuning.MAX_DREAD);
		}
		if (this.aftermath) {
			return Tuning.MAX_DREAD; // after the dragon
		}
		return (int) Math.min(Tuning.TIME_DREAD_CAP, this.activeTicks / Tuning.TICKS_PER_DREAD);
	}

	public long lastRun(String eventId) {
		return this.lastRun.getOrDefault(eventId, Long.MIN_VALUE / 2);
	}
}
