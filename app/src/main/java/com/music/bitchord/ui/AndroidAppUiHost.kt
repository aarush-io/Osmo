package com.music.bitchord.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.canvas.AppleArtistArtRepository
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.components.SlideDownBottomSheet

/** The shared pages' settings and downloads, answered from the phone's own stores. */
object AndroidAppUiHost : AppUiHost {
    override val homeRecentsViewType = AppSettings.homeRecentsViewType
    override fun setHomeRecentsViewType(value: LibraryViewType) = AppSettings.setHomeRecentsViewType(value)
    override val pinnedPlaylists = AppSettings.pinnedPlaylists
    override val librarySort = AppSettings.librarySort

    @Composable
    override fun downloadedIds(): Set<String> {
        val saved by Downloads.saved.collectAsStateWithLifecycle()
        return saved.keys
    }

    override suspend fun releaseMotion(album: String, artist: String): CanvasArtwork? {
        if (!AppSettings.animatedCanvas.value || album.isBlank() || artist.isBlank()) return null
        return CanvasRepository.canvasForAlbum(album, artist)
    }

    /**
     * The clip the artist page plays, off the same Apple lookup, so opening
     * the artist from the card costs nothing more.
     */
    override suspend fun artistMotion(browseId: String, artistName: String): CanvasArtwork? {
        if (!AppSettings.animatedCanvas.value) return null
        val art = AppleArtistArtRepository.artFor(artistName)
        return art?.videoUrl?.let { CanvasArtwork(url = it) }
    }

    /** The app's own sheet, as the Settings and page sheets are. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun MenuSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
        SlideDownBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
