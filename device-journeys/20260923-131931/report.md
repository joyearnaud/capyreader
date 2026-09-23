# Device journeys — 20260923-131931

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
- ❌ FAIL: article byline rendered (text 'de \|à ' not found in dump)
- 📸 screenshot: 2-article-native-reader-FAIL-article byline rendered.png
- ❌ FAIL: article body rendered (text 'Article URL\|http\|the \|de \|Le ' not found in dump)
- 📸 screenshot: 2-article-native-reader-FAIL-article body rendered.png
- ✅ PASS: no crash

**2-article-native-reader: 2 passed, 4 failed**

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
- ❌ FAIL: reference opened an article (text 'Article URL\|http\|de \|Le \|the ' not found in dump)
- 📸 screenshot: 3-digest-sheet-FAIL-reference opened an article.png
- 📸 screenshot: 3-digest-sheet-sheet-reopened.png
- ❌ FAIL: back on the list with the sheet above it (see screenshot) (text 'Comments' not found in dump)
- 📸 screenshot: 3-digest-sheet-FAIL-back on the list with the sheet above it (see screenshot).png
- ✅ PASS: no crash

**3-digest-sheet: 3 passed, 8 failed**

### 4-summaries-history
- ❌ FAIL: drawer row present (text 'Résumés' not found in dump)
- 📸 screenshot: 4-summaries-history-FAIL-drawer row present.png
- tap_text 'Résumés' not found in dump — used fallback (295,825)
- 📸 screenshot: 4-summaries-history-summaries-list.png
- ❌ FAIL: history rows rendered (text 'Résumé de liste\|Résumé d'article' not found in dump)
- 📸 screenshot: 4-summaries-history-FAIL-history rows rendered.png
- 📸 screenshot: 4-summaries-history-history-detail.png
- ❌ FAIL: history detail open (top bar) (text 'Résumés' not found in dump)
- 📸 screenshot: 4-summaries-history-FAIL-history detail open (top bar).png
- tap_text ' 5.' (matched bounds)
- 📸 screenshot: 4-summaries-history-history-article.png
- ❌ FAIL: history reference opened an article (text 'Article URL\|http\|de \|Le \|the ' not found in dump)
- 📸 screenshot: 4-summaries-history-FAIL-history reference opened an article.png
- 📸 screenshot: 4-summaries-history-history-detail-again.png
- ❌ FAIL: back to digest detail (native pop) (text 'Résumés' not found in dump)
- 📸 screenshot: 4-summaries-history-FAIL-back to digest detail (native pop).png
- ✅ PASS: no crash

**4-summaries-history: 4 passed, 13 failed**

### 5-mark-read-scoped
- unread badge before: 192
- tap_text 'Tout le contenu' not found in dump — used fallback (878,1593)
- 📸 screenshot: 5-mark-read-scoped-before-mark.png
- tap_text 'Tout marquer comme lu' not found in dump — used fallback (904,2653)
- ❌ FAIL: confirmation dialog (text 'Marquer tous les articles comme lus' not found in dump)
- 📸 screenshot: 5-mark-read-scoped-FAIL-confirmation dialog.png
- tap_text 'Confirmer' not found in dump — used fallback (844,1520)
- unread badge after: 168
- 📸 screenshot: 5-mark-read-scoped-after-mark.png
- ✅ PASS: unread badge decreased (192 → 168)
- ✅ PASS: no crash

**5-mark-read-scoped: 6 passed, 14 failed**

