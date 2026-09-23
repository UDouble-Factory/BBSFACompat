package io.mr_w98.bbsfacompat.animation;

import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.jem.CemAnimator;
import mchorse.bbs_mod.cubic.jem.CemState;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.Map;
import java.util.WeakHashMap;

public final class FaPlayerAnimator extends CemAnimator {
    public static final String ROOT = "fa_player_root";
    private final FaAnimation program;
    private final FaPlayerStage stage = new FaPlayerStage();
    private final Map<IEntity, Playback> states = new WeakHashMap<>();
    private final StubEntity preview = new StubEntity();
    private FaControls controls;
    private IModelInstance armature;
    private boolean firstPerson;
    private double previewTicks;
    private long previewNanos;

    public FaPlayerAnimator(FaAnimation program) {
        super(program, null);
        this.program = program;
        this.preview.setOnGround(true);
    }

    public void configure(FaControls controls, boolean firstPerson) {
        this.controls = controls;
        this.firstPerson = firstPerson;
    }

    @Override
    public void setup(IModelInstance model, ActionsConfig actions, boolean fade) {
        this.armature = model;
    }

    @Override
    public void update(IEntity entity) {
        if (entity == null || armature == null) return;
        Playback playback = states.get(entity);
        double tick = entity.getAge();
        if (playback != null && tick >= Math.floor(playback.stamp) && tick - playback.stamp < 1) return;

        armature.getModel().resetPose();
        evaluate(entity, 0, false, false);
        if (armature instanceof ModelInstance model) model.clearChannels();
    }

    @Override
    public void applyActions(IEntity entity, IModelInstance model, float transition) {
        this.armature = model;
        boolean inGui = entity == null;
        if (inGui) {
            long now = System.nanoTime();
            if (previewNanos != 0) previewTicks += Math.min(0.5, (now - previewNanos) / 1E9) * 20;
            previewNanos = now;
            preview.setAge((int) previewTicks);
            entity = preview;
            transition = (float) (previewTicks - Math.floor(previewTicks));
        }
        evaluate(entity, transition, inGui, firstPerson);

        if (!firstPerson && model.getModel() instanceof mchorse.bbs_mod.cubic.data.model.Model cubic) {
            applyRoot(cubic.getGroup(ROOT), entity, transition);
        }
    }

    private void evaluate(IEntity entity, float transition, boolean inGui, boolean firstPerson) {
        program.configure(controls, firstPerson);
        double stamp = entity.getAge() + (double) transition;
        Playback playback = states.get(entity);
        boolean rewind = playback != null && stamp < playback.stamp && (entity.isStandIn() || entity.getAge() < Math.floor(playback.stamp));
        if (playback == null || rewind) {
            playback = new Playback(program.createState());
            states.put(entity, playback);
        }
        playback.stamp = Math.max(playback.stamp, stamp);
        program.apply(playback.state, entity, transition, inGui, status, stage.seed(entity, transition));
    }

    static void applyRoot(ModelGroup root, IEntity entity, float transition) {
        if (root == null) return;
        float pitch = Mth.lerp(transition, entity.getPrevPitch(), entity.getPitch());
        float swim = entity.getLeaningPitch(transition);
        root.current.scale.set(0.9375F);
        root.current.rotationMode = Transform.RotationMode.QUATERNION;
        root.current.quat.identity();
        if (FaPlayerMotion.isCrouching(entity)) root.current.translate.y -= 2;
        if (entity.isFallFlying()) {
            float ticks = FaPlayerMotion.fallFlyingTicks(entity) + transition;
            root.current.quat.rotateX(Mth.clamp(ticks * ticks / 100, 0, 1) * (-90 - pitch) * Mth.DEG_TO_RAD);
            var look = entity.getRotationVec(transition);
            var velocity = entity.lerpVelocity(transition);
            double lengths = look.horizontalDistanceSqr() * velocity.horizontalDistanceSqr();
            if (lengths > 0) {
                double dot = (velocity.x * look.x + velocity.z * look.z) / Math.sqrt(lengths);
                double cross = velocity.x * look.z - velocity.z * look.x;
                root.current.quat.rotateY((float) (Math.signum(cross) * Math.acos(Mth.clamp(dot, -1, 1))));
            }
        } else if (swim > 0) {
            root.current.quat.rotateX(swim * (entity.isTouchingWater() ? -90 - pitch : -90) * Mth.DEG_TO_RAD);
            if (entity.getEntityPose() == Pose.SWIMMING) {
                var offset = root.current.quat.transform(new org.joml.Vector3f(0, -16, 4.8F));
                root.current.translate.add(-offset.x, offset.y, offset.z);
            }
        }
        root.current.quat.getEulerAnglesZYX(root.current.rotate).mul(Mth.RAD_TO_DEG);
    }

    private static final class Playback {
        private final CemState state;
        private double stamp = Double.NEGATIVE_INFINITY;

        private Playback(CemState state) {
            this.state = state;
        }
    }
}
