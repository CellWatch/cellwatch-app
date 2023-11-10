package com.cellwatch.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.cellwatch.R;

public class HomeActivity extends AppCompatActivity {
        private String[] mNavigationDrawerItemTitles;
        private DrawerLayout mDrawerLayout;
        private ListView mDrawerList;
        Toolbar toolbar;
        private CharSequence mDrawerTitle;
        private CharSequence mTitle;
        androidx.appcompat.app.ActionBarDrawerToggle mDrawerToggle;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_home);
            mTitle = mDrawerTitle = getTitle();
            mNavigationDrawerItemTitles= getResources().getStringArray(R.array.navigation_drawer_items_array);
            mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
            mDrawerList = (ListView) findViewById(R.id.left_drawer);

            //NK Trying navcontroller to see if it will work
            //ok, so in order to do this, I would have to completely change all the navigation on the
            //whole app.  This should probably be done at some later point.
            //NavController navController = Navigation.findNavController(this, R.id.nav_host)

            setupToolbar();

            DataModel[] drawerItem = new DataModel[6];

            //Places that new items need to be added
            //1. a new layout
            //2. here, add the item to the data model
            //3. In the navigation_drawer_items_array in strings
            //4. Under SelectItem below
            //5. As an fragment class
            //. Probably in the nav graph

            drawerItem[0] = new DataModel(R.drawable.map_ptr_icon, "Map");
            drawerItem[1] = new DataModel(R.drawable.speedometer_icon, "Measure");
            drawerItem[2] = new DataModel(R.drawable.clock_icon, "Measurement History");
            drawerItem[3] = new DataModel(R.drawable.star_1, "Settings");
            drawerItem[4] = new DataModel(R.drawable.help_icon, "Help");
            drawerItem[5] = new DataModel(R.drawable.cw_bkg3, "CellWatch");
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setHomeButtonEnabled(true);

            DrawerItemCustomAdapter adapter = new DrawerItemCustomAdapter(this, R.layout.list_view_item_row, drawerItem);
            mDrawerList.setAdapter(adapter);
            mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
            mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
            mDrawerLayout.addDrawerListener(mDrawerToggle);
            setupDrawerToggle();

            Fragment tempFragment = new HomeFragment();
            FragmentManager fragmentManager = getSupportFragmentManager();
            fragmentManager.beginTransaction().replace(R.id.content_frame, tempFragment).commit();

        }

        private class DrawerItemClickListener implements ListView.OnItemClickListener {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //NK TODO: this sets the background color for the item but doesn't un-set any others
                view.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.highlightblue));
                selectItem(position);
            }

        }

        private void selectItem(int position) {

            Fragment fragment = null;

            switch (position) {
                case 0:
                    fragment = new MapFragment();
                    break;
                case 1:
                    fragment = new MeasureFragment();
                    break;
                case 2:
                    fragment = new MeasureHistoryFragment();
                    break;
                case 3:
                    fragment = new SettingsFragment();
                    break;
                case 4:
                    fragment = new HelpFragment();
                    break;
                case 5:
                    //NK TODO: Make this lead to the about fragment
                    fragment = new HelpFragment();
                    break;
                default:
                    break;
            }

            if (fragment != null) {
                FragmentManager fragmentManager = getSupportFragmentManager();
                fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();

                mDrawerList.setItemChecked(position, true);
                mDrawerList.setSelection(position);
                setTitle(mNavigationDrawerItemTitles[position]);
                mDrawerLayout.closeDrawer(mDrawerList);

            } else {
                Log.e("MainActivity", "Error in creating fragment");
            }
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {

            if (mDrawerToggle.onOptionsItemSelected(item)) {
                return true;
            }

            return super.onOptionsItemSelected(item);
        }

        @Override
        public void setTitle(CharSequence title) {
            mTitle = title;
            getSupportActionBar().setTitle(mTitle);
        }

        @Override
        protected void onPostCreate(Bundle savedInstanceState) {
            super.onPostCreate(savedInstanceState);
            mDrawerToggle.syncState();
        }

        void setupToolbar(){
            toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        void setupDrawerToggle(){
            mDrawerToggle = new androidx.appcompat.app.ActionBarDrawerToggle(this,mDrawerLayout,toolbar,R.string.app_name, R.string.app_name);
            //This is necessary to change the icon of the Drawer Toggle upon state change.
            mDrawerToggle.syncState();
        }
}

