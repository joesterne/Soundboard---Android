package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.api.GeminiHelper
import kotlinx.coroutines.launch

@Composable
fun WebSearchDialog(
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search Web for Sounds (AI)") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search Query") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (query.isNotBlank()) {
                            isSearching = true
                            resultText = ""
                            coroutineScope.launch {
                                resultText = GeminiHelper.searchWeb(query)
                                isSearching = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = query.isNotBlank() && !isSearching
                ) {
                    Text(if (isSearching) "Searching..." else "Search")
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (resultText.isNotEmpty()) {
                    androidx.compose.foundation.text.selection.SelectionContainer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
