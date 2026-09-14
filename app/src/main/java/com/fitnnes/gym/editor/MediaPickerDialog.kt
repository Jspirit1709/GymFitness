package com.fitnnes.gym.editor

import android.app.AlertDialog
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
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
 * Punto 3: además de la detección automática por extensión, el usuario puede marcar
 * manualmente el tipo (Imagen / Video / YouTube) con el RadioGroup rgMediaType, así
 * un video de cualquier duración (10s a 1h) y de cualquier hosting/CDN funciona sin
 * depender de que la URL tenga una extensión reconocible.
 *
 * IMPORTANTE: debe crearse en onCreate() de la Activity (antes de STARTED), porque
 * registra un ActivityResultLauncher internamente.
 *
 * REQUIERE agregar a dialog_media_picker.xml el RadioGroup con ids rgMediaType,
 * rbTypeImage, rbTypeVideo, rbTypeYoutube — ver media_picker_radio_snippet.xml adjunto.
 */
class MediaPickerDialog(private val activity: AppCompatActivity) {

    private var pendingUri: String? = null
    private var pendingType: MediaType = MediaType.NONE
    private var onResult: ((String?, MediaType) -> Unit)? = null

    private var ivPreview: ImageView? = null
    private var ivPlaceholder: ImageView? = null
    private var etUrl: EditText? = null
    private var rgMediaType: RadioGroup? = null
    private var rbImage: RadioButton? = null
    private var rbVideo: RadioButton? = null
    private var rbYoutube: RadioButton? = null

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
                syncRadioToPendingType()
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
        rgMediaType = view.findViewById(R.id.rgMediaType)
        rbImage = view.findViewById(R.id.rbTypeImage)
        rbVideo = view.findViewById(R.id.rbTypeVideo)
        rbYoutube = view.findViewById(R.id.rbTypeYoutube)
        val btnPick = view.findViewById<ImageButton>(R.id.btnPickFromDevice)
        val btnConfirmUrl = view.findViewById<ImageButton>(R.id.btnConfirmUrl)

        if (currentType == MediaType.YOUTUBE) {
            etUrl?.setText(currentUri.orEmpty())
        }
        syncRadioToPendingType()
        updatePreview()

        btnPick.setOnClickListener { pickLauncher.launch(arrayOf("image/*", "video/*")) }

        btnConfirmUrl.setOnClickListener {
            val url = etUrl?.text?.toString()?.trim().orEmpty()
            if (url.isEmpty()) return@setOnClickListener
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(activity, activity.getString(R.string.media_invalid_link), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // El tipo lo define el selector manual, no la detección automática — así
            // cualquier video (corto o de una hora, cualquier hosting) funciona si el
            // usuario lo marca como tal.
            val selectedType = when (rgMediaType?.checkedRadioButtonId) {
                R.id.rbTypeVideo -> MediaType.VIDEO_FILE
                R.id.rbTypeYoutube -> MediaType.YOUTUBE
                R.id.rbTypeImage -> MediaType.IMAGE_BASE64
                else -> MediaUtils.detectMediaType(url)
            }

            if (selectedType == MediaType.YOUTUBE && MediaUtils.youtubeVideoId(url) == null) {
                Toast.makeText(activity, activity.getString(R.string.media_invalid_link), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            pendingUri = url
            pendingType = selectedType
            updatePreview()
        }

        // Si el usuario pega un link y todavía no tocó el selector a mano, le sugerimos
        // el tipo automáticamente — pero nunca le pisamos una elección manual ya hecha.
        etUrl?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim().orEmpty()
                if (text.startsWith("http://") || text.startsWith("https://")) {
                    when (MediaUtils.detectMediaType(text)) {
                        MediaType.YOUTUBE -> rbYoutube?.isChecked = true
                        MediaType.VIDEO_FILE -> rbVideo?.isChecked = true
                        else -> { /* no forzamos Imagen: el usuario puede querer marcar Video a mano */ }
                    }
                }
            }
        })

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

    private fun syncRadioToPendingType() {
        when (pendingType) {
            MediaType.VIDEO_FILE -> rbVideo?.isChecked = true
            MediaType.YOUTUBE -> rbYoutube?.isChecked = true
            else -> rbImage?.isChecked = true
        }
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
