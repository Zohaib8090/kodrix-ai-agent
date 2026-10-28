package com.example.agent.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import com.example.agent.Risk
import com.example.agent.Tool
import com.example.agent.ToolResult
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GetTimeTool : Tool {
    override val name = "get_current_time"
    override val description = "Returns the phone's current local date, time and timezone. Use it instead of guessing the date."
    override val parameters = JSONObject("""{"type":"object","properties":{}}""")
    override suspend fun run(args: JSONObject): ToolResult {
        val tz = TimeZone.getDefault()
        val f = SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = tz }
        return ToolResult("${f.format(Date())} (${tz.id})")
    }
}

/** Opens an installed app by its visible name (e.g. "Spotify") or package name. */
class OpenAppTool(private val context: Context) : Tool {
    override val name = "open_app"
    override val description = "Launch an installed app by its name or package name."
    override val parameters = JSONObject(
        """{"type":"object","properties":{"app":{"type":"string","description":"App label like 'Spotify' or a package name"}},"required":["app"]}"""
    )

    override suspend fun run(args: JSONObject): ToolResult {
        val wanted = args.optString("app").trim()
        if (wanted.isEmpty()) return ToolResult("Missing 'app'.", true)
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        val match = apps.firstOrNull { it.activityInfo.packageName.equals(wanted, true) }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().equals(wanted, true) }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().contains(wanted, true) }
            ?: return ToolResult(
                "No installed app matches '$wanted'. Installed examples: " +
                    apps.take(25).joinToString { it.loadLabel(pm).toString() },
                true,
            )
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return ToolResult("Found ${match.loadLabel(pm)} but it has no launch intent.", true)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ToolResult("Opened ${match.loadLabel(pm)}.")
    }
}

class SetAlarmTool(private val context: Context) : Tool {
    override val name = "set_alarm"
    override val description = "Create an alarm at a 24-hour time, using the system clock app."
    override val parameters = JSONObject(
        """{"type":"object","properties":{"hour":{"type":"integer","minimum":0,"maximum":23},"minute":{"type":"integer","minimum":0,"maximum":59},"label":{"type":"string"}},"required":["hour","minute"]}"""
    )

    override suspend fun run(args: JSONObject): ToolResult {
        val h = args.optInt("hour", -1)
        val m = args.optInt("minute", -1)
        if (h !in 0..23 || m !in 0..59) return ToolResult("hour must be 0-23 and minute 0-59.", true)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, args.optString("label"))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return ToolResult("No clock app can set alarms.", true)
        context.startActivity(intent)
        return ToolResult("Alarm requested for %02d:%02d.".format(h, m))
    }
}

class OpenUrlTool(private val context: Context) : Tool {
    override val name = "open_url"
    override val description = "Open an http(s) link in the browser."
    override val parameters = JSONObject("""{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}""")
    override suspend fun run(args: JSONObject): ToolResult {
        val url = args.optString("url").trim()
        val uri = Uri.parse(url)
        if (uri.scheme != "https" && uri.scheme != "http") return ToolResult("Only http(s) links are allowed.", true)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ToolResult("Opened $url")
    }
}

/** Opens the dialer with the number filled in. The user still presses call, so this is safe. */
class DialTool(private val context: Context) : Tool {
    override val name = "dial_number"
    override val description = "Open the phone dialer with a number filled in (does not place the call)."
    override val parameters = JSONObject("""{"type":"object","properties":{"number":{"type":"string"}},"required":["number"]}""")
    override suspend fun run(args: JSONObject): ToolResult {
        val n = args.optString("number").filter { it.isDigit() || it == '+' }
        if (n.length < 3) return ToolResult("That doesn't look like a phone number.", true)
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ToolResult("Dialer opened with $n. The user must press call.")
    }
}

/** Drafts a text message in the messaging app. The user still presses send. */
class DraftSmsTool(private val context: Context) : Tool {
    override val name = "draft_sms"
    override val description = "Open the messaging app with a recipient and message filled in (does not send)."
    override val risk = Risk.SAFE
    override val parameters = JSONObject(
        """{"type":"object","properties":{"number":{"type":"string"},"body":{"type":"string"}},"required":["number","body"]}"""
    )

    override suspend fun run(args: JSONObject): ToolResult {
        val n = args.optString("number").filter { it.isDigit() || it == '+' }
        if (n.length < 3) return ToolResult("That doesn't look like a phone number.", true)
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$n")).putExtra("sms_body", args.optString("body"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ToolResult("Message drafted to $n. The user must press send.")
    }
}
