"""Independent golden vectors for core:astro generated with PyEphem (libastro: VSOP87 + ELP2000-82).

Conventions matched to PhotoCapsules/USNO:
  * Sunrise/sunset & moonrise/moonset: upper limb, horizon -34' refraction, no pressure model.
  * Twilights: Sun centre at -6/-12/-18 deg, geometric.
  * Positions: geometric (airless) topocentric altitude, compared with BodyPosition.trueAltitude.
"""
import json, math, sys, datetime as dt
import ephem

LOCS = [
    ("Madrid", 40.4168, -3.7038, 667), ("New York", 40.7128, -74.0060, 10), ("Sydney", -33.8688, 151.2093, 20),
    ("Reykjavik", 64.1466, -21.9426, 30), ("Tromso", 69.6492, 18.9553, 10), ("Quito", -0.1807, -78.4678, 2850),
    ("Fiji", -17.7134, 178.0650, 5), ("Ushuaia", -54.8019, -68.3030, 20), ("Honolulu", 21.3069, -157.8583, 5),
    ("Svalbard", 78.2232, 15.6267, 10), ("Cape Town", -33.9249, 18.4241, 10), ("Tokyo", 35.6762, 139.6503, 40),
]
DATES = ["2024-01-15", "2025-03-20", "2025-06-21", "2026-08-12", "2026-10-31", "2026-12-21", "2027-04-05", "2030-09-10"]

def obs(lat, lon, elev, date):
    o = ephem.Observer(); o.lat = str(lat); o.lon = str(lon); o.elevation = elev
    o.pressure = 0; o.date = date
    return o

def iso(d):
    return ephem.Date(d).datetime().replace(tzinfo=datetime_utc()).isoformat().replace("+00:00", "Z")

def datetime_utc():
    return dt.timezone.utc

positions, risesets, twilights = [], [], []
for name, lat, lon, elev in LOCS:
    for d in DATES:
        for hh in (0, 6, 12, 18):
            t = f"{d} {hh:02d}:00:00"
            o = obs(lat, lon, elev, t)
            s = ephem.Sun(o); m = ephem.Moon(o)
            positions.append({"loc": name, "lat": lat, "lon": lon, "elev": elev, "utc": d + f"T{hh:02d}:00:00Z",
                              "sunAz": math.degrees(s.az), "sunAlt": math.degrees(s.alt),
                              "moonAz": math.degrees(m.az), "moonAlt": math.degrees(m.alt),
                              "moonIllum": m.moon_phase, "moonDistKm": m.earth_distance * ephem.meters_per_au / 1000.0,
                              "sunDistKm": s.earth_distance * ephem.meters_per_au / 1000.0})
        # Rise/set: next events after 00:00 UTC
        base = f"{d} 00:00:00"
        rec = {"loc": name, "lat": lat, "lon": lon, "elev": elev, "fromUtc": d + "T00:00:00Z"}
        for body, key in ((ephem.Sun(), "sun"), (ephem.Moon(), "moon")):
            for fn, k2 in (("next_rising", "Rise"), ("next_setting", "Set")):
                o = obs(lat, lon, elev, base); o.horizon = "-0:34"
                try:
                    r = getattr(o, fn)(body)
                    rec[key + k2] = iso(r)
                except (ephem.AlwaysUpError, ephem.NeverUpError):
                    rec[key + k2] = None
        risesets.append(rec)
        rec = {"loc": name, "lat": lat, "lon": lon, "elev": elev, "fromUtc": d + "T00:00:00Z"}
        for deg in (6, 12, 18):
            for fn, k2 in (("next_rising", "Dawn"), ("next_setting", "Dusk")):
                o = obs(lat, lon, elev, base); o.horizon = f"-{deg}"
                try:
                    r = getattr(o, fn)(ephem.Sun(), use_center=True)
                    rec[f"tw{deg}{k2}"] = iso(r)
                except (ephem.AlwaysUpError, ephem.NeverUpError):
                    rec[f"tw{deg}{k2}"] = None
        twilights.append(rec)

phases = []
d = ephem.Date("2024-01-01")
end = ephem.Date("2028-01-01")
while d < end:
    nm = ephem.next_new_moon(d); fq = ephem.next_first_quarter_moon(d); fm = ephem.next_full_moon(d); lq = ephem.next_last_quarter_moon(d)
    ev = min((nm, "NEW"), (fq, "FIRST_QUARTER"), (fm, "FULL"), (lq, "LAST_QUARTER"))
    phases.append({"phase": ev[1], "utc": iso(ev[0])})
    d = ephem.Date(ev[0] + 1)

seasons = []
for y in range(2020, 2036):
    d0 = ephem.Date(f"{y}-01-01")
    me = ephem.next_vernal_equinox(d0); js = ephem.next_summer_solstice(d0); se = ephem.next_autumnal_equinox(d0); ds = ephem.next_winter_solstice(d0)
    seasons.append({"year": y, "march": iso(me), "june": iso(js), "september": iso(se), "december": iso(ds)})

# Lunar apsides by brute-force distance extremum search
def mdist(t):
    m = ephem.Moon(ephem.Date(t)); return m.earth_distance * ephem.meters_per_au / 1000.0
apsides = []
t = ephem.Date("2025-01-01").real; tend = ephem.Date("2027-01-01").real
step = 0.25
prev2, prev1 = mdist(t), mdist(t + step); t += 2 * step
while t < tend:
    cur = mdist(t)
    if (prev1 < prev2 and prev1 < cur) or (prev1 > prev2 and prev1 > cur):
        lo, hi = t - 2 * step, t
        peri = prev1 < prev2
        for _ in range(60):
            a = lo + (hi - lo) / 3; b = hi - (hi - lo) / 3
            fa, fb = mdist(a), mdist(b)
            if (fa < fb) == peri: hi = b
            else: lo = a
        tt = (lo + hi) / 2
        apsides.append({"perigee": peri, "utc": iso(tt), "distKm": mdist(tt)})
    prev2, prev1 = prev1, cur; t += step

# Galactic centre and Polaris positions (fixed J2000 bodies)
fixed = []
gc = ephem.FixedBody(); gc._ra = "17:45:40.04"; gc._dec = "-29:00:28.1"; gc._epoch = ephem.J2000
po = ephem.FixedBody(); po._ra = "2:31:49.09"; po._dec = "89:15:50.8"; po._epoch = ephem.J2000
for name, lat, lon, elev in LOCS[:6]:
    for d in DATES[:5]:
        o = obs(lat, lon, elev, d + " 22:00:00")
        gc.compute(o); po.compute(o)
        fixed.append({"loc": name, "lat": lat, "lon": lon, "elev": elev, "utc": d + "T22:00:00Z",
                      "gcAz": math.degrees(gc.az), "gcAlt": math.degrees(gc.alt), "polAz": math.degrees(po.az), "polAlt": math.degrees(po.alt)})

# J2000 solar longitude (astrometric, no aberration -> tolerance must allow ~0.006 deg)
sollon = []
for d in ["2026-01-03 12:00", "2026-04-22 18:00", "2026-08-13 00:00", "2026-12-14 06:00", "2030-06-01 00:00"]:
    s = ephem.Sun(ephem.Date(d))
    e = ephem.Ecliptic(ephem.Equatorial(s.a_ra, s.a_dec, epoch=ephem.J2000), epoch=ephem.J2000)
    sollon.append({"utc": d.replace(" ", "T") + ":00Z", "lambda": math.degrees(e.lon)})

json.dump({"source": "PyEphem %s" % ephem.__version__, "positions": positions, "risesets": risesets, "twilights": twilights,
           "phases": phases, "seasons": seasons, "apsides": apsides, "fixed": fixed, "solarLongitude": sollon},
          open(sys.argv[1], "w"), indent=0)
print(len(positions), len(risesets), len(phases), len(apsides))
