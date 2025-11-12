package com.tvplayer.app;

// Standard Android framework imports
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent; 
import android.view.MotionEvent; 
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

// AndroidX/AppCompat (UI and compatibility support)
import androidx.appcompat.app.AppCompatActivity;

// Media3/ExoPlayer imports for video playback
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

// Java standard utility imports for time/date formatting
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Smart Skip Detection imports
import com.tvplayer.app.skipdetection.SmartSkipManager;
import com.tvplayer.app.skipdetection.MediaIdentifier;
import com.tvplayer.app.skipdetection.SkipDetectionCallback;
import com.tvplayer.app.skipdetection.SkipDetectionResult;
import com.tvplayer.app.skipdetection.SkipDetectionResult.SkipSegment;
import com.tvplayer.app.skipdetection.SkipDetectionResult.SkipSegmentType;
import com.tvplayer.app.skipdetection.SkipDetectionResult.DetectionSource;

public class MainActivity extends AppCompatActivity implements Player.Listener, SkipDetectionCallback {

    // --- Core Media Player Components ---
    private PlayerView playerView;
    private ExoPlayer player;
    
    // --- UI/Control Components ---
    private View customControls;
    private Handler controlsHandler = new Handler(Looper.getMainLooper());
    private static final int CONTROLS_TIMEOUT_MS = 5000;
    private ImageButton btnPlayPause, btnSettings, btnAudioDelay, btnSubtitleDelay, btnSubtitles, btnSpeed, btnButton4, btnButton5;
    private TextView tvCurrentTime, tvTotalTime, tvFinishTime, tvElapsedLabel, tvTotalLabel;
    private ProgressBar progressBar;
    
    // --- Skip Button Components ---
    private Button btnSkipIntro, btnSkipRecap, btnSkipCredits, btnNextEpisode;
    
    // --- Time/Scrubbing State ---
    private Handler timeUpdateHandler = new Handler(Looper.getMainLooper());
    private long scrubSpeed = 0; // 0 = not scrubbing, > 0 = fast forward, < 0 = rewind
    private int scrubMultiplier = 0; // 1x, 2x, 4x, etc.
    private final Runnable timeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updatePlayerTimeInfo();
            // Reschedule the runnable to run again in 1 second
            timeUpdateHandler.postDelayed(this, 1000); 
        }
    };
    private final Runnable controlsTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            hideControls();
        }
    };

    // --- Custom Logic Helpers ---
    private PreferencesHelper preferencesHelper;
    private SkipMarkers skipMarkers;
    private SmartSkipManager smartSkipManager;


    // =========================================================================
    // LIFECYCLE METHODS
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ensure the screen remains on while the activity is running
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        
        // Initialize helper classes
        preferencesHelper = new PreferencesHelper(this);
        skipMarkers = new SkipMarkers();
        smartSkipManager = new SmartSkipManager(this, preferencesHelper);

        // Map UI elements to their IDs
        initializeViews();
        
        // Set up the ExoPlayer instance
        initializePlayer();
        
        // Handle incoming intent (e.g., being launched from Stremio)
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // Update the activity's intent
        handleIntent(intent);
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        // Start the time update loop
        timeUpdateHandler.post(timeUpdateRunnable);
        // Load preferences in case they were changed in SettingsActivity
        loadPreferences();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Stop the time update loop when the activity is not visible
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release the player when the activity is destroyed
        releasePlayer();
        // Shut down the SmartSkipManager's thread pool
        smartSkipManager.shutdown();
        // Remove pending callbacks
        controlsHandler.removeCallbacks(controlsTimeoutRunnable);
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
    }

    // =========================================================================
    // INITIALIZATION & MEDIA HANDLING
    // =========================================================================

    /**
     * Finds and initializes all view components from activity_main.xml.
     * Interacts with: activity_main.xml
     */
    private void initializeViews() {
        playerView = findViewById(R.id.playerView);
        customControls = findViewById(R.id.customControlsContainer);

        // Time Info
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        tvFinishTime = findViewById(R.id.tvFinishTime);
        tvElapsedLabel = findViewById(R.id.tvElapsedLabel);
        tvTotalLabel = findViewById(R.id.tvTotalLabel);
        progressBar = findViewById(R.id.progressBar);

        // Control Buttons
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnSettings = findViewById(R.id.btnSettings);
        btnAudioDelay = findViewById(R.id.btnAudioDelay);
        btnSubtitleDelay = findViewById(R.id.btnSubtitleDelay);
        btnSubtitles = findViewById(R.id.btnSubtitles);
        btnSpeed = findViewById(R.id.btnSpeed);
        btnButton4 = findViewById(R.id.btnButton4);
        btnButton5 = findViewById(R.id.btnButton5);

        // Skip Buttons
        btnSkipIntro = findViewById(R.id.btnSkipIntro);
        btnSkipRecap = findViewById(R.id.btnSkipRecap);
        btnSkipCredits = findViewById(R.id.btnSkipCredits);
        btnNextEpisode = findViewById(R.id.btnNextEpisode);

        // Set click listeners for control buttons
        btnPlayPause.setOnClickListener(v -> player.setPlayWhenReady(!player.getPlayWhenReady()));
        btnSettings.setOnClickListener(v -> navigateToSettings());
        
        // Placeholder button listeners for features not yet implemented
        btnAudioDelay.setOnClickListener(v -> Toast.makeText(this, "Audio Delay Pressed", Toast.LENGTH_SHORT).show());
        btnSubtitleDelay.setOnClickListener(v -> Toast.makeText(this, "Subtitle Delay Pressed", Toast.LENGTH_SHORT).show());
        btnSubtitles.setOnClickListener(v -> Toast.makeText(this, "Subtitles Pressed", Toast.LENGTH_SHORT).show());
        btnSpeed.setOnClickListener(v -> Toast.makeText(this, "Playback Speed Pressed", Toast.LENGTH_SHORT).show());
        btnButton4.setOnClickListener(v -> Toast.makeText(this, "Button 4 Pressed", Toast.LENGTH_SHORT).show());
        btnButton5.setOnClickListener(v -> Toast.makeText(this, "Button 5 Pressed", Toast.LENGTH_SHORT).show());

        // Set click listeners for skip buttons
        btnSkipIntro.setOnClickListener(v -> performSkip(SkipSegmentType.INTRO));
        btnSkipRecap.setOnClickListener(v -> performSkip(SkipSegmentType.RECAP));
        btnSkipCredits.setOnClickListener(v -> performSkip(SkipSegmentType.CREDITS));
        btnNextEpisode.setOnClickListener(v -> performSkip(SkipSegmentType.NEXT_EPISODE));
        
        // Hide controls initially
        hideControls();
    }

    /**
     * Creates and configures the ExoPlayer instance.
     * Interacts with: Media3/ExoPlayer library
     */
    private void initializePlayer() {
        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            player.addListener(this);
            playerView.setPlayer(player);
            // Apply delay settings immediately
            applyPlayerSettings();
        }
    }
    
    /**
     * Releases the ExoPlayer resources.
     */
    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    /**
     * Handles incoming Intents, typically a VIEW intent for a media URI.
     * @param intent The intent that started the activity.
     */
    private void handleIntent(Intent intent) {
        Uri uri = intent.getData();
        if (uri != null && player != null) {
            MediaItem mediaItem = new MediaItem.Builder()
                .setUri(uri)
                .build();
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
            
            // Start the Smart Skip detection process
            startSkipDetection(uri);
        }
    }
    
    /**
     * Applies preference-based settings like audio/subtitle delay to the player.
     * Interacts with: PreferencesHelper.java
     */
    private void applyPlayerSettings() {
        if (player == null) return;
        
        // Apply audio delay (ExoPlayer expects a long in microseconds for audio delay)
        long audioDelayUs = (long) preferencesHelper.getAudioDelayMs() * 1000;
        player.setAudioDelay(audioDelayUs);

        // Apply subtitle delay (ExoPlayer expects a long in microseconds for subtitle delay)
        long subtitleDelayUs = (long) preferencesHelper.getSubtitleDelayMs() * 1000;
        player.setSubtitleDelay(subtitleDelayUs);
    }
    
    /**
     * Loads the manual skip markers from preferences.
     * Interacts with: PreferencesHelper.java, SkipMarkers.java
     */
    private void loadPreferences() {
        // Load the manual preference skip points into the SkipMarkers object
        skipMarkers.setIntro(preferencesHelper.getIntroStart(), preferencesHelper.getIntroEnd());
        skipMarkers.setRecap(preferencesHelper.getRecapStart(), preferencesHelper.getRecapEnd());
        skipMarkers.setCredits(preferencesHelper.getCreditsStart(), preferencesHelper.getCreditsEnd());
        skipMarkers.setNextEpisodeStart(preferencesHelper.getNextEpisodeStart());
        
        // Re-apply player settings in case delays were changed
        applyPlayerSettings();
    }
    
    /**
     * Initiates the Smart Skip detection process for the current media.
     * @param mediaUri The URI of the currently playing media.
     */
    private void startSkipDetection(Uri mediaUri) {
        // Placeholder for MediaIdentifier logic - In a real app, this would parse the URI or a file path 
        // to extract a title, episode number, IMDb ID, etc., to populate the MediaIdentifier.
        // For testing, we create a minimal identifier.
        // We set runtimeSeconds to a placeholder value (e.g., 20 minutes) as it's required by some strategies.
        MediaIdentifier mediaIdentifier = new MediaIdentifier.Builder()
            .setTitle(mediaUri.getLastPathSegment())
            .setRuntimeSeconds(20 * 60) 
            .build();
        
        // Kick off the detection process on the SmartSkipManager
        smartSkipManager.detectSkipSegments(mediaIdentifier, this);
        
        // Clear any old skip data until the new detection result comes back
        skipMarkers.clearAll();
        updateSkipButtonVisibility();
    }
    
    // =========================================================================
    // SKIP LOGIC & ACTIONS
    // =========================================================================
    
    /**
     * Updates the UI to show or hide skip buttons based on the player's current position.
     * Interacts with: SkipMarkers.java
     */
    private void updateSkipButtonVisibility() {
        if (player == null || player.getDuration() <= 0) {
            btnSkipIntro.setVisibility(View.GONE);
            btnSkipRecap.setVisibility(View.GONE);
            btnSkipCredits.setVisibility(View.GONE);
            btnNextEpisode.setVisibility(View.GONE);
            return;
        }

        long currentPositionSeconds = player.getCurrentPosition() / 1000;
        
        // Intro Button Logic
        if (skipMarkers.isInIntro(currentPositionSeconds)) {
            btnSkipIntro.setVisibility(View.VISIBLE);
        } else {
            btnSkipIntro.setVisibility(View.GONE);
        }

        // Recap Button Logic
        if (skipMarkers.isInRecap(currentPositionSeconds)) {
            btnSkipRecap.setVisibility(View.VISIBLE);
        } else {
            btnSkipRecap.setVisibility(View.GONE);
        }

        // Credits Button Logic
        if (skipMarkers.isInCredits(currentPositionSeconds)) {
            btnSkipCredits.setVisibility(View.VISIBLE);
        } else {
            btnSkipCredits.setVisibility(View.GONE);
        }

        // Next Episode Button Logic
        if (skipMarkers.isAtNextEpisode(currentPositionSeconds)) {
            btnNextEpisode.setVisibility(View.VISIBLE);
        } else {
            btnNextEpisode.setVisibility(View.GONE);
        }
        
        // TODO: Auto-skip logic to be implemented here
    }

    /**
     * Performs a seek operation based on the requested skip type.
     * Interacts with: SkipMarkers.java
     * @param type The type of segment to skip (INTRO, RECAP, CREDITS, NEXT_EPISODE).
     */
    private void performSkip(SkipSegmentType type) {
        long seekToMs = 0;
        String toastMessage = "";

        switch (type) {
            case INTRO:
                seekToMs = skipMarkers.getIntroEnd() * 1000;
                toastMessage = "Skipping Intro";
                break;
            case RECAP:
                seekToMs = skipMarkers.getRecapEnd() * 1000;
                toastMessage = "Skipping Recap";
                break;
            case CREDITS:
                // For Credits, we skip to the end of the video.
                seekToMs = player.getDuration(); 
                toastMessage = "Skipping Credits";
                break;
            case NEXT_EPISODE:
                // For Next Episode, we seek to the start time (this will trigger player.onPlaybackStateChanged(STATE_ENDED) eventually)
                seekToMs = skipMarkers.getNextEpisodeStart() * 1000; 
                toastMessage = "Next Episode Marker Hit";
                break;
            default:
                return;
        }

        if (seekToMs > 0 && seekToMs < player.getDuration()) {
            player.seekTo(seekToMs);
            Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show();
            // Hide all skip buttons immediately after a successful skip
            btnSkipIntro.setVisibility(View.GONE);
            btnSkipRecap.setVisibility(View.GONE);
            btnSkipCredits.setVisibility(View.GONE);
            btnNextEpisode.setVisibility(View.GONE);
        } else {
            Toast.makeText(this, "Skip marker is invalid or not set.", Toast.LENGTH_SHORT).show();
        }
    }
    
    // =========================================================================
    // SMART SKIP CALLBACKS (SkipDetectionCallback interface implementation)
    // =========================================================================

    /**
     * Called by SmartSkipManager when a skip segment result is successfully found.
     * Interacts with: SmartSkipManager.java, SkipMarkers.java
     */
    @Override
    public void onDetectionComplete(SkipDetectionResult result) {
        // Must run on the main thread to update UI
        runOnUiThread(() -> {
            if (result.isSuccess()) {
                // Clear any existing markers (e.g., from manual preferences)
                skipMarkers.clearAll();

                // Apply the new segments from the successful detection result
                for (SkipSegment segment : result.getSegments()) {
                    if (segment.type == SkipSegmentType.INTRO) {
                        skipMarkers.setIntro(segment.startSeconds, segment.endSeconds);
                    } else if (segment.type == SkipSegmentType.RECAP) {
                        skipMarkers.setRecap(segment.startSeconds, segment.endSeconds);
                    } else if (segment.type == SkipSegmentType.CREDITS) {
                        // NOTE: For Credits, we only care about the START time for the button logic
                        skipMarkers.setCredits(segment.startSeconds, segment.endSeconds);
                    }
                }
                
                // Set a Next Episode marker if one of the segments is close to the end
                if (result.hasSegmentType(SkipSegmentType.CREDITS) && player.getDuration() > 0) {
                    // Set next episode marker to start 10 seconds before credits start
                    SkipSegment credits = result.getSegmentByType(SkipSegmentType.CREDITS);
                    int nextEpTime = credits.startSeconds - 10;
                    if (nextEpTime > 0) {
                        skipMarkers.setNextEpisodeStart(nextEpTime);
                    }
                }
                
                Toast.makeText(this, "Skip markers found via " + result.getSource().getDisplayName(), Toast.LENGTH_LONG).show();
            } else {
                // If external detection failed, fall back to manual preferences (already loaded in onStart)
                loadPreferences();
                Toast.makeText(this, "Skip detection failed. Using manual preferences.", Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Called by SmartSkipManager on a complete detection failure.
     */
    @Override
    public void onDetectionFailed(String errorMessage) {
        // Must run on the main thread to update UI
        runOnUiThread(() -> {
            loadPreferences();
            Toast.makeText(this, "Skip detection critical failure: " + errorMessage + ". Using manual preferences.", Toast.LENGTH_LONG).show();
        });
    }

    // =========================================================================
    // UI/CONTROL METHODS
    // =========================================================================

    /**
     * Navigates to the SettingsActivity.
     * Interacts with: SettingsActivity.java
     */
    private void navigateToSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
    
    /**
     * Shows the custom control overlay and starts the auto-hide timer.
     */
    private void showControls() {
        if (customControls.getVisibility() != View.VISIBLE) {
            customControls.setVisibility(View.VISIBLE);
        }
        resetControlsTimeout();
    }

    /**
     * Hides the custom control overlay.
     */
    private void hideControls() {
        customControls.setVisibility(View.GONE);
        // Ensure skip buttons are always visible if they should be
        updateSkipButtonVisibility();
    }

    /**
     * Shows controls if hidden, or hides them if visible.
     */
    private void toggleControls() {
        if (customControls.getVisibility() == View.VISIBLE) {
            hideControls();
        } else {
            showControls();
        }
    }
    
    /**
     * Resets the 5-second timer before the controls auto-hide.
     */
    private void resetControlsTimeout() {
        controlsHandler.removeCallbacks(controlsTimeoutRunnable);
        controlsHandler.postDelayed(controlsTimeoutRunnable, CONTROLS_TIMEOUT_MS);
    }
    
    /**
     * Updates the play/pause button icon based on the player state.
     */
    private void updatePlayPauseButton() {
        if (player == null) return;
        if (player.isPlaying()) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    // =========================================================================
    // TIME & PROGRESS BAR UPDATES
    // =========================================================================

    /**
     * Updates all time displays (current, total, finish time) and the progress bar.
     */
    private void updatePlayerTimeInfo() {
        if (player == null || player.getDuration() <= 0) {
            tvCurrentTime.setText("00:00:00");
            tvTotalTime.setText("00:00:00");
            tvFinishTime.setText("00:00");
            progressBar.setProgress(0);
            updateSkipButtonVisibility();
            return;
        }

        long currentPosition = player.getCurrentPosition();
        long totalDuration = player.getDuration();
        long currentPositionSeconds = currentPosition / 1000;

        // Scrubbing Mode (when FF/RW buttons are held)
        if (scrubSpeed != 0) {
            currentPosition += scrubSpeed;
            currentPosition = Math.max(0, currentPosition);
            currentPosition = Math.min(totalDuration, currentPosition);
            
            // Immediately seek the player to the new position if the multiplier is high
            // to provide visual feedback and prevent large jumps after releasing the button.
            if (Math.abs(scrubMultiplier) > 1) {
                player.seekTo(currentPosition);
            }
            // Update the display for a more responsive feel, but don't seek yet.
        }

        // Time Formatting
        String currentTimeFormatted = formatTime(currentPosition);
        String totalTimeFormatted = formatTime(totalDuration);
        String finishTimeFormatted = formatFinishTime(totalDuration);

        tvCurrentTime.setText(currentTimeFormatted);
        tvTotalTime.setText(totalTimeFormatted);
        tvFinishTime.setText(finishTimeFormatted);
        
        // Progress Bar Update
        int progress = (int) ((currentPosition * 100) / totalDuration);
        progressBar.setProgress(progress);
        
        // Skip Button Logic
        updateSkipButtonVisibility();
    }

    /**
     * Converts a millisecond duration into a HH:mm:ss format.
     */
    private String formatTime(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60));

        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            // Remove hours if the total duration is less than 1 hour
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    /**
     * Calculates and formats the finish time (system time when the video ends).
     */
    private String formatFinishTime(long totalDuration) {
        long finishTimeMillis = System.currentTimeMillis() + totalDuration - player.getCurrentPosition();
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
        return sdf.format(new Date(finishTimeMillis));
    }
    
    // =========================================================================
    // KEY EVENT HANDLING (Custom Remote/D-Pad Logic)
    // =========================================================================

    /**
     * Stops the fast-forward/rewind scrubbing action.
     */
    private void stopScrubbing() {
        if (player == null || scrubSpeed == 0) return;
        
        // Seek to the final scrubbed position
        long finalPosition = player.getCurrentPosition() + scrubSpeed;
        finalPosition = Math.max(0, finalPosition);
        finalPosition = Math.min(player.getDuration(), finalPosition);
        player.seekTo(finalPosition);

        // Reset state
        scrubSpeed = 0;
        scrubMultiplier = 0;
        
        // Show controls briefly after stopping scrub
        showControls();
    }

    /**
     * Main handler for all physical key presses (Remote/D-Pad).
     * Interacts with: Android KeyEvent system
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Ignore the event if the player is not ready
        if (player == null || player.getPlaybackState() == Player.STATE_IDLE || player.getDuration() <= 0) {
            return super.dispatchKeyEvent(event);
        }
        
        // --- KEY DOWN ACTION HANDLING ---
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            
            // Consume key press if we are scrubbing to prevent other actions
            if (scrubSpeed != 0 && (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_REWIND || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)) {
                
                // Increase multiplier only on repeat events (long press)
                if (event.getRepeatCount() > 0) {
                    // Start at 1x, then 2x, 4x, 8x, up to 16x
                    scrubMultiplier = Math.min(16, (scrubMultiplier == 0 ? 1 : scrubMultiplier * 2));
                    long baseSeek = 500L * scrubMultiplier; // 500ms is the base step
                    scrubSpeed = (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_REWIND) ? -baseSeek : baseSeek;
                } else if (scrubMultiplier == 0) {
                    // First press is 1x
                    scrubMultiplier = 1;
                    scrubSpeed = (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_REWIND) ? -500L : 500L;
                }
                updatePlayerTimeInfo();
                return true;
            }

            // Key press handling for non-scrubbing actions
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    // Toggle controls on D-pad center (only on first press)
                    if (event.getRepeatCount() == 0) {
                        toggleControls();
                        return true;
                    }
                    return false; // Allow system to handle focus movement for repeat event

                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    // Always show controls on D-pad movement
                    showControls();
                    // If controls are visible, allow default focus movement for the system
                    if (customControls.getVisibility() == View.VISIBLE) {
                        return super.dispatchKeyEvent(event);
                    } else {
                        // If controls are hidden, seek 5 seconds forward
                        player.seekTo(player.getCurrentPosition() + 5000);
                        return true;
                    }
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    showControls();
                    if (customControls.getVisibility() == View.VISIBLE) {
                        return super.dispatchKeyEvent(event);
                    } else {
                        // If controls are hidden, seek 5 seconds backward
                        player.seekTo(player.getCurrentPosition() - 5000);
                        return true;
                    }
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    // First time a media key is pressed (not a long press repeat)
                    if (event.getRepeatCount() == 0) {
                        scrubMultiplier = 1;
                        long seekTime = (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_REWIND) ? -500L : 500L;
                        scrubSpeed = seekTime;
                        updatePlayerTimeInfo();
                    }
                    return true;
                    
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    // Stop scrubbing before playing/pausing
                    if (scrubMultiplier > 0) {
                        stopScrubbing();
                    }
                    if (player.isPlaying()) {
                        player.pause();
                    } else {
                        player.play();
                    }
                    updatePlayPauseButton();
                    toggleControls(); 
                    return true;
            }

        // --- KEY UP ACTION HANDLING ---
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    // Stop scrubbing when the button is released
                    stopScrubbing();
                    return true;

                case KeyEvent.KEYCODE_DPAD_CENTER:
                    // FIX: Ensure the UP event is consumed if controls are hidden
                    if (customControls.getVisibility() != View.VISIBLE) {
                        return true; 
                    }
                    break;
            }
        }

        // Fallback: Delegate unhandled keys to PlayerView's internal controller logic 
        if (playerView != null && playerView.dispatchKeyEvent(event)) {
            customControls.setVisibility(View.VISIBLE);
            resetControlsTimeout();
            return true;
        }

        // Final fallback
        return super.dispatchKeyEvent(event);
    }

}
