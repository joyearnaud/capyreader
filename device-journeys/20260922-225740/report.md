# Device journeys — 20260922-225740

App: com.capyreader.app.debug

### 1-launch
- tap_text 'Non lus' not found in dump — used fallback (295,490)
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
