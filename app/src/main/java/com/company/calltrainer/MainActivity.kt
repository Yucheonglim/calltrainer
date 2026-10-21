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
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isCallActive = false
    private val chatHistory = JSONArray()

    override fun onCreateIncomingConnection(account: PhoneAccountHandle?, req: ConnectionRequest?): Connection {
        val extras = req?.extras
        val callerName = extras?.getString("CALLER_NAME") ?: "Unknown"
        val rawNumber = extras?.getString("CALLER_NUMBER") ?: "010-0000-0000"
        val cleanNum = rawNumber.replace(Regex("[^0-9]"), "")

        val tempNum = if (cleanNum.length >= 4) {
            val last = cleanNum.last()
            cleanNum.dropLast(1) + (if (last == '9') '8' else (last + 1))
        } else "01098765432"

        ensureContactForCallLog(callerName, tempNum)

        val conn = object : Connection() {
            override fun onAnswer() {
                super.onAnswer()
                clearWatchAlert()
                setActive()
                isCallActive = true
                // 전화를 받는 순간 AI 음성통화 모드가 켜져 있으면 즉시 사람 목소리 시작!
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

    private fun startAiConversationIfEnabled(callerName: String) {
        val prefs = getSharedPreferences("TeamCallPrefs", Context.MODE_PRIVATE)
        val useAi = prefs.getBoolean("USE_AI", false)
        val apiKey = prefs.getString("OPENAI_KEY", "") ?: ""
        val prompt = prefs.getString("SAVED_PROMPT", "여보세요, 통화 가능하십니까?") ?: ""
        val voice = prefs.getString("VOICE_TYPE", "onyx") ?: "onyx"

        if (!useAi || apiKey.isEmpty()) return

        // AI에게 상대방 역할 부여
        val sysMsg = JSONObject().apply {
            put("role", "system")
            put("content", "당신은 지금 전화를 건 '$callerName'입니다. 실제 전화 통화처럼 1~2문장으로 짧고 자연스러운 구어체로만 대답하세요. 첫 상황/대사 지시: $prompt")
        }
        chatHistory.put(sysMsg)

        // 전화를 받자마자 0.8초 뒤에 AI가 먼저 첫마디를 사람 목소리로 건넴!
        Thread {
            try {
                Thread.sleep(800)
                if (!isCallActive) return@Thread
                speakWithOpenAi(apiKey, voice, prompt) {
                    listenUserReply(apiKey, voice)
                }
            } catch (e: Exception) {}
        }.start()
    }

    // 오픈AI 최고급 사람 목소리(Onyx / Nova)를 생성해 수화기로 들려주는 함수
    private fun speakWithOpenAi(apiKey: String, voice: String, textToSpeak: String, onDone: () -> Unit) {
        Thread {
            try {
                val url = URL("https://api.openai.com/v1/audio/speech")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }

                val body = JSONObject().apply {
                    put("model", "tts-1")
                    put("input", textToSpeak)
                    put("voice", voice)
                }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val tempFile = File(cacheDir, "ai_voice.mp3")
                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }

                mainHandler.post {
                    if (!isCallActive) return@post
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            setDataSource(tempFile.absolutePath)
                            prepare()
                            setOnCompletionListener { onDone() }
                            start()
                        }
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {}
        }.start()
    }

    // 내가 수화기에 대고 말하면 알아듣고 AI 두뇌(GPT-4o-mini)가 다음 대답을 만드는 함수
    private fun listenUserReply(apiKey: String, voice: String) {
        mainHandler.post {
            if (!isCallActive) return@post
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                val srIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                }
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val userText = matches?.firstOrNull() ?: ""
                        if (userText.isNotBlank() && isCallActive) {
                            askGptAndSpeak(apiKey, voice, userText)
                        } else if (isCallActive) {
                            listenUserReply(apiKey, voice)
                        }
                    }
                    override fun onError(error: Int) {
                        if (isCallActive) mainHandler.postDelayed({ listenUserReply(apiKey, voice) }, 1000)
                    }
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                speechRecognizer?.startListening(srIntent)
            } catch (e: Exception) {}
        }
    }

    private fun askGptAndSpeak(apiKey: String, voice: String, userText: String) {
        Thread {
            try {
                chatHistory.put(JSONObject().apply {
                    put("role", "user")
                    put("content", userText)
                })

                val url = URL("https://api.openai.com/v1/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }

                val reqBody = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", chatHistory)
                    put("max_tokens", 120)
                }
                conn.outputStream.use { it.write(reqBody.toString().toByteArray(Charsets.UTF_8)) }

                val resStr = conn.inputStream.bufferedReader().use { it.readText() }
                val reply = JSONObject(resStr)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                chatHistory.put(JSONObject().apply {
                    put("role", "assistant")
                    put("content", reply)
                })

                if (isCallActive) {
                    speakWithOpenAi(apiKey, voice, reply) {
                        listenUserReply(apiKey, voice)
                    }
                }
            } catch (e: Exception) {}
        }.start()
    }

    private fun stopAiAndCleanUp() {
        isCallActive = false
        clearWatchAlert()
        mainHandler.post {
            try { mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null } catch (e: Exception) {}
            try { speechRecognizer?.destroy(); speechRecognizer = null } catch (e: Exception) {}
        }
    }

    private fun ensureContactForCallLog(name: String, number: String) {
        try {
            val lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(lookupUri, arrayOf(ContactsContract.PhoneLookup.CONTACT_ID), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    contentResolver.delete(
                        ContactsContract.RawContacts.CONTENT_URI,
                        "${ContactsContract.RawContacts.CONTACT_ID}=?",
                        arrayOf(c.getLong(0).toString())
                    )
                }
            }
            val rawUri = contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, ContentValues())
            val rawId = ContentUris.parseId(rawUri ?: return)

            contentResolver.insert(ContactsContract.Data.CONTENT_URI, ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            })
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            })
        } catch (e: Exception) {}
    }

    private fun ringWatch(name: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(CH_ID, "수신전화", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                }
                nm.createNotificationChannel(ch)
            }
            val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CH_ID)
            else @Suppress("DEPRECATION") Notification.Builder(this)

            b.setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle(name)
                .setContentText("수신전화")
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
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

