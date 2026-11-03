package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The shops, cards and sums here are invented to cover the shapes banks use. None is a real notification. */
class BankAlertTest {
    private fun parse(text: String, title: String = "Bank", appName: String = "") = parseBankNotification(title, text, appName)

    @Test fun `reads a payment with the balance after it`() {
        val alert = parse("Ödəniş: 12.40 AZN, BRAVO SUPERMARKET, Kart: *1234, Balans: 533.12 AZN")!!
        assertThat(alert.amount).isEqualTo(Money(12_40))
        assertThat(alert.kind).isEqualTo(TransactionKind.EXPENSE)
        assertThat(alert.merchant).isEqualTo("BRAVO SUPERMARKET")
    }

    @Test fun `the balance is never mistaken for the payment, even when it comes first`() {
        val alert = parse("Balans: 533.12 AZN. Ödəniş 6.80 AZN, YANGO")!!
        assertThat(alert.amount).isEqualTo(Money(6_80))
        assertThat(alert.merchant).isEqualTo("YANGO")
    }

    @Test fun `the manat sign works either side of the number`() {
        assertThat(parse("Payment ₼16.31 at SPOTIFY")!!.amount).isEqualTo(Money(16_31))
        assertThat(parse("Payment 16.31 ₼ at SPOTIFY")!!.amount).isEqualTo(Money(16_31))
    }

    @Test fun `large and comma-decimal sums read correctly`() {
        assertThat(parse("Payment 1,234.56 AZN, SHOP")!!.amount).isEqualTo(Money(1_234_56))
        assertThat(parse("Payment 1 234,56 AZN, SHOP")!!.amount).isEqualTo(Money(1_234_56))
        assertThat(parse("Payment 12,4 AZN, SHOP")!!.amount).isEqualTo(Money(12_40))
        assertThat(parse("Payment 1,234 AZN, SHOP")!!.amount).isEqualTo(Money(1_234_00))
        assertThat(parse("Payment 12 AZN, SHOP")!!.amount).isEqualTo(Money(12_00))
    }

    @Test fun `money coming in is income`() {
        val alert = parse("Mədaxil: 100.00 AZN, Kart: *1234")!!
        assertThat(alert.kind).isEqualTo(TransactionKind.INCOME)
        assertThat(alert.amount).isEqualTo(Money(100_00))
    }

    @Test fun `russian wording works`() {
        val alert = parse("Покупка 8.20 AZN, WOLT, карта *1234, остаток 90.00 AZN")!!
        assertThat(alert.amount).isEqualTo(Money(8_20))
        assertThat(alert.merchant).isEqualTo("WOLT")
        assertThat(parse("Зачисление 50.00 AZN")!!.kind).isEqualTo(TransactionKind.INCOME)
    }

    @Test fun `a merchant label wins`() {
        assertThat(parse("Ödəniş 5.00 AZN. Kart *1234. Obyekt: ARAZ MARKET, 12:30")!!.merchant).isEqualTo("ARAZ MARKET")
    }

    @Test fun `a payment with no shop in it still comes through`() {
        val alert = parse("Ödəniş: 3.00 AZN, Kart: *1234")!!
        assertThat(alert.amount).isEqualTo(Money(3_00))
        assertThat(alert.merchant).isEmpty()
    }

    @Test fun `a foreign payment uses the manat figure sent with it`() {
        val brackets = parse("Ödəniş: 16.00 USD (27.54 AZN), SPOTIFY, Kart: *1234")!!
        assertThat(brackets.amount).isEqualTo(Money(27_54))
        assertThat(brackets.merchant).isEqualTo("SPOTIFY")
        val sentences = parse("Payment of 16.00 USD at NETFLIX. Charged 27.54 AZN.")!!
        assertThat(sentences.amount).isEqualTo(Money(27_54))
        assertThat(sentences.merchant).contains("NETFLIX")
        assertThat(parse("Ödəniş \$16 = 27.54 ₼, ADOBE")!!.amount).isEqualTo(Money(27_54))
    }

    @Test fun `an exchange rate is not the payment`() {
        val alert = parse("1 USD = 1.70 AZN, ödəniş 27.20 AZN, NETFLIX")!!
        assertThat(alert.amount).isEqualTo(Money(27_20))
        assertThat(alert.merchant).isEqualTo("NETFLIX")
    }

    @Test fun `a separate commission is not the payment`() {
        assertThat(parse("Komissiya: 0.30 AZN. Ödəniş 27.54 AZN, SPOTIFY")!!.amount).isEqualTo(Money(27_54))
        assertThat(parse("Fee 0.30 AZN, payment 27.54 AZN, SPOTIFY")!!.amount).isEqualTo(Money(27_54))
    }

    @Test fun `a foreign payment with no manat figure is left alone`() {
        assertThat(parse("Payment 12.00 USD at AMAZON")).isNull()
    }

    @Test fun `any wording that mentions a card or an account counts as money moving`() {
        val card = parse("Kartınızdan 9.50 AZN silindi. WOLT")!!
        assertThat(card.amount).isEqualTo(Money(9_50))
        assertThat(card.merchant).isEqualTo("WOLT")
        assertThat(parse("Hesabınızdan 4.00 AZN çıxıldı, TEST SHOP")!!.merchant).isEqualTo("TEST SHOP")
        assertThat(parse("*1234 12.00 AZN SOME SHOP")!!.amount).isEqualTo(Money(12_00))
    }

    @Test fun `the title names the shop when the text doesn't`() {
        assertThat(parse("Ödəniş 12.40 AZN", title = "Bravo Supermarket")!!.merchant).isEqualTo("Bravo Supermarket")
    }

    @Test fun `the word for payment isn't part of the shop's name, however it ends`() {
        assertThat(parse("4.20 AZN, Kart *1234", title = "Taksi ödənişi")!!.merchant).isEqualTo("Taksi")
    }

    @Test fun `a title that's only the app's name isn't a shop`() {
        assertThat(parse("Ödəniş 12.40 AZN", title = "Any Mobile", appName = "Any Mobile")!!.merchant).isEmpty()
        assertThat(parse("Ödəniş 12.40 AZN", title = "Kart əməliyyatı")!!.merchant).isEmpty()
    }

    @Test fun `a word that only ends like a fee stays a shop`() {
        assertThat(parse("Coffee 3.00 AZN, Kart *1234")!!.amount).isEqualTo(Money(3_00))
    }

    @Test fun `login codes are left alone, whatever sum they mention`() {
        assertThat(parse("Your code is 4821 for a payment of 12.40 AZN")).isNull()
        assertThat(parse("Təsdiq kodu: 4821. Ödəniş 12.40 AZN")).isNull()
    }

    @Test fun `offers and balances on their own are not payments`() {
        assertThat(parse("Sizə 5000 AZN kredit təklif edirik!")).isNull()
        assertThat(parse("Kartınıza 20 AZN keşbek qazanın")).isNull()
        assertThat(parse("Balans: 533.12 AZN")).isNull()
        assertThat(parse("Your balance is 533.12 AZN")).isNull()
    }

    @Test fun `no sum at all, no entry`() {
        assertThat(parse("Ödəniş uğurla tamamlandı")).isNull()
    }

    @Test fun `the sum can be in the title with the shop, card and balance on lines under it`() {
        val alert = parse("SOME SHOP\nKart: **1234\nBalans: 2,000.50₼", title = "💸 Ödəniş: -5.20₼")!!
        assertThat(alert.amount).isEqualTo(Money(5_20))
        assertThat(alert.kind).isEqualTo(TransactionKind.EXPENSE)
        assertThat(alert.merchant).isEqualTo("SOME SHOP")
        assertThat(alert.cardEnding).isEqualTo("1234")
    }

    @Test fun `a shop's name can hold underscores and a branch number`() {
        val alert = parse("BOL ZETOIL_27I_SAYLI P3\nKart: **1234\nBalans: 2,744.29₼", title = "💸 Ödəniş: -73.76₼")!!
        assertThat(alert.amount).isEqualTo(Money(73_76))
        assertThat(alert.kind).isEqualTo(TransactionKind.EXPENSE)
        assertThat(alert.merchant).isEqualTo("BOL ZETOIL_27I_SAYLI P3")
        assertThat(alert.cardEnding).isEqualTo("1234")
    }

    // A bank wraps its sums in left-to-right marks, which a screenshot can't show. This is that wording, character for character.
    private val mark = "‎"

    @Test fun `invisible marks around a sum don't hide it`() {
        val alert = parse("SOME*SHOP.COM\nKart: **1234\nBalans: ${mark}2,744.29${mark}₼", title = "💸 Ödəniş: -${mark}15.24${mark}₼")!!
        assertThat(alert.amount).isEqualTo(Money(15_24))
        assertThat(alert.kind).isEqualTo(TransactionKind.EXPENSE)
        assertThat(alert.merchant).isEqualTo("SHOP.COM")
        assertThat(alert.cardEnding).isEqualTo("1234")
    }

    @Test fun `invisible marks don't change what a notification means`() {
        val foreign = parse("ZETAFLIX\nKart: **1234", title = "💸 Ödəniş: -${mark}9.49${mark}\$ (-${mark}16.31${mark}₼)")!!
        assertThat(foreign.amount).isEqualTo(Money(16_31))
        val cash = parse("SOME MARKET\nKart: **1234", title = "💸 Nağdlaşdırma: -${mark}70.00${mark}₼")!!
        assertThat(cash.kind).isEqualTo(TransactionKind.TRANSFER)
        val incoming = parse("SOMEONE\nKart: **1234", title = "Köçürmə: +${mark}50.00${mark}₼")!!
        assertThat(incoming.kind).isEqualTo(TransactionKind.INCOME)
    }

    @Test fun `marks in a shop's name don't end up in it`() {
        assertThat(parse("${mark}SOME SHOP${mark}\nKart: **1234", title = "Ödəniş: -5.00₼")!!.merchant).isEqualTo("SOME SHOP")
    }

    @Test fun `a balance with marks around it still isn't the payment`() {
        assertThat(parse("Balans: ${mark}533.12${mark}₼")).isNull()
    }

    @Test fun `a foreign payment in the title uses its manat figure`() {
        val alert = parse("ZETAFLIX\nKart: **1234\nBalans: 900.00₼", title = "💸 Ödəniş: -9.49\$ (-16.31₼)")!!
        assertThat(alert.amount).isEqualTo(Money(16_31))
        assertThat(alert.merchant).isEqualTo("ZETAFLIX")
    }

    @Test fun `taking cash out is a transfer, whichever shop's machine it came from`() {
        val alert = parse("SOME MARKET 8 MKR\nKart: **1234\nBalans: 279.36₼", title = "💸 Nağdlaşdırma: -70.00₼")!!
        assertThat(alert.amount).isEqualTo(Money(70_00))
        assertThat(alert.kind).isEqualTo(TransactionKind.TRANSFER)
        assertThat(alert.cardEnding).isEqualTo("1234")
        assertThat(parse("NƏĞDLƏŞDIRMƏ 20.00 AZN, Kart *1234")!!.kind).isEqualTo(TransactionKind.TRANSFER)
        assertThat(parse("Bankomatdan 20.00 AZN çıxarıldı")!!.kind).isEqualTo(TransactionKind.TRANSFER)
        assertThat(parse("ATM 20.00 AZN, Card *1234")!!.kind).isEqualTo(TransactionKind.TRANSFER)
        assertThat(parse("Снятие наличных 20.00 AZN")!!.kind).isEqualTo(TransactionKind.TRANSFER)
    }

    @Test fun `a shop with atm inside a word is still a payment`() {
        assertThat(parse("Ödəniş: 3.00 AZN, BATMAN STORE")!!.kind).isEqualTo(TransactionKind.EXPENSE)
    }

    @Test fun `a plus sign means money came in`() {
        assertThat(parse("SOMEONE\nKart: **1234", title = "Köçürmə: +50.00₼")!!.kind).isEqualTo(TransactionKind.INCOME)
        assertThat(parse("Köçürmə: + 50.00 AZN, Kart *1234")!!.kind).isEqualTo(TransactionKind.INCOME)
        assertThat(parse("Köçürmə: -50.00₼, Kart *1234")!!.kind).isEqualTo(TransactionKind.EXPENSE)
    }

    @Test fun `what a card statement adds isn't part of the shop's name`() {
        assertThat(parse("ACME* CLOUDY SUB\nKart: **1234", title = "Ödəniş: -5.00₼")!!.merchant).isEqualTo("CLOUDY")
        assertThat(parse("PAYPAL *SOME SHOP\nKart: **1234", title = "Ödəniş: -5.00₼")!!.merchant).isEqualTo("SOME SHOP")
        assertThat(parse("Zetaflix Q37B8DA2CE\nKart: **1234", title = "Ödəniş: -5.00₼")!!.merchant).isEqualTo("Zetaflix")
    }

    @Test fun `numbers that belong to a shop's name stay`() {
        assertThat(parse("24SAAT MARKET 8\nKart: **1234", title = "Ödəniş: -5.00₼")!!.merchant).isEqualTo("24SAAT MARKET 8")
        assertThat(parse("7ELEVEN\nKart: **1234", title = "Ödəniş: -5.00₼")!!.merchant).isEqualTo("7ELEVEN")
    }

    // A top-up from that bank, with invented numbers: the operator and the phone number share a line.
    @Test fun `a top-up is paid to the operator, not to the phone number`() {
        val alert = parse("Nar 77:1234567\nKart: **1234\nBalans: ${mark}1,234.56${mark}₼${mark}", title = "💸 Ödəniş: -${mark}10.00${mark}₼")!!
        assertThat(alert.amount).isEqualTo(Money(10_00))
        assertThat(alert.kind).isEqualTo(TransactionKind.EXPENSE)
        assertThat(alert.merchant).isEqualTo("Nar")
        assertThat(alert.cardEnding).isEqualTo("1234")
        assertThat(parse("Azercell +994 50 123 45 67\nKart: **1234", title = "Ödəniş: -5.00₼")!!.merchant).isEqualTo("Azercell")
        assertThat(parse("Bakcell 0551234567\nKart: **1234", title = "Ödəniş: -5.00₼")!!.merchant).isEqualTo("Bakcell")
    }

    @Test fun `a reference number still keeps its line out of shop names`() {
        assertThat(parse("Ref 1234567\nKart: **1234", title = "Ödəniş: -5.00₼")!!.merchant).isEmpty()
    }

    @Test fun `the card is read from any mask, and may not be there`() {
        assertThat(parse("Ödəniş 5.00 AZN, Kart: *1234")!!.cardEnding).isEqualTo("1234")
        assertThat(parse("Ödəniş 5.00 AZN, Kart: 4169 73** **** 1234")!!.cardEnding).isEqualTo("1234")
        assertThat(parse("Ödəniş 5.00 AZN, SOME SHOP")!!.cardEnding).isEmpty()
    }
}
