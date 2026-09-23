package io.mr_w98.bbsfacompat.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class EmfPlayerIndex {
    private static final Logger LOGGER = LoggerFactory.getLogger("bbsfacompat");

    private EmfPlayerIndex() {
    }

    public static void add(ResourceManager resources, Map<String, ResourceLocation> assets) {
        for (String name : new String[]{"player", "player_slim"}) {
            ResourceLocation model = ResourceLocation.withDefaultNamespace("emf/cem/" + name + ".jem");
            if (resources.getResource(model).isEmpty()) continue;

            String folder = "models/cem/" + name + "/";
            assets.keySet().removeIf(path -> path.startsWith(folder));
            collect(resources, model, folder, name + ".jem", assets, new HashSet<>());

            String skinFolder = name.equals("player_slim") ? "slim" : "wide";
            ResourceLocation skin = ResourceLocation.withDefaultNamespace("textures/entity/player/" + skinFolder + "/steve.png");
            assets.put(folder + "model.png", skin);
        }
    }

    private static void collect(ResourceManager resources, ResourceLocation file, String folder, String localName, Map<String, ResourceLocation> assets, Set<ResourceLocation> visited) {
        if (resources.getResource(file).isEmpty()) return;
        assets.put(folder + localName, file);
        if (!visited.add(file)) return;

        try (var reader = new InputStreamReader(resources.getResource(file).orElseThrow().open(), StandardCharsets.UTF_8)) {
            collectReferences(resources, file, folder, JsonParser.parseReader(reader), assets, visited);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Unable to index FA Player model {}", file, e);
        }
    }

    private static void collectReferences(ResourceManager resources, ResourceLocation file, String folder, JsonElement element, Map<String, ResourceLocation> assets, Set<ResourceLocation> visited) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectReferences(resources, file, folder, child, assets, visited);
            }
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("model") && object.get("model").isJsonPrimitive()) {
                String reference = object.get("model").getAsString();
                String path = reference.endsWith(".jpm") ? reference : reference + ".jpm";
                ResourceLocation part = resolve(resources, file, path);
                if (part != null) {
                    collect(resources, part, folder, path.substring(path.lastIndexOf('/') + 1), assets, visited);
                }
            }
            for (var entry : object.entrySet()) {
                collectReferences(resources, file, folder, entry.getValue(), assets, visited);
            }
        }
    }

    private static ResourceLocation resolve(ResourceManager resources, ResourceLocation file, String reference) {
        String beside = file.getPath().substring(0, file.getPath().lastIndexOf('/') + 1);
        for (String path : new String[]{beside + reference, "emf/cem/" + reference, "optifine/cem/" + reference}) {
            ResourceLocation id = ResourceLocation.tryBuild(file.getNamespace(), path);
            if (id != null && resources.getResource(id).isPresent()) return id;
        }
        return null;
    }
}
