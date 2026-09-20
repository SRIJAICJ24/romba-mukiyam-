package com.rombamukiyam.vault.core.common

import android.util.Log
import com.rombamukiyam.vault.BuildConfig

/**
 * The ONLY logging entry point in the app.
 *
 * Rules:
 *  - Never pass secrets, usernames, customer IDs or entity toString() output.
 *  - Messages are static-ish strings + safe codes. Throwables are logged by class name only in release.
 *  - Release builds additionally strip android.util.Log via R8.
 */
object SafeLog {
    private const val PREFIX = "RM/"

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(PREFIX + tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(PREFIX + tag, message)
    }

    fun w(tag: String, message: String, t: Throwable? = null) {
        Log.w(PREFIX + tag, message + suffix(t))
    }

    fun e(tag: String, message: String, t: Throwable? = null) {
        Log.e(PREFIX + tag, message + suffix(t))
    }

    /** In release we log only the exception class; in debug the message too (debug never holds real data). */
    private fun suffix(t: Throwable?): String = when {
        t == null -> ""
        BuildConfig.DEBUG -> " [${t.javaClass.simpleName}: ${t.message}]"
        else -> " [${t.javaClass.simpleName}]"
    }
}
