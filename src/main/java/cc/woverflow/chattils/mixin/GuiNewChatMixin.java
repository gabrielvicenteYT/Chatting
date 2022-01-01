package cc.woverflow.chattils.mixin;

import cc.woverflow.chattils.Chattils;
import cc.woverflow.chattils.chat.ChatSearchingManager;
import cc.woverflow.chattils.chat.ChatTabs;
import cc.woverflow.chattils.config.ChattilsConfig;
import cc.woverflow.chattils.hook.GuiNewChatHook;
import cc.woverflow.chattils.utils.ModCompatHooks;
import cc.woverflow.chattils.utils.RenderHelper;
import gg.essential.universal.UMouse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

@Mixin(value = GuiNewChat.class, priority = Integer.MIN_VALUE)
public abstract class GuiNewChatMixin extends Gui implements GuiNewChatHook {
    @Unique private int chattils$right = 0;
    @Unique private boolean chattils$shouldCopy;
    @Unique private boolean chattils$chatCheck;
    @Unique private int chattils$textOpacity;
    @Shadow @Final private Minecraft mc;
    @Shadow @Final private List<ChatLine> drawnChatLines;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private float percentComplete;
    private String chattils$previousText = "";

    @Shadow public abstract boolean getChatOpen();

    @Shadow public abstract float getChatScale();

    @Shadow public abstract int getLineCount();

    @Shadow private int scrollPos;
    @Shadow @Final private List<ChatLine> chatLines;

    @Shadow public abstract void deleteChatLine(int id);

    @Unique private static final ResourceLocation COPY = new ResourceLocation("chattils:copy.png");

    @Inject(method = "printChatMessageWithOptionalDeletion", at = @At("HEAD"), cancellable = true)
    private void handlePrintChatMessage(IChatComponent chatComponent, int chatLineId, CallbackInfo ci) {
        handleChatTabMessage(chatComponent, chatLineId, mc.ingameGUI.getUpdateCounter(), false, ci);
        if (!EnumChatFormatting.getTextWithoutFormattingCodes(chatComponent.getUnformattedText()).toLowerCase(Locale.ENGLISH).contains(chattils$previousText.toLowerCase(Locale.ENGLISH))) {
            percentComplete = 1.0F;
        }
    }

    @Inject(method = "setChatLine", at = @At("HEAD"), cancellable = true)
    private void handleSetChatLine(IChatComponent chatComponent, int chatLineId, int updateCounter, boolean displayOnly, CallbackInfo ci) {
        ChatSearchingManager.getCache().invalidateAll();
        handleChatTabMessage(chatComponent, chatLineId, updateCounter, displayOnly, ci);
    }

    @Inject(method = "drawChat", at = @At("HEAD"))
    private void checkScreenshotKeybind(int j2, CallbackInfo ci) {
        if (Chattils.INSTANCE.getKeybind().isPressed()) {
            Chattils.INSTANCE.setDoTheThing(true);
        }
        chattils$chatCheck = false;
    }

    @ModifyVariable(method = "drawChat", at = @At("HEAD"), argsOnly = true)
    private int setUpdateCounterWhjenYes(int updateCounter) {
        return Chattils.INSTANCE.getDoTheThing() ? 0 : updateCounter;
    }

    @ModifyVariable(method = "drawChat", at = @At("STORE"), index = 2)
    private int setChatLimitWhenYes(int linesToDraw) {
        return Chattils.INSTANCE.getDoTheThing()
                ? GuiNewChat.calculateChatboxHeight(mc.gameSettings.chatHeightFocused) / 9
                : linesToDraw;
    }

    @ModifyArgs(method = "drawChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiNewChat;drawRect(IIIII)V"), slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;clamp_double(DDD)D"), to = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;enableBlend()V")))
    private void captureDrawRect(Args args) {
        int left = args.get(0);
        int top = args.get(1);
        int right = args.get(2);
        int bottom = args.get(3);
        if (mc.currentScreen instanceof GuiChat) {
            float f = this.getChatScale();
            int mouseX = MathHelper.floor_double(UMouse.getScaledX()) - 3;
            int mouseY = MathHelper.floor_double(UMouse.getScaledY()) - 27 + ModCompatHooks.getYOffset() - ModCompatHooks.getChatPosition();
            mouseX = MathHelper.floor_float((float)mouseX / f);
            mouseY = -(MathHelper.floor_float((float)mouseY / f)); //WHY DO I NEED TO DO THIS
            if (mouseX >= (left + ModCompatHooks.getXOffset()) && mouseY < bottom && mouseX < (right + 9 + ModCompatHooks.getXOffset()) && mouseY >= top) {
                chattils$shouldCopy = true;
                drawCopyChatBox(right, top);
            }
        }
    }

    @Redirect(method = "drawChat", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GuiNewChat;drawnChatLines:Ljava/util/List;", opcode = Opcodes.GETFIELD))
    private List<ChatLine> injected(GuiNewChat instance) {
        return ChatSearchingManager.filterMessages(chattils$previousText, drawnChatLines);
    }

    @ModifyVariable(method = "drawChat", at = @At("STORE"), ordinal = 7)
    private int modifyYeah(int value) {
        return chattils$textOpacity = (int) (((float) (getChatOpen() ? 255 : value)) * (mc.gameSettings.chatOpacity * 0.9F + 0.1F));
    }

    @Redirect(method = "drawChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"))
    private int redirectDrawString(FontRenderer instance, String text, float x, float y, int color) {
        return ModCompatHooks.redirectDrawString(text, x, y, color);
    }

    @Inject(method = "drawChat", at = @At("RETURN"))
    private void checkStuff(int j2, CallbackInfo ci) {
        if (!chattils$chatCheck && chattils$shouldCopy) {
            chattils$shouldCopy = false;
        }
    }

    @Inject(method = "getChatHeight", at = @At("HEAD"), cancellable = true)
    private void customHeight_getChatHeight(CallbackInfoReturnable<Integer> cir) {
        if (ChattilsConfig.INSTANCE.getCustomChatHeight()) cir.setReturnValue(Chattils.INSTANCE.getChatHeight(this.getChatOpen()));
    }

    @Override
    public int getRight() {
        return chattils$right;
    }

    @Override
    public boolean shouldCopy() {
        return chattils$shouldCopy;
    }

    private void handleChatTabMessage(IChatComponent chatComponent, int chatLineId, int updateCounter, boolean displayOnly, CallbackInfo ci) {
        if (ChattilsConfig.INSTANCE.getChatTabs()) {
            if (!ChatTabs.INSTANCE.shouldRender(chatComponent)) {
                percentComplete = 1.0F;
                if (chatLineId != 0) {
                    deleteChatLine(chatLineId);
                }
                if (!displayOnly) {
                    chatLines.add(0, new ChatLine(updateCounter, chatComponent, chatLineId));
                    while (this.chatLines.size() > (100 + ModCompatHooks.getExtendedChatLength()))
                    {
                        this.chatLines.remove(this.chatLines.size() - 1);
                    }
                }
                ci.cancel();
            }
        }
    }

    private void drawCopyChatBox(int right, int top) {
        chattils$chatCheck = true;
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
        chattils$right = right;
        Gui.drawModalRectWithCustomSizedTexture(right, top, 0f, 0f, 9, 9, 9, 9);
        GlStateManager.disableAlpha();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.popMatrix();
    }

    @Override
    public Transferable getChattilsChatComponent(int mouseY) {
        if (this.getChatOpen()) {
            ScaledResolution scaledresolution = new ScaledResolution(this.mc);
            int i = scaledresolution.getScaleFactor();
            float f = this.getChatScale();
            int k = mouseY / i - 27 + ModCompatHooks.getYOffset() - ModCompatHooks.getChatPosition();
            k = MathHelper.floor_float((float) k / f);

            if (k >= 0) {
                int l = Math.min(this.getLineCount(), this.drawnChatLines.size());

                if (k < this.mc.fontRendererObj.FONT_HEIGHT * l + l) {
                    int i1 = k / this.mc.fontRendererObj.FONT_HEIGHT + this.scrollPos;

                    if (i1 >= 0 && i1 < this.drawnChatLines.size()) {
                        ChatLine subLine = this.drawnChatLines.get(i1);
                        ChatLine fullLine = this.getFullMessage(subLine);
                        if (GuiScreen.isShiftKeyDown()) {
                            if (fullLine != null) {
                                BufferedImage image = Chattils.INSTANCE.screenshotLine(fullLine);
                                if (image != null) RenderHelper.INSTANCE.copyBufferedImageToClipboard(image);
                            }
                            return null;
                        }
                        ChatLine line = GuiScreen.isCtrlKeyDown() ? subLine : fullLine;
                        String message = line == null ? "Could not find chat message." : line.getChatComponent().getFormattedText();
                        return new StringSelection(GuiScreen.isAltKeyDown() ? message : EnumChatFormatting.getTextWithoutFormattingCodes(message));
                    }

                }
            }
        }
        return null;
    }

    @Override
    public String getPrevText() {
        return chattils$previousText;
    }

    @Override
    public void setPrevText(String prevText) {
        chattils$previousText = prevText;
    }

    @Override
    public int getTextOpacity() {
        return chattils$textOpacity;
    }
}