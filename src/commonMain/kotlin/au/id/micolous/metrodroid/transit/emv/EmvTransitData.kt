/*
 * EmvTransitData.kt
 *
 * Copyright 2019 Google
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
package au.id.micolous.metrodroid.transit.emv

import au.id.micolous.metrodroid.card.emv.EmvCardMain
import au.id.micolous.metrodroid.card.iso7816.ISO7816TLV
import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.emv.EmvData.LOG_ENTRY
import au.id.micolous.metrodroid.transit.emv.EmvData.T2Data
import au.id.micolous.metrodroid.transit.emv.EmvData.TAGMAP
import au.id.micolous.metrodroid.transit.emv.EmvData.TAG_NAME1
import au.id.micolous.metrodroid.transit.emv.EmvData.TAG_NAME2
import au.id.micolous.metrodroid.transit.emv.EmvLogEntry.Companion.parseEmvTrip
import au.id.micolous.metrodroid.ui.HeaderListItem
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.util.ImmutableByteArray


private fun findT2Data(tlvs: List<ImmutableByteArray>): ImmutableByteArray? {
    for (tlv in tlvs) {
        val t2 = ISO7816TLV.findBERTLV(tlv, T2Data, false)
        if (t2 != null)
            return t2
    }

    return null
}

private fun splitby4(input: String?): String? {
    if (input == null)
        return null
    val len = input.length
    val res = (0..len step 4).fold("") { prev, i -> prev + input.substring(i, minOf(i + 4, len)) + " " }
    return if (res.endsWith(' ')) res.substring(0, res.length - 1) else res
}

fun parseEmvTransitData(card: EmvCardMain): EmvTransitData {
    val allTlv = card.getAllTlv()
    val logEntry = getTag(allTlv, LOG_ENTRY)
    val logFormat = card.logFormat
    val logEntries = if (logEntry != null && logFormat != null) {
        val logSfi = logEntry[0]
        val logRecords = card.getSfiFile(logSfi.toInt())
        logRecords?.recordList?.mapNotNull { parseEmvTrip(it, logFormat) }
    } else
        null
    val pinTriesRemaining = card.pinTriesRemaining?.let {
        ISO7816TLV.removeTlvHeader(it).byteArrayToInt()
    }
    return EmvTransitData(
            tlvs = allTlv,
            pinTriesRemaining = pinTriesRemaining,
            logEntries = logEntries,
            t2 = findT2Data(allTlv),
            name = findName(allTlv))
}


private fun getTag(tlvs: List<ImmutableByteArray>, id: String): ImmutableByteArray? {
    for (tlv in tlvs) {
        return ISO7816TLV.findBERTLV(tlv, id, false) ?: continue
    }
    return null
}

private fun findName(tlvs: List<ImmutableByteArray>): String {
    for (tag in listOf(TAG_NAME2, TAG_NAME1)) {
        val variant = getTag(tlvs, tag) ?: continue
        return variant.readASCII()
    }
    return "EMV"
}

fun parseEmvTransitIdentity(card: EmvCardMain): TransitIdentity {
    val allTlv = card.getAllTlv()
    return TransitIdentity(
            findName(allTlv),
            splitby4(getPan(findT2Data(allTlv))))
}

private fun getPan(t2: ImmutableByteArray?): String? {
    val t2s = t2?.toHexString() ?: return null
    return t2s.substringBefore('d', t2s)
}

private fun getPostPan(t2: ImmutableByteArray): String? {
    val t2s = t2.toHexString()
    return t2s.substringAfter('d')
}

@Parcelize
data class EmvTransitData(private val tlvs: List<ImmutableByteArray>,
                          private val name: String,
                          private val pinTriesRemaining: Int?,
                          private val t2: ImmutableByteArray?,
                          private val logEntries: List<EmvLogEntry>?) : TransitData() {
    override val serialNumber get() = splitby4(getPan(t2))

    override val info get(): List<ListItem> {
        val res = mutableListOf<ListItem>()
        if (t2 != null) {
            val postPan = getPostPan(t2)
            res += ListItem("PAN", splitby4(getPan(t2)))
            if (postPan != null) {
                res += ListItem(R.string.expiry_date, "${postPan.substring(2, 4)}/${postPan.substring(0, 2)}")
                val serviceCode = postPan.substring(4, 7)
                res += ListItem("Service code", serviceCode)
                res += ListItem(R.string.discretionary_data,
                        postPan.substring(7).let { it.substringBefore('f', it) }
                )
            }
        }
        if (pinTriesRemaining != null)
            res += ListItem(
                    Localizer.localizePlural(R.plurals.emv_pin_attempts_remaining, pinTriesRemaining),
                    pinTriesRemaining.toString())
        res += listOf(HeaderListItem("TLV tags"))
        val unknownIds = mutableSetOf<String>()
        for (tlv in tlvs) {
            val (li, unknowns) = ISO7816TLV.infoBerTLVWithUnknowns(tlv, TAGMAP)

            res += li
            unknownIds += unknowns
        }

        res += ListItem("Unparsed IDs", unknownIds.joinToString(", "))
        return res
    }

    override val trips get() = logEntries

    override val cardName get() = name
}