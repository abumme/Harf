@file:OptIn(ExperimentalWasmJsInterop::class)

package uz.abumme.harfgame.data

// Registers the IANA timezone database with js-joda for the browser tests; without it TimeZone.of("Europe/Moscow")
// throws. Same loader as sharedUI's PlatformModule.wasmJs.kt.
@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule

private val jsJodaTz = JsJodaTimeZoneModule
