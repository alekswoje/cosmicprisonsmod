package me.landon.client.runtime;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

public final class PeacefulMiningRenderStateKeys {
    public static final RenderStateDataKey<Boolean> GHOSTED =
            RenderStateDataKey.create(() -> "cosmicprisonsmod:peaceful_mining_ghosted");

    private PeacefulMiningRenderStateKeys() {}
}
