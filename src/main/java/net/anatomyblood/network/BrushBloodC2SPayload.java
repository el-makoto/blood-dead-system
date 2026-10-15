package net.anatomyblood.network;

import net.anatomyblood.util.BloodConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BrushBloodC2SPayload(double x, double y, double z, boolean mainHand) implements CustomPayload {
    public static final CustomPayload.Id<BrushBloodC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(BloodConstants.MOD_ID, "brush_blood_c2s"));

    public static final PacketCodec<RegistryByteBuf, BrushBloodC2SPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeDouble(value.x());
                buf.writeDouble(value.y());
                buf.writeDouble(value.z());
                buf.writeBoolean(value.mainHand());
            },
            buf -> new BrushBloodC2SPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
