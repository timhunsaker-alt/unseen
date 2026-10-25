package dev.unseen.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;

/**
 * Replaces the spinning panorama on the title screen with a still of the forest.
 * It is only a picture. Nothing about it moves.
 */
public final class TitleBackground {
	private static final Identifier ART = Identifier.fromNamespaceAndPath("unseen", "textures/gui/title_background.png");
	private static final float ART_ASPECT = 1920.0F / 1104.0F;

	private TitleBackground() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof TitleScreen) {
				ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY, delta) -> {
					// Cover-fit: fill the window and crop whatever overflows, keeping the figure's side in frame.
					float screenAspect = (float) s.width / s.height;
					float u0 = 0.0F, u1 = 1.0F, v0 = 0.0F, v1 = 1.0F;
					if (screenAspect < ART_ASPECT) {
						float visible = screenAspect / ART_ASPECT;
						u0 = (1.0F - visible) * 0.5F;
						u1 = u0 + visible;
					} else {
						float visible = ART_ASPECT / screenAspect;
						v0 = (1.0F - visible) * 0.5F;
						v1 = v0 + visible;
					}
					graphics.blit(ART, 0, 0, s.width, s.height, u0, u1, v0, v1);
				});
			}
		});
	}
}
