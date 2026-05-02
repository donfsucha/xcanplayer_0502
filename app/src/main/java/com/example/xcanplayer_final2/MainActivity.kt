package com.example.xcanplayer_final2

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cna.xcanplayer.R
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var webView: android.webkit.WebView
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var btnSpeed: TextView
    private lateinit var btnSchedule: ImageButton
    private lateinit var btnRotate: ImageButton

    private lateinit var immersiveHelper: ImmersiveModeHelper
    private lateinit var webController: FondantWebController
    private lateinit var scheduleUiController: ScheduleUiController
    private lateinit var localStore: LocalStore

    private var scheduleList = mutableListOf<ScheduleItem>()
    private val scheduleCheckHandler = Handler(Looper.getMainLooper())

    // 마지막으로 실제 적용한 URL
    private var lastAppliedUrl = ""

    // 스케줄 팝업에서 수정 여부
    private var isScheduleModified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        webView = findViewById(R.id.webView)
        // 1. 웹뷰 엔진 설정: 자바스크립트를 켜서 최신 웹페이지나 영상이 제대로 작동하게 합니다.
        webView.settings.javaScriptEnabled = true

        // (선택) 웹페이지가 화면 크기에 맞게 쏙 들어가도록 설정합니다.
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true

        // 2. 테스트 접속: 화면이 잘 나오는지 확인하기 위해 구글 메인 화면을 불러옵니다.
        // 나중에 이 부분을 사용자님이 원하시는 진짜 영상 주소로 바꾸시면 됩니다!
        fullscreenContainer = findViewById(R.id.fullscreen_container)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnSchedule = findViewById(R.id.btnSchedule)
        btnRotate = findViewById(R.id.btnRotate)

        immersiveHelper = ImmersiveModeHelper(window, window.decorView)
        localStore = LocalStore(this)

        scheduleList = localStore.loadSchedule().toMutableList()
        scheduleList.sortWith(compareBy<ScheduleItem> { it.hour }.thenBy { it.minute })

        scheduleUiController = ScheduleUiController(this, localStore) { updatedList ->
            scheduleList.clear()
            scheduleList.addAll(updatedList)
            scheduleList.sortWith(compareBy<ScheduleItem> { it.hour }.thenBy { it.minute })
            isScheduleModified = true
        }

        webController = FondantWebController(webView, fullscreenContainer) {
            checkAndApplyOrientation()
        }

        webController.setupWebView()
        setupButtons()

        // 첫 실행 시 너무 빨리 붙지 않도록 약간 지연 후 현재 시간대 스케줄 적용
        webView.postDelayed({
            applyScheduleNow(force = true, showToast = false)
        }, 1200L)

        startScheduleChecker()
    }

    override fun onResume() {
        super.onResume()

        // 앱 복귀 시에도 현재 시간대 기준으로 다시 확인
        webView.postDelayed({
            applyScheduleNow(force = false, showToast = false)
        }, 500L)
    }

    private fun checkAndApplyOrientation() {
        if (webController.isVideoPage() || webController.customView != null) {
            if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            immersiveHelper.applyImmersiveMode(true)
        } else {
            if (resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            immersiveHelper.applyImmersiveMode(false)
        }
    }

    private fun setupButtons() {
        btnSpeed.setOnClickListener {
            webController.currentSpeed =
                if (webController.currentSpeed >= 2.0f) 1.0f
                else webController.currentSpeed + 0.25f

            btnSpeed.text = "${webController.currentSpeed}x"
            webController.applySpeed()
        }

        btnRotate.setOnClickListener {
            requestedOrientation =
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
        }

        btnSchedule.setOnClickListener {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            immersiveHelper.applyImmersiveMode(false)
            isScheduleModified = false

            scheduleUiController.showScheduleDialog(scheduleList) {
                checkAndApplyOrientation()

                if (isScheduleModified) {
                    applyScheduleNow(force = true, showToast = true)
                }
            }
        }
    }

    private fun applyScheduleNow(force: Boolean, showToast: Boolean) {
        val targetUrl = getUrlForCurrentTime()
        if (targetUrl.isBlank()) return

        if (!force && normalizeUrl(targetUrl) == normalizeUrl(lastAppliedUrl)) {
            return
        }

        lastAppliedUrl = targetUrl
        webController.loadSmartUrl(targetUrl)

        if (showToast) {
            Toast.makeText(this, "현재 시간대 스케줄로 이동합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getUrlForCurrentTime(): String {
        if (scheduleList.isEmpty()) return FondantDefaults.QT_URL

        val now = Calendar.getInstance()
        val curMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        var matchedUrl = scheduleList.last().url
        for (item in scheduleList) {
            val itemMinutes = item.hour * 60 + item.minute
            if (itemMinutes <= curMinutes) {
                matchedUrl = item.url
            } else {
                break
            }
        }
        return matchedUrl
    }

    private fun startScheduleChecker() {
        scheduleCheckHandler.postDelayed(object : Runnable {
            override fun run() {
                // "지금 시간대" 기준으로 계속 재판단
                applyScheduleNow(force = false, showToast = false)
                scheduleCheckHandler.postDelayed(this, 15000L)
            }
        }, 5000L)
    }

    private fun normalizeUrl(url: String): String {
        return url.trim().removeSuffix("/")
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        super.onDestroy()
        scheduleCheckHandler.removeCallbacksAndMessages(null)
    }

    override fun onBackPressed() {
        if (webController.customView != null) {
            webController.customViewCallback?.onCustomViewHidden()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}