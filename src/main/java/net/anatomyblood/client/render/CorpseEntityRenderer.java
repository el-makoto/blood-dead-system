package net.anatomyblood.client.render;

import com.mojang.authlib.GameProfile;
import net.anatomyblood.entity.CorpseEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public class CorpseEntityRenderer extends EntityRenderer<CorpseEntity, CorpseRenderState> {
    private final PlayerEntityModel modelWide;
    private final PlayerEntityModel modelSlim;
    private final PlayerEntityRenderState playerRenderState = new PlayerEntityRenderState();
    private static final Map<UUID, SkinTextures> SKIN_CACHE = new ConcurrentHashMap<>();

    public CorpseEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.modelWide = new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false);
        this.modelSlim = new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER_SLIM), true);
        this.shadowRadius = 0.5f;

        // Force initialize playerRenderState boolean flags for outer skin layers
        this.playerRenderState.hatVisible = true;
        this.playerRenderState.jacketVisible = true;
        this.playerRenderState.leftSleeveVisible = true;
        this.playerRenderState.rightSleeveVisible = true;
        this.playerRenderState.leftPantsLegVisible = true;
        this.playerRenderState.rightPantsLegVisible = true;
    }

    @Override
    public CorpseRenderState createRenderState() {
        return new CorpseRenderState();
    }

    @Override
    public void updateRenderState(CorpseEntity entity, CorpseRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.yaw = entity.getYaw();
        state.playerUuid = entity.getPlayerUuid().orElse(Util.NIL_UUID);
        state.playerName = entity.getPlayerName();
        state.ragdoll = entity.getRagdoll();
        state.draggingPlayerUuid = entity.getDraggingPlayerUuid().orElse(null);
    }

    private SkinTextures getSkinTextures(UUID uuid, String name) {
        if (uuid == null || uuid.equals(Util.NIL_UUID)) {
            return DefaultSkinHelper.getSkinTextures(Util.NIL_UUID);
        }

        SkinTextures cached = SKIN_CACHE.get(uuid);
        if (cached != null) {
            return cached;
        }

        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (networkHandler != null) {
            PlayerListEntry entry = networkHandler.getPlayerListEntry(uuid);
            if (entry != null) {
                SkinTextures textures = entry.getSkinTextures();
                if (textures != null) {
                    SKIN_CACHE.put(uuid, textures);
                    return textures;
                }
            }
        }

        GameProfile profile = new GameProfile(uuid, name);
        SkinTextures textures = MinecraftClient.getInstance().getSkinProvider().getSkinTextures(profile);
        if (textures != null) {
            MinecraftClient.getInstance().getSkinProvider().fetchSkinTextures(profile).thenAccept(optionalSkin -> {
                optionalSkin.ifPresent(s -> SKIN_CACHE.put(uuid, s));
            });
            return textures;
        }

        return DefaultSkinHelper.getSkinTextures(uuid);
    }

    @Override
    public void render(CorpseRenderState state, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light) {
        matrixStack.push();

        // Position model lying flat horizontal on the ground imitating an inert corpse
        matrixStack.translate(0.0D, 0.2D, 0.0D);
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - state.yaw));
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
        matrixStack.translate(0.0D, -0.6D, 0.1D);

        // Fetch skin textures for player profile
        UUID playerUuid = state.playerUuid != null ? state.playerUuid : Util.NIL_UUID;
        String playerName = state.playerName != null ? state.playerName : "";

        SkinTextures skinTextures = getSkinTextures(playerUuid, playerName);
        Identifier texture = skinTextures.texture();
        PlayerEntityModel model = (skinTextures.model() == SkinTextures.Model.SLIM) ? this.modelSlim : this.modelWide;

        // Force model parts visibility
        model.head.visible = true;
        model.hat.visible = true;
        model.body.visible = true;
        model.rightArm.visible = true;
        model.leftArm.visible = true;
        model.rightLeg.visible = true;
        model.leftLeg.visible = true;

        // Dynamic Jolt Ragdoll joint posture angles for fallen corpse
        net.anatomyblood.physics.JoltRagdoll ragdoll = state.ragdoll;
        if (ragdoll != null) {
            model.head.pitch = ragdoll.headPitch;
            model.head.yaw = ragdoll.headYaw;
            model.head.roll = ragdoll.headRoll;

            model.rightArm.pitch = ragdoll.rightArmPitch;
            model.rightArm.yaw = ragdoll.rightArmYaw;
            model.rightArm.roll = ragdoll.rightArmRoll;

            model.leftArm.pitch = ragdoll.leftArmPitch;
            model.leftArm.yaw = ragdoll.leftArmYaw;
            model.leftArm.roll = ragdoll.leftArmRoll;

            model.rightLeg.pitch = ragdoll.rightLegPitch;
            model.rightLeg.yaw = ragdoll.rightLegYaw;
            model.rightLeg.roll = ragdoll.rightLegRoll;

            model.leftLeg.pitch = ragdoll.leftLegPitch;
            model.leftLeg.yaw = ragdoll.leftLegYaw;
            model.leftLeg.roll = ragdoll.leftLegRoll;

            model.body.pitch = ragdoll.bodyPitch;
            model.body.yaw = ragdoll.bodyYaw;
            model.body.roll = ragdoll.bodyRoll;
        }

        // Force copy transforms to outer skin layers (hat, jacket, sleeves, pants)
        // Bypasses any pose resets from animation mods (Not Enough Animations / EMF)
        model.hat.copyTransform(model.head);
        model.jacket.copyTransform(model.body);
        model.rightSleeve.copyTransform(model.rightArm);
        model.leftSleeve.copyTransform(model.leftArm);
        model.rightPants.copyTransform(model.rightLeg);
        model.leftPants.copyTransform(model.leftLeg);

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        model.render(matrixStack, vertexConsumer, light, OverlayTexture.DEFAULT_UV);

        matrixStack.pop();
        super.render(state, matrixStack, vertexConsumers, light);
    }
}
