# Conversation History Persistence Audit

## Summary
The conversation persistence system stores messages and tool executions in JSON format within a single `messagesJson` field in the `ConversationEntity`. **Tool executions ARE saved**, but there are critical gaps in persistence coverage.

---

## 1. ConversationEntity Fields (Complete)

**File:** `app/src/main/java/com/amaya/intelligence/data/local/db/entity/ConversationEntity.kt`

```kotlin
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,                           // ✓ Conversation title (first 5 words of first user message)
    val workspacePath: String?,                  // ✓ Project path
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messagesJson: String                     // ✓ JSON array of messages with tool executions
)
```

**Assessment:** Only 5 fields. Missing: token counts, error states, active agent info.

---

## 2. ConversationDao Queries

**File:** `app/src/main/java/com/amaya/intelligence/data/local/db/dao/ConversationDao.kt`

```kotlin
@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentConversations(limit: Int): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): ConversationEntity?
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversation(conversation: ConversationEntity): Long
    
    @Update
    suspend fun updateConversation(conversation: ConversationEntity)
    
    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)
    
    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: Long)
    
    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()
}
```

**Assessment:** Basic CRUD operations. No query pagination, no search by title, no filtering.

---

## 3. ChatViewModel - Message Serialization

### 3.1 Save Path: `serializeMessagesToJson()` (Lines 472-496)

```kotlin
private fun serializeMessagesToJson(messages: List<UiMessage>): String {
    val jsonArray = org.json.JSONArray()
    messages.forEach { msg ->
        val obj = org.json.JSONObject()
        obj.put("role", msg.role.name)
        obj.put("content", msg.content)
        if (msg.toolExecutions.isNotEmpty()) {
            val execArr = org.json.JSONArray()
            msg.toolExecutions.forEach { exec ->
                val e = org.json.JSONObject()
                e.put("toolCallId", exec.toolCallId)
                e.put("name", exec.name)
                e.put("status", exec.status.name)
                exec.result?.let { e.put("result", it) }
                val argsObj = org.json.JSONObject()
                exec.arguments.forEach { (k, v) -> argsObj.put(k, v ?: org.json.JSONObject.NULL) }
                e.put("arguments", argsObj)
                execArr.put(e)
            }
            obj.put("toolExecutions", execArr)
        }
        jsonArray.put(obj)
    }
    return jsonArray.toString()
}
```

**✓ SAVES:**
- Message role (USER/ASSISTANT/SYSTEM)
- Message content (text)
- Tool executions:
  - `toolCallId` (unique identifier)
  - `name` (tool name)
  - `status` (PENDING/RUNNING/SUCCESS/ERROR)
  - `result` (tool output)
  - `arguments` (tool input arguments)

**✗ MISSING:**
- `msg.timestamp` — not serialized (line 545 defines it, not saved)
- `msg.id` (UUID) — not serialized (line 542 defines it, not saved)
- **Subagent execution children** — `exec.children` (line 557) NOT serialized
  - This means parallel subagent results are lost on reload
- Error state of ToolExecution (no `isError` field persisted, only `status`)

---

### 3.2 Load Path: `parseMessagesFromJson()` (Lines 390-434)

```kotlin
private fun parseMessagesFromJson(json: String): List<UiMessage> {
    if (json.isBlank()) return emptyList()
    return try {
        val messages = mutableListOf<UiMessage>()
        val jsonArray = org.json.JSONArray(json)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val roleStr = obj.getString("role")
            val content = obj.getString("content")
            val role = when (roleStr) {
                "USER"      -> MessageRole.USER
                "ASSISTANT" -> MessageRole.ASSISTANT
                "SYSTEM"    -> MessageRole.SYSTEM
                else        -> MessageRole.USER
            }
            // Restore tool executions
            val toolExecutions = mutableListOf<ToolExecution>()
            if (obj.has("toolExecutions")) {
                val execArr = obj.getJSONArray("toolExecutions")
                for (j in 0 until execArr.length()) {
                    val e = execArr.getJSONObject(j)
                    val argsMap = mutableMapOf<String, Any?>()
                    if (e.has("arguments")) {
                        val argsObj = e.getJSONObject("arguments")
                        argsObj.keys().forEach { k -> argsMap[k] = argsObj.get(k) }
                    }
                    toolExecutions.add(
                        ToolExecution(
                            toolCallId = e.getString("toolCallId"),
                            name       = e.getString("name"),
                            arguments  = argsMap,
                            result     = e.optString("result", null as String?),
                            status     = try { ToolStatus.valueOf(e.getString("status")) }
                                         catch (_: Exception) { ToolStatus.SUCCESS }
                        )
                    )
                }
            }
            messages.add(UiMessage(role = role, content = content, toolExecutions = toolExecutions))
        }
        messages
    } catch (e: Exception) {
        emptyList()
    }
}
```

**✓ RESTORES:**
- Message role
- Message content
- Tool executions (all fields that were saved)

**✗ MISSING:**
- `msg.timestamp` — always uses default `System.currentTimeMillis()` (new time)
- `msg.id` — always generates new UUID on restore (line 542)
- **Subagent children** — not restored (because not saved)

---

### 3.3 Save Trigger: `saveCurrentConversation()` (Lines 436-470)

```kotlin
private fun saveCurrentConversation() {
    val messages = _uiState.value.messages
    if (messages.isEmpty()) return
    
    viewModelScope.launch {
        try {
            val firstUserMsg = messages.firstOrNull { it.role == MessageRole.USER }?.content ?: "New Conversation"
            val title = firstUserMsg.split("\\s+".toRegex()).take(5).joinToString(" ")
            
            val now = System.currentTimeMillis()
            val messagesJson = serializeMessagesToJson(messages)
            
            if (currentConversationId != null) {
                val existing = conversationDao.getConversationById(currentConversationId!!)
                if (existing != null) {
                    conversationDao.updateConversation(
                        existing.copy(title = title.take(50), messagesJson = messagesJson, updatedAt = now)
                    )
                }
            } else {
                val conversation = ConversationEntity(
                    id = 0,
                    title = title.take(50),
                    workspacePath = _uiState.value.workspacePath,
                    messagesJson = messagesJson,
                    createdAt = now,
                    updatedAt = now
                )
                currentConversationId = conversationDao.insertConversation(conversation)
            }
        } catch (e: Exception) {
            // Silent fail
        }
    }
}
```

**Triggers (Lines 323, 344, 354):**
- `is AgentEvent.Done` → Line 323: `saveCurrentConversation()`
- `stopGeneration()` → Line 344: `saveCurrentConversation()`
- `clearConversation()` → Line 354: `saveCurrentConversation()`

**⚠️ GAPS:**
- Saves only when conversation ends or is explicitly cleared
- **NOT saved during user input** — if app crashes mid-conversation before AI finishes, unsaved messages are lost
- `totalInputTokens` / `totalOutputTokens` (lines 533-534) are **NEVER saved**

---

### 3.4 Load Trigger: `loadConversation()` (Lines 368-388)

```kotlin
fun loadConversation(conversationId: Long) {
    viewModelScope.launch {
        try {
            val conversation = conversationDao.getConversationById(conversationId)
            if (conversation != null) {
                currentConversationId = conversationId
                val messages = parseMessagesFromJson(conversation.messagesJson)
                _uiState.update {
                    it.copy(
                        workspacePath = conversation.workspacePath,
                        messages = messages,
                        totalInputTokens = 0,      // ALWAYS RESET
                        totalOutputTokens = 0      // ALWAYS RESET
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to load conversation: ${e.message}") }
        }
    }
}
```

**⚠️ CRITICAL ISSUE (Lines 379-380):**
- Token counts are **hardcoded to 0** on load
- No way to recover historical token usage

---

## 4. Missing from Persistence

### **CRITICAL - Never Saved:**
1. **Token counts** (`totalInputTokens`, `totalOutputTokens`)
   - Status: Lost on reload
   - Location: `ChatUiState` lines 533-534
   - Impact: User cannot see historical token usage

2. **Subagent execution children**
   - Status: Lost on reload
   - Location: `ToolExecution.children` line 557
   - Impact: Parallel subagent results disappear after reload

3. **Todo items** (AI-generated task list)
   - Status: Cleared on new conversation (line 356)
   - Location: `todoRepository.clear()` line 356
   - Impact: Todo progress bar is not persisted

4. **Message timestamps and UUIDs**
   - Status: Regenerated on load
   - Location: `UiMessage` lines 542, 545
   - Impact: Cannot reconstruct original message ordering

5. **Error state**
   - Status: Not persisted
   - Location: `ChatUiState.error` line 529
   - Impact: User loses error context on reload

6. **Active agent info**
   - Status: Not persisted
   - Location: `ChatUiState.activeAgentId` line 537
   - Impact: Don't know which agent was used for this conversation

7. **Conversation metadata**
   - Status: Only title/workspacePath saved
   - Missing: createdAt, updatedAt are not exposed to UI state
   - Impact: Cannot show "Last edited: X minutes ago"

---

## 5. Data Loss Scenarios

### **Scenario 1: App Crash During Conversation**
1. User sends message → AI starts responding
2. App crashes before AI finishes
3. Result: **Message is lost** (save only triggers on `AgentEvent.Done`)

### **Scenario 2: Reload After Subagent Call**
1. User requests parallel subagent execution
2. AI calls `invoke_subagents` with multiple tasks
3. App is force-closed before AI finishes
4. User reopens and loads conversation
5. Result: **Subagent results are lost** (`children` not serialized)

### **Scenario 3: Token Usage Tracking**
1. User asks AI for code review (100 requests over 10 conversations)
2. Each conversation shows input/output tokens
3. User reloads conversation history
4. Result: **All token counts reset to 0** (hardcoded on load)

### **Scenario 4: Todo Progress Lost**
1. User gives AI a multi-step task
2. AI updates todo items with `update_todo` tool
3. User navigates away and back
4. Result: **Todo items cleared** (cleared on `clearConversation()`)

---

## 6. Code Quality Issues

### Issue 1: Silent Failure in `saveCurrentConversation()`
**Lines 466-468:**
```kotlin
} catch (e: Exception) {
    // Silent fail
}
```
**Problem:** No logging, no user notification. App could be failing to save systematically.

### Issue 2: No Transactional Integrity
**Lines 448-454:**
```kotlin
if (currentConversationId != null) {
    val existing = conversationDao.getConversationById(currentConversationId!!)
    if (existing != null) {
        conversationDao.updateConversation(...)
    }
}
```
**Problem:** Race condition between `getConversationById()` and `updateConversation()`. If another coroutine deletes the row in between, the update silently fails.

### Issue 3: JSON Parsing Swallows Errors
**Lines 431-433:**
```kotlin
} catch (e: Exception) {
    emptyList()
}
```
**Problem:** Corrupted JSON silently returns empty list. User loses conversation without knowing why.

---

## 7. Recommendations

### **High Priority:**
1. **Add token counts to ConversationEntity**
   ```kotlin
   val totalInputTokens: Int = 0
   val totalOutputTokens: Int = 0
   ```

2. **Serialize subagent children in `serializeMessagesToJson()`**
   ```kotlin
   if (exec.children.isNotEmpty()) {
       val childArr = org.json.JSONArray()
       exec.children.forEach { child ->
           val c = org.json.JSONObject()
           c.put("index", child.index)
           c.put("taskName", child.taskName)
           c.put("prompt", child.prompt)
           c.put("result", child.result)
           c.put("status", child.status.name)
           childArr.put(c)
       }
       e.put("children", childArr)
   }
   ```

3. **Restore subagent children in `parseMessagesFromJson()`**
   ```kotlin
   val children = mutableListOf<SubagentExecution>()
   if (e.has("children")) {
       val childArr = e.getJSONArray("children")
       for (k in 0 until childArr.length()) {
           val c = childArr.getJSONObject(k)
           children.add(SubagentExecution(
               index = c.getInt("index"),
               taskName = c.getString("taskName"),
               prompt = c.getString("prompt"),
               result = c.optString("result"),
               status = try { ToolStatus.valueOf(c.getString("status")) } 
                        catch (_: Exception) { ToolStatus.SUCCESS }
           ))
       }
   }
   exec = exec.copy(children = children)
   ```

4. **Persist token counts on load**
   ```kotlin
   // In loadConversation() — extract from entity if available
   totalInputTokens = conversation.totalInputTokens,
   totalOutputTokens = conversation.totalOutputTokens
   ```

5. **Persist todo items to database**
   - Add `TodoEntity` table or store as `todoItemsJson` in `ConversationEntity`
   - Load/save via `TodoRepository`

6. **Add error handling and logging**
   ```kotlin
   } catch (e: Exception) {
       LogUtils.error("Failed to save conversation", e)
       _uiState.update { it.copy(error = "Failed to save conversation") }
   }
   ```

### **Medium Priority:**
7. Persist message timestamps and UUIDs
8. Persist active agent ID per conversation
9. Add conversation search query to DAO
10. Add transactional safety to save operation

---

## Summary Table

| Item | Saved? | Restored? | Lost On Reload? |
|------|--------|-----------|-----------------|
| Message role | ✓ | ✓ | ✗ |
| Message content | ✓ | ✓ | ✗ |
| Tool executions | ✓ | ✓ | ✗ |
| Subagent children | ✗ | ✗ | **✓ LOST** |
| Token counts | ✗ | ✗ | **✓ LOST** |
| Todo items | ✗ | ✗ | **✓ LOST** |
| Message timestamp | ✗ | ✗ | **✓ LOST** |
| Message UUID | ✗ | ✗ | **✓ LOST** |
| Error state | ✗ | ✗ | **✓ LOST** |
| Active agent ID | ✗ | ✗ | **✓ LOST** |
| Workspace path | ✓ | ✓ | ✗ |

**Tool Executions:** ✓ Correctly persisted (all fields)
**Critical Gaps:** 4 major data loss scenarios
