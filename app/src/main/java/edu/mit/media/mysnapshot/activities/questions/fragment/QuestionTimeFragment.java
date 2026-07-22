package edu.mit.media.mysnapshot.activities.questions.fragment;


import android.view.ViewGroup;

import java.time.LocalTime;

import edu.mit.media.mysnapshot.R;
import edu.mit.media.mysnapshot.view.TimePickerView;

public abstract class QuestionTimeFragment extends QuestionFragment<String> {

    TimePickerView timePicker;

    LocalTime defaultTime;


    public QuestionTimeFragment() {
    }


    @Override
    protected void initViews(ViewGroup root) {

        timePicker = (TimePickerView) root.findViewById(R.id.timePicker);
        timePicker.setListener(new TimePickerView.TimePickerListener() {
            @Override
            public void onTimePicked(LocalTime time) {
                if (listener != null) {
                    listener.onSelected(getValue());
                }
            }
        });
        timePicker.setTime(QuestionNotificationFragment.parseDateString(getValue()));
        timePicker.setDefaultTime(defaultTime);


    }

    public void setTime(LocalTime time) {
        timePicker.setTime(time);
    }


    @Override
    public String getValue() {
        LocalTime time = timePicker.getTime();
        return QuestionNotificationFragment.encode(time);
    }

    @Override
    public void setValue(String dateString) {
        super.setValue(dateString);
    }

    public void setDefaultTime(LocalTime defaultTime) {
        this.defaultTime = defaultTime;
    }



}
