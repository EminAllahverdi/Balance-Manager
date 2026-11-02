package com.eminallahverdi.balance.core.data

import com.eminallahverdi.balance.core.database.entity.AccountEntity
import com.eminallahverdi.balance.core.database.entity.CategoryEntity
import com.eminallahverdi.balance.core.database.entity.DebtEntity
import com.eminallahverdi.balance.core.database.entity.MerchantEntity
import com.eminallahverdi.balance.core.database.entity.PendingEntryEntity
import com.eminallahverdi.balance.core.database.entity.PersonEntity
import com.eminallahverdi.balance.core.database.entity.RecurringPaymentEntity
import com.eminallahverdi.balance.core.database.entity.TransactionEntity
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.Debt
import com.eminallahverdi.balance.core.model.DebtDirection
import com.eminallahverdi.balance.core.model.LoanTerms
import com.eminallahverdi.balance.core.model.Merchant
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.PendingEntry
import com.eminallahverdi.balance.core.model.PendingStatus
import com.eminallahverdi.balance.core.model.Person
import com.eminallahverdi.balance.core.model.RecurringFrequency
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.core.model.Transaction
import com.eminallahverdi.balance.core.model.TransactionKind
import java.time.Instant
import java.time.LocalDate

internal fun PersonEntity.toModel() = Person(id, name, color, avatarUri, sortOrder, isArchived)
internal fun Person.toEntity() = PersonEntity(id, name, color, avatarUri, sortOrder, isArchived)

internal fun CategoryEntity.toModel() = Category(id, name, CategoryType.valueOf(type), parentId, icon, color, defaultPersonId, sortOrder, isArchived)
internal fun Category.toEntity() = CategoryEntity(id, name, type.name, parentId, icon, color, defaultPersonId, sortOrder, isArchived)

internal fun AccountEntity.toModel() = Account(id, name, AccountType.valueOf(type), icon, color, includeInTotal, showOnWidget, sortOrder, isArchived, cardEnding)
internal fun Account.toEntity() = AccountEntity(id, name, type.name, icon, color, includeInTotal, showOnWidget, sortOrder, isArchived, cardEnding)

internal fun MerchantEntity.toModel() = Merchant(id, name, lastCategoryId, lastAccountId, lastPersonId, useCount)

internal fun RecurringPaymentEntity.toModel() = RecurringPayment(
    id = id,
    name = name,
    amount = Money(amountMinor),
    accountId = accountId,
    categoryId = categoryId,
    personId = personId,
    frequency = RecurringFrequency.valueOf(frequency),
    nextDate = LocalDate.ofEpochDay(nextDate),
    anchorDay = anchorDay,
    loan = paymentsLeft?.let { LoanTerms(paymentsMade ?: 0, it, lastAmountMinor?.let(::Money)) },
)

internal fun RecurringPayment.toEntity() = RecurringPaymentEntity(
    id = id,
    name = name,
    amountMinor = amount.minor,
    accountId = accountId,
    categoryId = categoryId,
    personId = personId,
    frequency = frequency.name,
    nextDate = nextDate.toEpochDay(),
    anchorDay = anchorDay,
    paymentsMade = loan?.paymentsMade,
    paymentsLeft = loan?.paymentsLeft,
    lastAmountMinor = loan?.lastPayment?.minor,
)

internal fun TransactionEntity.toModel() = Transaction(
    id = id,
    kind = TransactionKind.valueOf(kind),
    amount = Money(amountMinor),
    date = LocalDate.ofEpochDay(date),
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    personId = personId,
    merchantId = merchantId,
    note = note,
    tripName = tripName,
    debtId = debtId,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

internal fun Transaction.toEntity(externalId: String? = null) = TransactionEntity(
    id = id,
    kind = kind.name,
    amountMinor = amount.minor,
    date = date.toEpochDay(),
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    personId = personId,
    merchantId = merchantId,
    note = note,
    tripName = tripName,
    externalId = externalId,
    debtId = debtId,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

internal fun DebtEntity.toModel() = Debt(id, counterparty, DebtDirection.valueOf(direction), Money(amountMinor), categoryId, note, LocalDate.ofEpochDay(date))
internal fun Debt.toEntity() = DebtEntity(id, counterparty, direction.name, amount.minor, categoryId, note, date.toEpochDay())

internal fun PendingEntryEntity.toModel() = PendingEntry(
    id = id,
    externalId = externalId,
    source = source,
    amount = Money(amountMinor),
    kind = TransactionKind.valueOf(kind),
    merchant = merchant,
    postedAt = Instant.ofEpochMilli(postedAt),
    status = PendingStatus.valueOf(status),
    title = title,
    cardEnding = cardEnding,
)
