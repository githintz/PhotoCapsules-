package com.photocapsules.feature.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.photocapsules.ui.components.Tip
import com.photocapsules.ui.components.pressScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.feature.ar.ArMode
import com.photocapsules.ui.Routes
import com.photocapsules.ui.components.Card
import com.photocapsules.ui.components.InfoText
import com.photocapsules.ui.components.SectionTitle
import com.photocapsules.ui.components.ValueRow
import com.photocapsules.ui.theme.Palette

private data class Tool(val label: String, val icon: ImageVector, val color: Color, val route: String, val summary: String, val details: String)

private data class ToolGroup(val title: String, val subtitle: String, val tools: List<Tool>)

private val toolGroups = listOf(
    ToolGroup("Plan a shot", "Find where and when the light will be right", listOf(
        Tool("Planner", Icons.Filled.Map, Color(0xFFE85D4A), Routes.PLANNER, "Map with Sun, Moon and Milky Way",
            "Place a pin where you will stand and one on your subject, then see where the Sun, Moon and Milky Way will be at any date and time."),
        Tool("Sun", Icons.Filled.WbSunny, Color(0xFFFFB300), Routes.SUN, "Sunrise, sunset, golden hour",
            "Today's sunrise, sunset, twilights, golden and blue hour, plus a monthly calendar and the seasons."),
        Tool("Moon", Icons.Filled.NightsStay, Color(0xFF5C9DFF), Routes.MOON, "Phase, rise and set, supermoons",
            "Moon phase, rise and set times, a phase calendar, and the year's supermoons, perigees and apogees."),
        Tool("Meteor Showers", Icons.Filled.AutoAwesome, Color(0xFFEC407A), Routes.METEORS, "What is active and when it peaks",
            "Every major meteor shower with its peak date and the number of meteors per hour to expect from your location."),
    )),
    ToolGroup("Night sky", "Stars, the Milky Way and mount alignment", listOf(
        Tool("Night AR", Icons.Filled.BlurOn, Color(0xFF5E35B1), Routes.ar(ArMode.NIGHT), "Milky Way over the live camera",
            "Point the phone at the sky to see where the Milky Way, the celestial poles and the star-trail pattern are, at any time of night."),
        Tool("Polar Scope", Icons.Filled.TrackChanges, Color(0xFFD84343), Routes.POLAR, "Align a tracker or mount",
            "Shows where Polaris (or σ Octantis in the south) must sit in your polar scope's reticle to polar-align a star tracker or equatorial mount."),
        Tool("Spot Stars", Icons.Filled.CameraAlt, Color(0xFF7E57C2), Routes.SPOT_STARS, "Longest exposure for sharp stars",
            "The longest shutter time that keeps stars as points for your camera and lens (NPF and 500 rules)."),
        Tool("Star Trails", Icons.Filled.Toll, Color(0xFF3949AB), Routes.STAR_TRAILS, "Trail length for an exposure",
            "How long the star trails get for an exposure time, or the time needed for a trail of a given angle."),
    )),
    ToolGroup("Exposure & timing", "Settings, filters and long exposures", listOf(
        Tool("Exposure", Icons.Filled.Exposure, Color(0xFF8E6CFF), Routes.EXPOSURE, "Equivalent settings, ND filters",
            "Work out the shutter speed, aperture or ISO that keeps the same exposure when you change a setting or add an ND filter."),
        Tool("Time lapse", Icons.Filled.Timelapse, Color(0xFFFF7043), Routes.TIME_LAPSE, "Interval, photos, card space",
            "The shooting interval, number of photos and memory needed for a time-lapse clip."),
        Tool("Timer", Icons.Filled.Timer, Color(0xFFEF5350), Routes.TIMER, "Countdown for long exposures",
            "A countdown that keeps running with the screen off and vibrates when the exposure is done."),
    )),
    ToolGroup("Focus & framing", "Sharpness, field of view and lens choice", listOf(
        Tool("DoF", Icons.Filled.CenterFocusStrong, Color(0xFF2EC4B6), Routes.DOF, "Depth of field and hyperfocal",
            "What will be sharp, from where to where, for a lens, aperture and focus distance."),
        Tool("FoV", Icons.Filled.Crop, Color(0xFF3DDC84), Routes.FOV, "What fits in the frame",
            "The angle of view of a lens and the size of the scene it covers at a distance."),
        Tool("DoF Table", Icons.Filled.TableChart, Color(0xFF26A69A), Routes.DOF_TABLE, "Depth of field at a glance",
            "A reference table of depth of field for one lens across apertures and distances."),
        Tool("Hyperfocal", Icons.Filled.GridOn, Color(0xFF00897B), Routes.HYPERFOCAL, "Hyperfocal distance table",
            "Hyperfocal distance for every focal length and aperture: focus there for maximum sharpness to infinity."),
        Tool("Subject distance", Icons.Filled.Straighten, Color(0xFF66BB6A), Routes.SUBJECT, "How far to stand",
            "The closest distance at which a subject of a given size fits in the frame."),
        Tool("Focal length match", Icons.Filled.CompareArrows, Color(0xFF29B6F6), Routes.FL_MATCH, "Same picture, other camera",
            "The lens and aperture on a second camera that give the same framing and depth of field."),
    )),
)

@Composable
fun MenuScreen(go: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(1) }
    Scaffold(
        containerColor = Palette.Background,
        bottomBar = {
            NavigationBar(containerColor = Palette.Surface) {
                val colors = NavigationBarItemDefaults.colors(selectedIconColor = Palette.Accent, selectedTextColor = Palette.Accent, indicatorColor = Palette.SurfaceHigh)
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Person, null) }, label = { Text("My Stuff") }, colors = colors)
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Apps, null) }, label = { Text("Tools") }, colors = colors)
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.School, null) }, label = { Text("Academy") }, colors = colors)
            }
        },
    ) { padding ->
        when (tab) {
            0 -> MyStuffMenu(padding, go)
            1 -> PillsGrid(padding, go)
            else -> AcademyMenu(padding, go)
        }
    }
}

@Composable
private fun PillsGrid(padding: PaddingValues, go: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    // Sections fade and slide in once, one after the other.
    var shown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val open: (Tool) -> Unit = { t ->
        if (t.route.startsWith("ar/")) com.photocapsules.data.AppState.ar = com.photocapsules.data.ArRequest(com.photocapsules.data.AppState.sky.value)
        go(t.route)
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        val columns = (maxWidth / 190.dp).toInt().coerceIn(2, 4)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Text("PhotoCapsules", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, start = 4.dp))
            Text("Plan the light. Nail the shot.", color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search tools") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear search") } },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            val q = query.trim()
            if (q.isNotEmpty()) {
                val hits = toolGroups.flatMap { it.tools }.filter { it.label.contains(q, true) || it.summary.contains(q, true) || it.details.contains(q, true) }
                Spacer(Modifier.height(12.dp))
                if (hits.isEmpty()) Text("No tool matches \"$q\"", color = Palette.TextDim, modifier = Modifier.padding(8.dp))
                else ToolRows(hits, columns, open)
            } else {
                toolGroups.forEachIndexed { i, g ->
                    AnimatedVisibility(
                        visible = shown,
                        enter = fadeIn(tween(350, delayMillis = i * 70)) + slideInVertically(tween(350, delayMillis = i * 70)) { it / 6 },
                    ) {
                        Column {
                            Text(g.title, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, start = 4.dp))
                            Text(g.subtitle, color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                            ToolRows(g.tools, columns, open)
                        }
                    }
                }
                Text("Tip: press and hold any tool or button to see what it does.", color = Palette.TextDim, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 18.dp, start = 4.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ToolRows(tools: List<Tool>, columns: Int, open: (Tool) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tools.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t -> ToolTile(t, Modifier.weight(1f)) { open(t) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ToolTile(t: Tool, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Tip(t.details, modifier) {
        Row(
            Modifier.fillMaxWidth().height(86.dp).pressScale(interaction).clip(RoundedCornerShape(20.dp)).background(Palette.Surface)
                .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).background(t.color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(t.label, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.summary, color = Palette.TextDim, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MyStuffMenu(padding: PaddingValues, go: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("My Stuff", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
        Card {
            MenuRow(Icons.Filled.Bookmarks, "Plans", "Your saved photo plans") { go(Routes.PLANS) }
            MenuRow(Icons.Filled.Place, "Points of Interest", "Saved locations & nearby places") { go(Routes.POIS) }
            MenuRow(Icons.Filled.Backup, "Backup", "Export / import KMZ") { go(Routes.BACKUP) }
            MenuRow(Icons.Filled.Settings, "Settings", "Units, camera, map, AR…") { go(Routes.SETTINGS) }
        }
    }
}

@Composable
private fun AcademyMenu(padding: PaddingValues, go: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Academy", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
        Card { MenuRow(Icons.Filled.MenuBook, "Glossary & how-to", "Light types, azimuth, DoF, NPF rule, planning workflow…") { go(Routes.GLOSSARY) } }
        SectionTitle("Data sources", "Where the numbers and maps come from")
        Card {
            ValueRow("Ephemerides", "Astronomy Engine (VSOP87/ELP)")
            ValueRow("Meteor showers", "IMO Working List 2026")
            ValueRow("Maps", "© OpenStreetMap contributors")
            ValueRow("Elevation / geocoding", "Open-Meteo, Nominatim")
            ValueRow("Nearby places", "Wikipedia GeoSearch")
        }
        InfoText("Calculations are verified against USNO, NASA eclipse catalogs, PyEphem and geographiclib in automated tests.")
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Palette.Accent)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Palette.Text, fontSize = 16.sp)
            Text(sub, color = Palette.TextDim, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.TextDim)
    }
}
