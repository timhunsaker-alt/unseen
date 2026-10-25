package dev.unseen.net;

import dev.unseen.Unseen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: "take a picture of yourself sleeping." The nonce stops forged replies. */
public record SnapRequestPayload(long nonce) implements CustomPacketPayload {
	public static final Type<SnapRequestPayload> TYPE = new Type<>(Unseen.id("snap"));
	public static final StreamCodec<FriendlyByteBuf, SnapRequestPayload> CODEC =
			StreamCodec.ofMember(SnapRequestPayload::write, SnapRequestPayload::read);

	private static SnapRequestPayload read(FriendlyByteBuf buf) {
		return new SnapRequestPayload(buf.readLong());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeLong(this.nonce);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
