package com.simpleautofarm.client;

import com.simpleautofarm.SimpleAutoFarm;
import com.simpleautofarm.menu.GeneratorMenu;
import com.simpleautofarm.util.NumberFormatter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class GeneratorScreen extends AbstractContainerScreen<GeneratorMenu> {

    private static final Identifier TEXTURE = SimpleAutoFarm.prefix("textures/gui/generator.png");
    private static final Identifier FLAME_SPRITE = SimpleAutoFarm.prefix("generator/flame");
    private static final Identifier UPGRADE_TEXTURE = SimpleAutoFarm.prefix("textures/gui/upgrade_slots.png");

    private static final int ENERGY_X = 8;
    private static final int ENERGY_Y = 18;
    private static final int ENERGY_W = 12;
    private static final int ENERGY_H = 52;
    private static final int ENERGY_COLOR = 0xFF3FBF3F;

    private static final int FLUID_X = 22;
    private static final int FLUID_Y = 18;
    private static final int FLUID_W = 12;
    private static final int FLUID_H = 52;
    private static final int FLUID_COLOR = 0xFFFF6A00;
    private static final int FLUID_BORDER_COLOR = 0xFF3B3B3B;
    private static final int FLUID_EMPTY_COLOR = 0xFF141414;

    /** Horizontal center of the "FE/tick" label, clear of the energy/fluid bars. */
    private static final int RATE_CENTER_X = 88;

    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        // upgrade slot backgrounds (2 vertical slots, top-right)
        graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADE_TEXTURE, this.leftPos + 149, this.topPos + 17, 0.0F, 0.0F, 18, 18, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADE_TEXTURE, this.leftPos + 149, this.topPos + 35, 0.0F, 36.0F, 18, 18, 256, 256);

        // energy bar
        int energy = this.menu.getEnergy();
        int energyFillable = ENERGY_H - 2;
        int capacity = Math.max(1, this.menu.getEnergyCapacity());
        int energyFilled = Math.min((int) ((long) energy * energyFillable / capacity), energyFillable);
        if (energyFilled > 0) {
            graphics.fill(
                    this.leftPos + ENERGY_X + 1,
                    this.topPos + ENERGY_Y + 1 + (energyFillable - energyFilled),
                    this.leftPos + ENERGY_X + ENERGY_W - 1,
                    this.topPos + ENERGY_Y + ENERGY_H - 1,
                    ENERGY_COLOR);
        }

        int fluid = this.menu.getFluidAmount();
        int fluidCapacity = this.menu.getFluidCapacity();

        // fluid (lava) bar — always visible with a border (Pro generator only)
        if (fluidCapacity > 0) {
            graphics.fill(this.leftPos + FLUID_X, this.topPos + FLUID_Y,
                    this.leftPos + FLUID_X + FLUID_W, this.topPos + FLUID_Y + FLUID_H, FLUID_BORDER_COLOR);
            graphics.fill(this.leftPos + FLUID_X + 1, this.topPos + FLUID_Y + 1,
                    this.leftPos + FLUID_X + FLUID_W - 1, this.topPos + FLUID_Y + FLUID_H - 1, FLUID_EMPTY_COLOR);

            int fluidFillable = FLUID_H - 2;
            int fluidFilled = Math.min((int) ((long) fluid * fluidFillable / fluidCapacity), fluidFillable);
            if (fluidFilled > 0) {
                graphics.fill(
                        this.leftPos + FLUID_X + 1,
                        this.topPos + FLUID_Y + 1 + (fluidFillable - fluidFilled),
                        this.leftPos + FLUID_X + FLUID_W - 1,
                        this.topPos + FLUID_Y + FLUID_H - 1,
                        FLUID_COLOR);
            }
        }

        // flame above the fuel slot: always shows an unlit flame, with the lit fill on top
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FLAME_SPRITE, this.leftPos + 56, this.topPos + 36, 14, 14, 0xFF595959);

        int flameHeight = 0;
        if (this.menu.isCreative()) {
            // creative: the machine is always active, so show a full flame
            flameHeight = 14;
        } else if (fluidCapacity > 0) {
            // Pro: lit flame reflects remaining lava, so it stays stable instead of flickering
            if (fluid > 0) {
                flameHeight = Mth.ceil((float) fluid / (float) fluidCapacity * 13.0F) + 1;
            }
        } else {
            int burnTime = this.menu.getBurnTime();
            int burnTotal = this.menu.getBurnTimeTotal();
            if (burnTime > 0 && burnTotal > 0) {
                flameHeight = Mth.ceil((float) burnTime / (float) burnTotal * 13.0F) + 1;
            }
        }
        if (flameHeight > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FLAME_SPRITE, 14, 14, 0, 14 - flameHeight,
                    this.leftPos + 56, this.topPos + 36 + 14 - flameHeight, 14, flameHeight);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(this.font, this.title, 8, 6, 0xFF404040, false);
        graphics.text(this.font, this.playerInventoryTitle, 8, this.imageHeight - 94, 0xFF404040, false);

        // energy production rate, to the right of the energy/fluid bars
        String rate = NumberFormatter.abbreviateEnergy(this.menu.getFePerTick()) + " FE/tick";
        graphics.text(this.font, rate, RATE_CENTER_X - this.font.width(rate) / 2, 22, 0xFF404040, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = mouseX - this.leftPos;
        int y = mouseY - this.topPos;
        if (x >= ENERGY_X && x < ENERGY_X + ENERGY_W && y >= ENERGY_Y && y < ENERGY_Y + ENERGY_H) {
            graphics.setTooltipForNextFrame(this.font,
                    Component.translatable("tooltip.simpleautofarm.energy",
                            NumberFormatter.abbreviateEnergy(this.menu.getEnergy()),
                            NumberFormatter.abbreviateEnergy(this.menu.getEnergyCapacity())),
                    mouseX, mouseY);
            return;
        }
        if (this.menu.getFluidCapacity() > 0
                && x >= FLUID_X && x < FLUID_X + FLUID_W && y >= FLUID_Y && y < FLUID_Y + FLUID_H) {
            graphics.setTooltipForNextFrame(this.font,
                    Component.translatable("tooltip.simpleautofarm.fluid", this.menu.getFluidAmount(), this.menu.getFluidCapacity()),
                    mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }
}
