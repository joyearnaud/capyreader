# Device journeys — 20260922-232613

App: com.capyreader.app.debug

### 1-launch
- tap_text 'Non lus' (matched bounds)
- 📸 screenshot: 1-launch-list.png
- ❌ FAIL: list rows rendered (text 'Comments' not found in dump)
- 📸 screenshot: 1-launch-FAIL-list rows rendered.png
- ❌ FAIL: a feed or folder title is visible (text 'Development\|News\|Tech' not found in dump)
- 📸 screenshot: 1-launch-FAIL-a feed or folder title is visible.png
- ✅ PASS: no crash

**1-launch: 1 passed, 2 failed**

### 2-article-native-reader
- 📸 screenshot: 2-article-native-reader-article.png
- ✅ PASS: article byline rendered
- ✅ PASS: article body rendered
- ✅ PASS: no crash

**2-article-native-reader: 4 passed, 2 failed**

### 3-digest-sheet
- ❌ FAIL: scope dialog shown (text 'Résumer cette liste' not found in dump)
- 📸 screenshot: 3-digest-sheet-FAIL-scope dialog shown.png
- tap_text 'Tout le contenu' not found in dump — used fallback (878,1593)
- 📸 screenshot: 3-digest-sheet-sheet-loading-or-hit.png
- ❌ FAIL: digest content never rendered
- 📸 screenshot: 3-digest-sheet-sheet-content.png
- 📸 screenshot: 3-digest-sheet-before-ref.png
- tap_text ' 5.' not found in dump — used fallback (640,1400)
- 📸 screenshot: 3-digest-sheet-article-from-ref.png
- ✅ PASS: reference opened an article
- 📸 screenshot: 3-digest-sheet-sheet-reopened.png
- ❌ FAIL: back on the list with the sheet above it (see screenshot) (text 'Comments' not found in dump)
- 📸 screenshot: 3-digest-sheet-FAIL-back on the list with the sheet above it (see screenshot).png
- ✅ PASS: no crash

**3-digest-sheet: 6 passed, 5 failed**

### 4-summaries-history
- ✅ PASS: drawer row present
- tap_text 'Résumés' (matched bounds)
- 📸 screenshot: 4-summaries-history-summaries-list.png
- ✅ PASS: history rows rendered
- 📸 screenshot: 4-summaries-history-history-detail.png
- ✅ PASS: history detail open (top bar)
- tap_text ' 5.' (matched bounds)
- 📸 screenshot: 4-summaries-history-history-article.png
- ✅ PASS: history reference opened an article
- 📸 screenshot: 4-summaries-history-history-detail-again.png
- ✅ PASS: back to digest detail (native pop)
- ✅ PASS: no crash

**4-summaries-history: 12 passed, 5 failed**

### 5-mark-read-scoped
- unread badge before: 168
- ❌ ABORT: app not in foreground — remaining steps cancelled

**ABORTED** — app left the foreground mid-journey.
