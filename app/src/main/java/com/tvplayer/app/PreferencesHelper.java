package com.tvplayer.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class PreferencesHelper {
    private final SharedPreferences prefs;

    public PreferencesHelper(Context context) {
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public String getTraktApiKey() {
        return prefs.getString("trakt_api_key", "");
    }

    public String getTmdbApiKey() {
        return prefs.getString("tmdb_api_key", "");
    }

    public String getTvdbApiKey() {
        return prefs.getString("tvdb_api_key", "");
    }

    public String getRealDebridKey() {
        return prefs.getString("real_debrid_key", "");
    }

    public String getTorboxKey() {
        return prefs.getString("torbox_key", "");
    }

    public String getAllDebridKey() {
        return prefs.getString("all_debrid_key", "");
    }

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
        return parseIntSafe(prefs.getString("credits_start", "0"), 0);
    }

    public int getCreditsEnd() {
        return parseIntSafe(prefs.getString("credits_end", "0"), 0);
    }

    public int getNextEpisodeStart() {
        return parseIntSafe(prefs.getString("next_ep_start", "0"), 0);
    }

    public boolean isAutoSkipIntro() {
        return prefs.getBoolean("auto_skip_intro", false);
    }

    public boolean isAutoSkipRecap() {
        return prefs.getBoolean("auto_skip_recap", false);
    }

    public boolean isAutoSkipCredits() {
        return prefs.getBoolean("auto_skip_credits", false);
    }

    public int getAudioDelayMs() {
        return parseIntSafe(prefs.getString("audio_delay_ms", "0"), 0);
    }

    public int getSubtitleDelayMs() {
        return parseIntSafe(prefs.getString("subtitle_delay_ms", "0"), 0);
    }

    public void setAudioDelayMs(int delayMs) {
        prefs.edit().putString("audio_delay_ms", String.valueOf(delayMs)).apply();
    }

    public void setSubtitleDelayMs(int delayMs) {
        prefs.edit().putString("subtitle_delay_ms", String.valueOf(delayMs)).apply();
    }

    private int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
