package me.guardian.trigger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TriggerArea {
    private static final Gson GSON = new Gson();
    public static final String TYPE_COMMANDS = "commands";
    public static final String TYPE_PRIVATE = "private";

    public final UUID id;
    public final String dimension;
    public final BlockPos min;
    public final BlockPos max;
    public final List<String> commands;
    public final boolean runOnce;
    public int runCount;
    public String triggerType;
    public String triggerMode;
    public String triggerSelectors;
    public boolean globalRestrictions;
    public String restrictionMode;
    public String restrictionSelectors;
    public String privateSelectors;
    public boolean restrictBlockBreaking;
    public boolean restrictAttacking;
    public boolean restrictInteractions;

    public TriggerArea(UUID id, String dimension, BlockPos first, BlockPos second) {
        this.id = id;
        this.dimension = dimension;
        this.min = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        this.max = new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        this.commands = new ArrayList<>();
        this.runOnce = false;
        this.triggerType = TYPE_COMMANDS;
        this.triggerMode = "everyone";
        this.triggerSelectors = "";
        this.globalRestrictions = false;
        this.restrictionMode = "everyone";
        this.restrictionSelectors = "";
        this.privateSelectors = "@a";
        this.restrictBlockBreaking = true;
        this.restrictAttacking = true;
        this.restrictInteractions = false;
    }

    private TriggerArea(UUID id, String dimension, BlockPos min, BlockPos max, List<String> commands, boolean runOnce, int runCount,
                        String triggerType, String triggerMode, String triggerSelectors, boolean globalRestrictions, String restrictionMode,
                        String restrictionSelectors, String privateSelectors, boolean restrictBlockBreaking, boolean restrictAttacking,
                        boolean restrictInteractions) {
        this.id = id;
        this.dimension = dimension;
        this.min = min;
        this.max = max;
        this.commands = new ArrayList<>(commands);
        this.runOnce = runOnce;
        this.runCount = runCount;
        this.triggerType = normalizeTriggerType(triggerType, globalRestrictions);
        this.triggerMode = triggerMode == null || triggerMode.isBlank() ? "everyone" : triggerMode;
        this.triggerSelectors = triggerSelectors == null ? "" : triggerSelectors;
        this.globalRestrictions = globalRestrictions;
        this.restrictionMode = restrictionMode == null || restrictionMode.isBlank() ? "everyone" : restrictionMode;
        this.restrictionSelectors = restrictionSelectors == null ? "" : restrictionSelectors;
        this.privateSelectors = privateSelectors == null || privateSelectors.isBlank() ? "@a" : privateSelectors;
        this.restrictBlockBreaking = restrictBlockBreaking;
        this.restrictAttacking = restrictAttacking;
        this.restrictInteractions = restrictInteractions;
    }

    public boolean contains(Entity entity) {
        AABB box = entity.getBoundingBox();
        return entity.level().dimension().identifier().toString().equals(dimension)
                && box.minX >= min.getX() && box.maxX <= max.getX() + 1.0D
                && box.minY >= min.getY() && box.maxY <= max.getY() + 1.0D
                && box.minZ >= min.getZ() && box.maxZ <= max.getZ() + 1.0D;
    }

    public boolean intersects(Entity entity) {
        AABB box = entity.getBoundingBox();
        return entity.level().dimension().identifier().toString().equals(dimension)
                && box.maxX > min.getX() && box.minX < max.getX() + 1.0D
                && box.maxY > min.getY() && box.minY < max.getY() + 1.0D
                && box.maxZ > min.getZ() && box.minZ < max.getZ() + 1.0D;
    }

    public boolean contains(ResourceKey<Level> level, BlockPos pos) {
        return level.identifier().toString().equals(dimension)
                && pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public TriggerArea edited(List<String> newCommands, boolean newRunOnce, String newTriggerType, String newTriggerMode, String newTriggerSelectors,
                              String newPrivateSelectors, boolean newRestrictBlockBreaking, boolean newRestrictAttacking,
                              boolean newRestrictInteractions) {
        return new TriggerArea(id, dimension, min, max, newCommands, newRunOnce, runCount,
                newTriggerType, newTriggerMode, newTriggerSelectors, TYPE_PRIVATE.equals(newTriggerType), "only_matching",
                newPrivateSelectors, newPrivateSelectors, newRestrictBlockBreaking, newRestrictAttacking, newRestrictInteractions);
    }

    public boolean isPrivate() {
        return TYPE_PRIVATE.equals(triggerType);
    }

    public String serialize() {
        JsonObject object = new JsonObject();
        object.addProperty("id", id.toString());
        object.addProperty("dimension", dimension);
        putPos(object, "min", min);
        putPos(object, "max", max);
        JsonArray commandArray = new JsonArray();
        commands.forEach(commandArray::add);
        object.add("commands", commandArray);
        object.addProperty("run_once", runOnce);
        object.addProperty("run_count", runCount);
        object.addProperty("trigger_type", triggerType);
        object.addProperty("trigger_mode", triggerMode);
        object.addProperty("trigger_selectors", triggerSelectors);
        object.addProperty("global_restrictions", globalRestrictions);
        object.addProperty("restriction_mode", restrictionMode);
        object.addProperty("restriction_selectors", restrictionSelectors);
        object.addProperty("private_selectors", privateSelectors);
        object.addProperty("restrict_block_breaking", restrictBlockBreaking);
        object.addProperty("restrict_attacking", restrictAttacking);
        object.addProperty("restrict_interactions", restrictInteractions);
        return GSON.toJson(object);
    }

    public static TriggerArea deserialize(String raw) {
        JsonObject object = GSON.fromJson(raw, JsonObject.class);
        List<String> commands = new ArrayList<>();
        if (object.has("commands") && object.get("commands").isJsonArray()) {
            object.getAsJsonArray("commands").forEach(element -> commands.add(element.getAsString()));
        }
        return new TriggerArea(
                UUID.fromString(object.get("id").getAsString()),
                object.get("dimension").getAsString(),
                readPos(object.getAsJsonObject("min")),
                readPos(object.getAsJsonObject("max")),
                commands,
                object.has("run_once") && object.get("run_once").getAsBoolean(),
                object.has("run_count") ? object.get("run_count").getAsInt() : 0,
                stringOr(object, "trigger_type", object.has("global_restrictions") && object.get("global_restrictions").getAsBoolean() ? TYPE_PRIVATE : TYPE_COMMANDS),
                stringOr(object, "trigger_mode", "everyone"),
                stringOr(object, "trigger_selectors", ""),
                object.has("global_restrictions") && object.get("global_restrictions").getAsBoolean(),
                stringOr(object, "restriction_mode", "everyone"),
                stringOr(object, "restriction_selectors", ""),
                stringOr(object, "private_selectors", stringOr(object, "restriction_selectors", "@a")),
                !object.has("restrict_block_breaking") || object.get("restrict_block_breaking").getAsBoolean(),
                !object.has("restrict_attacking") || object.get("restrict_attacking").getAsBoolean(),
                object.has("restrict_interactions") && object.get("restrict_interactions").getAsBoolean()
        );
    }

    private static String normalizeTriggerType(String raw, boolean oldGlobalRestrictions) {
        if (TYPE_PRIVATE.equals(raw) || oldGlobalRestrictions) {
            return TYPE_PRIVATE;
        }
        return TYPE_COMMANDS;
    }

    private static void putPos(JsonObject object, String key, BlockPos pos) {
        JsonObject posObject = new JsonObject();
        posObject.addProperty("x", pos.getX());
        posObject.addProperty("y", pos.getY());
        posObject.addProperty("z", pos.getZ());
        object.add(key, posObject);
    }

    private static BlockPos readPos(JsonObject object) {
        return new BlockPos(object.get("x").getAsInt(), object.get("y").getAsInt(), object.get("z").getAsInt());
    }

    private static String stringOr(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }
}
