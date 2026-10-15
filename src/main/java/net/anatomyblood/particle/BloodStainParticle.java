package net.anatomyblood.particle;

import net.anatomyblood.util.BloodConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public class BloodStainParticle extends SpriteBillboardParticle {
    public static final Set<BloodStainParticle> ACTIVE_STAINS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final boolean permanent;

    protected float currentAlpha = 0.88f;

    public BloodStainParticle(ClientWorld world, double x, double y, double z, SpriteProvider spriteProvider, boolean permanent) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.permanent = permanent;
        this.velocityMultiplier = 0.0f;
        this.gravityStrength = 0.0f;
        this.setColor(BloodConstants.BLOOD_RED, BloodConstants.BLOOD_GREEN, BloodConstants.BLOOD_BLUE);
        this.scale = 0.6f + this.random.nextFloat() * 0.6f;
        this.maxAge = permanent ? 2000000000 : 6000;
        this.currentAlpha = 0.88f;
        this.setSprite(spriteProvider);
        ACTIVE_STAINS.add(this);
    }

    public static void clearAllStains() {
        for (BloodStainParticle p : ACTIVE_STAINS) {
            p.markDead();
        }
        ACTIVE_STAINS.clear();
    }

    /**
     * Decreases the opacity of the stain by a brush stroke.
     * Returns true if the stain was completely cleaned and marked dead.
     */
    public boolean brushClean(float amount) {
        this.currentAlpha -= amount;
        if (this.currentAlpha <= 0.08f) {
            this.markDead();
            ACTIVE_STAINS.remove(this);
            return true;
        }
        return false;
    }

    public static boolean hasStainNear(double x, double y, double z, double radius) {
        double rSq = radius * radius;
        for (BloodStainParticle p : ACTIVE_STAINS) {
            if (p.isAlive()) {
                double dx = p.x - x;
                double dy = p.y - y;
                double dz = p.z - z;
                if (dx * dx + dy * dy + dz * dz <= rSq) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int cleanBloodNear(double x, double y, double z, double radius, float amount) {
        double rSq = radius * radius;
        int cleaned = 0;
        for (BloodStainParticle p : ACTIVE_STAINS) {
            if (p.isAlive()) {
                double dx = p.x - x;
                double dy = p.y - y;
                double dz = p.z - z;
                if (dx * dx + dy * dy + dz * dz <= rSq) {
                    p.brushClean(amount);
                    cleaned++;
                }
            }
        }
        return cleaned;
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        Vec3d cameraPos = camera.getPos();
        float x = (float) (MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cameraPos.getX());
        float y = (float) (MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cameraPos.getY()) + 0.015f;
        float z = (float) (MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cameraPos.getZ());

        float size = this.getSize(tickDelta);
        float uMin = this.getMinU();
        float uMax = this.getMaxU();
        float vMin = this.getMinV();
        float vMax = this.getMaxV();
        int light = this.getBrightness(tickDelta);

        // Fade out alpha if temporary and near end of life
        float ageAlpha = (!permanent && this.age > this.maxAge - 100)
                ? Math.max(0.0f, (float) (this.maxAge - this.age) / 100.0f)
                : 1.0f;
        this.alpha = this.currentAlpha * ageAlpha;

        // Lay flat on top face of the block
        vertexConsumer.vertex(x - size, y, z - size).texture(uMin, vMin).color(this.red, this.green, this.blue, this.alpha).light(light);
        vertexConsumer.vertex(x - size, y, z + size).texture(uMin, vMax).color(this.red, this.green, this.blue, this.alpha).light(light);
        vertexConsumer.vertex(x + size, y, z + size).texture(uMax, vMax).color(this.red, this.green, this.blue, this.alpha).light(light);
        vertexConsumer.vertex(x + size, y, z - size).texture(uMax, vMin).color(this.red, this.green, this.blue, this.alpha).light(light);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;
        private final boolean permanent;

        public Factory(SpriteProvider spriteProvider, boolean permanent) {
            this.spriteProvider = spriteProvider;
            this.permanent = permanent;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
            return new BloodStainParticle(world, x, y, z, this.spriteProvider, this.permanent);
        }
    }
}
