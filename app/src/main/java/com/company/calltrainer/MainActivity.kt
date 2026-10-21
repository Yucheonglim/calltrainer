package com.company.calltrainer

import android.Manifest
import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.*
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import android.speech.*
import android.telecom.*
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var telecomManager: TelecomManager
    private lateinit var phoneAccountHandle: PhoneAccountHandle
    private lateinit var nameInput: EditText
    private lateinit var numberInput: EditText
    private lateinit var promptInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var mainScrollView: ScrollView
    private lateinit var scheduleStatusText: TextView
    private lateinit var cancelScheduleBtn: Button
    private lateinit var aiConfigBox: LinearLayout
    private val uiTimerHandler = Handler(Looper.getMainLooper())
    private val AUTH_REQUEST_CODE = 5555
    private var isUnlocked = false
    private var targetRingTimeMillis = 0L
    private var scheduledName = ""
    private var scheduledNumber = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("TeamCallPrefs", Context.MODE_PRIVATE)

        val perms = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(perms.toTypedArray(), 3000)

        telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        phoneAccountHandle = PhoneAccountHandle(ComponentName(this, FakeCallService::class.java), "HD_Voice_Trainer")
        val phoneAccount = PhoneAccount.builder(phoneAccountHandle, "HD Voice")
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .build()
        telecomManager.registerPhoneAccount(phoneAccount)

        mainScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F6F9"))
            visibility = View.INVISIBLE
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 80, 50, 80)
        }

        mainLayout.addView(TextView(this).apply {
            text = "🔒 Team Call : Secure Line"
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A1A1A"))
            setPadding(10, 0, 0, 50)
        })

        // 1. 연락처 입력 카드
        val step1Card = createCard()
        step1Card.addView(Button(this).apply {
            text = "📒 연락처 불러오기"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1565C0"))
            background = roundBg("#E3F2FD", 24f)
            setPadding(20, 30, 20, 30)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 25) }
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), 1001)
            }
        })

        nameInput = EditText(this).apply {
            hint = "이름 입력"
            setText("대표회계사")
            textSize = 16f
            setPadding(35, 30, 35, 30)
            background = roundBg("#F8F9FA", 16f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 20) }
        }
        numberInput = EditText(this).apply {
            hint = "전화번호 입력"
            setText("010-9876-5432")
            textSize = 16f
            setPadding(35, 30, 35, 30)
            background = roundBg("#F8F9FA", 16f)
        }
        step1Card.addView(nameInput)
        step1Card.addView(numberInput)
        mainLayout.addView(step1Card)

        // 2. 시간 선택 + [벨소리만 vs AI 목소리] 선택 카드
        val step2Card = createCard()
        val timeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER
            addView(createRadio(101, "5초", true))
            addView(createRadio(102, "15초", false))
            addView(createRadio(103, "1분", false))
            addView(createRadio(104, "5분", false))
        }
        step2Card.addView(timeGroup)

        // [신규 추가!] 벨소리만 울릴지, AI 사람 목소리가 나올지 선택하는 스위치
        val modeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 30, 0, 0) }
            addView(createRadio(201, "🔕 벨소리만", true))
            addView(createRadio(202, "🤖 AI 음성통화", false))
        }
        step2Card.addView(modeGroup)

        // [AI 음성통화 선택 시 나타나는 세부 설정창]
        aiConfigBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 25, 0, 0) }
        }

        val voiceGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER
            addView(createRadio(301, "👔 중년 남성(Onyx)", true))
            addView(createRadio(302, "👩 차분한 여성(Nova)", false))
        }

        promptInput = EditText(this).apply {
            hint = "AI가 전화 받자마자 할 첫마디 또는 상황 입력"
            setText(prefs.getString("SAVED_PROMPT", "안녕하세요, 대표님 박입니다. 건설사 감사 결제때문에 연락드렸습니다."))
            textSize = 14f
            setPadding(30, 25, 30, 25)
            background = roundBg("#F8F9FA", 16f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 15, 0, 15) }
        }

        apiKeyInput = EditText(this).apply {
            hint = "OpenAI API 키 입력 (sk-... 한 번만 입력하면 자동저장)"
            setText(prefs.getString("OPENAI_KEY", ""))
            textSize = 13f
            setPadding(30, 25, 30, 25)
            background = roundBg("#FFF8E1", 16f)
        }

        aiConfigBox.addView(voiceGroup)
        aiConfigBox.addView(promptInput)
        aiConfigBox.addView(apiKeyInput)
        step2Card.addView(aiConfigBox)

        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            aiConfigBox.visibility = if (checkedId == 202) View.VISIBLE else View.GONE
        }

        mainLayout.addView(step2Card)

        // 3. 실행 버튼 및 스케줄러 표시창 카드
        val step3Card = createCard()
        step3Card.addView(Button(this).apply {
            text = "START SECURE CALL"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = roundBg("#1976D2", 28f)
            setPadding(20, 40, 20, 40)
            setOnClickListener {
                val name = nameInput.text.toString().ifBlank { "Unknown" }
                val number = numberInput.text.toString().ifBlank { "010-0000-0000" }
                val useAi = (modeGroup.checkedRadioButtonId == 202)
                val voiceType = if (voiceGroup.checkedRadioButtonId == 301) "onyx" else "nova"
                val promptTxt = promptInput.text.toString().ifBlank { "여보세요, 통화 가능하십니까?" }
                val apiKeyTxt = apiKeyInput.text.toString().trim()

                if (useAi && apiKeyTxt.isEmpty()) {
                    Toast.makeText(context, "AI 통화를 위해 OpenAI API 키(sk-...)를 입력해 주세요!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                // 입력한 API 키와 대사는 다음번에 또 치지 않게 자동 저장!
                prefs.edit()
                    .putString("OPENAI_KEY", apiKeyTxt)
                    .putString("SAVED_PROMPT", promptTxt)
                    .putBoolean("USE_AI", useAi)
                    .putString("VOICE_TYPE", voiceType)
                    .apply()

                val delay = when (timeGroup.checkedRadioButtonId) {
                    101 -> 5000L
                    102 -> 15000L
                    103 -> 60000L
                    104 -> 300000L
                    else -> 5000L
                }
                scheduleExactCall(name, number, delay)
            }
        })

        scheduleStatusText = TextView(this).apply {
            text = "📅 예약된 콜 내역이 없습니다."
            textSize = 14f
            setTextColor(Color.parseColor("#555555"))
            background = roundBg("#F1F5F9", 20f)
            setPadding(35, 35, 35, 35)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 30, 0, 0) }
        }

        cancelScheduleBtn = Button(this).apply {
            text = "예약 취소"
            textSize = 13f
            setTextColor(Color.parseColor("#D32F2F"))
            background = roundBg("#FFEBEE", 18f)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 15, 0, 0) }
            setOnClickListener { cancelScheduledCall() }
        }

        step3Card.addView(scheduleStatusText)
        step3Card.addView(cancelScheduleBtn)
        mainLayout.addView(step3Card)

        // 4. 하단 통화 허용 설정 카드
        val setCard = createCard()
        setCard.addView(TextView(this).apply {
            text = "전화가 안된다면 아래 버튼을 눌러서 'HD Voice' 스위치를 켜주세요."
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            setPadding(5, 0, 5, 25)
        })
        setCard.addView(Button(this).apply {
            text = "통화 허용 설정"
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            background = roundBg("#E0E0E0", 20f)
            setOnClickListener { startActivity(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)) }
        })
        mainLayout.addView(setCard)

        mainScrollView.addView(mainLayout)
        setContentView(mainScrollView)

        checkFingerprintOnce()
    }

    private fun scheduleExactCall(name: String, number: String, delayMillis: Long) {
        cancelScheduledCall(showToast = false)
        scheduledName = name
        scheduledNumber = number
        targetRingTimeMillis = System.currentTimeMillis() + delayMillis

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmCallReceiver::class.java).apply {
            putExtra("CALLER_NAME", name)
            putExtra("CALLER_NUMBER", number)
        }
        val pi = PendingIntent.getBroadcast(this, 777, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(targetRingTimeMillis, pi), pi)

        cancelScheduleBtn.visibility = View.VISIBLE
        startCountdownUi()
        Toast.makeText(this, "콜 스케줄이 등록되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun cancelScheduledCall(showToast: Boolean = true) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(this, 777, Intent(this, AlarmCallReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        uiTimerHandler.removeCallbacksAndMessages(null)
        targetRingTimeMillis = 0L
        scheduleStatusText.text = "📅 예약된 콜 내역이 없습니다."
        scheduleStatusText.setTextColor(Color.parseColor("#555555"))
        cancelScheduleBtn.visibility = View.GONE
        if (showToast) Toast.makeText(this, "예약된 콜이 취소되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun startCountdownUi() {
        val targetTimeStr = SimpleDateFormat("a h:mm:ss", Locale.KOREA).format(Date(targetRingTimeMillis))
        uiTimerHandler.post(object : Runnable {
            override fun run() {
                val remain = targetRingTimeMillis - System.currentTimeMillis()
                if (remain <= 0L) {
                    scheduleStatusText.text = "✅ [$scheduledName] 콜 발신 완료!"
                    scheduleStatusText.setTextColor(Color.parseColor("#2E7D32"))
                    cancelScheduleBtn.visibility = View.GONE
                    return
                }
                val totalSec = (remain / 1000) + 1
                val remainStr = if (totalSec / 60 > 0) "${totalSec / 60}분 ${totalSec % 60}초 후" else "${totalSec % 60}초 후"
                scheduleStatusText.text = "📌 예약 대상: $scheduledName ($scheduledNumber)\n⏰ 예정 시각: $targetTimeStr ($remainStr 콜 도착)"
                scheduleStatusText.setTextColor(Color.parseColor("#1565C0"))
                uiTimerHandler.postDelayed(this, 500)
            }
        })
    }

    private fun checkFingerprintOnce() {
        if (isUnlocked) { mainScrollView.visibility = View.VISIBLE; return }
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardSecure) {
            @Suppress("DEPRECATION")
            val authIntent = km.createConfirmDeviceCredentialIntent("🔒 팀전화 보안 인증", "지문을 인식해 주세요.")
            if (authIntent != null) startActivityForResult(authIntent, AUTH_REQUEST_CODE)
            else { isUnlocked = true; mainScrollView.visibility = View.VISIBLE }
        } else { isUnlocked = true; mainScrollView.visibility = View.VISIBLE }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) { isUnlocked = true; mainScrollView.visibility = View.VISIBLE } else finish()
            return
        }
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val proj = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
            contentResolver.query(uri, proj, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    nameInput.setText(c.getString(0) ?: "")
                    numberInput.setText(formatNum(c.getString(1) ?: ""))
                }
            }
        }
    }

    private fun formatNum(raw: String): String {
        val d = raw.replace(Regex("[^0-9]"), "")
        return when (d.length) {
            11 -> "${d.substring(0, 3)}-${d.substring(3, 7)}-${d.substring(7, 11)}"
            10 -> "${d.substring(0, 3)}-${d.substring(3, 6)}-${d.substring(6, 10)}"
            else -> raw
        }
    }

    private fun createRadio(idVal: Int, txt: String, chk: Boolean) = RadioButton(this).apply {
        id = idVal; text = txt; textSize = 14f; isChecked = chk
        layoutParams = RadioGroup.LayoutParams(0, -2, 1f)
    }

    private fun createCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(45, 45, 45, 45)
        background = roundBg("#FFFFFF", 32f)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 35) }
        elevation = 4f
    }

    private fun roundBg(hex: String, r: Float) = GradientDrawable().apply {
        setColor(Color.parseColor(hex)); cornerRadius = r
    }
}

class AlarmCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "TeamCall:Wake")
        wl.acquire(10000L)

        val name = intent.getStringExtra("CALLER_NAME") ?: "Unknown"
        val number = intent.getStringExtra("CALLER_NUMBER") ?: "010-0000-0000"
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val handle = PhoneAccountHandle(ComponentName(context, FakeCallService::class.java), "HD_Voice_Trainer")
            val extras = Bundle().apply {
                putString("CALLER_NAME", name)
                putString("CALLER_NUMBER", number)
            }
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

