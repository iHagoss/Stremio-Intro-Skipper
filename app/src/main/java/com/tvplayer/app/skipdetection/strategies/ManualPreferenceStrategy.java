package com.tvplayer.app.skipdetection.strategies;

import com.tvplayer.app.PreferencesHelper;
import com.tvplayer.app.skipdetection.MediaIdentifier;
import com.tvplayer.app.skipdetection.SkipDetectionResult;
import com.tvplayer.app.skipdetection.SkipDetectionResult.SkipSegment;
import com.tvplayer.app.skipdetection.SkipDetectionResult.SkipSegmentType;
import com.tvplayer.app.skipdetection.SkipDetectionResult.DetectionSource;
import com.tvplayer.app.skipdetection.SkipDetectionStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * ManualPreferenceStrategy
 * FUNCTION: This is the fallback strategy. It reads the skip times that the user
 * has manually entered in the app's settings.
 * INTERACTS WITH: PreferencesHelper.java (to read settings).
 * PERSONALIZATION: This strategy is entirely driven by user settings.
 */
public class ManualPreferenceStrategy implements SkipDetectionStrategy {
    
    private final PreferencesHelper prefsHelper;
    
    public ManualPreferenceStrategy(PreferencesHelper prefsHelper) {
        this.prefsHelper = prefsHelper;
    }
    
    @Override
    public SkipDetectionResult detect(MediaIdentifier mediaIdentifier) {
        List<SkipSegment> segments = new ArrayList<>();
        
        // # Read Intro time from settings
        int introStart = prefsHelper.getIntroStart();
        int introEnd = prefsHelper.getIntroEnd();
        if (introEnd > introStart) {
            segments.add(new SkipSegment(SkipSegmentType.INTRO, introStart, introEnd));
        }
        
        // # Read Recap time from settings
        int recapStart = prefsHelper.getRecapStart();
        int recapEnd = prefsHelper.getRecapEnd();
        if (recapEnd > recapStart) {
            segments.add(new SkipSegment(SkipSegmentType.RECAP, recapStart, recapEnd));
        }
        
        // # Read Credits time from settings
        // # Note: The 'credits_start' preference is an offset from the end (e.g., 180 seconds)
        long runtime = mediaIdentifier.getRuntimeSeconds();
        int creditsOffset = prefsHelper.getCreditsStart();
        if (creditsOffset > 0 && runtime > 0) {
            int creditsStart = (int) (runtime - creditsOffset);
            if (creditsStart > 0) {
                // # Credits run from the calculated start time to the end of the media
                segments.add(new SkipSegment(SkipSegmentType.CREDITS, creditsStart, (int) runtime));
            }
        }
        
        // # Read Next Episode marker from settings
        int nextEpStart = prefsHelper.getNextEpisodeStart();
        if (nextEpStart > 0 && runtime > 0 && nextEpStart < runtime) {
             // # This segment is symbolic; its start time is the marker
            segments.add(new SkipSegment(SkipSegmentType.NEXT_EPISODE, nextEpStart, (int) runtime));
        }
        
        if (segments.isEmpty()) {
            return SkipDetectionResult.failed(DetectionSource.MANUAL_PREFERENCE, "No manual skip times set.");
        }
        
        // # Manual settings are given a medium-high confidence by default
        return SkipDetectionResult.success(
            DetectionSource.MANUAL_PREFERENCE,
            0.7f,
            segments.toArray(new SkipSegment[0])
        );
    }
    
    @Override
    public String getStrategyName() {
        return "Manual User Preferences";
    }
    
    @Override
    public boolean isAvailable() {
        return true; // # Always available as a fallback
    }
    
    @Override
    public int getPriority() {
        // # FIX: Set to 100 for P1 Priority (Category 5: Lowest Priority).
        return 100;
    }
}