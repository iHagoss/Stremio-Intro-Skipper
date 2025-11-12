package com.tvplayer.app.skipdetection;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.Player;

import com.tvplayer.app.PreferencesHelper;
// # Import the newly created AudioFingerprintStrategy.
import com.tvplayer.app.skipdetection.strategies.AudioFingerprintStrategy; 
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

// # SmartSkipManager: Coordinates all skip detection strategies, manages execution, 
// # prioritization, caching, and returns the best result to the main activity.
// # Interacts with: All SkipDetectionStrategy files, MainActivity, PreferencesHelper.
public class SmartSkipManager {
    
    private static final String TAG = "SmartSkipManager";
    // # Maximum time (10 seconds) to wait for all asynchronous detection strategies to finish.
    private static final int DETECTION_TIMEOUT_MS = 10000;
    
    private final Context context;
    private final PreferencesHelper prefsHelper;
    // # Executor for running detection strategies off the main thread (network/disk intensive).
    private final ExecutorService executorService; 
    // # Handler to post results back to the main UI thread.
    private final Handler mainHandler;
    private final CacheStrategy cacheStrategy;
    private final List<SkipDetectionStrategy> strategies;
    
    // # Constructor: Initializes strategies and sorting logic.
    public SmartSkipManager(Context context, PreferencesHelper prefsHelper, Player player) {
        this.context = context;
        this.prefsHelper = prefsHelper;
        this.executorService = Executors.newFixedThreadPool(4); // Max 4 strategies running concurrently
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // # Initialize CacheStrategy (used internally before other strategies run).
        this.cacheStrategy = new CacheStrategy(context);
        
        // # Initialize all detection strategies.
        this.strategies = new ArrayList<>();
        // # Manual Strategy (Lowest Priority)
        this.strategies.add(new ManualPreferenceStrategy(prefsHelper));
        // # Community API Strategies (Medium Priority)
        this.strategies.add(new IntroHaterStrategy());
        this.strategies.add(new IntroSkipperStrategy());
        // # NEW: Audio Scanning Strategy (Medium-High Priority)
        this.strategies.add(new AudioFingerprintStrategy());
        // # Metadata/Chapter Strategies (Highest Priority)
        this.strategies.add(new MetadataHeuristicStrategy(prefsHelper));
        this.strategies.add(new ChapterStrategy(player));
        
        // # Sort the strategies based on their priority score (Highest score = Highest priority).
        // # The highest priority strategies will be at the beginning of the list.
        Collections.sort(this.strategies, Comparator.comparingInt(SkipDetectionStrategy::getPriority).reversed());
        
        // # Log the final sorted order for debugging and confirmation.
        Log.i(TAG, "SmartSkipManager initialized. Strategy Priority Order:");
        for (int i = 0; i < this.strategies.size(); i++) {
            SkipDetectionStrategy s = this.strategies.get(i);
            Log.i(TAG, String.format("  #%d: %s (P=%d)", i + 1, s.getStrategyName(), s.getPriority()));
        }
    }
    
    // # Public method to start the detection process asynchronously.
    // # Interacts with: MediaIdentifier (input data), SkipDetectionCallback (result handler).
    public void detectSkipSegmentsAsync(MediaIdentifier mediaIdentifier, SkipDetectionCallback callback) {
        // # Run the entire detection logic on a background thread.
        executorService.submit(() -> {
            SkipDetectionResult result = detectSkipSegmentsSync(mediaIdentifier);
            
            // # Post the final result back to the main UI thread using the Handler.
            mainHandler.post(() -> {
                if (result.isSuccess()) {
                    callback.onDetectionComplete(result);
                } else {
                    callback.onDetectionFailed(result.getErrorMessage());
                }
            });
        });
    }
    
    // # The core, synchronous (blocking) detection logic.
    private SkipDetectionResult detectSkipSegmentsSync(MediaIdentifier mediaIdentifier) {
        
        // # 1. Check Cache First (Cache is separate from the main priority list for speed).
        SkipDetectionResult cachedResult = cacheStrategy.detect(mediaIdentifier);
        if (cachedResult.isSuccess()) {
            Log.i(TAG, "Cache hit: Returning result from " + cachedResult.getSource());
            return cachedResult;
        }
        
        // # Variables to hold the best successful result found and manage the async flow.
        final AtomicReference<SkipDetectionResult> bestResult = new AtomicReference<>(null);
        final AtomicBoolean completed = new AtomicBoolean(false);
        final List<Future<?>> futures = new ArrayList<>();

        // # 2. Run all strategies concurrently according to their priority.
        for (SkipDetectionStrategy strategy : strategies) {
            if (!strategy.isAvailable()) {
                continue; // Skip strategies that are not configured or available.
            }
            
            // # Submit each strategy to be run on the thread pool.
            Future<?> future = executorService.submit(() -> {
                try {
                    Log.d(TAG, "Starting detection: " + strategy.getStrategyName());
                    SkipDetectionResult result = strategy.detect(mediaIdentifier);
                    
                    // # Critical Section: Only one thread can update the best result.
                    synchronized (bestResult) {
                        // # If a result is already found and it has a higher priority, skip.
                        if (bestResult.get() != null && 
                            bestResult.get().getConfidence() >= result.getConfidence()) {
                            return; 
                        }
                        
                        // # If a successful result is found, set it as the best.
                        if (result.isSuccess()) {
                            bestResult.set(result);
                            // # If a very high priority result is found (e.g., Chapter/Metadata), 
                            // # we can potentially stop other, lower-priority lookups early.
                            if (result.getConfidence() > 0.8f) { 
                                completed.set(true); 
                            }
                            Log.i(TAG, "Found best result from " + strategy.getStrategyName());
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in strategy " + strategy.getStrategyName(), e);
                }
            });
            
            futures.add(future);
        }
        
        // # 3. Wait for all threads/futures to complete or the timeout to expire.
        long startTime = System.currentTimeMillis();
        while (!completed.get() && 
               (System.currentTimeMillis() - startTime) < DETECTION_TIMEOUT_MS &&
               !allFuturesCompleted(futures)) {
            try {
                // # Wait a short period before checking again.
                Thread.sleep(100); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // # 4. Cancel any remaining running threads to clean up resources.
        for (Future<?> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
        
        // # 5. Final Result Determination.
        SkipDetectionResult result = bestResult.get();
        
        // # If no strategy succeeded, use the Manual Preferences as a final fallback.
        if (result == null) {
            ManualPreferenceStrategy fallback = new ManualPreferenceStrategy(prefsHelper);
            result = fallback.detect(mediaIdentifier);
            Log.w(TAG, "No segment found. Falling back to Manual Preferences.");
        }
        
        // # 6. Cache the best non-cache result for future playback.
        cacheStrategy.cacheResult(mediaIdentifier, result);
        
        return result;
    }
    
    // # Helper to check if all submitted detection tasks are finished.
    private boolean allFuturesCompleted(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            if (!future.isDone()) {
                return false;
            }
        }
        return true;
    }
    
    // # Public methods for cache management.
    public void clearCache() {
        cacheStrategy.clearCache();
    }
    
    public void invalidateCache(MediaIdentifier mediaIdentifier) {
        cacheStrategy.invalidateCache(mediaIdentifier);
    }
    
    // # Clean shutdown of the thread pool.
    public void shutdown() {
        executorService.shutdown();
    }
}
