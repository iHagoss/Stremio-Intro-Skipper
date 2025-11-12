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

// # This strategy attempts to detect skip segments by querying the Intro-Skipper community API (used by Stremio/Jellyfin).
// # Interacts with: Intro-Skipper public server over HTTP.
public class IntroSkipperStrategy implements SkipDetectionStrategy {
    
    private static final String TAG = "IntroSkipperStrategy";
    // # The community API base URL. This is a proxy/mirror of the official service.
    private static final String STREMIO_API_URL = "https://busy-jacinta-shugi-c2885b2e.koyeb.app";
    private static final int TIMEOUT_SECONDS = 8;
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String customEndpoint;
    
    // # Constructor 1: Uses the default Stremio API URL.
    public IntroSkipperStrategy() {
        this(null);
    }
    
    // # Constructor 2: Allows a custom endpoint (useful for development or alternate mirrors).
    public IntroSkipperStrategy(String customEndpoint) {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
        this.customEndpoint = customEndpoint;
    }
    
    // # Core detection logic: Constructs a URL and fetches skip segments from the API.
    // # This implementation requires a Trakt ID for lookup.
    @Override
    public SkipDetectionResult detect(MediaIdentifier mediaIdentifier) {
        String traktId = mediaIdentifier.getTraktId();
        Integer season = mediaIdentifier.getSeasonNumber();
        Integer episode = mediaIdentifier.getEpisodeNumber();
        
        if (traktId == null || traktId.isEmpty() || season == null || episode == null) {
            return SkipDetectionResult.failed(DetectionSource.INTRO_SKIPPER_API, "Missing Trakt ID or episode info.");
        }
        
        String baseUrl = customEndpoint != null ? customEndpoint : STREMIO_API_URL;
        // # Constructs the API URL for a specific episode using the Trakt ID.
        String url = String.format("%s/trakt/%s/%d/%d", baseUrl, traktId, season, episode);

        Request request = new Request.Builder()
            .url(url)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String json = response.body().string();
                List<SkipSegment> segments = parseResponse(json);
                
                if (!segments.isEmpty()) {
                    Log.d(TAG, "Intro-Skipper detection successful with " + segments.size() + " segments");
                    // # Returns a high-confidence result, as community servers are usually accurate.
                    return SkipDetectionResult.success(
                        DetectionSource.INTRO_SKIPPER_API,
                        0.75f,
                        segments.toArray(new SkipSegment[0])
                    );
                } else {
                    return SkipDetectionResult.failed(DetectionSource.INTRO_SKIPPER_API, "API returned no skip data.");
                }
            } else {
                return SkipDetectionResult.failed(DetectionSource.INTRO_SKIPPER_API, 
                    "API call failed: " + response.code());
            }
        } catch (Exception e) {
            Log.e(TAG, "Network error during Intro-Skipper lookup", e);
            return SkipDetectionResult.failed(DetectionSource.INTRO_SKIPPER_API, "Network error: " + e.getMessage());
        }
    }
    
    // # Helper method to parse the JSON response from the API.
    private List<SkipSegment> parseResponse(String json) {
        List<SkipSegment> segments = new ArrayList<>();
        
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            
            if (root.has("skipSegments") && root.get("skipSegments").isJsonArray()) {
                JsonArray segmentsArray = root.get("skipSegments").getAsJsonArray();
                
                for (int i = 0; i < segmentsArray.size(); i++) {
                    JsonObject segment = segmentsArray.get(i).getAsJsonObject();
                    // # API provides segment type (e.g., "intro", "recap")
                    String typeStr = segment.has("skipType") ? segment.get("skipType").getAsString().toLowerCase() : "";
                    // # API provides start/end times in seconds (as doubles, so cast to int)
                    double start = segment.has("showSkipPromptAt") ? segment.get("showSkipPromptAt").getAsDouble() : 0;
                    double end = segment.has("hideSkipPromptAt") ? segment.get("hideSkipPromptAt").getAsDouble() : 0;
                    
                    if (start >= 0 && end > start) {
                        if (typeStr.contains("intro") || typeStr.contains("opening")) {
                            segments.add(new SkipSegment(SkipSegmentType.INTRO, (int) start, (int) end));
                        } else if (typeStr.contains("recap")) {
                            segments.add(new SkipSegment(SkipSegmentType.RECAP, (int) start, (int) end));
                        } else if (typeStr.contains("credits") || typeStr.contains("outro")) {
                            segments.add(new SkipSegment(SkipSegmentType.CREDITS, (int) start, (int) end));
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing intro-skipper response", e);
        }
        
        return segments;
    }
    
    @Override
    public String getStrategyName() {
        return "Intro-Skipper (Stremio/Jellyfin)";
    }
    
    @Override
    public boolean isAvailable() {
        return true; // The API is generally always available to try.
    }
    
    @Override
    public int getPriority() {
        // # UPDATED: Set to 400 (Category 3: Community Servers).
        return 400;
    }
}
