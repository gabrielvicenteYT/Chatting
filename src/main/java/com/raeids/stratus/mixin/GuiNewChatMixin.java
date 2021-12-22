package com.raeids.stratus.mixin;

import com.raeids.stratus.Stratus;
import com.raeids.stratus.config.StratusConfig;
import com.raeids.stratus.hook.ChatSearchingKt;
import com.raeids.stratus.hook.ChatTabs;
import com.raeids.stratus.hook.GuiNewChatHook;
import com.raeids.stratus.hook.ModCompatHooks;
import gg.essential.universal.UMouse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;

@Mixin(value = GuiNewChat.class, priority = Integer.MIN_VALUE)
public abstract class GuiNewChatMixin extends Gui implements GuiNewChatHook {
    @Unique private int stratus$right = 0;
    @Unique private boolean stratus$shouldCopy;
    @Unique private boolean stratus$chatCheck;
    @Shadow @Final private Minecraft mc;
    @Shadow @Final private List<ChatLine> drawnChatLines;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private float percentComplete;

    @Shadow public abstract boolean getChatOpen();

    @Shadow public abstract float getChatScale();

    @Shadow public abstract int getLineCount();

    @Shadow private int scrollPos;
    @Shadow @Final private List<ChatLine> chatLines;

    @Shadow public abstract void deleteChatLine(int id);

    @Unique private static final ResourceLocation COPY = new ResourceLocation("stratus:copy.png");

    @Inject(method = "printChatMessageWithOptionalDeletion", at = @At("HEAD"), cancellable = true)
    private void handlePrintChatMessage(IChatComponent chatComponent, int chatLineId, CallbackInfo ci) {
        handleChatTabMessage(chatComponent, chatLineId, mc.ingameGUI.getUpdateCounter(), false, ci);
    }

    @Inject(method = "setChatLine", at = @At("HEAD"), cancellable = true)
    private void handleSetChatLine(IChatComponent chatComponent, int chatLineId, int updateCounter, boolean displayOnly, CallbackInfo ci) {
        handleChatTabMessage(chatComponent, chatLineId, updateCounter, displayOnly, ci);
    }

    @Inject(method = "drawChat", at = @At("HEAD"))
    private void checkScreenshotKeybind(int j2, CallbackInfo ci) {
        if (Stratus.INSTANCE.getKeybind().isPressed()) {
            Stratus.INSTANCE.setDoTheThing(true);
        }
        stratus$chatCheck = false;
    }

    @ModifyVariable(method = "drawChat", at = @At("HEAD"), argsOnly = true)
    private int setUpdateCounterWhjenYes(int updateCounter) {
        return Stratus.INSTANCE.getDoTheThing() ? 0 : updateCounter;
    }

    @ModifyVariable(method = "drawChat", at = @At("STORE"), index = 2)
    private int setChatLimitWhenYes(int linesToDraw) {
        return Stratus.INSTANCE.getDoTheThing()
                ? GuiNewChat.calculateChatboxHeight(mc.gameSettings.chatHeightFocused) / 9
                : linesToDraw;
    }

    //TODO: fix with betterchat
    @ModifyArgs(method = "drawChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiNewChat;drawRect(IIIII)V"), slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;clamp_double(DDD)D"), to = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;enableBlend()V")))
    private void captureDrawRect(Args args) {
        int left = args.get(0);
        int top = args.get(1);
        int right = args.get(2);
        int bottom = args.get(3);
        if (mc.currentScreen instanceof GuiChat) {
            float f = this.getChatScale();
            int mouseX = MathHelper.floor_double(UMouse.getScaledX()) - 3;
            int mouseY = MathHelper.floor_double(UMouse.getScaledY()) - 27 + (Stratus.INSTANCE.isBetterChat() ? ModCompatHooks.getYOffset() : 0) + (Stratus.INSTANCE.isPatcher() && ModCompatHooks.getChatPosition() ? 12 : 0);
            mouseX = MathHelper.floor_float((float)mouseX / f);
            mouseY = -(MathHelper.floor_float((float)mouseY / f)); //WHY DO I NEED TO DO THIS
            if (mouseX >= (left + (Stratus.INSTANCE.isBetterChat() ? ModCompatHooks.getXOffset() : 0)) && mouseY < bottom && mouseX < (right + 9 + (Stratus.INSTANCE.isBetterChat() ? ModCompatHooks.getXOffset() : 0)) && mouseY >= top) {
                stratus$shouldCopy = true;
                drawCopyChatBox(right, top);
            }
        }
    }

    @Redirect(method = "drawChat", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GuiNewChat;drawnChatLines:Ljava/util/List;", opcode = Opcodes.GETFIELD))
    private List<ChatLine> injected(GuiNewChat instance) {
        return ChatSearchingKt.filterMessages(drawnChatLines);
    }

    @Inject(method = "drawChat", at = @At("RETURN"))
    private void checkStuff(int j2, CallbackInfo ci) {
        if (!stratus$chatCheck && stratus$shouldCopy) {
            stratus$shouldCopy = false;
        }
    }

    @Override
    public int getRight() {
        return stratus$right;
    }

    @Override
    public boolean shouldCopy() {
        return stratus$shouldCopy;
    }

    private void handleChatTabMessage(IChatComponent chatComponent, int chatLineId, int updateCounter, boolean displayOnly, CallbackInfo ci) {
        if (StratusConfig.INSTANCE.getChatTabs()) {
            if (!ChatTabs.INSTANCE.shouldRender(chatComponent)) {
                percentComplete = 1.0F;
                if (chatLineId != 0) {
                    deleteChatLine(chatLineId);
                }
                if (!displayOnly) {
                    chatLines.add(0, new ChatLine(updateCounter, chatComponent, chatLineId));
                    while (this.chatLines.size() > (Stratus.INSTANCE.isPatcher() ? 32767 : 100))
                    {
                        this.chatLines.remove(this.chatLines.size() - 1);
                    }
                }
                ci.cancel();
            }
        }
    }

    private void drawCopyChatBox(int right, int top) {
        stratus$chatCheck = true;
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.pushMatrix();
        mc.getTextureManager().bindTexture(COPY);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.1f);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        stratus$right = right;
        Gui.drawModalRectWithCustomSizedTexture(right, top, 0f, 0f, 9, 9, 9, 9);
        GlStateManager.disableAlpha();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.popMatrix();
    }

    @Override
    public String getStratusChatComponent(int mouseY) {
        if (this.getChatOpen()) {
            ScaledResolution scaledresolution = new ScaledResolution(this.mc);
            int i = scaledresolution.getScaleFactor();
            float f = this.getChatScale();
            int k = mouseY / i - (27 + (Stratus.INSTANCE.isPatcher() && ModCompatHooks.getChatPosition() ? 12 : 0)) + (Stratus.INSTANCE.isBetterChat() ? ModCompatHooks.getYOffset() : 0);
            k = MathHelper.floor_float((float) k / f);

            if (k >= 0) {
                int l = Math.min(this.getLineCount(), this.drawnChatLines.size());

                if (k < this.mc.fontRendererObj.FONT_HEIGHT * l + l) {
                    int i1 = k / this.mc.fontRendererObj.FONT_HEIGHT + this.scrollPos;

                    if (i1 >= 0 && i1 < this.drawnChatLines.size()) {
                        return this.drawnChatLines.get(i1).getChatComponent().getFormattedText();
                    }

                }
            }
        }
        return null;
    }
}
