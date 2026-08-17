package com.ihy2ln.weaverse.core.util

import java.util.UUID

/**
 * `core`-layer id generator for values that never touch Room (e.g. new
 * block ids created while parsing Markdown). Deliberately separate from
 * `data/db/entity/IdGen.kt`'s identical one-liner — `core` must not depend
 * on `data` for something this trivial.
 */
fun newId(): String = UUID.randomUUID().toString()
