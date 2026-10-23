package com.deratization.player

import android.Manifest
import android.graphics.BitmapFactory
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.audiofx.Equalizer
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val Ink = Color(0xFF08080D)
private val Panel = Color(0xFF17171F)
private val Muted = Color(0xFF9C9CA9)
private val Pink = Color(0xFFFF36B7)
private val Violet = Color(0xFF783DFF)
private val Orange = Color(0xFFFF5D3A)

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val dateAdded: Long,
    val uri: Uri
)

enum class SortMode(val label: String) {
    TITLE("Название"), ARTIST("Исполнитель"), DURATION("Длительность"), DATE("Дата добавления")
}

class LocalAudioRepository(private val context: Context) {
    suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.IS_MUSIC
        )
        val result = mutableListOf<Track>()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        context.contentResolver.query(
            collection, projection, selection, null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += Track(
                    id = id,
                    title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Без названия",
                    artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Неизвестный исполнитель",
                    album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Без альбома",
                    durationMs = cursor.getLong(durationCol),
                    dateAdded = cursor.getLong(dateCol),
                    uri = ContentUris.withAppendedId(collection, id)
                )
            }
        }
        // Also include audio files imported into Deratization's private Music folder.
        val importedDir = File(context.filesDir, "music")
        if (importedDir.exists()) {
            importedDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("mp3", "m4a", "aac", "ogg", "wav", "flac", "opus") }?.forEach { file ->
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?.takeIf { it.isNotBlank() } ?: "Импортированный трек"
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    result += Track(
                        id = -file.absolutePath.hashCode().toLong().let { kotlin.math.abs(it) }.coerceAtLeast(1L),
                        title = title, artist = artist, album = "Импортировано",
                        durationMs = duration, dateAdded = file.lastModified() / 1000L,
                        uri = Uri.fromFile(file)
                    )
                } catch (_: Exception) {
                    // Skip files whose metadata cannot be read.
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }
        }
        result.distinctBy { it.id }
    }
}

class MusicPlayer(context: Context) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    private var equalizer: Equalizer? = null
    fun attachEqualizer(): Equalizer? {
        return try {
            if (equalizer == null) {
                equalizer = Equalizer(0, exoPlayer.audioSessionId).apply { enabled = true }
            }
            equalizer
        } catch (_: Exception) { null }
    }
    fun releaseEqualizer() {
        try { equalizer?.release() } catch (_: Exception) {}
        equalizer = null
    }
    fun playQueue(tracks: List<Track>, startAt: Int = 0) {
        if (tracks.isEmpty()) return
        exoPlayer.setMediaItems(tracks.map { MediaItem.fromUri(it.uri) }, startAt.coerceIn(0, tracks.lastIndex), 0L)
        exoPlayer.prepare()
        exoPlayer.play()
    }
    fun toggle() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }
    fun release() {
        releaseEqualizer()
        exoPlayer.release()
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var musicPlayer: MusicPlayer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        musicPlayer = MusicPlayer(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Ink, surface = Panel, primary = Pink,
                    secondary = Violet, onBackground = Color.White, onSurface = Color.White
                )
            ) {
                DeratizationApp(
                    context = this,
                    musicPlayer = musicPlayer,
                    onRelease = { musicPlayer.release() }
                )
            }
        }
    }
    override fun onDestroy() {
        if (::musicPlayer.isInitialized) musicPlayer.release()
        super.onDestroy()
    }
}

@Composable
fun DeratizationApp(context: Context, musicPlayer: MusicPlayer, onRelease: () -> Unit) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var sortMode by remember { mutableStateOf(SortMode.TITLE) }
    var selectedTab by remember { mutableStateOf("Треки") }
    var currentTrack by remember { mutableStateOf<Track?>(null) }
    var currentQueue by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isPlaying by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showSort by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showWaveSettings by remember { mutableStateOf(false) }
    var wavePlaylistId by remember { mutableStateOf<Long?>(null) }
    var permissionGranted by remember { mutableStateOf(hasAudioPermission(context)) }
    val database = remember { DeratizationDatabase.get(context) }
    val scope = rememberCoroutineScope()

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) tracks = LocalAudioRepository(context).loadTracks()
    }
    DisposableEffect(musicPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = musicPlayer.exoPlayer.currentMediaItemIndex
                val track = currentQueue.getOrNull(index)
                if (track != null) {
                    currentTrack = track
                    scope.launch {
                        database.historyDao().add(
                            HistoryEntity(trackId = track.id, title = track.title, artist = track.artist, uri = track.uri.toString())
                        )
                    }
                }
            }
        }
        musicPlayer.exoPlayer.addListener(listener)
        onDispose { musicPlayer.exoPlayer.removeListener(listener) }
    }

    val sortedTracks = remember(tracks, sortMode, query) {
        val filtered = tracks.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
        when (sortMode) {
            SortMode.TITLE -> filtered.sortedBy { it.title.lowercase(Locale.getDefault()) }
            SortMode.ARTIST -> filtered.sortedBy { it.artist.lowercase(Locale.getDefault()) }
            SortMode.DURATION -> filtered.sortedBy { it.durationMs }
            SortMode.DATE -> filtered.sortedByDescending { it.dateAdded }
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(Modifier.fillMaxSize()) {
            Header()
            when (selectedTab) {
                "Волна" -> WaveScreen(
                    modifier = Modifier.weight(1f),
                    onPlay = {
                        scope.launch {
                            val sourceTracks = if (wavePlaylistId == null) tracks else {
                                val ids = database.playlistDao().getTrackIds(wavePlaylistId!!)
                                ids.mapNotNull { id -> tracks.find { it.id == id } }
                            }
                            if (sourceTracks.isNotEmpty()) {
                                val shuffled = sourceTracks.shuffled()
                                currentQueue = shuffled
                                musicPlayer.playQueue(shuffled)
                                currentTrack = shuffled.first()
                            } else {
                                Toast.makeText(context, "В выбранном плейлисте нет доступных треков", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onSettings = { showWaveSettings = true }
                )
                "Плейлисты" -> PlaylistManagerScreen(
                    database = database,
                    tracks = tracks,
                    onPlayTracks = { chosen ->
                        if (chosen.isNotEmpty()) {
                            currentQueue = chosen
                            musicPlayer.playQueue(chosen)
                            currentTrack = chosen.first()
                        }
                    },
                    onTracksImported = {
                        scope.launch { tracks = LocalAudioRepository(context).loadTracks() }
                    }
                )
                "История" -> HistoryScreen(database = database)
                "Треки" -> {
                    if (!permissionGranted) {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                            Text("Нужен доступ к аудиофайлам", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("Deratization читает только музыкальную библиотеку устройства.", color = Muted)
                            Spacer(Modifier.height(18.dp))
                            Button(onClick = {
                                requestPermission.launch(
                                    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                                    else Manifest.permission.READ_EXTERNAL_STORAGE
                                )
                            }) { Text("Разрешить доступ") }
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, null, tint = Muted)
                            Spacer(Modifier.width(8.dp))
                            TextField(
                                value = query, onValueChange = { query = it },
                                placeholder = { Text("Поиск по трекам", color = Muted) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            IconButton(onClick = { showSort = true }) {
                                Icon(Icons.Default.Sort, "Сортировка", tint = Pink)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${sortedTracks.size} треков", color = Muted, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                if (sortedTracks.isNotEmpty()) {
                                    currentQueue = sortedTracks
                                    musicPlayer.playQueue(sortedTracks)
                                    currentTrack = sortedTracks.first()
                                }
                            }) { Icon(Icons.Default.PlayArrow, null); Text("Слушать всё") }
                        }
                        if (sortedTracks.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (tracks.isEmpty()) "Музыка не найдена на устройстве" else "Ничего не найдено", color = Muted)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 100.dp)
                            ) {
                                items(sortedTracks, key = { it.id }) { track ->
                                    TrackRow(track = track, onClick = {
                                        currentQueue = sortedTracks
                                        musicPlayer.playQueue(sortedTracks, sortedTracks.indexOf(track))
                                        currentTrack = track
                                    })
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(86.dp))
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            currentTrack?.let { track ->
                MiniPlayer(
                    track = track, isPlaying = isPlaying,
                    onPlay = { musicPlayer.toggle() },
                    onOpen = { showPlayer = true }
                )
            }
            NavigationBar(containerColor = Color(0xFF101015), tonalElevation = 0.dp) {
                listOf(
                    Triple("Волна", Icons.Default.Shuffle, "Волна"),
                    Triple("Треки", Icons.Default.LibraryMusic, "Треки"),
                    Triple("Плейлисты", Icons.Default.PlaylistPlay, "Плейлисты"),
                    Triple("История", Icons.Default.History, "История")
                ).forEach { (label, icon, tab) ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(icon, null) },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Pink, selectedTextColor = Pink,
                            indicatorColor = Color(0xFF302034), unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }

        if (showSort) {
            AlertDialog(
                onDismissRequest = { showSort = false },
                title = { Text("Сортировать по") },
                text = {
                    Column {
                        SortMode.values().forEach { mode ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .clickable { sortMode = mode; showSort = false }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = sortMode == mode, onClick = { sortMode = mode; showSort = false })
                                Spacer(Modifier.width(8.dp))
                                Text(mode.label)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showSort = false }) { Text("Готово") } },
                containerColor = Panel
            )
        }
        if (showPlayer && currentTrack != null) {
            FullPlayer(
                track = currentTrack!!, isPlaying = isPlaying,
                onPlay = { musicPlayer.toggle() },
                onPrevious = { musicPlayer.exoPlayer.seekToPreviousMediaItem() },
                onNext = { musicPlayer.exoPlayer.seekToNextMediaItem() },
                onEqualizer = { showEqualizer = true },
                onClose = { showPlayer = false }
            )
        }
        if (showEqualizer) {
            EqualizerDialog(
                equalizer = musicPlayer.attachEqualizer(),
                onDismiss = { showEqualizer = false }
            )
        }
        if (showWaveSettings) {
            WaveSettingsDialog(
                database = database,
                selectedId = wavePlaylistId,
                onSelect = { wavePlaylistId = it },
                onDismiss = { showWaveSettings = false }
            )
        }
    }
}

private fun hasAudioPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun Header() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("DERATIZATION", color = Pink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
            Text("Музыка без границ", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Icon(Icons.Default.Equalizer, null, tint = Violet, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun WaveScreen(modifier: Modifier = Modifier, onPlay: () -> Unit, onSettings: () -> Unit) {
    Box(modifier.fillMaxWidth()) {
        AnimatedArtwork(Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.Center).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ТВОЯ МУЗЫКАЛЬНАЯ ВСЕЛЕННАЯ", color = Color.White.copy(alpha = .75f), fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(16.dp))
            Text("Моя волна", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("Случайный порядок. Твой звук.", color = Color.White.copy(alpha = .8f))
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = Pink),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(horizontal = 30.dp, vertical = 14.dp)
            ) { Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(8.dp)); Text("Запустить волну") }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onSettings) { Icon(Icons.Default.Settings, null, tint = Color.White); Spacer(Modifier.width(6.dp)); Text("Настроить волну", color = Color.White) }
        }
    }
}

@Composable
fun AnimatedArtwork(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "gradient")
    val shift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000), RepeatMode.Reverse), label = "shift"
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF120A27), Color(0xFF5D0879),
                    Color(0xFFEB145C), Color(0xFFFF542F),
                    Color(0xFF6B21C7), Color(0xFF08080D)
                ),
                start = androidx.compose.ui.geometry.Offset(w * shift, 0f),
                end = androidx.compose.ui.geometry.Offset(w * (1f - shift), h)
            )
        )
        // Abstract flowing ribbons / living fallback pattern.
        repeat(7) { i ->
            val x = w * ((i * .19f + shift * .22f) % 1f)
            val y = h * ((i * .137f + (1f - shift) * .31f) % 1f)
            drawCircle(
                color = listOf(Pink, Violet, Orange, Color.Cyan)[i % 4].copy(alpha = .10f),
                radius = w * (.12f + (i % 3) * .035f),
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
        }
    }
}

@Composable
fun TrackArtwork(track: Track, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = track.uri) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                if (track.uri.scheme == "file") retriever.setDataSource(track.uri.path ?: "")
                else retriever.setDataSource(context, track.uri)
                retriever.embeddedPicture?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            } catch (_: Exception) {
                null
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
    }
    Box(modifier) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "Обложка ${track.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            AnimatedArtwork(Modifier.fillMaxSize())
            Icon(Icons.Default.Album, null, tint = Color.White.copy(alpha = .85f), modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(track, Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatDuration(track.durationMs), color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun MiniPlayer(track: Track, isPlaying: Boolean, onPlay: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF24232D)).clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(track, Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlay) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White) }
    }
}

@Composable
fun FullPlayer(track: Track, isPlaying: Boolean, onPlay: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onEqualizer: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        AnimatedArtwork(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .60f)))
        Column(
            Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("⌄  Свернуть", color = Color.White) }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.FavoriteBorder, null, tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            TrackArtwork(track, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)))
            Spacer(Modifier.height(30.dp))
            Text(track.title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Color.White.copy(alpha = .72f), fontSize = 17.sp)
            Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = onPlay, modifier = Modifier.size(76.dp).clip(CircleShape).background(Color.White)) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Ink, modifier = Modifier.size(42.dp))
                }
                IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEqualizer) {
                Icon(Icons.Default.Equalizer, null, tint = Pink)
                Spacer(Modifier.width(8.dp))
                Text("Открыть эквалайзер", color = Color.White)
            }
        }
    }
}

@Composable
fun PlaceholderScreen(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Pink, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(18.dp))
        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Muted)
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}


@Composable
fun PlaylistManagerScreen(
    database: DeratizationDatabase,
    tracks: List<Track>,
    onPlayTracks: (List<Track>) -> Unit,
    onTracksImported: () -> Unit
) {
    val playlists by database.playlistDao().observePlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val toastContext = LocalContext.current
    var name by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<Long?>(null) }
    var selectedTrackIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    LaunchedEffect(selectedPlaylist, tracks.size) {
        selectedTrackIds = selectedPlaylist?.let { database.playlistDao().getTrackIds(it) } ?: emptyList()
    }
    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var importUrl by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Плейлисты", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("${playlists.size} подборок", color = Muted, fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showImport = true }) { Icon(Icons.Default.Download, "Импорт аудио", tint = Pink) }
                Button(onClick = { showCreate = true }, colors = ButtonDefaults.buttonColors(containerColor = Pink)) {
                    Text("+ Создать")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlaylistPlay, null, tint = Pink, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Пока нет плейлистов", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Создай подборку и добавляй в неё музыку", color = Muted)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 110.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp))
                            .background(Panel).clickable { selectedPlaylist = if (selectedPlaylist == playlist.id) null else playlist.id }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) { AnimatedArtwork(Modifier.fillMaxSize()) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(if (selectedPlaylist == playlist.id) "Выбрано · нажми ▶ для запуска" else "Локальный плейлист", color = Muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = {
                            scope.launch { database.playlistDao().delete(playlist.id) }
                            if (selectedPlaylist == playlist.id) selectedPlaylist = null
                        }) { Text("×", color = Muted, fontSize = 24.sp) }
                    }
                    if (selectedPlaylist == playlist.id) {
                        Text("В плейлисте · ${selectedTrackIds.size}", color = Pink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                        selectedTrackIds.mapNotNull { id -> tracks.find { it.id == id } }.forEach { track ->
                            Row(
                                Modifier.fillMaxWidth().padding(start = 8.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, color = Muted, fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        database.playlistDao().removeTrack(playlist.id, track.id)
                                        selectedTrackIds = database.playlistDao().getTrackIds(playlist.id)
                                    }
                                }) { Text("−", color = Orange, fontSize = 22.sp) }
                            }
                        }
                        Text("Добавить треки", color = Pink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                        tracks.take(80).forEach { track ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    scope.launch {
                                        val ids = database.playlistDao().getTrackIds(playlist.id)
                                        if (track.id !in ids) {
                                            database.playlistDao().addTrack(PlaylistTrackEntity(playlist.id, track.id, ids.size))
                                        }
                                        selectedTrackIds = database.playlistDao().getTrackIds(playlist.id)
                                    }
                                }.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, color = Muted, fontSize = 12.sp)
                                }
                                Text(if (track.id in selectedTrackIds) "✓" else "+", color = Pink, fontSize = 22.sp)
                            }
                        }
                        TextButton(onClick = {
                            scope.launch {
                                val ids = database.playlistDao().getTrackIds(playlist.id)
                                val chosen = ids.mapNotNull { id -> tracks.find { it.id == id } }
                                if (chosen.isNotEmpty()) onPlayTracks(chosen)
                            }
                        }) { Icon(Icons.Default.PlayArrow, null); Text("Воспроизвести плейлист") }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false; name = "" },
            title = { Text("Новый плейлист") },
            text = {
                TextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text("Название подборки") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val safeName = name.trim()
                    if (safeName.isNotEmpty()) scope.launch { database.playlistDao().create(PlaylistEntity(name = safeName)) }
                    name = ""; showCreate = false
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Отмена") } },
            containerColor = Panel
        )
    }
    if (showImport) {
        AlertDialog(
            onDismissRequest = { if (!importing) showImport = false },
            title = { Text("Импорт аудио по ссылке") },
            text = {
                Column {
                    Text("Вставь прямую ссылку на аудиофайл (например, .mp3 или .m4a). Страницы YouTube/VK/SoundCloud не скачиваются автоматически — нужны разрешённые ссылки или официальный API.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    TextField(value = importUrl, onValueChange = { importUrl = it }, singleLine = true, placeholder = { Text("https://example.com/track.mp3") })
                    if (importing) { Spacer(Modifier.height(12.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Pink) }
                }
            },
            confirmButton = {
                TextButton(enabled = !importing, onClick = {
                    val link = importUrl.trim()
                    if (link.isNotBlank()) {
                        importing = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { downloadDirectAudio(context = appContext, urlText = link) }
                            importing = false
                            if (result.isSuccess) {
                                Toast.makeText(toastContext, "Аудио импортировано", Toast.LENGTH_SHORT).show()
                                onTracksImported()
                                showImport = false
                                importUrl = ""
                            } else {
                                Toast.makeText(toastContext, result.exceptionOrNull()?.message ?: "Не удалось импортировать файл", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }) { Text("Импортировать") }
            },
            dismissButton = { TextButton(enabled = !importing, onClick = { showImport = false }) { Text("Отмена") } },
            containerColor = Panel
        )
    }
}


private suspend fun downloadDirectAudio(context: Context, urlText: String): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val uri = Uri.parse(urlText)
        require(uri.scheme == "https" || uri.scheme == "http") { "Нужна ссылка http/https" }
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        require(extension in setOf("mp3", "m4a", "aac", "ogg", "wav", "flac", "opus")) {
            "Ссылка должна вести напрямую на аудиофайл поддерживаемого формата"
        }
        val connection = (URL(urlText).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DeratizationAndroid/0.3")
        }
        try {
            connection.connect()
            require(connection.responseCode in 200..299) { "Сервер ответил: ${connection.responseCode}" }
            val contentType = connection.contentType?.lowercase().orEmpty()
            require(contentType.startsWith("audio/") || contentType.contains("octet-stream")) {
                "Источник не вернул аудиофайл (Content-Type: ${contentType.ifBlank { "неизвестен" }})"
            }
            val folder = File(context.filesDir, "music").apply { mkdirs() }
            val name = "import_${System.currentTimeMillis()}.$extension"
            val target = File(folder, name)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 150L * 1024L * 1024L) { "Файл больше лимита 150 МБ" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(target.length() > 0) { "Скачанный файл пустой" }
            target
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
fun WaveSettingsDialog(
    database: DeratizationDatabase,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val playlists by database.playlistDao().observePlaylists().collectAsState(initial = emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки «Моей волны»") },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedId == null, onClick = { onSelect(null) })
                    Spacer(Modifier.width(8.dp))
                    Text("Все треки")
                }
                playlists.forEach { playlist ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(playlist.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedId == playlist.id, onClick = { onSelect(playlist.id) })
                        Spacer(Modifier.width(8.dp))
                        Text(playlist.name)
                    }
                }
                if (playlists.isEmpty()) Text("Создай плейлист в разделе «Плейлисты», чтобы выбрать его для волны.", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        containerColor = Panel
    )
}

@Composable
fun EqualizerDialog(equalizer: Equalizer?, onDismiss: () -> Unit) {
    var enabled by remember(equalizer) { mutableStateOf(equalizer?.enabled == true) }
    var bandValues by remember(equalizer) {
        mutableStateOf(
            equalizer?.let { eq ->
                (0 until eq.numberOfBands.toInt()).map { index ->
                    eq.getBandLevel(index.toShort()).toFloat()
                }
            } ?: emptyList()
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Эквалайзер") },
        text = {
            if (equalizer == null) {
                Text("Эквалайзер недоступен на этом устройстве или аудиосессия ещё не создана.", color = Muted)
            } else {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Включён", modifier = Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = {
                            enabled = it
                            try { equalizer.enabled = it } catch (_: Exception) {}
                        })
                    }
                    val bandCount = equalizer.numberOfBands.toInt()
                    (0 until bandCount).forEach { index ->
                        val range = equalizer.bandLevelRange
                        val centerHz = try { equalizer.getCenterFreq(index.toShort()) / 1000 } catch (_: Exception) { (index + 1) * 1000 }
                        Text("$centerHz Гц", color = Muted, fontSize = 12.sp)
                        Slider(
                            value = bandValues.getOrElse(index) { 0f },
                            onValueChange = { value ->
                                bandValues = bandValues.toMutableList().also { it[index] = value }
                                try { equalizer.setBandLevel(index.toShort(), value.toInt().toShort()) } catch (_: Exception) {}
                            },
                            valueRange = range[0].toFloat()..range[1].toFloat()
                        )
                    }
                    Text("Настройки зависят от возможностей аудиоэффектов устройства.", color = Muted, fontSize = 11.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        containerColor = Panel
    )
}

@Composable
fun HistoryScreen(database: DeratizationDatabase) {
    val history by database.historyDao().observeHistory().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("История", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Последние 200 прослушиваний", color = Muted, fontSize = 13.sp)
            }
            TextButton(onClick = { scope.launch { database.historyDao().clear() } }) { Text("Очистить", color = Pink) }
        }
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Здесь появятся прослушанные треки", color = Muted)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                items(history, key = { it.id }) { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) { AnimatedArtwork(Modifier.fillMaxSize()) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.artist, color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
