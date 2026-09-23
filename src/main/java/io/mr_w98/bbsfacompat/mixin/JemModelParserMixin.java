package io.mr_w98.bbsfacompat.mixin;

import com.google.gson.JsonObject;
import io.mr_w98.bbsfacompat.animation.FaAnimation;
import io.mr_w98.bbsfacompat.animation.FaPlayerAnimator;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.jem.CemAnimation;
import mchorse.bbs_mod.cubic.jem.JemModelParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = JemModelParser.class, remap = false)
public abstract class JemModelParserMixin {
    @Redirect(method = "parse(Lcom/google/gson/JsonObject;Lmchorse/bbs_mod/cubic/jem/JemModelParser$JpmResolver;Lmchorse/bbs_mod/math/molang/MolangParser;Lmchorse/bbs_mod/cubic/jem/CemHierarchy;)Lmchorse/bbs_mod/cubic/jem/JemModelParser$Result;", at = @At(value = "NEW", target = "mchorse/bbs_mod/cubic/jem/CemAnimation"))
    private static CemAnimation bbsfa$playerProgram(JsonObject jem, JemModelParser.JpmResolver resolver, mchorse.bbs_mod.math.molang.MolangParser parser, mchorse.bbs_mod.cubic.jem.CemHierarchy hierarchy) {
        if (!FaAnimation.isPlayer(jem)) return new CemAnimation();
        for (var element : jem.getAsJsonArray("models")) {
            if (!element.isJsonObject() || !element.getAsJsonObject().has("model")) continue;
            String file = element.getAsJsonObject().get("model").getAsString();
            if (file.startsWith("a_player_") && (resolver == null || resolver.apply(file) == null)) {
                throw new IllegalArgumentException("FA Player requires " + file + ". Enable the resource pack, or copy its JPM files alongside the JEM model.");
            }
        }
        return new FaAnimation();
    }

    @Inject(method = "parse(Lcom/google/gson/JsonObject;Lmchorse/bbs_mod/cubic/jem/JemModelParser$JpmResolver;Lmchorse/bbs_mod/math/molang/MolangParser;Lmchorse/bbs_mod/cubic/jem/CemHierarchy;)Lmchorse/bbs_mod/cubic/jem/JemModelParser$Result;", at = @At("RETURN"))
    private static void bbsfa$rendererRoot(CallbackInfoReturnable<JemModelParser.Result> cir) {
        var result = cir.getReturnValue();
        if (!(result.animation() instanceof FaAnimation)) return;
        ModelGroup root = new ModelGroup(FaPlayerAnimator.ROOT);
        root.children.addAll(result.model().topGroups);
        result.model().topGroups.clear();
        result.model().topGroups.add(root);
        result.animation().markPart(root);
        result.model().initialize();
        result.animation().setup(result.model());
    }
}
