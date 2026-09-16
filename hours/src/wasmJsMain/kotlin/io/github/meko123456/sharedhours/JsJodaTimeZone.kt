package io.github.meko123456.sharedhours

/**
 * Pulls the time-zone database into the Wasm bundle. Same reasoning as the JavaScript target: see
 * the `jsMain` copy of this file.
 */
@JsModule("@js-joda/timezone")
internal external object JsJodaTimeZoneModule

private val timeZoneDatabase = JsJodaTimeZoneModule
