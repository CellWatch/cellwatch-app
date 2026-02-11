//package edu.gatech.cc.cellwatch.core.util
//
//import android.content.ClipData
//import android.content.ClipboardManager
//import android.content.Context
//import android.view.View
//import android.widget.Toast
//import edu.gatech.cc.cellwatch.R
//
//fun View.setCopyOnClick(label: String, getText: () -> CharSequence) {
//    val context = this.context
//    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE).let {
//        if (it is ClipboardManager) {
//            it
//        } else {
//            Log.e("copyOnClick", "expected ClipboardManager, got $it")
//            null
//        }
//    } ?: return
//
//    this.setOnClickListener {
//        val text = getText()
//        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
//        Toast.makeText(context, context.getString(R.string.copied, text), Toast.LENGTH_SHORT).show()
//    }
//}