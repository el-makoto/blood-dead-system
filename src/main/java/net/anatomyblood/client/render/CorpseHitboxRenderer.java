package net.anatomyblood.client.render;

import net.anatomyblood.entity.CorpseEntity;
import net.anatomyblood.physics.JoltRagdoll;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class CorpseHitboxRenderer {

    public static void renderPartHitboxes(CorpseEntity corpse, MatrixStack matrices, VertexConsumer vertices, float tickDelta) {
        JoltRagdoll ragdoll = corpse.getRagdoll();
        if (ragdoll == null) return;

        double lerpX = MathHelper.lerp(tickDelta, corpse.lastRenderX, corpse.getX());
        double lerpY = MathHelper.lerp(tickDelta, corpse.lastRenderY, corpse.getY());
        double lerpZ = MathHelper.lerp(tickDelta, corpse.lastRenderZ, corpse.getZ());
        Vec3d lerpOrigin = new Vec3d(lerpX, lerpY, lerpZ);

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

        // Derive torso orthonormal basis vectors
        Vec3d spineVec = chestPos.subtract(pelvisPos);
        double spineLen = spineVec.length();
        Vec3d uUp = spineLen > 0.001D ? spineVec.multiply(1.0D / spineLen) : new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d uDown = uUp.multiply(-1.0D);

        Vec3d uRight = net.anatomyblood.physics.JoltRagdoll.computeTorsoRight(spineVec, ragdoll.getInterpolatedBodyYaw(tickDelta));
        Vec3d uLeft = uRight.multiply(-1.0D);
        Vec3d uFront = uUp.crossProduct(uRight).normalize();
        Vec3d uBack = uFront.multiply(-1.0D);

        // 1. Torso Hitbox (Green) - Exactly 8x12x4 pixels (0.50m x 0.75m x 0.25m)
        Vec3d torsoOrigin = chestPos.add(uUp.multiply(0.08D));
        renderOrientedWireframe(matrices, vertices, torsoOrigin, lerpOrigin, uLeft, uDown, uBack,
                new Box(-0.25D, 0.0D, -0.125D, 0.25D, spineLen + 0.08D, 0.125D), 0.0F, 1.0F, 0.0F, 1.0F);

        // 2. Head Hitbox (White) - Exactly 8x8x8 pixels (0.50m x 0.50m x 0.50m)
        Vec3d headVec = headPos.subtract(chestPos);
        Vec3d headUp = headVec.length() > 0.001D ? headVec.normalize() : uUp;
        Vec3d headDown = headUp.multiply(-1.0D);
        Vec3d headRight = uRight;
        Vec3d headLeft = headRight.multiply(-1.0D);
        Vec3d headFront = headUp.crossProduct(headRight).normalize();
        Vec3d headBack = headFront.multiply(-1.0D);
        Vec3d headOrigin = headPos.subtract(headUp.multiply(0.24D));
        renderOrientedWireframe(matrices, vertices, headOrigin, lerpOrigin, headLeft, headDown, headBack,
                new Box(-0.25D, -0.25D, -0.25D, 0.25D, 0.25D, 0.25D), 1.0F, 1.0F, 1.0F, 1.0F);

        // 3. Right Arm Hitbox (Orange) - Exactly 4x12x4 pixels (0.25m x 0.75m x 0.25m)
        Vec3d rArmVec = rHandPos.subtract(rShoulder);
        double rArmLen = rArmVec.length();
        Vec3d rArmDown = rArmLen > 0.001D ? rArmVec.multiply(1.0D / rArmLen) : uDown;
        Vec3d rArmLeft = uLeft.subtract(rArmDown.multiply(uLeft.dotProduct(rArmDown)));
        if (rArmLeft.lengthSquared() < 0.001D) {
            rArmLeft = uBack.crossProduct(rArmDown);
        }
        rArmLeft = rArmLeft.lengthSquared() > 0.001D ? rArmLeft.normalize() : uLeft;
        Vec3d rArmBack = rArmLeft.crossProduct(rArmDown).normalize();
        renderOrientedWireframe(matrices, vertices, rShoulder, lerpOrigin, rArmLeft, rArmDown, rArmBack,
                new Box(-0.11D, -0.04D, -0.125D, 0.11D, rArmLen, 0.125D), 1.0F, 0.4F, 0.0F, 1.0F);

        // 4. Left Arm Hitbox (Cyan) - Exactly 4x12x4 pixels (0.25m x 0.75m x 0.25m)
        Vec3d lArmVec = lHandPos.subtract(lShoulder);
        double lArmLen = lArmVec.length();
        Vec3d lArmDown = lArmLen > 0.001D ? lArmVec.multiply(1.0D / lArmLen) : uDown;
        Vec3d lArmLeft = uLeft.subtract(lArmDown.multiply(uLeft.dotProduct(lArmDown)));
        if (lArmLeft.lengthSquared() < 0.001D) {
            lArmLeft = uBack.crossProduct(lArmDown);
        }
        lArmLeft = lArmLeft.lengthSquared() > 0.001D ? lArmLeft.normalize() : uLeft;
        Vec3d lArmBack = lArmLeft.crossProduct(lArmDown).normalize();
        renderOrientedWireframe(matrices, vertices, lShoulder, lerpOrigin, lArmLeft, lArmDown, lArmBack,
                new Box(-0.11D, -0.04D, -0.125D, 0.11D, lArmLen, 0.125D), 0.0F, 0.7F, 1.0F, 1.0F);

        // 5. Right Leg Hitbox (Yellow) - Exactly 4x12x4 pixels (0.25m x 0.75m x 0.25m)
        Vec3d rLegVec = rFootPos.subtract(rHip);
        double rLegLen = rLegVec.length();
        Vec3d rLegDown = rLegLen > 0.001D ? rLegVec.multiply(1.0D / rLegLen) : uDown;
        Vec3d rLegLeft = uLeft.subtract(rLegDown.multiply(uLeft.dotProduct(rLegDown)));
        if (rLegLeft.lengthSquared() < 0.001D) {
            rLegLeft = uBack.crossProduct(rLegDown);
        }
        rLegLeft = rLegLeft.lengthSquared() > 0.001D ? rLegLeft.normalize() : uLeft;
        Vec3d rLegBack = rLegLeft.crossProduct(rLegDown).normalize();
        renderOrientedWireframe(matrices, vertices, rHip, lerpOrigin, rLegLeft, rLegDown, rLegBack,
                new Box(-0.125D, 0.0D, -0.125D, 0.125D, rLegLen, 0.125D), 1.0F, 1.0F, 0.0F, 1.0F);

        // 6. Left Leg Hitbox (Magenta) - Exactly 4x12x4 pixels (0.25m x 0.75m x 0.25m)
        Vec3d lLegVec = lFootPos.subtract(lHip);
        double lLegLen = lLegVec.length();
        Vec3d lLegDown = lLegLen > 0.001D ? lLegVec.multiply(1.0D / lLegLen) : uDown;
        Vec3d lLegLeft = uLeft.subtract(lLegDown.multiply(uLeft.dotProduct(lLegDown)));
        if (lLegLeft.lengthSquared() < 0.001D) {
            lLegLeft = uBack.crossProduct(lLegDown);
        }
        lLegLeft = lLegLeft.lengthSquared() > 0.001D ? lLegLeft.normalize() : uLeft;
        Vec3d lLegBack = lLegLeft.crossProduct(lLegDown).normalize();
        renderOrientedWireframe(matrices, vertices, lHip, lerpOrigin, lLegLeft, lLegDown, lLegBack,
                new Box(-0.125D, 0.0D, -0.125D, 0.125D, lLegLen, 0.125D), 1.0F, 0.0F, 1.0F, 1.0F);

        // 7. Head look direction vector (Blue)
        Vec3d lookDir = headFront.multiply(0.4D);
        VertexRendering.drawVector(
                matrices,
                vertices,
                new Vector3f((float) (headPos.x - lerpX), (float) (headPos.y - lerpY), (float) (headPos.z - lerpZ)),
                lookDir,
                0xFF0000FF
        );
    }

    private static void renderOrientedWireframe(
            MatrixStack matrices,
            VertexConsumer vertices,
            Vec3d originWorld,
            Vec3d lerpOrigin,
            Vec3d col0,
            Vec3d col1,
            Vec3d col2,
            Box localBox,
            float r, float g, float b, float a
    ) {
        matrices.push();
        matrices.translate(originWorld.x - lerpOrigin.x, originWorld.y - lerpOrigin.y, originWorld.z - lerpOrigin.z);

        Matrix3f rotMatrix = new Matrix3f();
        rotMatrix.set(
                new Vector3f((float) col0.x, (float) col0.y, (float) col0.z),
                new Vector3f((float) col1.x, (float) col1.y, (float) col1.z),
                new Vector3f((float) col2.x, (float) col2.y, (float) col2.z)
        );
        Quaternionf q = new Quaternionf().setFromNormalized(rotMatrix);
        matrices.multiply(q);

        VertexRendering.drawBox(matrices, vertices, localBox, r, g, b, a);
        matrices.pop();
    }
}
