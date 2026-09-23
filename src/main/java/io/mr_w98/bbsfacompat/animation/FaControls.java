package io.mr_w98.bbsfacompat.animation;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

import java.util.EnumMap;
import java.util.Map;

public final class FaControls {
    public final Map<FaLayer, ValueFloat> layers = new EnumMap<>(FaLayer.class);
    public final ValueBoolean jumping = new ValueBoolean("fa_jumping", false);
    public final ValueBoolean sleeping = new ValueBoolean("fa_sleeping", false);
    public final ValueFloat waterDepth = new ValueFloat("fa_water_depth", -1F, -1F, 32F);

    public FaControls(ModelForm form) {
        for (FaLayer layer : FaLayer.values()) {
            ValueFloat value = new ValueFloat(layer.id(), 1F, 0F, 1F);
            layers.put(layer, value);
            form.add(value);
        }
        form.add(jumping);
        form.add(sleeping);
        form.add(waterDepth);
    }

    public interface Holder {
        FaControls bbsfa$controls();
    }
}
