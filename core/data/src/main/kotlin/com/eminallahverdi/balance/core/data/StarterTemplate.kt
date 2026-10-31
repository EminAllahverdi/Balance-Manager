package com.eminallahverdi.balance.core.data

import com.eminallahverdi.balance.core.database.entity.AccountEntity
import com.eminallahverdi.balance.core.database.entity.CategoryEntity
import com.eminallahverdi.balance.core.database.entity.PersonEntity
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.CategoryType

/**
 * What a brand-new install starts with. Everything here is editable afterwards;
 * onboarding lets the user rename "Me", add people and drop categories they don't need.
 */
data class StarterTemplate(
    val people: List<TemplatePerson>,
    val accounts: List<TemplateAccount>,
    val categories: List<TemplateCategory>,
) {
    data class TemplatePerson(val key: String, val name: String, val color: Long, val sortOrder: Int) {
        fun toEntity() = PersonEntity(name = name, color = color, sortOrder = sortOrder)
    }

    data class TemplateAccount(val name: String, val type: AccountType, val icon: String, val color: Long, val sortOrder: Int) {
        fun toEntity() = AccountEntity(name = name, type = type.name, icon = icon, color = color, sortOrder = sortOrder)
    }

    data class TemplateCategory(
        val name: String,
        val type: CategoryType,
        val icon: String? = null,
        val color: Long? = null,
        val defaultPerson: String? = null,
        val children: List<TemplateCategory> = emptyList(),
    ) {
        fun toEntity(sortOrder: Int, defaultPersonId: Long?) = CategoryEntity(
            name = name,
            type = type.name,
            icon = icon ?: "more_horiz",
            color = color ?: 0xFF8A94A3,
            defaultPersonId = defaultPersonId,
            sortOrder = sortOrder,
        )
    }

    companion object {
        private val E = CategoryType.EXPENSE
        private val I = CategoryType.INCOME
        private fun sub(name: String, icon: String? = null, person: String? = null) =
            TemplateCategory(name, E, icon = icon, defaultPerson = person)

        /** The household template designed with Emin; a sensible start for anyone. */
        fun household(ownerName: String = "Me") = StarterTemplate(
            people = listOf(
                TemplatePerson("me", ownerName, 0xFF5B9DFF, 0),
                TemplatePerson("family", "Family", 0xFF8A94A3, 1),
            ),
            accounts = listOf(
                TemplateAccount("Card", AccountType.DEBIT_CARD, "payments", 0xFF5DCAA5, 0),
                TemplateAccount("Cash", AccountType.CASH, "account_balance_wallet", 0xFFF5B544, 1),
            ),
            categories = listOf(
                TemplateCategory("Groceries", E, "shopping_cart", 0xFF5DCAA5, "family"),
                TemplateCategory("Cafe & Restaurants", E, "restaurant", 0xFFFF9F6E, "me", listOf(sub("Coffee shop", "local_cafe"), sub("Restaurant"), sub("Delivery"))),
                TemplateCategory("Work", E, "work", 0xFFF5B544, "me", listOf(sub("Breakfast"), sub("Lunch"), sub("Snacks"), sub("Colleague gifts", "redeem"), sub("Collections"))),
                TemplateCategory("Transport", E, "local_taxi", 0xFFFFD166, "me", listOf(sub("Taxi"), sub("Public transport", "directions_bus"))),
                TemplateCategory("Car", E, "directions_car", 0xFF8AB4F8, "family", listOf(sub("Fuel", "local_gas_station"), sub("Wash"), sub("Service"), sub("Parking", "local_parking"))),
                TemplateCategory("Health", E, "medical_services", 0xFFFF7A66, "me", listOf(sub("Doctor"), sub("Pharmacy", "local_pharmacy"), sub("Lab tests"))),
                TemplateCategory("Grooming", E, "content_cut", 0xFFF49AC2, "me", listOf(sub("Barber"), sub("Laser"), sub("Manicure / Pedicure"), sub("Hair"))),
                TemplateCategory("Utilities", E, "bolt", 0xFF9AD0A8, "family", listOf(sub("Electricity"), sub("Gas"), sub("Water"), sub("Internet"), sub("Phone line", "smartphone"))),
                TemplateCategory("Subscriptions", E, "subscriptions", 0xFFB39DFF, "me", listOf(sub("Apps", "apps"), sub("Mobile top-up", "smartphone"))),
                TemplateCategory("Kids & Education", E, "school", 0xFFFFB547, null, listOf(sub("School"), sub("Activities"), sub("Toys"))),
                TemplateCategory("Shopping", E, "shopping_bag", 0xFF7FD1F7, "me", listOf(sub("Clothes", "checkroom"), sub("Home"), sub("Electronics"), sub("Online"))),
                TemplateCategory("Entertainment", E, "sports_esports", 0xFFC79BF2, "me", listOf(sub("Games"), sub("Cinema"), sub("Activities"), sub("Massage", "spa"))),
                TemplateCategory("Gifts & Occasions", E, "redeem", 0xFFF7A072, "family", listOf(sub("Presents"), sub("Occasions"))),
                TemplateCategory("Family support", E, "family_restroom", 0xFFB8C0CC, null, listOf(sub("Pocket money"), sub("Support"))),
                TemplateCategory("Travel", E, "flight", 0xFF6FC7D9, "family", listOf(sub("Hotel", "hotel"), sub("Transport"), sub("Food"), sub("Shopping"), sub("Fun"))),
                TemplateCategory("Other", E, "more_horiz", 0xFF8A94A3, "me"),
                TemplateCategory("Paycheck", I, "payments", 0xFF4ADE9B, "me", listOf(TemplateCategory("Salary", I), TemplateCategory("Bonus", I))),
                TemplateCategory("Cashback & Interest", I, "savings", 0xFF4ADE9B, "me"),
                TemplateCategory("Gift", I, "card_giftcard", 0xFFF7A072, "me"),
                TemplateCategory("Received from others", I, "swap_horiz", 0xFF6FC7D9, "me"),
                TemplateCategory("Other income", I, "more_horiz", 0xFF8A94A3, "me"),
            ),
        )
    }
}
