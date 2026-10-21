package com.company.calltrainer

import android.app.Activity
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var telecomManager: TelecomManager
    private lateinit var phoneAccountHandle: PhoneAccountHandle
    private lateinit var nameInput: EditText
    private lateinit var numberInput: EditText
    private lateinit var promptInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var statusBox: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var aiConfigBox: LinearLayout
    private var selectedDelayMs: Long = 5000L
    private var useAiMode: Boolean = false
    private var selectedVoice: String = "onyx"
    private var selectedSpeed: Double = 1.0
    private val PICK_CONTACT_REQUEST = 1001
    private val AUTH_REQUEST_CODE = 5555
    private var isUnlocked = false
    private lateinit var mainScrollView: ScrollView
    private val uiHandler = Handler(Looper.getMainLooper())
    private var targetRingTimeMs: Long = 0L

    private val voiceButtons = mutableListOf<Button>()
    private val presetButtons = mutableListOf<Button>()

    private val countdownRunnable = object : Runnable {
        override fun run() {
            val remainMs = targetRingTimeMs - System.currentTimeMillis()
            if (remainMs > 0) {
                val totalSec = (remainMs / 1000) + 1
                val min = totalSec / 60
                val sec = totalSec % 60
                val timeStr = if (min > 0) "${min}분 ${sec}초" else "${sec}초"
                val savedName = getSharedPreferences("TeamCallPrefs", Context.MODE_PRIVATE).getString("TARGET_NAME", "")
                val modeStr = if (useAiMode) "🤖 AI통화($selectedVoice)" else "🔕 벨소리만"
                statusText.text = "⏰ 예약됨: $savedName ($modeStr)\n⏳ 벨 울림까지 남은 시간: $timeStr"
                uiHandler.postDelayed(this, 500)
            } else {
                statusBox.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val perms = mutableListOf(
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(perms.toTypedArray(), 3000)

        telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val componentName = ComponentName(this, FakeCallService::class.java)
        phoneAccountHandle = PhoneAccountHandle(componentName, "HD_Voice_Trainer")

        try {
            val builder = PhoneAccount.builder(phoneAccountHandle, "HD Voice")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            telecomManager.registerPhoneAccount(builder.build())
        } catch (e: Exception) {}

        val prefs = getSharedPreferences("TeamCallPrefs", Context.MODE_PRIVATE)

        mainScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F6F9"))
            isFillViewport = true
            visibility = View.INVISIBLE
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 60, 50, 60)
        }

        root.addView(TextView(this).apply {
            text = "🔒 Team Call : Secure Line"
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A237E"))
            setPadding(0, 0, 0, 35)
        })

        val card1 = makeCard()
        card1.addView(Button(this).apply {
            text = "📒 내 연락처에서 상대방 불러오기"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1565C0"))
            background = makeRoundBg("#E3F2FD", 24f)
            setPadding(20, 30, 20, 30)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                startActivityForResult(intent, PICK_CONTACT_REQUEST)
            }
        })
        nameInput = EditText(this).apply {
            hint = "발신자 이름"
            setText("대한건설 김철수 소장")
            textSize = 16f
            setPadding(35, 30, 35, 30)
            background = makeRoundBg("#F1F3F5", 20f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 25, 0, 15) }
        }
        card1.addView(nameInput)
        numberInput = EditText(this).apply {
            hint = "전화번호"
            setText("010-9876-5432")
            textSize = 16f
            setPadding(35, 30, 35, 30)
            background = makeRoundBg("#F1F3F5", 20f)
        }
        card1.addView(numberInput)
        root.addView(card1)

        val card2 = makeCard()
        val rg = RadioGroup(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 4f
        }
        val times = listOf("5초" to 5000L, "15초" to 15000L, "1분" to 60000L, "5분" to 300000L)
        times.forEachIndexed { idx, pair ->
            val rb = RadioButton(this).apply {
                text = pair.first
                textSize = 15f
                id = 101 + idx
                isChecked = (idx == 0)
                layoutParams = RadioGroup.LayoutParams(0, -2, 1f)
            }
            rg.addView(rb)
        }
        rg.setOnCheckedChangeListener { _, id ->
            selectedDelayMs = when (id) {
                101 -> 5000L; 102 -> 15000L; 103 -> 60000L; 104 -> 300000L; else -> 5000L
            }
        }
        card2.addView(rg)
        root.addView(card2)

        val cardAi = makeCard()
        val modeGroup = RadioGroup(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        val rbSilent = RadioButton(this).apply {
            text = "🔕 벨소리만"
            textSize = 15f
            id = 201
            isChecked = true
            layoutParams = RadioGroup.LayoutParams(0, -2, 1f)
        }
        val rbAi = RadioButton(this).apply {
            text = "🤖 AI 음성통화"
            textSize = 15f
            id = 202
            layoutParams = RadioGroup.LayoutParams(0, -2, 1f)
        }
        modeGroup.addView(rbSilent)
        modeGroup.addView(rbAi)
        cardAi.addView(modeGroup)

        aiConfigBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 25, 0, 0)
        }

        aiConfigBox.addView(TextView(this).apply {
            text = "🎙️ 성우 목소리 선택 (남성 3명 / 여성 3명)"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#37474F"))
            setPadding(0, 0, 0, 12)
        })

        val voices = listOf(
            Triple("👔 중후한 남성(임원)", "onyx", 1.0),
            Triple("🏃 젊은 남성(실무자)", "echo", 1.1),
            Triple("👓 차분한 남성(지적)", "alloy", 1.0),
            Triple("👩‍💼 차분한 여성(비서)", "nova", 1.0),
            Triple("👩‍⚖️ 부드러운 여성(대표)", "shimmer", 1.0),
            Triple("⚡ 깐깐한 여성(구매팀)", "fable", 1.12)
        )

        for (row in 0..2) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 12) }
            }
            for (col in 0..1) {
                val idx = row * 2 + col
                val vInfo = voices[idx]
                val btn = Button(this).apply {
                    text = vInfo.first
                    textSize = 12.5f
                    setAllCaps(false)
                    setPadding(10, 22, 10, 22)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (col == 0) setMargins(0, 0, 8, 0) else setMargins(8, 0, 0, 0)
                    }
                    setOnClickListener {
                        selectedVoice = vInfo.second
                        selectedSpeed = vInfo.third
                        updateVoiceSelection(this)
                    }
                }
                voiceButtons.add(btn)
                rowLayout.addView(btn)
            }
            aiConfigBox.addView(rowLayout)
        }
        updateVoiceSelection(voiceButtons[0])

        aiConfigBox.addView(TextView(this).apply {
            text = "🎬 원터치 상황 설정 (누르면 아래 대사가 자동 변경됨)"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#37474F"))
            setPadding(0, 20, 0, 12)
        })

        val presets = listOf(
            "🏗️ 김법무 건설현장 보고" to "대표님, 김법무입니다! 지금 현장 실사 끝났습니다. 일단 보고서에 있던 것 처럼 큰문제는 아닌듯 합니다. 추락방지 레이어를 단단히 고정하지 않아서 빠진것 같고 현장사진으로 설명하면 될듯 합니다. ",
            "🔬 반도체 납기항의" to "안녕하세요? 대표님 마크나칩 김성철 이사입니다. 인수진행을 언제부터 하실지 궁금해서 연락 드립니다.",
            "💼 M&A 긴급협상" to "대표님, 노무라증권 이사입니다. 아까 말씀드린데로 안성건은 대표님이 잘 그려주시고 저희는 총알만 충문히 준비하겠습니다.",
            "😡 회계사" to "대표님, 회계사입니다. 홈플러스 말씀하신것 처럼 움직임이 있습니다. 정말 혜안이~  이젠 존경 합니다.  ",
            "📢 회계사 사무원여" to "대표님, 오늘 건설사 인수의향서 자서문제로 대표들 모임이 있습니다. 아시죠? 혹시나 해서 연락드립니다.  "
        )

        val presetGrid1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 3f }
        val presetGrid2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f; setPadding(0, 10, 0, 15) }

        presets.forEachIndexed { i, pair ->
            val pBtn = Button(this).apply {
                text = pair.first
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#0D47A1"))
                background = makeRoundBg("#E3F2FD", 16f)
                setPadding(8, 20, 8, 20)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(4, 0, 4, 0) }
                setOnClickListener {
                    promptInput.setText(pair.second)
                    Toast.makeText(this@MainActivity, "[${pair.first}] 시나리오가 세팅되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            if (i < 3) presetGrid1.addView(pBtn) else presetGrid2.addView(pBtn)
        }
        aiConfigBox.addView(presetGrid1)
        aiConfigBox.addView(presetGrid2)

        aiConfigBox.addView(TextView(this).apply {
            text = "✏️ AI 첫마디 및 상황 대본 (자유롭게 수정 가능)"
            textSize = 13f
            setTextColor(Color.parseColor("#546E7A"))
            setPadding(0, 5, 0, 8)
        })

        promptInput = EditText(this).apply {
            hint = "AI가 전화를 받자마자 할 첫마디와 상황을 자유롭게 적어주세요."
            setText(prefs.getString("SAVED_PROMPT", presets[2].second))
            textSize = 14f
            minLines = 3
            setPadding(30, 25, 30, 25)
            background = makeRoundBg("#F1F3F5", 20f)
        }
        aiConfigBox.addView(promptInput)

        apiKeyInput = EditText(this).apply {
            hint = "OpenAI API 키 입력 (sk-proj-...)"
            setText(prefs.getString("OPENAI_KEY", ""))
            textSize = 13f
            setPadding(30, 25, 30, 25)
            background = makeRoundBg("#FFF8E1", 20f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 15, 0, 0) }
        }
        aiConfigBox.addView(apiKeyInput)
        cardAi.addView(aiConfigBox)
        root.addView(cardAi)

        modeGroup.setOnCheckedChangeListener { _, id ->
            useAiMode = (id == 202)
            aiConfigBox.visibility = if (useAiMode) View.VISIBLE else View.GONE
        }

        statusBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundBg("#FFF3E0", 24f)
            setPadding(40, 35, 40, 35)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 30) }
        }
        statusText = TextView(this).apply {
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E65100"))
        }
        val cancelBtn = Button(this).apply {
            text = "❌ 예약된 전화 취소하기"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = makeRoundBg("#D32F2F", 16f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 20, 0, 0) }
            setOnClickListener { cancelScheduledCall() }
        }
        statusBox.addView(statusText)
        statusBox.addView(cancelBtn)
        root.addView(statusBox)

        root.addView(Button(this).apply {
            text = "START SECURE CALL"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeRoundBg("#1E88E5", 30f)
            setPadding(20, 45, 20, 45)
            setOnClickListener {
                val n = nameInput.text.toString().ifEmpty { "알 수 없음" }
                val p = numberInput.text.toString().ifEmpty { "010-0000-0000" }
                if (useAiMode && apiKeyInput.text.toString().trim().isEmpty()) {
                    Toast.makeText(this@MainActivity, "AI 통화를 위해 OpenAI API 키를 입력해 주세요!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                prefs.edit()
                    .putString("OPENAI_KEY", apiKeyInput.text.toString().trim())
                    .putString("SAVED_PROMPT", promptInput.text.toString().trim())
                    .putBoolean("USE_AI", useAiMode)
                    .putString("VOICE_TYPE", selectedVoice)
                    .putFloat("VOICE_SPEED", selectedSpeed.toFloat())
                    .apply()
                scheduleExactCall(n, p, selectedDelayMs)
            }
        })

        root.addView(TextView(this).apply {
            text = "전화가 안 된다면 아래 버튼을 눌러서 'HD Voice'를 켜주세요."
            textSize = 13f
            setTextColor(Color.parseColor("#757575"))
            gravity = Gravity.CENTER
            setPadding(0, 60, 0, 15)
        })

        root.addView(Button(this).apply {
            text = "⚙️ 통화 허용 설정"
            textSize = 14f
            background = makeRoundBg("#E0E0E0", 20f)
            setOnClickListener {
                startActivity(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS))
            }
        })

        mainScrollView.addView(root)
        setContentView(mainScrollView)
        checkExistingSchedule()
        checkFingerprintOnce()
    }

    private fun updateVoiceSelection(selectedBtn: Button) {
        voiceButtons.forEach { btn ->
            if (btn == selectedBtn) {
                btn.background = makeRoundBg("#1565C0", 18f)
                btn.setTextColor(Color.WHITE)
                btn.setTypeface(null, Typeface.BOLD)
            } else {
                btn.background = makeRoundBg("#ECEFF1", 18f)
                btn.setTextColor(Color.parseColor("#455A64"))
                btn.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    private fun scheduleExactCall(name: String, number: String, delayMs: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmCallReceiver::class.java).apply {
            putExtra("CALLER_NAME", name)
            putExtra("CALLER_NUMBER", number)
        }
        val pi = PendingIntent.getBroadcast(
            this, 777, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        targetRingTimeMs = System.currentTimeMillis() + delayMs

        getSharedPreferences("TeamCallPrefs", Context.MODE_PRIVATE).edit()
            .putLong("RING_TIME", targetRingTimeMs)
            .putString("TARGET_NAME", name)
            .apply()

        val clockInfo = AlarmManager.AlarmClockInfo(targetRingTimeMs, pi)
        alarmManager.setAlarmClock(clockInfo, pi)

        statusBox.visibility = View.VISIBLE
        uiHandler.removeCallbacks(countdownRunnable)
        uiHandler.post(countdownRunnable)
        Toast.makeText(this, "스케줄러에 전화가 등록되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun cancelScheduledCall() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmCallReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            this, 777, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
        targetRingTimeMs = 0L
        getSharedPreferences("TeamCallPrefs", Context.MODE_PRIVATE).edit().putLong("RING_TIME", 0L).apply()
        uiHandler.removeCallbacks(countdownRunnable)
        statusBox.visibility = View.GONE
        Toast.makeText(this, "예약된 전화가 취소되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun checkExistingSchedule() {
        val prefs = getSharedPreferences("TeamCallPrefs", Context.MODE_PRIVATE)
        val savedTime = prefs.getLong("RING_TIME", 0L)
        if (savedTime > System.currentTimeMillis()) {
            targetRingTimeMs = savedTime
            statusBox.visibility = View.VISIBLE
            uiHandler.removeCallbacks(countdownRunnable)
            uiHandler.post(countdownRunnable)
        }
    }

    private fun checkFingerprintOnce() {
        if (isUnlocked) {
            mainScrollView.visibility = View.VISIBLE
            return
        }
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardSecure) {
            val intent = km.createConfirmDeviceCredentialIntent("🔒 팀전화 보안 인증", "지문을 인식해 주세요.")
            if (intent != null) {
                startActivityForResult(intent, AUTH_REQUEST_CODE)
            } else {
                isUnlocked = true
                mainScrollView.visibility = View.VISIBLE
            }
        } else {
            isUnlocked = true
            mainScrollView.visibility = View.VISIBLE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTH_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                isUnlocked = true
                mainScrollView.visibility = View.VISIBLE
            } else {
                finish()
            }
            return
        }
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val nIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val pIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (nIdx >= 0) nameInput.setText(c.getString(nIdx))
                        if (pIdx >= 0) numberInput.setText(formatNum(c.getString(pIdx)))
                    }
                }
            }
        }
    }

    private fun formatNum(raw: String): String {
        val d = raw.replace(Regex("[^0-9]"), "")
        return when {
            d.length == 11 -> "${d.substring(0, 3)}-${d.substring(3, 7)}-${d.substring(7)}"
            d.length == 10 -> "${d.substring(0, 3)}-${d.substring(3, 6)}-${d.substring(6)}"
            else -> raw
        }
    }

    private fun makeCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = makeRoundBg("#FFFFFF", 32f)
        setPadding(45, 45, 45, 45)
        elevation = 6f
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 35) }
    }

    private fun makeRoundBg(hex: String, r: Float) = GradientDrawable().apply {
        setColor(Color.parseColor(hex))
        cornerRadius = r
    }
}

class AlarmCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TeamCall:AlarmWakeLock")
        wl.acquire(10000L)

        val name = intent.getStringExtra("CALLER_NAME") ?: "알 수 없음"
        val number = intent.getStringExtra("CALLER_NUMBER") ?: "010-0000-0000"

        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val handle = PhoneAccountHandle(ComponentName(context, FakeCallService::class.java), "HD_Voice_Trainer")
        val extras = Bundle().apply {
            putString("CALLER_NAME", name)
            putString("CALLER_NUMBER", number)
        }
        try {
            tm.addNewIncomingCall(handle, extras)
        } catch (e: Exception) {}
    }
}



class FakeCallService : ConnectionService() {
    private val NOTIF_ID = 9999
    private val CH_ID = "fold8_watch_sync_v9"
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isCallActive = false
    private var isRecording = false
    private val chatHistory = JSONArray()
    private var preloadedFirstAudio: File? = null

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val extras = request?.extras
        val callerName = extras?.getString("CALLER_NAME") ?: "알 수 없음"
        val rawNumber = extras?.getString("CALLER_NUMBER") ?: "010-0000-0000"

        val cleanNum = rawNumber.replace(Regex("[^0-9]"), "")
        val tempNum = if (cleanNum.length >= 4) {
            val lastChar = cleanNum.last()
            val nextChar = if (lastChar == '9') '8' else (lastChar + 1)
            cleanNum.dropLast(1) + nextChar
        } else "01098765432"

        ensureContactForCallLog(callerName, tempNum)

        // 벨이 울리는 동안 첫마디 목소리를 미리 다운로드(0.1초 즉시 응답용)
        preloadFirstAiVoice(callerName)

        val conn = object : Connection() {
            override fun onAnswer() {
                super.onAnswer()
                clearWatchAlert()
                setActive()
                isCallActive = true
                startAiConversationIfEnabled(callerName)
            }
            override fun onReject() {
                super.onReject()
                stopAiAndCleanUp()
                setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
                destroy()
            }
            override fun onDisconnect() {
                super.onDisconnect()
                stopAiAndCleanUp()
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }
        }

        conn.setAddress(Uri.fromParts("tel", tempNum, null), TelecomManager.PRESENTATION_ALLOWED)
        conn.setRinging()
        ringWatch(callerName)
        return conn
    }

    private fun preloadFirstAiVoice(callerName: String) {
        val prefs = getSharedPreferences("TeamCallPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("USE_AI", false)) return
        val apiKey = prefs.getString("OPENAI_KEY", "") ?: ""
        val firstLine = prefs.getString("SAVED_PROMPT", "안녕하세요? 대표님!") ?: "여보세요?"
        val voice = prefs.getString("VOICE_TYPE", "onyx") ?: "onyx"
        val speed = prefs.getFloat("VOICE_SPEED", 1.0f).toDouble()
        if (apiKey.isEmpty()) return

        chatHistory. encryptedClear()
        val sysPrompt = "당신은 '$callerName'입니다. 현재 상대방(대표님/담당자)에게 전화를 걸어 통화 중입니다. 첫마디 대사의 상황과 감정을 끝까지 유지하며 실제 전화 통화처럼 자연스럽고 짧게(1~2문장 이내로) 대답하세요."
        chatHistory.put(JSONObject().put("role", "system").put("content", sysPrompt))
        chatHistory.put(JSONObject().put("role", "assistant").put("content", firstLine))

        Thread {
            preloadedFirstAudio = downloadTtsFile(apiKey, firstLine, voice, speed, "preload_first.mp3")
        }.start()
    }

    private fun JSONArray.encryptedClear() {
        while (this.length() > 0) this.remove(0)
    }

    private fun startAiConversationIfEnabled(callerName: String) {
        val prefs = getSharedPreferences("TeamCallPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("USE_AI", false)) return
        val apiKey = prefs.getString("OPENAI_KEY", "") ?: ""
        val firstLine = prefs.getString("SAVED_PROMPT", "여보세요?") ?: "여보세요?"
        val voice = prefs.getString("VOICE_TYPE", "onyx") ?: "onyx"
        val speed = prefs.getFloat("VOICE_SPEED", 1.0f).toDouble()
        if (apiKey.isEmpty()) return

        Thread {
            // 미리 받아둔 파일이 있으면 0.1초 만에 즉시 재생, 아직 안 받아졌으면 최대 1.5초 대기 후 재생
            var waitCount = 0
            while (preloadedFirstAudio == null && waitCount < 15 && isCallActive) {
                Thread.sleep(100)
                waitCount++
            }
            val fileToPlay = preloadedFirstAudio ?: downloadTtsFile(apiKey, firstLine, voice, speed, "first_fallback.mp3")
            if (fileToPlay != null && isCallActive) {
                playAudioFile(fileToPlay) {
                    if (isCallActive) recordAndRecognizeUserVoice(apiKey, voice, speed)
                }
            }
        }.start()
    }

    private fun downloadTtsFile(apiKey: String, text: String, voice: String, speed: Double, fileName: String): File? {
        return try {
            val url = URL("https://api.openai.com/v1/audio/speech")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            val body = JSONObject().apply {
                put("model", "tts-1")
                put("input", text)
                put("voice", voice)
                put("speed", speed)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val outFile = File(cacheDir, fileName)
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            outFile
        } catch (e: Exception) {
            null
        }
    }

    private fun playAudioFile(file: File, onDone: () -> Unit) {
        mainHandler.post {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    prepare()
                    setOnCompletionListener { onDone() }
                    start()
                }
            } catch (e: Exception) {
                onDone()
            }
        }
    }

    // 백그라운드에서도 절대 안 막히는 직접 마이크 녹음 + 무음 감지(말 끝나면 자동 전송)
    private fun recordAndRecognizeUserVoice(apiKey: String, voice: String, speed: Double) {
        if (!isCallActive) return
        Thread {
            try {
                val sampleRate = 16000
                val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
                val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
                val minBufSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val recorder = android.media.AudioRecord(
                    android.media.MediaRecorder.AudioSource.MIC,
                    sampleRate, channelConfig, audioFormat, minBufSize * 2
                )

                val pcmFile = File(cacheDir, "user_speech.pcm")
                val wavFile = File(cacheDir, "user_speech.wav")
                val buffer = ShortArray(1024)

                recorder.startRecording()
                isRecording = true

                var speechDetected = false
                var silenceStartMs = System.currentTimeMillis()
                val recordStartMs = System.currentTimeMillis()

                FileOutputStream(pcmFile).use { fos ->
                    while (isCallActive && isRecording) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            var maxAmp = 0
                            val byteBuf = ByteArray(read * 2)
                            for (i in 0 until read) {
                                val s = buffer[i].toInt()
                                val absVal = kotlin.math.abs(s)
                                if (absVal > maxAmp) maxAmp = absVal
                                byteBuf[i * 2] = (s and 0x00FF).toByte()
                                byteBuf[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                            }
                            fos.write(byteBuf)

                            if (maxAmp > 1300) { // 목소리 감지됨
                                speechDetected = true
                                silenceStartMs = System.currentTimeMillis()
                            } else {
                                val silentDuration = System.currentTimeMillis() - silenceStartMs
                                // 말을 한 뒤 1.3초 동안 조용하면 말이 끝난 것으로 판단!
                                if (speechDetected && silentDuration > 1300) break
                                // 아무 말도 안 하고 6초가 지나면 다시 물어보기 위해 종료
                                if (!speechDetected && (System.currentTimeMillis() - recordStartMs) > 6000) break
                            }
                        }
                    }
                }
                recorder.stop()
                recorder.release()
                isRecording = false

                if (!isCallActive) return@Thread

                if (!speechDetected) {
                    // 사용자가 아무 말도 안 했으면 다시 귀 기울이기
                    recordAndRecognizeUserVoice(apiKey, voice, speed)
                    return@Thread
                }

                pcmToWav(pcmFile, wavFile, sampleRate)
                val userText = transcribeWithWhisper(apiKey, wavFile)

                if (userText.isNotBlank() && isCallActive) {
                    askGptAndSpeak(apiKey, userText, voice, speed)
                } else if (isCallActive) {
                    recordAndRecognizeUserVoice(apiKey, voice, speed)
                }
            } catch (e: Exception) {
                if (isCallActive) {
                    Thread.sleep(1000)
                    recordAndRecognizeUserVoice(apiKey, voice, speed)
                }
            }
        }.start()
    }

    private fun pcmToWav(pcmFile: File, wavFile: File, sampleRate: Int) {
        val pcmData = pcmFile.readBytes()
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte(); header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte(); header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0; header[22] = 1; header[23] = 0
        header[24] = (sampleRate and 0xff).toByte(); header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte(); header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte(); header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte(); header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2; header[33] = 0; header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte(); header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte(); header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        FileOutputStream(wavFile).use {
            it.write(header)
            it.write(pcmData)
        }
    }

    private fun transcribeWithWhisper(apiKey: String, wavFile: File): String {
        return try {
            val boundary = "----WebKitFormBoundary" + System.currentTimeMillis()
            val url = URL("https://api.openai.com/v1/audio/transcriptions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                doOutput = true
            }
            conn.outputStream.use { os ->
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Disposition: form-data; name=\"model\"\r\n\r\nwhisper-1\r\n".toByteArray())
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Disposition: form-data; name=\"language\"\r\n\r\nko\r\n".toByteArray())
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n".toByteArray())
                os.write("Content-Type: audio/wav\r\n\r\n".toByteArray())
                os.write(wavFile.readBytes())
                os.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val resp = conn.inputStream.bufferedReader().readText()
            JSONObject(resp).optString("text", "").trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun askGptAndSpeak(apiKey: String, userText: String, voice: String, speed: Double) {
        if (!isCallActive) return
        Thread {
            try {
                chatHistory.put(JSONObject().put("role", "user").put("content", userText))
                val url = URL("https://api.openai.com/v1/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
                val body = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", chatHistory)
                    put("max_tokens", 100)
                    put("temperature", 0.7)
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val respStr = conn.inputStream.bufferedReader().readText()
                val reply = JSONObject(respStr).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()

                chatHistory.put(JSONObject().put("role", "assistant").put("content", reply))

                if (isCallActive) {
                    val replyAudio = downloadTtsFile(apiKey, reply, voice, speed, "ai_reply.mp3")
                    if (replyAudio != null && isCallActive) {
                        playAudioFile(replyAudio) {
                            if (isCallActive) recordAndRecognizeUserVoice(apiKey, voice, speed)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isCallActive) recordAndRecognizeUserVoice(apiKey, voice, speed)
            }
        }.start()
    }

    private fun stopAiAndCleanUp() {
        isCallActive = false
        isRecording = false
        clearWatchAlert()
        mainHandler.post {
            try { mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null } catch (e: Exception) {}
        }
    }

    private fun ensureContactForCallLog(name: String, number: String) {
        try {
            val lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(lookupUri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    contentResolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id), null, null)
                }
            }
            val rawUri = contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, ContentValues())
            val rawId = ContentUris.parseId(rawUri ?: return)

            val nameCv = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            }
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, nameCv)

            val phoneCv = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, phoneCv)
        } catch (e: Exception) {}
    }

    private fun ringWatch(name: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(CH_ID, "Call", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                }
                nm.createNotificationChannel(ch)
            }
            val pi = PendingIntent.getActivity(this, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
            val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CH_ID) else Notification.Builder(this)
            b.setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle(name)
                .setContentText("수신전화")
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setFullScreenIntent(pi, true)
            nm.notify(NOTIF_ID, b.build())
        } catch (e: Exception) {}
    }

    private fun clearWatchAlert() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_ID)
        } catch (e: Exception) {}
    }
}



