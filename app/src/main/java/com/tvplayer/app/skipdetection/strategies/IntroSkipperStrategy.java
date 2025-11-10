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

public class IntroSkipperStrategy implements SkipDetectionStrategy {
    
    private static final String TAG = "IntroSkipperStrategy";
    private static final String STREMIO_API_URL = "https://busy-jacinta-shugi-c2885b2e.koyeb.app";
    private static final int TIMEOUT_SECONDS = 8;
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String customEndpoint;
    
    public IntroSkipperStrategy() {
        this(null);
    }
    
    public IntroSkipperStrategy(String customEndpoint) {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
        this.customEndpoint = customEndpoint;
    }
    
    @Override
    public SkipDetectionResult detect(MediaIdentifier mediaIdentifier) {
        if (!isAvailable()) {
            return SkipDetectionResult.failed(DetectionSource.INTRO_SKIPPER_API, "Strategy not available");
        }
        
        try {
            String endpoint = buildEndpoint(mediaIdentifier);
            if (endpoint == null) {
                return SkipDetectionResult.failed(DetectionSource.INTRO_SKIPPER_API, "Insufficient metadata");
            }
            
            Request request = new Request.Builder()
                .url(endpoint)
                .get()
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return SkipDetectionResult.failed(DetectionSource.INTRO_SKIPPER_API, "HTTP " + response.code());
                }
                
                String body = response.body().string();
                List<SkipSegment> segments = parseIntroSkipperResponse(body);
                
                if (segments.isEmpty()) {
                    return SkipDetectionResult.failed(DetectionSource.INTRO_SKIPPER_API, "No skip data found");
                }
                
                return SkipDetectionResult.success(
                    DetectionSource.INTRO_SKIPPER_API,
                    0.88f,
                    segments.toArray(new SkipSegment[0])
                );
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting with intro-skipper API", e);
            return SkipDetectionResult.failed(DetectionSource.INTRO_SKIPPER_API, e.getMessage());
        }
    }
    
    private String buildEndpoint(MediaIdentifier mediaIdentifier) {
        String baseUrl = customEndpoint != null ? customEndpoint : STREMIO_API_URL;
        
        if (mediaIdentifier.getImdbId() != null && mediaIdentifier.isTvShow()) {
            return String.format("%s/skip-segments/%s:%d:%d",
                baseUrl,
                mediaIdentifier.getImdbId(),
                mediaIdentifier.getSeasonNumber(),
                mediaIdentifier.getEpisodeNumber()
            );
        } else if (mediaIdentifier.getImdbId() != null) {
            return baseUrl + "/skip-segments/" + mediaIdentifier.getImdbId();
        }
        return null;
    }
    
    private List<SkipSegment> parseIntroSkipperResponse(String json) {
        List<SkipSegment> segments = new ArrayList<>();
        
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            
            if (root.has("intro")) {
                JsonObject intro = root.getAsJsonObject("intro");
                double start = intro.has("start") ? intro.get("start").getAsDouble() : 0;
                double end = intro.has("end") ? intro.get("end").getAsDouble() : 0;
                if (start >= 0 && end > start) {
                    segments.add(new SkipSegment(SkipSegmentType.INTRO, (int) start, (int) end));
                }
            }
            
            if (root.has("credits")) {
                JsonObject credits = root.getAsJsonObject("credits");
                double start = credits.has("start") ? credits.get("start").getAsDouble() : 0;
                double end = credits.has("end") ? credits.get("end").getAsDouble() : 0;
                if (start >= 0 && end > start) {
                    segments.add(new SkipSegment(SkipSegmentType.CREDITS, (int) start, (int) end));
                }
            }
            
            if (root.has("skipSegments")) {
                JsonArray segmentsArray = root.getAsJsonArray("skipSegments");
                for (int i = 0; i < segmentsArray.size(); i++) {
                    JsonObject segment = segmentsArray.get(i).getAsJsonObject();
                    String typeStr = segment.has("skipType") ? segment.get("skipType").getAsString().toLowerCase() : "";
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
        return true;
    }
    
    @Override
    public int getPriority() {
        return 350;
    }
}
