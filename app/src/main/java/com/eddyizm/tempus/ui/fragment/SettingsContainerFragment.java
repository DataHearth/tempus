package com.eddyizm.tempus.ui.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.core.widget.TextViewCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.scheduler.Requirements;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.BuildConfig;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.interfaces.DialogClickCallback;
import com.eddyizm.tempus.interfaces.ScanCallback;
import com.eddyizm.tempus.equalizer.EqualizerManager;
import com.eddyizm.tempus.equalizer.ParametricProfile;
import com.eddyizm.tempus.service.DownloaderService;
import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.dialog.DeleteDownloadStorageDialog;
import com.eddyizm.tempus.ui.dialog.DownloadStorageDialog;
import com.eddyizm.tempus.ui.dialog.StarredAlbumSyncDialog;
import com.eddyizm.tempus.ui.dialog.StarredArtistSyncDialog;
import com.eddyizm.tempus.ui.dialog.StarredSyncDialog;
import com.eddyizm.tempus.ui.dialog.StreamingCacheStorageDialog;
import com.eddyizm.tempus.ui.login.LoginActivity;
import com.eddyizm.tempus.util.ClickablePreferenceCategory;
import com.eddyizm.tempus.util.DownloadUtil;
import com.eddyizm.tempus.subsonic.models.MusicFolder;
import com.eddyizm.tempus.util.ExternalAudioReader;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.util.UIUtil;
import com.eddyizm.tempus.viewmodel.MainViewModel;
import com.eddyizm.tempus.viewmodel.SettingViewModel;
import com.google.android.material.color.MaterialColors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@OptIn(markerClass = UnstableApi.class)
public class SettingsContainerFragment extends PreferenceFragmentCompat {

    private static final String TAG = "SettingsFragment";
    private MainActivity activity;

    private SettingViewModel settingViewModel;
    private MainViewModel mainViewModel;

    private ActivityResultLauncher<Intent> directoryPickerLauncher;

    private MediaService.LocalBinder mediaServiceBinder;
    private boolean isServiceBound = false;
    private boolean pendingEqualizerReload = false;
    private ActivityResultLauncher<Intent> equalizerResultLauncher;
    private ActivityResultLauncher<String[]> parametricEqPickerLauncher;

    private static final int PARAMETRIC_EQ_MAX_BYTES = 16 * 1024;

    private static final String STATE_OPEN_CATEGORY = "open_category";

    // Root categories that begin a new card on the category index.
    private static final Set<String> CARD_GROUP_STARTS = new HashSet<>(Arrays.asList(
            "network_playback", "settings_misc_category_key"));

    // Null on the category index; otherwise the key of the category whose page is showing.
    private String openCategoryKey;

    // Kept apart from the categories, which carry them only on the index: as section headers
    // in search results they would add noise, and the filter would match against them.
    private final Map<String, CharSequence> categorySummaries = new HashMap<>();
    private final Map<String, Drawable> categoryIcons = new HashMap<>();

    private OnBackPressedCallback closeCategoryCallback;

    private int cardColor;
    private int cardSeparatorColor;
    private int cardRippleColor;

    private boolean musicFoldersObserved = false;

    // Null until the server answers with its folder list.
    private Integer musicFolderCount = null;

    private String searchQuery = "";

    public void setSearchQuery(String query) {
        this.searchQuery = UIUtil.normalizeForSearch(query).trim();
        applyPageState();
    }

    /**
     * Leaves a category page for the category index.
     *
     * @return false when the index is already showing, so the caller can leave the settings.
     */
    public boolean closeCategory() {
        if (openCategoryKey == null) return false;
        openCategoryKey = null;
        applyPageState();
        scrollToTop();
        return true;
    }

    private void openCategory(String key) {
        openCategoryKey = key;
        applyPageState();
        scrollToTop();
    }

    private void scrollToTop() {
        if (getView() != null) getListView().scrollToPosition(0);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            openCategoryKey = savedInstanceState.getString(STATE_OPEN_CATEGORY);
        }
        super.onCreate(savedInstanceState);

        closeCategoryCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                closeCategory();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, closeCategoryCallback);

        equalizerResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {}
        );

        parametricEqPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) importParametricEq(uri);
                });

        directoryPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            Uri uri = data.getData();
                            if (uri != null) {
                                requireContext().getContentResolver().takePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                );

                                Preferences.setDownloadDirectoryUri(uri.toString());
                                ExternalAudioReader.refreshCache();
                                Toast.makeText(requireContext(), R.string.settings_download_folder_set, Toast.LENGTH_SHORT).show();
                                checkDownloadDirectory();
                            }
                        }
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        View view = super.onCreateView(inflater, container, savedInstanceState);
        settingViewModel = new ViewModelProvider(requireActivity()).get(SettingViewModel.class);
        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        if (view != null) {
            getListView().setPadding(0, 0, 0, (int) getResources().getDimension(R.dimen.global_padding_bottom));
        }
        // The library divider spans the whole list, outside the cards; the cards draw their own.
        setDivider(null);

        Context context = requireContext();
        cardColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainer, Color.TRANSPARENT);
        cardSeparatorColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant, Color.TRANSPARENT);
        cardRippleColor = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorControlHighlight, Color.TRANSPARENT);

        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_OPEN_CATEGORY, openCategoryKey);
    }

    @SuppressLint("RestrictedApi")
    @NonNull
    @Override
    protected RecyclerView.Adapter onCreateAdapter(@NonNull PreferenceScreen preferenceScreen) {
        // PreferenceGroupAdapter is library-internal, but it is the only place that knows which
        // preference a row shows, which the cards need to know where each one starts and ends.
        return new PreferenceGroupAdapter(preferenceScreen) {
            @Override
            public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
                super.onBindViewHolder(holder, position);
                Preference preference = getItem(position);
                if (preference instanceof SeekBarPreference) styleSeekBarRow(holder);
                boolean card = isCardRow(preference);
                boolean first = card && (position == 0
                        || !isCardRow(getItem(position - 1))
                        || startsCardGroup(preference));
                boolean last = card && (position == getItemCount() - 1
                        || !isCardRow(getItem(position + 1))
                        || startsCardGroup(getItem(position + 1)));
                // Two cards in a row share one 8dp gap rather than stacking both margins.
                boolean followsCard = first && position > 0 && isCardRow(getItem(position - 1));
                styleCardRow(holder.itemView, card, first, last, followsCard);
            }
        };
    }

    // SeekBarPreference keeps the library layout, which holds the seekbar ids it binds to.
    private static void styleSeekBarRow(PreferenceViewHolder holder) {
        int[][] textViews = {
                {android.R.id.title, R.style.TextAppearance_Settings_RowTitle},
                {android.R.id.summary, R.style.TextAppearance_Settings_RowSummary},
                {androidx.preference.R.id.seekbar_value, R.style.TextAppearance_Settings_RowSummary},
        };
        for (int[] entry : textViews) {
            View view = holder.findViewById(entry[0]);
            if (view instanceof TextView) {
                TextViewCompat.setTextAppearance((TextView) view, entry[1]);
            }
        }
    }

    private boolean isIndexShowing() {
        return searchQuery.isEmpty() && openCategoryKey == null;
    }

    /**
     * Settings sit in rounded cards. Category headers, and the explanatory notes that have no
     * title and cannot be tapped, sit between cards instead.
     */
    private boolean isCardRow(@Nullable Preference preference) {
        if (preference == null) return false;
        if (preference instanceof PreferenceCategory) {
            return isIndexShowing() && preference.getParent() == getPreferenceScreen();
        }
        return preference.isSelectable() || !TextUtils.isEmpty(preference.getTitle());
    }

    private boolean startsCardGroup(@Nullable Preference preference) {
        return preference != null && isIndexShowing() && CARD_GROUP_STARTS.contains(preference.getKey());
    }

    private void styleCardRow(View row, boolean card, boolean first, boolean last, boolean followsCard) {
        float density = getResources().getDisplayMetrics().density;
        int side = card ? Math.round(16 * density) : 0;
        int gap = Math.round(8 * density);

        ViewGroup.LayoutParams params = row.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            int top = first && !followsCard ? gap : 0;
            ((ViewGroup.MarginLayoutParams) params).setMargins(side, top, side, last ? gap : 0);
            row.setLayoutParams(params);
        }

        // Non-card rows keep the background resetState() restored before this bind.
        if (!card) return;

        float radius = 16 * density;
        float top = first ? radius : 0;
        float bottom = last ? radius : 0;
        float[] radii = {top, top, top, top, bottom, bottom, bottom, bottom};

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadii(radii);
        shape.setColor(cardColor);

        Drawable content = shape;
        if (!first) {
            GradientDrawable separator = new GradientDrawable();
            separator.setColor(cardSeparatorColor);
            LayerDrawable layers = new LayerDrawable(new Drawable[]{shape, separator});
            layers.setLayerGravity(1, Gravity.TOP | Gravity.FILL_HORIZONTAL);
            layers.setLayerHeight(1, Math.max(1, Math.round(density)));
            content = layers;
        }

        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadii(radii);
        mask.setColor(Color.WHITE);

        row.setBackground(new RippleDrawable(ColorStateList.valueOf(cardRippleColor), content, mask));
    }

    @Override
    public void onStart() {
        super.onStart();
        activity.setBottomNavigationBarVisibility(false);
        activity.setBottomSheetVisibility(false);
    }

    @Override
    public void onResume() {
        super.onResume();

        setStreamingCacheSize();
        setAppLanguage();
        setMusicLibrary();
        setVersion();
        setNetorkPingTimeoutBase();

        actionPermission();
        actionTheme();
        actionLogout();
        actionScan();
        actionSyncStarredAlbums();
        actionSyncStarredTracks();
        actionSyncStarredArtists();
        actionChangeStreamingCacheStorage();
        actionChangeDownloadStorage();
        actionSetSongPreloadBuffer();
        actionSetDownloadDirectory();
        actionDeleteDownloadStorage();
        actionKeepScreenOn();
        actionAutoDownloadLyrics();
        actionMiniPlayerHeart();
        actionLanRemote();

        bindMediaService();
        actionBuiltinEqualizer();
        actionEqualizerSelector();
        actionParametricEqImport();
        actionReplayGainPreamp();

        applyPageState();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // The flag means "this view already has the observer", and a child fragment can outlive
        // its parent's view, so it has to be cleared with the view rather than the instance.
        musicFoldersObserved = false;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.global_preferences, rootKey);

        if (!BuildConfig.FLAVOR.equals("tempus")) {
            PreferenceCategory githubUpdateCategory = findPreference("settings_github_update_category_key");
            if (githubUpdateCategory != null) {
                getPreferenceScreen().removePreference(githubUpdateCategory);
            }
        }

        if (BuildConfig.FLAVOR.equals("degoogled")) {
            ClickablePreferenceCategory androidAutoCategory = findPreference("settings_androidauto_category_key");
            if (androidAutoCategory != null) {
                getPreferenceScreen().removePreference(androidAutoCategory);
            }
        }

        PreferenceScreen screen = getPreferenceScreen();
        if (screen != null) {
            applyRowLayouts(screen);
            for (int i = 0; i < screen.getPreferenceCount(); i++) {
                Preference pref = screen.getPreference(i);
                if (pref instanceof ClickablePreferenceCategory) {
                    ClickablePreferenceCategory category = (ClickablePreferenceCategory) pref;
                    if (category.getKey() == null) {
                        category.setKey("category_key_" + i);
                    }
                    categorySummaries.put(category.getKey(), category.getSummary());
                    categoryIcons.put(category.getKey(), category.getIcon());
                    category.setOnClickListener(cat -> {
                        String key = cat.getKey();
                        if ("lan_playback".equals(key)) {
                            startActivity(new Intent(requireContext(), com.eddyizm.tempus.lan.LanPlaybackActivity.class));
                            return;
                        }
                        if (isIndexShowing()) openCategory(key);
                    });
                }
            }
            if (openCategoryKey != null && findPreference(openCategoryKey) == null) {
                openCategoryKey = null;
            }
        }

        SwitchPreference downloadWifiOnlyPreference = findPreference("download_wifi_only");
        if (downloadWifiOnlyPreference != null) {
            downloadWifiOnlyPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof Boolean) {
                    boolean enabled = (Boolean) newValue;
                    Preferences.setDownloadWifiOnly(enabled);
                    Requirements requirements = enabled
                            ? new Requirements(Requirements.NETWORK_UNMETERED)
                            : new Requirements(0);
                    DownloadService.sendSetRequirements(requireContext(), DownloaderService.class, requirements, false);
                }
                return true;
            });
        }
    }

    /**
     * Root categories are left alone: applyPageState swaps their layout for each page.
     */
    private static void applyRowLayouts(PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            pref.setIconSpaceReserved(false);
            if (pref instanceof PreferenceGroup) {
                if (!(pref instanceof ClickablePreferenceCategory)) {
                    pref.setLayoutResource(R.layout.preference_settings_header);
                }
                applyRowLayouts((PreferenceGroup) pref);
                continue;
            }
            if (pref instanceof SeekBarPreference) continue;
            if (!pref.isSelectable() && TextUtils.isEmpty(pref.getTitle())) {
                pref.setLayoutResource(R.layout.preference_settings_note);
            } else {
                pref.setLayoutResource(R.layout.preference_settings_row);
            }
            if (pref instanceof SwitchPreference) {
                pref.setWidgetLayoutResource(R.layout.preference_settings_switch);
            }
        }
    }

    /**
     * Shows one of three pages from the single preference tree: the category index (each root
     * category as a row, children hidden), one category's page (only that category, its header
     * collapsed to nothing), or search results across every category. Keeping one tree is what
     * lets every findPreference(...) in this class work whichever page is showing.
     */
    private void applyPageState() {
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) return;

        resetVisibility(screen);

        checkSystemEqualizer();
        checkCacheStorage();
        checkStorage();
        checkDownloadDirectory();
        checkEqualizerBands();
        checkMusicLibrary();
        checkLanPlayback();

        boolean searching = !searchQuery.isEmpty();
        boolean index = isIndexShowing();
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference pref = screen.getPreference(i);
            if (!(pref instanceof ClickablePreferenceCategory)) continue;
            PreferenceCategory category = (PreferenceCategory) pref;
            String key = category.getKey();

            category.setSummary(index ? categorySummaries.get(key) : null);
            category.setIcon(index ? categoryIcons.get(key) : null);

            if (searching) {
                category.setLayoutResource(R.layout.preference_settings_header);
            } else if (index) {
                category.setLayoutResource(R.layout.preference_settings_category);
                for (int j = 0; j < category.getPreferenceCount(); j++) {
                    category.getPreference(j).setVisible(false);
                }
            } else if (key.equals(openCategoryKey)) {
                category.setLayoutResource(R.layout.preference_settings_hidden);
            } else {
                category.setVisible(false);
            }
        }

        if (searching) {
            filterPreferences(screen);
        }

        closeCategoryCallback.setEnabled(openCategoryKey != null);
        if (getParentFragment() instanceof SettingsFragment) {
            Preference open = openCategoryKey != null ? findPreference(openCategoryKey) : null;
            ((SettingsFragment) getParentFragment()).setPageTitle(open != null
                    ? open.getTitle()
                    : getString(R.string.menu_settings_button));
        }

        if (getListView() != null && getListView().getAdapter() != null) {
            getListView().getAdapter().notifyDataSetChanged();
        }
    }

    private void resetVisibility(PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            pref.setVisible(true);
            if (pref instanceof PreferenceGroup) {
                resetVisibility((PreferenceGroup) pref);
            }
        }
    }

    private boolean filterPreferences(PreferenceGroup group) {
        return filterPreferences(group, false);
    }

    private boolean filterPreferences(PreferenceGroup group, boolean forceVisible) {
        boolean hasVisibleChild = false;
        boolean isRoot = group == getPreferenceScreen();
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            boolean matches = forceVisible;

            if (!matches) {
                String title = pref.getTitle() != null ? UIUtil.normalizeForSearch(pref.getTitle().toString()) : "";
                String summary = pref.getSummary() != null ? UIUtil.normalizeForSearch(pref.getSummary().toString()) : "";
                if (title.contains(searchQuery) || summary.contains(searchQuery)) {
                    matches = true;
                }
            }

            if (pref instanceof PreferenceGroup) {
                boolean wasVisible = pref.isVisible();
                boolean childMatches = filterPreferences((PreferenceGroup) pref, matches && wasVisible);
                boolean groupVisible = (matches || childMatches) && wasVisible;
                pref.setVisible(groupVisible);
                if (groupVisible) {
                    hasVisibleChild = true;
                }
            } else {
                boolean wasVisible = pref.isVisible();
                boolean shouldBeVisible = matches && wasVisible;
                pref.setVisible(shouldBeVisible);
                if (shouldBeVisible) {
                    hasVisibleChild = true;
                }
            }
        }
        return hasVisibleChild;
    }

    private void checkSystemEqualizer() {
        Preference equalizer = findPreference("system_equalizer");
        Preference builtinEqualizer = findPreference("builtin_equalizer");

        int selectedEqValue = Preferences.getSelectedEqualizer();

        if (builtinEqualizer != null) {
            builtinEqualizer.setVisible(selectedEqValue == 1);
        }

        Preference parametricEqImport = findPreference("parametric_eq_import");
        if (parametricEqImport != null) {
            parametricEqImport.setVisible(selectedEqValue == 3);
        }

        if (equalizer != null) {
            Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);

            if ((intent.resolveActivity(requireActivity().getPackageManager()) != null)) {
                equalizer.setOnPreferenceClickListener(preference -> {
                    equalizerResultLauncher.launch(intent);
                    return true;
                });
                equalizer.setVisible(selectedEqValue == 2);
            } else {
                equalizer.setVisible(false);
            }
        }
    }

    private void checkCacheStorage() {
        Preference storage = findPreference("streaming_cache_storage");

        if (storage == null) return;

        try {
            if (requireContext().getExternalFilesDirs(null)[1] == null) {
                storage.setVisible(false);
            } else {
                storage.setSummary(Preferences.getStreamingCacheStoragePreference() == 0 ? R.string.download_storage_internal_dialog_negative_button : R.string.download_storage_external_dialog_positive_button);
            }
        } catch (Exception exception) {
            storage.setVisible(false);
        }
    }

    private void checkStorage() {
        Preference storage = findPreference("download_storage");

        if (storage == null) return;

        try {
            if (requireContext().getExternalFilesDirs(null)[1] == null) {
                storage.setVisible(false);
            } else {
                int pref = Preferences.getDownloadStoragePreference();
                if (pref == 0) {
                    storage.setSummary(R.string.download_storage_internal_dialog_negative_button);
                } else if (pref == 1) {
                    storage.setSummary(R.string.download_storage_external_dialog_positive_button);
                } else {
                    storage.setSummary(R.string.download_storage_directory_dialog_neutral_button);
                }
            }
        } catch (Exception exception) {
            storage.setVisible(false);
        }
    }

    private void checkDownloadDirectory() {
        Preference storage = findPreference("download_storage");
        Preference directory = findPreference("set_download_directory");

        if (directory == null) return;

        String current = Preferences.getDownloadDirectoryUri();
        if (current != null) {
            if (storage != null) storage.setVisible(false);
            directory.setVisible(true);
            directory.setIcon(R.drawable.ic_close);
            directory.setTitle(R.string.settings_clear_download_folder);
            directory.setSummary(current);
        } else {
            if (storage != null) storage.setVisible(true);
            if (Preferences.getDownloadStoragePreference() == 2) {
                directory.setVisible(true);
                directory.setIcon(R.drawable.ic_folder);
                directory.setTitle(R.string.settings_set_download_folder);
                directory.setSummary(R.string.settings_choose_download_folder);
            } else {
                directory.setVisible(false);
            }
        }
    }

    private void setNetorkPingTimeoutBase() {
        EditTextPreference networkPingTimeoutBase = findPreference("network_ping_timeout_base");

        if (networkPingTimeoutBase != null) {
            networkPingTimeoutBase.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            networkPingTimeoutBase.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setFilters(new InputFilter[]{ (source, start, end, dest, dstart, dend) -> {
                    for (int i = start; i < end; i++) {
                        if (!Character.isDigit(source.charAt(i))) {
                            return "";
                        }
                    }
                    return null;
                }});
            });

            networkPingTimeoutBase.setOnPreferenceChangeListener((preference, newValue) -> {
                String input = (String) newValue;
                return input != null && !input.isEmpty();
            });
        }
    }

    private void setStreamingCacheSize() {
        ListPreference streamingCachePreference = findPreference("streaming_cache_size");

        if (streamingCachePreference != null) {
            streamingCachePreference.setSummaryProvider(new Preference.SummaryProvider<ListPreference>() {
                @Nullable
                @Override
                public CharSequence provideSummary(@NonNull ListPreference preference) {
                    CharSequence entry = preference.getEntry();

                    if (entry == null) return null;

                    long currentSizeMb = DownloadUtil.getStreamingCacheSize(requireActivity()) / (1024 * 1024);

                    return getString(R.string.settings_summary_streaming_cache_size, entry, String.valueOf(currentSizeMb));
                }
            });
        }
    }

    private void setAppLanguage() {
        ListPreference localePref = (ListPreference) findPreference("language");

        Map<String, String> locales = UIUtil.getLangPreferenceDropdownEntries(requireContext());

        CharSequence[] entries = locales.keySet().toArray(new CharSequence[locales.size()]);
        CharSequence[] entryValues = locales.values().toArray(new CharSequence[locales.size()]);

        localePref.setEntries(entries);
        localePref.setEntryValues(entryValues);

        String value = localePref.getValue();
        if ("default".equals(value)) {
            localePref.setSummary(requireContext().getString(R.string.settings_system_language));
        } else {
            localePref.setSummary(Locale.forLanguageTag(value).getDisplayName());
        }

        localePref.setOnPreferenceChangeListener((preference, newValue) -> {
            if ("default".equals(newValue)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
                preference.setSummary(requireContext().getString(R.string.settings_system_language));
            } else {
                LocaleListCompat appLocale = LocaleListCompat.forLanguageTags((String) newValue);
                AppCompatDelegate.setApplicationLocales(appLocale);
                preference.setSummary(Locale.forLanguageTag((String) newValue).getDisplayName());
            }
            return true;
        });
    }

    private void setMusicLibrary() {
        ListPreference libraryPref = (ListPreference) findPreference("active_music_folder_id");
        if (libraryPref == null) return;

        // Opening a ListPreference whose entries are still null throws, and the server folders are
        // asynchronous and may never arrive, so seed the one entry that needs no server.
        if (libraryPref.getEntries() == null) {
            libraryPref.setEntries(new CharSequence[]{getString(R.string.settings_music_library_all)});
            libraryPref.setEntryValues(new CharSequence[]{Preferences.MUSIC_FOLDER_ALL});
        }

        // The widget's own key is shared by every server, so left alone it would show whichever
        // library was picked last, anywhere. Load this server's choice in so the screen cannot
        // claim a filter that is not being applied.
        String activeMusicFolderId = Preferences.getActiveMusicFolderId();
        libraryPref.setValue(activeMusicFolderId != null ? activeMusicFolderId : Preferences.MUSIC_FOLDER_ALL);

        setMusicLibrarySummary(libraryPref);

        libraryPref.setOnPreferenceChangeListener((preference, newValue) -> {
            libraryPref.setValue((String) newValue);
            mainViewModel.setActiveMusicFolderId((String) newValue);
            setMusicLibrarySummary(libraryPref);
            return true;
        });

        // onResume runs this on every return, and the call below is what fires the request, so ask
        // once per view. A failed load is then not retried until the screen is left and reopened,
        // which beats enqueueing another request and observer on every resume while a server is down.
        if (musicFoldersObserved) return;
        musicFoldersObserved = true;

        settingViewModel.getMusicFolders(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), musicFolderObserver);
    }

    private final Observer<List<MusicFolder>> musicFolderObserver = new Observer<List<MusicFolder>>() {
        @Override
        public void onChanged(List<MusicFolder> folders) {
            ListPreference libraryPref = (ListPreference) findPreference("active_music_folder_id");
            if (libraryPref == null || folders == null) return;

            List<CharSequence> entries = new ArrayList<>();
            List<CharSequence> entryValues = new ArrayList<>();

            entries.add(getString(R.string.settings_music_library_all));
            entryValues.add(Preferences.MUSIC_FOLDER_ALL);

            List<String> ids = new ArrayList<>();
            for (MusicFolder folder : folders) {
                if (folder.getId() == null) continue;
                ids.add(folder.getId());
                entries.add(folder.getName() != null ? folder.getName() : folder.getId());
                entryValues.add(folder.getId());
            }

            libraryPref.setEntries(entries.toArray(new CharSequence[0]));
            libraryPref.setEntryValues(entryValues.toArray(new CharSequence[0]));

            // A library removed, or one this account can no longer see, leaves a stale id that
            // would filter everything away. Discarding the choice is permanent though, so it takes
            // positive evidence: other libraries came back and this one was not among them. An
            // empty response is not that, and a server offering none shows nothing either way.
            String storedMusicFolderId = Preferences.getActiveMusicFolderId();
            if (storedMusicFolderId != null && !ids.isEmpty() && !ids.contains(storedMusicFolderId)) {
                libraryPref.setValue(Preferences.MUSIC_FOLDER_ALL);
                mainViewModel.setActiveMusicFolderId(null);
            }

            musicFolderCount = ids.size();
            applyPageState();

            setMusicLibrarySummary(libraryPref);
        }
    };

    private void checkLanPlayback() {
        Preference lanPref = findPreference("lan_playback");
        if (lanPref != null) lanPref.setVisible(Preferences.isLanRemoteEnabled());
    }

    private void actionLanRemote() {
        SwitchPreference preference = findPreference(Preferences.LAN_REMOTE_ENABLED);
        if (preference == null) return;

        preference.setOnPreferenceChangeListener((pref, newValue) -> {
            if (!Boolean.TRUE.equals(newValue)) {
                // Turning it off ends whatever is running: a controlled device, an open pairing, the listener.
                com.eddyizm.tempus.lan.LanRemoteSession.disconnect();
                com.eddyizm.tempus.lan.LanReceiverService.Companion.closePairing();
                requireContext().stopService(new Intent(requireContext(), com.eddyizm.tempus.lan.LanReceiverService.class));
            }
            // Posted so the new value is saved before checkLanPlayback reads it, and run through
            // applyPageState so an active search filter still applies to the row it reveals.
            getListView().post(() -> {
                if (getView() != null) applyPageState();
            });
            return true;
        });
    }

    /**
     * Below two libraries there is nothing to choose between, so the setting is noise.
     *
     * Call applyPageState rather than this, including from the folder response. That method
     * resets every category child to visible, runs these checks, and only then re-hides the
     * children of the categories whose page is not showing. Calling this alone performs only the
     * middle step, so a row it reveals would show on the category index.
     */
    private void checkMusicLibrary() {
        Preference libraryPref = findPreference("active_music_folder_id");
        if (libraryPref == null) return;

        // Visible while a filter is in force, since hiding it would remove the only way to clear
        // one. Also visible on a null count, which means the server has not answered yet or at all:
        // showing a control with nothing to choose between beats hiding one that was needed.
        libraryPref.setVisible(musicFolderCount == null
                || musicFolderCount > 1
                || Preferences.getActiveMusicFolderId() != null);
    }

    private void setMusicLibrarySummary(ListPreference libraryPref) {
        CharSequence entry = libraryPref.getEntry();
        if (entry != null) {
            libraryPref.setSummary(entry);
            return;
        }

        // getEntry() is null while the value is not among the entries: the folder list has not
        // arrived, or it arrived without enough evidence to clear a stale id. Saying "all
        // libraries" would contradict what is still being sent, so name the id, poorly as it reads.
        String activeMusicFolderId = Preferences.getActiveMusicFolderId();
        libraryPref.setSummary(activeMusicFolderId != null
                ? activeMusicFolderId
                : getString(R.string.settings_music_library_all));
    }

    private void setVersion() {
        findPreference("version").setSummary(BuildConfig.VERSION_NAME);
    }

    private void actionPermission() {
        findPreference("permissions").setOnPreferenceClickListener( preference -> {
            Intent tempus = new Intent(requireActivity(), LoginActivity.class);
            tempus.putExtra("HIDE_TAB_LAYOUT", true);
            tempus.putExtra("HIDE_TOPAPPBAR_LAYOUT", false);
            tempus.putExtra("SELECT_FRAGMENT", 1);
            startActivity(tempus);
            return true;
        });
    }

    private void actionTheme() {
        Preference theme = findPreference("theme");
        int selected = java.util.Arrays.asList(getResources().getStringArray(R.array.theme_list_values)).indexOf(Preferences.getTheme());
        if (selected >= 0) theme.setSummary(getResources().getStringArray(R.array.theme_list_titles)[selected]);
        theme.setOnPreferenceClickListener( preference -> {
            Intent tempus = new Intent(requireActivity(), LoginActivity.class);
            tempus.putExtra("HIDE_TAB_LAYOUT", true);
            tempus.putExtra("HIDE_TOPAPPBAR_LAYOUT", false);
            tempus.putExtra("SELECT_FRAGMENT", 2);
            startActivity(tempus);
            return true;
        });
    }
    private void actionLogout() {
        findPreference("logout").setOnPreferenceClickListener(preference -> {
            activity.quit();
            return true;
        });
    }

    private void actionScan() {
        findPreference("scan_library").setOnPreferenceClickListener(preference -> {
            settingViewModel.launchScan(new ScanCallback() {
                @Override
                public void onError(Exception exception) {
                    findPreference("scan_library").setSummary(exception.getMessage());
                }

                @Override
                public void onSuccess(boolean isScanning, long count) {
                    findPreference("scan_library").setSummary(getString(R.string.settings_scan_result, count));
                    if (isScanning) getScanStatus();
                }
            });

            return true;
        });
    }

    private void actionSyncStarredTracks() {
        findPreference("sync_starred_tracks_for_offline_use").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                if ((Boolean) newValue) {
                    StarredSyncDialog dialog = new StarredSyncDialog(() -> {
                        ((SwitchPreference)preference).setChecked(false);
                    });
                    dialog.show(activity.getSupportFragmentManager(), null);
                }
            }
            return true;
        });
    }

    private void actionSyncStarredAlbums() {
        findPreference("sync_starred_albums_for_offline_use").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                if ((Boolean) newValue) {
                    StarredAlbumSyncDialog dialog = new StarredAlbumSyncDialog(() -> {
                        ((SwitchPreference)preference).setChecked(false);
                    });
                    dialog.show(activity.getSupportFragmentManager(), null);
                }
            }
            return true;
        });
    }

    private void actionSyncStarredArtists() {
        findPreference("sync_starred_artists_for_offline_use").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                if ((Boolean) newValue) {
                    StarredArtistSyncDialog dialog = new StarredArtistSyncDialog(() -> {
                        ((SwitchPreference)preference).setChecked(false);
                    });
                    dialog.show(activity.getSupportFragmentManager(), null);
                }
            }
            return true;
        });
    }

    private void actionChangeStreamingCacheStorage() {
        findPreference("streaming_cache_storage").setOnPreferenceClickListener(preference -> {
            StreamingCacheStorageDialog dialog = new StreamingCacheStorageDialog(new DialogClickCallback() {
                @Override
                public void onPositiveClick() {
                    findPreference("streaming_cache_storage").setSummary(R.string.streaming_cache_storage_external_dialog_positive_button);
                }

                @Override
                public void onNegativeClick() {
                    findPreference("streaming_cache_storage").setSummary(R.string.streaming_cache_storage_internal_dialog_negative_button);
                }
            });
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        });
    }

    private void actionChangeDownloadStorage() {
        findPreference("download_storage").setOnPreferenceClickListener(preference -> {
            DownloadStorageDialog dialog = new DownloadStorageDialog(new DialogClickCallback() {
                @Override
                public void onPositiveClick() {
                    findPreference("download_storage").setSummary(R.string.download_storage_external_dialog_positive_button);
                    checkDownloadDirectory();
                }

                @Override
                public void onNegativeClick() {
                    findPreference("download_storage").setSummary(R.string.download_storage_internal_dialog_negative_button);
                    checkDownloadDirectory();
                }

                @Override
                public void onNeutralClick() {
                    findPreference("download_storage").setSummary(R.string.download_storage_directory_dialog_neutral_button);
                    checkDownloadDirectory();
                }
            });
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        });
    }

    private void actionSetSongPreloadBuffer() {
        ListPreference pref = findPreference("song_preload_buffer");

        Context ctx = getContext();
        Resources res = getResources();

        String[] titles = res.getStringArray(R.array.song_preload_buffer_titles);
        String[] values = res.getStringArray(R.array.song_preload_buffer_values);

        final int DEFAULT_IDX = 2; // 60 seconds
        final String DEFAULT_VAL = values[DEFAULT_IDX];

        String selectedValue = PreferenceManager
                .getDefaultSharedPreferences(ctx)
                .getString("song_preload_buffer", DEFAULT_VAL);

        int valueIdx = Arrays.asList(values).indexOf(selectedValue);
        int titleIdx = (valueIdx == -1) ? DEFAULT_IDX : valueIdx;

        pref.setSummary(
                ctx.getString(R.string.settings_song_preload_buffer_summary,
                        titles[titleIdx])
        );
    }

    private void actionSetDownloadDirectory() {
        Preference pref = findPreference("set_download_directory");
        if (pref != null) {
            pref.setOnPreferenceClickListener(preference -> {
                String current = Preferences.getDownloadDirectoryUri();

                if (current != null) {
                    Preferences.setDownloadDirectoryUri(null);
                    Preferences.setDownloadStoragePreference(0);
                    ExternalAudioReader.refreshCache();
                    Toast.makeText(requireContext(), R.string.settings_download_folder_cleared, Toast.LENGTH_SHORT).show();
                    checkStorage();
                    checkDownloadDirectory();
                } else {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    directoryPickerLauncher.launch(intent);
                }
                return true;
            });
        }
    }

    private void actionDeleteDownloadStorage() {
        findPreference("delete_download_storage").setOnPreferenceClickListener(preference -> {
            DeleteDownloadStorageDialog dialog = new DeleteDownloadStorageDialog();
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        });
    }

    private void actionMiniPlayerHeart() {
        SwitchPreference preference = findPreference("mini_shuffle_button_visibility");
        if (preference == null) {
            return;
        }

        preference.setChecked(Preferences.showShuffleInsteadOfHeart());
        preference.setOnPreferenceChangeListener((pref, newValue) -> {
            if (newValue instanceof Boolean) {
                Preferences.setShuffleInsteadOfHeart((Boolean) newValue);
            }
            return true;
        });
    }

    private void actionAutoDownloadLyrics() {
        SwitchPreference preference = findPreference("auto_download_lyrics");
        if (preference == null) {
            return;
        }

        preference.setChecked(Preferences.isAutoDownloadLyricsEnabled());
        preference.setOnPreferenceChangeListener((pref, newValue) -> {
            if (newValue instanceof Boolean) {
                Preferences.setAutoDownloadLyricsEnabled((Boolean) newValue);
            }
            return true;
        });
    }

    private void getScanStatus() {
        settingViewModel.getScanStatus(new ScanCallback() {
            @Override
            public void onError(Exception exception) {
                findPreference("scan_library").setSummary(exception.getMessage());
            }

            @Override
            public void onSuccess(boolean isScanning, long count) {
                findPreference("scan_library").setSummary(getString(R.string.settings_scan_result, count));
                if (isScanning) getScanStatus();
            }
        });
    }

    private void actionKeepScreenOn() {
        findPreference("always_on_display").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                if ((Boolean) newValue) {
                    activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
            return true;
        });
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mediaServiceBinder = (MediaService.LocalBinder) service;
            isServiceBound = true;
            if (pendingEqualizerReload) {
                mediaServiceBinder.reloadEqualizer();
                pendingEqualizerReload = false;
            }
            checkEqualizerBands();
            applyPageState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mediaServiceBinder = null;
            isServiceBound = false;
        }
    };

    private void bindMediaService() {
        Intent intent = new Intent(requireActivity(), MediaService.class);
        intent.setAction(MediaService.ACTION_BIND_EQUALIZER);
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        isServiceBound = true;
    }

    private void checkEqualizerBands() {
        if (mediaServiceBinder != null) {
            EqualizerManager eqManager = mediaServiceBinder.getEqualizerManager();
            short numBands = eqManager.getNumberOfBands();
            Preference appEqualizer = findPreference("app_equalizer");
            if (appEqualizer != null) {
                appEqualizer.setVisible(numBands > 0);
            }
        }
    }

    private void actionReplayGainPreamp() {
        SeekBarPreference preampPref = findPreference("replay_gain_preamp");
        if (preampPref == null) return;

        // Seed the widget with the currently persisted value so it shows the
        // correct position when the settings screen opens.
        preampPref.setValue((int) Preferences.getLoudnessPreamp());

        preampPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (!(newValue instanceof Integer)) return true;

            int dB = (Integer) newValue;
            Preferences.setLoudnessPreamp((float) dB);

            // Immediately re-apply gain to whatever is playing so the user can
            // hear the effect without restarting playback.
            if (isServiceBound && mediaServiceBinder != null) {
                com.eddyizm.tempus.util.ReplayGainUtil.reapplyCurrentTrackGain(
                        mediaServiceBinder.getPlayer());
            }

            return true;
        });
    }

    private void actionEqualizerSelector() {
        final ListPreference selectedEqualizer = findPreference("selected_equalizer");
        if (selectedEqualizer != null) {
            selectedEqualizer.setSummary(selectedEqualizer.getEntry());
            selectedEqualizer.setOnPreferenceChangeListener(
                    (preference, newValue) -> {
                        String newValStr = (String) newValue;
                        int idx = selectedEqualizer.findIndexOfValue(newValStr);
                        CharSequence newEntry = "";
                        if (idx >= 0 && selectedEqualizer.getEntries() != null) {
                            newEntry = selectedEqualizer.getEntries()[idx];
                        }
                        selectedEqualizer.setSummary(newEntry);
                        Preferences.setSelectedEqualizer(newValStr);

                        checkSystemEqualizer();

                        if (mediaServiceBinder != null) {
                            mediaServiceBinder.reloadEqualizer();
                        } else {
                            pendingEqualizerReload = true;
                        }
                        return true;
                    });
        }
    }

    private void actionParametricEqImport() {
        Preference importPref = findPreference("parametric_eq_import");
        if (importPref == null) return;

        String text = Preferences.getParametricEqProfile();
        ParametricProfile profile = text != null ? ParametricProfile.parse(text) : null;
        if (profile != null) {
            double preampDb = profile.getPreampDb();
            importPref.setSummary(getString(preampDb > 0
                            ? R.string.settings_parametric_eq_summary_louder
                            : R.string.settings_parametric_eq_summary,
                    Preferences.getParametricEqName(),
                    String.format(Locale.getDefault(), "%.1f", Math.abs(preampDb))));
        }

        importPref.setOnPreferenceClickListener(preference -> {
            parametricEqPickerLauncher.launch(new String[]{"text/*", "application/octet-stream"});
            return true;
        });
    }

    private void importParametricEq(Uri uri) {
        String text = readParametricEqFile(uri);
        ParametricProfile profile = text != null ? ParametricProfile.parse(text) : null;
        if (profile == null) {
            int message = text != null && ParametricProfile.isPerEarExport(text)
                    ? R.string.settings_parametric_eq_per_ear
                    : R.string.settings_parametric_eq_invalid;
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            return;
        }

        String name = displayName(uri);
        Preferences.setParametricEqProfile(profile.toText(), name);
        if (mediaServiceBinder != null) {
            mediaServiceBinder.reloadEqualizer();
        } else {
            pendingEqualizerReload = true;
        }
        actionParametricEqImport();
        Toast.makeText(requireContext(), getString(R.string.settings_parametric_eq_imported, name), Toast.LENGTH_SHORT).show();
    }

    private String readParametricEqFile(Uri uri) {
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1 && bytes.size() <= PARAMETRIC_EQ_MAX_BYTES) {
                bytes.write(buffer, 0, read);
            }
            return bytes.size() <= PARAMETRIC_EQ_MAX_BYTES
                    ? new String(bytes.toByteArray(), StandardCharsets.UTF_8)
                    : null;
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    private String displayName(Uri uri) {
        String name = DocumentFile.fromSingleUri(requireContext(), uri).getName();
        if (name == null) name = uri.getLastPathSegment();
        if (name == null) name = "AutoEq";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void actionBuiltinEqualizer() {
        Preference appEqualizer = findPreference("builtin_equalizer");
        if (appEqualizer != null) {
            appEqualizer.setOnPreferenceClickListener(preference -> {
                NavController navController = NavHostFragment.findNavController(this);
                NavOptions navOptions = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(R.id.equalizerFragment, true)
                        .build();
                activity.setBottomNavigationBarVisibility(true);
                activity.setBottomSheetVisibility(true);
                navController.navigate(R.id.equalizerFragment, null, navOptions);
                return true;
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isServiceBound) {
            requireActivity().unbindService(serviceConnection);
            isServiceBound = false;
            mediaServiceBinder = null;
        }
    }
}
