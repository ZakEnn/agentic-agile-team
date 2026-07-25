package com.agile.team.infrastructure.adapter.confluence;

/**
 * Utility to convert Confluence HTML/storage-format content to plain text.
 * Uses regex-based stripping — sufficient for Confluence page bodies.
 */
public final class HtmlToTextConverter {

    private HtmlToTextConverter() {
    }

    /**
     * Strips HTML tags and decodes common entities, returning clean plain text.
     */
    public static String convert(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        String text = html;

        // Replace <br>, <br/>, <p>, </p>, <div>, </div> with newlines
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</?(p|div|tr|li|h[1-6])[^>]*>", "\n");

        // Replace <td>, <th> with tab (table cells)
        text = text.replaceAll("(?i)<(td|th)[^>]*>", "\t");

        // Remove all remaining HTML tags
        text = text.replaceAll("<[^>]+>", "");

        // Decode common HTML entities
        text = text.replace("&amp;", "&");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&quot;", "\"");
        text = text.replace("&apos;", "'");
        text = text.replace("&#39;", "'");
        text = text.replace("&nbsp;", " ");

        // Decode numeric entities (&#NNN;)
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("&#(\\d+);").matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            try {
                int codePoint = Integer.parseInt(matcher.group(1));
                matcher.appendReplacement(sb, String.valueOf((char) codePoint));
            } catch (NumberFormatException e) {
                matcher.appendReplacement(sb, matcher.group());
            }
        }
        matcher.appendTail(sb);
        text = sb.toString();

        // Collapse multiple blank lines into at most two
        text = text.replaceAll("(\\s*\\n){3,}", "\n\n");

        // Trim leading/trailing whitespace per line
        text = text.lines()
                .map(String::strip)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        return text.strip();
    }
}
