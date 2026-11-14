# 課堂即時英語翻譯（Android）
手機麥克風收英文 → Android 語音辨識 → ML Kit 英文轉中文 → Hans-Hant 繁體轉換。

## 下載安裝
進入 GitHub 的 **Actions** → **Build Android APK** → 最新成功的執行紀錄 → **Artifacts** → 下載 **EnglishTranslator-APK**。解壓縮取得 **app-debug.apk**，傳到 Android 手機後開啟並安裝。

初次使用需要網路下載翻譯模型，允許麥克風權限。課堂使用時保持畫面開啟；可匯出英文與繁體中文對照 TXT。

**限制：** 使用 Android 系統語音辨識的逐段重啟方式，不是真正無縫串流；長時間課堂可能漏句，需實機測試。文字翻譯採裝置端 ML Kit，英文語音辨識服務可能使用網路。程式尚未經手機實機驗證。
