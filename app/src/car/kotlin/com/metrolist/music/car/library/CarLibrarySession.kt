package com.metrolist.music.car.library

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CarLibrarySession(
    private val context: Context,
) {
    val repository =
        CarLibraryRepository(
            locationStore = SharedPreferencesCarLibraryLocationStore(context),
            accessFactory = AndroidCarLibraryTreeAccessFactory(context),
        )
    val mediaResolver = CarLibraryMediaResolver(repository)

    private val _index = MutableStateFlow<CarLibraryIndex?>(null)
    val index: StateFlow<CarLibraryIndex?> = _index.asStateFlow()

    suspend fun reopen(): CarLibraryOpenResult =
        repository.reopen().also(::applyOpenResult)

    suspend fun open(uri: Uri): CarLibraryOpenResult =
        repository.open(
            ContentTreeCarLibraryTreeAccess(
                resolver = context.contentResolver,
                treeUri = uri,
            ),
        ).also(::applyOpenResult)

    suspend fun refresh(): CarLibraryRefreshResult =
        repository.refresh().also(::applyRefreshResult)

    private fun applyOpenResult(result: CarLibraryOpenResult) {
        _index.value =
            when (result) {
                is CarLibraryOpenResult.Opened -> result.index
                is CarLibraryOpenResult.Invalid,
                is CarLibraryOpenResult.Unavailable,
                -> null
            }
    }

    private fun applyRefreshResult(result: CarLibraryRefreshResult) {
        when (result) {
            is CarLibraryRefreshResult.Unchanged -> _index.value = result.index
            is CarLibraryRefreshResult.Updated -> _index.value = result.index
            is CarLibraryRefreshResult.Invalid,
            is CarLibraryRefreshResult.Unavailable,
            -> Unit
        }
    }
}