package com.fastshare.app.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fastshare.app.data.local.db.FastShareDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class DatabaseTest {
    private lateinit var db: FastShareDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FastShareDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun `database creates without error`() = runTest {
        assertThat(db.transferDao().count()).isEqualTo(0)
    }
}
