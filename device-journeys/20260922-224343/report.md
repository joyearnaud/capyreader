# Device journeys — 20260922-224343

App: com.capyreader.app.debug

### 1-launch
- 📸 screenshot: 1-launch-list.png
- ✅ PASS: list rows rendered
- ✅ PASS: a feed or folder title is visible
- ✅ PASS: no crash

**1-launch: 3 passed, 0 failed**

### 2-article-native-reader
- 📸 screenshot: 2-article-native-reader-article.png
- ✅ PASS: article byline rendered
- ✅ PASS: article body rendered
- ✅ PASS: no crash

**2-article-native-reader: 6 passed, 0 failed**

### 3-digest-sheet
- ✅ PASS: scope dialog shown
- tap_text 'Tout le contenu' not found in dump — used fallback (878,1593)
- 📸 screenshot: 3-digest-sheet-sheet-loading-or-hit.png
- ✅ PASS: digest content rendered
- 📸 screenshot: 3-digest-sheet-sheet-content.png
- 📸 screenshot: 3-digest-sheet-before-ref.png
- tap_text ' 5.' not found in dump — used fallback (640,1400)
- 📸 screenshot: 3-digest-sheet-article-from-ref.png
- ✅ PASS: reference opened an article
- 📸 screenshot: 3-digest-sheet-sheet-reopened.png
- ❌ FAIL: sheet reopened after article (text 'Résumé\|Synthèse\|Vue d' not found in dump)
- 📸 screenshot: 3-digest-sheet-FAIL-sheet reopened after article.png
- ✅ PASS: no crash

**3-digest-sheet: 10 passed, 1 failed**

### 4-summaries-history
- ❌ FAIL: drawer row present (text 'Résumés' not found in dump)
- 📸 screenshot: 4-summaries-history-FAIL-drawer row present.png
- tap_text 'Résumés' not found in dump — used fallback (295,825)
- 📸 screenshot: 4-summaries-history-summaries-list.png
- ❌ FAIL: history rows rendered (text 'Résumé de liste\|Résumé d'article' not found in dump)
- 📸 screenshot: 4-summaries-history-FAIL-history rows rendered.png
- 📸 screenshot: 4-summaries-history-history-detail.png
- ❌ FAIL: digest detail rendered (text 'Vue d\|Synthèse\|Résumé' not found in dump)
- 📸 screenshot: 4-summaries-history-FAIL-digest detail rendered.png
- tap_text ' 5.' not found in dump — used fallback (640,1400)
- 📸 screenshot: 4-summaries-history-history-article.png
- ✅ PASS: history reference opened an article
- 📸 screenshot: 4-summaries-history-history-detail-again.png
- ❌ FAIL: back to digest detail (native pop) (text 'Vue d\|Synthèse\|Résumé' not found in dump)
- 📸 screenshot: 4-summaries-history-FAIL-back to digest detail (native pop).png
- ✅ PASS: no crash

**4-summaries-history: 12 passed, 5 failed**

### 5-mark-read-scoped
- unread badge before: 192
- tap_text 'Tout le contenu' not found in dump — used fallback (878,1593)
- 📸 screenshot: 5-mark-read-scoped-before-mark.png
- tap_text 'Tout marquer comme lu' not found in dump — used fallback (904,2653)
- ✅ PASS: confirmation dialog
- tap_text 'Confirmer' not found in dump — used fallback (844,1520)
- unread badge after: 192
- 📸 screenshot: 5-mark-read-scoped-after-mark.png
- ❌ FAIL: badge did not decrease (192 → 192)
- ✅ PASS: no crash

**5-mark-read-scoped: 14 passed, 6 failed**

