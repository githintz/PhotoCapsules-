package com.photocapsules.feature.academy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photocapsules.ui.components.AppScreen
import com.photocapsules.ui.components.Card
import com.photocapsules.ui.components.ScrollColumn
import com.photocapsules.ui.components.SectionTitle
import com.photocapsules.ui.theme.Palette

private val terms = listOf(
    "Azimuth" to "Horizontal direction measured clockwise from true north (0° N, 90° E, 180° S, 270° W). Choose true or magnetic north in Settings.",
    "Elevation (altitude)" to "Angle above the horizon. The Sun/Moon elevation shown includes standard atmospheric refraction.",
    "Sunrise / sunset" to "The moment the Sun's upper limb touches the horizon, with 34′ of refraction. With the Planner's Horizon option, the dip of the horizon for your height is taken into account.",
    "Civil, nautical, astronomical twilight" to "Sun centre between 0° and −6°, −6° and −12°, −12° and −18° below the horizon.",
    "Golden hour" to "Sun elevation between +6° and −4°: warm, soft light with long shadows.",
    "Blue hour" to "Sun elevation between −4° and −6°: deep blue sky, balanced with artificial lights.",
    "Night" to "Sun more than 18° below the horizon: the sky is fully dark.",
    "Galactic Center visibility" to "The Galactic Center (Sgr A*) is above the horizon while the Sun is below −18°.",
    "Moon phase & illumination" to "Phase angle is the Moon–Sun elongation (0° new, 90° first quarter, 180° full, 270° last quarter). Illumination is the lit fraction of the disc.",
    "Supermoon" to "A full Moon closer than 360,000 km to the Earth's centre.",
    "Perigee / apogee" to "Closest / farthest point of the Moon's orbit.",
    "Angular diameter" to "Apparent size of the Sun or Moon (≈0.5°). Use it with the Sun/Moon tool to choose a shooting distance: distance = size ÷ (2·tan(diameter/2)), roughly 115 × subject size for the Moon.",
    "Shadow ratio" to "Shadow length divided by object height = 1/tan(elevation).",
    "Depth of field" to "Range of distances that look acceptably sharp. Near = s(H−f)/(H+s−2f), far = s(H−f)/(H−s) (∞ when s ≥ H).",
    "Hyperfocal distance" to "H = f²/(N·c) + f. Focusing at H makes everything from H/2 to infinity acceptably sharp.",
    "Circle of confusion (CoC)" to "Largest blur spot still perceived as a point. Default: sensor diagonal / 1442 (0.030 mm full frame). Advanced DoF lets you derive it from print size, viewing distance and visual acuity.",
    "Angle / field of view" to "AOV = 2·atan(sensor dimension / 2f). Field at distance d = d × sensor / f.",
    "500 rule" to "Maximum exposure before star trails ≈ 500 / (crop × focal length) seconds. A rough, outdated rule.",
    "NPF rule" to "Frédéric Michaud's rule taking aperture (N), pixel pitch (p) and focal length (f) into account: t = (35N + 30p)/f (simplified) or (16.856N + 0.0997f + 13.713p)/f (accurate), divided by cos(declination).",
    "Star trails" to "Stars rotate 360° per sidereal day (23 h 56 m 4 s): 15.04° per hour around the celestial poles.",
    "Exposure value (EV)" to "EV = log₂(N²/t) at ISO 100. Equivalent exposures keep N²/(t·ISO) constant; each ND stop doubles the exposure time.",
    "Meteor ZHR" to "Zenithal hourly rate: meteors per hour for an observer under a perfectly dark sky with the radiant at the zenith. Expected rate ≈ ZHR × sin(radiant elevation).",
    "Eclipse magnitude" to "Solar: fraction of the Sun's diameter covered (ratio of diameters during totality/annularity). Lunar: fraction of the Moon's diameter inside the umbra (umbral) or penumbra (penumbral).",
)

private val workflow = listOf(
    "1. Know the date" to "Set the date with the time bar, move the Red Pin to candidate spots and check where the Sun, Moon or Milky Way will be (top panels + map lines).",
    "2. Know the spot" to "Place the Red Pin at the only possible spot, put the Black Pin on your subject, then use Find to search when the Sun/Moon will be at the azimuth and elevation of the subject.",
    "3. Terrain" to "The Black Pin panel gives the elevation angle of the subject including Earth curvature. When the Sun/Moon azimuth line is dashed, the body is below that angle (hidden).",
    "4. In the field" to "Use AR / Night AR to check the composition, calibrate on the real Sun or Moon, and use the timer for long exposures.",
)

@Composable
fun GlossaryScreen(onBack: () -> Unit) {
    AppScreen("Glossary & how-to", onBack) { p ->
        ScrollColumn(p) {
            SectionTitle("Planning workflow")
            Card { workflow.forEach { (t, d) -> Entry(t, d) } }
            SectionTitle("Glossary")
            Card { terms.forEach { (t, d) -> Entry(t, d) } }
        }
    }
}

@Composable
private fun Entry(title: String, text: String) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(title, color = Palette.Accent, fontSize = 15.sp)
        Text(text, color = Palette.Text, fontSize = 13.sp)
    }
}
