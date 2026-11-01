package com.eminallahverdi.balance.core.data

import com.eminallahverdi.balance.core.database.dao.RecurringPaymentDao
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.RecurringPayment
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Reminders for payments that repeat. Nothing here adds a transaction: the owner wants every entry to be their own. */
@Singleton
class RecurringRepository @Inject constructor(
    private val dao: RecurringPaymentDao,
    private val transactions: TransactionRepository,
) {
    val payments: Flow<List<RecurringPayment>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun get(id: Long): RecurringPayment? = dao.get(id)?.toModel()
    suspend fun save(payment: RecurringPayment): Long = dao.upsert(payment.toEntity())
    suspend fun remove(id: Long) = dao.delete(id)

    /** Moves on once this date's entry is saved, which counts towards a loan. */
    suspend fun logged(payment: RecurringPayment) {
        dao.upsert(payment.logged().toEntity())
    }

    /** Moves past a date it didn't happen, which doesn't count towards a loan. */
    suspend fun skip(payment: RecurringPayment) {
        dao.upsert(payment.advanced().toEntity())
    }

    /** Moves past payments whose entry is already in the list, so a reminder only shows for one that's missing. */
    suspend fun settleLogged(today: LocalDate = LocalDate.now()) {
        dao.dueBy(today.toEpochDay()).map { it.toModel() }.forEach { payment ->
            var next = payment
            while (next.isDue(today) && isLogged(next, today)) next = next.logged()
            if (next != payment) dao.upsert(next.toEntity())
        }
    }

    private suspend fun isLogged(payment: RecurringPayment, today: LocalDate): Boolean =
        transactions.observeDetails(DateRange(payment.firstLoggedDay, today)).first().any(payment::isLoggedBy)

    suspend fun due(today: LocalDate = LocalDate.now()): List<RecurringPayment> =
        dao.dueBy(today.toEpochDay()).map { it.toModel() }.filter { it.isDue(today) }
}
