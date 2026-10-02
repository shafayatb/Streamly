package com.shafayatb.streamly.core.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Text for the UI that is either already resolved or still a string resource to localize. */
@Immutable
sealed interface UiText {
    data class DynamicString(val value: String) : UiText
    data class Resource(val id: StringResource, val args: List<Any> = emptyList()) : UiText
    data class PluralResource(
        val id: PluralStringResource,
        val quantity: Int,
        val args: List<Any> = emptyList(),
    ) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.DynamicString -> value
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
    is UiText.PluralResource -> pluralStringResource(id, quantity, *args.toTypedArray())
}

/** Resolves the text outside composition, e.g. for a snackbar shown from an event. */
suspend fun UiText.resolve(): String = when (this) {
    is UiText.DynamicString -> value
    is UiText.Resource -> getString(id, *args.toTypedArray())
    is UiText.PluralResource -> getPluralString(id, quantity, *args.toTypedArray())
}
