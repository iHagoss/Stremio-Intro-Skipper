package com.tvplayer.app.skipdetection;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.Player;

import com.tvplayer.app.PreferencesHelper;
// # Import the new audio strategy
import com.tvplayer.app.skipdetection.strategies.AudioFingerprintStrategy; 
import com.tvplayer.app.skipdetection.strategies.CacheStrategy;
import com.tvplayer.app.skipdetection.strategies.ChapterStrategy;
import com.tvplayer.app.skipdetection.strategies.IntroHaterStrategy;
import com.tvplayer.app.skipdetection.strategies.IntroSkipperStrategy;
import com.tvplayer.app.skipdetection.strategies.ManualPreferenceStrategy;
import com.tvplayer.app.skipdetection.strategies.MetadataHeuristicStrategy;

// # FIX: Added this import to resolve the 'cannot find symbol: variable DetectionSource' error
import com.tvplayer.app.skipdetection.SkipDetectionResult.DetectionSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SmartSkipManager
 * FUNCTION: Coordinates all skip detection strategies, manages execution,
 * prioritization, caching, and returns the best result to MainActivity.
 * INTERACTS WITH: All *Strategy.java files, MainActivity.java, PreferencesHelper.java.
 * PERSONALIZATION:
 * - DETECTION_TIMEOUT_MS: Change this to wait longer or shorter for API results (in milliseconds).
 * - EXECUTOR_THREADS: Change this to allow more/fewer strategies to run at the same time.
 */
public class SmartSkipManager {

    private static final String TAG = "SmartSkipManager";
    // # Max time (10 seconds) to wait for all strategies to finish.
    private static final int DETECTION_TIMEOUT_MS = 10000;
    // # Number of strategies to run at the same time.
    private static final int EXECUTOR_THREADS = 4;

    private final PreferencesHelper prefsHelper;
    private final ExecutorService executorService; 
    private final Handler mainHandler;
    private final CacheStrategy cacheStrategy;
    private final List<SkipDetectionStrategy> strategies;

    // # Special reference to ChapterStrategy so we can rebind the player
    private final ChapterStrategy chapterStrategy;

    /**
     * Constructor
     * FUNCTION: Initializes all strategies and sorts them by priority.
     * FIX: No longer takes a Player object to fix the startup build error.
     */
    public SmartSkipManager(Context context, PreferencesHelper prefsHelper) {
        this.prefsHelper = prefsHelper;
        this.executorService = Executors.newFixedThreadPool(EXECUTOR_THREADS);
        this.mainHandler = new Handler(Looper.getMainLooper());

        // # Initialize CacheStrategy (which is run separately)
        this.cacheStrategy = new CacheStrategy(context);

        // # Initialize all detection strategies
        this.strategies = new ArrayList<>();

        // # Create the ChapterStrategy instance (it's player-less for now)
        this.chapterStrategy = new ChapterStrategy();

        // # Add all strategies to the list
        // # P1 Priority 5 (Lowest)
        this.strategies.add(new ManualPreferenceStrategy(prefsHelper));
        // # P1 Priority 4
        this.strategies.add(new AudioFingerprintStrategy()); 
        // # P1 Priority 3
        this.strategies.add(new IntroHaterStrategy());
        this.strategies.add(new IntroSkipperStrategy());
        // # P1 Priority 1 (Highest)
        this.strategies.add(new MetadataHeuristicStrategy(prefsHelper));
        this.strategies.add(this.chapterStrategy); // # Add the instance we saved

        // # Sort the strategies based on their priority score (Highest number = Highest priority)
        Collections.sort(this.strategies, Comparator.comparingInt(SkipDetectionStrategy::getPriority).reversed());

        // # Log the final sorted order for debugging
        Log.i(TAG, "SmartSkipManager initialized. Strategy Priority Order:");
        for (int i = 0; i < this.strategies.size(); i++) {
            SkipDetectionStrategy s = this.strategies.get(i);
            Log.i(TAG, String.format("  #%d: %s (Priority=%d)", i + 1, s.getStrategyName(), s.getPriority()));
        }
    }

    /**
     * rebindPlayer
     * FUNCTION: Binds the player to strategies that need it (like ChapterStrategy).
     * This is called from MainActivity once the player is in the STATE_READY.
     * @param player The prepared Media3 Player instance.
     */
    public void rebindPlayer(Player player) {
        // # Pass the player to the chapter strategy
        if (chapterStrategy != null) {
            chapterStrategy.rebindToPlayer(player);
        }
    }

    /**
     * detectSkipSegmentsAsync
     * FUNCTION: Starts the detection process on a background thread.
     * @param mediaIdentifier Input data for the strategies.
     * @param callback The interface to return the result to (MainActivity).
     */
    public void detectSkipSegmentsAsync(MediaIdentifier mediaIdentifier, SkipDetectionCallback callback) {
        // # Run the entire detection logic on a background thread
        executorService.submit(() -> {
            SkipDetectionResult result = detectSkipSegmentsSync(mediaIdentifier);

            // # Post the final result back to the main UI thread
            mainHandler.post(() -> {
                if (result.isSuccess()) {
                    callback.onDetectionComplete(result);
                } else {
                    callback.onDetectionFailed(result.getErrorMessage());
                }
            });
        });
    }

    /**
     * detectSkipSegmentsSync
     * FUNCTION: The core, synchronous (blocking) detection logic.
     * This runs on the background thread.
     */
    private SkipDetectionResult detectSkipSegmentsSync(MediaIdentifier mediaIdentifier) {

        // # 1. Check Cache First
        SkipDetectionResult cachedResult = cacheStrategy.detect(mediaIdentifier);
        if (cachedResult.isSuccess()) {
            Log.i(TAG, "Cache hit: Returning result from " + cachedResult.getSource().getDisplayName());
            return cachedResult;
        }
        Log.i(TAG, "Cache miss. Starting fresh detection.");

        // # Clear any old chapter data before starting
        chapterStrategy.clearCapturedChapters();

        // # Variables to hold the best result
        final AtomicReference<SkipDetectionResult> bestResult = new AtomicReference<>(null);
        final List<Future<?>> futures = new ArrayList<>();

        // # 2. Run all strategies concurrently
        for (SkipDetectionStrategy strategy : strategies) {
            if (!strategy.isAvailable()) {
                Log.d(TAG, "Skipping unavailable strategy: " + strategy.getStrategyName());
                continue; 
            }

            // # Submit each strategy to be run on the thread pool
            Future<?> future = executorService.submit(() -> {
                try {
                    Log.d(TAG, "Starting detection: " + strategy.getStrategyName());
                    SkipDetectionResult result = strategy.detect(mediaIdentifier);

                    if (result.isSuccess()) {
                        Log.i(TAG, "Success from " + strategy.getStrategyName() + " (Conf: " + result.getConfidence() + ")");

                        // # Critical Section: Only one thread can update the best result
                        synchronized (bestResult) {
                            // # If no result is set, or if this new result is better, update it
                            if (bestResult.get() == null || result.getConfidence() > bestResult.get().getConfidence()) {
                                bestResult.set(result);
                                Log.i(TAG, "New best result set by " + strategy.getStrategyName());
                            }
                        }
                    } else {
                         Log.d(TAG, "Failed: " + strategy.getStrategyName() + " (" + result.getErrorMessage() + ")");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in strategy " + strategy.getStrategyName(), e);
                }
            });

            futures.add(future);
        }

        // # 3. Wait for all threads to complete or timeout
        long startTime = System.currentTimeMillis();
        boolean allDone = false;
        while (!allDone && (System.currentTimeMillis() - startTime) < DETECTION_TIMEOUT_MS) {
            allDone = true;
            for (Future<?> future : futures) {
                if (!future.isDone()) {
                    allDone = false;
                    break;
                }
            }
            if (!allDone) {
                try {
                    Thread.sleep(100); // # Wait 100ms before checking again
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // # 4. Cancel any remaining running threads (if timeout was hit)
        if (!allDone) {
            Log.w(TAG, "Detection timed out. Cancelling remaining strategies.");
            for (Future<?> future : futures) {
                if (!future.isDone()) {
                    future.cancel(true); // # Interrupt the running thread
                }
            }
        }

        // # 5. Final Result Determination
        SkipDetectionResult finalResult = bestResult.get();

        // # If no strategy succeeded, create a 'failed' result
        if (finalResult == null) {
            Log.w(TAG, "No strategy returned a successful result.");
            // # FIX: This line now compiles due to the new import
            finalResult = SkipDetectionResult.failed(DetectionSource.NONE, "No skip segments found by any strategy.");
        }

        // # 6. Cache the best result (even if it's a 'failed' result)
        cacheStrategy.cacheResult(mediaIdentifier, finalResult);

        return finalResult;
    }

    // # Public methods for cache management from Settings
    public void clearCache() {
        cacheStrategy.clearCache();
    }

    public void invalidateCache(MediaIdentifier mediaIdentifier) {
        cacheStrategy.invalidateCache(mediaIdentifier);
    }

    // # Clean shutdown of the thread pool
    public void shutdown() {
        executorService.shutdownNow();
    }
}