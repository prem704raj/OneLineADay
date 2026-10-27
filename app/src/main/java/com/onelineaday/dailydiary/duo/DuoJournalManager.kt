package com.onelineaday.dailydiary.duo

import android.content.Context
import android.content.SharedPreferences
import com.onelineaday.dailydiary.data.Journal
import com.onelineaday.dailydiary.data.JournalRepository
import java.security.MessageDigest
import java.util.UUID

class DuoJournalManager(
    private val repository: JournalRepository,
    private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "duo_journal_prefs"
        private const val KEY_DUO_JOURNAL_ID = "active_duo_journal_id"
        private const val KEY_DUO_PIN_HASH = "duo_pin_hash"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasDuoJournal(): Boolean = prefs.contains(KEY_DUO_JOURNAL_ID)

    fun getDuoJournalId(): String? = prefs.getString(KEY_DUO_JOURNAL_ID, null)

    suspend fun createDuoJournal(partnerName: String, pin: String): Journal {
        val id = "duo_" + UUID.randomUUID().toString().take(8)
        val journalName = "Duo ($partnerName)"
        val journal = Journal(id = id, name = journalName, colorHex = "#E05A8D")

        repository.insertJournal(journal)

        val pinHash = hashPin(pin)
        prefs.edit()
            .putString(KEY_DUO_JOURNAL_ID, id)
            .putString(KEY_DUO_PIN_HASH, pinHash)
            .apply()

        return journal
    }

    fun verifyDuoPin(inputPin: String): Boolean {
        val savedHash = prefs.getString(KEY_DUO_PIN_HASH, null) ?: return true
        return hashPin(inputPin) == savedHash
    }

    fun removeDuoJournal() {
        prefs.edit().clear().apply()
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(("olad_duo_salt_" + pin).toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

