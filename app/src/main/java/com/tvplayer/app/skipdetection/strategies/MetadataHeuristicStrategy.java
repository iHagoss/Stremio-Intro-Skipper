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

public class MetadataHeuristicStrategy implements SkipDetectionStrategy {
    
    private static final String TAG = "MetadataHeuristicStrategy";
    private static final int TIMEOUT_SECONDS = 8;
    
    private final PreferencesHelper prefsHelper;
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    public MetadataHeuristicStrategy(PreferencesHelper prefsHelper) {
        this.prefsHelper = prefsHelper;
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
            return SkipDetectionResult.failed(DetectionSource.METADATA_HEURISTIC, "No API keys configured");
        }
        
        List<SkipSegment> segments = new ArrayList<>();
        long runtimeSeconds = mediaIdentifier.getRuntimeSeconds();
        
        if (runtimeSeconds <= 0) {
            runtimeSeconds = fetchRuntimeFromMetadata(mediaIdentifier);
        }
        
        if (runtimeSeconds > 0) {
            applyHeuristics(segments, runtimeSeconds, mediaIdentifier.isTvShow());
        }
        
        if (segments.isEmpty()) {
            return SkipDetectionResult.failed(DetectionSource.METADATA_HEURISTIC, "Unable to apply heuristics");
        }
        
        return SkipDetectionResult.success(
            DetectionSource.METADATA_HEURISTIC,
            0.60f,
            segments.toArray(new SkipSegment[0])
        );
    }
    
    private long fetchRuntimeFromMetadata(MediaIdentifier mediaIdentifier) {
        if (mediaIdentifier.getTmdbId() != null && !prefsHelper.getTmdbApiKey().isEmpty()) {
            return fetchFromTMDB(mediaIdentifier);
        } else if (mediaIdentifier.getTraktId() != null && !prefsHelper.getTraktApiKey().isEmpty()) {
            return fetchFromTrakt(mediaIdentifier);
        }
        return 0;
    }
    
    private long fetchFromTMDB(MediaIdentifier mediaIdentifier) {
        try {
            String apiKey = prefsHelper.getTmdbApiKey();
            String type = mediaIdentifier.isTvShow() ? "tv" : "movie";
            String url = String.format("https://api.themoviedb.org/3/%s/%s?api_key=%s",
                type, mediaIdentifier.getTmdbId(), apiKey);
            
            Request request = new Request.Builder().url(url).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
                    if (root.has("runtime")) {
                        return root.get("runtime").getAsLong() * 60;
                    } else if (root.has("episode_run_time")) {
                        return root.getAsJsonArray("episode_run_time").get(0).getAsLong() * 60;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching from TMDB", e);
        }
        return 0;
    }
    
    private long fetchFromTrakt(MediaIdentifier mediaIdentifier) {
        try {
            String apiKey = prefsHelper.getTraktApiKey();
            String type = mediaIdentifier.isTvShow() ? "shows" : "movies";
            String url = String.format("https://api.trakt.tv/sync/%s/%s",
                type, mediaIdentifier.getTraktId());
            
            Request request = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("trakt-api-key", apiKey)
                .header("trakt-api-version", "2")
                .build();
                
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
                    if (root.has("runtime")) {
                        return root.get("runtime").getAsLong() * 60;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching from Trakt", e);
        }
        return 0;
    }
    
    private void applyHeuristics(List<SkipSegment> segments, long runtimeSeconds, boolean isTvShow) {
        if (!isTvShow) {
            return;
        }
        
        if (runtimeSeconds >= 20 * 60) {
            segments.add(new SkipSegment(SkipSegmentType.INTRO, 0, 90));
            
            int creditsStart = (int) (runtimeSeconds - 180);
            if (creditsStart > 0) {
                segments.add(new SkipSegment(SkipSegmentType.CREDITS, creditsStart, (int) runtimeSeconds));
            }
        } else if (runtimeSeconds >= 15 * 60) {
            segments.add(new SkipSegment(SkipSegmentType.INTRO, 0, 60));
            
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
        return !prefsHelper.getTmdbApiKey().isEmpty() || 
               !prefsHelper.getTraktApiKey().isEmpty() ||
               !prefsHelper.getTvdbApiKey().isEmpty();
    }
    
    @Override
    public int getPriority() {
        return 600;
    }
}
