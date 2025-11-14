package com.tvplayer.app.skipdetection.strategies;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
 * IntroHaterStrategy
 * FUNCTION: Detects skip segments by querying the IntroHater community API.
 * INTERACTS WITH: IntroHater public server (requires Internet), MediaIdentifier.java (for TMDB ID).
 * PERSONALIZATION: The API_BASE_URL can be changed if the service moves.
 */
public class IntroHaterStrategy implements SkipDetectionStrategy {
    
    private static final String TAG = "IntroHaterStrategy";
    // # The community API base URL.
    private static final String API_BASE_URL = "https://introhater.com/api";
    private static final int TIMEOUT_SECONDS = 8;
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    public IntroHaterStrategy() {
        // # HTTP client setup with a reasonable timeout
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
    }
    
    // # Core detection logic: Constructs a URL and fetches skip segments from the API.
    @Override
    public SkipDetectionResult detect(MediaIdentifier mediaIdentifier) {
        String tmdbId = mediaIdentifier.getTmdbId();
        Integer season = mediaIdentifier.getSeasonNumber();
        Integer episode = mediaIdentifier.getEpisodeNumber();
        
        if (tmdbId == null || tmdbId.isEmpty() || season == null || episode == null) {
            return SkipDetectionResult.failed(DetectionSource.INTROHATER_API, "Missing TMDB ID or episode info.");
        }
        
        // # Constructs the API URL for a specific episode.
        String url = String.format("%s/tmdb/%s/season/%d/episode/%d", API_BASE_URL, tmdbId, season, episode);
        
        Request request = new Request.Builder()
            .url(url)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String json = response.body().string();
                List<SkipSegment> segments = parseResponse(json);
                
                if (!segments.isEmpty()) {
                    Log.d(TAG, "IntroHater detection successful with " + segments.size() + " segments");
                    // # Returns a high-confidence result
                    return SkipDetectionResult.success(
                        DetectionSource.INTROHATER_API,
                        0.80f,
                        segments.toArray(new SkipSegment[0])
                    );
                } else {
                    return SkipDetectionResult.failed(DetectionSource.INTROHATER_API, "API returned no skip data.");
                }
            } else {
                return SkipDetectionResult.failed(DetectionSource.INTROHATER_API, 
                    "API call failed: " + response.code());
            }
        } catch (Exception e) {
            Log.e(TAG, "Network error during IntroHater lookup", e);
            return SkipDetectionResult.failed(DetectionSource.INTROHATER_API, "Network error: " + e.getMessage());
        }
    }
    
    // # Helper method to parse the JSON response from the API.
    private List<SkipSegment> parseResponse(String json) {
        List<SkipSegment> segments = new ArrayList<>();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root.has("segments") && root.get("segments").isJsonArray()) {
                JsonArray segmentsArray = root.get("segments").getAsJsonArray();
                for (int i = 0; i < segmentsArray.size(); i++) {
                    JsonObject segment = segmentsArray.get(i).getAsJsonObject();
                    String type = segment.has("type") ? segment.get("type").getAsString().toLowerCase() : "";
                    int start = segment.has("start") ? segment.get("start").getAsInt() : 0;
                    int end = segment.has("end") ? segment.get("end").getAsInt() : 0;
                    
                    if (start >= 0 && end > start) {
                        if (type.contains("intro") || type.contains("opening")) {
                            segments.add(new SkipSegment(SkipSegmentType.INTRO, start, end));
                        } else if (type.contains("recap")) {
                            segments.add(new SkipSegment(SkipSegmentType.RECAP, start, end));
                        } else if (type.contains("credits") || type.contains("outro")) {
                            segments.add(new SkipSegment(SkipSegmentType.CREDITS, start, end));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing IntroHater response", e);
        }
        return segments;
    }
    
    @Override
    public String getStrategyName() {
        return "IntroHater Community API";
    }
    
    @Override
    public boolean isAvailable() {
        return true; // # The API is generally always available to try.
    }
    
    @Override
    public int getPriority() {
        // # FIX: Set to 400 for P1 Priority (Category 3: Community Servers).
        return 400;
    }
}