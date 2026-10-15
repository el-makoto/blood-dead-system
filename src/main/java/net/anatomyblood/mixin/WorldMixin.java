package net.anatomyblood.mixin;

import net.anatomyblood.entity.CorpseEntity;
import net.anatomyblood.entity.CorpsePart;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

@Mixin(World.class)
public abstract class WorldMixin {

    @Inject(method = "getOtherEntities", at = @At("RETURN"))
    private void anatomyblood$addCorpseParts(Entity except, Box box, Predicate<? super Entity> predicate, CallbackInfoReturnable<List<Entity>> cir) {
        List<Entity> list = cir.getReturnValue();
        if (list == null || list.isEmpty()) return;

        int initialSize = list.size();
        for (int i = 0; i < initialSize; i++) {
            Entity entity = list.get(i);
            if (entity instanceof CorpseEntity corpse && entity != except) {
                for (CorpsePart part : corpse.parts) {
                    if (part != except && (predicate == null || predicate.test(part))) {
                        if (part.getBoundingBox().intersects(box)) {
                            list.add(part);
                        }
                    }
                }
            }
        }
    }
}
