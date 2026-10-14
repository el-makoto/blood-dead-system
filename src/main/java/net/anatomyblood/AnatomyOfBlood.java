package net.anatomyblood;

import net.anatomyblood.entity.ModEntities;
import net.anatomyblood.particle.ModParticles;
import net.anatomyblood.util.BloodConstants;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;

public class AnatomyOfBlood implements ModInitializer {
    public static GameRules.Key<GameRules.BooleanRule> PERSIST_CORPSE_ON_RESPAWN;
    public static GameRules.Key<GameRules.BooleanRule> DESPAWN_BLOOD;

    @Override
    public void onInitialize() {
        BloodConstants.LOGGER.info("Initializing Anatomy of Blood (anatomy_blood)...");

        PERSIST_CORPSE_ON_RESPAWN = GameRuleRegistry.register(
                "anatomyBloodPersistCorpseOnRespawn",
                GameRules.Category.PLAYER,
                GameRuleFactory.createBooleanRule(true)
        );

        DESPAWN_BLOOD = GameRuleRegistry.register(
                "anatomyBloodDespawnBlood",
                GameRules.Category.PLAYER,
                GameRuleFactory.createBooleanRule(false)
        );

        ModParticles.registerParticles();
        ModEntities.registerEntities();
        net.anatomyblood.physics.JoltManager.init();

        BloodConstants.LOGGER.info("Anatomy of Blood initialized successfully.");
    }

    public static boolean shouldPersistCorpse(ServerWorld world) {
        if (world == null || PERSIST_CORPSE_ON_RESPAWN == null) return true;
        try {
            GameRules.BooleanRule rule = world.getGameRules().get(PERSIST_CORPSE_ON_RESPAWN);
            return rule != null ? rule.get() : true;
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean shouldDespawnBlood(ServerWorld world) {
        if (world == null || DESPAWN_BLOOD == null) return false;
        try {
            GameRules.BooleanRule rule = world.getGameRules().get(DESPAWN_BLOOD);
            return rule != null ? rule.get() : false;
        } catch (Exception e) {
            return false;
        }
    }
}
