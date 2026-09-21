package com.onelineaday.dailydiary.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onelineaday.dailydiary.BuildConfig
import com.onelineaday.dailydiary.PremiumManager
import com.onelineaday.dailydiary.billing.BillingManager

@OptIn(ExperimentalMaterial3Api::class)
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

    val monthlyPrice = BillingManager.getFormattedPrice(BillingManager.PRODUCT_MONTHLY)
        ?: if (isLoading) "..." else "$1.99"
    val lifetimePrice = BillingManager.getFormattedPrice(BillingManager.PRODUCT_LIFETIME)
        ?: if (isLoading) "..." else "$9.99"

    val premiumGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFD700), // Gold
            Color(0xFFFFA500)  // Orange
        )
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                                .size(64.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(premiumGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WorkspacePremium,
                                contentDescription = "Premium",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Go Premium",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Remove all ads forever, unlock all custom themes, and support future development.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Catalog error / Play Console notice banner if products not loaded
                if (products.isEmpty() && !isLoading) {
                    if (BuildConfig.DEBUG) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🧪 Developer Testing Mode",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Google Play catalog is offline or not configured. Tapping Continue will simulate purchase and grant Premium.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    } else if (catalogMessage != null) {
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
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Plans
                PremiumPlanCard(
                    title = "Monthly",
                    price = monthlyPrice,
                    period = "/ month",
                    isSelected = selectedPlan == "monthly",
                    onClick = { selectedPlan = "monthly" }
                )

                Spacer(modifier = Modifier.height(12.dp))

                PremiumPlanCard(
                    title = "Lifetime",
                    price = lifetimePrice,
                    period = "one-time",
                    isSelected = selectedPlan == "lifetime",
                    isPopular = true,
                    onClick = { selectedPlan = "lifetime" }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (selectedPlan != null && activity != null) {
                            val productId = if (selectedPlan == "monthly") BillingManager.PRODUCT_MONTHLY else BillingManager.PRODUCT_LIFETIME
                            val isSub = selectedPlan == "monthly"

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
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFA500),
                        disabledContainerColor = Color(0xFFFFA500).copy(alpha = 0.4f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Continue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                        text = "Restore Purchases",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(4.dp))
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
                            text = "🛠️ Activate Premium (Debug Test Mode)",
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
private fun PremiumPlanCard(
    title: String,
    price: String,
    period: String,
    isSelected: Boolean,
    isPopular: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFFFFA500) else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isSelected) Color(0xFFFFA500).copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (isSelected) Color(0xFFFFA500) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

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
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFA500))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "BEST VALUE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
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
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
