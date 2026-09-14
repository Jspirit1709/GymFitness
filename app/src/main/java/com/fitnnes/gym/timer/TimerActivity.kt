package com.fitnnes.gym.timer

import android.content.*
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fitnnes.gym.R
import com.bumptech.glide.Glide
import com.fitnnes.gym.data.AppPrefs
import com.fitnnes.gym.data.MediaUtils
import com.fitnnes.gym.data.Repository
import com.fitnnes.gym.menu.MainActivity
import com.fitnnes.gym.workoutdomain.Exercise
import com.fitnnes.gym.workoutdomain.ExercisePlan
import com.fitnnes.gym.workoutdomain.MediaType

class TimerActivity : AppCompatActivity(), ServiceConnection, TimerListener {

    private var timerService: TimerService? = null
    private var isBound = false
    private var controlsLocked = false
    private var soundOn = true

    private lateinit var tvExerciseName: TextView
    private lateinit var tvPhase: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvSet: TextView
    private lateinit var tvRemainingSmall: TextView
    private lateinit var tvNextExercise: TextView
    private lateinit var tvPlanTime: TextView
    private lateinit var tvPlanName: TextView
    private lateinit var tvPlanProgress: TextView
    private lateinit var btnPauseResume: ImageButton
    private lateinit var btnSkipStart: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnSkipEnd: ImageButton
    private lateinit var btnLock: ImageButton
    private lateinit var btnSound: ImageButton
    private lateinit var btnStop: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var ivPhaseIcon: ImageView
    private lateinit var mediaContainer: View
    private lateinit var ivMedia: ImageView
    private lateinit var videoMedia: VideoView
    private lateinit var webMedia: WebView
    private lateinit var btnToggleMedia: ImageButton
    private lateinit var btnCloseMedia: ImageButton
    private lateinit var rootLayout: View
    private lateinit var planInfoLayout: View

    private var exercise: Exercise? = null
    private var exercisePlan: ExercisePlan? = null

    private var mediaPanelVisible = false
    private var currentMediaUri: String? = null
    private var currentMediaType: MediaType = MediaType.NONE
    private var currentVideoPlayer: MediaPlayer? = null

    companion object {
        private const val TAG = "TimerActivity"

        // FIX: antes viajaba el Exercise/ExercisePlan completo (con imágenes en base64)
        // por el Intent -> TransactionTooLargeException con workouts con varias
        // imágenes, y el entrenamiento no arrancaba. Ahora solo viaja el id.
        fun startWithExercise(context: Context, exerciseId: String): Intent {
            return Intent(context, TimerActivity::class.java).apply {
                val bundle = Bundle()
                bundle.putString(TimerService.EXTRA_EXERCISE_ID, exerciseId)
                putExtra(TimerService.EXTRA_BUNDLE, bundle)
            }
        }

        fun startWithPlan(context: Context, planId: String): Intent {
            return Intent(context, TimerActivity::class.java).apply {
                val bundle = Bundle()
                bundle.putString(TimerService.EXTRA_EXERCISE_PLAN_ID, planId)
                putExtra(TimerService.EXTRA_BUNDLE, bundle)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timer)
        if (AppPrefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        soundOn = AppPrefs.soundEnabled
        setupViews()
        loadIntentData()
        bindAndStartService()
    }

    private fun setupViews() {
        rootLayout = findViewById(R.id.rootLayout)
        tvExerciseName = findViewById(R.id.tvExerciseName)
        tvPhase = findViewById(R.id.tvPhase)
        tvTimer = findViewById(R.id.tvTimer)
        tvSet = findViewById(R.id.tvSet)
        tvRemainingSmall = findViewById(R.id.tvRemainingSmall)
        tvNextExercise = findViewById(R.id.tvNextExercise)
        tvPlanTime = findViewById(R.id.tvPlanTime)
        tvPlanName = findViewById(R.id.tvPlanName)
        tvPlanProgress = findViewById(R.id.tvPlanProgress)
        btnPauseResume = findViewById(R.id.btnPauseResume)
        btnSkipStart = findViewById(R.id.btnSkipStart)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        btnSkipEnd = findViewById(R.id.btnSkipEnd)
        btnLock = findViewById(R.id.btnLock)
        btnSound = findViewById(R.id.btnSound)
        btnStop = findViewById(R.id.btnStop)
        progressBar = findViewById(R.id.progressBar)
        ivPhaseIcon = findViewById(R.id.ivPhaseIcon)
        mediaContainer = findViewById(R.id.mediaContainer)
        ivMedia = findViewById(R.id.ivMedia)
        videoMedia = findViewById(R.id.videoMedia)
        webMedia = findViewById(R.id.webMedia)
        btnToggleMedia = findViewById(R.id.btnToggleMedia)
        btnCloseMedia = findViewById(R.id.btnCloseMedia)
        planInfoLayout = findViewById(R.id.planInfoLayout)

        webMedia.settings.javaScriptEnabled = true
        webMedia.settings.mediaPlaybackRequiresUserGesture = false
        webMedia.settings.domStorageEnabled = true
        // Puente JS para que la página de YouTube nos avise cuándo termina el video.
        webMedia.addJavascriptInterface(YoutubeBridge(), "AndroidBridge")

        // FIX: sin esto, si un video local falla (uri sin permiso persistente, archivo
        // corrupto, etc), Android mostraba su diálogo de error nativo tapando el timer
        // ("no inicia el entrenamiento"). Ahora lo interceptamos, lo logueamos, y
        // seguimos el entrenamiento sin video en vez de bloquear la pantalla.
        videoMedia.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "VideoView error: what=$what extra=$extra uri=$currentMediaUri")
            runOnUiThread {
                videoMedia.visibility = View.GONE
                mediaContainer.visibility = View.GONE
                ivPhaseIcon.visibility = View.VISIBLE
                stopVideoPlayback()
            }
            true // consumido: evita el diálogo de error por defecto del sistema
        }

        btnToggleMedia.setOnClickListener {
            mediaPanelVisible = !mediaPanelVisible
            renderMediaPanel()
        }
        btnCloseMedia.setOnClickListener {
            mediaPanelVisible = false
            renderMediaPanel()
        }

        btnPauseResume.setOnClickListener {
            val service = timerService ?: return@setOnClickListener
            if (service.getTimerState().isPaused) service.resumeTimer() else service.pauseTimer()
        }
        btnSkipStart.setOnClickListener { if (!controlsLocked) timerService?.skipToFirstExercise() }
        btnPrevious.setOnClickListener { if (!controlsLocked) timerService?.previousStep() }
        btnNext.setOnClickListener { if (!controlsLocked) timerService?.nextStep() }
        btnSkipEnd.setOnClickListener { if (!controlsLocked) timerService?.skipToLastExercise() }
        btnStop.setOnClickListener { if (!controlsLocked) { timerService?.stopTimer(); goToMenu() } }

        btnLock.setOnClickListener {
            controlsLocked = !controlsLocked
            updateLockUI()
        }

        btnSound.setOnClickListener {
            soundOn = !soundOn
            AppPrefs.soundEnabled = soundOn
            btnSound.setImageResource(if (soundOn) R.drawable.ic_sound_on else R.drawable.ic_sound_off)
            applySoundState()
        }
        btnSound.setImageResource(if (soundOn) R.drawable.ic_sound_on else R.drawable.ic_sound_off)
    }

    private fun updateLockUI() {
        btnLock.setImageResource(if (controlsLocked) R.drawable.ic_lock else R.drawable.ic_lock_open)
        val alpha = if (controlsLocked) 0.35f else 1f
        btnSkipStart.alpha = alpha
        btnPrevious.alpha = alpha
        btnNext.alpha = alpha
        btnSkipEnd.alpha = alpha
        btnStop.alpha = alpha
    }

    private fun loadIntentData() {
        intent.getBundleExtra(TimerService.EXTRA_BUNDLE)?.let { bundle ->
            bundle.getString(TimerService.EXTRA_EXERCISE_ID)?.let { id ->
                exercise = Repository.getExercise(id)
            }
            bundle.getString(TimerService.EXTRA_EXERCISE_PLAN_ID)?.let { id ->
                exercisePlan = Repository.getPlan(id)
            }
        }
    }

    private fun bindAndStartService() {
        val bundle = intent.getBundleExtra(TimerService.EXTRA_BUNDLE)
        val exerciseId = bundle?.getString(TimerService.EXTRA_EXERCISE_ID)
        val planId = bundle?.getString(TimerService.EXTRA_EXERCISE_PLAN_ID)
        val serviceIntent = when {
            planId != null -> TimerService.startWithPlan(this, planId)
            exerciseId != null -> TimerService.startWithExercise(this, exerciseId)
            else -> return
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, this, Context.BIND_AUTO_CREATE)
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = service as TimerService.TimerBinder
        timerService = binder.getService()
        timerService?.setTimerListener(this)
        isBound = true
        timerService?.getTimerState()?.let { updateUI(it) }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        timerService = null
        isBound = false
    }

    override fun onTimerStateChanged(state: TimerState) {
        runOnUiThread { updateUI(state) }
    }

    override fun onTimerFinished() {
        runOnUiThread { goToMenu() }
    }

    override fun onTimerTick(remainingSeconds: Int) {
        // manejado en onTimerStateChanged
    }

    private fun updateUI(state: TimerState) {
        tvExerciseName.text = state.currentExerciseName
        tvTimer.text = formatTime(state.currentTime)
        tvRemainingSmall.text = formatTime(state.currentTime)
        tvSet.text = getString(R.string.set_format, state.currentRound, state.totalRounds)

        val phaseName = when (state.phase) {
            TimerPhase.PREPARE -> "Prepare"
            TimerPhase.WORK -> "Work"
            TimerPhase.REST -> "Rest"
            TimerPhase.COOL_DOWN -> "Cooldown"
            TimerPhase.TRANSITION -> "Get ready"
            TimerPhase.FINISHED -> "Finished"
            TimerPhase.IDLE -> "Ready"
        }
        tvPhase.text = state.stepLabel.ifBlank { phaseName }

        val bgColor = when (state.phase) {
            TimerPhase.PREPARE -> R.color.phase_prepare
            TimerPhase.WORK -> R.color.phase_work
            TimerPhase.REST -> R.color.phase_rest
            TimerPhase.COOL_DOWN -> R.color.phase_cooldown
            TimerPhase.TRANSITION -> R.color.phase_transition
            TimerPhase.FINISHED -> R.color.phase_finished
            TimerPhase.IDLE -> R.color.phase_idle
        }
        rootLayout.setBackgroundColor(ContextCompat.getColor(this, bgColor))

        btnPauseResume.setImageResource(if (state.isPaused) R.drawable.ic_play else R.drawable.ic_pause)

        if (state.nextExerciseName != null) {
            tvNextExercise.visibility = View.VISIBLE
            tvNextExercise.text = getString(R.string.next_label, state.nextExerciseName)
        } else {
            tvNextExercise.visibility = View.GONE
        }

        if (state.planName != null) {
            planInfoLayout.visibility = View.VISIBLE
            tvPlanTime.text = "Plan ${formatTime(state.exerciseTotalTime)}"
            tvPlanName.text = state.planName
            tvPlanProgress.text = getString(R.string.set_format, state.planIndex + 1, state.planSize)
        } else {
            planInfoLayout.visibility = View.GONE
        }

        if (state.totalTime > 0) {
            progressBar.max = state.totalTime
            progressBar.progress = state.totalTime - state.currentTime
        }

        val mediaChanged = state.mediaUri != currentMediaUri || state.mediaType != currentMediaType
        currentMediaUri = state.mediaUri
        currentMediaType = state.mediaType

        val hasMedia = !state.mediaUri.isNullOrBlank() && state.mediaType != MediaType.NONE
        btnToggleMedia.visibility = if (hasMedia) View.VISIBLE else View.GONE
        ivPhaseIcon.visibility = if (hasMedia && mediaPanelVisible) View.GONE else View.VISIBLE

        if (!hasMedia) {
            if (mediaPanelVisible) {
                mediaPanelVisible = false
            }
            renderMediaPanel()
        } else if (mediaChanged) {
            renderMediaPanel()
        }
    }

    /**
     * Muestra u oculta el panel flotante de media, y carga el contenido correcto según el tipo.
     * FIX: toda la función está envuelta en try/catch para que un fallo con CUALQUIER tipo de
     * media (imagen, video o YouTube) nunca bloquee ni "cuelgue" el entrenamiento completo —
     * en el peor caso, se oculta el panel de media y el timer sigue corriendo solo.
     */
    private fun renderMediaPanel() {
        try {
            if (!mediaPanelVisible || currentMediaUri.isNullOrBlank()) {
                mediaContainer.visibility = View.GONE
                ivPhaseIcon.visibility = if (btnToggleMedia.visibility == View.VISIBLE) View.VISIBLE else ivPhaseIcon.visibility
                stopVideoPlayback()
                webMedia.loadUrl("about:blank")
                return
            }

            mediaContainer.visibility = View.VISIBLE
            ivPhaseIcon.visibility = View.GONE
            ivMedia.visibility = View.GONE
            videoMedia.visibility = View.GONE
            webMedia.visibility = View.GONE
            stopVideoPlayback()

            val uri = currentMediaUri ?: return
            when (currentMediaType) {
                MediaType.IMAGE_BASE64 -> {
                    ivMedia.visibility = View.VISIBLE
                    runCatching {
                        Glide.with(this).load(uri).into(ivMedia)
                    }.onFailure { e ->
                        Log.e(TAG, "Glide no pudo cargar la imagen, probando decode manual de base64", e)
                        // FIX: media importada del zip puede venir como base64 puro sin el
                        // prefijo "data:image/...;base64,", lo cual Glide no siempre resuelve.
                        // Fallback: decodificamos a mano en vez de dejar la imagen rota/perdida.
                        runCatching {
                            val cleanBase64 = uri.substringAfter(",", uri)
                            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                ivMedia.setImageBitmap(bitmap)
                            } else {
                                ivMedia.visibility = View.GONE
                            }
                        }.onFailure { e2 ->
                            Log.e(TAG, "Tampoco se pudo decodificar como base64 (len=${uri.length})", e2)
                            ivMedia.visibility = View.GONE
                        }
                    }
                }
                MediaType.VIDEO_FILE -> {
                    videoMedia.visibility = View.VISIBLE
                    // FIX: antes, start() estaba FUERA de este runCatching. Si setVideoURI
                    // fallaba (ej. URI de contenido sin permiso persistente tras reimportar),
                    // el error se tragaba en silencio y luego se llamaba start() igual sobre
                    // un player mal preparado -> Android mostraba su diálogo nativo de error,
                    // tapando el timer y dando la sensación de que "no inicia".
                    runCatching {
                        videoMedia.setMediaController(MediaController(this).apply { setAnchorView(videoMedia) })
                        videoMedia.setVideoURI(Uri.parse(uri))
                        videoMedia.setOnPreparedListener { mp ->
                            currentVideoPlayer = mp
                            mp.isLooping = false
                            applySoundState()
                            mp.setOnCompletionListener {
                                runOnUiThread {
                                    if (!controlsLocked) timerService?.nextStep()
                                }
                            }
                        }
                        videoMedia.start()
                    }.onFailure { e ->
                        Log.e(TAG, "Error al reproducir video local (uri=$uri)", e)
                        videoMedia.visibility = View.GONE
                        mediaContainer.visibility = View.GONE
                        ivPhaseIcon.visibility = View.VISIBLE
                    }
                }
                MediaType.YOUTUBE -> {
                    webMedia.visibility = View.VISIBLE
                    loadYoutube(uri)
                }
                MediaType.NONE -> {
                    mediaContainer.visibility = View.GONE
                    ivPhaseIcon.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo inesperado en renderMediaPanel, ocultando media para no bloquear el entrenamiento", e)
            runCatching {
                mediaContainer.visibility = View.GONE
                ivPhaseIcon.visibility = View.VISIBLE
                stopVideoPlayback()
            }
        }
    }

    /**
     * Carga el video de YouTube dentro de una página HTML mínima que envuelve el iframe
     * del embed. Esto permite escuchar los eventos del reproductor (onStateChange) vía la
     * API de postMessage de YouTube para: 1) detectar cuándo termina el video y pasar solo
     * al siguiente intervalo, y 2) mutear/desmutear según el botón de sonido.
     */
    private fun loadYoutube(url: String) {
        val embedUrl = runCatching { MediaUtils.youtubeEmbedUrl(url, muted = !soundOn) }
            .onFailure { e -> Log.e(TAG, "Error generando embed de YouTube (url=$url)", e) }
            .getOrNull()
        if (embedUrl == null) {
            webMedia.visibility = View.GONE
            mediaContainer.visibility = View.GONE
            ivPhaseIcon.visibility = View.VISIBLE
            return
        }
        // FIX: antes solo se escuchaba pasivo (window.addEventListener('message', ...))
        // sin nunca pedirle al iframe que empezara a emitir los eventos de estado — el
        // player de YouTube no manda onStateChange a menos que se lo pidas explícitamente
        // con el comando addEventListener vía postMessage. Por eso el video nunca
        // avisaba que había terminado. Ahora, al cargar el iframe, se manda ese comando.
        val html = """
            <!DOCTYPE html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>html,body{margin:0;padding:0;background:#000;height:100%;}
            iframe{width:100%;height:100%;border:0;position:fixed;top:0;left:0;}</style>
            </head><body>
            <iframe id="ytplayer" src="$embedUrl" allow="autoplay; encrypted-media" allowfullscreen></iframe>
            <script>
            var player = document.getElementById('ytplayer');
            function onPlayerFrameLoad() {
                player.contentWindow.postMessage(JSON.stringify({event: 'listening', id: 1}), '*');
                player.contentWindow.postMessage(JSON.stringify({
                    event: 'command', func: 'addEventListener', args: ['onStateChange']
                }), '*');
            }
            player.addEventListener('load', onPlayerFrameLoad);
            window.addEventListener('message', function(event) {
                try {
                    var data = JSON.parse(event.data);
                    // 0 = ended, según la YouTube IFrame Player API.
                    if (data.event === 'onStateChange' && data.info === 0) {
                        AndroidBridge.onYoutubeEnded();
                    }
                } catch (e) {}
            });
            </script>
            </body></html>
        """.trimIndent()
        runCatching {
            // baseUrl = youtube.com para que el postMessage entre el iframe y esta página
            // funcione bien con los permisos de origen que espera el player embebido.
            webMedia.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
        }.onFailure { e -> Log.e(TAG, "Error cargando WebView de YouTube", e) }
    }

    /** Aplica el estado actual de soundOn a la media que esté reproduciéndose ahora. */
    private fun applySoundState() {
        val volume = if (soundOn) 1f else 0f
        runCatching { currentVideoPlayer?.setVolume(volume, volume) }
            .onFailure { e -> Log.e(TAG, "Error aplicando volumen a MediaPlayer", e) }
        if (currentMediaType == MediaType.YOUTUBE && mediaPanelVisible && !currentMediaUri.isNullOrBlank()) {
            loadYoutube(currentMediaUri!!)
        }
    }

    /**
     * Puente para que la página de YouTube nos avise cuándo terminó el video de verdad.
     * FIX: antes llamaba a nextStep() (salto manual), que compite con el avance
     * automático del countdown normal. Ahora el countdown del intervalo YOUTUBE ya no
     * avanza solo al llegar a 00:00 (ver TimerService.startCountDown) — el video manda,
     * así que acá se llama a onYoutubeVideoEnded(), que es quien realmente decide avanzar
     * (corta el countdown si el video terminó antes, o resuelve la espera si ya estaba
     * en 00:00 esperando este aviso).
     */
    private inner class YoutubeBridge {
        @JavascriptInterface
        fun onYoutubeEnded() {
            runOnUiThread {
                if (!controlsLocked) timerService?.onYoutubeVideoEnded()
            }
        }
    }

    private fun stopVideoPlayback() {
        runCatching { if (videoMedia.isPlaying) videoMedia.stopPlayback() }
            .onFailure { e -> Log.e(TAG, "Error deteniendo video", e) }
        currentVideoPlayer = null
    }

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    private fun goToMenu() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(this)
            isBound = false
        }
        stopVideoPlayback()
        webMedia.destroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (controlsLocked) return
        timerService?.pauseTimer()
        super.onBackPressed()
    }
}
