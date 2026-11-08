package com.simpleautofarm.client;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.block.FluidFarmBlockEntity;
import com.simpleautofarm.menu.FluidFarmMenu;
import com.simpleautofarm.util.NumberFormatter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Screen of the simple auto fluid farm. The sample row, the container row and the upgrade column sit
 * where the farm's slots used to be; the two product rows the machine no longer uses carry nine
 * vertical tank bars instead, one per sample column and filled bottom-up with that tank's own fluid
 * (its still sprite and tint).
 */
public class FluidFarmScreen extends AbstractContainerScreen<FluidFarmMenu> {

    private static final ResourceLocation TEXTURE = SimpleAutoFarm.prefix("textures/gui/fluid_farm.png");
    private static final ResourceLocation UPGRADE_TEXTURE = SimpleAutoFarm.prefix("textures/gui/upgrade_slots.png");
    private static final ResourceLocation UPGRADE_PANEL_TEXTURE = SimpleAutoFarm.prefix("textures/gui/upgrade_panel.png");
    private static final ResourceLocation AUTO_EJECT_TEXTURE = SimpleAutoFarm.prefix("textures/gui/auto_eject_button.png");

    // split bars below the machine slots: progress (left half), energy (right half) - same as the farm
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

    // Nine vertical tank bars in the band the two product rows used to fill, one per sample column.
    // Every frame and its dark floor are baked into fluid_farm.png; only the fluid is drawn here.
    // The bars are 12px wide on the sample row's 18px pitch, so bar i is centred on slot i
    // (slot content x=8..23 -> centre 15.5; bar x=10..21 -> centre 15.5).
    private static final int TANK_COUNT = FluidFarmBlockEntity.TANK_SLOTS;
    private static final int TANK_X0 = 10;
    private static final int TANK_PITCH = 18;
    private static final int TANK_Y = 53;
    private static final int TANK_W = 12;
    private static final int TANK_H = 36;
    private static final int TANK_INNER_W = TANK_W - 2;
    private static final int TANK_INNER_H = TANK_H - 2;

    // upgrade column (right of the main panel)
    private static final int PANEL_X = 180;
    private static final int PANEL_Y = 14;
    private static final int SLOT_X = 184;

    // auto-eject button (below the upgrade slots, aligned with the 4th slot row)
    private static final int BUTTON_X = SLOT_X;
    private static final int BUTTON_Y = 71;
    private static final int BUTTON_SIZE = 16;

    // labels
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_Y = 108;

    /** Cached "does this texture file exist" lookups, so a missing fluid texture cannot log per frame. */
    private final Map<ResourceLocation, Boolean> textureChecks = new HashMap<>();

    public FluidFarmScreen(FluidFarmMenu menu, Inventory inventory, Component title) {
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
        // main panel, derived from the farm artwork: nine tank bars replace the two product rows
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

        for (int tank = 0; tank < TANK_COUNT; tank++) {
            this.renderTank(graphics, tank);
        }
    }

    /** Left edge of tank bar {@code tank}, aligned with the sample slot of the same column. */
    private static int tankX(int tank) {
        return TANK_X0 + tank * TANK_PITCH;
    }

    /**
     * Draws one tank bar bottom-up. The fluid's still sprite is repeated in whole tiles anchored at
     * the tank floor and the topmost tile is cropped to the fill height instead of being squashed
     * into it (the way Jade draws fluid gauges), so the pattern keeps its native scale at every fill
     * level. Every tile is the sprite itself, which keeps animated fluids animating. Fluids without
     * a usable still texture fall back to a plain tinted rectangle.
     */
    private void renderTank(GuiGraphics graphics, int tank) {
        int amount = this.menu.getTankAmount(tank);
        int capacity = Math.max(1, this.menu.getTankCapacity(tank));
        FluidStack stack = this.menu.getTankFluid(tank);
        if (stack.isEmpty() || amount <= 0) {
            return;
        }
        // Bottom-up fill; a non-empty tank always shows at least one pixel, and a full one (or a
        // capacity the creative upgrade blew up) fills the well completely.
        int fluidFilled = amount >= capacity
                ? TANK_INNER_H
                : Math.max(1, (int) ((long) amount * TANK_INNER_H / capacity));
        int innerX = this.leftPos + tankX(tank) + 1;
        int bottom = this.topPos + TANK_Y + 1 + TANK_INNER_H;
        int top = bottom - fluidFilled;
        int tint = fluidTint(stack);
        ResourceLocation still = stillTexture(stack);
        if (still == null || !textureExists(still)) {
            graphics.fill(innerX, top, innerX + TANK_INNER_W, bottom, tint);
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(still);
        // One tile is one full frame of the fluid texture (16px for the vanilla stills, a different
        // size for modded ones); the tile loop is anchored at the floor, so a growing level reveals
        // the pattern from the bottom up instead of rescaling it.
        int tile = Math.max(1, sprite.contents().height());
        // The still sprites are mostly white, so the fluid colour has to come from the vertex colour.
        float red = ((tint >> 16) & 0xFF) / 255.0F;
        float green = ((tint >> 8) & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;
        // The scissor crops the topmost tile to the fill height, so it shows only the bottom
        // (fluidFilled % tile) rows of the sprite instead of stretching them over the whole fill.
        graphics.enableScissor(innerX, top, innerX + TANK_INNER_W, bottom);
        for (int y = bottom - tile; y > top - tile; y -= tile) {
            graphics.blit(innerX, y, 0, TANK_INNER_W, tile, sprite, red, green, blue, 1.0F);
        }
        graphics.disableScissor();
    }

    /** Tint of the stored fluid, forced opaque. */
    private static int fluidTint(FluidStack stack) {
        int tint = IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(stack) | 0xFF000000;
        if (stack.getFluid().isSame(Fluids.WATER) && (tint & 0xFFFFFF) == 0xFFFFFF) {
            // Water takes its colour from the biome, which a GUI has none of: use the same constant
            // the block colour handler falls back to, so the bar is not plain white.
            return 0xFF3F76E4;
        }
        return tint;
    }

    /**
     * The fluid's still texture as an atlas sprite name ({@code block/water_still}); fluid types hand
     * out block-atlas names, which are also ordinary texture files.
     */
    private static ResourceLocation stillTexture(FluidStack stack) {
        return IClientFluidTypeExtensions.of(stack.getFluid()).getStillTexture(stack);
    }

    /**
     * Whether the sprite name resolves to a real texture file, i.e. {@code textures/<path>.png}. The
     * atlas itself returns a missing sprite instead of failing, so this is what decides the fallback.
     */
    private boolean textureExists(ResourceLocation sprite) {
        return this.textureChecks.computeIfAbsent(sprite, id -> {
            String path = id.getPath();
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                    path.startsWith("textures/") ? path : "textures/" + path + ".png");
            Minecraft client = Minecraft.getInstance();
            return client != null && client.getResourceManager().getResource(file).isPresent();
        });
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 8, TITLE_Y, 4210752, false);
        graphics.drawString(this.font, this.playerInventoryTitle, 8, INVENTORY_LABEL_Y, 4210752, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = mouseX - this.leftPos;
        int y = mouseY - this.topPos;
        for (int tank = 0; tank < TANK_COUNT; tank++) {
            int barX = tankX(tank);
            if (x >= barX && x < barX + TANK_W && y >= TANK_Y && y < TANK_Y + TANK_H) {
                FluidStack stack = this.menu.getTankFluid(tank);
                Component fluidName = stack.isEmpty()
                        ? Component.translatable("gui.simpleautofarm.fluid_farm.empty")
                        : stack.getHoverName();
                // One line, no slot number: the bars are told apart by their column, so the tooltip
                // only names the fluid and its level. The name is kept out of the lang key on purpose.
                // Both numbers take the unit of the tank's capacity, so the pair never mixes "250 mB"
                // with "16 B" and the unit does not jump around while the tank fills.
                int amount = this.menu.getTankAmount(tank);
                int capacity = this.menu.getTankCapacity(tank);
                Component level = Component.translatable("gui.simpleautofarm.fluid_farm.tank_slot",
                        NumberFormatter.abbreviateFluid(amount, capacity),
                        NumberFormatter.abbreviateFluid(capacity, capacity),
                        NumberFormatter.fluidUnit(capacity));
                graphics.renderComponentTooltip(this.font,
                        List.of(fluidName.copy().append(": ").append(level)), mouseX, mouseY);
                return;
            }
        }
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
        private final FluidFarmScreen screen;

        AutoEjectButton(int x, int y, FluidFarmScreen screen) {
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
