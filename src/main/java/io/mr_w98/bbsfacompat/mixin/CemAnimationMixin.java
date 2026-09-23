package io.mr_w98.bbsfacompat.mixin;

import io.mr_w98.bbsfacompat.animation.FaAnimation;
import mchorse.bbs_mod.cubic.jem.CemAnimation;
import mchorse.bbs_mod.cubic.jem.CemStatus;
import mchorse.bbs_mod.forms.entities.IEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CemAnimation.class, remap = false)
public abstract class CemAnimationMixin {
    @Inject(method = "setParameters", at = @At("TAIL"))
    private void bbsfa$parameters(IEntity entity, float transition, CemStatus status, int ticksAgo, CallbackInfo ci) {
        if ((Object) this instanceof FaAnimation animation) {
            animation.parameters(entity, transition);
        }
    }
}
