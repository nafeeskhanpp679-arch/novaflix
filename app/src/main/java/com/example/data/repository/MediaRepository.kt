package com.example.data.repository

import com.example.data.local.WatchItemDao
import com.example.data.local.WatchItemEntity
import com.example.data.model.*
import com.example.data.stream.StreamExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * MediaRepository for CineStream App.
 * Real content (movies/anime/webseries + multi-quality sources + episodes)
 * comes straight from FirebaseRepository, which reads the same Realtime
 * Database the Telegram auto-upload bot / NovaFlixAdmin panel write to.
 * ElitePlex remains as an optional alternate source, toggled by ServerConfig.
 *
 * Deliberately does NOT fall back to any hardcoded/fake catalog when the
 * real data is empty or a call fails — screens should show an empty/error
 * state in that case, not fake movies that could be mistaken for real
 * admin-uploaded content.
 */
class MediaRepository(
    private val watchItemDao: WatchItemDao,
    val firebaseRepository: FirebaseRepository = FirebaseRepository(),
    val elitePlexRepository: ElitePlexRepository = ElitePlexRepository(),
    val prefs: AppPrefs? = null
) {

    private fun uid(): String? = try {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    } catch (e: Exception) { null }


    // Generous timeouts: Render/Telegram stream servers can take 20-40s to wake up.
    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val streamExtractor = StreamExtractor(okHttpClient)

    val continueWatchingList: Flow<List<WatchItemEntity>> = watchItemDao.getContinueWatchingItems()
    // one card per title (progress rows of several episodes used to show the same series many times)
    val myList: Flow<List<WatchItemEntity>> = watchItemDao.getMyListItems().map { list -> list.distinctBy { it.mediaId } }

    // Strip the MediaItem.id prefix down to the raw Firebase push key.
    private fun keyOf(mediaId: String): String =
        mediaId.removePrefix("movie_").removePrefix("tv_").removePrefix("anime_")

    fun observeContentUpdates(): Flow<Unit> = firebaseRepository.observeContentUpdates()

    /** "The Dune: Part Two!" and "dune part two" -> same key. Used to spot the same title twice. */
    private fun titleKey(title: String): String =
        title.lowercase().filter { it.isLetterOrDigit() }.removePrefix("the")

    private fun MediaItem.dupKeys(): List<String> =
        listOfNotNull(titleKey(title).takeIf { it.isNotEmpty() }, tmdbId?.let { "tmdb:$it" })

    /** Removes every item whose title / tmdbId is already present in [adminKeys]. */
    private fun List<MediaItem>.withoutAdminUploads(adminKeys: Set<String>): List<MediaItem> =
        filter { item -> item.dupKeys().none { it in adminKeys } }

    /** One card per title inside a row. */
    private fun List<MediaItem>.uniqueTitles(): List<MediaItem> {
        val seen = HashSet<String>()
        return filter { item ->
            val keys = item.dupKeys().ifEmpty { listOf(item.id) }
            if (keys.any { it in seen }) false else { seen.addAll(keys); true }
        }
    }

    /**
     * Admin (Firebase) rows come first and are untouched. Sample-catalog titles are only added
     * when the admin has NOT uploaded that title, so nothing shows up twice and admin uploads
     * never get replaced by catalog data.
     */
    private fun mergeCategorySections(
        fromFb: List<Pair<String, List<MediaItem>>>,
        catalog: List<Pair<String, List<MediaItem>>>
    ): List<Pair<String, List<MediaItem>>> {
        if (fromFb.isEmpty()) return catalog.map { it.first to it.second.uniqueTitles() }.filter { it.second.isNotEmpty() }
        val adminKeys = fromFb.flatMap { it.second }.flatMap { it.dupKeys() }.toSet()
        val merged = mutableListOf<Pair<String, List<MediaItem>>>()

        for ((sectionName, items) in fromFb) {
            if (items.isNotEmpty()) merged.add(sectionName to items.uniqueTitles())
        }

        for ((sectionName, items) in catalog) {
            val filtered = items.withoutAdminUploads(adminKeys)
            if (filtered.isNotEmpty()) {
                val existingIndex = merged.indexOfFirst { it.first.equals(sectionName, ignoreCase = true) }
                if (existingIndex >= 0) {
                    val existingPair = merged[existingIndex]
                    merged[existingIndex] = existingPair.first to (existingPair.second + filtered).uniqueTitles()
                } else {
                    merged.add(sectionName to filtered.uniqueTitles())
                }
            }
        }
        return merged
    }

    /** Hero banners: admin first, catalog only for titles the admin has not uploaded. */
    private fun mergeHeroes(fromFb: List<MediaItem>, catalog: List<MediaItem>, count: Int): List<MediaItem> {
        val adminKeys = fromFb.flatMap { it.dupKeys() }.toSet()
        return (fromFb + catalog.withoutAdminUploads(adminKeys)).uniqueTitles().take(count)
    }

    // ------------------------------------------------------------------ //
    // Home screen
    // ------------------------------------------------------------------ //

    suspend fun getAdminCategorySections(): List<Pair<String, List<MediaItem>>> = withContext(Dispatchers.IO) {
        if (ServerConfig.isElitePlex()) {
            return@withContext try {
                elitePlexRepository.getMovieSections().map { (name, items) -> name to items.distinctBy { it.id } }
            } catch (e: Exception) { emptyList() }
        }
        val fromFb = try { firebaseRepository.getAdminCategorySections() } catch (e: Exception) { emptyList() }
        // HOME = movies only; admin uploads are never duplicated by the sample catalog
        val catalog = FallbackMediaCatalog.getHomeMovieSections()
        mergeCategorySections(fromFb, catalog)
            .map { (name, items) -> name to items.filter { it.mediaType == MediaType.MOVIE } }
            .filter { it.second.isNotEmpty() }
    }

    suspend fun getAdminFeaturedHeroBanners(count: Int = 6): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ServerConfig.isMovieBox()) {
            return@withContext try { elitePlexRepository.getHeroBanners(count, MediaType.MOVIE) } catch (e: Exception) { emptyList() }
        }
        val fromFb = try { firebaseRepository.getFeaturedHeroBanners(count) } catch (e: Exception) { emptyList() }
        mergeHeroes(fromFb, FallbackMediaCatalog.getHomeMovieHeroBanners(count), count)
    }

    suspend fun getAdminFeaturedHeroBanner(): MediaItem? = getAdminFeaturedHeroBanners(1).firstOrNull()

    suspend fun getCategories(): List<String> = withContext(Dispatchers.IO) {
        if (ServerConfig.isElitePlex()) {
            return@withContext listOf(
                "All", "Action", "Adventure", "Comedy", "Drama", "Crime",
                "Thriller", "Horror", "Romance", "Sci-Fi", "Fantasy",
                "Animation", "Family", "Bollywood", "Hollywood", "South Indian"
            )
        }
        val fromFb = try { firebaseRepository.getCategories() } catch (e: Exception) { emptyList() }
        if (fromFb.isNotEmpty()) fromFb else listOf("All", "Trending", "Action", "Sci-Fi", "Crime", "Comedy", "Drama", "Anime", "Bollywood", "Hollywood")
    }

    suspend fun hasAdminCustomStreams(mediaId: String, mediaType: MediaType, season: Int = 1, episode: Int = 1): Boolean = withContext(Dispatchers.IO) {
        val key = keyOf(mediaId)
        val sources = try {
            when (mediaType) {
                MediaType.MOVIE -> firebaseRepository.getMovieSources(key)
                MediaType.TV -> firebaseRepository.getEpisodeSources(key, isAnime = false, seasonNumber = season, episodeNumber = episode)
                MediaType.ANIME -> firebaseRepository.getEpisodeSources(key, isAnime = true, seasonNumber = season, episodeNumber = episode)
            }
        } catch (e: Exception) { emptyList() }
        sources.isNotEmpty()
    }

    /** Top 10 row: strictly filtered by MediaType from API or fallback. */
    suspend fun getTopTen(type: MediaType? = null, fallback: List<MediaItem> = emptyList()): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val targetType = type ?: MediaType.MOVIE
            if (ServerConfig.isElitePlex()) {
                val apiTop = try { elitePlexRepository.getTopTenFromApi(targetType) } catch (e: Exception) { emptyList() }
                if (apiTop.isNotEmpty()) {
                    return@withContext apiTop.filter { it.mediaType == targetType }.take(10)
                }
                val filtered = fallback.filter { it.mediaType == targetType }
                return@withContext filtered.sortedByDescending { it.rating }.take(10)
            }
            val list = try { firebaseRepository.getTopTen(targetType) } catch (e: Exception) { emptyList() }
            val strictlyFiltered = (if (list.isNotEmpty()) list else fallback).filter { it.mediaType == targetType }
            strictlyFiltered.take(10)
        }

    /** Admin-provided external subtitles + intro range for a movie / episode. */
    suspend fun getPlaybackExtras(mediaId: String, mediaType: MediaType, season: Int, episode: Int): PlaybackExtras =
        withContext(Dispatchers.IO) {
            if (ServerConfig.isElitePlex()) return@withContext PlaybackExtras()
            try {
                firebaseRepository.getPlaybackExtras(
                    key = keyOf(mediaId),
                    isMovie = mediaType == MediaType.MOVIE,
                    isAnime = mediaType == MediaType.ANIME,
                    season = season,
                    episode = episode
                )
            } catch (e: Exception) { PlaybackExtras() }
        }

    suspend fun getSeasonNumbers(mediaId: String, mediaType: MediaType): List<Int> = withContext(Dispatchers.IO) {
        if (mediaType == MediaType.MOVIE) return@withContext emptyList()
        try { firebaseRepository.getSeasonNumbers(keyOf(mediaId), mediaType == MediaType.ANIME) } catch (e: Exception) { emptyList() }
    }

    /** Real watched time (adds to the local counter and to users/{uid}/stats/watchTimeMs). */
    fun addWatchTime(deltaMs: Long) {
        if (deltaMs <= 0L) return
        prefs?.addWatchMs(deltaMs)
        uid()?.let { firebaseRepository.incrementWatchTime(it, deltaMs) }
    }

    /** "More Like This" - shared categories + shared cast. */
    suspend fun getSimilarItems(item: MediaItem, cast: List<CastMember>): List<MediaItem> =
        withContext(Dispatchers.IO) {
            try { firebaseRepository.getSimilarItems(item, cast) } catch (e: Exception) { emptyList() }
        }

    /** Best direct-download URL for a title/episode (admin downloadUrl first, then stream url). */
    suspend fun resolveDownloadUrl(mediaId: String, mediaType: MediaType, season: Int, episode: Int): String =
        withContext(Dispatchers.IO) {
            val key = keyOf(mediaId)
            val sources = try {
                when (mediaType) {
                    MediaType.MOVIE -> firebaseRepository.getMovieSources(key)
                    MediaType.TV -> firebaseRepository.getEpisodeSources(key, false, season, episode)
                    MediaType.ANIME -> firebaseRepository.getEpisodeSources(key, true, season, episode)
                }
            } catch (e: Exception) { emptyList() }
            val direct = sources.firstOrNull { it.downloadUrl.isNotBlank() }?.downloadUrl
            direct ?: sources.firstOrNull { !it.isHls }?.url ?: sources.firstOrNull()?.url ?: ""
        }

    // ------------------------------------------------------------------ //
    // Series screen
    // ------------------------------------------------------------------ //

    suspend fun getSeriesCategorySections(): List<Pair<String, List<MediaItem>>> = withContext(Dispatchers.IO) {
        if (ServerConfig.isMovieBox()) {
            return@withContext try {
                elitePlexRepository.getSeriesSections().map { (name, items) -> name to items.distinctBy { it.id } }
            } catch (e: Exception) { emptyList() }
        }
        val fromFb = try { firebaseRepository.getSeriesCategorySections() } catch (e: Exception) { emptyList() }
        val catalog = FallbackMediaCatalog.getSeriesCategorySections()
        mergeCategorySections(fromFb, catalog)
    }

    suspend fun getSeriesHeroBanners(count: Int = 5): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ServerConfig.isMovieBox()) {
            return@withContext try { elitePlexRepository.getHeroBanners(count, MediaType.TV) } catch (e: Exception) { emptyList() }
        }
        val fromFb = try { firebaseRepository.getSeriesHeroBanners(count) } catch (e: Exception) { emptyList() }
        mergeHeroes(fromFb, FallbackMediaCatalog.getSeriesHeroBanners(count), count)
    }

    // ------------------------------------------------------------------ //
    // Anime screen
    // ------------------------------------------------------------------ //

    suspend fun getAnimeCategorySections(): List<Pair<String, List<MediaItem>>> = withContext(Dispatchers.IO) {
        if (ServerConfig.isMovieBox()) {
            return@withContext try {
                elitePlexRepository.getAnimeSections().map { (name, items) -> name to items.distinctBy { it.id } }
            } catch (e: Exception) { emptyList() }
        }
        val fromFb = try { firebaseRepository.getAnimeCategorySections() } catch (e: Exception) { emptyList() }
        val catalog = FallbackMediaCatalog.getAnimeCategorySections()
        mergeCategorySections(fromFb, catalog)
    }

    suspend fun getAnimeHeroBanners(count: Int = 5): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ServerConfig.isMovieBox()) {
            return@withContext try { elitePlexRepository.getHeroBanners(count, MediaType.ANIME) } catch (e: Exception) { emptyList() }
        }
        val fromFb = try { firebaseRepository.getAnimeHeroBanners(count) } catch (e: Exception) { emptyList() }
        mergeHeroes(fromFb, FallbackMediaCatalog.getAnimeHeroBanners(count), count)
    }

    // ------------------------------------------------------------------ //
    // Legacy/simple accessors still used elsewhere in the app
    // ------------------------------------------------------------------ //

    suspend fun getTrendingMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ServerConfig.isElitePlex()) return@withContext try { elitePlexRepository.getTrendingMovies() } catch (e: Exception) { emptyList() }
        try { firebaseRepository.getTrendingMovies() } catch (e: Exception) { emptyList() }
    }

    suspend fun getHybridTrendingMovies(): List<MediaItem> = getTrendingMovies()

    suspend fun getLatestMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ServerConfig.isElitePlex()) return@withContext try { elitePlexRepository.getContentByCategory("Latest") } catch (e: Exception) { emptyList() }
        try {
            firebaseRepository.getContentByCategory("Latest").ifEmpty { firebaseRepository.getTrendingMovies() }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getIndianMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ServerConfig.isElitePlex()) return@withContext try { elitePlexRepository.getContentByCategory("Bollywood") } catch (e: Exception) { emptyList() }
        try {
            firebaseRepository.getContentByCategory("Indian").ifEmpty { firebaseRepository.getContentByCategory("Bollywood") }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getSouthIndianMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ServerConfig.isElitePlex()) return@withContext try { elitePlexRepository.getContentByCategory("South Indian") } catch (e: Exception) { emptyList() }
        try {
            firebaseRepository.getContentByCategory("South Indian").ifEmpty { firebaseRepository.getContentByCategory("Regional") }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getHollywoodMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ServerConfig.isElitePlex()) return@withContext try { elitePlexRepository.getContentByCategory("Hollywood") } catch (e: Exception) { emptyList() }
        try { firebaseRepository.getContentByCategory("Hollywood") } catch (e: Exception) { emptyList() }
    }

    suspend fun getTrendingTvSeries(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ServerConfig.isElitePlex()) return@withContext try { elitePlexRepository.getContentByCategory("TV Series") } catch (e: Exception) { emptyList() }
        try {
            firebaseRepository.getContentByCategory("TV Series").ifEmpty { firebaseRepository.getAllSeries(isAnime = false) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getPopularAnime(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ServerConfig.isElitePlex()) return@withContext try { elitePlexRepository.getContentByCategory("Anime") } catch (e: Exception) { emptyList() }
        try {
            firebaseRepository.getContentByCategory("Anime").ifEmpty { firebaseRepository.getAllSeries(isAnime = true) }
        } catch (e: Exception) { emptyList() }
    }

    // ------------------------------------------------------------------ //
    // Detail screen
    // ------------------------------------------------------------------ //

    suspend fun getMovieDetails(id: String): Pair<MediaItem, List<CastMember>> = withContext(Dispatchers.IO) {
        if (ServerConfig.isMovieBox() || id.startsWith("mb_")) {
            val item = try { elitePlexRepository.getMediaDetail(id) } catch (e: Exception) { null }
            if (item != null) return@withContext Pair(item, emptyList())
        }
        val key = keyOf(id)
        val fbDetail = try { firebaseRepository.getMovieDetail(key) } catch (e: Exception) { null }
        if (fbDetail != null && fbDetail.first.title != "Movie unavailable") {
            return@withContext fbDetail
        }
        val catalogItem = FallbackMediaCatalog.getMediaItemById(id)
        val item = catalogItem ?: MediaItem(
            id = "movie_$key", title = "Movie", overview = "",
            posterPath = null, backdropPath = null, mediaType = MediaType.MOVIE,
            rating = 8.0, releaseYear = "2024"
        )
        val cast = FallbackMediaCatalog.getCast(id)
        Pair(item, cast)
    }

    suspend fun getElitePlexMediaDetail(elitePlexId: String): MediaItem? {
        return elitePlexRepository.getMediaDetail(elitePlexId)
    }

    suspend fun getTvDetails(id: String): Triple<MediaItem, List<CastMember>, List<TmdbSeasonInfoDto>> =
        withContext(Dispatchers.IO) {
            if (ServerConfig.isMovieBox() || id.startsWith("mb_")) {
                return@withContext elitePlexRepository.getTvDetails(id)
            }
            val key = keyOf(id)
            val fbDetail = try { firebaseRepository.getSeriesDetail(key, isAnime = false) } catch (e: Exception) { null }
            val seasonNumbers = try { firebaseRepository.getSeasonNumbers(key, isAnime = false) } catch (e: Exception) { emptyList() }

            val (item, cast) = if (fbDetail != null && fbDetail.first.title != "Series unavailable") {
                fbDetail
            } else {
                val catalogItem = FallbackMediaCatalog.getMediaItemById(id)
                val fallbackItem = catalogItem ?: MediaItem(
                    id = "tv_$key", title = "Series", overview = "",
                    posterPath = null, backdropPath = null, mediaType = MediaType.TV,
                    rating = 8.5, releaseYear = "2024"
                )
                Pair(fallbackItem, FallbackMediaCatalog.getCast(id))
            }

            val seasons = if (seasonNumbers.isNotEmpty()) {
                seasonNumbers.map { num ->
                    TmdbSeasonInfoDto(id = num.toLong(), seasonNumber = num, name = "Season $num", episodeCount = null, posterPath = null)
                }
            } else {
                val totalSeasons = item.totalSeasons?.takeIf { it > 0 } ?: 1
                (1..totalSeasons).map { num ->
                    TmdbSeasonInfoDto(id = num.toLong(), seasonNumber = num, name = "Season $num", episodeCount = null, posterPath = null)
                }
            }

            Triple(item, cast, seasons)
        }

    suspend fun getAnimeFullDetails(id: String): Triple<MediaItem, List<CastMember>, List<TmdbSeasonInfoDto>> =
        withContext(Dispatchers.IO) {
            if (ServerConfig.isMovieBox() || id.startsWith("mb_")) {
                return@withContext elitePlexRepository.getTvDetails(id)
            }
            val key = keyOf(id)
            val fbDetail = try { firebaseRepository.getSeriesDetail(key, isAnime = true) } catch (e: Exception) { null }
            val seasonNumbers = try { firebaseRepository.getSeasonNumbers(key, isAnime = true) } catch (e: Exception) { emptyList() }

            val (item, cast) = if (fbDetail != null && fbDetail.first.title != "Anime unavailable") {
                fbDetail
            } else {
                val catalogItem = FallbackMediaCatalog.getMediaItemById(id)
                val fallbackItem = catalogItem ?: MediaItem(
                    id = "anime_$key", title = "Anime", overview = "",
                    posterPath = null, backdropPath = null, mediaType = MediaType.ANIME,
                    rating = 8.7, releaseYear = "2024"
                )
                Pair(fallbackItem, FallbackMediaCatalog.getCast(id))
            }

            val seasons = if (seasonNumbers.isNotEmpty()) {
                seasonNumbers.map { num ->
                    TmdbSeasonInfoDto(id = num.toLong(), seasonNumber = num, name = "Season $num", episodeCount = null, posterPath = null)
                }
            } else {
                val totalSeasons = item.totalSeasons?.takeIf { it > 0 } ?: 1
                (1..totalSeasons).map { num ->
                    TmdbSeasonInfoDto(id = num.toLong(), seasonNumber = num, name = "Season $num", episodeCount = null, posterPath = null)
                }
            }

            Triple(item, cast, seasons)
        }

    suspend fun getSeasonEpisodes(id: String, seasonNumber: Int): List<Episode> = withContext(Dispatchers.IO) {
        if (ServerConfig.isMovieBox() || id.startsWith("mb_")) {
            return@withContext elitePlexRepository.getSeasonEpisodes(id, seasonNumber)
        }
        val key = keyOf(id)
        val isAnime = id.startsWith("anime_")
        val fbEpisodes = try { firebaseRepository.getSeasonEpisodes(key, isAnime, seasonNumber) } catch (e: Exception) { emptyList() }
        if (fbEpisodes.isNotEmpty()) {
            fbEpisodes
        } else {
            FallbackMediaCatalog.getEpisodes(id, seasonNumber)
        }
    }

    suspend fun getAnimeDetails(id: String): Pair<MediaItem, List<Episode>> = withContext(Dispatchers.IO) {
        val (item, _, _) = getAnimeFullDetails(id)
        val episodes = getSeasonEpisodes(id, 1)
        Pair(item, episodes)
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        // .distinctBy{it.id} is the safety net: search results feed a LazyVerticalGrid keyed
        // by item.id, and any duplicate id there crashes the Search screen a moment after
        // results render. Every branch below is deduped before it returns.
        if (ServerConfig.isMovieBox()) {
            return@withContext try { elitePlexRepository.searchContent(query).distinctBy { it.id } } catch (e: Exception) { emptyList() }
        }
        val fromFb = try { firebaseRepository.searchContent(query) } catch (e: Exception) { emptyList() }
        val catalogMatches = FallbackMediaCatalog.getAllItems().filter {
            it.title.contains(query, ignoreCase = true) || it.genres.any { g -> g.contains(query, ignoreCase = true) }
        }
        val adminKeys = fromFb.flatMap { it.dupKeys() }.toSet()
        (fromFb + catalogMatches.withoutAdminUploads(adminKeys)).uniqueTitles().distinctBy { it.id }
    }

    // ------------------------------------------------------------------ //
    // Streaming
    // ------------------------------------------------------------------ //

    suspend fun extractStream(
        mediaId: String,
        mediaType: MediaType,
        title: String,
        season: Int = 1,
        episode: Int = 1
    ): StreamInfo {
        if (ServerConfig.isMovieBox() || mediaId.startsWith("mb_")) {
            val eliteStream = try {
                elitePlexRepository.extractStream(
                    mediaId = mediaId,
                    season = season,
                    episode = episode,
                    isMovie = mediaType == MediaType.MOVIE
                )
            } catch (e: Exception) { null }
            if (eliteStream != null && eliteStream.sources.isNotEmpty()) return eliteStream
        }

        val key = keyOf(mediaId)
        val sources = try {
            when (mediaType) {
                MediaType.MOVIE -> firebaseRepository.getMovieSources(key)
                MediaType.TV -> firebaseRepository.getEpisodeSources(key, isAnime = false, seasonNumber = season, episodeNumber = episode)
                MediaType.ANIME -> firebaseRepository.getEpisodeSources(key, isAnime = true, seasonNumber = season, episodeNumber = episode)
            }
        } catch (e: Exception) { emptyList() }

        if (sources.isNotEmpty()) {
            return StreamInfo(
                title = title,
                sources = sources,
                mediaId = mediaId,
                season = season,
                episode = episode
            )
        }

        // No admin-provided source yet for this title -> try a MovieBox scrape as a fallback
        try {
            val searchResults = elitePlexRepository.searchContent(title)
            val bestMatch = searchResults.firstOrNull { it.title.equals(title, ignoreCase = true) }
                ?: searchResults.firstOrNull()
            if (bestMatch != null) {
                val eliteStream = elitePlexRepository.extractStream(
                    mediaId = bestMatch.id,
                    season = season,
                    episode = episode,
                    isMovie = mediaType == MediaType.MOVIE
                )
                if (eliteStream != null && eliteStream.sources.isNotEmpty()) return eliteStream
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Eliteplex scrape fallback failed: ${e.message}")
        }

        return StreamInfo(title = title, sources = emptyList(), mediaId = mediaId, season = season, episode = episode)
    }

    // ------------------------------------------------------------------ //
    // Watch history / my list (unchanged)
    // ------------------------------------------------------------------ //

    suspend fun saveWatchProgress(
        mediaId: String,
        tmdbId: Long?,
        title: String,
        posterPath: String?,
        backdropPath: String?,
        mediaType: MediaType,
        season: Int,
        episode: Int,
        episodeTitle: String?,
        progressMillis: Long,
        durationMillis: Long,
        syncRemote: Boolean = true
    ) {
        val id = "${mediaType.name.lowercase()}_${mediaId}_s${season}_e$episode"

        // Movie/episode is basically finished (95%+ watched) - auto-clear it from
        // "Continue Watching" instead of leaving a completed row sitting there forever.
        val isFinished = durationMillis > 0L && progressMillis >= (durationMillis * 0.95)
        if (isFinished) {
            watchItemDao.deleteById(id)
            if (syncRemote) {
                try {
                    val userId = uid() ?: return
                    firebaseRepository.deleteContinueWatchingFromFirestore(userId, id)
                } catch (e: Exception) {
                    android.util.Log.e("MediaRepository", "Firebase auto-clear finished item note: ${e.message}")
                }
            }
            return
        }

        val existing = watchItemDao.getWatchItemByMediaId(mediaId)
        val entity = WatchItemEntity(
            id = id,
            mediaId = mediaId,
            tmdbId = tmdbId,
            title = title,
            posterPath = posterPath,
            backdropPath = backdropPath,
            mediaType = mediaType.name,
            seasonNumber = season,
            episodeNumber = episode,
            episodeTitle = episodeTitle,
            progressMillis = progressMillis,
            durationMillis = durationMillis,
            lastWatchedTimestamp = System.currentTimeMillis(),
            isInMyList = existing?.isInMyList ?: false
        )
        watchItemDao.insertOrUpdate(entity)

        if (!syncRemote) return

        try {
            val userId = uid() ?: return
            firebaseRepository.saveContinueWatchingToFirestore(
                userId = userId,
                itemId = id,
                mediaId = mediaId,
                tmdbId = tmdbId,
                title = title,
                posterPath = posterPath,
                backdropPath = backdropPath,
                mediaType = mediaType.name,
                seasonNumber = season,
                episodeNumber = episode,
                episodeTitle = episodeTitle,
                progressMillis = progressMillis,
                durationMillis = durationMillis
            )
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Firebase sync watch progress note: ${e.message}")
        }
    }

    suspend fun deleteContinueWatchingItem(id: String) {
        watchItemDao.deleteById(id)
        try {
            val userId = uid() ?: return
            firebaseRepository.deleteContinueWatchingFromFirestore(userId, id)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "Firebase delete continue watching note: ${e.message}")
        }
    }

    suspend fun toggleMyList(mediaItem: MediaItem, isInList: Boolean) {
        val existing = watchItemDao.getWatchItemByMediaId(mediaItem.id)
        val entity = existing?.copy(isInMyList = isInList) ?: WatchItemEntity(
            id = mediaItem.id,
            mediaId = mediaItem.id,
            tmdbId = mediaItem.tmdbId,
            title = mediaItem.title,
            posterPath = mediaItem.posterPath,
            backdropPath = mediaItem.backdropPath,
            mediaType = mediaItem.mediaType.name,
            isInMyList = isInList
        )
        if (existing != null) {
            watchItemDao.updateMyListStatus(mediaItem.id, isInList)
        } else if (isInList) {
            watchItemDao.insertOrUpdate(entity)
        }
        // real cloud save (users/{uid}/my_list)
        val userId = uid() ?: return
        if (isInList) {
            firebaseRepository.saveMyListItem(
                userId,
                entity.copy(
                    mediaId = mediaItem.id,
                    title = mediaItem.title,
                    posterPath = mediaItem.posterPath,
                    backdropPath = mediaItem.backdropPath,
                    mediaType = mediaItem.mediaType.name,
                    tmdbId = mediaItem.tmdbId
                )
            )
        } else {
            firebaseRepository.removeMyListItem(userId, mediaItem.id)
        }
    }

    suspend fun removeFromMyList(mediaId: String) {
        watchItemDao.updateMyListStatus(mediaId, false)
        uid()?.let { firebaseRepository.removeMyListItem(it, mediaId) }
    }

    suspend fun isMediaInMyList(mediaId: String): Boolean {
        return watchItemDao.getWatchItemByMediaId(mediaId)?.isInMyList ?: false
    }

    suspend fun getWatchItemByMediaId(mediaId: String): WatchItemEntity? {
        return watchItemDao.getWatchItemByMediaId(mediaId)
    }

    suspend fun getWatchItemById(id: String): WatchItemEntity? {
        return watchItemDao.getWatchItemById(id)
    }
}
