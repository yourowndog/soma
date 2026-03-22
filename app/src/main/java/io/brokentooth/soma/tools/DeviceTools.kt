package io.brokentooth.soma.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.Log

private const val TAG = "DeviceTools"

/**
 * Semantic app glossary — maps human-friendly aliases to Android package names.
 * Generated from device inventory on March 21, 2026.
 * Device: Samsung SM-S938U (Galaxy S25 Ultra), Android 16
 */
private val APP_GLOSSARY: Map<String, String> = mapOf(
    // Communication
    "messages" to "com.google.android.apps.messaging",
    "messaging" to "com.google.android.apps.messaging",
    "texts" to "com.google.android.apps.messaging",
    "sms" to "com.google.android.apps.messaging",
    "text messages" to "com.google.android.apps.messaging",
    "google messages" to "com.google.android.apps.messaging",
    "phone" to "com.google.android.dialer",
    "dialer" to "com.google.android.dialer",
    "calls" to "com.google.android.dialer",
    "google phone" to "com.google.android.dialer",
    "samsung phone" to "com.samsung.android.dialer",
    "contacts" to "com.google.android.contacts",
    "people" to "com.google.android.contacts",
    "address book" to "com.google.android.contacts",
    "google contacts" to "com.google.android.contacts",
    "samsung contacts" to "com.samsung.android.app.contacts",
    "gmail" to "com.google.android.gm",
    "email" to "com.google.android.gm",
    "mail" to "com.google.android.gm",
    "google mail" to "com.google.android.gm",
    "messenger" to "com.facebook.orca",
    "facebook messenger" to "com.facebook.orca",
    "fb messenger" to "com.facebook.orca",
    "beeper" to "com.beeper.android",
    "google voice" to "com.google.android.apps.googlevoice",
    "voice" to "com.google.android.apps.googlevoice",
    "meet" to "com.google.android.apps.tachyon",
    "google meet" to "com.google.android.apps.tachyon",
    "video call" to "com.google.android.apps.tachyon",

    // Social
    "reddit" to "com.reddit.frontpage",
    "facebook" to "it.rignanese.leo.slimfacebook",
    "fb" to "it.rignanese.leo.slimfacebook",
    "quora" to "com.quora.android",

    // Browser & Search
    "chrome" to "com.android.chrome",
    "browser" to "com.android.chrome",
    "web" to "com.android.chrome",
    "internet" to "com.android.chrome",
    "google chrome" to "com.android.chrome",
    "samsung browser" to "com.sec.android.app.sbrowser",
    "samsung internet" to "com.sec.android.app.sbrowser",
    "firefox" to "org.mozilla.firefox",
    "google" to "com.google.android.googlequicksearchbox",
    "google search" to "com.google.android.googlequicksearchbox",
    "search" to "com.google.android.googlequicksearchbox",

    // Productivity & Tools
    "calendar" to "com.samsung.android.calendar",
    "samsung calendar" to "com.samsung.android.calendar",
    "schedule" to "com.samsung.android.calendar",
    "notes" to "com.google.android.keep",
    "keep" to "com.google.android.keep",
    "keep notes" to "com.google.android.keep",
    "google keep" to "com.google.android.keep",
    "obsidian" to "md.obsidian",
    "tasks" to "com.google.android.apps.tasks",
    "google tasks" to "com.google.android.apps.tasks",
    "todo" to "com.google.android.apps.tasks",
    "reminder" to "com.samsung.android.app.reminder",
    "reminders" to "com.samsung.android.app.reminder",
    "samsung reminders" to "com.samsung.android.app.reminder",
    "clock" to "com.sec.android.app.clockpackage",
    "alarm" to "com.sec.android.app.clockpackage",
    "timer" to "com.sec.android.app.clockpackage",
    "samsung clock" to "com.sec.android.app.clockpackage",
    "files" to "com.sec.android.app.myfiles",
    "my files" to "com.sec.android.app.myfiles",
    "file manager" to "com.sec.android.app.myfiles",
    "samsung files" to "com.sec.android.app.myfiles",
    "google files" to "com.google.android.apps.nbu.files",
    "files by google" to "com.google.android.apps.nbu.files",
    "drive" to "com.google.android.apps.docs",
    "google drive" to "com.google.android.apps.docs",
    "docs" to "com.google.android.apps.docs.editors.docs",
    "google docs" to "com.google.android.apps.docs.editors.docs",
    "translate" to "com.google.android.apps.translate",
    "google translate" to "com.google.android.apps.translate",
    "maps" to "com.google.android.apps.maps",
    "google maps" to "com.google.android.apps.maps",
    "navigation" to "com.google.android.apps.maps",
    "gps" to "com.google.android.apps.maps",

    // AI & Assistants
    "chatgpt" to "com.openai.chatgpt",
    "openai" to "com.openai.chatgpt",
    "claude" to "com.anthropic.claude",
    "anthropic" to "com.anthropic.claude",
    "gemini" to "com.google.android.apps.bard",
    "bard" to "com.google.android.apps.bard",
    "grok" to "ai.x.grok",
    "tailwind" to "com.google.android.apps.labs.language.tailwind",
    "notebooklm" to "com.google.android.apps.labs.language.tailwind",

    // Entertainment & Media
    "youtube" to "com.google.android.youtube",
    "yt" to "com.google.android.youtube",
    "videos" to "com.google.android.youtube",
    "newpipe" to "InfinityLoop1309.NewPipeEnhanced",
    "spotify" to "com.spotify.music",
    "music" to "com.spotify.music",
    "audible" to "com.audible.application",
    "audiobooks" to "com.audible.application",
    "prime video" to "com.amazon.avod.thirdpartyclient",
    "amazon video" to "com.amazon.avod.thirdpartyclient",
    "gallery" to "com.sec.android.gallery3d",
    "samsung gallery" to "com.sec.android.gallery3d",
    "photos" to "com.google.android.apps.photos",
    "google photos" to "com.google.android.apps.photos",
    "camera" to "com.sec.android.app.camera",
    "take photo" to "com.sec.android.app.camera",
    "samsung camera" to "com.sec.android.app.camera",
    "voice recorder" to "com.sec.android.app.voicenote",
    "recorder" to "com.sec.android.app.voicenote",
    "samsung voice recorder" to "com.sec.android.app.voicenote",
    "guitar tabs" to "com.ultimateguitar.tabs",
    "ultimate guitar" to "com.ultimateguitar.tabs",
    "simply guitar" to "com.joytunes.simplyguitar.tuner",
    "tuner" to "com.joytunes.simplyguitar.tuner",

    // Finance & Shopping
    "wallet" to "com.google.android.apps.walletnfcrel",
    "google wallet" to "com.google.android.apps.walletnfcrel",
    "google pay" to "com.google.android.apps.walletnfcrel",
    "venmo" to "com.venmo",
    "chase" to "com.chase.sig.android",
    "chase bank" to "com.chase.sig.android",
    "capital one" to "com.konylabs.capitalone",
    "discover" to "com.discoverfinancial.mobile",
    "barclays" to "com.barclaycardus",
    "vanguard" to "com.vanguard",
    "principal" to "gokart.com.principal",
    "amazon" to "com.amazon.mShop.android.shopping",
    "shopping" to "com.amazon.mShop.android.shopping",
    "walmart" to "com.walmart.android",

    // Health & Fitness
    "fitbit" to "com.fitbit.FitbitMobile",
    "health" to "com.fitbit.FitbitMobile",

    // Utilities & System
    "settings" to "com.android.settings",
    "phone settings" to "com.android.settings",
    "device settings" to "com.android.settings",
    "play store" to "com.android.vending",
    "google play" to "com.android.vending",
    "app store" to "com.android.vending",
    "galaxy store" to "com.sec.android.app.samsungapps",
    "samsung store" to "com.sec.android.app.samsungapps",
    "f-droid" to "org.fdroid.fdroid",
    "fdroid" to "org.fdroid.fdroid",
    "good lock" to "com.samsung.android.goodlock",
    "tasker" to "net.dinglisch.android.taskerm",
    "brave dns" to "com.celzero.bravedns",
    "vpn" to "secure.unblock.unlimited.proxy.snap.hotspot.shield",
    "hotspot shield" to "secure.unblock.unlimited.proxy.snap.hotspot.shield",
    "allstate" to "com.allstate.view",
    "teladoc" to "com.teladoc.members",
    "indeed" to "com.indeed.android.jobsearch",
    "job search" to "com.indeed.android.jobsearch",
    "adp" to "com.adpmobile.android",
    "payroll" to "com.adpmobile.android",

    // Developer Tools
    "termux" to "com.termux",
    "terminal" to "com.termux",
    "shell" to "com.termux",
    "github" to "com.github.android",
    "shortcut maker" to "rk.android.app.shortcutmaker",

    // Games
    "minecraft" to "com.mojang.minecraftpe",
    "stardew valley" to "com.chucklefish.stardewvalley",
    "chess" to "com.chess",
    "sudoku" to "com.cronoprice.sudoku",
    "polytopia" to "air.com.midjiwan.polytopia",

    // SOMA itself
    "soma" to "io.brokentooth.soma"
)

fun openApp(context: Context, appNameOrPackage: String): String {
    try {
        val input = appNameOrPackage.trim()

        // 1. Glossary lookup (semantic alias → package)
        val glossaryPkg = APP_GLOSSARY[input.lowercase()]
        if (glossaryPkg != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(glossaryPkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "Opened app via glossary: '$input' → $glossaryPkg")
                return "Opened $glossaryPkg"
            }
        }

        // 2. Exact package name match
        val intent = context.packageManager.getLaunchIntentForPackage(input)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "Opened app by package: $input")
            return "Opened $input"
        }

        // 3. Fuzzy search: scan installed apps by label
        val pm = context.packageManager
        val searchLower = input.lowercase()
        val installedApps = pm.getInstalledApplications(0)
        val matches = installedApps.mapNotNull { appInfo ->
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            if (label.contains(searchLower) || searchLower.contains(label)) {
                val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent != null) "$label (${appInfo.packageName})" else null
            } else null
        }

        if (matches.size == 1) {
            val pkg = matches[0].substringAfter("(").substringBefore(")")
            val launchIntent = pm.getLaunchIntentForPackage(pkg)!!
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            Log.i(TAG, "Opened app by fuzzy search: $pkg")
            return "Opened ${matches[0]}"
        }
        if (matches.size > 1) {
            return "Multiple apps match '$input': ${matches.joinToString(", ")}. Please be more specific."
        }

        Log.w(TAG, "App not found: $input")
        return "App not found: $input"
    } catch (e: Exception) {
        Log.e(TAG, "Error opening app: $appNameOrPackage", e)
        return "Error opening app: $appNameOrPackage - ${e.message}"
    }
}

fun getDeviceInfo(context: Context): String {
    val deviceInfo = StringBuilder()
    
    // Device model
    deviceInfo.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
    
    // Android version
    deviceInfo.append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
    
    // Battery info
    try {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging
        deviceInfo.append("Battery: $batteryLevel% (${if (isCharging) "charging" else "not charging"})\n")
    } catch (e: Exception) {
        Log.e(TAG, "Error getting battery info", e)
        deviceInfo.append("Battery: Unknown\n")
    }
    
    // Network info
    try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        
        val networkType = when {
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            else -> "None"
        }
        deviceInfo.append("Network: $networkType\n")
    } catch (e: Exception) {
        Log.e(TAG, "Error getting network info", e)
        deviceInfo.append("Network: Unknown\n")
    }
    
    Log.d(TAG, "Device info retrieved")
    return deviceInfo.toString()
}

fun readClipboard(context: Context): String {
    try {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip
        
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString() ?: ""
            Log.d(TAG, "Read from clipboard: ${if (text.length > 50) "${text.take(50)}..." else text}")
            return text
        } else {
            Log.d(TAG, "Clipboard is empty")
            return "(empty clipboard)"
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error reading clipboard", e)
        return "Error reading clipboard: ${e.message}"
    }
}

fun writeClipboard(context: Context, text: String): String {
    try {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("SOMA Copied Text", text)
        clipboardManager.setPrimaryClip(clipData)
        
        val resultText = if (text.length > 50) "${text.take(50)}..." else text
        Log.i(TAG, "Wrote to clipboard: $resultText")
        return "Copied to clipboard: $resultText"
    } catch (e: Exception) {
        Log.e(TAG, "Error writing to clipboard", e)
        return "Error writing to clipboard: ${e.message}"
    }
}