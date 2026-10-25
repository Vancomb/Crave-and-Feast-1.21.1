package com.skps2010.client;

import com.skps2010.CravingManager;
import com.skps2010.FDConfigs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-game editor for the craving pool. Lists every cravable food (plus any
 * manually added items); click a row to toggle whether it may be chosen as a
 * craving. The box at the bottom force-adds an item by id to the pool, for
 * items that aren't auto-detected as food. Backed by {@link FDConfigs#CFG},
 * saved on close.
 */
public class CravingBlacklistScreen extends Screen {
    private static final int ROW_H = 20;
    private static final int LIST_TOP = 46;
    private static final int BOTTOM_BAR = 72;
    private static final int SCROLLBAR_W = 6;

    private final Screen parent;

    private final List<Item> allFoods = new ArrayList<>();
    private final List<Item> filtered = new ArrayList<>();
    private EditBox search;
    private EditBox addField;
    private int scroll; // pixels
    private boolean draggingScrollbar;
    private int scrollGrab; // cursor offset within the thumb while dragging
    private Component status; // transient feedback for the add field

    public CravingBlacklistScreen(Screen parent) {
        super(Component.translatable("cf.blacklist.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (allFoods.isEmpty()) {
            for (Item item : BuiltInRegistries.ITEM) {
                if (CravingManager.isCravableFood(item)) allFoods.add(item);
            }
            // Include manually whitelisted items that aren't foods so they show up here too.
            for (String s : FDConfigs.CFG.cravingWhitelist) {
                Item item = resolve(s);
                if (item != Items.AIR && !allFoods.contains(item)) allFoods.add(item);
            }
            allFoods.sort((a, b) -> nameOf(a).compareToIgnoreCase(nameOf(b)));
        }

        search = new EditBox(font, this.width / 2 - 150, 24, 300, 16,
                Component.translatable("cf.blacklist.search"));
        search.setHint(Component.translatable("cf.blacklist.search"));
        search.setResponder(s -> {
            applyFilter();
            scroll = 0;
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        int rowY = this.height - 52;
        addField = new EditBox(font, this.width / 2 - 150, rowY, 228, 18,
                Component.translatable("cf.blacklist.add_hint"));
        addField.setHint(Component.translatable("cf.blacklist.add_hint"));
        addField.setMaxLength(256);
        addRenderableWidget(addField);
        addRenderableWidget(Button.builder(Component.translatable("cf.blacklist.add"), b -> addTyped())
                .bounds(this.width / 2 + 84, rowY - 1, 66, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 75, this.height - 28, 150, 20).build());

        applyFilter();
    }

    private void applyFilter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (Item item : allFoods) {
            if (q.isEmpty()
                    || nameOf(item).toLowerCase(Locale.ROOT).contains(q)
                    || CravingManager.idOf(item).toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(item);
            }
        }
    }

    private static String nameOf(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    private static String normalize(String s) {
        s = s.trim();
        return s.contains(":") ? s : "minecraft:" + s;
    }

    /** Resolves an id (namespace optional) to a registered item, or AIR if none. */
    private static Item resolve(String s) {
        if (s == null) return Items.AIR;
        ResourceLocation rl = ResourceLocation.tryParse(normalize(s));
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return Items.AIR;
        return BuiltInRegistries.ITEM.get(rl);
    }

    /* ------------------------- add to pool ------------------------- */

    private void addTyped() {
        String raw = addField.getValue().trim();
        if (raw.isEmpty()) return;
        String id = normalize(raw);
        Item item = resolve(id);
        if (item == Items.AIR) {
            status = Component.translatable("cf.blacklist.add_invalid").withStyle(s -> s.withColor(0xFF6B6B));
            return;
        }
        if (allFoods.contains(item)) {
            status = Component.translatable("cf.blacklist.add_exists", nameOf(item)).withStyle(s -> s.withColor(0xFFD37C));
        } else {
            FDConfigs.CFG.cravingWhitelist.add(id);
            allFoods.add(item);
            allFoods.sort((a, b) -> nameOf(a).compareToIgnoreCase(nameOf(b)));
            status = Component.translatable("cf.blacklist.add_ok", nameOf(item)).withStyle(s -> s.withColor(0x7CC47C));
        }
        // Make sure it isn't simultaneously blacklisted.
        FDConfigs.CFG.cravingBlacklist.removeIf(s -> s != null && normalize(s).equals(id));
        addField.setValue("");
        applyFilter();
    }

    /* ------------------------- blacklist membership ------------------------- */

    private boolean isBlacklisted(Item item) {
        String id = CravingManager.idOf(item);
        for (String s : FDConfigs.CFG.cravingBlacklist) {
            if (s != null && normalize(s).equals(id)) return true;
        }
        return false;
    }

    private void toggle(Item item) {
        String id = CravingManager.idOf(item);
        var list = FDConfigs.CFG.cravingBlacklist;
        if (isBlacklisted(item)) {
            list.removeIf(s -> s != null && normalize(s).equals(id));
        } else {
            list.add(id);
        }
    }

    /* ------------------------- input ------------------------- */

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (addField != null && addField.isFocused()
                && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            addTyped();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        // Grab the scrollbar (thumb drags; clicking the track jumps the thumb to the cursor).
        if (maxScroll() > 0 && mouseX >= scrollbarX() && mouseX <= scrollbarX() + SCROLLBAR_W
                && mouseY >= LIST_TOP && mouseY < listBottom()) {
            int ty = thumbY(), th = thumbH();
            if (mouseY >= ty && mouseY < ty + th) {
                scrollGrab = (int) (mouseY - ty);
            } else {
                scrollGrab = th / 2;
                setScrollFromThumb(mouseY - scrollGrab);
            }
            draggingScrollbar = true;
            return true;
        }

        // Toggle a row.
        if (mouseX >= listX() && mouseX <= listX() + listW()
                && mouseY >= LIST_TOP && mouseY < listBottom()) {
            int idx = (int) ((mouseY - LIST_TOP + scroll) / ROW_H);
            if (idx >= 0 && idx < filtered.size()) {
                toggle(filtered.get(idx));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            setScrollFromThumb(mouseY - scrollGrab);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (mouseX >= listX() && mouseX <= listX() + listW()
                && mouseY >= LIST_TOP && mouseY < listBottom()) {
            scroll = Mth.clamp(scroll - (int) (dy * ROW_H), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    /* ------------------------- scrollbar geometry ------------------------- */

    private int scrollbarX() {
        return listX() + listW() + 2;
    }

    private int thumbH() {
        int trackH = listBottom() - LIST_TOP;
        if (filtered.isEmpty()) return trackH;
        return Mth.clamp(trackH * trackH / (filtered.size() * ROW_H), 16, trackH);
    }

    private int thumbY() {
        int max = maxScroll();
        if (max <= 0) return LIST_TOP;
        int trackH = listBottom() - LIST_TOP;
        return LIST_TOP + (trackH - thumbH()) * scroll / max;
    }

    private void setScrollFromThumb(double thumbTop) {
        int max = maxScroll();
        int range = (listBottom() - LIST_TOP) - thumbH();
        if (max <= 0 || range <= 0) {
            scroll = 0;
            return;
        }
        double rel = (thumbTop - LIST_TOP) / range;
        scroll = Mth.clamp((int) Math.round(rel * max), 0, max);
    }

    private int listW() {
        return 320;
    }

    private int listX() {
        return this.width / 2 - listW() / 2;
    }

    private int listBottom() {
        return this.height - BOTTOM_BAR;
    }

    private int maxScroll() {
        return Math.max(0, filtered.size() * ROW_H - (listBottom() - LIST_TOP));
    }

    /* ------------------------- render ------------------------- */

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(font, this.title, this.width / 2, 9, 0xFFFFFFFF);

        int x = listX(), w = listW(), top = LIST_TOP, bottom = listBottom();

        // List backdrop.
        g.fill(x - 2, top - 2, x + w + 2, bottom + 2, 0xFF000000);
        g.fill(x - 1, top - 1, x + w + 1, bottom + 1, 0xC0101014);

        g.enableScissor(x, top, x + w, bottom);
        int blocked = 0;
        for (int i = 0; i < filtered.size(); i++) {
            Item item = filtered.get(i);
            boolean bl = isBlacklisted(item);
            if (bl) blocked++;

            int ry = top - scroll + i * ROW_H;
            if (ry + ROW_H < top || ry > bottom) continue;

            boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY < ry + ROW_H
                    && mouseY >= top && mouseY < bottom;
            g.fill(x, ry, x + w, ry + ROW_H, hover ? 0x40FFFFFF : (i % 2 == 0 ? 0x18FFFFFF : 0x10FFFFFF));

            g.renderItem(new ItemStack(item), x + 2, ry + 2);
            int nameColor = bl ? 0xFF888888 : 0xFFFFFFFF;
            Component name = bl
                    ? Component.literal(nameOf(item)).withStyle(s -> s.withStrikethrough(true))
                    : Component.literal(nameOf(item));
            g.drawString(font, name, x + 24, ry + 6, nameColor);

            Component tag = Component.translatable(bl ? "cf.blacklist.blocked" : "cf.blacklist.allowed");
            int tagColor = bl ? 0xFFFF6B6B : 0xFF7CC47C;
            g.drawString(font, tag, x + w - font.width(tag) - 6, ry + 6, tagColor);
        }
        g.disableScissor();

        // Scrollbar (thumb is draggable).
        if (maxScroll() > 0) {
            int sx = scrollbarX();
            int barY = thumbY(), barH = thumbH();
            boolean over = mouseX >= sx && mouseX <= sx + SCROLLBAR_W && mouseY >= barY && mouseY < barY + barH;
            int barColor = (draggingScrollbar || over) ? 0xFFFFFFFF : 0xFFBBBBBB;
            g.fill(sx, top, sx + SCROLLBAR_W, bottom, 0x40FFFFFF);
            g.fill(sx, barY, sx + SCROLLBAR_W, barY + barH, barColor);
        }

        // Status line: transient add feedback, otherwise the blocked count.
        Component line = status != null ? status
                : Component.translatable("cf.blacklist.count", blocked, allFoods.size()).withStyle(s -> s.withColor(0xAAAAAA));
        g.drawCenteredString(font, line, this.width / 2, this.height - 66, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        FDConfigs.save();
        // In single-player, rebuild the pool right away so changes apply without a reload.
        if (minecraft != null) {
            var server = minecraft.getSingleplayerServer();
            if (server != null) server.execute(CravingManager::rebuildAllowedFoods);
        }
        if (minecraft != null && parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }
}
