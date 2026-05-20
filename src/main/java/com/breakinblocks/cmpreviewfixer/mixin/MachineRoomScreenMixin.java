package com.breakinblocks.cmpreviewfixer.mixin;

import com.breakinblocks.cmpreviewfixer.BakedLevelCleanup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disposes the previous BakedLevel held by MachineRoomScreen.renderer before
 * it is replaced or the screen closes.
 *
 * Target: dev.compactmods.machines.client.room.MachineRoomScreen
 *  - updateScene(BakedLevel): runs every time a new bake completes
 *  - onClose(): runs when the player closes the preview
 *
 * @Pseudo so the mixin is silently skipped when Compact Machines is absent;
 * require=0 so individual hooks fail gracefully if CM's class layout changes.
 */
@Pseudo
@Mixin(targets = "dev.compactmods.machines.client.room.MachineRoomScreen", remap = false)
public abstract class MachineRoomScreenMixin extends Screen {

    protected MachineRoomScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "updateScene", at = @At("HEAD"), require = 0, remap = false)
    private void cmpreviewfixer$disposePriorOnReplace(CallbackInfo ci) {
        BakedLevelCleanup.disposeScreenRenderer(this);
    }

    @Inject(method = "onClose", at = @At("HEAD"), require = 0)
    private void cmpreviewfixer$disposeOnClose(CallbackInfo ci) {
        BakedLevelCleanup.disposeScreenRenderer(this);
    }
}
