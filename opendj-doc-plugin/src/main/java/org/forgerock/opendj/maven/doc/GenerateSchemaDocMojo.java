/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *      Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.maven.doc;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.forgerock.opendj.ldap.schema.CoreSchemaSupportedLocales;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Generate schema-related reference documentation sources.
 */
@Mojo(name = "generate-schema-ref")
public class GenerateSchemaDocMojo extends AbstractMojo {
    /** The locale for which to generate the documentation. */
    @Parameter(defaultValue = "en")
    private String locale;

    /** Output directory for source files. */
    @Parameter(defaultValue = "${project.build.directory}/docbkx-sources/shared")
    private File outputDirectory;

    /**
     * Writes schema reference documentation source files.
     * @throws MojoExecutionException   Not used.
     * @throws MojoFailureException     Failed to write a file.
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Locale currentLocale = Locale.forLanguageTag(locale);

        final String localeReference = getLocalesAndSubTypesDocumentation(currentLocale);
        final File localeReferenceFile = new File(outputDirectory, "sec-locales-subtypes.xml");
        try {
            createOutputDirectory();
            writeStringToFile(localeReference, localeReferenceFile);
        } catch (FileNotFoundException e) {
            throw new MojoFailureException("Failed to write doc reference file.", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create output directory");
        }
    }

    /**
     * Create the output directory if it does not exist.
     * @throws IOException  Failed to create the directory.
     */
    private void createOutputDirectory() throws IOException {
        if (outputDirectory != null && !outputDirectory.exists()) {
            if (!outputDirectory.mkdirs()) {
                throw new IOException("Failed to create output directory.");
            }
        }
    }

    /**
     * Writes a string to a file.
     * @param string    The string to write.
     * @param file      The file to write to.
     * @throws FileNotFoundException The file did not exist, or could not be created for writing.
     */
    private void writeStringToFile(final String string, final File file) throws FileNotFoundException {
        PrintWriter printWriter = new PrintWriter(file);
        printWriter.print(string);
        printWriter.close();
    }

    private final Map<String, String> localeTagsToOids =
            CoreSchemaSupportedLocales.getJvmSupportedLocaleNamesToOids();

    /**
     * Returns a DocBook XML VariableList element documenting supported locales.
     * @param currentLocale The locale for which to generate the documentation.
     * @return A DocBook XML VariableList element documenting supported locales.
     */
    private String getLocalesDocumentation(final Locale currentLocale) {
        class LocaleDoc {
            String tag;
            String language;
            String oid;
        }

        Map<String, LocaleDoc> locales = new HashMap<String, LocaleDoc>();
        for (String tag : localeTagsToOids.keySet()) {
            final Locale locale = Locale.forLanguageTag(tag);
            final LocaleDoc localeDoc = new LocaleDoc();
            localeDoc.tag = tag;
            localeDoc.language = locale.getDisplayName(currentLocale);
            localeDoc.oid = localeTagsToOids.get(tag);
            if (!localeDoc.language.equals(localeDoc.tag)) {
                // No display language so must not be supported in current JVM
                locales.put(localeDoc.language, localeDoc);
            } else {
                if (localeDoc.tag.equals("sh")) {
                    localeDoc.language = DOC_LANGUAGE_SH.get().toString(currentLocale);
                    locales.put(localeDoc.language, localeDoc);
                }
            }
        }
        if (locales.isEmpty()) {
            return "";
        }

        final String eol = System.getProperty("line.separator");
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder
                .append(" <variablelist xml:id=\"supported-locales\">").append(eol)
                .append("  <title>").append(DOC_SUPPORTED_LOCALES_TITLE.get()).append("</title>").append(eol)
                .append("  <indexterm><primary>").append(DOC_SUPPORTED_LOCALES_INDEXTERM.get())
                .append("</primary></indexterm>").append(eol);
        Set<String> sortedLanguages = new TreeSet<String>(locales.keySet());
        for (String language : sortedLanguages) {
            LocaleDoc locale = locales.get(language);
            stringBuilder
                    .append("  <varlistentry>").append(eol)
                    .append("   <term>").append(locale.language).append("</term>").append(eol)
                    .append("   <listitem>").append(eol)
                    .append("    <para>").append(DOC_LOCALE_TAG.get(locale.tag)).append("</para>").append(eol)
                    .append("    <para>").append(DOC_LOCALE_OID.get(locale.oid)).append("</para>").append(eol)
                    .append("   </listitem>").append(eol)
                    .append("  </varlistentry>").append(eol);
        }
        stringBuilder.append(" </variablelist>").append(eol);
        return stringBuilder.toString();
    }

    /**
     * Returns a DocBook XML ItemizedList element documenting supported language subtypes.
     * @param currentLocale The locale for which to generate the documentation.
     * @return A DocBook XML ItemizedList element documenting supported language subtypes.
     */
    private String getSubTypesDocumentation(final Locale currentLocale) {
        Map<String, String> map = new TreeMap<String, String>();
        for (String tag : localeTagsToOids.keySet()) {
            int idx = tag.indexOf('-');
            if (idx == -1) {
                final Locale locale = Locale.forLanguageTag(tag);
                final String language = locale.getDisplayName(currentLocale);
                if (!language.equals(tag)) {
                    map.put(language, tag);
                } else {
                    if (tag.equals("sh")) {
                        map.put(DOC_LANGUAGE_SH.get().toString(currentLocale), tag);
                    }
                }
            }
        }

        final String eol = System.getProperty("line.separator");
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(" <itemizedlist xml:id=\"supported-language-subtypes\">").append(eol)
                .append("  <title>").append(DOC_SUPPORTED_SUBTYPES_TITLE.get()).append("</title>").append(eol)
                .append("  <indexterm><primary>").append(DOC_SUPPORTED_SUBTYPES_INDEXTERM.get())
                .append("</primary></indexterm>").append(eol);
        for (String language: map.keySet()) {
            stringBuilder
                    .append("  <listitem><para>").append(language).append(", ")
                    .append(map.get(language)).append("</para></listitem>").append(eol);
        }
        stringBuilder.append(" </itemizedlist>").append(eol);
        return stringBuilder.toString();
    }

    /**
     * Returns a DocBook XML Section element documenting supported locales and language subtypes.
     * @param currentLocale The locale for which to generate the documentation.
     * @return A DocBook XML Section element documenting supported locales and language subtypes.
     */
    private String getLocalesAndSubTypesDocumentation(final Locale currentLocale) {
        final String eol = System.getProperty("line.separator");
        return "<section xml:id=\"sec-locales-subtypes\" "
                + "xmlns=\"http://docbook.org/ns/docbook\" version=\"5.0\" "
                + "xml:lang=\"" + currentLocale.toLanguageTag() + "\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:schemaLocation=\"http://docbook.org/ns/docbook http://docbook.org/xml/5.0/xsd/docbook.xsd\""
                + ">" + eol
                + " <title>" + DOC_LOCALE_SECTION_TITLE.get() + "</title>" + eol
                + " <para>" + DOC_LOCALE_SECTION_INFO.get() + "</para>" + eol
                + getLocalesDocumentation(currentLocale) + eol
                + getSubTypesDocumentation(currentLocale) + eol
                + "</section>";
    }
}
