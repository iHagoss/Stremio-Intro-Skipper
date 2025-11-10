package com.tvplayer.app.skipdetection.strategies;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tvplayer.app.skipdetection.MediaIdentifier;
import com.tvplayer.app.skipdetection.SkipDetectionResult;
import com.tvplayer.app.skipdetection.SkipDetectionResult.SkipSegment;
import com.tvplayer.app.skipdetection.SkipDetectionResult.DetectionSource;
import com.tvplayer.app.skipdetection.SkipDetectionStrategy;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class CacheStrategy implements SkipDetectionStrategy {
    
    private static final String PREFS_NAME = "SkipDetectionCache";
    private static final long CACHE_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000;
    
    private final SharedPreferences prefs;
    private final Gson gson;
    
    public CacheStrategy(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }
    
    @Override
    public SkipDetectionResult detect(MediaIdentifier mediaIdentifier) {
        String cacheKey = mediaIdentifier.getCacheKey();
        String cachedJson = prefs.getString(cacheKey, null);
        
        if (cachedJson == null) {
            return SkipDetectionResult.failed(DetectionSource.CACHE, "No cached data");
        }
        
        try {
            CachedSkipData cachedData = gson.fromJson(cachedJson, CachedSkipData.class);
            
            if (System.currentTimeMillis() - cachedData.timestamp > CACHE_EXPIRY_MS) {
                prefs.edit().remove(cacheKey).apply();
                return SkipDetectionResult.failed(DetectionSource.CACHE, "Cache expired");
            }
            
            return SkipDetectionResult.success(
                DetectionSource.CACHE,
                0.95f,
                cachedData.segments.toArray(new SkipSegment[0])
            );
            
        } catch (Exception e) {
            return SkipDetectionResult.failed(DetectionSource.CACHE, "Cache read error: " + e.getMessage());
        }
    }
    
    public void cacheResult(MediaIdentifier mediaIdentifier, SkipDetectionResult result) {
        if (result == null || !result.isSuccess() || result.getSource() == DetectionSource.CACHE) {
            return;
        }
        
        String cacheKey = mediaIdentifier.getCacheKey();
        List<SkipSegment> segmentsList = new ArrayList<>();
        for (SkipSegment segment : result.getSegments()) {
            segmentsList.add(segment);
        }
        
        CachedSkipData cachedData = new CachedSkipData();
        cachedData.segments = segmentsList;
        cachedData.timestamp = System.currentTimeMillis();
        cachedData.source = result.getSource().name();
        cachedData.confidence = result.getConfidence();
        
        String json = gson.toJson(cachedData);
        prefs.edit().putString(cacheKey, json).apply();
    }
    
    public void clearCache() {
        prefs.edit().clear().apply();
    }
    
    public void invalidateCache(MediaIdentifier mediaIdentifier) {
        String cacheKey = mediaIdentifier.getCacheKey();
        prefs.edit().remove(cacheKey).apply();
    }
    
    @Override
    public String getStrategyName() {
        return "Local Cache";
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public int getPriority() {
        return 100;
    }
    
    private static class CachedSkipData {
        List<SkipSegment> segments;
        long timestamp;
        String source;
        float confidence;
    }
}
