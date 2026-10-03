package uz.abumme.harfgame.data

// Registers the IANA timezone database with js-joda for the browser tests; without it TimeZone.of("Europe/Moscow")
// throws. Same loader as sharedUI's PlatformModule.js.kt.
@JsModule("@js-joda/timezone")
@JsNonModule
external object JsJodaTimeZoneModule

private val jsJodaTz = JsJodaTimeZoneModule
