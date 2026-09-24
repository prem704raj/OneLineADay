package com.onelineaday.dailydiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.security.AppLockManager
import com.onelineaday.dailydiary.ui.components.PinEntryScreen

@Composable
fun LockScreen(
    onUnlockClick: () -> Unit,
    authenticationAvailable: Boolean,
    errorMessage: String? = null,
    onOpenSecuritySettings: () -> Unit,
    modifier: Modifier = Modifier,
    lockType: AppLockManager.LockType = AppLockManager.LockType.BIOMETRIC,
    onPinComplete: (String) -> Unit = {}
) {
    if (lockType == AppLockManager.LockType.PIN || lockType == AppLockManager.LockType.BOTH) {
        PinEntryScreen(
            title = stringResource(R.string.enter_pin),
            subtitle = stringResource(R.string.pin_subtitle),
            onPinComplete = onPinComplete,
            onBiometricFallback = if (lockType == AppLockManager.LockType.BOTH) onUnlockClick else null,
            errorMessage = errorMessage
        )
    } else {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            ),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {

            /*
             * Lock illustration
             */
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme
                                    .colorScheme
                                    .primaryContainer,

                                MaterialTheme
                                    .colorScheme
                                    .secondaryContainer
                            )
                        )
                    ),
                contentAlignment =
                    Alignment.Center
            ) {

                Icon(
                    imageVector =
                        Icons.Rounded.Lock,

                    contentDescription =
                        stringResource(R.string.diary_locked),

                    modifier =
                        Modifier.size(64.dp),

                    tint =
                        MaterialTheme
                            .colorScheme
                            .onPrimaryContainer
                )
            }

            Spacer(
                modifier =
                    Modifier.height(32.dp)
            )

            Text(
                text = stringResource(R.string.diary_locked),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Bold,
                color =
                    MaterialTheme
                        .colorScheme
                        .onBackground
            )

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            Text(
                text =
                    if (authenticationAvailable) {

                        stringResource(R.string.lock_auth_message)

                    } else {

                        stringResource(R.string.lock_not_available)
                    },

                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,

                textAlign =
                    TextAlign.Center,

                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            if (!errorMessage.isNullOrBlank()) {

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                Text(
                    text =
                        errorMessage,

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,

                    textAlign =
                        TextAlign.Center,

                    color =
                        MaterialTheme
                            .colorScheme
                            .error
                )
            }

            Spacer(
                modifier =
                    Modifier.height(40.dp)
            )

            if (authenticationAvailable) {

                Button(
                    onClick =
                        onUnlockClick,

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),

                    shape =
                        RoundedCornerShape(
                            16.dp
                        )
                ) {

                    Icon(
                        imageVector =
                            Icons.Rounded.Lock,

                        contentDescription =
                            null
                    )

                    Spacer(
                        modifier =
                            Modifier.width(10.dp)
                    )

                    Text(
                        text =
                            stringResource(R.string.unlock_diary),

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,

                        fontWeight =
                            FontWeight.Bold
                    )
                }

            } else {

                FilledTonalButton(
                    onClick =
                        onOpenSecuritySettings,

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),

                    shape =
                        RoundedCornerShape(
                            16.dp
                        )
                ) {

                    Icon(
                        imageVector =
                            Icons.Rounded.Security,

                        contentDescription =
                            null
                    )

                    Spacer(
                        modifier =
                            Modifier.width(10.dp)
                    )

                    Text(
                        text =
                            stringResource(R.string.open_device_security),

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,

                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }
        }
    }
    }
}

