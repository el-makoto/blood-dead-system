package net.anatomyblood.client.render;

import net.anatomyblood.physics.JoltRagdoll;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.EntityRenderState;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public class CorpseRenderState extends EntityRenderState {
    public float yaw;
    public UUID playerUuid;
    public String playerName;
    public JoltRagdoll ragdoll;
    public UUID draggingPlayerUuid;
}
