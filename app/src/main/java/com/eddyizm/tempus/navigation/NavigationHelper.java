package com.eddyizm.tempus.navigation;

import android.os.Build;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.eddyizm.tempus.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.jetbrains.annotations.Contract;

public class NavigationHelper {
    /* UI components */
    private BottomNavigationView bottomNavigationView;
    private FrameLayout bottomNavigationViewFrame;

    /* Navigation components */
    private NavHostFragment navHostFragment;
    private NavController navController;

    /* States that need to be remembered */
    // -- //

    /* Private constructor */
    public NavigationHelper(@NonNull BottomNavigationView bottomNavigationView,
                            @NonNull FrameLayout bottomNavigationViewFrame,
                            @NonNull NavHostFragment navHostFragment,
                            @NonNull NavController navController) {
        this.bottomNavigationView = bottomNavigationView;
        this.bottomNavigationViewFrame = bottomNavigationViewFrame;
        this.navHostFragment = navHostFragment;
        this.navController = navController;
    }

    public void syncWithBottomSheetBehavior(@NonNull BottomSheetBehavior<View> bottomSheetBehavior,
                                            @NonNull NavController navController) {
        NavDestination[] previous = {null};
        navController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> {
                    // React to the user clicking one of these on the bottom bar. Closing a
                    // sheet opened over the player also lands back on one, and must leave it open.
                    boolean fromDialog = previous[0] != null && "dialog".equals(previous[0].getNavigatorName());
                    previous[0] = destination;
                    boolean isTarget = isTargetDestination(destination);
                    int currentState = bottomSheetBehavior.getState();

                    if (isTarget && !fromDialog && currentState == BottomSheetBehavior.STATE_EXPANDED) {
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    }
                });

        NavigationUI.setupWithNavController(bottomNavigationView, navController);
    }

    @Contract(pure = true)
    private static boolean isTargetDestination(NavDestination destination) {
        int destId = destination.getId();
        return destId == R.id.homeFragment ||
                destId == R.id.libraryFragment ||
                destId == R.id.downloadFragment ||
                destId == R.id.albumCatalogueFragment ||
                destId == R.id.artistCatalogueFragment ||
                destId == R.id.genreCatalogueFragment ||
                destId == R.id.playlistCatalogueFragment;
    }

    /*
    Clean public methods
    Removes the need to invoke the activity on the fragment
     */

    public void setBottomNavigationBarVisibility(boolean visible) {
        int visibility = visible
                ? View.VISIBLE
                : View.GONE;
        bottomNavigationView.setVisibility(visibility);
        bottomNavigationViewFrame.setVisibility(visibility);
    }

    /*
    All of these are the "backward compatible" changes that don't break the assumption
    that everything was defined on the activity and is gobally available
     */

    @NonNull
    public NavController getNavController() {
        return navController;
    }

    @NonNull
    public BottomNavigationView getBottomNavigationView() {
        return bottomNavigationView;
    }

    @NonNull
    public FrameLayout getBottomNavigationViewFrame() {
        return bottomNavigationViewFrame;
    }

    /*
    Auxiliar functions, could be moved somewhere else
     */

    @OptIn(markerClass = UnstableApi.class)
    public void setSystemBarsVisibility(AppCompatActivity activity, boolean visibility) {
        WindowInsetsControllerCompat insetsController;
        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        insetsController = new WindowInsetsControllerCompat(window, decorView);

        if (visibility) {
            insetsController.show(WindowInsetsCompat.Type.navigationBars());
            insetsController.show(WindowInsetsCompat.Type.statusBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
        } else {
            insetsController.hide(WindowInsetsCompat.Type.navigationBars());
            insetsController.hide(WindowInsetsCompat.Type.statusBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
}
