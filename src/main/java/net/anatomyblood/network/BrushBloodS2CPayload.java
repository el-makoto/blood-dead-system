package net.anatomyblood.network;

import net.anatomyblood.util.BloodConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BrushBloodS2CPayload(double x, double y, double z) implements CustomPayload {
    public static final CustomPayload.Id<BrushBloodS2CPayload> ID = new CustomPayload.Id<>(Identifier.of(BloodConstants.MOD_ID, "brush_blood_s2c"));

    public static final PacketCodec<RegistryByteBuf, BrushBloodS2CPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeDouble(value.x());
                buf.writeDouble(value.y());
                buf.writeDouble(value.z());
            },
            buf -> new BrushBloodS2CPayload(buf.readDouble(), buf.readDouble(), buf.readDouble())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
