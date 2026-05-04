package com.cnanfc.xcanplayer

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log // 실시간 주소 추적을 위한 로그 도구 추가
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {

    // 언제든 꺼내 쓸 수 있는 정확한 고정 주소(상수) 모음
    companion object {
        private const val LIVING_LIFE_URL =
            "https://www.fondant.kr/series/00090200-0000-0000-0000-00000000071b?category=episode"

        private const val BIBLE_READING_URL =
            "https://www.fondant.kr/series/00090228-5db3-dc44-3c29-52bcaf0002ce?category=episode"
    }

    private lateinit var webView: WebView
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

    private var lastAppliedUrl = ""
    private var isScheduleModified = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        webView = findViewById(R.id.webView)
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

        // ★ 핵심 수정: MainActivity의 자체 웹뷰 설정을 지우고, Controller 하나만 믿고 갑니다!
        webController.setupWebView()
        setupButtons()

        webView.postDelayed({
            applyScheduleNow(force = true, showToast = false)
        }, 1200L)

        startScheduleChecker()
    }

    override fun onResume() {
        super.onResume()
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

        // 로그 추가: 개발자가 실시간으로 이동하려는 주소를 모니터링 할 수 있습니다.
        Log.d("XCAN_SCHEDULE", "force=$force targetUrl=$targetUrl lastAppliedUrl=$lastAppliedUrl")

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

    // ★ 새 기능: 스케줄에 저장된 이름(타이틀)을 분석하여 올바른 URL로 강제 보정합니다.
    private fun resolveScheduleUrl(item: ScheduleItem): String {
        val title = item.title.replace("\\s".toRegex(), "")
        val url = item.url.trim()

        return when {
            title.contains("생명의삶") || url.contains("00090200-0000-0000-0000-00000000071b") -> {
                LIVING_LIFE_URL
            }

            title.contains("성경통독") ||
                    title.contains("일일통독") ||
                    title.contains("통독") ||
                    url.contains("00090228-5db3-dc44-3c29-52bcaf0002ce") -> {
                if (url.isNotBlank()) url else BIBLE_READING_URL
            }

            else -> url
        }
    }

    // 보정 로직이 탑재된 시간표 확인 기능
    private fun getUrlForCurrentTime(): String {
        if (scheduleList.isEmpty()) return BIBLE_READING_URL

        val now = Calendar.getInstance()
        val curMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val sortedList = scheduleList.sortedWith(
            compareBy<ScheduleItem> { it.hour }.thenBy { it.minute }
        )

        var matchedUrl = resolveScheduleUrl(sortedList.last())

        for (item in sortedList) {
            val itemMinutes = item.hour * 60 + item.minute
            if (itemMinutes <= curMinutes) {
                matchedUrl = resolveScheduleUrl(item)
            } else {
                break
            }
        }

        return matchedUrl
    }

    private fun startScheduleChecker() {
        scheduleCheckHandler.postDelayed(object : Runnable {
            override fun run() {
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