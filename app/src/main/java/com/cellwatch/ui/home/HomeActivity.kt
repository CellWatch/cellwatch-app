package com.cellwatch.ui.home

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.cellwatch.R

class HomeActivity : AppCompatActivity() {
    private lateinit var mNavigationDrawerItemTitles: Array<String>
    private var mDrawerLayout: DrawerLayout? = null
    private var mDrawerList: ListView? = null
    var toolbar: Toolbar? = null
    private var mDrawerTitle: CharSequence? = null
    private var mTitle: CharSequence? = null
    var mDrawerToggle: ActionBarDrawerToggle? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        mDrawerTitle = title
        mTitle = mDrawerTitle
        mNavigationDrawerItemTitles =
            resources.getStringArray(R.array.navigation_drawer_items_array)
        mDrawerLayout = findViewById<View>(R.id.drawer_layout) as DrawerLayout
        mDrawerList = findViewById<View>(R.id.left_drawer) as ListView

        //NK Trying navcontroller to see if it will work
        //ok, so in order to do this, I would have to completely change all the navigation on the
        //whole app.  This should probably be done at some later point.
        //NavController navController = Navigation.findNavController(this, R.id.nav_host)
        setupToolbar()
        val drawerItem = arrayOfNulls<DataModel>(6)

        //Places that new items need to be added
        //1. a new layout
        //2. here, add the item to the data model
        //3. In the navigation_drawer_items_array in strings
        //4. Under SelectItem below
        //5. As an fragment class
        //. Probably in the nav graph
        drawerItem[0] = DataModel(R.drawable.map_ptr_icon, "Map")
        drawerItem[1] = DataModel(R.drawable.speedometer_icon, "Measure")
        drawerItem[2] = DataModel(R.drawable.clock_icon, "Measurement History")
        drawerItem[3] = DataModel(R.drawable.star_1, "Settings")
        drawerItem[4] = DataModel(R.drawable.help_icon, "Help")
        drawerItem[5] = DataModel(R.drawable.cw_bkg3, "CellWatch")
        supportActionBar!!.setDisplayHomeAsUpEnabled(false)
        supportActionBar!!.setHomeButtonEnabled(true)
        val adapter = DrawerItemCustomAdapter(this, R.layout.list_view_item_row, drawerItem)
        mDrawerList!!.adapter = adapter
        mDrawerList!!.onItemClickListener = DrawerItemClickListener()
        mDrawerLayout = findViewById<View>(R.id.drawer_layout) as DrawerLayout
        setupDrawerToggle()
        mDrawerLayout!!.addDrawerListener(mDrawerToggle!!)
        val tempFragment: Fragment = HomeFragment()
        val fragmentManager = supportFragmentManager
        fragmentManager.beginTransaction().replace(R.id.content_frame, tempFragment).commit()
    }

    private inner class DrawerItemClickListener : OnItemClickListener {
        override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
            //NK TODO: this sets the background color for the item but doesn't un-set any others
            view.setBackgroundColor(
                ContextCompat.getColor(
                    applicationContext,
                    R.color.highlightblue
                )
            )
            selectItem(position)
        }
    }

    private fun selectItem(position: Int) {
        var fragment: Fragment? = null
        when (position) {
            0 -> fragment = MapFragment()
            1 -> fragment = MeasureFragment()
            2 -> fragment = MeasureHistoryFragment()
            3 -> fragment = SettingsFragment()
            4 -> fragment = HelpFragment()
            5 ->                     //NK TODO: Make this lead to the about fragment
                fragment = HelpFragment()

            else -> {}
        }
        if (fragment != null) {
            val fragmentManager = supportFragmentManager
            fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit()
            mDrawerList!!.setItemChecked(position, true)
            mDrawerList!!.setSelection(position)
            title = mNavigationDrawerItemTitles[position]
            mDrawerLayout!!.closeDrawer(mDrawerList!!)
        } else {
            Log.e("MainActivity", "Error in creating fragment")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (mDrawerToggle!!.onOptionsItemSelected(item)) {
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun setTitle(title: CharSequence) {
        mTitle = title
        supportActionBar!!.title = mTitle
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        mDrawerToggle!!.syncState()
    }

    fun setupToolbar() {
        toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
    }

    fun setupDrawerToggle() {
        mDrawerToggle = ActionBarDrawerToggle(
            this,
            mDrawerLayout,
            toolbar,
            R.string.app_name,
            R.string.app_name
        )
        //This is necessary to change the icon of the Drawer Toggle upon state change.
        mDrawerToggle!!.syncState()
    }
}