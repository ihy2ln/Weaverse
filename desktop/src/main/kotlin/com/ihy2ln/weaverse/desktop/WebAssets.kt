package com.ihy2ln.weaverse.desktop

import com.ihy2ln.weaverse.sync.web.webAppCss as sharedCss
import com.ihy2ln.weaverse.sync.web.webAppJs as sharedJs
import com.ihy2ln.weaverse.sync.web.webIndexHtml as sharedHtml

fun webIndexHtml(): String = sharedHtml()
fun webAppCss(): String = sharedCss()
fun webAppJs(): String = sharedJs()
