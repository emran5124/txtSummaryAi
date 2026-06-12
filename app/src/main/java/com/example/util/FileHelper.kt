package com.example.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object FileHelper {

    fun saveSummaryToDownloads(
        context: Context,
        fileNameWithoutExtension: String,
        txtContent: String
    ): Pair<String?, String?> {
        val safeName = fileNameWithoutExtension.replace(Regex("[^a-zA-Z0-9_\\-\\u0600-\\u06FF]"), "_")
        val displayTxtName = "${safeName}_Summary.txt"
        val displayHtmlName = "${safeName}_Summary.html"

        val htmlContent = generateBookerHtml(safeName.replace("_", " "), txtContent)

        var txtSavedPath: String? = null
        var htmlSavedPath: String? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // MediaStore approach
                txtSavedPath = saveViaMediaStore(context, displayTxtName, "text/plain", txtContent)
                htmlSavedPath = saveViaMediaStore(context, displayHtmlName, "text/html", htmlContent)
            } else {
                // Legacy system file write
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val summariesDir = File(downloadsDir, "Summaries")
                if (!summariesDir.exists()) {
                    summariesDir.mkdirs()
                }

                val txtFile = File(summariesDir, displayTxtName)
                FileOutputStream(txtFile).use { fos ->
                    fos.write(txtContent.toByteArray())
                }
                txtSavedPath = Uri.fromFile(txtFile).toString()

                val htmlFile = File(summariesDir, displayHtmlName)
                FileOutputStream(htmlFile).use { fos ->
                    fos.write(htmlContent.toByteArray())
                }
                htmlSavedPath = Uri.fromFile(htmlFile).toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(txtSavedPath, htmlSavedPath)
    }

    private fun saveViaMediaStore(
        context: Context,
        displayName: String,
        mimeType: String,
        content: String
    ): String? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Summaries")
            }
        }

        val tableUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            return null
        }

        try {
            val fileUri = resolver.insert(tableUri, contentValues) ?: return null
            resolver.openOutputStream(fileUri)?.use { os ->
                os.write(content.toByteArray())
            }
            return fileUri.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun generateBookerHtml(title: String, rawSummary: String): String {
        // Escape backticks in rawSummary to avoid javascript string literal injection bugs
        val escapedSummary = rawSummary
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")

        return """
<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>خلاصه: $title</title>
    <style>
        :root {
            --bg-color: #f7f9fc;
            --card-color: #ffffff;
            --text-color: #2d3748;
            --accent-color: #3182ce;
            --border-color: #e2e8f0;
        }
        [data-theme="dark"] {
            --bg-color: #1a202c;
            --card-color: #2d3748;
            --text-color: #edf2f7;
            --accent-color: #63b3ed;
            --border-color: #4a5568;
        }
        body {
            font-family: system-ui, -apple-system, sans-serif;
            background-color: var(--bg-color);
            color: var(--text-color);
            line-height: 1.8;
            margin: 0;
            padding: 0;
            transition: all 0.3s ease;
        }
        .header {
            background-color: var(--card-color);
            border-bottom: 1px solid var(--border-color);
            padding: 1.5rem;
            display: flex;
            justify-content: space-between;
            align-items: center;
            position: sticky;
            top: 0;
            z-index: 100;
        }
        .container {
            max-width: 800px;
            margin: 2rem auto;
            padding: 0 1rem;
        }
        .book-card {
            background-color: var(--card-color);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 2.5rem;
            margin-bottom: 2rem;
            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
        }
        h1, h2, h3 {
            color: var(--accent-color);
            margin-top: 2rem;
        }
        h1 {
            font-size: 2rem;
            margin-bottom: 1rem;
            text-align: center;
        }
        h2 {
            font-size: 1.5rem;
            border-bottom: 2px solid var(--accent-color);
            padding-bottom: 0.5rem;
        }
        h3 {
            font-size: 1.2rem;
        }
        p {
            margin-bottom: 1.5rem;
            text-align: justify;
        }
        ul, ol {
            padding-right: 1.5rem;
            margin-bottom: 1.5rem;
        }
        li {
            margin-bottom: 0.5rem;
        }
        .theme-btn {
            background-color: var(--accent-color);
            color: #ffffff;
            border: none;
            padding: 0.5rem 1rem;
            border-radius: 6px;
            cursor: pointer;
            font-weight: bold;
        }
        .theme-btn:hover {
            opacity: 0.9;
        }
        .highlight-box {
            border-right: 4px solid var(--accent-color);
            background-color: rgba(49, 130, 206, 0.05);
            padding: 1rem;
            margin: 1.5rem 0;
            border-radius: 4px;
        }
    </style>
</head>
<body>
    <header class="header">
        <h2 style="margin: 0; font-size: 1.2rem;">خلاصه کتاب: $title</h2>
        <button class="theme-btn" onclick="toggleTheme()">تغییر پوسته</button>
    </header>
    <div class="container">
        <article class="book-card" id="book-content">
            <!-- Content built by booker2.js parser -->
        </article>
    </div>

    <script id="booker2-js">
        const rawContent = `$escapedSummary`;

        function parseBookerText(text) {
            const lines = text.split('\n');
            let html = '';
            let inList = false;

            for (let line of lines) {
                let trimmed = line.trim();
                if (!trimmed) continue;

                if (trimmed.startsWith('###')) {
                    if (inList) { html += '</ul>'; inList = false; }
                    html += `<h3>` + trimmed.replace(/^###\s*/, '') + `</h3>`;
                } else if (trimmed.startsWith('##')) {
                    if (inList) { html += '</ul>'; inList = false; }
                    html += `<h2>` + trimmed.replace(/^##\s*/, '') + `</h2>`;
                } else if (trimmed.startsWith('#')) {
                    if (inList) { html += '</ul>'; inList = false; }
                    html += `<h1>` + trimmed.replace(/^#\s*/, '') + `</h1>`;
                } else if (trimmed.startsWith('>')) {
                    if (inList) { html += '</ul>'; inList = false; }
                    html += `<div class="highlight-box">` + trimmed.replace(/^>\s*/, '') + `</div>`;
                } else if (trimmed.startsWith('*') || trimmed.startsWith('-')) {
                    if (!inList) { html += '<ul>'; inList = true; }
                    let itemText = trimmed.replace(/^[\*\-]\s*/, '');
                    itemText = itemText.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
                    html += `<li>` + itemText + `</li>`;
                } else {
                    if (inList) { html += '</ul>'; inList = false; }
                    let pText = trimmed.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
                    html += `<p>` + pText + `</p>`;
                }
            }
            if (inList) { html += '</ul>'; }
            return html;
        }

        document.getElementById('book-content').innerHTML = parseBookerText(rawContent);

        function toggleTheme() {
            const body = document.body;
            if (body.hasAttribute('data-theme')) {
                body.removeAttribute('data-theme');
            } else {
                body.setAttribute('data-theme', 'dark');
            }
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}
