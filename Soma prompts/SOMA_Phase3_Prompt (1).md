# SOMA Phase 3: Android Deep Integration

## Difficulty: Medium — Gemini Pro or Sonnet can handle this
## Prerequisite: Phase 2 complete (ToolRegistry working, function calling)

---

## Context

SOMA has a working tool registry with open_app, device_info, clipboard.
Phase 3 adds the tools that make it a real Android assistant: launching
any activity/intent, controlling system settings, reading notifications,
and running as a persistent background service.

All new tools are registered in ToolRegistry using the same pattern as
Phase 2. The LLM calls them via native function calling.

---

## Task 3a: Intent & Activity Tools

Add to `tools/IntentTools.kt`:

```kotlin
package io.brokentooth.soma.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Open any Android settings panel by name.
 */
fun openSettings(context: Context, panel: String): String {
    val settingsMap = mapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS,
        "sound" to Settings.ACTION_SOUND_SETTINGS,
        "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        "apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "security" to Settings.ACTION_SECURITY_SETTINGS,
        "accounts" to Settings.ACTION_SYNC_SETTINGS,
        "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
        "developer" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        "notifications" to Settings.ACTION_APP_NOTIFICATION_SETTINGS,
        "date" to Settings.ACTION_DATE_SETTINGS,
        "language" to Settings.ACTION_LOCALE_SETTINGS,
        "default_apps" to Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
        "data_usage" to Settings.ACTION_DATA_USAGE_SETTINGS,
        "nfc" to Settings.ACTION_NFC_SETTINGS,
        "main" to Settings.ACTION_SETTINGS,
    )

    val action = settingsMap[panel.lowercase()] ?: Settings.ACTION_SETTINGS
    val intent = Intent(action).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        "Opened ${panel} settings"
    } catch (e: Exception) {
        "Failed to open ${panel} settings: ${e.message}"
    }
}

/**
 * Open a URL in the default browser.
 */
fun openUrl(context: Context, url: String): String {
    val uri = if (url.startsWith("http")) Uri.parse(url) else Uri.parse("https://$url")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        "Opened $url"
    } catch (e: Exception) {
        "Failed to open URL: ${e.message}"
    }
}

/**
 * Make a phone call (opens dialer with number pre-filled).
 */
fun dialNumber(context: Context, number: String): String {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        "Opening dialer for $number"
    } catch (e: Exception) {
        "Failed to open dialer: ${e.message}"
    }
}

/**
 * Send a text/share intent.
 */
fun shareText(context: Context, text: String): String {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(Intent.createChooser(intent, "Share via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        "Opened share dialog"
    } catch (e: Exception) {
        "Failed to share: ${e.message}"
    }
}

/**
 * Search Google for a query.
 */
fun searchWeb(context: Context, query: String): String {
    val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
        putExtra("query", query)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        "Searching for: $query"
    } catch (e: Exception) {
        // Fallback to browser
        openUrl(context, "https://www.google.com/search?q=${Uri.encode(query)}")
    }
}
```

Register all of these in `ToolRegistry.kt`:
```kotlin
// In registerDefaults():

register(ToolDefinition(
    name = "open_settings",
    description = "Open an Android settings panel. Available panels: wifi, bluetooth, " +
        "display, sound, battery, storage, apps, location, security, notifications, " +
        "date, language, default_apps, data_usage, nfc, main",
    parameters = mapOf(
        "panel" to ToolParameter("string", "Settings panel to open")
    )
)) { params -> openSettings(context, params["panel"]?.toString() ?: "main") }

register(ToolDefinition(
    name = "open_url",
    description = "Open a URL in the default browser",
    parameters = mapOf(
        "url" to ToolParameter("string", "URL to open")
    )
)) { params -> openUrl(context, params["url"]?.toString() ?: return@register "Error: no URL") }

register(ToolDefinition(
    name = "dial_number",
    description = "Open the phone dialer with a number pre-filled (does not auto-call)",
    parameters = mapOf(
        "number" to ToolParameter("string", "Phone number to dial")
    )
)) { params -> dialNumber(context, params["number"]?.toString() ?: return@register "Error: no number") }

register(ToolDefinition(
    name = "share_text",
    description = "Open the Android share dialog to share text to any app",
    parameters = mapOf(
        "text" to ToolParameter("string", "Text to share")
    )
)) { params -> shareText(context, params["text"]?.toString() ?: return@register "Error: no text") }

register(ToolDefinition(
    name = "search_web",
    description = "Search the web for a query",
    parameters = mapOf(
        "query" to ToolParameter("string", "Search query")
    )
)) { params -> searchWeb(context, params["query"]?.toString() ?: return@register "Error: no query") }
```

---

## Task 3b: System Control Tools

Add to `tools/SystemTools.kt`:

```kotlin
package io.brokentooth.soma.tools

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings

/**
 * Set media volume (0-100 percentage).
 */
fun setVolume(context: Context, percentage: Int): String {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val targetVolume = (maxVolume * percentage.coerceIn(0, 100) / 100)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
    return "Volume set to $percentage% ($targetVolume/$maxVolume)"
}

/**
 * Get current volume level.
 */
fun getVolume(context: Context): String {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val pct = if (max > 0) (current * 100 / max) else 0
    return "Current volume: $pct% ($current/$max)"
}

/**
 * Set Do Not Disturb mode.
 * Requires NOTIFICATION_POLICY_ACCESS permission (user must grant in settings).
 */
fun setDoNotDisturb(context: Context, enabled: Boolean): String {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (!notificationManager.isNotificationPolicyAccessGranted) {
        return "DND permission not granted. Please enable in Settings > Notifications > Do Not Disturb access"
    }
    val filter = if (enabled) {
        NotificationManager.INTERRUPTION_FILTER_NONE
    } else {
        NotificationManager.INTERRUPTION_FILTER_ALL
    }
    notificationManager.setInterruptionFilter(filter)
    return "Do Not Disturb ${if (enabled) "enabled" else "disabled"}"
}

/**
 * Set screen brightness (0-100 percentage).
 * Requires WRITE_SETTINGS permission.
 */
fun setBrightness(context: Context, percentage: Int): String {
    if (!Settings.System.canWrite(context)) {
        return "Brightness permission not granted. Please enable in Settings > Apps > SOMA > Modify system settings"
    }
    val brightness = (255 * percentage.coerceIn(0, 100) / 100)
    // Disable auto-brightness first
    Settings.System.putInt(context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
    Settings.System.putInt(context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS, brightness)
    return "Brightness set to $percentage%"
}

/**
 * Toggle flashlight.
 */
fun toggleFlashlight(context: Context, on: Boolean): String {
    return try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return "No camera found"
        cameraManager.setTorchMode(cameraId, on)
        "Flashlight ${if (on) "ON" else "OFF"}"
    } catch (e: Exception) {
        "Flashlight error: ${e.message}"
    }
}

/**
 * Set an alarm using the system alarm app.
 */
fun setAlarm(context: Context, hour: Int, minute: Int, message: String): String {
    val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
        putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
        putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
        putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        "Setting alarm for ${String.format("%02d:%02d", hour, minute)}: $message"
    } catch (e: Exception) {
        "Failed to set alarm: ${e.message}"
    }
}

/**
 * Set a timer.
 */
fun setTimer(context: Context, seconds: Int, message: String): String {
    val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
        putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds)
        putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
        putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        "Setting timer for ${seconds}s: $message"
    } catch (e: Exception) {
        "Failed to set timer: ${e.message}"
    }
}
```

Register ALL of these in ToolRegistry with appropriate descriptions and parameters.

---

## Task 3c: Foreground Service

Create file: `service/SomaService.kt`

A foreground service that:
1. Shows a persistent notification ("SOMA is running")
2. Keeps the agent alive in background
3. Survives screen off / app switch
4. Has notification actions: "Open SOMA", "Stop"

```kotlin
package io.brokentooth.soma.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.brokentooth.soma.MainActivity
import io.brokentooth.soma.R

class SomaService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SOMA Agent Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps SOMA running in the background"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val stopIntent = Intent(this, SomaService::class.java).apply {
            action = ACTION_STOP
        }.let {
            PendingIntent.getService(this, 1, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SOMA")
            .setContentText("Agent running")
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Replace with custom icon
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "soma_service"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "io.brokentooth.soma.STOP_SERVICE"
    }
}
```

Add to AndroidManifest.xml:
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
<uses-permission android:name="android.permission.SET_ALARM" />
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />

<service
    android:name=".service.SomaService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

Start the service when the app launches (in MainActivity or SomaApplication):
```kotlin
val serviceIntent = Intent(this, SomaService::class.java)
startForegroundService(serviceIntent)
```

---

## Task 3d: Permissions Request Flow

Create file: `ui/PermissionsScreen.kt`

On first launch (or when a tool needs a permission), show a clean screen
explaining what permissions SOMA needs and why:
- Notification access (for foreground service)
- Modify system settings (for brightness)
- DND access (for Do Not Disturb)
- Camera (for flashlight)
- SET_ALARM (for alarms/timers)

Use Accompanist Permissions or manual permission requests.
Show each permission with a description and a "Grant" button.
Skip permissions that are already granted.

---

## Testing

1. "Set my volume to 50%" → volume changes
2. "Turn on the flashlight" → flashlight turns on
3. "Turn off the flashlight" → flashlight turns off
4. "Set brightness to 75%" → brightness changes (may need permission)
5. "Open wifi settings" → WiFi settings panel opens
6. "Search for weather in Texoma" → browser opens with Google search
7. "Set a timer for 5 minutes for laundry" → timer app opens
8. "Open google.com" → browser opens to Google
9. "Call 555-1234" → dialer opens with number
10. App stays running in background via foreground service notification
11. Tapping the notification opens SOMA
12. "Stop" action in notification kills the service
