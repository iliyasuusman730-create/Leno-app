# Leno App Audit, Security & Play Compliance Walkthrough

# Security Summary

1. **Real Google Sign-In & Firebase Auth Lockdown**
   - Configured the app with Firebase Authentication, Google Sign-In via Android Credential Manager (`GetSignInWithGoogleOption`), and the provisioned Firestore database (`R.string.firestore_database_id` via `LenoApplication.getFirestoreInstance()`).
   - Removed unverified email-typing dialogs for Google sign-in and account recovery; Google login, Google account linking, and registration now require a real Credential Manager Google ID token verified by Firebase Auth.
   - Removed passwordless owner/admin login shortcuts (`usr_officialjaiby_2026`) and spoofable username-based owner/verified privilege checks across `UserEntity`, `RemoteAccountService`, and `LenoRepository`.
   - Removed unauthenticated local UID fallbacks on cloud account creation and removed `signInWithEmailAndPassword` / `createUserWithEmailAndPassword` usage.

2. **Server-Side Firestore Security Rules & Zero-Trust Access Control**
   - Authored, verified against the Firestore Emulator, and deployed `firestore.rules` covering `users` (including subcollections), `messages`, `support_chats`, `classes` / `lessons` / `enrollments` / `class_questions`, `teacherApplications` / `teacher_applications`, `teacher_roles`, `announcements`, `usernames`, `leno_ids`, and `calls`.
   - Blocked unauthenticated reads/writes, self-promotion to `ADMIN` / `OWNER` / `TEACHER` / verified / official flags, reading other users' private direct messages, and free self-enrollment into paid classes.
   - Restricted `MessageRepository` and `ChatScreen` queries to the signed-in user's ID and suppressed raw exception traces behind safe `FirestoreErrorHandler` logging.

3. **Credential Storage & Android Backup Protection**
   - Stopped storing plaintext passwords in `SessionManager` (`active_auth_pass` is never written and any legacy stored value is immediately removed).
   - Disabled Android Auto Backup (`android:allowBackup="false"`) and excluded SharedPreferences, Room databases, and local media caches in `backup_rules.xml` and `data_extraction_rules.xml`.
   - Enabled release build code shrinking and obfuscation (`isMinifyEnabled = true`) with R8 rules in `app/proguard-rules.pro`.

4. **Google Play Compliance, Legal & Safe Intents**
   - Added in-app **Privacy Policy & Terms of Service** (`LegalInfoDialog`) accessible from both the Auth screen and Settings.
   - Added a self-service **Delete My Account** workflow in Settings that removes user profile/subcollection/handle records in Firestore, deletes the Firebase Auth account (prompting for fresh re-authentication if required), and wipes local Room/session data.
   - Replaced `ActivityResultContracts.GetContent()` image pickers in `ProfileScreen` with the Android Photo Picker (`PickVisualMedia`).
   - Replaced the simulated card/bank checkout dialog (which collected raw card/PIN/CVV/OTP fields and displayed third-party trademarks and personal bank account details) with a transparent **Course Enrollment Inquiry** dialog that routes users directly to **Message Instructor** or **Contact Official Support**.
   - Removed non-functional live VoIP call buttons in `ChatDetailScreen` and `UserProfileScreen`, and hardened file opening in `LessonRichBlocks` (`FileBlockView`) so `file://` URIs cannot trigger `FileUriExposedException`.

---

## PoC Verification

1. **Firestore Security Rules Verification (Emulator)**
   - Command:
     ```bash
     FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
     ```
   - Observed result: **5/5 tests passed** (`Unauthenticated users are denied reads and writes across collections`, `User profile creation prevents self-escalation to ADMIN, OWNER, TEACHER, or verified/official`, `Direct messages are isolated to sender and receiver`, `Paid class self-enrollment is blocked for students while free class enrollment succeeds`, `Admin verified email can approve teacher application while normal user cannot`).
   - Rules deployed to the provisioned Firebase project (`DeployRules` succeeded).

2. **Android Unit Tests Verification**
   - Command:
     ```bash
     gradle :app:testDebugUnitTest
     ```
   - Observed result: **BUILD SUCCESSFUL — 40/40 tests passed** across 8 suites (`CurriculumAndContinueLearningTest` 7/7, `ExampleUnitTest` 1/1, `LessonComposerSerializerTest` 4/4, `MessageRepositoryTest` 3/3, `RealCloudSyncAndDeduplicationTest` 4/4, `SecurityAndRecoveryTest` 7/7, `SessionAndUnreadReliabilityTest` 7/7 including `sessionManager_neverPersistsPlaintextPassword_andPurgesLegacyEntry`, `TeacherClassAccessSecurityTest` 7/7).

3. **Static Secret, Auth & Trademark Hygiene Audit**
   - Command:
     ```bash
     grep -rnE "FirebaseFirestore\.getInstance\(\)|Firebase\.firestore|signInAnonymously|signInWithEmailAndPassword|createUserWithEmailAndPassword|AIzaSy|8063896421|JIBRIL ABDURRAHMAN|••• 730|googleLoginEmailInput|showGoogleLoginDialog|showGoogleConnectDialog" app/src/main --exclude-dir=build
     ```
   - Observed result: **0 matches** (exit code 1 — no parameterless Firestore singletons, forbidden auth methods, hardcoded API keys, personal bank details, or unverified Google email dialogs remain in `app/src/main`).

4. **Android Applet Compilation Verification**
   - Tool: `compile_applet`
   - Observed result: **`Build succeeded - the applet is compiled`**.
