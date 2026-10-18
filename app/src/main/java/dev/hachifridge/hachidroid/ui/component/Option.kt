package dev.hachifridge.hachidroid.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import androidx.datastore.preferences.core.edit
import dev.hachifridge.hachidroid.core.dataStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map

@Composable
fun rememberDataStoreStringState(
    key: androidx.datastore.preferences.core.Preferences.Key<String>,
    defaultValue: String
): MutableState<String> {
    val context = LocalContext.current
    val dataStore = context.dataStore
    val coroutineScope = rememberCoroutineScope()

    val savedFlow = remember(key) {
        dataStore.data.map { preferences ->
            preferences[key] ?: defaultValue
        }
    }
    val savedValue = savedFlow.collectAsState(initial = defaultValue).value

    val state = remember {
        mutableStateOf(savedValue)
    }

    LaunchedEffect(savedValue) {
        if (state.value != savedValue) {
            state.value = savedValue
        }
    }

    return remember {
        object : MutableState<String> {
            override var value: String
                get() = state.value
                set(newValue) {
                    if (state.value != newValue) {
                        state.value = newValue

                        coroutineScope.launch {
                            dataStore.edit { preferences ->
                                preferences[key] = newValue
                            }
                        }
                    }
                }

            override fun component1() = value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}

/** Groups related options under an overline header in one tonal card. */
@Composable
fun OptionGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        SectionHeader(text = title)
        SectionCard(contentPadding = PaddingValues(0.dp)) {
            content()
        }
    }
}

@Composable
fun OptionBase(
    title: String,
    desc: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(all = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                OptionTitleAndDesc(title, desc)
            }
            Spacer(Modifier.width(16.dp))
            content()
        }
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
fun OptionTitleAndDesc(
    title: String,
    desc: String
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = desc,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun BooleanOption(
    title: String,
    desc: String,
    value: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    showDivider: Boolean = true
) {
    OptionBase(
        title = title,
        desc = desc,
        showDivider = showDivider,
        onClick = {
            if (enabled) {
                onCheckedChange(!value)
            }
        }
    ) {
        Switch(
            checked = value,
            onCheckedChange = {
                onCheckedChange(it)
            },
            enabled = enabled
        )
    }
}

@Composable
fun StringOption(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    showDivider: Boolean = true
) {
    var openDialog by remember { mutableStateOf(false) }
    OptionBase(
        title = title,
        desc = value,
        showDivider = showDivider,
        onClick = { openDialog = true }
    ) {}

    var tmpValue by remember { mutableStateOf(value) }
    LaunchedEffect(openDialog) {
        // Reset value every time dialog is reopened
        tmpValue = value
    }

    if (openDialog) {
        SimpleOkCancelDialog(
            title = title,
            onClose = { ok ->
                openDialog = false
                if (ok) {
                    onValueChange(tmpValue)
                }
            }
        ) {
            TextField(
                value = tmpValue,
                onValueChange = { tmpValue = it },
                placeholder = {
                    if (placeholder != null)
                        Text(placeholder)
                },
                keyboardOptions = keyboardOptions
            )
        }
    }
}

@Composable
fun IntOption(
    title: String,
    state: MutableIntState,
    placeholder: String? = null
) {
    val strValue = remember { mutableStateOf(state.intValue.toString()) }

    LaunchedEffect(strValue.value) {
        val str = strValue.value
        if (str.isDigitsOnly()) {
            val value = str.toInt()
            if (state.intValue != value)
                state.intValue = value
        }
    }

    StringOption(
        title = title,
        value = strValue.value,
        onValueChange = { strValue.value = it },
        placeholder = placeholder,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
fun RadioGroupOption(
    title: String,
    desc: String,
    choices: Array<String>,
    state: MutableState<Int>,
    showDivider: Boolean = true,
    choiceContent: @Composable (index: Int, text: String) -> Unit = { _, text ->
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp, 16.dp, 16.dp, bottom = 8.dp)
        ) {
            OptionTitleAndDesc(title, desc)
        }
        Column {
            choices.forEachIndexed { i, choice ->
                Row(
                    modifier = Modifier
                        .clickable { state.value = i }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = (state.value == i),
                            onClick = { state.value = i }
                        )
                        Spacer(Modifier.width(8.dp))
                        choiceContent(i, choice)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}