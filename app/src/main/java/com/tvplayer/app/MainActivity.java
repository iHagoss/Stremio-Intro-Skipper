package com.tvplayer.app;

// Standard Android framework imports
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent; // <-- ADDED FOR PROGRESS BAR SEEKING
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

/**
 * MainActivity: The primary activity responsible for video playback.
 * It manages the ExoPlayer instance, UI controls, time displays,
 * skip functionality (intro, recap, credits), and user settings integration.
 *
 * Interacts with:
 * - layout/activity_main.xml (the main screen layout)
 * - PreferencesHelper.java (for reading/writing user settings like delays and auto-skip)
 * - SkipMarkers.java (data structure for skip segment times)
 * - SettingsActivity.java (for accessing user preferences)
 */
public class MainActivity extends AppCompatActivity {

    // --- Media Player Components (ExoPlayer and View) ---
    private ExoPlayer player;
    private PlayerView playerView; // Interacts with androidx.media3.ui.PlayerView in activity_main.xml
    private View customControls; // Container for all custom control elements

    // --- Helper/Model Classes ---
    private PreferencesHelper prefsHelper; // Interacts with PreferencesHelper.java (SharedPreferences wrapper)
    private SkipMarkers skipMarkers; // Interacts with SkipMarkers.java (Data model)

    // --- Time/Progress UI Components (Interacts with activity_main.xml) ---
    private TextView currentTimeOfDay;
    private TextView elapsedTime;
    private TextView totalTime;
    private TextView remainingTime;
    private TextView finishTime;
    private ProgressBar progressBar;

    // --- Skip Button UI Components (Interacts with activity_main.xml) ---
    private Button btnSkipIntro;
    private Button btnSkipRecap;
    private Button btnSkipCredits;
    private Button btnNextEpisode;

    // --- Primary Control Buttons (Interacts with activity_main.xml) ---
    private ImageButton btnPlayPause;
    private ImageButton btnSettings;
    private ImageButton btnAudioDelay;
    private ImageButton btnSubtitleDelay;

    // --- Handler and Runnable for Continuous Updates and Control Timeout ---
    private Handler updateHandler; // Used for scheduling continuous time/progress updates
    private Runnable updateRunnable; // The task that updates time displays and skip buttons
    private Handler controlsHandler; // Used for scheduling the control visibility timeout
    private Runnable hideControlsRunnable; // The task that hides the controls after a timeout

    // --- State Variables ---
    private boolean autoSkippedIntro = false;
    private boolean autoSkippedRecap = false;
    private boolean autoSkippedCredits = false;

    private static final int CONTROLS_TIMEOUT = 5000; // 5 seconds

    // =========================================================================
    // --- Lifecycle Methods ---
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Flag to keep the screen from dimming/turning off during playback
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Set the layout for the activity (Interacts with R.layout.activity_main)
        setContentView(R.layout.activity_main);
        // Initialize helper classes
        prefsHelper = new PreferencesHelper(this);
        skipMarkers = new SkipMarkers();
        loadSkipMarkersFromPreferences(); // Load skip times from PreferencesHelper (Intro/Recap/Credits only)

        // Setup the primary components
        initializeViews();
        setupPlayer();
        handleIntent(getIntent()); // Process the video URL passed to the activity
        setupUpdateHandlers();
        setupClickListeners();
        
        // --- ADDED FOR PROGRESS BAR SEEKING ---
        setupProgressBarSeeking();
        // --------------------------------------
    }

    // =========================================================================
    // --- Setup Methods ---
    // =========================================================================

    /**
     * Finds and assigns all UI elements from the activity_main.xml layout to their Java variables.
     */
    private void initializeViews() {
        playerView = findViewById(R.id.playerView);
        customControls = findViewById(R.id.customControls);

        currentTimeOfDay = findViewById(R.id.currentTimeOfDay);
        elapsedTime = findViewById(R.id.elapsedTime);
        totalTime = findViewById(R.id.totalTime);
        remainingTime = findViewById(R.id.remainingTime);
        finishTime = findViewById(R.id.finishTime);
        progressBar = findViewById(R.id.progressBar);

        btnSkipIntro = findViewById(R.id.btnSkipIntro);
        btnSkipRecap = findViewById(R.id.btnSkipRecap);
        btnSkipCredits = findViewById(R.id.btnSkipCredits);
        btnNextEpisode = findViewById(R.id.btnNextEpisode);

        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnSettings = findViewById(R.id.btnSettings);
        btnAudioDelay = findViewById(R.id.btnAudioDelay);
        btnSubtitleDelay = findViewById(R.id.btnSubtitleDelay);

        // Toggle controls visibility when the main player view is clicked
        playerView.setOnClickListener(v -> toggleControls());
    }

    /**
     * Initializes the ExoPlayer instance and sets up the listener for playback events.
     */
    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            // Called when the player state changes (buffering, ready, ended)
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    updatePlayPauseButton(); 
                    updateCreditsMarker(); // NEW: Calculate and set Credits marker based on duration
                    updateNextEpisodeMarker(); // Calculate and set Next Episode marker based on duration
                    applyAudioSubtitleDelays(); // Apply delays once media is ready
                } else if (state == Player.STATE_ENDED) {
                    finish(); // Close the activity when the video ends
                }
         
            }

            // Called if an error occurs during playback
            @Override
            public void onPlayerError(PlaybackException error) {
                // Interacts with R.string.error_playback (strings.xml)
                Toast.makeText(MainActivity.this, R.string.error_playback, Toast.LENGTH_SHORT).show();
            
            }

            // Called when the playing state changes (play/pause)
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButton(); // Update the icon instantly on play/pause
            }
        });
        // Apply delays immediately, though they may take effect only after STATE_READY
        applyAudioSubtitleDelays();
    }

    /**
     * Processes the Intent that started this activity to extract the video URL.
     */
    private void handleIntent(Intent intent) {
        String videoUrl = null;
        if (intent != null) {
            // Check for direct data URI (e.g., from an external share)
            Uri data = intent.getData();
            if (data != null) {
                videoUrl = data.toString();
            }
            // Check for explicit "videoUrl" or "uri" extras (e.g., from an internal activity call)
            else if (intent.hasExtra("videoUrl")) {
                videoUrl = intent.getStringExtra("videoUrl");
            } else if (intent.hasExtra("uri")) {
                videoUrl = intent.getStringExtra("uri");
            }
        }

        // Validate and start playback, or show error and close
        if (videoUrl != null && (videoUrl.startsWith("http://") || videoUrl.startsWith("https://"))) {
            playVideo(videoUrl);
        } else {
            // Interacts with R.string.error_no_url (strings.xml)
            Toast.makeText(this, R.string.error_no_url, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * Configures ExoPlayer to load and play the specified video URL.
     */
    private void playVideo(String url) {
        MediaItem mediaItem = MediaItem.fromUri(url);
        player.setMediaItem(mediaItem);
        player.prepare(); // Prepare the media source
        player.setPlayWhenReady(true); // Start playback automatically
    }

    /**
     * Loads skip segment timings (intro, recap, credits) from the PreferencesHelper.
     * NOTE: The Credits and Next Episode markers are calculated based on video duration and set later.
     */
    private void loadSkipMarkersFromPreferences() {
        // Uses methods in PreferencesHelper to retrieve start/end times (stored as longs or ints)
        // Uses methods in SkipMarkers to store the retrieved data
        skipMarkers.setIntro(prefsHelper.getIntroStart(), prefsHelper.getIntroEnd());
        skipMarkers.setRecap(prefsHelper.getRecapStart(), prefsHelper.getRecapEnd());
        // REMOVED: Credits marker calculation moved to updateCreditsMarker() to use video duration
        // skipMarkers.setCredits(prefsHelper.getCreditsStart(), prefsHelper.getCreditsEnd()); 
        // The credits 'start' preference is now the OFFSET FROM END. The 'end' is now 0 (placeholder).
        skipMarkers.setCredits(prefsHelper.getCreditsStart(), 0);
        // skipMarkers.setNextEpisodeStart(prefsHelper.getNextEpisodeStart()); // REMOVED: Needs duration to calculate
    }
    
    /**
     * Calculates the actual start time for the Skip Credits marker (seconds from start)
     * based on the user's preference (seconds from end) and the video duration.
     * This is called once the player duration is known (STATE_READY).
     *
     * IMPORTANT: This is the required fix for calculating the skip point from the END of the video.
     */
    private void updateCreditsMarker() {
        if (player == null) return;
        long durationSec = player.getDuration() / 1000;
        
        // This is the user-defined seconds from the end (e.g., 90)
        // NOTE: We are using the previously loaded 'start' preference value as the offset.
        long markerOffsetFromEndSec = prefsHelper.getCreditsStart(); 

        long actualStartTimeSec = -1; // Default to disabled

        if (durationSec > 0 && markerOffsetFromEndSec > 0) {
            // New logic: Duration - Offset = Time from start
            actualStartTimeSec = durationSec - markerOffsetFromEndSec;
            
            // Ensure the marker is not set before the start of the video
            if (actualStartTimeSec < 0) {
                actualStartTimeSec = 0;
            }
        }
        
        // We set the start marker to actualStartTimeSec, and the end seek-to point to the total duration.
        skipMarkers.setCredits((int) actualStartTimeSec, (int) durationSec);
    }
    
    /**
     * Calculates the actual start time for the Next Episode skip marker (seconds from start)
     * based on the user's preference (seconds from end) and the video duration.
     * This is called once the player duration is known (STATE_READY).
     */
    private void updateNextEpisodeMarker() {
        if (player == null) return;
        long durationSec = player.getDuration() / 1000;
        
        // This is the user-defined seconds from the end (e.g., 90)
        long markerOffsetFromEndSec = prefsHelper.getNextEpisodeStart(); 

        long actualStartTimeSec = -1; // Default to disabled

        if (durationSec > 0 && markerOffsetFromEndSec > 0) {
            // New logic: Duration - Offset = Time from start
            actualStartTimeSec = durationSec - markerOffsetFromEndSec;
            
            // Ensure the marker is not set before the start of the video
            if (actualStartTimeSec < 0) {
                actualStartTimeSec = 0;
            }
        }
        
        // FIX: Cast long (actualStartTimeSec) to int, as setNextEpisodeStart expects an int.
        // If actualStartTimeSec is -1, it means the marker is disabled.
        skipMarkers.setNextEpisodeStart((int) actualStartTimeSec);
    }

    /**
     * Sets up the handlers for continuous UI updates and control timeout.
     */
    private void setupUpdateHandlers() {
        // Handler for the main UI thread
        updateHandler = new Handler(Looper.getMainLooper());
        controlsHandler = new Handler(Looper.getMainLooper());

        // The task that runs every 500ms to update time, progress, and skip buttons
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimeDisplays(); // Updates all time TextViews and ProgressBar
                updateSkipButtons(); // Checks if skip buttons should be visible
                checkAutoSkip(); // Performs auto-skip if enabled and markers are hit
                updateHandler.postDelayed(this, 500); // Reschedule for next run
            }
        };
        updateHandler.post(updateRunnable); // Start the update loop

        // The task that simply hides the custom controls
        hideControlsRunnable = () -> {
            customControls.setVisibility(View.GONE);
        };
    }

    /**
     * Attaches a touch listener to the custom progress bar to enable seeking via dragging.
     */
    private void setupProgressBarSeeking() {
        progressBar.setOnTouchListener((v, event) -> {
            // Only proceed if the player is ready and has a duration
            if (player == null || player.getDuration() <= 0) {
                return false; 
            }

            // Get the width of the progress bar and the X coordinate of the touch event
            int width = progressBar.getWidth();
            float x = event.getX();
            
            // Calculate the position as a percentage (0.0 to 1.0)
            float touchPercent = Math.max(0, Math.min(1, x / width));
            
            // Calculate the corresponding seek time in milliseconds
            long newPositionMs = (long) (player.getDuration() * touchPercent);
            
            // The seek action is performed when the user touches down or moves the finger
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    player.seekTo(newPositionMs);
                    // Update the custom time displays immediately for visual feedback
                    updateTimeDisplays();
                    // Consume the event to prevent background clicks/toggles
                    return true;
                
                case MotionEvent.ACTION_UP:
                    // Perform the final seek and reset controls timeout to re-hide the controls
                    player.seekTo(newPositionMs);
                    updateTimeDisplays();
                    resetControlsTimeout(); 
                    return true;
                    
                default:
                    return false;
            }
        });
    }

    /**
     * Sets up all click listeners for the various control buttons.
     */
    private void setupClickListeners() {
        // Toggles between play and pause states
        btnPlayPause.setOnClickListener(v -> { 
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
            resetControlsTimeout(); 
        });
        
        // Rewinds by 10 seconds
        findViewById(R.id.btnRewind).setOnClickListener(v -> { 
            player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
            resetControlsTimeout(); 
        });
        
        // Fast-forwards by 30 seconds
        findViewById(R.id.btnFastForward).setOnClickListener(v -> { 
            player.seekTo(Math.min(player.getDuration(), player.getCurrentPosition() + 30000));
            resetControlsTimeout(); 
        });
        
        // Seeks to the beginning of the video
        findViewById(R.id.btnPrevious).setOnClickListener(v -> { 
            player.seekTo(0);
            resetControlsTimeout(); 
        });
        
        // Closes the player (acts as stop/next)
        findViewById(R.id.btnNext).setOnClickListener(v -> { 
            finish();
        });
        
        // Seek past the intro segment
        btnSkipIntro.setOnClickListener(v -> { 
            if (skipMarkers.getIntro().isValid()) {
                player.seekTo(skipMarkers.getIntro().end * 1000L);
                btnSkipIntro.setVisibility(View.GONE);
            }
        });
        
        // Seek past the recap segment
        btnSkipRecap.setOnClickListener(v -> { 
            if (skipMarkers.getRecap().isValid()) {
                player.seekTo(skipMarkers.getRecap().end * 1000L);
                btnSkipRecap.setVisibility(View.GONE);
            }
        });
        
        // Seek past the credits segment (or to end of video)
        btnSkipCredits.setOnClickListener(v -> { 
            if (skipMarkers.getCredits().isValid()) {
                player.seekTo(skipMarkers.getCredits().end * 1000L);
                btnSkipCredits.setVisibility(View.GONE);
            }
        });
        
        // Closes the player (acts as stop/next)
        btnNextEpisode.setOnClickListener(v -> { 
            finish();
        });
        
        // Launches the settings activity (Interacts with SettingsActivity.java)
        btnSettings.setOnClickListener(v -> { 
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
        
        // Opens dialog to adjust audio delay
        btnAudioDelay.setOnClickListener(v -> showAudioDelayDialog());
        
        // Opens dialog to adjust subtitle delay
        btnSubtitleDelay.setOnClickListener(v -> showSubtitleDelayDialog());
        
        // Placeholder for subtitle track selection
        findViewById(R.id.btnSubtitles).setOnClickListener(v -> { 
            Toast.makeText(this, "Subtitle tracks selection coming soon", Toast.LENGTH_SHORT).show();
            resetControlsTimeout(); 
        });
        
        // Opens dialog to change playback speed
        findViewById(R.id.btnPlaybackSpeed).setOnClickListener(v -> showPlaybackSpeedDialog());
    }

    // =========================================================================
    // --- Update and Utility Methods ---
    // =========================================================================

    /**
     * Updates all the TextViews that display time information (current, total, remaining, finish).
     * This method is called repeatedly by the updateRunnable.
     */
    private void updateTimeDisplays() {
        if (player == null) return;
        long currentPosMs = player.getCurrentPosition();
        long durationMs = player.getDuration();

        if (durationMs <= 0) return;

        // Get the current playback speed (e.g., 1.0, 1.5, 2.0)
        // FIXED: Replaced non-existent getPlaybackSpeed() with correct ExoPlayer API
        float speed = player.getPlaybackParameters().speed;
        long remainingMs = durationMs - currentPosMs;

        // Calculate the real-world time remaining (in milliseconds) based on the playback speed.
        // This is the core logic for speed-adjusted finish time.
        long realWorldRemainingMs = (long) (remainingMs / speed);
        long currentPosSec = currentPosMs / 1000;
        long durationSec = durationMs / 1000;
        long remainingSec = remainingMs / 1000; // Content time remaining

        // Format and display the current time of day
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        currentTimeOfDay.setText(timeFormat.format(new Date()));

        // Display elapsed, total, and content remaining time
        elapsedTime.setText(formatTime(currentPosSec));
        totalTime.setText(formatTime(durationSec));
        remainingTime.setText(formatTime(remainingSec));

        // Calculate the finish time by adding the real-world remaining time to the current time
        long finishTimeMs = System.currentTimeMillis() + realWorldRemainingMs;
        finishTime.setText(timeFormat.format(new Date(finishTimeMs)));

        // Update the progress bar
        int progress = (int) ((currentPosMs * 100) / durationMs);
        progressBar.setProgress(progress);
    }

    /**
     * Checks the current playback position against the skip markers and shows/hides skip buttons.
     */
    private void updateSkipButtons() {
        if (player == null) return;
        long currentPosSec = player.getCurrentPosition() / 1000;

        // skipMarkers.isIn[Segment] uses the SkipMarkers data and logic
        btnSkipIntro.setVisibility(skipMarkers.isInIntro(currentPosSec) ? View.VISIBLE : View.GONE);
        btnSkipRecap.setVisibility(skipMarkers.isInRecap(currentPosSec) ? View.VISIBLE : View.GONE);
        btnSkipCredits.setVisibility(skipMarkers.isInCredits(currentPosSec) ? View.VISIBLE : View.GONE);
        
        // This check now uses the time calculated in updateNextEpisodeMarker()
        btnNextEpisode.setVisibility(skipMarkers.isAtNextEpisode(currentPosSec) ? View.VISIBLE : View.GONE);
    }

    /**
     * Checks preferences and current position to automatically seek past segments if auto-skip is enabled.
     */
    private void checkAutoSkip() {
        if (player == null || !player.isPlaying()) return;
        long currentPosSec = player.getCurrentPosition() / 1000;

        // Checks PreferencesHelper for auto-skip setting and SkipMarkers for current position
        if (prefsHelper.isAutoSkipIntro() && !autoSkippedIntro && skipMarkers.isInIntro(currentPosSec)) {
            // Seeks to the end marker time
            player.seekTo(skipMarkers.getIntro().end * 1000L);
            autoSkippedIntro = true;
            Toast.makeText(this, "Auto-skipped intro", Toast.LENGTH_SHORT).show();
        }

        if (prefsHelper.isAutoSkipRecap() && !autoSkippedRecap && skipMarkers.isInRecap(currentPosSec)) {
            player.seekTo(skipMarkers.getRecap().end * 1000L);
            autoSkippedRecap = true;
            Toast.makeText(this, "Auto-skipped recap", Toast.LENGTH_SHORT).show();
        }

        if (prefsHelper.isAutoSkipCredits() && !autoSkippedCredits && skipMarkers.isInCredits(currentPosSec)) {
            player.seekTo(skipMarkers.getCredits().end * 1000L);
            autoSkippedCredits = true;
            Toast.makeText(this, "Auto-skipped credits", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Updates the icon of the play/pause button based on the player's current state.
     */
    private void updatePlayPauseButton() {
        // Uses standard Android drawable resources (android.R.drawable)
        if (player != null && player.isPlaying()) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    /**
     * Toggles the visibility of the custom control overlay.
     */
    private void toggleControls() {
        if (customControls.getVisibility() == View.VISIBLE) {
            customControls.setVisibility(View.GONE);
            controlsHandler.removeCallbacks(hideControlsRunnable); // Cancel auto-hide
        } else {
            customControls.setVisibility(View.VISIBLE);
            resetControlsTimeout(); // Start auto-hide timer
        }
    }

    /**
     * Resets the timer for automatically hiding the control overlay.
     */
    private void resetControlsTimeout() {
        controlsHandler.removeCallbacks(hideControlsRunnable);
        controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_TIMEOUT);
    }

    /**
     * Placeholder method to apply audio and subtitle delays (Actual delay implementation
     * would typically involve ExoPlayer's APIs, which may be missing here but the
     * preference retrieval is correctly delegated to PreferencesHelper).
     */
    private void applyAudioSubtitleDelays() {
        if (player == null) return;
        // Retrieve delay values from PreferencesHelper
        int audioDelayMs = prefsHelper.getAudioDelayMs();
        int subtitleDelayMs = prefsHelper.getSubtitleDelayMs();

        // Log output for debugging purposes
        android.util.Log.d("MainActivity", "Audio delay: " + audioDelayMs + "ms, Subtitle delay: " + subtitleDelayMs + "ms");
    }

    /**
     * Resets the flags used to ensure auto-skip only happens once per segment per video load/resume.
     */
    private void resetAutoSkipFlags() {
        autoSkippedIntro = false;
        autoSkippedRecap = false;
        autoSkippedCredits = false;
    }

    /**
     * Shows a dialog to allow the user to select and set the Audio Delay preference.
     */
    private void showAudioDelayDialog() {
        int currentDelay = prefsHelper.getAudioDelayMs();
        String[] options = {"-500ms", "-250ms", "-100ms", "0ms (Reset)", "+100ms", "+250ms", "+500ms"};
        int[] values = {-500, -250, -100, 0, 100, 250, 500};
        new AlertDialog.Builder(this)
                .setTitle(R.string.audio_delay)
                .setItems(options, (dialog, which) -> {
                    prefsHelper.setAudioDelayMs(values[which]);
                    applyAudioSubtitleDelays();
                    Toast.makeText(this, "Audio delay: \n" + options[which], Toast.LENGTH_SHORT).show();
                })
                .show();
        resetControlsTimeout();
    }

    /**
     * Shows a dialog to allow the user to select and set the Subtitle Delay preference.
     */
    private void showSubtitleDelayDialog() {
        int currentDelay = prefsHelper.getSubtitleDelayMs();
        String[] options = {"-500ms", "-250ms", "-100ms", "0ms (Reset)", "+100ms", "+250ms", "+500ms"};
        int[] values = {-500, -250, -100, 0, 100, 250, 500};
        new AlertDialog.Builder(this)
                .setTitle(R.string.subtitle_delay)
                .setItems(options, (dialog, which) -> {
                    prefsHelper.setSubtitleDelayMs(values[which]);
                    applyAudioSubtitleDelays();
                    Toast.makeText(this, "Subtitle delay: \n" + options[which], Toast.LENGTH_SHORT).show();
                })
                .show();
        resetControlsTimeout();
    }

    /**
     * Shows a dialog to allow the user to select and set the Playback Speed.
     */
    private void showPlaybackSpeedDialog() {
        String[] options = {"0.25x", "0.5x", "0.75x", "1.0x (Normal)", "1.05x", "1.10x", "1.15x", "1.20x", "1.25x", "1.5x", "2.0x", "3.0x", "5.0x"};
        float[] speeds = {0.25f, 0.5f, 0.75f, 1.0f, 1.05f, 1.10f, 1.15f, 1.20f, 1.25f, 1.5f, 2.0f, 3.0f, 5.0f};

        new AlertDialog.Builder(this)
                .setTitle(R.string.playback_speed)
                .setItems(options, (dialog, which) -> {
                    player.setPlaybackSpeed(speeds[which]);
                    Toast.makeText(this, "Speed: " + options[which], Toast.LENGTH_SHORT).show();
         
        })
                .show();
        resetControlsTimeout();
    }

    /**
     * Formats a duration (in total seconds) into a readable time string (MM:SS or H:MM:SS).
     */
    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (hours > 0) {
            // Format as H:MM:SS
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs);
        } else {
            // Format as MM:SS
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs);
        }
    }

    // =========================================================================
    // --- Overridden Lifecycle Methods ---
    // =========================================================================

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Used if the activity is already running and a new video is launched (e.g., from a notification)
        setIntent(intent);
        handleIntent(intent);
        loadSkipMarkersFromPreferences();
        applyAudioSubtitleDelays();
        resetAutoSkipFlags();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resumes playback if the activity regains focus
        if (player != null) {
            player.setPlayWhenReady(true);
        }
        // Reloads preferences in case they were changed in SettingsActivity
        loadSkipMarkersFromPreferences();
        applyAudioSubtitleDelays();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pauses playback when the activity loses focus (e.g., user opens another app)
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Crucial for memory management: stop all running handlers and release the player
        if (updateHandler != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
        if (controlsHandler != null) {
            controlsHandler.removeCallbacks(hideControlsRunnable);
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
