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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2013-2015 ForgeRock AS.
 *      Portions copyright 2014 Manuel Gaupp
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.CollationMatchingRulesImpl.*;
import static org.forgerock.opendj.ldap.schema.ObjectClassType.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;
import static org.forgerock.opendj.ldap.schema.TimeBasedMatchingRulesImpl.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

final class CoreSchemaImpl {
    private static final Map<String, List<String>> X500_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("X.500"));

    private static final Map<String, List<String>> RFC2252_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 2252"));
    private static final Map<String, List<String>> RFC3045_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 3045"));
    private static final Map<String, List<String>> RFC3112_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 3112"));
    private static final Map<String, List<String>> RFC4512_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 4512"));
    private static final Map<String, List<String>> RFC4517_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 4517"));
    private static final Map<String, List<String>> RFC4519_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 4519"));
    private static final Map<String, List<String>> RFC4523_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 4523"));
    private static final Map<String, List<String>> RFC4530_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 4530"));
    private static final Map<String, List<String>> RFC5020_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 5020"));

    static final Map<String, List<String>> OPENDS_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("OpenDS Directory Server"));
    private static final Map<String, List<String>> OPENDJ_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("OpenDJ Directory Server"));

    private static final String EMPTY_STRING = "".intern();

    private static final Schema SINGLETON;

    /**
     * Provides the oid associated to each locale, for the registration of collation matching rules.
     * <p>
     * To add support for a new locale to collation matching rules, add its name as key and the corresponding oid as
     * value.
     */
    private static final Map<String, String> LOCALE_NAMES_TO_OIDS = new HashMap<String, String>();
    /**
     * Same as {@link CoreSchemaImpl#LOCALE_NAMES_TO_OIDS}, but it contains the old locale names
     * that will be used for the registration of collation matching rules.
     * It is automatically populated on static initialization of current class.
     * It allows the initialization process to complete when newer locales are referenced by config.ldif,
     * but the JVM only works with the old locale names.
     *
     * @see CoreSchemaImpl#LOCALE_NAMES_TO_OIDS
     */
    private static final Map<String, String> JVM_SUPPORTED_LOCALE_NAMES_TO_OIDS = new HashMap<String, String>();

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
        for (Entry<String, String> entry : LOCALE_NAMES_TO_OIDS.entrySet()) {
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

    static {
        final SchemaBuilder builder = new SchemaBuilder("Core Schema");
        defaultSyntaxes(builder);
        defaultMatchingRules(builder);
        defaultAttributeTypes(builder);
        defaultObjectClasses(builder);

        addRFC4519(builder);
        addRFC4523(builder);
        addRFC4530(builder);
        addRFC3045(builder);
        addRFC3112(builder);
        addRFC5020(builder);
        addSunProprietary(builder);
        addForgeRockProprietary(builder);

        SINGLETON = builder.toSchema().asNonStrictSchema();
    }

    static Schema getInstance() {
        return SINGLETON;
    }

    private static void addRFC3045(final SchemaBuilder builder) {
        builder.addAttributeType("1.3.6.1.1.4", Collections.singletonList("vendorName"),
                EMPTY_STRING, false, null, EMR_CASE_EXACT_IA5_OID, null, null, null,
                SYNTAX_DIRECTORY_STRING_OID, true, false, true, AttributeUsage.DSA_OPERATION,
                RFC3045_ORIGIN, false);

        builder.addAttributeType("1.3.6.1.1.5", Collections.singletonList("vendorVersion"),
                EMPTY_STRING, false, null, EMR_CASE_EXACT_IA5_OID, null, null, null,
                SYNTAX_DIRECTORY_STRING_OID, true, false, true, AttributeUsage.DSA_OPERATION,
                RFC3045_ORIGIN, false);
    }

    private static void addRFC3112(final SchemaBuilder builder) {
        builder.buildSyntax(SYNTAX_AUTH_PASSWORD_OID).description(SYNTAX_AUTH_PASSWORD_DESCRIPTION)
                .extraProperties(RFC3112_ORIGIN).implementation(new AuthPasswordSyntaxImpl()).addToSchema();
        builder.buildMatchingRule(EMR_AUTH_PASSWORD_EXACT_OID)
                .names(EMR_AUTH_PASSWORD_EXACT_NAME)
                .description(EMR_AUTH_PASSWORD_EXACT_DESCRIPTION).syntaxOID(SYNTAX_AUTH_PASSWORD_OID)
                .extraProperties(RFC3112_ORIGIN).implementation(new AuthPasswordExactEqualityMatchingRuleImpl())
                .addToSchema();
        builder.addAttributeType("1.3.6.1.4.1.4203.1.3.3", Collections
                .singletonList("supportedAuthPasswordSchemes"),
                "supported password storage schemes", false, null, EMR_CASE_EXACT_IA5_OID, null,
                null, null, SYNTAX_IA5_STRING_OID, false, false, false,
                AttributeUsage.DSA_OPERATION, RFC3112_ORIGIN, false);
        builder.addAttributeType("1.3.6.1.4.1.4203.1.3.4", Collections
                .singletonList("authPassword"), "password authentication information", false, null,
                EMR_AUTH_PASSWORD_EXACT_OID, null, null, null, SYNTAX_AUTH_PASSWORD_OID, false,
                false, false, AttributeUsage.USER_APPLICATIONS, RFC3112_ORIGIN, false);
        builder.buildObjectClass("1.3.6.1.4.1.4203.1.4.7")
                .names("authPasswordObject")
                .type(AUXILIARY)
                .description("authentication password mix in class")
                .optionalAttributes("authPassword")
                .extraProperties(RFC3112_ORIGIN)
                .addToSchema();
    }

    private static void addRFC4519(final SchemaBuilder builder) {
        builder.addAttributeType("2.5.4.15", Collections.singletonList("businessCategory"),
                EMPTY_STRING, false, null, EMR_CASE_IGNORE_OID, null, SMR_CASE_IGNORE_OID, null,
                SYNTAX_DIRECTORY_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.41", Collections.singletonList("name"), EMPTY_STRING,
                false, null, EMR_CASE_IGNORE_OID, null, SMR_CASE_IGNORE_OID, null,
                SYNTAX_DIRECTORY_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.6", Arrays.asList("c", "countryName"), EMPTY_STRING, false,
                "name", null, null, null, null, SYNTAX_COUNTRY_STRING_OID, true, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.3", Arrays.asList("cn", "commonName"), EMPTY_STRING, false,
                "name", null, null, null, null, null, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("0.9.2342.19200300.100.1.25", Arrays.asList("dc",
                "domainComponent"), EMPTY_STRING, false, null, EMR_CASE_IGNORE_IA5_OID, null,
                SMR_CASE_IGNORE_IA5_OID, null, SYNTAX_IA5_STRING_OID, true, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.13", Collections.singletonList("description"),
                EMPTY_STRING, false, null, EMR_CASE_IGNORE_OID, null, SMR_CASE_IGNORE_OID, null,
                SYNTAX_DIRECTORY_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.27", Collections.singletonList("destinationIndicator"),
                EMPTY_STRING, false, null, EMR_CASE_IGNORE_OID, null, SMR_CASE_IGNORE_OID, null,
                SYNTAX_PRINTABLE_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.49", Collections.singletonList("distinguishedName"),
                EMPTY_STRING, false, null, EMR_DN_OID, null, null, null, SYNTAX_DN_OID, false,
                false, false, AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.46", Collections.singletonList("dnQualifier"),
                EMPTY_STRING, false, null, EMR_CASE_IGNORE_OID, OMR_CASE_IGNORE_OID,
                SMR_CASE_IGNORE_OID, null, SYNTAX_PRINTABLE_STRING_OID, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.47", Collections.singletonList("enhancedSearchGuide"),
                EMPTY_STRING, false, null, null, null, null, null, SYNTAX_ENHANCED_GUIDE_OID,
                false, false, false, AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.23", Collections.singletonList("facsimileTelephoneNumber"),
                EMPTY_STRING, false, null, null, null, null, null, SYNTAX_FAXNUMBER_OID, false,
                false, false, AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.44", Collections.singletonList("generationQualifier"),
                EMPTY_STRING, false, "name", null, null, null, null, null, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.42", Collections.singletonList("givenName"), EMPTY_STRING,
                false, "name", null, null, null, null, null, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.51", Collections.singletonList("houseIdentifier"),
                EMPTY_STRING, false, null, EMR_CASE_IGNORE_OID, null, SMR_CASE_IGNORE_OID, null,
                SYNTAX_DIRECTORY_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.43", Collections.singletonList("initials"), EMPTY_STRING,
                false, "name", null, null, null, null, null, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.25", Collections.singletonList("internationalISDNNumber"),
                EMPTY_STRING, false, null, EMR_NUMERIC_STRING_OID, null, SMR_NUMERIC_STRING_OID,
                null, SYNTAX_NUMERIC_STRING_OID, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.7", Arrays.asList("l", "localityName"), EMPTY_STRING,
                false, "name", null, null, null, null, null, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.31", Collections.singletonList("member"), EMPTY_STRING,
                false, "distinguishedName", null, null, null, null, null, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.10", Arrays.asList("o", "organizationName"), EMPTY_STRING,
                false, null, EMR_CASE_IGNORE_OID, null, SMR_CASE_IGNORE_OID, null,
                SYNTAX_DIRECTORY_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.11", Arrays.asList("ou", "organizationalUnitName"),
                EMPTY_STRING, false, null, EMR_CASE_IGNORE_OID, null, SMR_CASE_IGNORE_OID, null,
                SYNTAX_DIRECTORY_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.32", Collections.singletonList("owner"), EMPTY_STRING,
                false, "distinguishedName", null, null, null, null, null, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.19", Collections
                .singletonList("physicalDeliveryOfficeName"), EMPTY_STRING, false, null,
                EMR_CASE_IGNORE_OID, null, SMR_CASE_IGNORE_OID, null, SYNTAX_DIRECTORY_STRING_OID,
                false, false, false, AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.16", Collections.singletonList("postalAddress"),
                EMPTY_STRING, false, null, EMR_CASE_IGNORE_OID, null, SMR_CASE_IGNORE_OID, null,
                SYNTAX_DIRECTORY_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.17", Collections.singletonList("postalCode"), EMPTY_STRING,
                false, null, EMR_CASE_IGNORE_OID, null, SMR_CASE_IGNORE_OID, null,
                SYNTAX_DIRECTORY_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.18", Collections.singletonList("postOfficeBox"),
                EMPTY_STRING, false, null, EMR_CASE_IGNORE_OID, null, SMR_CASE_IGNORE_OID, null,
                SYNTAX_DIRECTORY_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.28", Collections.singletonList("preferredDeliveryMethod"),
                EMPTY_STRING, false, null, null, null, null, null, SYNTAX_DELIVERY_METHOD_OID,
                true, false, false, AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.26", Collections.singletonList("registeredAddress"),
                EMPTY_STRING, false, "postalAddress", null, null, null, null,
                SYNTAX_POSTAL_ADDRESS_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.33", Collections.singletonList("roleOccupant"),
                EMPTY_STRING, false, "distinguishedName", null, null, null, null, null, false,
                false, false, AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.14", Collections.singletonList("searchGuide"),
                EMPTY_STRING, false, null, null, null, null, null, SYNTAX_GUIDE_OID, false, false,
                false, AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.34", Collections.singletonList("seeAlso"), EMPTY_STRING,
                false, "distinguishedName", null, null, null, null, null, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.5", Collections.singletonList("serialNumber"),
                EMPTY_STRING, false, null, EMR_CASE_IGNORE_OID, null, SMR_CASE_IGNORE_OID, null,
                SYNTAX_PRINTABLE_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.4", Arrays.asList("sn", "surname"), EMPTY_STRING, false,
                "name", null, null, null, null, null, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.8", Arrays.asList("st", "stateOrProvinceName"),
                EMPTY_STRING, false, "name", null, null, null, null, null, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.9", Arrays.asList("street", "streetAddress"), EMPTY_STRING,
                false, null, EMR_CASE_IGNORE_OID, null, SMR_CASE_IGNORE_OID, null,
                SYNTAX_DIRECTORY_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.20", Collections.singletonList("telephoneNumber"),
                EMPTY_STRING, false, null, EMR_TELEPHONE_OID, null, SMR_TELEPHONE_OID, null,
                SYNTAX_TELEPHONE_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.22",
                Collections.singletonList("teletexTerminalIdentifier"), EMPTY_STRING, false, null,
                null, null, null, null, SYNTAX_TELETEX_TERM_ID_OID, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.21", Collections.singletonList("telexNumber"),
                EMPTY_STRING, false, null, null, null, null, null, SYNTAX_TELEX_OID, false, false,
                false, AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.12", Collections.singletonList("title"), EMPTY_STRING,
                false, "name", null, null, null, null, null, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("0.9.2342.19200300.100.1.1", Arrays.asList("uid", "userid"),
                EMPTY_STRING, false, null, EMR_CASE_IGNORE_OID, null, SMR_CASE_IGNORE_OID, null,
                SYNTAX_DIRECTORY_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.50", Collections.singletonList("uniqueMember"),
                EMPTY_STRING, false, null, EMR_UNIQUE_MEMBER_OID, null, null, null,
                SYNTAX_NAME_AND_OPTIONAL_UID_OID, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.35", Collections.singletonList("userPassword"),
                EMPTY_STRING, false, null, EMR_OCTET_STRING_OID, null, null, null,
                SYNTAX_OCTET_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.24", Collections.singletonList("x121Address"),
                EMPTY_STRING, false, null, EMR_NUMERIC_STRING_OID, null, SMR_NUMERIC_STRING_OID,
                null, SYNTAX_NUMERIC_STRING_OID, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4519_ORIGIN, false);

        builder.addAttributeType("2.5.4.45", Collections.singletonList("x500UniqueIdentifier"),
                EMPTY_STRING, false, null, EMR_BIT_STRING_OID, null, null, null,
                SYNTAX_BIT_STRING_OID, false, false, false, AttributeUsage.USER_APPLICATIONS,
                RFC4519_ORIGIN, false);

        builder.buildObjectClass("2.5.6.11")
                .names("applicationProcess")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("cn")
                .optionalAttributes("seeAlso", "ou", "l", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.2")
                .names("country")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("c")
                .optionalAttributes("searchGuide", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("1.3.6.1.4.1.1466.344")
                .names("dcObject")
                .type(AUXILIARY)
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("dc")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.14")
                .names("device")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("cn")
                .optionalAttributes("serialNumber", "seeAlso", "owner", "ou", "o", "l", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.9")
                .names("groupOfNames")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("member", "cn")
                .optionalAttributes("businessCategory", "seeAlso", "owner", "ou", "o", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.17")
                .names("groupOfUniqueNames")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("member", "cn")
                .optionalAttributes("businessCategory", "seeAlso", "owner", "ou", "o", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.3")
                .names("locality")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .optionalAttributes("street", "seeAlso", "searchGuide", "st", "l", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.4")
                .names("organization")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("o")
                .optionalAttributes("userPassword", "searchGuide", "seeAlso", "businessCategory", "x121Address",
                        "registeredAddress", "destinationIndicator", "preferredDeliveryMethod", "telexNumber",
                        "teletexTerminalIdentifier", "telephoneNumber", "internationalISDNNumber",
                        "facsimileTelephoneNumber", "street", "postOfficeBox", "postalCode", "postalAddress",
                        "physicalDeliveryOfficeName", "st", "l", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.7")
                .names("organizationalPerson")
                .superiorObjectClasses("person")
                .optionalAttributes("title", "x121Address", "registeredAddress", "destinationIndicator",
                        "preferredDeliveryMethod", "telexNumber", "teletexTerminalIdentifier", "telephoneNumber",
                        "internationalISDNNumber", "facsimileTelephoneNumber", "street", "postOfficeBox",
                        "postalCode", "postalAddress", "physicalDeliveryOfficeName", "ou", "st", "l")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.8")
                .names("organizationalRole")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("cn")
                .optionalAttributes("x121Address", "registeredAddress", "destinationIndicator",
                        "preferredDeliveryMethod", "telexNumber", "teletexTerminalIdentifier", "telephoneNumber",
                        "internationalISDNNumber", "facsimileTelephoneNumber", "seeAlso", "roleOccupant",
                        "preferredDeliveryMethod", "street", "postOfficeBox", "postalCode", "postalAddress",
                        "physicalDeliveryOfficeName", "ou", "st", "l", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.5")
                .names("organizationalUnit")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("ou")
                .optionalAttributes("businessCategory", "description", "destinationIndicator",
                        "facsimileTelephoneNumber", "internationalISDNNumber", "l", "physicalDeliveryOfficeName",
                        "postalAddress", "postalCode", "postOfficeBox", "preferredDeliveryMethod",
                        "registeredAddress", "searchGuide", "seeAlso", "st", "street", "telephoneNumber",
                        "teletexTerminalIdentifier", "telexNumber", "userPassword", "x121Address")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.6")
                .names("person")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("sn", "cn")
                .optionalAttributes("userPassword", "telephoneNumber", "destinationIndicator", "seeAlso", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.10")
                .names("residentialPerson")
                .superiorObjectClasses("person")
                .requiredAttributes("l")
                .optionalAttributes("businessCategory", "x121Address", "registeredAddress", "destinationIndicator",
                        "preferredDeliveryMethod", "telexNumber", "teletexTerminalIdentifier", "telephoneNumber",
                        "internationalISDNNumber", "facsimileTelephoneNumber", "preferredDeliveryMethod", "street",
                        "postOfficeBox", "postalCode", "postalAddress", "physicalDeliveryOfficeName", "st", "l")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("1.3.6.1.1.3.1")
                .names("uidObject")
                .type(AUXILIARY)
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("uid")
                .optionalAttributes("businessCategory", "x121Address", "registeredAddress", "destinationIndicator",
                        "preferredDeliveryMethod", "telexNumber", "teletexTerminalIdentifier", "telephoneNumber",
                        "internationalISDNNumber", "facsimileTelephoneNumber", "preferredDeliveryMethod", "street",
                        "postOfficeBox", "postalCode", "postalAddress", "physicalDeliveryOfficeName", "st", "l")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();
    }

    private static void addRFC4523(final SchemaBuilder builder) {
        builder.buildSyntax(SYNTAX_CERTLIST_OID).description(SYNTAX_CERTLIST_DESCRIPTION)
                .extraProperties(RFC4523_ORIGIN).implementation(new CertificateListSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_CERTPAIR_OID).description(SYNTAX_CERTPAIR_DESCRIPTION)
                .extraProperties(RFC4523_ORIGIN).implementation(new CertificatePairSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_CERTIFICATE_OID).description(SYNTAX_CERTIFICATE_DESCRIPTION)
                .extraProperties(RFC4523_ORIGIN).implementation(new CertificateSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_CERTIFICATE_EXACT_ASSERTION_OID)
                .description(SYNTAX_CERTIFICATE_EXACT_ASSERTION_DESCRIPTION).extraProperties(RFC4523_ORIGIN)
                .implementation(new CertificateExactAssertionSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_SUPPORTED_ALGORITHM_OID).description(SYNTAX_SUPPORTED_ALGORITHM_DESCRIPTION)
                .extraProperties(RFC4523_ORIGIN).implementation(new SupportedAlgorithmSyntaxImpl()).addToSchema();

        builder.buildMatchingRule(EMR_CERTIFICATE_EXACT_OID).names(EMR_CERTIFICATE_EXACT_NAME)
                .syntaxOID(SYNTAX_CERTIFICATE_EXACT_ASSERTION_OID).extraProperties(RFC4523_ORIGIN)
                .implementation(new CertificateExactMatchingRuleImpl()).addToSchema();

        builder.addAttributeType("2.5.4.36", Collections.singletonList("userCertificate"),
                "X.509 user certificate", false, null, EMR_CERTIFICATE_EXACT_OID, null,
                null, null, SYNTAX_CERTIFICATE_OID, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4523_ORIGIN, false);
        builder.addAttributeType("2.5.4.37", Collections.singletonList("cACertificate"),
                "X.509 CA certificate", false, null, EMR_CERTIFICATE_EXACT_OID, null,
                null, null, SYNTAX_CERTIFICATE_OID, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4523_ORIGIN, false);
        builder.addAttributeType("2.5.4.38", Collections.singletonList("authorityRevocationList"),
                "X.509 authority revocation list", false, null, EMR_OCTET_STRING_OID, null,
                null, null, SYNTAX_CERTLIST_OID, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4523_ORIGIN, false);
        builder.addAttributeType("2.5.4.39", Collections.singletonList("certificateRevocationList"),
                "X.509 certificate revocation list", false, null, EMR_OCTET_STRING_OID, null,
                null, null, SYNTAX_CERTLIST_OID, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4523_ORIGIN, false);
        builder.addAttributeType("2.5.4.40", Collections.singletonList("crossCertificatePair"),
                "X.509 cross certificate pair", false, null, EMR_OCTET_STRING_OID, null,
                null, null, SYNTAX_CERTPAIR_OID, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4523_ORIGIN, false);
        builder.addAttributeType("2.5.4.52", Collections.singletonList("supportedAlgorithms"),
                "X.509 supported algorithms", false, null, EMR_OCTET_STRING_OID, null,
                null, null, SYNTAX_SUPPORTED_ALGORITHM_OID, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4523_ORIGIN, false);
        builder.addAttributeType("2.5.4.53", Collections.singletonList("deltaRevocationList"),
                "X.509 delta revocation list", false, null, EMR_OCTET_STRING_OID, null,
                null, null, SYNTAX_CERTLIST_OID, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4523_ORIGIN, false);

        builder.buildObjectClass("2.5.6.21")
                .names("pkiUser")
                .type(AUXILIARY)
                .description("X.509 PKI User")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .optionalAttributes("userCertificate")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.22")
                .names("pkiCA")
                .type(AUXILIARY)
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .description("X.509 PKI Certificate Authority")
                .optionalAttributes("cACertificate", "certificateRevocationList",
                    "authorityRevocationList", "crossCertificatePair")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.19")
                .names("cRLDistributionPoint")
                .description("X.509 CRL distribution point")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("cn")
                .optionalAttributes("certificateRevocationList", "authorityRevocationList", "deltaRevocationList")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.23")
                .names("deltaCRL")
                .type(AUXILIARY)
                .description("X.509 delta CRL")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .optionalAttributes("deltaRevocationList")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.15")
                .names("strongAuthenticationUser")
                .type(AUXILIARY)
                .description("X.521 strong authentication user")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("userCertificate")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.18")
                .names("userSecurityInformation")
                .type(AUXILIARY)
                .description("X.521 user security information")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .optionalAttributes("supportedAlgorithms")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.16")
                .names("certificationAuthority")
                .type(AUXILIARY)
                .description("X.509 certificate authority")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("authorityRevocationList", "certificateRevocationList", "cACertificate")
                .optionalAttributes("crossCertificatePair")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.16.2")
                .names("certificationAuthority-V2")
                .type(AUXILIARY)
                .description("X.509 certificate authority, version 2")
                .superiorObjectClasses("certificationAuthority")
                .optionalAttributes("deltaRevocationList")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();
    }

    private static void addRFC4530(final SchemaBuilder builder) {
        builder.buildSyntax(SYNTAX_UUID_OID).description(SYNTAX_UUID_DESCRIPTION).extraProperties(RFC4530_ORIGIN)
                .implementation(new UUIDSyntaxImpl()).addToSchema();
        builder.buildMatchingRule(EMR_UUID_OID).names(EMR_UUID_NAME).syntaxOID(SYNTAX_UUID_OID)
                .extraProperties(RFC4530_ORIGIN).implementation(new UUIDEqualityMatchingRuleImpl())
                .addToSchema();
        builder.buildMatchingRule(OMR_UUID_OID).names(OMR_UUID_NAME).syntaxOID(SYNTAX_UUID_OID)
                .extraProperties(RFC4530_ORIGIN).implementation(new UUIDOrderingMatchingRuleImpl())
                .addToSchema();
        builder.addAttributeType("1.3.6.1.1.16.4", Collections.singletonList("entryUUID"),
                "UUID of the entry", false, null, EMR_UUID_OID, OMR_UUID_OID, null, null,
                SYNTAX_UUID_OID, true, false, true, AttributeUsage.DIRECTORY_OPERATION,
                RFC4530_ORIGIN, false);
    }

    private static void addRFC5020(final SchemaBuilder builder) {
        builder.addAttributeType("1.3.6.1.1.20", Collections.singletonList("entryDN"),
                "DN of the entry", false, null, EMR_DN_OID, null, null, null,
                SYNTAX_DN_OID, true, false, true, AttributeUsage.DIRECTORY_OPERATION,
                RFC5020_ORIGIN, false);
    }

    private static void addSunProprietary(final SchemaBuilder builder) {
        builder.buildSyntax(SYNTAX_USER_PASSWORD_OID).description(SYNTAX_USER_PASSWORD_DESCRIPTION)
                .extraProperties(OPENDS_ORIGIN).implementation(new UserPasswordSyntaxImpl()).addToSchema();
        builder.buildMatchingRule(EMR_USER_PASSWORD_EXACT_OID)
                .names(Collections.singletonList(EMR_USER_PASSWORD_EXACT_NAME))
                .description(EMR_USER_PASSWORD_EXACT_DESCRIPTION).syntaxOID(SYNTAX_USER_PASSWORD_OID)
                .extraProperties(OPENDS_ORIGIN).implementation(new UserPasswordExactEqualityMatchingRuleImpl())
                .addToSchema();
        builder.buildMatchingRule(AMR_DOUBLE_METAPHONE_OID).names(Collections.singletonList(AMR_DOUBLE_METAPHONE_NAME))
                .description(AMR_DOUBLE_METAPHONE_DESCRIPTION).syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
                .extraProperties(OPENDS_ORIGIN).implementation(new DoubleMetaphoneApproximateMatchingRuleImpl())
                .addToSchema();
        builder.buildMatchingRule(OMR_RELATIVE_TIME_GREATER_THAN_OID)
                .names(OMR_RELATIVE_TIME_GREATER_THAN_NAME, OMR_RELATIVE_TIME_GREATER_THAN_ALT_NAME)
                .description(OMR_RELATIVE_TIME_GREATER_THAN_DESCRIPTION).syntaxOID(SYNTAX_GENERALIZED_TIME_OID)
                .extraProperties(OPENDS_ORIGIN).implementation(relativeTimeGTOMatchingRule())
                .addToSchema();
        builder.buildMatchingRule(OMR_RELATIVE_TIME_LESS_THAN_OID)
                .names(OMR_RELATIVE_TIME_LESS_THAN_NAME, OMR_RELATIVE_TIME_LESS_THAN_ALT_NAME)
                .description(OMR_RELATIVE_TIME_LESS_THAN_DESCRIPTION).syntaxOID(SYNTAX_GENERALIZED_TIME_OID)
                .extraProperties(OPENDS_ORIGIN).implementation(relativeTimeLTOMatchingRule())
                .addToSchema();
        builder.buildMatchingRule(MR_PARTIAL_DATE_AND_TIME_OID)
                .names(Collections.singletonList(MR_PARTIAL_DATE_AND_TIME_NAME))
                .description(MR_PARTIAL_DATE_AND_TIME_DESCRIPTION).syntaxOID(SYNTAX_GENERALIZED_TIME_OID)
                .extraProperties(OPENDS_ORIGIN).implementation(partialDateAndTimeMatchingRule())
                .addToSchema();
        addCollationMatchingRules(builder);
    }

    private static void addForgeRockProprietary(SchemaBuilder builder) {
        builder.addAttributeType("1.3.6.1.4.1.36733.2.1.1.141", Collections.singletonList("fullVendorVersion"),
                EMPTY_STRING, false, null, EMR_CASE_EXACT_IA5_OID, null, null, null,
                SYNTAX_DIRECTORY_STRING_OID, true, false, true, AttributeUsage.DSA_OPERATION,
                OPENDJ_ORIGIN , false);
    }

    /**
     * Adds the collation matching rules.
     * <p>
     * A set of collation matching rules is registered for each locale that is both available in the java runtime
     * environment and has an oid defined in the {@code LOCALE_NAMES_TO_OIDS} map. Note that the same oid can be used
     * for multiple locales (e.g., matching rule for "en" and "en-US" uses the same oid).
     * <p>
     * To add support for a new locale, add a corresponding entry in the {@code LOCALE_NAMES_TO_OIDS} map.
     */
    private static void addCollationMatchingRules(final SchemaBuilder builder) {
        // Build an intermediate map to ensure each locale name appears only once
        final Map<String, Locale> localesCache = new TreeMap<String, Locale>();
        for (Locale locale : Locale.getAvailableLocales()) {
            localesCache.put(localeName(locale), locale);
        }

        // Build a intermediate map to list all available oids with their locale names
        // An oid can be associated to multiple locale names
        final Map<String, List<String>> oidsCache = new HashMap<String, List<String>>();
        for (final String localeName: localesCache.keySet()) {
            String oid = JVM_SUPPORTED_LOCALE_NAMES_TO_OIDS.get(localeName);
            if (oid != null) {
                List<String> names = oidsCache.get(oid);
                if (names == null) {
                    names = new ArrayList<String>(5);
                    oidsCache.put(oid, names);
                }
                names.add(localeName);
            }
        }

        // Now build the matching rules from all available oids
        for (final Entry<String, List<String>> entry : oidsCache.entrySet()) {
            final String oid = entry.getKey();
            final List<String> names = entry.getValue();
            // take locale from first name - all locales of names are considered equivalent here
            final Locale locale = localesCache.get(names.get(0));
            addCollationMatchingRule(builder, oid, names, 1, "lt", collationLessThanMatchingRule(locale));
            addCollationMatchingRule(builder, oid, names, 2, "lte", collationLessThanOrEqualMatchingRule(locale));
            MatchingRuleImpl collationEqualityMatchingRule = collationEqualityMatchingRule(locale);
            addCollationMatchingRule(builder, oid, names, 3, "eq", collationEqualityMatchingRule);
            // the default oid is registered with equality matching rule
            final int ignored = 0;
            addCollationMatchingRule(builder, oid, names, ignored, "", collationEqualityMatchingRule);
            addCollationMatchingRule(builder, oid, names, 4, "gte", collationGreaterThanOrEqualToMatchingRule(locale));
            addCollationMatchingRule(builder, oid, names, 5, "gt", collationGreaterThanMatchingRule(locale));
            addCollationMatchingRule(builder, oid, names, 6, "sub", collationSubstringMatchingRule(locale));
        }
    }

    /** Add a specific collation matching rule to the schema. */
    private static void addCollationMatchingRule(final SchemaBuilder builder, final String baseOid,
            final List<String> names, final int numericSuffix, final String symbolicSuffix,
            final MatchingRuleImpl matchingRuleImplementation) {
        final String oid = symbolicSuffix.isEmpty() ? baseOid : baseOid + "." + numericSuffix;
        builder.buildMatchingRule(oid)
            .names(collationMatchingRuleNames(names, numericSuffix, symbolicSuffix))
            .syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
            .extraProperties(OPENDS_ORIGIN)
            .implementation(matchingRuleImplementation)
            .addToSchema();
    }

    /**
     * Build the complete list of names for a collation matching rule.
     *
     * @param localeNames
     *            List of locale names that correspond to the matching rule (e.g., "en", "en-US")
     * @param numSuffix
     *            numeric suffix corresponding to type of matching rule (e.g, 5 for greater than matching rule). It is
     *            ignored if equal to zero (0).
     * @param symbolicSuffix
     *            symbolic suffix corresponding to type of matching rule (e.g, "gt" for greater than matching rule). It
     *            may be empty ("") to indicate the default rule.
     * @return the names list (e.g, "en.5", "en.gt", "en-US.5", "en-US.gt")
     */
    private static String[] collationMatchingRuleNames(final List<String> localeNames, final int numSuffix,
        final String symbolicSuffix) {
        final List<String> names = new ArrayList<String>();
        for (String localeName : localeNames) {
            if (symbolicSuffix.isEmpty()) {
                // the default rule
                names.add(localeName);
            } else {
                names.add(localeName + "." + numSuffix);
                names.add(localeName + "." + symbolicSuffix);
            }
        }
        return names.toArray(new String[names.size()]);
    }

    /**
     * Returns the name corresponding to the provided locale.
     * <p>
     * The name is using format:
     * <pre>
     *   language code (lower case) + "-" + country code (upper case) + "-" + variant (upper case)
     * </pre>
     * Country code and variant are optional, they may not appear.
     * See LOCALE_NAMES_TO_OIDS keys for examples of names
     *
     * @param locale
     *          The locale
     * @return the name associated to the locale
     */
    private static String localeName(final Locale locale) {
        final StringBuilder name = new StringBuilder(locale.getLanguage());
        final String country = locale.getCountry();
        if (!country.isEmpty()) {
            name.append('-').append(country);
        }
        final String variant = locale.getVariant();
        if (!variant.isEmpty()) {
            name.append('-').append(variant.toUpperCase());
        }
        return name.toString();
    }

    private static void defaultAttributeTypes(final SchemaBuilder builder) {
        builder.addAttributeType("2.5.4.0", Collections.singletonList("objectClass"), EMPTY_STRING,
                false, null, EMR_OID_NAME, null, null, null, SYNTAX_OID_OID, false, false, false,
                AttributeUsage.USER_APPLICATIONS, RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.4.1", Collections.singletonList("aliasedObjectName"),
                EMPTY_STRING, false, null, EMR_DN_NAME, null, null, null, SYNTAX_DN_OID, true,
                false, false, AttributeUsage.DIRECTORY_OPERATION, RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.18.1", Collections.singletonList("createTimestamp"),
                EMPTY_STRING, false, null, EMR_GENERALIZED_TIME_NAME, OMR_GENERALIZED_TIME_NAME,
                null, null, SYNTAX_GENERALIZED_TIME_OID, true, false, true,
                AttributeUsage.DIRECTORY_OPERATION, RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.18.2", Collections.singletonList("modifyTimestamp"),
                EMPTY_STRING, false, null, EMR_GENERALIZED_TIME_NAME, OMR_GENERALIZED_TIME_NAME,
                null, null, SYNTAX_GENERALIZED_TIME_OID, true, false, true,
                AttributeUsage.DIRECTORY_OPERATION, RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.18.3", Collections.singletonList("creatorsName"),
                EMPTY_STRING, false, null, EMR_DN_NAME, null, null, null, SYNTAX_DN_OID, true,
                false, true, AttributeUsage.DIRECTORY_OPERATION, RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.18.4", Collections.singletonList("modifiersName"),
                EMPTY_STRING, false, null, EMR_DN_NAME, null, null, null, SYNTAX_DN_OID, true,
                false, true, AttributeUsage.DIRECTORY_OPERATION, RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.18.10", Collections.singletonList("subschemaSubentry"),
                EMPTY_STRING, false, null, EMR_DN_NAME, null, null, null, SYNTAX_DN_OID, true,
                false, true, AttributeUsage.DIRECTORY_OPERATION, RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.21.5", Collections.singletonList("attributeTypes"),
                EMPTY_STRING, false, null, EMR_OID_FIRST_COMPONENT_NAME, null, null, null,
                SYNTAX_ATTRIBUTE_TYPE_OID, false, false, false, AttributeUsage.DIRECTORY_OPERATION,
                RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.21.6", Collections.singletonList("objectClasses"),
                EMPTY_STRING, false, null, EMR_OID_FIRST_COMPONENT_NAME, null, null, null,
                SYNTAX_OBJECTCLASS_OID, false, false, false, AttributeUsage.DIRECTORY_OPERATION,
                RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.21.4", Collections.singletonList("matchingRules"),
                EMPTY_STRING, false, null, EMR_OID_FIRST_COMPONENT_NAME, null, null, null,
                SYNTAX_MATCHING_RULE_OID, false, false, false, AttributeUsage.DIRECTORY_OPERATION,
                RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.21.8", Collections.singletonList("matchingRuleUse"),
                EMPTY_STRING, false, null, EMR_OID_FIRST_COMPONENT_NAME, null, null, null,
                SYNTAX_MATCHING_RULE_USE_OID, false, false, false,
                AttributeUsage.DIRECTORY_OPERATION, RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.21.9", Collections.singletonList("structuralObjectClass"),
                EMPTY_STRING, false, null, EMR_OID_NAME, null, null, null, SYNTAX_OID_OID, true,
                false, true, AttributeUsage.DIRECTORY_OPERATION, RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.21.10", Collections.singletonList("governingStructureRule"),
                EMPTY_STRING, false, null, EMR_INTEGER_NAME, null, null, null, SYNTAX_INTEGER_OID,
                true, false, true, AttributeUsage.DIRECTORY_OPERATION, RFC4512_ORIGIN, false);

        builder.addAttributeType("1.3.6.1.4.1.1466.101.120.5", Collections
                .singletonList("namingContexts"), EMPTY_STRING, false, null, null, null, null,
                null, SYNTAX_DN_OID, false, false, false, AttributeUsage.DSA_OPERATION,
                RFC4512_ORIGIN, false);

        builder.addAttributeType("1.3.6.1.4.1.1466.101.120.6", Collections
                .singletonList("altServer"), EMPTY_STRING, false, null, null, null, null, null,
                SYNTAX_IA5_STRING_OID, false, false, false, AttributeUsage.DSA_OPERATION,
                RFC4512_ORIGIN, false);

        builder.addAttributeType("1.3.6.1.4.1.1466.101.120.7", Collections
                .singletonList("supportedExtension"), EMPTY_STRING, false, null, null, null, null,
                null, SYNTAX_OID_OID, false, false, false, AttributeUsage.DSA_OPERATION,
                RFC4512_ORIGIN, false);

        builder.addAttributeType("1.3.6.1.4.1.1466.101.120.13", Collections
                .singletonList("supportedControl"), EMPTY_STRING, false, null, null, null, null,
                null, SYNTAX_OID_OID, false, false, false, AttributeUsage.DSA_OPERATION,
                RFC4512_ORIGIN, false);

        builder.addAttributeType("1.3.6.1.4.1.1466.101.120.14", Collections
                .singletonList("supportedSASLMechanisms"), EMPTY_STRING, false, null, null, null,
                null, null, SYNTAX_DIRECTORY_STRING_OID, false, false, false,
                AttributeUsage.DSA_OPERATION, RFC4512_ORIGIN, false);

        builder.addAttributeType("1.3.6.1.4.1.4203.1.3.5", Collections
                .singletonList("supportedFeatures"), EMPTY_STRING, false, null, EMR_OID_NAME, null,
                null, null, SYNTAX_OID_OID, false, false, false, AttributeUsage.DSA_OPERATION,
                RFC4512_ORIGIN, false);

        builder.addAttributeType("1.3.6.1.4.1.1466.101.120.15", Collections
                .singletonList("supportedLDAPVersion"), EMPTY_STRING, false, null, null, null,
                null, null, SYNTAX_INTEGER_OID, false, false, false, AttributeUsage.DSA_OPERATION,
                RFC4512_ORIGIN, false);

        builder.addAttributeType("1.3.6.1.4.1.1466.101.120.16", Collections
                .singletonList("ldapSyntaxes"), EMPTY_STRING, false, null,
                EMR_OID_FIRST_COMPONENT_NAME, null, null, null, SYNTAX_LDAP_SYNTAX_OID, false,
                false, false, AttributeUsage.DIRECTORY_OPERATION, RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.21.1", Collections.singletonList("ditStructureRules"),
                EMPTY_STRING, false, null, EMR_INTEGER_FIRST_COMPONENT_NAME, null, null, null,
                SYNTAX_DIT_STRUCTURE_RULE_OID, false, false, false,
                AttributeUsage.DIRECTORY_OPERATION, RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.21.7", Collections.singletonList("nameForms"), EMPTY_STRING,
                false, null, EMR_OID_FIRST_COMPONENT_NAME, null, null, null, SYNTAX_NAME_FORM_OID,
                false, false, false, AttributeUsage.DIRECTORY_OPERATION, RFC4512_ORIGIN, false);

        builder.addAttributeType("2.5.21.2", Collections.singletonList("ditContentRules"),
                EMPTY_STRING, false, null, EMR_OID_FIRST_COMPONENT_NAME, null, null, null,
                SYNTAX_DIT_CONTENT_RULE_OID, false, false, false,
                AttributeUsage.DIRECTORY_OPERATION, RFC4512_ORIGIN, false);
    }

    private static void defaultMatchingRules(final SchemaBuilder builder) {
        builder.buildMatchingRule(EMR_BIT_STRING_OID).names(EMR_BIT_STRING_NAME).syntaxOID(SYNTAX_BIT_STRING_OID)
                .extraProperties(RFC4512_ORIGIN).implementation(new BitStringEqualityMatchingRuleImpl())
                .addToSchema();
        builder.buildMatchingRule(EMR_BOOLEAN_OID).names(EMR_BOOLEAN_NAME).syntaxOID(SYNTAX_BOOLEAN_OID)
                .extraProperties(RFC4512_ORIGIN).implementation(new BooleanEqualityMatchingRuleImpl())
                .addToSchema();
        builder.buildMatchingRule(EMR_CASE_EXACT_IA5_OID).names(EMR_CASE_EXACT_IA5_NAME)
                .syntaxOID(SYNTAX_IA5_STRING_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new CaseExactIA5EqualityMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(SMR_CASE_EXACT_IA5_OID).names(SMR_CASE_EXACT_IA5_NAME)
                .syntaxOID(SYNTAX_SUBSTRING_ASSERTION_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new CaseExactIA5SubstringMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(EMR_CASE_EXACT_OID).names(EMR_CASE_EXACT_NAME).syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
                .extraProperties(RFC4512_ORIGIN).implementation(new CaseExactEqualityMatchingRuleImpl())
                .addToSchema();
        builder.buildMatchingRule(OMR_CASE_EXACT_OID).names(OMR_CASE_EXACT_NAME).syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
                .extraProperties(RFC4512_ORIGIN).implementation(new CaseExactOrderingMatchingRuleImpl())
                .addToSchema();
        builder.buildMatchingRule(SMR_CASE_EXACT_OID).names(SMR_CASE_EXACT_NAME)
                .syntaxOID(SYNTAX_SUBSTRING_ASSERTION_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new CaseExactSubstringMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(EMR_CASE_IGNORE_IA5_OID).names(EMR_CASE_IGNORE_IA5_NAME)
                .syntaxOID(SYNTAX_IA5_STRING_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new CaseIgnoreIA5EqualityMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(SMR_CASE_IGNORE_IA5_OID).names(SMR_CASE_IGNORE_IA5_NAME)
                .syntaxOID(SYNTAX_SUBSTRING_ASSERTION_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new CaseIgnoreIA5SubstringMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(EMR_CASE_IGNORE_LIST_OID).names(EMR_CASE_IGNORE_LIST_NAME)
                .syntaxOID(SYNTAX_POSTAL_ADDRESS_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new CaseIgnoreListEqualityMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(SMR_CASE_IGNORE_LIST_OID).names(SMR_CASE_IGNORE_LIST_NAME)
                .syntaxOID(SYNTAX_SUBSTRING_ASSERTION_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new CaseIgnoreListSubstringMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(EMR_CASE_IGNORE_OID).names(EMR_CASE_IGNORE_NAME)
                .syntaxOID(SYNTAX_DIRECTORY_STRING_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new CaseIgnoreEqualityMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(OMR_CASE_IGNORE_OID).names(OMR_CASE_IGNORE_NAME)
                .syntaxOID(SYNTAX_DIRECTORY_STRING_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new CaseIgnoreOrderingMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(SMR_CASE_IGNORE_OID).names(SMR_CASE_IGNORE_NAME)
                .syntaxOID(SYNTAX_SUBSTRING_ASSERTION_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new CaseIgnoreSubstringMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(EMR_DIRECTORY_STRING_FIRST_COMPONENT_OID)
                .names(Collections.singletonList(EMR_DIRECTORY_STRING_FIRST_COMPONENT_NAME))
                .syntaxOID(SYNTAX_DIRECTORY_STRING_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(EMR_DN_OID).names(EMR_DN_NAME).syntaxOID(SYNTAX_DN_OID)
                .extraProperties(RFC4512_ORIGIN).implementation(new DistinguishedNameEqualityMatchingRuleImpl())
                .addToSchema();
        builder.buildMatchingRule(EMR_GENERALIZED_TIME_OID).names(EMR_GENERALIZED_TIME_NAME)
                .syntaxOID(SYNTAX_GENERALIZED_TIME_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new GeneralizedTimeEqualityMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(OMR_GENERALIZED_TIME_OID).names(OMR_GENERALIZED_TIME_NAME)
                .syntaxOID(SYNTAX_GENERALIZED_TIME_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new GeneralizedTimeOrderingMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(EMR_INTEGER_FIRST_COMPONENT_OID).names(EMR_INTEGER_FIRST_COMPONENT_NAME)
                .syntaxOID(SYNTAX_INTEGER_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new IntegerFirstComponentEqualityMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(EMR_INTEGER_OID).names(EMR_INTEGER_NAME).syntaxOID(SYNTAX_INTEGER_OID)
                .extraProperties(RFC4512_ORIGIN).implementation(new IntegerEqualityMatchingRuleImpl())
                .addToSchema();
        builder.buildMatchingRule(OMR_INTEGER_OID).names(OMR_INTEGER_NAME).syntaxOID(SYNTAX_INTEGER_OID)
                .extraProperties(RFC4512_ORIGIN).implementation(new IntegerOrderingMatchingRuleImpl())
                .addToSchema();
        builder.buildMatchingRule(EMR_KEYWORD_OID).names(EMR_KEYWORD_NAME).syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
                .extraProperties(RFC4512_ORIGIN).implementation(new KeywordEqualityMatchingRuleImpl())
                .addToSchema();
        builder.buildMatchingRule(EMR_NUMERIC_STRING_OID).names(EMR_NUMERIC_STRING_NAME)
                .syntaxOID(SYNTAX_NUMERIC_STRING_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new NumericStringEqualityMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(OMR_NUMERIC_STRING_OID).names(OMR_NUMERIC_STRING_NAME)
                .syntaxOID(SYNTAX_NUMERIC_STRING_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new NumericStringOrderingMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(SMR_NUMERIC_STRING_OID).names(SMR_NUMERIC_STRING_NAME)
                .syntaxOID(SYNTAX_SUBSTRING_ASSERTION_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new NumericStringSubstringMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(EMR_OID_FIRST_COMPONENT_OID).names(EMR_OID_FIRST_COMPONENT_NAME)
                .syntaxOID(SYNTAX_OID_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new ObjectIdentifierFirstComponentEqualityMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(EMR_OID_OID).names(EMR_OID_NAME).syntaxOID(SYNTAX_OID_OID)
                .extraProperties(RFC4512_ORIGIN).implementation(new ObjectIdentifierEqualityMatchingRuleImpl())
                .addToSchema();
        builder.buildMatchingRule(EMR_OCTET_STRING_OID).names(EMR_OCTET_STRING_NAME).syntaxOID(SYNTAX_OCTET_STRING_OID)
                .extraProperties(RFC4512_ORIGIN).implementation(new OctetStringEqualityMatchingRuleImpl())
                .addToSchema();
        builder.buildMatchingRule(OMR_OCTET_STRING_OID).names(OMR_OCTET_STRING_NAME).syntaxOID(SYNTAX_OCTET_STRING_OID)
                .extraProperties(RFC4512_ORIGIN).implementation(new OctetStringOrderingMatchingRuleImpl())
                .addToSchema();
        // SMR octet string is not in any LDAP RFC and its from X.500
        builder.buildMatchingRule(SMR_OCTET_STRING_OID).names(SMR_OCTET_STRING_NAME).syntaxOID(SYNTAX_OCTET_STRING_OID)
                .extraProperties(X500_ORIGIN).implementation(new OctetStringSubstringMatchingRuleImpl())
                .addToSchema();
        // Depreciated in RFC 4512
        builder.buildMatchingRule(EMR_PROTOCOL_INFORMATION_OID).names(EMR_PROTOCOL_INFORMATION_NAME)
                .syntaxOID(SYNTAX_PROTOCOL_INFORMATION_OID).extraProperties(RFC2252_ORIGIN)
                .implementation(new ProtocolInformationEqualityMatchingRuleImpl()).addToSchema();
        // Depreciated in RFC 4512
        builder.buildMatchingRule(EMR_PRESENTATION_ADDRESS_OID).names(EMR_PRESENTATION_ADDRESS_NAME)
                .syntaxOID(SYNTAX_PRESENTATION_ADDRESS_OID).extraProperties(RFC2252_ORIGIN)
                .implementation(new PresentationAddressEqualityMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(EMR_TELEPHONE_OID).names(EMR_TELEPHONE_NAME).syntaxOID(SYNTAX_TELEPHONE_OID)
                .extraProperties(RFC2252_ORIGIN).implementation(new TelephoneNumberEqualityMatchingRuleImpl())
                .addToSchema();
        builder.buildMatchingRule(SMR_TELEPHONE_OID).names(SMR_TELEPHONE_NAME)
                .syntaxOID(SYNTAX_SUBSTRING_ASSERTION_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new TelephoneNumberSubstringMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(EMR_UNIQUE_MEMBER_OID).names(EMR_UNIQUE_MEMBER_NAME)
                .syntaxOID(SYNTAX_NAME_AND_OPTIONAL_UID_OID).extraProperties(RFC4512_ORIGIN)
                .implementation(new UniqueMemberEqualityMatchingRuleImpl()).addToSchema();
        builder.buildMatchingRule(EMR_WORD_OID).names(EMR_WORD_NAME).syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
                .extraProperties(RFC4512_ORIGIN).implementation(new KeywordEqualityMatchingRuleImpl())
                .addToSchema();
    }

    private static void defaultObjectClasses(final SchemaBuilder builder) {
        builder.buildObjectClass(TOP_OBJECTCLASS_OID)
                .names(TOP_OBJECTCLASS_NAME)
                .type(ABSTRACT)
                .description(TOP_OBJECTCLASS_DESCRIPTION)
                .requiredAttributes("objectClass")
                .extraProperties(RFC4512_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.1")
                .names("alias")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("aliasedObjectName")
                .extraProperties(RFC4512_ORIGIN)
                .addToSchema();

        builder.buildObjectClass(EXTENSIBLE_OBJECT_OBJECTCLASS_OID)
                .names(EXTENSIBLE_OBJECT_OBJECTCLASS_NAME)
                .type(AUXILIARY)
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .extraProperties(RFC4512_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.20.1")
                .names("subschema")
                .type(AUXILIARY)
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .optionalAttributes("dITStructureRules", "nameForms", "ditContentRules", "objectClasses",
                    "attributeTypes", "matchingRules", "matchingRuleUse")
                .extraProperties(RFC4512_ORIGIN)
                .addToSchema();
    }

    private static void defaultSyntaxes(final SchemaBuilder builder) {
        // All RFC 4512 / 4517
        builder.buildSyntax(SYNTAX_ATTRIBUTE_TYPE_OID).description(SYNTAX_ATTRIBUTE_TYPE_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new AttributeTypeSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_BINARY_OID).description(SYNTAX_BINARY_DESCRIPTION).extraProperties(RFC4512_ORIGIN)
                .implementation(new BinarySyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_BIT_STRING_OID).description(SYNTAX_BIT_STRING_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new BitStringSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_BOOLEAN_OID).description(SYNTAX_BOOLEAN_DESCRIPTION).extraProperties(RFC4512_ORIGIN)
                .implementation(new BooleanSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_COUNTRY_STRING_OID).description(SYNTAX_COUNTRY_STRING_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new CountryStringSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_DELIVERY_METHOD_OID).description(SYNTAX_DELIVERY_METHOD_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new DeliveryMethodSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_DIRECTORY_STRING_OID).description(SYNTAX_DIRECTORY_STRING_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new DirectoryStringSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_DIT_CONTENT_RULE_OID).description(SYNTAX_DIT_CONTENT_RULE_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new DITContentRuleSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_DIT_STRUCTURE_RULE_OID).description(SYNTAX_DIT_STRUCTURE_RULE_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new DITStructureRuleSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_DN_OID).description(SYNTAX_DN_DESCRIPTION).extraProperties(RFC4512_ORIGIN)
                .implementation(new DistinguishedNameSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_ENHANCED_GUIDE_OID).description(SYNTAX_ENHANCED_GUIDE_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new EnhancedGuideSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_FAXNUMBER_OID).description(SYNTAX_FAXNUMBER_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new FacsimileNumberSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_FAX_OID).description(SYNTAX_FAX_DESCRIPTION).extraProperties(RFC4512_ORIGIN)
                .implementation(new FaxSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_GENERALIZED_TIME_OID).description(SYNTAX_GENERALIZED_TIME_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new GeneralizedTimeSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_GUIDE_OID).description(SYNTAX_GUIDE_DESCRIPTION).extraProperties(RFC4512_ORIGIN)
                .implementation(new GuideSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_IA5_STRING_OID).description(SYNTAX_IA5_STRING_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new IA5StringSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_INTEGER_OID).description(SYNTAX_INTEGER_DESCRIPTION).extraProperties(RFC4512_ORIGIN)
                .implementation(new IntegerSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_JPEG_OID).description(SYNTAX_JPEG_DESCRIPTION).extraProperties(RFC4512_ORIGIN)
                .implementation(new JPEGSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_MATCHING_RULE_OID).description(SYNTAX_MATCHING_RULE_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new MatchingRuleSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_MATCHING_RULE_USE_OID).description(SYNTAX_MATCHING_RULE_USE_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new MatchingRuleUseSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_LDAP_SYNTAX_OID).description(SYNTAX_LDAP_SYNTAX_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new LDAPSyntaxDescriptionSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_NAME_AND_OPTIONAL_UID_OID).description(SYNTAX_NAME_AND_OPTIONAL_UID_DESCRIPTION)
                .extraProperties(RFC4517_ORIGIN).implementation(new NameAndOptionalUIDSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_NAME_FORM_OID).description(SYNTAX_NAME_FORM_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new NameFormSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_NUMERIC_STRING_OID).description(SYNTAX_NUMERIC_STRING_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new NumericStringSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_OBJECTCLASS_OID).description(SYNTAX_OBJECTCLASS_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new ObjectClassSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_OCTET_STRING_OID).description(SYNTAX_OCTET_STRING_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new OctetStringSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_OID_OID).description(SYNTAX_OID_DESCRIPTION).extraProperties(RFC4512_ORIGIN)
                .implementation(new OIDSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_OTHER_MAILBOX_OID).description(SYNTAX_OTHER_MAILBOX_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new OtherMailboxSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_POSTAL_ADDRESS_OID).description(SYNTAX_POSTAL_ADDRESS_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new PostalAddressSyntaxImpl()).addToSchema();
        // Depreciated in RFC 4512
        builder.buildSyntax(SYNTAX_PRESENTATION_ADDRESS_OID).description(SYNTAX_PRESENTATION_ADDRESS_DESCRIPTION)
                .extraProperties(RFC2252_ORIGIN).implementation(new PresentationAddressSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_PRINTABLE_STRING_OID).description(SYNTAX_PRINTABLE_STRING_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new PrintableStringSyntaxImpl()).addToSchema();
        // Depreciated in RFC 4512
        builder.buildSyntax(SYNTAX_PROTOCOL_INFORMATION_OID).description(SYNTAX_PROTOCOL_INFORMATION_DESCRIPTION)
                .extraProperties(RFC2252_ORIGIN).implementation(new ProtocolInformationSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_SUBSTRING_ASSERTION_OID).description(SYNTAX_SUBSTRING_ASSERTION_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new SubstringAssertionSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_TELEPHONE_OID).description(SYNTAX_TELEPHONE_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new TelephoneNumberSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_TELETEX_TERM_ID_OID).description(SYNTAX_TELETEX_TERM_ID_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new TeletexTerminalIdentifierSyntaxImpl())
                .addToSchema();
        builder.buildSyntax(SYNTAX_TELEX_OID).description(SYNTAX_TELEX_DESCRIPTION).extraProperties(RFC4512_ORIGIN)
                .implementation(new TelexNumberSyntaxImpl()).addToSchema();
        builder.buildSyntax(SYNTAX_UTC_TIME_OID).description(SYNTAX_UTC_TIME_DESCRIPTION)
                .extraProperties(RFC4512_ORIGIN).implementation(new UTCTimeSyntaxImpl()).addToSchema();
    }

    private CoreSchemaImpl() {
        // Prevent instantiation.
    }
}
