package com.tvplayer.app;

/**
 * SkipMarkers: A core data and logic class for managing video skip segments
 * (Intro, Recap, Credits) and the Next Episode button marker.
 *
 * Interacts with:
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
         * @return True if the position is between start (inclusive) and end (exclusive).
         */
        public boolean contains(long positionSeconds) {
            return positionSeconds >= start && positionSeconds < end;
        }
        
        /**
         * Checks if the time range is valid (start is non-negative and before end).
         * @return True if the marker is set correctly.
         */
        public boolean isValid() {
            return start >= 0 && end > start;
        }
    }

    // --- Skip Segment Data Fields ---
    private TimeRange intro;
    private TimeRange recap;
    private TimeRange credits;
    private int nextEpisodeStart;

    /**
     * Constructor: Initializes all markers to an invalid (cleared) state.
     */
    public SkipMarkers() {
        this.intro = new TimeRange(-1, -1);
        this.recap = new TimeRange(-1, -1);
        this.credits = new TimeRange(-1, -1);
        this.nextEpisodeStart = -1;
    }

    /**
     * Clears all segment markers.
     */
    public void clearAll() {
        this.intro = new TimeRange(-1, -1);
        this.recap = new TimeRange(-1, -1);
        this.credits = new TimeRange(-1, -1);
        this.nextEpisodeStart = -1;
    }

    // --- Setter Methods (Called by MainActivity after loading preferences or API results) ---

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
    
    // --- Getter Methods (For performing the seek in MainActivity) ---

    public int getIntroStart() { return intro.start; }
    public int getIntroEnd() { return intro.end; }

    public int getRecapStart() { return recap.start; }
    public int getRecapEnd() { return recap.end; }
    
    public int getCreditsStart() { return credits.start; }
    public int getCreditsEnd() { return credits.end; }
    
    public int getNextEpisodeStart() { return nextEpisodeStart; }

    // --- Logic Methods (For controlling button visibility in MainActivity) ---

    /**
     * Determines if the player is currently inside the Intro segment.
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
     * @param positionSeconds The current video time in seconds.
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
