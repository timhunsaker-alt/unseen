package dev.unseen;

import dev.unseen.director.Director;
import dev.unseen.net.PhotoPayload;
import dev.unseen.net.SnapRequestPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. Wires the director into the game's events.
 *
 * The "entity" in this mod has no model and no AI. It's a director that
 * watches the player and decides when to do something small and wrong.
 */
public class Unseen implements ModInitializer {
	public static final String MOD_ID = "unseen";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		UnseenBlocks.register();

		// Networking: server asks the client for a photo, client sends the pixels back.
		PayloadTypeRegistry.clientboundPlay().register(SnapRequestPayload.TYPE, SnapRequestPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PhotoPayload.TYPE, PhotoPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PhotoPayload.TYPE,
				(payload, context) -> Director.get().onPhotoReceived(context.player(), payload));

		// World lifecycle: load and save the director's memory.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> Director.get().start(server));
		ServerLifecycleEvents.AFTER_SAVE.register((server, flush, force) -> Director.get().save());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Director.get().stop());

		ServerTickEvents.END_SERVER_TICK.register(server -> Director.get().tick(server));

		EntitySleepEvents.START_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayer player) {
				Director.get().onStartSleeping(player, sleepingPos);
			}
		});

		EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayer player) {
				Director.get().onWake(player);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				Director.get().onDeath(player);
			} else if (entity instanceof EnderDragon) {
				Director.get().onDragonDeath(entity);
			}
		});
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
				Director.get().onChat(sender, message.signedContent()));

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, buildContext, selection) -> {
					UnseenCommand.register(dispatcher);
					UnseenLogCommand.register(dispatcher);
				});

		LOGGER.info("...");
	}
}
