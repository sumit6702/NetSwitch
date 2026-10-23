package com.sumit.netswitch.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedSimSlot: Int
        get() = prefs.getInt(KEY_SIM_SLOT, 0)
        set(value) = prefs.edit().putInt(KEY_SIM_SLOT, value).apply()

    var custom5gMask: String
        get() = prefs.getString(KEY_CUSTOM_5G_MASK, DEFAULT_5G_MASK) ?: DEFAULT_5G_MASK
        set(value) = prefs.edit().putString(KEY_CUSTOM_5G_MASK, value).apply()

    var custom4gMask: String
        get() = prefs.getString(KEY_CUSTOM_4G_MASK, DEFAULT_4G_MASK) ?: DEFAULT_4G_MASK
        set(value) = prefs.edit().putString(KEY_CUSTOM_4G_MASK, value).apply()

    var savedOriginalMask: String?
        get() = prefs.getString(KEY_SAVED_ORIGINAL_MASK, null)
        set(value) = prefs.edit().putString(KEY_SAVED_ORIGINAL_MASK, value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    fun resetMasksToDefault() {
        prefs.edit()
            .putString(KEY_CUSTOM_5G_MASK, DEFAULT_5G_MASK)
            .putString(KEY_CUSTOM_4G_MASK, DEFAULT_4G_MASK)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "netswitch_preferences"
        private const val KEY_SIM_SLOT = "key_sim_slot"
        private const val KEY_CUSTOM_5G_MASK = "key_custom_5g_mask"
        private const val KEY_CUSTOM_4G_MASK = "key_custom_4g_mask"
        private const val KEY_SAVED_ORIGINAL_MASK = "key_saved_original_mask"
        private const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"

        const val DEFAULT_5G_MASK = "11001101001110000111"
        const val DEFAULT_4G_MASK = "01001101001110000111"
    }
}
