package com.tvplayer.app.skipdetection;

public class MediaIdentifier {
    private final String title;
    private final String showName;
    private final Integer seasonNumber;
    private final Integer episodeNumber;
    private final long runtimeSeconds;
    private final String imdbId;
    private final String tmdbId;
    private final String traktId;
    private final String tvdbId;
    
    private MediaIdentifier(Builder builder) {
        this.title = builder.title;
        this.showName = builder.showName;
        this.seasonNumber = builder.seasonNumber;
        this.episodeNumber = builder.episodeNumber;
        this.runtimeSeconds = builder.runtimeSeconds;
        this.imdbId = builder.imdbId;
        this.tmdbId = builder.tmdbId;
        this.traktId = builder.traktId;
        this.tvdbId = builder.tvdbId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getShowName() {
        return showName;
    }
    
    public Integer getSeasonNumber() {
        return seasonNumber;
    }
    
    public Integer getEpisodeNumber() {
        return episodeNumber;
    }
    
    public long getRuntimeSeconds() {
        return runtimeSeconds;
    }
    
    public String getImdbId() {
        return imdbId;
    }
    
    public String getTmdbId() {
        return tmdbId;
    }
    
    public String getTraktId() {
        return traktId;
    }
    
    public String getTvdbId() {
        return tvdbId;
    }
    
    public boolean isTvShow() {
        return seasonNumber != null && episodeNumber != null;
    }
    
    public String getCacheKey() {
        if (isTvShow() && showName != null) {
            return showName + "_S" + seasonNumber + "E" + episodeNumber;
        }
        return title != null ? title : "unknown";
    }
    
    public static class Builder {
        private String title;
        private String showName;
        private Integer seasonNumber;
        private Integer episodeNumber;
        private long runtimeSeconds;
        private String imdbId;
        private String tmdbId;
        private String traktId;
        private String tvdbId;
        
        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }
        
        public Builder setShowName(String showName) {
            this.showName = showName;
            return this;
        }
        
        public Builder setSeasonNumber(Integer seasonNumber) {
            this.seasonNumber = seasonNumber;
            return this;
        }
        
        public Builder setEpisodeNumber(Integer episodeNumber) {
            this.episodeNumber = episodeNumber;
            return this;
        }
        
        public Builder setRuntimeSeconds(long runtimeSeconds) {
            this.runtimeSeconds = runtimeSeconds;
            return this;
        }
        
        public Builder setImdbId(String imdbId) {
            this.imdbId = imdbId;
            return this;
        }
        
        public Builder setTmdbId(String tmdbId) {
            this.tmdbId = tmdbId;
            return this;
        }
        
        public Builder setTraktId(String traktId) {
            this.traktId = traktId;
            return this;
        }
        
        public Builder setTvdbId(String tvdbId) {
            this.tvdbId = tvdbId;
            return this;
        }
        
        public MediaIdentifier build() {
            return new MediaIdentifier(this);
        }
    }
}
