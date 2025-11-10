package com.tvplayer.app;

// Standard Android framework imports
import android.app.AlertDialog;
import android.content.Intent;
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
 * ... (Description truncated for brevity)
 */
public class MainActivity extends AppCompatActivity {

    // --- Media Player Components (ExoPlayer and View) ---
    private ExoPlayer player;
    private PlayerView playerView; 
    private View customControls; 

    // --- Helper/Model Classes ---
    private PreferencesHelper prefsHelper; 
    private SkipMarkers skipMarkers; 

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
    private Handler updateHandler; 
    private Runnable updateRunnable; 
    private Handler controlsHandler; 
    private Runnable hideControlsRunnable; 

    // --- State Variables ---
    private boolean autoSkippedIntro = false;
    private boolean autoSkippedRecap = false;
    private boolean autoSkippedCredits = false;
    
    // --- For Resumption/Hand-off Fix ---
    private long lastPositionMs = 0; 

    private static final int CONTROLS_TIMEOUT = 5000; // 5 seconds
    
    // --- New Constants for Seek Time ---
    private static final long REWIND_MS = 10000; // 10 seconds for Rewind/Prev
    private static final long FORWARD_MS = 30000; // 30 seconds for Fast Forward/Next
    // --- Constant for D-pad Quick Skip ---
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
        prefsHelper = new PreferencesHelper(this);
        skipMarkers = new SkipMarkers();
        loadSkipMarkersFromPreferences(); 
        
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
        // Save current position if player exists
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
            // Save current position on pause (Crucial for Stremio hand-off)
            lastPositionMs = player.getCurrentPosition();
            player.setPlayWhenReady(false);
        }
        stopScrubbing(); // Stop scrubbing on pause
    }

    // =========================================================================
    // --- Setup Methods ---
    // =========================================================================

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

        playerView.setOnClickListener(v -> toggleControls());
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
                    
                    // --- RESUMPTION FIX: Seek to the last known position ---
                    if (lastPositionMs > 0) {
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
            playVideo(videoUrl);
        } else {
            Toast.makeText(this, R.string.error_no_url, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void playVideo(String url) {
        MediaItem mediaItem = MediaItem.fromUri(url);
        player.setMediaItem(mediaItem);
        player.prepare(); 
        player.setPlayWhenReady(true);
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
            // Only handle key DOWN actions
            if (event.getAction() != KeyEvent.ACTION_DOWN || player == null || player.getDuration() <= 0) {
                return false;
            }
            
            long currentPosMs = player.getCurrentPosition();
            long newPositionMs = currentPosMs;
            int seekStepMs = 15000; // 15 seconds seek jump for D-pad
            
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
            
            // Consume the event so it doesn't try to move focus
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
        
        // Used REWIND_MS
        findViewById(R.id.btnRewind).setOnClickListener(v -> { 
            player.seekTo(Math.max(0, player.getCurrentPosition() - REWIND_MS));
            resetControlsTimeout(); 
        });
        
        // Used FORWARD_MS
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
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
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
    
    private void updateSkipButtons() {
        if (player == null) return;
        long currentPosSec = player.getCurrentPosition() / 1000;

        btnSkipIntro.setVisibility(skipMarkers.isInIntro(currentPosSec) ? View.VISIBLE : View.GONE);
        btnSkipRecap.setVisibility(skipMarkers.isInRecap(currentPosSec) ? View.VISIBLE : View.GONE);
        btnSkipCredits.setVisibility(skipMarkers.isInCredits(currentPosSec) ? View.VISIBLE : View.GONE);
        btnNextEpisode.setVisibility(skipMarkers.isAtNextEpisode(currentPosSec) ? View.VISIBLE : View.GONE);
    }

    private void checkAutoSkip() {
        if (player == null || !player.isPlaying()) return;
        long currentPosSec = player.getCurrentPosition() / 1000;

        if (prefsHelper.isAutoSkipIntro() && !autoSkippedIntro && skipMarkers.isInIntro(currentPosSec)) {
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

    private void updatePlayPauseButton() {
        if (player != null && player.isPlaying()) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    /**
     * FIX: Toggles the visibility of the custom control overlay and *requests* focus 
     * to the play/pause button, ensuring the request is posted to guarantee UI availability.
     */
    private void toggleControls() {
        if (customControls.getVisibility() == View.VISIBLE) {
            customControls.setVisibility(View.GONE);
            controlsHandler.removeCallbacks(hideControlsRunnable);
        } else {
            customControls.setVisibility(View.VISIBLE);
            resetControlsTimeout();
            
            // --- FIX: Post the request focus to guarantee controls are drawn and focusable ---
            // Request focus on the custom control root first, then the preferred button
            customControls.requestFocus();
            
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
                .setTitle(R.string.audio_delay)
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
                .setTitle(R.string.subtitle_delay)
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
                .setTitle(R.string.playback_speed)
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
        
        // 1. Pause the video and set the state
        if (player.isPlaying()) {
            player.pause();
            updatePlayPauseButton();
        }
        
        isScrubbingForward = forward;
        
        // Stop any existing runnable
        if (scrubRunnable != null) {
            scrubHandler.removeCallbacks(scrubRunnable);
        }
        
        // Base step is 150ms per tick, multiplied by the current speed (1x, 2x, 3x)
        final long baseStepMs = 150;
        final long seekAmountMs = (long) (baseStepMs * scrubMultiplier * (forward ? 1 : -1));

        // 2. Define the continuous seeking loop
        scrubRunnable = new Runnable() {
            @Override
            public void run() {
                if (player == null || scrubMultiplier == 0) return;
                
                // Execute the seek
                player.seekTo(player.getCurrentPosition() + seekAmountMs);
                
                // Show the controls and update time displays
                customControls.setVisibility(View.VISIBLE);
                updateTimeDisplays(); 
                
                // Loop every 100ms
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
        // Crucial: Reset multiplier when the scrub ends
        scrubMultiplier = 0; 
        resetControlsTimeout();
        // Toast.makeText(this, "Scrubbing stopped", Toast.LENGTH_SHORT).show(); // Removed for less clutter
    }
    
    // =========================================================================
    // --- Overridden Lifecycle Methods ---
    // =========================================================================

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        
        // --- RESUMPTION FIX: If we get a new intent, save the current position
        // before re-initializing to ensure the new player instance resumes.
        if (player != null) {
            lastPositionMs = player.getCurrentPosition();
            player.release();
            player = null; // Ensure the old player is released
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


    /**
     * FIX: Handles Play/Pause and Scrubbing logic explicitly.
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
                    // FIX: If controls are visible, let the view handle the click/focus.
                    if (customControls.getVisibility() == View.VISIBLE && customControls.findFocus() != null) {
                        break; 
                    }
                    // If controls are hidden, use Center/OK for Play/Pause toggle.
                    if (player.isPlaying()) {
                        player.pause();
                    } else {
                        player.play();
                    }
                    updatePlayPauseButton();
                    toggleControls(); // Show/Reset controls
                    return true; // Consume the event

                // --- D-pad Quick Skip (Step Seeking) ---
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (customControls.getVisibility() == View.VISIBLE && customControls.findFocus() != null) {
                        // Allow focused item (like ProgressBar) to handle D-pad events
                        break;
                    }
                    // Handle quick seek outside of controls
                    long seekAmount = (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) ? -DPAD_QUICK_SEEK_MS : DPAD_QUICK_SEEK_MS;
                    if (scrubMultiplier > 0) stopScrubbing(); 
                    player.seekTo(Math.max(0, Math.min(player.getDuration(), currentPosMs + seekAmount))); 
                    toggleControls();
                    return true;

                // --- Scrubbing Start/Increase ---
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    boolean isForward = (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
                    
                    if (scrubMultiplier == 0) {
                        // Initial Press: Start at x1
                        scrubMultiplier = 1;
                    } else if (event.getRepeatCount() > 0) {
                        // FIX: Subsequent hold: Increase speed (max x3)
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
                        // Quick tap in the same direction, already scrubbing: Do nothing to avoid flicker
                        return true;
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
                        // Fall through to play/pause toggle below
                    }
                    // General Play/Pause Toggle
                    if (player.isPlaying()) {
                        player.pause();
                    } else {
                        player.play();
                    }
                    updatePlayPauseButton();
                    toggleControls(); 
                    return true;
            }

        // --- KEY UP ACTION HANDLING (Used to stop scrubbing on media key release) ---
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    // Stop scrubbing when the button is released
                    stopScrubbing();
                    return true;
                
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    // Prevents accidental double-toggle if the view consumed the ACTION_DOWN
                    // If controls are visible, let the system handle the ACTION_UP
                    if (customControls.getVisibility() == View.VISIBLE) {
                        break;
                    }
                    return true; // Consume the event if controls are hidden, as the DOWN did the work.
            }
        }

        // Fallback: Delegate unhandled keys to PlayerView's internal controller logic 
        if (playerView != null && playerView.dispatchKeyEvent(event)) {
            // Show controls if the PlayerView processes a key (e.g., UP/DOWN for controls navigation)
            customControls.setVisibility(View.VISIBLE);
            resetControlsTimeout();
            return true;
        }

        // Final fallback to default system behavior
        return super.dispatchKeyEvent(event);
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
}
