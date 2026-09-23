package io.mr_w98.bbsfacompat.mixin;

import io.mr_w98.bbsfacompat.animation.FaAnimation;
import io.mr_w98.bbsfacompat.animation.FaControls;
import io.mr_w98.bbsfacompat.animation.FaLayer;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelFormPanel;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIModelFormPanel.class, remap = false)
public abstract class UIModelFormPanelMixin extends UIFormPanel<ModelForm> {
    @Unique private UISection bbsfa$section;

    protected UIModelFormPanelMixin(UIForm editor) {
        super(editor);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbsfa$buildControls(UIForm editor, CallbackInfo ci) {
        bbsfa$section = section(IKey.constant("FA Player"), "model.fa_player", false);
        bbsfa$section.title.tooltip(IKey.constant("Layer strength: 1 = resource pack, 0 = muted. Animate fa_* property tracks in the film editor. Pose edits are applied after FA."));
        for (FaLayer layer : FaLayer.values()) {
            var field = UIValues.trackpad(() -> ((FaControls.Holder) form).bbsfa$controls().layers.get(layer));
            field.limit(0, 1);
            bbsfa$section.fields.add(UI.labelRow(IKey.constant(layer.label), field));
        }
        bbsfa$section.fields.add(UIValues.toggle(IKey.constant("Jump input"), () -> ((FaControls.Holder) form).bbsfa$controls().jumping));
        bbsfa$section.fields.add(UIValues.toggle(IKey.constant("Sleeping"), () -> ((FaControls.Holder) form).bbsfa$controls().sleeping));
        var depth = UIValues.trackpad(() -> ((FaControls.Holder) form).bbsfa$controls().waterDepth);
        depth.limit(-1, 32).tooltip(IKey.constant("-1 = detect water depth from the world; set a depth for staged underwater shots."));
        bbsfa$section.fields.add(UI.labelRow(IKey.constant("Water depth"), depth));
    }

    @Inject(method = "startEdit(Lmchorse/bbs_mod/forms/forms/ModelForm;)V", at = @At("TAIL"))
    private void bbsfa$showControls(ModelForm form, CallbackInfo ci) {
        bbsfa$section.removeFromParent();
        var model = ModelFormRenderer.getModel(form);
        if (model != null && model.cemAnimation instanceof FaAnimation && model.config.cemAnimation.get()) {
            options.add(bbsfa$section);
        }
        options.resize();
    }
}
