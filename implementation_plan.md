Verification Checklist
Workstream A — Installer Verification Screen
 Cold start → Splash (Lottie + logo only) → Verification Screen (shield + steps) → Home/Login
 Sideloaded APK (via ADB) → Verification Screen shows "Checking installation source… ❌" → transitions to IntegrityBlockedScreen
 Play Store install → all 3 steps show ✅ → navigates to Home/Login
 IntegrityBlockedScreen "Retry Verification" button → goes back to verification screen
 Non-Android platform → verification screen skips all checks, goes straight to Home/Login
 Dark mode: verification screen respects AppTheme dark colors
Workstream B — AI Overlay Grammar Mode
 Enable accessibility service → open any text field → type text with grammar errors → tap bubble
 Casual persona: returns 1 corrected version with minimal changes (not 3 rewrites)
 Casual persona: preserves original tone/voice — only grammar/spelling/punctuation fixed
 Casual persona: if no errors, returns text unchanged with "No errors found"
 Academic persona: still generates 3 scholarly rewrites (unchanged behavior)
 Poetry persona: still generates lyrical version (unchanged behavior)
 Custom persona: still uses custom prompt (unchanged behavior)
Workstream C — Document Processing Hub
 Camera scan: Open any tool → tap 📷 → scan document → OCR extracts text → populates input
 File picker: Tap 📄 → pick PDF → text extracted → info chip shows "filename.pdf · X pages · Y words"
 File picker DOCX: Pick .docx file → text extracted correctly
 URL import: Tap 🔗 → paste article URL → text extracted (HTML stripped) → populates input
 URL import PDF: Paste URL to a .pdf file → downloaded + text extracted
 Document viewer: Tap info chip → dual-mode viewer opens → "Document" tab shows rendered PDF, "Text" tab shows extracted text
 Send to tool: In viewer → "Send to Tool" → pick Grammar → Grammar page opens with text pre-filled
 Export PDF: Process text → tap export → choose PDF → branded PDF generated and shared
 Export DOCX: Choose DOCX → branded DOCX generated
 Export TXT: Choose TXT → plain text file shared
 Large document: Import 50-page PDF → chunking activates → progress shown → full result
 Home screen: "Scan Document" and "Import File" cards visible in tools row
 Tool popup: Scan/Import options in grid
 Dark mode: All new widgets respect AppTheme dark/light
 Platform guard: Camera scan hidden on desktop/web
Implementation Order (Suggested)
B first (smallest scope — single file edit in Kotlin, ~30 min)
A second (new screen + splash modification, ~2-3 hours)
C in phases:
C1–C3: Models + Services (foundation, ~3-4 hours)
C4–C6: Widgets + Viewer (UI components, ~4-5 hours)
C8: pubspec.yaml dependencies
C9: unified_ai_service chunking
C10–C15: Wire into all 6 tool screens (~1 hour each)
C16–C17: Home screen + tool popup updates (~1 hour)
C7: DOCX template asset
Testing + polish (~2-3 hours)
Total estimated effort: ~20-25 hours

