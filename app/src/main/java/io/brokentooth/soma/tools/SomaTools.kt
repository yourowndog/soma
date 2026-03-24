package io.brokentooth.soma.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/** Holds the Android application context for tool execution. Set before running any agent. */
object ToolContext {
    lateinit var appContext: Context
    
    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
object OpenAppTool : SimpleTool<OpenAppTool.Args>(
    argsSerializer = serializer<Args>(),
    name = "open_app",
    description = "Opens an Android app. You can pass either a package name (e.g. com.android.chrome) or an app name (e.g. 'messages', 'chrome', 'settings'). If the exact package isn't found, it searches installed apps by name. Common packages: com.android.chrome (Chrome), com.google.android.gm (Gmail), com.google.android.apps.maps (Maps), com.android.settings (Settings), com.google.android.youtube (YouTube)"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("App package name (e.g. com.android.chrome) or app name keyword (e.g. 'messages', 'chrome')")
        val packageName: String
    )
    
    override suspend fun execute(args: Args): String {
        return openApp(ToolContext.appContext, args.packageName)
    }
}

object GetDeviceInfoTool : SimpleTool<GetDeviceInfoTool.Args>(
    argsSerializer = serializer<Args>(),
    name = "get_device_info",
    description = "Get device information including battery level, charging status, network connectivity, and device model"
) {
    @Serializable
    class Args
    
    override suspend fun execute(args: Args): String {
        return getDeviceInfo(ToolContext.appContext)
    }
}

object ClipboardReadTool : SimpleTool<ClipboardReadTool.Args>(
    argsSerializer = serializer<Args>(),
    name = "clipboard_read",
    description = "Read the current text from the device clipboard"
) {
    @Serializable
    class Args
    
    override suspend fun execute(args: Args): String {
        return readClipboard(ToolContext.appContext)
    }
}

object ClipboardWriteTool : SimpleTool<ClipboardWriteTool.Args>(
    argsSerializer = serializer<Args>(),
    name = "clipboard_write",
    description = "Write text to the device clipboard"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Text to copy to clipboard")
        val text: String
    )
    
    override suspend fun execute(args: Args): String {
        return writeClipboard(ToolContext.appContext, args.text)
    }
}

object FlashlightTool : SimpleTool<FlashlightTool.Args>(
    argsSerializer = serializer<Args>(),
    name = "toggle_flashlight",
    description = "Turn the device flashlight (camera flash) on or off"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Set to true to turn on, false to turn off")
        val state: Boolean
    )
    
    override suspend fun execute(args: Args): String {
        return toggleFlashlight(ToolContext.appContext, args.state)
    }
}

object SettingsPanelTool : SimpleTool<SettingsPanelTool.Args>(
    argsSerializer = serializer<Args>(),
    name = "open_settings_panel",
    description = "Open a specific Android settings panel (e.g. wifi, bluetooth, display, sound, apps, battery, location)"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The name of the settings panel to open (e.g. 'wifi', 'bluetooth', 'display')")
        val panel: String
    )
    
    override suspend fun execute(args: Args): String {
        return openSettingsPanel(ToolContext.appContext, args.panel)
    }
}