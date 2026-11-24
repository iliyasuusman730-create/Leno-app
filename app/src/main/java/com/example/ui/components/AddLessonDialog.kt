package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.LessonEntity
import com.example.ui.components.lesson.TeacherLessonComposer

@Composable
fun AddLessonDialog(
    classId: String = "",
    classTitle: String,
    initialLesson: LessonEntity? = null,
    existingModules: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSubmitLesson: (title: String, content: String, summary: String, onResult: (Boolean, String) -> Unit) -> Unit
) {
    var isPublishing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isPublishing) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        TeacherLessonComposer(
            classId = classId,
            classTitle = classTitle,
            initialLesson = initialLesson,
            existingModules = existingModules,
            isPublishing = isPublishing,
            onDismiss = onDismiss,
            onPublish = { title, content, summary, clearDraft ->
                if (!isPublishing) {
                    isPublishing = true
                    onSubmitLesson(title, content, summary) { success, _ ->
                        isPublishing = false
                        if (success) {
                            clearDraft()
                            onDismiss()
                        }
                    }
                }
            }
        )
    }
}
