package com.fitnnes.gym.timer

import android.app.*
import android.content.*
import android.os.*
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.fitnnes.gym.R
import com.fitnnes.gym.data.AppPrefs
import com.fitnnes.gym.data.Repository
import com.fitnnes.gym.data.WorkoutSession
import com.fitnnes.gym.workoutdomain.Exercise
import com.fitnnes.gym.workoutdomain.ExercisePlan
import com.fitnnes.gym.workoutdomain.MediaType
import com.fitnnes.gym.workoutdomain.PhaseType
import java.util.Locale

enum class TimerPhase {
    IDLE, PREPARE, WORK, REST, COOL_DOWN, TRANSITION, FINISHED
}

/** Un paso concreto y "aplanado" de la secuencia actual (soporta modo simple y custom). */
data class TimerStep(
    val phase: TimerPhase,
    val duration: Int,
    val label: String,
    val repetitions: Int? = null,
    val mediaUri: String? = null,
    val mediaType: MediaType = MediaType.NONE
)

data class TimerState(
    val phase: TimerPhase = TimerPhase.IDLE,
    val stepLabel: String = "",
    val currentTime: Int = 0,
    val totalTime: Int = 0,
    val currentRound: Int = 1,
    val totalRounds: Int = 1,
    val currentExerciseName: String = "",
    val nextExerciseName: String? = null,
    val isPaused: Boolean = false,
    val isRunning: Boolean = false,
    val planName: String? = null,
    val planIndex: Int = 0,
    val planSize: Int = 1,
    val planElapsed: Int = 0,
    val exerciseTotalTime: Int = 0,
    val mediaUri: String? = null,
    val mediaType: MediaType = MediaType.NONE,
    val hasPreviousStep: Boolean = false,
    val hasNextStep: Boolean = true
)

interface TimerListener {
    fun onTimerStateChanged(state: TimerState)
    fun onTimerFinished()
    fun onTimerTick(remainingSeconds: Int)
}

class TimerService : Service() {

    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    private val binder = TimerBinder()
    private var countDownTimer: CountDownTimer? = null
    private var timerListener: TimerListener? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var exercisePlan: ExercisePlan? = null
    private var planExercises: List<Exercise> = emptyList()
    private var currentExerciseIndex = 0

    private var steps: List<TimerStep> = emptyList()
    private var stepIndex = 0
    private var currentRound = 1
    private var totalRounds = 1

    private var currentPhase = TimerPhase.IDLE
    private var isPaused = false
    private var isRunning = false
    private var remainingTime = 0
    private var planElapsedSeconds = 0
    private var wakeLock: PowerManager.WakeLock? = null

    private var sessionStartElapsed = 0

    companion object {
        const val CHANNEL_ID = "timer_channel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_EXERCISE = "EXTRA_EXERCISE"
        const val EXTRA_EXERCISE_PLAN = "EXTRA_EXERCISE_PLAN"
        const val EXTRA_BUNDLE = "EXTRA_BUNDLE"

        fun startWithExercise(context: Context, exercise: Exercise): Intent {
            return Intent(context, TimerService::class.java).apply {
                val bundle = Bundle()
                bundle.putParcelable(EXTRA_EXERCISE, exercise)
                putExtra(EXTRA_BUNDLE, bundle)
            }
        }

        fun startWithPlan(context: Context, plan: ExercisePlan): Intent {
            return Intent(context, TimerService::class.java).apply {
                val bundle = Bundle()
                bundle.putParcelable(EXTRA_EXERCISE_PLAN, plan)
                putExtra(EXTRA_BUNDLE, bundle)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.language = Locale.getDefault()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getBundleExtra(EXTRA_BUNDLE)?.let { bundle ->
            bundle.getParcelable<Exercise>(EXTRA_EXERCISE)?.let { startTimerWithExercise(it) }
            bundle.getParcelable<ExercisePlan>(EXTRA_EXERCISE_PLAN)?.let { startTimerWithPlan(it) }
        }
        return START_STICKY
    }

    fun setTimerListener(listener: TimerListener?) {
        timerListener = listener
    }

    fun startTimerWithExercise(ex: Exercise) {
        exercisePlan = null
        planExercises = listOf(ex)
        currentExerciseIndex = 0
        planElapsedSeconds = 0
        loadCurrentExercise()
    }

    fun startTimerWithPlan(plan: ExercisePlan) {
        exercisePlan = plan
        planExercises = Repository.getExercisesForPlan(plan)
        currentExerciseIndex = 0
        planElapsedSeconds = 0
        loadCurrentExercise()
    }

    private fun loadCurrentExercise() {
        val ex = planExercises.getOrNull(currentExerciseIndex) ?: return finishTimer()
        steps = buildSteps(ex)
        stepIndex = 0
        currentRound = 1
        totalRounds = if (ex.useCustomIntervals) ex.rounds else 1
        sessionStartElapsed = planElapsedSeconds
        runStep()
    }

    private fun buildSteps(ex: Exercise): List<TimerStep> {
        val list = mutableListOf<TimerStep>()
        if (ex.useCustomIntervals && ex.customSequence.isNotEmpty()) {
            list.add(TimerStep(TimerPhase.PREPARE, minOf(ex.prepareTime, 5).coerceAtLeast(3), "Prepárate"))
            for (round in 1..ex.rounds) {
                for (interval in ex.customSequence) {
                    val phase = when (interval.phaseType) {
                        PhaseType.PREPARE -> TimerPhase.PREPARE
                        PhaseType.WORK -> TimerPhase.WORK
                        PhaseType.REST -> TimerPhase.REST
                        PhaseType.COOL_DOWN -> TimerPhase.COOL_DOWN
                        PhaseType.TRANSITION -> TimerPhase.TRANSITION
                    }
                    list.add(
                        TimerStep(
                            phase = phase,
                            duration = interval.time,
                            label = interval.name.ifBlank { defaultLabel(phase) },
                            repetitions = interval.repetitions,
                            mediaUri = interval.mediaUri,
                            mediaType = interval.mediaType
                        )
                    )
                }
            }
        } else {
            if (ex.prepareTime > 0) list.add(TimerStep(TimerPhase.PREPARE, ex.prepareTime, defaultLabel(TimerPhase.PREPARE)))
            for (i in 1..ex.iterations) {
                list.add(TimerStep(TimerPhase.WORK, ex.workTime, defaultLabel(TimerPhase.WORK)))
                if (ex.restTime > 0 && i < ex.iterations) {
                    list.add(TimerStep(TimerPhase.REST, ex.restTime, defaultLabel(TimerPhase.REST)))
                }
            }
            if (ex.coolDownTime > 0) list.add(TimerStep(TimerPhase.COOL_DOWN, ex.coolDownTime, defaultLabel(TimerPhase.COOL_DOWN)))
        }
        return list
    }

    private fun defaultLabel(phase: TimerPhase) = when (phase) {
        TimerPhase.PREPARE -> "Prepárate"
        TimerPhase.WORK -> "Work"
        TimerPhase.REST -> "Rest"
        TimerPhase.COOL_DOWN -> "Cooldown"
        TimerPhase.TRANSITION -> "Get ready"
        else -> ""
    }

    private fun runStep() {
        val step = steps.getOrNull(stepIndex) ?: return onExerciseFinished()
        currentPhase = step.phase
        isRunning = true
        isPaused = false
        remainingTime = step.duration
        notifyState()
        announcePhase(step)
        startCountDown(step.duration)
        updateNotification(step)
    }

    private fun announcePhase(step: TimerStep) {
        if (!AppPrefs.voiceAssistantEnabled) return
        val text = when (step.phase) {
            TimerPhase.WORK -> "Trabajo"
            TimerPhase.REST -> "Descanso"
            TimerPhase.PREPARE -> "Prepárate"
            TimerPhase.COOL_DOWN -> "Enfriamiento"
            else -> step.label
        }
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "phase")
    }

    private fun startCountDown(seconds: Int) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTime = ((millisUntilFinished + 999) / 1000).toInt()
                timerListener?.onTimerTick(remainingTime)
                notifyState()
                if (AppPrefs.vibrationEnabled) vibrateOnCountdown(remainingTime)
                if (AppPrefs.voiceAssistantEnabled && remainingTime in 1..3) {
                    if (ttsReady) tts?.speak(remainingTime.toString(), TextToSpeech.QUEUE_ADD, null, "count")
                }
            }

            override fun onFinish() {
                remainingTime = 0
                planElapsedSeconds += steps.getOrNull(stepIndex)?.duration ?: 0
                stepIndex++
                if (stepIndex < steps.size) {
                    val prevRound = currentRound
                    updateRoundIfNeeded()
                    runStep()
                } else {
                    onExerciseFinished()
                }
            }
        }.start()
    }

    private fun updateRoundIfNeeded() {
        val ex = getCurrentExercise() ?: return
        if (ex.useCustomIntervals && ex.customSequence.isNotEmpty()) {
            val introSteps = 1 // paso de "prepárate" inicial
            val perRound = ex.customSequence.size
            val idxIntoRounds = stepIndex - introSteps
            if (idxIntoRounds >= 0) {
                currentRound = (idxIntoRounds / perRound) + 1
            }
        }
    }

    private fun onExerciseFinished() {
        logSession()
        if (currentExerciseIndex < planExercises.size - 1) {
            currentPhase = TimerPhase.TRANSITION
            notifyState()
            currentExerciseIndex++
            loadCurrentExercise()
        } else {
            finishTimer()
        }
    }

    private fun logSession() {
        val ex = getCurrentExercise() ?: return
        val duration = planElapsedSeconds - sessionStartElapsed
        if (duration <= 0) return
        Repository.addSession(
            WorkoutSession(
                exerciseId = ex.id,
                exerciseName = ex.name,
                tags = ex.tags,
                durationSeconds = duration,
                calories = ((duration / 60.0) * AppPrefs.caloriesPerMinute).toInt()
            )
        )
    }

    fun getCurrentExercise(): Exercise? = planExercises.getOrNull(currentExerciseIndex)

    // ---------- Controles de transporte ----------

    fun pauseTimer() {
        if (isRunning && !isPaused) {
            countDownTimer?.cancel()
            isPaused = true
            isRunning = false
            notifyState()
        }
    }

    fun resumeTimer() {
        if (isPaused) {
            isPaused = false
            isRunning = true
            startCountDown(remainingTime)
            notifyState()
        }
    }

    fun skipPhase() {
        countDownTimer?.cancel()
        planElapsedSeconds += (steps.getOrNull(stepIndex)?.duration ?: 0) - remainingTime
        remainingTime = 0
        stepIndex++
        if (stepIndex < steps.size) {
            updateRoundIfNeeded()
            runStep()
        } else {
            onExerciseFinished()
        }
    }

    /** Reinicia el ejercicio actual desde el principio. */
    fun restartExercise() {
        countDownTimer?.cancel()
        stepIndex = 0
        currentRound = 1
        planElapsedSeconds = sessionStartElapsed
        runStep()
    }

    /**
     * Retrocede un bloque/intervalo dentro del ejercicio actual. Si ya estamos en el
     * primer bloque, retrocede al ejercicio anterior del plan (si existe).
     */
    fun previousStep() {
        countDownTimer?.cancel()
        if (stepIndex > 0) {
            stepIndex--
            planElapsedSeconds = (planElapsedSeconds - (steps.getOrNull(stepIndex)?.duration ?: 0)).coerceAtLeast(0)
            updateRoundIfNeeded()
            runStep()
        } else {
            previousExercise()
        }
    }

    /**
     * Avanza un bloque/intervalo dentro del ejercicio actual. Si ya estamos en el
     * último bloque, avanza al siguiente ejercicio del plan (o termina la sesión).
     */
    fun nextStep() {
        countDownTimer?.cancel()
        planElapsedSeconds += (steps.getOrNull(stepIndex)?.duration ?: 0) - remainingTime
        stepIndex++
        if (stepIndex < steps.size) {
            updateRoundIfNeeded()
            runStep()
        } else {
            onExerciseFinished()
        }
    }

    /** Salta al ejercicio anterior del plan. */
    fun previousExercise() {
        if (currentExerciseIndex > 0) {
            countDownTimer?.cancel()
            currentExerciseIndex--
            planElapsedSeconds = 0
            loadCurrentExercise()
        } else {
            restartExercise()
        }
    }

    /** Salta al siguiente ejercicio del plan. */
    fun nextExercise() {
        countDownTimer?.cancel()
        if (currentExerciseIndex < planExercises.size - 1) {
            currentExerciseIndex++
            planElapsedSeconds = 0
            loadCurrentExercise()
        } else {
            finishTimer()
        }
    }

    fun skipToFirstExercise() {
        countDownTimer?.cancel()
        currentExerciseIndex = 0
        planElapsedSeconds = 0
        loadCurrentExercise()
    }

    fun skipToLastExercise() {
        countDownTimer?.cancel()
        currentExerciseIndex = (planExercises.size - 1).coerceAtLeast(0)
        planElapsedSeconds = 0
        loadCurrentExercise()
    }

    fun stopTimer() {
        countDownTimer?.cancel()
        currentPhase = TimerPhase.IDLE
        isRunning = false
        isPaused = false
        notifyState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishTimer() {
        currentPhase = TimerPhase.FINISHED
        isRunning = false
        timerListener?.onTimerFinished()
        notifyState()
        if (AppPrefs.vibrationEnabled) vibrate(longArrayOf(0, 300, 100, 300, 100, 300))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun getTimerState(): TimerState {
        val ex = getCurrentExercise()
        val step = steps.getOrNull(stepIndex)
        val nextEx = planExercises.getOrNull(currentExerciseIndex + 1)

        val stepMediaUri = step?.mediaUri
        val mediaUri = if (!stepMediaUri.isNullOrBlank()) stepMediaUri else ex?.mediaUri
        val mediaType = if (!stepMediaUri.isNullOrBlank()) step.mediaType else (ex?.mediaType ?: MediaType.NONE)

        val hasPrev = stepIndex > 0 || currentExerciseIndex > 0
        val hasNext = stepIndex < steps.size - 1 || currentExerciseIndex < planExercises.size - 1

        return TimerState(
            phase = currentPhase,
            stepLabel = step?.label ?: "",
            currentTime = remainingTime,
            totalTime = step?.duration ?: 0,
            currentRound = currentRound,
            totalRounds = totalRounds,
            currentExerciseName = ex?.name ?: "",
            nextExerciseName = nextEx?.name,
            isPaused = isPaused,
            isRunning = isRunning,
            planName = exercisePlan?.name,
            planIndex = currentExerciseIndex,
            planSize = planExercises.size,
            planElapsed = planElapsedSeconds,
            exerciseTotalTime = ex?.getTotalTime() ?: 0,
            mediaUri = mediaUri,
            mediaType = mediaType,
            hasPreviousStep = hasPrev,
            hasNextStep = hasNext
        )
    }

    private fun notifyState() {
        timerListener?.onTimerStateChanged(getTimerState())
    }

    private fun vibrateOnCountdown(seconds: Int) {
        if (seconds in 1..3) vibrate(longArrayOf(0, 100))
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GymFitness::TimerWakeLock")
        wakeLock?.acquire(60 * 60 * 1000L)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Timer", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateNotification(step: TimerStep) {
        val ex = getCurrentExercise()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(ex?.name ?: getString(R.string.app_name))
            .setContentText("${step.label} · ${step.duration}s")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        wakeLock?.release()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
