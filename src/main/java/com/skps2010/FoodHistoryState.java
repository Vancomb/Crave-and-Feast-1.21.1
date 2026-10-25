package com.skps2010;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Per-player list of recently eaten foods. Stored in world/data/crave_and_feast_food_history.dat */
public class FoodHistoryState extends SavedData {
    private static final String NAME = "crave_and_feast_food_history";
    private static final SavedData.Factory<FoodHistoryState> FACTORY =
            new SavedData.Factory<>(FoodHistoryState::new, FoodHistoryState::load);

    private final Map<UUID, List<String>> history = new HashMap<>();

    public FoodHistoryState() {
    }

    public static FoodHistoryState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static FoodHistoryState load(CompoundTag tag, HolderLookup.Provider registries) {
        FoodHistoryState s = new FoodHistoryState();
        CompoundTag all = tag.getCompound("history");
        for (String key : all.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                ListTag list = all.getList(key, Tag.TAG_STRING);
                List<String> foods = new ArrayList<>(list.size());
                for (int i = 0; i < list.size(); i++) foods.add(list.getString(i));
                s.history.put(uuid, foods);
            } catch (IllegalArgumentException ignored) {
                // bad UUID key, skip
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        history.forEach((uuid, foods) -> {
            ListTag list = new ListTag();
            for (String f : foods) list.add(StringTag.valueOf(f));
            all.put(uuid.toString(), list);
        });
        tag.put("history", all);
        return tag;
    }

    /** Adds a record and returns how many times this food is now in the history. */
    public int record(Player player, String foodId) {
        List<String> list = history.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());

        list.add(foodId);
        while (list.size() > Math.max(1, FDConfigs.CFG.recordingFoodCount)) list.removeFirst();

        int count = 0;
        for (String f : list) if (f.equals(foodId)) count++;
        setDirty();
        return count;
    }

    public List<String> getHistory(Player player) {
        List<String> list = history.get(player.getUUID());
        if (list == null) return Collections.emptyList();
        return new ArrayList<>(list);
    }
}
