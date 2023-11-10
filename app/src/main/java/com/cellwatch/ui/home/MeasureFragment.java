package com.cellwatch.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

public class MeasureFragment extends Fragment {
    private int progress = 0;
    Button buttonIncrement;
    Button buttonDecrement;
    ProgressBar progressBar;
    TextView textView;

    public MeasureFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_measure, container, false);


            buttonDecrement = (Button) rootView.findViewById(R.id.button_decr);
            buttonIncrement = (Button) rootView.findViewById(R.id.button_incr);
            progressBar = (ProgressBar) rootView.findViewById(R.id.progress_bar);
            textView = (TextView) rootView.findViewById(R.id.text_view_progress);

            // when clicked on buttonIncrement progress is increased by 10%
            buttonIncrement.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // if progress is less than or equal
                    // to 90% then only it can be increased
                    if (progress <= 90) {
                        progress += 10;
                        updateProgressBar();
                    }
                }
            });

            // when clicked on buttonIncrement progress is decreased by 10%
            buttonDecrement.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // If progress is greater than
                    // 10% then only it can be decreased
                    if (progress >= 10) {
                        progress -= 10;
                        updateProgressBar();
                    }
                }
            });
        return rootView;
    }

    // updateProgressBar() method sets
    // the progress of ProgressBar in text
    private void updateProgressBar() {
        progressBar.setProgress(progress);
        textView.setText(String.valueOf(progress));
    }



}
