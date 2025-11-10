package com.tvplayer.app.skipdetection;

public interface SkipDetectionStrategy {
    
    SkipDetectionResult detect(MediaIdentifier mediaIdentifier);
    
    String getStrategyName();
    
    boolean isAvailable();
    
    int getPriority();
}
