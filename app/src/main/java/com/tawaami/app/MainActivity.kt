package com.tawaami.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private val Dark = Color(0xFF0B0D12)
private val SurfaceColor = Color(0xFF171B22)
private val CardColor = Color(0xFF20262F)
private val Pink = Color(0xFFD9A8BD)
private val PinkSoft = Color(0xFFEFD8E4)
private val White = Color.White
private val Muted = Color(0xFFB7B9BF)

data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Pink,
                    background = Dark,
                    surface = SurfaceColor
                )
            ) {
                TawaamiApp(this)
            }
        }
    }
}

@Composable
fun TawaamiApp(activity: MainActivity) {
    val prefs = remember { activity.getSharedPreferences("tawaami", Activity.MODE_PRIVATE) }
    val taskEngine = remember { TaskEngine(activity) }
    val offlineAI = remember { OfflineAI() }

    var tab by remember { mutableStateOf(0) }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("جاهز") }
    var memory by remember { mutableStateOf(prefs.getString("memory", "") ?: "") }
    var companionName by remember { mutableStateOf(prefs.getString("companion_name", "توأمي") ?: "توأمي") }
    var tasks by remember { mutableStateOf(taskEngine.loadTasks(prefs)) }
    var messages by remember { mutableStateOf(loadMessages(prefs)) }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) draft = spoken
        }
    }

    fun saveMessages(list: List<ChatMessage>) {
        val arr = JSONArray()
        list.forEach { msg ->
            arr.put(JSONObject().put("text", msg.text).put("fromUser", msg.fromUser))
        }
        prefs.edit().putString("messages", arr.toString()).apply()
    }

    fun sendMessage() {
        val text = draft.trim()
        if (text.isEmpty()) return

        val userMessage = ChatMessage(text, true)
        val next = messages + userMessage
        messages = next
        saveMessages(next)
        draft = ""

        val localReply = offlineAI.respond(text)
        val commandReply = taskEngine.handleCommand(text)

        val finalReply = when {
            text.contains("الوقت") || text.contains("التاريخ") || text.contains("حساب") ||
            text.contains("فتح") || text.contains("مهامي") || text.contains("مذكرة") ||
            text.contains("تذكير") -> commandReply
            else -> localReply
        }

        val botMessage = ChatMessage(finalReply, false)
        val updated = messages + botMessage
        messages = updated
        saveMessages(updated)
        status = "تمت الاستجابة"
    }

    when (tab) {
        0 -> ChatScreen(
            messages = messages,
            draft = draft,
            onDraftChange = { draft = it },
            onSend = { sendMessage() },
            onVoiceInput = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث إلى توائمي")
                }
                runCatching { speechLauncher.launch(intent) }
            },
            status = status,
            companionName = companionName
        )

        1 -> TaskScreen(
            tasks = tasks,
            onAdd = { title -> tasks = taskEngine.addTask(prefs, title) },
            onToggle = { id -> tasks = taskEngine.toggleTask(prefs, id) },
            onDelete = { id -> tasks = taskEngine.deleteTask(prefs, id) }
        )

        2 -> MemoryScreen(
            memory = memory,
            onSave = { newMemory ->
                memory = newMemory
                prefs.edit().putString("memory", newMemory).apply()
            }
        )

        3 -> SettingsScreen(
            companionName = companionName,
            onNameChanged = {
                companionName = it
                prefs.edit().putString("companion_name", it).apply()
            },
            onClearChat = {
                messages = emptyList()
                saveMessages(emptyList())
                status = "تم مسح المحادثة"
            }
        )
    }

    NavigationBar(containerColor = SurfaceColor) {
        NavigationBarItem(
            selected = tab == 0,
            onClick = { tab = 0 },
            icon = { Icon(Icons.Default.Chat, null) },
            label = { Text("المحادثة") }
        )
        NavigationBarItem(
            selected = tab == 1,
            onClick = { tab = 1 },
            icon = { Icon(Icons.Default.Checklist, null) },
            label = { Text("المهام") }
        )
        NavigationBarItem(
            selected = tab == 2,
            onClick = { tab = 2 },
            icon = { Icon(Icons.Default.Bookmark, null) },
            label = { Text("الذاكرة") }
        )
        NavigationBarItem(
            selected = tab == 3,
            onClick = { tab = 3 },
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text("الإعدادات") }
        )
    }
}

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceInput: () -> Unit,
    status: String,
    companionName: String
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Dark).padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(Pink, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🤖", fontSize = 22.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(companionName, color = White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("مساعد ذكي محلي", color = PinkSoft, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(status, color = PinkSoft, fontSize = 10.sp)
        }

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🫂", fontSize = 70.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("أنا توائمي", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("أستطيع مساعدتك في المهام والردود والمعلومات.", color = Muted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.fromUser) Arrangement.Start else Arrangement.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (msg.fromUser) Pink.copy(alpha = 0.18f) else CardColor,
                            modifier = Modifier.widthIn(max = 300.dp)
                        ) {
                            Text(
                                text = msg.text,
                                color = White,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onVoiceInput) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Pink)
            }

            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("اكتب أو تحدث...") }
            )

            IconButton(onClick = onSend, enabled = draft.isNotBlank()) {
                Icon(Icons.Default.Send, contentDescription = null, tint = Pink)
            }
        }
    }
}

@Composable
fun TaskScreen(
    tasks: List<TaskItem>,
    onAdd: (String) -> Unit,
    onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    var taskDraft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(Dark).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("قائمة المهام", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = taskDraft,
                onValueChange = { taskDraft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("أضف مهمة جديدة") }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (taskDraft.isNotBlank()) {
                    onAdd(taskDraft)
                    taskDraft = ""
                }
            }) {
                Text("إضافة")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tasks) { task ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = task.done,
                            onCheckedChange = { onToggle(task.id) }
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = task.title,
                            color = White,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (task.done) TextDecoration.LineThrough else null
                        )
                        IconButton(onClick = { onDelete(task.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Pink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryScreen(
    memory: String,
    onSave: (String) -> Unit
) {
    var localMemory by remember(memory) { mutableStateOf(memory) }

    Column(
        modifier = Modifier.fillMaxSize().background(Dark).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ذاكرة توائمي", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("احفظ المعلومات التي تريد أن يتذكرها المساعد.", color = Muted)

        OutlinedTextField(
            value = localMemory,
            onValueChange = { localMemory = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text("ملاحظات الذاكرة") }
        )

        Button(
            onClick = { onSave(localMemory) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("حفظ الذاكرة")
        }
    }
}

@Composable
fun SettingsScreen(
    companionName: String,
    onNameChanged: (String) -> Unit,
    onClearChat: () -> Unit
) {
    var localName by remember(companionName) { mutableStateOf(companionName) }

    Column(
        modifier = Modifier.fillMaxSize().background(Dark).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("الإعدادات", color = White, fontWeight = FontWeight.Bold, fontSize = 24.sp)

        OutlinedTextField(
            value = localName,
            onValueChange = {
                localName = it
                onNameChanged(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("اسم المساعد") }
        )

        Button(
            onClick = onClearChat,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("مسح المحادثة")
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "الوضع: محلي بالكامل بدون إنترنت\nالذكاء والمهام والذاكرة داخل الجهاز.",
                color = PinkSoft,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

fun loadMessages(prefs: android.content.SharedPreferences): List<ChatMessage> {
    val raw = prefs.getString("messages", null) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(ChatMessage(obj.getString("text"), obj.getBoolean("fromUser")))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
