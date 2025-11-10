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

public class ManualPreferenceStrategy implements SkipDetectionStrategy {
    
    private final PreferencesHelper prefsHelper;
    
    public ManualPreferenceStrategy(PreferencesHelper prefsHelper) {
        this.prefsHelper = prefsHelper;
    }
    
    @Override
    public SkipDetectionResult detect(MediaIdentifier mediaIdentifier) {
        List<SkipSegment> segments = new ArrayList<>();
        
        int introStart = prefsHelper.getIntroStart();
        int introEnd = prefsHelper.getIntroEnd();
        if (introStart >= 0 && introEnd > introStart) {
            segments.add(new SkipSegment(SkipSegmentType.INTRO, introStart, introEnd));
        }
        
        int recapStart = prefsHelper.getRecapStart();
        int recapEnd = prefsHelper.getRecapEnd();
        if (recapStart >= 0 && recapEnd > recapStart) {
            segments.add(new SkipSegment(SkipSegmentType.RECAP, recapStart, recapEnd));
        }
        
        int creditsStart = prefsHelper.getCreditsStart();
        long runtimeSeconds = mediaIdentifier.getRuntimeSeconds();
        if (creditsStart > 0 && runtimeSeconds > 0) {
            int actualStart = (int) (runtimeSeconds - creditsStart);
            if (actualStart > 0) {
                segments.add(new SkipSegment(SkipSegmentType.CREDITS, actualStart, (int) runtimeSeconds));
            }
        }
        
        return SkipDetectionResult.success(
            DetectionSource.MANUAL_PREFERENCES,
            0.5f,
            segments.toArray(new SkipSegment[0])
        );
    }
    
    @Override
    public String getStrategyName() {
        return "Manual Preferences (Fallback)";
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public int getPriority() {
        return 1000;
    }
}
