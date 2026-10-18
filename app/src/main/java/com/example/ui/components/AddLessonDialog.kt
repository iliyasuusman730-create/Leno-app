package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.lesson.TeacherLessonComposer

@Composable
fun AddLessonDialog(
    classTitle: String,
    onDismiss: () -> Unit,
    onSubmitLesson: (title: String, content: String, summary: String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        TeacherLessonComposer(
            classTitle = classTitle,
            onDismiss = onDismiss,
            onPublish = { title, content, summary ->
                onSubmitLesson(title, content, summary)
                onDismiss()
            }
        )
    }
}
