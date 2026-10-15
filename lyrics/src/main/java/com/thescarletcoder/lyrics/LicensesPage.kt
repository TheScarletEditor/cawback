package com.thescarletcoder.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings > Privacy > About > "Open source licenses" (0.5.1): the parts Cawback is built with, and each license in
 * full on tap. [reading] is the index into [Licenses.parts] being read, or -1 for the list.
 */
@Composable
fun LicensesPage(reading: Int, onRead: (Int) -> Unit) {
    val part = Licenses.parts.getOrNull(reading)
    // A license always opens at its top, and Back to the list starts at its top too.
    key(reading) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (part == null) "Open source licenses" else part.license, fontFamily = Fraunces, fontSize = 28.sp, color = L.text,
            modifier = Modifier.semantics { heading() })
        if (part == null) {
            Text("Cawback is free software. It's built with these, each under its own license.",
                fontFamily = Inter, fontSize = 16.sp, color = L.soft)
            Licenses.parts.forEachIndexed { i, p -> PartCard(p) { onRead(i) } }
        } else LicenseText(part)
    } }
}

@Composable
private fun PartCard(part: Licenses.Part, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(L.surface).border(1.dp, L.line, shape)
            .clickable(role = Role.Button, onClickLabel = "Read the license", onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(part.name, fontFamily = Inter, fontSize = 17.sp, color = L.text)
            Text(part.license, fontFamily = Inter, fontSize = 14.sp, color = L.soft)
        }
        SoftText("Read", Modifier.padding(start = 12.dp))
    }
}

/** One license in full, read from the APK off the main thread. */
@Composable
private fun LicenseText(part: Licenses.Part) {
    val context = LocalContext.current
    var text by remember(part) { mutableStateOf(Licenses.cache[part.asset]) }
    LaunchedEffect(part) {
        if (text != null) return@LaunchedEffect
        text = withContext(Dispatchers.IO) {
            runCatching { Licenses.reflow(context.assets.open(part.asset).bufferedReader().use { it.readText() }) }
                .onSuccess { Licenses.cache[part.asset] = it }
                .getOrDefault("This license text is missing from the app. It's in the source code on GitHub.")
        }
    }
    SoftText(part.name)
    text?.let { Text(it, fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, color = L.text) }
}
