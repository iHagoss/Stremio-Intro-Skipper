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

// # This strategy attempts to detect skip segments based on Chapter Markers 
// # (like ID3/EMSG metadata) embedded directly in the media file.
public class ChapterStrategy implements SkipDetectionStrategy {
    
    private static final String TAG = "ChapterStrategy";
    private Player player;
    private final List<ChapterData> capturedChapters = new CopyOnWriteArrayList<>();
    private Player.Listener metadataListener;
    
    // # Constructor: Initialises the player and sets up the metadata listener.
    // # Interacts with: Player (ExoPlayer/Media3)
    public ChapterStrategy(Player player) {
        rebindToPlayer(player);
    }
    
    // # Rebinds the strategy to a new player instance and clears old chapter data.
    public void rebindToPlayer(Player player) {
        this.player = player;
        capturedChapters.clear();
        setupMetadataListener();
    }
    
    // # Sets up a listener to capture metadata (like ChapterFrame) as the video loads/plays.
    private void setupMetadataListener() {
        if (player == null) return;
        
        try {
            if (metadataListener != null) {
                try {
                    // # Safely remove the old listener if it exists before adding a new one.
                    player.removeListener(metadataListener);
                } catch (Exception ignore) {
                    // Ignore, listener may not have been fully registered
                }
            }
        } catch (Exception e) {
             Log.e(TAG, "Error removing old listener", e);
        }
        
        metadataListener = new Player.Listener() {
            @Override
            public void onMediaMetadataChanged(androidx.media3.common.MediaMetadata mediaMetadata) {
                // Not used for chapter metadata in this implementation
            }
            
            @Override
            public void onMetadata(Metadata metadata) {
                for (int i = 0; i < metadata.length(); i++) {
                    Metadata.Entry entry = metadata.get(i);
                    // # Checks for ID3 ChapterFrame metadata, which contains chapter start/end times.
                    if (entry instanceof ChapterFrame) {
                        ChapterFrame chapter = (ChapterFrame) entry;
                        // # Title of the chapter often contains the type (Intro, Recap, etc.)
                        String title = chapter.id; 
                        
                        // # ID3 times are in milliseconds, convert to seconds.
                        int startSec = (int) (chapter.startTimeMs / 1000);
                        int endSec = (int) (chapter.endTimeMs / 1000);
                        
                        // # Heuristic to determine segment type from the title (e.g., 'Intro', 'Opening').
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
                        TextInformationFrame textFrame = (TextInformationFrame) entry;
                        // # Logic for parsing textFrame is complex and omitted, focusing on ChapterFrame for stability.
                        Log.d(TAG, "Ignored TextInformationFrame: " + textFrame.id);
                    }
                }
            }
        };
        
        player.addListener(metadataListener);
    }
    
    // # Core detection logic: Reads the chapters captured by the listener and converts them to segments.
    // # Interacts with: MediaIdentifier (to check content details)
    @Override
    public SkipDetectionResult detect(MediaIdentifier mediaIdentifier) {
        if (!isAvailable()) {
            return SkipDetectionResult.failed(DetectionSource.CHAPTER_MARKERS, "Player is not available or bound");
        }
        
        try {
            List<SkipSegment> segments = new ArrayList<>();
            
            // # Loop through the stored chapter data.
            for (ChapterData chapter : capturedChapters) {
                // # Convert the stored type string back to an enum and create a SkipSegment.
                String typeUpper = chapter.type.toUpperCase();
                SkipSegmentType type = SkipSegmentType.valueOf(typeUpper);
                
                // # Basic validation
                if (chapter.endSec > chapter.startSec) {
                    segments.add(new SkipSegment(type, chapter.startSec, chapter.endSec));
                }
            }
            
            if (segments.isEmpty()) {
                return SkipDetectionResult.failed(DetectionSource.CHAPTER_MARKERS, 
                    "Captured chapters did not match skip categories");
            }
            
            Log.d(TAG, "Chapter detection successful with " + segments.size() + " segments");
            // # Returns a high-confidence result, as chapter markers are usually very accurate.
            return SkipDetectionResult.success(
                DetectionSource.CHAPTER_MARKERS,
                0.85f,
                segments.toArray(new SkipSegment[0])
            );
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting chapters", e);
            return SkipDetectionResult.failed(DetectionSource.CHAPTER_MARKERS, "Error reading chapters: " + e.getMessage());
        }
    }
    
    // # Clears the list of captured chapters, usually called before loading a new media item.
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
        // # UPDATED: Set to 500 for the highest priority (Category 1: Chapter/Metadata).
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
