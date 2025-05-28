/**
 * MIT License
 * LoopCut Mini - Negative Speech Detection App
 * Copyright (c) 2025
 */
package com.loopcut

import android.content.Context
import java.text.Normalizer
import java.util.*
import kotlin.collections.ArrayDeque

class LoopDetector(private val context: Context) {
    private val negativeWords: HashSet<String>
    private val stopWords: HashSet<String>
    private val tokenDeque = ArrayDeque<String>()
    private val maxTokens = 200 // 30秒相当
    private var lastTriggerTime = 0L
    private val cooldownMs = 5 * 60 * 1000L // 5分
    
    // 否定パターンの正規表現
    private val negationPattern = Regex(".*?(ない|なくて|なかった)$")
    private val selfBlamePattern = Regex("(私|俺|自分).*?(ダメ|くず|最悪)")
    
    init {
        // ネガティブ語辞書をロード
        negativeWords = HashSet()
        val negWordsArray = context.resources.getStringArray(com.anonymous.loopcutmini.R.array.negative_words)
        negativeWords.addAll(negWordsArray)
        
        // ストップワード（誤検知防止）
        stopWords = HashSet<String>().apply {
            add("じゃない")
            add("じゃないか")
            add("じゃないかな")
            add("じゃない？")
            add("ないか？")
            add("ないかな？")
        }
    }
    
    /**
     * テキストを正規化してトークンに分割
     */
    private fun normalizeAndTokenize(text: String): List<String> {
        // UTF-8 lowercase
        var normalized = text.lowercase(Locale.getDefault())
        
        // 全角→半角変換
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKD)
        
        // カタカナ→ひらがな変換
        normalized = normalized.map { char ->
            when (char) {
                in 'ァ'..'ヶ' -> (char - 'ァ' + 'ぁ')
                else -> char
            }
        }.joinToString("")
        
        // 句読点・英数字・記号をスペースに置換
        normalized = normalized.replace(Regex("[^ぁ-ん]"), " ")
        
        // 連続スペース縮約とトークン分割
        return normalized.split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.length >= 2 }
    }
    
    /**
     * トークンがネガティブ語かチェック
     */
    private fun isNegativeToken(token: String): Boolean {
        // ストップワードチェック（否定疑問など）
        if (stopWords.any { token.contains(it) }) {
            return false
        }
        
        // 完全一致チェック
        if (negativeWords.contains(token)) {
            return true
        }
        
        // 否定終止パターン
        if (negationPattern.matches(token)) {
            return true
        }
        
        // 自責フレーズパターン
        if (selfBlamePattern.containsMatchIn(token)) {
            return true
        }
        
        return false
    }
    
    /**
     * 音声テキストを解析してネガティブ語数をカウント
     * @param transcribedText Whisperからの文字起こし結果
     * @return トリガーするかどうか
     */
    fun processText(transcribedText: String): Boolean {
        val tokens = normalizeAndTokenize(transcribedText)
        
        // トークンをdequeに追加
        tokens.forEach { token ->
            tokenDeque.addLast(token)
            
            // 最大サイズを超えたら古いトークンを削除
            if (tokenDeque.size > maxTokens) {
                tokenDeque.removeFirst()
            }
        }
        
        // ネガティブ語カウント
        val negativeCount = tokenDeque.count { isNegativeToken(it) }
        
        // トリガー判定
        if (negativeCount >= 3) {
            val currentTime = System.currentTimeMillis()
            
            // クールダウンチェック
            if (currentTime - lastTriggerTime >= cooldownMs) {
                lastTriggerTime = currentTime
                return true
            }
        }
        
        return false
    }
    
    /**
     * デバッグ用：現在のdeque状態とネガティブ語カウントを取得
     */
    fun getDebugInfo(): String {
        val negCount = tokenDeque.count { isNegativeToken(it) }
        return "Tokens: ${tokenDeque.size}, Negative: $negCount, Last: ${tokenDeque.lastOrNull()}"
    }
    
    /**
     * dequeをクリア
     */
    fun clear() {
        tokenDeque.clear()
    }
}
