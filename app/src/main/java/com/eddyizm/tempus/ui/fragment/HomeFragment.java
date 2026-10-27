package com.eddyizm.tempus.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;
import androidx.viewpager2.widget.ViewPager2;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentHomeBinding;
import com.eddyizm.tempus.navigation.NavigationController;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.fragment.pager.HomePager;
import com.eddyizm.tempus.util.Preferences;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.Objects;

@UnstableApi
public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";

    private FragmentHomeBinding bind;
    private MainActivity activity;
    private NavigationController navigationController;

    private MaterialToolbar materialToolbar;
    private AppBarLayout appBarLayout;
    private ChipGroup tabChipGroup;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        navigationController = activity.getNavigationController();
        bind = FragmentHomeBinding.inflate(inflater, container, false);
        return bind.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initAppBar();
        initHomePager();
    }

    @Override
    public void onStart() {
        super.onStart();

        activity.toggleBottomNavigationBarVisibilityOnOrientationChange();
        activity.setBottomSheetVisibility(true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initAppBar() {
        appBarLayout = bind.getRoot().findViewById(R.id.toolbar_fragment);
        materialToolbar = bind.getRoot().findViewById(R.id.toolbar);

        activity.setSupportActionBar(materialToolbar);

        Objects.requireNonNull(materialToolbar.getOverflowIcon()).setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));

        tabChipGroup = (ChipGroup) getLayoutInflater().inflate(R.layout.view_home_tab_chips, appBarLayout, false);
        appBarLayout.addView(tabChipGroup);
    }

    private void initHomePager() {
        HomePager pager = new HomePager(this);

        pager.addFragment(new HomeTabMusicFragment(), getString(R.string.home_section_music), R.drawable.ic_home);

        if (Preferences.isPodcastSectionVisible())
            pager.addFragment(new HomeTabPodcastFragment(), getString(R.string.home_section_podcast), R.drawable.ic_graphic_eq);

        if (Preferences.isRadioSectionVisible())
            pager.addFragment(new HomeTabRadioFragment(), getString(R.string.home_section_radio), R.drawable.ic_play_for_work);

        bind.homeViewPager.setAdapter(pager);
        bind.homeViewPager.setOffscreenPageLimit(3);
        bind.homeViewPager.setUserInputEnabled(false);

        for (int position = 0; position < pager.getItemCount(); position++) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_home_tab_chip, tabChipGroup, false);
            chip.setId(View.generateViewId());
            chip.setText(pager.getPageTitle(position));
            int page = position;
            chip.setOnClickListener(v -> bind.homeViewPager.setCurrentItem(page, false));
            tabChipGroup.addView(chip);
        }
        tabChipGroup.check(tabChipGroup.getChildAt(bind.homeViewPager.getCurrentItem()).getId());

        tabChipGroup.setVisibility(Preferences.isPodcastSectionVisible() || Preferences.isRadioSectionVisible() ? View.VISIBLE : View.GONE);

        bind.homeViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tabChipGroup.check(tabChipGroup.getChildAt(position).getId());
                refreshToolbarLibraryScope();
            }
        });
    }

    /**
     * The music library line in the toolbar belongs to the Music tab. Podcast and Radio share the
     * toolbar and are not filtered by library, so the line would name something with nothing to do
     * with what is on screen. Music is always the first page; the other two are optional.
     *
     * The toolbar reads this on every update instead of being pushed the page once, so a page the
     * pager put back on its own cannot leave the line naming the wrong tab.
     */
    public boolean isLibraryScopedTab() {
        return bind != null && bind.homeViewPager.getCurrentItem() == 0;
    }

    private void refreshToolbarLibraryScope() {
        ToolbarFragment toolbarFragment = (ToolbarFragment) getChildFragmentManager().findFragmentById(R.id.toolbar_fragment);
        if (toolbarFragment != null) toolbarFragment.refreshMusicLibraryIndicator();
    }
}
