package net.anatomyblood.entity;

import net.anatomyblood.util.BloodConstants;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModEntities {
    public static final RegistryKey<EntityType<?>> CORPSE_KEY = RegistryKey.of(
            RegistryKeys.ENTITY_TYPE,
            Identifier.of(BloodConstants.MOD_ID, "corpse")
    );

    public static final EntityType<CorpseEntity> CORPSE = Registry.register(
            Registries.ENTITY_TYPE,
            CORPSE_KEY,
            EntityType.Builder.<CorpseEntity>create(CorpseEntity::new, SpawnGroup.MISC)
                    .dimensions(1.4f, 0.6f)
                    .maxTrackingRange(256)
                    .trackingTickInterval(1)
                    .build(CORPSE_KEY)
    );

    public static void registerEntities() {
        BloodConstants.LOGGER.info("Registered Corpse entity for Anatomy of Blood.");
    }
}
