package com.example.cellwatch;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.fragment.app.Fragment;

public class SettingsEditFragment extends Fragment {
        String[] langArray;
        String[] shareArray;

        Spinner langSpinner;
        Spinner shareSpinner;

        public SettingsEditFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

            View rootView = inflater.inflate(R.layout.fragment_settings_edit, container, false);

            //Create arrayadapter for lang array
            ArrayAdapter<String> langadapter;
            //get language array and spinner
            langSpinner = (Spinner)rootView.findViewById(R.id.lang_spinner);
            langArray = getResources().getStringArray(R.array.language_options_array);
            // assign an array to the adapter
            langadapter = new ArrayAdapter<String>(this.getActivity(), android.R.layout.simple_list_item_1, langArray);
            //set the spinners adapter to the previously created one.
            langSpinner.setAdapter(langadapter);

            //Create arrayadapter for share array
            ArrayAdapter<String> shareadapter;
            //get share array and spinner
            shareSpinner = (Spinner)rootView.findViewById(R.id.data_share_spinner);
            shareArray = getResources().getStringArray(R.array.data_sharing_options_array);
            // assign an array to the adapter
            shareadapter = new ArrayAdapter<String>(this.getActivity(), android.R.layout.simple_list_item_1, shareArray);
            //set the spinners adapter to the previously created one.
            shareSpinner.setAdapter(shareadapter);

            return rootView;
        }

}


