package net.anatomyblood.mixin;

import net.anatomyblood.AnatomyOfBlood;
import net.anatomyblood.entity.CorpseEntity;
import net.anatomyblood.entity.ModEntities;
import net.anatomyblood.particle.ModParticles;
import net.anatomyblood.util.BloodConstants;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onServerPlayerDeath(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        ServerWorld serverWorld = player.getServerWorld();

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        boolean despawnBlood = AnatomyOfBlood.shouldDespawnBlood(serverWorld);
        SimpleParticleType stainParticle = despawnBlood ? ModParticles.BLOOD_STAIN_TEMPORARY : ModParticles.BLOOD_STAIN_PERMANENT;

        // Spawn blood particles: stain ONLY if on ground, drips/splashes fall with gravity
        if (player.isOnGround()) {
            serverWorld.spawnParticles(stainParticle, x, y + 0.05D, z, 20, 0.5D, 0.0D, 0.5D, 0.0D);
        }
        serverWorld.spawnParticles(ModParticles.BLOOD_DRIP, x, y + 0.8D, z, 40, 0.4D, 0.4D, 0.4D, 0.15D);
        serverWorld.spawnParticles(ModParticles.BLOOD_SPLASH, x, y + 0.8D, z, 30, 0.5D, 0.5D, 0.5D, 0.25D);

        // On fall death: spawn massive blood particle burst
        if (damageSource.isOf(DamageTypes.FALL)) {
            serverWorld.spawnParticles(ModParticles.BLOOD_DRIP, x, y + (player.getHeight() / 2.0D), z, 160, 0.6D, 0.6D, 0.6D, 0.25D);
            serverWorld.spawnParticles(ModParticles.BLOOD_SPLASH, x, y + (player.getHeight() / 2.0D), z, 120, 0.8D, 0.8D, 0.8D, 0.4D);
        }

        // Spawn Corpse ragdoll entity with player's UUID, velocity and skin
        CorpseEntity corpse = new CorpseEntity(ModEntities.CORPSE, serverWorld);
        corpse.refreshPositionAndAngles(x, y, z, player.getYaw(), player.getPitch());
        corpse.setVelocity(player.getVelocity()); // Inherit momentum for ragdoll physics
        corpse.setPlayerUuid(player.getUuid());
        corpse.setPlayerName(player.getGameProfile().getName());
        boolean spawned = serverWorld.spawnEntity(corpse);

        BloodConstants.LOGGER.info("Spawned CorpseEntity for {} at ({}, {}, {}). Success: {}", player.getGameProfile().getName(), x, y, z, spawned);
    }

    @Inject(method = "copyFrom", at = @At("HEAD"))
    private void onRespawnCopyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        ServerWorld world = player.getServerWorld();
        BloodConstants.LOGGER.info("Player {} respawns. Corpse remains persistent in world.", player.getGameProfile().getName());
    }
}
