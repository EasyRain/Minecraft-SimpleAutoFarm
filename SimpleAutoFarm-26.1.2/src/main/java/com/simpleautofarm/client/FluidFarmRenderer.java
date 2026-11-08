package com.simpleautofarm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simpleautofarm.block.FluidFarmBlock;
import com.simpleautofarm.block.FluidFarmBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the fluid cube inside the fluid farm's glass cavity.
 *
 * <p>The block state's {@link FluidFarmBlock#FILL} property is the only signal used: it is already synced
 * to the client by the block update that changes the variant, so no block entity packet is involved. The
 * cube is submitted through {@link SubmitNodeCollector#submitCustomGeometry} with the block atlas'
 * translucent sheet, full bright light and a normal on every vertex.
 */
@OnlyIn(Dist.CLIENT)
public class FluidFarmRenderer implements BlockEntityRenderer<FluidFarmBlockEntity, FluidFarmRenderer.State> {

    /** Inner cavity of the model: x/z 2..14, y 4..14 pixels; a hair of inset avoids z-fighting. */
    private static final float X1 = 2.0F / 16.0F + 0.0001F;
    private static final float X2 = 14.0F / 16.0F - 0.0001F;
    private static final float Y1 = 4.0F / 16.0F + 0.0001F;
    private static final float Y2 = 14.0F / 16.0F - 0.0001F;
    private static final float Z1 = 2.0F / 16.0F + 0.0001F;
    private static final float Z2 = 14.0F / 16.0F - 0.0001F;

    private static final Identifier WATER_STILL = Identifier.withDefaultNamespace("block/water_still");
    private static final Identifier LAVA_STILL = Identifier.withDefaultNamespace("block/lava_still");

    /** Water keeps the vanilla GUI blue; lava's texture is already coloured; other fluids are neutral. */
    private static final int WATER_TINT = 0xFF3F76E4;
    private static final int LAVA_TINT = 0xFFFFFFFF;
    private static final int OTHER_TINT = 0xFF7FB6C8;

    public FluidFarmRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FluidFarmBlockEntity blockEntity, State state, float partialTicks,
                                   Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        state.visible = false;
        state.sprite = null;
        state.tint = 0xFFFFFFFF;

        // The synced fluid is the real source; the block state is only a fallback, so water and lava stay
        // visible even if a sync packet never arrived.
        FluidStack display = blockEntity.getDisplayFluid();
        if (!display.isEmpty()) {
            Fluid fluid = display.getFluid();
            state.sprite = spriteForFluid(fluid);
            state.tint = tintFor(fluid, display);
            state.visible = state.sprite != null;
            if (state.visible) {
                return;
            }
        }

        BlockState blockState = blockEntity.getBlockState();
        if (!blockState.hasProperty(FluidFarmBlock.FILL)) {
            return;
        }
        switch (blockState.getValue(FluidFarmBlock.FILL)) {
            case EMPTY -> state.visible = false;
            case WATER -> {
                state.sprite = sprite(WATER_STILL);
                state.tint = WATER_TINT;
                state.visible = true;
            }
            case LAVA -> {
                state.sprite = sprite(LAVA_STILL);
                state.tint = LAVA_TINT;
                state.visible = true;
            }
            // Step 4 keeps this only as the last resort when no fluid was synced.
            case OTHER -> {
                state.sprite = sprite(WATER_STILL);
                state.tint = OTHER_TINT;
                state.visible = true;
            }
        }
        if (state.sprite == null) {
            state.visible = false;
        }
    }

    /**
     * The fluid's still sprite from the baked fluid models (the same source the GUI bars use), or null
     * while the models are not ready.
     */
    @Nullable
    private static TextureAtlasSprite spriteForFluid(Fluid fluid) {
        try {
            return Minecraft.getInstance().getModelManager()
                    .getFluidStateModelSet()
                    .get(fluid.defaultFluidState())
                    .stillMaterial()
                    .sprite();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * The fluid's tint: water keeps the fixed GUI blue (a machine has no biome colour), every other fluid
     * asks its baked model's {@link FluidTintSource}, exactly like the GUI bars do.
     */
    private static int tintFor(Fluid fluid, FluidStack display) {
        if (fluid.isSame(Fluids.WATER)) {
            return WATER_TINT;
        }
        try {
            net.minecraft.client.renderer.block.FluidModel model = Minecraft.getInstance()
                    .getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
            if (model.tintSource() instanceof FluidTintSource fluidTint) {
                return fluidTint.colorAsStack(display) | 0xFF000000;
            }
        } catch (RuntimeException ignored) {
            // No tint information: draw the texture as it is.
        }
        return 0xFFFFFFFF;
    }

    /**
     * Looks a block-atlas sprite up by name. Wrapped so a missing or not-yet-stitched sprite disables the
     * cube for that frame instead of throwing inside the renderer.
     */
    @Nullable
    private static TextureAtlasSprite sprite(Identifier texture) {
        try {
            return Minecraft.getInstance().getAtlasManager()
                    .get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, texture));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (!state.visible || sprite == null) {
            return;
        }
        int tint = state.tint;
        int light = LightCoordsUtil.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;
        collector.submitCustomGeometry(poseStack, Sheets.translucentBlockSheet(), (entry, consumer) -> {
            float u0 = sprite.getU0();
            float u1 = sprite.getU1();
            float v0 = sprite.getV0();
            float v1 = sprite.getV1();
            quad(consumer, entry, Face.DOWN, tint, light, overlay, u0, u1, v0, v1,
                    X1, Y1, Z1, X2, Y1, Z1, X2, Y1, Z2, X1, Y1, Z2);
            quad(consumer, entry, Face.UP, tint, light, overlay, u0, u1, v0, v1,
                    X1, Y2, Z2, X2, Y2, Z2, X2, Y2, Z1, X1, Y2, Z1);
            quad(consumer, entry, Face.NORTH, tint, light, overlay, u0, u1, v0, v1,
                    X1, Y1, Z1, X1, Y2, Z1, X2, Y2, Z1, X2, Y1, Z1);
            quad(consumer, entry, Face.SOUTH, tint, light, overlay, u0, u1, v0, v1,
                    X2, Y1, Z2, X2, Y2, Z2, X1, Y2, Z2, X1, Y1, Z2);
            quad(consumer, entry, Face.WEST, tint, light, overlay, u0, u1, v0, v1,
                    X1, Y1, Z2, X1, Y2, Z2, X1, Y2, Z1, X1, Y1, Z1);
            quad(consumer, entry, Face.EAST, tint, light, overlay, u0, u1, v0, v1,
                    X2, Y1, Z1, X2, Y2, Z1, X2, Y2, Z2, X2, Y1, Z2);
        });
    }

    /** The six faces, used to pick the UV axes and the normal. */
    private enum Face {
        DOWN, UP, NORTH, SOUTH, WEST, EAST
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose entry, Face face, int tint, int light,
                             int overlay, float u0, float u1, float v0, float v1, float... xyz) {
        vertex(consumer, entry, face, tint, light, overlay, u0, u1, v0, v1, xyz[0], xyz[1], xyz[2]);
        vertex(consumer, entry, face, tint, light, overlay, u0, u1, v0, v1, xyz[3], xyz[4], xyz[5]);
        vertex(consumer, entry, face, tint, light, overlay, u0, u1, v0, v1, xyz[6], xyz[7], xyz[8]);
        vertex(consumer, entry, face, tint, light, overlay, u0, u1, v0, v1, xyz[9], xyz[10], xyz[11]);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose entry, Face face, int tint, int light,
                               int overlay, float u0, float u1, float v0, float v1,
                               float x, float y, float z) {
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
        float nx = switch (face) {
            case WEST -> -1.0F;
            case EAST -> 1.0F;
            default -> 0.0F;
        };
        float ny = switch (face) {
            case DOWN -> -1.0F;
            case UP -> 1.0F;
            default -> 0.0F;
        };
        float nz = switch (face) {
            case NORTH -> -1.0F;
            case SOUTH -> 1.0F;
            default -> 0.0F;
        };
        consumer.addVertex(entry, x, y, z)
                .setColor(tint)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(entry, nx, ny, nz);
    }

    /** Render state of the fluid farm: which sprite and tint the cube uses, if any. */
    public static class State extends BlockEntityRenderState {
        public boolean visible;
        public int tint = 0xFFFFFFFF;
        public TextureAtlasSprite sprite;
    }
}
