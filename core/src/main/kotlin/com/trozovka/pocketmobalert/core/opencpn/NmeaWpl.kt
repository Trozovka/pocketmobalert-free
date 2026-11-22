package com.trozovka.pocketmobalert.core.opencpn

import java.util.Locale

/**
 * Builds a standard NMEA-0183 $--WPL (waypoint location) sentence naming a waypoint at a given
 * position. This places a marked waypoint on OpenCPN's chart -- it does NOT trigger OpenCPN's
 * own built-in MOB alarm/tracking mode, which would require a dedicated OpenCPN plugin (out of
 * scope here; flagged as a possible future milestone, not a promise).
 */
object NmeaWpl {
    fun buildSentence(latitude: Double, longitude: Double, waypointName: String): String {
        val latHemisphere = if (latitude >= 0) "N" else "S"
        val lonHemisphere = if (longitude >= 0) "E" else "W"
        val latField = toNmeaCoordinate(Math.abs(latitude), degreeDigits = 2)
        val lonField = toNmeaCoordinate(Math.abs(longitude), degreeDigits = 3)

        val body = "GPWPL,$latField,$latHemisphere,$lonField,$lonHemisphere,$waypointName"
        val checksum = body.fold(0) { acc, c -> acc xor c.code }
        return String.format(Locale.US, "$%s*%02X\r\n", body, checksum)
    }

    /**
     * NMEA coordinate format is [degrees][minutes.mmm], degrees zero-padded to [degreeDigits].
     * Always a decimal point, whatever the phone's language; rounded first so minutes never show 60.
     */
    private fun toNmeaCoordinate(absValue: Double, degreeDigits: Int): String {
        val thousandths = Math.round(absValue * 60_000.0)
        val degreesStr = (thousandths / 60_000).toString().padStart(degreeDigits, '0')
        return degreesStr + String.format(Locale.US, "%06.3f", (thousandths % 60_000) / 1000.0)
    }
}
