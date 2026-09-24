package com.grindrplus.manager.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.grindrplus.manager.GoatCounterSnapshot
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun HomeScreen(innerPadding: PaddingValues, viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.fetchData()
    }

    Column(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "GrindrPlus",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Enhanced Features for Grindr",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        GoatCounterStatsBlock(
            snapshot = viewModel.goatStats.value,
            loading = viewModel.goatStatsLoading.value,
        )

        viewModel.errorMessage.value?.let { message ->
            Text(
                text = message,
                color = Red,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Contributors",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (viewModel.isLoading.value) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(viewModel.contributors.size) { index ->
                        val (login, avatarUrl) = viewModel.contributors.entries.elementAt(viewModel.contributors.size - index - 1)
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Avatar of $login",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        "https://github.com/$login".toUri()
                                    ).also { intent ->
                                        context.startActivity(intent)
                                    }
                                },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            val sortedReleases = viewModel.releases.entries.sortedByDescending { (_, release) -> release.publishedAt }
            items(sortedReleases.size) { index ->
                val (_, release) = sortedReleases[index]
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AsyncImage(
                                model = release.avatarUrl,
                                contentDescription = "Avatar of ${release.author}",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text =
                                    "${release.author} • ${release.name}", fontSize = 14.sp, fontWeight = FontWeight.Bold
                            )
                        }

                        MarkdownText(
                            markdown = release.description,
                            syntaxHighlightColor = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoatCounterStatsBlock(
    snapshot: GoatCounterSnapshot?,
    loading: Boolean,
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Community stats",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        when {
            loading && snapshot == null -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
            snapshot != null -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCell(
                        label = "Total",
                        value = snapshot.totalLabel,
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        label = "This week",
                        value = snapshot.weekLabel,
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        label = "Home",
                        value = snapshot.homeLabel,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = "Open live board",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable {
                            Intent(
                                Intent.ACTION_VIEW,
                                snapshot.publicUrl.toUri(),
                            ).also { context.startActivity(it) }
                        },
                )
            }
            else -> {
                Text(
                    text = "Open live board",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://grindr-plus.goatcounter.com".toUri(),
                        ).also { context.startActivity(it) }
                    },
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
