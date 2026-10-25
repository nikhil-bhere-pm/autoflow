package com.autoflow.app.presentation.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoflow.app.R
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.util.CurrencyFormatter
import com.autoflow.app.presentation.viewmodel.AutopayUiState

@Composable
fun AutopayScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    uiState: AutopayUiState = AutopayUiState.Empty
) {
    val colors = AutoFlowTheme.colors

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            Surface(
                color = colors.bg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.categories_title),
                            tint = colors.ink
                        )
                    }

                    Text(
                        text = stringResource(R.string.autopay_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Summary card (surface, 20dp radius, 16dp padding, soft shadow)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.sheet,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.autopay_summary_label),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.muted
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = CurrencyFormatter.formatPaiseToInr(0L),
                        style = AutoFlowTheme.typography.displaySmall.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = colors.ink
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.autopay_summary_empty_line),
                        fontSize = 12.5.sp,
                        color = colors.muted
                    )
                }
            }

            // 2. Section label "How it will work" + Card with 3 rows
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.autopay_how_it_works_section),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.muted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.sheet,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Row 1: Autopay tag
                        HowItWorksRow(
                            iconResId = R.drawable.ic_repeat,
                            title = stringResource(R.string.autopay_tag_title),
                            text = stringResource(R.string.autopay_tag_text)
                        )

                        HorizontalDivider(thickness = 1.dp, color = colors.line)

                        // Row 2: Repeats tag
                        HowItWorksRow(
                            iconResId = R.drawable.ic_cat_calendar,
                            title = stringResource(R.string.autopay_repeats_title),
                            text = stringResource(R.string.autopay_repeats_text)
                        )

                        HorizontalDivider(thickness = 1.dp, color = colors.line)

                        // Row 3: Next expected
                        HowItWorksRow(
                            iconResId = R.drawable.ic_cat_calendar,
                            title = stringResource(R.string.autopay_next_expected_title),
                            text = stringResource(R.string.autopay_next_expected_text)
                        )
                    }
                }
            }

            // 3. Note row (info icon + 12.5sp muted text)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 1.dp)
                )

                Text(
                    text = stringResource(R.string.autopay_note_text),
                    fontSize = 12.5.sp,
                    color = colors.muted,
                    lineHeight = 18.sp
                )
            }

            // 4. Centred empty block: 56dp circle in chip color, 15sp bold title, 13sp muted text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.chip),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_repeat),
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.autopay_empty_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.autopay_empty_text),
                    fontSize = 13.sp,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.widthIn(max = 280.dp)
                )
            }
        }
    }
}

@Composable
private fun HowItWorksRow(
    iconResId: Int,
    title: String,
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(AppRadius.md))
                .background(colors.tint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = colors.onTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )

            Text(
                text = text,
                fontSize = 12.5.sp,
                color = colors.muted,
                lineHeight = 16.sp
            )
        }
    }
}

@Preview(name = "Light - AutopayScreen")
@Composable
private fun AutopayScreenLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        AutopayScreen(onNavigateBack = {})
    }
}

@Preview(name = "Dark - AutopayScreen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutopayScreenDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        AutopayScreen(onNavigateBack = {})
    }
}
