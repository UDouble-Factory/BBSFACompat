package io.mr_w98.bbsfacompat.mixin;

import io.mr_w98.bbsfacompat.animation.FaControls;
import mchorse.bbs_mod.forms.forms.ModelForm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelForm.class, remap = false)
public abstract class ModelFormMixin implements FaControls.Holder {
    @Unique private FaControls bbsfa$controls;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbsfa$addControls(CallbackInfo ci) {
        this.bbsfa$controls = new FaControls((ModelForm) (Object) this);
    }

    @Override
    public FaControls bbsfa$controls() {
        return this.bbsfa$controls;
    }
}
