package dev.unseen.voice;

import dev.unseen.Unseen;
import dev.unseen.director.Director;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The voice companion. Adds one scare to the Unseen director ("voice"): a sound of the player's
 * own voice, from somewhere behind them, saying something they said, or something they didn't.
 *
 * Everything is opt-in per player and stays on the server (and your own voice server).
 */
public class UnseenVoice implements ModInitializer {
	private static VoiceConfig config;
	private static volatile VoiceStore store;
	private static volatile ClipRecorder recorder;
	private static volatile PhraseBank bank;

	public static VoiceConfig config() {
		return config;
	}

	public static VoiceStore store() {
		return store;
	}

	public static ClipRecorder recorder() {
		return recorder;
	}

	public static PhraseBank bank() {
		return bank;
	}

	@Override
	public void onInitialize() {
		config = VoiceConfig.load(FabricLoader.getInstance().getConfigDir().resolve("unseen-voice.json"));
		Director.get().register(new VoiceReplayEvent());

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Path root = server.getWorldPath(LevelResource.DATA).resolve("unseen_voice");
			store = new VoiceStore(root);
			recorder = new ClipRecorder(store, config);
			bank = new PhraseBank(store, config);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ClipRecorder r = recorder;
			PhraseBank b = bank;
			recorder = null;
			bank = null;
			store = null;
			if (r != null) {
				r.close();
			}
			if (b != null) {
				b.close();
			}
		});

		// Every five minutes: build phrase banks for anyone who has enough recorded speech.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			PhraseBank b = bank;
			if (b != null && server.getTickCount() % (5 * 60 * 20) == 0) {
				b.autoBuild(uuid -> {
					ServerPlayer p = server.getPlayerList().getPlayer(uuid);
					return p == null ? null : p.getName().getString();
				});
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> VoiceCommand.register(dispatcher));
		Unseen.LOGGER.info("...and it is listening.");
	}
}
