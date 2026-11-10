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

    /** [key] is its English name, which stays the same whichever language [name] is in. */
    data class TemplateCategory(
        val name: String,
        val type: CategoryType,
        val icon: String? = null,
        val color: Long? = null,
        val defaultPerson: String? = null,
        val children: List<TemplateCategory> = emptyList(),
        val key: String = name,
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

    /** Every category name, sub-categories included. */
    val categoryNames: List<String> get() = categories.flatMap { parent -> listOf(parent.name) + parent.children.map { it.name } }

    /** The owner, then [others]. A category that was theirs and has nobody left goes to the owner. */
    fun withPeople(others: List<TemplatePerson>): StarterTemplate {
        val owner = people.first()
        val everyone = listOf(owner) + others
        fun TemplateCategory.reassigned(): TemplateCategory = copy(
            defaultPerson = defaultPerson?.takeIf { key -> everyone.any { it.key == key } } ?: owner.key.takeIf { defaultPerson != null },
            children = children.map { it.reassigned() },
        )
        return copy(people = everyone, categories = categories.map { it.reassigned() })
    }

    /** Leaves out the categories whose [TemplateCategory.key] is in [dropped]. */
    fun without(dropped: Set<String>): StarterTemplate = copy(categories = categories.filterNot { it.key in dropped })

    companion object {
        private val E = CategoryType.EXPENSE
        private val I = CategoryType.INCOME
        private val SIMPLE = setOf(
            "Groceries", "Cafe & Restaurants", "Transport", "Health", "Utilities", "Shopping", "Entertainment", "Other",
            "Paycheck", "Other income",
        )

        private fun sub(name: String, icon: String? = null, person: String? = null) =
            TemplateCategory(name, E, icon = icon, defaultPerson = person)

        /** Eight spending categories with nothing under them, for someone who'd rather start small. */
        fun simple(ownerName: String = "Me", azerbaijani: Boolean = false): StarterTemplate = english(ownerName).let { full ->
            full.copy(categories = full.categories.filter { it.name in SIMPLE }.map { it.copy(children = emptyList()) })
        }.inLanguage(azerbaijani)

        /**
         * The household template designed with Emin; a sensible start for anyone. In Azerbaijani the names are written as
         * someone there would say them, and the owner's own name is left as typed.
         */
        fun household(ownerName: String = "Me", azerbaijani: Boolean = false): StarterTemplate = english(ownerName).inLanguage(azerbaijani)

        private fun StarterTemplate.inLanguage(azerbaijani: Boolean): StarterTemplate = if (!azerbaijani) this else copy(
            people = people.map { if (it.key == "family" || it.name == "Me") it.copy(name = AZERBAIJANI.getValue(it.name)) else it },
            accounts = accounts.map { it.copy(name = AZERBAIJANI.getValue(it.name)) },
            categories = categories.map { parent ->
                parent.copy(
                    name = AZERBAIJANI.getValue(parent.name),
                    children = parent.children.map { it.copy(name = AZERBAIJANI["${parent.name}/${it.name}"] ?: AZERBAIJANI.getValue(it.name)) },
                )
            },
        )

        private fun english(ownerName: String) = StarterTemplate(
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
                TemplateCategory("Kids & Education", E, "school", 0xFFF5B544, null, listOf(sub("School"), sub("Activities"), sub("Toys"))),
                TemplateCategory("Shopping", E, "shopping_bag", 0xFF8AB4F8, "me", listOf(sub("Clothes", "checkroom"), sub("Home"), sub("Electronics"), sub("Online"))),
                TemplateCategory("Entertainment", E, "sports_esports", 0xFFC79BF2, "me", listOf(sub("Games"), sub("Cinema"), sub("Activities"), sub("Massage", "spa"))),
                TemplateCategory("Gifts & Occasions", E, "redeem", 0xFFFF9F6E, "family", listOf(sub("Presents"), sub("Occasions"))),
                TemplateCategory("Family support", E, "family_restroom", 0xFF8A94A3, null, listOf(sub("Pocket money"), sub("Support"))),
                TemplateCategory("Travel", E, "flight", 0xFF6FC7D9, "family", listOf(sub("Hotel", "hotel"), sub("Transport"), sub("Food"), sub("Shopping"), sub("Fun"))),
                TemplateCategory("Other", E, "more_horiz", 0xFF8A94A3, "me"),
                TemplateCategory("Paycheck", I, "payments", 0xFF4ADE9B, "me", listOf(TemplateCategory("Salary", I), TemplateCategory("Bonus", I))),
                TemplateCategory("Cashback & Interest", I, "savings", 0xFF4ADE9B, "me"),
                TemplateCategory("Gift", I, "card_giftcard", 0xFFFF9F6E, "me"),
                TemplateCategory("Received from others", I, "swap_horiz", 0xFF6FC7D9, "me"),
                TemplateCategory("Other income", I, "more_horiz", 0xFF8A94A3, "me"),
            ),
        )

        /**
         * The template in Azerbaijani, by meaning rather than word for word. "Parent/Child" names a sub-category one way
         * under one parent where its English name is shared, like Activities, which for children are clubs.
         */
        private val AZERBAIJANI = mapOf(
            "Me" to "Mən", "Family" to "Ailə", "Card" to "Kart", "Cash" to "Nağd",
            "Groceries" to "Ərzaq",
            "Cafe & Restaurants" to "Kafe və restoranlar", "Coffee shop" to "Kofe", "Restaurant" to "Restoran", "Delivery" to "Çatdırılma",
            "Work" to "İş", "Breakfast" to "Səhər yeməyi", "Lunch" to "Nahar", "Snacks" to "Qəlyanaltı",
            "Colleague gifts" to "İş yoldaşlarına hədiyyə", "Collections" to "Pul yığımı",
            "Transport" to "Nəqliyyat", "Taxi" to "Taksi", "Public transport" to "İctimai nəqliyyat",
            "Car" to "Avtomobil", "Fuel" to "Yanacaq", "Wash" to "Yuyulma", "Service" to "Servis", "Parking" to "Dayanacaq",
            "Health" to "Sağlamlıq", "Doctor" to "Həkim", "Pharmacy" to "Aptek", "Lab tests" to "Analizlər",
            "Grooming" to "Şəxsi qulluq", "Barber" to "Bərbər", "Laser" to "Lazer", "Manicure / Pedicure" to "Manikür / Pedikür", "Hair" to "Saç",
            "Utilities" to "Kommunal xidmətlər", "Electricity" to "İşıq", "Gas" to "Qaz", "Water" to "Su", "Internet" to "İnternet",
            "Phone line" to "Ev telefonu",
            "Subscriptions" to "Abunəliklər", "Apps" to "Tətbiqlər", "Mobile top-up" to "Mobil balans",
            "Kids & Education" to "Uşaqlar və təhsil", "School" to "Məktəb", "Kids & Education/Activities" to "Dərnəklər", "Toys" to "Oyuncaqlar",
            "Shopping" to "Alış-veriş", "Clothes" to "Geyim", "Home" to "Ev", "Electronics" to "Elektronika", "Online" to "Onlayn",
            "Entertainment" to "Əyləncə", "Games" to "Oyunlar", "Cinema" to "Kino", "Activities" to "Aktiv istirahət", "Massage" to "Masaj",
            "Gifts & Occasions" to "Hədiyyələr və mərasimlər", "Presents" to "Hədiyyələr", "Occasions" to "Mərasimlər",
            "Family support" to "Ailəyə dəstək", "Pocket money" to "Cib xərcliyi", "Support" to "Dəstək",
            "Travel" to "Səyahət", "Hotel" to "Otel", "Food" to "Yemək", "Fun" to "Əyləncə",
            "Other" to "Digər",
            "Paycheck" to "Əmək haqqı", "Salary" to "Maaş", "Bonus" to "Mükafat",
            "Cashback & Interest" to "Keşbek və faizlər", "Gift" to "Hədiyyə", "Received from others" to "Başqalarından gələn",
            "Other income" to "Digər gəlir",
        )
    }
}
