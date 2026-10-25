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

	/** Set by the debug command: take a photo the next time they sleep. */
	public boolean forcePhotoNextSleep;

	public int dread() {
		if (this.dreadOverride >= 0) {
			return Math.min(this.dreadOverride, Tuning.MAX_DREAD);
		}
		return (int) Math.min(Tuning.MAX_DREAD, this.activeTicks / Tuning.TICKS_PER_DREAD);
	}

	public long lastRun(String eventId) {
		return this.lastRun.getOrDefault(eventId, Long.MIN_VALUE / 2);
	}
}
