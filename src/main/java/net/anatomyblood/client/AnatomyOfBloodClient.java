package net.anatomyblood.client;

import net.anatomyblood.client.render.CorpseEntityRenderer;
import net.anatomyblood.entity.ModEntities;
import net.anatomyblood.particle.BloodDripParticle;
import net.anatomyblood.particle.BloodSplashParticle;
import net.anatomyblood.particle.BloodStainParticle;
import net.anatomyblood.particle.ModParticles;
import net.anatomyblood.util.BloodConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

@Environment(EnvType.CLIENT)
public class AnatomyOfBloodClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BloodConstants.LOGGER.info("Initializing Anatomy of Blood Client...");

        // Register particle factories
        ParticleFactoryRegistry.getInstance().register(ModParticles.BLOOD_DRIP, BloodDripParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.BLOOD_SPLASH, BloodSplashParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.BLOOD_STAIN_PERMANENT, provider -> new BloodStainParticle.Factory(provider, true));
        ParticleFactoryRegistry.getInstance().register(ModParticles.BLOOD_STAIN_TEMPORARY, provider -> new BloodStainParticle.Factory(provider, false));
        ParticleFactoryRegistry.getInstance().register(ModParticles.BLOOD_TRAIL_PERMANENT, provider -> new net.anatomyblood.particle.BloodTrailParticle.Factory(provider, true));
        ParticleFactoryRegistry.getInstance().register(ModParticles.BLOOD_TRAIL_TEMPORARY, provider -> new net.anatomyblood.particle.BloodTrailParticle.Factory(provider, false));

        // Register corpse entity renderer
        EntityRendererRegistry.register(ModEntities.CORPSE, CorpseEntityRenderer::new);

        // Register packet receiver to clear blood on command
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                net.anatomyblood.network.ClearBloodPayload.ID,
                (payload, context) -> context.client().execute(BloodStainParticle::clearAllStains)
        );

        // Register packet receiver for synchronized brush cleaning
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                net.anatomyblood.network.BrushBloodS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    BloodStainParticle.cleanBloodNear(payload.x(), payload.y(), payload.z(), 1.0D, 0.28f);
                })
        );

        // Register right-click brush cleaning interaction
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) {
                net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
                if (stack.isOf(net.minecraft.item.Items.BRUSH) && !player.getItemCooldownManager().isCoolingDown(stack)) {
                    net.minecraft.util.math.Vec3d hitPos = hitResult.getPos();
                    if (BloodStainParticle.hasStainNear(hitPos.x, hitPos.y, hitPos.z, 1.0D)) {
                        BloodStainParticle.cleanBloodNear(hitPos.x, hitPos.y, hitPos.z, 1.0D, 0.28f);
                        player.swingHand(hand);
                        net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(
                                net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, 1.0f, 1.0f)
                        );
                        world.addParticle(ModParticles.BLOOD_SPLASH, hitPos.x, hitPos.y + 0.02D, hitPos.z, 0.0D, 0.02D, 0.0D);
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new net.anatomyblood.network.BrushBloodC2SPayload(hitPos.x, hitPos.y, hitPos.z, hand == net.minecraft.util.Hand.MAIN_HAND)
                        );
                        player.getItemCooldownManager().set(stack, 5);
                        return net.minecraft.util.ActionResult.SUCCESS;
                    }
                }
            }
            return net.minecraft.util.ActionResult.PASS;
        });

        BloodConstants.LOGGER.info("Anatomy of Blood Client ready.");
    }
}
