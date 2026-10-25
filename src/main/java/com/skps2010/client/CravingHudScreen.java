package com.skps2010.client;

import com.skps2010.FDConfigs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * In-game editor for the two HUD modules: the on-screen HUD (white) and the
 * inventory HUD (blue). Drag a module to move it, drag its bottom-right corner
 * to resize, and click the eye icon to hide/show it. Saved on close.
 */
public class CravingHudScreen extends Screen {
    private enum Mod {SCREEN, INVENTORY}

    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 3.0f;
    private static final int HANDLE = 8;   // resize-corner hit radius

    private static final int EYE_W = 16, EYE_H = 16;
    private static final int EYE_WHITE = 0xFFFFFFFF, EYE_GRAY = 0xFF3F3F3F;
    // Pixel-art visibility icons (white '#' + dark-gray 'o'), traced from the reference art.
    private static final String[] EYE = {
            "................",
            "................",
            "................",
            "................",
            "......#####.....",
            ".....#.oooo#....",
            "....#.o###..#...",
            "...#.o.###o..#..",
            "....#...##o.#.o.",
            ".....#...oo#.o..",
            "......#####.o...",
            ".......ooooo....",
            "................",
            "................",
            "................",
            "................",
    };
    // Same eye with a diagonal slash through it, shown when the module is hidden.
    private static final String[] EYE_OFF = {
            "................",
            "................",
            ".............#..",
            "............#.o.",
            "......####.#.o..",
            ".....#.ooo#.o...",
            "....#.o#.#.o#...",
            "...#.o..#.o..#..",
            "....#..#.#..#.o.",
            ".....o#.o.o#.o..",
            ".....#.####.o...",
            "....#.o.oooo....",
            "...#.o..........",
            "....o...........",
            "................",
            "................",
    };

    private static final int BTN_W = 150, BTN_H = 20, BTN_GAP = 6, PANEL_PAD = 12;

    private final Screen parent;

    private ItemStack preview = ItemStack.EMPTY;
    private Mod active;
    private boolean dragging;
    private boolean resizing;
    private double dragOffX, dragOffY;

    private int panelX, panelY, panelW, panelH;

    public CravingHudScreen(Screen parent) {
        super(Component.translatable("cf.hud.editor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        preview = ClientPayloadHandler.current.isEmpty()
                ? new ItemStack(Items.APPLE)
                : ClientPayloadHandler.current;

        int blockH = 2 * BTN_H + BTN_GAP;
        panelW = BTN_W + 2 * PANEL_PAD;
        panelH = blockH + 2 * PANEL_PAD;
        panelX = this.width / 2 - panelW / 2;
        panelY = this.height - panelH - 8;

        int cx = this.width / 2 - BTN_W / 2;
        int reset = panelY + PANEL_PAD;
        int done = reset + BTN_H + BTN_GAP;

        addRenderableWidget(Button.builder(Component.translatable("cf.hud.editor.reset"), b -> {
            var c = FDConfigs.CFG;
            c.hudX = 126; c.hudY = 7; c.hudScale = 1.0f;
            c.invHudX = 355; c.invHudY = 104; c.invHudScale = 1.0f;
        }).bounds(cx, reset, BTN_W, BTN_H).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(cx, done, BTN_W, BTN_H).build());
    }

    /* ------------------------- per-module config accessors ------------------------- */

    private int getX(Mod m) { return m == Mod.SCREEN ? FDConfigs.CFG.hudX : FDConfigs.CFG.invHudX; }
    private int getY(Mod m) { return m == Mod.SCREEN ? FDConfigs.CFG.hudY : FDConfigs.CFG.invHudY; }
    private float getScale(Mod m) { return m == Mod.SCREEN ? FDConfigs.CFG.hudScale : FDConfigs.CFG.invHudScale; }
    private boolean isEnabled(Mod m) { return m == Mod.SCREEN ? FDConfigs.CFG.hudEnabled : FDConfigs.CFG.invHudEnabled; }

    private void setX(Mod m, int v) { if (m == Mod.SCREEN) FDConfigs.CFG.hudX = v; else FDConfigs.CFG.invHudX = v; }
    private void setY(Mod m, int v) { if (m == Mod.SCREEN) FDConfigs.CFG.hudY = v; else FDConfigs.CFG.invHudY = v; }
    private void setScale(Mod m, float v) { if (m == Mod.SCREEN) FDConfigs.CFG.hudScale = v; else FDConfigs.CFG.invHudScale = v; }
    private void toggleEnabled(Mod m) {
        if (m == Mod.SCREEN) FDConfigs.CFG.hudEnabled = !FDConfigs.CFG.hudEnabled;
        else FDConfigs.CFG.invHudEnabled = !FDConfigs.CFG.invHudEnabled;
    }

    private int accent(Mod m) { return m == Mod.SCREEN ? CraveAndFeastClient.SCREEN_ACCENT : CraveAndFeastClient.INV_ACCENT; }
    private String labelKey(Mod m) { return m == Mod.SCREEN ? "cf.hud.editor.module.screen" : "cf.hud.editor.module.inventory"; }

    private int mw(Mod m) { return (int) (CraveAndFeastClient.hudWidth(font, preview) * getScale(m)); }
    private int mh(Mod m) { return (int) (CraveAndFeastClient.hudHeight() * getScale(m)); }

    /** Y of the eye/label strip: above the box, or below it when the box hugs the top edge. */
    private int labelY(Mod m) {
        int y = getY(m);
        return (y - (EYE_H + 2) >= 1) ? y - (EYE_H + 2) : y + mh(m) + 2;
    }

    /* ------------------------- input ------------------------- */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        // The eye toggles visibility; it takes priority over dragging the body.
        for (Mod m : Mod.values()) {
            int ex = getX(m), ey = labelY(m);
            if (mouseX >= ex - 1 && mouseX <= ex + EYE_W + 1 && mouseY >= ey - 1 && mouseY <= ey + EYE_H + 1) {
                toggleEnabled(m);
                return true;
            }
        }
        // Resize corners next.
        for (Mod m : Mod.values()) {
            int cx = getX(m) + mw(m), cy = getY(m) + mh(m);
            if (mouseX >= cx - HANDLE && mouseX <= cx + HANDLE && mouseY >= cy - HANDLE && mouseY <= cy + HANDLE) {
                active = m;
                resizing = true;
                return true;
            }
        }
        // Then dragging the body.
        for (Mod m : Mod.values()) {
            int x = getX(m), y = getY(m);
            if (mouseX >= x && mouseX <= x + mw(m) && mouseY >= y && mouseY <= y + mh(m)) {
                active = m;
                dragging = true;
                dragOffX = mouseX - x;
                dragOffY = mouseY - y;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (active != null && dragging) {
            setX(active, Mth.clamp((int) Math.round(mouseX - dragOffX), 0, Math.max(0, this.width - mw(active))));
            setY(active, Mth.clamp((int) Math.round(mouseY - dragOffY), 0, Math.max(0, this.height - mh(active))));
            return true;
        }
        if (active != null && resizing) {
            int baseW = CraveAndFeastClient.hudWidth(font, preview);
            setScale(active, Mth.clamp((float) (mouseX - getX(active)) / baseW, MIN_SCALE, MAX_SCALE));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        resizing = false;
        active = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /* ------------------------- render ------------------------- */

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        // Opaque control panel (Iris-style).
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF000000);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1E1E22);
        g.renderOutline(panelX, panelY, panelW, panelH, 0xFF3A3A42);

        super.render(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("cf.hud.editor.hint"), this.width / 2, 26, 0xFFAAAAAA);

        drawModule(g, Mod.SCREEN);
        drawModule(g, Mod.INVENTORY);
    }

    private void drawModule(GuiGraphics g, Mod m) {
        int x = getX(m), y = getY(m), w = mw(m), h = mh(m);
        boolean enabled = isEnabled(m);
        boolean busy = active == m && (dragging || resizing);
        int accent = accent(m);
        int outline = busy ? 0xFFFFFF00 : (enabled ? accent : 0xFF808080);

        g.fill(x, y, x + w, y + h, enabled ? 0x22FFFFFF : 0x12FFFFFF);
        g.renderOutline(x, y, w, h, outline);

        CraveAndFeastClient.renderHud(g, font, preview, x, y, getScale(m));

        // Eye visibility toggle + module label, next to the title.
        int ly = labelY(m);
        drawEye(g, x, ly, !enabled);
        g.drawString(font, Component.translatable(labelKey(m)), x + EYE_W + 2, ly + (EYE_H - font.lineHeight) / 2, enabled ? accent : 0xFF808080);
    }

    /** Draws the visibility eye, or the slashed eye when {@code hidden}, in its native white/gray. */
    private void drawEye(GuiGraphics g, int ox, int oy, boolean hidden) {
        String[] art = hidden ? EYE_OFF : EYE;
        for (int r = 0; r < art.length; r++) {
            String row = art[r];
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch == '#') g.fill(ox + c, oy + r, ox + c + 1, oy + r + 1, EYE_WHITE);
                else if (ch == 'o') g.fill(ox + c, oy + r, ox + c + 1, oy + r + 1, EYE_GRAY);
            }
        }
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
