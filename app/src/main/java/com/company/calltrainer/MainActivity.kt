package com.company.calltrainer

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// 1. 직원이 이름/번호를 치고 내 폰으로 전화를 거는 셀프 연습 화면
class MainActivity : Activity() {

    private lateinit var telecomManager: TelecomManager
    private lateinit var phoneAccountHandle: PhoneAccountHandle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val componentName = ComponentName(this, FakeCallService::class.java)
        phoneAccountHandle = PhoneAccountHandle(componentName, "HD_Voice_Trainer")

        val phoneAccount = PhoneAccount.builder(phoneAccountHandle, "HD Voice")
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .build()
        telecomManager.registerPhoneAccount(phoneAccount)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 120, 60, 60)
            setBackgroundColor(Color.WHITE)
        }

        val titleText = TextView(this).apply {
            text = "📞 사내 전화응대 셀프 훈련기"
            textSize = 22f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 50)
        }

        val nameInput = EditText(this).apply {
            hint = "발신자 이름 (예: 대한건설 김철수 소장)"
            setText("대한건설 김철수 소장")
        }

        val numberInput = EditText(this).apply {
            hint = "전화번호 (예: 010-9876-5432)"
            setText("010-9876-5432")
        }

        val enableAccountBtn = Button(this).apply {
            text = "① 최초 1회 필수: 갤럭시 순정통화 허용하기"
            setOnClickListener {
                val intent = Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)
                startActivity(intent)
                Toast.makeText(context, "목록에서 'HD Voice' 스위치를 켜주세요!", Toast.LENGTH_LONG).show()
            }
        }

        val callBtn = Button(this).apply {
            text = "② 5초 뒤 내 폰에 전화 걸기 (누르고 화면 끄세요)"
            setOnClickListener {
                val name = nameInput.text.toString()
                val number = numberInput.text.toString()

                Toast.makeText(context, "5초 뒤 [$name]에게 전화가 옵니다! 화면을 끄세요.", Toast.LENGTH_LONG).show()

                Handler(Looper.getMainLooper()).postDelayed({
                    triggerIncomingCall(name, number)
                }, 5000)
            }
        }

        layout.addView(titleText)
        layout.addView(nameInput)
        layout.addView(numberInput)
        layout.addView(enableAccountBtn)
        layout.addView(callBtn)

        setContentView(layout)
    }

    private fun triggerIncomingCall(name: String, number: String) {
        try {
            val extras = Bundle().apply {
                putString("CALLER_NAME", name)
                putString("CALLER_NUMBER", number)
            }
            telecomManager.addNewIncomingCall(phoneAccountHandle, extras)
        } catch (e: SecurityException) {
            Toast.makeText(this, "먼저 ①번 버튼을 눌러 'HD Voice' 스위치를 켜주세요!", Toast.LENGTH_LONG).show()
        }
    }
}

// 2. 갤럭시 기종별 진짜 순정 전화 화면을 그대로 띄워주는 심장부
class FakeCallService : ConnectionService() {
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val connection = object : Connection() {
            override fun onAnswer() {
                super.onAnswer()
                setActive() // 통화 연결됨 (00:01 시간 카운트 시작)
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
