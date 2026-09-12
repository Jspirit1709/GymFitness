package com.fitnnes.gym.timer

import android.content.*
import android.net.Uri
import android.os.*
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fitnnes.gym.R
import com.fitnnes.gym.data.AppPrefs
import com.fitnnes.gym.menu.MainActivity
import com.fitnnes.gym.workoutdomain.Exercise
import com.fitnnes.gym.workoutdomain.ExercisePlan

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
    private lateinit var rootLayout: View
    private lateinit var planInfoLayout: View

    private var exercise: Exercise? = null
    private var exercisePlan: ExercisePlan? = null

    companion object {
        fun startWithExercise(context: Context, exercise: Exercise): Intent {
            return Intent(context, TimerActivity::class.java).apply {
                val bundle = Bundle()
                bundle.putParcelable(TimerService.EXTRA_EXERCISE, exercise)
                putExtra(TimerService.EXTRA_BUNDLE, bundle)
            }
        }

        fun startWithPlan(context: Context, plan: ExercisePlan): Intent {
            return Intent(context, TimerActivity::class.java).apply {
                val bundle = Bundle()
                bundle.putParcelable(TimerService.EXTRA_EXERCISE_PLAN, plan)
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
        planInfoLayout = findViewById(R.id.planInfoLayout)

        btnPauseResume.setOnClickListener {
            val service = timerService ?: return@setOnClickListener
            if (service.getTimerState().isPaused) service.resumeTimer() else service.pauseTimer()
        }
        btnSkipStart.setOnClickListener { if (!controlsLocked) timerService?.skipToFirstExercise() }
        btnPrevious.setOnClickListener { if (!controlsLocked) timerService?.previousExercise() }
        btnNext.setOnClickListener { if (!controlsLocked) timerService?.nextExercise() }
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
        // btnPauseResume se mantiene siempre accesible (como en el original)
    }

    private fun loadIntentData() {
        intent.getBundleExtra(TimerService.EXTRA_BUNDLE)?.let { bundle ->
            exercise = bundle.getParcelable(TimerService.EXTRA_EXERCISE)
            exercisePlan = bundle.getParcelable(TimerService.EXTRA_EXERCISE_PLAN)
        }
    }

    private fun bindAndStartService() {
        val serviceIntent = when {
            exercisePlan != null -> TimerService.startWithPlan(this, exercisePlan!!)
            exercise != null -> TimerService.startWithExercise(this, exercise!!)
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

        if (!state.mediaUri.isNullOrBlank()) {
            mediaContainer.visibility = View.VISIBLE
            ivPhaseIcon.visibility = View.GONE
            runCatching { ivMedia.setImageURI(Uri.parse(state.mediaUri)) }
        } else {
            mediaContainer.visibility = View.GONE
            ivPhaseIcon.visibility = View.VISIBLE
        }
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
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (controlsLocked) return
        timerService?.pauseTimer()
        super.onBackPressed()
    }
}
