package com.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.data.ContactRuleEntity
import com.expensetracker.ui.theme.DarkPrimary
import com.expensetracker.ui.theme.DarkSurface
import com.expensetracker.ui.theme.TextSecondary
import com.expensetracker.ui.viewmodels.MainViewModel

import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(viewModel: MainViewModel = viewModel()) {
    val sampleRules by viewModel.rules.collectAsState()
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<ContactRuleEntity?>(null) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Automation Targets",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Set email dispatch frequencies for your contacts.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (sampleRules.isEmpty()) {
                Text("No automation rules configured. Press + to add your first email target.", color = TextSecondary, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(sampleRules) { rule ->
                        RuleItem(
                            rule = rule, 
                            onToggle = { enabled -> viewModel.toggleRule(rule, enabled) },
                            onDelete = { viewModel.deleteRule(rule) },
                            onSendInvoice = { viewModel.sendManualInvoice(rule, context) },
                            onEdit = { editingRule = rule }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddRuleDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = DarkPrimary,
            contentColor = Color.White
        ) {
            Icon(Icons.Filled.Add, "Add Target")
        }

        if (showAddRuleDialog) {
            AddRuleDialog(
                onDismiss = { showAddRuleDialog = false },
                onSave = { name, email, freqType, freqVal ->
                    viewModel.addRule(name, email, freqType, freqVal)
                    showAddRuleDialog = false
                }
            )
        }

        editingRule?.let { rule ->
            EditRuleDialog(
                rule = rule,
                onDismiss = { editingRule = null },
                onSave = { updatedRule ->
                    viewModel.updateRule(updatedRule)
                    editingRule = null
                }
            )
        }
    }
}

@Composable
fun RuleItem(rule: ContactRuleEntity, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onSendInvoice: () -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Email, contentDescription = null, tint = DarkPrimary)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Send to ${rule.name}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(text = rule.email, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                IconButton(onClick = onSendInvoice) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Send, contentDescription = "Send Invoice", tint = DarkPrimary)
                }
                IconButton(onClick = onEdit) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Edit, contentDescription = "Edit Rule", tint = Color.Gray)
                }
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = DarkPrimary, checkedTrackColor = DarkPrimary.copy(alpha = 0.5f))
                )
                IconButton(onClick = onDelete) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Delete, contentDescription = "Delete Rule", tint = Color.Red)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Type: ${rule.frequencyType}", style = MaterialTheme.typography.bodySmall, color = DarkPrimary)
                Text(text = "Value: ${rule.frequencyValue}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var frequencyValue by remember { mutableStateOf("") }
    
    val frequencyOptions = listOf("Manual", "Date", "Payment Count")
    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf(frequencyOptions[0]) }
    
    var emailError by remember { mutableStateOf(false) }
    var emptyError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.padding(24.dp)
            ) {
                item {
                    Text(text = "New Dispatch Rule", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; emptyError = false },
                        label = { Text("Contact Name (e.g. Dhruv)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DarkPrimary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { 
                            email = it
                            emailError = email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
                            emptyError = false
                        },
                        label = { Text("Contact Email") },
                        isError = emailError,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DarkPrimary)
                    )
                    if (emailError) {
                        Text("Invalid email format", color = Color.Red, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedOption,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Frequency Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DarkPrimary)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            frequencyOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        selectedOption = option
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    if (selectedOption != "Manual") {
                        OutlinedTextField(
                            value = frequencyValue,
                            onValueChange = { frequencyValue = it; emptyError = false },
                            label = { Text("Value (e.g. 5 days, 10 tx)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DarkPrimary)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                item {
                    if (emptyError) {
                        Text("Please fill required fields", color = Color.Red, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Button(
                            onClick = { 
                                if (name.isBlank() || email.isBlank() || emailError || (selectedOption != "Manual" && frequencyValue.isBlank())) {
                                    emptyError = true
                                } else {
                                    onSave(name, email, selectedOption, frequencyValue)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary)
                        ) {
                            Text("Save Target", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRuleDialog(rule: ContactRuleEntity, onDismiss: () -> Unit, onSave: (ContactRuleEntity) -> Unit) {
    var frequencyValue by remember { mutableStateOf(rule.frequencyValue) }
    var currentCount by remember { mutableStateOf(rule.currentCount.toString()) }
    
    val frequencyOptions = listOf("Manual", "Date", "Payment Count")
    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf(rule.frequencyType) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = DarkSurface, modifier = Modifier.fillMaxWidth()) {
            LazyColumn(modifier = Modifier.padding(24.dp)) {
                item {
                    Text(text = "Edit Rule: ${rule.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(text = "Name and Email are locked for identity.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selectedOption, onValueChange = {}, readOnly = true,
                            label = { Text("Frequency Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DarkPrimary)
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            frequencyOptions.forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = { selectedOption = option; expanded = false })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    if (selectedOption != "Manual") {
                        OutlinedTextField(
                            value = frequencyValue, onValueChange = { frequencyValue = it },
                            label = { Text("Frequency Value (e.g. 5)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DarkPrimary)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                item {
                    OutlinedTextField(
                        value = currentCount, onValueChange = { currentCount = it },
                        label = { Text("Current Payment Count") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DarkPrimary)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
                        Button(
                            onClick = { 
                                val cValue = currentCount.toIntOrNull() ?: 0
                                onSave(rule.copy(frequencyType = selectedOption, frequencyValue = frequencyValue, currentCount = cValue))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary)
                        ) { Text("Update Rule", color = Color.White) }
                    }
                }
            }
        }
    }
}
