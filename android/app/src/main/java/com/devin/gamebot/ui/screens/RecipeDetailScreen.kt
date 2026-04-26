package com.devin.gamebot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devin.gamebot.ui.vm.RecipeDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: Long,
    onBack: () -> Unit,
    vm: RecipeDetailViewModel,
) {
    val recipe by vm.recipe.collectAsStateWithLifecycle()
    val steps by vm.steps.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.name ?: "Recipe") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            recipe?.let { r ->
                OutlinedTextField(
                    value = r.name,
                    onValueChange = { vm.rename(it) },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                r.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("Steps (${steps.size})", style = MaterialTheme.typography.titleMedium)
            steps.forEachIndexed { idx, step ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${idx + 1}. ${step.intent}", style = MaterialTheme.typography.bodyLarge)
                        step.expectAfter?.let {
                            Text(
                                "Expect after: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "Action hint: ${step.actionHint}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // recipeId is required for ViewModel construction; reference it to avoid
            // warnings if future edits drop the parameter from the body.
            @Suppress("UNUSED_EXPRESSION") recipeId
        }
    }
}
