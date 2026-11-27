package com.eminallahverdi.balance.core.backup

import com.eminallahverdi.balance.core.model.AppEnvironment
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Dev's copy can never pass for the real one: not by folder, not by file name, not by the tag it's found with. */
class AccountantCopyTest {
    @Test fun `the real app's names are plain`() {
        assertThat(accountantFolderName(AppEnvironment.PROD)).isEqualTo("Cibim – accountant")
        assertThat(accountantFileName("snapshot", AppEnvironment.PROD)).isEqualTo("Cibim snapshot")
        assertThat(accountantFileName("transactions", AppEnvironment.PROD)).isEqualTo("Cibim transactions")
    }

    @Test fun `Dev's say they're test data`() {
        assertThat(accountantFolderName(AppEnvironment.DEV)).isEqualTo("Cibim Dev – accountant")
        assertThat(accountantFileName("snapshot", AppEnvironment.DEV)).isEqualTo("Cibim Dev snapshot – test data")
        assertThat(accountantFileName("transactions", AppEnvironment.DEV)).isEqualTo("Cibim Dev transactions – test data")
    }

    @Test fun `Dev's files are tagged apart`() {
        assertThat(accountantTag("snapshot", AppEnvironment.DEV)).isNotEqualTo(accountantTag("snapshot", AppEnvironment.PROD))
    }

    @Test fun `a rename sends only the name`() {
        assertThat(renameJson("Cibim snapshot")).isEqualTo("""{"name":"Cibim snapshot"}""")
    }
}
