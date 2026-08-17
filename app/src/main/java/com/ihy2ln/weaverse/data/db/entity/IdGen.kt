package com.ihy2ln.weaverse.data.db.entity

import java.util.UUID

/** Every entity's primary key default — spec §4: "String UUID primary keys everywhere." */
fun newId(): String = UUID.randomUUID().toString()

/** Every entity's timestamp default — spec §4: "Instant-as-epoch-millis timestamps." */
fun nowEpochMillis(): Long = System.currentTimeMillis()
