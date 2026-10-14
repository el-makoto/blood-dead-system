package net.anatomyblood.mixin;

import net.anatomyblood.particle.ModParticles;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "damage", at = @At("RETURN"))
    private void onDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && source.isOf(DamageTypes.FALL)) {
            LivingEntity entity = (LivingEntity) (Object) this;

            // Spawn blood particles proportional to fall damage amount (#F0509A)
            int dripCount = Math.max(6, (int) (amount * 8));
            int splashCount = Math.max(4, (int) (amount * 5));

            double x = entity.getX();
            double y = entity.getY() + (entity.getHeight() / 2.0D);
            double z = entity.getZ();

            world.spawnParticles(ModParticles.BLOOD_DRIP, x, y, z, dripCount, 0.35D, 0.35D, 0.35D, 0.15D);
            world.spawnParticles(ModParticles.BLOOD_SPLASH, x, y, z, splashCount, 0.45D, 0.45D, 0.45D, 0.25D);
        }
    }
}
