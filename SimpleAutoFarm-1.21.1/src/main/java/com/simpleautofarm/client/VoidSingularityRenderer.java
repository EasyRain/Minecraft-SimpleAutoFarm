package com.simpleautofarm.client;

import com.simpleautofarm.block.VoidSingularityBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renders the Void Singularity by extending the vanilla {@link TheEndPortalRenderer} instead of
 * re-implementing its cube — the block gets exactly the vanilla end-portal starfield.
 *
 * <p>Extending the vanilla class is also what makes shader packs work: Iris mixes into
 * {@code TheEndPortalRenderer.render} and re-draws the portal itself (an animated {@code end_portal.png}
 * cube) whenever a pack is active. A hand-rolled renderer that merely asks for
 * {@link net.minecraft.client.renderer.RenderType#endPortal()} is never touched by that mixin, so the
 * block rendered as nothing (fully invisible, you could see straight through it) under shaders.
 */
@OnlyIn(Dist.CLIENT)
public class VoidSingularityRenderer extends TheEndPortalRenderer<VoidSingularityBlockEntity> {

    public VoidSingularityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** Full cube: vanilla insets the top/bottom faces because the real portal is a flat plane. */
    @Override
    protected float getOffsetUp() {
        return 1.0F;
    }

    @Override
    protected float getOffsetDown() {
        return 0.0F;
    }
}
