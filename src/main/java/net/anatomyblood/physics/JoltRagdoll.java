package net.anatomyblood.physics;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class JoltRagdoll {
    // Ragdoll bone angles (in radians) for player model parts
    public float headPitch = 0.2F;
    public float headYaw = 0.15F;
    public float headRoll = 0.0F;

    public float rightArmPitch = 0.35F;
    public float rightArmYaw = 0.4F;
    public float rightArmRoll = 0.15F;

    public float leftArmPitch = -0.25F;
    public float leftArmYaw = -0.45F;
    public float leftArmRoll = -0.15F;

    public float rightLegPitch = 0.1F;
    public float rightLegYaw = 0.2F;
    public float rightLegRoll = 0.08F;

    public float leftLegPitch = -0.1F;
    public float leftLegYaw = -0.25F;
    public float leftLegRoll = -0.08F;

    public float bodyPitch = 0.0F;
    public float bodyYaw = 0.0F;
    public float bodyRoll = 0.0F;

    private int ticksAlive = 0;

    public JoltRagdoll(Vec3d initialVelocity, float yaw) {
        JoltManager.init();
    }

    public void tick(boolean isOnGround, Vec3d velocity) {
        ticksAlive++;
        double speed = velocity != null ? velocity.horizontalLength() : 0.0D;

        if (speed > 0.02D) {
            // Dragging / Motion Ragdoll Swing Physics
            float swing = MathHelper.sin(ticksAlive * 0.4F) * (float) (speed * 1.5D);
            float swingCos = MathHelper.cos(ticksAlive * 0.4F) * (float) (speed * 1.5D);

            rightArmPitch = 0.35F + swing;
            leftArmPitch = -0.25F - swing;
            rightLegPitch = 0.1F - swingCos;
            leftLegPitch = -0.1F + swingCos;

            headPitch = 0.2F + swing * 0.3F;
            headYaw = 0.15F + swingCos * 0.3F;
            bodyRoll = swing * 0.2F;
        } else {
            // Settled resting ragdoll pose
            headPitch = lerp(headPitch, 0.25F, 0.1F);
            headYaw = lerp(headYaw, 0.15F, 0.1F);

            rightArmPitch = lerp(rightArmPitch, 0.35F, 0.1F);
            rightArmYaw = lerp(rightArmYaw, 0.4F, 0.1F);

            leftArmPitch = lerp(leftArmPitch, -0.25F, 0.1F);
            leftArmYaw = lerp(leftArmYaw, -0.45F, 0.1F);

            rightLegPitch = lerp(rightLegPitch, 0.1F, 0.1F);
            rightLegYaw = lerp(rightLegYaw, 0.2F, 0.1F);

            leftLegPitch = lerp(leftLegPitch, -0.1F, 0.1F);
            leftLegYaw = lerp(leftLegYaw, -0.25F, 0.1F);

            bodyRoll = lerp(bodyRoll, 0.0F, 0.1F);
        }
    }

    public void tick(boolean isOnGround) {
        tick(isOnGround, Vec3d.ZERO);
    }

    private float lerp(float start, float target, float delta) {
        return start + delta * (target - start);
    }
}
