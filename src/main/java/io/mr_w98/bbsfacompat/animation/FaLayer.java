package io.mr_w98.bbsfacompat.animation;

public enum FaLayer {
    IDLE("Idle / look", "idl_"),
    MOVEMENT("Movement", "mvmnt_"),
    WATER("Swimming", "udrwtr_"),
    GLIDING("Gliding", "gldng_"),
    FLIGHT("Creative flight", "fly_"),
    VERTICAL("Jump / fall / climb", "vrtcl_"),
    EQUIPMENT("Equipment / arm swing", "eqp_", "spr_");

    public final String label;
    private final String[] prefixes;

    FaLayer(String label, String... prefixes) {
        this.label = label;
        this.prefixes = prefixes;
    }

    public String id() {
        return "fa_" + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static FaLayer of(String variable) {
        for (FaLayer layer : values()) {
            for (String prefix : layer.prefixes) {
                if (variable.startsWith("var." + prefix)) return layer;
            }
        }
        return null;
    }
}
