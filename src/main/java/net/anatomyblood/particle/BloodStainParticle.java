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

@Environment(EnvType.CLIENT)
public class BloodStainParticle extends SpriteBillboardParticle {
    private final boolean permanent;

    public BloodStainParticle(ClientWorld world, double x, double y, double z, SpriteProvider spriteProvider, boolean permanent) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.permanent = permanent;
        this.velocityMultiplier = 0.0f;
        this.gravityStrength = 0.0f;
        this.setColor(BloodConstants.BLOOD_RED, BloodConstants.BLOOD_GREEN, BloodConstants.BLOOD_BLUE);
        this.scale = 0.6f + this.random.nextFloat() * 0.6f;
        this.maxAge = permanent ? 2000000000 : 6000;
        this.setSprite(spriteProvider);
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

        // Fade out alpha only if temporary and near end of life
        if (!permanent && this.age > this.maxAge - 100) {
            this.alpha = Math.max(0.0f, (float) (this.maxAge - this.age) / 100.0f);
        } else {
            this.alpha = 1.0f;
        }

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
