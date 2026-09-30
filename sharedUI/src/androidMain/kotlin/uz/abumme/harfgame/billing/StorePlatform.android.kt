package uz.abumme.harfgame.billing

actual val hasAppStore: Boolean = true

/** Play grants refunds from the buyer's order history. */
actual val storeRefundUrl: String? = "https://play.google.com/store/account/orderhistory"
