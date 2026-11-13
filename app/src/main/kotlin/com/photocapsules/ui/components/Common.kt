package com.photocapsules.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
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
    val help = Help.screen(title)
    var showHelp by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Palette.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1)
                        if (help != null) Text(help.subtitle, color = Palette.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    if (onBack != null) Tip("Back") { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
                },
                actions = {
                    actions()
                    if (help != null) Tip("How to use this tool") {
                        IconButton(onClick = { showHelp = true }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, "Help", tint = Palette.TextDim) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Background),
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
    if (showHelp && help != null) AlertDialog(
        onDismissRequest = { showHelp = false },
        title = { Text(title) },
        text = { Text(help.body, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Got it") } },
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
modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Surface).animateContentSize().padding(vertical = 6.dp),
        content = content,
    )
}

/** Section heading with an optional one-line explanation under it. */
@Composable
fun SectionTitle(text: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp)) {
        Text(text, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(subtitle, color = Palette.TextDim, fontSize = 12.sp)
    }
}

/** "Label ........ value" row; clickable when [onClick] is given (value shown in accent colour). */
@Composable
fun ValueRow(label: String, value: String, onClick: (() -> Unit)? = null, valueColor: Color? = null, sub: String? = null, help: String? = Help.row(label)) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Palette.Text, fontSize = 15.sp, modifier = Modifier.weight(1f, fill = false))
                if (help != null) InfoDot(label, help)
            }
            if (sub != null) Text(sub, color = Palette.TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(value, color = valueColor ?: if (onClick != null) Palette.Accent else Palette.Text, fontSize = 15.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
        // A chevron marks the rows that open an editor.
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.TextDim, modifier = Modifier.size(18.dp))
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
    Row(
        Modifier.fillMaxWidth().background(Palette.Surface).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        for ((label, action) in actions) {
            Tip(Help.action(label)) {
                FilledTonalButton(
                    onClick = action,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Palette.SurfaceHigh, contentColor = Palette.Accent),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    actionIcon(label)?.let { Icon(it, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                    Text(label)
                }
            }
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Palette.Accent else Palette.SurfaceHigh, label = "pillBg")
    val fg by animateColorAsState(if (selected) Color.Black else Palette.Text, label = "pillFg")
    Box(
        Modifier.clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
fun PillRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

private fun actionIcon(label: String): ImageVector? = when (label) {
    "AR" -> Icons.Filled.ViewInAr
    "Planner" -> Icons.Filled.Map
    "Share" -> Icons.Filled.Share
    "Timer" -> Icons.Filled.Timer
    "Table", "EV table", "Interval table" -> Icons.Filled.TableChart
    else -> null
}
