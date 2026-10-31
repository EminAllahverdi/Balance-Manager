package com.eminallahverdi.balance.core.model

/** Someone the money is spent for. Fully user-editable: add, rename, recolour, set a photo, archive. */
data class Person(
    val id: Long,
    val name: String,
    /** ARGB colour used for dots, split bars and the avatar background. */
    val color: Long,
    /** Content URI of a picture copied into app storage, or null to show the initial. */
    val avatarUri: String? = null,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
) {
    val initial: String get() = name.trim().take(1).uppercase()
}

enum class CategoryType { EXPENSE, INCOME }

/**
 * A category or, when [parentId] is set, a sub-category.
 * Sub-categories inherit the parent's icon and colour unless they set their own.
 */
data class Category(
    val id: Long,
    val name: String,
    val type: CategoryType,
    val parentId: Long? = null,
    /** Key into the app's icon set, e.g. "shopping_cart". */
    val icon: String,
    val color: Long,
    /** Who this kind of spending is usually for. Null = use the parent's default, then "Me". */
    val defaultPersonId: Long? = null,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
) {
    val isSubCategory: Boolean get() = parentId != null

    /** For a sub-category when its parent changes: keeps anything it set itself, follows the rest. */
    fun followParentStyle(oldParent: Category, newParent: Category): Category = copy(
        icon = if (icon == oldParent.icon) newParent.icon else icon,
        color = if (color == oldParent.color) newParent.color else color,
    )
}

enum class AccountType { DEBIT_CARD, CREDIT_CARD, CASH, SAVINGS }

data class Account(
    val id: Long,
    val name: String,
    val type: AccountType,
    val icon: String,
    val color: Long,
    val includeInTotal: Boolean = true,
    val showOnWidget: Boolean = true,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
)

/** A shop or service. Remembers what you picked last time so the next entry is pre-filled. */
data class Merchant(
    val id: Long,
    val name: String,
    val lastCategoryId: Long? = null,
    val lastAccountId: Long? = null,
    val lastPersonId: Long? = null,
    val useCount: Int = 0,
)
