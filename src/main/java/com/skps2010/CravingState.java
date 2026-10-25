package com.skps2010;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player current craving. Stored in world/data/crave_and_feast_craving.dat */
public class CravingState extends SavedData {
    private static final String NAME = "crave_and_feast_craving";
    private static final SavedData.Factory<CravingState> FACTORY =
            new SavedData.Factory<>(CravingState::new, CravingState::load);

    private final Map<UUID, Entry> data = new HashMap<>();

    public CravingState() {
    }

    public static CravingState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static CravingState load(CompoundTag tag, HolderLookup.Provider registries) {
        CravingState s = new CravingState();
        CompoundTag all = tag.getCompound("data");
        for (String key : all.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                CompoundTag e = all.getCompound(key);
                if (!e.contains("itemId", Tag.TAG_STRING)) continue;
                s.data.put(uuid, new Entry(
                        e.getString("itemId"),
                        e.getLong("nextChangeTick"),
                        e.getInt("eatenInRound")));
            } catch (IllegalArgumentException ignored) {
                // bad UUID key, skip
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        data.forEach((uuid, e) -> {
            CompoundTag t = new CompoundTag();
            t.putString("itemId", e.itemId());
            t.putLong("nextChangeTick", e.nextChangeTick());
            t.putInt("eatenInRound", e.eatenInRound());
            all.put(uuid.toString(), t);
        });
        tag.put("data", all);
        return tag;
    }

    public void set(UUID uuid, Entry e) {
        data.put(uuid, e);
        setDirty();
    }

    public Entry get(UUID uuid) {
        return data.get(uuid);
    }

    /** A player's current craving: item id, tick of next change, times eaten this round */
    public record Entry(String itemId, long nextChangeTick, int eatenInRound) {
    }
}
