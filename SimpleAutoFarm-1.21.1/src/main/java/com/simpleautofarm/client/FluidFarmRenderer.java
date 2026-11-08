package com.simpleautofarm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simpleautofarm.block.FluidFarmBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
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
 * the block model baked" look. It is drawn full bright in the translucent pass: the casing's inner
 * walls sit behind it, and a lit, opaque pass would leave the cube black and invisible.
 */
public class FluidFarmRenderer implements BlockEntityRenderer<FluidFarmBlockEntity> {

    /** Inner cavity of the model: x/z 3..13, y 4..13 pixels. Only a hair of inset avoids z-fighting. */
    private static final float X1 = 3.0F / 16.0F + 0.0001F;
    private static final float X2 = 13.0F / 16.0F - 0.0001F;
    private static final float Y1 = 4.0F / 16.0F + 0.0001F;
    private static final float Y2 = 13.0F / 16.0F - 0.0001F;
    private static final float Z1 = 3.0F / 16.0F + 0.0001F;
    private static final float Z2 = 13.0F / 16.0F - 0.0001F;

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
        // Fluids in the world are drawn full bright; without this the cube sits in the casing's shadow.
        int light = LightTexture.FULL_BRIGHT;

        quad(consumer, entry, sprite, tint, light, packedOverlay, Face.DOWN,
                X1, Y1, Z1, X2, Y1, Z1, X2, Y1, Z2, X1, Y1, Z2);
        quad(consumer, entry, sprite, tint, light, packedOverlay, Face.UP,
                X1, Y2, Z2, X2, Y2, Z2, X2, Y2, Z1, X1, Y2, Z1);
        quad(consumer, entry, sprite, tint, light, packedOverlay, Face.NORTH,
                X1, Y1, Z1, X1, Y2, Z1, X2, Y2, Z1, X2, Y1, Z1);
        quad(consumer, entry, sprite, tint, light, packedOverlay, Face.SOUTH,
                X2, Y1, Z2, X2, Y2, Z2, X1, Y2, Z2, X1, Y1, Z2);
        quad(consumer, entry, sprite, tint, light, packedOverlay, Face.WEST,
                X1, Y1, Z2, X1, Y2, Z2, X1, Y2, Z1, X1, Y1, Z1);
        quad(consumer, entry, sprite, tint, light, packedOverlay, Face.EAST,
                X2, Y1, Z1, X2, Y2, Z1, X2, Y2, Z2, X2, Y1, Z2);
    }

    /** The six faces, used to pick which local coordinate goes into U and which into V. */
    private enum Face {
        DOWN, UP, NORTH, SOUTH, WEST, EAST
    }

    /**
     * One textured quad. UVs are derived per vertex from the block-local coordinates, so one block
     * spans one full sprite (the same scale a vanilla fluid block uses) instead of stretching the
     * texture over the whole cube face.
     */
    private static void quad(VertexConsumer consumer, PoseStack.Pose entry, TextureAtlasSprite sprite,
                             int tint, int light, int overlay, Face face, float... xyz) {
        vertex(consumer, entry, sprite, tint, light, overlay, face,
                xyz[0], xyz[1], xyz[2]);
        vertex(consumer, entry, sprite, tint, light, overlay, face,
                xyz[3], xyz[4], xyz[5]);
        vertex(consumer, entry, sprite, tint, light, overlay, face,
                xyz[6], xyz[7], xyz[8]);
        vertex(consumer, entry, sprite, tint, light, overlay, face,
                xyz[9], xyz[10], xyz[11]);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose entry, TextureAtlasSprite sprite,
                               int tint, int light, int overlay, Face face, float x, float y, float z) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float uCoord = switch (face) {
            case DOWN, UP, NORTH, SOUTH -> x;
            case WEST, EAST -> z;
        };
        float vCoord = switch (face) {
            case DOWN, UP -> z;
            default -> y;
        };
        float u = u0 + uCoord * (u1 - u0);
        float v = v0 + vCoord * (v1 - v0);
        // The translucent block-compatible format carries a normal per vertex; without it the buffer
        // builder rejects the vertex with "Missing elements in vertex: Normal".
        float nx = 0.0F;
        float ny = 0.0F;
        float nz = 0.0F;
        switch (face) {
            case DOWN -> ny = -1.0F;
            case UP -> ny = 1.0F;
            case NORTH -> nz = -1.0F;
            case SOUTH -> nz = 1.0F;
            case WEST -> nx = -1.0F;
            case EAST -> nx = 1.0F;
        }
        consumer.addVertex(entry, x, y, z)
                .setColor(tint)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(entry, nx, ny, nz);
    }
}
