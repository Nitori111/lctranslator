package com.example.lctranslator

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Prefs {
    private const val FILE = "lct_secure_prefs"

    fun get(ctx: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx, FILE, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun apiKey(ctx: Context): String =
        get(ctx).getString(ctx.getString(R.string.pref_api_key), "") ?: ""

    fun targetLang(ctx: Context): String =
        get(ctx).getString(ctx.getString(R.string.pref_target_lang),
            ctx.getString(R.string.default_target_lang)) ?: ctx.getString(R.string.default_target_lang)

    fun provider(ctx: Context): LlmProvider =
        LlmProvider.valueOf(
            get(ctx).getString(ctx.getString(R.string.pref_provider), LlmProvider.CLAUDE.name)
                ?: LlmProvider.CLAUDE.name
        )

    fun captionPkg(ctx: Context): String =
        get(ctx).getString(ctx.getString(R.string.pref_caption_pkg),
            ctx.getString(R.string.default_caption_pkg)) ?: ctx.getString(R.string.default_caption_pkg)

    fun baseUrl(ctx: Context): String =
        get(ctx).getString(ctx.getString(R.string.pref_base_url), "") ?: ""

    fun model(ctx: Context): String =
        get(ctx).getString(ctx.getString(R.string.pref_model), "") ?: ""
}
