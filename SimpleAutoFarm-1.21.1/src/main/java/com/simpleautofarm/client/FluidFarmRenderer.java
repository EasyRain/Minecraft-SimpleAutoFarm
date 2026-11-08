package com.simpleautofarm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simpleautofarm.block.FluidFarmBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Draws the fluid cube inside the fluid farm's glass cavity.
 *
 * <p>The cube uses the fluid's own still texture and tint, so every fluid looks like it does in the
 * world (water blue, lava orange, modded fluids with their own colour) instead of the flat "whatever
 * the block model baked" look. Translucent rendering is required: the casing's inner walls are drawn
 * behind the cube and an opaque pass would hide it.
 */
public class FluidFarmRenderer implements BlockEntityRenderer<FluidFarmBlockEntity> {

    /** Inner cavity of the model (x/z 3..13, y 4..13), inset one pixel against the walls. */
    private static final float X1 = 4 / 16.0F;
    private static final float X2 = 12 / 16.0F;
    private static final float Y1 = 5 / 16.0F;
    private static final float Y2 = 12 / 16.0F;
    private static final float Z1 = 4 / 16.0F;
    private static final float Z2 = 12 / 16.0F;

    public FluidFarmRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FluidFarmBlockEntity blockEntity, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        FluidStack display = blockEntity.getDisplayFluid();
        if (display.isEmpty()) {
            return;
        }
        Fluid fluid = display.getFluid();
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation still = extensions.getStillTexture(display);
        if (still == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(still);
        int tint = extensions.getTintColor(display);
        if (fluid.isSame(Fluids.WATER) && (tint & 0xFFFFFF) == 0xFFFFFF) {
            // Water takes its colour from the biome, which a machine has none of: same constant the block
            // colour handler falls back to, so the cube is vanilla blue instead of white.
            tint = 0xFF3F76E4;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.translucent());
        PoseStack.Pose entry = pose.last();
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        quad(consumer, entry, u0, v0, u1, v1, tint, packedLight, packedOverlay,
                X1, Y1, Z1, X2, Y1, Z1, X2, Y1, Z2, X1, Y1, Z2); // down
        quad(consumer, entry, u0, v0, u1, v1, tint, packedLight, packedOverlay,
                X1, Y2, Z2, X2, Y2, Z2, X2, Y2, Z1, X1, Y2, Z1); // up
        quad(consumer, entry, u0, v0, u1, v1, tint, packedLight, packedOverlay,
                X1, Y1, Z1, X1, Y2, Z1, X2, Y2, Z1, X2, Y1, Z1); // north
        quad(consumer, entry, u0, v0, u1, v1, tint, packedLight, packedOverlay,
                X2, Y1, Z2, X2, Y2, Z2, X1, Y2, Z2, X1, Y1, Z2); // south
        quad(consumer, entry, u0, v0, u1, v1, tint, packedLight, packedOverlay,
                X1, Y1, Z2, X1, Y2, Z2, X1, Y2, Z1, X1, Y1, Z1); // west
        quad(consumer, entry, u0, v0, u1, v1, tint, packedLight, packedOverlay,
                X2, Y1, Z1, X2, Y2, Z1, X2, Y2, Z2, X2, Y1, Z2); // east
    }

    /** One textured quad; {@code xyz} holds the four corners as 12 floats in order. */
    private static void quad(VertexConsumer consumer, PoseStack.Pose entry, float u0, float v0, float u1,
                             float v1, int tint, int packedLight, int packedOverlay, float... xyz) {
        vertex(consumer, entry, xyz[0], xyz[1], xyz[2], u0, v1, tint, packedLight, packedOverlay);
        vertex(consumer, entry, xyz[3], xyz[4], xyz[5], u1, v1, tint, packedLight, packedOverlay);
        vertex(consumer, entry, xyz[6], xyz[7], xyz[8], u1, v0, tint, packedLight, packedOverlay);
        vertex(consumer, entry, xyz[9], xyz[10], xyz[11], u0, v0, tint, packedLight, packedOverlay);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose entry, float x, float y, float z,
                               float u, float v, int tint, int packedLight, int packedOverlay) {
        consumer.addVertex(entry, x, y, z)
                .setColor(tint)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight);
    }
}
