package com.tvplayer.app;

import android.net.Uri;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MediaMetadataParser
 * FUNCTION: Utility class to extract TV show metadata (season, episode, show name)
 *           from video filenames and URIs.
 * INTERACTS WITH: MainActivity.java (uses it to parse URIs)
 * FIXED: Implemented comprehensive regex patterns to parse S01E01, 1x01,
 *        and other common TV show filename formats.
 */
public class MediaMetadataParser {

    private static final String TAG = "MediaMetadataParser";

    // # Regex patterns for different episode naming conventions
    // # Pattern 1: S01E01, s01e01, S1E1 (most common)
    private static final Pattern PATTERN_SE = Pattern.compile(
        "[Ss](\\d{1,2})[Ee](\\d{1,2})",
        Pattern.CASE_INSENSITIVE
    );

    // # Pattern 2: 1x01 (alternative format)
    private static final Pattern PATTERN_X = Pattern.compile(
        "(\\d{1,2})x(\\d{1,2})",
        Pattern.CASE_INSENSITIVE
    );

    // # Pattern 3: Season 1 Episode 1 (full text)
    private static final Pattern PATTERN_SEASON_EPISODE = Pattern.compile(
        "Season\\s*(\\d{1,2})\\s*Episode\\s*(\\d{1,2})",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * ParsedMetadata
     * FUNCTION: Container class to hold parsed metadata.
     */
    public static class ParsedMetadata {
        public String showName;
        public Integer seasonNumber;
        public Integer episodeNumber;
        public String episodeTitle;
        public boolean isTvShow;

        public ParsedMetadata() {
            this.isTvShow = false;
        }

        @Override
        public String toString() {
            if (isTvShow && seasonNumber != null && episodeNumber != null) {
                return showName + " - S" + String.format("%02d", seasonNumber) + 
                       "E" + String.format("%02d", episodeNumber) +
                       (episodeTitle != null ? " - " + episodeTitle : "");
            }
            return showName != null ? showName : "Unknown";
        }
    }

    /**
     * parseFromUri
     * FUNCTION: Extracts metadata from a video URI by analyzing the filename/path.
     * @param uri The video URI (from intent or player)
     * @return ParsedMetadata object with extracted information
     */
    public static ParsedMetadata parseFromUri(Uri uri) {
        ParsedMetadata metadata = new ParsedMetadata();

        if (uri == null) {
            return metadata;
        }

        // # Get the filename from URI
        String path = uri.getLastPathSegment();
        if (path == null) {
            path = uri.getPath();
        }
        if (path == null) {
            path = uri.toString();
        }

        Log.d(TAG, "Parsing metadata from: " + path);

        // # Try each pattern to extract season/episode
        Matcher matcher = PATTERN_SE.matcher(path);
        if (matcher.find()) {
            metadata.seasonNumber = Integer.parseInt(matcher.group(1));
            metadata.episodeNumber = Integer.parseInt(matcher.group(2));
            metadata.isTvShow = true;
            Log.d(TAG, "Matched S##E## pattern: S" + metadata.seasonNumber + 
                  "E" + metadata.episodeNumber);
        } else {
            matcher = PATTERN_X.matcher(path);
            if (matcher.find()) {
                metadata.seasonNumber = Integer.parseInt(matcher.group(1));
                metadata.episodeNumber = Integer.parseInt(matcher.group(2));
                metadata.isTvShow = true;
                Log.d(TAG, "Matched #x## pattern: " + metadata.seasonNumber + 
                      "x" + metadata.episodeNumber);
            } else {
                matcher = PATTERN_SEASON_EPISODE.matcher(path);
                if (matcher.find()) {
                    metadata.seasonNumber = Integer.parseInt(matcher.group(1));
                    metadata.episodeNumber = Integer.parseInt(matcher.group(2));
                    metadata.isTvShow = true;
                    Log.d(TAG, "Matched 'Season # Episode #' pattern");
                }
            }
        }

        // # Extract show name (everything before the season/episode pattern)
        if (metadata.isTvShow && matcher.start() > 0) {
            String namepart = path.substring(0, matcher.start());
            // # Clean up the show name: remove path separators and file extensions
            namepart = namepart.replaceAll("[/\\\\]", " ");
            namepart = namepart.replaceAll("[._-]", " ");
            namepart = namepart.trim();
            metadata.showName = namepart;
            Log.d(TAG, "Extracted show name: " + metadata.showName);
        } else {
            // # If not a TV show, use the filename as the title
            metadata.showName = path.replaceAll("[._-]", " ")
                                   .replaceAll("\\.[a-zA-Z0-9]{2,4}$", "") // Remove extension
                                   .trim();
        }

        return metadata;
    }

    /**
     * parseFromString
     * FUNCTION: Convenience method to parse from a String instead of Uri.
     * @param path The file path or name
     * @return ParsedMetadata object
     */
    public static ParsedMetadata parseFromString(String path) {
        if (path == null || path.isEmpty()) {
            return new ParsedMetadata();
        }
        return parseFromUri(Uri.parse(path));
    }
}
