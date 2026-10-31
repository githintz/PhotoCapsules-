# PhotoPills: Feature Research

These notes come from the official PhotoPills User Guide, parts I and II (updated for iOS v2.12 and Android v1.7), the photopills.com calculator pages, the Google Play listing (updated Aug 2026), and third-party reviews. **PhotoCapsules** aims to match PhotoPills feature for feature. It does not copy branding, artwork, text or proprietary data.

## 1. App structure
PhotoPills has three menus:
* **Pills**: Planner, Sun, Moon, Exposure, DoF, FoV, DoF Table, Hyperfocal Table, Night AR, Meteor Showers, Star Trails, Spot Stars, Time lapse, Timer, Subject distance, Focal length match.
* **My Stuff**: Awards (online community, out of scope), Plans, Points of Interest, Backup (KMZ), Settings.
* **Academy**: user guide, tutorials, glossary. We ship an offline glossary and help screen instead.

Widgets: Sun, Moon, Galactic Center visibility, combined Sun+Moon+MW, and upcoming Plans.

## 2. Planner (the core tool)
**Four parts: Top Panels, Map, Time Bar, Bottom options.**

### Top panels (swipeable)
1. **Shadow**: subject height → shadow length (Sun by day, Moon by night). Toggles the Shadow layer.
2. **Pin-to-pin geodetics** (Red Pin = observer, Black Pin = subject):
   * distance, azimuth (bearing), altitude difference, elevation angle;
   * Sun/Moon/MW-arch/Galactic Center altitude above the Black Pin ground level when aligned;
   * Sun/Moon real size at the Black Pin distance.
3. **Sun/Moon position**: azimuth and elevation, Moon phase name and illumination %. Toggles the Sun/Moon lines.
4. **Rise/Set**: time@azimuth for sunrise, sunset, moonrise and moonset, plus a Moon phase picture. Tap to jump to the next main phase; double-tap to jump to the previous one.
5. **Twilights**: civil (0…−6°), nautical (−6…−12°), astronomical (−12…−18°).
6. **Magic hours**: golden hour (Sun from +6° to −4°) and blue hour (−4° to −6°).
7. **Galactic Center visibility**: when and where (az/el) the GC becomes visible and not visible. Visible means the GC is above the horizon during astronomical night.
8. **Milky Way position**:
   * GC az/el, and az/el of the highest point of the MW arch;
   * a "Moon energy bar" (darkness ∝ 1 − illumination);
   * MW inclination picture. Tap to jump to the next new Moon.
9. **Eclipse selector and info**: name, date, type and magnitude at the Red Pin. Lunar eclipses show umbral and penumbral magnitudes.
10. **Eclipse phase times**: P1, U1, U2, max, U3, U4, P4 for lunar eclipses; C1–C4 and max for solar eclipses. A phase picture taps through to the next phase.
11. **Meteor showers**: name, activity period, peak date/time, radiant az/el, and meteors/h of all active showers.

### Map
* **Red Pin**:
  * moved by drag, long-press, GPS, the "move pin" crosshair mode, address search, lat/lon (DDD/DMS), a geotagged photo, or a saved POI;
  * Undo/Redo of pin positions;
  * Swap pins.
* **Black Pin**: subject. A dashed azimuth line means the body is hidden by terrain at the Black Pin.
* **(+) toolbar**:
  * GPS, Move pin, Undo/Redo, Expand map, Swap pins;
  * Extend Sun/Moon azimuth lines, Show my position, Map type, Compass-rotate map.
* **Map settings**:
  * Planner mode: Camera or Drone.
  * Map type.
  * Map tool: None / FoV / DoF / Sun-Moon size distance.
  * Layers:
    * Sun: Standard, Path or Light direction, plus Show size and Real (geodesic) azimuth lines.
    * Moon: same styles as Sun.
    * Milky Way: arch, GC line, horizon crossings, GC rise/set lines.
    * Meteor radiant path.
    * Eclipse paths.
    * Twilight colouring.
    * Shadow.
  * Reset layers: restores Sun-Std, Moon-Std and Twilights.
* **Path rendering**: a polar sky plot centred on the Red Pin, with contour circles every 10° of elevation (outer = 0°, centre = 90°).
* **Drone mode**:
  * drone/camera selector, focal length, composition grid (thirds, golden ratio, diagonal, triangle, golden spiral);
  * landscape/portrait;
  * ground frame for an altitude, with area and altitude readout.
* **FoV tool**:
  * camera, focal length (nominal or 35mm-equivalent, teleconverter, lock), focus distance (custom or Black Pin), orientation;
  * azimuth (custom or Black Pin);
  * a wedge on the map with a darkened outside, and draggable handles for FoV and azimuth.
* **DoF tool**: as the FoV tool, plus aperture. Draws near and far limit arcs (grey), focus (red) and hyperfocal (blue).
* **Sun/Moon tool**: desired Sun/Moon size ⇄ shooting distance from the Black Pin. Drawn as a circle. The rule of thumb is "rule of 100".

### Time Bar
* 24 h colour band of light type: night, astronomical, nautical, civil, golden, day.
* Sun and Moon elevation curves.
* Drag to scrub. Tap the centre for a date picker with time zone; double-tap for now.
* Tap the sides to jump to the next/previous event. Long-press switches to a fine (15 min/1 h) scale.

### Bottom options
* **Find**: Sun or Moon at a given azimuth (± error) and elevation (± error) within a date range.
  * Returns a sortable table: date, az, el, Moon phase, light type.
  * Selection is via a 2D map, AR or numeric input.
* **AR**: Sun/Moon path, rise/set directions, horizon, 10° grid. Swipe changes time; tapping the sides changes day.
* **Night AR**:
  * Milky Way + GC, celestial equator, poles, Polaris, RA/Dec grid;
  * star-trail pattern arcs, Moon path, meteor radiant.
* **Load**: address search, Plan, POI, lat/lon, geotagged photo.
* **Save**: Plan (pin, black pin, date/time, tz, notes) or POI.
* **More**:
  * Date: date, time, time zone (auto or manual).
  * Altitudes: custom altitude and offset for the Red and Black pins.
  * Horizon: height above horizon; adjusts rise/set through horizon dip.
  * Action: share plan (KMZ), calendar alert, open in maps, copy coordinates.

## 3. Sun pill
* **Info**:
  * light-type coloured header;
  * sunrise/sunset and current light type;
  * the day's events ordered midnight→midnight: rise/set, Moon rise/set + phase, GC visibility, golden/blue hours, twilights;
  * Sun elevation path chart.
* **Info table**: time to rise/set, daylight duration, azimuth, elevation, angular diameter, distance, shadow ratio, transit.
* **Calendar**: monthly table of sunrise/sunset time@azimuth.
* **AR**.
* **Seasons**: equinoxes and solstices per year.
* **Action**: share, send to Planner.

## 4. Moon pill
* **Info**:
  * Moon picture showing phase and orientation as seen from the location;
  * rise/set, phase name and %;
  * events, with Sun elevation at moonrise/set;
  * Moon and Sun elevation path chart;
  * next 5 main phases;
  * Sun/Moon az/el with light type.
* **Info table**: time to rise/set, angular diameter, age, distance, shadow ratio, transit.
* **Calendar**: month grid of phases, with supermoons highlighted.
* **AR**.
* **Distances**: perigees, apogees and supermoons per year, each with light-type and phase picture.

## 5. Calculators
| Pill | Inputs | Outputs |
|---|---|---|
| Exposure | Calculate shutter/aperture/ISO; test settings (N, t, ISO); final settings + ND filter (stops) | equivalent setting, EV; EV table (EV100 for typical scenes); timer |
| DoF | camera (CoC), focal (real), aperture, focus distance, teleconverter | hyperfocal, hyperfocal near limit (H/2), near, far, total, in front, behind; Inverse (solve f, N or distance for a target DoF); Advanced CoC (print size, viewing distance, visual acuity) |
| FoV | camera, focal, distance, orientation | H/V/D angle of view and field width/height/diagonal; Inverse (solve f or distance) |
| DoF table | camera, focal | grid aperture × distance of total DoF or limits; hyperfocal row |
| Hyperfocal table | camera | grid focal × aperture |
| Spot Stars | camera (sensor size, MP), focal, aperture, declination, accuracy | 500 rule, NPF rule (default/accurate) |
| Star Trails | exposure time ⇄ rotation angle, hemisphere | simulation drawing, 15.04°/h |
| Time lapse | calculate interval/clip length/event duration; fps, MB per photo | photos, memory, interval table |
| Timer | duration | countdown + notification |
| Subject distance | camera, focal, subject W/H, orientation | min distance |
| Focal length match | camera 1, camera 2, f, N, distance | f₂, N₂ matching FoV and DoF + AOV/DoF for both |

## 6. Meteor showers
* Calendar of IMO showers by class (I–IV): activity period, peak (solar longitude → local date/time), ZHR, velocity, radiant.
* Meteors/h at the location and time, scaled by radiant elevation.
* Moon phase and hours without Moon. Radiant path chart.
* AR radiant.

## 7. Settings (My Stuff)
* units (metric/imperial);
* aperture scale (full, ½, ⅓ stops);
* default camera;
* map type;
* elevation provider;
* coordinate format (DDD/DMM/DMS);
* show my position;
* azimuth reference (true or magnetic north);
* device height for AR (default 1.35 m);
* gyroscope toggle and compass warning.

## 8. UI design cues
* **Dark UI**: near-black backgrounds (#141416 / #1E1E22) with white text and saturated accent colours that encode meaning consistently:
  * Sun yellow (#FFC93C); sunrise yellow, sunset orange (#FF8A2B);
  * Moon light blue (#7FC8FF); moonrise light blue, moonset dark blue (#3F6FD8);
  * Milky Way white/grey.
  * Light types: day `#8FD3FF`, golden `#FFB347`, blue hour `#3A5BD9`, nautical `#2B3A8C`, astronomical `#1C2357`, night `#0B0E24`.
* **Pills menu**: a grid of round, colourful "pill" icons with labels under them. A bottom navigation switches between My Stuff, Pills and Academy.
* **Calculator screens**:
  * at the top, a "Calculate" selector, then grouped input rows (label left, value right in accent colour; tap to edit with a wheel/list picker);
  * a results card;
  * a bottom bar with Timer / Table / AR / Share.
* **Planner**: the panels are a swipeable pager with page dots above a full-width map. The map layers use the colour code above. Under the map sits the colourful time bar, then a bottom action bar (Find, AR, Night AR, Load, Save, More).

## 9. Out of scope (proprietary or online community)
* **Awards**, plus Academy video content.
* **The 10,500-entry proprietary POI database**: replaced by free Wikipedia GeoSearch nearby places.
* **iCloud sync**: replaced by KMZ backup and import.
