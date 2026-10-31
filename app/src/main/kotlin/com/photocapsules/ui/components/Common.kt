package com.photocapsules.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.ui.theme.Palette

@Composable
fun AppScreen(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Palette.Background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Background),
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
}

/** Scrollable column with standard padding for calculator screens. */
@Composable
fun ScrollColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Palette.Surface, RoundedCornerShape(12.dp)).padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), color = Palette.TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp))
}

/** "Label ........ value" row; clickable when [onClick] is given (value shown in accent colour). */
@Composable
fun ValueRow(label: String, value: String, onClick: (() -> Unit)? = null, valueColor: Color? = null, sub: String? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Palette.Text, fontSize = 15.sp)
            if (sub != null) Text(sub, color = Palette.TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(value, color = valueColor ?: if (onClick != null) Palette.Accent else Palette.Text, fontSize = 15.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

@Composable
fun Divider() = HorizontalDivider(color = Palette.Divider, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 14.dp))

@Composable
fun BigResult(label: String, value: String, color: Color = Palette.Accent) {
    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Palette.TextDim, fontSize = 13.sp)
        Text(value, color = color, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Tabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    TabRow(selectedTabIndex = selected, containerColor = Palette.Background, contentColor = Palette.Accent) {
        titles.forEachIndexed { i, t -> Tab(selected = i == selected, onClick = { onSelect(i) }, text = { Text(t, maxLines = 1) }) }
    }
}

/** Numeric input dialog. Accepts "," or "." as decimal separator. */
@Composable
fun NumberDialog(
    title: String,
    initial: Double,
    unit: String = "",
    min: Double = -Double.MAX_VALUE,
    max: Double = Double.MAX_VALUE,
    allowNegative: Boolean = false,
    onDismiss: () -> Unit,
    onValue: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(if (initial.isFinite()) trimNumber(initial) else "") }
    val parsed = text.replace(',', '.').toDoubleOrNull()
    val valid = parsed != null && parsed.isFinite() && parsed >= min && parsed <= max && (allowNegative || parsed >= 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    suffix = { if (unit.isNotEmpty()) Text(unit) },
                    keyboardOptions = KeyboardOptions(keyboardType = if (allowNegative) KeyboardType.Text else KeyboardType.Decimal),
                    isError = !valid,
                )
                if (!valid) Text(rangeHint(min, max), color = Palette.Red, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onValue(parsed!!); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun rangeHint(min: Double, max: Double): String = when {
    min > -Double.MAX_VALUE && max < Double.MAX_VALUE -> "Enter a value between ${trimNumber(min)} and ${trimNumber(max)}"
    min > -Double.MAX_VALUE -> "Enter a value ≥ ${trimNumber(min)}"
    else -> "Enter a valid number"
}

fun trimNumber(v: Double): String {
    val s = String.format(java.util.Locale.US, "%.6f", v)
    return s.trimEnd('0').trimEnd('.')
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<T>, label: (T) -> String, selected: T?, onDismiss: () -> Unit, onSelect: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                items(options) { o ->
                    Text(
                        label(o),
                        color = if (o == selected) Palette.Accent else Palette.Text,
                        fontWeight = if (o == selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(o); onDismiss() }.padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, onDismiss: () -> Unit, multiline: Boolean = false, onValue: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = !multiline, minLines = if (multiline) 4 else 1) },
        confirmButton = { TextButton(onClick = { onValue(text); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun InfoText(text: String) {
    Text(text, color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
}

@Composable
fun BottomActions(vararg actions: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth().background(Palette.Surface).padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        for ((label, action) in actions) {
            TextButton(onClick = action) { Text(label, color = Palette.Accent) }
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.background(if (selected) Palette.Accent else Palette.SurfaceHigh, RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, color = if (selected) Color.Black else Palette.Text, fontSize = 13.sp) }
}

@Composable
fun PillRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, o -> Pill(o, i == selected) { onSelect(i) } }
    }
}

@Composable
fun SimpleTable(headers: List<String>, rows: List<List<String>>, highlightRow: Int? = null, onRowClick: ((Int) -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(Palette.SurfaceHigh).padding(vertical = 6.dp)) {
            headers.forEach { Text(it, Modifier.weight(1f), color = Palette.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center) }
        }
        rows.forEachIndexed { i, r ->
            Row(
                Modifier.fillMaxWidth().background(if (i == highlightRow) Palette.SurfaceHigh else Color.Transparent)
                    .let { if (onRowClick != null) it.clickable { onRowClick(i) } else it }.padding(vertical = 7.dp),
            ) {
                r.forEach { Text(it, Modifier.weight(1f), color = Palette.Text, fontSize = 13.sp, textAlign = TextAlign.Center) }
            }
            HorizontalDivider(color = Palette.Divider, thickness = 0.5.dp)
        }
    }
}
