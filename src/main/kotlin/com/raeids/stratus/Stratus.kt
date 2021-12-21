package com.raeids.stratus

import com.raeids.stratus.command.StratusCommand
import com.raeids.stratus.config.StratusConfig
import com.raeids.stratus.hook.ChatTabs
import com.raeids.stratus.hook.GuiNewChatHook
import com.raeids.stratus.mixin.GuiNewChatAccessor
import com.raeids.stratus.updater.Updater
import com.raeids.stratus.utils.RenderHelper
import gg.essential.api.EssentialAPI
import gg.essential.universal.UDesktop
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.gui.GuiChat
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.settings.KeyBinding
import net.minecraft.client.shader.Framebuffer
import net.minecraftforge.client.event.MouseEvent
import net.minecraftforge.common.MinecraftForge.EVENT_BUS
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


@Mod(
    modid = Stratus.ID,
    name = Stratus.NAME,
    version = Stratus.VER,
    modLanguageAdapter = "gg.essential.api.utils.KotlinAdapter"
)
object Stratus {

    val keybind = KeyBinding("Screenshot Chat", Keyboard.KEY_NONE, "Stratus")
    const val NAME = "@NAME@"
    const val VER = "@VER@"
    const val ID = "@ID@"
    var doTheThing = false
    lateinit var jarFile: File
        private set

    private val fileFormatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss'.png'")

    val modDir = File(File(Minecraft.getMinecraft().mcDataDir, "W-OVERFLOW"), NAME)

    @Mod.EventHandler
    fun onFMLPreInitialization(event: FMLPreInitializationEvent) {
        if (!modDir.exists()) modDir.mkdirs()
        jarFile = event.sourceFile
    }

    @Mod.EventHandler
    fun onInitialization(event: FMLInitializationEvent) {
        StratusConfig.preload()
        StratusCommand.register()
        ClientRegistry.registerKeyBinding(keybind)
        EVENT_BUS.register(this)
        ChatTabs.initialize()
        Updater.update()
    }

    @SubscribeEvent
    fun onMouseClick(event: MouseEvent) {
        val hook = Minecraft.getMinecraft().ingameGUI.chatGUI as GuiNewChatHook
        if (hook.shouldCopy()) {
            try {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(hook.copyString()), null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SubscribeEvent
    fun onTickEvent(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START && Minecraft.getMinecraft().theWorld != null && Minecraft.getMinecraft().thePlayer != null && (Minecraft.getMinecraft().currentScreen == null || Minecraft.getMinecraft().currentScreen is GuiChat)) {
            if (doTheThing) {
                screenshot()
                doTheThing = false
            }
        }
    }

    private fun screenshot() {
        val hud = Minecraft.getMinecraft().ingameGUI
        val chat = hud.chatGUI

        /* Render chat fully. */
        var w = chat.chatWidth
        var h = chat.chatHeight
        if ((chat as GuiNewChatAccessor).drawnChatLines.size < 20) {
            h = (chat as GuiNewChatAccessor).drawnChatLines
                .size * Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT
        }
        if (w <= 0 || h <= 0 || (chat as GuiNewChatAccessor).drawnChatLines.isEmpty()) {
            EssentialAPI.getNotifications().push("Stratus", "Chat window is empty.")
            return
        }
        val chatLines: MutableList<String> = ArrayList()
        val fr: FontRenderer = Minecraft.getMinecraft().fontRendererObj
        for (chatLine in (chat as GuiNewChatAccessor).drawnChatLines) chatLines.add(chatLine.chatComponent.formattedText)
        if (chatLines.isNotEmpty()) {
            w = fr.getStringWidth(chatLines.stream().max(Comparator.comparingInt { obj: String -> obj.length }).get())
        }
        val fb: Framebuffer = RenderHelper.createBindFramebuffer(w, h)
        GlStateManager.translate(-2f, (160 - (180 - h)).toFloat(), 0f)
        chat.drawChat(hud.updateCounter)
        val file = File(Minecraft.getMinecraft().mcDataDir, "screenshots/chat/" + fileFormatter.format(Date()))
        RenderHelper.screenshotFramebuffer(fb, file)
        Minecraft.getMinecraft().entityRenderer.setupOverlayRendering()
        Minecraft.getMinecraft().framebuffer.bindFramebuffer(true)
        EssentialAPI.getNotifications()
            .push("Stratus", "Chat screenshotted successfully.\nClick to open.") {
                try {
                    UDesktop.browse(file.toURI())
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: UnsupportedOperationException) {
                    e.printStackTrace()
                    EssentialAPI.getNotifications().push("Stratus", "Could not browse!")
                }
            }
    }
}
