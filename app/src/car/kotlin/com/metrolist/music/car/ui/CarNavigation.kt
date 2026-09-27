package com.metrolist.music.car.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CarDestination {
    NOW_PLAYING,
    DOWNLOADS,
    LIBRARY,
    SEARCH,
    MORE,
}

fun carStartDestination(hasRestoredPlayback: Boolean): CarDestination =
    if (hasRestoredPlayback) CarDestination.NOW_PLAYING else CarDestination.DOWNLOADS

class CarNavigationState(
    startDestination: CarDestination,
) {
    private val _stack = MutableStateFlow(listOf(startDestination))
    val stack: StateFlow<List<CarDestination>> = _stack.asStateFlow()

    val current: CarDestination
        get() = _stack.value.last()

    fun navigate(destination: CarDestination) {
        if (destination == current) return
        _stack.value = _stack.value + destination
    }

    fun back(): Boolean {
        val currentStack = _stack.value
        if (currentStack.size <= 1) return false
        _stack.value = currentStack.dropLast(1)
        return true
    }
}
