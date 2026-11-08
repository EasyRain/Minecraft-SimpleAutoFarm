package com.simpleautofarm.client;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.block.FluidFarmBlockEntity;
import com.simpleautofarm.menu.FluidFarmMenu;
import com.simpleautofarm.util.NumberFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Screen of the simple auto fluid farm. The sample row, the container row and the upgrade column sit
 * where the farm's slots used to be; the two product rows the machine no longer uses carry nine
 * vertical tank bars instead, one per sample column and filled bottom-up with that tank's own fluid
 * (its still sprite and tint).
 *
 * <p>26.1.2 note: rendering goes through {@link GuiGraphicsExtractor} ({@code extractBackground} /
 * {@code extractLabels} / {@code extractTooltip} / {@code extractSlot}) and the {@code blit} calls
 * name a {@code RenderPipeline}; scissors still exist, so the fluid pattern keeps its native tile size
 * at every fill level.
 */
public class FluidFarmScreen extends AbstractContainerScreen<FluidFarmMenu> {

    private static final Identifier TEXTURE = SimpleAutoFarm.prefix("textures/gui/fluid_farm.png");
    private static final Identifier UPGRADE_TEXTURE = SimpleAutoFarm.prefix("textures/gui/upgrade_slots.png");
    private static final Identifier UPGRADE_PANEL_TEXTURE = SimpleAutoFarm.prefix("textures/gui/upgrade_panel.png");
    private static final Identifier AUTO_EJECT_TEXTURE = SimpleAutoFarm.prefix("textures/gui/auto_eject_button.png");

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

    // auto-eject button (below the upgrade slots)
    private static final int BUTTON_X = SLOT_X;
    private static final int BUTTON_Y = 71;
    private static final int BUTTON_SIZE = 16;

    // labels
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_Y = 108;

    public FluidFarmScreen(FluidFarmMenu menu, Inventory inventory, Component title) {
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

        // main panel, derived from the farm artwork: nine tank bars replace the two product rows
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos,
                0.0F, 0.0F, 176, this.imageHeight, 256, 256);

        // upgrade column panel (bordered, right of the main panel)
        graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADE_PANEL_TEXTURE, this.leftPos + PANEL_X,
                this.topPos + PANEL_Y, 0.0F, 0.0F, 24, 77, 256, 256);

        // upgrade slot backgrounds (3 vertical slots)
        graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADE_TEXTURE, this.leftPos + SLOT_X - 1,
                this.topPos + 17, 0.0F, 0.0F, 18, 18, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADE_TEXTURE, this.leftPos + SLOT_X - 1,
                this.topPos + 35, 0.0F, 18.0F, 18, 18, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADE_TEXTURE, this.leftPos + SLOT_X - 1,
                this.topPos + 53, 0.0F, 36.0F, 18, 18, 256, 256);

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
            this.extractTank(graphics, tank);
        }
    }

    /** Left edge of tank bar {@code tank}, aligned with the sample slot of the same column. */
    private static int tankX(int tank) {
        return TANK_X0 + tank * TANK_PITCH;
    }

    /**
     * Draws one tank bar bottom-up. The fluid's still sprite comes from the baked fluid models
     * ({@code ModelManager#getFluidStateModelSet().get(fluidState).stillMaterial().sprite()}) — the same
     * source the vanilla {@code FluidRenderer} uses — and is repeated in whole tiles anchored at the
     * tank floor, with the topmost tile cropped by a scissor instead of stretched. The still textures are
     * mostly white, so water gets its fixed blue laid over the tiles; other fluids keep their own
     * texture colour.
     */
    private void extractTank(GuiGraphicsExtractor graphics, int tank) {
        int amount = this.menu.getTankAmount(tank);
        int capacity = Math.max(1, this.menu.getTankCapacity(tank));
        FluidStack stack = this.menu.getTankFluid(tank);
        if (stack.isEmpty() || amount <= 0) {
            return;
        }
        int fluidFilled = amount >= capacity
                ? TANK_INNER_H
                : Math.max(1, (int) ((long) amount * TANK_INNER_H / capacity));
        int innerX = this.leftPos + tankX(tank) + 1;
        int bottom = this.topPos + TANK_Y + 1 + TANK_INNER_H;
        int top = bottom - fluidFilled;
        TextureAtlasSprite sprite = stillSprite(stack);
        if (sprite == null) {
            graphics.fill(innerX, top, innerX + TANK_INNER_W, bottom, fluidColor(stack));
            return;
        }
        int tile = Math.max(1, sprite.contents().height());
        int tint = fluidColor(stack);
        graphics.enableScissor(innerX, top, innerX + TANK_INNER_W, bottom);
        for (int y = bottom - tile; y > top - tile; y -= tile) {
            // 26.1.2's sprite blit takes an ARGB colour, so the fluid is tinted instead of overlaid
            // (many still textures are grey/white and only the tint gives them their colour).
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, innerX, y, TANK_INNER_W, tile, tint);
        }
        graphics.disableScissor();
    }

    /** The fluid's still sprite from the baked fluid models, or {@code null} while they are missing. */
    @Nullable
    private static TextureAtlasSprite stillSprite(FluidStack stack) {
        try {
            FluidStateModelSet models = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
            return models.get(stack.getFluid().defaultFluidState()).stillMaterial().sprite();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Per-fluid tint cache: the baked fluid model does not change while the game runs. */
    private static final Map<Fluid, Integer> TINTS = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Fluid, Integer> eldest) {
            return size() > 128;
        }
    };

    /**
     * Tint of the still tiles: water keeps the fixed GUI blue the block colour handler also falls back
     * to; every other fluid asks its baked fluid model's tint source, preferring NeoForge's
     * {@link FluidTintSource} (the same type and {@code colorAsStack} call Jade uses) and falling back
     * to the plain {@link BlockTintSource#color(BlockState)}. White when no tint source exists.
     */
    private static int fluidColor(FluidStack stack) {
        Fluid fluid = stack.getFluid();
        Integer cached = TINTS.get(fluid);
        if (cached != null) {
            return cached;
        }
        int color = 0xFFFFFFFF;
        if (fluid.isSame(Fluids.WATER)) {
            color = 0xFF3F76E4;
        } else {
            try {
                FluidStateModelSet models = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
                FluidModel model = models.get(fluid.defaultFluidState());
                BlockTintSource tintSource = model.tintSource();
                if (tintSource instanceof FluidTintSource fluidTint) {
                    // Same call Jade makes: a fluid tint may depend on the stack, not only on the state.
                    color = fluidTint.colorAsStack(new FluidStack(fluid, 1)) | 0xFF000000;
                } else if (tintSource != null) {
                    color = tintSource.color(fluid.defaultFluidState().createLegacyBlock()) | 0xFF000000;
                }
            } catch (RuntimeException exception) {
                color = 0xFFFFFFFF;
            }
        }
        TINTS.put(fluid, color);
        return color;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(this.font, this.title, 8, TITLE_Y, 0xFF404040, false);
        graphics.text(this.font, this.playerInventoryTitle, 8, INVENTORY_LABEL_Y, 0xFF404040, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = mouseX - this.leftPos;
        int y = mouseY - this.topPos;
        for (int tank = 0; tank < TANK_COUNT; tank++) {
            int barX = tankX(tank);
            if (x >= barX && x < barX + TANK_W && y >= TANK_Y && y < TANK_Y + TANK_H) {
                FluidStack stack = this.menu.getTankFluid(tank);
                Component fluidName = stack.isEmpty()
                        ? Component.translatable("gui.simpleautofarm.fluid_farm.empty")
                        : stack.getHoverName();
                // One line, no slot number: the bars are told apart by their column, so the tooltip only
                // names the fluid and its level. Both numbers take the unit of the tank's capacity, so
                // the pair never mixes "250 mB" with "16 B" and the unit does not jump while filling.
                int amount = this.menu.getTankAmount(tank);
                int capacity = this.menu.getTankCapacity(tank);
                Component level = Component.translatable("gui.simpleautofarm.fluid_farm.tank_slot",
                        NumberFormatter.abbreviateFluid(amount, capacity),
                        NumberFormatter.abbreviateFluid(capacity, capacity),
                        NumberFormatter.fluidUnit(capacity));
                graphics.setComponentTooltipForNextFrame(this.font,
                        List.of(fluidName.copy().append(": ").append(level)), mouseX, mouseY);
                return;
            }
        }
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
            graphics.setComponentTooltipForNextFrame(this.font, List.of(
                    Component.translatable("tooltip.simpleautofarm.progress",
                            this.menu.getProgress(), this.menu.getProductionTicks())), mouseX, mouseY);
            return;
        }
        if (x >= BUTTON_X && x < BUTTON_X + BUTTON_SIZE && y >= BUTTON_Y && y < BUTTON_Y + BUTTON_SIZE) {
            String key = "tooltip.simpleautofarm.auto_eject.off";
            if (this.menu.isAutoEject()) {
                key = this.menu.isAeConnected()
                        ? "tooltip.simpleautofarm.auto_eject.on_ae"
                        : "tooltip.simpleautofarm.auto_eject.on";
            }
            graphics.setComponentTooltipForNextFrame(this.font, List.of(Component.translatable(key)), mouseX, mouseY);
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
        private final FluidFarmScreen screen;

        AutoEjectButton(int x, int y, FluidFarmScreen screen) {
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
            graphics.blit(RenderPipelines.GUI_TEXTURED, AUTO_EJECT_TEXTURE, this.getX(), this.getY(),
                    on ? 16.0F : 0.0F, 0.0F, 16, 16, 256, 256);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
