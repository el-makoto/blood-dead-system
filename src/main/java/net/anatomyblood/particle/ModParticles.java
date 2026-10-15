package net.anatomyblood.particle;

import net.anatomyblood.util.BloodConstants;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModParticles {
    public static final SimpleParticleType BLOOD_DRIP = FabricParticleTypes.simple();
    public static final SimpleParticleType BLOOD_SPLASH = FabricParticleTypes.simple();
    public static final SimpleParticleType BLOOD_STAIN_PERMANENT = FabricParticleTypes.simple();
    public static final SimpleParticleType BLOOD_STAIN_TEMPORARY = FabricParticleTypes.simple();
    public static final SimpleParticleType BLOOD_TRAIL_PERMANENT = FabricParticleTypes.simple();
    public static final SimpleParticleType BLOOD_TRAIL_TEMPORARY = FabricParticleTypes.simple();

    public static void registerParticles() {
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(BloodConstants.MOD_ID, "blood_drip"), BLOOD_DRIP);
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(BloodConstants.MOD_ID, "blood_splash"), BLOOD_SPLASH);
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(BloodConstants.MOD_ID, "blood_stain_permanent"), BLOOD_STAIN_PERMANENT);
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(BloodConstants.MOD_ID, "blood_stain_temporary"), BLOOD_STAIN_TEMPORARY);
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(BloodConstants.MOD_ID, "blood_trail_permanent"), BLOOD_TRAIL_PERMANENT);
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(BloodConstants.MOD_ID, "blood_trail_temporary"), BLOOD_TRAIL_TEMPORARY);
        BloodConstants.LOGGER.info("Registered Anatomy of Blood particles (#F0509A).");
    }
}
