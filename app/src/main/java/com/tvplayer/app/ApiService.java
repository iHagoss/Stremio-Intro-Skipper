package com.tvplayer.app;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ApiService {
    private final OkHttpClient client;
    private final Gson gson;

    public ApiService() {
        this.client = new OkHttpClient();
        this.gson = new Gson();
    }

    public interface SkipMarkersCallback {
        void onSuccess(SkipMarkers markers);
        void onError(Exception e);
    }

    public void fetchSkipMarkers(String url, SkipMarkersCallback callback) {
        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected code " + response);
                    }

                    String jsonData = response.body().string();
                    SkipMarkers markers = gson.fromJson(jsonData, SkipMarkers.class);
                    
                    new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(markers));
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onError(e));
                }
            }
        });
    }
}
