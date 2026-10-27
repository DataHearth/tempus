package com.eddyizm.tempus.util

import android.content.SharedPreferences
import com.eddyizm.tempus.App
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CustomCommandMigrationTest {

    private val firstButton = "custom_command_first_button"
    private val secondButton = "custom_command_second_button"

    /** Runs [block] against preferences backed by [stored], which the block's writes update. */
    private fun withPreferences(stored: MutableMap<String, Any>, block: () -> Unit) {
        val editor = mock<SharedPreferences.Editor>()
        whenever(editor.putString(anyString(), anyOrNull())).thenAnswer {
            stored[it.getArgument(0)] = it.getArgument<String>(1)
            editor
        }
        whenever(editor.putBoolean(anyString(), anyBoolean())).thenAnswer {
            stored[it.getArgument(0)] = it.getArgument<Boolean>(1)
            editor
        }

        val prefs = mock<SharedPreferences>()
        whenever(prefs.edit()).thenReturn(editor)
        whenever(prefs.getString(anyString(), anyOrNull())).thenAnswer {
            stored[it.getArgument(0)] as String? ?: it.getArgument<String?>(1)
        }
        whenever(prefs.getBoolean(anyString(), anyBoolean())).thenAnswer {
            stored[it.getArgument(0)] as Boolean? ?: it.getArgument<Boolean>(1)
        }

        val app = mock<App>()
        whenever(app.preferences).thenReturn(prefs)

        mockStatic(App::class.java).use { appStatic ->
            appStatic.`when`<App> { App.getInstance() }.thenReturn(app)
            block()
        }
    }

    @Test
    fun theOldRepeatDefaultBecomesInstantMix() {
        val stored = mutableMapOf<String, Any>(secondButton to "[repeatID]")

        withPreferences(stored) {
            Preferences.migrateCustomCommandSecondButton()
            assertEquals("[instantMixID]", Preferences.getCustomCommandSecondButton())
        }
    }

    @Test
    fun anotherChoiceIsKept() {
        val stored = mutableMapOf<String, Any>(secondButton to "[shuffleID]")

        withPreferences(stored) {
            Preferences.migrateCustomCommandSecondButton()
            assertEquals("[shuffleID]", Preferences.getCustomCommandSecondButton())
        }
    }

    @Test
    fun repeatPickedAfterTheMigrationIsKept() {
        val stored = mutableMapOf<String, Any>(secondButton to "[repeatID]")

        withPreferences(stored) {
            Preferences.migrateCustomCommandSecondButton()
            stored[secondButton] = "[repeatID]"
            Preferences.migrateCustomCommandSecondButton()
            assertEquals("[repeatID]", Preferences.getCustomCommandSecondButton())
        }
    }

    @Test
    fun repeatIsKeptWhenInstantMixIsAlreadyTheFirstButton() {
        val stored = mutableMapOf<String, Any>(firstButton to "[instantMixID]", secondButton to "[repeatID]")

        withPreferences(stored) {
            Preferences.migrateCustomCommandSecondButton()
            assertEquals("[repeatID]", Preferences.getCustomCommandSecondButton())
        }
    }
}
