package com.simpleautofarm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleautofarm.block.VoidSingularityBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renders the Void Singularity as a full-cube end-portal starfield (the same end-portal render type
 * used by the vanilla end portal, which reacts to the camera's view angle).
 *
 * <p>Going through {@link AbstractEndPortalRenderer#submitSpecial} — the vanilla geometry helper — is what
 * keeps shader packs working (Iris/Oculus mix into {@code AbstractEndPortalRenderer.submitCube} and redraw
 * the portal themselves while a pack is active, so the call is rewritten into their animated end-portal
 * cube; a hand-rolled cube that bypassed that helper simply vanished under shaders).
 */
@OnlyIn(Dist.CLIENT)
public class VoidSingularityRenderer implements BlockEntityRenderer<VoidSingularityBlockEntity, VoidSingularityRenderer.State> {

    public VoidSingularityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        AbstractEndPortalRenderer.submitSpecial(RenderTypes.endPortal(), poseStack, submitNodeCollector);
    }

    public static class State extends BlockEntityRenderState {
    }
}
