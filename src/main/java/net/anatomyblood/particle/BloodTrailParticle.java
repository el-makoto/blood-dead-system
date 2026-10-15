package net.anatomyblood.particle;

import net.anatomyblood.util.BloodConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class BloodTrailParticle extends BloodStainParticle {
    private final float forwardX;
    private final float forwardZ;
    private final float rightX;
    private final float rightZ;

    // Redstone line width (~0.22m wide) and segment length (~0.30m long)
    private final float halfWidth;
    private final float halfLength;

    // Subtle 3D non-planar height perturbations per corner
    private final float h0, h1, h2, h3;

    public BloodTrailParticle(ClientWorld world, double x, double y, double z,
                              double dirX, double dirY, double dirZ,
                              SpriteProvider spriteProvider, boolean permanent) {
        super(world, x, y, z, spriteProvider, permanent);

        // Subtle wet blood transparency
        this.currentAlpha = 0.85f;
        this.scale = 0.25f;

        // Calculate dragging orientation
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len > 0.005D) {
            this.forwardX = (float) (dirX / len);
            this.forwardZ = (float) (dirZ / len);
        } else {
            float angle = this.random.nextFloat() * ((float) Math.PI * 2.0f);
            this.forwardX = MathHelper.cos(angle);
            this.forwardZ = MathHelper.sin(angle);
        }
        this.rightX = -this.forwardZ;
        this.rightZ = this.forwardX;

        // Exact redstone wire width (halfWidth ~ 0.11m = 0.22m wide)
        this.halfWidth = 0.11f;
        this.halfLength = 0.15f;

        // Subtle 3D non-planar height perturbations per corner (+0.2mm to +1.8mm)
        this.h0 = (this.random.nextFloat() * 0.0012f);
        this.h1 = 0.0006f + (this.random.nextFloat() * 0.0012f);
        this.h2 = 0.0008f + (this.random.nextFloat() * 0.0012f);
        this.h3 = 0.0002f + (this.random.nextFloat() * 0.0008f);
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        Vec3d cameraPos = camera.getPos();
        float cx = (float) (MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cameraPos.getX());
        // Just slightly above the block top surface (+3.5mm) to completely avoid Z-fighting without floating
        float cy = (float) (MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cameraPos.getY()) + 0.0035f;
        float cz = (float) (MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cameraPos.getZ());

        float uMin = this.getMinU();
        float uMax = this.getMaxU();
        float vMin = this.getMinV();
        float vMax = this.getMaxV();
        int light = this.getBrightness(tickDelta);

        // Fade out alpha if temporary and near end of life
        float ageAlpha = (!this.dead && this.age > this.maxAge - 100)
                ? Math.max(0.0f, (float) (this.maxAge - this.age) / 100.0f)
                : 1.0f;
        this.alpha = this.currentAlpha * ageAlpha;

        float fxL = this.forwardX * this.halfLength;
        float fzL = this.forwardZ * this.halfLength;
        float rxW = this.rightX * this.halfWidth;
        float rzW = this.rightZ * this.halfWidth;

        // 4 corners of the redstone-width line
        // Corner 0: rear-left
        float x0 = cx - fxL - rxW;
        float y0 = cy + this.h0;
        float z0 = cz - fzL - rzW;

        // Corner 1: front-left
        float x1 = cx + fxL - rxW;
        float y1 = cy + this.h1;
        float z1 = cz + fzL - rzW;

        // Corner 2: front-right
        float x2 = cx + fxL + rxW;
        float y2 = cy + this.h2;
        float z2 = cz + fzL + rzW;

        // Corner 3: rear-right
        float x3 = cx - fxL + rxW;
        float y3 = cy + this.h3;
        float z3 = cz - fzL + rzW;

        // 1. TOP FACE: Counter-Clockwise (Normal points UP +Y toward sky and camera)
        vertexConsumer.vertex(x0, y0, z0).texture(uMin, vMin).color(this.red, this.green, this.blue, this.alpha).light(light);
        vertexConsumer.vertex(x3, y3, z3).texture(uMax, vMin).color(this.red, this.green, this.blue, this.alpha).light(light);
        vertexConsumer.vertex(x2, y2, z2).texture(uMax, vMax).color(this.red, this.green, this.blue, this.alpha).light(light);
        vertexConsumer.vertex(x1, y1, z1).texture(uMin, vMax).color(this.red, this.green, this.blue, this.alpha).light(light);

        // 2. REVERSE FACE: Double-sided rendering to guarantee zero culling from any angle
        vertexConsumer.vertex(x1, y1, z1).texture(uMin, vMax).color(this.red, this.green, this.blue, this.alpha).light(light);
        vertexConsumer.vertex(x2, y2, z2).texture(uMax, vMax).color(this.red, this.green, this.blue, this.alpha).light(light);
        vertexConsumer.vertex(x3, y3, z3).texture(uMax, vMin).color(this.red, this.green, this.blue, this.alpha).light(light);
        vertexConsumer.vertex(x0, y0, z0).texture(uMin, vMin).color(this.red, this.green, this.blue, this.alpha).light(light);
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
        public Particle createParticle(SimpleParticleType type, ClientWorld world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new BloodTrailParticle(world, x, y, z, velocityX, velocityY, velocityZ, this.spriteProvider, this.permanent);
        }
    }
}
