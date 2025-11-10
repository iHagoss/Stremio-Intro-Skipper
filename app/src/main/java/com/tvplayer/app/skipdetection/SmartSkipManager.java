package com.tvplayer.app.skipdetection;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.Player;

import com.tvplayer.app.PreferencesHelper;
import com.tvplayer.app.skipdetection.strategies.CacheStrategy;
import com.tvplayer.app.skipdetection.strategies.ChapterStrategy;
import com.tvplayer.app.skipdetection.strategies.IntroHaterStrategy;
import com.tvplayer.app.skipdetection.strategies.IntroSkipperStrategy;
import com.tvplayer.app.skipdetection.strategies.ManualPreferenceStrategy;
import com.tvplayer.app.skipdetection.strategies.MetadataHeuristicStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class SmartSkipManager {
    
    private static final String TAG = "SmartSkipManager";
    private static final int DETECTION_TIMEOUT_MS = 10000;
    
    private final Context context;
    private final PreferencesHelper prefsHelper;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final CacheStrategy cacheStrategy;
    private final List<SkipDetectionStrategy> strategies;
    
    public SmartSkipManager(Context context, PreferencesHelper prefsHelper) {
        this.context = context;
        this.prefsHelper = prefsHelper;
        this.executorService = Executors.newFixedThreadPool(4);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.cacheStrategy = new CacheStrategy(context);
        this.strategies = new ArrayList<>();
        initializeStrategies();
    }
    
    private void initializeStrategies() {
        strategies.add(new ManualPreferenceStrategy(prefsHelper));
        strategies.add(new IntroHaterStrategy());
        strategies.add(new IntroSkipperStrategy());
        strategies.add(new MetadataHeuristicStrategy(prefsHelper));
        
        Collections.sort(strategies, new Comparator<SkipDetectionStrategy>() {
            @Override
            public int compare(SkipDetectionStrategy s1, SkipDetectionStrategy s2) {
                return Integer.compare(s1.getPriority(), s2.getPriority());
            }
        });
    }
    
    public void addChapterStrategy(ChapterStrategy chapterStrategy) {
        for (int i = 0; i < strategies.size(); i++) {
            if (strategies.get(i) instanceof ChapterStrategy) {
                strategies.remove(i);
                break;
            }
        }
        
        if (chapterStrategy != null) {
            strategies.add(chapterStrategy);
            Collections.sort(strategies, new Comparator<SkipDetectionStrategy>() {
                @Override
                public int compare(SkipDetectionStrategy s1, SkipDetectionStrategy s2) {
                    return Integer.compare(s1.getPriority(), s2.getPriority());
                }
            });
        }
    }
    
    public void detectSkipMarkers(MediaIdentifier mediaIdentifier, SkipDetectionCallback callback) {
        SkipDetectionResult cachedResult = cacheStrategy.detect(mediaIdentifier);
        if (cachedResult.isSuccess()) {
            Log.d(TAG, "Using cached skip markers for: " + mediaIdentifier.getCacheKey());
            mainHandler.post(() -> callback.onDetectionComplete(cachedResult));
            return;
        }
        
        Log.d(TAG, "Cache miss, attempting smart detection for: " + mediaIdentifier.getCacheKey());
        executorService.execute(() -> {
            SkipDetectionResult bestResult = runDetectionStrategies(mediaIdentifier);
            
            if (bestResult.isSuccess()) {
                cacheStrategy.cacheResult(mediaIdentifier, bestResult);
                Log.d(TAG, "Detection successful using: " + bestResult.getSource().name() + 
                    " (confidence: " + bestResult.getConfidence() + ")");
            } else {
                Log.d(TAG, "All detection methods failed, using manual preferences");
            }
            
            final SkipDetectionResult finalResult = bestResult;
            mainHandler.post(() -> callback.onDetectionComplete(finalResult));
        });
    }
    
    private SkipDetectionResult runDetectionStrategies(MediaIdentifier mediaIdentifier) {
        final AtomicReference<SkipDetectionResult> bestResult = new AtomicReference<>(null);
        final AtomicBoolean completed = new AtomicBoolean(false);
        List<Future<?>> futures = new ArrayList<>();
        
        for (final SkipDetectionStrategy strategy : strategies) {
            if (!strategy.isAvailable()) {
                Log.d(TAG, "Strategy not available: " + strategy.getStrategyName());
                continue;
            }
            
            Future<?> future = executorService.submit(() -> {
                if (completed.get()) {
                    return;
                }
                
                try {
                    Log.d(TAG, "Trying strategy: " + strategy.getStrategyName());
                    SkipDetectionResult result = strategy.detect(mediaIdentifier);
                    
                    if (result.isSuccess()) {
                        synchronized (bestResult) {
                            SkipDetectionResult current = bestResult.get();
                            if (current == null || result.getConfidence() > current.getConfidence()) {
                                bestResult.set(result);
                                Log.d(TAG, "New best result from: " + strategy.getStrategyName() + 
                                    " (confidence: " + result.getConfidence() + ")");
                                
                                if (result.getConfidence() >= 0.85f) {
                                    completed.set(true);
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "Strategy failed: " + strategy.getStrategyName() + 
                            " - " + result.getErrorMessage());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in strategy: " + strategy.getStrategyName(), e);
                }
            });
            
            futures.add(future);
        }
        
        long startTime = System.currentTimeMillis();
        while (!completed.get() && 
               (System.currentTimeMillis() - startTime) < DETECTION_TIMEOUT_MS &&
               !allFuturesCompleted(futures)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        for (Future<?> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
        
        SkipDetectionResult result = bestResult.get();
        if (result == null) {
            ManualPreferenceStrategy fallback = new ManualPreferenceStrategy(prefsHelper);
            result = fallback.detect(mediaIdentifier);
        }
        
        return result;
    }
    
    private boolean allFuturesCompleted(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            if (!future.isDone()) {
                return false;
            }
        }
        return true;
    }
    
    public void clearCache() {
        cacheStrategy.clearCache();
    }
    
    public void invalidateCache(MediaIdentifier mediaIdentifier) {
        cacheStrategy.invalidateCache(mediaIdentifier);
    }
    
    public void shutdown() {
        executorService.shutdown();
    }
}
