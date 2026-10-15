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

        // Register custom network payloads
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(
                net.anatomyblood.network.ClearBloodPayload.ID,
                net.anatomyblood.network.ClearBloodPayload.CODEC
        );
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(
                net.anatomyblood.network.BrushBloodC2SPayload.ID,
                net.anatomyblood.network.BrushBloodC2SPayload.CODEC
        );
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(
                net.anatomyblood.network.BrushBloodS2CPayload.ID,
                net.anatomyblood.network.BrushBloodS2CPayload.CODEC
        );

        // Server receiver for brush cleaning
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                net.anatomyblood.network.BrushBloodC2SPayload.ID,
                (payload, context) -> {
                    net.minecraft.server.network.ServerPlayerEntity player = context.player();
                    ServerWorld world = player.getServerWorld();
                    context.server().execute(() -> {
                        double px = payload.x();
                        double py = payload.y();
                        double pz = payload.z();

                        if (player.squaredDistanceTo(px, py, pz) > 36.0D) return;

                        net.minecraft.util.Hand hand = payload.mainHand() ? net.minecraft.util.Hand.MAIN_HAND : net.minecraft.util.Hand.OFF_HAND;
                        net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
                        if (!stack.isOf(net.minecraft.item.Items.BRUSH)) return;

                        // Damage brush in survival
                        if (!player.isCreative()) {
                            net.minecraft.entity.EquipmentSlot slot = payload.mainHand() ? net.minecraft.entity.EquipmentSlot.MAINHAND : net.minecraft.entity.EquipmentSlot.OFFHAND;
                            stack.damage(1, world, player, item -> player.sendEquipmentBreakStatus(item, slot));
                        }

                        player.getItemCooldownManager().set(stack, 5);

                        world.playSound(null, px, py, pz, net.minecraft.sound.SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);

                        net.anatomyblood.network.BrushBloodS2CPayload s2c = new net.anatomyblood.network.BrushBloodS2CPayload(px, py, pz);
                        for (net.minecraft.server.network.ServerPlayerEntity other : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(world, new net.minecraft.util.math.BlockPos((int) px, (int) py, (int) pz))) {
                            if (other != player) {
                                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(other, s2c);
                            }
                        }
                    });
                }
        );

        // Seamless floor-click interaction: If player right clicks near a corpse on the floor with empty hand, grab it; if already dragging, drop it.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand != net.minecraft.util.Hand.MAIN_HAND || !player.getMainHandStack().isEmpty()) {
                return net.minecraft.util.ActionResult.PASS;
            }

            // If player is already dragging a corpse, clicking anywhere on ground drops it
            net.minecraft.util.math.Box dragSearchBox = player.getBoundingBox().expand(8.0D);
            java.util.List<net.anatomyblood.entity.CorpseEntity> draggingCorpses = world.getEntitiesByClass(
                    net.anatomyblood.entity.CorpseEntity.class, dragSearchBox,
                    c -> c.getDraggingPlayerUuid().map(uuid -> uuid.equals(player.getUuid())).orElse(false)
            );
            if (!draggingCorpses.isEmpty()) {
                return draggingCorpses.get(0).interactWithPart(draggingCorpses.get(0).torsoPart, player, hand);
            }

            // Ray from player crosshair to click point on floor
            net.minecraft.util.math.Vec3d eyePos = player.getEyePos();
            net.minecraft.util.math.Vec3d lookDir = player.getRotationVec(1.0F).normalize();
            net.minecraft.util.math.Vec3d clickPos = hitResult.getPos();

            // Search for nearby corpses within interaction reach
            net.minecraft.util.math.Box searchBox = player.getBoundingBox().expand(5.0D);
            java.util.List<net.anatomyblood.entity.CorpseEntity> nearbyCorpses = world.getEntitiesByClass(
                    net.anatomyblood.entity.CorpseEntity.class, searchBox,
                    c -> c.getDraggingPlayerUuid().isEmpty()
            );

            if (nearbyCorpses.isEmpty()) {
                return net.minecraft.util.ActionResult.PASS;
            }

            net.anatomyblood.entity.CorpseEntity closestCorpse = null;
            net.anatomyblood.entity.CorpsePart targetPart = null;
            double bestScore = Double.MAX_VALUE;

            for (net.anatomyblood.entity.CorpseEntity corpse : nearbyCorpses) {
                // Find limb aimed at by crosshair ray
                net.anatomyblood.entity.CorpsePart part = corpse.findTargetedPart(player, eyePos, lookDir);
                net.minecraft.util.math.Vec3d partCenter = corpse.getPartCenter(part);

                // Distance from click point on the floor to the targeted limb
                double dClickSq = clickPos.squaredDistanceTo(partCenter);
                if (dClickSq < 1.4D * 1.4D && dClickSq < bestScore) {
                    bestScore = dClickSq;
                    closestCorpse = corpse;
                    targetPart = part;
                }
            }

            if (closestCorpse != null && targetPart != null) {
                return closestCorpse.interactWithPart(targetPart, player, hand);
            }

            return net.minecraft.util.ActionResult.PASS;
        });

        ModParticles.registerParticles();
        ModEntities.registerEntities();
        net.anatomyblood.command.DeleteBodysCommand.register();
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
