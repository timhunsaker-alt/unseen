package dev.unseen.net;

import dev.unseen.Unseen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the finished photo, already converted to map colors.
 * 128 x 128 = 16384 bytes, one map color per pixel.
 */
public record PhotoPayload(long nonce, byte[] pixels) implements CustomPacketPayload {
	public static final int SIZE = 128 * 128;

	public static final Type<PhotoPayload> TYPE = new Type<>(Unseen.id("photo"));
	public static final StreamCodec<FriendlyByteBuf, PhotoPayload> CODEC =
			StreamCodec.ofMember(PhotoPayload::write, PhotoPayload::read);

	private static PhotoPayload read(FriendlyByteBuf buf) {
		long nonce = buf.readLong();
		byte[] pixels = buf.readByteArray();
		return new PhotoPayload(nonce, pixels);
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeLong(this.nonce);
		buf.writeByteArray(this.pixels);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
