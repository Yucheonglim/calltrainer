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
    private val CH_ID = "fold8_watch_sync_v8"

    override fun onCreateIncomingConnection(account: PhoneAccountHandle?, req: ConnectionRequest?): Connection {
        val extras = req?.extras
        val callerName = extras?.getString("CALLER_NAME") ?: "Unknown"
        val rawNumber = extras?.getString("CALLER_NUMBER") ?: "010-0000-0000"
        val cleanNum = rawNumber.replace(Regex("[^0-9]"), "")

        val tempNum = if (cleanNum.length >= 4) {
            val last = cleanNum.last()
            cleanNum.dropLast(1) + (if (last == '9') '8' else (last + 1))
        } else "01098765432"

        // [핵심 해결] 통화목록(최근 기록)에도 이름이 영구적으로 표시되도록 이름표를 유지합니다!
        ensureContactForCallLog(callerName, tempNum)

        val conn = object : Connection() {
            override fun onAnswer() {
                super.onAnswer()
                clearWatchAlert()
                setActive()
            }
            override fun onReject() {
                super.onReject()
                clearWatchAlert()
                setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
                destroy()
            }
            override fun onDisconnect() {
                super.onDisconnect()
                clearWatchAlert()
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }
        }

        conn.setAddress(Uri.fromParts("tel", tempNum, null), TelecomManager.PRESENTATION_ALLOWED)
        conn.setRinging()

        ringWatch(callerName)
        return conn
    }

    private fun ensureContactForCallLog(name: String, number: String) {
        try {
            // 이전에 만든 같은 번호의 임시 연락처가 있으면 먼저 정리 후 최신 이름으로 등록
            val lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(lookupUri, arrayOf(ContactsContract.PhoneLookup.CONTACT_ID), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val contactId = c.getLong(0)
                    contentResolver.delete(
                        ContactsContract.RawContacts.CONTENT_URI,
                        "${ContactsContract.RawContacts.CONTACT_ID}=?",
                        arrayOf(contactId.toString())
                    )
                }
            }

            val rawUri = contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, ContentValues())
            val rawId = ContentUris.parseId(rawUri ?: return)

            val nameValues = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            }
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, nameValues)

            val phoneValues = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, phoneValues)
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
            val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CH_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
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
