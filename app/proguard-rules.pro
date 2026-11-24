-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class com.example.data.entity.** { *; }
-keep class com.example.data.model.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.ServerTimestamp <fields>;
}
