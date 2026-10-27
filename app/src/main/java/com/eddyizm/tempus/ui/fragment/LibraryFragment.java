package com.eddyizm.tempus.ui.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.Navigation;

import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentLibraryBinding;
import com.eddyizm.tempus.helper.recyclerview.CustomLinearSnapHelper;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.navigation.NavigationController;
import com.eddyizm.tempus.repository.PlaylistRepository;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.adapter.AlbumAdapter;
import com.eddyizm.tempus.ui.adapter.ArtistAdapter;
import com.eddyizm.tempus.ui.adapter.GenreAdapter;
import com.eddyizm.tempus.ui.adapter.MusicFolderAdapter;
import com.eddyizm.tempus.ui.adapter.PlaylistHorizontalAdapter;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.viewmodel.LibraryViewModel;
import com.eddyizm.tempus.viewmodel.MainViewModel;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@UnstableApi
public class LibraryFragment extends Fragment implements ClickCallback {
    private static final String TAG = "LibraryFragment";

    private FragmentLibraryBinding bind;
    private MainActivity activity;
    private NavigationController navigationController;
    private LibraryViewModel libraryViewModel;
    private MainViewModel mainViewModel;

    private MusicFolderAdapter musicFolderAdapter;
    private AlbumAdapter albumAdapter;
    private ArtistAdapter artistAdapter;
    private GenreAdapter genreAdapter;
    private PlaylistHorizontalAdapter playlistHorizontalAdapter;

    private MaterialToolbar materialToolbar;

    private final Map<View, View> anchorChips = new LinkedHashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        navigationController = activity.getNavigationController();

        bind = FragmentLibraryBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        libraryViewModel = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);
        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        libraryViewModel.clearCacheIfMusicFolderChanged();

        init();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initAppBar();
        initMusicFolderView();
        initAlbumView();
        initArtistView();
        initGenreView();
        initPlaylistView();
        initSectionCounts();
        initSwipeToRefresh();
        initMusicFolderChangeListener();
    }

    private void initMusicFolderChangeListener() {
        mainViewModel.getActiveMusicFolderId().observe(getViewLifecycleOwner(), musicFolderId ->
                libraryViewModel.reloadIfMusicFolderChanged(getViewLifecycleOwner()));
    }

    @Override
    public void onStart() {
        super.onStart();
        activity.toggleBottomNavigationBarVisibilityOnOrientationChange();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshPlaylistView();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        anchorChips.clear();
        bind = null;
    }

    private void init() {
        bind.albumCatalogueTextViewClickable.setOnClickListener(v -> activity.navController.navigate(R.id.action_libraryFragment_to_albumCatalogueFragment));
        bind.artistCatalogueTextViewClickable.setOnClickListener(v -> activity.navController.navigate(R.id.action_libraryFragment_to_artistCatalogueFragment));
        bind.genreCatalogueTextViewClickable.setOnClickListener(v -> activity.navController.navigate(R.id.action_libraryFragment_to_genreCatalogueFragment));
        bind.playlistCatalogueTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.PLAYLIST_ALL, Constants.PLAYLIST_ALL);
            activity.navController.navigate(R.id.action_libraryFragment_to_playlistCatalogueFragment, bundle);
        });

        bind.albumRefreshButton.setOnClickListener(v -> libraryViewModel.refreshAlbumSample(getViewLifecycleOwner()));
        bind.artistRefreshButton.setOnClickListener(v -> libraryViewModel.refreshArtistSample(getViewLifecycleOwner()));

        // Long press on a title predates the refresh buttons and still refreshes that section alone.
        bind.albumCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            libraryViewModel.refreshAlbumSample(getViewLifecycleOwner());
            return true;
        });
        bind.artistCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            libraryViewModel.refreshArtistSample(getViewLifecycleOwner());
            return true;
        });
        bind.genreCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            libraryViewModel.refreshGenreSample(getViewLifecycleOwner());
            return true;
        });
        bind.playlistCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            libraryViewModel.refreshPlaylistSample(getViewLifecycleOwner());
            return true;
        });
    }

    private void initAppBar() {
        materialToolbar = bind.getRoot().findViewById(R.id.toolbar);

        activity.setSupportActionBar(materialToolbar);
        navigationController.setHamburgerMenuForLandscape(activity, materialToolbar);

        Objects.requireNonNull(materialToolbar.getOverflowIcon()).setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));

        AppBarLayout appBarLayout = bind.getRoot().findViewById(R.id.toolbar_fragment);
        View anchors = getLayoutInflater().inflate(R.layout.view_library_anchor_chips, appBarLayout, false);
        appBarLayout.addView(anchors);

        anchorChips.put(anchors.findViewById(R.id.library_anchor_folders), bind.libraryMusicFolderSector);
        anchorChips.put(anchors.findViewById(R.id.library_anchor_albums), bind.libraryAlbumSector);
        anchorChips.put(anchors.findViewById(R.id.library_anchor_artists), bind.libraryArtistSector);
        anchorChips.put(anchors.findViewById(R.id.library_anchor_genres), bind.libraryGenresSector);
        anchorChips.put(anchors.findViewById(R.id.library_anchor_playlists), bind.libraryPlaylistSector);

        for (Map.Entry<View, View> anchor : anchorChips.entrySet()) {
            View sector = anchor.getValue();
            anchor.getKey().setOnClickListener(v -> bind.fragmentLibraryNestedScrollView.smoothScrollTo(0, sector.getTop()));
        }

        // Sections come and go with the server's answers; a chip whose section is hidden would jump nowhere.
        bind.libraryLinearLayoutContainer.getViewTreeObserver().addOnGlobalLayoutListener(this::syncAnchorChips);
        syncAnchorChips();
    }

    private void syncAnchorChips() {
        for (Map.Entry<View, View> anchor : anchorChips.entrySet()) {
            int visibility = anchor.getValue().getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE;
            if (anchor.getKey().getVisibility() != visibility) anchor.getKey().setVisibility(visibility);
        }
    }

    private void initSectionCounts() {
        LifecycleOwner owner = getViewLifecycleOwner();
        libraryViewModel.getAlbumCount(owner).observe(owner, count -> setSectionCount(bind.albumCountTextView, count));
        libraryViewModel.getArtistCount(owner).observe(owner, count -> setSectionCount(bind.artistCountTextView, count));
        libraryViewModel.getGenreCount().observe(owner, count -> setSectionCount(bind.genreCountTextView, count));
        libraryViewModel.getPlaylistCount().observe(owner, count -> setSectionCount(bind.playlistCountTextView, count));
    }

    private void setSectionCount(TextView view, Integer count) {
        if (bind == null) return;
        view.setText(count != null ? getString(R.string.library_section_count, count) : null);
    }

    private void initMusicFolderView() {
        if (!Preferences.isMusicDirectorySectionVisible()) {
            bind.libraryMusicFolderSector.setVisibility(View.GONE);
            return;
        }

        bind.musicFolderRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.musicFolderRecyclerView.setHasFixedSize(true);

        musicFolderAdapter = new MusicFolderAdapter(this);
        bind.musicFolderRecyclerView.setAdapter(musicFolderAdapter);
        libraryViewModel.getMusicFolders(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), musicFolders -> {
            if (musicFolders == null) {
                if (bind != null) bind.libraryMusicFolderSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryMusicFolderSector.setVisibility(!musicFolders.isEmpty() ? View.VISIBLE : View.GONE);

                musicFolderAdapter.setItems(musicFolders);
            }
        });
    }

    private void initAlbumView() {
        bind.albumRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.albumRecyclerView.setHasFixedSize(true);

        albumAdapter = new AlbumAdapter(this);
        bind.albumRecyclerView.setAdapter(albumAdapter);
        libraryViewModel.getAlbumSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.libraryAlbumSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryAlbumSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);

                albumAdapter.setItems(albums);
            }
        });

        CustomLinearSnapHelper albumSnapHelper = new CustomLinearSnapHelper();
        albumSnapHelper.attachToRecyclerView(bind.albumRecyclerView);
    }

    private void initArtistView() {
        bind.artistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.artistRecyclerView.setHasFixedSize(true);

        artistAdapter = new ArtistAdapter(this, false, false);
        bind.artistRecyclerView.setAdapter(artistAdapter);
        libraryViewModel.getArtistSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), artists -> {
            if (artists == null) {
                if (bind != null) bind.libraryArtistSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryArtistSector.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);

                artistAdapter.setItems(artists);
            }
        });

        CustomLinearSnapHelper artistSnapHelper = new CustomLinearSnapHelper();
        artistSnapHelper.attachToRecyclerView(bind.artistRecyclerView);
    }

    private void initGenreView() {
        bind.genreRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2, GridLayoutManager.HORIZONTAL, false));
        bind.genreRecyclerView.setHasFixedSize(true);

        genreAdapter = new GenreAdapter(this);
        bind.genreRecyclerView.setAdapter(genreAdapter);

        libraryViewModel.getGenreSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), genres -> {
            if (genres == null) {
                if (bind != null) bind.libraryGenresSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryGenresSector.setVisibility(!genres.isEmpty() ? View.VISIBLE : View.GONE);

                genreAdapter.setItems(genres);
            }
        });

        CustomLinearSnapHelper genreSnapHelper = new CustomLinearSnapHelper();
        genreSnapHelper.attachToRecyclerView(bind.genreRecyclerView);
    }

    private void initPlaylistView() {
        bind.playlistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.playlistRecyclerView.setHasFixedSize(true);

        playlistHorizontalAdapter = new PlaylistHorizontalAdapter(this);
        bind.playlistRecyclerView.setAdapter(playlistHorizontalAdapter);
        libraryViewModel.getPlaylistSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), playlists -> {
            if (playlists == null) {
                if (bind != null) bind.libraryPlaylistSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryPlaylistSector.setVisibility(!playlists.isEmpty() ? View.VISIBLE : View.GONE);

                playlistHorizontalAdapter.setItems(playlists);
            }
        });
    }

    private void refreshPlaylistView() {
        final Handler handler = new Handler(Looper.getMainLooper());

        final Runnable runnable = () -> {
            if (getView() != null && bind != null && libraryViewModel != null)
                libraryViewModel.refreshPlaylistSample(getViewLifecycleOwner());
        };

        handler.postDelayed(runnable, 100);
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

    @Override
    public void onGenreClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songListPageFragment, bundle);
    }

    @Override
    public void onPlaylistClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.playlistPageFragment, bundle);
    }

    @Override
    public void onPlaylistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.playlistRowBottomSheetDialog, bundle);
    }

    @Override
    public void onMusicFolderClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.indexFragment, bundle);
    }

    public void initSwipeToRefresh() {
        bind.swipeLibraryToRefresh.setOnRefreshListener(() -> {
            pullToRefresh();
            bind.swipeLibraryToRefresh.setRefreshing(false);
        });
    }

    private void pullToRefresh() {
        LifecycleOwner lifecycleOwner = getViewLifecycleOwner();
        libraryViewModel.refreshAlbumSample(lifecycleOwner);
        libraryViewModel.refreshGenreSample(lifecycleOwner);
        libraryViewModel.refreshArtistSample(lifecycleOwner);
        libraryViewModel.refreshPlaylistSample(lifecycleOwner);
        libraryViewModel.refreshArtistAndAlbumCount(lifecycleOwner);
    }
}
