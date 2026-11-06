package com.speakeasy.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.speakeasy.app.ui.screens.AiChatScreen
import com.speakeasy.app.ui.screens.ArticleScreen
import com.speakeasy.app.ui.screens.AiHubScreen
import com.speakeasy.app.ui.screens.BookScreen
import com.speakeasy.app.ui.screens.ChallengeScreen
import com.speakeasy.app.ui.screens.DialogueScreen
import com.speakeasy.app.ui.screens.GameHubScreen
import com.speakeasy.app.ui.screens.GameScreen
import com.speakeasy.app.ui.screens.HomeScreen
import com.speakeasy.app.ui.screens.LabHubScreen
import com.speakeasy.app.ui.screens.LabLessonScreen
import com.speakeasy.app.ui.screens.NewsScreen
import com.speakeasy.app.ui.screens.StoriesHubScreen
import com.speakeasy.app.ui.screens.StoryScreen
import com.speakeasy.app.ui.screens.PhrasesScreen
import com.speakeasy.app.ui.screens.PlacementScreen
import com.speakeasy.app.ui.screens.ProgressScreen
import com.speakeasy.app.ui.screens.ReviewScreen
import com.speakeasy.app.ui.screens.ShadowScreen
import com.speakeasy.app.ui.screens.SpeakScreen
import com.speakeasy.app.ui.screens.TopicScreen
import com.speakeasy.app.ui.screens.VocabGroupScreen
import com.speakeasy.app.ui.screens.VocabHubScreen
import com.speakeasy.app.ui.screens.VideoHubScreen
import com.speakeasy.app.ui.screens.VideoScreen
import com.speakeasy.app.ui.theme.SkyLight

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Học", Icons.Filled.Home),
    Tab("play", "Chơi", Icons.Filled.SportsEsports),
    Tab("news", "Đọc báo", Icons.Filled.Newspaper),
    Tab("ai", "Nói với AI", Icons.AutoMirrored.Filled.Chat),
    Tab("me", "Tiến độ", Icons.Filled.Insights),
)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = SkyLight),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable("home") {
                HomeScreen(
                    openTopic = { nav.navigate("topic/$it") },
                    openReview = { nav.navigate("review") },
                    openLab = { nav.navigate("lab") },
                    openStories = { nav.navigate("stories") },
                    openVocab = { nav.navigate("vocab") },
                    openNews = { nav.switchTab("news") },
                    openVideos = { nav.navigate("videos") },
                    openPlacement = { nav.navigate("placement") },
                )
            }
            composable("vocab") { VocabHubScreen(onBack = nav::popBackStack, openGroup = { nav.navigate("vocab/$it") }) }
            composable("vocab/{id}") { VocabGroupScreen(it.arguments!!.getString("id")!!, onBack = nav::popBackStack) }
            composable("news") { NewsScreen(openArticle = { link -> nav.navigate("article/${Uri.encode(link)}") }) }
            composable("article/{link}") { ArticleScreen(it.arguments!!.getString("link")!!, onBack = nav::popBackStack) }
            composable("videos") { VideoHubScreen(onBack = nav::popBackStack, openVideo = { nav.navigate("video/$it") }) }
            composable("video/{id}") {
                VideoScreen(
                    it.arguments!!.getString("id")!!,
                    onBack = nav::popBackStack,
                    // Another video of the channel replaces this one, so back still returns to the hub.
                    openVideo = { id -> nav.navigate("video/$id") { popUpTo("videos") } },
                )
            }
            composable("review") { ReviewScreen(onBack = nav::popBackStack) }
            composable("lab") { LabHubScreen(onBack = nav::popBackStack, openLesson = { nav.navigate("lab/$it") }) }
            composable("lab/{id}") { LabLessonScreen(it.arguments!!.getString("id")!!, onBack = nav::popBackStack) }
            composable("stories") {
                StoriesHubScreen(
                    onBack = nav::popBackStack,
                    openStory = { nav.navigate("story/$it") },
                    openBook = { nav.navigate("book/$it") },
                )
            }
            composable("book/{id}") { BookScreen(it.arguments!!.getString("id")!!, onBack = nav::popBackStack) }
            composable("story/{id}") { StoryScreen(it.arguments!!.getString("id")!!, onBack = nav::popBackStack) }
            composable("play") {
                GameHubScreen(
                    openGame = { mode -> nav.navigate("game/mix/$mode") },
                    openChallenge = { nav.navigate("challenge/$it") },
                    openLab = { nav.navigate("lab") },
                    openStories = { nav.navigate("stories") },
                )
            }
            composable("challenge/{id}") { ChallengeScreen(it.arguments!!.getString("id")!!, onBack = nav::popBackStack) }
            composable("me") { ProgressScreen(openPlacement = { nav.navigate("placement") }) }
            composable("placement") { PlacementScreen(onBack = nav::popBackStack, onContinue = { nav.switchTab("home") }) }
            composable("ai") { AiHubScreen(openChat = { nav.navigate("ai/$it") }) }
            composable("ai/{scenario}") {
                AiChatScreen(it.arguments!!.getString("scenario")!!, onBack = nav::popBackStack)
            }
            composable("topic/{id}") {
                TopicScreen(
                    topicId = it.arguments!!.getString("id")!!,
                    onBack = nav::popBackStack,
                    open = { path -> nav.navigate(path) },
                )
            }
            composable("topic/{id}/phrases") {
                PhrasesScreen(it.arguments!!.getString("id")!!, onBack = nav::popBackStack)
            }
            composable("topic/{id}/dialogue") {
                DialogueScreen(it.arguments!!.getString("id")!!, onBack = nav::popBackStack)
            }
            composable("topic/{id}/speak") {
                SpeakScreen(it.arguments!!.getString("id")!!, onBack = nav::popBackStack)
            }
            composable("topic/{id}/shadow") {
                ShadowScreen(it.arguments!!.getString("id")!!, onBack = nav::popBackStack)
            }
            composable("game/{id}/{mode}") {
                GameScreen(
                    topicId = it.arguments!!.getString("id")!!,
                    mode = it.arguments!!.getString("mode")!!,
                    onBack = nav::popBackStack,
                )
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
