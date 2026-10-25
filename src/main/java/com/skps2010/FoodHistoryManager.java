package com.skps2010;

import net.minecraft.server.level.ServerPlayer;

public final class FoodHistoryManager {
    private FoodHistoryManager() {
    }

    public static int recordAndGetCount(ServerPlayer player, String foodId) {
        FoodHistoryState state = FoodHistoryState.get(player.server);
        int count = state.record(player, foodId);
        Network.send(player, new FoodHistoryPayload(player));
        return count;
    }
}
