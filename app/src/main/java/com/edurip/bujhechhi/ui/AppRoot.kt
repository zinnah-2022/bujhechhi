package com.edurip.bujhechhi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.edurip.bujhechhi.server.ClassEngine

sealed interface Route {
    data object Home : Route
    data class Report(val sessionId: Long) : Route
    data class QuizEdit(val quizId: Long) : Route
}

@Composable
fun AppRoot() {
    val live by ClassEngine.live.collectAsState()
    var route by remember { mutableStateOf<Route>(Route.Home) }

    when (val r = route) {
        is Route.QuizEdit -> {
            BackHandler { route = Route.Home }
            QuizEditorScreen(id = r.quizId, onBack = { route = Route.Home })
        }
        is Route.Report -> {
            if (live.active) {
                LiveScreen(
                    onEnded = { id -> route = Route.Report(id) },
                    onEditQuiz = { route = Route.QuizEdit(it) },
                )
            } else {
                BackHandler { route = Route.Home }
                ReportScreen(sessionId = r.sessionId, onBack = { route = Route.Home })
            }
        }
        Route.Home -> {
            if (live.active) {
                LiveScreen(
                    onEnded = { id -> route = Route.Report(id) },
                    onEditQuiz = { route = Route.QuizEdit(it) },
                )
            } else {
                HomeScreen(
                    onReport = { route = Route.Report(it) },
                    onEditQuiz = { route = Route.QuizEdit(it) },
                )
            }
        }
    }
}
