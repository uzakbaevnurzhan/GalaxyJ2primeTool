package com.example.ui.github

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL

data class GitHubRelease(
    val name: String,
    val tagName: String,
    val htmlUrl: String,
    val body: String,
    val publishedAt: String
)

class GitHubViewModel : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _releases = MutableStateFlow<List<GitHubRelease>>(emptyList())
    val releases: StateFlow<List<GitHubRelease>> = _releases

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        fetchReleases()
    }

    private fun fetchReleases() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val releasesList = withContext(Dispatchers.IO) {
                    val url = "https://api.github.com/repos/uzakbaevnurzhan/GalaxyJ2primeTool/releases"
                    val response = URL(url).readText()
                    val jsonArray = JSONArray(response)
                    val list = mutableListOf<GitHubRelease>()
                    
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(
                            GitHubRelease(
                                name = obj.optString("name", "Unknown Release"),
                                tagName = obj.optString("tag_name", "v0.0"),
                                htmlUrl = obj.optString("html_url", ""),
                                body = obj.optString("body", "No description provided."),
                                publishedAt = obj.optString("published_at", "").take(10)
                            )
                        )
                    }
                    list
                }
                _releases.value = releasesList
            } catch (e: Exception) {
                _error.value = "Failed to load releases: ${e.message}. Is the repo public?"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

@Composable
fun GitHubScreen(navController: NavController, viewModel: GitHubViewModel = viewModel()) {
    val isLoading by viewModel.isLoading.collectAsState()
    val releases by viewModel.releases.collectAsState()
    val error by viewModel.error.collectAsState()
    val uriHandler = LocalUriHandler.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("GitHub Releases", style = MaterialTheme.typography.headlineMedium)
        Text("uzakbaevnurzhan/GalaxyJ2primeTool", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        if (!isLoading && error == null && releases.isEmpty()) {
            Text("No releases found.")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(releases) { release ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (release.htmlUrl.isNotEmpty()) {
                                uriHandler.openUri(release.htmlUrl)
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(release.name.ifEmpty { release.tagName }, style = MaterialTheme.typography.titleMedium)
                            Text(release.publishedAt, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = release.body,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3
                        )
                    }
                }
            }
        }
    }
}
