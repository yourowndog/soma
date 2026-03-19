package io.brokentooth.soma.tools

import android.content.Context
import android.content.Intent

/**
 * Opens an app by package name.
 * Returns success/failure message.
 */
fun openApp(context: Context, packageName: String): String {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    return if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        "Opened $packageName"
    } else {
        "App not found: $packageName"
    }
}

/**
 * Scans assistant response for [[OPEN_APP:package.name]] patterns.
 * Returns a result string if a tool call was found, null otherwise.
 */
private val OPEN_APP_PATTERN = Regex("""\[\[OPEN_APP:([a-zA-Z0-9_.]+)]]""")

fun handleToolCalls(context: Context, response: String): String? {
    val match = OPEN_APP_PATTERN.find(response) ?: return null
    val packageName = match.groupValues[1]
    return openApp(context, packageName)
}
