package com.eddyizm.tempus.ui.fragment;

import androidx.core.os.BundleCompat;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentSearchBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.service.MediaManager;
import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Playlist;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.adapter.SearchResultAdapter;
import com.eddyizm.tempus.ui.adapter.SongHorizontalAdapter;
import com.eddyizm.tempus.ui.adapter.PlaylistHorizontalAdapter;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.viewmodel.PlaybackViewModel;
import com.eddyizm.tempus.viewmodel.SearchViewModel;
import com.eddyizm.tempus.subsonic.models.PlaylistWithSongs;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@UnstableApi
public class SearchFragment extends Fragment implements ClickCallback {
    private static final String TAG = "SearchFragment";

    private FragmentSearchBinding bind;
    private MainActivity activity;
    private SearchViewModel searchViewModel;
    private PlaybackViewModel playbackViewModel;

    private SearchResultAdapter artistAdapter;
    private SearchResultAdapter albumAdapter;

    private List<ArtistID3> artists = Collections.emptyList();
    private List<AlbumID3> albums = Collections.emptyList();
    private List<Child> songs = Collections.emptyList();
    private SongHorizontalAdapter songHorizontalAdapter;
    private PlaylistHorizontalAdapter playlistHorizontalAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    // Survives the view being rebuilt when the user comes back from a result, so the results do too.
    private String submittedQuery;
    private boolean settingQuery;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentSearchBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        searchViewModel = new ViewModelProvider(requireActivity()).get(SearchViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        initSearchResultView();
        initSearchView();

        if (submittedQuery != null) {
            search(submittedQuery);
        } else {
            inputFocus();
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeMediaBrowser();

        MediaManager.registerPlaybackObserver(mediaBrowserListenableFuture, playbackViewModel);
        observePlayback();
    }

    @Override
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
        super.onDestroyView();
        bind = null;
    }

    private void initSearchResultView() {
        bind.searchResultArtistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        artistAdapter = SearchResultAdapter.forArtists(this);
        bind.searchResultArtistRecyclerView.setAdapter(artistAdapter);

        bind.searchResultAlbumRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        albumAdapter = SearchResultAdapter.forAlbums(this);
        bind.searchResultAlbumRecyclerView.setAdapter(albumAdapter);

        bind.searchResultTracksRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        songHorizontalAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, true, false, null);
        songHorizontalAdapter.setShowKind(true);
        songHorizontalAdapter.setDenseRows(true);
        setMediaBrowserListenableFuture();
        reapplyPlayback();

        bind.searchResultTracksRecyclerView.setAdapter(songHorizontalAdapter);

        bind.allsongsview.setLayoutManager(new LinearLayoutManager(requireContext()));

        playlistHorizontalAdapter = new PlaylistHorizontalAdapter(this);
        bind.allsongsview.setAdapter(playlistHorizontalAdapter);

        bind.searchFilterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> showResult());
    }

    private void initSearchView() {
        setRecentSuggestions();

        bind.searchBackButton.setOnClickListener(v -> {
            hideKeyboard();
            Navigation.findNavController(requireView()).navigateUp();
        });

        bind.searchClearButton.setOnClickListener(v -> {
            bind.searchEditText.setText(null);
            inputFocus();
        });

        bind.searchEditText.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            String query = bind.searchEditText.getText().toString();

            if (isQueryValid(query)) {
                search(query);
                return true;
            }

            return false;
        });

        bind.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                bind.searchClearButton.setVisibility(charSequence.length() > 0 ? View.VISIBLE : View.GONE);
                if (settingQuery) return;

                submittedQuery = null;
                showSuggestions();

                if (start + count > 1) {
                    setSearchSuggestions(charSequence.toString());
                } else {
                    setRecentSuggestions();
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private void showSuggestions() {
        bind.searchViewSuggestionContainer.setVisibility(View.VISIBLE);
        bind.searchResultLayout.setVisibility(View.GONE);
    }

    private void showResults() {
        bind.searchViewSuggestionContainer.setVisibility(View.GONE);
        bind.searchResultLayout.setVisibility(View.VISIBLE);
    }

    public void setRecentSuggestions() {
        bind.searchViewSuggestionContainer.removeAllViews();

        List<String> recentSearches = searchViewModel.getRecentSearchSuggestion();
        if (!recentSearches.isEmpty()) {
            TextView header = new TextView(requireContext(), null, 0, R.style.Browse_Subheader);
            header.setText(R.string.search_recent_title);
            header.setTypeface(ResourcesCompat.getFont(requireContext(), R.font.inter_semi_bold));
            int top = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
            header.setPadding(header.getPaddingStart(), top, header.getPaddingEnd(), header.getPaddingBottom());
            bind.searchViewSuggestionContainer.addView(header);
        }

        for (String suggestion : recentSearches) {
            View view = LayoutInflater.from(bind.searchViewSuggestionContainer.getContext()).inflate(R.layout.item_search_suggestion, bind.searchViewSuggestionContainer, false);

            ImageView leadingImageView = view.findViewById(R.id.search_suggestion_icon);
            TextView titleView = view.findViewById(R.id.search_suggestion_title);
            ImageView tailingImageView = view.findViewById(R.id.search_suggestion_delete_icon);

            leadingImageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_history, null));
            titleView.setText(suggestion);

            view.setOnClickListener(v -> search(suggestion));

            tailingImageView.setOnClickListener(v -> {
                searchViewModel.deleteRecentSearch(suggestion);
                setRecentSuggestions();
            });

            bind.searchViewSuggestionContainer.addView(view);
        }
    }

    public void setSearchSuggestions(String query) {
        searchViewModel.getSearchSuggestion(query).observe(getViewLifecycleOwner(), suggestions -> {
            bind.searchViewSuggestionContainer.removeAllViews();

            for (String suggestion : suggestions) {
                View view = LayoutInflater.from(bind.searchViewSuggestionContainer.getContext()).inflate(R.layout.item_search_suggestion, bind.searchViewSuggestionContainer, false);

                ImageView leadingImageView = view.findViewById(R.id.search_suggestion_icon);
                TextView titleView = view.findViewById(R.id.search_suggestion_title);
                ImageView tailingImageView = view.findViewById(R.id.search_suggestion_delete_icon);

                leadingImageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_search, null));
                titleView.setText(suggestion);
                tailingImageView.setVisibility(View.GONE);

                view.setOnClickListener(v -> search(suggestion));

                bind.searchViewSuggestionContainer.addView(view);
            }
        });
    }

    public void search(String query) {
        submittedQuery = query;
        searchViewModel.setQuery(query);
        bind.allSongs.setText(R.string.search_all_songs_loading);
        playlistHorizontalAdapter.setItems(Collections.emptyList());

        settingQuery = true;
        bind.searchEditText.setText(query);
        bind.searchEditText.setSelection(query.length());
        settingQuery = false;

        hideKeyboard();
        showResults();
        performSearch(query);
    }

    public void updateUI(List<Playlist> allSongs) {
        if (allSongs != null && !allSongs.isEmpty()) {
            playlistHorizontalAdapter.setItems(allSongs);
            if (getView() != null && bind != null) {
                bind.allSongs.setText(this.getView().getContext().getString(R.string.search_all_songs_play, String.valueOf(allSongs.get(0).getName())));
            }
        } else {
            playlistHorizontalAdapter.setItems(Collections.emptyList());
        }
    }
    private void performSearch(String query) {
        searchViewModel.search3(this, query).observe(getViewLifecycleOwner(), result -> {
            if (bind == null) return;

            artists = result.getArtists() != null ? result.getArtists() : Collections.emptyList();
            albums = result.getAlbums() != null ? result.getAlbums() : Collections.emptyList();
            songs = result.getSongs() != null ? result.getSongs() : Collections.emptyList();

            artistAdapter.setItems(artists);
            albumAdapter.setItems(albums);
            songHorizontalAdapter.setItems(songs);

            showResult();
        });
    }

    private void showResult() {
        if (bind == null) return;

        int checked = bind.searchFilterChipGroup.getCheckedChipId();
        boolean all = checked == R.id.search_filter_all;
        boolean showArtists = (all || checked == R.id.search_filter_artists) && !artists.isEmpty();
        boolean showAlbums = (all || checked == R.id.search_filter_albums) && !albums.isEmpty();
        boolean showSongs = (all || checked == R.id.search_filter_songs) && !songs.isEmpty();

        bind.searchArtistSector.setVisibility(showArtists ? View.VISIBLE : View.GONE);
        bind.searchAlbumSector.setVisibility(showAlbums ? View.VISIBLE : View.GONE);
        bind.searchSongSector.setVisibility(showSongs ? View.VISIBLE : View.GONE);
        bind.emptyResponseContainer.setVisibility(showArtists || showAlbums || showSongs ? View.GONE : View.VISIBLE);
        bind.searchEmptyTitle.setText(getString(R.string.search_no_results_title, submittedQuery));

        showTopResult(all);
    }

    /**
     * The best match is the first artist, else the first album, else the first song: the server
     * ranks within each kind only, and a query that names an artist most likely means the artist.
     */
    private void showTopResult(boolean all) {
        if (!all || (artists.isEmpty() && albums.isEmpty() && songs.isEmpty())) {
            bind.searchTopResultCard.setVisibility(View.GONE);
            return;
        }

        Context context = requireContext();
        ShapeAppearanceModel tile = ShapeAppearanceModel.builder(context, 0, R.style.ShapeAppearance_Browse_Tile).build();
        bind.searchTopResultCard.setVisibility(View.VISIBLE);

        if (!artists.isEmpty()) {
            ArtistID3 artist = artists.get(0);
            bind.searchTopResultTitle.setText(artist.getName());
            bind.searchTopResultKind.setText(SearchResultAdapter.artistKind(context));
            bind.searchTopResultCover.setShapeAppearanceModel(tile.withCornerSize(new RelativeCornerSize(0.5f)));
            CustomGlideRequest.Builder.from(context, artist.getCoverArtId(), CustomGlideRequest.ResourceType.Artist).build().into(bind.searchTopResultCover);
            bind.searchTopResultCard.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ARTIST_OBJECT, artist.strippedForNav());
                onArtistClick(bundle);
            });
        } else if (!albums.isEmpty()) {
            AlbumID3 album = albums.get(0);
            bind.searchTopResultTitle.setText(album.getName());
            bind.searchTopResultKind.setText(SearchResultAdapter.albumKind(context, album));
            bind.searchTopResultCover.setShapeAppearanceModel(tile);
            CustomGlideRequest.Builder.from(context, album.getCoverArtId(), CustomGlideRequest.ResourceType.Album).build().into(bind.searchTopResultCover);
            bind.searchTopResultCard.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ALBUM_OBJECT, album.strippedForNav());
                onAlbumClick(bundle);
            });
        } else {
            Child song = songs.get(0);
            bind.searchTopResultTitle.setText(song.getTitle());
            bind.searchTopResultKind.setText(getString(R.string.search_result_song, song.getArtist()));
            bind.searchTopResultCover.setShapeAppearanceModel(tile);
            CustomGlideRequest.Builder.from(context, song.getCoverArtId(), CustomGlideRequest.ResourceType.Song).build().into(bind.searchTopResultCover);
            bind.searchTopResultCard.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(Collections.singletonList(song)));
                bundle.putInt(Constants.ITEM_POSITION, 0);
                onMediaClick(bundle);
            });
        }
    }

    private boolean isQueryValid(String query) {
        return !query.equals("") && query.trim().length() > 1;
    }

    private void inputFocus() {
        EditText editText = bind.searchEditText;
        editText.requestFocus();
        new WindowInsetsControllerCompat(requireActivity().getWindow(), editText).show(WindowInsetsCompat.Type.ime());
    }

    private void hideKeyboard() {
        new WindowInsetsControllerCompat(requireActivity().getWindow(), bind.searchEditText).hide(WindowInsetsCompat.Type.ime());
        bind.searchEditText.clearFocus();
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
        songHorizontalAdapter.notifyDataSetChanged();
        activity.setBottomSheetInPeek(true);
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }

    @Override
    public void onPlaylistClick(Bundle bundle) {
        PlaylistWithSongs playlistWithSongs = BundleCompat.getParcelable(bundle, Constants.PLAYLIST_OBJECT, PlaylistWithSongs.class);
        if (playlistWithSongs != null) {
            MediaManager.startQueue(mediaBrowserListenableFuture, playlistWithSongs.getEntries(), 0);
        }
    }

    @Override
    public void onPlaylistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.playlistBottomSheetDialog, bundle);
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
