package com.example.ui.screens

import com.example.ui.components.TvHeroBanner
import com.example.ui.components.LocalIsTv
import com.example.ui.components.tvFocusRing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.ui.components.AppBottomBar
import com.example.ui.components.BottomTab
import com.example.ui.components.SkeletonScreen
import com.example.ui.components.UserAvatar
import com.example.ui.components.bestName
import com.example.ui.components.bestPhotoUrl
import com.example.ui.components.pressScale
import com.example.ui.components.rememberFirebaseUser
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.example.data.repository.AppPrefs
import com.example.data.repository.ServerConfig
import com.example.ui.components.ServerSelectionDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.WatchItemEntity
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.theme.*
import com.example.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.delay

private val NovaFlixRed = Color(0xFF5B7CFA)
private val NovaFlixRedGlow = Color(0x805B7CFA)
private val HaiFlixRed = NovaFlixRed
private val HaiFlixRedGlow = NovaFlixRedGlow
private val DarkBackground = Color(0xFF070912)
private val CardBackground = Color(0xFF0E1220)
private val BorderColor = Color(0x1AFFFFFF)
private val TextSecondaryColor = Color(0xFFA0A0A0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetail: (mediaId: String, mediaType: MediaType) -> Unit,
    onNavigateToPlayer: (mediaId: String, mediaType: MediaType, title: String, posterPath: String?, backdropPath: String?, tmdbId: Long?, season: Int, episode: Int) -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToAnime: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToMyList: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val continueWatchingList by viewModel.continueWatching.collectAsStateWithLifecycle()
    val currentServerType by ServerConfig.currentServer.collectAsState()
    val context = LocalContext.current
    var showServerDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadHomeData(force = true)
    }

    if (showServerDialog) {
        ServerSelectionDialog(
            prefs = AppPrefs.get(context),
            isFirstLaunch = false,
            onDismiss = { showServerDialog = false },
            onServerSelected = {
                showServerDialog = false
                viewModel.loadHomeData(force = true)
            }
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Brush.linearGradient(listOf(NovaFlixRed, Color(0xFF8B5CF6)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "NOVAFLIX",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                letterSpacing = 1.sp,
                                color = Color.White
                            )
                        }
                    },
                    actions = {
                        // Server selection indicator chip
                        Surface(
                            onClick = { showServerDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = when (currentServerType) {
                                ServerConfig.ServerType.MERA_SERVER -> Color(0xFF172044)
                                ServerConfig.ServerType.MOVIEBOX -> Color(0xFF132A18)
                                ServerConfig.ServerType.CUSTOM -> Color(0xFF132030)
                            },
                            border = BorderStroke(
                                1.dp,
                                when (currentServerType) {
                                    ServerConfig.ServerType.MERA_SERVER -> Color(0xFF5B7CFA).copy(alpha = 0.6f)
                                    ServerConfig.ServerType.MOVIEBOX -> Color(0xFF4CAF50).copy(alpha = 0.6f)
                                    ServerConfig.ServerType.CUSTOM -> Color(0xFF2196F3).copy(alpha = 0.6f)
                                }
                            ),
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(12.dp)).padding(end = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (currentServerType) {
                                                ServerConfig.ServerType.MERA_SERVER -> Color(0xFF5B7CFA)
                                                ServerConfig.ServerType.MOVIEBOX -> Color(0xFF4CAF50)
                                                ServerConfig.ServerType.CUSTOM -> Color(0xFF2196F3)
                                            }
                                        )
                                )
                                Text(
                                    text = when (currentServerType) {
                                        ServerConfig.ServerType.MERA_SERVER -> "Primary Server"
                                        ServerConfig.ServerType.MOVIEBOX -> "MovieBox"
                                        ServerConfig.ServerType.CUSTOM -> "Custom"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        val authUser = rememberFirebaseUser()
                        UserAvatar(
                            photoUrl = authUser?.bestPhotoUrl(),
                            name = if (authUser != null && !authUser.isAnonymous) authUser.bestName() else null,
                            size = 38.dp,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(CircleShape)
                                .tvFocusRing(CircleShape).clickable(onClick = onNavigateToProfile)
                                .testTag("profile_top_button")
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBackground
                    )
                )

                // Real-time Search Bar at top of Home Screen
                HomeSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onClear = { viewModel.clearSearch() },
                    isSearching = uiState.isSearching,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }
        },
        bottomBar = {
            AppBottomBar(
                selected = BottomTab.HOME,
                onHome = { },
                onSeries = onNavigateToSeries,
                onAnime = onNavigateToAnime,
                onDownloads = onNavigateToDownloads,
                onProfile = onNavigateToProfile
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        if (uiState.searchQuery.isNotBlank()) {
            SearchLiveResultsView(
                query = uiState.searchQuery,
                isSearching = uiState.isSearching,
                results = uiState.searchResults,
                selectedFilter = uiState.searchFilter,
                onFilterSelected = { viewModel.setSearchFilter(it) },
                onClearSearch = { viewModel.clearSearch() },
                onMediaClick = { item ->
                    onNavigateToDetail(item.id, item.mediaType)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else if (uiState.isLoading) {
            SkeletonScreen(modifier = Modifier.padding(paddingValues))
        } else if (uiState.error != null && uiState.categorySections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = HaiFlixRed,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Unable to load content",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.error ?: "Please check your network and try again.",
                        color = TextSecondaryColor,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        modifier = Modifier.tvFocusRing(RoundedCornerShape(20.dp)),
                        onClick = { viewModel.loadHomeData(force = true) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HaiFlixRed),
                        border = BorderStroke(1.dp, HaiFlixRed),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            // When category changes, scroll LazyColumn back to top
            LaunchedEffect(uiState.selectedCategory) {
                coroutineScope.launch {
                    listState.animateScrollToItem(0)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 64.dp)
            ) {
                // Hero Banner
                if (uiState.heroBannerItems.isNotEmpty() && uiState.selectedCategory == "All") {
                    item {
                        HeroBannerSection(
                            featuredItems = uiState.heroBannerItems,
                            onPlayClick = { item ->
                                onNavigateToPlayer(
                                    item.id,
                                    item.mediaType,
                                    item.title,
                                    item.posterPath,
                                    item.backdropPath,
                                    item.tmdbId,
                                    1,
                                    1
                                )
                            },
                            onDetailClick = { item ->
                                onNavigateToDetail(item.id, item.mediaType)
                            }
                        )
                    }
                }

                // Dynamic Admin Category Chips Selector
                if (uiState.categories.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.categories) { category ->
                                val isSelected = uiState.selectedCategory == category
                                val catIcon = getAdminCategoryIcon(category)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (isSelected) {
                                                Brush.horizontalGradient(listOf(Color(0xFF5B7CFA), Color(0xFF8B5CF6)))
                                            } else {
                                                SolidColor(CardBackground)
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFF9B8CFF) else BorderColor,
                                            RoundedCornerShape(20.dp)
                                        )
                                        .tvFocusRing(RoundedCornerShape(20.dp)).clickable { viewModel.selectCategory(category) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = catIcon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else TextSecondaryColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = category,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else TextSecondaryColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Continue Watching Section
                if (continueWatchingList.isNotEmpty() && (uiState.selectedCategory == "All")) {
                    item {
                        SectionHeader(title = "Continue Watching", icon = Icons.Default.History)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(continueWatchingList, key = { it.id }) { item ->
                                ContinueWatchingCard(
                                    item = item,
                                    onClick = {
                                        val mediaType = try { MediaType.valueOf(item.mediaType) } catch (e: Exception) { MediaType.MOVIE }
                                        onNavigateToPlayer(
                                            item.mediaId,
                                            mediaType,
                                            item.title,
                                            item.posterPath,
                                            item.backdropPath,
                                            item.tmdbId,
                                            item.seasonNumber,
                                            item.episodeNumber
                                        )
                                    },
                                    onDeleteClick = {
                                        viewModel.deleteContinueWatching(item.id)
                                    }
                                )
                            }
                        }
                    }
                }

                // Top 10 Movies Section
                if (uiState.top10Items.isNotEmpty() && uiState.selectedCategory == "All") {
                    item {
                        val top10RowState = rememberLazyListState()
                        val isTv = LocalIsTv.current
                        Column(modifier = Modifier
                            .padding(vertical = 12.dp)
                            // TV: intercept Up/Down keys so they scroll the LazyColumn
                            // instead of getting "swallowed" by the inner LazyRow focus
                            .then(
                                if (isTv) Modifier.onKeyEvent { ev ->
                                    // Only intercept on KeyDown
                                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                                    // Let Left/Right pass through to LazyRow (horizontal scroll)
                                    // Up/Down should NOT be consumed here — LazyColumn handles vertical
                                    false
                                } else Modifier
                            )
                        ) {
                            SectionHeader(
                                title = "Top 10 Movies Today",
                                icon = Icons.Default.LocalFireDepartment
                            )
                            LazyRow(
                                state = top10RowState,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(uiState.top10Items.take(10).withIndex().toList()) { (index, item) ->
                                    Top10MediaCard(
                                        rank = index + 1,
                                        media = item,
                                        onClick = { onNavigateToDetail(item.id, item.mediaType) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic Admin Category Sections from Firebase
                val sectionsToDisplay = if (uiState.selectedCategory == "All") {
                    uiState.categorySections
                } else {
                    val directMatch = uiState.categorySections.filter { it.categoryName.equals(uiState.selectedCategory, ignoreCase = true) }
                    if (directMatch.isNotEmpty()) {
                        directMatch
                    } else {
                        // Filter items across all sections by genre or mediaType
                        val selectedCat = uiState.selectedCategory.lowercase()
                        val filteredItems = uiState.categorySections.flatMap { it.items }.distinctBy { it.id }.filter { item ->
                            item.genres.any { it.equals(selectedCat, ignoreCase = true) } ||
                            (selectedCat.contains("movie") && item.mediaType == MediaType.MOVIE) ||
                            (selectedCat.contains("series") && item.mediaType == MediaType.TV) ||
                            (selectedCat.contains("anime") && item.mediaType == MediaType.ANIME)
                        }
                        if (filteredItems.isNotEmpty()) {
                            listOf(com.example.ui.viewmodel.AdminCategorySection(uiState.selectedCategory, filteredItems))
                        } else {
                            emptyList()
                        }
                    }
                }

                if (sectionsToDisplay.isEmpty() && uiState.categorySections.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No titles in \"${uiState.selectedCategory}\"",
                                color = TextSecondaryColor,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    sectionsToDisplay.forEach { section ->
                        if (section.items.isNotEmpty()) {
                            item(key = "admin_section_${section.categoryName}") {
                                SectionHeader(
                                    title = section.categoryName,
                                    icon = getAdminCategoryIcon(section.categoryName)
                                )
                                MediaRow(
                                    items = section.items,
                                    onItemClick = { onNavigateToDetail(it.id, it.mediaType) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroBannerSection(
    featuredItems: List<MediaItem>,
    onPlayClick: (MediaItem) -> Unit,
    onDetailClick: (MediaItem) -> Unit
) {
    if (featuredItems.isEmpty()) return
    if (LocalIsTv.current) {
        TvHeroBanner(items = featuredItems, tagLabel = "NOVAFLIX ORIGINALS", onPlay = onPlayClick, onDetails = onDetailClick)
        return
    }

    val pagerState = rememberPagerState(pageCount = { featuredItems.size })
    LaunchedEffect(featuredItems.size) {
        while (featuredItems.size > 1) {
            delay(6500)
            val count = pagerState.pageCount
            if (count > 1) pagerState.animateScrollToPage((pagerState.currentPage + 1) % count)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val media = featuredItems[page]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(CardBackground)
                    .border(1.dp, Color(0x365B7CFA), RoundedCornerShape(26.dp))
                    .tvFocusRing(RoundedCornerShape(26.dp))
                    .clickable { onDetailClick(media) }
            ) {
                AsyncImage(
                    model = media.getFullBackdropUrl() ?: media.getFullPosterUrl(),
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color(0x12070912),
                            0.28f to Color(0x10070912),
                            0.53f to Color(0x8C070912),
                            0.74f to Color(0xF5070912),
                            1f to DarkBackground
                        )
                    )
                )
                Box(
                    Modifier.fillMaxWidth().height(150.dp).align(Alignment.BottomCenter).background(
                        Brush.verticalGradient(listOf(Color.Transparent, DarkBackground))
                    )
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(7.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF5B7CFA), Color(0xFF8B5CF6)))).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text("FEATURED", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                        }
                        Text(media.mediaType.name, color = Color.White.copy(alpha = .9f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(media.title, color = Color.White, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    val meta = listOfNotNull(
                        if (media.rating > 0.0) "★ " + String.format("%.1f", media.rating) else null,
                        media.releaseYear.takeIf { it.isNotBlank() },
                        media.genres.firstOrNull()
                    ).joinToString("  •  ")
                    if (meta.isNotBlank()) Text(meta, color = Color(0xFFD7DDF0), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    if (media.overview.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(media.overview, color = Color(0xFFB7C0D7), fontSize = 12.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onPlayClick(media) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF080B15)),
                            modifier = Modifier.height(48.dp).tvFocusRing(RoundedCornerShape(14.dp)).testTag("hero_play_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Watch now", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                        OutlinedButton(
                            onClick = { onDetailClick(media) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = .55f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.height(48.dp).tvFocusRing(RoundedCornerShape(14.dp))
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Details", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                if (featuredItems.size > 1) {
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(16.dp).clip(CircleShape).background(Color(0x99070912)).padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${page + 1} / ${featuredItems.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(featuredItems.size) { i ->
                Box(
                    Modifier.padding(horizontal = 3.dp).width(if (pagerState.currentPage == i) 22.dp else 6.dp).height(5.dp)
                        .clip(CircleShape)
                        .background(if (pagerState.currentPage == i) Color(0xFF7B8DFF) else Color(0xFF3A4258))
                )
            }
        }
    }
}

private fun getAdminCategoryIcon(categoryName: String): androidx.compose.ui.graphics.vector.ImageVector {
    val lower = categoryName.lowercase()
    return when {
        "tv" in lower || "series" in lower || "show" in lower -> Icons.Default.Tv
        "anime" in lower || "animation" in lower -> Icons.Default.Subtitles
        "trending" in lower || "popular" in lower || "fire" in lower -> Icons.Default.LocalFireDepartment
        "latest" in lower || "new" in lower -> Icons.Default.NewReleases
        "top" in lower || "star" in lower || "featured" in lower -> Icons.Default.Star
        else -> Icons.Default.Movie
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 12.dp)
    ) {
        // Red accent bar: width: 5px, height: 22px, glow (identical to DetailScreen CategoryHeaderSection)
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(HaiFlixRed)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight(700),
            color = Color.White
        )
    }
}

@Composable
private fun MediaRow(
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit
) {
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.id }) { item ->
            MediaCard(item = item, onClick = { onItemClick(item) })
        }
    }
}

@Composable
private fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit
) {
    val isTv = LocalIsTv.current
    // TV: wider cards look better on big screens; phone keeps 130dp
    val cardWidth = if (isTv) 175.dp else 130.dp
    Box(
        modifier = Modifier.pressScale(onClick)
            .width(cardWidth)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .testTag("media_card_${item.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Color.Black)
            ) {
                val context = LocalContext.current
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(item.getFullPosterUrl())
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Rating badge in top-right
                Surface(
                    color = Color.Black.copy(alpha = 0.80f),
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = String.format("%.1f", item.rating),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom title gradient overlay like DetailScreen ContentCardView
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                startY = 120f
                            )
                        )
                )

                // Title overlay at bottom of poster
                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight(600),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                )
            }

            // Subtitle info row (year & media type)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.releaseYear,
                    color = TextSecondaryColor,
                    fontSize = 11.sp
                )
                Text(
                    text = item.mediaType.name,
                    color = HaiFlixRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: WatchItemEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val progressRatio = if (item.durationMillis > 0) item.progressMillis.toFloat() / item.durationMillis.toFloat() else 0f
    val isTv = LocalIsTv.current

    val cwWidth  = if (isTv) 260.dp else 200.dp
    val cwHeight = if (isTv) 148.dp else 115.dp

    Column(
        // TV: hold OK on the card to remove it from Continue Watching (no tiny X button to reach)
        modifier = Modifier.pressScale(onClick, onLongClick = if (isTv) onDeleteClick else null)
            .width(cwWidth)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .testTag("continue_card_${item.id}")
    ) {
        Box(
            modifier = Modifier
                .width(cwWidth)
                .height(cwHeight)
                .background(Color.Black)
        ) {
            AsyncImage(
                model = item.getFullBackdropUrl() ?: item.getFullPosterUrl(),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Play Overlay with HaiFlix red accent
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = HaiFlixRed,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(7.dp)
                            .fillMaxSize()
                    )
                }
            }

            // Delete Button Top Right (touch only)
            if (!isTv) IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.tvFocusRing(CircleShape)
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(26.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Progress Bar with HaiFlix red
            LinearProgressIndicator(
                progress = { progressRatio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter),
                color = HaiFlixRed,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }

        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight(600),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val epLabel = if (item.mediaType != "MOVIE") {
                "S${item.seasonNumber} E${item.episodeNumber} ${item.episodeTitle ?: ""}"
            } else {
                "Movie"
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = epLabel,
                color = TextSecondaryColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HomeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .tvFocusRing(RoundedCornerShape(14.dp))
            .height(48.dp)
            .testTag("home_search_bar"),
        shape = RoundedCornerShape(14.dp),
        color = CardBackground,
        border = BorderStroke(
            1.dp,
            if (query.isNotEmpty()) HaiFlixRed.copy(alpha = 0.8f) else BorderColor
        ),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = if (query.isNotEmpty()) HaiFlixRed else TextSecondaryColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("home_search_input"),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                singleLine = true,
                cursorBrush = SolidColor(HaiFlixRed),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search movies, series, anime...",
                            color = TextSecondaryColor,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
            )

            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = HaiFlixRed
                )
            } else if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.tvFocusRing(CircleShape)
                        .size(28.dp)
                        .testTag("home_search_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = TextSecondaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchLiveResultsView(
    query: String,
    isSearching: Boolean,
    results: List<MediaItem>,
    selectedFilter: MediaType?,
    onFilterSelected: (MediaType?) -> Unit,
    onClearSearch: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Filter chips and status header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSearching) "Searching Firebase..." else "Results for \"$query\"",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                if (!isSearching && results.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${results.size} found",
                            color = HaiFlixRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick filter chips: All, Movies, Series, Anime
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    null to "All",
                    MediaType.MOVIE to "Movies",
                    MediaType.TV to "Series",
                    MediaType.ANIME to "Anime"
                ).forEach { (type, label) ->
                    val isSelected = selectedFilter == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) HaiFlixRed else CardBackground)
                            .border(1.dp, if (isSelected) HaiFlixRed else BorderColor, RoundedCornerShape(20.dp))
                            .tvFocusRing(RoundedCornerShape(20.dp)).clickable { onFilterSelected(type) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextSecondaryColor
                        )
                    }
                }
            }
        }

        if (isSearching && results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = HaiFlixRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Searching titles in Firebase...",
                        color = TextSecondaryColor,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(CardBackground)
                            .border(1.dp, BorderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondaryColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No titles found",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "No movies, series, or anime match \"$query\". Check your spelling or try another title.",
                        color = TextSecondaryColor,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedButton(
                        modifier = Modifier.tvFocusRing(RoundedCornerShape(20.dp)),
                        onClick = onClearSearch,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HaiFlixRed),
                        border = BorderStroke(1.dp, HaiFlixRed),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Clear Search", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = if (LocalIsTv.current) GridCells.Adaptive(140.dp) else GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results, key = { it.id }) { item ->
                    SearchGridMediaCard(
                        item = item,
                        onClick = { onMediaClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchGridMediaCard(
    item: MediaItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.pressScale(onClick)
            .fillMaxWidth()
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .testTag("search_result_card_${item.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = item.getFullPosterUrl(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Media type badge (MOVIE, SERIES, ANIME) in top-left
                val (badgeBg, badgeText) = when (item.mediaType) {
                    MediaType.MOVIE -> HaiFlixRed to "MOVIE"
                    MediaType.TV -> Color(0xFF3B82F6) to "SERIES"
                    MediaType.ANIME -> Color(0xFF8B5CF6) to "ANIME"
                }
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Rating badge in top-right
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = String.format("%.1f", item.rating),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom title gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f)),
                                startY = 120f
                            )
                        )
                )

                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.releaseYear,
                    color = TextSecondaryColor,
                    fontSize = 11.sp
                )
                Text(
                    text = item.mediaType.name,
                    color = HaiFlixRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
