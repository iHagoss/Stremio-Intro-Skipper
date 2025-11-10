package com.tvplayer.app.skipdetection;

public interface SkipDetectionCallback {
    
    void onDetectionComplete(SkipDetectionResult result);
    
    void onDetectionFailed(String errorMessage);
}
