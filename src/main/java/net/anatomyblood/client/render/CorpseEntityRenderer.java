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
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
        state.tickDelta = tickDelta;
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
        float tickDelta = state.tickDelta;
        net.anatomyblood.physics.JoltRagdoll ragdoll = state.ragdoll;

        if (ragdoll == null) {
            super.render(state, matrixStack, vertexConsumers, light);
            return;
        }

        // Fetch skin textures for player profile
        UUID playerUuid = state.playerUuid != null ? state.playerUuid : Util.NIL_UUID;
        String playerName = state.playerName != null ? state.playerName : "";
        SkinTextures skinTextures = getSkinTextures(playerUuid, playerName);
        Identifier texture = skinTextures.texture();
        PlayerEntityModel model = (skinTextures.model() == SkinTextures.Model.SLIM) ? this.modelSlim : this.modelWide;

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));

        // Sub-tick interpolated world coordinates for all 7 physical nodes & joints
        Vec3d lerpOrigin = new Vec3d(state.x, state.y, state.z);
        Vec3d pelvisPos = ragdoll.getInterpolatedPelvis(tickDelta);
        Vec3d chestPos = ragdoll.getInterpolatedChest(tickDelta);
        Vec3d headPos = ragdoll.getInterpolatedHead(tickDelta);
        Vec3d rHandPos = ragdoll.getInterpolatedRightHand(tickDelta);
        Vec3d lHandPos = ragdoll.getInterpolatedLeftHand(tickDelta);
        Vec3d rFootPos = ragdoll.getInterpolatedRightFoot(tickDelta);
        Vec3d lFootPos = ragdoll.getInterpolatedLeftFoot(tickDelta);
        Vec3d rShoulder = ragdoll.getInterpolatedRightShoulder(tickDelta);
        Vec3d lShoulder = ragdoll.getInterpolatedLeftShoulder(tickDelta);
        Vec3d rHip = ragdoll.getInterpolatedRightHip(tickDelta);
        Vec3d lHip = ragdoll.getInterpolatedLeftHip(tickDelta);

        // Derive torso orthonormal basis vectors in world coordinates
        Vec3d spineVec = chestPos.subtract(pelvisPos);
        double spineLen = spineVec.length();
        Vec3d uUp = spineLen > 0.001D ? spineVec.multiply(1.0D / spineLen) : new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d uDown = uUp.multiply(-1.0D);

        float yawRad = (float) Math.toRadians(ragdoll.getInterpolatedBodyYaw(tickDelta));
        Vec3d forwardRef = new Vec3d(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));

        Vec3d uRight = forwardRef.crossProduct(uUp).normalize();
        if (uRight.lengthSquared() < 0.001D) {
            uRight = new Vec3d(0.0D, 1.0D, 0.0D).crossProduct(uUp).normalize();
            if (uRight.lengthSquared() < 0.001D) {
                uRight = new Vec3d(1.0D, 0.0D, 0.0D);
            }
        }
        Vec3d uLeft = uRight.multiply(-1.0D);
        Vec3d uFront = uUp.crossProduct(uRight).normalize();
        Vec3d uBack = uFront.multiply(-1.0D);

        // Ensure outer clothing layers are fully visible and active
        model.hat.visible = true;
        model.jacket.visible = true;
        model.rightSleeve.visible = true;
        model.leftSleeve.visible = true;
        model.rightPants.visible = true;
        model.leftPants.visible = true;

        // 1. Torso (Body + Jacket)
        Vec3d torsoOrigin = chestPos.add(uUp.multiply(0.08D));
        float torsoScaleY = (float) MathHelper.clamp(spineLen / 0.68D, 0.7D, 1.4D);
        renderOrientedPart(matrixStack, vertexConsumer, light, model.body, torsoOrigin, lerpOrigin, uLeft, uDown, uBack, torsoScaleY);

        // 2. Head (Head + Hat)
        Vec3d headVec = headPos.subtract(chestPos);
        Vec3d headUp = headVec.length() > 0.001D ? headVec.normalize() : uUp;
        Vec3d headDown = headUp.multiply(-1.0D);
        Vec3d headRight = uRight;
        Vec3d headLeft = headRight.multiply(-1.0D);
        Vec3d headFront = headUp.crossProduct(headRight).normalize();
        Vec3d headBack = headFront.multiply(-1.0D);
        Vec3d headOrigin = headPos.subtract(headUp.multiply(0.24D));
        renderOrientedPart(matrixStack, vertexConsumer, light, model.head, headOrigin, lerpOrigin, headLeft, headDown, headBack, 1.0F);

        // 3. Right Arm (Right Arm + Right Sleeve)
        Vec3d rArmVec = rHandPos.subtract(rShoulder);
        double rArmLen = rArmVec.length();
        Vec3d rArmDown = rArmLen > 0.001D ? rArmVec.multiply(1.0D / rArmLen) : uDown;
        Vec3d rArmBack = uRight.crossProduct(rArmDown).normalize();
        if (rArmBack.lengthSquared() < 0.001D) rArmBack = uBack;
        Vec3d rArmLeft = rArmDown.crossProduct(rArmBack).normalize();
        float rArmScaleY = (float) MathHelper.clamp(rArmLen / 0.62D, 0.7D, 1.4D);
        renderOrientedPart(matrixStack, vertexConsumer, light, model.rightArm, rShoulder, lerpOrigin, rArmLeft, rArmDown, rArmBack, rArmScaleY);

        // 4. Left Arm (Left Arm + Left Sleeve)
        Vec3d lArmVec = lHandPos.subtract(lShoulder);
        double lArmLen = lArmVec.length();
        Vec3d lArmDown = lArmLen > 0.001D ? lArmVec.multiply(1.0D / lArmLen) : uDown;
        Vec3d lArmBack = uRight.crossProduct(lArmDown).normalize();
        if (lArmBack.lengthSquared() < 0.001D) lArmBack = uBack;
        Vec3d lArmLeft = lArmDown.crossProduct(lArmBack).normalize();
        float lArmScaleY = (float) MathHelper.clamp(lArmLen / 0.62D, 0.7D, 1.4D);
        renderOrientedPart(matrixStack, vertexConsumer, light, model.leftArm, lShoulder, lerpOrigin, lArmLeft, lArmDown, lArmBack, lArmScaleY);

        // 5. Right Leg (Right Leg + Right Pants)
        Vec3d rLegVec = rFootPos.subtract(rHip);
        double rLegLen = rLegVec.length();
        Vec3d rLegDown = rLegLen > 0.001D ? rLegVec.multiply(1.0D / rLegLen) : uDown;
        Vec3d rLegBack = uRight.crossProduct(rLegDown).normalize();
        if (rLegBack.lengthSquared() < 0.001D) rLegBack = uBack;
        Vec3d rLegLeft = rLegDown.crossProduct(rLegBack).normalize();
        float rLegScaleY = (float) MathHelper.clamp(rLegLen / 0.72D, 0.7D, 1.4D);
        renderOrientedPart(matrixStack, vertexConsumer, light, model.rightLeg, rHip, lerpOrigin, rLegLeft, rLegDown, rLegBack, rLegScaleY);

        // 6. Left Leg (Left Leg + Left Pants)
        Vec3d lLegVec = lFootPos.subtract(lHip);
        double lLegLen = lLegVec.length();
        Vec3d lLegDown = lLegLen > 0.001D ? lLegVec.multiply(1.0D / lLegLen) : uDown;
        Vec3d lLegBack = uRight.crossProduct(lLegDown).normalize();
        if (lLegBack.lengthSquared() < 0.001D) lLegBack = uBack;
        Vec3d lLegLeft = lLegDown.crossProduct(lLegBack).normalize();
        float lLegScaleY = (float) MathHelper.clamp(lLegLen / 0.72D, 0.7D, 1.4D);
        renderOrientedPart(matrixStack, vertexConsumer, light, model.leftLeg, lHip, lerpOrigin, lLegLeft, lLegDown, lLegBack, lLegScaleY);

        super.render(state, matrixStack, vertexConsumers, light);
    }

    private void renderOrientedPart(
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            int light,
            ModelPart part,
            Vec3d originWorld,
            Vec3d lerpOrigin,
            Vec3d col0,
            Vec3d col1,
            Vec3d col2,
            float scaleY
    ) {
        matrices.push();
        // Translate from entity lerp origin to limb joint world origin
        matrices.translate(originWorld.x - lerpOrigin.x, originWorld.y - lerpOrigin.y, originWorld.z - lerpOrigin.z);

        // Orthogonal 3x3 orientation matrix converting model coordinate space to world coordinate space
        Matrix3f rotMatrix = new Matrix3f();
        rotMatrix.set(
                new Vector3f((float) col0.x, (float) col0.y, (float) col0.z),
                new Vector3f((float) col1.x, (float) col1.y, (float) col1.z),
                new Vector3f((float) col2.x, (float) col2.y, (float) col2.z)
        );
        Quaternionf q = new Quaternionf().setFromNormalized(rotMatrix);
        matrices.multiply(q);

        if (Math.abs(scaleY - 1.0F) > 0.01F) {
            matrices.scale(1.0F, scaleY, 1.0F);
        }

        part.setPivot(0.0F, 0.0F, 0.0F);
        part.setAngles(0.0F, 0.0F, 0.0F);
        part.visible = true;
        part.hidden = false;

        part.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
