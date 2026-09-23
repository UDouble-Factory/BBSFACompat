package io.mr_w98.bbsfacompat.animation;

import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.jem.CemVanillaSeed;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FaPlayerStageTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void seedMatchesVanillaHeadPivotsWalkingCrouchingRidingAndSwing() {
        for (boolean slim : new boolean[]{false, true}) {
            for (int scenario = 0; scenario < 5; scenario++) {
                IEntity source = source();
                boolean crouch = scenario == 1;
                boolean riding = scenario == 2;
                float swing = scenario == 3 ? 0.45F : 0;
                float speed = scenario == 4 ? 0.8F : 0;
                when(source.isSneaking()).thenReturn(crouch);
                when(source.isRiding()).thenReturn(riding);
                when(source.getHandSwingProgress(0.5F)).thenReturn(swing);
                when(source.getLimbSpeed(0.5F)).thenReturn(speed);
                when(source.getLimbPos(0.5F)).thenReturn(2.3F);

                LivingEntity living = mock(LivingEntity.class);
                when(living.getMainArm()).thenReturn(HumanoidArm.RIGHT);
                when(living.getDeltaMovement()).thenReturn(Vec3.ZERO);
                when(living.getItemBySlot(any())).thenReturn(ItemStack.EMPTY);
                living.swingingArm = InteractionHand.MAIN_HAND;
                PlayerModel<LivingEntity> vanilla = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
                vanilla.crouching = crouch;
                vanilla.riding = riding;
                vanilla.attackTime = swing;
                vanilla.setupAnim(living, riding ? 0 : 2.3F, speed, 100.5F, 35, 20);
                CemVanillaSeed seed = new FaPlayerStage().seed(source, 0.5F);
                same(vanilla.head, seed.get("head"));
                same(vanilla.body, seed.get("body"));
                same(vanilla.rightArm, seed.get("right_arm"));
                same(vanilla.leftArm, seed.get("left_arm"));
                same(vanilla.rightLeg, seed.get("right_leg"));
                same(vanilla.leftLeg, seed.get("left_leg"));
                same(vanilla.hat, seed.get("headwear"));
            }
        }
    }

    @Test
    void offHandUseAndLeftHandedBowFollowTheCorrectArm() {
        IEntity source = source();
        when(source.isRightHanded()).thenReturn(false);
        ItemStack bow = new ItemStack(Items.BOW);
        when(source.getEquipmentStack(EquipmentSlot.MAINHAND)).thenReturn(bow);
        ItemUsePose.setSource((entity, main) -> main ? new ItemUsePose.Use(bow.getUseAnimation(), 10, bow, 20, null) : null);
        try {
            var seed = new FaPlayerStage().seed(source, 0.5F);
            assertEquals(0.1 + Math.toRadians(35), seed.get("left_arm").ry, 1E-5);
            assertEquals(-0.5 + Math.toRadians(35), seed.get("right_arm").ry, 1E-5);
        } finally {
            ItemUsePose.setSource(null);
        }
    }

    @Test
    void rendererRootUsesVanillaRotationOrderAndLocalSwimOffset() {
        IEntity source = source();
        when(source.isFallFlying()).thenReturn(true);
        when(source.getRoll()).thenReturn(20);
        when(source.getRotationVec(0.5F)).thenReturn(new Vec3(0, 0, 1));
        when(source.lerpVelocity(0.5F)).thenReturn(new Vec3(1, 0, 0));
        ModelGroup root = new ModelGroup(FaPlayerAnimator.ROOT);
        FaPlayerAnimator.applyRoot(root, source, 0.5F);
        var expected = new Matrix4f().rotateX((float) Math.toRadians(-110)).rotateY((float) Math.PI / 2);
        assertVector(expected.transformDirection(new Vector3f(0, 1, 0)), root.current.quat.transform(new Vector3f(0, 1, 0)));

        root.reset();
        when(source.isFallFlying()).thenReturn(false);
        when(source.getLeaningPitch(0.5F)).thenReturn(1F);
        when(source.getEntityPose()).thenReturn(Pose.SWIMMING);
        when(source.isTouchingWater()).thenReturn(true);
        FaPlayerAnimator.applyRoot(root, source, 0.5F);
        Vector3f offset = new Matrix4f().rotateX((float) Math.toRadians(-110)).transformDirection(new Vector3f(0, -16, 4.8F));
        assertVector(offset, new Vector3f(-root.current.translate.x, root.current.translate.y, root.current.translate.z));
    }

    @Test
    void liveGliderUsesActualFlightTicksInsteadOfBbsRoll() {
        LivingEntity living = mock(LivingEntity.class);
        when(living.getFallFlyingTicks()).thenReturn(20);
        MCEntity source = mock(MCEntity.class);
        when(source.getMcEntity()).thenReturn(living);
        when(source.isFallFlying()).thenReturn(true);
        when(source.getVelocity()).thenReturn(new Vec3(0, 0, 1));
        when(source.lerpVelocity(0.5F)).thenReturn(new Vec3(0, 0, 1));
        when(source.getRotationVec(0.5F)).thenReturn(new Vec3(0, 0, 1));
        when(source.getEquipmentStack(any())).thenReturn(ItemStack.EMPTY);
        when(source.isSneaking()).thenReturn(true);

        assertEquals(0, new MCEntity(living).getRoll());
        ModelGroup root = new ModelGroup(FaPlayerAnimator.ROOT);
        FaPlayerAnimator.applyRoot(root, source, 0.5F);
        assertVector(new Vector3f(0, 0, -1), root.current.quat.transform(new Vector3f(0, 1, 0)));
        assertEquals(-Math.PI / 4, new FaPlayerStage().seed(source, 0.5F).get("head").rx, 1E-6);
        assertEquals(0, root.current.translate.y);
        assertEquals(0, new FaPlayerStage().seed(source, 0.5F).get("body").rx);

        when(living.getFallFlyingTicks()).thenReturn(1);
        root.reset();
        FaPlayerAnimator.applyRoot(root, source, 0.5F);
        assertVector(new Matrix4f().rotateX((float) Math.toRadians(-90 * 2.25 / 100)).transformDirection(new Vector3f(0, 1, 0)), root.current.quat.transform(new Vector3f(0, 1, 0)));
    }

    @Test
    void crouchingIncludesUnscaledPlayerRenderOffset() {
        IEntity source = source();
        when(source.isSneaking()).thenReturn(true);
        ModelGroup root = new ModelGroup(FaPlayerAnimator.ROOT);
        FaPlayerAnimator.applyRoot(root, source, 0.5F);
        assertEquals(-2, root.current.translate.y, 1E-6);
        assertEquals(0.9375F, root.current.scale.y);
        assertEquals(0.9375 * 2 - 2, root.current.translate.y + root.current.scale.y * 2, 1E-6);

        when(source.isSneaking()).thenReturn(false);
        root.reset();
        FaPlayerAnimator.applyRoot(root, source, 0.5F);
        assertEquals(0, root.current.translate.y);
    }

    @Test
    void movementUsesEmfSignsAndNormalizedWorldVelocity() {
        IEntity source = source();
        when(source.getVelocity()).thenReturn(new Vec3(0.15, 0, 0));
        var left = FaPlayerMotion.direction(source, 0.5F);
        assertEquals(-1, left.x, 1E-6);
        assertEquals(0, left.y, 1E-6);
        when(source.getVelocity()).thenReturn(new Vec3(-0.15, 0, 0));
        assertEquals(1, FaPlayerMotion.direction(source, 0.5F).x, 1E-6);

        when(source.getVelocity()).thenReturn(new Vec3(-0.15, 0, 0.15));
        var diagonal = FaPlayerMotion.direction(source, 0.5F);
        assertEquals(Math.sqrt(0.5), diagonal.x, 1E-6);
        assertEquals(Math.sqrt(0.5), diagonal.y, 1E-6);

        when(source.getPrevYaw()).thenReturn(90F);
        when(source.getYaw()).thenReturn(90F);
        when(source.getVelocity()).thenReturn(new Vec3(-0.15, 0, 0));
        assertEquals(1, FaPlayerMotion.direction(source, 0.5F).y, 1E-6);
        assertEquals(0, FaPlayerMotion.direction(source, 0.5F).x, 1E-6);
    }

    @Test
    void recordingMovementFallsBackToPositionThenInputs() {
        IEntity source = source();
        when(source.getX()).thenReturn(0.2);
        assertEquals(-1, FaPlayerMotion.direction(source, 0.5F).x, 1E-6);
        when(source.getX()).thenReturn(0.0);
        when(source.getSidewaysSpeed()).thenReturn(1F);
        when(source.getForwardSpeed()).thenReturn(1F);
        assertEquals(-Math.sqrt(0.5), FaPlayerMotion.direction(source, 0.5F).x, 1E-6);
        assertEquals(Math.sqrt(0.5), FaPlayerMotion.direction(source, 0.5F).y, 1E-6);
    }

    @Test
    void worldRotationTracksBodyWhileHeadLookStaysRelative() {
        IEntity source = source();
        when(source.getBodyYaw()).thenReturn(15F);
        when(source.getPrevBodyYaw()).thenReturn(15F);
        FaAnimation animation = new FaAnimation();
        animation.parameters(source, 0.5F);
        assertEquals(Math.toRadians(15), animation.parser.getOrCreateVariable("rot_y").doubleValue(), 1E-6);
        assertEquals(20, animation.parser.getOrCreateVariable("head_yaw").doubleValue(), 1E-6);
    }

    private static IEntity source() {
        IEntity source = mock(IEntity.class);
        when(source.getAge()).thenReturn(100);
        when(source.getPitch()).thenReturn(20F);
        when(source.getPrevPitch()).thenReturn(20F);
        when(source.getHeadYaw()).thenReturn(35F);
        when(source.getPrevHeadYaw()).thenReturn(35F);
        when(source.getVelocity()).thenReturn(Vec3.ZERO);
        when(source.getEquipmentStack(any())).thenReturn(ItemStack.EMPTY);
        when(source.isRightHanded()).thenReturn(true);
        return source;
    }

    private static void same(ModelPart vanilla, CemVanillaSeed.Part seed) {
        assertEquals(vanilla.x, seed.ax, 1E-5, "pivot x");
        assertEquals(vanilla.y, seed.ay, 1E-5, "pivot y");
        assertEquals(vanilla.z, seed.az, 1E-5, "pivot z");
        assertEquals(vanilla.xRot, seed.rx, 1E-5, "pitch");
        assertEquals(vanilla.yRot, seed.ry, 1E-5, "yaw");
        assertEquals(vanilla.zRot, seed.rz, 1E-5, "roll");
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertTrue(expected.distance(actual) < 1E-4, expected + " != " + actual);
    }
}
