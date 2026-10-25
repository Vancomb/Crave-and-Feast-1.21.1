package com.skps2010;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CravingManager {
    // Cached pool of foods that can be craved
    private static volatile List<Item> allowedFoods = List.of();
    // Normalized (namespaced) blacklist, rebuilt alongside the food pool
    private static volatile Set<String> blacklist = Set.of();

    private CravingManager() {
    }

    /* ------------------------- food pool ------------------------- */

    public static void rebuildAllowedFoods() {
        blacklist = normalizedBlacklist();
        List<Item> pool = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!isCravableFood(item)) continue;
            if (blacklist.contains(idOf(item))) continue; // never crave blacklisted foods
            pool.add(item);
        }
        // Force-include manually whitelisted items that weren't auto-detected as food.
        for (String id : normalizeIds(FDConfigs.CFG.cravingWhitelist)) {
            if (blacklist.contains(id)) continue;
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) continue;
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != Items.AIR && !pool.contains(item)) pool.add(item);
        }
        allowedFoods = List.copyOf(pool);
        CraveAndFeast.LOGGER.info("[CraveAndFeast] {} foods available for cravings ({} blacklisted)",
                pool.size(), blacklist.size());
    }

    /** Whether an item is a non-harmful food, i.e. eligible for the craving pool (before blacklisting). */
    public static boolean isCravableFood(Item item) {
        if (item == Items.AIR) return false;
        FoodProperties food;
        try {
            // NeoForge-aware: also picks up modded items that supply food via getFoodProperties
            food = new ItemStack(item).getFoodProperties(null);
        } catch (Exception e) {
            return false; // some modded items need a real entity; just skip them
        }
        return food != null && !hasNegativeEffect(food);
    }

    private static Set<String> normalizedBlacklist() {
        return normalizeIds(FDConfigs.CFG.cravingBlacklist);
    }

    /** Normalizes a list of ids to full registry ids ("minecraft:" assumed when the namespace is omitted). */
    private static Set<String> normalizeIds(List<String> list) {
        if (list == null || list.isEmpty()) return Set.of();
        Set<String> out = new HashSet<>();
        for (String s : list) {
            if (s == null) continue;
            s = s.trim();
            if (s.isEmpty()) continue;
            if (!s.contains(":")) s = "minecraft:" + s;
            out.add(s);
        }
        return out;
    }

    private static boolean isBlacklisted(String id) {
        return blacklist.contains(id);
    }

    private static boolean hasNegativeEffect(FoodProperties food) {
        for (var pe : food.effects()) {
            try {
                var inst = pe.effect();
                if (inst != null && inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                    return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static Optional<String> pickRandomAllowedId() {
        if (allowedFoods.isEmpty()) rebuildAllowedFoods();
        var pool = allowedFoods;
        if (pool.isEmpty()) return Optional.empty();
        var it = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        return Optional.of(BuiltInRegistries.ITEM.getKey(it).toString());
    }

    public static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /* ------------------------- core flow ------------------------- */

    /** Makes sure the player has a craving; rerolls (and notifies) if expired. */
    public static CravingState.Entry ensureCraving(ServerPlayer p) {
        var st = CravingState.get(p.server);
        var now = p.server.overworld().getGameTime();
        var e = st.get(p.getUUID());
        if (e == null || now >= e.nextChangeTick() || isBlacklisted(e.itemId())) e = rerollCraving(p, now);
        return e;
    }

    /** Called when eating; counts craving hits and rerolls once the per-round limit is reached. */
    public static void onConsume(ServerPlayer p, ItemStack stack) {
        var e = ensureCraving(p);
        var id = idOf(stack.getItem());
        if (!id.equals(e.itemId())) return;

        var st = CravingState.get(p.server);
        var c = e.eatenInRound() + 1;
        st.set(p.getUUID(), new CravingState.Entry(e.itemId(), e.nextChangeTick(), c));

        if (c >= FDConfigs.CFG.cravingMaxCount) rerollCraving(p, p.server.overworld().getGameTime());
    }

    public static boolean isCraving(ServerPlayer p, Item item) {
        var e = ensureCraving(p);
        return idOf(item).equals(e.itemId());
    }

    public static String getCurrentCravingItem(MinecraftServer server, UUID uuid) {
        CravingState.Entry e = CravingState.get(server).get(uuid);
        if (e == null) return "";
        return e.itemId();
    }

    /* ------------------------- reroll + notify ------------------------- */

    /** Forces an immediate new craving for the player and notifies their client. Used by /cravings_refresh. */
    public static CravingState.Entry forceReroll(ServerPlayer p) {
        return rerollCraving(p, p.server.overworld().getGameTime());
    }

    private static CravingState.Entry rerollCraving(ServerPlayer p, long now) {
        var st = CravingState.get(p.server);
        var id = pickRandomAllowedId().orElse("minecraft:bread"); // safe fallback
        var next = now + FDConfigs.CFG.cravingChangeInterval;

        var e = new CravingState.Entry(id, next, 0);
        st.set(p.getUUID(), e);

        Network.send(p, new CravingPayload(id));
        Network.send(p, new FoodHistoryPayload(p));
        return e;
    }

    public static void tickCraving(ServerPlayer p, long now) {
        var st = CravingState.get(p.server);
        var e = st.get(p.getUUID());
        if (e == null || now >= e.nextChangeTick()) rerollCraving(p, now);
    }
}
