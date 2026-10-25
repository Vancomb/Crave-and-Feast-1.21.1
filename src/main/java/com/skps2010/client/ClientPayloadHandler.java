package com.skps2010.client;

import com.skps2010.CravingPayload;
import com.skps2010.FoodHistoryPayload;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** Client-side state received from the server. Only touched on the client. */
public final class ClientPayloadHandler {
    static ItemStack current = ItemStack.EMPTY;
    static Map<String, FoodHistoryPayload.FoodInfo> map = Map.of();
    /** True once the server has sent us data (i.e. the server runs this mod). */
    static boolean synced = false;
    /** {@link Util#getMillis()} of the last craving update, which starts the on-screen HUD's fade. 0 = never shown. */
    static long cravingShownAtMs = 0L;

    private ClientPayloadHandler() {
    }

    // NeoForge runs play payload handlers on the main client thread by default.
    public static void handleFoodHistory(FoodHistoryPayload payload) {
        map = Map.copyOf(payload.map());
        synced = true;
    }

    public static void handleCraving(CravingPayload payload) {
        current = new ItemStack(payload.toItem());
        synced = true;
        cravingShownAtMs = Util.getMillis(); // (re)start the HUD fade whenever the craving changes
    }

    static FoodHistoryPayload.FoodInfo get(String id) {
        var info = map.get(id);
        if (info != null) return info;
        return map.get("default");
    }

    static void reset() {
        current = ItemStack.EMPTY;
        map = Map.of();
        synced = false;
        cravingShownAtMs = 0L;
    }
}
