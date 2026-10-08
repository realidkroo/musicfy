// AccountImportViewModel.kt

package com.example.musicfy.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.importer.ParsedImport
import com.example.musicfy.importer.account.AccountImportSession
import com.example.musicfy.importer.account.AccountImportState
import com.example.musicfy.importer.account.AccountService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** The Import settings page's account import; all the work is in [AccountImportSession]. */
@HiltViewModel
class AccountImportViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val service: AccountService = AccountService.fromRoute(savedStateHandle.get<String>("service")) ?: AccountService.SPOTIFY

    private val session = AccountImportSession(service, context, viewModelScope).also { it.start() }

    val state: StateFlow<AccountImportState> = session.state
    val ready: StateFlow<ParsedImport?> = session.ready
    val notice: StateFlow<String?> = session.notice

    fun onHeader(name: String, value: String, url: String) = session.onHeader(name, value, url)
    fun onApplePageProbe(raw: String?) = session.onApplePageProbe(raw)
    fun onCookies(cookieHeader: String?) = session.onCookies(cookieHeader)
    fun signInAgain() = session.signInAgain()
    fun refreshYouTube() = session.refreshYouTube()
    fun retry() = session.retry()
    fun toggle(id: String) = session.toggle(id)
    fun selectAll() = session.selectAll()
    fun selectNone() = session.selectNone()
    fun readSelected() = session.readSelected()
    fun cancelReading() = session.cancelReading()
    fun consumeReady() = session.consumeReady()
    fun clearNotice() = session.clearNotice()
}
