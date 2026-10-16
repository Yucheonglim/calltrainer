package com.company.calltrainer

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// 1. 심플하고 깔끔한 보안 콜(Secure Line) 설정 화면 (튕김 원인 100% 제거!)
class MainActivity : Activity() {

    private lateinit var nameInput: EditText
    private lateinit var numberInput: EditText
    private val PICK_CONTACT_REQUEST = 1001
    private val callHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            setText("01098765432")
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
                val number = numberInput.text.toString().ifBlank { "01000000000" }

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
                    val intent = Intent(this@MainActivity, IncomingCallActivity::class.java).apply {
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
                    // 하이픈(-)을 자동으로 제거하여 깔끔하게 입력
                    numberInput.setText(rawNumber.replace(Regex("[^0-9+]"), ""))
                }
            }
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

// 2. 단색 배경이 아닌 '최신 갤럭시 실제 통화 그라데이션 화면'을 100% 똑같이 띄우는 화면
class IncomingCallActivity : Activity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private var seconds = 0
    private lateinit var callStateText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 화면이 꺼져 있거나 잠겨 있어도 즉시 화면을 켜고 띄우기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val callerName = intent.getStringExtra("CALLER_NAME") ?: "Unknown"
        val callerNumber = intent.getStringExtra("CALLER_NUMBER") ?: "01000000000"

        // [단색 해결!] 최신 갤럭시 One UI 특유의 고급스러운 3색 오로라 그라데이션 배경 적용
        val galaxyGradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#1A2980"), // 깊은 네이비 블루
                Color.parseColor("#26D0CE"), // 갤럭시 시그니처 오로라 민트/블루
                Color.parseColor("#141E30")  // 다크 블루
            )
        )

        val rootLayout = RelativeLayout(this).apply {
            background = galaxyGradient
            setPadding(60, 140, 60, 140)
        }

        // 상단 통화 정보 영역 (HD Voice + 발신자 이름 + 하이픈 없는 깔끔한 번호)
        val topBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_TOP) }
            layoutParams = params
        }

        callStateText = TextView(this).apply {
            text = "HD Voice 수신 전화"
            textSize = 15f
            setTextColor(Color.parseColor("#E0F7FA"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 35)
        }

        val nameText = TextView(this).apply {
            text = callerName
            textSize = 34f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 15)
        }

        val numberText = TextView(this).apply {
            text = "휴대전화  $callerNumber"
            textSize = 18f
            setTextColor(Color.parseColor("#ECEFF1"))
            gravity = Gravity.CENTER
        }

        topBox.addView(callStateText)
        topBox.addView(nameText)
        topBox.addView(numberText)
        rootLayout.addView(topBox)

        // 하단 갤럭시 원형 통화/거절 버튼 영역
        val bottomBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) }
            layoutParams = params
        }

        // 초록색 통화 받기 원형 버튼
        val answerBtn = Button(this).apply {
            text = "📞"
            textSize = 28f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00C853"))
            }
            val params = LinearLayout.LayoutParams(210, 210).apply {
                setMargins(0, 0, 180, 0)
            }
            layoutParams = params

            setOnClickListener {
                stopRingtoneAndVibration()
                visibility = View.GONE // 통화 받으면 초록 버튼 숨기고 빨간 종료 버튼만 중앙에 남김
                val endParams = LinearLayout.LayoutParams(210, 210)
                bottomBox.getChildAt(1).layoutParams = endParams
                startCallTimer()
            }
        }

        // 빨간색 통화 거절/종료 원형 버튼
        val rejectBtn = Button(this).apply {
            text = "✖"
            textSize = 26f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF1744"))
            }
            val params = LinearLayout.LayoutParams(210, 210)
            layoutParams = params

            setOnClickListener {
                stopRingtoneAndVibration()
                timerHandler.removeCallbacksAndMessages(null)
                finish()
            }
        }

        bottomBox.addView(answerBtn)
        bottomBox.addView(rejectBtn)
        rootLayout.addView(bottomBox)

        setContentView(rootLayout)

        // 내 갤럭시 폰의 원래 기본 벨소리와 진동 울리기!
        startRingtoneAndVibration()
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

    private fun startCallTimer() {
        seconds = 0
        timerHandler.post(object : Runnable {
            override fun run() {
                val mins = seconds / 60
                val secs = seconds % 60
                callStateText.text = String.format("HD Voice  %02d:%02d", mins, secs)
                seconds++
                timerHandler.postDelayed(this, 1000)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtoneAndVibration()
        timerHandler.removeCallbacksAndMessages(null)
    }
}
