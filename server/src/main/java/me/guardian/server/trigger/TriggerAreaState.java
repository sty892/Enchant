package me.guardian.server.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.guardian.trigger.TriggerArea;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TriggerAreaState extends SavedData {
    public static final String STATE_NAME = "guardian_mod_trigger_areas";
    public static final Codec<TriggerAreaState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("areas", List.of()).forGetter(state -> state.areas.stream().map(TriggerArea::serialize).toList())
    ).apply(instance, TriggerAreaState::new));
    public static final SavedDataType<TriggerAreaState> TYPE = new SavedDataType<>(STATE_NAME, TriggerAreaState::new, CODEC, null);

    public final List<TriggerArea> areas = new ArrayList<>();

    public TriggerAreaState() {
    }

    public TriggerAreaState(List<String> serializedAreas) {
        for (String serializedArea : serializedAreas) {
            try {
                areas.add(TriggerArea.deserialize(serializedArea));
            } catch (RuntimeException ignored) {
            }
        }
    }

    public static TriggerAreaState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public TriggerArea get(UUID id) {
        for (TriggerArea area : areas) {
            if (area.id.equals(id)) {
                return area;
            }
        }
        return null;
    }

    public void put(TriggerArea area) {
        areas.removeIf(existing -> existing.id.equals(area.id));
        areas.add(area);
        setDirty();
    }

    public boolean remove(UUID id) {
        boolean removed = areas.removeIf(existing -> existing.id.equals(id));
        if (removed) {
            setDirty();
        }
        return removed;
    }
}
