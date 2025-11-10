package com.tvplayer.app.skipdetection;

public class SkipDetectionResult {
    
    public enum DetectionSource {
        CACHE,
        CHAPTER_MARKERS,
        INTROHATER_API,
        INTRO_SKIPPER_API,
        METADATA_HEURISTIC,
        AUDIO_FINGERPRINT,
        MANUAL_PREFERENCES,
        FAILED
    }
    
    public enum SkipSegmentType {
        INTRO,
        RECAP,
        CREDITS
    }
    
    public static class SkipSegment {
        public final SkipSegmentType type;
        public final int startSeconds;
        public final int endSeconds;
        
        public SkipSegment(SkipSegmentType type, int startSeconds, int endSeconds) {
            this.type = type;
            this.startSeconds = startSeconds;
            this.endSeconds = endSeconds;
        }
        
        public boolean isValid() {
            return startSeconds >= 0 && endSeconds > startSeconds;
        }
    }
    
    private final DetectionSource source;
    private final float confidence;
    private final SkipSegment[] segments;
    private final boolean success;
    private final String errorMessage;
    
    private SkipDetectionResult(DetectionSource source, float confidence, SkipSegment[] segments, boolean success, String errorMessage) {
        this.source = source;
        this.confidence = confidence;
        this.segments = segments != null ? segments : new SkipSegment[0];
        this.success = success;
        this.errorMessage = errorMessage;
    }
    
    public static SkipDetectionResult success(DetectionSource source, float confidence, SkipSegment... segments) {
        return new SkipDetectionResult(source, confidence, segments, true, null);
    }
    
    public static SkipDetectionResult failed(DetectionSource source, String errorMessage) {
        return new SkipDetectionResult(source, 0.0f, null, false, errorMessage);
    }
    
    public DetectionSource getSource() {
        return source;
    }
    
    public float getConfidence() {
        return confidence;
    }
    
    public SkipSegment[] getSegments() {
        return segments;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public SkipSegment getSegmentByType(SkipSegmentType type) {
        for (SkipSegment segment : segments) {
            if (segment.type == type) {
                return segment;
            }
        }
        return null;
    }
    
    public boolean hasSegmentType(SkipSegmentType type) {
        return getSegmentByType(type) != null;
    }
}
