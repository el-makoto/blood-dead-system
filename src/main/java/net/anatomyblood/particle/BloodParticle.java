package net.anatomyblood.particle;

import net.anatomyblood.util.BloodConstants;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;

public class BloodParticle extends SpriteBillboardParticle {

    public BloodParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteProvider spriteProvider, boolean splash) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        
        this.velocityMultiplier = 0.88f;
        this.velocityX = velocityX + (this.random.nextDouble() - 0.5D) * 0.1D;
        this.velocityY = velocityY + (splash ? (this.random.nextDouble() * 0.15D) : -0.02D);
        this.velocityZ = velocityZ + (this.random.nextDouble() - 0.5D) * 0.1D;

        // Set Blood Color #F0509A
        this.setColor(BloodConstants.BLOOD_RED, BloodConstants.BLOOD_GREEN, BloodConstants.BLOOD_BLUE);

        this.scale = splash ? (0.18f + this.random.nextFloat() * 0.12f) : (0.12f + this.random.nextFloat() * 0.08f);
        this.maxAge = 25 + this.random.nextInt(25);
        this.gravityStrength = splash ? 0.9f : 0.6f;

        this.setSprite(spriteProvider);
    }

    @Override
    public void tick() {
        super.tick();
        // Fade out alpha as particle ages
        this.alpha = Math.max(0.0f, 1.0f - ((float) this.age / (float) this.maxAge));
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }
}
