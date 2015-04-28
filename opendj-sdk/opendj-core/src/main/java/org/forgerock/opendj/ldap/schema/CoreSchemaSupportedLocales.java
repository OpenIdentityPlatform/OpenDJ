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
 *
 *      Copyright 2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Provides a map of supported locale tags to OIDs.
 */
public final class CoreSchemaSupportedLocales {
    /**
     * Returns the unmodifiable map of JVM supported locale tags to OIDs.
     * @return The unmodifiable map of JVM supported locale tags to OIDs.
     */
    public static Map<String, String> getJvmSupportedLocaleNamesToOids() {
        return Collections.unmodifiableMap(JVM_SUPPORTED_LOCALE_NAMES_TO_OIDS);
    }

    /**
     * Provides the oid associated to each locale, for the registration of collation matching rules.
     * <p>
     * To add support for a new locale to collation matching rules, add its name as key and the corresponding oid as
     * value.
     */
    private static final Map<String, String> LOCALE_NAMES_TO_OIDS = new HashMap<>();

    /**
     * Same as {@link CoreSchemaSupportedLocales#LOCALE_NAMES_TO_OIDS}, but it contains the old locale names
     * that will be used for the registration of collation matching rules.
     * It is automatically populated on static initialization of current class.
     * It allows the initialization process to complete when newer locales are referenced by config.ldif,
     * but the JVM only works with the old locale names.
     * @see CoreSchemaSupportedLocales#LOCALE_NAMES_TO_OIDS
     */
    private static final Map<String, String> JVM_SUPPORTED_LOCALE_NAMES_TO_OIDS = new HashMap<>();

    static {
        LOCALE_NAMES_TO_OIDS.put("af", "1.3.6.1.4.1.42.2.27.9.4.1.1");
        LOCALE_NAMES_TO_OIDS.put("am", "1.3.6.1.4.1.42.2.27.9.4.2.1");
        LOCALE_NAMES_TO_OIDS.put("ar", "1.3.6.1.4.1.42.2.27.9.4.3.1");
        LOCALE_NAMES_TO_OIDS.put("ar-AE", "1.3.6.1.4.1.42.2.27.9.4.4.1");
        LOCALE_NAMES_TO_OIDS.put("ar-BH", "1.3.6.1.4.1.42.2.27.9.4.5.1");
        LOCALE_NAMES_TO_OIDS.put("ar-DZ", "1.3.6.1.4.1.42.2.27.9.4.6.1");
        LOCALE_NAMES_TO_OIDS.put("ar-EG", "1.3.6.1.4.1.42.2.27.9.4.7.1");
        LOCALE_NAMES_TO_OIDS.put("ar-IN", "1.3.6.1.4.1.42.2.27.9.4.8.1");
        LOCALE_NAMES_TO_OIDS.put("ar-IQ", "1.3.6.1.4.1.42.2.27.9.4.9.1");
        LOCALE_NAMES_TO_OIDS.put("ar-JO", "1.3.6.1.4.1.42.2.27.9.4.10.1");
        LOCALE_NAMES_TO_OIDS.put("ar-KW", "1.3.6.1.4.1.42.2.27.9.4.11.1");
        LOCALE_NAMES_TO_OIDS.put("ar-LB", "1.3.6.1.4.1.42.2.27.9.4.12.1");
        LOCALE_NAMES_TO_OIDS.put("ar-LY", "1.3.6.1.4.1.42.2.27.9.4.13.1");
        LOCALE_NAMES_TO_OIDS.put("ar-MA", "1.3.6.1.4.1.42.2.27.9.4.14.1");
        LOCALE_NAMES_TO_OIDS.put("ar-OM", "1.3.6.1.4.1.42.2.27.9.4.15.1");
        LOCALE_NAMES_TO_OIDS.put("ar-QA", "1.3.6.1.4.1.42.2.27.9.4.16.1");
        LOCALE_NAMES_TO_OIDS.put("ar-SA", "1.3.6.1.4.1.42.2.27.9.4.17.1");
        LOCALE_NAMES_TO_OIDS.put("ar-SD", "1.3.6.1.4.1.42.2.27.9.4.18.1");
        LOCALE_NAMES_TO_OIDS.put("ar-SY", "1.3.6.1.4.1.42.2.27.9.4.19.1");
        LOCALE_NAMES_TO_OIDS.put("ar-TN", "1.3.6.1.4.1.42.2.27.9.4.20.1");
        LOCALE_NAMES_TO_OIDS.put("ar-YE", "1.3.6.1.4.1.42.2.27.9.4.21.1");
        LOCALE_NAMES_TO_OIDS.put("be", "1.3.6.1.4.1.42.2.27.9.4.22.1");
        LOCALE_NAMES_TO_OIDS.put("bg", "1.3.6.1.4.1.42.2.27.9.4.23.1");
        LOCALE_NAMES_TO_OIDS.put("bn", "1.3.6.1.4.1.42.2.27.9.4.24.1");
        LOCALE_NAMES_TO_OIDS.put("ca", "1.3.6.1.4.1.42.2.27.9.4.25.1");
        LOCALE_NAMES_TO_OIDS.put("cs", "1.3.6.1.4.1.42.2.27.9.4.26.1");
        LOCALE_NAMES_TO_OIDS.put("da", "1.3.6.1.4.1.42.2.27.9.4.27.1");
        LOCALE_NAMES_TO_OIDS.put("de", "1.3.6.1.4.1.42.2.27.9.4.28.1");
        LOCALE_NAMES_TO_OIDS.put("de-DE", "1.3.6.1.4.1.42.2.27.9.4.28.1");
        LOCALE_NAMES_TO_OIDS.put("de-AT", "1.3.6.1.4.1.42.2.27.9.4.29.1");
        LOCALE_NAMES_TO_OIDS.put("de-BE", "1.3.6.1.4.1.42.2.27.9.4.30.1");
        LOCALE_NAMES_TO_OIDS.put("de-CH", "1.3.6.1.4.1.42.2.27.9.4.31.1");
        LOCALE_NAMES_TO_OIDS.put("de-LU", "1.3.6.1.4.1.42.2.27.9.4.32.1");
        LOCALE_NAMES_TO_OIDS.put("el", "1.3.6.1.4.1.42.2.27.9.4.33.1");
        LOCALE_NAMES_TO_OIDS.put("en", "1.3.6.1.4.1.42.2.27.9.4.34.1");
        LOCALE_NAMES_TO_OIDS.put("en-US", "1.3.6.1.4.1.42.2.27.9.4.34.1");
        LOCALE_NAMES_TO_OIDS.put("en-AU", "1.3.6.1.4.1.42.2.27.9.4.35.1");
        LOCALE_NAMES_TO_OIDS.put("en-CA", "1.3.6.1.4.1.42.2.27.9.4.36.1");
        LOCALE_NAMES_TO_OIDS.put("en-GB", "1.3.6.1.4.1.42.2.27.9.4.37.1");
        LOCALE_NAMES_TO_OIDS.put("en-HK", "1.3.6.1.4.1.42.2.27.9.4.38.1");
        LOCALE_NAMES_TO_OIDS.put("en-IE", "1.3.6.1.4.1.42.2.27.9.4.39.1");
        LOCALE_NAMES_TO_OIDS.put("en-IN", "1.3.6.1.4.1.42.2.27.9.4.40.1");
        LOCALE_NAMES_TO_OIDS.put("en-MT", "1.3.6.1.4.1.42.2.27.9.4.41.1");
        LOCALE_NAMES_TO_OIDS.put("en-NZ", "1.3.6.1.4.1.42.2.27.9.4.42.1");
        LOCALE_NAMES_TO_OIDS.put("en-PH", "1.3.6.1.4.1.42.2.27.9.4.43.1");
        LOCALE_NAMES_TO_OIDS.put("en-SG", "1.3.6.1.4.1.42.2.27.9.4.44.1");
        LOCALE_NAMES_TO_OIDS.put("en-VI", "1.3.6.1.4.1.42.2.27.9.4.45.1");
        LOCALE_NAMES_TO_OIDS.put("en-ZA", "1.3.6.1.4.1.42.2.27.9.4.46.1");
        LOCALE_NAMES_TO_OIDS.put("en-ZW", "1.3.6.1.4.1.42.2.27.9.4.47.1");
        LOCALE_NAMES_TO_OIDS.put("eo", "1.3.6.1.4.1.42.2.27.9.4.48.1");
        LOCALE_NAMES_TO_OIDS.put("es", "1.3.6.1.4.1.42.2.27.9.4.49.1");
        LOCALE_NAMES_TO_OIDS.put("es-ES", "1.3.6.1.4.1.42.2.27.9.4.49.1");
        LOCALE_NAMES_TO_OIDS.put("es-AR", "1.3.6.1.4.1.42.2.27.9.4.50.1");
        LOCALE_NAMES_TO_OIDS.put("es-BO", "1.3.6.1.4.1.42.2.27.9.4.51.1");
        LOCALE_NAMES_TO_OIDS.put("es-CL", "1.3.6.1.4.1.42.2.27.9.4.52.1");
        LOCALE_NAMES_TO_OIDS.put("es-CO", "1.3.6.1.4.1.42.2.27.9.4.53.1");
        LOCALE_NAMES_TO_OIDS.put("es-CR", "1.3.6.1.4.1.42.2.27.9.4.54.1");
        LOCALE_NAMES_TO_OIDS.put("es-DO", "1.3.6.1.4.1.42.2.27.9.4.55.1");
        LOCALE_NAMES_TO_OIDS.put("es-EC", "1.3.6.1.4.1.42.2.27.9.4.56.1");
        LOCALE_NAMES_TO_OIDS.put("es-GT", "1.3.6.1.4.1.42.2.27.9.4.57.1");
        LOCALE_NAMES_TO_OIDS.put("es-HN", "1.3.6.1.4.1.42.2.27.9.4.58.1");
        LOCALE_NAMES_TO_OIDS.put("es-MX", "1.3.6.1.4.1.42.2.27.9.4.59.1");
        LOCALE_NAMES_TO_OIDS.put("es-NI", "1.3.6.1.4.1.42.2.27.9.4.60.1");
        LOCALE_NAMES_TO_OIDS.put("es-PA", "1.3.6.1.4.1.42.2.27.9.4.61.1");
        LOCALE_NAMES_TO_OIDS.put("es-PE", "1.3.6.1.4.1.42.2.27.9.4.62.1");
        LOCALE_NAMES_TO_OIDS.put("es-PR", "1.3.6.1.4.1.42.2.27.9.4.63.1");
        LOCALE_NAMES_TO_OIDS.put("es-PY", "1.3.6.1.4.1.42.2.27.9.4.64.1");
        LOCALE_NAMES_TO_OIDS.put("es-SV", "1.3.6.1.4.1.42.2.27.9.4.65.1");
        LOCALE_NAMES_TO_OIDS.put("es-US", "1.3.6.1.4.1.42.2.27.9.4.66.1");
        LOCALE_NAMES_TO_OIDS.put("es-UY", "1.3.6.1.4.1.42.2.27.9.4.67.1");
        LOCALE_NAMES_TO_OIDS.put("es-VE", "1.3.6.1.4.1.42.2.27.9.4.68.1");
        LOCALE_NAMES_TO_OIDS.put("et", "1.3.6.1.4.1.42.2.27.9.4.69.1");
        LOCALE_NAMES_TO_OIDS.put("eu", "1.3.6.1.4.1.42.2.27.9.4.70.1");
        LOCALE_NAMES_TO_OIDS.put("fa", "1.3.6.1.4.1.42.2.27.9.4.71.1");
        LOCALE_NAMES_TO_OIDS.put("fa-IN", "1.3.6.1.4.1.42.2.27.9.4.72.1");
        LOCALE_NAMES_TO_OIDS.put("fa-IR", "1.3.6.1.4.1.42.2.27.9.4.73.1");
        LOCALE_NAMES_TO_OIDS.put("fi", "1.3.6.1.4.1.42.2.27.9.4.74.1");
        LOCALE_NAMES_TO_OIDS.put("fo", "1.3.6.1.4.1.42.2.27.9.4.75.1");
        LOCALE_NAMES_TO_OIDS.put("fr", "1.3.6.1.4.1.42.2.27.9.4.76.1");
        LOCALE_NAMES_TO_OIDS.put("fr-FR", "1.3.6.1.4.1.42.2.27.9.4.76.1");
        LOCALE_NAMES_TO_OIDS.put("fr-BE", "1.3.6.1.4.1.42.2.27.9.4.77.1");
        LOCALE_NAMES_TO_OIDS.put("fr-CA", "1.3.6.1.4.1.42.2.27.9.4.78.1");
        LOCALE_NAMES_TO_OIDS.put("fr-CH", "1.3.6.1.4.1.42.2.27.9.4.79.1");
        LOCALE_NAMES_TO_OIDS.put("fr-LU", "1.3.6.1.4.1.42.2.27.9.4.80.1");
        LOCALE_NAMES_TO_OIDS.put("ga", "1.3.6.1.4.1.42.2.27.9.4.81.1");
        LOCALE_NAMES_TO_OIDS.put("gl", "1.3.6.1.4.1.42.2.27.9.4.82.1");
        LOCALE_NAMES_TO_OIDS.put("gu", "1.3.6.1.4.1.42.2.27.9.4.83.1");
        LOCALE_NAMES_TO_OIDS.put("gv", "1.3.6.1.4.1.42.2.27.9.4.84.1");
        LOCALE_NAMES_TO_OIDS.put("he", "1.3.6.1.4.1.42.2.27.9.4.85.1");
        LOCALE_NAMES_TO_OIDS.put("hi", "1.3.6.1.4.1.42.2.27.9.4.86.1");
        LOCALE_NAMES_TO_OIDS.put("hr", "1.3.6.1.4.1.42.2.27.9.4.87.1");
        LOCALE_NAMES_TO_OIDS.put("hu", "1.3.6.1.4.1.42.2.27.9.4.88.1");
        LOCALE_NAMES_TO_OIDS.put("hy", "1.3.6.1.4.1.42.2.27.9.4.89.1");
        LOCALE_NAMES_TO_OIDS.put("id", "1.3.6.1.4.1.42.2.27.9.4.90.1");
        LOCALE_NAMES_TO_OIDS.put("is", "1.3.6.1.4.1.42.2.27.9.4.91.1");
        LOCALE_NAMES_TO_OIDS.put("it", "1.3.6.1.4.1.42.2.27.9.4.92.1");
        LOCALE_NAMES_TO_OIDS.put("it-CH", "1.3.6.1.4.1.42.2.27.9.4.93.1");
        LOCALE_NAMES_TO_OIDS.put("ja", "1.3.6.1.4.1.42.2.27.9.4.94.1");
        LOCALE_NAMES_TO_OIDS.put("kl", "1.3.6.1.4.1.42.2.27.9.4.95.1");
        LOCALE_NAMES_TO_OIDS.put("kn", "1.3.6.1.4.1.42.2.27.9.4.96.1");
        LOCALE_NAMES_TO_OIDS.put("ko", "1.3.6.1.4.1.42.2.27.9.4.97.1");
        LOCALE_NAMES_TO_OIDS.put("kok", "1.3.6.1.4.1.42.2.27.9.4.98.1");
        LOCALE_NAMES_TO_OIDS.put("kw", "1.3.6.1.4.1.42.2.27.9.4.99.1");
        LOCALE_NAMES_TO_OIDS.put("lt", "1.3.6.1.4.1.42.2.27.9.4.100.1");
        LOCALE_NAMES_TO_OIDS.put("lv", "1.3.6.1.4.1.42.2.27.9.4.101.1");
        LOCALE_NAMES_TO_OIDS.put("mk", "1.3.6.1.4.1.42.2.27.9.4.102.1");
        LOCALE_NAMES_TO_OIDS.put("mr", "1.3.6.1.4.1.42.2.27.9.4.103.1");
        LOCALE_NAMES_TO_OIDS.put("mt", "1.3.6.1.4.1.42.2.27.9.4.104.1");
        LOCALE_NAMES_TO_OIDS.put("nl", "1.3.6.1.4.1.42.2.27.9.4.105.1");
        LOCALE_NAMES_TO_OIDS.put("nl-NL", "1.3.6.1.4.1.42.2.27.9.4.105.1");
        LOCALE_NAMES_TO_OIDS.put("nl-BE", "1.3.6.1.4.1.42.2.27.9.4.106.1");
        LOCALE_NAMES_TO_OIDS.put("no", "1.3.6.1.4.1.42.2.27.9.4.107.1");
        LOCALE_NAMES_TO_OIDS.put("no-NO", "1.3.6.1.4.1.42.2.27.9.4.107.1");
        LOCALE_NAMES_TO_OIDS.put("no-NO-NY", "1.3.6.1.4.1.42.2.27.9.4.108.1");
        LOCALE_NAMES_TO_OIDS.put("nn", "1.3.6.1.4.1.42.2.27.9.4.109.1");
        LOCALE_NAMES_TO_OIDS.put("nb", "1.3.6.1.4.1.42.2.27.9.4.110.1");
        LOCALE_NAMES_TO_OIDS.put("no-NO-B", "1.3.6.1.4.1.42.2.27.9.4.110.1");
        LOCALE_NAMES_TO_OIDS.put("om", "1.3.6.1.4.1.42.2.27.9.4.111.1");
        LOCALE_NAMES_TO_OIDS.put("om-ET", "1.3.6.1.4.1.42.2.27.9.4.112.1");
        LOCALE_NAMES_TO_OIDS.put("om-KE", "1.3.6.1.4.1.42.2.27.9.4.113.1");
        LOCALE_NAMES_TO_OIDS.put("pl", "1.3.6.1.4.1.42.2.27.9.4.114.1");
        LOCALE_NAMES_TO_OIDS.put("pt", "1.3.6.1.4.1.42.2.27.9.4.115.1");
        LOCALE_NAMES_TO_OIDS.put("pt-PT", "1.3.6.1.4.1.42.2.27.9.4.115.1");
        LOCALE_NAMES_TO_OIDS.put("pt-BR", "1.3.6.1.4.1.42.2.27.9.4.116.1");
        LOCALE_NAMES_TO_OIDS.put("ro", "1.3.6.1.4.1.42.2.27.9.4.117.1");
        LOCALE_NAMES_TO_OIDS.put("ru", "1.3.6.1.4.1.42.2.27.9.4.118.1");
        LOCALE_NAMES_TO_OIDS.put("ru-RU", "1.3.6.1.4.1.42.2.27.9.4.118.1");
        LOCALE_NAMES_TO_OIDS.put("ru-UA", "1.3.6.1.4.1.42.2.27.9.4.119.1");
        LOCALE_NAMES_TO_OIDS.put("sh", "1.3.6.1.4.1.42.2.27.9.4.120.1");
        LOCALE_NAMES_TO_OIDS.put("sk", "1.3.6.1.4.1.42.2.27.9.4.121.1");
        LOCALE_NAMES_TO_OIDS.put("sl", "1.3.6.1.4.1.42.2.27.9.4.122.1");
        LOCALE_NAMES_TO_OIDS.put("so", "1.3.6.1.4.1.42.2.27.9.4.123.1");
        LOCALE_NAMES_TO_OIDS.put("so-SO", "1.3.6.1.4.1.42.2.27.9.4.123.1");
        LOCALE_NAMES_TO_OIDS.put("so-DJ", "1.3.6.1.4.1.42.2.27.9.4.124.1");
        LOCALE_NAMES_TO_OIDS.put("so-ET", "1.3.6.1.4.1.42.2.27.9.4.125.1");
        LOCALE_NAMES_TO_OIDS.put("so-KE", "1.3.6.1.4.1.42.2.27.9.4.126.1");
        LOCALE_NAMES_TO_OIDS.put("sq", "1.3.6.1.4.1.42.2.27.9.4.127.1");
        LOCALE_NAMES_TO_OIDS.put("sr", "1.3.6.1.4.1.42.2.27.9.4.128.1");
        LOCALE_NAMES_TO_OIDS.put("sv", "1.3.6.1.4.1.42.2.27.9.4.129.1");
        LOCALE_NAMES_TO_OIDS.put("sv-SE", "1.3.6.1.4.1.42.2.27.9.4.129.1");
        LOCALE_NAMES_TO_OIDS.put("sv-FI", "1.3.6.1.4.1.42.2.27.9.4.130.1");
        LOCALE_NAMES_TO_OIDS.put("sw", "1.3.6.1.4.1.42.2.27.9.4.131.1");
        LOCALE_NAMES_TO_OIDS.put("sw-KE", "1.3.6.1.4.1.42.2.27.9.4.132.1");
        LOCALE_NAMES_TO_OIDS.put("sw-TZ", "1.3.6.1.4.1.42.2.27.9.4.133.1");
        LOCALE_NAMES_TO_OIDS.put("ta", "1 3  1.3.6.1.4.1.42.2.27.9.4.134.1");
        LOCALE_NAMES_TO_OIDS.put("te", "1.3.6.1.4.1.42.2.27.9.4.135.1");
        LOCALE_NAMES_TO_OIDS.put("th", "1.3.6.1.4.1.42.2.27.9.4.136.1");
        LOCALE_NAMES_TO_OIDS.put("ti", "1.3.6.1.4.1.42.2.27.9.4.137.1");
        LOCALE_NAMES_TO_OIDS.put("ti-ER", "1.3.6.1.4.1.42.2.27.9.4.138.1");
        LOCALE_NAMES_TO_OIDS.put("ti-ET", "1.3.6.1.4.1.42.2.27.9.4.139.1");
        LOCALE_NAMES_TO_OIDS.put("tr", "1.3.6.1.4.1.42.2.27.9.4.140.1");
        LOCALE_NAMES_TO_OIDS.put("uk", "1.3.6.1.4.1.42.2.27.9.4.141.1");
        LOCALE_NAMES_TO_OIDS.put("vi", "1.3.6.1.4.1.42.2.27.9.4.142.1");
        LOCALE_NAMES_TO_OIDS.put("zh", "1.3.6.1.4.1.42.2.27.9.4.143.1");
        LOCALE_NAMES_TO_OIDS.put("zh-CN", "1.3.6.1.4.1.42.2.27.9.4.144.1");
        LOCALE_NAMES_TO_OIDS.put("zh-HK", "1.3.6.1.4.1.42.2.27.9.4.145.1");
        LOCALE_NAMES_TO_OIDS.put("zh-MO", "1.3.6.1.4.1.42.2.27.9.4.146.1");
        LOCALE_NAMES_TO_OIDS.put("zh-SG", "1.3.6.1.4.1.42.2.27.9.4.147.1");
        LOCALE_NAMES_TO_OIDS.put("zh-TW", "1.3.6.1.4.1.42.2.27.9.4.148.1");

        initializeJvmSupportedLocaleNamesToOids();
    }

    private static void initializeJvmSupportedLocaleNamesToOids() {
        for (Map.Entry<String, String> entry : LOCALE_NAMES_TO_OIDS.entrySet()) {
            final String localeName = entry.getKey();
            final String oid = entry.getValue();
            final String oldLocaleName = new Locale(localeName).toString();

            final int idx = oldLocaleName.indexOf('-');
            if (idx == -1) {
                // no dash, oldLocaleName is lowercase, which is correct for the language tag
                JVM_SUPPORTED_LOCALE_NAMES_TO_OIDS.put(oldLocaleName, oid);
            } else if (oldLocaleName.equalsIgnoreCase(localeName)) {
                // fast path to avoid the string computation in the else clause
                JVM_SUPPORTED_LOCALE_NAMES_TO_OIDS.put(localeName, oid);
            } else {
                // Old locale is different from locale and there are country, and/or variants.
                // Ensure the country and variants are uppercase as in LOCALE_NAMES_TO_OIDS
                // to avoid problems during matching rules initialization.
                // e.g. locale name "zh-SG" is converted to "zh-sg" in old locale name
                final StringBuilder sb = new StringBuilder();
                sb.append(oldLocaleName, 0, idx + 1)
                        .append(oldLocaleName.substring(idx + 1, oldLocaleName.length()).toUpperCase(Locale.ENGLISH));
                JVM_SUPPORTED_LOCALE_NAMES_TO_OIDS.put(sb.toString(), oid);
            }
        }
    }

    /**
     * Prevents construction.
     */
    private CoreSchemaSupportedLocales() {
        // Not used.
    }
}
