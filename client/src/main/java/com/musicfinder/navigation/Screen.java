package com.musicfinder.navigation;

public enum Screen {
    LOGIN("/com/musicfinder/view/login-view.fxml", "Music Finder - Login", "Login", 1000, 680),
    FORGOT_PASSWORD("/com/musicfinder/view/forgot-password-view.fxml", "Music Finder - Forgot Password", "Forgot Password", 1100, 720),
    REGISTER("/com/musicfinder/view/register-view.fxml", "Music Finder - Register", "Register", 1000, 680),
    SONG_DETAIL("/com/musicfinder/view/song-detail-content.fxml", "Music Finder - Song Detail", "Song Detail", 1100, 720),
    HOME("/com/musicfinder/view/home-content.fxml", "Music Finder - Home", "Discover", 1100, 720),
    SEARCH("/com/musicfinder/view/search-content.fxml", "Music Finder - Search", "Search", 1100, 720),
    ARTISTS("/com/musicfinder/view/artists-content.fxml", "Music Finder - Artists", "Artists", 1100, 720),
    ARTIST_DETAIL("/com/musicfinder/view/artist-detail-content.fxml", "Music Finder - Artist", "Artist Profile", 1100, 720),
    PLAYLISTS("/com/musicfinder/view/my-playlists-content.fxml", "Music Finder - My Playlists", "My Playlists", 1100, 720),
    FAVORITES("/com/musicfinder/view/favorites-content.fxml", "Music Finder - Favorites", "Favorites", 1100, 720),
    SEARCH_HISTORY("/com/musicfinder/view/search-history-content.fxml", "Music Finder - Search History", "Search History",
            1100, 720),
    TRENDING("/com/musicfinder/view/trending-content.fxml", "Music Finder - Trending", "Trending", 1100, 720),
    PROFILE("/com/musicfinder/view/profile-content.fxml", "Music Finder - Profile", "Your Profile", 1100,
            720),
    PLAYLIST_SONG("/com/musicfinder/view/playlist-song-content.fxml","Music Finder - Playlist","Playlist",1100,720);

    private final String fxml;
    private final String title;
    private final String headerTitle;
    private final double minWidth;
    private final double minHeight;

    Screen(String fxml, String title, String headerTitle, double minWidth, double minHeight) {
        this.fxml = fxml;
        this.title = title;
        this.headerTitle = headerTitle;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
    }

    public String getFxml() {
        return fxml;
    }

    public String getTitle() {
        return title;
    }

    public String getHeaderTitle() {
        return headerTitle;
    }

    public double getMinWidth() {
        return minWidth;
    }

    public double getMinHeight() {
        return minHeight;
    }

    public boolean isLogin() {
        return this == LOGIN;
    }
}
