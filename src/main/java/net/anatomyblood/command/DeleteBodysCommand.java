package net.anatomyblood.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.anatomyblood.entity.CorpseEntity;
import net.anatomyblood.entity.ModEntities;
import net.anatomyblood.network.ClearBloodPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.List;

public class DeleteBodysCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Register /deletebodys and alias /deletebodies
        dispatcher.register(CommandManager.literal("deletebodys")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(DeleteBodysCommand::execute));

        dispatcher.register(CommandManager.literal("deletebodies")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(DeleteBodysCommand::execute));
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        int removedCorpses = 0;

        // 1. Delete all corpses across all dimensions
        for (ServerWorld world : source.getServer().getWorlds()) {
            List<? extends CorpseEntity> corpses = world.getEntitiesByType(ModEntities.CORPSE, entity -> true);
            for (CorpseEntity corpse : corpses) {
                corpse.discard();
                removedCorpses++;
            }
        }

        // 2. Broadcast clear blood payload to all online players
        ClearBloodPayload payload = new ClearBloodPayload();
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }

        final int count = removedCorpses;
        source.sendFeedback(() -> Text.literal("§a[Anatomy of Blood] Se eliminaron " + count + " cuerpos y se limpió toda la sangre."), true);
        return removedCorpses;
    }
}
