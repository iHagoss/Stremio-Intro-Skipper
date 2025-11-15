package com.tvplayer.app;

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
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.ui.PlayerView;

import com.tvplayer.app.skipdetection.*;
import com.tvplayer.app.skipdetection.strategies.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private View customControls;

    // UI components for time display
    private TextView currentTimeOfDay, elapsedTime, totalTime, remainingTime, finishTime;
    private ProgressBar progressBar;

    // UI components for skip buttons
    private Button btnSkipIntro, btnSkipRecap, btnSkipCredits, btnNextEpisode;

    // UI components for control buttons including playback speed and audio delay
    private ImageButton btnPlayPause, btnSettings, btnAudioDelay, btnSubtitleDelay;

    private TextView titleTextView;  // Display title & episode info here

    // Track selection for resolutions and audio languages
    private DefaultTrackSelector trackSelector;

    // Handlers and Runnables for UI updates
    private Handler updateHandler;
    private Runnable updateRunnable;
    private Handler controlsHandler;
    private Runnable hideControlsRunnable;

    // Playback position and media tracking for resumption and Stremio
    private long lastPositionMs = 0;
    private String currentVideoUrl = null;

    // Skip management
    private PreferencesHelper prefsHelper;
    private SkipMarkers skipMarkers;
    private SmartSkipManager smartSkipManager;
    private List<SkipDetectionStrategy> skipStrategies;

    // Flags for auto skip prevents multiple triggers
    private boolean autoSkippedIntro = false, autoSkippedRecap = false, autoSkippedCredits = false;

    // For trakt syncing
    private TraktSyncManager traktSyncManager;

    // Volume control independent of Android system volume
    private VolumeController volumeController;

    private static final int CONTROLS_TIMEOUT = 5000; // 5 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefsHelper = new PreferencesHelper(this);
        skipMarkers = new SkipMarkers();

        // Initialize track selector with default parameters
        trackSelector = new DefaultTrackSelector(this);
        player = new ExoPlayer.Builder(this).setTrackSelector(trackSelector).build();

        playerView = findViewById(R.id.playerView);
        playerView.setPlayer(player);
        customControls = findViewById(R.id.customControls);

        // Initialize UI components
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

        titleTextView = findViewById(R.id.titleTextView);

        // Initialize custom controllers
        volumeController = new VolumeController(this);
        traktSyncManager = new TraktSyncManager(this, prefsHelper);

        smartSkipManager = new SmartSkipManager(this, prefsHelper);
        skipStrategies = new ArrayList<>();
        // Adding strategies in prioritized order for fallback
        skipStrategies.add(new ChapterStrategy(player));
        skipStrategies.add(new AudioFingerprintStrategy());
        skipStrategies.add(new ManualPreferenceStrategy(prefsHelper));
        // Add other custom skip strategies as needed

        smartSkipManager.setStrategies(skipStrategies);

        setupPlayerListeners();
        setupUIListeners();
        setupUpdateHandlers();

        // Load initial intent
        handleIntent(getIntent());

        // Other initializations
        loadSkipMarkersFromPreferences();
        applyAudioSubtitleDelays();
        volumeController.register();

    }

    // Handle intents from Stremio or other apps
    private void handleIntent(Intent intent) {
        Uri dataUri = intent.getData();
        String videoUrl = null;
        long requestedPosition = 0;
        String incomingTitle = intent.getStringExtra("title");
        String incomingShowName = intent.getStringExtra("showName");
        Integer season = intent.hasExtra("season") ? intent.getIntExtra("season", -1) : null;
        Integer episode = intent.hasExtra("episode") ? intent.getIntExtra("episode", -1) : null;
        String imdbId = intent.getStringExtra("imdbId");
        String posterUrl = intent.getStringExtra("poster"); // For poster passing from Stremio

        if (dataUri != null) {
            videoUrl = dataUri.toString();
        } else if (intent.hasExtra("videoUrl")) {
            videoUrl = intent.getStringExtra("videoUrl");
        }

        if (intent.hasExtra("position")) {
            requestedPosition = intent.getLongExtra("position", 0);
        }

        if (videoUrl != null) {
            MediaItem mediaItem = MediaItem.Builder.fromUri(videoUrl).setTag(new MediaMetadata.Builder()
                    .setTitle(incomingTitle)
                    .setDisplayTitle(incomingTitle)
                    .setArtist(incomingShowName)
                    .setSeasonNumber(season == null ? -1 : season)
                    .setEpisodeNumber(episode == null ? -1 : episode)
                    .setExtras( Bundle.EMPTY)
                    .build())
                    .build();

            player.setMediaItem(mediaItem, requestedPosition);
            player.prepare();
            player.play();

            currentVideoUrl = videoUrl;

            // Update UI for title display
            updateTitleDisplay(incomingTitle, incomingShowName, season, episode);

            // Pass poster info to UI if implemented

            resetAutoSkipFlags();
        }
    }

    private void updateTitleDisplay(String title, String showName, Integer season, Integer episode) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            sb.append(title);
        }
        if (showName != null && !showName.isEmpty()) {
            sb.append(" - ").append(showName);
        }
        if (season != null && season > 0) {
            sb.append(" S").append(season);
        }
        if (episode != null && episode > 0) {
            sb.append("E").append(episode);
        }
        titleTextView.setText(sb.toString());
        titleTextView.setVisibility(View.VISIBLE);
    }

    private void setupPlayerListeners() {
        player.addListener(new Player.Listener() {

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    applyAudioSubtitleDelays();
                    updatePlayPauseButton();
                    loadSkipMarkersFromPreferences();
                    smartSkipManager.attachPlayer(player);
                    attemptSmartSkipDetection();

                    if (lastPositionMs > 0) {
                        player.seekTo(lastPositionMs);
                        lastPositionMs = 0;
                    }
                } else if (state == Player.STATE_ENDED) {
                    if (prefsHelper.isAutoPlayNextEpisode()) {
                        // Implement auto-play next episode logic utilizing Stremio API or intent
                        finish(); // Placeholder: close for now
                    }
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButton();
                if (isPlaying) {
                    traktSyncManager.startSyncing(player, getCurrentMediaMetadata());
                } else {
                    traktSyncManager.stopSyncing();
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Toast.makeText(MainActivity.this, "Playback error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Track selection change listener example
        player.addListener(new Player.Listener() {
            @Override
            public void onTracksChanged(Tracks tracks) {
                // React to available audio languages and video resolutions
                // Populate your UI elements accordingly
            }
        });
    }

    private MediaMetadata getCurrentMediaMetadata() {
        MediaItem item = player.getCurrentMediaItem();
        if (item != null) {
            return item.mediaMetadata;
        }
        return null;
    }

    private void setupUIListeners() {
        btnPlayPause.setOnClickListener(v -> {
            if (player.isPlaying()) player.pause();
            else player.play();
            updatePlayPauseButton();
            resetControlsTimeout();
        });

        btnSkipIntro.setOnClickListener(v -> skipIfValid(skipMarkers.getIntro()));
        btnSkipRecap.setOnClickListener(v -> skipIfValid(skipMarkers.getRecap()));
        btnSkipCredits.setOnClickListener(v -> skipIfValid(skipMarkers.getCredits()));
        btnNextEpisode.setOnClickListener(v -> finish()); // Should trigger next episode play in final

        btnAudioDelay.setOnClickListener(v -> showAudioDelayDialog());
        btnSubtitleDelay.setOnClickListener(v -> showSubtitleDelayDialog());

        // Volume controls for dpad up/down custom mapping
        playerView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    volumeController.increaseVolume();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    volumeController.decreaseVolume();
                    return true;
            }
            return false;
        });

        // Progress bar gesture
        progressBar.setOnTouchListener((v, event) -> {
            if (player.getDuration() <= 0) return false;
            int action = event.getAction();
            float x = event.getX();
            float pct = Math.max(0, Math.min(1, x / progressBar.getWidth()));
            long pos = (long) (player.getDuration() * pct);
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                player.seekTo(pos);
                updateTimeDisplays();
                return true;
            } else if (action == MotionEvent.ACTION_UP) {
                player.seekTo(pos);
                updateTimeDisplays();
                resetControlsTimeout();
                return true;
            }
            return false;
        });

        // Playback speed can be controlled with a custom dialog
        findViewById(R.id.btnPlaybackSpeed).setOnClickListener(v -> showPlaybackSpeedDialog());
    }

    private void skipIfValid(SkipSegment segment) {
        if (segment != null && segment.isValid()) {
            player.seekTo(segment.end * 1000L);
            resetAutoSkipFlags();
            hideSkipButtons();
        }
    }

    private void resetAutoSkipFlags() {
        autoSkippedIntro = autoSkippedRecap = autoSkippedCredits = false;
    }

    private void hideSkipButtons() {
        btnSkipIntro.setVisibility(View.GONE);
        btnSkipRecap.setVisibility(View.GONE);
        btnSkipCredits.setVisibility(View.GONE);
        btnNextEpisode.setVisibility(View.GONE);
    }

    private void attemptSmartSkipDetection() {
        if (player == null || smartSkipManager == null) return;

        MediaMetadata metadata = player.getCurrentMediaItem() != null ? player.getCurrentMediaItem().mediaMetadata : null;
        String title = metadata != null ? metadata.title : null;
        String showName = metadata != null ? metadata.artist : null;
        Integer season = metadata != null && metadata.seasonNumber > 0 ? metadata.seasonNumber : null;
        Integer episode = metadata != null && metadata.episodeNumber > 0 ? metadata.episodeNumber : null;

        smartSkipManager.detectSkipMarkers(new MediaIdentifier.Builder()
            .setTitle(title)
            .setShowName(showName)
            .setSeasonNumber(season)
            .setEpisodeNumber(episode)
            .build(), new SkipDetectionCallback() {

            @Override
            public void onDetectionComplete(SkipDetectionResult result) {
                if (result.isSuccess()) {
                    applyDetectedSkipMarkers(result);
                }
            }

            @Override
            public void onDetectionFailed(String errorMessage) {
                // Log or show error
            }
        });
    }

    private void applyDetectedSkipMarkers(SkipDetectionResult result) {
        skipMarkers.setIntro(result.getIntroStart(), result.getIntroEnd());
        skipMarkers.setRecap(result.getRecapStart(), result.getRecapEnd());
        skipMarkers.setCredits(result.getCreditsStart(), result.getCreditsEnd());
        // Update skip buttons visibility appropriately based on position
    }

    private void updateTimeDisplays() {
        if (player == null) return;
        long posMs = player.getCurrentPosition();
        long durMs = player.getDuration();

        if (durMs <= 0) return;

        float speed = player.getPlaybackParameters().speed;
        long remainingMs = durMs - posMs;
        long realRemaining = (long) (remainingMs / speed);

        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        currentTimeOfDay.setText(timeFormat.format(new Date()));

        elapsedTime.setText(formatTime(posMs / 1000));
        totalTime.setText(formatTime(durMs / 1000));
        remainingTime.setText(formatTime(remainingMs / 1000));

        long finishTimeMs = System.currentTimeMillis() + realRemaining;
        finishTime.setText(timeFormat.format(new Date(finishTimeMs)));

        int progress = (int) ((posMs * 100) / durMs);
        progressBar.setProgress(progress);
    }

    private String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    private void updatePlayPauseButton() {
        if (player != null && player.isPlaying()) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private void resetControlsTimeout() {
        if (controlsHandler != null && hideControlsRunnable != null) {
            controlsHandler.removeCallbacks(hideControlsRunnable);
            controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_TIMEOUT);
        }
    }

    private void applyAudioSubtitleDelays() {
        if (player == null) return;

        int audioDelay = prefsHelper.getAudioDelayMs();
        int subtitleDelay = prefsHelper.getSubtitleDelayMs();
        // Implement your custom logic here to apply audio/subtitle delays
        // Note: Media3 ExoPlayer doesn't provide direct API for adjusting subtitle delay,
        // so likely requires custom subtitle rendering or preprocessing.
    }

    private void showAudioDelayDialog() {
        final String[] options = {"-500ms", "-250ms", "-100ms", "0ms (Reset)", "+100ms", "+250ms", "+500ms"};
        final int[] values = {-500, -250, -100, 0, 100, 250, 500};
        new AlertDialog.Builder(this)
            .setTitle("Audio Delay")
            .setItems(options, (dialog, which) -> {
                prefsHelper.setAudioDelayMs(values[which]);
                applyAudioSubtitleDelays();
                Toast.makeText(this, "Audio delay: " + options[which], Toast.LENGTH_SHORT).show();
            }).show();
        resetControlsTimeout();
    }

    private void showSubtitleDelayDialog() {
        final String[] options = {"-500ms", "-250ms", "-100ms", "0ms (Reset)", "+100ms", "+250ms", "+500ms"};
        final int[] values = {-500, -250, -100, 0, 100, 250, 500};
        new AlertDialog.Builder(this)
            .setTitle("Subtitle Delay")
            .setItems(options, (dialog, which) -> {
                prefsHelper.setSubtitleDelayMs(values[which]);
                applyAudioSubtitleDelays();
                Toast.makeText(this, "Subtitle delay: " + options[which], Toast.LENGTH_SHORT).show();
            }).show();
        resetControlsTimeout();
    }

    private void showPlaybackSpeedDialog() {
        final String[] options = {"0.25x", "0.50x", "0.75x", "1.00x (Normal)", "1.25x", "1.50x", "2.0x"};
        final float[] speeds = {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        new AlertDialog.Builder(this)
            .setTitle("Playback Speed")
            .setItems(options, (dialog, which) -> {
                player.setPlaybackSpeed(speeds[which]);
                updateTimeDisplays();
                Toast.makeText(this, "Speed: " + options[which], Toast.LENGTH_SHORT).show();
            }).show();
        resetControlsTimeout();
    }

    // VolumeController handles volume independent from system
    private class VolumeController {
        private int currentVolume = 5; // Example initial volume level (range 0-10)
        private final Context context;
        private Toast volumeToast;

        VolumeController(Context ctx) {
            context = ctx;
        }

        void increaseVolume() {
            if (currentVolume < 10) currentVolume++;
            showVolumeToast();
            // Custom code to set volume in player if applicable
        }

        void decreaseVolume() {
            if (currentVolume > 0) currentVolume--;
            showVolumeToast();
            // Custom code to set volume in player if applicable
        }

        void showVolumeToast() {
            if (volumeToast != null) volumeToast.cancel();
            volumeToast = Toast.makeText(context, "Volume: " + currentVolume, Toast.LENGTH_SHORT);
            volumeToast.show();
        }

        void register() {
            // Add any receiver or observer if needed
        }

        void unregister() {
            // Cleanup if needed
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            lastPositionMs = player.getCurrentPosition();
            player.setPlayWhenReady(false);
            traktSyncManager.stopSyncing();
        }
        if (updateHandler != null) updateHandler.removeCallbacks(updateRunnable);
        if (controlsHandler != null) controlsHandler.removeCallbacks(hideControlsRunnable);
        volumeController.unregister();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.setPlayWhenReady(true);
            traktSyncManager.startSyncing(player, getCurrentMediaMetadata());
        }
        setupUpdateHandlers();
        volumeController.register();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        traktSyncManager.stopSyncing();
        if (updateHandler != null) updateHandler.removeCallbacks(updateRunnable);
        if (controlsHandler != null) controlsHandler.removeCallbacks(hideControlsRunnable);
        volumeController.unregister();
    }

    private void setupUpdateHandlers() {
        updateHandler = new Handler(Looper.getMainLooper());
        controlsHandler = new Handler(Looper.getMainLooper());

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimeDisplays();
                updateSkipButtons();
                traktSyncManager.syncPlaybackPosition(player);
                updateHandler.postDelayed(this, 10000); // Sync every 10 seconds
            }
        };
        updateHandler.post(updateRunnable);

        hideControlsRunnable = () -> customControls.setVisibility(View.GONE);
        resetControlsTimeout();
    }

    private void updateSkipButtons() {
        if (player == null) return;
        long posSec = player.getCurrentPosition() / 1000;

        // Manage visibility of skip buttons based on position
        btnSkipIntro.setVisibility(skipMarkers.isInIntro(posSec) ? View.VISIBLE : View.GONE);
        btnSkipRecap.setVisibility(skipMarkers.isInRecap(posSec) ? View.VISIBLE : View.GONE);
        btnSkipCredits.setVisibility(skipMarkers.isInCredits(posSec) ? View.VISIBLE : View.GONE);
        btnNextEpisode.setVisibility(skipMarkers.isAtNextEpisode(posSec) ? View.VISIBLE : View.GONE);
    }

    // Override to support D-pad and controller volume controls separately from system
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
                volumeController.increaseVolume();
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                volumeController.decreaseVolume();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
