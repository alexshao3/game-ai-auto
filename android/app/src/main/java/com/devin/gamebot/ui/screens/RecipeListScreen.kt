package com.devin.gamebot.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devin.gamebot.R
import com.devin.gamebot.ui.vm.RecipeListViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    onCreateRecipe: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    vm: RecipeListViewModel = viewModel(),
) {
    val recipes by vm.recipes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("AI Game Bot") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateRecipe) {
                Icon(Icons.Filled.Add, contentDescription = "Record new task")
            }
        },
    ) { padding ->
        if (recipes.isEmpty()) {
            Column(
                Modifier
                    .padding(padding)
                    .padding(24.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.recipe_list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            val df = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(recipes, key = { it.id }) { recipe ->
                    ListItem(
                        headlineContent = { Text(recipe.name) },
                        supportingContent = {
                            val updated = df.format(Date(recipe.updatedAtMs))
                            Text(recipe.description ?: "Updated $updated", maxLines = 2)
                        },
                        trailingContent = {
                            IconButton(onClick = { vm.delete(recipe) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenRecipe(recipe.id) }
                            .padding(horizontal = 8.dp),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
