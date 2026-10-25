package com.skps2010.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.skps2010.CraveAndFeast;
import com.skps2010.CravingManager;
import com.skps2010.FDConfigs;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

@Mod(value = CraveAndFeast.MODID, dist = Dist.CLIENT)
public class CraveAndFeastClient {
    private static final ResourceLocation HUD_ID =
            ResourceLocation.fromNamespaceAndPath(CraveAndFeast.MODID, "craving_hud");

    /** Title-text accent colors: white for the on-screen HUD, blue for the inventory HUD. */
    public static final int SCREEN_ACCENT = 0xFFFFFFFF;
    public static final int INV_ACCENT = 0xFF66CCFF;

    public CraveAndFeastClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(CraveAndFeastClient::registerLayers);
        NeoForge.EVENT_BUS.addListener(CraveAndFeastClient::onTooltip);
        NeoForge.EVENT_BUS.addListener(CraveAndFeastClient::onLogout);
        NeoForge.EVENT_BUS.addListener(CraveAndFeastClient::onInventoryRender);

        // "Config" button next to the mod in the Mods list opens the config landing screen.
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (c, parent) -> new CravingConfigScreen(parent));
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        // Fabric version: attachElementBefore(CHAT) -> drawn just under the chat
        event.registerBelow(VanillaGuiLayers.CHAT, HUD_ID, CraveAndFeastClient::render);
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPayloadHandler.reset();
    }

    /* ------------------------- inventory HUD ------------------------- */

    /** Draws the inventory HUD module on top of the inventory and creative screens. */
    private static void onInventoryRender(ScreenEvent.Render.Post event) {
        var cfg = FDConfigs.CFG;
        if (!cfg.invHudEnabled) return;
        var screen = event.getScreen();
        if (!(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) return;

        ItemStack current = ClientPayloadHandler.current;
        if (current.isEmpty()) return;

        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isCreative()) return; // visual only: hide in creative
        renderHud(event.getGuiGraphics(), mc.font, current, cfg.invHudX, cfg.invHudY, cfg.invHudScale);
    }

    /* ------------------------- tooltip ------------------------- */

    private static void onTooltip(ItemTooltipEvent event) {
        if (!ClientPayloadHandler.synced) return; // server doesn't run the mod
        ItemStack stack = event.getItemStack();
        FoodProperties fc;
        try {
            fc = stack.getFoodProperties(event.getEntity());
        } catch (Exception e) {
            return;
        }
        if (fc == null) return;

        var info = ClientPayloadHandler.get(CravingManager.idOf(stack.getItem()));
        if (info == null) return;

        int base = fc.nutrition();
        int mod = Math.round(base * info.multiplier());
        List<Component> lines = event.getToolTip();
        lines.add(Component.literal(combinedIcons(base, mod - base)));
        if (info.display() != null && !info.display().isEmpty())
            lines.add(Component.translatable(info.display()));
    }

    /**
     * White shanks for the base value, yellow shanks for the bonus.
     * For penalties (addPts < 0) only the reduced total is shown in white.
     * Glyphs  (full) /  (half) come from assets/minecraft/font/default.json.
     */
    private static String combinedIcons(int basePts, int addPts) {
        int totalPts = Math.max(0, basePts + addPts);
        int totalFull = totalPts / 2;
        int totalHalf = totalPts % 2;

        int baseFull = Math.min(basePts / 2, totalFull);
        int yellowFull = totalFull - baseFull;

        StringBuilder sb = new StringBuilder();
        if (baseFull > 0) {
            sb.append("§f");
            sb.append("".repeat(baseFull));
        }
        if (yellowFull > 0) {
            sb.append("§e");
            sb.append("".repeat(yellowFull));
        }
        if (totalHalf == 1) {
            sb.append(addPts > 0 ? "§e" : "§f");
            sb.append('');
        }
        return sb.toString();
    }

    /* ------------------------- HUD ------------------------- */

    // On-screen HUD fade envelope (ms): fade in, hold fully opaque, then fade out. ~10s visible in total.
    private static final long FADE_IN_MS = 500;
    private static final long HOLD_MS = 9000;
    private static final long FADE_OUT_MS = 500;

    private static void render(GuiGraphics g, DeltaTracker delta) {
        var cfg = FDConfigs.CFG;
        if (!cfg.hudEnabled) return;

        ItemStack current = ClientPayloadHandler.current;
        if (current.isEmpty()) return;

        var mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (mc.player != null && mc.player.isCreative()) return; // visual only: hide in creative

        float alpha = fadeAlpha(ClientPayloadHandler.cravingShownAtMs);
        if (alpha <= 0f) return; // fully faded out; nothing to draw until the next craving update

        renderHud(g, mc.font, current, cfg.hudX, cfg.hudY, cfg.hudScale, alpha);
    }

    /** Opacity (0..1) of the on-screen HUD this frame, given when the craving was last updated. */
    private static float fadeAlpha(long shownAtMs) {
        if (shownAtMs == 0L) return 0f; // no craving shown yet
        long t = Util.getMillis() - shownAtMs;
        if (t < 0) return 1f;
        if (t < FADE_IN_MS) return t / (float) FADE_IN_MS;
        if (t < FADE_IN_MS + HOLD_MS) return 1f;
        long out = t - (FADE_IN_MS + HOLD_MS);
        if (out < FADE_OUT_MS) return 1f - out / (float) FADE_OUT_MS;
        return 0f;
    }

    /** Draws the HUD content fully opaque. Shared by both HUD modules and the editor. */
    static void renderHud(GuiGraphics g, Font font, ItemStack stack, int x, int y, float scale) {
        renderHud(g, font, stack, x, y, scale, 1.0f);
    }

    /** Draws the HUD content at (x, y) scaled by {@code scale}, at the given {@code alpha} (0..1). */
    static void renderHud(GuiGraphics g, Font font, ItemStack stack, int x, int y, float scale, float alpha) {
        int aByte = Mth.clamp((int) (alpha * 255f), 0, 255);
        int a = aByte << 24;

        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);

        // Tint the item icon's alpha via the shader color; blend lets it fade rather than pop.
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, alpha);
        g.renderItem(stack, 0, 0);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        // Font rendering snaps to fully opaque when the alpha byte is < 4, which flickers at the tail
        // of the fade; skip the text below that threshold (the icon keeps fading smoothly to zero).
        if (aByte >= 4) {
            g.drawString(font, Component.translatable("cf.hud.title"), 20, 2, a | 0x00FFFFFF, true);
            g.drawString(font, stack.getHoverName(), 20, 12, a | 0x00AAAAAA, true);
        }
        g.pose().popPose();
    }

    /** Unscaled width of the HUD content for the given item. */
    static int hudWidth(Font font, ItemStack stack) {
        int title = font.width(Component.translatable("cf.hud.title"));
        int name = font.width(stack.getHoverName());
        return 20 + Math.max(title, name);
    }

    /** Unscaled height of the HUD content. */
    static int hudHeight() {
        return 21;
    }
}
