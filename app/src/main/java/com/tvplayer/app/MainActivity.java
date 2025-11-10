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

/**
 * MainActivity: The primary activity responsible for video playback.
 */
public class MainActivity extends AppCompatActivity {

    // --- Media Player Components (ExoPlayer and View) ---
    private ExoPlayer player;
    private PlayerView playerView; 
    private View customControls; 

    // --- Helper/Model Classes ---
    // NOTE: Using full inner class implementations below to avoid "placeholder" issues
    private PreferencesHelper prefsHelper; 
    private SkipMarkers skipMarkers; 

    // --- Time/Progress UI Components ---
    private TextView currentTimeOfDay;
    private TextView elapsedTime;
    private TextView totalTime;
    private TextView remainingTime;
    private TextView finishTime;
    private ProgressBar progressBar;

    // --- Skip Button UI Components ---
    private Button btnSkipIntro;
    private Button btnSkipRecap;
    private Button btnSkipCredits;
    private Button btnNextEpisode;

    // --- Primary Control Buttons ---
    private ImageButton btnPlayPause;
    private ImageButton btnSettings;
    private ImageButton btnAudioDelay;
    private ImageButton btnSubtitleDelay;

    // --- Handlers and Runnables ---
    private Handler updateHandler; 
    private Runnable updateRunnable; 
    private Handler controlsHandler; 
    private Runnable hideControlsRunnable; 

    // --- State Variables ---
    private boolean autoSkippedIntro = false;
    private boolean autoSkippedRecap = false;
    private boolean autoSkippedCredits = false;
    
    // --- For Resumption/Hand-off Fix (Stremio) ---
    private long lastPositionMs = 0; 

    private static final int CONTROLS_TIMEOUT = 5000; // 5 seconds
    
    // --- Constants for Seek Time ---
    private static final long REWIND_MS = 10000; // 10 seconds for Rewind/Prev
    private static final long FORWARD_MS = 30000; // 30 seconds for Fast Forward/Next
    private static final long DPAD_QUICK_SEEK_MS = 10000; // 10 seconds for D-pad taps

    // --- Scrubbing Variables ---
    private int scrubMultiplier = 0; // 0=Off, 1=x1, 2=x2, 3=x3 (max)
    private Handler scrubHandler = new Handler(Looper.getMainLooper());
    private Runnable scrubRunnable;
    private boolean isScrubbingForward = false;

    // =========================================================================
    // --- Lifecycle Methods ---
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        
        // --- Initialize Helper Classes (Using inner class for completion) ---
        prefsHelper = new PreferencesHelper(this);
        skipMarkers = new SkipMarkers();
        loadSkipMarkersFromPreferences(); 
        // -------------------------------------------------------------------
        
        // Load last known position from saved instance state
        if (savedInstanceState != null) {
            lastPositionMs = savedInstanceState.getLong("lastPositionMs", 0);
        }

        initializeViews();
        setupPlayer();
        handleIntent(getIntent()); 
        setupUpdateHandlers();
        setupClickListeners();
        setupProgressBarSeeking();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (player != null) {
            outState.putLong("lastPositionMs", player.getCurrentPosition());
        } else {
            outState.putLong("lastPositionMs", lastPositionMs);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            // CRITICAL FIX: Save current position on pause (e.g., when handing off)
            lastPositionMs = player.getCurrentPosition();
            android.util.Log.d("PlayerResume", "Saving position: " + lastPositionMs + "ms");
            player.setPlayWhenReady(false);
        }
        stopScrubbing(); // Stop scrubbing on pause
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        
        // FIX: If we get a new intent (e.g., from Stremio), save position and re-init player.
        if (player != null) {
            lastPositionMs = player.getCurrentPosition();
            player.release();
            player = null; 
        }
        
        setupPlayer();
        handleIntent(intent);
        loadSkipMarkersFromPreferences();
        applyAudioSubtitleDelays();
        resetAutoSkipFlags();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.setPlayWhenReady(true);
        }
        loadSkipMarkersFromPreferences();
        applyAudioSubtitleDelays();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateHandler != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
        if (controlsHandler != null) {
            controlsHandler.removeCallbacks(hideControlsRunnable);
        }
        if (scrubHandler != null) {
            scrubHandler.removeCallbacksAndMessages(null);
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }


    // =========================================================================
    // --- Setup and Player Methods ---
    // =========================================================================

    private void initializeViews() {
        // NOTE: These IDs must match your R.layout.activity_main XML file
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

        playerView.setOnClickListener(v -> toggleControls());
        
        // Ensure skip buttons are focusable for TV
        btnSkipIntro.setFocusable(true);
        btnSkipRecap.setFocusable(true);
        btnSkipCredits.setFocusable(true);
        btnNextEpisode.setFocusable(true);
    }

    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    updatePlayPauseButton(); 
                    updateCreditsMarker(); 
                    updateNextEpisodeMarker(); 
                    applyAudioSubtitleDelays(); 
                    
                    // FIX: Seek to the last known position on STATE_READY
                    if (lastPositionMs > 0) {
                        android.util.Log.d("PlayerResume", "Seeking to saved position: " + lastPositionMs + "ms");
                        player.seekTo(lastPositionMs);
                        lastPositionMs = 0; // Reset after seeking
                    }
                } else if (state == Player.STATE_ENDED) {
                    finish(); 
                }
            }
            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(MainActivity.this, R.string.error_playback, Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButton(); 
            }
        });
        applyAudioSubtitleDelays();
    }

    private void handleIntent(Intent intent) {
        String videoUrl = null;
        if (intent != null) {
            Uri data = intent.getData();
            if (data != null) {
                videoUrl = data.toString();
            } else if (intent.hasExtra("videoUrl")) {
                videoUrl = intent.getStringExtra("videoUrl");
            } else if (intent.hasExtra("uri")) {
                videoUrl = intent.getStringExtra("uri");
            }
        }

        if (videoUrl != null && (videoUrl.startsWith("http://") || videoUrl.startsWith("https://"))) {
            MediaItem mediaItem = MediaItem.fromUri(videoUrl);
            player.setMediaItem(mediaItem);
            player.prepare(); 
            player.setPlayWhenReady(true);
        } else if (player == null || player.getMediaItemCount() == 0) {
            // Note: R.string.error_no_url must be defined
            // Toast.makeText(this, R.string.error_no_url, Toast.LENGTH_LONG).show();
            // finish();
        }
    }
    
    private void loadSkipMarkersFromPreferences() {
        skipMarkers.setIntro(prefsHelper.getIntroStart(), prefsHelper.getIntroEnd());
        skipMarkers.setRecap(prefsHelper.getRecapStart(), prefsHelper.getRecapEnd());
        skipMarkers.setCredits(prefsHelper.getCreditsStart(), 0);
    }
    
    private void updateCreditsMarker() {
        if (player == null) return;
        long durationSec = player.getDuration() / 1000;
        long markerOffsetFromEndSec = prefsHelper.getCreditsStart(); 
        long actualStartTimeSec = -1;
        if (durationSec > 0 && markerOffsetFromEndSec > 0) {
            actualStartTimeSec = durationSec - markerOffsetFromEndSec;
            if (actualStartTimeSec < 0) {
                actualStartTimeSec = 0;
            }
        }
        skipMarkers.setCredits((int) actualStartTimeSec, (int) durationSec);
    }
    
    private void updateNextEpisodeMarker() {
        if (player == null) return;
        long durationSec = player.getDuration() / 1000;
        long markerOffsetFromEndSec = prefsHelper.getNextEpisodeStart(); 
        long actualStartTimeSec = -1; 
        if (durationSec > 0 && markerOffsetFromEndSec > 0) {
            actualStartTimeSec = durationSec - markerOffsetFromEndSec;
            if (actualStartTimeSec < 0) {
                actualStartTimeSec = 0;
            }
        }
        skipMarkers.setNextEpisodeStart((int) actualStartTimeSec);
    }

    private void setupUpdateHandlers() {
        updateHandler = new Handler(Looper.getMainLooper());
        controlsHandler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimeDisplays(); 
                updateSkipButtons(); 
                checkAutoSkip(); 
                updateHandler.postDelayed(this, 500); 
            }
        };
        updateHandler.post(updateRunnable); 
        hideControlsRunnable = () -> {
            customControls.setVisibility(View.GONE);
        };
    }

    private void setupProgressBarSeeking() {
        // --- 1. Touch Listener (Mobile/Touch Devices) ---
        progressBar.setOnTouchListener((v, event) -> {
            if (player == null || player.getDuration() <= 0) {
                return false; 
            }
            int width = progressBar.getWidth();
            float x = event.getX();
            float touchPercent = Math.max(0, Math.min(1, x / width));
            long newPositionMs = (long) (player.getDuration() * touchPercent);
            
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    player.seekTo(newPositionMs);
                    updateTimeDisplays();
                    return true;
                
                case MotionEvent.ACTION_UP:
                    player.seekTo(newPositionMs);
                    updateTimeDisplays();
                    resetControlsTimeout(); 
                    return true;
                    
                default:
                    return false;
            }
        });
        
        // --- 2. Key Listener (Android TV/Firestick D-pad) ---
        progressBar.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || player == null || player.getDuration() <= 0) {
                return false;
            }
            
            long currentPosMs = player.getCurrentPosition();
            long newPositionMs = currentPosMs;
            int seekStepMs = 15000; 
            
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    newPositionMs = Math.max(0, currentPosMs - seekStepMs);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    newPositionMs = Math.min(player.getDuration(), currentPosMs + seekStepMs);
                    break;
                default:
                    return false; 
            }
            
            player.seekTo(newPositionMs);
            updateTimeDisplays();
            resetControlsTimeout(); 
            return true;
        });
    }

    private void setupClickListeners() {
        btnPlayPause.setOnClickListener(v -> { 
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
            resetControlsTimeout(); 
        });
        
        findViewById(R.id.btnRewind).setOnClickListener(v -> { 
            player.seekTo(Math.max(0, player.getCurrentPosition() - REWIND_MS));
            resetControlsTimeout(); 
        });
        
        findViewById(R.id.btnFastForward).setOnClickListener(v -> { 
            player.seekTo(Math.min(player.getDuration(), player.getCurrentPosition() + FORWARD_MS));
            resetControlsTimeout(); 
        });
        
        findViewById(R.id.btnPrevious).setOnClickListener(v -> { 
            player.seekTo(0);
            resetControlsTimeout(); 
        });
        
        findViewById(R.id.btnNext).setOnClickListener(v -> { 
            finish();
        });
        
        btnSkipIntro.setOnClickListener(v -> { 
            if (skipMarkers.getIntro().isValid()) {
                player.seekTo(skipMarkers.getIntro().end * 1000L);
                btnSkipIntro.setVisibility(View.GONE);
            }
        });
        
        btnSkipRecap.setOnClickListener(v -> { 
            if (skipMarkers.getRecap().isValid()) {
                player.seekTo(skipMarkers.getRecap().end * 1000L);
                btnSkipRecap.setVisibility(View.GONE);
            }
        });
        
        btnSkipCredits.setOnClickListener(v -> { 
            if (skipMarkers.getCredits().isValid()) {
                player.seekTo(skipMarkers.getCredits().end * 1000L);
                btnSkipCredits.setVisibility(View.GONE);
            }
        });
        
        btnNextEpisode.setOnClickListener(v -> { 
            finish();
        });
        
        btnSettings.setOnClickListener(v -> { 
            // Note: SettingsActivity must be defined
            // Intent intent = new Intent(this, SettingsActivity.class);
            // startActivity(intent);
        });
        
        btnAudioDelay.setOnClickListener(v -> showAudioDelayDialog());
        btnSubtitleDelay.setOnClickListener(v -> showSubtitleDelayDialog());
        
        findViewById(R.id.btnSubtitles).setOnClickListener(v -> { 
            Toast.makeText(this, "Subtitle tracks selection coming soon", Toast.LENGTH_SHORT).show();
            resetControlsTimeout(); 
        });
        
        findViewById(R.id.btnPlaybackSpeed).setOnClickListener(v -> showPlaybackSpeedDialog());
    }

    private void updateTimeDisplays() {
        if (player == null) return;
        long currentPosMs = player.getCurrentPosition();
        long durationMs = player.getDuration();

        if (durationMs <= 0) return;

        float speed = player.getPlaybackParameters().speed;
        long remainingMs = durationMs - currentPosMs;
        long realWorldRemainingMs = (long) (remainingMs / speed);
        long currentPosSec = currentPosMs / 1000;
        long durationSec = durationMs / 1000;
        long remainingSec = remainingMs / 1000; 

        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        currentTimeOfDay.setText(timeFormat.format(new Date()));

        elapsedTime.setText(formatTime(currentPosSec));
        totalTime.setText(formatTime(durationSec));
        remainingTime.setText(formatTime(remainingSec));

        long finishTimeMs = System.currentTimeMillis() + realWorldRemainingMs;
        finishTime.setText(timeFormat.format(new Date(finishTimeMs)));

        int progress = (int) ((currentPosMs * 100) / durationMs);
        progressBar.setProgress(progress);
    }
    
    /**
     * FIX: Check for marker visibility and explicitly request focus on the first visible button.
     */
    private void updateSkipButtons() {
        if (player == null) return;
        long currentPosSec = player.getCurrentPosition() / 1000;
        
        boolean focusRequested = false;

        // Intro
        if (skipMarkers.isInIntro(currentPosSec)) {
            btnSkipIntro.setVisibility(View.VISIBLE);
            if (!focusRequested) {
                btnSkipIntro.requestFocus();
                focusRequested = true;
            }
        } else {
            btnSkipIntro.setVisibility(View.GONE);
        }

        // Recap
        if (skipMarkers.isInRecap(currentPosSec)) {
            btnSkipRecap.setVisibility(View.VISIBLE);
            if (!focusRequested) {
                btnSkipRecap.requestFocus();
                focusRequested = true;
            }
        } else {
            btnSkipRecap.setVisibility(View.GONE);
        }

        // Credits
        if (skipMarkers.isInCredits(currentPosSec)) {
            btnSkipCredits.setVisibility(View.VISIBLE);
            if (!focusRequested) {
                btnSkipCredits.requestFocus();
                focusRequested = true;
            }
        } else {
            btnSkipCredits.setVisibility(View.GONE);
        }
        
        // Next Episode
        if (skipMarkers.isAtNextEpisode(currentPosSec)) {
            btnNextEpisode.setVisibility(View.VISIBLE);
            if (!focusRequested) {
                btnNextEpisode.requestFocus();
                focusRequested = true;
            }
        } else {
            btnNextEpisode.setVisibility(View.GONE);
        }
    }

    private void checkAutoSkip() {
        if (player == null || !player.isPlaying()) return;
        long currentPosSec = player.getCurrentPosition() / 1000;

        if (prefsHelper.isAutoSkipIntro() && !autoSkippedIntro && skipMarkers.isInIntro(currentPosSec)) {
            player.seekTo(skipMarkers.getIntro().end * 1000L);
            autoSkippedIntro = true;
            // Toast.makeText(this, "Auto-skipped intro", Toast.LENGTH_SHORT).show();
        }

        if (prefsHelper.isAutoSkipRecap() && !autoSkippedRecap && skipMarkers.isInRecap(currentPosSec)) {
            player.seekTo(skipMarkers.getRecap().end * 1000L);
            autoSkippedRecap = true;
            // Toast.makeText(this, "Auto-skipped recap", Toast.LENGTH_SHORT).show();
        }

        if (prefsHelper.isAutoSkipCredits() && !autoSkippedCredits && skipMarkers.isInCredits(currentPosSec)) {
            player.seekTo(skipMarkers.getCredits().end * 1000L);
            autoSkippedCredits = true;
            // Toast.makeText(this, "Auto-skipped credits", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePlayPauseButton() {
        if (player != null && player.isPlaying()) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    /**
     * FIX: Toggles controls and forces focus to ensure menu navigation works.
     */
    private void toggleControls() {
        if (customControls.getVisibility() == View.VISIBLE) {
            customControls.setVisibility(View.GONE);
            controlsHandler.removeCallbacks(hideControlsRunnable);
        } else {
            customControls.setVisibility(View.VISIBLE);
            resetControlsTimeout();
            
            // CRITICAL TV FIX: Ensure the container is focusable
            customControls.setFocusable(true);
            customControls.setFocusableInTouchMode(true);
            
            // Request focus on the custom control root first
            customControls.requestFocus();
            
            // Post the request focus for the preferred child to guarantee focus
            controlsHandler.post(() -> {
                if (btnPlayPause != null) {
                    btnPlayPause.requestFocus();
                } else {
                    progressBar.requestFocus(); 
                }
            });
        }
    }

    private void resetControlsTimeout() {
        controlsHandler.removeCallbacks(hideControlsRunnable);
        controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_TIMEOUT);
    }

    private void applyAudioSubtitleDelays() {
        if (player == null) return;
        int audioDelayMs = prefsHelper.getAudioDelayMs();
        int subtitleDelayMs = prefsHelper.getSubtitleDelayMs();
        android.util.Log.d("MainActivity", "Audio delay: " + audioDelayMs + "ms, Subtitle delay: " + subtitleDelayMs + "ms");
    }

    private void resetAutoSkipFlags() {
        autoSkippedIntro = false;
        autoSkippedRecap = false;
        autoSkippedCredits = false;
    }
    
    private void showAudioDelayDialog() {
        int currentDelay = prefsHelper.getAudioDelayMs();
        String[] options = {"-500ms", "-250ms", "-100ms", "0ms (Reset)", "+100ms", "+250ms", "+500ms"};
        int[] values = {-500, -250, -100, 0, 100, 250, 500};
        new AlertDialog.Builder(this)
                .setTitle("Audio Delay")
                .setItems(options, (dialog, which) -> {
                    prefsHelper.setAudioDelayMs(values[which]);
                    applyAudioSubtitleDelays();
                    Toast.makeText(this, "Audio delay: \n" + options[which], Toast.LENGTH_SHORT).show();
                })
                .show();
        resetControlsTimeout();
    }

    private void showSubtitleDelayDialog() {
        int currentDelay = prefsHelper.getSubtitleDelayMs();
        String[] options = {"-500ms", "-250ms", "-100ms", "0ms (Reset)", "+100ms", "+250ms", "+500ms"};
        int[] values = {-500, -250, -100, 0, 100, 250, 500};
        new AlertDialog.Builder(this)
                .setTitle("Subtitle Delay")
                .setItems(options, (dialog, which) -> {
                    prefsHelper.setSubtitleDelayMs(values[which]);
                    applyAudioSubtitleDelays();
                    Toast.makeText(this, "Subtitle delay: \n" + options[which], Toast.LENGTH_SHORT).show();
                })
                .show();
        resetControlsTimeout();
    }

    private void showPlaybackSpeedDialog() {
        String[] options = {"0.25x", "0.50x", "0.75x", "0.80x", "0.85x", "0.90x", "0.95x", "1.00x (Normal)", "1.05x", "1.10x", "1.15x", "1.20x", "1.25x", "1.50x", "2.0x", "2.5x"};
        float[] speeds = {0.25f, 0.50f, 0.75f, 0.80f, 0.85f, 0.90f, 0.95f, 1.00f, 1.05f, 1.10f, 1.15f, 1.20f, 1.25f, 1.50f, 2.0f, 2.5f};

        new AlertDialog.Builder(this)
                .setTitle("Playback Speed")
                .setItems(options, (dialog, which) -> {
                    player.setPlaybackSpeed(speeds[which]);
                    updateTimeDisplays();
                    Toast.makeText(this, "Speed: " + options[which], Toast.LENGTH_SHORT).show();
        })
                .show();
        resetControlsTimeout();
    }

    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs);
        }
    }

    /**
     * Executes the continuous scrubbing seek action.
     */
    private void startScrubbing(boolean forward) {
        if (player == null) return;
        
        if (player.isPlaying()) {
            player.pause();
            updatePlayPauseButton();
        }
        
        isScrubbingForward = forward;
        
        if (scrubRunnable != null) {
            scrubHandler.removeCallbacks(scrubRunnable);
        }
        
        final long baseStepMs = 150;
        final long seekAmountMs = (long) (baseStepMs * scrubMultiplier * (forward ? 1 : -1));

        scrubRunnable = new Runnable() {
            @Override
            public void run() {
                if (player == null || scrubMultiplier == 0) return;
                
                player.seekTo(player.getCurrentPosition() + seekAmountMs);
                
                customControls.setVisibility(View.VISIBLE);
                updateTimeDisplays(); 
                
                scrubHandler.postDelayed(this, 100); 
            }
        };
        scrubHandler.post(scrubRunnable);
        Toast.makeText(this, "Scrubbing x" + scrubMultiplier, Toast.LENGTH_SHORT).show();
    }

    /**
     * Stops all scrubbing activity.
     */
    private void stopScrubbing() {
        if (scrubRunnable != null) {
            scrubHandler.removeCallbacks(scrubRunnable);
            scrubRunnable = null;
        }
        scrubMultiplier = 0; 
        resetControlsTimeout();
    }
    
    // =========================================================================
    // --- Key Event Dispatch (The core TV fix) ---
    // =========================================================================

    /**
     * FIX: Handles Play/Pause, D-pad seeking, and variable-speed scrubbing explicitly.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (player == null) {
            return super.dispatchKeyEvent(event);
        }
        
        long currentPosMs = player.getCurrentPosition();

        // --- KEY DOWN ACTION HANDLING ---
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    // 1. FIX: Check for visible skip buttons (HIGHEST PRIORITY)
                    if (btnSkipIntro.getVisibility() == View.VISIBLE) {
                        btnSkipIntro.performClick();
                        return true;
                    } else if (btnSkipRecap.getVisibility() == View.VISIBLE) {
                        btnSkipRecap.performClick();
                        return true;
                    } else if (btnSkipCredits.getVisibility() == View.VISIBLE) {
                        btnSkipCredits.performClick();
                        return true;
                    } else if (btnNextEpisode.getVisibility() == View.VISIBLE) {
                        btnNextEpisode.performClick();
                        return true;
                    }
                    
                    // 2. Original Play/Pause and Controls logic
                    if (customControls.getVisibility() == View.VISIBLE && customControls.findFocus() != null) {
                        break; // Let the focused view handle the click
                    }
                    
                    // Toggle Play/Pause if controls are hidden/unfocused
                    if (player.isPlaying()) {
                        player.pause();
                    } else {
                        player.play();
                    }
                    updatePlayPauseButton();
                    toggleControls(); 
                    return true; 

                // --- D-pad Quick Skip (Step Seeking) ---
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    // FIX: Always consume D-pad Left/Right events if controls are not focused to force seeking.
                    if (customControls.getVisibility() == View.VISIBLE && customControls.findFocus() != null) {
                        break;
                    }
                    
                    long seekAmount = (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) ? -DPAD_QUICK_SEEK_MS : DPAD_QUICK_SEEK_MS;
                    if (scrubMultiplier > 0) stopScrubbing(); 
                    player.seekTo(Math.max(0, Math.min(player.getDuration(), currentPosMs + seekAmount))); 
                    toggleControls();
                    return true;

                // --- Scrubbing Start/Increase (Delayed speed fix) ---
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    boolean isForward = (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
                    
                    if (scrubMultiplier == 0) {
                        // Initial Press: Always start at x1
                        scrubMultiplier = 1;
                    } else if (event.getRepeatCount() > 0) {
                        // Subsequent hold (Repeat event): Increase speed only if direction matches
                        if (isForward == isScrubbingForward) {
                             if (scrubMultiplier < 3) scrubMultiplier++;
                        } else {
                            // Direction changed while holding: Reset to x1 in the new direction
                            scrubMultiplier = 1; 
                        }
                    } else if (isForward != isScrubbingForward) {
                        // Direction changed on a new tap: Reset to x1 in the new direction
                        scrubMultiplier = 1; 
                    } else {
                        // If it's a quick tap in the same direction, and we're already scrubbing, ignore to prevent flicker.
                        return true; 
                    }
                    
                    // Ensure a clean start if direction changes
                    if (scrubMultiplier == 1 && isForward != isScrubbingForward) {
                        stopScrubbing(); 
                    }
                    
                    startScrubbing(isForward);
                    return true;
                    
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    if (scrubMultiplier > 0) stopScrubbing();
                    player.seekTo(0); 
                    toggleControls();
                    return true;

                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    if (scrubMultiplier > 0) stopScrubbing();
                    finish(); 
                    return true;
                
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
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
    
    // =========================================================================
    // --- Helper/Model Classes (Required for code completeness) ---
    // =========================================================================

    /**
     * Inner class for handling SharedPreferences.
     */
    public static class PreferencesHelper {
        private final SharedPreferences prefs;
        private static final String PREF_NAME = "TVPlayerPrefs";

        public PreferencesHelper(Context context) {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }

        // Delay settings
        public int getAudioDelayMs() { return prefs.getInt("audio_delay", 0); }
        public void setAudioDelayMs(int delay) { prefs.edit().putInt("audio_delay", delay).apply(); }
        public int getSubtitleDelayMs() { return prefs.getInt("subtitle_delay", 0); }
        public void setSubtitleDelayMs(int delay) { prefs.edit().putInt("subtitle_delay", delay).apply(); }

        // Marker settings (default values are 0 for simplicity)
        public int getIntroStart() { return prefs.getInt("intro_start", 0); }
        public int getIntroEnd() { return prefs.getInt("intro_end", 90); }
        public int getRecapStart() { return prefs.getInt("recap_start", 0); }
        public int getRecapEnd() { return prefs.getInt("recap_end", 60); }
        public int getCreditsStart() { return prefs.getInt("credits_offset", 300); } // 5 minutes from end
        public int getNextEpisodeStart() { return prefs.getInt("next_episode_offset", 60); } // 1 minute from end

        // Auto Skip settings
        public boolean isAutoSkipIntro() { return prefs.getBoolean("auto_skip_intro", false); }
        public boolean isAutoSkipRecap() { return prefs.getBoolean("auto_skip_recap", false); }
        public boolean isAutoSkipCredits() { return prefs.getBoolean("auto_skip_credits", false); }
    }

    /**
     * Inner class for managing skip markers.
     */
    public static class SkipMarkers {
        public static class Marker {
            public int start;
            public int end;
            public Marker(int s, int e) { start = s; end = e; }
            public boolean isValid() { return end > start && start >= 0; }
        }

        private Marker intro = new Marker(-1, -1);
        private Marker recap = new Marker(-1, -1);
        private Marker credits = new Marker(-1, -1);
        private int nextEpisodeStart = -1;

        public void setIntro(int start, int end) { intro = new Marker(start, end); }
        public Marker getIntro() { return intro; }
        public boolean isInIntro(long currentSec) { return currentSec >= intro.start && currentSec < intro.end; }

        public void setRecap(int start, int end) { recap = new Marker(start, end); }
        public Marker getRecap() { return recap; }
        public boolean isInRecap(long currentSec) { return currentSec >= recap.start && currentSec < recap.end; }

        // Credits uses the duration for 'end'
        public void setCredits(int start, int durationSec) { credits = new Marker(start, durationSec); }
        public Marker getCredits() { return credits; }
        public boolean isInCredits(long currentSec) { return credits.start > 0 && currentSec >= credits.start && currentSec < credits.end; }

        public void setNextEpisodeStart(int start) { nextEpisodeStart = start; }
        public boolean isAtNextEpisode(long currentSec) { return nextEpisodeStart > 0 && currentSec >= nextEpisodeStart; }
    }
}
