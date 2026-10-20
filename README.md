# NovaFlix — Android + Android TV

NovaFlix is a native Android OTT client built with Kotlin and Jetpack Compose. The project includes mobile and television-aware UI, media browsing, details, search, watchlist, profile, downloads, and player screens.

## Architecture rule (must be preserved)

- The **user app must never call TMDB or any external metadata API**.
- A separate, secured Admin Panel is responsible for importing and curating movie/series/anime metadata from TMDB.
- Curated catalog records and app-managed user data are delivered to the user app through Firebase.
- TMDB credentials must remain server-side in the admin/backend environment. Never embed them in Android resources, Kotlin source, or client configuration.
- Playback must use only authorized playback URLs that the administrator has configured. Metadata availability does not imply streaming rights or a playable video source.

## Android targets

- Android phones and tablets
- Android TV / Google TV with remote-friendly focus and large-screen layouts
- Low-end device considerations: bounded image caching, restrained motion, lazy lists, and graceful offline/error states

## Build a debug APK with GitHub Actions

1. Open the repository's **Actions** tab.
2. Select **Build Debug APK**.
3. Open the newest successful run and download the `cinestream-debug-apk` artifact.
4. Extract the ZIP to get the APK and install it on your test device.

Every push to a branch triggers the build workflow. Build success verifies compilation only; test actual device behavior separately.

## Firebase setup

The project expects Firebase configuration for enabled Firebase features. Add the correct Android Firebase configuration to `app/google-services.json` and configure Firebase Authentication / Google sign-in, Realtime Database or Firestore, and restrictive security rules before production use. Never make admin write permissions public. If configuration is absent or invalid, show an understandable setup/offline state instead of falsely reporting successful login or sync.

## Separate Admin Panel — implementation boundary

The Admin Panel is a separate application/backend, not a user-facing screen that exposes metadata credentials. It should provide authenticated admin-only import/curation, validation, moderation, content publishing, seasons/episodes, posters/backdrops, genres, audio/subtitle tracks, and authorized playback URLs. Server-side functions call TMDB and write curated records to Firebase. The Android client reads the curated Firebase data only.

## Important status notes

A successful APK build does not mean Firebase credentials, Google Sign-In OAuth configuration, admin import, video sources, offline downloads, or all device-specific features are configured. Verify each integration with real credentials and authorized test media before release.
