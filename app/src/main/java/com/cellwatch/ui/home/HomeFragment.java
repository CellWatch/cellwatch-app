package com.example.cellwatch;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class HomeFragment extends Fragment {

    String[] langArray;

    Spinner langSpinner;

    Button moreInfoButton;
    public HomeFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.home_fragment, container, false);

        //Create arrayadapter for lang array
        ArrayAdapter<String> langadapter;
        //get language array and spinner
        langSpinner = (Spinner)rootView.findViewById(R.id.lang_spinner);
        langArray = getResources().getStringArray(R.array.language_options_array);
        // assign an array to the adapter
        langadapter = new ArrayAdapter<String>(this.getActivity(), android.R.layout.simple_list_item_1, langArray);
        //set the spinners adapter to the previously created one.
        langSpinner.setAdapter(langadapter);


        moreInfoButton = (Button)rootView.findViewById(R.id.button_readmore);
        moreInfoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Simple navigation change instead of navgraph
                FragmentTransaction fragmentTransaction = getActivity()
                        .getSupportFragmentManager().beginTransaction();
                fragmentTransaction.replace(R.id.content_frame, new ReadMore());
                fragmentTransaction.commit();
                //NK TODO: Change navigation system to Navgraph, and re-implement this
                //    NavHostFragment.findNavController(SettingsFragment.this)
                //            .navigate(R.id.action_settingsFragment_to_settingsEditFragment);
            }
        });

        return rootView;
    }
}
