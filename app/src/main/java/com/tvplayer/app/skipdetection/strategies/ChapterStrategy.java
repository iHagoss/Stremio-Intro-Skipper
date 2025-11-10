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

public class ChapterStrategy implements SkipDetectionStrategy {
    
    private static final String TAG = "ChapterStrategy";
    private Player player;
    private final List<ChapterData> capturedChapters = new CopyOnWriteArrayList<>();
    private Player.Listener metadataListener;
    
    public ChapterStrategy(Player player) {
        rebindToPlayer(player);
    }
    
    public void rebindToPlayer(Player player) {
        this.player = player;
        capturedChapters.clear();
        setupMetadataListener();
    }
    
    private void setupMetadataListener() {
        if (player == null) return;
        
        try {
            if (metadataListener != null) {
                try {
                    player.removeListener(metadataListener);
                } catch (Exception e) {
                    Log.w(TAG, "Could not remove old listener", e);
                }
            }
            
            metadataListener = new Player.Listener() {
                @Override
                public void onMetadata(Metadata metadata) {
                    if (metadata == null) return;
                    
                    Log.d(TAG, "Received metadata with " + metadata.length() + " entries");
                    
                    for (int i = 0; i < metadata.length(); i++) {
                        Metadata.Entry entry = metadata.get(i);
                        
                        if (entry instanceof ChapterFrame) {
                            ChapterFrame chapter = (ChapterFrame) entry;
                            processChapterFrame(chapter);
                        } else if (entry instanceof TextInformationFrame) {
                            TextInformationFrame textFrame = (TextInformationFrame) entry;
                            if (textFrame.id != null && textFrame.id.equals("CHAP")) {
                                Log.d(TAG, "Found chapter text frame: " + textFrame.values);
                            }
                        }
                    }
                }
            };
            
            player.addListener(metadataListener);
            Log.d(TAG, "Metadata listener registered successfully to player: " + player);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up metadata listener", e);
        }
    }
    
    private void processChapterFrame(ChapterFrame chapter) {
        try {
            String chapterId = chapter.chapterId != null ? chapter.chapterId.toLowerCase() : "";
            
            long startMs = chapter.startTimeMs;
            long endMs = chapter.endTimeMs;
            int startSec = (int) (startMs / 1000);
            int endSec = (int) (endMs / 1000);
            
            String type = "unknown";
            if (chapterId.contains("intro") || chapterId.contains("opening")) {
                type = "intro";
            } else if (chapterId.contains("recap") || chapterId.contains("previously")) {
                type = "recap";
            } else if (chapterId.contains("credits") || chapterId.contains("ending") || chapterId.contains("outro")) {
                type = "credits";
            }
            
            if (!type.equals("unknown")) {
                ChapterData chapterData = new ChapterData(type, startSec, endSec);
                capturedChapters.add(chapterData);
                Log.d(TAG, "Captured chapter: " + type + " from " + startSec + "s to " + endSec + "s");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing chapter frame", e);
        }
    }
    
    @Override
    public SkipDetectionResult detect(MediaIdentifier mediaIdentifier) {
        if (player == null) {
            return SkipDetectionResult.failed(DetectionSource.CHAPTER_MARKERS, "Player not available");
        }
        
        try {
            if (capturedChapters.isEmpty()) {
                return SkipDetectionResult.failed(DetectionSource.CHAPTER_MARKERS, 
                    "No chapter metadata captured (file may not contain chapter markers)");
            }
            
            List<SkipSegment> segments = new ArrayList<>();
            
            for (ChapterData chapter : capturedChapters) {
                if (chapter.type.contains("intro") || chapter.type.contains("opening")) {
                    segments.add(new SkipSegment(SkipSegmentType.INTRO, chapter.startSec, chapter.endSec));
                } else if (chapter.type.contains("recap") || chapter.type.contains("previously")) {
                    segments.add(new SkipSegment(SkipSegmentType.RECAP, chapter.startSec, chapter.endSec));
                } else if (chapter.type.contains("credits") || chapter.type.contains("ending") || chapter.type.contains("outro")) {
                    segments.add(new SkipSegment(SkipSegmentType.CREDITS, chapter.startSec, chapter.endSec));
                }
            }
            
            if (segments.isEmpty()) {
                return SkipDetectionResult.failed(DetectionSource.CHAPTER_MARKERS, 
                    "Captured chapters did not match skip categories");
            }
            
            Log.d(TAG, "Chapter detection successful with " + segments.size() + " segments");
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
    
    public void clearCapturedChapters() {
        capturedChapters.clear();
    }
    
    @Override
    public String getStrategyName() {
        return "Chapter Markers (ID3/EMSG Metadata)";
    }
    
    @Override
    public boolean isAvailable() {
        return player != null && metadataListener != null;
    }
    
    @Override
    public int getPriority() {
        return 200;
    }
    
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
