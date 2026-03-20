package io.brokentooth.soma.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.brokentooth.soma.agent.ModelOption
import io.brokentooth.soma.ui.theme.SomaAccentBlue
import io.brokentooth.soma.ui.theme.SomaAccentGreen
import io.brokentooth.soma.ui.theme.SomaSurface
import io.brokentooth.soma.ui.theme.SomaTextMuted
import io.brokentooth.soma.ui.theme.SomaTextPrimary
import io.brokentooth.soma.ui.theme.SomaTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    models: List<ModelOption>,
    currentModelId: String,
    isLoading: Boolean,
    onModelSelected: (ModelOption) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SomaSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Select Model",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SomaTextPrimary,
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = SomaAccentBlue
                    )
                } else {
                    Text(
                        text = "${models.size} models",
                        fontSize = 11.sp,
                        color = SomaTextMuted
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Group models by category
            val grouped = models.groupBy { 
                if (it.provider == "gemini" && it.isFree) "Direct (Free)"
                else if (it.isFree) "Free Models"
                else if (it.ipdScore >= 80f) "High Value Paid"
                else "Standard Paid"
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                // Ensure order: Direct -> Free -> High Value -> Standard Paid
                val categoryOrder = listOf("Direct (Free)", "Free Models", "High Value Paid", "Standard Paid")
                val sortedGroups = grouped.entries.sortedBy { categoryOrder.indexOf(it.key).takeIf { idx -> idx != -1 } ?: 99 }

                sortedGroups.forEach { (category, categoryModels) ->
                    item(key = "header_$category") {
                        Text(
                            text = category.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = SomaTextMuted,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }

                    items(categoryModels, key = { "${it.provider}/${it.id}" }) { model ->
                        val isSelected = model.id == currentModelId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onModelSelected(model)
                                    onDismiss()
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = model.displayName,
                                fontSize = 14.sp,
                                color = if (isSelected) SomaTextPrimary else SomaTextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = SomaAccentGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
