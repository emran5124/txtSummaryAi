package com.example.util

object HtmlExporter {

    fun generateReaderHtml(bookTitle: String, rawTextContent: String): String {
        val escapedText = rawTextContent
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")

        return """
<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>خلاصه ساز هوشمند کتاب | $bookTitle</title>
    <style>
        :root {
            --bg-main: #f8fafc;
            --bg-card: #ffffff;
            --text-main: #1e293b;
            --text-muted: #64748b;
            --primary: #2563eb;
            --border: #e2e8f0;
            --sidebar-width: 320px;
            
            /* Custom Colors */
            --color-green: #15803d;
            --color-red: #b91c1c;
            --color-yellow: #a16207;
            --color-blue: #1d4ed8;
            
            /* Highlight Backgrounds */
            --hl-green: #dcfce7;
            --hl-red: #fee2e2;
            --hl-yellow: #fef9c3;
            --hl-blue: #dbeafe;
        }

        [data-theme="dark"] {
            --bg-main: #0f172a;
            --bg-card: #1e293b;
            --text-main: #f1f5f9;
            --text-muted: #94a3b8;
            --primary: #3b82f6;
            --border: #334155;
            
            --color-green: #4ade80;
            --color-red: #f87171;
            --color-yellow: #facc15;
            --color-blue: #60a5fa;
            
            --hl-green: #064e3b;
            --hl-red: #7f1d1d;
            --hl-yellow: #713f12;
            --hl-blue: #1e3a8a;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: system-ui, -apple-system, sans-serif;
        }

        body {
            background-color: var(--bg-main);
            color: var(--text-main);
            display: flex;
            height: 100vh;
            overflow: hidden;
            direction: rtl;
        }

        /* Sidebar Navigation & Search */
        .sidebar {
            width: var(--sidebar-width);
            background-color: var(--bg-card);
            border-left: 1px solid var(--border);
            display: flex;
            flex-direction: column;
            transition: transform 0.3s ease;
            z-index: 100;
        }

        .sidebar.collapsed {
            transform: translateX(100%);
            position: absolute;
        }

        .sidebar-header {
            padding: 1.25rem;
            border-bottom: 1px solid var(--border);
        }

        .sidebar-title {
            font-size: 1.15rem;
            font-weight: bold;
            color: var(--primary);
            margin-bottom: 0.75rem;
        }

        .search-box {
            position: relative;
        }

        .search-input {
            width: 100%;
            padding: 0.6rem 0.75rem 0.6rem 2rem;
            border: 1px solid var(--border);
            border-radius: 8px;
            background-color: var(--bg-main);
            color: var(--text-main);
            font-size: 0.9rem;
            outline: none;
        }

        .search-input:focus {
            border-color: var(--primary);
        }

        /* Navigation Links List */
        .nav-sections {
            flex: 1;
            overflow-y: auto;
            padding: 1rem;
        }

        .nav-item {
            display: block;
            padding: 0.6rem 0.8rem;
            border-radius: 6px;
            color: var(--text-main);
            text-decoration: none;
            font-size: 0.95rem;
            margin-bottom: 0.25rem;
            cursor: pointer;
            transition: background 0.2s;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .nav-item:hover, .nav-item.active {
            background-color: var(--bg-main);
            color: var(--primary);
            font-weight: bold;
        }

        /* Search Results Container */
        .search-results {
            max-height: 250px;
            overflow-y: auto;
            border-top: 1px solid var(--border);
            background: var(--bg-card);
            padding: 0.5rem;
        }

        .search-result-snippet {
            font-size: 0.85rem;
            padding: 0.5rem;
            border-radius: 6px;
            cursor: pointer;
            margin-bottom: 0.25rem;
            border-bottom: 1px solid var(--border);
        }

        .search-result-snippet:hover {
            background: var(--bg-main);
        }

        .search-highlight {
            background-color: #fde047;
            color: #000000;
            font-weight: bold;
            padding: 0 2px;
            border-radius: 2px;
        }

        /* Main Reader Panels */
        .main-content {
            flex: 1;
            display: flex;
            flex-direction: column;
            overflow: hidden;
            position: relative;
        }

        /* Top Bar */
        .top-bar {
            height: 60px;
            background-color: var(--bg-card);
            border-bottom: 1px solid var(--border);
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0 1.5rem;
        }

        .top-left-actions {
            display: flex;
            align-items: center;
            gap: 1rem;
        }

        .interactive-btn {
            background: none;
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 0.4rem 0.75rem;
            color: var(--text-main);
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 0.4rem;
            font-size: 0.9rem;
            transition: background 0.2s;
        }

        .interactive-btn:hover {
            background-color: var(--bg-main);
        }

        /* Reading Area & Chapters virtualization container */
        .reader-viewport {
            flex: 1;
            overflow-y: auto;
            padding: 2rem 1.5rem;
            scroll-behavior: smooth;
        }

        .reader-container {
            max-width: 750px;
            margin: 0 auto;
            padding-bottom: 6rem;
        }

        /* Virtualized chapters/paragraphs */
        .chapter-node {
            background-color: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 2.5rem;
            margin-bottom: 2rem;
            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
            min-height: 100px;
        }

        .chapter-placeholder {
            display: flex;
            justify-content: center;
            align-items: center;
            color: var(--text-muted);
            font-style: italic;
            height: 154px;
            border: 1px dashed var(--border);
            border-radius: 12px;
            margin-bottom: 2rem;
        }

        /* Typography spacing rules */
        h1, h2, h3, h4 {
            color: var(--primary);
            line-height: 1.4;
            margin-bottom: 1rem;
        }
        h1 { font-size: 1.85rem; border-bottom: 3px solid var(--primary); padding-bottom: 0.5rem; }
        h2 { font-size: 1.5rem; border-bottom: 2px solid var(--border); padding-bottom: 0.4rem; margin-top: 1.5rem; }
        h3 { font-size: 1.25rem; margin-top: 1rem; }

        p {
            font-size: 1.1rem;
            line-height: 1.9;
            margin-bottom: 1.5rem;
            text-align: justify;
            word-break: break-word;
        }

        blockquote {
            border-right: 4px solid var(--primary);
            padding: 0.75rem 1.25rem;
            margin: 1.5rem 0;
            background-color: rgba(37, 99, 235, 0.04);
            font-style: italic;
            border-radius: 4px;
        }

        ul, ol {
            padding-right: 1.5rem;
            margin-bottom: 1.5rem;
        }

        li {
            font-size: 1.1rem;
            line-height: 1.8;
            margin-bottom: 0.5rem;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            margin: 1.5rem 0;
        }

        th, td {
            border: 1px solid var(--border);
            padding: 0.75rem;
            text-align: right;
        }

        th {
            background-color: var(--bg-main);
        }

        code {
            font-family: monospace;
            background: rgba(148, 163, 184, 0.2);
            padding: 0.2rem 0.4rem;
            border-radius: 4px;
            font-size: 0.9em;
        }

        pre {
            background: var(--bg-main);
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 1rem;
            overflow-x: auto;
            margin-bottom: 1.5rem;
        }
        
        pre code {
            background: none;
            padding: 0;
        }

        /* Color custom tags styles */
        .color-green { color: var(--color-green); font-weight: 500; }
        .color-red { color: var(--color-red); font-weight: 500; }
        .color-yellow { color: var(--color-yellow); font-weight: 500; }
        .color-blue { color: var(--color-blue); font-weight: 500; }

        .hl-green { background-color: var(--hl-green); padding: 2px 4px; border-radius: 4px; }
        .hl-red { background-color: var(--hl-red); padding: 2px 4px; border-radius: 4px; }
        .hl-yellow { background-color: var(--hl-yellow); padding: 2px 4px; border-radius: 4px; }
        .hl-blue { background-color: var(--hl-blue); padding: 2px 4px; border-radius: 4px; }

        /* Media Queries */
        @media (max-width: 768px) {
            .sidebar {
                position: absolute;
                right: 0;
                top: 60px;
                height: calc(100vh - 60px);
                transform: translateX(0);
            }
            .sidebar.collapsed {
                transform: translateX(100%);
            }
        }
    </style>
</head>
<body>

    <!-- SIDEBAR -->
    <aside class="sidebar" id="sidebar">
        <div class="sidebar-header">
            <h3 class="sidebar-title">فهرست کل کتاب</h3>
            <div class="search-box">
                <input type="text" class="search-input" id="search-input" placeholder="جستجو کل فصول...">
            </div>
        </div>
        <div class="search-results" id="search-results-panel" style="display: none;"></div>
        <nav class="nav-sections" id="nav-sections-list"></nav>
    </aside>

    <!-- CONTENT VIEWPORT -->
    <main class="main-content">
        <header class="top-bar">
            <div class="top-left-actions">
                <button class="interactive-btn" id="toggle-sidebar-btn">
                     فهرست مطالب
                </button>
                <span id="book-title-display" style="font-weight: 600;">$bookTitle</span>
            </div>
            <div>
                <button class="interactive-btn" id="toggle-theme-btn">
                    تغییر تم 🌓
                </button>
            </div>
        </header>

        <!-- Dynamic Reading Viewport -->
        <div class="reader-viewport" id="reader-viewport">
            <div class="reader-container" id="reader-container">
                <!-- Virtual rendering engine places nodes here -->
            </div>
        </div>
    </main>

    <script>
        // Raw content loaded dynamically from code
        const rawContent = `$escapedText`;

        // Configurations & Session state
        let currentTheme = localStorage.getItem("reader-theme") || "light";
        let isSidebarOpen = localStorage.getItem("reader-sidebar-open") !== "false";
        let lastScrollY = parseFloat(localStorage.getItem("reader-last-position")) || 0;
        let lastChapterIndex = parseInt(localStorage.getItem("reader-last-chapter")) || 0;

        // Custom Lexical Syntax Node Parser for Colors/Highlights containing nested rules
        function parseCustomStyles(text) {
            // Apply text colors with priority ordering
            // [text] => green
            // ¥¥text¥¥ => red
            // «text» => yellow
            // <text> => blue
            
            // Highlight shapes:
            // [[text]] => green hl
            // ((text)) => red hl
            // ««text»» => yellow hl
            // <<text>> => blue hl

            let processed = text;

            // Handle standard bold/italic markdown details nicely
            processed = processed.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
            processed = processed.replace(/\*(.*?)\*/g, '<em>$1</em>');

            // Recursive parser loop to manage nested styling syntax correctly
            function recursiveReplace(inputStr) {
                let current = inputStr;
                
                // Nested highlights
                const hlGreenReg = /\[\[((?:[^[\]]|\n)*?)\]\]/g;
                const hlRedReg = /\(\(((?:[^()]|\n)*?)\)\)/g;
                const hlYellowReg = /««((?:[^«»]|\n)*?)»»/g;
                const hlBlueReg = /<<((?:[^<>]|\n)*?)>>/g;

                // Nested text colors
                const cGreenReg = /\[((?:[^[\]]|\n)*?)\]/g;
                const cRedReg = /¥¥((?:[^¥]|\n)*?)¥¥/g;
                const cYellowReg = /«((?:[^«»]|\n)*?)»/g;
                const cBlueReg = /<((?:[^<>]|\n)*?)>/g;

                let replaced = false;

                if (hlGreenReg.test(current)) { current = current.replace(hlGreenReg, '<span class="hl-green">$1</span>'); replaced = true; }
                if (hlRedReg.test(current)) { current = current.replace(hlRedReg, '<span class="hl-red">$1</span>'); replaced = true; }
                if (hlYellowReg.test(current)) { current = current.replace(hlYellowReg, '<span class="hl-yellow">$1</span>'); replaced = true; }
                if (hlBlueReg.test(current)) { current = current.replace(hlBlueReg, '<span class="hl-blue">$1</span>'); replaced = true; }

                if (cGreenReg.test(current)) { current = current.replace(cGreenReg, '<span class="color-green">$1</span>'); replaced = true; }
                if (cRedReg.test(current)) { current = current.replace(cRedReg, '<span class="color-red">$1</span>'); replaced = true; }
                if (cYellowReg.test(current)) { current = current.replace(cYellowReg, '<span class="color-yellow">$1</span>'); replaced = true; }
                if (cBlueReg.test(current)) { current = current.replace(cBlueReg, '<span class="color-blue">$1</span>'); replaced = true; }

                if (replaced) {
                    return recursiveReplace(current);
                }
                return current;
            }

            return recursiveReplace(processed);
        }

        // Structural line parsers for markdown & layout blocks
        function parseMarkdownToHtml(rawText) {
            const lines = rawText.split('\n');
            let htmlResult = '';
            let inList = false;
            let inTable = false;

            for (let line of lines) {
                let trimmed = line.trim();
                if (!trimmed) {
                    if (inList) { htmlResult += '</ul>'; inList = false; }
                    if (inTable) { htmlResult += '</table>'; inTable = false; }
                    continue;
                }

                // Table parsing
                if (trimmed.startsWith('|')) {
                    if (!inTable) { htmlResult += '<table>'; inTable = true; }
                    let cells = trimmed.split('|').map(c => c.trim()).filter((c, i, a) => i > 0 && i < a.length - 1);
                    htmlResult += '<tr>' + cells.map(c => `<td>` + parseCustomStyles(c) + `</td>`).join('') + '</tr>';
                    continue;
                } else if (inTable) {
                    htmlResult += '</table>'; inTable = false;
                }

                // Header matches
                if (trimmed.startsWith('####')) {
                    htmlResult += `<h4>` + parseCustomStyles(trimmed.substring(4).trim()) + `</h4>`;
                } else if (trimmed.startsWith('###')) {
                    htmlResult += `<h3>` + parseCustomStyles(trimmed.substring(3).trim()) + `</h3>`;
                } else if (trimmed.startsWith('##')) {
                    htmlResult += `<h2>` + parseCustomStyles(trimmed.substring(2).trim()) + `</h2>`;
                } else if (trimmed.startsWith('#')) {
                    htmlResult += `<h1>` + parseCustomStyles(trimmed.substring(1).trim()) + `</h1>`;
                } else if (trimmed.startsWith('>')) {
                    htmlResult += `<blockquote>` + parseCustomStyles(trimmed.substring(1).trim()) + `</blockquote>`;
                } else if (trimmed.startsWith('* ') || trimmed.startsWith('- ')) {
                    if (!inList) { htmlResult += '<ul>'; inList = true; }
                    htmlResult += `<li>` + parseCustomStyles(trimmed.substring(2)) + `</li>`;
                } else {
                    if (inList) { htmlResult += '</ul>'; inList = false; }
                    htmlResult += `<p>` + parseCustomStyles(trimmed) + `</p>`;
                }
            }

            if (inList) htmlResult += '</ul>';
            if (inTable) htmlResult += '</table>';

            return htmlResult;
        }

        // Parse chapters based on logical lines
        const chapters = [];
        function indexChapters() {
            // Check headers like 🚩[topic]🚩 or normal headings
            const regexTopic = /🚩\[(.*?)\]🚩/;
            const blocks = rawContent.split(/\n(?=(?:#|🚩|f|فصل))/i);
            
            blocks.forEach((block, index) => {
                let cleanBlock = block.trim();
                if (!cleanBlock) return;

                let title = "بخش " + (index + 1);
                const topicMatch = cleanBlock.match(regexTopic);
                if (topicMatch) {
                    title = topicMatch[1];
                    cleanBlock = cleanBlock.replace(regexTopic, '').trim();
                } else if (cleanBlock.startsWith('#')) {
                    const firstLine = cleanBlock.split('\n')[0];
                    title = firstLine.replace(/^[#\s]+/, '').trim();
                    cleanBlock = cleanBlock.substring(firstLine.length).trim();
                }

                chapters.push({
                    id: index,
                    title: title,
                    raw: cleanBlock,
                    html: parseMarkdownToHtml(cleanBlock)
                });
            });

            if (chapters.length === 0) {
                chapters.push({
                    id: 0,
                    title: "متن کامل",
                    raw: rawContent,
                    html: parseMarkdownToHtml(rawContent)
                });
            }
        }

        indexChapters();

        // Build navigation index
        const navbar = document.getElementById("nav-sections-list");
        chapters.forEach((chapter, index) => {
            const item = document.createElement("a");
            item.className = "nav-item" + (index === lastChapterIndex ? " active" : "");
            item.innerText = chapter.title;
            item.onclick = () => {
                navigateToChapter(index);
                if (window.innerWidth <= 768) {
                    toggleSidebar(false);
                }
            };
            navbar.appendChild(item);
        });

        // Virtualized Lazy Rendering Engine
        const viewPort = document.getElementById("reader-viewport");
        const container = document.getElementById("reader-container");
        const renderedNodes = new Map(); // chapterId -> element

        function initViewport() {
            container.style.height = "auto";
            container.innerHTML = "";

            chapters.forEach((chapter) => {
                const node = document.createElement("div");
                node.id = "chapter-" + chapter.id;
                node.className = "chapter-node";
                node.style.minHeight = "200px";
                
                // Add title display
                const header = document.createElement("h2");
                header.innerText = chapter.title;
                node.appendChild(header);

                const body = document.createElement("div");
                body.className = "chapter-body";
                node.appendChild(body);

                container.appendChild(node);
                renderedNodes.set(chapter.id, node);
            });

            viewPort.scrollTop = lastScrollY;
            updateLazyMounting();
        }

        // Mount visible chapter content, unload distant chapters
        function updateLazyMounting() {
            const viewportTop = viewPort.scrollTop;
            const viewportBottom = viewportTop + viewPort.clientHeight;
            const buffer = 800; // preload buffer height

            let activeChapterIndex = lastChapterIndex;

            chapters.forEach((chapter) => {
                const node = renderedNodes.get(chapter.id);
                if (!node) return;

                const nodeTop = node.offsetTop;
                const nodeBottom = nodeTop + node.offsetHeight;

                // Check if element is within safe buffer of viewport
                const isNear = (nodeBottom >= viewportTop - buffer) && (nodeTop <= viewportBottom + buffer);

                const body = node.querySelector(".chapter-body");
                if (isNear) {
                    if (body.innerHTML === "") {
                        body.innerHTML = chapter.html;
                    }
                } else {
                    body.innerHTML = ""; // Unload to free memory
                }

                // Highlight active chapter
                if (viewportTop >= nodeTop - 100 && viewportTop < nodeBottom) {
                    activeChapterIndex = chapter.id;
                }
            });

            // Sync with sidebar menu selection
            const navItems = document.querySelectorAll(".nav-item");
            navItems.forEach((item, idx) => {
                if (idx === activeChapterIndex) {
                    item.classList.add("active");
                } else {
                    item.classList.remove("active");
                }
            });

            localStorage.setItem("reader-last-position", viewPort.scrollTop);
            localStorage.setItem("reader-last-chapter", activeChapterIndex);
        }

        viewPort.onscroll = updateLazyMounting;

        function navigateToChapter(index) {
            const node = renderedNodes.get(index);
            if (node) {
                // Ensure content is loaded before scrolling there
                const body = node.querySelector(".chapter-body");
                if (body && body.innerHTML === "") {
                    body.innerHTML = chapters[index].html;
                }
                viewPort.scrollTop = node.offsetTop - 20;
                updateLazyMounting();
            }
        }

        // FULL BOOK SEARCH ENGINE
        const searchInput = document.getElementById("search-input");
        const searchPanel = document.getElementById("search-results-panel");

        searchInput.oninput = () => {
            const query = searchInput.value.trim().toLowerCase();
            if (!query) {
                searchPanel.style.display = "none";
                searchPanel.innerHTML = "";
                return;
            }

            searchPanel.style.display = "block";
            searchPanel.innerHTML = "";

            let foundCount = 0;
            chapters.forEach((chapter) => {
                const idx = chapter.raw.toLowerCase().indexOf(query);
                if (idx !== -1) {
                    foundCount++;
                    const start = Math.max(0, idx - 40);
                    const end = Math.min(chapter.raw.length, idx + query.length + 50);
                    let snippet = chapter.raw.substring(start, end);
                    
                    // highlight occurrences
                    const escapedQuery = query.replace(/[-\/\\^${'$'}\*+?.()|[\]{}]/g, '\\$&');
                    const matchRegex = new RegExp(escapedQuery, "ig");
                    snippet = snippet.replace(matchRegex, (m) => '<span class="search-highlight">' + m + '</span>');

                    const item = document.createElement("div");
                    item.className = "search-result-snippet";
                    item.innerHTML = '<strong>' + chapter.title + ':</strong> ...' + snippet + '...';
                    item.onclick = () => {
                        navigateToChapter(chapter.id);
                        // highlight search text inside chapter momentarily
                        setTimeout(() => {
                            const node = renderedNodes.get(chapter.id);
                            if (node) {
                                const paragraphs = node.querySelectorAll("p, li");
                                paragraphs.forEach(p => {
                                    if (p.textContent.toLowerCase().includes(query)) {
                                        const highlightHtml = p.innerHTML.replace(matchRegex, (m) => '<span style="background-color: #fde047; padding: 2px;">' + m + '</span>');
                                        p.innerHTML = highlightHtml;
                                    }
                                });
                            }
                        }, 250);
                    };
                    searchPanel.appendChild(item);
                }
            });

            if (foundCount === 0) {
                searchPanel.innerHTML = '<div style="padding: 0.5rem; text-align: center; color: var(--text-muted);">موردی پیدا نشد.</div>';
            }
        };

        // UI Interactions
        const sidebar = document.getElementById("sidebar");
        const toggleSidebarBtn = document.getElementById("toggle-sidebar-btn");

        function toggleSidebar(forceState) {
            const open = (forceState !== undefined) ? forceState : sidebar.classList.contains("collapsed");
            if (open) {
                sidebar.classList.remove("collapsed");
                localStorage.setItem("reader-sidebar-open", "true");
            } else {
                sidebar.classList.add("collapsed");
                localStorage.setItem("reader-sidebar-open", "false");
            }
        }

        toggleSidebarBtn.onclick = () => toggleSidebar();

        // Theme management
        const bodyEl = document.documentElement;
        function updateTheme(theme) {
            bodyEl.setAttribute("data-theme", theme);
            currentTheme = theme;
            localStorage.setItem("reader-theme", theme);
        }

        document.getElementById("toggle-theme-btn").onclick = () => {
            const nextTheme = (currentTheme === "light") ? "dark" : "light";
            updateTheme(nextTheme);
        };

        // Initialize theme & sidebar layout state
        updateTheme(currentTheme);
        toggleSidebar(isSidebarOpen);
        initViewport();
    </script>
</body>
</html>
        """.trimIndent()
    }
}
