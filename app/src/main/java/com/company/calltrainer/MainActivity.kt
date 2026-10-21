package com.company.calltrainer

import android.Manifest
import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import android.telecom.*
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var nameInput: EditText
    private lateinit var numberInput: EditText
    private lateinit var mainScrollView: ScrollView
    private lateinit var scheduleStatusText: TextView
    private lateinit var cancelBtn: Button
    private var countDownTimer: CountDownTimer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val AUTH_REQUEST_CODE = 5555
    private var isUnlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val perms = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(perms.toTypedArray(), 3000)

        val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val handle = PhoneAccountHandle(ComponentName(this, FakeCallService::class.java), "HD_Voice_Trainer")
        val account = PhoneAccount.builder(handle, "HD Voice")
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .build()
        tm.registerPhoneAccount(account)

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

        // 1. 연락처 및 입력 카드
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
            setText("대한건설 김철수 소장")
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

        // 2. 시간 선택 카드
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
        mainLayout.addView(step2Card)

        // 3. [신규 추가!] 실시간 콜 스케줄러 표시창 + 실행 버튼 카드
        val step3Card = createCard()

        scheduleStatusText = TextView(this).apply {
            text = "📋 예약 대기 중 (현재 예약된 콜이 없습니다)"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#555555"))
            background = roundBg("#F1F3F5", 20f)
            setPadding(35, 35, 35, 35)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 30) }
        }

        val startBtn = Button(this).apply {
            text = "START SECURE CALL"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = roundBg("#1976D2", 28f)
            setPadding(20, 40, 20, 40)
            setOnClickListener {
                val name = nameInput.text.toString().ifBlank { "Unknown" }
                val number = numberInput.text.toString().ifBlank { "010-0000-0000" }
                val delay = when (timeGroup.checkedRadioButtonId) {
                    101 -> 5000L
                    102 -> 15000L
                    103 -> 60000L
                    104 -> 300000L
                    else -> 5000L
                }
                scheduleExactCall(name, number, delay)
            }
        }

        cancelBtn = Button(this).apply {
            text = "예약 취소"
            textSize = 14f
            setTextColor(Color.parseColor("#D32F2F"))
            background = roundBg("#FFEBEE", 20f)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 20, 0, 0) }
            setOnClickListener { cancelScheduledCall() }
        }

        step3Card.addView(scheduleStatusText)
        step3Card.addView(startBtn)
        step3Card.addView(cancelBtn)
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

    // [핵심 해결] 5분 뒤 화면이 꺼져 있어도 100% 울리게 하는 시스템 알람 + 실시간 카운트다운 표시창!
    private fun scheduleExactCall(name: String, number: String, delayMillis: Long) {
        cancelScheduledCall()

        // 1. 폴드8이 5분 동안 절전 모드로 잠들지 못하게 CPU 깨어있기 잠금(WakeLock) 작동!
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TeamCall::ExactTimerLock").apply {
            acquire(delayMillis + 10000L)
        }

        // 2. 갤럭시 모닝콜 알람 시계(setAlarmClock)에 정밀 등록 (절전 모드 100% 관통!)
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmCallReceiver::class.java).apply {
            putExtra("CALLER_NAME", name)
            putExtra("CALLER_NUMBER", number)
        }
        val pi = PendingIntent.getBroadcast(
            this, 777, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = System.currentTimeMillis() + delayMillis
        val clockInfo = AlarmManager.AlarmClockInfo(triggerTime, pi)
        am.setAlarmClock(clockInfo, pi)

        // 3. 스케줄러 표시창에 1초 단위로 남은 시간 실시간 표시!
        cancelBtn.visibility = View.VISIBLE
        scheduleStatusText.background = roundBg("#E8F5E9", 20f)
        scheduleStatusText.setTextColor(Color.parseColor("#1B5E20"))

        countDownTimer = object : CountDownTimer(delayMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSec = (millisUntilFinished / 1000) + 1
                val min = totalSec / 60
                val sec = totalSec % 60
                val timeStr = if (min > 0) "${min}분 ${sec}초" else "${sec}초"
                scheduleStatusText.text = "⏰ 예약된 발신자: $name\n($number)\n\n📞 콜 도착까지 남은 시간: $timeStr"
            }

            override fun onFinish() {
                scheduleStatusText.text = "✅ [$name] 콜 발신이 실행되었습니다!"
                scheduleStatusText.background = roundBg("#E3F2FD", 20f)
                scheduleStatusText.setTextColor(Color.parseColor("#1565C0"))
                cancelBtn.visibility = View.GONE
            }
        }.start()
    }

    private fun cancelScheduledCall() {
        countDownTimer?.cancel()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {}

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmCallReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            this, 777, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)

        scheduleStatusText.text = "📋 예약 대기 중 (현재 예약된 콜이 없습니다)"
        scheduleStatusText.background = roundBg("#F1F3F5", 20f)
        scheduleStatusText.setTextColor(Color.parseColor("#555555"))
        cancelBtn.visibility = View.GONE
    }

    private fun checkFingerprintOnce() {
        if (isUnlocked) {
            mainScrollView.visibility = View.VISIBLE
            return
        }
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardSecure) {
            @Suppress("DEPRECATION")
            val authIntent = km.createConfirmDeviceCredentialIntent("🔒 팀전화 보안 인증", "지문을 인식해 주세요.")
            if (authIntent != null) {
                startActivityForResult(authIntent, AUTH_REQUEST_CODE)
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
            if (resultCode == RESULT_OK) {
                isUnlocked = true
                mainScrollView.visibility = View.VISIBLE
            } else finish()
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
        id = idVal; text = txt; textSize = 15f; isChecked = chk
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

// 5분이 지나도 잠든 폴드8을 강제로 깨워서 전화를 거는 알람 수신기!
class AlarmCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val name = intent.getStringExtra("CALLER_NAME") ?: "Unknown"
            val number = intent.getStringExtra("CALLER_NUMBER") ?: "010-0000-0000"
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
