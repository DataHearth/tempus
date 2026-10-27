package com.eddyizm.tempus.ui.fragment;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.core.view.MenuProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentAlbumPageBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.helper.CoverTint;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.service.MediaManager;
import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.adapter.SongHorizontalAdapter;
import com.eddyizm.tempus.ui.dialog.PlaylistChooserDialog;
import com.eddyizm.tempus.ui.dialog.RatingDialog;
import com.eddyizm.tempus.util.AssetLinkUtil;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.DownloadUtil;
import com.eddyizm.tempus.util.MappingUtil;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.util.ExternalAudioWriter;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.viewmodel.AlbumPageViewModel;
import com.eddyizm.tempus.viewmodel.PlaybackViewModel;
import com.eddyizm.tempus.viewmodel.PlayerBottomSheetViewModel;
import com.eddyizm.tempus.util.FavoriteRegistry;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@UnstableApi
public class AlbumPageFragment extends Fragment implements ClickCallback, MenuProvider {
    private FragmentAlbumPageBinding bind;
    private MainActivity activity;
    private AlbumPageViewModel albumPageViewModel;
    private PlaybackViewModel playbackViewModel;
    private SongHorizontalAdapter songHorizontalAdapter;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private LiveData<ArtistID3> pendingArtist;
    private Observer<ArtistID3> pendingArtistObserver;
    private String tintedCoverId;
    private LiveData<CoverTint> playingAccent;

    /** @noinspection deprecation*/
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity().addMenuProvider(this, this, Lifecycle.State.RESUMED);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.album_page_menu, menu);
        menu.findItem(R.id.action_album_info).setChecked(bind != null && bind.albumHeader.albumDetailView.getVisibility() == View.VISIBLE);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentAlbumPageBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        albumPageViewModel = new ViewModelProvider(requireActivity()).get(AlbumPageViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        Bundle args = getArguments();
        if (args == null) {
            Log.e("AlbumPageFragment", "Arguments are null after process restoration.");
            if (activity != null && activity.navController != null) activity.navController.navigateUp();
            return view;
        }

        AlbumID3 albumArg = BundleCompat.getParcelable(args, Constants.ALBUM_OBJECT, AlbumID3.class);
        if (albumArg == null) {
            if (activity != null && activity.navController != null) activity.navController.navigateUp();
            return view;
        }

        init(view, albumArg);
        initAppBar();
        initAlbumInfoTextButton();
        initAlbumNotes();
        initMusicButton();
        initDownloadButton();
        initBackCover();
        initSongsView();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();

        MediaManager.registerPlaybackObserver(mediaBrowserListenableFuture, playbackViewModel);
        observePlayback();
    }

    public void onResume() {
        super.onResume();
        if (songHorizontalAdapter != null) setMediaBrowserListenableFuture();
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        removePendingArtistObserver();
        super.onDestroyView();
        tintedCoverId = null;
        bind = null;
    }

    private void removePendingArtistObserver() {
        if (pendingArtist != null && pendingArtistObserver != null) {
            pendingArtist.removeObserver(pendingArtistObserver);
        }
        pendingArtist = null;
        pendingArtistObserver = null;
    }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem item) {
            if (item.getItemId() == R.id.action_rate_album) {
                Bundle bundle = new Bundle();
                AlbumID3 album = albumPageViewModel.getAlbum().getValue();
                bundle.putParcelable(Constants.ALBUM_OBJECT, album.strippedForNav());
                RatingDialog dialog = new RatingDialog();
                dialog.setArguments(bundle);
                dialog.show(requireActivity().getSupportFragmentManager(), null);
                return true;
            }

        if (item.getItemId() == R.id.action_download_album) {
            downloadAlbum();
            return true;
        }
        if (item.getItemId() == R.id.action_album_info) {
            boolean show = bind.albumHeader.albumDetailView.getVisibility() != View.VISIBLE;
            bind.albumHeader.albumDetailView.setVisibility(show ? View.VISIBLE : View.GONE);
            item.setChecked(show);
            return true;
        }
        if (item.getItemId() == R.id.action_add_to_playlist) {
            List<Child> songs = albumPageViewModel.getAlbumSongLiveList().getValue();
            if (songs == null || songs.isEmpty()) {
                return true;
            }
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(songs));

            PlaylistChooserDialog dialog = new PlaylistChooserDialog();
            dialog.setArguments(bundle);
            dialog.show(requireActivity().getSupportFragmentManager(), null);
            return true;
        }
        if (item.getItemId() == R.id.action_play_next || item.getItemId() == R.id.action_add_to_queue) {
            boolean playNext = item.getItemId() == R.id.action_play_next;
            List<Child> songs = albumPageViewModel.getAlbumSongLiveList().getValue();
            if (songs == null || songs.isEmpty()) {
                return true;
            }
            MediaManager.enqueue(mediaBrowserListenableFuture, songs, playNext);
            activity.setBottomSheetInPeek(true);
            return true;
        }

        return false;
    }

    private void downloadAlbum() {
        List<Child> songs = albumPageViewModel.getAlbumSongLiveList().getValue();
        if (songs == null || songs.isEmpty()) {
            return;
        }
        if (Preferences.getDownloadDirectoryUri() == null) {
            DownloadUtil.getDownloadTracker(requireContext()).download(
                MappingUtil.mapDownloads(songs),
                songs.stream().map(Download::new).collect(Collectors.toList())
            );
        } else {
            songs.forEach(child -> ExternalAudioWriter.downloadToUserDirectory(requireContext(), child));
        }
    }

    private void init(View view, AlbumID3 albumArg) {
        albumPageViewModel.setAlbum(getViewLifecycleOwner(), albumArg);
        MaterialButton favoriteToggle = bind.albumHeader.buttonFavorite;
        favoriteToggle.setChecked(FavoriteRegistry.resolve(FavoriteRegistry.Kind.ALBUM, albumArg.getId(), albumArg.getStarred() != null));

        favoriteToggle.setOnClickListener(v -> {
            albumPageViewModel.setFavorite();
        });
        albumPageViewModel.getAlbum().observe(getViewLifecycleOwner(), album -> {
            if (album != null) {
                favoriteToggle.setChecked(FavoriteRegistry.resolve(FavoriteRegistry.Kind.ALBUM, album.getId(), album.getStarred() != null));
            }
        });
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.animToolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);

        }

        bind.animToolbar.setTitle(null);

        albumPageViewModel.getAlbum().observe(getViewLifecycleOwner(), album -> {
            if (bind != null && album != null) {
                bind.albumHeader.albumNameLabel.setText(album.getName());
                bind.albumHeader.albumArtistLabel.setText(album.getArtist());
                AssetLinkUtil.AssetLink artistLink = buildArtistLink(album);
                bind.albumHeader.albumArtistRow.setOnLongClickListener(v -> {
                    if (artistLink != null) {
                        AssetLinkUtil.copyToClipboard(requireContext(), artistLink);
                        Toast.makeText(requireContext(), getString(R.string.asset_link_copied_toast, artistLink.id), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    return false;
                });
                bindMetaLine(album);

                if (album.getGenre() != null && !album.getGenre().isEmpty()) {
                    bind.albumHeader.albumGenresTextview.setText(album.getGenre());
                    bind.albumHeader.albumGenresTextview.setVisibility(View.VISIBLE);
                }
                else{
                    bind.albumHeader.albumGenresTextview.setVisibility(View.GONE);
                }

                if (album.getReleaseDate() != null && album.getOriginalReleaseDate() != null) {
                    if (album.getReleaseDate().getFormattedDate() != null || album.getOriginalReleaseDate().getFormattedDate() != null)
                        bind.albumHeader.albumReleaseYearsTextview.setVisibility(View.VISIBLE);
                    else
                        bind.albumHeader.albumReleaseYearsTextview.setVisibility(View.GONE);

                    if (album.getReleaseDate().getFormattedDate() == null || album.getOriginalReleaseDate().getFormattedDate() == null) {
                        bind.albumHeader.albumReleaseYearsTextview.setText(getString(R.string.album_page_release_date_label, album.getReleaseDate() != null ? album.getReleaseDate().getFormattedDate() : album.getOriginalReleaseDate().getFormattedDate()));
                    }

                    if (album.getReleaseDate().getFormattedDate() != null && album.getOriginalReleaseDate().getFormattedDate() != null) {
                        if (Objects.equals(album.getReleaseDate().getYear(), album.getOriginalReleaseDate().getYear()) && Objects.equals(album.getReleaseDate().getMonth(), album.getOriginalReleaseDate().getMonth()) && Objects.equals(album.getReleaseDate().getDay(), album.getOriginalReleaseDate().getDay())) {
                            bind.albumHeader.albumReleaseYearsTextview.setText(getString(R.string.album_page_release_date_label, album.getReleaseDate().getFormattedDate()));
                        } else {
                            bind.albumHeader.albumReleaseYearsTextview.setText(getString(R.string.album_page_release_dates_label, album.getReleaseDate().getFormattedDate(), album.getOriginalReleaseDate().getFormattedDate()));
                        }
                    }
                }

                applyCoverTint(album.getCoverArtId());
            }
        });

        bind.animToolbar.setNavigationOnClickListener(v -> activity.navController.navigateUp());

        Objects.requireNonNull(bind.animToolbar.getOverflowIcon()).setTint(MaterialColors.getColor(bind.animToolbar, com.google.android.material.R.attr.colorOnSurface));

        if(Preferences.showAlbumDetail()){
            bind.albumHeader.albumDetailView.setVisibility(View.VISIBLE);
        }
    }

    private void bindMetaLine(AlbumID3 album) {
        SpannableStringBuilder meta = new SpannableStringBuilder();
        appendMetaPart(meta, releaseTypeLabel(album.getReleaseTypes()));
        int year = album.getYear();
        if (year != 0) {
            int start = appendMetaPart(meta, String.valueOf(year));
            int linkColor = MaterialColors.getColor(bind.albumHeader.albumMetaLabel, com.google.android.material.R.attr.colorOnSurfaceVariant);
            meta.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    openYearLink(year);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint paint) {
                    paint.setColor(linkColor);
                    paint.setUnderlineText(false);
                }
            }, start, meta.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int songCount = album.getSongCount() != null ? album.getSongCount() : 0;
        if (songCount > 0) {
            appendMetaPart(meta, getResources().getQuantityString(R.plurals.page_song_count, songCount, songCount));
        }
        if (album.getDuration() != null && album.getDuration() > 0) {
            appendMetaPart(meta, MusicUtil.getReadableTotalDuration(requireContext(), album.getDuration()));
        }

        bind.albumHeader.albumMetaLabel.setText(meta);
        bind.albumHeader.albumMetaLabel.setMovementMethod(year != 0 ? LinkMovementMethod.getInstance() : null);
        bind.albumHeader.albumMetaLabel.setOnLongClickListener(year == 0 ? null : v -> {
            AssetLinkUtil.AssetLink yearLink = buildYearLink(year);
            if (yearLink != null) {
                AssetLinkUtil.copyToClipboard(requireContext(), yearLink);
                Toast.makeText(requireContext(), getString(R.string.asset_link_copied_toast, yearLink.id), Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    /** Servers without release types still get the "Album" lead the meta line always opens with. */
    private String releaseTypeLabel(@Nullable List<String> releaseTypes) {
        String releaseType = releaseTypes != null && !releaseTypes.isEmpty() && releaseTypes.get(0) != null
                ? releaseTypes.get(0).trim() : "";
        if (releaseType.isEmpty() || releaseType.equalsIgnoreCase("album")) {
            return getString(R.string.track_info_album);
        }
        return Character.toUpperCase(releaseType.charAt(0)) + releaseType.substring(1);
    }

    private static int appendMetaPart(SpannableStringBuilder meta, String part) {
        if (meta.length() > 0) meta.append(" · ");
        int start = meta.length();
        meta.append(part);
        return start;
    }

    private void applyCoverTint(String coverId) {
        if (Objects.equals(coverId, tintedCoverId)) return;
        tintedCoverId = coverId;
        int surface = MaterialColors.getColor(bind.getRoot(), com.google.android.material.R.attr.colorSurface);
        int tintHeight = getResources().getDimensionPixelSize(R.dimen.page_header_tint_height);
        CoverTint.load(requireContext(), coverId, tint -> {
            if (bind == null) return;
            // The tint stays put behind the toolbar and the list while the header scrolls away.
            LayerDrawable background = new LayerDrawable(new Drawable[]{
                    new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{tint.getBand(), surface})
            });
            background.setLayerGravity(0, Gravity.TOP | Gravity.FILL_HORIZONTAL);
            background.setLayerHeight(0, tintHeight);
            bind.getRoot().setBackground(background);
        });
    }

    private void initAlbumInfoTextButton() {
        bind.albumHeader.albumArtistRow.setOnClickListener(v -> {
            removePendingArtistObserver();
            pendingArtist = albumPageViewModel.getArtist();
            pendingArtistObserver = artist -> {
                removePendingArtistObserver();
                if (artist == null) {
                    Toast.makeText(requireContext(), R.string.album_error_retrieving_artist, Toast.LENGTH_SHORT).show();
                    return;
                }

                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ARTIST_OBJECT, artist.strippedForNav());
                activity.navController.navigate(R.id.action_albumPageFragment_to_artistPageFragment, bundle);
            };
            pendingArtist.observe(getViewLifecycleOwner(), pendingArtistObserver);
        });
    }

    private void initAlbumNotes() {
        albumPageViewModel.getAlbumInfo().observe(getViewLifecycleOwner(), albumInfo -> {
            if (albumInfo != null) {
                if (bind != null) bind.albumHeader.albumNotesTextview.setVisibility(View.VISIBLE);
                if (bind != null)
                    bind.albumHeader.albumNotesTextview.setText(MusicUtil.forceReadableString(albumInfo.getNotes()));

                if (bind != null && albumInfo.getLastFmUrl() != null && !albumInfo.getLastFmUrl().isEmpty()) {
                    bind.albumHeader.albumNotesTextview.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(albumInfo.getLastFmUrl()));
                        startActivity(intent);
                    });
                }
            } else {
                if (bind != null) bind.albumHeader.albumNotesTextview.setVisibility(View.GONE);
            }
        });
    }

    private void initMusicButton() {
        albumPageViewModel.getAlbumSongLiveList().observe(getViewLifecycleOwner(), songs -> {
            if (bind != null && songs != null && !songs.isEmpty()) {
                bind.albumHeader.albumPagePlayButton.setOnClickListener(v -> {
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                });

                bind.albumHeader.albumPageShuffleButton.setOnClickListener(v -> {
                    Collections.shuffle(songs);
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                });
            }

            if (bind != null && (songs == null || songs.isEmpty())) {
                bind.albumHeader.albumPagePlayButton.setEnabled(false);
                bind.albumHeader.albumPageShuffleButton.setEnabled(false);
            }
        });
    }

    private void initDownloadButton() {
        bind.albumHeader.albumPageDownloadButton.setOnClickListener(v -> downloadAlbum());
    }

    private void initBackCover() {
        albumPageViewModel.getAlbum().observe(getViewLifecycleOwner(), album -> {
            if (bind != null && album != null) {
                CustomGlideRequest.Builder.from(requireContext(), album.getCoverArtId(), CustomGlideRequest.ResourceType.Album).build().into(bind.albumHeader.albumCoverImageView);
                CustomGlideRequest.Builder.from(requireContext(), album.getArtistId(), CustomGlideRequest.ResourceType.Artist).build().into(bind.albumHeader.albumArtistImageView);
            }
        });
    }

    private void initSongsView() {
        playingAccent = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class).getCoverTint();
        playingAccent.observe(getViewLifecycleOwner(), tint -> {
            if (songHorizontalAdapter != null) songHorizontalAdapter.setAccentColor(tint != null ? tint.getAccent() : null);
        });
        bind.songRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.songRecyclerView.setHasFixedSize(true);

        /* Edge-to-edge dynamic inset for bottom padding */
        bind.songRecyclerView.setClipToPadding(false);
        bind.songRecyclerView.post(() -> {
            if (bind == null) return;
            int peekHeight = (int) getResources().getDimension(R.dimen.bottom_sheet_behavior_peek_height);
            WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(
                    requireActivity().getWindow().getDecorView());
            int navBottom = rootInsets == null ? 0
                    : rootInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            bind.songRecyclerView.setPadding(
                    bind.songRecyclerView.getPaddingLeft(),
                    bind.songRecyclerView.getPaddingTop(),
                    bind.songRecyclerView.getPaddingRight(),
                    navBottom+peekHeight);
        });

        // Synchronize scrolling if info scroll view exists (landscape)
        if (bind.getRoot().findViewById(R.id.album_info_scroll_view) != null) {
            androidx.core.widget.NestedScrollView albumInfoScrollView = bind.getRoot().findViewById(R.id.album_info_scroll_view);
            bind.songRecyclerView.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    albumInfoScrollView.scrollBy(0, dy);
                }
            });
        }

        albumPageViewModel.getAlbum().observe(getViewLifecycleOwner(), album -> {
            if (bind != null && album != null) {
                songHorizontalAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, false, false, album);
                songHorizontalAdapter.setAccentColor(playingAccent.getValue() != null ? playingAccent.getValue().getAccent() : null);
                bind.songRecyclerView.setAdapter(songHorizontalAdapter);
                setMediaBrowserListenableFuture();
                reapplyPlayback();

                albumPageViewModel.getAlbumSongLiveList().observe(getViewLifecycleOwner(), songs -> {
                    songHorizontalAdapter.setItems(songs);
                    reapplyPlayback();
                });
            }
        });
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    @Override
    public void onMediaClick(Bundle bundle) {
        MediaManager.startQueue(mediaBrowserListenableFuture, BundleCompat.getParcelableArrayList(bundle, Constants.TRACKS_OBJECT, Child.class), bundle.getInt(Constants.ITEM_POSITION));
        activity.setBottomSheetInPeek(true);
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }

    private void observePlayback() {
        playbackViewModel.getCurrentSongId().observe(getViewLifecycleOwner(), id -> {
            if (songHorizontalAdapter != null) {
                Boolean playing = playbackViewModel.getIsPlaying().getValue();
                songHorizontalAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            if (songHorizontalAdapter != null) {
                String id = playbackViewModel.getCurrentSongId().getValue();
                songHorizontalAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
    }

    private void reapplyPlayback() {
        if (songHorizontalAdapter != null) {
            String id = playbackViewModel.getCurrentSongId().getValue();
            Boolean playing = playbackViewModel.getIsPlaying().getValue();
            songHorizontalAdapter.setPlaybackState(id, playing != null && playing);
        }
    }

    private void setMediaBrowserListenableFuture() {
        songHorizontalAdapter.setMediaBrowserListenableFuture(mediaBrowserListenableFuture);
    }

    private void openYearLink(int year) {
        AssetLinkUtil.AssetLink link = buildYearLink(year);
        if (link != null) {
            activity.openAssetLink(link);
        }
    }

    private AssetLinkUtil.AssetLink buildYearLink(int year) {
        if (year <= 0) return null;
        return AssetLinkUtil.buildAssetLink(AssetLinkUtil.TYPE_YEAR, String.valueOf(year));
    }

    private AssetLinkUtil.AssetLink buildArtistLink(AlbumID3 album) {
        if (album == null || album.getArtistId() == null || album.getArtistId().isEmpty()) {
            return null;
        }
        return AssetLinkUtil.buildAssetLink(AssetLinkUtil.TYPE_ARTIST, album.getArtistId());
    }
}
