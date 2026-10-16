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
    private lateinit var statusText: TextView
    private val PICK_CONTACT_REQUEST = 1001
    private val callHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 갤럭시 순정 통화 계정 등록 (HD Voice 위장)
        telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val componentName = ComponentName(this, FakeCallService::class.java)
        phoneAccountHandle = PhoneAccountHandle(componentName, "HD_Voice_Trainer")

        val phoneAccount = PhoneAccount.builder(phoneAccountHandle, "HD Voice")
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .build()
        telecomManager.registerPhoneAccount(phoneAccount)

        // 전체 화면 스크롤 가능하게 설정 (배경: 밝은 회색으로 깔끔하게)
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F6F9"))
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 70, 50, 80)
        }

        // 상단 타이틀 영역
        val titleText = TextView(this).apply {
            text = "📞 Team Call 모의훈련"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A1A1A"))
            setPadding(10, 0, 0, 10)
        }

        val subTitleText = TextView(this).apply {
            text = "상대방과 시간을 설정한 뒤 맨 아래 [실행] 버튼을 눌러주세요."
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setPadding(10, 0, 0, 40)
        }

        mainLayout.addView(titleText)
        mainLayout.addView(subTitleText)

        // [STEP 1 카드] 발신자 정보 설정 (연락처 불러오기 + 직접 입력)
        val step1Card = createCardLayout()
        step1Card.addView(createSectionTitle("1단계: 누가 전화를 걸까요?"))
        step1Card.addView(createHelperText("내 연락처에서 고르거나, 아래 빈칸에 직접 적어주세요."))

        val contactBtn = Button(this).apply {
            text = "📒 내 휴대폰 연락처에서 불러오기"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1565C0"))
            background = createRoundedBackground("#E3F2FD", 24f)
            setPadding(20, 30, 20, 30)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 15, 0, 30) }
            layoutParams = params

            setOnClickListener {
                // 갤럭시 기본 연락처(전화번호부) 선택 화면 띄우기
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                startActivityForResult(intent, PICK_CONTACT_REQUEST)
            }
        }

        val nameLabel = createLabelText("발신자 이름 (화면에 표시될 이름)")
        nameInput = EditText(this).apply {
            hint = "예: 대한건설 김철수 소장"
            setText("대한건설 김철수 소장")
            textSize = 16f
            setPadding(30, 25, 30, 25)
            background = createRoundedBackground("#F8F9FA", 16f)
        }

        val numberLabel = createLabelText("발신자 전화번호")
        numberInput = EditText(this).apply {
            hint = "예: 010-1234-5678"
            setText("010-1234-5678")
            textSize = 16f
            setPadding(30, 25, 30, 25)
            background = createRoundedBackground("#F8F9FA", 16f)
        }

        step1Card.addView(contactBtn)
        step1Card.addView(nameLabel)
        step1Card.addView(nameInput)
        step1Card.addView(numberLabel)
        step1Card.addView(numberInput)
        mainLayout.addView(step1Card)

        // [STEP 2 카드] 전화가 걸려 올 시간 선택 (5초 / 15초 / 1분 / 5분)
        val step2Card = createCardLayout()
        step2Card.addView(createSectionTitle("2단계: 언제 전화가 오게 할까요?"))
        step2Card.addView(createHelperText("실행 버튼을 누른 뒤 벨이 울릴 대기 시간을 선택하세요."))

        val timeRadioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(10, 15, 10, 5)
        }

        val rb5Sec = RadioButton(this).apply {
            id = 101
            text = "  5초 뒤 (바로 테스트할 때)"
            textSize = 16f
            setPadding(10, 20, 10, 20)
            isChecked = true // 기본 선택값
        }
        val rb15Sec = RadioButton(this).apply {
            id = 102
            text = "  15초 뒤 (화면 끄고 준비할 때)"
            textSize = 16f
            setPadding(10, 20, 10, 20)
        }
        val rb1Min = RadioButton(this).apply {
            id = 103
            text = "  1분 뒤 (자리 정돈 후 연습할 때)"
            textSize = 16f
            setPadding(10, 20, 10, 20)
        }
        val rb5Min = RadioButton(this).apply {
            id = 104
            text = "  5분 뒤 (업무 중 실전처럼 받을 때)"
            textSize = 16f
            setPadding(10, 20, 10, 20)
        }

        timeRadioGroup.addView(rb5Sec)
        timeRadioGroup.addView(rb15Sec)
        timeRadioGroup.addView(rb1Min)
        timeRadioGroup.addView(rb5Min)
        step2Card.addView(timeRadioGroup)
        mainLayout.addView(step2Card)

        // [STEP 3 카드] 실행 버튼 및 상태 안내
        val step3Card = createCardLayout()
        step3Card.addView(createSectionTitle("3단계: 모의 전화 시작하기"))

        statusText = TextView(this).apply {
            text = "💡 준비 완료: 아래 파란색 실행 버튼을 누르세요."
            textSize = 14f
            setTextColor(Color.parseColor("#2E7D32"))
            setPadding(10, 10, 10, 25)
        }

        val startCallBtn = Button(this).apply {
            text = "📞 모의 전화 실행하기"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = createRoundedBackground("#1976D2", 28f)
            setPadding(20, 40, 20, 40)

            setOnClickListener {
                val name = nameInput.text.toString().ifBlank { "이름 없음" }
                val number = numberInput.text.toString().ifBlank { "010-0000-0000" }

                val (delayMillis, timeLabel) = when (timeRadioGroup.checkedRadioButtonId) {
                    101 -> Pair(5000L, "5초")
                    102 -> Pair(15000L, "15초")
                    103 -> Pair(60000L, "1분")
                    104 -> Pair(300000L, "5분")
                    else -> Pair(5000L, "5초")
                }

                // 기존 예약된 전화가 있으면 취소하고 새로 예약
                callHandler.removeCallbacksAndMessages(null)

                statusText.text = "⏳ 예약됨: $timeLabel 뒤에 [$name]님에게 전화가 옵니다!\n지금 휴대폰 화면을 끄고 대기하세요."
                statusText.setTextColor(Color.parseColor("#D84315"))

                Toast.makeText(
                    context,
                    "✅ $timeLabel 뒤에 전화가 울립니다! 화면을 끄고 기다리세요.",
                    Toast.LENGTH_LONG
                ).show()

                callHandler.postDelayed({
                    triggerIncomingCall(name, number)
                    statusText.text = "💡 준비 완료: 다시 연습하려면 실행 버튼을 누르세요."
                    statusText.setTextColor(Color.parseColor("#2E7D32"))
                }, delayMillis)
            }
        }

        val cancelBtn = Button(this).apply {
            text = "예약된 전화 취소하기"
            textSize = 14f
            setTextColor(Color.parseColor("#555555"))
            background = createRoundedBackground("#EEEEEE", 20f)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 20, 0, 0) }
            layoutParams = params

            setOnClickListener {
                callHandler.removeCallbacksAndMessages(null)
                statusText.text = "🛑 취소됨: 예약된 전화가 취소되었습니다."
                statusText.setTextColor(Color.parseColor("#666666"))
                Toast.makeText(context, "예약된 전화를 취소했습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        step3Card.addView(statusText)
        step3Card.addView(startCallBtn)
        step3Card.addView(cancelBtn)
        mainLayout.addView(step3Card)

        // [하단 설정 카드] 최초 1회 통화 권한 스위치 안내
        val settingCard = createCardLayout()
        settingCard.addView(createHelperText("※ 만약 전화벨이 울리지 않는다면 아래 버튼을 눌러 'HD Voice' 스위치가 켜져 있는지 확인하세요. (최초 1회만 켜두면 됩니다)"))

        val enableAccountBtn = Button(this).apply {
            text = "⚙️ 통화 허용 설정 확인하기 (최초 1회)"
            textSize = 14f
            setTextColor(Color.parseColor("#444444"))
            background = createRoundedBackground("#E0E0E0", 20f)
            setOnClickListener {
                val intent = Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)
                startActivity(intent)
                Toast.makeText(context, "목록에서 'HD Voice' 스위치를 켜주세요!", Toast.LENGTH_LONG).show()
            }
        }
        settingCard.addView(enableAccountBtn)
        mainLayout.addView(settingCard)

        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }

    // 연락처 앱에서 사람을 선택했을 때 이름과 번호를 자동으로 빈칸에 넣어주는 기능
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
                    val selectedNumber = if (numberIndex >= 0) cursor.getString(numberIndex) else ""

                    nameInput.setText(selectedName)
                    numberInput.setText(selectedNumber)
                    Toast.makeText(this, "📒 [$selectedName] 연락처를 불러왔습니다!", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "먼저 하단의 [통화 허용 설정] 버튼을 눌러 'HD Voice'를 켜주세요!", Toast.LENGTH_LONG).show()
        }
    }

    // 예쁜 흰색 둥근 카드를 만들어주는 디자인 도우미 함수들
    private fun createCardLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 45, 45, 45)
            background = createRoundedBackground("#FFFFFF", 32f)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 40) }
            layoutParams = params
            elevation = 6f
        }
    }

    private fun createRoundedBackground(hexColor: String, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(hexColor))
            cornerRadius = radius
        }
    }

    private fun createSectionTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#111111"))
            setPadding(0, 0, 0, 8)
        }
    }

    private fun createHelperText(desc: String): TextView {
        return TextView(this).apply {
            text = desc
            textSize = 13f
            setTextColor(Color.parseColor("#777777"))
            setPadding(0, 0, 0, 20)
        }
    }

    private fun createLabelText(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#444444"))
            setPadding(5, 25, 0, 10)
        }
    }
}

// 갤럭시 기종별 진짜 순정 전화 화면을 띄워주는 심장부
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
        val callerName = extras?.getString("CALLER_NAME") ?: "거래처 김 부장"
        val callerNumber = extras?.getString("CALLER_NUMBER") ?: "010-1234-5678"

        connection.setAddress(Uri.fromParts("tel", callerNumber, null), TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED)
        connection.setRinging()

        return connection
    }
}
