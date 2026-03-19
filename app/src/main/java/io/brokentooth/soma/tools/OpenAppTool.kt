package io.brokentooth.soma.tools

import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "OpenAppTool"

/**
 * Opens an app by package name.
 * Returns success/failure message.
 */
fun openApp(context: Context, packageName: String): String {
    Log.d(TAG, "openApp called: $packageName")
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    return if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Log.i(TAG, "Successfully launched: $packageName")
        "Opened $packageName"
    } else {
        Log.w(TAG, "No launch intent found for: $packageName")
        "App not found: $packageName"
    }
}

/**
 * Scans assistant response for [[OPEN_APP:package.name]] patterns.
 * Returns a result string if a tool call was found, null otherwise.
 */
private val OPEN_APP_PATTERN = Regex("""\[\[OPEN_APP:([a-zA-Z0-9_.]+)]]""")

fun handleToolCalls(context: Context, response: String): String? {
    Log.d(TAG, "handleToolCalls: scanning response (${response.length} chars)")
    val match = OPEN_APP_PATTERN.find(response)
    if (match == null) {
        Log.d(TAG, "handleToolCalls: no OPEN_APP pattern found")
        // Log a snippet so we can see what the model actually returned
        Log.d(TAG, "handleToolCalls: response snippet = ${response.take(200)}")
        return null
    }
    val packageName = match.groupValues[1]
    Log.i(TAG, "handleToolCalls: found OPEN_APP pattern, package=$packageName")
    return openApp(context, packageName)
}
