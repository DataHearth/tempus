package com.eddyizm.tempus.ui.fragment;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentArtistPageBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.helper.CoverTint;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.service.MediaManager;
import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.adapter.AlbumCarouselAdapter;
import com.eddyizm.tempus.ui.adapter.ArtistCarouselAdapter;
import com.eddyizm.tempus.ui.adapter.SongHorizontalAdapter;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.viewmodel.ArtistPageViewModel;
import com.eddyizm.tempus.viewmodel.PlaybackViewModel;
import com.eddyizm.tempus.viewmodel.PlayerBottomSheetViewModel;
import com.eddyizm.tempus.util.FavoriteRegistry;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@UnstableApi
public class ArtistPageFragment extends Fragment implements ClickCallback {
    private FragmentArtistPageBinding bind;
    private MainActivity activity;
    private ArtistPageViewModel artistPageViewModel;
    private PlaybackViewModel playbackViewModel;

    private SongHorizontalAdapter songHorizontalAdapter;
    private AlbumCarouselAdapter releasesAdapter;
    private AlbumCarouselAdapter appearsOnAdapter;
    private ArtistCarouselAdapter similarArtistAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private static final List<String> RELEASE_TYPE_ORDER = List.of(
            "album", "ep", "single",
            "compilation", "soundtrack", "live", "remix"
    );
    private String selectedReleaseType;
    private boolean bioExpanded;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentArtistPageBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        artistPageViewModel = new ViewModelProvider(requireActivity()).get(ArtistPageViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        Bundle args = getArguments();
        ArtistID3 artistArg = args != null ? BundleCompat.getParcelable(args, Constants.ARTIST_OBJECT, ArtistID3.class) : null;
        if (artistArg == null) {
            if (activity != null && activity.navController != null) activity.navController.navigateUp();
            return view;
        }

        init(view, artistArg);
        initAppBar();
        initHeader();
        initArtistInfo();
        initPlayButtons();
        initTopSongsView();
        initCategorizedAlbumsView();
        initSimilarArtistsView();

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
        emptyTopSongsTimerHandler.removeCallbacks(emptyTopSongsTimerRunnable);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        emptyTopSongsTimerHandler.removeCallbacks(emptyTopSongsTimerRunnable);
        bioExpanded = false;
        bind = null;
    }

    private void init(View view, ArtistID3 artistArg) {
        artistPageViewModel.setArtist(artistArg);
        artistPageViewModel.fetchCategorizedAlbums(getViewLifecycleOwner());

        bind.mostStreamedSongTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.MEDIA_BY_ARTIST, Constants.MEDIA_BY_ARTIST);
            ArtistID3 artistForNav = artistPageViewModel.getArtist();
            bundle.putParcelable(Constants.ARTIST_OBJECT, artistForNav != null ? artistForNav.strippedForNav() : null); // #688: drop heavy album lists from the nav arg
            activity.navController.navigate(R.id.action_artistPageFragment_to_songListPageFragment, bundle);
        });

        MaterialButton favoriteToggle = bind.buttonFavorite;
        favoriteToggle.setChecked(FavoriteRegistry.resolve(FavoriteRegistry.Kind.ARTIST, artistPageViewModel.getArtist().getId(), artistPageViewModel.getArtist().getStarred() != null));
        favoriteToggle.setOnClickListener(v -> artistPageViewModel.setFavorite(requireContext()));

        bind.buttonMoreOptions.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            ArtistID3 artistForNav = artistPageViewModel.getArtist();
            bundle.putParcelable(Constants.ARTIST_OBJECT, artistForNav != null ? artistForNav.strippedForNav() : null);
            Navigation.findNavController(requireView()).navigate(R.id.artistBottomSheetDialog, bundle);
        });

        bind.artistPageContentLayout.post(() -> {
            if (bind == null) return;
            int peekHeight = (int) getResources().getDimension(R.dimen.bottom_sheet_behavior_peek_height);
            WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(
                    requireActivity().getWindow().getDecorView());
            int navBottom = rootInsets == null ? 0
                    : rootInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            bind.artistPageContentLayout.setPadding(
                    bind.artistPageContentLayout.getPaddingLeft(),
                    bind.artistPageContentLayout.getPaddingTop(),
                    bind.artistPageContentLayout.getPaddingRight(),
                    navBottom+peekHeight);
        });
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.animToolbar);
        if (activity.getSupportActionBar() != null)
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        bind.animToolbar.setTitle(null);
        bind.animToolbar.setNavigationOnClickListener(v -> activity.navController.navigateUp());
    }

    private void initHeader() {
        ArtistID3 artist = artistPageViewModel.getArtist();
        bind.artistNameLabel.setText(artist.getName());

        int surface = MaterialColors.getColor(bind.getRoot(), com.google.android.material.R.attr.colorSurface);
        CoverTint.load(requireContext(), artistImageId(artist), tint -> {
            if (bind == null) return;
            bind.artistHeader.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{tint.getBand(), surface}));
        });

        bind.bioToggleTextView.setOnClickListener(v -> {
            bioExpanded = !bioExpanded;
            applyBioExpansion();
        });
    }

    private static String artistImageId(ArtistID3 artist) {
        return artist.getCoverArtId() != null && !artist.getCoverArtId().trim().isEmpty()
                ? artist.getCoverArtId()
                : artist.getId();
    }

    private void applyBioExpansion() {
        bind.bioTextView.setMaxLines(bioExpanded ? Integer.MAX_VALUE : 3);
        bind.bioToggleTextView.setText(bioExpanded ? R.string.artist_page_title_biography_less_button : R.string.artist_page_title_biography_more_button);
    }

    private void initArtistInfo() {
        artistPageViewModel.getArtistInfo(artistPageViewModel.getArtist().getId()).observe(getViewLifecycleOwner(), artistInfo -> {
            if (artistInfo == null) {
                if (bind != null) bind.artistPageBioSector.setVisibility(View.GONE);
            } else {
                if (getContext() != null && bind != null) {
                    ArtistID3 currentArtist = artistPageViewModel.getArtist();
                    String primaryId = artistImageId(currentArtist);
                    
                    final String fallbackId = (Objects.requireNonNull(primaryId).equals(currentArtist.getCoverArtId()) &&
                                            currentArtist.getId() != null && 
                                            !currentArtist.getId().equals(primaryId))
                            ? currentArtist.getId()
                            : null;
                    
                    CustomGlideRequest.Builder
                            .from(requireContext(), primaryId, CustomGlideRequest.ResourceType.Artist)
                            .build()
                            .listener(new com.bumptech.glide.request.RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e,
                                                            Object model,
                                                            @NonNull com.bumptech.glide.request.target.Target<Drawable> target,
                                                            boolean isFirstResource) {
                                    if (e != null) {
                                        e.getMessage();
                                        if (e.getMessage().contains("400") && fallbackId != null) {

                                            Log.d("ArtistCover", "Primary ID failed (400), trying fallback: " + fallbackId);

                                            CustomGlideRequest.Builder
                                                    .from(requireContext(), fallbackId, CustomGlideRequest.ResourceType.Artist)
                                                    .build()
                                                    .into(bind.artistBackdropImageView);
                                            return true;
                                        }
                                    }
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(@NonNull Drawable resource,
                                                               @NonNull Object model,
                                                               com.bumptech.glide.request.target.Target<Drawable> target,
                                                               @NonNull com.bumptech.glide.load.DataSource dataSource,
                                                               boolean isFirstResource) {
                                    return false;
                                }
                            })
                            .into(bind.artistBackdropImageView);
                }

                if (bind != null) {
                    String normalizedBio = MusicUtil.forceReadableString(artistInfo.getBiography()).trim();
                    String lastFmUrl = artistInfo.getLastFmUrl();

                    if (normalizedBio.isEmpty()) {
                        bind.bioTextView.setVisibility(View.GONE);
                    } else {
                        bind.bioTextView.setVisibility(View.VISIBLE);
                        bind.bioTextView.setText(normalizedBio);
                        applyBioExpansion();
                        bind.bioTextView.post(() -> {
                            if (bind == null || bind.bioTextView.getLayout() == null) return;
                            android.text.Layout layout = bind.bioTextView.getLayout();
                            boolean truncated = layout.getLineCount() > 0 && layout.getEllipsisCount(layout.getLineCount() - 1) > 0;
                            bind.bioToggleTextView.setVisibility(truncated || bioExpanded ? View.VISIBLE : View.GONE);
                        });
                    }

                    if (lastFmUrl == null) {
                        bind.bioMoreTextViewClickable.setVisibility(View.GONE);
                    } else {
                        bind.bioMoreTextViewClickable.setOnClickListener(v -> {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(artistInfo.getLastFmUrl()));
                            startActivity(intent);
                        });
                        bind.bioMoreTextViewClickable.setVisibility(View.VISIBLE);
                    }

                    bind.artistPageBioSector.setVisibility(!normalizedBio.isEmpty() || lastFmUrl != null ? View.VISIBLE : View.GONE);
                }
            }
        });
    }

    private void initPlayButtons() {
        bind.artistPageShuffleButton.setOnClickListener(v -> artistPageViewModel.getArtistShuffleList().observe(getViewLifecycleOwner(), new Observer<List<Child>>() {
            @Override
            public void onChanged(List<Child> songs) {
                if (songs != null && !songs.isEmpty()) {
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                    artistPageViewModel.getArtistShuffleList().removeObserver(this);
                }
            }
        }));

        bind.artistPageRadioButton.setOnClickListener(v -> artistPageViewModel.getArtistInstantMix().observe(getViewLifecycleOwner(), new Observer<List<Child>>() {
            @Override
            public void onChanged(List<Child> songs) {
                if (songs != null && !songs.isEmpty()) {
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                    artistPageViewModel.getArtistInstantMix().removeObserver(this);
                }
            }
        }));
    }

    private static final long TOP_SONGS_TIMEOUT = 3000;
    private static final int TOP_SONGS_SHOWN = 5;
    private final Handler emptyTopSongsTimerHandler = new Handler(Looper.getMainLooper());
    private final Runnable emptyTopSongsTimerRunnable = () -> {
        bind.artistPageTopSongsSector.setVisibility(View.VISIBLE);
        bind.mostStreamedSongRecyclerContainer.setVisibility(View.GONE);
        bind.mostStreamedSongRecyclerView.setVisibility(View.GONE);
        bind.mostStreamedSongRecyclerPlaceholder.setVisibility(View.GONE);
        bind.mostStreamedSongRecyclerEmpty.setVisibility(View.VISIBLE);
        bind.mostStreamedSongTextViewClickable.setVisibility(View.GONE);
    };

    private void initTopSongsView() {
        bind.mostStreamedSongRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        songHorizontalAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, true, true, null);
        songHorizontalAdapter.setShowRowNumbers(true);
        songHorizontalAdapter.setDenseRows(true);
        new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class).getCoverTint().observe(getViewLifecycleOwner(), tint -> {
            if (songHorizontalAdapter != null) songHorizontalAdapter.setAccentColor(tint != null ? tint.getAccent() : null);
        });
        bind.mostStreamedSongRecyclerView.setAdapter(songHorizontalAdapter);
        setMediaBrowserListenableFuture();
        reapplyPlayback();

        artistPageViewModel.getArtistTopSongList()
                .observe(getViewLifecycleOwner(), songs -> {
                    /* Top Songs Timer unset whenever we enter the observer */
                    emptyTopSongsTimerHandler.removeCallbacks(emptyTopSongsTimerRunnable);

                    if (songs.isEmpty()) {
                        // FETCHING -> Placeholder View
                        bind.artistPageTopSongsSector.setVisibility(View.VISIBLE);
                        bind.mostStreamedSongRecyclerContainer.setVisibility(View.GONE);
                        bind.mostStreamedSongRecyclerView.setVisibility(View.GONE);
                        bind.mostStreamedSongRecyclerPlaceholder.setVisibility(View.VISIBLE);
                        bind.mostStreamedSongRecyclerEmpty.setVisibility(View.GONE);
                        // Start timeout to get out of placeholder
                        emptyTopSongsTimerHandler.postDelayed(emptyTopSongsTimerRunnable, TOP_SONGS_TIMEOUT);
                        return;
                    }

                    // TOP SONGS -> Reycler View
                    bind.mostStreamedSongTextViewClickable.setVisibility(View.VISIBLE);
                    bind.artistPageTopSongsSector.setVisibility(View.VISIBLE);
                    bind.mostStreamedSongRecyclerContainer.setVisibility(View.VISIBLE);
                    bind.mostStreamedSongRecyclerView.setVisibility(View.VISIBLE);
                    bind.mostStreamedSongRecyclerPlaceholder.setVisibility(View.GONE);
                    bind.mostStreamedSongRecyclerEmpty.setVisibility(View.GONE);
                    bind.mostStreamedSongTextViewClickable.setVisibility(
                            songs.size() > TOP_SONGS_SHOWN ? View.VISIBLE : View.GONE);

                    songHorizontalAdapter.setItems(
                            songs.stream().limit(TOP_SONGS_SHOWN).collect(Collectors.toList()));
                    reapplyPlayback();

                });
    }

    private void initCategorizedAlbumsView() {

        bind.allAlbumsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.allAlbumsRecyclerView.setHasFixedSize(true);
        bind.allAlbumsRecyclerView.setNestedScrollingEnabled(false);
        releasesAdapter = new AlbumCarouselAdapter(this, false);
        bind.allAlbumsRecyclerView.setAdapter(releasesAdapter);
        artistPageViewModel.getMapOfAlbums().observe(getViewLifecycleOwner(), albums -> {
            if (bind != null) {
                bindArtistMeta(albums);
                bindReleases(albums != null ? albums : Collections.emptyMap());
            }
        });

        // Appears On
        bind.appearsOnRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.appearsOnRecyclerView.setHasFixedSize(true);
        bind.appearsOnRecyclerView.setNestedScrollingEnabled(false);
        appearsOnAdapter = new AlbumCarouselAdapter(this, true); // Show artist name for Appears On
        bind.appearsOnRecyclerView.setAdapter(appearsOnAdapter);
        artistPageViewModel.getAppearsOn().observe(getViewLifecycleOwner(), albums -> {
            if (bind != null) {
                bind.artistPageAppearsOnSector.setVisibility(albums != null && !albums.isEmpty() ? View.VISIBLE : View.GONE);
                if (albums != null) {
                    bind.appearsOnSeeAllTextView.setVisibility(albums.size() > 5 ? View.VISIBLE : View.GONE);
                    appearsOnAdapter.setItems(albums);
                    bind.appearsOnSeeAllTextView.setOnClickListener(v -> navigateToAlbumList(getString(R.string.artist_page_title_appears_on_section), albums));
                }
            }
        });
    }

    private void bindArtistMeta(Map<String, List<AlbumID3>> albums) {
        if (albums == null || albums.isEmpty()) {
            bind.artistMetaLabel.setVisibility(View.GONE);
            return;
        }
        int albumCount = 0;
        int songCount = 0;
        Map<String, Integer> genres = new HashMap<>();
        for (List<AlbumID3> releases : albums.values()) {
            albumCount += releases.size();
            for (AlbumID3 album : releases) {
                songCount += album.getSongCount() != null ? album.getSongCount() : 0;
                if (album.getGenre() != null && !album.getGenre().trim().isEmpty()) {
                    genres.merge(album.getGenre().trim(), 1, Integer::sum);
                }
            }
        }
        List<String> parts = new ArrayList<>();
        genres.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(genre -> parts.add(genre.getKey()));
        parts.add(getResources().getQuantityString(R.plurals.artist_page_album_count, albumCount, albumCount));
        parts.add(getResources().getQuantityString(R.plurals.page_song_count, songCount, songCount));
        bind.artistMetaLabel.setText(String.join(" · ", parts));
        bind.artistMetaLabel.setVisibility(View.VISIBLE);
    }

    private void bindReleases(Map<String, List<AlbumID3>> albums) {
        List<String> types = new ArrayList<>(albums.keySet());
        types.sort(Comparator.comparing((String type) -> !RELEASE_TYPE_ORDER.contains(type))
                .thenComparingInt(RELEASE_TYPE_ORDER::indexOf)
                .thenComparing(Comparator.naturalOrder()));

        bind.artistPageAllAlbumsSectors.setVisibility(types.isEmpty() ? View.GONE : View.VISIBLE);
        if (!types.contains(selectedReleaseType)) {
            selectedReleaseType = types.isEmpty() ? null : types.get(0);
        }

        bind.releaseTypeChipGroup.setOnCheckedStateChangeListener(null);
        bind.releaseTypeChipGroup.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (String type : types) {
            Chip chip = (Chip) inflater.inflate(R.layout.chip_release_type, bind.releaseTypeChipGroup, false);
            chip.setId(View.generateViewId());
            chip.setTag(type);
            chip.setText(releaseTypeTitle(type));
            chip.setChecked(type.equals(selectedReleaseType));
            bind.releaseTypeChipGroup.addView(chip);
        }
        bind.releaseTypeChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip checked = group.findViewById(checkedIds.get(0));
            selectedReleaseType = (String) checked.getTag();
            showSelectedReleases(albums);
        });

        showSelectedReleases(albums);
    }

    private void showSelectedReleases(Map<String, List<AlbumID3>> albums) {
        List<AlbumID3> releases = selectedReleaseType != null
                ? albums.getOrDefault(selectedReleaseType, Collections.emptyList())
                : Collections.<AlbumID3>emptyList();
        releasesAdapter.setItems(releases);
        bind.allAlbumsRecyclerView.scrollToPosition(0);
        bind.releasesSeeAllTextView.setVisibility(releases.size() > 5 ? View.VISIBLE : View.GONE);
        bind.releasesSeeAllTextView.setOnClickListener(v -> navigateToAlbumList(releaseTypeTitle(selectedReleaseType), releases));
    }

    /** Server release types without a string of their own are shown as reported, capitalised. */
    private String releaseTypeTitle(String type) {
        switch (type) {
            case "album": return getString(R.string.artist_page_title_album_section);
            case "ep": return getString(R.string.artist_page_title_ep_section);
            case "single": return getString(R.string.artist_page_title_single_section);
            case "compilation": return getString(R.string.artist_page_title_compilation_section);
            case "soundtrack": return getString(R.string.artist_page_title_soundtrack_section);
            case "live": return getString(R.string.artist_page_title_live_section);
            case "remix": return getString(R.string.artist_page_title_remix_section);
            default: return Character.toUpperCase(type.charAt(0)) + type.substring(1);
        }
    }

    private void navigateToAlbumList(String title, List<com.eddyizm.tempus.subsonic.models.AlbumID3> albums) {
        Bundle bundle = new Bundle();
        bundle.putString(Constants.ALBUM_LIST_TITLE, title);
        // #688: strip each album's heavy nested lists so this whole list (the single
        // biggest nav argument, ~34KB) stays small in the saved-state Bundle.
        ArrayList<com.eddyizm.tempus.subsonic.models.AlbumID3> albumsForNav = new ArrayList<>(albums.size());
        for (com.eddyizm.tempus.subsonic.models.AlbumID3 albumForNav : albums) {
            albumsForNav.add(albumForNav.strippedForNav());
        }
        bundle.putParcelableArrayList(Constants.ALBUMS_OBJECT, albumsForNav);
        Navigation.findNavController(requireView()).navigate(R.id.albumListPageFragment, bundle);
    }

    private void initSimilarArtistsView() {
        bind.similarArtistsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.similarArtistsRecyclerView.setHasFixedSize(true);
        bind.similarArtistsRecyclerView.setNestedScrollingEnabled(false);

        similarArtistAdapter = new ArtistCarouselAdapter(this);
        bind.similarArtistsRecyclerView.setAdapter(similarArtistAdapter);

        artistPageViewModel.getArtistInfo(artistPageViewModel.getArtist().getId()).observe(getViewLifecycleOwner(), artist -> {
            if (artist == null) {
                if (bind != null) bind.similarArtistSector.setVisibility(View.GONE);
            } else {
                if (bind != null && artist.getSimilarArtists() != null)
                    bind.similarArtistSector.setVisibility(!artist.getSimilarArtists().isEmpty() ? View.VISIBLE : View.GONE);

                List<ArtistID3> artists = new ArrayList<>();

                if (artist.getSimilarArtists() != null) {
                    artists.addAll(artist.getSimilarArtists());
                }

                similarArtistAdapter.setItems(artists);
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

    @Override
    public void onAlbumClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumPageFragment, bundle);
    }

    @Override
    public void onAlbumLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumBottomSheetDialog, bundle);
    }

    @Override
    public void onArtistClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistPageFragment, bundle);
    }

    @Override
    public void onArtistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistBottomSheetDialog, bundle);
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
}
