package com.devin.gamebot

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.devin.gamebot.service.CaptureService
import com.devin.gamebot.ui.MainScreen
import com.devin.gamebot.ui.nav.Routes
import com.devin.gamebot.ui.screens.RecipeDetailScreen
import com.devin.gamebot.ui.screens.RecipeListScreen
import com.devin.gamebot.ui.screens.RecorderScreen
import com.devin.gamebot.ui.vm.RecipeDetailViewModel

/**
 * Hosts the top-level Compose [NavHost]. Phase 1 routes:
 *
 *   recipes (start)
 *     ├── recorder
 *     ├── recipes/{id}
 *     └── phase0/test  (legacy capture-debug screen, kept as escape hatch)
 *
 * The legacy capture flow remains available so the user can still verify
 * FLAG_SECURE / MediaProjection independent of the recipe pipeline.
 */
class MainActivity : ComponentActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    private val phase0ProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            CaptureService.start(this, result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MediaProjectionManager::class.java)

        setContent { AppNavHost() }
    }

    @Composable
    private fun AppNavHost() {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = Routes.RecipeList) {
            composable(Routes.RecipeList) {
                RecipeListScreen(
                    onCreateRecipe = { nav.navigate(Routes.Recorder) },
                    onOpenRecipe = { id -> nav.navigate(Routes.recipeDetail(id)) },
                )
            }
            composable(Routes.Recorder) {
                RecorderScreen(
                    onBack = { nav.popBackStack() },
                    onRecipeReady = { recipeId ->
                        nav.popBackStack()
                        nav.navigate(Routes.recipeDetail(recipeId))
                    },
                )
            }
            composable(
                route = Routes.RecipeDetail,
                arguments = listOf(navArgument("recipeId") { type = NavType.LongType }),
            ) { entry ->
                val recipeId = entry.arguments?.getLong("recipeId") ?: 0L
                val vm: RecipeDetailViewModel = viewModel(
                    factory = recipeDetailFactory(recipeId),
                )
                RecipeDetailScreen(
                    recipeId = recipeId,
                    onBack = { nav.popBackStack() },
                    vm = vm,
                )
            }
        }
    }

    private fun recipeDetailFactory(recipeId: Long): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return RecipeDetailViewModel(application, recipeId) as T
            }
        }

    /** Kept for the legacy Phase 0 capture-debug screen — wired via [MainScreen]. */
    @Suppress("unused")
    private fun requestPhase0Projection() {
        phase0ProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    @Suppress("unused")
    private fun openAccessibilitySettings() {
        startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
