package com.grindrplus.manager.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grindrplus.core.Constants
import com.grindrplus.manager.GPlusMessage
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationScreen(
    innerPadding: PaddingValues,
    viewModel: NewsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading
    val errorMessage by viewModel.errorMessage
    val messages = viewModel.messages

    LaunchedEffect(Unit) {
        viewModel.fetchNews()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        data = Constants.NEWS_PAGE_URL.toUri()
                    })
                },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column {
                Text(
                    text = "News",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                )

                Text(
                    text = "Fork announcements for terenzif/GrindrPlus. Tap to open the wiki News page. " +
                        "Home shows Releases — this tab does not.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                )
            }
        }

        when {
            isLoading && messages.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            messages.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage?.let { "Could not load news" }
                                ?: "No news yet — open the wiki",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.fetchNews(forceRefresh = true) }) {
                            Text("Retry")
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        NewsMessageCard(message = message)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsMessageCard(message: GPlusMessage) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) {
        if (message.timestamp > 0L) {
            dateFormat.format(Date(message.timestamp * 1000))
        } else {
            ""
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (formattedTime.isNotEmpty()) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            MarkdownText(
                markdown = message.content,
                syntaxHighlightColor = Color.Transparent,
            )
        }
    }
}
