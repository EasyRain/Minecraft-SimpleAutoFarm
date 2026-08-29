package com.simpleautofarm.client;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.menu.FarmMenu;
import com.simpleautofarm.util.NumberFormatter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.List;

public class FarmScreen extends AbstractContainerScreen<FarmMenu> {

    private static final Identifier TEXTURE = SimpleAutoFarm.prefix("textures/gui/auto_farm.png");
    private static final Identifier UPGRADE_TEXTURE = SimpleAutoFarm.prefix("textures/gui/upgrade_slots.png");
    private static final Identifier UPGRADE_PANEL_TEXTURE = SimpleAutoFarm.prefix("textures/gui/upgrade_panel.png");
    private static final Identifier AUTO_EJECT_TEXTURE = SimpleAutoFarm.prefix("textures/gui/auto_eject_button.png");

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
        super(menu, inventory, title, 204, 200);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(new AutoEjectButton(this.leftPos + BUTTON_X, this.topPos + BUTTON_Y, this));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);

        // main farm panel (176 wide)
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, 176, this.imageHeight, 256, 256);

        // upgrade column panel (bordered, right of the main panel)
        graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADE_PANEL_TEXTURE, this.leftPos + PANEL_X, this.topPos + PANEL_Y, 0.0F, 0.0F, 24, 77, 256, 256);

        // upgrade slot backgrounds (3 vertical slots)
        graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADE_TEXTURE, this.leftPos + SLOT_X - 1, this.topPos + 17, 0.0F, 0.0F, 18, 18, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADE_TEXTURE, this.leftPos + SLOT_X - 1, this.topPos + 35, 0.0F, 18.0F, 18, 18, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADE_TEXTURE, this.leftPos + SLOT_X - 1, this.topPos + 53, 0.0F, 36.0F, 18, 18, 256, 256);

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
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(this.font, this.title, 8, 6, 0xFF404040, false);
        graphics.text(this.font, this.playerInventoryTitle, 8, 108, 0xFF404040, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = mouseX - this.leftPos;
        int y = mouseY - this.topPos;
        if (x >= BAR_X && x < BAR_X + BAR_W && y >= BAR_Y && y < BAR_Y + BAR_H) {
            graphics.setComponentTooltipForNextFrame(this.font, List.of(
                    Component.translatable("tooltip.simpleautofarm.energy",
                            NumberFormatter.abbreviateEnergy(this.menu.getEnergy()),
                            NumberFormatter.abbreviateEnergy(this.menu.getEnergyCapacity())),
                    Component.translatable("tooltip.simpleautofarm.energy_use",
                            NumberFormatter.abbreviateEnergy(this.menu.getEnergyConsumptionPerTick()))
            ), mouseX, mouseY);
            return;
        }
        if (x >= PROG_X && x < PROG_X + PROG_W && y >= PROG_Y && y < PROG_Y + PROG_H) {
            graphics.setTooltipForNextFrame(this.font,
                    Component.translatable("tooltip.simpleautofarm.progress", this.menu.getProgress(), this.menu.getProductionTicks()),
                    mouseX, mouseY);
            return;
        }
        if (x >= BUTTON_X && x < BUTTON_X + BUTTON_SIZE && y >= BUTTON_Y && y < BUTTON_Y + BUTTON_SIZE) {
            graphics.setTooltipForNextFrame(this.font,
                    Component.translatable(this.menu.isAutoEject() ? "tooltip.simpleautofarm.auto_eject.on" : "tooltip.simpleautofarm.auto_eject.off"),
                    mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        ItemStack stack = slot.getItem();
        if (!stack.isEmpty() && stack.getCount() > 99) {
            graphics.item(stack, slot.x, slot.y, slot.x + slot.y * this.imageWidth);
            renderScaledStackCount(graphics, NumberFormatter.abbreviate(stack.getCount()), slot.x, slot.y);
        } else {
            super.extractSlot(graphics, slot, mouseX, mouseY);
        }
    }

    /** Renders a stack count scaled to fit within the 16px slot (mirrors Sophisticated Backpacks). */
    private void renderScaledStackCount(GuiGraphicsExtractor graphics, String text, int x, int y) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        float scale = Math.min(1.0F, 16.0F / (float) this.font.width(text));
        if (scale < 1.0F) {
            pose.scale(scale, scale);
        }
        float drawX = ((float) (x + 19 - 2) - this.font.width(text) * scale) / scale;
        float drawY = ((float) (y + 6 + 3) + (1.0F / (scale * scale) - 1.0F)) / scale;
        graphics.text(this.font, text, Math.round(drawX), Math.round(drawY), 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    private static class AutoEjectButton extends AbstractButton {
        private final FarmScreen screen;

        AutoEjectButton(int x, int y, FarmScreen screen) {
            super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.empty());
            this.screen = screen;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            if (this.screen.minecraft.gameMode instanceof MultiPlayerGameMode gameMode) {
                gameMode.handleInventoryButtonClick(this.screen.menu.containerId, 0);
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            boolean on = this.screen.menu.isAutoEject();
            graphics.blit(RenderPipelines.GUI_TEXTURED, AUTO_EJECT_TEXTURE, this.getX(), this.getY(), on ? 16.0F : 0.0F, 0.0F, 16, 16, 256, 256);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
