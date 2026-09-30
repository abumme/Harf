package uz.abumme.harfgame.billing

/**
 * Whether this platform has an app store behind [PurchaseController]. False on desktop and web, where
 * purchases are a no-op and cosmetics are free, so screens can say so instead of offering a dead flow.
 */
expect val hasAppStore: Boolean

/**
 * Where the platform grants refunds — Apple's Report a Problem, Google Play's order history — or null
 * where there is no store. The store owns the refund, so the app links out rather than pretending to
 * handle it.
 */
expect val storeRefundUrl: String?
