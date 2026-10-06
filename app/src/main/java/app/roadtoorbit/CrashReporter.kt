package app.roadtoorbit

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Makes crashes diagnosable without a debugger or a cable. An uncaught exception on any thread (and any error the
 * game recovers from) is written to a small text file together with what usually explains it: the stack trace,
 * the device, the memory situation, a census of the threads and the last things the game did. The next launch
 * shows the report with a COPY button, so it can be pasted into a bug report.
 *
 * Everything in here is defensive: it must work while the heap is full and must never throw.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"
    private const val MAX_CHARS = 5_000
    private const val BREADCRUMBS = 14

    private val crumbs = ArrayDeque<String>()

    @Volatile private var file: File? = null
    @Volatile private var device = "unknown device"
    @Volatile private var previous: Thread.UncaughtExceptionHandler? = null

    /** Call as early as possible (MainActivity.onCreate). Safe to call more than once. */
    fun install(context: Context) {
        try {
            file = File(context.applicationContext.filesDir, FILE)
            device = describeDevice(context)
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is Handler) return
            previous = current
            Thread.setDefaultUncaughtExceptionHandler(Handler)
        } catch (_: Throwable) {
            // reporting is a convenience, never a requirement
        }
    }

    private object Handler : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            save("The app was closed by an uncaught exception", t.name, e)
            previous?.uncaughtException(t, e)
        }
    }

    /** Remembers a short note about what the game just did ("phase RUN", "action PLAY"). Cheap; call on events, not per frame. */
    fun note(message: String) {
        try {
            synchronized(crumbs) {
                if (crumbs.size >= BREADCRUMBS) crumbs.removeFirst()
                crumbs.addLast(message)
            }
        } catch (_: Throwable) {
        }
    }

    /** Writes the report to disk and returns its text (null if even that failed). Never throws. */
    fun save(kind: String, threadName: String, error: Throwable): String? {
        val text = try {
            format(kind, threadName, error)
        } catch (_: Throwable) {
            // most likely out of memory while formatting: fall back to the bare minimum
            "$kind on thread \"$threadName\"\n${error.javaClass.name}: ${safeMessage(error)}"
        }
        try {
            file?.writeText(text)
        } catch (_: Throwable) {
        }
        return text
    }

    /** The report left behind by the previous session, or null. */
    fun pending(context: Context): String? = try {
        val f = File(context.applicationContext.filesDir, FILE)
        if (f.isFile && f.length() > 0) f.readText().take(MAX_CHARS) else null
    } catch (_: Throwable) {
        null
    }

    fun clear(context: Context) {
        try {
            File(context.applicationContext.filesDir, FILE).delete()
        } catch (_: Throwable) {
        }
    }

    // ---- the report ------------------------------------------------------------------------------------

    internal fun format(kind: String, threadName: String, error: Throwable): String {
        val sb = StringBuilder(2048)
        sb.append("Road to Orbit report\n")
        sb.append(kind).append(" on thread \"").append(threadName).append("\" at ")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
        appendError(sb, error, 24)
        var cause = error.cause
        var depth = 0
        while (cause != null && depth < 3) {
            sb.append("Caused by: ")
            appendError(sb, cause, 8)
            cause = cause.cause
            depth++
        }
        sb.append('\n').append(device).append('\n')
        sb.append(memoryLine()).append('\n')
        sb.append(threadLine()).append('\n')
        val recent = try { synchronized(crumbs) { crumbs.joinToString(" > ") } } catch (_: Throwable) { "" }
        if (recent.isNotEmpty()) sb.append("Recent: ").append(recent).append('\n')
        return sb.toString().take(MAX_CHARS)
    }

    /** The message, however broken the exception is (a custom getMessage can throw). */
    private fun safeMessage(e: Throwable): String = try {
        e.message?.take(300) ?: "(no message)"
    } catch (_: Throwable) {
        "(message unavailable)"
    }

    private fun appendError(sb: StringBuilder, e: Throwable, frames: Int) {
        sb.append(e.javaClass.name).append(": ").append(safeMessage(e)).append('\n')
        val trace = e.stackTrace
        for (i in 0 until minOf(frames, trace.size)) sb.append("  at ").append(trace[i]).append('\n')
        if (trace.size > frames) sb.append("  ... ").append(trace.size - frames).append(" more\n")
    }

    private fun describeDevice(context: Context): String {
        val version = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.versionCode})"
        } catch (_: Throwable) {
            "?"
        }
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "?"
        return "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), $abi, app $version"
    }

    private fun memoryLine(): String {
        val rt = Runtime.getRuntime()
        val mb = 1024 * 1024
        return "Memory: Java heap ${(rt.totalMemory() - rt.freeMemory()) / mb} of ${rt.maxMemory() / mb} MB used, " +
            "native heap ${android.os.Debug.getNativeHeapAllocatedSize() / mb} MB"
    }

    /** How many threads there are and what they are called - a thread storm is obvious at a glance. */
    private fun threadLine(): String = try {
        val names = Thread.getAllStackTraces().keys.groupingBy { it.name.trimEnd { c -> c.isDigit() || c == '-' || c == ' ' || c == '#' } }.eachCount()
        val top = names.entries.sortedByDescending { it.value }.take(6).joinToString(", ") { "${it.key} x${it.value}" }
        "Threads: ${names.values.sum()} ($top)"
    } catch (_: Throwable) {
        "Threads: ?"
    }
}
