package com.example.gameremotecontroller

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.gameremotecontroller.play.PlayViewModel
import com.example.gameremotecontroller.play.PlayViewModelFactory
import com.example.gameremotecontroller.ui.play.EditScreen
import com.example.gameremotecontroller.ui.play.PlayRoute

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Play)
  val context = LocalContext.current
  val playViewModel: PlayViewModel =
    viewModel(factory = PlayViewModelFactory(context.applicationContext))

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Play> {
          PlayRoute(
            viewModel = playViewModel,
            onOpenEdit = { backStack.add(Edit) },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<Edit> {
          val profile by playViewModel.profile.collectAsStateWithLifecycle()
          EditScreen(
            profile = profile,
            onSave = { p ->
              playViewModel.saveProfile(p)
              backStack.removeLastOrNull()
            },
            modifier = Modifier.safeDrawingPadding().padding(8.dp),
          )
        }
        entry<Connection> {
          Button(onClick = { backStack.removeLastOrNull() }) { Text("뒤로") }
        }
        entry<Main> {
          com.example.gameremotecontroller.ui.main.MainScreen(
            onItemClick = { navKey -> backStack.add(navKey) },
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
      },
  )
}
