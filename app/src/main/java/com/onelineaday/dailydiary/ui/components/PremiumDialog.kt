package com.onelineaday.dailydiary.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onelineaday.dailydiary.BuildConfig
import com.onelineaday.dailydiary.PremiumManager
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.billing.BillingManager

@Composable
fun PremiumDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val products by BillingManager.products.collectAsState()
    val isLoading by BillingManager.isLoadingProducts.collectAsState()
    val catalogMessage by BillingManager.catalogMessage.collectAsState()
    val isPremium by PremiumManager.isPremium.collectAsState()

    var selectedPlan by remember { mutableStateOf<String?>("lifetime") }

    LaunchedEffect(Unit) {
        BillingManager.refreshProducts()
    }

    // Auto-dismiss dialog as soon as user gets premium
    LaunchedEffect(isPremium) {
        if (isPremium) {
            onDismiss()
        }
    }

    val monthlyRawPrice = BillingManager.getFormattedPrice(BillingManager.PRODUCT_MONTHLY)
    val lifetimeRawPrice = BillingManager.getFormattedPrice(BillingManager.PRODUCT_LIFETIME)

    val monthlyAvailable = monthlyRawPrice != null
    val lifetimeAvailable = lifetimeRawPrice != null

    val unavailableLabel = stringResource(R.string.premium_unavailable)
    val planUnavailableMessage = stringResource(R.string.premium_plan_unavailable)

    val monthlyPrice = monthlyRawPrice ?: if (isLoading) "..." else if (BuildConfig.DEBUG) "₹190.00" else unavailableLabel
    val lifetimePrice = lifetimeRawPrice ?: if (isLoading) "..." else if (BuildConfig.DEBUG) "₹950.00" else unavailableLabel

    val headerGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        )
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with close button
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(headerGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WorkspacePremium,
                                contentDescription = "Premium",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.premium_go_premium),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.premium_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Feature Highlights Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PremiumFeatureItem(stringResource(R.string.premium_feature_ad_free))
                        PremiumFeatureItem(stringResource(R.string.premium_feature_themes))
                        PremiumFeatureItem(stringResource(R.string.premium_feature_cloud_backup))
                        PremiumFeatureItem(stringResource(R.string.premium_feature_unlimited))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Play Store Notice if products not loaded in release
                if (products.isEmpty() && !isLoading && !BuildConfig.DEBUG && catalogMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Play Store Notice",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = catalogMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { BillingManager.refreshProducts() }) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Retry",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Plans
                PremiumPlanCard(
                    title = stringResource(R.string.premium_monthly),
                    price = monthlyPrice,
                    period = "/ month",
                    isSelected = selectedPlan == "monthly",
                    enabled = monthlyAvailable || BuildConfig.DEBUG,
                    onClick = { selectedPlan = "monthly" }
                )

                Spacer(modifier = Modifier.height(12.dp))

                PremiumPlanCard(
                    title = stringResource(R.string.premium_lifetime),
                    price = lifetimePrice,
                    period = "one-time",
                    isSelected = selectedPlan == "lifetime",
                    isPopular = true,
                    enabled = lifetimeAvailable || BuildConfig.DEBUG,
                    onClick = { selectedPlan = "lifetime" }
                )

                Spacer(modifier = Modifier.height(24.dp))

                val isCurrentPlanAvailable = (selectedPlan == "monthly" && (monthlyAvailable || BuildConfig.DEBUG)) ||
                        (selectedPlan == "lifetime" && (lifetimeAvailable || BuildConfig.DEBUG))

                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (selectedPlan != null && activity != null) {
                            val isSub = selectedPlan == "monthly"
                            val productId = if (isSub) BillingManager.PRODUCT_MONTHLY else BillingManager.PRODUCT_LIFETIME
                            val isLiveAvailable = if (isSub) monthlyAvailable else lifetimeAvailable

                            if (!isLiveAvailable) {
                                if (BuildConfig.DEBUG) {
                                    // Developer / offline debug simulation
                                    PremiumManager.setPremium(context, true, testMode = true)
                                    Toast.makeText(context, "Debug: Premium activated for testing!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                    return@Button
                                }
                                Toast.makeText(context, planUnavailableMessage, Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            BillingManager.launchBillingFlow(
                                activity = activity,
                                productId = productId,
                                isSubscription = isSub,
                                onSuccess = {
                                    onDismiss()
                                },
                                onError = { errorMsg ->
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                }
                            )
                        } else if (selectedPlan == null) {
                            Toast.makeText(context, "Please select a plan", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isLoading && isCurrentPlanAvailable,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.premium_continue),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Restore purchases button
                TextButton(
                    onClick = {
                        val activity = context as? Activity
                        if (activity != null) {
                            BillingManager.restorePurchases(activity)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.premium_restore),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Debug Test Mode toggle (automatically stripped in Release builds)
                if (BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            PremiumManager.setPremium(context, true, testMode = true)
                            Toast.makeText(context, "Developer Test: Premium activated!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "🛠️ Instant Test Premium (Debug Only)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumFeatureItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PremiumPlanCard(
    title: String,
    price: String,
    period: String,
    isSelected: Boolean,
    isPopular: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val borderColor = if (!enabled) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    } else if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val bgColor = if (!enabled) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    } else if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected && enabled) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isPopular) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = stringResource(R.string.premium_best_value),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = price,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = period,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

