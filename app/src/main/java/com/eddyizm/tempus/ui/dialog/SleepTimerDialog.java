package com.eddyizm.tempus.ui.dialog;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.util.SleepTimerManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SleepTimerDialog extends BottomSheetDialogFragment {

    public interface SleepTimerListener {
        void onTimerSet(int minutes);
        void onTimerCancelled();

        /** Called when the user picks "End of track" (stop after current song). */
        default void onEndOfTrackSet() {}
    }

    // Indices into R.array.sleep_timer_duration_labels, whose order is 5,10,15,20,30,45,60,END_OF_TRACK,CUSTOM.
    private static final int LABEL_15 = 2;
    private static final int LABEL_30 = 4;
    private static final int LABEL_45 = 5;
    private static final int LABEL_CUSTOM = 8;

    private SleepTimerListener listener;

    public void setSleepTimerListener(SleepTimerListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_sleep_timer, container, false);
        String[] labels = getResources().getStringArray(R.array.sleep_timer_duration_labels);

        bindMinutes(view, R.id.sleep_timer_15_row, labels[LABEL_15], 15);
        bindMinutes(view, R.id.sleep_timer_30_row, labels[LABEL_30], 30);
        bindMinutes(view, R.id.sleep_timer_45_row, labels[LABEL_45], 45);
        bindMinutes(view, R.id.sleep_timer_60_row, null, 60);

        view.findViewById(R.id.sleep_timer_end_of_track_row).setOnClickListener(v -> {
            if (listener != null) listener.onEndOfTrackSet();
            dismiss();
        });

        TextView custom = view.findViewById(R.id.sleep_timer_custom_row);
        custom.setText(labels[LABEL_CUSTOM]);
        custom.setOnClickListener(v -> {
            showCustomInputDialog();
            dismiss();
        });

        boolean remote = com.eddyizm.tempus.lan.LanRemoteSession.isActive();
        com.eddyizm.tempus.lan.LanRemoteState remoteState = com.eddyizm.tempus.lan.LanRemoteSession.current();
        boolean timerActive = remote ? remoteState.getTimerActive() : SleepTimerManager.getInstance().isActive();

        if (timerActive) {
            boolean isEndOfTrack = remote ? remoteState.getTimerEndOfTrack() : SleepTimerManager.getInstance().isEndOfTrack();
            String statusMessage;
            if (isEndOfTrack) {
                statusMessage = getString(R.string.sleep_timer_dialog_end_of_track_active);
            } else {
                String remaining = remote ? remoteState.getTimerRemaining() : SleepTimerManager.getInstance().getRemainingFormatted();
                statusMessage = getString(R.string.sleep_timer_dialog_active_message, remaining);
            }
            ((TextView) view.findViewById(R.id.sleep_timer_status_text_view)).setText(statusMessage);
            view.findViewById(R.id.sleep_timer_cancel_group).setVisibility(View.VISIBLE);
            view.findViewById(R.id.sleep_timer_cancel_row).setOnClickListener(v -> {
                if (listener != null) listener.onTimerCancelled();
                dismiss();
            });
        }

        return view;
    }

    private void bindMinutes(View root, int rowId, @Nullable String label, int minutes) {
        TextView row = root.findViewById(rowId);
        if (label != null) row.setText(label);
        row.setOnClickListener(v -> {
            if (listener != null) listener.onTimerSet(minutes);
            dismiss();
        });
    }

    private void showCustomInputDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(getString(R.string.sleep_timer_custom_hint));
        int dp16 = Math.round(16 * getResources().getDisplayMetrics().density);
        input.setPadding(dp16, dp16, dp16, dp16);

        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.sleep_timer_dialog_title)
                .setView(input)
                .setPositiveButton(R.string.sleep_timer_custom_set, (d, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        try {
                            int minutes = Integer.parseInt(text);
                            if (minutes > 0 && listener != null) {
                                listener.onTimerSet(minutes);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                })
                .setNegativeButton(R.string.sleep_timer_dialog_close, null)
                .show();
    }
}
