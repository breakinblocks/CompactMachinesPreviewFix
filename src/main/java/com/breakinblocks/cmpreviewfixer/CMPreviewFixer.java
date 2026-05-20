package com.breakinblocks.cmpreviewfixer;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(CMPreviewFixer.MODID)
public final class CMPreviewFixer {
    public static final String MODID = "cmpreviewfixer";
    public static final Logger LOG = LogUtils.getLogger();

    public CMPreviewFixer(IEventBus modEventBus) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        boolean cmPresent = ModList.get().isLoaded("compactmachines");
        if (cmPresent) {
            LOG.info("CMPreviewFixer active — patching Compact Machines room preview leak");
        } else {
            LOG.info("CMPreviewFixer loaded but Compact Machines is not present; mixin will be a no-op");
        }
    }
}
