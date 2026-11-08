package com.simpleautofarm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simpleautofarm.block.FluidFarmBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the fluid cube inside the fluid farm's glass cavity.
 *
 * <p>26.1.2 renders block entities through a render state: {@link #extractRenderState} copies the fluid's
 * still sprite and tint out of the block entity, {@link #submit} then hands the six quads to the
 * {@link SubmitNodeCollector}. The cube uses the fluid's own texture and tint, so water is vanilla blue,
 * lava orange and modded fluids keep their own colour, which no static block model can do.
 */
@OnlyIn(Dist.CLIENT)
public class FluidFarmRenderer implements BlockEntityRenderer<FluidFarmBlockEntity, FluidFarmRenderer.State> {

    /** Inner cavity of the model: x/z 2..14, y 4..14 pixels. A hair of inset avoids z-fighting. */
    private static final float X1 = 2.0F / 16.0F + 0.0001F;
    private static final float X2 = 14.0F / 16.0F - 0.0001F;
    private static final float Y1 = 4.0F / 16.0F + 0.0001F;
    private static final float Y2 = 14.0F / 16.0F - 0.0001F;
    private static final float Z1 = 2.0F / 16.0F + 0.0001F;
    private static final float Z2 = 14.0F / 16.0F - 0.0001F;

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
        FluidStack display = blockEntity.getDisplayFluid();
        if (display.isEmpty()) {
            state.visible = false;
            return;
        }
        Fluid fluid = display.getFluid();
        state.visible = true;
        state.tint = tintFor(fluid, display);
        try {
            state.sprite = Minecraft.getInstance().getModelManager()
                    .getFluidStateModelSet()
                    .get(fluid.defaultFluidState())
                    .stillMaterial()
                    .sprite();
        } catch (RuntimeException exception) {
            state.sprite = null;
            state.visible = false;
        }
    }

    /**
     * Tint of the fluid: water keeps the fixed GUI blue (the biome colour does not exist for a machine),
     * everything else asks its fluid model's {@link FluidTintSource} like the GUI bars do.
     */
    private static int tintFor(Fluid fluid, FluidStack display) {
        if (fluid.isSame(Fluids.WATER)) {
            return 0xFF3F76E4;
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

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (!state.visible || sprite == null) {
            return;
        }
        int tint = state.tint;
        int light = LightCoordsUtil.FULL_BRIGHT;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(sprite.atlasLocation()),
                (entry, consumer) -> {
                    float u0 = sprite.getU0();
                    float u1 = sprite.getU1();
                    float v0 = sprite.getV0();
                    float v1 = sprite.getV1();
                    quad(consumer, entry, Face.DOWN, tint, light, u0, u1, v0, v1,
                            X1, Y1, Z1, X2, Y1, Z1, X2, Y1, Z2, X1, Y1, Z2);
                    quad(consumer, entry, Face.UP, tint, light, u0, u1, v0, v1,
                            X1, Y2, Z2, X2, Y2, Z2, X2, Y2, Z1, X1, Y2, Z1);
                    quad(consumer, entry, Face.NORTH, tint, light, u0, u1, v0, v1,
                            X1, Y1, Z1, X1, Y2, Z1, X2, Y2, Z1, X2, Y1, Z1);
                    quad(consumer, entry, Face.SOUTH, tint, light, u0, u1, v0, v1,
                            X2, Y1, Z2, X2, Y2, Z2, X1, Y2, Z2, X1, Y1, Z2);
                    quad(consumer, entry, Face.WEST, tint, light, u0, u1, v0, v1,
                            X1, Y1, Z2, X1, Y2, Z2, X1, Y2, Z1, X1, Y1, Z1);
                    quad(consumer, entry, Face.EAST, tint, light, u0, u1, v0, v1,
                            X2, Y1, Z1, X2, Y2, Z1, X2, Y2, Z2, X2, Y1, Z2);
                });
    }

    /** The six faces, used to pick the UV axes and the normal. */
    private enum Face {
        DOWN, UP, NORTH, SOUTH, WEST, EAST
    }

    /** One textured quad; {@code xyz} holds the four corners as 12 floats in order. */
    private static void quad(VertexConsumer consumer, PoseStack.Pose entry, Face face, int tint, int light,
                             float u0, float u1, float v0, float v1, float... xyz) {
        vertex(consumer, entry, face, tint, light, u0, u1, v0, v1, xyz[0], xyz[1], xyz[2]);
        vertex(consumer, entry, face, tint, light, u0, u1, v0, v1, xyz[3], xyz[4], xyz[5]);
        vertex(consumer, entry, face, tint, light, u0, u1, v0, v1, xyz[6], xyz[7], xyz[8]);
        vertex(consumer, entry, face, tint, light, u0, u1, v0, v1, xyz[9], xyz[10], xyz[11]);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose entry, Face face, int tint, int light,
                               float u0, float u1, float v0, float v1, float x, float y, float z) {
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
            case DOWN -> 0.0F;
            case UP -> 0.0F;
            case NORTH -> 0.0F;
            case SOUTH -> 0.0F;
            case WEST -> -1.0F;
            case EAST -> 1.0F;
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
                .setOverlay(0)
                .setLight(light)
                .setNormal(entry, nx, ny, nz);
    }

    /** Render state of the fluid farm: which sprite and tint the cube uses. */
    public static class State extends BlockEntityRenderState {
        public boolean visible;
        public int tint = 0xFFFFFFFF;
        public TextureAtlasSprite sprite;
    }
}
