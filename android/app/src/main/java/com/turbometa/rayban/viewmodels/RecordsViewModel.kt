package com.turbometa.rayban.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.turbometa.rayban.data.ConversationStorage
import com.turbometa.rayban.models.ConversationRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecordsViewModel(application: Application) : AndroidViewModel(application) {

    private val conversationStorage = ConversationStorage.getInstance(application)

    private val _conversations = MutableStateFlow<List<ConversationRecord>>(emptyList())
    val conversations: StateFlow<List<ConversationRecord>> = _conversations.asStateFlow()

    private val _selectedConversation = MutableStateFlow<ConversationRecord?>(null)
    val selectedConversation: StateFlow<ConversationRecord?> = _selectedConversation.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _showDeleteConfirmDialog = MutableStateFlow(false)
    val showDeleteConfirmDialog: StateFlow<Boolean> = _showDeleteConfirmDialog.asStateFlow()

    private var conversationToDelete: String? = null

    init {
        loadConversations()
    }

    fun loadConversations() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _conversations.value = conversationStorage.getAllConversations()
            } catch (e: Exception) {
                _message.value = "Failed to load conversations: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectConversation(conversation: ConversationRecord) {
        _selectedConversation.value = conversation
    }

    fun clearSelection() {
        _selectedConversation.value = null
    }

    fun showDeleteConfirm(conversationId: String) {
        conversationToDelete = conversationId
        _showDeleteConfirmDialog.value = true
    }

    fun hideDeleteConfirm() {
        conversationToDelete = null
        _showDeleteConfirmDialog.value = false
    }

    fun confirmDelete() {
        val id = conversationToDelete ?: return
        viewModelScope.launch {
            val success = conversationStorage.deleteConversation(id)
            if (success) {
                _conversations.value = _conversations.value.filter { it.id != id }
                if (_selectedConversation.value?.id == id) {
                    _selectedConversation.value = null
                }
                _message.value = "Conversation deleted"
            } else {
                _message.value = "Failed to delete conversation"
            }
            hideDeleteConfirm()
        }
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            val success = conversationStorage.deleteAllConversations()
            if (success) {
                _conversations.value = emptyList()
                _selectedConversation.value = null
                _message.value = "All conversations deleted"
            } else {
                _message.value = "Failed to delete conversations"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun getConversationPreview(record: ConversationRecord): String {
        val lastMessage = record.messages.lastOrNull()
        return lastMessage?.content?.take(100) ?: "No messages"
    }

    fun getFormattedDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun getMessageCount(record: ConversationRecord): Int {
        return record.messages.size
    }
}
