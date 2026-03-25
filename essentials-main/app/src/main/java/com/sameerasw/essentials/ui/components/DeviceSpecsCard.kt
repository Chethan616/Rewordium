package com.sameerasw.essentials.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.data.model.DeviceSpecs
import com.sameerasw.essentials.ui.components.containers.RoundedCardContainer
import com.sameerasw.essentials.ui.components.modifiers.shimmer
import com.sameerasw.essentials.ui.theme.Shapes

@Composable
fun DeviceSpecsCard(
    deviceSpecs: DeviceSpecs?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    RoundedCardContainer(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Section Header
        SpecHeader("Device Specifications")

        if (isLoading) {
            repeat(5) {
                LoadingSpecSection()
            }
        } else if (deviceSpecs != null) {
            deviceSpecs.detailSpec.forEach { category ->
                SpecSection(
                    title = category.category,
                    specs = category.specifications.map { it.name to it.value }
                )
            }

            if (deviceSpecs.imageUrls.isNotEmpty()) {
                SpecFooter(deviceSpecs)
            }

        } else {
            // Fallback or empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = Shapes.extraSmall
                    )
                    .padding(32.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = "Specifications unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LoadingSpecSection() {
    Column(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = Shapes.extraSmall
            )
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(16.dp)
                .clip(Shapes.extraSmall)
                .shimmer()
        )

        Spacer(modifier = Modifier.height(12.dp))

        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(12.dp)
                        .clip(Shapes.extraSmall)
                        .shimmer()
                )
                Spacer(modifier = Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(Shapes.extraSmall)
                        .shimmer()
                )
            }
        }
    }
}

@Composable
private fun SpecHeader(title: String) {
    Column(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = Shapes.extraSmall
            )
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}


@Composable
private fun SpecFooter(deviceSpecs: DeviceSpecs?) {
    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = Shapes.extraSmall
            )
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = "Powered by",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        Text(
            text = "GSMArena.com",
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
            ),
            modifier = Modifier
                .clickable {
                    uriHandler.openUri("https://www.gsmarena.com")
                }
                .padding(start = 8.dp)
        )
    }
}

@Composable
private fun SpecSection(
    title: String,
    specs: List<Pair<String, String>>
) {
    Column(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = Shapes.extraSmall
            )
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        specs.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    modifier = Modifier.width(100.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
