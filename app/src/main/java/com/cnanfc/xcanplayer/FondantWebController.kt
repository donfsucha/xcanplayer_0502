package com.cnanfc.xcanplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

class FondantWebController(
    private val webView: WebView,
    private val fullscreenContainer: FrameLayout,
    private val onOrientationChangeNeeded: () -> Unit
) {
    companion object {
        private const val BIBLE_SERIES_ID = "00090228-5db3-dc44-3c29-52bcaf0002ce"
        private const val LIVING_LIFE_SERIES_ID = "00090200-0000-0000-0000-00000000071b"

        private const val PREF_NAME = "fondant_runtime"
        private const val KEY_LAST_BIBLE_URL = "key_last_bible_url"
        private const val KEY_LAST_BIBLE_POSITION = "key_last_bible_position"
        // [GPT 수정 1] 초기화 적용 여부를 확인하는 키 추가
        private const val KEY_RESUME_FIX_APPLIED = "key_resume_fix_applied_v2"
    }

    var customView: View? = null
    var customViewCallback: WebChromeClient.CustomViewCallback? = null
    var currentSpeed: Float = 1.0f

    private var currentTargetUrl = ""
    private var isBibleMode = true

    private val prefs by lazy {
        webView.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    inner class AndroidBotInterface {
        @JavascriptInterface
        fun requestPortrait() {
            val activity = webView.context as? Activity
            activity?.runOnUiThread {
                if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    onOrientationChangeNeeded()
                }
            }
        }

        @JavascriptInterface
        fun onBibleProgress(url: String, seconds: Double) {
            saveBibleResumePosition(url, seconds)
        }

        // [GPT 수정 2] 영상이 끝났을 때 위치를 지우는 함수 추가
        @JavascriptInterface
        fun onBibleEpisodeEnded(url: String) {
            clearBibleResumePosition(url)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.setBackgroundColor(Color.TRANSPARENT)

        // [GPT 수정 3] 처음 실행 시 오염된 위치 정보 1회 강제 초기화
        clearCorruptedResumePositionOnce()

        try {
            webView.removeJavascriptInterface("AndroidBot")
        } catch (_: Exception) {
        }
        webView.addJavascriptInterface(AndroidBotInterface(), "AndroidBot")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                customView = view
                customViewCallback = callback
                fullscreenContainer.addView(view)
                fullscreenContainer.visibility = View.VISIBLE
                webView.visibility = View.GONE
                onOrientationChangeNeeded()
            }

            override fun onHideCustomView() {
                fullscreenContainer.removeView(customView)
                customView = null
                fullscreenContainer.visibility = View.GONE
                webView.visibility = View.VISIBLE
                customViewCallback?.onCustomViewHidden()
                onOrientationChangeNeeded()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                handleOrientationByUrl(url)

                if (!url.isNullOrBlank() && isBiblePlayableUrl(url)) {
                    saveBibleResumeUrl(url)
                }

                applySpeed()
                injectTouchMagic()
                injectMasterBot()

                if (!url.isNullOrBlank() && isBiblePlayableUrl(url)) {
                    webView.postDelayed({ restoreBiblePositionIfNeeded(url) }, 1200L)
                    webView.postDelayed({ restoreBiblePositionIfNeeded(url) }, 2800L)
                }
            }
        }
    }

    private fun handleOrientationByUrl(url: String?) {
        val activity = webView.context as? Activity ?: return
        if (url == null) return

        if (url.contains("/series/") || url.contains("/watch") || url.contains("/play")) {
            if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                onOrientationChangeNeeded()
            }
        }
    }

    fun loadSmartUrl(url: String) {
        isBibleMode = url.contains(BIBLE_SERIES_ID) ||
                url.contains("bible", ignoreCase = true) ||
                url.contains("통독")

        val finalUrl = if (isBibleMode) {
            getSavedBibleResumeUrl().takeUnless { it.isNullOrBlank() } ?: url
        } else {
            url
        }

        currentTargetUrl = finalUrl
        webView.loadUrl(finalUrl)
    }

    fun isVideoPage(): Boolean =
        webView.url?.let { it.contains("/series/") || it.contains("/watch") || it.contains("/play") }
            ?: false

    fun applySpeed() {
        val js = """
            javascript:(function() {
              window.androidSpeed = $currentSpeed;
              if (window.speedInt) clearInterval(window.speedInt);
              window.speedInt = setInterval(function() {
                var v = document.querySelector('video');
                if (v && v.playbackRate != window.androidSpeed) {
                  v.playbackRate = window.androidSpeed;
                }
              }, 1000);
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    private fun injectTouchMagic() {
        val js = """
            javascript:(function() {
              document.addEventListener('touchstart', function(e) {
                if (e.touches.length > 0) {
                  var touch = e.touches[0];
                  var event = new MouseEvent('mousemove', {
                    view: window,
                    bubbles: true,
                    cancelable: true,
                    clientX: touch.clientX,
                    clientY: touch.clientY
                  });
                  e.target.dispatchEvent(event);
                }
              }, {passive: true});
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    // [GPT 수정 5] 새 영상으로 바뀌면 위치를 0초로 초기화
    private fun saveBibleResumeUrl(url: String) {
        if (!isBiblePlayableUrl(url)) return

        val oldUrl = prefs.getString(KEY_LAST_BIBLE_URL, null)
        val isDifferentVideo = oldUrl == null || normalizeUrl(oldUrl) != normalizeUrl(url)

        val editor = prefs.edit()
            .putString(KEY_LAST_BIBLE_URL, url)

        if (isDifferentVideo) {
            editor.putFloat(KEY_LAST_BIBLE_POSITION, 0f)
        }

        editor.apply()
    }

    private fun getSavedBibleResumeUrl(): String? {
        return prefs.getString(KEY_LAST_BIBLE_URL, null)
    }

    private fun saveBibleResumePosition(url: String, seconds: Double) {
        if (!isBiblePlayableUrl(url)) return
        if (seconds < 1.0) return

        prefs.edit()
            .putString(KEY_LAST_BIBLE_URL, url)
            .putFloat(KEY_LAST_BIBLE_POSITION, seconds.toFloat())
            .apply()
    }

    private fun getSavedBibleResumePosition(): Double {
        return prefs.getFloat(KEY_LAST_BIBLE_POSITION, 0f).toDouble()
    }

    // [GPT 수정 4] 오염된 위치 한 번 정리 및 강제 삭제 함수
    private fun clearCorruptedResumePositionOnce() {
        if (prefs.getBoolean(KEY_RESUME_FIX_APPLIED, false)) return

        prefs.edit()
            .putFloat(KEY_LAST_BIBLE_POSITION, 0f)
            .putBoolean(KEY_RESUME_FIX_APPLIED, true)
            .apply()
    }

    private fun clearBibleResumePosition(url: String) {
        if (!isBiblePlayableUrl(url)) return

        prefs.edit()
            .putString(KEY_LAST_BIBLE_URL, url)
            .putFloat(KEY_LAST_BIBLE_POSITION, 0f)
            .apply()
    }

    // [GPT 수정 6] 영상 끝부분이면 복원하지 않음
    private fun restoreBiblePositionIfNeeded(url: String) {
        val savedUrl = getSavedBibleResumeUrl() ?: return
        val savedPosition = getSavedBibleResumePosition()

        if (savedPosition < 2.0) return
        if (normalizeUrl(savedUrl) != normalizeUrl(url)) return

        val js = """
            javascript:(function() {
              try {
                var v = document.querySelector('video');
                if (!v) return;

                var saved = $savedPosition;
                var duration = Number(v.duration || 0);

                // 저장 위치가 영상 끝부분이면 복원하지 않음
                if (duration > 0 && saved >= duration - 3.0) {
                  return;
                }

                if (saved < 2.0) return;

                if (Math.abs((v.currentTime || 0) - saved) > 2.0) {
                  v.currentTime = saved;
                }
              } catch(e) {}
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    private fun isBiblePlayableUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(BIBLE_SERIES_ID.lowercase()) &&
                (lower.contains("/play?") || lower.contains("/play/") || lower.contains("/watch"))
    }

    private fun normalizeUrl(url: String): String {
        return url.trim().removeSuffix("/")
    }

    private fun jsEscape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    private fun injectMasterBot() {
        val savedBibleUrl = jsEscape(getSavedBibleResumeUrl() ?: "")
        val pendingUrl = jsEscape(currentTargetUrl)

        val js = """
            javascript:(function() {
              if (window.botInt) clearInterval(window.botInt);
              if (!window.lastPlayClickTime) window.lastPlayClickTime = 0;

              function triggerClick(el) {
                if (!el) return false;
                try {
                  el.click();
                  var ev = new MouseEvent('click', {
                    bubbles: true,
                    cancelable: true,
                    view: window
                  });
                  el.dispatchEvent(ev);
                  return true;
                } catch (e) {
                  return false;
                }
              }

              function normalizeText(text) {
                return (text || '').replace(/\s+/g, ' ').trim();
              }

              function isPlayableHref(h) {
                if (!h) return false;
                return (
                  h.indexOf('/watch') >= 0 ||
                  h.indexOf('/play?') >= 0 ||
                  h.indexOf('/play/') >= 0 ||
                  /\/play($|[?#])/.test(h)
                );
              }

              function isHomeLike(url) {
                if (!url) return false;
                return (
                  url === 'https://www.fondant.kr' ||
                  url === 'https://www.fondant.kr/' ||
                  url === 'https://fondant.kr' ||
                  url === 'https://fondant.kr/' ||
                  url.indexOf('https://www.fondant.kr/main') === 0 ||
                  url.indexOf('https://fondant.kr/main') === 0 ||
                  url.indexOf('https://www.fondant.kr/?') === 0 ||
                  url.indexOf('https://fondant.kr/?') === 0
                );
              }

              function isBibleSeriesPage(url) {
                return (
                  url.indexOf('/series/00090228-5db3-dc44-3c29-52bcaf0002ce') >= 0 &&
                  url.indexOf('/play') < 0 &&
                  url.indexOf('/watch') < 0
                );
              }

              function isLivingLifeSeriesPage(url) {
                return (
                  url.indexOf('/series/00090200-0000-0000-0000-00000000071b') >= 0 &&
                  url.indexOf('/play') < 0 &&
                  url.indexOf('/watch') < 0
                );
              }

              function clickLatestSortIfNeeded() {
                var tags = document.querySelectorAll('button, a, div, span, p');
                for (var i = 0; i < tags.length; i++) {
                  var text = (tags[i].innerText || '').replace(/\s/g, '');
                  if (text === '최신화부터') {
                    return triggerClick(tags[i]);
                  }
                }
                return false;
              }

              function findLivingLifeLatestEpisodeLink() {
                var nodes = Array.prototype.slice.call(
                  document.querySelectorAll('div, span, p, h1, h2, h3, button, a')
                );

                var cutoffTop = 0;

                for (var i = 0; i < nodes.length; i++) {
                  var text = normalizeText(nodes[i].innerText || nodes[i].textContent || '').replace(/\s/g, '');
                  if (
                    text === '회차' ||
                    text.indexOf('최신화부터') >= 0 ||
                    text.indexOf('첫화부터') >= 0 ||
                    text.indexOf('[큐티]생명의삶') >= 0
                  ) {
                    var r = nodes[i].getBoundingClientRect();
                    if (r && r.top > cutoffTop) cutoffTop = r.top;
                  }
                }

                var links = Array.prototype.slice.call(document.querySelectorAll('a'));
                var candidates = [];

                for (var j = 0; j < links.length; j++) {
                  var a = links[j];
                  var h = a.href || '';
                  if (!isPlayableHref(h)) continue;

                  var txt = normalizeText(a.innerText || a.textContent || '');
                  var cleanTxt = txt.replace(/\s/g, '');

                  if (
                    cleanTxt.indexOf('이어보기') >= 0 ||
                    cleanTxt.indexOf('계속') >= 0
                  ) {
                    continue;
                  }

                  var rect = a.getBoundingClientRect();
                  if (!rect) continue;

                  var visible = rect.width > 0 && rect.height > 0;
                  if (!visible) continue;

                  if (rect.top <= cutoffTop + 20) continue;

                  candidates.push({
                    el: a,
                    top: rect.top
                  });
                }

                candidates.sort(function(a, b) {
                  return a.top - b.top;
                });

                return candidates.length > 0 ? candidates[0].el : null;
              }

              function findTopPlayableLink() {
                var links = Array.prototype.slice.call(document.querySelectorAll('a'));
                var candidates = [];

                for (var i = 0; i < links.length; i++) {
                  var a = links[i];
                  var h = a.href || '';
                  if (!isPlayableHref(h)) continue;

                  var rect = a.getBoundingClientRect();
                  var visible = rect.width > 0 && rect.height > 0;
                  if (visible && rect.top > 100) {
                    candidates.push({ el: a, top: rect.top });
                  }
                }

                candidates.sort(function(a, b) {
                  return a.top - b.top;
                });

                return candidates.length > 0 ? candidates[0].el : null;
              }

              window.botInt = setInterval(function() {
                var v = document.querySelector('video');
                var u = window.location.href;
                var now = Date.now();

                if (v) {
                  if (window.androidSpeed && v.playbackRate !== window.androidSpeed) {
                    v.playbackRate = window.androidSpeed;
                  }
                  v.muted = false;
                  try { v.volume = 1.0; } catch(e) {}

                  // [GPT 수정 7] 영상 끝부분 3초 이내는 위치를 저장하지 않음
                  if (
                    u.indexOf('00090228-5db3-dc44-3c29-52bcaf0002ce') >= 0 &&
                    (u.indexOf('/play?') >= 0 || u.indexOf('/play/') >= 0 || u.indexOf('/watch') >= 0)
                  ) {
                    try {
                      var current = Number(v.currentTime || 0);
                      var duration = Number(v.duration || 0);
                      var remain = duration > 0 ? duration - current : 9999;

                      if (current > 1.0 && remain > 3.0 && !v.ended) {
                        AndroidBot.onBibleProgress(u, current);
                      }
                    } catch(e) {}
                  }

                  if (
                    u.indexOf('00090228-5db3-dc44-3c29-52bcaf0002ce') >= 0 &&
                    v.duration > 0 &&
                    (v.duration - v.currentTime <= 1.0 || v.ended)
                  ) {
                    // [GPT 수정 8] 다음 회차 넘어가기 직전에 확실하게 위치 0초로 정리
                    try {
                      AndroidBot.onBibleEpisodeEnded(u);
                    } catch(e) {}

                    var tags = document.querySelectorAll('button, a, div, span, p');
                    for (var i = tags.length - 1; i >= 0; i--) {
                      var rawTxt = tags[i].innerText || '';
                      var clean = rawTxt.replace(/\s/g, '');
                      if (rawTxt.length < 30 && clean.indexOf('다음회차') >= 0) {
                        if (triggerClick(tags[i])) {
                          window.lastPlayClickTime = now;
                          return;
                        }
                      }
                    }
                  }

                  if (v.paused && v.currentTime < 1.0) {
                    var p = v.play();
                    if (p !== undefined) p.catch(function(e){});
                  }

                  return;
                }

                if (isHomeLike(u)) {
                  if (now - window.lastPlayClickTime > 2500) {
                    var targetUrl = '${pendingUrl}';
                    if (targetUrl) {
                      window.location.href = targetUrl;
                      window.lastPlayClickTime = now;
                      return;
                    }
                  }
                }

                if (u.indexOf('/series/') >= 0 || u.indexOf('/watch') >= 0 || u.indexOf('/play') >= 0) {
                  var links = document.querySelectorAll('a');
                  for (var i = 0; i < links.length; i++) {
                    var el = links[i];
                    var txt = (el.innerText || '').replace(/\s/g, '');
                    if (txt.indexOf('로그인') >= 0) {
                      try { AndroidBot.requestPortrait(); } catch(e) {}
                      triggerClick(el);
                      return;
                    }
                  }
                }

                if (isBibleSeriesPage(u)) {
                  if (now - window.lastPlayClickTime > 2500) {
                    var resumeUrl = '${savedBibleUrl}';

                    if (resumeUrl) {
                      window.location.href = resumeUrl;
                      window.lastPlayClickTime = now;
                      return;
                    }

                    var firstPlayable = findTopPlayableLink();
                    if (firstPlayable) {
                      if (triggerClick(firstPlayable)) {
                        window.lastPlayClickTime = now;
                        return;
                      }
                    }
                  }
                }

                if (isLivingLifeSeriesPage(u)) {
                  if (now - window.lastPlayClickTime > 2500) {
                    clickLatestSortIfNeeded();

                    var latestEpisode = findLivingLifeLatestEpisodeLink();
                    if (latestEpisode) {
                      if (triggerClick(latestEpisode)) {
                        window.lastPlayClickTime = now;
                        return;
                      }
                    }

                    var topPlayable = findTopPlayableLink();
                    if (topPlayable) {
                      if (triggerClick(topPlayable)) {
                        window.lastPlayClickTime = now;
                        return;
                      }
                    }
                  }
                }

              }, 1500);
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }
}