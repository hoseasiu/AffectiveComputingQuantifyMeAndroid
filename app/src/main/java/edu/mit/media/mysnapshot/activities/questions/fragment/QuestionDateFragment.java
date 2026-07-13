package edu.mit.media.mysnapshot.activities.questions.fragment;


import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

import edu.mit.media.mysnapshot.R;

public class QuestionDateFragment extends QuestionFragment<String> {

    View button;
    TextView dateTextView;
    Calendar date;

    public QuestionDateFragment() {
    }


    @Override
    protected void initViews(ViewGroup root) {

        button = root.findViewById(R.id.button);
        dateTextView = (TextView) root.findViewById(R.id.date);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePickerDialog();
            }
        });

        setDateText();

    }

    boolean shown = false;

    private static WeakReference<QuestionDateFragment> activeInstance;

    public void showDatePickerDialog() {
        if (shown) {
            return;
        }
        shown = true;
        activeInstance = new WeakReference<>(this);

        DatePickerFragment newFragment = DatePickerFragment.newInstance(date);
        newFragment.show(getActivity().getFragmentManager(), "datePicker");
    }

    public void setDate(Calendar date) {
        this.date = date;
        setDateText();
        if (listener != null) {
            listener.onSelected(getValue());
        }
    }


    public void setDateText() {
        if (dateTextView != null) {
            if (date == null) {
                dateTextView.setText("");
                dateTextView.setVisibility(View.GONE);
            } else {
                dateTextView.setText(new SimpleDateFormat("MMMM d\nyyyy").format(date.getTime()));
                dateTextView.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public String getValue() {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat("yyyy-MM-dd").format(date.getTime());
    }

    @Override
    public void setValue(String dateString) {
        Calendar cal = Calendar.getInstance();
        try {
            cal.setTime(new SimpleDateFormat("yyyy-MM-dd").parse(dateString));
            setDate(cal);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public static class DatePickerFragment extends DialogFragment
            implements DatePickerDialog.OnDateSetListener {

        private static final String ARG_YEAR = "year";
        private static final String ARG_MONTH = "month";
        private static final String ARG_DAY = "day";

        public static DatePickerFragment newInstance(Calendar date) {
            Calendar defaultCal = Calendar.getInstance();
            defaultCal.set(1970, 0, 1);
            final Calendar c = date == null ? defaultCal : date;

            Bundle args = new Bundle();
            args.putInt(ARG_YEAR, c.get(Calendar.YEAR));
            args.putInt(ARG_MONTH, c.get(Calendar.MONTH));
            args.putInt(ARG_DAY, c.get(Calendar.DAY_OF_MONTH));

            DatePickerFragment fragment = new DatePickerFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            int year = args.getInt(ARG_YEAR);
            int month = args.getInt(ARG_MONTH);
            int day = args.getInt(ARG_DAY);

            return new DatePickerDialog(getActivity(), this, year, month, day);
        }

        @Override
        public void onStart() {
            super.onStart();
            // The app-wide theme overrides buttonStyle with white text (for the app's own
            // buttons), which system AlertDialogs like this one also inherit, leaving the
            // CANCEL/OK buttons invisible against the picker's white background. Force a
            // readable color directly rather than relying on theme resolution.
            Dialog dialog = getDialog();
            if (dialog instanceof AlertDialog) {
                AlertDialog alertDialog = (AlertDialog) dialog;
                Button positive = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (positive != null) {
                    positive.setTextColor(Color.BLACK);
                }
                Button negative = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (negative != null) {
                    negative.setTextColor(Color.BLACK);
                }
            }
        }

        public void onDateSet(DatePicker view, int year, int month, int day) {
            QuestionDateFragment target = activeInstance == null ? null : activeInstance.get();
            if (target != null) {
                target.shown = false;
                target.setDate(new GregorianCalendar(year, month, day));
            }
        }

        @Override
        public void onStop() {
            super.onStop();
            QuestionDateFragment target = activeInstance == null ? null : activeInstance.get();
            if (target != null) {
                target.shown = false;
            }
        }

    }

    @Override
    public int getLayoutId() {
        return R.layout.fragment_question_date;
    }


}
