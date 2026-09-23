package io.mr_w98.bbsfacompat.animation;

import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.cubic.animation.VanillaArmPoses;
import mchorse.bbs_mod.cubic.animation.VanillaBone;
import mchorse.bbs_mod.cubic.animation.VanillaSwimPose;
import mchorse.bbs_mod.cubic.jem.CemVanillaSeed;
import mchorse.bbs_mod.cubic.jem.ICemVanillaStage;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.UseAnim;

public final class FaPlayerStage implements ICemVanillaStage {
    private final CemVanillaSeed seed = new CemVanillaSeed();

    @Override
    public void tick(IEntity entity) {
    }

    @Override
    public CemVanillaSeed seed(IEntity entity, float transition) {
        var head = reset("head", 0, 0, 0);
        var body = reset("body", 0, 0, 0);
        var rightArm = reset("right_arm", -5, 2, 0);
        var leftArm = reset("left_arm", 5, 2, 0);
        var rightLeg = reset("right_leg", -1.9F, 12, 0);
        var leftLeg = reset("left_leg", 1.9F, 12, 0);
        reset("root", 0, 24, 0);

        float pitch = Mth.lerp(transition, entity.getPrevPitch(), entity.getPitch()) * Mth.DEG_TO_RAD;
        float yaw = Mth.wrapDegrees(Mth.rotLerp(transition, entity.getPrevHeadYaw(), entity.getHeadYaw()) - Mth.rotLerp(transition, entity.getPrevBodyYaw(), entity.getBodyYaw())) * Mth.DEG_TO_RAD;
        float swim = entity.getLeaningPitch(transition);
        float swing = entity.getHandSwingProgress(transition);
        float phase = entity.isRiding() ? 0 : entity.getLimbPos(transition);
        float speed = entity.isRiding() ? 0 : Math.min(1, entity.getLimbSpeed(transition));
        float age = entity.getAge() + transition;
        boolean gliding = entity.isFallFlying() && FaPlayerMotion.fallFlyingTicks(entity) > 4;
        boolean swimmingPose = entity.getEntityPose() == Pose.SWIMMING;
        head.ry = yaw;
        head.rx = gliding ? -Mth.PI / 4 : Mth.lerp(swim, pitch, swimmingPose ? -Mth.PI / 4 : pitch);

        double velocity = entity.getVelocity().lengthSqr() / 0.2;
        float divisor = gliding ? (float) Math.max(1, velocity * velocity * velocity) : 1;
        rightArm.rx = Mth.cos(phase * 0.6662F + Mth.PI) * speed / divisor;
        leftArm.rx = Mth.cos(phase * 0.6662F) * speed / divisor;
        rightLeg.rx = Mth.cos(phase * 0.6662F) * 1.4F * speed / divisor;
        leftLeg.rx = Mth.cos(phase * 0.6662F + Mth.PI) * 1.4F * speed / divisor;
        rightLeg.ry = rightLeg.rz = 0.005F;
        leftLeg.ry = leftLeg.rz = -0.005F;

        if (entity.isRiding()) {
            rightArm.rx -= 0.62831855F;
            leftArm.rx -= 0.62831855F;
            rightLeg.rx = leftLeg.rx = -1.4137167F;
            rightLeg.ry = 0.31415927F;
            leftLeg.ry = -0.31415927F;
            rightLeg.rz = 0.07853982F;
            leftLeg.rz = -0.07853982F;
        }

        var mainUse = ItemUsePose.get(entity, true);
        var offUse = ItemUsePose.get(entity, false);
        boolean rightHanded = entity.isRightHanded();
        boolean crouching = FaPlayerMotion.isCrouching(entity);
        VanillaArmPoses.apply(new Bone(rightHanded ? rightArm : leftArm, !rightHanded), new Bone(rightHanded ? leftArm : rightArm, !rightHanded), head.rx, rightHanded ? head.ry : -head.ry,
            entity.getEquipmentStack(EquipmentSlot.MAINHAND), entity.getEquipmentStack(EquipmentSlot.OFFHAND), mainUse, offUse, crouching, swing > 0);

        if (swing > 0) {
            boolean attackRight = rightHanded != entity.isSwingingOffHand();
            var arm = attackRight ? rightArm : leftArm;
            body.ry = Mth.sin(Mth.sqrt(swing) * Mth.TWO_PI) * (attackRight ? 0.2F : -0.2F);
            rightArm.az = Mth.sin(body.ry) * 5;
            rightArm.ax = -Mth.cos(body.ry) * 5;
            leftArm.az = -Mth.sin(body.ry) * 5;
            leftArm.ax = Mth.cos(body.ry) * 5;
            rightArm.ry += body.ry;
            leftArm.ry += body.ry;
            leftArm.rx += body.ry;
            float ease = 1 - swing;
            ease = 1 - ease * ease * ease * ease;
            arm.rx -= Mth.sin(ease * Mth.PI) * 1.2F + Mth.sin(swing * Mth.PI) * -(head.rx - 0.7F) * 0.75F;
            arm.ry += body.ry * 2;
            arm.rz += Mth.sin(swing * Mth.PI) * -0.4F;
        }

        if (crouching) {
            body.rx = 0.5F;
            rightArm.rx += 0.4F;
            leftArm.rx += 0.4F;
            rightLeg.az = leftLeg.az = 4;
            rightLeg.ay = leftLeg.ay = 12.2F;
            head.ay = 4.2F;
            body.ay = 3.2F;
            rightArm.ay = leftArm.ay = 5.2F;
        }

        bob(rightArm, age, 1, rightHanded ? mainUse : offUse);
        bob(leftArm, age, -1, rightHanded ? offUse : mainUse);
        if (swim > 0) {
            boolean attackLeft = swing > 0 && rightHanded == entity.isSwingingOffHand();
            VanillaSwimPose.apply(new Bone(attackLeft ? leftArm : rightArm, attackLeft), new Bone(attackLeft ? rightArm : leftArm, attackLeft),
                new Bone(attackLeft ? leftLeg : rightLeg, attackLeft), new Bone(attackLeft ? rightLeg : leftLeg, attackLeft), swim, phase, swing, mainUse != null || offUse != null || entity.isUsingItem());
        }
        copy("headwear", head);
        copy("jacket", body);
        copy("right_sleeve", rightArm);
        copy("left_sleeve", leftArm);
        copy("right_pants", rightLeg);
        copy("left_pants", leftLeg);
        return seed;
    }

    private static void bob(CemVanillaSeed.Part arm, float age, float sign, ItemUsePose.Use use) {
        if (use != null && use.action() == UseAnim.SPYGLASS) return;
        arm.rz += sign * (Mth.cos(age * 0.09F) * 0.05F + 0.05F);
        arm.rx += sign * Mth.sin(age * 0.067F) * 0.05F;
    }

    private CemVanillaSeed.Part reset(String name, float x, float y, float z) {
        var part = seed.part(name);
        part.tx = part.ax = x;
        part.ty = part.ay = y;
        part.tz = part.az = z;
        part.rx = part.ry = part.rz = 0;
        part.sx = part.sy = part.sz = 1;
        part.visible = true;
        return part;
    }

    private void copy(String name, CemVanillaSeed.Part source) {
        var part = reset(name, source.ax, source.ay, source.az);
        part.rx = source.rx;
        part.ry = source.ry;
        part.rz = source.rz;
    }

    private record Bone(CemVanillaSeed.Part part, boolean mirrored) implements VanillaBone {
        @Override
        public float pitch() {
            return part.rx;
        }

        @Override
        public void pitch(float value) {
            part.rx = value;
        }

        @Override
        public float yaw() {
            return mirrored ? -part.ry : part.ry;
        }

        @Override
        public void yaw(float value) {
            part.ry = mirrored ? -value : value;
        }

        @Override
        public float roll() {
            return mirrored ? -part.rz : part.rz;
        }

        @Override
        public void roll(float value) {
            part.rz = mirrored ? -value : value;
        }
    }
}
