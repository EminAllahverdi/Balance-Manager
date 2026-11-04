package com.eminallahverdi.balance.core.data

import androidx.room.withTransaction
import com.eminallahverdi.balance.core.database.BalanceDatabase
import com.eminallahverdi.balance.core.database.dao.DebtDao
import com.eminallahverdi.balance.core.database.dao.TransactionDao
import com.eminallahverdi.balance.core.model.Debt
import com.eminallahverdi.balance.core.model.DebtBalance
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.TransactionKind
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One thing to pay back: how much of [debt], and who the expense was for when it becomes one. */
data class DebtPayment(val debt: Debt, val paid: Money, val personId: Long?)

@Singleton
class DebtRepository @Inject constructor(
    private val db: BalanceDatabase,
    private val dao: DebtDao,
    private val transactionDao: TransactionDao,
    private val transactions: TransactionRepository,
) {
    /** Every debt with what's settled, worked out from its entries, so editing or deleting one keeps this right. */
    val debts: Flow<List<DebtBalance>> = combine(dao.observeAll(), transactionDao.observeDebtEntries()) { debts, entries ->
        val rows = entries.map { it.toModel() }
        debts.map { it.toModel() }.map { debt -> DebtBalance(debt, debt.paidBack(rows), debt.mine(rows)) }
    }

    /** Records a debt, and for a plain loan the money that moved in or out of [accountId]. */
    suspend fun add(debt: Debt, accountId: Long?) = db.withTransaction {
        val saved = debt.copy(id = dao.upsert(debt.toEntity()))
        if (accountId != null) saved.opening(accountId)?.let { transactions.save(it, merchantName = null) }
    }

    /**
     * A bill paid for others too, all at once: the whole of it leaves [accountId], [mine] of it as spending and the rest as
     * owed. The shop is remembered on both, so a share marked later still shows where it was.
     */
    suspend fun addPaidForOthers(bill: Debt, accountId: Long, mine: Money, merchant: String?, personId: Long?) = db.withTransaction {
        val saved = bill.copy(id = dao.upsert(bill.toEntity()))
        val share = saved.share(mine, accountId, personId)
        val merchantId = merchant?.let { transactions.teach(it, share) }
        if (!mine.isZero) transactions.save(share.copy(merchantId = merchantId), merchantName = null)
        saved.opening(accountId, mine)?.let { transactions.save(it.copy(merchantId = merchantId), merchantName = null) }
    }

    /**
     * Marks [mine] of what the others still owe on a bill as the user's own share. It becomes spending in the bill's category, on
     * the bill's day, from the account that paid it, and that much less is counted as owed, so the balance doesn't move.
     */
    suspend fun addShare(bill: Debt, mine: Money, personId: Long?) = db.withTransaction {
        val entries = transactionDao.forDebt(bill.id).map { it.toModel() }
        val opening = entries.firstOrNull { it.kind == TransactionKind.DEBT && it.amount.isNegative }
        val earlier = entries.firstOrNull { it.kind == TransactionKind.EXPENSE }
        val accountId = opening?.accountId ?: earlier?.accountId ?: return@withTransaction
        val share = bill.share(mine, accountId, earlier?.personId ?: personId).copy(merchantId = opening?.merchantId ?: earlier?.merchantId)
        transactions.save(share, merchantName = null)
        if (opening != null) {
            val stillOwed = opening.amount + mine
            if (stillOwed.isNegative) transactions.save(opening.copy(amount = stillOwed), merchantName = null) else transactionDao.delete(opening.id)
        }
    }

    /** Pays each one from [accountId] as its own entry, so each lands in its own category. */
    suspend fun pay(payments: List<DebtPayment>, accountId: Long, on: LocalDate) = db.withTransaction {
        payments.forEach { payment ->
            transactions.save(payment.debt.repayment(payment.paid, accountId, on, payment.personId), merchantName = null)
        }
    }

    /** For a debt added by mistake: removes it and the money it moved. */
    suspend fun remove(debt: Debt) = db.withTransaction {
        transactionDao.deleteForDebt(debt.id)
        dao.delete(debt.id)
    }
}
