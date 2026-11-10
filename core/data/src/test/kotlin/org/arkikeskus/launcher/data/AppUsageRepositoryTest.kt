package org.arkikeskus.launcher.data

import com.google.common.truth.Truth.assertThat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppUsageRepositoryTest {

    private val half = AppUsageRepository.HALF_LIFE_MS

    @Test
    fun `currentScore halves across one half-life`() {
        val stat = UsageStat(score = 4f, lastUsed = 0L)
        assertThat(AppUsageRepository.currentScore(stat, half)).isWithin(0.001f).of(2f)
    }

    @Test
    fun `currentScore does not inflate when the clock goes backwards`() {
        val stat = UsageStat(score = 4f, lastUsed = half)
        // atMillis before lastUsed → elapsed clamps to 0 → no growth.
        assertThat(AppUsageRepository.currentScore(stat, 0L)).isWithin(0.001f).of(4f)
    }

    @Test
    fun `applyLaunch on a new app yields score 1 at the launch time`() {
        val stat = AppUsageRepository.applyLaunch(null, 1234L)
        assertThat(stat.score).isWithin(0.001f).of(1f)
        assertThat(stat.lastUsed).isEqualTo(1234L)
    }

    @Test
    fun `applyLaunch one half-life later decays then adds one`() {
        val prev = UsageStat(score = 1f, lastUsed = 0L)
        val stat = AppUsageRepository.applyLaunch(prev, half)
        assertThat(stat.score).isWithin(0.001f).of(1.5f) // 0.5 decay + 1
        assertThat(stat.lastUsed).isEqualTo(half)
    }

    @Test
    fun `a recently used app outranks an older more-used app`() {
        val day = 24L * 60 * 60 * 1000
        val now = 100L * day
        val recent = UsageStat(score = 3f, lastUsed = now - 1 * day)
        val older = UsageStat(score = 5f, lastUsed = now - 30 * day)
        assertThat(AppUsageRepository.currentScore(recent, now))
            .isGreaterThan(AppUsageRepository.currentScore(older, now))
    }

    @Test
    fun `recordLaunch persists a stat through the data store`() = runTest {
        val repo = AppUsageRepository(InMemoryDataStore())
        repo.recordLaunch("com.example/Main/0")
        val stat = repo.usage.first()["com.example/Main/0"]
        assertThat(stat).isNotNull()
        assertThat(stat!!.score).isWithin(0.001f).of(1f)
    }

    @Test
    fun `a launch before the store was read builds on the stored score`() = runBlocking {
        val key = "com.example/Main/0"
        val now = System.currentTimeMillis()
        val store = InMemoryDataStore()
        store.edit { it[stringPreferencesKey(AppUsageRepository.USAGE_KEY)] = "$key\t5.0\t$now" }
        val repo = AppUsageRepository(store)
        // Recorded at once, before the repository's own read of the store has come back.
        repo.recordLaunch(key)
        val shown = withTimeout(5_000) { repo.usage.first { (it[key]?.score ?: 0f) > 5.5f } }
        assertThat(shown[key]!!.score).isWithin(0.01f).of(6f)
        // And the write keeps the history: 5 + 1, not 1.
        repo.flush()
        val written = withTimeout(5_000) {
            store.data.map { it[stringPreferencesKey(AppUsageRepository.USAGE_KEY)].orEmpty() }
                .first { it.startsWith("$key\t6") }
        }
        assertThat(written.split("\t")[1].toFloat()).isWithin(0.01f).of(6f)
    }
}

