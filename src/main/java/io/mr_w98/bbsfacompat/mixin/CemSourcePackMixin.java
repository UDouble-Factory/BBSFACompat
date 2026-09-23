package io.mr_w98.bbsfacompat.mixin;

import io.mr_w98.bbsfacompat.pack.EmfPlayerIndex;
import mchorse.bbs_mod.utils.resources.CemSourcePack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.TreeMap;

@Mixin(value = CemSourcePack.class, remap = false)
public abstract class CemSourcePackMixin {
    @Shadow @Final private ResourceManager manager;
    @Shadow private volatile Map<String, ResourceLocation> assets;

    @Inject(method = "reindex", at = @At("TAIL"))
    private void bbsfa$indexPlayer(CallbackInfo ci) {
        Map<String, ResourceLocation> updated = new TreeMap<>(this.assets);
        EmfPlayerIndex.add(this.manager, updated);
        this.assets = updated;
    }
}
