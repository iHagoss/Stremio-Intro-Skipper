package com.tvplayer.app;

// Standard Android framework imports
import android.content.Context;
import android.content.SharedPreferences;

// AndroidX/Preference for getting the default preference file
import androidx.preference.PreferenceManager;

/**
 * PreferencesHelper: A utility class to encapsulate all reading and writing
 * to the application's SharedPreferences. This makes the code in MainActivity.java
 * cleaner and centralizes all preference keys.
 *
 * Interacts with:
 * - MainActivity.java: Called to load all user settings and apply them to the player/skip logic.
 * - preferences.xml: Contains the keys and default values used here.
 */
public class PreferencesHelper {
    
    // The main object used to read and write preferences
    private final SharedPreferences prefs;

    /**
     * Constructor. Uses the default SharedPreferences file for the application.
     * @param context The application context.
     */
    public PreferencesHelper(Context context) {
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    // --- API KEY GETTERS ---
    
    public String getTraktApiKey() {
        return prefs.getString("trakt_api_key", "");
    }

    public String getTmdbApiKey() {
        return prefs.getString("tmdb_api_key", "");
    }

    public String getTvdbApiKey() {
        return prefs.getString("tvdb_api_key", "");
    }

    // --- DEBRID KEY GETTERS ---
    
    public String getRealDebridKey() {
        return prefs.getString("real_debrid_key", "");
    }

    public String getTorboxKey() {
        return prefs.getString("torbox_key", "");
    }

    public String getAllDebridKey() {
        return prefs.getString("all_debrid_key", "");
    }

    // --- MANUAL SKIP TIMING GETTERS (in seconds) ---
    // Note: EditTextPreference stores values as Strings, so we must parse them.
    
    public int getIntroStart() {
        return parseIntSafe(prefs.getString("intro_start", "0"), 0);
    }

    public int getIntroEnd() {
        return parseIntSafe(prefs.getString("intro_end", "0"), 0);
    }

    public int getRecapStart() {
        return parseIntSafe(prefs.getString("recap_start", "0"), 0);
    }

    public int getRecapEnd() {
        return parseIntSafe(prefs.getString("recap_end", "0"), 0);
    }

    public int getCreditsStart() {
        // This is the offset (time from end) in the settings menu
        return parseIntSafe(prefs.getString("credits_start", "0"), 0);
    }

    public int getCreditsEnd() {
        return parseIntSafe(prefs.getString("credits_end", "0"), 0);
    }

    public int getNextEpisodeStart() {
        return parseIntSafe(prefs.getString("next_ep_start", "0"), 0);
    }

    // --- AUTO-SKIP SETTINGS ---
    
    public boolean isAutoSkipIntro() {
        return prefs.getBoolean("auto_skip_intro", false);
    }

    public boolean isAutoSkipRecap() {
        return prefs.getBoolean("auto_skip_recap", false);
    }

    public boolean isAutoSkipCredits() {
        return prefs.getBoolean("auto_skip_credits", false);
    }

    // --- DELAY SETTINGS (in milliseconds) ---
    
    public int getAudioDelayMs() {
        return parseIntSafe(prefs.getString("audio_delay_ms", "0"), 0);
    }

    public int getSubtitleDelayMs() {
        return parseIntSafe(prefs.getString("subtitle_delay_ms", "0"), 0);
    }

    /**
     * Setter for Audio Delay, for run-time adjustments (e.g., from a button press).
     * @param delayMs The new delay in milliseconds.
     */
    public void setAudioDelayMs(int delayMs) {
        prefs.edit().putString("audio_delay_ms", String.valueOf(delayMs)).apply();
    }

    /**
     * Setter for Subtitle Delay, for run-time adjustments.
     * @param delayMs The new delay in milliseconds.
     */
    public void setSubtitleDelayMs(int delayMs) {
        prefs.edit().putString("subtitle_delay_ms", String.valueOf(delayMs)).apply();
    }

    /**
     * Safely converts a String value from preferences to an integer.
     * @param value The string from SharedPreferences.
     * @param defaultValue The value to return if parsing fails.
     * @return The parsed integer or the default value.
     */
    private int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
