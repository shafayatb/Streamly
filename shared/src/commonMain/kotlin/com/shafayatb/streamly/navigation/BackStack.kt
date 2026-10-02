package com.shafayatb.streamly.navigation

/**
 * Makes [route] the only destination, e.g. after sign-in so Back cannot return to onboarding.
 * Adds before removing so the stack is never empty mid-update.
 */
fun <T> MutableList<T>.resetTo(route: T) {
    add(route)
    while (size > 1) removeAt(0)
}

/** Pops the top destination but never the root, which belongs to the system back gesture. */
fun <T> MutableList<T>.popIfNotRoot() {
    if (size > 1) removeAt(lastIndex)
}

/** Swaps the top destination for [route], so Back skips the one it replaced. */
fun <T> MutableList<T>.replaceTop(route: T) {
    if (isEmpty()) add(route) else set(lastIndex, route)
}
