package org.arkikeskus.launcher.feature.appdrawer

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.SearchResult
import org.arkikeskus.launcher.ui.LauncherIcons
import org.arkikeskus.launcher.ui.component.ContactAvatar
import org.arkikeskus.launcher.ui.drawer.DrawerColors
import org.arkikeskus.launcher.ui.drawer.DrawerStyleSpec
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import org.arkikeskus.launcher.ui.expressive.ExpressiveCard
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

// The drawer's search field and its result rows (calculator, contacts, web search) (split out of AppDrawerScreen.kt).

/** The drawer search field, shared by the top and bottom positions. */
@Composable
internal fun DrawerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    colors: DrawerColors,
    style: DrawerStyleSpec,
) {
    val container = if (style.searchOutlined) Color.Transparent else colors.field
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.app_drawer_search_hint)) },
        leadingIcon = {
            Icon(
                painter = painterResource(LauncherIcons.Search),
                contentDescription = null,
                tint = colors.contentDim,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(LauncherIcons.Close),
                        contentDescription = stringResource(R.string.search_clear),
                        tint = colors.accent,
                    )
                }
            }
        },
        shape = style.searchShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = colors.accent,
            focusedTextColor = colors.content,
            unfocusedTextColor = colors.content,
            focusedPlaceholderColor = colors.contentDim,
            unfocusedPlaceholderColor = colors.contentDim,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .then(
                if (style.searchOutlined) {
                    Modifier.border(1.dp, colors.contentDim.copy(alpha = 0.6f), style.searchShape)
                } else {
                    Modifier
                },
            ),
    )
}

/** The Gemini app, and Google's Gemini-powered "AI Mode" search page (which takes the query in its URL). */
private const val GEMINI_PACKAGE = "com.google.android.apps.bard"

private const val GEMINI_SEARCH_URL = "https://www.google.com/search?udm=50&q="

/**
 * Hands [query] to the system's web search. A device without one gets Gemini instead: the query is
 * shared to the Gemini app when it is installed, otherwise the Gemini-powered search page opens.
 */
internal fun launchWebSearch(context: android.content.Context, query: String) {
    if (query.isBlank()) return
    val started = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_WEB_SEARCH)
                .putExtra(android.app.SearchManager.QUERY, query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (started) return
    val sharedToGemini = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, query)
                .setPackage(GEMINI_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (!sharedToGemini) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(GEMINI_SEARCH_URL + android.net.Uri.encode(query)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

@Composable
internal fun CalcResultCard(calc: SearchResult.Calculation, onCopy: () -> Unit) {
    val p = LocalExpressivePalette.current
    ExpressiveCard(onClick = onCopy) {
        Text("${calc.expression} = ", color = p.dim, fontSize = 18.sp)
        Text(calc.result, color = LocalAccent.current, fontSize = 22.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

@Composable
internal fun ContactResultRow(contact: SearchResult.Contact, onOpened: () -> Unit) {
    val context = LocalContext.current
    val p = LocalExpressivePalette.current
    val callDesc = stringResource(R.string.search_contact_call)
    val messageDesc = stringResource(R.string.search_contact_message)
    ExpressiveCard(onClick = {
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(contact.lookupUri))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            onOpened()
        }
    }) {
        // Avatar is decorative here — the contact name provides the accessible label.
        ContactAvatar(name = contact.name, photoUri = contact.photoUri)
        Text(contact.name, color = p.text, fontSize = 16.sp,
            modifier = Modifier.weight(1f).padding(start = 14.dp))
        if (contact.number != null) {
            IconButton(onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_DIAL,
                            android.net.Uri.fromParts("tel", contact.number, null))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    onOpened()
                }
            }) { Icon(painter = painterResource(LauncherIcons.Call), contentDescription = callDesc, tint = LocalAccent.current) }
            IconButton(onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_SENDTO,
                            android.net.Uri.fromParts("smsto", contact.number, null))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    onOpened()
                }
            }) { Icon(painter = painterResource(LauncherIcons.Message), contentDescription = messageDesc, tint = LocalAccent.current) }
        }
    }
}
