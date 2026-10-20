package com.company.calltrainer

import android.Manifest
import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import android.telecom.*
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var telecomManager: TelecomManager
    private lateinit var phoneAccountHandle: PhoneAccountHandle
    private lateinit var nameInput: EditText
    private lateinit var numberInput: EditText
    private lateinit var mainScrollView: ScrollView
    private val callHandler = Handler(Looper.getMainLooper())
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

        telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        phoneAccountHandle = PhoneAccountHandle(ComponentName(this, FakeCallService::class.java), "HD_Voice_Trainer")
        val phoneAccount = PhoneAccount.builder(phoneAccountHandle, "HD Voice")
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .build()
        telecomManager.registerPhoneAccount(phoneAccount)

        mainScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F6F9"))
            visibility = View.INVISIBLE // [핵심] 지문 인증 통과 전까지는 화면을 아예 숨김!
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
                isUnlocked = true // 연락처 창 갔다 올 때는 잠금 유지
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
                val (delay, label) = when (timeGroup.checkedRadioButtonId) {
                    101 -> 5000L to "5초"
                    102 -> 15000L to "15초"
                    103 -> 60000L to "1분"
                    104 -> 300000L to "5분"
                    else -> 5000L to "5초"
                }
                callHandler.removeCallbacksAndMessages(null)
                Toast.makeText(context, "$label 뒤 연결됩니다. 화면을 끄고 대기하세요.", Toast.LENGTH_SHORT).show()
                callHandler.postDelayed({ triggerIncomingCall(name, number) }, delay)
            }
        })
        mainLayout.addView(step3Card)

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
            setOnClickListener {
                isUnlocked = true
                startActivity(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS))
            }
        })
        mainLayout.addView(setCard)

        mainScrollView.addView(mainLayout)
        setContentView(mainScrollView)

        // 앱을 켜자마자 폴드8 지문/생체인증 잠금창 실행!
        checkFingerprintLock()
    }

    // [지문 잠금 핵심 함수] 핸드폰에 등록된 지문/생체인증/패턴을 검사합니다!
    private fun checkFingerprintLock() {
        if (isUnlocked) return
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardSecure) {
            @Suppress("DEPRECATION")
            val authIntent = km.createConfirmDeviceCredentialIntent("🔒 팀전화 보안 인증", "지문을 인식하거나 잠금을 해제해 주세요.")
            if (authIntent != null) {
                startActivityForResult(authIntent, AUTH_REQUEST_CODE)
            } else {
                mainScrollView.visibility = View.VISIBLE
            }
        } else {
            // 만약 폰 자체에 잠금이 아예 안 걸려 있을 때만 바로 열림
            mainScrollView.visibility = View.VISIBLE
        }
    }

    override fun onStop() {
        super.onStop()
        // 앱을 닫고 홈 화면으로 나가면 다시 자동으로 잠금 상태로 전환!
        if (!isChangingConfigurations) {
            isUnlocked = false
            mainScrollView.visibility = View.INVISIBLE
        }
    }

    override fun onRestart() {
        super.onRestart()
        // 다시 앱을 켜면 또 지문을 물어보도록 철통 보안 적용!
        if (!isUnlocked) {
            checkFingerprintLock()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // 1. 지문 인증 결과 확인
        if (requestCode == AUTH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // 지문 일치! 숨겨둔 화면을 짠 하고 보여줌!
                isUnlocked = true
                mainScrollView.visibility = View.VISIBLE
            } else {
                // 지문 틀리거나 취소 누르면 즉시 앱을 꺼버림 (남이 절대 못 봄!)
                Toast.makeText(this, "보안 인증이 취소되어 앱을 종료합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }

        // 2. 연락처 불러오기 결과 확인
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            mainScrollView.visibility = View.VISIBLE
            val uri = data?.data ?: return
            val proj = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
            contentResolver.query(uri, proj, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    nameInput.setText(c.getString(0) ?: "")
                    numberInput.setText(formatNum(c.getString(1) ?: ""))
                }
            }
        } else if (requestCode == 1001) {
            mainScrollView.visibility = View.VISIBLE
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

    private fun triggerIncomingCall(name: String, number: String) {
        try {
            val extras = Bundle().apply {
                putString("CALLER_NAME", name)
                putString("CALLER_NUMBER", number)
            }
            telecomManager.addNewIncomingCall(phoneAccountHandle, extras)
        } catch (e: SecurityException) {
            Toast.makeText(this, "하단의 [통화 허용 설정]을 눌러 'HD Voice'를 켜주세요.", Toast.LENGTH_LONG).show()
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


class FakeCallService : ConnectionService() {
    private val NOTIF_ID = 9999
    private val CH_ID = "fold8_watch_sync_v7"

    override fun onCreateIncomingConnection(account: PhoneAccountHandle?, req: ConnectionRequest?): Connection {
        val extras = req?.extras
        val callerName = extras?.getString("CALLER_NAME") ?: "Unknown"
        val rawNumber = extras?.getString("CALLER_NUMBER") ?: "010-0000-0000"
        val cleanNum = rawNumber.replace(Regex("[^0-9]"), "")

        val tempNum = if (cleanNum.length >= 4) {
            val last = cleanNum.last()
            cleanNum.dropLast(1) + (if (last == '9') '8' else (last + 1))
        } else "01098765432"

        // 큰 글씨 이름만 주소록으로 띄우기 위해 임시 연락처 등록
        val tempId = addTempContact(callerName, tempNum)

        val conn = object : Connection() {
            override fun onAnswer() {
                super.onAnswer()
                clearWatchAlert()
                setActive()
            }
            override fun onReject() {
                super.onReject()
                clearWatchAlert()
                removeTempContact(tempId)
                setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
                destroy()
            }
            override fun onDisconnect() {
                super.onDisconnect()
                clearWatchAlert()
                removeTempContact(tempId)
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }
        }

        // [핵심 수정 1] 전화번호 밑에 작은 글씨로 이름이 한 번 더 중복 출력되던 setCallerDisplayName을 제거!
        // 이제 큰 이름 1개 + 전화번호 1개만 깔끔하게 나옵니다!
        conn.setAddress(Uri.fromParts("tel", tempNum, null), TelecomManager.PRESENTATION_ALLOWED)
        conn.setRinging()

        // [핵심 수정 2] 워치에는 전화번호 없이 오직 '연락처 이름'만 깔끔하게 전송!
        ringWatch(callerName)
        return conn
    }

    private fun addTempContact(name: String, number: String): Long {
        try {
            val rawUri = contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, ContentValues())
            val rawId = ContentUris.parseId(rawUri ?: return -1L)

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
            return rawId
        } catch (e: Exception) {
            return -1L
        }
    }

    private fun removeTempContact(rawId: Long) {
        if (rawId <= 0L) return
        try {
            val uri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawId)
            contentResolver.delete(uri, null, null)
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
            // 워치 화면에는 전화번호나 Team Call 없이 오직 '연락처 이름'만 출력!
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
