package io.mr_w98.bbsfacompat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.mr_w98.bbsfacompat.animation.FaAnimation;
import io.mr_w98.bbsfacompat.animation.FaControls;
import io.mr_w98.bbsfacompat.animation.FaLayer;
import io.mr_w98.bbsfacompat.animation.FaPlayerAnimator;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.ICubicRenderer;
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
import org.joml.Vector3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
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
                Files.writeString(Path.of("result.txt"), "PASS: EMF pack discovery, both player models, runtime mixins, animation state, equipment, layers, pose overlay, serialization, gliding, strafe direction, crouch foot height, creative flight leg lift and lean in four headings\n");
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
            movementRegressions(model);
            creativeFlightRegressions(model);
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

    private static void movementRegressions(ModelInstance model) {
        Model cubic = (Model) model.model;
        FaPlayerAnimator animator = new FaPlayerAnimator((FaAnimation) model.cemAnimation);
        Sample glider = new Sample();
        glider.moving = true;
        glider.setEquipmentStack(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        glider.setOnGround(false);
        glider.setFallFlying(true);
        glider.setRoll(20);
        glider.setVelocity(0, 0, 1);
        simulate(animator, model, glider, 100, 160);
        check(value(model, "var.gliding") > 0.99, "Elytra glide animation did not settle");
        Vector3f up = cubic.getGroup(FaPlayerAnimator.ROOT).current.quat.transform(new Vector3f(0, 1, 0));
        check(up.z < -0.99, "Elytra glider remains upright: " + up);

        for (int side : new int[]{1, -1}) {
            Sample strafe = new Sample();
            strafe.moving = true;
            strafe.sideways = side;
            strafe.setVelocity(side * 0.15F, 0, 0);
            simulate(animator, model, strafe, 100, 160);
            check(value(model, "move_strafing") * side < -0.99, "Wrong EMF strafe sign");
            check(cubic.getGroup("head").current.rotate.y * side > 5, "Head looks away from the strafe direction: " + cubic.getGroup("head").current.rotate.y);
        }

        Sample crouch = new Sample();
        crouch.moving = true;
        crouch.setSneaking(true);
        crouch.setVelocity(0, 0, 0.1F);
        simulate(animator, model, crouch, 100, 140);
        double highestStance = Double.NEGATIVE_INFINITY;
        for (int tick = 141; tick <= 220; tick++) {
            simulate(animator, model, crouch, tick, tick);
            check(value(model, "var.sneak2") == 1, "Crouching vanilla seed did not reach FA");
            double height = Math.min(footHeight(cubic.getGroup("right_leg")), footHeight(cubic.getGroup("left_leg")));
            highestStance = Math.max(highestStance, height);
        }
        check(highestStance < 0.05, "Crouch walk floats above the ground: " + highestStance);
    }

    private static double footHeight(ModelGroup leg) {
        Matrix4f matrix = partMatrix(leg, 0);
        double height = Double.POSITIVE_INFINITY;
        for (var cube : leg.cubes) {
            for (int corner = 0; corner < 8; corner++) {
                Vector3f point = new Vector3f(cube.origin).add((corner & 1) == 0 ? 0 : cube.size.x, (corner & 2) == 0 ? 0 : cube.size.y, (corner & 4) == 0 ? 0 : cube.size.z).div(16);
                height = Math.min(height, matrix.transformPosition(point).y);
            }
        }
        return height;
    }

    private static Matrix4f partMatrix(ModelGroup part, float yaw) {
        var hierarchy = new ArrayList<ModelGroup>();
        for (ModelGroup group = part; group != null; group = group.parent) {
            hierarchy.add(group);
        }
        Collections.reverse(hierarchy);
        PoseStack stack = new PoseStack();
        stack.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        for (ModelGroup group : hierarchy) {
            ICubicRenderer.translateGroup(stack, group);
            ICubicRenderer.moveToGroupPivot(stack, group);
            ICubicRenderer.rotateGroup(stack, group);
            ICubicRenderer.scaleGroup(stack, group);
            ICubicRenderer.moveBackFromGroupPivot(stack, group);
        }
        return new Matrix4f(stack.last().pose());
    }

    private static void creativeFlightRegressions(ModelInstance model) {
        Model cubic = (Model) model.model;
        Sample flyer = new Sample();
        flyer.moving = true;
        flyer.setFlying(true);
        flyer.setOnGround(false);
        Vector3f[] baseline = new Vector3f[2];
        for (float yaw : new float[]{0, 90, 180, -90}) {
            flyer.setYaw(yaw);
            flyer.setPrevYaw(yaw);
            flyer.setHeadYaw(yaw);
            flyer.setPrevHeadYaw(yaw);
            flyer.setBodyYaw(yaw);
            flyer.setPrevBodyYaw(yaw);
            float[] rightLegY = new float[2];
            float[] leftLegY = new float[2];
            for (int side : new int[]{1, -1}) {
                int index = side == 1 ? 0 : 1;
                flyer.sideways = side;
                float x = side * 0.15F * (float) Math.cos(Math.toRadians(yaw));
                float z = side * 0.15F * (float) Math.sin(Math.toRadians(yaw));
                flyer.setVelocity(x, 0, z);
                FaPlayerAnimator animator = new FaPlayerAnimator((FaAnimation) model.cemAnimation);
                simulate(animator, model, flyer, 100, 180);
                check(value(model, "var.flying") > 0.99 && value(model, "var.gliding") == 0, "Creative flight was mistaken for gliding");
                check(value(model, "move_strafing") * side < -0.99, "Creative strafe direction is reversed");
                check(Math.abs(value(model, "move_forward")) < 1E-5, "Side flight contains forward movement");
                check(value(model, "var.fly_rlegrz") * side > 0.1 && value(model, "var.fly_llegrz") * side > 0.1, "Creative flight legs lean the wrong way");

                ModelGroup right = cubic.getGroup("right_leg");
                ModelGroup left = cubic.getGroup("left_leg");
                rightLegY[index] = right.current.translate.y;
                leftLegY[index] = left.current.translate.y;
                Vector3f up = partMatrix(cubic.getGroup("body"), yaw).transformDirection(new Vector3f(0, 1, 0));
                check(up.x * x + up.z * z > 0, "Creative flight torso leans away from movement");
                Vector3f pose = new Vector3f(right.current.translate.y, left.current.translate.y, right.current.rotate.z);
                if (yaw == 0) baseline[index] = pose;
                else check(pose.distance(baseline[index]) < 0.005, "Creative leg animation depends on the world heading: " + yaw);
            }
            check(Math.abs(rightLegY[1] - rightLegY[0] - 0.9) < 0.005, "Right strafe does not lift the right leg by the pack's expected amount");
            check(Math.abs(leftLegY[0] - leftLegY[1] - 0.9) < 0.005, "Left strafe does not lift the left leg by the pack's expected amount");
        }
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
        private float sideways;

        @Override
        public float getSidewaysSpeed() {
            return sideways;
        }

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
