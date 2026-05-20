package com.breakinblocks.cmpreviewfixer.mixin;

import com.breakinblocks.cmpreviewfixer.BakedLevelCleanup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
