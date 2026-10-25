package com.skps2010;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(CraveAndFeast.MODID)
public class CraveAndFeast {
    public static final String MODID = "crave_and_feast";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CraveAndFeast(IEventBus modBus) {
        FDConfigs.load();

        modBus.addListener(Network::register);

        NeoForge.EVENT_BUS.addListener(CraveAndFeast::onServerTick);
        NeoForge.EVENT_BUS.addListener(CraveAndFeast::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(CraveAndFeast::onServerStarted);
        NeoForge.EVENT_BUS.addListener(CraveAndFeast::onRegisterCommands);

        LOGGER.info("[CraveAndFeast] Initialized!");
    }

    private static void onServerStarted(ServerStartedEvent event) {
        CravingManager.rebuildAllowedFoods();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CravingCommands.register(event.getDispatcher());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 20 != 0) return; // check once per second
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CravingManager.tickCraving(p, now);
        }
    }

    // Replaces the Fabric PlayerManagerMixin#onPlayerConnect
    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CravingManager.ensureCraving(player);
        Network.send(player, new FoodHistoryPayload(player));
        Network.send(player, new CravingPayload(
                CravingManager.getCurrentCravingItem(player.server, player.getUUID())));
    }
}
