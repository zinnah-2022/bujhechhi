package com.edurip.bujhechhi.data

import android.content.Context
import android.content.SharedPreferences

/** শিক্ষকের সেটিংস (শুধু এই ফোনে থাকে)। */
object Settings {
    private lateinit var p: SharedPreferences

    fun init(ctx: Context) {
        if (!::p.isInitialized) p = ctx.applicationContext.getSharedPreferences("bj_settings", Context.MODE_PRIVATE)
    }

    var redThresholdPct: Int
        get() = p.getInt("red_pct", 30)
        set(v) = p.edit().putInt("red_pct", v.coerceIn(10, 90)).apply()

    var redMinCount: Int
        get() = p.getInt("red_min", 2)
        set(v) = p.edit().putInt("red_min", v.coerceIn(1, 20)).apply()

    var quizSecs: Int
        get() = p.getInt("quiz_secs", 30)
        set(v) = p.edit().putInt("quiz_secs", v.coerceIn(5, 600)).apply()

    var vibrateOnAlert: Boolean
        get() = p.getBoolean("vibrate", true)
        set(v) = p.edit().putBoolean("vibrate", v).apply()

    var apiKey: String
        get() = p.getString("api_key", "") ?: ""
        set(v) = p.edit().putString("api_key", v.trim()).apply()

    /** ব্রেইন: AI-কে স্থায়ী নির্দেশনা (প্রতিবার প্রশ্ন বানানোর সময় যুক্ত হয়)। */
    var aiBrain: String
        get() = p.getString("ai_brain", "") ?: ""
        set(v) = p.edit().putString("ai_brain", v.trim()).apply()

    var aiModel: String
        get() = p.getString("ai_model", "") ?: ""
        set(v) = p.edit().putString("ai_model", v.trim()).apply()

    var lastTitle: String
        get() = p.getString("last_title", "") ?: ""
        set(v) = p.edit().putString("last_title", v).apply()
}
