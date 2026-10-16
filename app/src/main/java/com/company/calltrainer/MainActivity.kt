package com.company.calltrainer

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var telecomManager: TelecomManager
    private lateinit var phoneAccountHandle: PhoneAccountHandle
    private lateinit var nameInput: EditText
    private lateinit var numberInput: EditText
    private val PICK_CONTACT_REQUEST = 1001
    private val callHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val componentName = ComponentName(this, FakeCallService::class.java)
        phoneAccountHandle = PhoneAccountHandle(componentName, "HD_Voice_Trainer")

        val phoneAccount = PhoneAccount.builder(phoneAccountHandle, "HD Voice")
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .build()
        telecomManager.registerPhoneAccount(phoneAccount)

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
                Toast.makeText(context, "$timeLabel 뒤 연결됩니다. 화면을 끄고 대기하세요.", Toast.LENGTH_SHORT).show()

                callHandler.postDelayed({
                    triggerIncomingCall(name, number)
                }, delayMillis)
            }
        }

        step3Card.addView(startCallBtn)
        mainLayout.addView(step3Card)

        // 5. 하단 통화 허용 설정 안내
        val settingCard = createCardLayout()
        val helperText = TextView(this).apply {
            text = "전화가 안된다면 아래 버튼을 눌러서 'HD Voice' 스위치를 켜주세요."
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            setPadding(5, 0, 5, 25)
        }

        val enableAccountBtn = Button(this).apply {
            text = "통화 허용 설정"
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            background = createRoundedBackground("#E0E0E0", 20f)
            setOnClickListener {
                val intent = Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)
                startActivity(intent)
            }
        }

        settingCard.addView(helperText)
        settingCard.addView(enableAccountBtn)
        mainLayout.addView(settingCard)

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
                    numberInput.setText(rawNumber.replace(Regex("[^0-9]"), ""))
                }
            }
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
            Toast.makeText(this, "하단의 [통화 허용 설정] 버튼을 눌러 'HD Voice' 스위치를 켜주세요.", Toast.LENGTH_LONG).show()
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

class FakeCallService : ConnectionService() {
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val connection = object : Connection() {
            override fun onAnswer() {
                super.onAnswer()
                setActive()
            }
            override fun onReject() {
                super.onReject()
                setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
                destroy()
            }
            override fun onDisconnect() {
                super.onDisconnect()
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }
        }

        val extras = request?.extras
        val callerName = extras?.getString("CALLER_NAME") ?: "Unknown"
        val cleanNumber = (extras?.getString("CALLER_NUMBER") ?: "01000000000").replace(Regex("[^0-9]"), "")

        // [오류 위험 0% 완벽 해결책!]
        // 100% 진짜 아라비아 숫자(0~9)만 그대로 사용해서 앞의 '010'이 절대 잘리지 않게 합니다!
        // 단, 내 주소록의 단색 프로필 카드와 매칭되는 것만 피하기 위해 맨 마지막 숫자 한 자리만 살짝 바꿉니다.
        val safeNumber = if (cleanNumber.isNotEmpty()) {
            val lastDigit = cleanNumber.last()
            val newLastDigit = if (lastDigit == '9') '8' else (lastDigit + 1)
            cleanNumber.dropLast(1) + newLastDigit
        } else {
            "01098765432"
        }

        connection.setAddress(Uri.fromParts("tel", safeNumber, null), TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED)
        connection.setRinging()

        return connection
    }
}
