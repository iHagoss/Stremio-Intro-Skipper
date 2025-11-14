package com.tvplayer.app.skipdetection.strategies;

import android.util.Log;

import androidx.media3.common.Metadata;
import androidx.media3.common.Player;
import androidx.media3.extractor.metadata.id3.ChapterFrame;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;

import com.tvplayer.app.skipdetection.MediaIdentifier;
import com.tvplayer.app.skipdetection.SkipDetectionResult;
import com.tvplayer.app.skipdetection.SkipDetectionResult.SkipSegment;
import com.tvplayer.app.skipdetection.SkipDetectionResult.SkipSegmentType;
import com.tvplayer.app.skipdetection.SkipDetectionResult.DetectionSource;
import com.tvplayer.app.skipdetection.SkipDetectionStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ChapterStrategy
 * FUNCTION: Detects skip segments by listening for embedded Chapter markers (e.g., ID3 tags)
 * in the media stream. This is a very reliable, high-priority source.
 * INTERACTS WITH: SmartSkipManager.java (which calls it), Player (Media3) (which it listens to).
 * PERSONALIZATION: The 'title.toLowerCase().contains(...)' logic can be expanded
 * to support more chapter names (e.g., "opening", "ending", "previously on").
 */
public class ChapterStrategy implements SkipDetectionStrategy {
    
    private static final String TAG = "ChapterStrategy";
    private Player player;
    private final List<ChapterData> capturedChapters = new CopyOnWriteArrayList<>();
    private Player.Listener metadataListener;
    
    /**
     * FIX: Constructor is now empty. The player is provided later via rebindToPlayer
     * to avoid the circular dependency / startup order bug.
     */
    public ChapterStrategy() {
        // # Player is not available at construction time
    }
    
    /**
     * FIX: This method is called by SmartSkipManager once the player is ready.
     * It binds this strategy to the active player instance.
     * @param player The ExoPlayer/Media3 instance.
     */
    public void rebindToPlayer(Player player) {
        // # If we're binding to a new player, remove the listener from the old one
        if (this.player != null && this.metadataListener != null) {
            try {
                this.player.removeListener(this.metadataListener);
            } catch (Exception e) {
                Log.w(TAG, "Failed to remove old listener, player may be released.");
            }
        }
        
        this.player = player;
        this.capturedChapters.clear();
        
        if (player != null) {
            setupMetadataListener();
        }
    }
    
    /**
     * Sets up the Media3 player listener to capture metadata events.
     */
    private void setupMetadataListener() {
        // # Create the listener
        this.metadataListener = new Player.Listener() {
            @Override
            public void onMetadata(Metadata metadata) {
                // # This callback fires when new metadata (like chapters) is found in the stream
                for (int i = 0; i < metadata.length(); i++) {
                    Metadata.Entry entry = metadata.get(i);
                    // # Check if the metadata entry is a ChapterFrame
                    if (entry instanceof ChapterFrame) {
                        ChapterFrame chapter = (ChapterFrame) entry;
                        // # The 'id' field often contains the chapter title
                        String title = chapter.id; 
                        
                        // # Times are in milliseconds, convert to seconds
                        int startSec = (int) (chapter.startTimeMs / 1000);
                        int endSec = (int) (chapter.endTimeMs / 1000);
                        
                        // # Heuristic to determine segment type from the title (e.g., 'Intro', 'Opening')
                        if (title.toLowerCase().contains("intro") || title.toLowerCase().contains("opening")) {
                            capturedChapters.add(new ChapterData(SkipSegmentType.INTRO.name(), startSec, endSec));
                        } else if (title.toLowerCase().contains("recap")) {
                            capturedChapters.add(new ChapterData(SkipSegmentType.RECAP.name(), startSec, endSec));
                        } else if (title.toLowerCase().contains("credits") || title.toLowerCase().contains("outro")) {
                            capturedChapters.add(new ChapterData(SkipSegmentType.CREDITS.name(), startSec, endSec));
                        }
                        
                        Log.d(TAG, "Captured Chapter: " + title + " [" + startSec + "s to " + endSec + "s]");
                    }
                    // # Also check for generic TextInformationFrame, sometimes used for chapter cues.
                    else if (entry instanceof TextInformationFrame) {
                        // This logic is complex and not fully implemented, but shows potential
                        Log.d(TAG, "Ignored TextInformationFrame: " + ((TextInformationFrame) entry).id);
                    }
                }
            }
        };
        
        // # Attach the listener to the player
        this.player.addListener(this.metadataListener);
    }
    
    /**
     * Core detection logic: Reads the chapters captured by the listener.
     * This is called by SmartSkipManager on a background thread.
     */
    @Override
    public SkipDetectionResult detect(MediaIdentifier mediaIdentifier) {
        if (!isAvailable()) {
            return SkipDetectionResult.failed(DetectionSource.CHAPTER_MARKERS, "Player is not available or bound");
        }
        
        try {
            List<SkipSegment> segments = new ArrayList<>();
            
            // # Loop through the stored chapter data
            for (ChapterData chapter : capturedChapters) {
                // # Convert the stored type string back to an enum
                String typeUpper = chapter.type.toUpperCase();
                SkipSegmentType type = SkipSegmentType.valueOf(typeUpper);
                
                if (chapter.endSec > chapter.startSec) {
                    segments.add(new SkipSegment(type, chapter.startSec, chapter.endSec));
                }
            }
            
            if (segments.isEmpty()) {
                return SkipDetectionResult.failed(DetectionSource.CHAPTER_MARKERS, 
                    "No matching chapter segments found.");
            }
            
            Log.d(TAG, "Chapter detection successful with " + segments.size() + " segments");
            // # Returns a high-confidence result, as chapter markers are very accurate
            return SkipDetectionResult.success(
                DetectionSource.CHAPTER_MARKERS,
                0.90f, // High confidence
                segments.toArray(new SkipSegment[0])
            );
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting chapters", e);
            return SkipDetectionResult.failed(DetectionSource.CHAPTER_MARKERS, "Error reading chapters: " + e.getMessage());
        }
    }
    
    /**
     * Clears the list of captured chapters, usually called before loading a new media item.
     */
    public void clearCapturedChapters() {
        capturedChapters.clear();
    }
    
    @Override
    public String getStrategyName() {
        return "Chapter Markers (ID3/EMSG Metadata)";
    }
    
    @Override
    public boolean isAvailable() {
        // # Strategy is available if a player is bound and the listener is set up.
        return player != null && metadataListener != null;
    }
    
    @Override
    public int getPriority() {
        // # FIX: Set to 500 for P1 Priority (Category 1: Chapter/Metadata).
        return 500;
    }
    
    // # Private class to hold parsed chapter data temporarily.
    private static class ChapterData {
        String type;
        int startSec;
        int endSec;
        
        ChapterData(String type, int startSec, int endSec) {
            this.type = type;
            this.startSec = startSec;
            this.endSec = endSec;
        }
    }
}