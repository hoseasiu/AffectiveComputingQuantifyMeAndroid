package edu.mit.media.mysnapshot.activities.fragments;

import android.app.Activity;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import edu.mit.media.mysnapshot.R;
import edu.mit.media.mysnapshot.engine.ExperimentEngine;


public class FailedStageFragment extends NewStageFragment {

    private static final String REASON_ARG = "reason";

    public FailedStageFragment() {

    }

    @Override
    public int getLayoutId() {
        return R.layout.fragment_failed_stage;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        String reasonName = getArguments() != null ? getArguments().getString(REASON_ARG) : null;
        int reasonTextId;
        if (ExperimentEngine.RestartReason.TOO_MANY_MISSED_DAYS.name().equals(reasonName)) {
            reasonTextId = R.string.restart_reason_missed_days;
        } else if (ExperimentEngine.RestartReason.TARGET_ZONE_UNREACHABLE.name().equals(reasonName)) {
            reasonTextId = R.string.restart_reason_target_zone_unreachable;
        } else {
            reasonTextId = R.string.restart_reason_generic;
        }
        ((TextView) view.findViewById(R.id.reason_text)).setText(reasonTextId);

        return view;
    }

    protected static final String FRAGMENT_TAG = "qgDASVIJGWNRvsn04wmg0esidv";

    public static void showDialog(Activity activity, ExperimentEngine.RestartReason reason) {
        FailedStageFragment fragment = new FailedStageFragment();
        Bundle args = new Bundle();
        args.putString(REASON_ARG, reason != null ? reason.name() : null);
        fragment.setArguments(args);
        fragment.setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        fragment.setCancelable(false);

        fragment.show(activity.getFragmentManager().beginTransaction(), FRAGMENT_TAG);
    }

}
