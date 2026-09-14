package com.fitnnes.gym.timer

import android.app.*
import android.content.*
import android.os.*
import android.speech.tts.TextToSpeech
import android.util.Log
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
    private var youtubeSafetyTimer: CountDownTimer? = null
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
        private const val TAG = "TimerService"
        const val EXTRA_EXERCISE_ID = "EXTRA_EXERCISE_ID"
        const val EXTRA_EXERCISE_PLAN_ID = "EXTRA_EXERCISE_PLAN_ID"
        const val EXTRA_BUNDLE = "EXTRA_BUNDLE"

        // Red de seguridad MUY generosa para intervalos con video de YouTube: nunca
        // debería dispararse con un video real (aunque dure una hora), solo evita que
        // el entrenamiento quede trabado para siempre si el bridge JS falla del todo.
        private const val YOUTUBE_SAFETY_TIMEOUT_MS = 3 * 60 * 60 * 1000L // 3 horas

        // FIX: antes se mandaba el Exercise/ExercisePlan COMPLETO (Parcelable, con las
        // imágenes en base64 embebidas) por el Intent. Con varios intervalos con imagen
        // eso supera el límite del buffer de transacciones Binder (~1MB) y tira
        // TransactionTooLargeException -> el service ni arranca, sin crash visible, y
        // el entrenamiento "no inicia". Ahora solo viaja el id (String, liviano) y acá
        // abajo se resuelve el objeto completo contra el Repository en memoria (mismo
        // proceso, sin Binder de por medio).
        fun startWithExercise(context: Context, exerciseId: String): Intent {
            return Intent(context, TimerService::class.java).apply {
                val bundle = Bundle()
                bundle.putString(EXTRA_EXERCISE_ID, exerciseId)
                putExtra(EXTRA_BUNDLE, bundle)
            }
        }

        fun startWithPlan(context: Context, planId: String): Intent {
            return Intent(context, TimerService::class.java).apply {
                val bundle = Bundle()
                bundle.putString(EXTRA_EXERCISE_PLAN_ID, planId)
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
            bundle.getString(EXTRA_EXERCISE_ID)?.let { id ->
                val ex = Repository.getExercise(id)
                if (ex != null) {
                    startTimerWithExercise(ex)
                } else {
                    Log.e(TAG, "No se encontró el ejercicio id=$id en el Repository")
                    stopSelf()
                }
            }
            bundle.getString(EXTRA_EXERCISE_PLAN_ID)?.let { id ->
                val plan = Repository.getPlan(id)
                if (plan != null) {
                    startTimerWithPlan(plan)
                } else {
                    Log.e(TAG, "No se encontró el plan id=$id en el Repository")
                    stopSelf()
                }
            }
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
        startCountDown(step.duration, step.mediaType)
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

    private fun startCountDown(seconds: Int, mediaType: MediaType = MediaType.NONE) {
        countDownTimer?.cancel()
        youtubeSafetyTimer?.cancel()
        val isYoutubeStep = mediaType == MediaType.YOUTUBE
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
                notifyState()
                if (isYoutubeStep) {
                    // No avanzamos solos: el video de YouTube manda. Esperamos el aviso
                    // real de fin de video (onYoutubeVideoEnded, desde TimerActivity) o,
                    // si el bridge falla del todo, la red de seguridad de 3 horas.
                    Log.d(TAG, "Paso YOUTUBE llegó a 00:00, esperando fin real del video")
                    startYoutubeSafetyTimer()
                } else {
                    advanceStep()
                }
            }
        }.start()
    }

    /** Red de seguridad ante fallas del bridge JS de YouTube (ver companion). */
    private fun startYoutubeSafetyTimer() {
        youtubeSafetyTimer?.cancel()
        youtubeSafetyTimer = object : CountDownTimer(YOUTUBE_SAFETY_TIMEOUT_MS, YOUTUBE_SAFETY_TIMEOUT_MS) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                Log.w(TAG, "Timeout de seguridad de YouTube alcanzado, avanzando igual")
                advanceStep()
            }
        }.start()
    }

    /**
     * Llamado desde TimerActivity cuando el bridge JS del WebView detecta que el video
     * de YouTube terminó de verdad. Si el video termina antes de que se acabe el tiempo
     * mostrado, corta el countdown ahí mismo; si ya estaba esperando (countdown en 0),
     * avanza directo.
     */
    fun onYoutubeVideoEnded() {
        youtubeSafetyTimer?.cancel()
        countDownTimer?.cancel()
        advanceStep()
    }

    /** Avanza al siguiente paso/intervalo, o termina el ejercicio si no queda ninguno. */
    private fun advanceStep() {
        countDownTimer?.cancel()
        youtubeSafetyTimer?.cancel()
        planElapsedSeconds += steps.getOrNull(stepIndex)?.duration ?: 0
        stepIndex++
        if (stepIndex < steps.size) {
            updateRoundIfNeeded()
            runStep()
        } else {
            onExerciseFinished()
        }
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
            youtubeSafetyTimer?.cancel()
            isPaused = true
            isRunning = false
            notifyState()
        }
    }

    fun resumeTimer() {
        if (isPaused) {
            isPaused = false
            isRunning = true
            val currentMediaType = steps.getOrNull(stepIndex)?.mediaType ?: MediaType.NONE
            startCountDown(remainingTime, currentMediaType)
            notifyState()
        }
    }

    fun skipPhase() {
        countDownTimer?.cancel()
        youtubeSafetyTimer?.cancel()
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
        youtubeSafetyTimer?.cancel()
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
        youtubeSafetyTimer?.cancel()
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
        youtubeSafetyTimer?.cancel()
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
            youtubeSafetyTimer?.cancel()
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
        youtubeSafetyTimer?.cancel()
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
        youtubeSafetyTimer?.cancel()
        currentExerciseIndex = 0
        planElapsedSeconds = 0
        loadCurrentExercise()
    }

    fun skipToLastExercise() {
        countDownTimer?.cancel()
        youtubeSafetyTimer?.cancel()
        currentExerciseIndex = (planExercises.size - 1).coerceAtLeast(0)
        planElapsedSeconds = 0
        loadCurrentExercise()
    }

    fun stopTimer() {
        countDownTimer?.cancel()
        youtubeSafetyTimer?.cancel()
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
        youtubeSafetyTimer?.cancel()
        wakeLock?.release()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
