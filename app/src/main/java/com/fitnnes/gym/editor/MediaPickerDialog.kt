package com.fitnnes.gym.editor

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.fitnnes.gym.R
import com.fitnnes.gym.data.MediaUtils
import com.fitnnes.gym.workoutdomain.MediaType

/**
 * Diálogo reutilizable para elegir o editar la media (imagen/video del dispositivo,
 * o un link de video/YouTube) tanto de un ejercicio completo como de un intervalo
 * individual dentro de una secuencia personalizada.
 *
 * IMPORTANTE: debe crearse en onCreate() de la Activity (antes de STARTED), porque
 * registra un ActivityResultLauncher internamente.
 */
class MediaPickerDialog(private val activity: AppCompatActivity) {

    private var pendingUri: String? = null
    private var pendingType: MediaType = MediaType.NONE
    private var onResult: ((String?, MediaType) -> Unit)? = null

    private var ivPreview: ImageView? = null
    private var ivPlaceholder: ImageView? = null
    private var etUrl: EditText? = null

    private val pickLauncher =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    activity.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val mime = runCatching { activity.contentResolver.getType(uri) }.getOrNull().orEmpty()
                val isVideo = mime.startsWith("video")
                pendingUri = uri.toString()
                pendingType = if (isVideo) MediaType.VIDEO_FILE else MediaType.IMAGE_BASE64
                etUrl?.setText("")
                updatePreview()
            }
        }

    fun show(currentUri: String?, currentType: MediaType, onResult: (String?, MediaType) -> Unit) {
        this.onResult = onResult
        pendingUri = currentUri
        pendingType = currentType

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_media_picker, null)
        ivPreview = view.findViewById(R.id.ivMediaDialogPreview)
        ivPlaceholder = view.findViewById(R.id.ivMediaDialogPlaceholder)
        etUrl = view.findViewById(R.id.etMediaUrl)
        val btnPick = view.findViewById<ImageButton>(R.id.btnPickFromDevice)
        val btnConfirmUrl = view.findViewById<ImageButton>(R.id.btnConfirmUrl)

        if (currentType == MediaType.YOUTUBE) {
            etUrl?.setText(currentUri.orEmpty())
        }
        updatePreview()

        btnPick.setOnClickListener { pickLauncher.launch(arrayOf("image/*", "video/*")) }
        btnConfirmUrl.setOnClickListener {
            val url = etUrl?.text?.toString()?.trim().orEmpty()
            if (url.isEmpty()) return@setOnClickListener
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(activity, activity.getString(R.string.media_invalid_link), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pendingUri = url
            pendingType = MediaUtils.detectMediaType(url)
            updatePreview()
        }

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.media_picker_title))
            .setView(view)
            .setNeutralButton(activity.getString(R.string.delete)) { _, _ ->
                onResult(null, MediaType.NONE)
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .setPositiveButton("OK") { _, _ ->
                onResult(pendingUri, pendingType)
            }
            .show()
    }

    private fun updatePreview() {
        val uri = pendingUri
        if (uri.isNullOrBlank()) {
            ivPreview?.visibility = View.GONE
            ivPlaceholder?.visibility = View.VISIBLE
            ivPlaceholder?.setImageResource(R.drawable.ic_media)
            return
        }
        when (pendingType) {
            MediaType.IMAGE_BASE64 -> {
                ivPreview?.visibility = View.VISIBLE
                ivPlaceholder?.visibility = View.GONE
                runCatching {
                    com.bumptech.glide.Glide.with(activity).load(uri).into(ivPreview!!)
                }.onFailure {
                    ivPreview?.visibility = View.GONE
                    ivPlaceholder?.visibility = View.VISIBLE
                }
            }
            MediaType.VIDEO_FILE, MediaType.YOUTUBE -> {
                // No mostramos un frame del video, solo un ícono indicando que hay video cargado.
                ivPreview?.visibility = View.GONE
                ivPlaceholder?.visibility = View.VISIBLE
                ivPlaceholder?.setImageResource(R.drawable.ic_play)
            }
            MediaType.NONE -> {
                ivPreview?.visibility = View.GONE
                ivPlaceholder?.visibility = View.VISIBLE
                ivPlaceholder?.setImageResource(R.drawable.ic_media)
            }
        }
    }
}
