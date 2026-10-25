package com.skps2010;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record FoodHistoryPayload(Map<String, FoodInfo> map) implements CustomPacketPayload {
    public static final Type<FoodHistoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CraveAndFeast.MODID, "crave_and_feast_sync"));

    public static final StreamCodec<FriendlyByteBuf, FoodHistoryPayload> STREAM_CODEC =
            CustomPacketPayload.codec(FoodHistoryPayload::write, FoodHistoryPayload::read);

    public FoodHistoryPayload(ServerPlayer player) {
        this(makeMap(player));
    }

    private static FoodHistoryPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, FoodInfo> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf();
            float mult = buf.readFloat();
            String disp = buf.readUtf();
            map.put(key, new FoodInfo(mult, disp));
        }
        return new FoodHistoryPayload(map);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(map.size());
        for (var e : map.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeFloat(e.getValue().multiplier());
            buf.writeUtf(e.getValue().display());
        }
    }

    private static Map<String, FoodInfo> makeMap(ServerPlayer player) {
        var cfg = FDConfigs.CFG;
        FoodHistoryState state = FoodHistoryState.get(player.server);
        List<String> history = state.getHistory(player);
        String cravingId = CravingManager.getCurrentCravingItem(player.server, player.getUUID());

        // How many times each food appears in the recent history
        Map<String, Long> counts = history.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        Map<String, FoodInfo> map = new HashMap<>();

        // Eaten foods: pick multiplier/display from the rules; craving overrides
        for (var e : counts.entrySet()) {
            String foodId = e.getKey();
            int count = e.getValue().intValue();

            float mult;
            String disp;
            if (foodId.equals(cravingId)) {
                mult = cfg.cravingMultiplier;
                disp = cfg.cravingDisplay;
            } else {
                mult = 1.0f;
                disp = "";
                for (var r : cfg.rules) {
                    if (r.maxCount < 0 || count <= r.maxCount) {
                        mult = r.multiplier;
                        disp = r.display;
                        break;
                    }
                }
            }
            map.put(foodId, new FoodInfo(mult, disp));
        }

        // Craving food is always included, even if never eaten
        if (cravingId != null && !cravingId.isEmpty()) {
            map.put(cravingId, new FoodInfo(cfg.cravingMultiplier, cfg.cravingDisplay));
        }

        // Default for never-eaten foods: first rule
        var first = cfg.rules.getFirst();
        map.put("default", new FoodInfo(first.multiplier, first.display));

        return map;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record FoodInfo(float multiplier, String display) {
    }
}
