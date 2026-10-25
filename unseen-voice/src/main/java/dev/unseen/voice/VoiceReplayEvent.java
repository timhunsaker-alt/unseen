package dev.unseen.voice;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import dev.unseen.Unseen;
import dev.unseen.director.PlayerRecord;
import dev.unseen.director.Senses;
import dev.unseen.director.Tuning;
import dev.unseen.events.HauntEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Your own voice, from somewhere behind you. Sometimes it's something you said earlier.
 * Sometimes it's something you never said.
 *
 * Only the target player hears it. It comes from a spot they aren't looking at.
 */
public class VoiceReplayEvent implements HauntEvent {
	@Override
	public String id() {
		return "voice";
	}

	@Override
	public String description() {
		return "Your own voice spoke from somewhere behind you.";
	}

	@Override
	public int minDread() {
		return 3;
	}

	@Override
	public long cooldown() {
		return 12L * Tuning.MINUTE;
	}

	@Override
	public int weight() {
		return 8;
	}

	@Override
	public boolean run(ServerPlayer player, PlayerRecord record, Random random) {
		VoiceStore store = UnseenVoice.store();
		VoiceConfig config = UnseenVoice.config();
		VoicechatServerApi api = UnseenVoicePlugin.api();
		if (store == null || config == null || api == null || !store.isOptedIn(player.getUUID())) {
			return false;
		}
		VoicechatConnection connection = api.getConnectionOf(player.getUUID());
		if (connection == null || !connection.isConnected() || connection.isDisabled()) {
			return false; // they don't have voice chat running, so there's nobody to hear it
		}

		// A recorded clip, or a phrase made in their voice. Phrases are picked more often.
		List<Path> sounds = new ArrayList<>(store.clips(player.getUUID()));
		List<Path> phrases = store.bank(player.getUUID());
		for (int i = 0; i < Math.max(1, config.phraseWeight); i++) {
			sounds.addAll(phrases);
		}
		if (sounds.isEmpty()) {
			return false;
		}

		Vec3 spot = this.spotBehind(player, config, random);
		if (spot == null) {
			return false;
		}
		try {
			return this.play(api, player, spot, sounds.get(random.nextInt(sounds.size())), config);
		} catch (IOException e) {
			Unseen.LOGGER.warn("Couldn't play a voice: {}", e.getMessage());
			return false;
		}
	}

	/** A point out of the player's view: behind them, to one side or the other. */
	private Vec3 spotBehind(ServerPlayer player, VoiceConfig config, Random random) {
		Vec3 look = player.getLookAngle();
		double behind = Math.atan2(-look.z, -look.x);
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = behind + Math.toRadians((random.nextDouble() - 0.5) * 120.0);
			double distance = config.minDistance + random.nextDouble() * Math.max(0, config.maxDistance - config.minDistance);
			Vec3 spot = new Vec3(player.getX() + Math.cos(angle) * distance, player.getY() + 1.0, player.getZ() + Math.sin(angle) * distance);
			if (!Senses.canSee(player, spot)) {
				return spot;
			}
		}
		return null;
	}

	private boolean play(VoicechatServerApi api, ServerPlayer player, Vec3 spot, Path file, VoiceConfig config) throws IOException {
		Wav.Audio audio = Wav.read(file);
		short[] pcm = Wav.resample(audio.samples(), audio.rate(), Wav.VOICE_RATE);
		pcm = Wav.padToFrames(Wav.gain(Wav.stretch(pcm, config.playbackSpeed), config.playbackVolume));

		LocationalAudioChannel channel = api.createLocationalAudioChannel(UUID.randomUUID(),
				api.fromServerLevel((ServerLevel) player.level()), api.createPosition(spot.x, spot.y, spot.z));
		if (channel == null) {
			return false;
		}
		UUID target = player.getUUID();
		channel.setDistance(config.hearingDistance);
		channel.setFilter(listener -> listener.getUuid().equals(target)); // only them

		OpusEncoder encoder = api.createEncoder();
		AudioPlayer audioPlayer = api.createAudioPlayer(channel, encoder, pcm);
		audioPlayer.setOnStopped(encoder::close);
		audioPlayer.startPlaying();
		return true;
	}
}
