package com.onelineaday.dailydiary.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onelineaday.dailydiary.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.privacy_policy_title)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(
                                R.string.privacy_policy_back
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 20.dp,
                    vertical = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            Text(
                text = stringResource(
                    R.string.privacy_v2_last_updated
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PolicySection(
                title = stringResource(
                    R.string.privacy_v2_section_1_title
                ),
                content = stringResource(
                    R.string.privacy_v2_section_1_content
                )
            )

            PolicySection(
                title = stringResource(
                    R.string.privacy_v2_section_2_title
                ),
                content = stringResource(
                    R.string.privacy_v2_section_2_content
                )
            )

            PolicySection(
                title = stringResource(
                    R.string.privacy_v2_section_3_title
                ),
                content = stringResource(
                    R.string.privacy_v2_section_3_content
                )
            )

            PolicySection(
                title = stringResource(
                    R.string.privacy_v2_section_4_title
                ),
                content = stringResource(
                    R.string.privacy_v2_section_4_content
                )
            )

            PolicySection(
                title = stringResource(
                    R.string.privacy_v2_section_5_title
                ),
                content = stringResource(
                    R.string.privacy_v2_section_5_content
                )
            )

            PolicySection(
                title = stringResource(
                    R.string.privacy_v2_section_6_title
                ),
                content = stringResource(
                    R.string.privacy_v2_section_6_content
                )
            )

            PolicySection(
                title = stringResource(
                    R.string.privacy_v2_section_7_title
                ),
                content = stringResource(
                    R.string.privacy_v2_section_7_content
                )
            )

            PolicySection(
                title = stringResource(
                    R.string.privacy_v2_section_8_title
                ),
                content = stringResource(
                    R.string.privacy_v2_section_8_content
                )
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

@Composable
private fun PolicySection(
    title: String,
    content: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
