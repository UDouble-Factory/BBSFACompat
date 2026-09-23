package io.mr_w98.bbsfacompat.animation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.cubic.jem.CemParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class FaExpressionsTest {
    @Test
    @Tag("resource-pack")
    void compilesEveryExpressionInBothPlayerModelsAndTheirParts() throws Exception {
        int count = 0;
        try (ZipFile zip = new ZipFile(System.getProperty("bbsfacompat.testPack"))) {
            for (String name : new String[]{"player", "player_slim"}) {
                FaAnimation animation = new FaAnimation();
                JsonObject model = read(zip, name + ".jem");
                assertTrue(FaAnimation.isPlayer(model));
                for (JsonElement element : model.getAsJsonArray("models")) {
                    JsonObject part = element.getAsJsonObject();
                    if (part.has("model") && zip.getEntry("assets/minecraft/emf/cem/" + part.get("model").getAsString()) != null) {
                        count += compile(animation, read(zip, part.get("model").getAsString()));
                    }
                    count += compile(animation, part);
                }
                assertFalse(animation.isEmpty());
            }
        }
        assertTrue(count > 1000, "Reference pack expressions were not fully visited: " + count);
    }

    @Test
    void nbtQueriesRemainDynamicAcrossActorsAndFrames() throws Exception {
        FaNbt nbt = new FaNbt();
        CemParser parser = new CemParser();
        var expression = parser.parse(nbt.rewrite("if(nbt(abilities.flying, 1), 3, nbt(Inventory, raw:iregex:.*Slot:-106b.*shield.*), 2, nbt(SleepingX, exists:true), 1, 0)"));
        nbt.update(parser, Map.of("abilities.flying", "1")::get);
        assertEquals(3, expression.doubleValue());
        nbt.update(parser, Map.of("Inventory", "[{Slot:-106b,id:\"minecraft:shield\"}]")::get);
        assertEquals(2, expression.doubleValue());
        nbt.update(parser, Map.of("SleepingX", "0")::get);
        assertEquals(1, expression.doubleValue());
        nbt.update(parser, ignored -> null);
        assertEquals(0, expression.doubleValue());
    }

    @Test
    void weightsComposeIndependentChannelsWithoutMultiplyingInternalDependenciesTwice() throws Exception {
        CemParser parser = new CemParser();
        parser.setValue("var.idl_bodyrx", 0.4);
        parser.setValue("var.mvmnt_bodyrx", 0.8);
        parser.setValue("fa_idle", 0.5);
        parser.setValue("fa_movement", 0.25);
        assertEquals(0.4, parser.parse(FaAnimation.weightReads("body.rx", "var.idl_bodyrx+var.mvmnt_bodyrx")).doubleValue(), 1E-9);
        assertEquals("var.idl_bodyrx*2", FaAnimation.weightReads("var.idl_bodytx", "var.idl_bodyrx*2"));
        parser.setValue("fa_idle", 0);
        assertEquals(0.2, parser.parse(FaAnimation.weightReads("head.rx", "var.idl_bodyrx+var.mvmnt_bodyrx")).doubleValue(), 1E-9);
    }

    @Test
    void unsupportedOrBrokenExpressionsFailLoudly() {
        assertThrows(IllegalArgumentException.class, () -> new FaAnimation().addStatement("head.rx", "missing_function(1)"));
        assertThrows(IllegalArgumentException.class, () -> new FaNbt().rewrite("nbt(unknown.path, 1)"));
        assertThrows(IllegalArgumentException.class, () -> new FaNbt().rewrite("nbt(abilities.flying, 1"));
    }

    @Test
    void groundedPlayerDoesNotTriggerTheFlightFallback() throws Exception {
        CemParser parser = new CemParser();
        parser.setValue("is_on_ground", 1);
        String expression = "!is_riding && !is_gliding && !is_on_ground && round((pos_y-var.pre_posy)*50)/50 ==0";
        assertEquals(0, parser.parse(FaExpression.normalize(expression)).doubleValue());
        assertEquals(1, parser.parse(FaExpression.normalize("true || false && false")).doubleValue());
        assertEquals(7, parser.parse(FaExpression.normalize("1+2*3")).doubleValue());
        assertEquals(-0.03, parser.parse(FaExpression.normalize("-3e-2")).doubleValue(), 1E-9);
    }

    private static int compile(FaAnimation animation, JsonObject object) {
        int count = 0;
        if (!object.has("animations")) return count;
        for (JsonElement block : object.getAsJsonArray("animations")) {
            for (var entry : block.getAsJsonObject().entrySet()) {
                animation.addStatement(entry.getKey(), entry.getValue().getAsString());
                count++;
            }
        }
        return count;
    }

    private static JsonObject read(ZipFile zip, String file) throws Exception {
        try (var reader = new InputStreamReader(zip.getInputStream(zip.getEntry("assets/minecraft/emf/cem/" + file)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
