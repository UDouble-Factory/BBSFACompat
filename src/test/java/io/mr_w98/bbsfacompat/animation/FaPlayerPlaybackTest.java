package io.mr_w98.bbsfacompat.animation;

import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.math.molang.MolangParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FaPlayerPlaybackTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void ticksKeepLiveStateRunningWhenTheBodyIsNotRendered() {
        PlaybackFixture fixture = new PlaybackFixture(false);
        fixture.render(100, 0.5F);
        for (int tick = 101; tick <= 141; tick++) {
            fixture.tick(tick);
        }
        fixture.render(141, 0.5F);
        assertEquals(2.05, fixture.elapsed(), 1E-6);
    }

    @Test
    void firstVisibleFrameAfterStartingInFirstPersonIsAlreadyAnimated() {
        PlaybackFixture fixture = new PlaybackFixture(false);
        for (int tick = 100; tick <= 140; tick++) {
            fixture.tick(tick);
        }
        fixture.render(140, 0.5F);
        assertEquals(2.025, fixture.elapsed(), 1E-6);
    }

    @Test
    void liveSubtickRenderPassesDoNotResetHistory() {
        PlaybackFixture fixture = new PlaybackFixture(false);
        fixture.render(100, 0.75F);
        fixture.render(101, 0.75F);
        fixture.animator.configure(null, true);
        fixture.render(101, 0);
        assertEquals(0.05, fixture.elapsed(), 1E-6);
        fixture.tick(101);
        fixture.tick(101);
        assertEquals(0.05, fixture.elapsed(), 1E-6);
        fixture.animator.configure(null, false);
        fixture.render(102, 0);
        assertEquals(0.0625, fixture.elapsed(), 1E-6);
    }

    @Test
    void recordingRewindStillResetsState() {
        PlaybackFixture fixture = new PlaybackFixture(true);
        fixture.render(100, 0.75F);
        double initial = fixture.elapsed();
        fixture.render(101, 0.75F);
        assertTrue(fixture.elapsed() > initial);
        fixture.render(101, 0);
        assertEquals(initial, fixture.elapsed(), 1E-6);
    }

    private static final class PlaybackFixture {
        private final FaAnimation program = new FaAnimation();
        private final FaPlayerAnimator animator = new FaPlayerAnimator(program);
        private final IEntity entity = mock(IEntity.class);
        private final IModelInstance armature = mock(IModelInstance.class);
        private final Model model = new Model(new MolangParser());

        private PlaybackFixture(boolean standIn) {
            when(entity.isStandIn()).thenReturn(standIn);
            when(entity.isOnGround()).thenReturn(true);
            when(entity.getVelocity()).thenReturn(Vec3.ZERO);
            when(entity.getEquipmentStack(any())).thenReturn(ItemStack.EMPTY);
            when(armature.getModel()).thenReturn(model);
            ModelGroup head = new ModelGroup("head");
            head.initial.translate.set(0, 24, 0);
            model.topGroups.add(head);
            model.initialize();
            program.markPart(head);
            program.addStatement("var.elapsed", "var.elapsed+frame_time");
            program.addStatement("head.rx", "var.elapsed");
            program.setup(model);
            animator.setup(armature, new ActionsConfig(), false);
        }

        private void tick(int age) {
            when(entity.getAge()).thenReturn(age);
            animator.update(entity);
        }

        private void render(int age, float transition) {
            when(entity.getAge()).thenReturn(age);
            model.resetPose();
            animator.applyActions(entity, armature, transition);
        }

        private double elapsed() {
            return program.parser.getOrCreateVariable("var.elapsed").doubleValue();
        }
    }
}
