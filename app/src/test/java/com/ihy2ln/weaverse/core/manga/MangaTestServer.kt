package com.ihy2ln.weaverse.core.manga

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

internal fun mangaDispatcher(handler: (RecordedRequest) -> MockResponse): Dispatcher =
    object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = handler(request)
    }
