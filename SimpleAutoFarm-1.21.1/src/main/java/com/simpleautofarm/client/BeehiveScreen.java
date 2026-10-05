package com.simpleautofarm.client;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.menu.BeehiveMenu;
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

/**
 * Screen of the simple auto beehive.  It reuses the farm artwork unchanged: the machine slots
 * occupy the same place as on the farm, and the three-row band in the middle is shared between
 * the products (page 0) and the bee-food storage (page 1).  The page is toggled by a second
 * button under the auto-eject button; only the band slots move, everything else stays put.
 */
public class BeehiveScreen extends AbstractContainerScreen<BeehiveMenu> {

    private static final ResourceLocation TEXTURE = SimpleAutoFarm.prefix("textures/gui/auto_farm.png");
    private static final ResourceLocation UPGRADE_TEXTURE = SimpleAutoFarm.prefix("textures/gui/upgrade_slots.png");
    private static final ResourceLocation COMB_SLOT_TEXTURE = SimpleAutoFarm.prefix("textures/gui/comb_slot.png");
    private static final ResourceLocation UPGRADE_PANEL_TEXTURE = SimpleAutoFarm.prefix("textures/gui/upgrade_panel_tall.png");
    private static final ResourceLocation AUTO_EJECT_TEXTURE = SimpleAutoFarm.prefix("textures/gui/auto_eject_button.png");
    private static final ResourceLocation PAGE_TEXTURE = SimpleAutoFarm.prefix("textures/gui/page_button.png");

    // split bars below the band: progress (left half), energy (right half) - same as the farm
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

    // upgrade column (right of the main panel): 3 upgrades + the comb-block upgrade,
    // then the two buttons at y=89/107 - the strip ends 4 rows under the lower button,
    // exactly like the farm screen's 24x77 strip ends under its single button
    private static final int PANEL_X = 180;
    private static final int PANEL_Y = 14;
    private static final int PANEL_H = 113;
    private static final int SLOT_X = 184;
    private static final int COMB_SLOT_Y = 72;

    // shared three-row band (products on page 0, bee food on page 1)
    private static final int BAND_X = 7;
    private static final int BAND_Y = 36;
    private static final int BAND_W = 8 + 9 * 18 - 7;
    private static final int BAND_H = 54;

    // buttons stack in the upgrade column's 18px grid, right below the comb-block slot:
    // its art occupies y=71..88, so the farm's cell pitch (17 + i * 18) puts the buttons at 89 and 107
    private static final int BUTTON_X = 184;
    private static final int EJECT_Y = 89;
    private static final int PAGE_Y = 107;
    private static final int BUTTON_SIZE = 16;

    // labels
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_Y = 108;
    /** Right edge of the honey line: the main panel's inner margin (image width 176 - 8). */
    private static final int HONEY_RIGHT_EDGE = 168;

    public BeehiveScreen(BeehiveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 204;
        this.imageHeight = 200;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(new AutoEjectButton(this.leftPos + BUTTON_X, this.topPos + EJECT_Y, this));
        this.addRenderableWidget(new PageButton(this.leftPos + BUTTON_X, this.topPos + PAGE_Y, this));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // main panel: the farm artwork, unchanged
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, 176, this.imageHeight);

        // upgrade column panel: the farm strip extended downwards so it reaches the page button
        graphics.blit(UPGRADE_PANEL_TEXTURE, this.leftPos + PANEL_X, this.topPos + PANEL_Y, 0, 0, 24, PANEL_H);

        // upgrade slot backgrounds: the three upgrades reuse the farm's frames, the comb-block
        // upgrade slot has its own frame with a greyed-out Block Upgrade hint behind the item
        for (int i = 0; i < 4; i++) {
            int y = this.topPos + 17 + i * 18;
            if (i < 3) {
                graphics.blit(UPGRADE_TEXTURE, this.leftPos + SLOT_X - 1, y, 0, i * 18, 18, 18);
            } else {
                graphics.blit(COMB_SLOT_TEXTURE, this.leftPos + SLOT_X - 1, y, 0, 0, 18, 18);
            }
        }

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
        Component title = this.title;
        if (this.menu.isFoodPage()) {
            // the band is showing the bee-food storage, say so in the title
            title = Component.empty().append(title).append(" · ")
                    .append(Component.translatable("gui.simpleautofarm.beehive.food"));
        }
        graphics.drawString(this.font, title, 8, TITLE_Y, 4210752, false);
        graphics.drawString(this.font, this.playerInventoryTitle, 8, INVENTORY_LABEL_Y, 4210752, false);
        if (this.menu.getHoneyAmount() > 0) {
            // The honey line sits on the inventory-label row, right aligned: as a title suffix the
            // English text ("Simple Auto Beehive · 3.9K/32.0K mB honey") runs past the panel edge.
            // The numbers use the K/M form (not the k/m one) so they cannot be read as "mB".
            Component honey = Component.translatable("gui.simpleautofarm.beehive.honey",
                    NumberFormatter.abbreviateEnergy(this.menu.getHoneyAmount()),
                    NumberFormatter.abbreviateEnergy(this.menu.getHoneyCapacity()));
            graphics.drawString(this.font, honey, HONEY_RIGHT_EDGE - this.font.width(honey),
                    INVENTORY_LABEL_Y, 4210752, false);
        }
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
        if (x >= BUTTON_X && x < BUTTON_X + BUTTON_SIZE && y >= EJECT_Y && y < EJECT_Y + BUTTON_SIZE) {
            String key = "tooltip.simpleautofarm.auto_eject.off";
            if (this.menu.isAutoEject()) {
                key = this.menu.isAeConnected()
                        ? "tooltip.simpleautofarm.auto_eject.on_ae"
                        : "tooltip.simpleautofarm.auto_eject.on";
            }
            graphics.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
            return;
        }
        if (x >= BUTTON_X && x < BUTTON_X + BUTTON_SIZE && y >= PAGE_Y && y < PAGE_Y + BUTTON_SIZE) {
            String key = this.menu.isFoodPage()
                    ? "tooltip.simpleautofarm.beehive.page_output"
                    : "tooltip.simpleautofarm.beehive.page_food";
            graphics.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
            return;
        }
        if (x >= SLOT_X - 1 && x < SLOT_X + 17 && y >= COMB_SLOT_Y - 1 && y < COMB_SLOT_Y + 17
                && this.menu.isCombUpgradeEmpty()) {
            // only while the slot is empty: with an upgrade installed its own tooltip must win
            graphics.renderTooltip(this.font, Component.translatable("tooltip.simpleautofarm.beehive.slot.comb"), mouseX, mouseY);
            return;
        }
        if (x >= BAND_X && x < BAND_X + BAND_W && y >= 17 && y < 35 && !this.menu.isResourceBeesLoaded()) {
            graphics.renderTooltip(this.font, Component.translatable("tooltip.simpleautofarm.beehive.requires_pb"), mouseX, mouseY);
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
        private final BeehiveScreen screen;

        AutoEjectButton(int x, int y, BeehiveScreen screen) {
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

    /** Switches the shared three-row band between the products and the bee-food storage. */
    private static class PageButton extends AbstractButton {
        private final BeehiveScreen screen;

        PageButton(int x, int y, BeehiveScreen screen) {
            super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.empty());
            this.screen = screen;
        }

        @Override
        public void onPress() {
            this.screen.menu.togglePage();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean food = this.screen.menu.isFoodPage();
            graphics.blit(PAGE_TEXTURE, this.getX(), this.getY(), food ? BUTTON_SIZE : 0, 0, BUTTON_SIZE, BUTTON_SIZE);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
