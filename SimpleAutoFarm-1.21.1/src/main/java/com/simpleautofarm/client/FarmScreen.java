package com.simpleautofarm.client;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.menu.FarmMenu;
import com.simpleautofarm.util.NumberFormatter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class FarmScreen extends AbstractContainerScreen<FarmMenu> {

    private static final ResourceLocation TEXTURE = SimpleAutoFarm.prefix("textures/gui/auto_farm.png");
    private static final ResourceLocation UPGRADE_TEXTURE = SimpleAutoFarm.prefix("textures/gui/upgrade_slots.png");
    private static final ResourceLocation UPGRADE_PANEL_TEXTURE = SimpleAutoFarm.prefix("textures/gui/upgrade_panel.png");
    private static final ResourceLocation AUTO_EJECT_TEXTURE = SimpleAutoFarm.prefix("textures/gui/auto_eject_button.png");

    // split bars below the machine slots: progress (left half), energy (right half)
    private static final int PROG_X = 7;
    private static final int PROG_Y = 96;
    private static final int PROG_W = 80;
    private static final int PROG_H = 8;
    private static final int PROG_FILL_COLOR = 0xFFE07B2A;

    private static final int BAR_X = 88;
    private static final int BAR_Y = 96;
    private static final int BAR_W = 81;
    private static final int BAR_H = 8;
    private static final int BAR_FILL_COLOR = 0xFF3FBF3F;

    // upgrade column (right of the main panel)
    private static final int PANEL_X = 180;
    private static final int PANEL_Y = 14;
    private static final int SLOT_X = 184;

    // auto-eject button (below the upgrade slots, aligned with the 4th slot row)
    private static final int BUTTON_X = SLOT_X;
    private static final int BUTTON_Y = 71;
    private static final int BUTTON_SIZE = 16;

    public FarmScreen(FarmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 204;
        this.imageHeight = 200;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(new AutoEjectButton(this.leftPos + BUTTON_X, this.topPos + BUTTON_Y, this));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // main farm panel (176 wide)
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, 176, this.imageHeight);

        // upgrade column panel (bordered, right of the main panel)
        graphics.blit(UPGRADE_PANEL_TEXTURE, this.leftPos + PANEL_X, this.topPos + PANEL_Y, 0, 0, 24, 77);

        // upgrade slot backgrounds (3 vertical slots)
        graphics.blit(UPGRADE_TEXTURE, this.leftPos + SLOT_X - 1, this.topPos + 17, 0, 0, 18, 18);
        graphics.blit(UPGRADE_TEXTURE, this.leftPos + SLOT_X - 1, this.topPos + 35, 0, 18, 18, 18);
        graphics.blit(UPGRADE_TEXTURE, this.leftPos + SLOT_X - 1, this.topPos + 53, 0, 36, 18, 18);

        // progress bar (left half)
        int progress = this.menu.getProgress();
        int progFillable = PROG_W - 2;
        int productionTicks = Math.max(1, this.menu.getProductionTicks());
        int progFilled = Math.min((int) ((long) progress * progFillable / productionTicks), progFillable);
        if (progFilled > 0) {
            graphics.fill(this.leftPos + PROG_X + 1, this.topPos + PROG_Y + 1,
                    this.leftPos + PROG_X + 1 + progFilled, this.topPos + PROG_Y + PROG_H - 1, PROG_FILL_COLOR);
        }

        // energy bar (right half, horizontal)
        int energy = this.menu.getEnergy();
        int fillable = BAR_W - 2;
        int capacity = Math.max(1, this.menu.getEnergyCapacity());
        int filled = Math.min((int) ((long) energy * fillable / capacity), fillable);
        if (filled > 0) {
            graphics.fill(this.leftPos + BAR_X + 1, this.topPos + BAR_Y + 1,
                    this.leftPos + BAR_X + 1 + filled, this.topPos + BAR_Y + BAR_H - 1, BAR_FILL_COLOR);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 8, 6, 4210752, false);
        graphics.drawString(this.font, this.playerInventoryTitle, 8, 108, 4210752, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = mouseX - this.leftPos;
        int y = mouseY - this.topPos;
        if (x >= BAR_X && x < BAR_X + BAR_W && y >= BAR_Y && y < BAR_Y + BAR_H) {
            graphics.renderComponentTooltip(this.font, List.of(
                    Component.translatable("tooltip.simpleautofarm.energy",
                            NumberFormatter.abbreviateEnergy(this.menu.getEnergy()),
                            NumberFormatter.abbreviateEnergy(this.menu.getEnergyCapacity())),
                    Component.translatable("tooltip.simpleautofarm.energy_use",
                            NumberFormatter.abbreviateEnergy(this.menu.getEnergyConsumptionPerTick()))
            ), mouseX, mouseY);
            return;
        }
        if (x >= PROG_X && x < PROG_X + PROG_W && y >= PROG_Y && y < PROG_Y + PROG_H) {
            graphics.renderTooltip(this.font,
                    Component.translatable("tooltip.simpleautofarm.progress", this.menu.getProgress(), this.menu.getProductionTicks()),
                    mouseX, mouseY);
            return;
        }
        if (x >= BUTTON_X && x < BUTTON_X + BUTTON_SIZE && y >= BUTTON_Y && y < BUTTON_Y + BUTTON_SIZE) {
            String key = "tooltip.simpleautofarm.auto_eject.off";
            if (this.menu.isAutoEject()) {
                key = this.menu.isAeConnected()
                        ? "tooltip.simpleautofarm.auto_eject.on_ae"
                        : "tooltip.simpleautofarm.auto_eject.on";
            }
            graphics.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        ItemStack stack = slot.getItem();
        if (!stack.isEmpty() && stack.getCount() > 99) {
            graphics.renderItem(stack, slot.x, slot.y, slot.x + slot.y * this.imageWidth);
            renderStackCount(graphics, NumberFormatter.abbreviate(stack.getCount()), slot.x, slot.y);
        } else {
            super.renderSlot(graphics, slot);
        }
    }

    /** Renders a stack count scaled to fit within the 16px slot (mirrors Sophisticated Backpacks). */
    private void renderStackCount(GuiGraphics graphics, String text, int x, int y) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 200.0F);
        float scale = Math.min(1.0F, 16.0F / (float) this.font.width(text));
        if (scale < 1.0F) {
            pose.scale(scale, scale, 1.0F);
        }
        float drawX = ((float) (x + 19 - 2) - this.font.width(text) * scale) / scale;
        float drawY = ((float) (y + 6 + 3) + (1.0F / (scale * scale) - 1.0F)) / scale;
        graphics.drawString(this.font, text, drawX, drawY, 0xFFFFFF, true);
        pose.popPose();
    }

    private static class AutoEjectButton extends AbstractButton {
        private final FarmScreen screen;

        AutoEjectButton(int x, int y, FarmScreen screen) {
            super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.empty());
            this.screen = screen;
        }

        @Override
        public void onPress() {
            if (this.screen.minecraft.gameMode instanceof MultiPlayerGameMode gameMode) {
                gameMode.handleInventoryButtonClick(this.screen.menu.containerId, 0);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean on = this.screen.menu.isAutoEject();
            graphics.blit(AUTO_EJECT_TEXTURE, this.getX(), this.getY(), on ? BUTTON_SIZE : 0, 0, BUTTON_SIZE, BUTTON_SIZE);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
