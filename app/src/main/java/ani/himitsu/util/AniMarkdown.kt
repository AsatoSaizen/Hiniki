package ani.himitsu.util

import ani.himitsu.util.ColorEditor.Companion.toCssColor

object AniMarkdown {
    private fun String.convertNestedImageToHtml(): String {
        return """\[!\[(.*?)]\((.*?)\)]\((.*?)\)""".toRegex().replace(this) { matchResult ->
            val altText = matchResult.groupValues[1]
            val imageUrl = matchResult.groupValues[2]
            val linkUrl = matchResult.groupValues[3]
            """<a href="$linkUrl"><img src="$imageUrl" alt="$altText"></a>"""
        }
    }

    private fun String.convertImageToHtml(): String {
        return """!\[(.*?)]\((.*?)\)""".toRegex().replace(this) { matchResult ->
            val altText = matchResult.groupValues[1]
            val imageUrl = matchResult.groupValues[2]
            """<img src="$imageUrl" alt="$altText">"""
        }
    }

    private fun String.convertImageTagToHtml(): String {
        return """img\(.*?\)""".toRegex().replace(this) { matchResult ->
            val imageUrl = matchResult.groupValues[1]
            """<img src="$imageUrl" alt="$imageUrl">"""
        }
    }

    private fun String.convertLinkToHtml(): String {
        return """\[(.*?)]\((.*?)\)""".toRegex().replace(this) { matchResult ->
            val linkText = matchResult.groupValues[1]
            val linkUrl = matchResult.groupValues[2]
            """<a href="$linkUrl">$linkText</a>"""
        }
    }

    private fun String.replaceLeftovers(): String {
        return replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("<pre>", "")
            .replace("`", "")
            .replace(">\n<", "><br><")
            .replace("\n", "<br>")
    }

    private fun String.formattingToHtml(): String {
        return replace("(?s)~~~(.*?)~~~".toRegex(), "<center>$1</center>")
            .replace("(?s)~~(.*?)~~".toRegex(), "<s>$1</s>")
            .replace("(?s)___(.*?)___".toRegex(), "<em><strong>$1</strong></em>")
            .replace("(?s)__(.*?)__".toRegex(), "<strong>$1</strong>")
            .replace("(?s)_(.*?)_".toRegex(), "<em>$1</em>")
            .replace("(?s)\\*\\*\\*(.*?)\\*\\*\\*".toRegex(), "<em><strong>$1</strong></em>")
            .replace("(?s)\\*\\*(.*?)\\*\\*".toRegex(), "<strong>$1</strong>")
            .replace("(?s)\\*(.*?)\\*".toRegex(), "<em>$1</em>")
            .replace("(?s)___".toRegex(), "<hr style=\"appearance: none;\">")
            .replace("(?s)---".toRegex(), "<hr style=\"appearance: none;\">")
    }

    fun getBasicAniHTML(html: String): String {
        return html.convertNestedImageToHtml()
            .convertImageToHtml()
            .convertImageTagToHtml()
            .convertLinkToHtml()
            .replaceLeftovers()
            .formattingToHtml()
    }

    fun getFullAniHTML(html: String, textColor: Int): String {
        return """
<html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, charset=UTF-8">
            <style>
                body {
                    color: ${textColor.toCssColor()};
                    margin: 0;
                    padding: 0;
                    max-width: 100%;
                    overflow-x: hidden; /* Prevent horizontal scrolling */
                }
                img {
                    max-width: 100%;
                    height: auto; /* Maintain aspect ratio */
                }
                video {
                    max-width: 100%;
                    height: auto; /* Maintain aspect ratio */
                }
                a {
                    color: ${textColor.toCssColor()};
                }
                /* Add responsive design elements for other content as needed */
            </style>
    </head>
    <body>
        ${getBasicAniHTML(html)}
    </body>
</html>
            """.trimIndent()
    }
}