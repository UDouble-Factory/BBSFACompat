package io.mr_w98.bbsfacompat.mixin;

import io.mr_w98.bbsfacompat.animation.FaAnimation;
import io.mr_w98.bbsfacompat.animation.FaControls;
import io.mr_w98.bbsfacompat.animation.FaPlayerAnimator;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModelFormRenderer.class, remap = false)
public abstract class ModelFormRendererMixin {
    @Shadow private IAnimator animator;
    @Shadow private boolean renderingArm;
    @Shadow public abstract ModelForm getForm();
    @Unique private boolean bbsfa$lastArm;

    @Inject(method = "createAnimator", at = @At("HEAD"), cancellable = true)
    private static void bbsfa$animator(ModelInstance model, CallbackInfoReturnable<IAnimator> cir) {
        if (model.cemAnimation instanceof FaAnimation animation && model.config.cemAnimation.get()) {
            cir.setReturnValue(new FaPlayerAnimator(animation));
        }
    }

    @Inject(method = "evaluateChannels(Lmchorse/bbs_mod/forms/entities/IEntity;Lmchorse/bbs_mod/cubic/ModelInstance;F)V", at = @At("HEAD"))
    private void bbsfa$configure(IEntity entity, ModelInstance model, float transition, CallbackInfo ci) {
        if (animator instanceof FaPlayerAnimator player) {
            if (bbsfa$lastArm != renderingArm) model.clearChannels();
            bbsfa$lastArm = renderingArm;
            player.configure(((FaControls.Holder) getForm()).bbsfa$controls(), renderingArm);
        }
    }
}
