package com.eddyizm.tempus.service

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import com.eddyizm.tempus.BuildConfig
import com.eddyizm.tempus.repository.AutomotiveRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.eddyizm.tempus.R
import com.eddyizm.tempus.util.ConstantsAA
import com.eddyizm.tempus.util.Preferences
import androidx.core.net.toUri
import androidx.media3.session.SessionError
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val LIBRARY_COUNTS_TIMEOUT_SECONDS = 3L

private typealias ChildrenFuture = ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>

@UnstableApi
object MediaBrowserTree {
    private lateinit var appContext: Context
    private lateinit var automotiveRepository: AutomotiveRepository

    private var treeNodes: MutableMap<String, MediaItemNode> = mutableMapOf()

    private var isInitialized = false

    private val timeoutScheduler by lazy { Executors.newSingleThreadScheduledExecutor() }

    private val mixIds = listOf(ConstantsAA.QUICKMIX_ID, ConstantsAA.MYMIX_ID, ConstantsAA.DISCOVERYMIX_ID)

    private fun iconUri(resId: Int): Uri =
        "android.resource://${BuildConfig.APPLICATION_ID}/$resId".toUri()

    private class MediaItemNode(val item: MediaItem) {
        private val children: MutableList<MediaItem> = ArrayList()

        fun addChild(childID: String) {
            this.children.add(treeNodes[childID]!!.item)
        }

        fun getChildren(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val listenableFuture = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            val libraryResult = LibraryResult.ofItemList(children, null)

            listenableFuture.set(libraryResult)

            return listenableFuture
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildMediaItem(
        gridView: Boolean,
        title: String,
        mediaId: String,
        isPlayable: Boolean,
        isBrowsable: Boolean,
        mediaType: @MediaMetadata.MediaType Int,
        subtitleConfigurations: List<SubtitleConfiguration> = mutableListOf(),
        album: String? = null,
        artist: String? = null,
        genre: String? = null,
        sourceUri: Uri? = null,
        imageUri: Uri? = null
    ): MediaItem {
        val extras = Bundle()
        if( gridView ) {
                extras.apply {
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                )
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                )
            }
        }
        else{
            extras.apply {
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                )
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                )
            }
        }

        val metadata = MediaMetadata.Builder()
            .setAlbumTitle(album)
            .setTitle(title)
            .setArtist(artist)
            .setGenre(genre)
            .setIsBrowsable(isBrowsable)
            .setIsPlayable(isPlayable)
            .setArtworkUri(imageUri)
            .setMediaType(mediaType)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setSubtitleConfigurations(subtitleConfigurations)
            .setMediaMetadata(metadata)
            .setUri(sourceUri)
            .build()
    }
    fun initialize(
        context: Context,
        automotiveRepository: AutomotiveRepository) {
        this.automotiveRepository = automotiveRepository
        appContext = context.applicationContext
        if (isInitialized) return

        isInitialized = true
    }

    private fun addNode(
        gridView: Boolean,
        titleRes: Int,
        mediaId: String,
        iconRes: Int,
        mediaType: @MediaMetadata.MediaType Int
    ) {
        treeNodes[mediaId] = MediaItemNode(
            buildMediaItem(
                gridView = gridView,
                title = appContext.getString(titleRes),
                mediaId = mediaId,
                isPlayable = false,
                isBrowsable = true,
                imageUri = iconUri(iconRes),
                mediaType = mediaType
            )
        )
    }

    private fun addMix(id: String, titleRes: Int, descriptionRes: Int, trackCount: Int, iconRes: Int) {
        val mix = buildMediaItem(
            gridView = false,
            title = appContext.getString(titleRes),
            mediaId = ConstantsAA.MADE_FOR_YOU_SOURCE + "[" + trackCount + "]" + id,
            isPlayable = true,
            isBrowsable = false,
            imageUri = iconUri(iconRes),
            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
        )
        treeNodes[id] = MediaItemNode(AutomotiveRepository.withSubtitle(mix, appContext.getString(descriptionRes)))
    }

    fun buildTree() {
        treeNodes.clear()

        treeNodes[ConstantsAA.ROOT_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = "Root Folder",
                    mediaId = ConstantsAA.ROOT_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        addNode(true, R.string.aa_home, ConstantsAA.HOME_ID, R.drawable.ic_aa_home, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        addNode(false, R.string.aa_library, ConstantsAA.LIBRARY_ID, R.drawable.ic_aa_title, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        addNode(true, R.string.aa_playlists, ConstantsAA.PLAYLIST_ID, R.drawable.ic_aa_playlist, MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
        addNode(false, R.string.aa_radio_podcasts, ConstantsAA.RADIO_PODCAST_ID, R.drawable.ic_aa_podcasts, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

        addNode(true, R.string.aa_artists, ConstantsAA.ARTISTS_ID, R.drawable.ic_aa_artists, MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
        addNode(true, R.string.aa_albums, ConstantsAA.ALBUMS_ID, R.drawable.ic_aa_albums, MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
        addNode(false, R.string.aa_genres, ConstantsAA.GENRES_ID, R.drawable.ic_aa_genres, MediaMetadata.MEDIA_TYPE_FOLDER_GENRES)
        addNode(false, R.string.aa_music_folder, ConstantsAA.FOLDER_ID, R.drawable.ic_aa_folders, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        addNode(false, R.string.download_title_section, ConstantsAA.DOWNLOADED_ID, R.drawable.ic_aa_downloaded, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

        treeNodes[ConstantsAA.SHUFFLE_SOURCE + ConstantsAA.RANDOM_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_shuffle_all),
                    mediaId = ConstantsAA.SHUFFLE_SOURCE + ConstantsAA.RANDOM_ID,
                    isPlayable = true,
                    isBrowsable = false,
                    imageUri = iconUri(R.drawable.ic_aa_random),
                    mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
                )
            )

        addMix(ConstantsAA.QUICKMIX_ID, R.string.aa_quick_mix, R.string.aa_quick_mix_description,
            ConstantsAA.NUMBER_OF_TRACKS_IN_SMALL_MIX, R.drawable.ic_aa_quickmix)
        addMix(ConstantsAA.MYMIX_ID, R.string.aa_my_mix, R.string.aa_my_mix_description,
            ConstantsAA.NUMBER_OF_TRACKS_IN_MEDIUM_MIX, R.drawable.ic_aa_mymix)
        addMix(ConstantsAA.DISCOVERYMIX_ID, R.string.aa_discovery_mix, R.string.aa_discovery_mix_description,
            ConstantsAA.NUMBER_OF_TRACKS_IN_LARGE_MIX, R.drawable.ic_aa_discoverymix)

        val root = treeNodes[ConstantsAA.ROOT_ID]!!
        root.addChild(ConstantsAA.HOME_ID)
        root.addChild(ConstantsAA.LIBRARY_ID)
        root.addChild(ConstantsAA.PLAYLIST_ID)
        root.addChild(ConstantsAA.RADIO_PODCAST_ID)
    }

    fun getRootItem(): MediaItem {
        return treeNodes[ConstantsAA.ROOT_ID]!!.item
    }

    // A section that fails to load is left out rather than failing the whole tab
    private fun sections(vararg sections: Pair<String, ChildrenFuture>): ChildrenFuture =
        Futures.whenAllComplete(sections.map { it.second }).call({
            val items = sections.flatMap { (title, future) ->
                val children = runCatching { Futures.getDone(future).value }.getOrNull() ?: emptyList()
                children.map { AutomotiveRepository.withGroupTitle(it, title) }
            }
            LibraryResult.ofItemList(items, null)
        }, MoreExecutors.directExecutor())

    private fun getHome(): ChildrenFuture = sections(
        appContext.getString(R.string.aa_mixes) to
                Futures.immediateFuture(LibraryResult.ofItemList(mixIds.map { treeNodes[it]!!.item }, null)),
        appContext.getString(R.string.aa_recently_added) to automotiveRepository.getAlbums(
            ConstantsAA.ALBUM_ID,
            "newest",
            ConstantsAA.NUMBER_OF_DISPLAYED_ALBUMS,
            false),
        appContext.getString(R.string.aa_starred_albums) to automotiveRepository.getStarredAlbums(ConstantsAA.ALBUM_ID)
    )

    private fun getLibrary(): ChildrenFuture {
        // The counts only label the rows: offline, the Downloads row must not wait for the
        // server requests to time out.
        val counts = Futures.catching(
            Futures.withTimeout(
                automotiveRepository.libraryCounts,
                LIBRARY_COUNTS_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
                timeoutScheduler),
            Exception::class.java,
            { emptyMap() },
            MoreExecutors.directExecutor())

        return Futures.transform(counts, { counts ->
            fun row(nodeId: String, countKey: String, pluralRes: Int): MediaItem {
                val item = treeNodes[nodeId]!!.item
                val count = counts[countKey] ?: return item
                return AutomotiveRepository.withSubtitle(item, appContext.resources.getQuantityString(pluralRes, count, count))
            }

            LibraryResult.ofItemList(listOf(
                row(ConstantsAA.ARTISTS_ID, ConstantsAA.ARTISTS_ID, R.plurals.aa_artist_count),
                row(ConstantsAA.ALBUMS_ID, ConstantsAA.ALBUMS_ID, R.plurals.aa_album_count),
                row(ConstantsAA.GENRES_ID, ConstantsAA.GENRES_ID, R.plurals.aa_genre_count),
                row(ConstantsAA.FOLDER_ID, ConstantsAA.FOLDER_ID, R.plurals.aa_folder_count),
                treeNodes[ConstantsAA.DOWNLOADED_ID]!!.item,
                row(ConstantsAA.SHUFFLE_SOURCE + ConstantsAA.RANDOM_ID, ConstantsAA.RANDOM_ID, R.plurals.player_queue_song_count)
            ), null)
        }, MoreExecutors.directExecutor())
    }

    private fun getRadioAndPodcasts(): ChildrenFuture {
        val stations = Futures.transform(automotiveRepository.internetRadioStations, { result ->
            val withHosts = result.value?.map { station ->
                station.localConfiguration?.uri?.host?.let { AutomotiveRepository.withSubtitle(station, it) } ?: station
            } ?: return@transform result
            LibraryResult.ofItemList(withHosts, null)
        }, MoreExecutors.directExecutor())

        return sections(
            appContext.getString(R.string.aa_radio_stations) to stations,
            appContext.getString(R.string.aa_new_episodes) to
                    automotiveRepository.getNewestPodcastEpisodes(ConstantsAA.NUMBER_OF_DISPLAYED_PODCASTS)
        )
    }

    fun getChildren(
        id: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return when (id) {
            ConstantsAA.ROOT_ID -> treeNodes[ConstantsAA.ROOT_ID]?.getChildren()!!

            ConstantsAA.HOME_ID -> getHome()
            ConstantsAA.LIBRARY_ID -> getLibrary()
            ConstantsAA.PLAYLIST_ID -> automotiveRepository.getPlaylists(ConstantsAA.PLAYLIST_ID)
            ConstantsAA.RADIO_PODCAST_ID -> getRadioAndPodcasts()

            ConstantsAA.ALBUMS_ID -> automotiveRepository.getAlbums(
                ConstantsAA.ALBUM_ID,
                "alphabeticalByName",
                ConstantsAA.MAX_ITEMS,
                true)
            ConstantsAA.ARTISTS_ID -> automotiveRepository.getArtists(ConstantsAA.ARTIST_ID)
            ConstantsAA.FOLDER_ID -> automotiveRepository.getMusicFolders(ConstantsAA.FOLDER_ID)
            ConstantsAA.DOWNLOADED_ID -> automotiveRepository.getDownloadedSongs()
            ConstantsAA.RANDOM_ID -> automotiveRepository.getRandomSongs(ConstantsAA.MAX_SHUFFLE_ITEMS)
            ConstantsAA.GENRES_ID -> automotiveRepository.getGenres(ConstantsAA.GENRES_ID)

            else -> {
                if (id.startsWith(ConstantsAA.GENRES_ID)) {
                    val shuffle = Preferences.isAndroidAutoShuffleGenreSongsEnabled()
                    // If the user doesn't want random songs, it's likely it's for perusing them, so provide as many as possible
                    val count = if (shuffle) ConstantsAA.MAX_SHUFFLE_ITEMS else ConstantsAA.MAX_ITEMS
                    return automotiveRepository.getSongsByGenre(id.removePrefix(ConstantsAA.GENRES_ID), count, shuffle)
                }

                if (id.startsWith(ConstantsAA.PLAYLIST_ID)) {
                    return automotiveRepository.getPlaylistSongs(id.removePrefix(ConstantsAA.PLAYLIST_ID))
                }

                if (id.startsWith(ConstantsAA.ALBUM_ID)) {
                    return automotiveRepository.getAlbumTracks(id.removePrefix(ConstantsAA.ALBUM_ID))
                }

                if (id.startsWith(ConstantsAA.ARTIST_ID)) {
                    return automotiveRepository.getArtistAlbum(ConstantsAA.ALBUM_ID,id.removePrefix(ConstantsAA.ARTIST_ID))
                }

                if (id.startsWith(ConstantsAA.FOLDER_ID)) {
                    return automotiveRepository.getIndexes(ConstantsAA.INDEX_ID,id.removePrefix(ConstantsAA.FOLDER_ID))
                }

                if (id.startsWith(ConstantsAA.INDEX_ID)) {
                    return automotiveRepository.getDirectories(ConstantsAA.DIRECTORY_ID,id.removePrefix(ConstantsAA.INDEX_ID))
                }

                if (id.startsWith(ConstantsAA.DIRECTORY_ID)) {
                    return automotiveRepository.getDirectories(ConstantsAA.DIRECTORY_ID,id.removePrefix(ConstantsAA.DIRECTORY_ID))
                }

                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            }
        }
    }

    fun search(query: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return automotiveRepository.search(
            query,
            ConstantsAA.ALBUM_ID,
            ConstantsAA.ARTIST_ID
        )
    }
}
