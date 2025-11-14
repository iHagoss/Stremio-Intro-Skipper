package com.tvplayer.app.skipdetection.strategies;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.tvplayer.app.PreferencesHelper;
import com.tvplayer.app.skipdetection.MediaIdentifier;
import com.tvplayer.app.skipdetection.SkipDetectionResult;
import com.tvplayer.app.skipdetection.SkipDetectionResult.SkipSegment;
import com.tvplayer.app.skipdetection.SkipDetectionResult.SkipSegmentType;
import com.tvplayer.app.skipdetection.SkipDetectionResult.DetectionSource;
import com.tvplayer.app.skipdetection.SkipDetectionStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * MetadataHeuristicStrategy
 * FUNCTION: Uses existing Trakt/TMDB IDs to fetch metadata (like runtime) 
 * and apply simple heuristics (guesses) to find skip points.
 * INTERACTS WITH: PreferencesHelper.java (for API keys), MediaIdentifier.java (for IDs).
 * PERSONALIZATION: The 'applyHeuristics' method contains the hardcoded rules.
 * You can adjust the times (e.g., '0, 90' for intro) to be more or less aggressive.
 */
public class MetadataHeuristicStrategy implements SkipDetectionStrategy {
    
    private static final String TAG = "MetadataHeuristicStrategy";
    private static final int TIMEOUT_SECONDS = 8;
    
    // # Base URL for Trakt API lookup (used to get runtime for heuristics).
    private static final String TRAKT_API_URL = "https://api.trakt.tv"; 
    
    private final PreferencesHelper prefsHelper;
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    public MetadataHeuristicStrategy(PreferencesHelper prefsHelper) {
        this.prefsHelper = prefsHelper;
        // # HTTP client setup with a reasonable timeout for network requests.
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
    }
    
    // # Core detection logic: Fetches runtime and applies time-based guesswork.
    @Override
    public SkipDetectionResult detect(MediaIdentifier mediaIdentifier) {
        // # Use runtime from the media identifier first, if available.
        long runtimeSeconds = mediaIdentifier.getRuntimeSeconds();
        boolean isTvShow = mediaIdentifier.getSeasonNumber() != null && mediaIdentifier.getEpisodeNumber() != null;
        
        // # If runtime is not available, try to fetch it from Trakt using an ID.
        if (runtimeSeconds <= 0 && isAvailable()) {
            runtimeSeconds = fetchRuntimeFromTrakt(mediaIdentifier, isTvShow);
        }
        
        // # If runtime is still not valid, the strategy cannot proceed.
        if (runtimeSeconds <= 0) {
            return SkipDetectionResult.failed(DetectionSource.METADATA_HEURISTIC, "No valid runtime found or fetched.");
        }
        
        List<SkipSegment> segments = new ArrayList<>();
        
        // # Apply simple rules (heuristics) based on the total length of the content.
        applyHeuristics(segments, runtimeSeconds, isTvShow);
        
        if (segments.isEmpty()) {
            return SkipDetectionResult.failed(DetectionSource.METADATA_HEURISTIC, "Heuristics did not generate any skip segments.");
        }
        
        // # Returns a result based on calculated guesses. Confidence is moderate.
        return SkipDetectionResult.success(
            DetectionSource.METADATA_HEURISTIC,
            0.40f, 
            segments.toArray(new SkipSegment[0])
        );
    }
    
    // # Helper to fetch media runtime from the Trakt API.
    private long fetchRuntimeFromTrakt(MediaIdentifier mediaIdentifier, boolean isTvShow) {
        String traktApiKey = prefsHelper.getTraktApiKey();
        if (traktApiKey.isEmpty()) {
            return 0; // # Cannot use Trakt without a key
        }
        
        String url;
        String traktId = mediaIdentifier.getTraktId();
        
        if (traktId != null && !traktId.isEmpty()) {
            if (isTvShow) {
                // # Trakt API endpoint for a specific episode.
                url = String.format("%s/shows/%s/seasons/%d/episodes/%d?extended=full",
                    TRAKT_API_URL, traktId, mediaIdentifier.getSeasonNumber(), mediaIdentifier.getEpisodeNumber());
            } else {
                // # Trakt API endpoint for a movie.
                url = String.format("%s/movies/%s?extended=full", TRAKT_API_URL, traktId);
            }
        } else {
            return 0; // # No ID to look up
        }
        
        Request request = new Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("trakt-api-version", "2")
            // # Uses the API key stored in preferences for authentication.
            .header("trakt-api-key", traktApiKey)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String json = response.body().string();
                JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
                
                // # Trakt runtime is usually returned in minutes, convert to seconds.
                if (jsonObject.has("runtime")) {
                    return jsonObject.get("runtime").getAsLong() * 60; 
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching from Trakt", e);
        }
        return 0;
    }
    
    // # Simple, hardcoded rules to guess where segments might be based on total runtime.
    private void applyHeuristics(List<SkipSegment> segments, long runtimeSeconds, boolean isTvShow) {
        if (!isTvShow) {
            return; // # Heuristics are generally less reliable for movies
        }
        
        if (runtimeSeconds >= 20 * 60) { // # 20+ minute episode
            // # Assume a 0-90 second intro.
            segments.add(new SkipSegment(SkipSegmentType.INTRO, 0, 90));
            // # Assume credits start 180 seconds before the end.
            int creditsStart = (int) (runtimeSeconds - 180);
            if (creditsStart > 0) {
                segments.add(new SkipSegment(SkipSegmentType.CREDITS, creditsStart, (int) runtimeSeconds));
            }
        } else if (runtimeSeconds >= 15 * 60) { // # 15-19 minute episode
            // # Assume a 0-60 second intro.
            segments.add(new SkipSegment(SkipSegmentType.INTRO, 0, 60));
            // # Assume credits start 120 seconds before the end.
            int creditsStart = (int) (runtimeSeconds - 120);
            if (creditsStart > 0) {
                segments.add(new SkipSegment(SkipSegmentType.CREDITS, creditsStart, (int) runtimeSeconds));
            }
        }
    }
    
    @Override
    public String getStrategyName() {
        return "Metadata-Based Heuristics";
    }
    
    @Override
    public boolean isAvailable() {
        // # Strategy is available if the user has provided at least one API key.
        return !prefsHelper.getTmdbApiKey().isEmpty() || 
               !prefsHelper.getTraktApiKey().isEmpty() ||
               !prefsHelper.getTvdbApiKey().isEmpty();
    }
    
    @Override
    public int getPriority() {
        // # FIX: Set to 500 for P1 Priority (Category 1: Chapter/Metadata).
        return 500;
    }
}