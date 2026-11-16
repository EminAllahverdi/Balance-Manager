package com.eminallahverdi.balance.core.data

import com.eminallahverdi.balance.core.database.dao.PlannedPaymentDao
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.PlannedPayment
import com.eminallahverdi.balance.core.model.PlannedStatus
import com.eminallahverdi.balance.core.model.plannedStatuses
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * One-off payments the owner means to make. Whether each is paid is worked out from the entries as they change, so
 * deleting the entry that paid one puts it back, and nothing is written but the owner's own ticks.
 */
@Singleton
class PlannedRepository @Inject constructor(
    private val dao: PlannedPaymentDao,
    private val transactions: TransactionRepository,
) {
    private val planned: Flow<List<PlannedPayment>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    /** Each planned payment with what paid it. Only entries since the earliest still waiting was planned are read. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val statuses: Flow<List<PlannedStatus>> = planned.flatMapLatest { list ->
        val from = list.filter { it.paidOn == null }.minOfOrNull { it.plannedOn }
        if (from == null) {
            flowOf(plannedStatuses(list, emptyList()))
        } else {
            transactions.observeDetails(DateRange(from, DateRange.ALL_TIME.endInclusive)).map { plannedStatuses(list, it) }
        }
    }

    suspend fun save(payment: PlannedPayment): Long = dao.upsert(payment.toEntity())
    suspend fun remove(id: Long) = dao.delete(id)

    /** Ticks it as paid on [on], or with null unticks it. */
    suspend fun setPaid(id: Long, on: LocalDate?) = dao.setPaid(id, on?.toEpochDay())
}
