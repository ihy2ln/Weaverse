package com.ihy2ln.weaverse.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.shared.Platform

/**
 * First real Compose Multiplatform screen, replacing the plain-SwiftUI
 * walking-skeleton placeholder on iOS. Ported screens land here (or in
 * sibling files under this package) as the full port proceeds; this file
 * is the entry point [com.ihy2ln.weaverse.shared.MainViewController] hosts.
 */
@Composable
fun RootScreen() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Weaverse",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Shared Compose Multiplatform UI",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Running on ${Platform().name}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
