package com.portal.slideshow;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** UI-facing settings snapshot. Persistence continues to use the established V1 preference keys. */
final class PortalSettings {
    enum TileMode { NATIVE, WEB_DRIVEN }

    final String deviceId;
    final String deviceName;
    final String sharedAlbumUrl;
    final String currentAlbumName;
    final TileMode tileMode;
    final Set<String> enabledTiles;
    final String photoHostUrl;
    final String videoFallbackUrl;
    final String assistantUrl;
    final boolean assistantConnected;

    PortalSettings(String deviceId, String deviceName, String sharedAlbumUrl,
                   String currentAlbumName, TileMode tileMode, Set<String> enabledTiles,
                   String photoHostUrl, String videoFallbackUrl, String assistantUrl,
                   boolean assistantConnected) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.sharedAlbumUrl = sharedAlbumUrl;
        this.currentAlbumName = currentAlbumName;
        this.tileMode = tileMode;
        this.enabledTiles = Collections.unmodifiableSet(new HashSet<String>(enabledTiles));
        this.photoHostUrl = photoHostUrl;
        this.videoFallbackUrl = videoFallbackUrl;
        this.assistantUrl = assistantUrl;
        this.assistantConnected = assistantConnected;
    }
}
