package dev.unseen.director;

import dev.unseen.Unseen;
import dev.unseen.events.FootprintsEvent;
import dev.unseen.events.FootstepsEvent;
import dev.unseen.events.HauntEvent;
import dev.unseen.events.MissingTorchEvent;
import dev.unseen.events.OpenDoorEvent;
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
	private static final ResourceKey<Level> DARKROOM = ResourceKey.create(Registries.DIMENSION, Unseen.id("darkroom"));

	public static Director get() {
		return INSTANCE;
	}

	private final Random random = new Random();
	private final FootstepsEvent footsteps = new FootstepsEvent();
	private final List<HauntEvent> events = List.of(
			new FootprintsEvent(),
			new MissingTorchEvent(),
			new OpenDoorEvent(),
			this.footsteps);

	/** Photos we've asked for and are waiting on: player -> request. */
	private final Map<UUID, PendingPhoto> pendingPhotos = new HashMap<>();

	private record PendingPhoto(long nonce, BlockPos bed, long sendAt, long expiresAt, boolean sent) {
		PendingPhoto markSent() {
			return new PendingPhoto(this.nonce, this.bed, this.sendAt, this.expiresAt, true);
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
	}

	public void save() {
		if (this.state != null) {
			this.state.save();
		}
	}

	public void stop() {
		this.save();
		this.state = null;
		this.server = null;
	}

	public List<HauntEvent> events() {
		return this.events;
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

		if (now % Tuning.SECOND != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			PlayerRecord record = this.state.player(player.getUUID());
			record.activeTicks += Tuning.SECOND;

			// Spread players' rolls out so they don't all happen on the same tick.
			if ((now / Tuning.SECOND + (player.getUUID().hashCode() & 0xFF)) % (Tuning.CHECK_INTERVAL / Tuning.SECOND) == 0) {
				this.consider(player, record, now);
			}
			if (!record.undeliveredPhotos.isEmpty() && now % (10 * Tuning.SECOND) == 0) {
				this.retryDeliveries(player, record);
			}
		}
	}

	/** The director's main decision: do something now, or keep waiting. */
	private void consider(ServerPlayer player, PlayerRecord record, long now) {
		int dread = record.dread();
		if (dread <= 0 || player.isSleeping() || !Senses.isAlone(player)) {
			return;
		}
		if (now - record.lastEventAt < Tuning.QUIET_TIME[dread]) {
			return;
		}
		if (this.random.nextDouble() >= Tuning.EVENT_CHANCE[dread]) {
			return;
		}

		List<HauntEvent> candidates = new ArrayList<>();
		for (HauntEvent event : this.events) {
			if (dread >= event.minDread() && now - record.lastRun(event.id()) >= event.cooldown()) {
				candidates.add(event);
			}
		}
		// Try events in a weighted-random order until one of them actually works.
		while (!candidates.isEmpty()) {
			HauntEvent event = this.pickWeighted(candidates);
			candidates.remove(event);
			if (this.runEvent(player, record, event, now)) {
				return;
			}
		}
	}

	private HauntEvent pickWeighted(List<HauntEvent> candidates) {
		int total = 0;
		for (HauntEvent event : candidates) {
			total += event.weight();
		}
		int roll = this.random.nextInt(Math.max(1, total));
		for (HauntEvent event : candidates) {
			roll -= event.weight();
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

	// ---------------------------------------------------------------- sleeping & photos

	public void onStartSleeping(ServerPlayer player, BlockPos bed) {
		if (this.state == null) {
			return;
		}
		PlayerRecord record = this.state.player(player.getUUID());

		// It learns where you sleep.
		record.homePos = bed.asLong();
		record.homeDimension = Senses.level(player).dimension().identifier().toString();

		long now = this.state.worldTicks();
		boolean due = record.dread() >= Tuning.PHOTO_MIN_DREAD
				&& now - record.lastRun("photo") >= Tuning.PHOTO_COOLDOWN
				&& this.random.nextDouble() < Tuning.PHOTO_CHANCE
				&& Senses.isAlone(player);
		if (due || record.forcePhotoNextSleep) {
			record.forcePhotoNextSleep = false;
			long nonce = this.random.nextLong();
			this.pendingPhotos.put(player.getUUID(),
					new PendingPhoto(nonce, bed, now + Tuning.PHOTO_DELAY, now + Tuning.PHOTO_TIMEOUT, false));
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
				ServerPlayNetworking.send(player, new SnapRequestPayload(pending.nonce()));
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
