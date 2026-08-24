package com.ihy2ln.weaverse.shared

/**
 * First real piece of code shared across every target this module builds for
 * (Android, iOS, desktop JVM) — proves the expect/actual wiring works end to
 * end before any real domain logic moves in here. See docs/IOS-PORT-PLAN.md.
 */
expect class Platform() {
    val name: String
}

fun greeting(): String = "Weaverse shared module running on ${Platform().name}"
