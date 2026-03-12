package com.gitmob.android.ui.create

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitmob.android.data.RepoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.gitmob.android.ui.theme.*

class CreateRepoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = RepoRepository()
    private val _done = MutableStateFlow<String?>(null) // fullName on success
    val done = _done.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    fun create(name: String, desc: String, private: Boolean, autoInit: Boolean) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val r = repo.createRepo(name.trim(), desc.trim().ifEmpty { null }, private, autoInit)
                _done.value = r.fullName
            } catch (e: Exception) {
                _error.value = e.message ?: "创建失败"
            } finally {
                _loading.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRepoScreen(
    onBack: () -> Unit,
    onCreated: (String, String) -> Unit, // owner, repo
    vm: CreateRepoViewModel = viewModel(),
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var private by remember { mutableStateOf(false) }
    var autoInit by remember { mutableStateOf(true) }

    val done by vm.done.collectAsState()
    val error by vm.error.collectAsState()
    val loading by vm.loading.collectAsState()

    LaunchedEffect(done) {
        done?.let { fullName ->
            val parts = fullName.split("/")
            if (parts.size == 2) onCreated(parts[0], parts[1])
        }
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = { Text("新建仓库", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField(label = "仓库名称 *", value = name, onValueChange = { name = it }, placeholder = "my-project", singleLine = true)
            FormField(label = "描述（可选）", value = desc, onValueChange = { desc = it }, placeholder = "项目描述...", singleLine = false)

            ToggleCard(label = "私有仓库", sub = "仅自己可见", checked = private, onToggle = { private = it })
            ToggleCard(label = "自动初始化 README", sub = "创建后即可克隆", checked = autoInit, onToggle = { autoInit = it })

            if (error != null) {
                Text(error!!, color = RedColor, fontSize = 13.sp,
                    modifier = Modifier.background(RedDim, RoundedCornerShape(8.dp)).padding(10.dp).fillMaxWidth())
            }

            Button(
                onClick = {
                    if (name.isNotBlank()) vm.create(name, desc, private, autoInit)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
                enabled = name.isNotBlank() && !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("创建仓库", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun FormField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, singleLine: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextTertiary, fontSize = 14.sp) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Coral, unfocusedBorderColor = Border,
                focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            ),
        )
    }
}

@Composable
private fun ToggleCard(label: String, sub: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(BgCard, RoundedCornerShape(12.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(label, fontSize = 14.sp, color = TextPrimary)
            Text(sub, fontSize = 11.sp, color = TextTertiary, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Coral,
                uncheckedThumbColor = TextTertiary, uncheckedTrackColor = BgItem,
            ),
        )
    }
}
