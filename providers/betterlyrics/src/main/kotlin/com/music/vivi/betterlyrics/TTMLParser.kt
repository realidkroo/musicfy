// ttmlparser kt
// what is this for you ask its for ttmlparser ofc

package com.example.musicfy.betterlyrics

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

object TTMLParser {
    
    data class ParsedLine(
        val text: String,
        val startTime: Double,
        val words: List<ParsedWord>,
        val agent: String? = null,
        val isBackground: Boolean = false,
        val backgroundLines: List<ParsedLine> = emptyList()
    )
    
    data class ParsedWord(
        val text: String,
        val startTime: Double,
        val endTime: Double
    )
    
    private data class SpanInfo(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val hasTrailingSpace: Boolean
    )
    
    // helper function to get attribute by local name handles namespace prefixes
    private fun Element.getAttributeByLocalName(localName: String): String {
        // first try namespace aware lookup
        val nsValue = getAttributeNS("http://www.w3.org/ns/ttml#metadata", localName)
        if (nsValue.isNotEmpty()) return nsValue
        
        // then try with common prefixes
        val prefixedValue = getAttribute("ttm:$localName")
        if (prefixedValue.isNotEmpty()) return prefixedValue
        
        // finally search through all attributes
        val attrs = attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val attrName = attr.nodeName ?: continue
            if (attrName == localName || attrName.endsWith(":$localName")) {
                return attr.nodeValue ?: ""
            }
        }
        return ""
    }
    
    fun parseTTML(ttml: String): List<ParsedLine> {
        val lines = mutableListOf<ParsedLine>()
        
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ttml.byteInputStream())
            
            val pElements = doc.getElementsByTagName("p")
            
            for (i in 0 until pElements.length) {
                val pElement = pElements.item(i) as? Element ?: continue
                
                val begin = pElement.getAttribute("begin")
                if (begin.isNullOrEmpty()) continue
                
                val startTime = parseTime(begin)
                val spanInfos = mutableListOf<SpanInfo>()
                val backgroundLines = mutableListOf<ParsedLine>()
                
                // get agent vocalist info ttm agent attribute
                val agent = pElement.getAttributeByLocalName("agent").ifEmpty { null }
                
                // parse child nodes to preserve whitespace between spans
                val childNodes = pElement.childNodes
                for (j in 0 until childNodes.length) {
                    val node = childNodes.item(j)
                    
                    when (node.nodeType) {
                        Node.ELEMENT_NODE -> {
                            val span = node as? Element
                            if (span?.tagName?.lowercase() == "span") {
                                // check for background vocal role ttm role= x bg
                                val role = span.getAttributeByLocalName("role")
                                
                                when (role) {
                                    "x-bg" -> {
                                        // parse background vocal line
                                        val bgLine = parseBackgroundSpan(span, startTime)
                                        if (bgLine != null) {
                                            backgroundLines.add(bgLine)
                                        }
                                    }
                                    "x-translation", "x-roman" -> {
                                        // skip translation and romanization spans
                                    }
                                    else -> {
                                        // regular word span
                                        val wordBegin = span.getAttribute("begin")
                                        val wordEnd = span.getAttribute("end")
                                        val wordText = span.textContent?.trim() ?: ""
                                        
                                        if (wordText.isNotEmpty() && wordBegin.isNotEmpty() && wordEnd.isNotEmpty()) {
                                            val nextSibling = node.nextSibling
                                            val hasTrailingSpace = isWordBoundary(nextSibling)
                                            
                                            spanInfos.add(
                                                SpanInfo(
                                                    text = wordText,
                                                    startTime = parseTime(wordBegin),
                                                    endTime = parseTime(wordEnd),
                                                    hasTrailingSpace = hasTrailingSpace
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // merge consecutive spans without whitespace between them into single words
                val words = mergeSpansIntoWords(spanInfos)
                val lineText = words.joinToString(" ") { it.text }
                
                // if no spans found use text content directly excluding background text
                val finalText = if (lineText.isEmpty()) {
                    getDirectTextContent(pElement).trim()
                } else {
                    lineText
                }
                
                if (finalText.isNotEmpty()) {
                    lines.add(
                        ParsedLine(
                            text = finalText,
                            startTime = startTime,
                            words = words,
                            agent = agent,
                            isBackground = false,
                            backgroundLines = backgroundLines
                        )
                    )
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        
        return lines
    }
    
    private fun parseBackgroundSpan(span: Element, parentStartTime: Double): ParsedLine? {
        val bgBegin = span.getAttribute("begin")
        val bgEnd = span.getAttribute("end")
        val bgStartTime = if (bgBegin.isNotEmpty()) parseTime(bgBegin) else parentStartTime
        
        val spanInfos = mutableListOf<SpanInfo>()
        val childNodes = span.childNodes
        
        for (j in 0 until childNodes.length) {
            val node = childNodes.item(j)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val innerSpan = node as? Element
                if (innerSpan?.tagName?.lowercase() == "span") {
                    val role = innerSpan.getAttributeByLocalName("role")
                    
                    // skip translation and romanization spans
                    if (role == "x-translation" || role == "x-roman") continue
                    
                    val wordBegin = innerSpan.getAttribute("begin")
                    val wordEnd = innerSpan.getAttribute("end")
                    val wordText = innerSpan.textContent?.trim() ?: ""
                    
                    if (wordText.isNotEmpty() && wordBegin.isNotEmpty() && wordEnd.isNotEmpty()) {
                        val nextSibling = node.nextSibling
                        val hasTrailingSpace = nextSibling?.nodeType == Node.TEXT_NODE && 
                            nextSibling.textContent?.contains(Regex("\\s")) == true
                        
                        spanInfos.add(
                            SpanInfo(
                                text = wordText,
                                startTime = parseTime(wordBegin),
                                endTime = parseTime(wordEnd),
                                hasTrailingSpace = hasTrailingSpace
                            )
                        )
                    }
                }
            }
        }
        
        val words = mergeSpansIntoWords(spanInfos)
        val lineText = words.joinToString(" ") { it.text }
        
        val finalText = if (lineText.isEmpty()) {
            getDirectTextContent(span).trim()
        } else {
            lineText
        }
        
        return if (finalText.isNotEmpty()) {
            ParsedLine(
                text = finalText,
                startTime = bgStartTime,
                words = words,
                agent = null,
                isBackground = true,
                backgroundLines = emptyList()
            )
        } else null
    }
    
    private fun getDirectTextContent(element: Element): String {
        val sb = StringBuilder()
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.TEXT_NODE) {
                sb.append(node.textContent)
            } else if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as? Element
                val role = el?.getAttributeByLocalName("role") ?: ""
                // skip background translation and romanization spans
                if (role != "x-bg" && role != "x-translation" && role != "x-roman") {
                    if (el?.tagName?.lowercase() == "span") {
                        sb.append(el.textContent ?: "")
                    }
                }
            }
        }
        return sb.toString()
    }
    
    // whether the text node between two spans is a real word gap rather than xml indentation apple music ttml times each syllable as its own span and relies on the literal whitespace between spans to say where one word actually ends the previous test was contains regex \\s which treats any whitespace as a gap so as soon as the document arrives pretty printed the \n indentation node sitting between every span reads as a word boundary and every syllable becomes its own word that is what turned dirancang lega into di ran cang le ga a genuine separator is a space or tab on the same line a run of whitespace containing a newline is formatting and the spans it separates belong to the same word
    private fun isWordBoundary(node: Node?): Boolean {
        if (node == null || node.nodeType != Node.TEXT_NODE) return false
        val between = node.textContent ?: return false
        if (between.isEmpty()) return false
        if (between.contains('\n') || between.contains('\r')) return false
        return between.any { it == ' ' || it == '\t' || it == ' ' }
    }

    private fun mergeSpansIntoWords(spanInfos: List<SpanInfo>): List<ParsedWord> {
        if (spanInfos.isEmpty()) return emptyList()
        
        val words = mutableListOf<ParsedWord>()
        var currentText = StringBuilder()
        var currentStartTime = spanInfos[0].startTime
        var currentEndTime = spanInfos[0].endTime
        
        for ((index, span) in spanInfos.withIndex()) {
            if (index == 0) {
                currentText.append(span.text)
                currentStartTime = span.startTime
                currentEndTime = span.endTime
            } else {
                // check if previous span had trailing space word boundary
                val prevSpan = spanInfos[index - 1]
                if (prevSpan.hasTrailingSpace) {
                    // save current word and start new one
                    if (currentText.isNotEmpty()) {
                        words.add(
                            ParsedWord(
                                text = currentText.toString().trim(),
                                startTime = currentStartTime,
                                endTime = currentEndTime
                            )
                        )
                    }
                    currentText = StringBuilder(span.text)
                    currentStartTime = span.startTime
                    currentEndTime = span.endTime
                } else {
                    // no space between spans merge into same word syllables
                    currentText.append(span.text)
                    currentEndTime = span.endTime
                }
            }
        }
        
        // add the last word
        if (currentText.isNotEmpty()) {
            words.add(
                ParsedWord(
                    text = currentText.toString().trim(),
                    startTime = currentStartTime,
                    endTime = currentEndTime
                )
            )
        }
        
        return words
    }
    
    fun toLRC(lines: List<ParsedLine>): String {
        return buildString {
            lines.forEach { line ->
                val timeMs = (line.startTime * 1000).toLong()
                val minutes = timeMs / 60000
                val seconds = (timeMs % 60000) / 1000
                val centiseconds = (timeMs % 1000) / 10
                
                // add agent info if present
                val agentPrefix = if (!line.agent.isNullOrEmpty()) "{agent:${line.agent}}" else ""
                
                appendLine(String.format("[%02d:%02d.%02d]%s%s", minutes, seconds, centiseconds, agentPrefix, line.text))
                
                if (line.words.isNotEmpty()) {
                    val wordsData = line.words.joinToString("|") { word ->
                        "${word.text}:${word.startTime}:${word.endTime}"
                    }
                    appendLine("<$wordsData>")
                }
                
                // add background vocals as separate lines
                line.backgroundLines.forEach { bgLine ->
                    val bgTimeMs = (bgLine.startTime * 1000).toLong()
                    val bgMinutes = bgTimeMs / 60000
                    val bgSeconds = (bgTimeMs % 60000) / 1000
                    val bgCentiseconds = (bgTimeMs % 1000) / 10
                    
                    appendLine(String.format("[%02d:%02d.%02d]{bg}%s", bgMinutes, bgSeconds, bgCentiseconds, bgLine.text))
                    
                    if (bgLine.words.isNotEmpty()) {
                        val bgWordsData = bgLine.words.joinToString("|") { word ->
                            "${word.text}:${word.startTime}:${word.endTime}"
                        }
                        appendLine("<$bgWordsData>")
                    }
                }
            }
        }
    }
    
    private fun parseTime(timeStr: String): Double {
        return try {
            when {
                timeStr.contains(":") -> {
                    val parts = timeStr.split(":")
                    when (parts.size) {
                        2 -> {
                            val minutes = parts[0].toDouble()
                            val seconds = parts[1].toDouble()
                            minutes * 60 + seconds
                        }
                        3 -> {
                            val hours = parts[0].toDouble()
                            val minutes = parts[1].toDouble()
                            val seconds = parts[2].toDouble()
                            hours * 3600 + minutes * 60 + seconds
                        }
                        else -> timeStr.toDoubleOrNull() ?: 0.0
                    }
                }
                else -> timeStr.toDoubleOrNull() ?: 0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
}
