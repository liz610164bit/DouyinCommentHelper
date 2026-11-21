package com.example.douyincommenthelper

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class CommentAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false
    private var generation = 0
    private var lastSnapshot = ""
    private var lastActionAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (!isTargetPackage(pkg)) return

        val prefs = getSharedPreferences(ScanState.PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(ScanState.RUNNING, false)) return

        if (!scanning) {
            scanning = true
            val current = ++generation
            handler.postDelayed({ scan(current) }, 300)
        }
    }

    override fun onInterrupt() {
        stop(ScanState.STATUS_STOPPED)
    }

    override fun onDestroy() {
        generation++
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun scan(expected: Int) {
        if (expected != generation) return

        val prefs = getSharedPreferences(ScanState.PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(ScanState.RUNNING, false)) {
            scanning = false
            return
        }

        val keywords = prefs.getString(ScanState.KEYWORDS, "").orEmpty()
            .split(',', '，', '\n')
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }

        if (keywords.isEmpty()) {
            stop(ScanState.STATUS_NO_KEYWORDS)
            return
        }

        val root = rootInActiveWindow
        if (root == null || !isTargetPackage(root.packageName?.toString().orEmpty())) {
            pause(ScanState.STATUS_NO_CONTAINER)
            return
        }

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        findScrollableCandidates(root, candidates)

        if (candidates.isEmpty()) {
            saveDiagnostics(root)
            pause(ScanState.STATUS_NO_CONTAINER)
            return
        }
        val ranked = candidates.map { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val texts = mutableListOf<String>()
            collectVisibleTexts(node, texts)
            Triple(node, rect, texts)
        }.filter { (_, rect, texts) ->
            rect.height() >= resources.displayMetrics.heightPixels * 0.18f &&
                rect.bottom >= resources.displayMetrics.heightPixels * 0.45f &&
                texts.size >= 2
        }.sortedByDescending { (_, rect, texts) ->
            rect.height() * 2 + texts.sumOf { it.length.coerceAtMost(80) }
        }

        if (ranked.isEmpty()) {
            saveDiagnostics(root)
            pause(ScanState.STATUS_NO_CONTAINER)
            return
        }

        val best = ranked.first()
        if (ranked.size > 1) {
            val second = ranked[1]
            val firstScore = best.second.height() * 2 +
                best.third.sumOf { it.length.coerceAtMost(80) }
            val secondScore = second.second.height() * 2 +
                second.third.sumOf { it.length.coerceAtMost(80) }

            if (secondScore >= firstScore * 0.85) {
                saveDiagnostics(root)
                pause(ScanState.STATUS_NO_CONTAINER)
                return
            }
        }

        val comments = best.third.distinct()
        val snapshot = comments.joinToString("|")

        if (snapshot.isBlank()) {
            pause(ScanState.STATUS_NO_COMMENT_TEXT)
            return
        }

        if (snapshot == lastSnapshot) {
            pause(ScanState.STATUS_NO_PROGRESS)
            return
        }
        lastSnapshot = snapshot

        val hit = comments.firstOrNull { text ->
            val normalized = text.lowercase(Locale.ROOT)
            keywords.any { normalized.contains(it) }
        }

        if (hit != null) {
            prefs.edit().putString(ScanState.LAST_HIT_TEXT, hit).apply()
            stop(ScanState.STATUS_HIT)
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastActionAt < 700) {
            scanning = false
            return
        }
        lastActionAt = now

        if (!best.first.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            pause(ScanState.STATUS_CANNOT_SCROLL)
            return
        }

        handler.postDelayed({
            if (expected == generation) {
                scanning = false
                scan(expected)
            }
        }, 900)
    }

    private fun findScrollableCandidates(
        node: AccessibilityNodeInfo,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.isVisibleToUser && node.isScrollable) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val screenHeight = resources.displayMetrics.heightPixels

            if (!rect.isEmpty &&
                rect.bottom > screenHeight * 0.45f &&
                rect.height() > screenHeight * 0.18f
            ) {
                result.add(node)
            }
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                findScrollableCandidates(it, result)
            }
        }
    }

    private fun collectVisibleTexts(
        node: AccessibilityNodeInfo,
        result: MutableList<String>
    ) {
        if (!node.isVisibleToUser) return

        val value = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()

        val rect = Rect()
        node.getBoundsInScreen(rect)

        val text = if (value.length >= 2) value else description
        if (text.length >= 2 && !rect.isEmpty && text.length <= 500) {
            val lower = text.lowercase(Locale.ROOT)
            val excluded = listOf("点赞", "回复", "举报", "展开", "收起", "更多")
            if (excluded.none { lower == it }) {
                result.add(text)
            }
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                collectVisibleTexts(it, result)
            }
        }
    }

    private fun saveDiagnostics(root: AccessibilityNodeInfo) {
        val output = StringBuilder()
        var count = 0

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (count >= 150 || depth > 14) return
            count++

            val rect = Rect()
            node.getBoundsInScreen(rect)

            output.append("  ".repeat(depth.coerceAtMost(8)))
                .append(node.className?.toString()?.substringAfterLast('.'))
                .append(" | id=").append(node.viewIdResourceName.orEmpty())
                .append(" | scroll=").append(node.isScrollable)
                .append(" | text=").append(node.text?.toString().orEmpty())
                .append(" | bounds=").append(rect.flattenToString())
                .append('\n')

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { visit(it, depth + 1) }
            }
        }

        visit(root, 0)

        getSharedPreferences(ScanState.PREFS, MODE_PRIVATE).edit()
            .putString(ScanState.DIAGNOSTICS, output.toString())
            .apply()
    }

    private fun pause(status: String) {
        stop(status)
    }

    private fun stop(status: String) {
        generation++
        scanning = false
        handler.removeCallbacksAndMessages(null)

        getSharedPreferences(ScanState.PREFS, MODE_PRIVATE).edit()
            .putBoolean(ScanState.RUNNING, false)
            .putString(ScanState.STATUS, status)
            .apply()
    }

    private fun isTargetPackage(pkg: String): Boolean =
        pkg == "com.ss.android.ugc.aweme" ||
        pkg == "com.ss.android.ugc.live" ||
        pkg == "com.zhiliaoapp.musically"
}
