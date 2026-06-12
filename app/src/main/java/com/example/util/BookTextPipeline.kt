package com.example.util

import java.io.BufferedReader
import java.io.StringReader

object BookTextPipeline {

    // Target a chunk limit of 25,000 characters to leave a comfortable safety buffer below 30,000 max.
    private const val CHUNK_SIZE_LIMIT = 25000

    data class TextSection(val title: String, val content: String)

    /**
     * Parse full book TXT into structured blocks (either custom sections or paragraphs),
     * and bundle them deterministically into stable chunks under 25k characters.
     */
    fun buildDeterministicChunks(text: String): List<String> {
        if (text.length <= CHUNK_SIZE_LIMIT) {
            return listOf(text.trim())
        }

        val sections = detectSections(text)
        val blocks = if (sections.size > 1) {
            // Preserved section structure
            sections.map { "${it.title}\n\n${it.content}" }
        } else {
            // Fall back to paragraphs if no sections were detected
            splitIntoParagraphs(text)
        }

        return groupBlocksIntoChunks(blocks)
    }

    /**
     * Scan the document for indicators of sections or chapters.
     */
    private fun detectSections(text: String): List<TextSection> {
        val reader = BufferedReader(StringReader(text))
        val sections = mutableListOf<TextSection>()
        var currentTitle = "مقدمه"
        val currentContent = StringBuilder()

        val dividerPatterns = listOf(
            Regex("^#+\\s+.*"), // Markdown headers #, ##, ###
            Regex("^(فصل|بخش|گفتار|باب|مبحث|موضوع)\\s+\\d+.*", RegexOption.IGNORE_CASE), // Persian structure words (Fasl 1, Bukhsh 2)
            Regex("^(Chapter|Section)\\s+\\d+.*", RegexOption.IGNORE_CASE), // English chapters
            Regex("^[-=\\*]{3,}\\s*$") // Separators like ---, ===, ***
        )

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val trimmedLine = line!!.trim()
            val isHeader = dividerPatterns.any { it.matches(trimmedLine) }

            if (isHeader) {
                // Save old section
                if (currentContent.isNotEmpty()) {
                    sections.add(TextSection(currentTitle, currentContent.toString().trim()))
                    currentContent.setLength(0)
                }
                currentTitle = trimmedLine
            } else {
                currentContent.append(line).append("\n")
            }
        }

        // Add final section
        if (currentContent.isNotEmpty() || sections.isEmpty()) {
            sections.add(TextSection(currentTitle, currentContent.toString().trim()))
        }

        return sections
    }

    /**
     * Splits text into distinct paragraphs using blank lines.
     */
    private fun splitIntoParagraphs(text: String): List<String> {
        val paragraphBlockList = mutableListOf<String>()
        val lines = text.split("\n")
        val currentParagraph = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (currentParagraph.isNotEmpty()) {
                    paragraphBlockList.add(currentParagraph.toString().trim())
                    currentParagraph.setLength(0)
                }
            } else {
                currentParagraph.append(line).append("\n")
            }
        }
        if (currentParagraph.isNotEmpty()) {
            paragraphBlockList.add(currentParagraph.toString().trim())
        }

        return paragraphBlockList
    }

    /**
     * Deterministically packages blocks (sections or paragraphs) into chunks ≤ CHUNK_SIZE_LIMIT.
     * Large blocks that exceed limits individually are split into character-count chunks.
     */
    private fun groupBlocksIntoChunks(blocks: List<String>): List<String> {
        val chunks = mutableListOf<String>()
        val currentChunk = StringBuilder()

        for (block in blocks) {
            val cleanBlock = block.trim()
            if (cleanBlock.isEmpty()) continue

            // If a single block on its own is larger than 25k, split it by absolute length boundaries
            if (cleanBlock.length > CHUNK_SIZE_LIMIT) {
                // Flush the builder first if there is content
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk.setLength(0)
                }
                // Segment the oversized block
                var index = 0
                while (index < cleanBlock.length) {
                    val end = minOf(index + CHUNK_SIZE_LIMIT, cleanBlock.length)
                    chunks.add(cleanBlock.substring(index, end).trim())
                    index = end
                }
            } else {
                // If it can fit combined, append it
                if (currentChunk.length + cleanBlock.length + 2 <= CHUNK_SIZE_LIMIT) {
                    if (currentChunk.isNotEmpty()) {
                        currentChunk.append("\n\n")
                    }
                    currentChunk.append(cleanBlock)
                } else {
                    // Flush the current chunk and start the next with this block
                    chunks.add(currentChunk.toString().trim())
                    currentChunk.setLength(0)
                    currentChunk.append(cleanBlock)
                }
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        return chunks
    }
}
