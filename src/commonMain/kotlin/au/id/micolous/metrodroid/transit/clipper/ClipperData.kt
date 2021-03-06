/*
 * ClipperData.kt
 *
 * Copyright 2011 "an anonymous contributor"
 * Copyright 2011-2014 Eric Butler <eric@codebutler.com>
 * Copyright 2018 Google
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package au.id.micolous.metrodroid.transit.clipper

import au.id.micolous.metrodroid.util.NumberUtils

import au.id.micolous.metrodroid.multi.FormattedString
import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.transit.Station
import au.id.micolous.metrodroid.transit.Trip
import au.id.micolous.metrodroid.util.StationTableReader

internal object ClipperData {
    const val AGENCY_CALTRAIN = 0x06
    private const val AGENCY_GGT = 0x0b
    const val AGENCY_MUNI = 0x12
    const val AGENCY_GG_FERRY = 0x19
    const val AGENCY_BAY_FERRY = 0x1b

    val GG_FERRY_ROUTES = mapOf(
            0x01 to FormattedString.language("San Francisco", "en-US"),
            0x02 to FormattedString.language("Sausalito", "en-US"),
            0x03 to FormattedString.language("Larkspur", "en-US"),
            0x04 to FormattedString.language("San Francisco", "en-US"))

    const val CLIPPER_STR = "clipper"

    fun getMode(agency: Int): Trip.Mode {
        return StationTableReader.getOperatorDefaultMode(CLIPPER_STR, agency)
    }

    fun getAgencyName(agency: Int, isShort: Boolean) =
        StationTableReader.getOperatorName(CLIPPER_STR, agency, isShort)

    fun getStation(agency: Int, stationId: Int, isEnd: Boolean): Station? {
        val humanReadableId = NumberUtils.intToHex(agency) + "/" + NumberUtils.intToHex(stationId)
        val s = StationTableReader.getStationNoFallback(CLIPPER_STR, agency shl 16 or stationId,
                humanReadableId)
        if (s != null)
            return s

        if (agency == ClipperData.AGENCY_GGT || agency == ClipperData.AGENCY_CALTRAIN || agency == ClipperData.AGENCY_GG_FERRY) {
            if (stationId == 0xffff)
                return Station.nameOnly(Localizer.localizeString(R.string.clipper_end_of_line))
            if (agency != ClipperData.AGENCY_GG_FERRY)
                return Station.nameOnly(Localizer.localizeString(R.string.clipper_zone_number, stationId.toString()))
        }

        // Placeholders
        return if (stationId == (if (isEnd) 0xffff else 0)) null else Station.unknown(humanReadableId)
    }
}
