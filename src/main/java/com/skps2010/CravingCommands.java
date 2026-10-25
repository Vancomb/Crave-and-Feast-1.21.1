package com.skps2010;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Server commands for Crave &amp; Feast. */
public final class CravingCommands {
    private CravingCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /cravings_refresh - reroll every online player's current craving. Op-only (permission level 2).
        dispatcher.register(Commands.literal("cravings_refresh")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    int count = 0;
                    for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                        CravingManager.forceReroll(p);
                        count++;
                    }
                    final int n = count;
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("Refreshed cravings for " + n + " player(s)."), true);
                    return n;
                }));
    }
}
