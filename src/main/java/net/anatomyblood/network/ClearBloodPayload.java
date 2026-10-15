package net.anatomyblood.network;

import net.anatomyblood.util.BloodConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ClearBloodPayload() implements CustomPayload {
    public static final CustomPayload.Id<ClearBloodPayload> ID = new CustomPayload.Id<>(Identifier.of(BloodConstants.MOD_ID, "clear_blood"));
    public static final PacketCodec<RegistryByteBuf, ClearBloodPayload> CODEC = PacketCodec.unit(new ClearBloodPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
