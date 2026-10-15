package net.anatomyblood.mixin;

import net.anatomyblood.entity.CorpseEntity;
import net.anatomyblood.entity.CorpsePart;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    @Inject(method = "getEntityById", at = @At("RETURN"), cancellable = true)
    private void anatomyblood$getCorpsePartById(int id, CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() == null) {
            ClientWorld world = (ClientWorld) (Object) this;
            CorpsePart part = CorpseEntity.getPartById(world, id);
            if (part != null) {
                cir.setReturnValue(part);
            }
        }
    }
}
