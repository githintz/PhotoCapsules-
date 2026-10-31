package com.photocapsules.core.photo

import kotlin.math.abs
import kotlin.math.log2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoTest {
    private val ff = Camera("", "FF", 36.0, 24.0, 6000, 4000)
    private val canonApsc = Camera("", "Canon APS-C", 22.3, 14.9, 6000, 4000)
    private val nikonDx = Camera("", "Nikon DX", 23.5, 15.6, 6000, 4000)
    private val mft = Camera("", "MFT", 17.3, 13.0, 5184, 3888)

    @Test fun cocAndCrop() {
        assertEquals(0.030, ff.circleOfConfusionMm)
        assertEquals(0.019, canonApsc.circleOfConfusionMm) // DOFMaster Canon APS-C
        assertEquals(0.020, nikonDx.circleOfConfusionMm)   // DOFMaster Nikon DX
        assertEquals(0.015, mft.circleOfConfusionMm)       // DOFMaster Four Thirds
        assertEquals(1.0, ff.cropFactor, 1e-9)
        assertEquals(1.61, canonApsc.cropFactor, 0.01)
        assertEquals(2.0, mft.cropFactor, 0.01)
        assertEquals(6.0, ff.pixelPitchUm!!, 1e-9)
        // pixel count from megapixels + aspect ratio
        val mp = Camera("", "x", 36.0, 24.0, megapixels = 24.0)
        assertEquals(6000, mp.horizontalPixels)
    }

    @Test fun cameraDatabaseLoads() {
        val all = CameraDatabase.all
        assertTrue(all.size > 100)
        assertTrue(all.all { it.sensorWidthMm > it.sensorHeightMm || it.sensorWidthMm == it.sensorHeightMm })
        assertTrue(all.all { it.circleOfConfusionMm in 0.004..0.2 })
        val a7iii = CameraDatabase.find("Sony Alpha 7 III")!!
        assertEquals(5.93, a7iii.pixelPitchUm!!, 0.01)
        assertTrue(CameraDatabase.search("nikon z6").isNotEmpty())
    }

    @Test fun dofMatchesHandComputedDofmasterExample() {
        // 50 mm f/8 FF (c=0.030) focused at 10 ft = 3.048 m.
        val r = DepthOfField.compute(DofInput(50.0, 8.0, 3.048, 0.030))
        assertEquals(10.4667, r.hyperfocalM, 1e-3)
        assertEquals(2.3668, r.nearLimitM, 1e-3)
        assertEquals(4.2799, r.farLimitM, 1e-3)
        assertEquals(r.farLimitM - r.nearLimitM, r.totalM, 1e-12)
        assertEquals(r.hyperfocalM / 2, r.hyperfocalNearLimitM, 1e-12)
        // Classic approximation (Wikipedia) agrees within 1% when s >> f: Dn ≈ Hs/(H+s)
        val hApprox = 50.0 * 50.0 / (8 * 0.03) / 1000
        assertEquals(hApprox * 3.048 / (hApprox + 3.048), r.nearLimitM, 0.01 * r.nearLimitM)
    }

    @Test fun dofAtAndBeyondHyperfocalIsInfinite() {
        val h = DepthOfField.hyperfocalM(24.0, 11.0, 0.03)
        val r = DepthOfField.compute(DofInput(24.0, 11.0, h, 0.03))
        assertTrue(r.isFarInfinite)
        assertEquals(h / 2, r.nearLimitM, 1e-6)
        assertTrue(DepthOfField.compute(DofInput(24.0, 11.0, h * 3, 0.03)).farLimitM.isInfinite())
    }

    @Test fun teleconverterMultipliesFocalAndAperture() {
        val a = DepthOfField.compute(DofInput(200.0, 4.0, 20.0, 0.03, 1.4))
        val b = DepthOfField.compute(DofInput(280.0, 5.6, 20.0, 0.03))
        assertEquals(b.nearLimitM, a.nearLimitM, 1e-9)
        assertEquals(b.farLimitM, a.farLimitM, 1e-9)
    }

    @Test fun inverseDofRoundTrips() {
        val coc = 0.03
        val target = DepthOfField.compute(DofInput(35.0, 5.6, 4.0, coc)).totalM
        assertEquals(4.0, DepthOfField.distanceForTotalDof(35.0, 5.6, coc, target)!!, 1e-6)
        assertEquals(5.6, DepthOfField.apertureForTotalDof(35.0, 4.0, coc, target)!!, 1e-6)
        assertEquals(35.0, DepthOfField.focalForTotalDof(5.6, 4.0, coc, target)!!, 1e-6)
        assertNull(DepthOfField.distanceForTotalDof(35.0, 5.6, coc, 0.0))
        // Any finite DoF is reachable just below the hyperfocal distance
        assertTrue(DepthOfField.distanceForTotalDof(35.0, 5.6, coc, 1e6)!! < DepthOfField.hyperfocalM(35.0, 5.6, coc))
    }

    @Test fun advancedCocDefaultsReproduceStandard() {
        assertEquals(0.030, CircleOfConfusion.compute(ff.diagonalMm), 1e-4)
        // Doubling print size halves the CoC; doubling viewing distance doubles it.
        val base = CircleOfConfusion.compute(ff.diagonalMm)
        assertEquals(base / 2, CircleOfConfusion.compute(ff.diagonalMm, 0.508, 0.4064), 1e-9)
        assertEquals(base * 2, CircleOfConfusion.compute(ff.diagonalMm, viewingDistanceM = 0.5), 1e-9)
    }

    @Test fun fieldOfView() {
        // 50 mm on FF: 39.6° H, 27.0° V, 46.8° D (standard published values)
        val r = FieldOfView.compute(ff, 50.0, 10.0, Orientation.LANDSCAPE)
        assertEquals(39.6, r.horizontalAngleDeg, 0.05)
        assertEquals(27.0, r.verticalAngleDeg, 0.05)
        assertEquals(46.8, r.diagonalAngleDeg, 0.05)
        assertEquals(7.2, r.horizontalFieldM, 1e-9)
        val p = FieldOfView.compute(ff, 50.0, 10.0, Orientation.PORTRAIT)
        assertEquals(r.verticalAngleDeg, p.horizontalAngleDeg, 1e-9)
        // 14 mm FF ≈ 114° diagonal
        assertEquals(114.2, FieldOfView.compute(ff, 14.0, 1.0, Orientation.LANDSCAPE).diagonalAngleDeg, 0.1)
        // Inverse round-trips
        assertEquals(50.0, FieldOfView.focalForField(ff, Orientation.LANDSCAPE, FovDimension.HORIZONTAL, 7.2, 10.0), 1e-9)
        assertEquals(10.0, FieldOfView.distanceForField(ff, Orientation.LANDSCAPE, FovDimension.HORIZONTAL, 7.2, 50.0), 1e-9)
        assertEquals(50.0, FieldOfView.focalForAngle(ff, Orientation.LANDSCAPE, FovDimension.DIAGONAL, r.diagonalAngleDeg), 1e-9)
    }

    @Test fun exposureReciprocity() {
        // f/2.8, 30 s, ISO 6400 test → f/4, ISO 400: 30 * (4/2.8)^2 * 16 = 979.6 s (16.3 min)
        val test = ExposureSettings(2.8, 30.0, 6400.0)
        val r = ExposureCalculator.equivalent(test, ExposureTarget.SHUTTER, 4.0, 0.0001, 400.0)
        assertEquals(30.0 * (4.0 / 2.8) * (4.0 / 2.8) * 16, r.shutterSeconds, 1e-6)
        // ND 10 stops: 1/60 → 1024/60 = 17.07 s
        val nd = ExposureCalculator.equivalent(ExposureSettings(8.0, 1 / 60.0, 100.0), ExposureTarget.SHUTTER, 8.0, 1.0, 100.0, 10.0)
        assertEquals(1024 / 60.0, nd.shutterSeconds, 1e-9)
        assertEquals(test.ev100, r.ev100, 1e-9)
        // EV100 of sunny-16 (f/16, 1/100, ISO100) ≈ 14.64
        assertEquals(log2(256.0 * 100), ExposureSettings(16.0, 0.01, 100.0).ev100, 1e-9)
        // Solve aperture & ISO round-trip
        val ap = ExposureCalculator.equivalent(test, ExposureTarget.APERTURE, 0.0, r.shutterSeconds, 400.0)
        assertEquals(4.0, ap.aperture, 1e-9)
        val iso = ExposureCalculator.equivalent(test, ExposureTarget.ISO, 4.0, r.shutterSeconds, 0.0001)
        assertEquals(400.0, iso.iso, 1e-6)
        assertEquals(1 / 125.0, ExposureCalculator.shutterForEv(15.0, 16.0, 100.0), 0.0003)
    }

    @Test fun ndFilters() {
        val nd1000 = NdFilter.common.first { it.name.startsWith("ND1000 ") }
        assertEquals(10.0, nd1000.stops); assertEquals(3.01, nd1000.opticalDensity, 0.01)
        assertEquals(1024.0, nd1000.factor, 1e-9)
    }

    @Test fun spotStars() {
        // 500 rule: 14 mm FF = 35.7 s; APS-C (1.5x) 14 mm = 23.8 s
        assertEquals(35.714, SpotStars.rule500(1.0, 14.0), 1e-3)
        assertEquals(23.81, SpotStars.rule500(1.5, 14.0), 0.01)
        // Michaud simplified: (35*2.8 + 30*6)/14 = 19.857 s
        assertEquals(19.857, SpotStars.npf(2.8, 14.0, 6.0, 0.0, SpotStars.Accuracy.DEFAULT), 1e-3)
        // Michaud full k=1: (16.856*2.8 + 0.0997*14 + 13.713*6)/14 = 9.248 s
        assertEquals((16.856 * 2.8 + 0.0997 * 14 + 13.713 * 6) / 14, SpotStars.npf(2.8, 14.0, 6.0, 0.0, SpotStars.Accuracy.ACCURATE), 1e-9)
        // Declination 60° doubles the time
        assertEquals(2 * 19.857, SpotStars.npf(2.8, 14.0, 6.0, 60.0, SpotStars.Accuracy.DEFAULT), 1e-3)
        // ±90° declination does not blow up to infinity
        assertTrue(SpotStars.npf(2.8, 14.0, 6.0, 90.0, SpotStars.Accuracy.DEFAULT).isFinite())
        assertNull(SpotStars.compute(Camera("", "film", 36.0, 24.0), 14.0, 2.8, 0.0, SpotStars.Accuracy.DEFAULT).npfSeconds)
    }

    @Test fun starTrails() {
        assertEquals(15.041, StarTrails.degreesPerHour, 1e-3)
        assertEquals(360.0, StarTrails.angleForExposure(StarTrails.SIDEREAL_DAY_SECONDS), 1e-9)
        assertEquals(3600.0, StarTrails.exposureForAngle(StarTrails.angleForExposure(3600.0)), 1e-9)
    }

    @Test fun timeLapse() {
        // 2 h event, 10 s clip at 25 fps → 250 photos, interval 28.8 s
        val r = TimeLapse.compute(TimeLapse.Target.INTERVAL, 0.0, 10.0, 7200.0, 25.0, 25.0)
        assertEquals(28.8, r.intervalSeconds, 1e-9); assertEquals(250, r.photos); assertEquals(6250.0, r.memoryMb, 1e-9)
        val c = TimeLapse.compute(TimeLapse.Target.CLIP_LENGTH, 5.0, 0.0, 3600.0, 30.0, 20.0)
        assertEquals(24.0, c.clipLengthSeconds, 1e-9); assertEquals(720, c.photos)
        val d = TimeLapse.compute(TimeLapse.Target.EVENT_DURATION, 3.0, 20.0, 0.0, 24.0, 1.0)
        assertEquals(1440.0, d.eventDurationSeconds, 1e-9)
    }

    @Test fun subjectDistance() {
        // 50 mm FF landscape, 1.8 m tall person: height-limited, u = 50*(1+1800/24) = 3800 mm
        val r = SubjectDistance.compute(ff, 50.0, 0.5, 1.8, Orientation.LANDSCAPE)
        assertEquals(3.8, r.distanceM, 1e-9)
        assertEquals(FovDimension.VERTICAL, r.limitingDimension)
        val p = SubjectDistance.compute(ff, 50.0, 0.5, 1.8, Orientation.PORTRAIT)
        assertEquals(0.05 * (1 + 1800.0 / 36), p.distanceM, 1e-9)
        assertTrue(abs(r.fieldHeightM - 1.8) < 1e-9)
    }

    @Test fun focalLengthMatch() {
        val r = FocalLengthMatch.compute(ff, mft, 50.0, 2.8, 5.0)
        assertEquals(mft.diagonalMm / ff.diagonalMm, r.ratio, 1e-12)
        assertEquals(25.0, r.camera2.focalMm, 0.2)
        assertEquals(1.4, r.camera2.aperture, 0.02)
        assertEquals(r.camera1.fov.diagonalAngleDeg, r.camera2.fov.diagonalAngleDeg, 1e-9)
        // DoF nearly equal (exactly equal only in the s >> f limit and with proportional CoC)
        assertEquals(r.camera1.dof.totalM, r.camera2.dof.totalM, 0.03 * r.camera1.dof.totalM)
    }

    @Test fun tables() {
        val t = DofTables.dofTable(ff, 50.0, listOf(2.8, 8.0), listOf(0.04, 1.0))
        assertNull(t[0][0]); assertNotNull(t[1][1])
        val h = DofTables.hyperfocalTable(ff, listOf(8.0), listOf(24.0))
        assertEquals(2.424, h[0][0], 1e-3)
    }

    @Test fun drone() {
        val r = DroneFrame.footprint(ff, 24.0, 100.0, Orientation.LANDSCAPE)
        assertEquals(150.0, r.widthM, 1e-9); assertEquals(100.0, r.heightM, 1e-9)
        assertEquals(100.0, DroneFrame.altitudeForWidth(ff, 24.0, 150.0, Orientation.LANDSCAPE), 1e-9)
    }

    @Test fun formatting() {
        assertEquals("1/250", Format.shutter(1 / 250.0))
        assertEquals("1/4", Format.shutter(0.25))
        assertEquals("0.3\"", Format.shutter(0.3))
        assertEquals("2.5\"", Format.shutter(2.5))
        assertEquals("30\"", Format.shutter(30.0))
        assertEquals("16m 20s", Format.shutter(980.0))
        assertEquals("f/2.8", Format.aperture(2.8)); assertEquals("f/16", Format.aperture(16.0))
        assertEquals("∞", Format.distance(Double.POSITIVE_INFINITY, UnitSystem.METRIC))
        assertEquals("3.28 ft", Format.distance(1.0, UnitSystem.IMPERIAL))
        assertEquals("1h 00m 00s", Format.duration(3600.0))
        assertEquals(1 / 250.0, Stops.nearest(Stops.shutterThird, 1 / 240.0), 1e-12)
    }
}
