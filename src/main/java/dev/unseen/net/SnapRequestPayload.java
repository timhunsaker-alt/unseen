package dev.unseen.net;

import dev.unseen.Unseen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: "take a picture of yourself sleeping." The nonce stops forged replies.
 * {@code detail} 0 is the normal grainy photo; 1 is the sharper one used after the dragon.
 * {@code shadow} is 0 for none, or how far along the shadow figure is in the frame (1 to 8).
 */
public record SnapRequestPayload(long nonce, int detail, int shadow) implements CustomPacketPayload {
	public static final Type<SnapRequestPayload> TYPE = new Type<>(Unseen.id("snap"));
	public static final StreamCodec<FriendlyByteBuf, SnapRequestPayload> CODEC =
			StreamCodec.ofMember(SnapRequestPayload::write, SnapRequestPayload::read);

	private static SnapRequestPayload read(FriendlyByteBuf buf) {
		return new SnapRequestPayload(buf.readLong(), buf.readVarInt(), buf.readVarInt());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeLong(this.nonce);
		buf.writeVarInt(this.detail);
		buf.writeVarInt(this.shadow);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
