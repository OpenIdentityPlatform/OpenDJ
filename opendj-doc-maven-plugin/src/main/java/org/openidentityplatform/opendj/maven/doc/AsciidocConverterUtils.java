package org.openidentityplatform.opendj.maven.doc;

public class AsciidocConverterUtils {
    public static String escapeVerticalLine(String text) {
        return text.replace("|", "\\|");
    }
}
