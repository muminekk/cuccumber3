package com.cucumber.mixin;

import com.cucumber.NameTags;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides the vanilla nametag of bombed players (cucumber draws its own). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void cucumber$hideVanillaTag(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (NameTags.hides(entity)) cir.setReturnValue(false);
    }
}
