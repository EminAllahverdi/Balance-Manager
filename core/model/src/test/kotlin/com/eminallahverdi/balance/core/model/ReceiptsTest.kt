package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

class ReceiptsTest {
    /** Laid out as an e-kassa receipt is, with an invented shop, tax number and fiscal ID. */
    private val columns = """
        "YAŞIL BAZAR" MMC
        VÖEN: 1234567890
        Obyekt: Yaşıl Bazar Nərimanov
        Satış çeki № 4182
        Kassir: Leyla   Növbə: 12
        Tarix: 03.11.2026  Saat: 19:42:10
        Malın adı             Say    Qiymət   Cəmi
        Süd Ailə 3.2% 1L       2     1,95     3,90
        ƏDV 18%: 0.59
        Çörək ağ               1     0,60     0,60
        Pomidor kq          0.756    4,20     3,18
        Kartof                 2     0,85     1,70
        Toyuq filesi 1kq       1    11,40    11,40
        Fairy 450ml            1     3,95     3,95
        Cəmi:                                24,73
        ƏDV 18%:                              3,77
        Nağdsız:                             24,73
        Fiskal ID: 7MbR5E7xNh2Y
    """.trimIndent().lines()

    @Test fun `every item is read with its quantity, price and total`() {
        val receipt = parseReceiptText(columns)
        assertThat(receipt.items.map { it.name }).containsExactly("Süd Ailə 3.2% 1L", "Çörək ağ", "Pomidor kq", "Kartof", "Toyuq filesi 1kq", "Fairy 450ml").inOrder()
        assertThat(receipt.items.first().quantityMilli).isEqualTo(2000)
        assertThat(receipt.items.first().price).isEqualTo(Money(1_95))
        assertThat(receipt.items[2].quantityMilli).isEqualTo(756)
        assertThat(receipt.items[2].total).isEqualTo(Money(3_18))
        assertThat(receipt.total).isEqualTo(Money(24_73))
        assertThat(receipt.isComplete).isTrue()
    }

    @Test fun `the shop, day and fiscal ID come from the header and footer`() {
        val receipt = parseReceiptText(columns)
        assertThat(receipt.store).isEqualTo("Yaşil Bazar")
        assertThat(receipt.date).isEqualTo(LocalDate.of(2026, 11, 3))
        assertThat(receipt.fiscalId).isEqualTo("7MbR5E7xNh2Y")
    }

    @Test fun `header lines that happen to multiply out aren't items`() {
        val receipt = parseReceiptText(listOf("Kassa: 2 Növbə: 15 Çek: 30", "Çörək 1 0.60 0.60", "Cəmi 0.60"))
        assertThat(receipt.items.map { it.name }).containsExactly("Çörək")
    }

    @Test fun `a name above its numbers, as some tills print them`() {
        val receipt = parseReceiptText(listOf("Qatıq Atena 2.5% 450q", "2 x 1.35 = 2.70", "Su Sirab 1.5L", "1 x 0.80 = 0.80", "YEKUN 3.50"))
        assertThat(receipt.items.map { it.name }).containsExactly("Qatıq Atena 2.5% 450q", "Su Sirab 1.5L").inOrder()
        assertThat(receipt.isComplete).isTrue()
    }

    @Test fun `columns read apart are put back into rows`() {
        val pieces = listOf(
            TextPiece(10, 100, 200, 120, "Süd 1L"), TextPiece(300, 102, 330, 121, "2"), TextPiece(400, 99, 450, 119, "1.95"), TextPiece(500, 101, 560, 120, "3.90"),
            TextPiece(10, 130, 200, 150, "Çörək"), TextPiece(400, 131, 450, 151, "0.60"), TextPiece(500, 130, 560, 150, "0.60"),
        )
        assertThat(rowsFrom(pieces)).containsExactly("Süd 1L 2 1.95 3.90", "Çörək 0.60 0.60").inOrder()
        assertThat(parseReceiptText(rowsFrom(pieces)).items).hasSize(2)
    }

    @Test fun `items fall into groups, and Kartof isn't a card payment`() {
        assertThat(groupOf("Süd Ailə 3.2% 1L")).isEqualTo(ItemGroup.DAIRY)
        assertThat(groupOf("Toyuq filesi")).isEqualTo(ItemGroup.MEAT_FISH)
        // As text recognition reads it off a receipt.
        assertThat(groupOf("Toyug filesi 1kg")).isEqualTo(ItemGroup.MEAT_FISH)
        assertThat(itemKey("Toyug filesi")).isEqualTo(itemKey("Toyuq filesi"))
        assertThat(groupOf("Kartof")).isEqualTo(ItemGroup.FRUIT_VEG)
        assertThat(groupOf("Çörək ağ")).isEqualTo(ItemGroup.BREAD)
        assertThat(groupOf("Fairy 450ml")).isEqualTo(ItemGroup.HOUSEHOLD)
        assertThat(groupOf("Paket")).isEqualTo(ItemGroup.HOUSEHOLD)
        assertThat(groupOf("Something new")).isEqualTo(ItemGroup.OTHER)
    }

    @Test fun `a product is the same whatever its size or spelling`() {
        assertThat(itemKey("Süd Ailə 3.2% 1L")).isEqualTo(itemKey("SUD AILE 3,2% 1 L"))
        assertThat(itemKey("Pomidor kq")).isEqualTo("pomidor")
    }

    @Test fun `the fiscal ID comes from the QR link or as typed`() {
        assertThat(fiscalIdFrom("https://monitoring.e-kassa.gov.az/#/index?doc=7MbR5E7xNh2Y")).isEqualTo("7MbR5E7xNh2Y")
        assertThat(fiscalIdFrom(" 7MbR5E7xNh2Y ")).isEqualTo("7MbR5E7xNh2Y")
        assertThat(fiscalIdFrom("hello")).isNull()
    }

    private fun receipt(day: LocalDate, vararg items: ReceiptItem) = Receipt(0, "Yaşıl Bazar", day, items.map { it.total }.sum(), null, items.toList())
    private fun item(name: String, minor: Long) = ReceiptItem(name, 1000, Money(minor), Money(minor))

    @Test fun `a month is compared with the one before and the average months`() {
        val november = YearMonth.of(2026, 11)
        val spent = mapOf(
            november to Money(300_00),
            november.minusMonths(1) to Money(250_00),
            november.minusMonths(2) to Money(200_00),
            november.minusMonths(3) to Money(150_00),
        )
        val receipts = listOf(
            receipt(LocalDate.of(2026, 11, 3), item("Süd 1L", 4_00), item("Toyuq", 12_00), item("Süd 1L", 4_00)),
            receipt(LocalDate.of(2026, 10, 5), item("Süd 1L", 2_00)),
            receipt(LocalDate.of(2026, 9, 5), item("Süd 1L", 4_00)),
        )
        val report = groceryReport(november, spent, receipts)
        assertThat(report.spent).isEqualTo(Money(300_00))
        assertThat(report.lastMonth).isEqualTo(Money(250_00))
        assertThat(report.averages[3]).isEqualTo(Money(200_00))
        assertThat(report.averages[12]).isEqualTo(Money(200_00))
        assertThat(report.receipts).isEqualTo(1)
        assertThat(report.topItems.first().name).isEqualTo("Toyuq")
        val milk = report.topItems.first { it.key == "sud" }
        assertThat(milk.amount).isEqualTo(Money(8_00))
        assertThat(milk.times).isEqualTo(2)
        // Two months before had receipts: 2.00 and 4.00 make 3.00 a month.
        assertThat(milk.before).isEqualTo(Money(3_00))
        assertThat(report.groups.first().group).isEqualTo(ItemGroup.MEAT_FISH)
        assertThat(percentChange(Money(8_00), Money(4_00))).isEqualTo(100)
        assertThat(percentChange(Money(8_00), null)).isNull()
    }
}
