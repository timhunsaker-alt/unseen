package dev.unseen.voice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Talks to your own VoiceStudio server. Only ever sent: a short reference recording of a
 * player who opted in, and the text of a phrase. Nothing else, and nowhere else.
 */
public class VoiceStudioClient {
	private final VoiceConfig config;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	public VoiceStudioClient(VoiceConfig config) {
		this.config = config;
	}

	private String base() {
		String url = this.config.voiceStudioUrl.strip();
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private HttpRequest.Builder request(String path) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(this.base() + path)).timeout(Duration.ofMinutes(5));
		if (this.config.apiKey != null && !this.config.apiKey.isBlank()) {
			builder.header("Authorization", "Bearer " + this.config.apiKey.strip());
		}
		return builder;
	}

	public boolean healthy() {
		try {
			HttpResponse<Void> response = this.http.send(this.request("/health").timeout(Duration.ofSeconds(10)).GET().build(),
					HttpResponse.BodyHandlers.discarding());
			return response.statusCode() == 200;
		} catch (IOException | RuntimeException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Makes a cloned voice from a reference recording. Returns the profile id, or null if the
	 * server didn't say (the voice can still be used by name).
	 */
	public String createProfile(String name, byte[] wav) throws IOException, InterruptedException {
		String boundary = "----unseen" + UUID.randomUUID().toString().replace("-", "");
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		field(body, boundary, "name", name);
		field(body, boundary, "kind", "clone");
		field(body, boundary, "language", this.config.language);
		body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"ref_audio\"; filename=\"reference.wav\"\r\n"
				+ "Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		body.write(wav);
		body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

		HttpResponse<String> response = this.http.send(this.request("/profiles")
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() / 100 != 2) {
			throw new IOException("Creating the voice failed: HTTP " + response.statusCode() + " " + response.body());
		}
		try {
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			return json.has("id") && !json.get("id").isJsonNull() ? json.get("id").getAsString() : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Records that the speaker gave permission, using the server's own consent record. */
	public void attest(String profileId, String basis) throws IOException, InterruptedException {
		JsonObject json = new JsonObject();
		json.addProperty("basis", basis);
		HttpResponse<String> response = this.http.send(this.request("/profiles/" + profileId + "/attestation")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.toString())).build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() / 100 != 2) {
			throw new IOException("Recording consent failed: HTTP " + response.statusCode() + " " + response.body());
		}
	}

	/** Says {@code text} in the named voice. Returns 24 kHz, 16-bit, mono samples. */
	public short[] speak(String voice, String text) throws IOException, InterruptedException {
		JsonObject json = new JsonObject();
		json.addProperty("model", this.config.model);
		json.addProperty("input", text);
		json.addProperty("voice", voice);
		json.addProperty("response_format", "pcm");
		HttpResponse<byte[]> response = this.http.send(this.request("/v1/audio/speech")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.toString())).build(), HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() / 100 != 2) {
			throw new IOException("Speech failed: HTTP " + response.statusCode() + " "
					+ new String(response.body(), StandardCharsets.UTF_8));
		}
		return Wav.fromPcm(response.body());
	}

	private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
		out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
				.getBytes(StandardCharsets.UTF_8));
	}
}
