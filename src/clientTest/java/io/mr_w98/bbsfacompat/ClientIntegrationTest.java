package io.mr_w98.bbsfacompat;

import io.mr_w98.bbsfacompat.animation.FaAnimation;
import io.mr_w98.bbsfacompat.animation.FaControls;
import io.mr_w98.bbsfacompat.animation.FaLayer;
import io.mr_w98.bbsfacompat.animation.FaPlayerAnimator;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.resources.CemSourcePack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientIntegrationTest implements ClientModInitializer {
    private boolean finished;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished || client.getOverlay() != null || client.screen == null) return;
            finished = true;
            try {
                run();
                Files.writeString(Path.of("result.txt"), "PASS: EMF pack discovery, both player models, runtime mixins, animation state, equipment, layers, pose overlay, serialization\n");
            } catch (Throwable error) {
                error.printStackTrace();
                try {
                    Files.writeString(Path.of("result.txt"), "FAIL: " + error + "\n");
                } catch (Exception ignored) {
                }
            } finally {
                client.stop();
            }
        });
    }

    private static void run() throws Exception {
        CemSourcePack pack = new CemSourcePack();
        for (String name : new String[]{"player", "player_slim"}) {
            check(pack.hasAsset(Link.assets("models/cem/" + name + "/" + name + ".jem")), "Missing EMF player entry: " + name);
            check(pack.hasAsset(Link.assets("models/cem/" + name + "/a_player_variables.jpm")), "Missing external JPM");
            ModelInstance model = BBSModClient.getModels().loadModel("cem/" + name);
            check(model != null && model.cemAnimation instanceof FaAnimation, "FA program was not installed");
            var cubic = (mchorse.bbs_mod.cubic.data.model.Model) model.model;
            check(cubic.getGroup(FaPlayerAnimator.ROOT) != null, "Missing renderer root");
            ModelForm form = new ModelForm();
            form.model.set("cem/" + name);
            var controls = ((FaControls.Holder) form).bbsfa$controls();
            ModelFormRenderer renderer = new ModelFormRenderer(form);
            renderer.ensureAnimator(0);
            check(renderer.getAnimator() instanceof FaPlayerAnimator, "FA animator was not installed");
            FaPlayerAnimator animator = (FaPlayerAnimator) renderer.getAnimator();
            var panel = new mchorse.bbs_mod.ui.forms.editors.panels.UIModelFormPanel(null);
            panel.startEdit(form);
            check(panel.options.getChildren(mchorse.bbs_mod.ui.framework.elements.UISection.class).size() >= 2, "FA controls are missing from the editor");
            animator.configure(controls, false);
            Sample actor = new Sample();
            actor.setPrevHeadYaw(45);
            actor.setHeadYaw(45);
            actor.setPrevPitch(30);
            actor.setPitch(30);
            simulate(animator, model, actor, 100, 140);
            check(Math.abs(cubic.getGroup("head").current.rotate.y) > 25, "Head does not follow yaw");
            check(Math.abs(cubic.getGroup("head").current.rotate.x) > 15, "Head does not follow pitch");

            actor.moving = true;
            simulate(animator, model, actor, 141, 180);
            check(Math.abs(value(model, "var.mvmnt_rlegrx")) > 0.01, "Walking did not run: " + java.util.stream.Stream.of("limb_speed", "frame_time", "frame_counter", "varb.fcc", "var.walk", "var.run", "var.flying", "var.in_air", "var.ls", "var.mvmnt_rlegrx").map(key -> key + "=" + value(model, key)).toList());
            check(Math.abs(cubic.getGroup("head").current.rotate.y) > 20, "Walking suppressed looking");

            actor.setFlying(true);
            actor.setOnGround(false);
            simulate(animator, model, actor, 181, 210);
            check(value(model, "var.flying") > 0.9, "Flying NBT condition did not run");
            actor.setFlying(false);
            actor.setOnGround(true);
            actor.setEquipmentStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            ItemUsePose.setSource((entity, main) -> main ? null : new ItemUsePose.Use(Items.SHIELD.getDefaultInstance().getUseAnimation(), 10, Items.SHIELD.getDefaultInstance(), 20, null));
            try {
                simulate(animator, model, actor, 211, 240);
                check(value(model, "varb.lblock") == 1, "Offhand shield condition did not run");
            } finally {
                ItemUsePose.setSource(null);
            }

            controls.layers.get(FaLayer.MOVEMENT).set(0F);
            simulate(animator, model, actor, 241, 241);
            check(value(model, "fa_movement") == 0, "Layer control did not reach the expression program");
            var head = cubic.getGroup("head");
            float before = head.current.rotate.x;
            form.pose.get().getOrCreate("head").rotate.x = (float) Math.toRadians(12);
            model.model.applyPose(form.pose.get());
            check(Math.abs(head.current.rotate.x - before - 12) < 0.001, "Pose overlay replaced FA animation");

            controls.sleeping.set(true);
            ModelForm restored = new ModelForm();
            restored.fromData(form.toData());
            var restoredControls = ((FaControls.Holder) restored).bbsfa$controls();
            check(restoredControls.sleeping.get(), "FA state was not serialized");
            check(restoredControls.layers.get(FaLayer.MOVEMENT).get() == 0, "Layer was not serialized");

            var tracks = mchorse.bbs_mod.film.replays.tracks.TrackCatalog.of(form);
            for (FaLayer layer : FaLayer.values()) {
                check(tracks.stream().anyMatch(track -> layer.id().equals(track.id().toKey())), "Missing keyframe track: " + layer.id());
            }
            var properties = new mchorse.bbs_mod.film.replays.FormProperties("test");
            var channel = properties.getOrCreate(form, FaLayer.MOVEMENT.id());
            channel.insert(0, 0F);
            channel.insert(20, 1F);
            properties.applyProperties(form, 10);
            check(Math.abs(controls.layers.get(FaLayer.MOVEMENT).get() - 0.5) < 0.001, "Layer keyframes did not interpolate");

            Sample other = new Sample();
            simulate(animator, model, other, 100, 105);
            check(value(model, "var.flying") == 0, "Animation state leaked between actors");
            model.model.resetPose();
            animator.applyActions(other, model, 0.5F);
            double repeat = value(model, "var.t_idle");
            model.model.resetPose();
            animator.applyActions(other, model, 0.5F);
            check(repeat == value(model, "var.t_idle"), "Repeated rendering advanced simulation time");
            for (var bone : model.model.getAllGroups()) {
                check(bone.current.translate.isFinite() && bone.current.rotate.isFinite() && bone.current.scale.isFinite(), "Nonfinite bone: " + bone.id);
            }
        }
    }

    private static double value(ModelInstance model, String name) {
        return model.cemAnimation.parser.getOrCreateVariable(name).doubleValue();
    }

    private static void simulate(FaPlayerAnimator animator, ModelInstance model, Sample actor, int first, int last) {
        for (int tick = first; tick <= last; tick++) {
            actor.setAge(tick);
            model.model.resetPose();
            animator.applyActions(actor, model, 0.5F);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Sample extends StubEntity {
        private boolean moving;

        @Override
        public float getLimbPos(float transition) {
            return moving ? (getAge() + transition) * 0.6F : 0;
        }

        @Override
        public float getLimbSpeed(float transition) {
            return moving ? 0.45F : 0;
        }
    }
}
