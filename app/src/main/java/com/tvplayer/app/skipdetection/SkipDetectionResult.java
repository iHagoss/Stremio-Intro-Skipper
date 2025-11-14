package com.tvplayer.app.skipdetection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SkipDetectionResult
 * FUNCTION: A data class that holds the results of a skip detection operation.
 * It contains the source, success status, and a list of found segments.
 * INTERACTS WITH: All *Strategy.java files (which create this) and MainActivity.java (which consumes this).
 * PERSONALIZATION: You can add more metadata to this class if strategies need to pass more info (e.g., confidence score).
 */
public class SkipDetectionResult {

    // --- Enums ---

    /**
     * SkipSegmentType: Defines the type of segment found.
     * FIX: Added NEXT_EPISODE to support the "Next Episode" button logic.
     */
    public enum SkipSegmentType {
        INTRO,
        RECAP,
        CREDITS,
        NEXT_EPISODE, // Added to fix compile error
        UNKNOWN
    }

    /**
     * DetectionSource: Identifies which strategy found the result.
     * FIX: Added getDisplayName() to provide a user-friendly name for Toasts.
     */
    public enum DetectionSource {
        MANUAL_PREFERENCE("Manual Settings"),
        CACHE("Cache"),
        CHAPTER_MARKERS("Chapter Markers"),
        METADATA_HEURISTIC("Metadata Heuristics"),
        INTROHATER_API("IntroHater API"),
        INTRO_SKIPPER_API("Intro-Skipper API"),
        AUDIO_FINGERPRINT("Audio Scan"),
        NONE("None");
        
        private final String displayName;
        
        DetectionSource(String displayName) {
            this.displayName = displayName;
        }

        // # FIX: Added this helper method to fix compile error in MainActivity
        public String getDisplayName() {
            return displayName;
        }
    }

    // --- Inner Class: SkipSegment ---

    /**
     * SkipSegment: Holds the start and end time for a single skip-able segment.
     */
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
            return endSeconds > startSeconds && startSeconds >= 0;
        }
    }

    // --- Main Class Fields ---

    private final boolean success;
    private final DetectionSource source;
    private final String errorMessage;
    private final List<SkipSegment> segments;
    private final float confidence; // Confidence score (0.0 to 1.0)

    // --- Constructors ---

    // # Private constructor, use static factory methods
    private SkipDetectionResult(boolean success, DetectionSource source, float confidence, List<SkipSegment> segments, String errorMessage) {
        this.success = success;
        this.source = source;
        this.confidence = confidence;
        this.segments = segments != null ? Collections.unmodifiableList(segments) : Collections.emptyList();
        this.errorMessage = errorMessage;
    }

    // --- Static Factory Methods ---

    /**
     * Creates a successful result with one or more segments.
     */
    public static SkipDetectionResult success(DetectionSource source, float confidence, SkipSegment... segments) {
        List<SkipSegment> segmentList = new ArrayList<>();
        if (segments != null) {
            for (SkipSegment segment : segments) {
                if (segment != null && segment.isValid()) {
                    segmentList.add(segment);
                }
            }
        }
        if (segmentList.isEmpty()) {
            return failed(source, "Success reported but no valid segments provided.");
        }
        return new SkipDetectionResult(true, source, confidence, segmentList, null);
    }
    
    /**
     * Creates a failed result.
     */
    public static SkipDetectionResult failed(DetectionSource source, String errorMessage) {
        return new SkipDetectionResult(false, source, 0f, null, errorMessage);
    }

    // --- Public Getters ---

    public boolean isSuccess() {
        return success;
    }

    public DetectionSource getSource() {
        return source;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
    
    public float getConfidence() {
        return confidence;
    }

    public List<SkipSegment> getSegments() {
        return segments;
    }

    /**
     * Helper to get the first segment matching a specific type.
     */
    public SkipSegment getSegmentByType(SkipSegmentType type) {
        for (SkipSegment segment : segments) {
            if (segment.type == type) {
                return segment;
            }
        }
        return null;
    }
    
    /**
     * Helper to check if a segment of a specific type exists.
     */
    public boolean hasSegmentType(SkipSegmentType type) {
        for (SkipSegment segment : segments) {
            if (segment.type == type) {
                return true;
            }
        }
        return false;
    }
}