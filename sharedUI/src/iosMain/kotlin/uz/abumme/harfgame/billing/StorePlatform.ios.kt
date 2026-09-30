package uz.abumme.harfgame.billing

actual val hasAppStore: Boolean = true

/** Apple grants refunds through Report a Problem, not through the app. */
actual val storeRefundUrl: String? = "https://reportaproblem.apple.com"
