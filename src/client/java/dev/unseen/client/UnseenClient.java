package dev.unseen.client;

import dev.unseen.net.SnapRequestPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class UnseenClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(SnapRequestPayload.TYPE,
				(payload, context) -> PhotoCamera.request(payload.nonce(), payload.detail(), payload.shadow()));
		ClientTickEvents.END_CLIENT_TICK.register(PhotoCamera::tick);
		TitleBackground.register();
	}
}
