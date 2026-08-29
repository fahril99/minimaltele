package com.minimaltelegram

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class PhotoViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val imageView = ImageView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        setContentView(imageView)
        
        val path = intent.getStringExtra("photo_path")
        if (path != null) {
            Glide.with(this).load(path).into(imageView)
            imageView.setOnLongClickListener {
                saveMediaToDownloads(path, "photo_${System.currentTimeMillis()}.jpg")
                true
            }
        }
        
        imageView.setOnClickListener {
            finish()
        }
        
        Toast.makeText(this, "Long press to save to Downloads", Toast.LENGTH_SHORT).show()
    }
    
    private fun saveMediaToDownloads(filePath: String, fileName: String) {
        try {
            val sourceFile = java.io.File(filePath)
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val destFile = java.io.File(downloadsDir, fileName)
            sourceFile.copyTo(destFile, overwrite = true)
            Toast.makeText(this, "Saved to Downloads", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
        }
    }
}
