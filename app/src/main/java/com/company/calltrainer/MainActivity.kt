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
                startActivity(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS))
            }
        })
        mainLayout.addView(setCard)

        mainScrollView.addView(mainLayout)
        setContentView(mainScrollView)

        // [수정 완료] 오직 앱을 처음 켤 때(onCreate) 딱 1번만 지문 인증 실행!
        checkFingerprintOnce()
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
            } else {
                finish()
            }
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
