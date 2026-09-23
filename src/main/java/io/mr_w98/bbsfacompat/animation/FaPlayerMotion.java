package io.mr_w98.bbsfacompat.animation;

import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

final class FaPlayerMotion {
    private FaPlayerMotion() {
    }

    static int fallFlyingTicks(IEntity entity) {
        var living = ItemUsePose.livingOf(entity);
        return living == null ? entity.getRoll() : living.getFallFlyingTicks();
    }

    static boolean isCrouching(IEntity entity) {
        var living = ItemUsePose.livingOf(entity);
        return living == null ? entity.isSneaking() : living.isCrouching();
    }

    static Vec2 direction(IEntity entity, float transition) {
        var velocity = entity.getVelocity();
        double x = velocity.x;
        double z = velocity.z;
        double length = Math.hypot(x, z);
        if (length < 1E-7) {
            x = entity.getX() - entity.getPrevX();
            z = entity.getZ() - entity.getPrevZ();
            length = Math.hypot(x, z);
        }
        if (length < 1E-7) {
            float strafe = -entity.getSidewaysSpeed();
            float forward = entity.getForwardSpeed();
            double inputLength = Math.hypot(strafe, forward);
            return inputLength < 1E-7 ? Vec2.ZERO : new Vec2((float) (strafe / inputLength), (float) (forward / inputLength));
        }

        double yaw = Math.toRadians(Mth.rotLerp(transition, entity.getPrevYaw(), entity.getYaw()));
        float forward = (float) ((-x * Math.sin(yaw) + z * Math.cos(yaw)) / length);
        float strafe = (float) ((-x * Math.cos(yaw) - z * Math.sin(yaw)) / length);
        return new Vec2(strafe, forward);
    }
}
