package dev.unseen.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import dev.unseen.Unseen;

/** The hook Simple Voice Chat loads (see the "voicechat" entrypoint in fabric.mod.json). */
public class UnseenVoicePlugin implements VoicechatPlugin {
	private static volatile VoicechatServerApi serverApi;

	/** Null until the voice chat server has started. */
	public static VoicechatServerApi api() {
		return serverApi;
	}

	@Override
	public String getPluginId() {
		return "unseen_voice";
	}

	@Override
	public void initialize(VoicechatApi api) {
		Unseen.LOGGER.info("Unseen Voice found Simple Voice Chat.");
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(VoicechatServerStartedEvent.class, event -> serverApi = event.getVoicechat());
		registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
		registration.registerEvent(PlayerDisconnectedEvent.class, event -> {
			ClipRecorder recorder = UnseenVoice.recorder();
			if (recorder != null) {
				recorder.forget(event.getPlayerUuid());
			}
		});
	}

	private void onMicrophone(MicrophonePacketEvent event) {
		try {
			ClipRecorder recorder = UnseenVoice.recorder();
			VoicechatConnection sender = event.getSenderConnection();
			if (recorder != null && sender != null) {
				recorder.onMicrophone(event.getVoicechat(), sender.getPlayer().getUuid(), event.getPacket().getOpusEncodedData());
			}
		} catch (RuntimeException e) {
			Unseen.LOGGER.warn("Voice clip skipped", e); // never let this disturb anyone's voice chat
		}
	}
}
