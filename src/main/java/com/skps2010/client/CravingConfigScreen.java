package com.skps2010.client;

import com.skps2010.FDConfigs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Landing screen opened from the "Config" button next to the mod in the Mods
 * list. Offers the HUD editor (move / resize / hide) and the craving blacklist.
 */
public class CravingConfigScreen extends Screen {
    private static final int BTN_W = 180, BTN_H = 20, BTN_GAP = 6, PANEL_PAD = 12;

    private final Screen parent;
    private int panelX, panelY, panelW, panelH;

    public CravingConfigScreen(Screen parent) {
        super(Component.translatable("cf.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int blockH = 3 * BTN_H + 2 * BTN_GAP;
        panelW = BTN_W + 2 * PANEL_PAD;
        panelH = blockH + 2 * PANEL_PAD;
        panelX = this.width / 2 - panelW / 2;
        panelY = this.height / 2 - panelH / 2;

        int cx = this.width / 2 - BTN_W / 2;
        int hud = panelY + PANEL_PAD;
        int blacklist = hud + BTN_H + BTN_GAP;
        int done = blacklist + BTN_H + BTN_GAP;

        addRenderableWidget(Button.builder(Component.translatable("cf.config.hud"),
                b -> minecraft.setScreen(new CravingHudScreen(this)))
                .bounds(cx, hud, BTN_W, BTN_H).build());

        addRenderableWidget(Button.builder(Component.translatable("cf.config.blacklist"),
                b -> minecraft.setScreen(new CravingBlacklistScreen(this)))
                .bounds(cx, blacklist, BTN_W, BTN_H).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(cx, done, BTN_W, BTN_H).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        // Opaque control panel (Iris-style), matching the HUD editor.
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF000000);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1E1E22);
        g.renderOutline(panelX, panelY, panelW, panelH, 0xFF3A3A42);

        super.render(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(font, this.title, this.width / 2, panelY - 16, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        FDConfigs.save();
        if (minecraft != null && parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }
}
