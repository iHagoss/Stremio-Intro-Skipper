# Using TV Player as External Video Player

TV Player can be launched as an external video player from streaming apps like Stremio and Syncler on Android TV.

## How It Works

When another app wants to play a video, it can send an `ACTION_VIEW` intent with the video URL. TV Player will:
1. Receive the intent
2. Validate the video URL (must be http:// or https://)
3. Start playback with the provided URL
4. Apply skip functionality if markers are available

## Supported Apps

### Stremio
1. Install TV Player on your Android TV
2. In Stremio, select a movie or episode
3. When playback starts or in settings, choose "External Player"
4. Select "TV Player" from the list
5. Video plays with intro/credits skip functionality

### Syncler
1. Install TV Player on your Android TV
2. In Syncler, navigate to a video
3. Long-press or use menu to select "Play with"
4. Choose "TV Player"
5. Enjoy playback with smart skip features

### Other Apps
Any Android app that supports external video players can use TV Player. The app must:
- Send an `ACTION_VIEW` intent
- Include a valid http:// or https:// video URL
- Use one of these methods:
  - `intent.setData(Uri)` - Preferred method
  - `intent.putExtra(Intent.EXTRA_STREAM, Uri)` - Alternate method

## Supported URL Schemes

- `http://` - Standard HTTP streaming
- `https://` - Secure HTTPS streaming

**Not supported**: file://, content://, rtsp://, or other custom schemes

## Error Handling

If TV Player receives an invalid intent:
- Shows error message: "No valid video URL provided"
- Closes gracefully
- Does not play default video

This ensures you always know when something goes wrong.

## Skip Functionality with External Videos

When launched as external player:
- Auto-skip intro works if API provides markers
- Skip Intro button appears during intro range
- Skip Credits button appears during credits range
- All features work exactly as standalone mode

## Technical Details

### Manifest Configuration
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:mimeType="video/*" />
    <data android:scheme="http" />
    <data android:scheme="https" />
</intent-filter>
```

### Intent Data Priority
1. First checks `intent.getData()`
2. Then checks `Intent.EXTRA_STREAM`
3. If neither valid, shows error and exits

## Troubleshooting

**TV Player doesn't appear in external player list:**
- Ensure app is installed on Android TV
- Check that the streaming app supports external players
- Try restarting the streaming app

**Video won't play:**
- Verify URL is http:// or https://
- Check internet connection
- Ensure video format is supported by ExoPlayer

**Skip buttons don't appear:**
- Check that MARKERS_API_URL is configured in MainActivity.java
- Verify API returns valid JSON with intro/credits timestamps
- API must be accessible from your network

## API Configuration

To enable skip functionality for external videos, configure the API endpoint:

Edit `app/src/main/java/com/tvplayer/app/MainActivity.java`:
```java
private static final String MARKERS_API_URL = "https://your-api.com/skip-markers.json";
```

Your API should return:
```json
{
  "intro": { "start": 0, "end": 90 },
  "credits": { "start": 2500, "end": 2700 }
}
```

All timestamps in seconds.

## Privacy & Security

- No video URLs are logged or stored
- No data sent to external servers (except markers API if configured)
- App only validates URL scheme (http/https)
- Closes immediately if invalid URL detected

## Limitations

- Only supports http/https streaming URLs
- Cannot play local files (file://)
- Cannot access device storage (content://)
- Requires network connection for streaming
- Skip markers API must be configured manually

## Best Practices

1. **Test First**: Try with default video URL before using as external player
2. **Configure API**: Set up markers API for best experience
3. **Network**: Ensure stable connection for smooth playback
4. **Remote**: Keep Android TV remote nearby for skip buttons
