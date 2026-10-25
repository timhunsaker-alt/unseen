package dev.unseen.director;

import dev.unseen.Unseen;
import dev.unseen.events.FootprintsEvent;
import dev.unseen.events.FootstepsEvent;
import dev.unseen.events.BedTurn;
import dev.unseen.events.CandleEvent;
import dev.unseen.events.CreakEvent;
import dev.unseen.events.CropsEvent;
import dev.unseen.events.DeathFlowerEvent;
import dev.unseen.events.DoorstepEvent;
import dev.unseen.events.EchoEvent;
import dev.unseen.events.FarBellEvent;
import dev.unseen.events.FootprintCircleEvent;
import dev.unseen.events.HauntEvent;
import dev.unseen.events.HeartbeatEvent;
import dev.unseen.events.JournalEvent;
import dev.unseen.events.KnockEvent;
import dev.unseen.events.LogbookEvent;
import dev.unseen.events.MapEvent;
import dev.unseen.events.MissingTorchEvent;
import dev.unseen.events.OpenDoorEvent;
import dev.unseen.events.ReturnEvent;
import dev.unseen.events.SignEvent;
import dev.unseen.events.SortedChestEvent;
import dev.unseen.events.StrayLightEvent;
import dev.unseen.events.TamperEvent;
import dev.unseen.events.TorchTrailEvent;
import dev.unseen.events.WanderingCandlesEvent;
import dev.unseen.events.WindowPrintsEvent;
import dev.unseen.events.WingbeatsEvent;
import dev.unseen.events.WitnessEvent;
import dev.unseen.net.PhotoPayload;
import dev.unseen.net.SnapRequestPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The entity itself. It has no body. It keeps track of each player, slowly
 * escalates, and picks moments when nobody is looking to do one small thing.
 */
public final class Director {
	private static final Director INSTANCE = new Director();

	/**
	 * Photo maps claim to be from a dimension that doesn't exist, so the game
	 * never redraws them with real terrain. The picture stays a picture.
	 */
	public static final ResourceKey<Level> DARKROOM = ResourceKey.create(Registries.DIMENSION, Unseen.id("darkroom"));

	public static Director get() {
		return INSTANCE;
	}

	private final Random random = new Random();
	private final FootstepsEvent footsteps = new FootstepsEvent();
	private final List<HauntEvent> events = new ArrayList<>(List.of(
			new FootprintsEvent(),
			new MissingTorchEvent(),
			new OpenDoorEvent(),
			new KnockEvent(),
			new TorchTrailEvent(),
			new FootprintCircleEvent(),
			new JournalEvent(),
			new WitnessEvent(),
			new CreakEvent(),
			new FarBellEvent(),
			new EchoEvent(),
			new DeathFlowerEvent(),
			new SignEvent(),
			new StrayLightEvent(),
			new TamperEvent(),
			new MapEvent(),
			new CropsEvent(),
			new WindowPrintsEvent(),
			new SortedChestEvent(),
			new CandleEvent(),
			new DoorstepEvent(),
			new HeartbeatEvent(),
			new WingbeatsEvent(),
			new LogbookEvent(),
			new WanderingCandlesEvent(),
			new ReturnEvent(),
			this.footsteps));

	/** Things that happen a few seconds from now (a second knock, the next torch going out). */
	private record Delayed(long due, Runnable task) {
	}

	private final List<Delayed> delayed = new ArrayList<>();

	/** Photos we've asked for and are waiting on: player -> request. */
	private final Map<UUID, PendingPhoto> pendingPhotos = new HashMap<>();

	private record PendingPhoto(long nonce, BlockPos bed, long sendAt, long expiresAt, boolean sent, int detail, int shadow) {
		PendingPhoto markSent() {
			return new PendingPhoto(this.nonce, this.bed, this.sendAt, this.expiresAt, true, this.detail, this.shadow);
		}
	}

	private WatcherState state;
	private MinecraftServer server;

	private Director() {
	}

	// ---------------------------------------------------------------- lifecycle

	public void start(MinecraftServer server) {
		this.server = server;
		this.state = new WatcherState(server.getWorldPath(LevelResource.DATA).resolve("unseen_watcher.json"));
		this.state.load();
		this.pendingPhotos.clear();
		this.delayed.clear();
	}

	public void save() {
		if (this.state != null) {
			this.state.save();
		}
	}

	public void stop() {
		this.delayed.clear();
		this.save();
		this.state = null;
		this.server = null;
	}

	public List<HauntEvent> events() {
		return java.util.Collections.unmodifiableList(this.events);
	}

	/**
	 * Lets another mod add its own scares (the voice companion does this).
	 * Call during mod initialization, before the world starts.
	 */
	public void register(HauntEvent event) {
		this.events.add(event);
	}

	public PlayerRecord record(ServerPlayer player) {
		return this.state == null ? null : this.state.player(player.getUUID());
	}

	// ---------------------------------------------------------------- every tick

	public void tick(MinecraftServer server) {
		if (this.state == null) {
			return;
		}
		this.state.advance();
		long now = this.state.worldTicks();

		this.footsteps.tick(server);
		this.tickPhotos(now);
		this.tickDelayed(now);

		if (now % Tuning.SECOND != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			PlayerRecord record = this.state.player(player.getUUID());
			record.activeTicks += Tuning.SECOND;

			// After the dragon: a day of silence, then dread 6.
			record.quiet = record.dragonSlain && now < record.dragonQuietEndsAt;
			record.aftermath = record.dragonSlain && !record.quiet;

			// It writes down roughly where you are and what you're doing, about once a minute.
			if ((now / Tuning.SECOND + (player.getUUID().hashCode() & 0xFF)) % Tuning.ACTIVITY_SAMPLE_SECONDS == 0) {
				ActivityLog.sample(player, record);
			}

			// Spread players' rolls out so they don't all happen on the same tick.
			if ((now / Tuning.SECOND + (player.getUUID().hashCode() & 0xFF)) % (Tuning.CHECK_INTERVAL / Tuning.SECOND) == 0) {
				this.consider(player, record, now);
			}
			for (HauntEvent event : this.events) {
				try {
					event.tickPlayer(player, record, now);
				} catch (RuntimeException e) {
					Unseen.LOGGER.error("Event {} cleanup failed", event.id(), e);
				}
			}
			if (!record.undeliveredPhotos.isEmpty() && now % (10 * Tuning.SECOND) == 0) {
				this.retryDeliveries(player, record);
			}
		}
	}

	/** Runs {@code task} on the server thread after {@code ticks}. Dropped if the world closes first. */
	public void later(long ticks, Runnable task) {
		this.delayed.add(new Delayed(this.now() + ticks, task));
	}

	private void tickDelayed(long now) {
		if (this.delayed.isEmpty()) {
			return;
		}
		List<Delayed> due = new ArrayList<>();
		this.delayed.removeIf(d -> {
			if (d.due() <= now) {
				due.add(d);
				return true;
			}
			return false;
		});
		for (Delayed d : due) {
			try {
				d.task().run();
			} catch (RuntimeException e) {
				Unseen.LOGGER.error("Delayed task failed", e);
			}
		}
	}

	/** The director's main decision: do something now, or keep waiting. */
	private void consider(ServerPlayer player, PlayerRecord record, long now) {
		int dread = record.dread();
		if (dread <= 0 || player.isSleeping() || !Senses.isAlone(player)) {
			return;
		}
		if (record.quiet && record.dreadOverride < 0) {
			return; // the quiet after the dragon: nothing at all
		}
		if (now - record.lastEventAt < Tuning.QUIET_TIME[dread]) {
			return;
		}
		if (this.random.nextDouble() >= Tuning.EVENT_CHANCE[dread]) {
			return;
		}

		List<HauntEvent> candidates = new ArrayList<>();
		for (HauntEvent event : this.events) {
			// At dread 6 everything comes back sooner.
			long cooldown = dread >= Tuning.MAX_DREAD ? (long) (event.cooldown() * Tuning.DREAD6_COOLDOWN_FACTOR) : event.cooldown();
			if (dread >= event.minDread() && now - record.lastRun(event.id()) >= cooldown) {
				candidates.add(event);
			}
		}
		// Try events in a weighted-random order until one of them actually works.
		while (!candidates.isEmpty()) {
			HauntEvent event = this.pickWeighted(candidates, dread);
			candidates.remove(event);
			if (this.runEvent(player, record, event, now)) {
				return;
			}
		}
	}

	private HauntEvent pickWeighted(List<HauntEvent> candidates, int dread) {
		int total = 0;
		for (HauntEvent event : candidates) {
			total += event.weight(dread);
		}
		int roll = this.random.nextInt(Math.max(1, total));
		for (HauntEvent event : candidates) {
			roll -= event.weight(dread);
			if (roll < 0) {
				return event;
			}
		}
		return candidates.get(0);
	}

	public boolean runEvent(ServerPlayer player, PlayerRecord record, HauntEvent event, long now) {
		boolean happened;
		try {
			happened = event.run(player, record, this.random);
		} catch (RuntimeException e) {
			// A broken scare must never crash someone's world.
			Unseen.LOGGER.error("Event {} failed", event.id(), e);
			happened = false;
		}
		if (happened) {
			record.lastEventAt = now;
			record.lastRun.put(event.id(), now);
			Unseen.LOGGER.debug("{} -> {}", event.id(), player.getName().getString());
		}
		return happened;
	}

	public long now() {
		return this.state == null ? 0 : this.state.worldTicks();
	}

	// ---------------------------------------------------------------- things it remembers

	public void onDeath(ServerPlayer player) {
		if (this.state == null) {
			return;
		}
		PlayerRecord record = this.state.player(player.getUUID());
		record.deathPos = player.blockPosition().asLong();
		record.deathDimension = Senses.level(player).dimension().identifier().toString();
	}

	/** It reads what you say, and keeps the last few things. */
	public void onChat(ServerPlayer player, String text) {
		if (this.state == null) {
			return;
		}
		String line = text.strip();
		if (line.isEmpty()) {
			return;
		}
		if (line.length() > Tuning.CHAT_MAX_LENGTH) {
			line = line.substring(0, Tuning.CHAT_MAX_LENGTH);
		}
		PlayerRecord record = this.state.player(player.getUUID());
		record.chat.add(line);
		while (record.chat.size() > Tuning.CHAT_MEMORY) {
			record.chat.remove(0);
		}
	}

	/** The dragon fell. Everyone who was there starts the quiet; dread 6 follows when it ends. */
	public void onDragonDeath(net.minecraft.world.entity.Entity dragon) {
		if (this.state == null || !(dragon.level() instanceof ServerLevel level)) {
			return;
		}
		long now = this.state.worldTicks();
		double radius = Tuning.DRAGON_CREDIT_RADIUS;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive() || player.distanceToSqr(dragon) > radius * radius) {
				continue;
			}
			PlayerRecord record = this.state.player(player.getUUID());
			if (!record.dragonSlain) {
				record.dragonSlain = true;
				record.dragonQuietEndsAt = now + Tuning.POST_DRAGON_QUIET;
			}
		}
		this.save();
	}

	/** Debug: pretend the dragon fell ("slain"), end the quiet at once ("skipquiet"), or undo it all ("clear"). */
	public void debugDragon(PlayerRecord record, String mode) {
		long now = this.now();
		switch (mode) {
			case "slain" -> {
				record.dragonSlain = true;
				record.dragonQuietEndsAt = now + Tuning.POST_DRAGON_QUIET;
			}
			case "skipquiet" -> {
				record.dragonSlain = true;
				record.dragonQuietEndsAt = now;
			}
			default -> {
				record.dragonSlain = false;
				record.dragonQuietEndsAt = 0;
				record.returnDone = false;
				record.shadowStep = 0;
			}
		}
		record.quiet = record.dragonSlain && now < record.dragonQuietEndsAt;
		record.aftermath = record.dragonSlain && !record.quiet;
	}

	/** After they get out of bed: sometimes they're facing the wall. */
	public void onWake(ServerPlayer player) {
		if (this.state == null) {
			return;
		}
		PlayerRecord record = this.state.player(player.getUUID());
		boolean forced = record.forceBedTurnNextWake;
		record.forceBedTurnNextWake = false;
		if (!forced && (record.dread() < Tuning.BED_TURN_MIN_DREAD || this.random.nextDouble() >= Tuning.BED_TURN_CHANCE
				|| !Senses.isAlone(player))) {
			return;
		}
		// Wait a moment: the game moves them out of bed after this event fires.
		this.later(3, () -> BedTurn.apply(player));
	}

	// ---------------------------------------------------------------- sleeping & photos

	public void onStartSleeping(ServerPlayer player, BlockPos bed) {
		if (this.state == null) {
			return;
		}
		PlayerRecord record = this.state.player(player.getUUID());

		// It learns where you sleep.
		record.homePos = bed.asLong();
		record.homeDimension = Senses.level(player).dimension().identifier().toString();
		record.timesSlept++;

		long now = this.state.worldTicks();
		int dread = record.dread();
		boolean aftermath = dread >= Tuning.MAX_DREAD;
		long cooldown = aftermath ? Tuning.DREAD6_PHOTO_COOLDOWN : Tuning.PHOTO_COOLDOWN;
		double chance = aftermath ? Tuning.DREAD6_PHOTO_CHANCE : Tuning.PHOTO_CHANCE;
		boolean due = !(record.quiet && record.dreadOverride < 0)
				&& dread >= Tuning.PHOTO_MIN_DREAD
				&& now - record.lastRun("photo") >= cooldown
				&& this.random.nextDouble() < chance
				&& Senses.isAlone(player);
		if (due || record.forcePhotoNextSleep) {
			record.forcePhotoNextSleep = false;
			long nonce = this.random.nextLong();
			// After the dragon the photo is sharper, and there's a shadow in it that gets closer each time.
			int detail = aftermath ? 1 : 0;
			int shadow = aftermath ? Math.min(record.shadowStep + 1, Tuning.PHOTO_SHADOW_STEPS) : 0;
			this.pendingPhotos.put(player.getUUID(),
					new PendingPhoto(nonce, bed, now + Tuning.PHOTO_DELAY, now + Tuning.PHOTO_TIMEOUT, false, detail, shadow));
		}
	}

	private void tickPhotos(long now) {
		Iterator<Map.Entry<UUID, PendingPhoto>> it = this.pendingPhotos.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, PendingPhoto> entry = it.next();
			PendingPhoto pending = entry.getValue();
			if (now > pending.expiresAt()) {
				it.remove();
				continue;
			}
			if (!pending.sent() && now >= pending.sendAt()) {
				ServerPlayer player = this.server.getPlayerList().getPlayer(entry.getKey());
				if (player == null || !player.isSleeping() || !ServerPlayNetworking.canSend(player, SnapRequestPayload.TYPE)) {
					it.remove(); // got up too fast, left, or doesn't have the mod on their client
					continue;
				}
				ServerPlayNetworking.send(player, new SnapRequestPayload(pending.nonce(), pending.detail(), pending.shadow()));
				entry.setValue(pending.markSent());
			}
		}
	}

	public void onPhotoReceived(ServerPlayer player, PhotoPayload payload) {
		PendingPhoto pending = this.pendingPhotos.get(player.getUUID());
		if (this.state == null || pending == null || !pending.sent() || pending.nonce() != payload.nonce()
				|| payload.pixels().length != PhotoPayload.SIZE) {
			return; // not something we asked for
		}
		this.pendingPhotos.remove(player.getUUID());

		ServerLevel level = Senses.level(player);
		MapId mapId = level.getFreeMapId();
		MapItemSavedData map = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, DARKROOM);
		System.arraycopy(payload.pixels(), 0, map.colors, 0, PhotoPayload.SIZE);
		level.setMapData(mapId, map);

		PlayerRecord record = this.state.player(player.getUUID());
		record.lastRun.put("photo", this.state.worldTicks());
		if (pending.shadow() > 0) {
			record.shadowStep = pending.shadow(); // the next photo has it one step closer
		}

		if (!this.deliver(level, pending.bed(), mapId)) {
			// No chest near the bed. It'll wait until there is one.
			record.undeliveredPhotos.add(mapId.id());
		}
	}

	private boolean deliver(ServerLevel level, BlockPos near, MapId mapId) {
		ItemStack photo = new ItemStack(Items.FILLED_MAP);
		photo.set(DataComponents.MAP_ID, mapId);
		return Senses.tuckInto(Senses.containersNear(level, near), photo, this.random);
	}

	private void retryDeliveries(ServerPlayer player, PlayerRecord record) {
		ServerLevel level = Senses.level(player);
		BlockPos home = FootprintsEvent.homeIn(level, record);
		if (home == null || !Senses.homeLoaded(level, home) || player.isSleeping()) {
			return;
		}
		// Only while they're out of the room.
		if (player.blockPosition().distSqr(home) < 12 * 12) {
			return;
		}
		Iterator<Integer> it = record.undeliveredPhotos.iterator();
		while (it.hasNext()) {
			if (this.deliver(level, home, new MapId(it.next()))) {
				it.remove();
			} else {
				return;
			}
		}
	}
}
