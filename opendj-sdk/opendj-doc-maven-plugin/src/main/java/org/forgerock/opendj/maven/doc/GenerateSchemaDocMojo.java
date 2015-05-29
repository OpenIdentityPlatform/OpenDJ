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
import static org.forgerock.opendj.maven.doc.Utils.*;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.forgerock.opendj.ldap.schema.CoreSchemaSupportedLocales;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
        final Locale currentLocale = getLocaleFromTag(locale);
        final String localeReference = getLocalesAndSubTypesDocumentation(currentLocale);
        final File localeReferenceFile = new File(outputDirectory, "sec-locales-subtypes.xml");
        try {
            writeStringToFile(localeReference, localeReferenceFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + localeReferenceFile.getPath());
        }
    }

    /**
     * Returns a DocBook XML Section element documenting supported locales and language subtypes.
     * @param currentLocale The locale for which to generate the documentation.
     * @return A DocBook XML Section element documenting supported locales and language subtypes.
     */
    private String getLocalesAndSubTypesDocumentation(final Locale currentLocale) {
        final Map<String, Object> map = new HashMap<>();
        map.put("year", new SimpleDateFormat("yyyy").format(new Date()));
        map.put("lang", getTagFromLocale(currentLocale));
        map.put("title", DOC_LOCALE_SECTION_TITLE.get());
        map.put("info", DOC_LOCALE_SECTION_INFO.get());
        map.put("locales", getLocalesDocMap(currentLocale));
        map.put("subtypes", getSubTypesDocMap(currentLocale));
        return applyTemplate("sec-locales-subtypes.ftl", map);
    }

    private final Map<String, String> localeTagsToOids =
            CoreSchemaSupportedLocales.getJvmSupportedLocaleNamesToOids();

    /** Container for documentation regarding a locale. */
    private class LocaleDoc {
        String tag;
        String language;
        String oid;
    }

    /**
     * Returns a map of languages to Locale documentation containers.
     * @param currentLocale     The Locale of the resulting documentation.
     * @return A map of languages to Locale documentation containers.
     */
    private Map<String, LocaleDoc> getLanguagesToLocalesMap(final Locale currentLocale) {
        Map<String, LocaleDoc> locales = new TreeMap<>();
        for (String tag : localeTagsToOids.keySet()) {
            final Locale locale = getLocaleFromTag(tag);
            if (locale == null) {
                continue;
            }
            final LocaleDoc localeDoc = new LocaleDoc();
            localeDoc.tag = tag;
            localeDoc.language = locale.getDisplayName(currentLocale);
            localeDoc.oid = localeTagsToOids.get(tag);
            if (!localeDoc.language.equals(localeDoc.tag)) {
                // No display language so must not be supported in current JVM
                locales.put(localeDoc.language, localeDoc);
            } else if (localeDoc.tag.equals("sh")) {
                localeDoc.language = DOC_LANGUAGE_SH.get().toString(currentLocale);
                locales.put(localeDoc.language, localeDoc);
            }
        }
        return locales;
    }

    /**
     * Returns a map of information for documenting supported locales.
     * @param currentLocale The locale for which to generate the information.
     * @return A map of information for documenting supported locales.
     */
    private Map<String, Object> getLocalesDocMap(final Locale currentLocale) {
        final Map<String, Object> result = new HashMap<>();
        result.put("title", DOC_SUPPORTED_LOCALES_TITLE.get());
        result.put("indexTerm", DOC_SUPPORTED_LOCALES_INDEXTERM.get());
        final Map<String, LocaleDoc> localesMap = getLanguagesToLocalesMap(currentLocale);
        final Set<String> sortedLanguages = localesMap.keySet();
        final List<Map<String, Object>> locales = new LinkedList<>();
        for (final String language : sortedLanguages) {
            final LocaleDoc locale = localesMap.get(language);
            final Map<String, Object> map = new HashMap<>();
            map.put("language", locale.language);
            map.put("tag", DOC_LOCALE_TAG.get(locale.tag));
            map.put("oid", DOC_LOCALE_OID.get(locale.oid));
            locales.add(map);
        }
        result.put("locales", locales);
        return result;
    }

    /**
     * Returns a map of information for documenting supported language subtypes.
     * @param currentLocale The locale for which to generate the information.
     * @return A map of information for documenting supported language subtypes.
     */
    private Map<String, Object> getSubTypesDocMap(final Locale currentLocale) {
        final Map<String, Object> result = new HashMap<>();
        result.put("title", DOC_SUPPORTED_SUBTYPES_TITLE.get());
        result.put("indexTerm", DOC_SUPPORTED_SUBTYPES_INDEXTERM.get());
        final List<Map<String, Object>> locales = new LinkedList<>();
        for (final String tag : localeTagsToOids.keySet()) {
            final Map<String, Object> map = new HashMap<>();
            int idx = tag.indexOf('-');
            if (idx == -1) {
                final Locale locale = getLocaleFromTag(tag);
                if (locale == null) {
                    continue;
                }
                final String language = locale.getDisplayName(currentLocale);
                if (!language.equals(tag)) {
                    map.put("language", language);
                    map.put("tag", tag);
                } else if (tag.equals("sh")) {
                    map.put("language", DOC_LANGUAGE_SH.get().toString(currentLocale));
                    map.put("tag", tag);
                }
                if (!map.isEmpty()) {
                    locales.add(map);
                }
            }
        }
        result.put("locales", locales);
        return result;
    }

    /**
     * Returns the Locale based on the tag, or null if the tag is null.
     * <br>
     * Java 6 is missing {@code Locale.forLanguageTag()}.
     * @param tag   The tag for the locale, such as {@code en_US}.
     * @return The Locale based on the tag, or null if the tag is null.
     */
    private Locale getLocaleFromTag(final String tag) {
        if (tag == null) {
            return null;
        }

        // Apparently Locale tags can include not only languages and countries,
        // but also variants, e.g. es_ES_Traditional_WIN.
        // Pull these apart to be able to construct the locale.
        //
        // OpenDJ does not seem to have any locales with variants, but just in case...
        // The separator in OpenDJ seems to be '-'.
        // @see CoreSchemaSupportedLocales#LOCALE_NAMES_TO_OIDS
        final char sep = '-';
        int langIdx = tag.indexOf(sep);
        final String lang;
        if (langIdx == -1) {
            // No country or variant
            return new Locale(tag);
        } else {
            lang = tag.substring(0, langIdx);
        }

        int countryIdx = tag.indexOf(sep, langIdx + 1);
        final String country;
        if (countryIdx == -1) {
            // No variant
            country = tag.substring(langIdx + 1);
            return new Locale(lang, country);
        } else {
            country = tag.substring(langIdx + 1, countryIdx);
            final String variant = tag.substring(countryIdx + 1);
            return new Locale(lang, country, variant);
        }
    }

    /**
     * Returns the tag based on the Locale, or null if the Locale is null.
     * <br>
     * Java 6 is missing {@code Locale.toLanguageTag()}.
     * @param locale        The Locale for which to return the tag.
     * @return The tag based on the Locale, or null if the Locale is null.
     */
    private String getTagFromLocale(final Locale locale) {
        if (locale == null) {
            return null;
        }

        final String  lang    = locale.getLanguage();
        final String  country = locale.getCountry();
        final String  variant = locale.getVariant();
        final char    sep     = '-';
        StringBuilder tag     = new StringBuilder();
        if (lang != null) {
            tag.append(lang);
        }
        if (country != null && !country.isEmpty()) {
            tag.append(sep).append(country);
        }
        if (variant != null && !variant.isEmpty()) {
            tag.append(sep).append(variant);
        }
        return tag.toString();
    }
}
