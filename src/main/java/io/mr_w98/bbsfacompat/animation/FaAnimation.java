package io.mr_w98.bbsfacompat.animation;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.cubic.jem.CemAnimation;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

import java.util.regex.Pattern;

public final class FaAnimation extends CemAnimation {
    private static final Pattern VARIABLE = Pattern.compile("\\bvar\\.[A-Za-z_][A-Za-z_0-9]*");
    private final FaNbt nbt = new FaNbt();
    private FaControls controls;
    private boolean firstPerson;

    public static boolean isPlayer(JsonObject jem) {
        if (!jem.has("models")) return false;
        for (var model : jem.getAsJsonArray("models")) {
            if (!model.isJsonObject()) continue;
            JsonObject object = model.getAsJsonObject();
            if (object.has("model") && object.get("model").getAsString().equals("a_player_variables.jpm")) return true;
        }
        return false;
    }

    public void configure(FaControls controls, boolean firstPerson) {
        this.controls = controls;
        this.firstPerson = firstPerson;
    }

    @Override
    public void addStatement(String target, String expression) {
        String compiled = FaExpression.normalize(weightReads(target, nbt.rewrite(expression)));
        try {
            parser.parse(compiled);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot compile FA Player expression " + target + ": " + expression, e);
        }
        super.addStatement(target, compiled);
    }

    static String weightReads(String target, String expression) {
        FaLayer own = FaLayer.of(target);
        return VARIABLE.matcher(expression).replaceAll(match -> {
            FaLayer layer = FaLayer.of(match.group());
            return layer == null || layer == own ? match.group() : "(" + match.group() + "*" + layer.id() + ")";
        });
    }

    public void parameters(IEntity entity, float transition) {
        float head = Mth.rotLerp(transition, entity.getPrevHeadYaw(), entity.getHeadYaw());
        float body = Mth.rotLerp(transition, entity.getPrevBodyYaw(), entity.getBodyYaw());
        parser.setValue("head_yaw", Mth.wrapDegrees(head - body));
        parser.setValue("rot_y", Math.toRadians(body));
        var movement = FaPlayerMotion.direction(entity, transition);
        parser.setValue("move_forward", movement.y);
        parser.setValue("move_strafing", movement.x);
        parser.setValue("is_first_person_hand", firstPerson ? 1 : 0);
        parser.setValue("is_paused", 0);
        parser.setValue("is_jumping", (controls != null && controls.jumping.get()) || (!entity.isOnGround() && entity.getY() > entity.getPrevY()) ? 1 : 0);
        parser.setValue("fluid_depth_up", controls != null && controls.waterDepth.get() >= 0 ? controls.waterDepth.get() : waterDepth(entity, transition));

        ItemUsePose.Use mainUse = ItemUsePose.get(entity, true);
        ItemUsePose.Use offUse = ItemUsePose.get(entity, false);
        boolean usingMain = mainUse != null;
        boolean usingOff = offUse != null;
        boolean using = usingMain || usingOff || entity.isUsingItem();
        boolean main = usingMain || (!usingOff && entity.isSwinging() && !entity.isSwingingOffHand());
        boolean off = usingOff || (!usingMain && entity.isSwinging() && entity.isSwingingOffHand());
        parser.setValue("is_using_item", using ? 1 : 0);
        parser.setValue("is_blocking", entity.isBlocking() || (mainUse != null && mainUse.action() == UseAnim.BLOCK) || (offUse != null && offUse.action() == UseAnim.BLOCK) ? 1 : 0);
        parser.setValue("is_swinging_right_arm", (entity.isRightHanded() ? main : off) ? 1 : 0);
        parser.setValue("is_swinging_left_arm", (entity.isRightHanded() ? off : main) ? 1 : 0);

        for (FaLayer layer : FaLayer.values()) {
            parser.setValue(layer.id(), controls == null ? 1 : Mth.clamp(controls.layers.get(layer).get(), 0, 1));
        }
        nbt.update(parser, path -> nbtValue(entity, path));
    }

    private String nbtValue(IEntity entity, String path) {
        return switch (path) {
            case "SleepingX" -> entity.getEntityPose() == Pose.SLEEPING || (controls != null && controls.sleeping.get()) ? "0" : null;
            case "abilities.flying" -> entity.isFlying() ? "1" : "0";
            case "SelectedItem.id" -> itemId(entity.getEquipmentStack(EquipmentSlot.MAINHAND));
            case "equipment.offhand" -> itemId(entity.getEquipmentStack(EquipmentSlot.OFFHAND));
            case "Inventory" -> {
                String item = itemId(entity.getEquipmentStack(EquipmentSlot.OFFHAND));
                yield item == null ? "[]" : "[{Slot:-106b,id:\"" + item + "\"}]";
            }
            default -> null;
        };
    }

    private static String itemId(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static double waterDepth(IEntity entity, float transition) {
        if (!entity.isTouchingWater() || entity.getWorld() == null) return 0;
        double y = Mth.lerp(transition, entity.getPrevY(), entity.getY());
        BlockPos pos = BlockPos.containing(entity.getX(), y, entity.getZ());
        double surface = y;
        for (int i = 0; i < 32; i++) {
            var fluid = entity.getWorld().getFluidState(pos);
            if (fluid.isEmpty()) break;
            surface = pos.getY() + fluid.getHeight(entity.getWorld(), pos);
            pos = pos.above();
        }
        return Math.max(0, surface - y);
    }
}
