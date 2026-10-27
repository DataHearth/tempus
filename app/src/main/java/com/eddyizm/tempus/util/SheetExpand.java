package com.eddyizm.tempus.util;

import android.app.Dialog;
import android.content.res.Configuration;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public final class SheetExpand {
    private SheetExpand() {
    }

    /** A phone in landscape is too short for a collapsed sheet's peek to reach its first action. */
    public static void inLandscape(Dialog dialog) {
        if (!(dialog instanceof BottomSheetDialog)) return;
        if (dialog.getContext().getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) return;
        BottomSheetBehavior<?> behavior = ((BottomSheetDialog) dialog).getBehavior();
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }
}
