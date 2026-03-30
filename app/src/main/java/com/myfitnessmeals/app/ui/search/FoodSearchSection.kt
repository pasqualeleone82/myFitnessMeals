package com.myfitnessmeals.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun FoodSearchSection(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearchClicked: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Search by text", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                label = { Text("Food name") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("meal_search_input"),
                singleLine = true,
            )
            Button(
                onClick = onSearchClicked,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("meal_search_button"),
            ) {
                Text("Search")
            }
        }
    }
}
