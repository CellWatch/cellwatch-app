package com.example.cellwatch;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class ReadMore extends Fragment {

    Button goHomeButton;

    public ReadMore() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_readmore, container, false);

        goHomeButton = (Button)rootView.findViewById(R.id.button3);
        goHomeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Simple navigation change instead of navgraph
                FragmentTransaction fragmentTransaction = getActivity()
                        .getSupportFragmentManager().beginTransaction();
                fragmentTransaction.replace(R.id.content_frame, new HomeFragment());
                fragmentTransaction.commit();
            }
        });
        return rootView;
    }
}