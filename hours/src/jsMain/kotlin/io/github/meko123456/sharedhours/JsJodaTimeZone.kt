package io.github.meko123456.sharedhours

/**
 * Pulls the time-zone database into the JavaScript bundle.
 *
 * Every other target has a tz database without asking: the JVM and Android carry one, Apple
 * platforms carry one. JavaScript carries none, so kotlinx-datetime reads one from the
 * `@js-joda/timezone` npm package — and declaring that package as a dependency is necessary but not
 * sufficient. The data only reaches js-joda when the module is actually imported, and a package
 * nothing references is precisely what a bundler drops.
 *
 * Without this file the library compiles cleanly for JS and then throws `IllegalTimeZoneException`
 * on every zone lookup, which is to say all of it fails. That is the reason this target's tests run
 * on Node instead of it being published compiled-only: the break is invisible to the compiler.
 *
 * The property below is what keeps the import: it exists to be imported, not to be used.
 */
@JsModule("@js-joda/timezone")
@JsNonModule
internal external object JsJodaTimeZoneModule

private val timeZoneDatabase = JsJodaTimeZoneModule
