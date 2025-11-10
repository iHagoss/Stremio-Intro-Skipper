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

public class IntroHaterStrategy implements SkipDetectionStrategy {
    
    private static final String TAG = "IntroHaterStrategy";
    private static final String API_BASE_URL = "https://introhater.com/api";
    private static final int TIMEOUT_SECONDS = 8;
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    public IntroHaterStrategy() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
    }
    
    @Override
    public SkipDetectionResult detect(MediaIdentifier mediaIdentifier) {
        if (!isAvailable()) {
            return SkipDetectionResult.failed(DetectionSource.INTROHATER_API, "Strategy not available");
        }
        
        try {
            String endpoint = buildEndpoint(mediaIdentifier);
            if (endpoint == null) {
                return SkipDetectionResult.failed(DetectionSource.INTROHATER_API, "Insufficient metadata");
            }
            
            Request request = new Request.Builder()
                .url(endpoint)
                .get()
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return SkipDetectionResult.failed(DetectionSource.INTROHATER_API, "HTTP " + response.code());
                }
                
                String body = response.body().string();
                List<SkipSegment> segments = parseIntroHaterResponse(body);
                
                if (segments.isEmpty()) {
                    return SkipDetectionResult.failed(DetectionSource.INTROHATER_API, "No skip data found");
                }
                
                return SkipDetectionResult.success(
                    DetectionSource.INTROHATER_API,
                    0.90f,
                    segments.toArray(new SkipSegment[0])
                );
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting with IntroHater API", e);
            return SkipDetectionResult.failed(DetectionSource.INTROHATER_API, e.getMessage());
        }
    }
    
    private String buildEndpoint(MediaIdentifier mediaIdentifier) {
        if (mediaIdentifier.isTvShow() && mediaIdentifier.getShowName() != null) {
            return String.format("%s/show/%s/season/%d/episode/%d",
                API_BASE_URL,
                mediaIdentifier.getShowName().replace(" ", "%20"),
                mediaIdentifier.getSeasonNumber(),
                mediaIdentifier.getEpisodeNumber()
            );
        } else if (mediaIdentifier.getImdbId() != null) {
            return API_BASE_URL + "/imdb/" + mediaIdentifier.getImdbId();
        }
        return null;
    }
    
    private List<SkipSegment> parseIntroHaterResponse(String json) {
        List<SkipSegment> segments = new ArrayList<>();
        
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            
            if (root.has("intro")) {
                JsonObject intro = root.getAsJsonObject("intro");
                int start = intro.has("start") ? intro.get("start").getAsInt() : 0;
                int end = intro.has("end") ? intro.get("end").getAsInt() : 0;
                if (start >= 0 && end > start) {
                    segments.add(new SkipSegment(SkipSegmentType.INTRO, start, end));
                }
            }
            
            if (root.has("recap")) {
                JsonObject recap = root.getAsJsonObject("recap");
                int start = recap.has("start") ? recap.get("start").getAsInt() : 0;
                int end = recap.has("end") ? recap.get("end").getAsInt() : 0;
                if (start >= 0 && end > start) {
                    segments.add(new SkipSegment(SkipSegmentType.RECAP, start, end));
                }
            }
            
            if (root.has("credits") || root.has("outro")) {
                JsonObject credits = root.has("credits") ? root.getAsJsonObject("credits") : root.getAsJsonObject("outro");
                int start = credits.has("start") ? credits.get("start").getAsInt() : 0;
                int end = credits.has("end") ? credits.get("end").getAsInt() : 0;
                if (start >= 0 && end > start) {
                    segments.add(new SkipSegment(SkipSegmentType.CREDITS, start, end));
                }
            }
            
            if (root.has("segments")) {
                JsonArray segmentsArray = root.getAsJsonArray("segments");
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
        return true;
    }
    
    @Override
    public int getPriority() {
        return 300;
    }
}
