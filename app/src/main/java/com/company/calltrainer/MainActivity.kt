package com.company.calltrainer

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.hypot

class MainActivity : Activity() {

    private lateinit var nameInput: EditText
    private lateinit var numberInput: EditText
    private val PICK_CONTACT_REQUEST = 1001
    private val callHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3000)
            }
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F6F9"))
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 80, 50, 80)
        }

        // 1. 보안 콜 영문 제목
        val titleText = TextView(this).apply {
            text = "🔒 Team Call : Secure Line"
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A1A1A"))
            setPadding(10, 0, 0, 50)
        }
        mainLayout.addView(titleText)

        // 2. 연락처 불러오기 및 수동 입력
        val step1Card = createCardLayout()

        val contactBtn = Button(this).apply {
            text = "📒 연락처 불러오기"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1565C0"))
            background = createRoundedBackground("#E3F2FD", 24f)
            setPadding(20, 30, 20, 30)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 25) }
            layoutParams = params

            setOnClickListener {
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                startActivityForResult(intent, PICK_CONTACT_REQUEST)
            }
        }

        nameInput = EditText(this).apply {
            hint = "이름 입력"
            setText("대한건설 김철수 소장")
            textSize = 16f
            setPadding(35, 30, 35, 30)
            background = createRoundedBackground("#F8F9FA", 16f)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 20) }
            layoutParams = params
        }

        numberInput = EditText(this).apply {
            hint = "전화번호 입력"
            setText("010-9876-5432")
            textSize = 16f
            setPadding(35, 30, 35, 30)
            background = createRoundedBackground("#F8F9FA", 16f)
        }

        step1Card.addView(contactBtn)
        step1Card.addView(nameInput)
        step1Card.addView(numberInput)
        mainLayout.addView(step1Card)

        // 3. 시간 선택 버튼 (5초 / 15초 / 1분 / 5분)
        val step2Card = createCardLayout()

        val timeRadioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER
        }

        timeRadioGroup.addView(createSimpleRadioButton(101, "5초", true))
        timeRadioGroup.addView(createSimpleRadioButton(102, "15초", false))
        timeRadioGroup.addView(createSimpleRadioButton(103, "1분", false))
        timeRadioGroup.addView(createSimpleRadioButton(104, "5분", false))
        step2Card.addView(timeRadioGroup)
        mainLayout.addView(step2Card)

        // 4. 보안 전화 실행 영문 버튼
        val step3Card = createCardLayout()

        val startCallBtn = Button(this).apply {
            text = "START SECURE CALL"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = createRoundedBackground("#1976D2", 28f)
            setPadding(20, 40, 20, 40)

            setOnClickListener {
                val name = nameInput.text.toString().ifBlank { "Unknown" }
                val number = formatPhoneNumber(numberInput.text.toString().ifBlank { "010-0000-0000" })

                val (delayMillis, timeLabel) = when (timeRadioGroup.checkedRadioButtonId) {
                    101 -> Pair(5000L, "5초")
                    102 -> Pair(15000L, "15초")
                    103 -> Pair(60000L, "1분")
                    104 -> Pair(300000L, "5분")
                    else -> Pair(5000L, "5초")
                }

                callHandler.removeCallbacksAndMessages(null)
                Toast.makeText(context, "$timeLabel 뒤 연결됩니다.", Toast.LENGTH_SHORT).show()

                callHandler.postDelayed({
                    val intent = Intent(this@MainActivity, GalaxyCallScreenActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("CALLER_NAME", name)
                        putExtra("CALLER_NUMBER", number)
                    }
                    startActivity(intent)
                }, delayMillis)
            }
        }

        step3Card.addView(startCallBtn)
        mainLayout.addView(step3Card)

        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
            val contactUri: Uri = data?.data ?: return
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val selectedName = if (nameIndex >= 0) cursor.getString(nameIndex) else ""
                    val rawNumber = if (numberIndex >= 0) cursor.getString(numberIndex) else ""

                    nameInput.setText(selectedName)
                    numberInput.setText(formatPhoneNumber(rawNumber))
                }
            }
        }
    }

    private fun formatPhoneNumber(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return if (digits.length == 11) {
            "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7, 11)}"
        } else if (digits.length == 10) {
            "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6, 10)}"
        } else {
            raw
        }
    }

    private fun createSimpleRadioButton(btnId: Int, label: String, checked: Boolean): RadioButton {
        return RadioButton(this).apply {
            id = btnId
            text = label
            textSize = 15f
            isChecked = checked
            val params = RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = params
        }
    }

    private fun createCardLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 45, 45, 45)
            background = createRoundedBackground("#FFFFFF", 32f)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 35) }
            layoutParams = params
            elevation = 4f
        }
    }

    private fun createRoundedBackground(hexColor: String, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(hexColor))
            cornerRadius = radius
        }
    }
}

// 최신 갤럭시 폴드8 / One UI 7 순정 통화 화면을 정밀 복제한 화면 (보안 경고 0%, 워치 연동 100%)
class GalaxyCallScreenActivity : Activity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private var seconds = 0
    private lateinit var subStatusText: TextView
    private lateinit var incomingBottomLayout: RelativeLayout
    private lateinit var activeCallPanel: LinearLayout
    private val WATCH_NOTIF_ID = 8888
    private val CHANNEL_ID = "galaxy_watch_ring"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val callerName = intent.getStringExtra("CALLER_NAME") ?: "대한건설 김철수 소장"
        val callerNumber = intent.getStringExtra("CALLER_NUMBER") ?: "010-9876-5432"

        // 최신 갤럭시 One UI 순정 통화 배경 (깊고 세련된 삼성 순정 오로라 다크블루-틸 그라데이션)
        val oneUiBackground = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#0F2027"),
                Color.parseColor("#203A43"),
                Color.parseColor("#2C5364")
            )
        )

        val root = RelativeLayout(this).apply {
            background = oneUiBackground
        }

        // [상단 영역] 이름은 대문짝만하게 크게(34pt), 전화번호는 작게(17pt)!
        val topInfoBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 180, 40, 0)
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_TOP) }
            layoutParams = params
        }

        subStatusText = TextView(this).apply {
            text = "수신 전화"
            textSize = 15f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 25)
        }

        // 1. 발신자 이름 (가장 크게!)
        val bigNameText = TextView(this).apply {
            text = callerName
            textSize = 34f
            setTypeface(Typeface.create("sans-serif-light", Typeface.BOLD))
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        // 2. 전화번호 (그 아래 작고 깔끔하게!)
        val smallNumberText = TextView(this).apply {
            text = "휴대전화  $callerNumber"
            textSize = 17f
            setTextColor(Color.parseColor("#CFD8DC"))
            gravity = Gravity.CENTER
        }

        topInfoBox.addView(subStatusText)
        topInfoBox.addView(bigNameText)
        topInfoBox.addView(smallNumberText)
        root.addView(topInfoBox)

        // [하단 수신 영역] 갤럭시 순정 스와이프(밀어서 받기) 수화기 원형 버튼
        incomingBottomLayout = RelativeLayout(this).apply {
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                550
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins(80, 0, 80, 120)
            }
            layoutParams = params
        }

        val guideHint = TextView(this).apply {
            text = "아이콘을 바깥쪽으로 밀어 통화 또는 거절"
            textSize = 13f
            setTextColor(Color.parseColor("#90A4AE"))
            gravity = Gravity.CENTER
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_TOP) }
            layoutParams = params
        }
        incomingBottomLayout.addView(guideHint)

        // 왼쪽 아래: 초록색 순정 통화 스와이프 버튼
        val answerSwipeBtn = createGalaxyCallButton(isAnswer = true) {
            onCallAnswered()
        }
        val answerParams = RelativeLayout.LayoutParams(240, 240).apply {
            addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        }
        incomingBottomLayout.addView(answerSwipeBtn, answerParams)

        // 오른쪽 아래: 빨간색 순정 거절 스와이프 버튼
        val rejectSwipeBtn = createGalaxyCallButton(isAnswer = false) {
            endCallAndClose()
        }
        val rejectParams = RelativeLayout.LayoutParams(240, 240).apply {
            addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        }
        incomingBottomLayout.addView(rejectSwipeBtn, rejectParams)

        root.addView(incomingBottomLayout)

        // [통화 중 하단 메뉴 패널] 전화를 받으면 나타나는 갤럭시 순정 6버튼 + 빨간 종료 버튼
        activeCallPanel = createActiveCallPanel()
        activeCallPanel.visibility = View.GONE
        root.addView(activeCallPanel)

        setContentView(root)

        startRingtoneAndVibration()
        sendWatchAlert(callerName, callerNumber)
    }

    // 손가락으로 밀어서(스와이프) 받거나 터치할 때 하얀 물결 원이 커지는 갤럭시 순정 버튼 구현
    private fun createGalaxyCallButton(isAnswer: Boolean, onTrigger: () -> Unit): View {
        return object : View(this) {
            private var touchRadius = 0f
            private var startX = 0f
            private var startY = 0f
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isAnswer) Color.parseColor("#00C853") else Color.parseColor("#FF1744")
                style = Paint.Style.FILL
            }
            private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#40FFFFFF")
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 70f
                textAlign = Paint.Align.CENTER
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                if (touchRadius > 0f) {
                    canvas.drawCircle(cx, cy, touchRadius.coerceAtMost(cx), ripplePaint)
                }
                canvas.drawCircle(cx, cy, 95f, bgPaint)
                val symbol = if (isAnswer) "✆" else "✕"
                val yOffset = (iconPaint.descent() + iconPaint.ascent()) / 2
                canvas.drawText(symbol, cx, cy - yOffset, iconPaint)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.x
                        startY = event.y
                        touchRadius = 115f
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dist = hypot((event.x - startX).toDouble(), (event.y - startY).toDouble()).toFloat()
                        touchRadius = 95f + dist
                        invalidate()
                        if (dist > 70f) {
                            touchRadius = 0f
                            onTrigger()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        touchRadius = 0f
                        invalidate()
                        onTrigger()
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
        }
    }

    // 전화를 받았을 때 실제 갤럭시 통화 화면처럼 바뀌는 하단 제어판 (녹음, 키패드, 스피커 등 표시)
    private fun createActiveCallPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33000000"))
                cornerRadius = 60f
            }
            setPadding(50, 70, 50, 90)
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins(40, 0, 40, 80)
            }
            layoutParams = params

            val row1 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 50)
                addView(createMenuItem("녹음"))
                addView(createMenuItem("음소거"))
                addView(createMenuItem("스피커"))
            }
            val row2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 70)
                addView(createMenuItem("블루투스"))
                addView(createMenuItem("키패드"))
                addView(createMenuItem("통화 추가"))
            }

            val endCallBtn = Button(context).apply {
                text = "✕"
                textSize = 26f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#FF1744"))
                }
                layoutParams = LinearLayout.LayoutParams(200, 200)
                setOnClickListener { endCallAndClose() }
            }

            addView(row1)
            addView(row2)
            addView(endCallBtn)
        }
    }

    private fun createMenuItem(label: String): TextView {
        return TextView(this).apply {
            text = "●\n$label"
            textSize = 13f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    private fun onCallAnswered() {
        stopRingtoneAndVibration()
        cancelWatchAlert()
        incomingBottomLayout.visibility = View.GONE
        activeCallPanel.visibility = View.VISIBLE

        seconds = 0
        timerHandler.post(object : Runnable {
            override fun run() {
                val mins = seconds / 60
                val secs = seconds % 60
                subStatusText.text = String.format("HD Voice  %02d:%02d", mins, secs)
                subStatusText.setTextColor(Color.parseColor("#81C784"))
                seconds++
                timerHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun endCallAndClose() {
        stopRingtoneAndVibration()
        cancelWatchAlert()
        timerHandler.removeCallbacksAndMessages(null)
        finish()
    }

    private fun startRingtoneAndVibration() {
        try {
            val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone?.play()
        } catch (e: Exception) {}

        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {}
    }

    private fun stopRingtoneAndVibration() {
        try { ringtone?.stop() } catch (e: Exception) {}
        try { vibrator?.cancel() } catch (e: Exception) {}
    }

    private fun sendWatchAlert(name: String, number: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Watch Call Sync",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { enableVibration(true) }
                nm.createNotificationChannel(channel)
            }
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
            builder.setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle("📞 $name")
                .setContentText(number)
                .setAutoCancel(true)
            nm.notify(WATCH_NOTIF_ID, builder.build())
        } catch (e: Exception) {}
    }

    private fun cancelWatchAlert() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(WATCH_NOTIF_ID)
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtoneAndVibration()
        cancelWatchAlert()
        timerHandler.removeCallbacksAndMessages(null)
    }
}
