package net.anatomyblood.mixin;

import net.anatomyblood.AnatomyOfBlood;
import net.anatomyblood.entity.CorpseEntity;
import net.anatomyblood.particle.ModParticles;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "damage", at = @At("HEAD"))
    private void onPlayerDamageReleaseCorpse(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        // When player receives damage, release any corpses currently being dragged
        Box box = player.getBoundingBox().expand(32.0D);
        List<CorpseEntity> corpses = world.getEntitiesByClass(CorpseEntity.class, box, c ->
                c.getDraggingPlayerUuid().map(uuid -> uuid.equals(player.getUuid())).orElse(false)
        );
        for (CorpseEntity corpse : corpses) {
            corpse.setDraggingPlayerUuid(null);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onWoundedPlayerTick(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        if (!player.getWorld().isClient() && player.getWorld() instanceof ServerWorld serverWorld) {
            // Wounded blood trail: If health < 50% and player is moving, drop blood particles on ground
            if (player.getHealth() <= player.getMaxHealth() * 0.5F && player.getVelocity().lengthSquared() > 0.001D) {
                if (player.age % 10 == 0) {
                    boolean despawnBlood = AnatomyOfBlood.shouldDespawnBlood(serverWorld);
                    SimpleParticleType stainParticle = despawnBlood ? ModParticles.BLOOD_STAIN_TEMPORARY : ModParticles.BLOOD_STAIN_PERMANENT;

                    double x = player.getX();
                    double y = player.getY() + 0.05D;
                    double z = player.getZ();

                    if (player.isOnGround()) {
                        serverWorld.spawnParticles(stainParticle, x, y, z, 2, 0.2D, 0.0D, 0.2D, 0.0D);
                    }
                    serverWorld.spawnParticles(ModParticles.BLOOD_DRIP, x, y + 0.5D, z, 3, 0.1D, 0.1D, 0.1D, 0.05D);
                }
            }
        }
    }
}
