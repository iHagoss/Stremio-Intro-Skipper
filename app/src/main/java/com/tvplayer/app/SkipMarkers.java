package com.tvplayer.app;

/**
 * SkipMarkers
 * FUNCTION: A data class that holds the active, in-use skip segments
 * (Intro, Recap, Credits) and the Next Episode marker.
 * INTERACTS WITH: MainActivity.java (which sets/gets data from this class).
 * PERSONALIZATION: The 'WINDOW_SECONDS' for the Next Episode button can be
 * changed to show the button earlier or later.
 */
public class SkipMarkers {

    /**
     * TimeRange: An inner class to hold the start and end of a segment
     * (Intro, Recap, Credits). All times are stored in seconds (int).
     */
    public static class TimeRange {
        // # The start of the segment in seconds
        public final int start; 
        // # The end of the segment in seconds
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
            // # Check if time is within the valid range
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
        clearAll();
    }

    /**
     * Clears all segment markers. Called when new media is loaded.
     */
    public void clearAll() {
        this.intro = new TimeRange(-1, -1);
        this.recap = new TimeRange(-1, -1);
        this.credits = new TimeRange(-1, -1);
        this.nextEpisodeStart = -1;
    }

    // --- Setter Methods (Called by MainActivity) ---

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

    // # FIX: Added getIntro() to return the TimeRange object, fixing build error
    public TimeRange getIntro() { return intro; }

    // # FIX: Added getRecap() to return the TimeRange object, fixing build error
    public TimeRange getRecap() { return recap; }

    public TimeRange getCredits() { return credits; }

    public int getNextEpisodeStart() { return nextEpisodeStart; }

    // --- Logic Methods (For controlling button visibility in MainActivity) ---

    /**
     * Determines if the player is currently inside the Intro segment.
     * @param positionSeconds The current video time in seconds.
     * @return true if the button should be shown.
     */
    public boolean isInIntro(long positionSeconds) {
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
        // # Defines a 15-second window before the marker time to display the button.
        final int WINDOW_SECONDS = 15; 

        // # Show the button if the marker is set and we are within the display window
        return nextEpisodeStart > 0 && 
               positionSeconds >= (nextEpisodeStart - WINDOW_SECONDS) &&
               positionSeconds < (nextEpisodeStart + WINDOW_SECONDS); // # Show for a bit after too
    }
}