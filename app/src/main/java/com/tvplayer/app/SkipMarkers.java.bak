package com.tvplayer.app;

/**
 * SkipMarkers: A core data and logic class for managing video skip segments
 * (Intro, Recap, Credits) and the Next Episode button marker.
 * * Interacts with:
 * - MainActivity.java: This activity calls the 'isIn...' and 'isAt...' methods 
 * to determine when to display the skip buttons and calls the getter methods 
 * to determine where to seek to when a button is pressed.
 * - PreferencesHelper.java: This class is used by MainActivity to load the 
 * start and end times for these markers from the application's preferences,
 * which are then set using the setter methods below.
 */
public class SkipMarkers {

    /**
     * TimeRange: An inner class to hold the start and end of a segment
     * (Intro, Recap, Credits). All times are stored in seconds (int).
     */
    public static class TimeRange {
        // The start of the segment in seconds from the beginning of the video.
        public final int start; 
        // The end of the segment in seconds from the beginning of the video.
        public final int end; 

        public TimeRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        /**
         * Checks if the current video position is within the segment's range.
         * @param positionSeconds The current video time in seconds.
         * @return True if the position is between start and end (inclusive).
         */
        public boolean contains(long positionSeconds) {
            return positionSeconds >= start && positionSeconds <= end;
        }

        /**
         * Validates that the TimeRange has meaningful, non-zero data.
         * This prevents buttons from appearing if preference values were not loaded or are default (0).
         * @return True if start is non-negative AND end is after start.
         */
        public boolean isValid() {
            return start >= 0 && end > start;
        }
    }

    // --- Private Marker Data Fields ---
    
    private TimeRange intro;
    private TimeRange recap;
    private TimeRange credits;
    // Stores the calculated Next Episode marker time (seconds from video start).
    private int nextEpisodeStart; 

    /**
     * Constructor: Initializes all markers to an invalid (0, 0) state.
     */
    public SkipMarkers() {
        this.intro = new TimeRange(0, 0);
        this.recap = new TimeRange(0, 0);
        this.credits = new TimeRange(0, 0);
        this.nextEpisodeStart = 0;
    }

    // --- Public Setter Methods (Used by MainActivity to populate data) ---

    public void setIntro(int start, int end) {
        this.intro = new TimeRange(start, end);
    }

    public void setRecap(int start, int end) {
        this.recap = new TimeRange(start, end);
    }

    public void setCredits(int start, int end) {
        this.credits = new TimeRange(start, end);
    }

    public void setNextEpisodeStart(int start) {
        this.nextEpisodeStart = start;
    }

    // --- Public Getter Methods (Used by MainActivity for seek actions) ---
    
    // MainActivity uses these getters to seek to the *end* time of the segment.
    public TimeRange getIntro() {
        return intro;
    }

    public TimeRange getRecap() {
        return recap;
    }

    public TimeRange getCredits() {
        return credits;
    }

    // MainActivity uses this getter to seek to the calculated next episode time.
    public int getNextEpisodeStart() {
        return nextEpisodeStart;
    }

    // --- Core Logic: State Checkers (Used by MainActivity to show/hide buttons) ---

    /**
     * Determines if the current playback time is within the Intro segment.
     * @param positionSeconds The current video time in seconds.
     * @return true if the button should be shown.
     */
    public boolean isInIntro(long positionSeconds) {
        // Button shows only if the markers are valid AND the current time is in the range.
        return intro.isValid() && intro.contains(positionSeconds);
    }

    public boolean isInRecap(long positionSeconds) {
        return recap.isValid() && recap.contains(positionSeconds);
    }

    public boolean isInCredits(long positionSeconds) {
        return credits.isValid() && credits.contains(positionSeconds);
    }

    /**
     * Determines if the Next Episode button should be displayed.
     * * @param positionSeconds The current video time in seconds.
     * @return true if the button should be shown.
     */
    public boolean isAtNextEpisode(long positionSeconds) {
        // FIX: Defines a window (in seconds) before the marker time to display the button.
        // This gives the user time to click the button before the skip happens.
        final int WINDOW_SECONDS = 10; 

        // 1. Check if the marker time is valid (set to a positive value).
        // 2. Check if the current position is after the start of the display window.
        // 3. Check if the current position is BEFORE the actual skip time (nextEpisodeStart).
        return nextEpisodeStart > 0 && 
               positionSeconds >= (nextEpisodeStart - WINDOW_SECONDS) &&
               positionSeconds < nextEpisodeStart; 
    }
}
