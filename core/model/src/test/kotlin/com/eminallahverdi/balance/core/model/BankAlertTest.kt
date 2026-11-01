package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The wordings here are invented to cover the shapes banks use. None is a real notification. */
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
}
