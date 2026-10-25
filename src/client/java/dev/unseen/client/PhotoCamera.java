package dev.unseen.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.unseen.Unseen;
import dev.unseen.net.PhotoPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;

/**
 * Takes the picture of you sleeping.
 *
 * For a split second it swings the camera out to look down at you from the
 * foot of the bed, hides the HUD, grabs the frame, and puts everything back.
 * That one-frame flicker is the only clue you'll get.
 */
public final class PhotoCamera {
	private enum Phase { IDLE, POSING, SHOOTING, RESTORING }

	/** Looking up this much makes the mirrored third-person camera look down at you. */
	private static final float POSE_PITCH = -40.0F;

	private static Phase phase = Phase.IDLE;
	private static long nonce;
	private static int detail;
	private static int shadow;

	private static CameraType savedCamera;
	private static boolean savedHideGui;
	private static float savedPitch;
	private static float savedPitchO;

	private PhotoCamera() {
	}

	public static void request(long requestNonce, int requestDetail, int requestShadow) {
		if (phase == Phase.IDLE) {
			nonce = requestNonce;
			detail = requestDetail;
			shadow = requestShadow;
			phase = Phase.POSING;
		}
	}

	/** Runs at the end of every client tick. */
	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (phase == Phase.IDLE) {
			return;
		}
		if (player == null) {
			phase = Phase.IDLE;
			return;
		}

		switch (phase) {
			case POSING -> {
				if (!player.isSleeping()) {
					phase = Phase.IDLE; // woke up first; no photo this time
					return;
				}
				savedCamera = client.options.getCameraType();
				savedHideGui = client.gui.hud.isHidden();
				savedPitch = player.getXRot();
				savedPitchO = player.xRotO;

				client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
				player.setXRot(POSE_PITCH);
				player.xRotO = POSE_PITCH;
				// The bed screen (Leave Bed button + chat bar) isn't part of the HUD, so hiding the HUD
				// doesn't remove it. Drop it for this frame; Gui.tick reopens it at the start of the next tick.
				client.gui.setScreen(null);
				phase = Phase.SHOOTING; // let at least one frame render like this
			}
			case SHOOTING -> {
				long shotNonce = nonce;
				int shotDetail = detail;
				int shotShadow = shadow;
				try {
					Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> develop(client, image, shotNonce, shotDetail, shotShadow));
				} catch (RuntimeException e) {
					Unseen.LOGGER.warn("Photo failed", e);
				}
				phase = Phase.RESTORING;
			}
			case RESTORING -> {
				client.options.setCameraType(savedCamera);
				if (client.gui.hud.isHidden() != savedHideGui) {
					client.gui.hud.toggle();
				}
				player.setXRot(savedPitch);
				player.xRotO = savedPitchO;
				phase = Phase.IDLE;
			}
			default -> phase = Phase.IDLE;
		}
	}

	private static void develop(Minecraft client, NativeImage image, long shotNonce, int shotDetail, int shotShadow) {
		byte[] pixels;
		try (image) {
			pixels = Darkroom.develop(image::getPixel, image.getWidth(), image.getHeight(), shotNonce, shotDetail, shotShadow);
		} catch (RuntimeException e) {
			Unseen.LOGGER.warn("Photo failed to develop", e);
			return;
		}
		client.execute(() -> {
			if (ClientPlayNetworking.canSend(PhotoPayload.TYPE)) {
				ClientPlayNetworking.send(new PhotoPayload(shotNonce, pixels));
			}
		});
	}
}
