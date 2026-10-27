package com.eddyizm.tempus.util

object ConstantsAA {
    // Android Auto max items
    const val MAX_ITEMS = 500
    const val MAX_SHUFFLE_ITEMS = 100

    // Android Auto displayed constants
    const val NUMBER_OF_DISPLAYED_ALBUMS = 15
    const val NUMBER_OF_DISPLAYED_PODCASTS = 100

    // Android Root function
    const val ROOT_ID = "[rootID]"
    // Android Auto tabs
    const val HOME_ID = "[homeID]"
    const val LIBRARY_ID = "[libraryID]"
    const val PLAYLIST_ID = "[playlistID]"
    const val RADIO_PODCAST_ID = "[radioPodcastID]"
    // Android Auto functions
    const val ALBUMS_ID = "[albumsID]"
    const val ARTISTS_ID = "[artistsID]"
    const val RANDOM_ID = "[randomID]"
    const val FOLDER_ID = "[folderID]"
    const val GENRES_ID = "[genresID]"
    const val DOWNLOADED_ID = "[downloadedID]"

    // Android Auto System functions
    const val INDEX_ID = "[indexID]"
    const val DIRECTORY_ID = "[directoryID]"
    const val ALBUM_ID = "[albumID]"
    // Followed by a letter range title such as "A–F"
    const val ALBUM_RANGE_ID = "[albumRangeID]"
    const val ARTIST_ID = "[artistID]"
    const val QUICKMIX_ID = "[quickmixID]"
    const val MYMIX_ID = "[mymixID]"
    const val DISCOVERYMIX_ID = "[discoverymixID]"

    // Android Auto Source tag
    const val QUEUE_CACHED_SOURCE = "[aaQueueCachedSource]"
    const val INSTANTMIX_SOURCE = "[instantMixSource]"
    const val MADE_FOR_YOU_SOURCE = "[madeForYouSource]"
    // Followed by the id of the node whose tracks are shuffled
    const val SHUFFLE_SOURCE = "[shuffleSource]"

    // Android Auto InstantMix constants
    const val MIN_TRACKS_SMALL_MIX = 20
    const val MIN_TRACKS_MEDIUM_MIX = 30
    const val MIN_TRACKS_LARGE_MIX = 40
    const val NUMBER_OF_TRACKS_IN_SMALL_MIX = 12
    const val NUMBER_OF_TRACKS_IN_MEDIUM_MIX = 15
    const val NUMBER_OF_TRACKS_IN_LARGE_MIX = 18

    // Android Auto MadeForYou constants
    const val NUMBER_OF_RECENT_ALBUMS_FOR_MIX = 20
    const val NUMBER_OF_RECENT_TRACKS_FOR_MIX = 50
}
