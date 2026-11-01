package com.eminallahverdi.balance.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,
    @ColumnInfo(name = "avatar_uri") val avatarUri: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
)

@Entity(
    tableName = "category",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["parent_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["default_person_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("parent_id"), Index("default_person_id")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "EXPENSE" or "INCOME". */
    val type: String,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    val icon: String,
    val color: Long,
    @ColumnInfo(name = "default_person_id") val defaultPersonId: Long? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
)

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val icon: String,
    val color: Long,
    @ColumnInfo(name = "include_in_total") val includeInTotal: Boolean = true,
    @ColumnInfo(name = "show_on_widget") val showOnWidget: Boolean = true,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
)

@Entity(tableName = "merchant", indices = [Index(value = ["name"], unique = true)])
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "last_category_id") val lastCategoryId: Long? = null,
    @ColumnInfo(name = "last_account_id") val lastAccountId: Long? = null,
    @ColumnInfo(name = "last_person_id") val lastPersonId: Long? = null,
    @ColumnInfo(name = "use_count") val useCount: Int = 0,
)

@Entity(
    tableName = "txn",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["to_account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["person_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = MerchantEntity::class, parentColumns = ["id"], childColumns = ["merchant_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = DebtEntity::class, parentColumns = ["id"], childColumns = ["debt_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["external_id"], unique = true), Index("date"), Index("account_id"), Index("to_account_id"), Index("category_id"), Index("person_id"), Index("merchant_id"), Index("debt_id")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    /** Minor units (qəpik). */
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    /** LocalDate.toEpochDay(). */
    val date: Long,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "to_account_id") val toAccountId: Long? = null,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "person_id") val personId: Long? = null,
    @ColumnInfo(name = "merchant_id") val merchantId: Long? = null,
    val note: String = "",
    @ColumnInfo(name = "trip_name") val tripName: String? = null,
    /** Id in the system it was imported from, so a re-import never duplicates. */
    @ColumnInfo(name = "external_id") val externalId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "debt_id") val debtId: Long? = null,
)

@Entity(
    tableName = "debt",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("category_id")],
)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val counterparty: String,
    /** "I_OWE" or "OWED_TO_ME". */
    val direction: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    val note: String = "",
    /** LocalDate.toEpochDay(). */
    val date: Long,
)

@Entity(
    tableName = "recurring_payment",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["person_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("account_id"), Index("category_id"), Index("person_id")],
)
data class RecurringPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "person_id") val personId: Long? = null,
    /** "WEEKLY", "MONTHLY" or "YEARLY". */
    val frequency: String,
    /** LocalDate.toEpochDay(). */
    @ColumnInfo(name = "next_date") val nextDate: Long,
    @ColumnInfo(name = "anchor_day") val anchorDay: Int,
    /** Set on a bank loan, like the two below. */
    @ColumnInfo(name = "payments_made") val paymentsMade: Int? = null,
    @ColumnInfo(name = "payments_left") val paymentsLeft: Int? = null,
    @ColumnInfo(name = "last_amount_minor") val lastAmountMinor: Long? = null,
)

/** A ready-made entry read from a bank's notification. Finished ones stay a while, so a repeated notification isn't offered twice. */
@Entity(tableName = "pending_entry", indices = [Index(value = ["external_id"], unique = true)])
data class PendingEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "external_id") val externalId: String,
    val source: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val kind: String,
    val merchant: String,
    @ColumnInfo(name = "posted_at") val postedAt: Long,
    /** The notification's title, which can say what a payment was when the text doesn't. */
    val title: String = "",
    /** "WAITING", "ADDED" or "SKIPPED". */
    val status: String,
)

/** The merchant a bank's wording was confirmed as, so BRAVO SUPERMARKET comes back as Bravo. [raw] is lowercase. */
@Entity(
    tableName = "merchant_alias",
    foreignKeys = [ForeignKey(entity = MerchantEntity::class, parentColumns = ["id"], childColumns = ["merchant_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("merchant_id")],
)
data class MerchantAliasEntity(
    @PrimaryKey val raw: String,
    @ColumnInfo(name = "merchant_id") val merchantId: Long,
)
