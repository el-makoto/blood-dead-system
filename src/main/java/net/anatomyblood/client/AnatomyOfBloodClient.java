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

        // Register corpse entity renderer
        EntityRendererRegistry.register(ModEntities.CORPSE, CorpseEntityRenderer::new);

        BloodConstants.LOGGER.info("Anatomy of Blood Client ready.");
    }
}
