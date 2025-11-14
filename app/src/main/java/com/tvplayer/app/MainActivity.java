package com.tvplayer.app;

// # Standard Android framework imports
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent; 
import android.view.MotionEvent; 
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

// # AndroidX/AppCompat (UI and compatibility support)
import androidx.appcompat.app.AppCompatActivity;

// # Media3/ExoPlayer imports for video playback
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

// # Java standard utility imports for time/date formatting
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

// # Smart Skip Detection imports
import com.tvplayer.app.skipdetection.SmartSkipManager;
import com.tvplayer.app.skipdetection.MediaIdentifier;
import com.tvplayer.app.skipdetection.SkipDetectionCallback;
import com.tvplayer.app.skipdetection.SkipDetectionResult;
import com.tvplayer.app.skipdetection.SkipDetectionResult.SkipSegment;
import com.tvplayer.app.skipdetection.SkipDetectionResult.SkipSegmentType;
import com.tvplayer.app.skipdetection.SkipDetectionResult.DetectionSource;

/**
 * MainActivity
 * FUNCTION: The main video player activity. Manages player state, UI overlays,
 * controls, and coordinates the skip detection logic.
 * INTERACTS WITH:
 * - activity_main.xml (The layout)
 * - styles.xml / colors.xml (Styling)
 * - PreferencesHelper.java (Loading settings)
 * - SmartSkipManager.java (Running skip detection)
 * - SkipMarkers.java (Holding the active skip times)
 * - SettingsActivity.java (Launching the settings page)
 */
public class MainActivity extends AppCompatActivity implements Player.Listener, SkipDetectionCallback {

    private static final String TAG = "MainActivity";

    // --- Core Media Player Components ---
    private PlayerView playerView;
    private ExoPlayer player;
    
    // --- UI/Control Components ---
    private View customControls; // # ID: customControlsOverlay
    private View skipButtonsOverlayContainer; // # Container for all skip buttons
    private Handler controlsHandler = new Handler(Looper.getMainLooper());
    private static final int CONTROLS_TIMEOUT_MS = 5000;
    private ImageButton btnPlayPause, btnSettings, btnRewind, btnFastForward;
    private TextView tvCurrentTime, tvTotalTime, tvRemainingTime, tvFinishTime;
    private ProgressBar progressBar;
    
    // --- Skip Button Components ---
    private Button btnSkipIntro, btnSkipRecap, btnSkipCredits, btnNextEpisode;
    private Button btnSkipCancel; // # NEW: Cancel Button (Feature P2/Cancel)
    
    // --- Time/Scrubbing State ---
    private Handler timeUpdateHandler = new Handler(Looper.getMainLooper());
    private static final int SCRUB_INTERVAL_MS = 250; 
    private static final int SCRUB_STEP_MS = 5000;
    private int scrubMultiplier = 0; // # 0 = not scrubbing
    
    // --- Custom Logic Helpers ---
    private PreferencesHelper preferencesHelper;
    private SkipMarkers skipMarkers;
    private SmartSkipManager smartSkipManager;

    // --- Time Formatting ---
    private final SimpleDateFormat durationFormat;
    private final SimpleDateFormat finishTimeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    
    // --- Runnables ---
    
    // # Runnable to hide controls after a timeout
    private final Runnable controlsTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            hideControls();
        }
    };
    
    /**
     * Runnable that updates all time displays and the progress bar every second.
     */
    private final Runnable timeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && (player.getPlaybackState() == Player.STATE_READY || player.getPlaybackState() == Player.STATE_BUFFERING) && player.isPlaying()) {
                updateProgress();
            }
            // # Reschedule the runnable to run again in 1 second
            timeUpdateHandler.postDelayed(this, 1000); 
        }
    };
    
    /**
     * Runnable for handling fast-scrubbing (FF/RW).
     */
    private final Runnable scrubRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && scrubMultiplier != 0) {
                long seekAmount = (long) scrubMultiplier * SCRUB_STEP_MS;
                long newPosition = player.getCurrentPosition() + seekAmount;
                // # Clamp position to valid range
                newPosition = Math.max(0, Math.min(newPosition, player.getDuration()));
                player.seekTo(newPosition);
                
                updateProgress(); // # Update time display immediately
                resetControlsTimeout(); // # Keep the controls visible
                
                // # Reschedule for the next seek tick
                timeUpdateHandler.postDelayed(this, SCRUB_INTERVAL_MS);
            }
        }
    };


    /**
     * Constructor
     * FUNCTION: Initializes the time formatter for durations.
     */
    public MainActivity() {
        super();
        // # This formatter will correctly show "00:30:00" instead of "30:00"
        durationFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        durationFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    // =========================================================================
    // LIFECYCLE METHODS
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // # Ensure the screen remains on while the activity is running
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        
        // # 1. Initialize helper classes
        preferencesHelper = new PreferencesHelper(this);
        skipMarkers = new SkipMarkers();
        // # Initialize SmartSkipManager *without* the Player.
        smartSkipManager = new SmartSkipManager(this, preferencesHelper);

        // # 2. Map UI elements to their IDs
        initializeViews();
        
        // # 3. Set up the ExoPlayer instance
        initializePlayer();
        
        // # 4. Handle incoming intent (e.g., being launched from Stremio)
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // # Update the activity's intent
        
        // # Release the old player and create a new one for the new media
        releasePlayer();
        initializePlayer();
        handleIntent(intent);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // # Start the time update loop
        timeUpdateHandler.post(timeUpdateRunnable);
        // # Load preferences in case they were changed in SettingsActivity
        loadPreferencesAndApply();
        // # Resume playback if the player exists
        if (player != null) {
            player.play();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // # Stop the time update loop when the activity is not visible
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
        // # Pause playback
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // # Release all resources
        releasePlayer();
        if (smartSkipManager != null) {
            smartSkipManager.shutdown();
        }
        controlsHandler.removeCallbacks(controlsTimeoutRunnable);
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        
        // # ID: customControlsOverlay
        customControls = findViewById(R.id.customControlsOverlay); 
        skipButtonsOverlayContainer = findViewById(R.id.skipButtonsOverlayContainer);

        // # Time Info
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        tvRemainingTime = findViewById(R.id.tvRemainingTime);
        tvFinishTime = findViewById(R.id.tvFinishTime);
        progressBar = findViewById(R.id.progressBar);

        // # Control Buttons
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnSettings = findViewById(R.id.btnSettings);
        btnRewind = findViewById(R.id.btnRewind);
        btnFastForward = findViewById(R.id.btnFastForward);

        // # Skip Buttons
        btnSkipIntro = findViewById(R.id.btnSkipIntro);
        btnSkipRecap = findViewById(R.id.btnSkipRecap);
        btnSkipCredits = findViewById(R.id.btnSkipCredits);
        btnNextEpisode = findViewById(R.id.btnNextEpisode);
        btnSkipCancel = findViewById(R.id.btnSkipCancel); // # NEW: Find cancel button

        // # Set click listeners for controls
        setupControlListeners();
        
        // # Set click listeners for skip buttons
        setupSkipButtonListeners();
        
        // # Hide controls initially
        hideControls();
    }
    
    /**
     * Sets click listeners for all playback controls.
     */
    private void setupControlListeners() {
        // # Player view itself toggles controls
        playerView.setOnClickListener(v -> toggleControls());

        // # Playback controls
        btnPlayPause.setOnClickListener(v -> {
            if (player != null) player.setPlayWhenReady(!player.getPlayWhenReady());
            resetControlsTimeout();
        });
        btnRewind.setOnClickListener(v -> {
            if (player != null) player.seekTo(player.getCurrentPosition() - 10000); // # 10 sec
            resetControlsTimeout();
        });
        btnFastForward.setOnClickListener(v -> {
            if (player != null) player.seekTo(player.getCurrentPosition() + 10000); // # 10 sec
            resetControlsTimeout();
        });
        
        // # Settings button
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            resetControlsTimeout();
        });
        
        // # Placeholder button listeners for other controls
        findViewById(R.id.btnAudioDelay).setOnClickListener(v -> Toast.makeText(this, "Audio Delay Not Implemented", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnSubtitleDelay).setOnClickListener(v -> Toast.makeText(this, "Subtitle Delay Not Implemented", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnSubtitles).setOnClickListener(v -> Toast.makeText(this, "Subtitles Not Implemented", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnPlaybackSpeed).setOnClickListener(v -> Toast.makeText(this, "Speed Not Implemented", Toast.LENGTH_SHORT).show());
    }
    
    /**
     * Sets click listeners for all skip-related buttons.
     */
    private void setupSkipButtonListeners() {
        btnSkipIntro.setOnClickListener(v -> performSkip(SkipSegmentType.INTRO));
        btnSkipRecap.setOnClickListener(v -> performSkip(SkipSegmentType.RECAP));
        btnSkipCredits.setOnClickListener(v -> performSkip(SkipSegmentType.CREDITS));
        btnNextEpisode.setOnClickListener(v -> performSkip(SkipSegmentType.NEXT_EPISODE)); 
        
        // # NEW: Cancel button hides the skip overlay
        btnSkipCancel.setOnClickListener(v -> {
            hideSkipButtons();
            // # Briefly show main controls so user knows action was registered
            showControls(); 
        });
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
        }
    }
    
    /**
     * Releases the ExoPlayer resources.
     */
    private void releasePlayer() {
        if (player != null) {
            player.removeListener(this); // # Important: remove listener
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
            Log.i(TAG, "Handling new media intent: " + uri.toString());
            MediaItem mediaItem = MediaItem.fromUri(uri);
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
        } else {
            Log.w(TAG, "Intent was null or had no data. Player not started.");
        }
    }
    
    /**
     * Loads settings from PreferencesHelper and applies them to the player.
     * Interacts with: PreferencesHelper.java
     */
    private void loadPreferencesAndApply() {
        if (player == null) return;
        
        // # Load manual markers into our SkipMarkers object
        skipMarkers.setIntro(preferencesHelper.getIntroStart(), preferencesHelper.getIntroEnd());
        skipMarkers.setRecap(preferencesHelper.getRecapStart(), preferencesHelper.getRecapEnd());
        
        // # Note: Credits "start" is an offset from the end
        long durationSec = player.getDuration() / 1000;
        int creditsOffset = preferencesHelper.getCreditsStart();
        if (creditsOffset > 0 && durationSec > 0) {
            skipMarkers.setCredits((int)(durationSec - creditsOffset), (int)durationSec);
        } else {
             // # This is a fallback if offset isn't set but manual end time is
            skipMarkers.setCredits(0, preferencesHelper.getCreditsEnd()); 
        }
        
        skipMarkers.setNextEpisodeStart(preferencesHelper.getNextEpisodeStart());

        // # Apply Subtitle Delay
        // # FIX: This is the correct Media3 API call for subtitle delay.
        // # The method is setSubtitleDelay(long) on the Player object.
        try {
            int subtitleDelayMs = preferencesHelper.getSubtitleDelayMs();
            player.setSubtitleDelay((long) subtitleDelayMs); // # This is the correct method
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply subtitle delay", e);
        }
    }
    
    /**
     * Initiates the Smart Skip detection process for the current media.
     * This is called once the player is ready.
     */
    private void startSkipDetection() {
        if (player == null || player.getDuration() <= 0) return;
        
        // # Pass the (now ready) player to the manager
        smartSkipManager.rebindPlayer(player);
        
        // # Create the MediaIdentifier with all known info
        MediaIdentifier mediaIdentifier = new MediaIdentifier.Builder()
            .setTitle(player.getMediaMetadata().title != null ? player.getMediaMetadata().title.toString() : "Unknown")
            .setRuntimeSeconds(player.getDuration() / 1000)
            // # TODO: Add Trakt/TMDB/TVDB IDs here when available
            // .setTraktId("...")
            // .setTmdbId("...")
            // .setSeasonNumber(1)
            // .setEpisodeNumber(1)
            .build();
            
        // # Start the async detection. 'this' (MainActivity) is the callback.
        smartSkipManager.detectSkipSegmentsAsync(mediaIdentifier, this);
    }
    
    // =========================================================================
    // PLAYER EVENT HANDLING (Player.Listener)
    // =========================================================================

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        // # Update the play/pause button icon
        updatePlayPauseButton();
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (playbackState == Player.STATE_READY) {
            Log.i(TAG, "Player is READY.");
            // # Player is ready, load settings
            loadPreferencesAndApply();
            // # Start the progress bar update loop
            timeUpdateHandler.post(timeUpdateRunnable);
            // # Start the skip detection process
            startSkipDetection();
            
        } else if (playbackState == Player.STATE_ENDED) {
            Log.i(TAG, "Player has ENDED.");
            finish(); // # Close the activity when the video finishes
            
        } else if (playbackState == Player.STATE_BUFFERING) {
            Log.d(TAG, "Player is BUFFERING.");
            
        } else if (playbackState == Player.STATE_IDLE) {
            Log.d(TAG, "Player is IDLE.");
        }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        // # Shows an alert dialog on playback error
        Log.e(TAG, "Player Error", error);
        new AlertDialog.Builder(this)
                .setTitle("Playback Error")
                .setMessage(error.getMessage())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .show();
    }
    
    // =========================================================================
    // SMART SKIP CALLBACKS (SkipDetectionCallback)
    // =========================================================================

    /**
     * Called by SmartSkipManager when a skip segment result is successfully found.
     * This runs on the MAIN THREAD.
     * Interacts with: SmartSkipManager.java, SkipMarkers.java
     */
    @Override
    public void onDetectionComplete(SkipDetectionResult result) {
        if (result.isSuccess()) {
            // # Use getDisplayName() which now exists
            Log.i(TAG, "Skip detection success from: " + result.getSource().getDisplayName());
            Toast.makeText(this, "Skip markers found via " + result.getSource().getDisplayName(), Toast.LENGTH_SHORT).show();
            
            // # Clear manual markers and apply the new, better ones
            skipMarkers.clearAll();
            for (SkipSegment segment : result.getSegments()) {
                if (segment.type == SkipSegmentType.INTRO) {
                    skipMarkers.setIntro(segment.startSeconds, segment.endSeconds);
                } else if (segment.type == SkipSegmentType.RECAP) {
                    skipMarkers.setRecap(segment.startSeconds, segment.endSeconds);
                } else if (segment.type == SkipSegmentType.CREDITS) {
                    skipMarkers.setCredits(segment.startSeconds, segment.endSeconds);
                } else if (segment.type == SkipSegmentType.NEXT_EPISODE) {
                    skipMarkers.setNextEpisodeStart(segment.startSeconds);
                }
            }
        } else {
            // # On failure, we just keep the manual preferences that were already loaded
            Log.w(TAG, "Skip detection failed: " + result.getErrorMessage() + ". Using manual preferences.");
            Toast.makeText(this, "Skip detection failed. Using manual settings.", Toast.LENGTH_LONG).show();
        }
        
        // # Do an initial check to show buttons if we're already in a segment
        updateSkipButtonVisibility();
        // # Check for auto-skip
        performAutoSkip();
    }

    /**
     * Called by SmartSkipManager on a critical failure.
     * This runs on the MAIN THREAD.
     */
    @Override
    public void onDetectionFailed(String errorMessage) {
        // # On critical failure, just use manual preferences
        Log.e(TAG, "Critical skip detection failure: " + errorMessage);
        Toast.makeText(this, "Skip detection error. Using manual settings.", Toast.LENGTH_LONG).show();
        loadPreferencesAndApply();
        updateSkipButtonVisibility();
    }

    // =========================================================================
    // SKIP LOGIC & ACTIONS
    // =========================================================================

    /**
     * Hides all skip-related buttons and the overlay container.
     * Called when a skip is performed or 'Cancel' is pressed.
     * (Feature P2/Cancel)
     */
    private void hideSkipButtons() {
        skipButtonsOverlayContainer.setVisibility(View.GONE);
        btnSkipIntro.setVisibility(View.GONE);
        btnSkipRecap.setVisibility(View.GONE);
        btnSkipCredits.setVisibility(View.GONE);
        btnNextEpisode.setVisibility(View.GONE);
        btnSkipCancel.setVisibility(View.GONE);
        
        // # Return focus to the player view to prevent a "lost focus" state
        if(playerView != null) {
            playerView.requestFocus();
        }
    }
    
    /**
     * Manages visibility and focus of all skip buttons.
     * Implements priority (Intro > Recap > Credits) (Feature P1).
     * Implements focus-fix (Feature P2/Focus).
     * This is called every second by the timeUpdateRunnable.
     */
    private void updateSkipButtonVisibility() {
        if (player == null || skipMarkers == null) return;

        long currentPositionSec = player.getCurrentPosition() / 1000;
        
        // # Check which segments are active
        boolean inIntro = skipMarkers.isInIntro(currentPositionSec);
        boolean inRecap = skipMarkers.isInRecap(currentPositionSec);
        boolean inCredits = skipMarkers.isInCredits(currentPositionSec);
        boolean atNextEp = skipMarkers.isAtNextEpisode(currentPositionSec);

        // # Reset visibility
        btnSkipIntro.setVisibility(View.GONE);
        btnSkipRecap.setVisibility(View.GONE);
        btnSkipCredits.setVisibility(View.GONE);
        btnNextEpisode.setVisibility(View.GONE);
        btnSkipCancel.setVisibility(View.GONE); // # Also hide cancel

        Button buttonToFocus = null;

        // --- PRIORITY LOGIC (Feature P1) ---
        // # Only show ONE skip button at a time, in this order:
        if (inIntro) {
            btnSkipIntro.setVisibility(View.VISIBLE);
            buttonToFocus = btnSkipIntro;
        } else if (inRecap) {
            btnSkipRecap.setVisibility(View.VISIBLE);
            buttonToFocus = btnSkipRecap;
        } else if (inCredits) {
            btnSkipCredits.setVisibility(View.VISIBLE);
            buttonToFocus = btnSkipCredits;
        }
        
        // # Next Episode button can show alongside Credits
        if (atNextEp) {
            btnNextEpisode.setVisibility(View.VISIBLE);
            if (buttonToFocus == null) { // # Focus NextEp if no other button is active
                buttonToFocus = btnNextEpisode;
            }
        }
        
        // --- FOCUS & CANCEL BUTTON LOGIC (Feature P2) ---
        if (buttonToFocus != null) {
            // # If any button is visible, show the container and the Cancel button
            skipButtonsOverlayContainer.setVisibility(View.VISIBLE);
            btnSkipCancel.setVisibility(View.VISIBLE);

            // # FOCUS FIX: Request focus on the highest-priority button
            // # This check prevents stealing focus if the user is using the main controls
            if (!customControls.isShown() && !skipButtonsOverlayContainer.hasFocus()) {
                 buttonToFocus.requestFocus();
            }
        } else if (skipButtonsOverlayContainer.isShown()) {
            // # No buttons are active, hide the container
            hideSkipButtons();
        }
    }

    /**
     * Performs a seek operation based on the requested skip type.
     * @param type The type of segment to skip (INTRO, RECAP, etc.).
     */
    private void performSkip(SkipSegmentType type) {
        if (player == null || skipMarkers == null) return;
        
        long seekToMs = -1;
        String toastMessage = "";

        switch (type) {
            case INTRO:
                // # Use getIntro().end to get the TimeRange object
                seekToMs = skipMarkers.getIntro().end * 1000L;
                toastMessage = "Skipping Intro";
                break;
            case RECAP:
                // # Use getRecap().end to get the TimeRange object
                seekToMs = skipMarkers.getRecap().end * 1000L;
                toastMessage = "Skipping Recap";
                break;
            case CREDITS:
                // # For Credits, skip to the end of the video
                seekToMs = player.getDuration(); 
                toastMessage = "Skipping Credits";
                break;
            case NEXT_EPISODE:
                // # For Next Episode, seek to the end to trigger STATE_ENDED
                seekToMs = player.getDuration();
                toastMessage = "Loading Next Episode..."; // # Placeholder
                break;
            default:
                return;
        }

        if (seekToMs >= 0) {
            player.seekTo(seekToMs);
            if (!toastMessage.isEmpty()) {
                Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show();
            }
            // # Hide buttons immediately after skip
            hideSkipButtons();
        }
    }
    
    /**
     * Checks preferences and automatically seeks if an auto-skip is due.
     */
    private void performAutoSkip() {
        if (player == null || skipMarkers == null) return;
        
        long currentPositionSec = player.getCurrentPosition() / 1000;
        
        // # Check Auto-Skip Intro
        if (preferencesHelper.isAutoSkipIntro() && skipMarkers.isInIntro(currentPositionSec)) {
            Log.i(TAG, "Auto-skipping Intro");
            Toast.makeText(this, "Auto-skipping Intro", Toast.LENGTH_SHORT).show();
            performSkip(SkipSegmentType.INTRO);
            return; // # Only perform one auto-skip per check
        }
        
        // # Check Auto-Skip Recap
        if (preferencesHelper.isAutoSkipRecap() && skipMarkers.isInRecap(currentPositionSec)) {
            Log.i(TAG, "Auto-skipping Recap");
            Toast.makeText(this, "Auto-skipping Recap", Toast.LENGTH_SHORT).show();
            performSkip(SkipSegmentType.RECAP);
            return;
        }
        
        // # Check Auto-Skip Credits
        if (preferencesHelper.isAutoSkipCredits() && skipMarkers.isInCredits(currentPositionSec)) {
            Log.i(TAG, "Auto-skipping Credits");
            Toast.makeText(this, "Auto-skipping Credits", Toast.LENGTH_SHORT).show();
            performSkip(SkipSegmentType.CREDITS);
        }
    }
    
    // =========================================================================
    // UI/CONTROL METHODS
    // =========================================================================
    
    /**
     * Shows the custom control overlay and starts the auto-hide timer.
     */
    private void showControls() {
        if (customControls.getVisibility() != View.VISIBLE) {
            customControls.setVisibility(View.VISIBLE);
            // # Request focus on the play button for D-pad control
            btnPlayPause.requestFocus();
        }
        resetControlsTimeout();
    }

    /**
     * Hides the custom control overlay.
     */
    private void hideControls() {
        customControls.setVisibility(View.GONE);
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
        // # Use controlsTimeoutRunnable
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
     * Updates all time-related UI elements.
     */
    private void updateProgress() {
        if (player == null || player.getDuration() <= 0) {
            return; // # Don't update if player isn't ready
        }

        long durationMs = player.getDuration();
        long currentPositionMs = player.getCurrentPosition();
        long remainingMs = durationMs - currentPositionMs;

        // # Update Progress Bar (max is 1000)
        int progress = (int) ((currentPositionMs * 1000) / durationMs);
        progressBar.setProgress(progress);

        // # Update time text views using the UTC-based duration formatter
        tvCurrentTime.setText(durationFormat.format(new Date(currentPositionMs)));
        tvTotalTime.setText(" / " + durationFormat.format(new Date(durationMs)));
        tvRemainingTime.setText(durationFormat.format(new Date(remainingMs)));
        
        // # Update Finish At time using the system's local time zone
        tvFinishTime.setText(finishTimeFormat.format(new Date(System.currentTimeMillis() + remainingMs)));
        
        // # Update the skip buttons visibility
        updateSkipButtonVisibility();
    }
    
    // =========================================================================
    // KEY EVENT HANDLING (Custom Remote/D-Pad Logic)
    // =========================================================================

    /**
     * Stops the fast-forward/rewind scrubbing action.
     */
    private void stopScrubbing() {
        scrubMultiplier = 0;
        timeUpdateHandler.removeCallbacks(scrubRunnable);
    }

    /**
     * Main handler for all physical key presses (Remote/D-Pad).
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (player == null) return super.dispatchKeyEvent(event);

        // # Show controls on ANY key press if they are hidden
        if (event.getAction() == KeyEvent.ACTION_DOWN && !customControls.isShown()) {
            // # Don't show controls if it's a media key that works while hidden
            // # FIX: Changed MEDIA_PLAY to KEYCODE_MEDIA_PLAY
            if (event.getKeyCode() != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE &&
                event.getKeyCode() != KeyEvent.KEYCODE_MEDIA_PLAY &&
                event.getKeyCode() != KeyEvent.KEYCODE_MEDIA_PAUSE) {
                
                // # Also don't show controls if a skip button is visible
                if (!skipButtonsOverlayContainer.isShown()) {
                    showControls();
                }
            }
        }
        
        // # Reset auto-hide timer (unless a skip button has focus)
        if (!skipButtonsOverlayContainer.hasFocus()) {
             resetControlsTimeout();
        }

        // --- KEY DOWN ACTION HANDLING ---
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                // # Handle Play/Pause
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                // # FIX: Changed MEDIA_PLAY to KEYCODE_MEDIA_PLAY
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    player.play();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    player.pause();
                    return true;
                
                // # Handle D-Pad Center / Enter
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    // # If controls are hidden AND skip buttons are hidden, toggle controls
                    if (!customControls.isShown() && !skipButtonsOverlayContainer.isShown()) {
                        toggleControls();
                        return true;
                    }
                    // # If controls are shown, or skip buttons are shown, let system handle click
                    return super.dispatchKeyEvent(event);

                // # Handle FF/RW
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (event.getRepeatCount() == 0) { // # On first press
                        stopScrubbing();
                        scrubMultiplier = 1;
                        timeUpdateHandler.post(scrubRunnable);
                    }
                    return true;
                    
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (event.getRepeatCount() == 0) { // # On first press
                        stopScrubbing();
                        scrubMultiplier = -1;
                        timeUpdateHandler.post(scrubRunnable);
                    }
                    return true;
            }
        }
        
        // --- KEY UP ACTION HANDLING ---
        if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                // # Stop scrubbing when FF/RW/D-Pad L/R is released
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    stopScrubbing();
                    return true;
            }
        }

        // # Fallback: let the system handle it
        return super.dispatchKeyEvent(event);
    }
}
