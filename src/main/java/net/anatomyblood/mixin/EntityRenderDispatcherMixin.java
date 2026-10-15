package net.anatomyblood.mixin;

import net.anatomyblood.client.render.CorpseHitboxRenderer;
import net.anatomyblood.entity.CorpseEntity;
import net.anatomyblood.entity.CorpsePart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void anatomyblood$renderMultipartHitboxes(MatrixStack matrices, VertexConsumer vertices, Entity entity, float tickDelta, float red, float green, float blue, CallbackInfo ci) {
        if (entity instanceof CorpseEntity corpse) {
            CorpseHitboxRenderer.renderPartHitboxes(corpse, matrices, vertices, tickDelta);
            ci.cancel();
        } else if (entity instanceof CorpsePart) {
            ci.cancel();
        }
    }
}
