package com.cellwatch.ui.home

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.cellwatch.R

class DrawerItemCustomAdapter(
    var mContext: Context,
    var layoutResourceId: Int,
    data: Array<DataModel?>
) : ArrayAdapter<DataModel?>(
    mContext, layoutResourceId, data!!
) {
    var data: Array<DataModel>? = null

    init {
        this.data = data
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var listItem = convertView
        val inflater = (mContext as Activity).layoutInflater
        listItem = inflater.inflate(layoutResourceId, parent, false)
        val imageViewIcon = listItem.findViewById<View>(R.id.imageViewIcon) as ImageView
        val textViewName = listItem.findViewById<View>(R.id.textViewName) as TextView
        val folder = data!![position]
        imageViewIcon.setImageResource(folder.icon)
        textViewName.text = folder.name
        return listItem
    }
}