package com.eddyizm.tempus.repository;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaConstants;
import androidx.media3.session.SessionError;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.BuildConfig;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.database.dao.SessionMediaItemDao;
import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.model.InternetRadioStationCache;
import com.eddyizm.tempus.model.SessionMediaItem;
import com.eddyizm.tempus.provider.AlbumArtContentProvider;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.AlbumWithSongsID3;
import com.eddyizm.tempus.subsonic.models.Artist;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Directory;
import com.eddyizm.tempus.subsonic.models.DiscTitle;
import com.eddyizm.tempus.subsonic.models.Index;
import com.eddyizm.tempus.subsonic.models.IndexID3;
import com.eddyizm.tempus.subsonic.models.InternetRadioStation;
import com.eddyizm.tempus.subsonic.models.MusicFolder;
import com.eddyizm.tempus.subsonic.models.Playlist;
import com.eddyizm.tempus.subsonic.models.PlaylistWithSongs;
import com.eddyizm.tempus.subsonic.models.PodcastEpisode;
import com.eddyizm.tempus.subsonic.models.SubsonicResponse;
import com.eddyizm.tempus.subsonic.models.Genre;
import com.eddyizm.tempus.util.ConstantsAA;
import com.eddyizm.tempus.util.MappingUtil;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.util.Preferences;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@UnstableApi
public class AutomotiveRepository {
    private final SessionMediaItemDao sessionMediaItemDao = AppDatabase.getInstance().sessionMediaItemDao();

    private static final String TAG = "AutomotiveRepository";

    private static final int MIN_LETTER_GROUP_SIZE = 6;
    private static final int TARGET_LETTER_GROUPS = 8;

    public final InstantMixBuilder instantMixBuilder = new InstantMixBuilder(this);
    public final MadeForYouBuilder madeForYouBuilder = new MadeForYouBuilder(this);

    private Bundle createContentStyleExtras(boolean gridView) {
        Bundle extras = new Bundle();
        int contentStyle = gridView
                ? MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                : MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM;
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, contentStyle);
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, contentStyle);
        return extras;
    }

    // Media3 hands the car the artist as a row's subtitle unless a display title is set, so the
    // display title is what lets a row show its own subtitle.
    public static MediaItem withSubtitle(MediaItem item, String subtitle) {
        MediaMetadata metadata = item.mediaMetadata.buildUpon()
                .setDisplayTitle(item.mediaMetadata.title)
                .setSubtitle(subtitle)
                .build();
        return item.buildUpon().setMediaMetadata(metadata).build();
    }

    public static MediaItem withGroupTitle(MediaItem item, String groupTitle) {
        Bundle extras = new Bundle();
        extras.putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, groupTitle);
        return withExtras(item, extras);
    }

    private static MediaItem withSingleItemStyle(MediaItem item, boolean gridView) {
        Bundle extras = new Bundle();
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM, gridView
                ? MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                : MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM);
        return withExtras(item, extras);
    }

    private static MediaItem withExtras(MediaItem item, Bundle added) {
        Bundle extras = item.mediaMetadata.extras == null ? new Bundle() : new Bundle(item.mediaMetadata.extras);
        extras.putAll(added);
        return item.buildUpon()
                .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
                .build();
    }

    private static String joinSubtitle(String first, String second) {
        if (first == null || first.isEmpty()) return second;
        if (second == null || second.isEmpty()) return first;
        return App.getContext().getString(R.string.aa_subtitle_pair, first, second);
    }

    private static String songCountAndLength(int songCount, long durationSeconds) {
        return joinSubtitle(
                App.getContext().getResources().getQuantityString(R.plurals.player_queue_song_count, songCount, songCount),
                MusicUtil.getReadablePodcastDurationString(durationSeconds));
    }

    private static MediaItem createShuffle(String title, String subtitle, String shuffledNodeId) {
        MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setArtworkUri(Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.ic_aa_random))
                .build();

        MediaItem item = new MediaItem.Builder()
                .setMediaId(ConstantsAA.SHUFFLE_SOURCE + shuffledNodeId)
                .setMediaMetadata(mediaMetadata)
                .build();
        return withSubtitle(item, subtitle);
    }

    private static String titleOf(MediaItem item) {
        return item.mediaMetadata.title == null ? "" : item.mediaMetadata.title.toString();
    }

    static String letterKey(String name) {
        if (name == null || name.isEmpty()) return "#";
        char first = Character.toUpperCase(Normalizer.normalize(name.substring(0, 1), Normalizer.Form.NFD).charAt(0));
        return first >= 'A' && first <= 'Z' ? String.valueOf(first) : "#";
    }

    // Sorts the items and titles them by letter range ("A–F", "G–M", …) so the car's A–Z jump
    // lands on a section title. Ranges hold whole letters and grow with the library size.
    static List<MediaItem> groupByLetterRange(List<MediaItem> items) {
        List<MediaItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing((MediaItem item) -> letterKey(titleOf(item)))
                .thenComparing(AutomotiveRepository::titleOf, String.CASE_INSENSITIVE_ORDER));

        List<String> titles = letterRangeTitles(sorted.stream().map(AutomotiveRepository::titleOf).collect(Collectors.toList()));
        for (int i = 0; i < sorted.size(); i++) {
            sorted.set(i, withGroupTitle(sorted.get(i), titles.get(i)));
        }
        return sorted;
    }

    static List<String> letterRangeTitles(List<String> namesSortedByLetter) {
        int size = namesSortedByLetter.size();
        int minPerGroup = Math.max(MIN_LETTER_GROUP_SIZE, (size + TARGET_LETTER_GROUPS - 1) / TARGET_LETTER_GROUPS);
        List<String> keys = namesSortedByLetter.stream().map(AutomotiveRepository::letterKey).collect(Collectors.toList());

        List<Integer> groupEnds = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < size; i++) {
            boolean lastOfLetter = i + 1 == size || !keys.get(i + 1).equals(keys.get(i));
            if (lastOfLetter && i + 1 - start >= minPerGroup) {
                groupEnds.add(i + 1);
                start = i + 1;
            }
        }
        if (start < size) {
            if (!groupEnds.isEmpty() && size - start < minPerGroup / 2) {
                groupEnds.set(groupEnds.size() - 1, size);
            } else {
                groupEnds.add(size);
            }
        }

        List<String> titles = new ArrayList<>();
        int from = 0;
        for (int end : groupEnds) {
            String first = keys.get(from);
            String last = keys.get(end - 1);
            String title = first.equals(last) ? first : first + "–" + last;
            for (int i = from; i < end; i++) titles.add(title);
            from = end;
        }
        return titles;
    }

    private static <T> ListenableFuture<T> fetch(Call<ApiResponse> call, Function<SubsonicResponse, T> read) {
        final SettableFuture<T> future = SettableFuture.create();

        call.enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                SubsonicResponse subsonicResponse = getSubsonicResponseOrNull(response);
                T value = null;
                if (response.isSuccessful() && subsonicResponse != null) {
                    try {
                        value = read.apply(subsonicResponse);
                    } catch (RuntimeException e) {
                        // A missing list in the response leaves the value unknown
                    }
                }
                future.set(value);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                future.set(null);
            }
        });

        return future;
    }

    // Counts keyed by the Library row they label; a count the server can't give is left out.
    public ListenableFuture<Map<String, Integer>> getLibraryCounts() {
        ListenableFuture<List<ArtistID3>> artists = fetch(
                App.getSubsonicClientInstance(false).getBrowsingClient().getArtists(),
                response -> response.getArtists().getIndices().stream()
                        .filter(index -> index.getArtists() != null)
                        .flatMap(index -> index.getArtists().stream())
                        .collect(Collectors.toList()));
        ListenableFuture<Integer> genres = fetch(
                App.getSubsonicClientInstance(false).getBrowsingClient().getGenres(),
                response -> response.getGenres().getGenres().size());
        ListenableFuture<Integer> folders = fetch(
                App.getSubsonicClientInstance(false).getBrowsingClient().getMusicFolders(),
                response -> response.getMusicFolders().getMusicFolders().size());
        ListenableFuture<Long> songs = fetch(
                App.getSubsonicClientInstance(false).getMediaLibraryScanningClient().getScanStatus(),
                response -> response.getScanStatus().getCount());

        return Futures.whenAllComplete(artists, genres, folders, songs).call(() -> {
            Map<String, Integer> counts = new HashMap<>();
            List<ArtistID3> artistList = Futures.getDone(artists);
            if (artistList != null) {
                counts.put(ConstantsAA.ARTISTS_ID, artistList.size());
                // Subsonic has no album count; an album shared by several album artists counts once for each
                counts.put(ConstantsAA.ALBUMS_ID, artistList.stream().mapToInt(ArtistID3::getAlbumCount).sum());
            }
            if (Futures.getDone(genres) != null) counts.put(ConstantsAA.GENRES_ID, Futures.getDone(genres));
            if (Futures.getDone(folders) != null) counts.put(ConstantsAA.FOLDER_ID, Futures.getDone(folders));
            if (Futures.getDone(songs) != null) counts.put(ConstantsAA.RANDOM_ID, Futures.getDone(songs).intValue());
            return counts;
        }, MoreExecutors.directExecutor());
    }

    private MediaItem createArtist(String artistName, String id, String artistCoverArtId){
        Uri artworkUri = (artistCoverArtId != null && !artistCoverArtId.isEmpty())
                ? AlbumArtContentProvider.contentUri(artistCoverArtId)
                : Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.ic_aa_artists);

        MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                .setTitle(artistName)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                .setArtworkUri(artworkUri)
                .setExtras(createContentStyleExtras(true))
                .build();

        return new MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(mediaMetadata)
                .setUri("")
                .build();
    }

    private MediaItem createAlbum(String albumName, String artirstName, String genre, String id, boolean isPlayable, String albumCoverArtId){
        Uri artworkUri = (albumCoverArtId != null && !albumCoverArtId.isEmpty())
                ? AlbumArtContentProvider.contentUri(albumCoverArtId)
                : Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.ic_aa_albums);

        MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                .setTitle(albumName)
                .setAlbumTitle(albumName)
                .setArtist(artirstName)
                .setGenre(genre)
                .setIsBrowsable(!isPlayable)
                .setIsPlayable(isPlayable)
                .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                .setArtworkUri(artworkUri)
                .build();

        return new MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(mediaMetadata)
                .setUri("")
                .build();
    }

    private static SubsonicResponse getSubsonicResponseOrNull(Response<ApiResponse> response) {
        try {
            return response.body() != null ? response.body().getSubsonicResponse() : null;
        } catch (RuntimeException e) {
            // subsonicResponse is a lateinit property: a body without "subsonic-response" throws on access instead of returning null
            return null;
        }
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getAlbums(String prefix, String type, int size, boolean groupByLetter) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();
        if (size > ConstantsAA.MAX_ITEMS) size = ConstantsAA.MAX_ITEMS;
        final int maxSize = size;

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2(type, maxSize, 0, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbumList2() != null && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getAlbumList2().getAlbums();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (AlbumID3 album : albums) {
                                MediaItem mediaItem = createAlbum(
                                        album.getName(),
                                        album.getArtist(),
                                        album.getGenre(),
                                        prefix + album.getId(),
                                        false,
                                        album.getCoverArtId()
                                );
                                mediaItems.add(mediaItem);
                            }

                            if (groupByLetter) {
                                mediaItems = groupByLetterRange(mediaItems);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getArtists(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtists()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getSubsonicResponse().getArtists() != null
                                && response.body().getSubsonicResponse().getArtists().getIndices() != null) {

                            List<IndexID3> indices = response.body().getSubsonicResponse().getArtists().getIndices();
                            List<MediaItem> mediaItems = new ArrayList<>();

                            int count = 0;
                            for (IndexID3 index : indices) {
                                if (index.getArtists() != null && count < ConstantsAA.MAX_ITEMS) {
                                    for (ArtistID3 artist : index.getArtists()) {
                                        if (count >= ConstantsAA.MAX_ITEMS) break;

                                        MediaItem mediaItem = createArtist(
                                                artist.getName(),
                                                prefix + artist.getId(),
                                                artist.getCoverArtId()
                                        );
                                        String albumCount = App.getContext().getResources().getQuantityString(
                                                R.plurals.aa_album_count, artist.getAlbumCount(), artist.getAlbumCount());

                                        mediaItems.add(withSubtitle(mediaItem, albumCount));
                                        count++;
                                    }
                                }
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(groupByLetterRange(mediaItems)), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });
        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getRandomSongs(int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getRandomSongs(count, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getRandomSongs() != null && response.body().getSubsonicResponse().getRandomSongs().getSongs() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getRandomSongs().getSongs();

                            setChildrenMetadata(songs);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs, ConstantsAA.QUEUE_CACHED_SOURCE);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getDownloadedSongs() {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        new Thread(() -> {
            // Any throw here must resolve the future, otherwise the Android Auto
            // browse request hangs forever with no error feedback.
            try {
                List<Download> downloads = AppDatabase.getInstance().downloadDao().getAllSync();

                if (downloads != null && !downloads.isEmpty()) {
                    if( !Preferences.isAndroidAutoShuffleDownloadedTracksEnabled() ) {
                        downloads = downloads.subList(0, Math.min(ConstantsAA.MAX_ITEMS, downloads.size()));
                    }
                    else {
                        Collections.shuffle(downloads);
                        downloads = downloads.subList(0, Math.min(ConstantsAA.MAX_SHUFFLE_ITEMS, downloads.size()));
                    }

                    List<Child> songs = new ArrayList<>(downloads);

                    setChildrenMetadata(songs);

                    List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs, ConstantsAA.QUEUE_CACHED_SOURCE);

                    listenableFuture.set(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null));
                } else {
                    listenableFuture.set(LibraryResult.ofItemList(ImmutableList.of(), null));
                }
            } catch (Exception e) {
                listenableFuture.setException(e);
            }
        }).start();

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getStarredAlbums(String prefix) {

        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null && response.body().getSubsonicResponse().getStarred2().getAlbums() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getStarred2().getAlbums();
                            albums = albums.subList(0, Math.min(ConstantsAA.MAX_ITEMS, albums.size()));

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (AlbumID3 album : albums) {
                                MediaItem mediaItem = createAlbum(
                                        album.getName(),
                                        album.getArtist(),
                                        album.getGenre(),
                                        prefix + album.getId(),
                                        false,
                                        album.getCoverArtId()
                                );
                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getMusicFolders(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicFolders()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getMusicFolders() != null && response.body().getSubsonicResponse().getMusicFolders().getMusicFolders() != null) {
                            List<MusicFolder> musicFolders = response.body().getSubsonicResponse().getMusicFolders().getMusicFolders();

                            List<MediaItem> mediaItems = new ArrayList<>();
                            Uri artworkUri = Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.ic_aa_folders);

                            for (MusicFolder musicFolder : musicFolders) {
                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(musicFolder.getName())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + musicFolder.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getIndexes(String prefix, String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getIndexes(id, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        SubsonicResponse subsonicResponse = getSubsonicResponseOrNull(response);
                        if (response.isSuccessful() && subsonicResponse != null && subsonicResponse.getIndexes() != null) {
                            List<MediaItem> mediaItems = new ArrayList<>();

                            if (response.body().getSubsonicResponse().getIndexes().getIndices() != null) {
                                List<Index> indices = response.body().getSubsonicResponse().getIndexes().getIndices();

                                for (Index index : indices) {
                                    if (index.getArtists() != null) {
                                        for (Artist artist : index.getArtists()) {
                                            MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                                    .setTitle(artist.getName())
                                                    .setIsBrowsable(true)
                                                    .setIsPlayable(false)
                                                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                                                    .build();

                                            MediaItem mediaItem = new MediaItem.Builder()
                                                    .setMediaId(prefix + artist.getId())
                                                    .setMediaMetadata(mediaMetadata)
                                                    .setUri("")
                                                    .build();

                                            mediaItems.add(mediaItem);
                                        }
                                    }
                                }
                            }

                            if (response.body().getSubsonicResponse().getIndexes().getChildren() != null) {
                                List<Child> children = response.body().getSubsonicResponse().getIndexes().getChildren();

                                for (Child song : children) {
                                    Uri artworkUri = AlbumArtContentProvider.contentUri(song.getCoverArtId());

                                    MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                            .setTitle(song.getTitle())
                                            .setAlbumTitle(song.getAlbum())
                                            .setArtist(song.getArtist())
                                            .setIsBrowsable(false)
                                            .setIsPlayable(true)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                            .setArtworkUri(artworkUri)
                                            .build();

                                    MediaItem mediaItem = new MediaItem.Builder()
                                            .setMediaId(prefix + song.getId())
                                            .setMediaMetadata(mediaMetadata)
                                            .setUri(MusicUtil.getStreamUri(song.getId()))
                                            .build();

                                    mediaItems.add(mediaItem);
                                }

                                setChildrenMetadata(children);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getDirectories(String prefix, String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicDirectory(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        SubsonicResponse subsonicResponse = getSubsonicResponseOrNull(response);
                        if (response.isSuccessful() && subsonicResponse != null && subsonicResponse.getDirectory() != null && subsonicResponse.getDirectory().getChildren() != null) {
                            Directory directory = response.body().getSubsonicResponse().getDirectory();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (Child child : directory.getChildren()) {
                                Uri artworkUri = AlbumArtContentProvider.contentUri(child.getCoverArtId());

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(child.getTitle())
                                        .setIsBrowsable(child.isDir())
                                        .setIsPlayable(!child.isDir())
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(child.isDir() ? prefix + child.getId() : child.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri(!child.isDir() ? MusicUtil.getStreamUri(child.getId()) : Uri.parse(""))
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            setChildrenMetadata(directory.getChildren().stream().filter(child -> !child.isDir()).collect(Collectors.toList()));

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getPlaylists(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylists()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlaylists() != null && response.body().getSubsonicResponse().getPlaylists().getPlaylists() != null) {
                            List<Playlist> playlists = response.body().getSubsonicResponse().getPlaylists().getPlaylists();
                            playlists = playlists.subList(0, Math.min(ConstantsAA.MAX_ITEMS, playlists.size()));

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (Playlist playlist : playlists) {
                                String coverId = playlist.getCoverArtId();
                                Uri artworkUri = (coverId != null && !coverId.isEmpty())
                                        ? AlbumArtContentProvider.contentUri(coverId)
                                        : Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.ic_aa_playlist);

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(playlist.getName())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + playlist.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(withSubtitle(mediaItem, songCountAndLength(playlist.getSongCount(), playlist.getDuration())));
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getNewestPodcastEpisodes(int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .getNewestPodcasts(count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getNewestPodcasts() != null && response.body().getSubsonicResponse().getNewestPodcasts().getEpisodes() != null) {
                            List<PodcastEpisode> episodes = response.body().getSubsonicResponse().getNewestPodcasts().getEpisodes();

                            Map<String, Long> resumePositions = new HashMap<>();
                            Preferences.getContinueListening().forEach(point -> resumePositions.put(point.getId(), point.getPosition()));

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (PodcastEpisode episode : episodes) {
                                Uri artworkUri = AlbumArtContentProvider.contentUri(episode.getCoverArtId());

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(episode.getTitle())
                                        .setIsBrowsable(false)
                                        .setIsPlayable(true)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(episode.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri(MusicUtil.getStreamUri(episode.getStreamId()))
                                        .build();

                                int durationSeconds = episode.getDuration() != null ? episode.getDuration() : 0;
                                mediaItem = withSubtitle(mediaItem, joinSubtitle(
                                        episode.getAlbum() != null ? episode.getAlbum() : episode.getArtist(),
                                        durationSeconds > 0 ? MusicUtil.getReadablePodcastDurationString(durationSeconds) : null));

                                // Only a started episode has a known state: a finished one leaves no resume point behind
                                Long position = resumePositions.get(episode.getId());
                                if (position != null && durationSeconds > 0) {
                                    Bundle completion = new Bundle();
                                    completion.putInt(MediaConstants.EXTRAS_KEY_COMPLETION_STATUS, MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED);
                                    completion.putDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE, Math.min(1.0, position / (durationSeconds * 1000.0)));
                                    mediaItem = withExtras(mediaItem, completion);
                                }

                                mediaItems.add(mediaItem);
                            }

                            setPodcastEpisodesMetadata(episodes);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getInternetRadioStations() {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getInternetRadioClient()
                .getInternetRadioStations()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        // Server radios are optional here: a server with no stations omits the list, but
                        // the user's locally-added radios must still appear. Collect whatever the server
                        // returned (possibly none); the local cache is always merged in below. See #810.
                        final List<InternetRadioStation> serverStations = new ArrayList<>();
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getInternetRadioStations() != null && response.body().getSubsonicResponse().getInternetRadioStations().getInternetRadioStations() != null) {
                            serverStations.addAll(response.body().getSubsonicResponse().getInternetRadioStations().getInternetRadioStations());
                        }

                        new Thread(() -> {
                            List<InternetRadioStation> localStations = new ArrayList<>();
                            for (InternetRadioStationCache cache : AppDatabase.getInstance().internetRadioStationDao().getLocal()) {
                                localStations.add(cache.toInternetRadioStation());
                            }

                            List<InternetRadioStation> radioStations = mergeAndSortRadioStations(serverStations, localStations);

                            List<MediaItem> mediaItems = new ArrayList<>();
                            for (InternetRadioStation radioStation : radioStations) {
                                mediaItems.add(MappingUtil.mapInternetRadioStation(radioStation));
                            }

                            setInternetRadioStationsMetadata(radioStations);

                            listenableFuture.set(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null));
                        }).start();
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    // Merge the server's internet radios with the user's locally-added ones, sorted by name
    // (case-insensitive, null names first). Pulled out so the #810 behavior, locals always
    // included even when the server returns none, is covered by a unit test.
    static List<InternetRadioStation> mergeAndSortRadioStations(
            List<InternetRadioStation> serverStations,
            List<InternetRadioStation> localStations) {
        List<InternetRadioStation> merged = new ArrayList<>();
        if (serverStations != null) merged.addAll(serverStations);
        if (localStations != null) merged.addAll(localStations);
        merged.sort(java.util.Comparator.comparing(
                station -> station.getName() == null ? "" : station.getName(),
                String.CASE_INSENSITIVE_ORDER));
        return merged;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getAlbumTracks(String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getAlbum(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbum() != null && response.body().getSubsonicResponse().getAlbum().getSongs() != null) {
                            AlbumWithSongsID3 album = response.body().getSubsonicResponse().getAlbum();
                            List<Child> tracks = album.getSongs();

                            setChildrenMetadata(tracks);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(tracks, ConstantsAA.QUEUE_CACHED_SOURCE);
                            for (int i = 0; i < mediaItems.size(); i++) {
                                Child track = tracks.get(i);
                                mediaItems.set(i, withSubtitle(mediaItems.get(i), joinSubtitle(
                                        track.getTrack() != null ? String.valueOf(track.getTrack()) : null,
                                        MusicUtil.getReadableDurationString(track.getDuration(), false))));
                            }

                            setMultiDiscTitleGrouping(App.getContext(), album.getDiscTitles(), mediaItems);

                            mediaItems.add(0, createShuffle(
                                    App.getContext().getString(R.string.aa_shuffle_album),
                                    songCountAndLength(tracks.size(), album.getDuration() != null ? album.getDuration() : 0),
                                    ConstantsAA.ALBUM_ID + id));

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    static void setMultiDiscTitleGrouping(Context context, List<DiscTitle> discTitles, List<MediaItem> mediaItems) {
        Map<Integer, String> discTitleMap = getMultiDiscTitles(context, discTitles, mediaItems);

        if (discTitleMap.isEmpty()) {
            return;
        }

        for (int i = 0; i < mediaItems.size(); i++) {
            var mediaItem = mediaItems.get(i);
            var disc = mediaItem.mediaMetadata.discNumber;

            if (disc == null) {
                continue;
            }

            Bundle extras = mediaItem.mediaMetadata.extras == null ? new Bundle() : new Bundle(mediaItem.mediaMetadata.extras);

            extras.putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, discTitleMap.get(disc));

            var metadata = mediaItem.mediaMetadata.buildUpon().setExtras(extras).build();

            mediaItems.set(i, mediaItem.buildUpon().setMediaMetadata(metadata).build());
        }
    }

    static Map<Integer, String> getMultiDiscTitles(Context context, List<DiscTitle> discTitles, List<MediaItem> mediaItems) {
        var discs = mediaItems.stream()
                .map(it -> it.mediaMetadata.discNumber)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (discs.size() < 2) {
            return Collections.emptyMap();
        }

        Map<Integer, String> discTitleMap = new HashMap<>();

        if (discTitles != null && !discTitles.isEmpty()) {
            discTitles.forEach(it -> {
                var disc = it.getDisc();
                var title = it.getTitle();

                if (disc == null || title == null || title.isBlank()) {
                    return;
                }

                var discTitle = context.getString(R.string.disc_titlefull, disc.toString(), title);

                discTitleMap.put(disc, discTitle);
            });
        }

        discs.forEach(disc -> discTitleMap.computeIfAbsent(disc, it -> context.getString(R.string.disc_titleless, it.toString())));

        return discTitleMap;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getArtistAlbum(String prefix, String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        SubsonicResponse subsonicResponse = getSubsonicResponseOrNull(response);
                        if (response.isSuccessful() && subsonicResponse != null && subsonicResponse.getArtist() != null && subsonicResponse.getArtist().getAlbums() != null) {

                            List<AlbumID3> albums = response.body().getSubsonicResponse().getArtist().getAlbums();

                            List<MediaItem> mediaItems = new ArrayList<>();
                            int totalTracks = 0;

                            for (AlbumID3 album : albums) {
                                if (album.getSongCount() != null) {
                                    totalTracks += album.getSongCount();
                                }

                                MediaItem mediaItem = createAlbum(
                                        album.getName(),
                                        album.getArtist(),
                                        album.getGenre(),
                                        prefix + album.getId(),
                                        false,
                                        album.getCoverArtId()
                                );
                                mediaItems.add(mediaItem);
                            }

                            if (albums.size() >= 2 && totalTracks >= ConstantsAA.MIN_TRACKS_SMALL_MIX) {
                                int numberOfTracks =
                                        totalTracks >= ConstantsAA.MIN_TRACKS_LARGE_MIX ? ConstantsAA.NUMBER_OF_TRACKS_IN_LARGE_MIX :
                                                totalTracks >= ConstantsAA.MIN_TRACKS_MEDIUM_MIX ? ConstantsAA.NUMBER_OF_TRACKS_IN_MEDIUM_MIX :
                                                        ConstantsAA.NUMBER_OF_TRACKS_IN_SMALL_MIX;
                                ArtistID3 artist = response.body().getSubsonicResponse().getArtist();
                                MediaItem instantMixItem = createAlbum(
                                        App.getContext().getString(R.string.aa_instant_mix),
                                        null,
                                        "Instant Mix",
                                        ConstantsAA.INSTANTMIX_SOURCE + "[" + numberOfTracks + "]" + id,
                                        true,
                                        artist.getCoverArtId()
                                );
                                mediaItems.add(0, instantMixItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getInstantMix(String artistId, int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        Log.d(TAG, "Instant Mix: Starting for artistId=" + artistId + " for " + count + " tracks");

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(artistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getArtist() != null
                                && response.body().getSubsonicResponse().getArtist().getAlbums() != null) {

                            List<AlbumID3> albums = new ArrayList<>(
                                    response.body().getSubsonicResponse().getArtist().getAlbums()
                            );
                            Log.d(TAG, "Instant Mix: Found " + albums.size() + " albums");

                            Random random = new Random();
                            Collections.shuffle(albums, random);

                            // Fetch just the first album to get the first track
                            AlbumID3 firstAlbum = albums.get(0);
                            App.getSubsonicClientInstance(false)
                                    .getBrowsingClient()
                                    .getAlbum(firstAlbum.getId())
                                    .enqueue(new Callback<ApiResponse>() {
                                        @Override
                                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                                            if (response.isSuccessful()
                                                    && response.body() != null
                                                    && response.body().getSubsonicResponse().getAlbum() != null
                                                    && response.body().getSubsonicResponse().getAlbum().getSongs() != null) {

                                                List<Child> songs = response.body().getSubsonicResponse().getAlbum().getSongs();
                                                Child firstTrack = songs.get(random.nextInt(songs.size()));

                                                Log.d(TAG, "Instant Mix: First track: " + firstTrack.getTitle());

                                                setChildrenMetadata(List.of(firstTrack));

                                                // Tag parent_id with artistId so handle() can resume the mix
                                                MediaItem mediaItem = MappingUtil.mapMediaItem(firstTrack,
                                                        ConstantsAA.INSTANTMIX_SOURCE+ "[" + count + "]"  + artistId);

                                                listenableFuture.set(LibraryResult.ofItemList(
                                                        ImmutableList.of(mediaItem), null));
                                            } else {
                                                Log.e(TAG, "Instant Mix: Failed to fetch first album");
                                                listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                                            }
                                        }
                                        @Override
                                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                                            Log.e(TAG, "Instant Mix: Failed to fetch first album: " + t.getMessage());
                                            listenableFuture.setException(t);
                                        }
                                    });
                        } else {
                            Log.e(TAG, "Instant Mix: Failed to retrieve artist albums for artistId=" + artistId);
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Instant Mix: Network failure: " + t.getMessage());
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getPlaylistSongs(String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        SubsonicResponse subsonicResponse = getSubsonicResponseOrNull(response);
                        if (response.isSuccessful() && subsonicResponse != null && subsonicResponse.getPlaylist() != null && subsonicResponse.getPlaylist().getEntries() != null) {
                            PlaylistWithSongs playlist = subsonicResponse.getPlaylist();
                            List<Child> tracks = playlist.getEntries();

                            if( !Preferences.isAndroidAutoShufflePlaylistsEnabled() ) {
                                tracks = tracks.subList(0, Math.min(ConstantsAA.MAX_ITEMS, tracks.size()));
                            }
                            else {
                                Collections.shuffle(tracks);
                                tracks = tracks.subList(0, Math.min(ConstantsAA.MAX_SHUFFLE_ITEMS, tracks.size()));
                            }

                            setChildrenMetadata(tracks);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(tracks, ConstantsAA.QUEUE_CACHED_SOURCE);
                            for (int i = 0; i < mediaItems.size(); i++) {
                                Child track = tracks.get(i);
                                mediaItems.set(i, withSubtitle(mediaItems.get(i), joinSubtitle(
                                        track.getArtist(),
                                        MusicUtil.getReadableDurationString(track.getDuration(), false))));
                            }

                            mediaItems.add(0, createShuffle(
                                    App.getContext().getString(R.string.aa_shuffle_playlist),
                                    songCountAndLength(playlist.getSongCount(), playlist.getDuration()),
                                    ConstantsAA.PLAYLIST_ID + id));

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getMadeForYou(String mixType, int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2("recent", ConstantsAA.NUMBER_OF_RECENT_ALBUMS_FOR_MIX, 0, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getAlbumList2() != null
                                && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null) {
                            List<AlbumID3> recentAlbums = new ArrayList<>(
                                    response.body().getSubsonicResponse().getAlbumList2().getAlbums());
                            Log.d(TAG, "MadeForYou: recent albums loaded: " + recentAlbums.size());

                            if (recentAlbums.isEmpty()) {
                                Log.w(TAG, "MadeForYou: No recent albums, falling back to random song");
                                fallbackToFirstRandomSong(mixType, count, listenableFuture);
                                return;
                            }

                            Random random = new Random();
                            Collections.shuffle(recentAlbums, random);

                            // Fetch just the first album to get the first track
                            AlbumID3 firstAlbum = recentAlbums.get(0);
                            App.getSubsonicClientInstance(false)
                                    .getBrowsingClient()
                                    .getAlbum(firstAlbum.getId())
                                    .enqueue(new Callback<ApiResponse>() {
                                        @Override
                                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                                            if (response.isSuccessful()
                                                    && response.body() != null
                                                    && response.body().getSubsonicResponse().getAlbum() != null
                                                    && response.body().getSubsonicResponse().getAlbum().getSongs() != null) {

                                                List<Child> songs = response.body().getSubsonicResponse().getAlbum().getSongs();
                                                Child firstTrack = songs.get(random.nextInt(songs.size()));

                                                Log.d(TAG, "MadeForYou: First track: " + firstTrack.getTitle());

                                                setChildrenMetadata(List.of(firstTrack));

                                                // Tag parent_id with mixType so handle() can resume the mix
                                                MediaItem mediaItem = MappingUtil.mapMediaItem(firstTrack,
                                                        ConstantsAA.MADE_FOR_YOU_SOURCE+ "[" + count + "]"  + mixType);

                                                listenableFuture.set(LibraryResult.ofItemList(
                                                        ImmutableList.of(mediaItem), null));
                                            } else {
                                                Log.e(TAG, "MadeForYou: Failed to fetch first album");
                                                fallbackToFirstRandomSong(mixType, count, listenableFuture);                                            }
                                        }
                                        @Override
                                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                                            Log.e(TAG, "MadeForYou: Failed to fetch first album: " + t.getMessage());
                                            listenableFuture.setException(t);
                                        }
                                    });
                        } else {
                            Log.w(TAG, "MadeForYou: Recent albums failed, falling back to random songs");
                            fallbackToFirstRandomSong(mixType, count, listenableFuture);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "MadeForYou: Failed to fetch recent albums: " + t.getMessage());
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    private void fallbackToFirstRandomSong(
            String mixType,
            int count,
            SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture) {

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getRandomSongs(1, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getRandomSongs() != null
                                && response.body().getSubsonicResponse().getRandomSongs().getSongs() != null) {

                            List<Child> songs = response.body().getSubsonicResponse().getRandomSongs().getSongs();
                            Child firstTrack = songs.get(0);

                            Log.d(TAG, "MadeForYou: Fallback first track: " + firstTrack.getTitle());

                            setChildrenMetadata(List.of(firstTrack));

                            MediaItem mediaItem = MappingUtil.mapMediaItem(firstTrack,
                                    ConstantsAA.MADE_FOR_YOU_SOURCE + "[" + count + "]" + mixType);

                            listenableFuture.set(LibraryResult.ofItemList(
                                    ImmutableList.of(mediaItem), null));
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "MadeForYou: Fallback random song failed: " + t.getMessage());
                        listenableFuture.setException(t);
                    }
                });
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> search(String query, String albumPrefix, String artistPrefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3(query, 20, 0, 20, 0, 20, 0)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        SubsonicResponse subsonicResponse = getSubsonicResponseOrNull(response);
                        if (response.isSuccessful() && subsonicResponse != null && subsonicResponse.getSearchResult3() != null) {
                            List<MediaItem> mediaItems = new ArrayList<>();
                            Context context = App.getContext();

                            if (response.body().getSubsonicResponse().getSearchResult3().getArtists() != null) {
                                for (ArtistID3 artist : response.body().getSubsonicResponse().getSearchResult3().getArtists()) {

                                    MediaItem mediaItem = createArtist(
                                            artist.getName(),
                                            artistPrefix + artist.getId(),
                                            artist.getCoverArtId()
                                    );
                                    mediaItem = withSubtitle(mediaItem, context.getString(R.string.aa_artist));
                                    mediaItem = withSingleItemStyle(mediaItem, false);
                                    mediaItems.add(withGroupTitle(mediaItem, context.getString(R.string.aa_artists)));
                                }
                            }

                            if (response.body().getSubsonicResponse().getSearchResult3().getAlbums() != null) {
                                for (AlbumID3 album : response.body().getSubsonicResponse().getSearchResult3().getAlbums()) {
                                    MediaItem mediaItem = createAlbum(
                                            album.getName(),
                                            album.getArtist(),
                                            album.getGenre(),
                                            albumPrefix + album.getId(),
                                            false,
                                            album.getCoverArtId()
                                    );
                                    mediaItem = withSubtitle(mediaItem, joinSubtitle(album.getArtist(), album.getYear() > 0 ? String.valueOf(album.getYear()) : null));
                                    mediaItem = withSingleItemStyle(mediaItem, true);
                                    mediaItems.add(withGroupTitle(mediaItem, context.getString(R.string.aa_albums)));
                                }
                            }

                            if (response.body().getSubsonicResponse().getSearchResult3().getSongs() != null) {
                                List<Child> tracks = response.body().getSubsonicResponse().getSearchResult3().getSongs();
                                setChildrenMetadata(tracks);
                                for (Child track : tracks) {
                                    MediaItem mediaItem = withSubtitle(MappingUtil.mapMediaItem(track), joinSubtitle(track.getArtist(), track.getAlbum()));
                                    mediaItem = withSingleItemStyle(mediaItem, false);
                                    mediaItems.add(withGroupTitle(mediaItem, context.getString(R.string.aa_songs)));
                                }
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setChildrenMetadata(List<Child> children) {
        long timestamp = System.currentTimeMillis();
        ArrayList<SessionMediaItem> sessionMediaItems = new ArrayList<>();

        for (Child child : children) {
            SessionMediaItem sessionMediaItem = new SessionMediaItem(child);
            sessionMediaItem.setTimestamp(timestamp);
            sessionMediaItems.add(sessionMediaItem);
        }

        InsertAllThreadSafe insertAll = new InsertAllThreadSafe(sessionMediaItemDao, sessionMediaItems);
        Thread thread = new Thread(insertAll);
        thread.start();
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setPodcastEpisodesMetadata(List<PodcastEpisode> podcastEpisodes) {
        long timestamp = System.currentTimeMillis();
        ArrayList<SessionMediaItem> sessionMediaItems = new ArrayList<>();

        for (PodcastEpisode podcastEpisode : podcastEpisodes) {
            SessionMediaItem sessionMediaItem = new SessionMediaItem(podcastEpisode);
            sessionMediaItem.setTimestamp(timestamp);
            sessionMediaItems.add(sessionMediaItem);
        }

        InsertAllThreadSafe insertAll = new InsertAllThreadSafe(sessionMediaItemDao, sessionMediaItems);
        Thread thread = new Thread(insertAll);
        thread.start();
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setInternetRadioStationsMetadata(List<InternetRadioStation> internetRadioStations) {
        long timestamp = System.currentTimeMillis();
        ArrayList<SessionMediaItem> sessionMediaItems = new ArrayList<>();

        for (InternetRadioStation internetRadioStation : internetRadioStations) {
            SessionMediaItem sessionMediaItem = new SessionMediaItem(internetRadioStation);
            sessionMediaItem.setTimestamp(timestamp);
            sessionMediaItems.add(sessionMediaItem);
        }

        InsertAllThreadSafe insertAll = new InsertAllThreadSafe(sessionMediaItemDao, sessionMediaItems);
        Thread thread = new Thread(insertAll);
        thread.start();
    }

    public SessionMediaItem getSessionMediaItem(String id) {
        SessionMediaItem sessionMediaItem = null;

        GetMediaItemThreadSafe getMediaItemThreadSafe = new GetMediaItemThreadSafe(sessionMediaItemDao, id);
        Thread thread = new Thread(getMediaItemThreadSafe);
        thread.start();

        try {
            thread.join();
            sessionMediaItem = getMediaItemThreadSafe.getSessionMediaItem();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return sessionMediaItem;
    }

    public List<MediaItem> getMetadatas(long timestamp) {
        List<MediaItem> mediaItems = Collections.emptyList();

        GetMediaItemsThreadSafe getMediaItemsThreadSafe = new GetMediaItemsThreadSafe(sessionMediaItemDao, timestamp);
        Thread thread = new Thread(getMediaItemsThreadSafe);
        thread.start();

        try {
            thread.join();
            mediaItems = getMediaItemsThreadSafe.getMediaItems();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return mediaItems;
    }

    public void deleteMetadata() {
        DeleteAllThreadSafe delete = new DeleteAllThreadSafe(sessionMediaItemDao);
        Thread thread = new Thread(delete);
        thread.start();
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getGenres(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getGenres()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getGenres() != null && response.body().getSubsonicResponse().getGenres().getGenres() != null) {
                            List<Genre> genres = response.body().getSubsonicResponse().getGenres().getGenres();

                            // Sort genres alphabetically by name
                            genres.sort((g1, g2) -> {
                                String name1 = g1.getGenre() != null ? g1.getGenre() : "";
                                String name2 = g2.getGenre() != null ? g2.getGenre() : "";
                                return name1.compareToIgnoreCase(name2);
                            });

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (Genre genre : genres) {
                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(genre.getGenre())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + genre.getGenre())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(withSubtitle(mediaItem, App.getContext().getResources().getQuantityString(
                                        R.plurals.player_queue_song_count, genre.getSongCount(), genre.getSongCount())));
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getSongsByGenre(String genre, int count, boolean shuffle) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        Call<ApiResponse> call;
        if (shuffle) {
            call = App.getSubsonicClientInstance(false)
                    .getAlbumSongListClient()
                    .getRandomSongs(count, null, null, genre);
        } else {
            call = App.getSubsonicClientInstance(false)
                    .getAlbumSongListClient()
                    .getSongsByGenre(genre, count, 0);
        }

        call.enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<com.eddyizm.tempus.subsonic.models.Child> songs;
                    if (shuffle) {
                        songs = response.body().getSubsonicResponse().getRandomSongs() != null
                                ? response.body().getSubsonicResponse().getRandomSongs().getSongs()
                                : null;
                    } else {
                        songs = response.body().getSubsonicResponse().getSongsByGenre() != null
                                ? response.body().getSubsonicResponse().getSongsByGenre().getSongs()
                                : null;
                    }

                    if (songs != null) {
                        setChildrenMetadata(songs);
                        List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs, ConstantsAA.QUEUE_CACHED_SOURCE);
                        LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);
                        listenableFuture.set(libraryResult);
                    } else {
                        listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                    }
                } else {
                    listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                listenableFuture.setException(t);
            }
        });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getSongsByGenre(String genre, int count) {
        return getSongsByGenre(genre, count, false);
    }

    private static class GetMediaItemThreadSafe implements Runnable {
        private final SessionMediaItemDao sessionMediaItemDao;
        private final String id;

        private SessionMediaItem sessionMediaItem;

        public GetMediaItemThreadSafe(SessionMediaItemDao sessionMediaItemDao, String id) {
            this.sessionMediaItemDao = sessionMediaItemDao;
            this.id = id;
        }

        @Override
        public void run() {
            sessionMediaItem = sessionMediaItemDao.get(id);
        }

        public SessionMediaItem getSessionMediaItem() {
            return sessionMediaItem;
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private static class GetMediaItemsThreadSafe implements Runnable {
        private final SessionMediaItemDao sessionMediaItemDao;
        private final Long timestamp;
        private final List<MediaItem> mediaItems = new ArrayList<>();

        public GetMediaItemsThreadSafe(SessionMediaItemDao sessionMediaItemDao, Long timestamp) {
            this.sessionMediaItemDao = sessionMediaItemDao;
            this.timestamp = timestamp;
        }

        @Override
        public void run() {
            List<SessionMediaItem> sessionMediaItems = sessionMediaItemDao.get(timestamp);
            sessionMediaItems.forEach(sessionMediaItem -> mediaItems.add(sessionMediaItem.getMediaItem()));
        }

        public List<MediaItem> getMediaItems() {
            return mediaItems;
        }
    }

    private static class InsertAllThreadSafe implements Runnable {
        private final SessionMediaItemDao sessionMediaItemDao;
        private final List<SessionMediaItem> sessionMediaItems;

        public InsertAllThreadSafe(SessionMediaItemDao sessionMediaItemDao, List<SessionMediaItem> sessionMediaItems) {
            this.sessionMediaItemDao = sessionMediaItemDao;
            this.sessionMediaItems = sessionMediaItems;
        }

        @Override
        public void run() {
            sessionMediaItemDao.insertAll(sessionMediaItems);
        }
    }

    private static class DeleteAllThreadSafe implements Runnable {
        private final SessionMediaItemDao sessionMediaItemDao;

        public DeleteAllThreadSafe(SessionMediaItemDao sessionMediaItemDao) {
            this.sessionMediaItemDao = sessionMediaItemDao;
        }

        @Override
        public void run() {
            sessionMediaItemDao.deleteAll();
        }
    }
}
