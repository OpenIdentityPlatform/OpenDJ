/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2014 Manuel Gaupp
 * Portions Copyright 2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Tool for generating CoreSchema.java.
 */
final class GenerateCoreSchema {
    private static final Set<String> ABBREVIATIONS = new HashSet<>(Arrays.asList("SASL",
            "LDAP", "DN", "DIT", "RDN", "JPEG", "OID", "UUID", "IA5", "UID", "UTC", "X500", "X121",
            "C", "CN", "O", "OU", "L", "DC", "ISDN", "SN", "ST"));

    /**
     * Tool for generating CoreSchema.java.
     *
     * @param args
     *            The command line arguments (none required).
     */
    public static void main(final String[] args) {
        testSplitNameIntoWords();

        final Schema schema = Schema.getCoreSchema();

        final SortedMap<String, Syntax> syntaxes = new TreeMap<>();
        for (final Syntax syntax : schema.getSyntaxes()) {
            if (isOpenDSOID(syntax.getOID())) {
                continue;
            }

            final String name = syntax.getDescription().replaceAll(" Syntax$", "");
            final String fieldName = name.replace(" ", "_").replaceAll("[.-]", "")
                    .toUpperCase(Locale.ENGLISH).concat("_SYNTAX");
            syntaxes.put(fieldName, syntax);
        }

        final SortedMap<String, MatchingRule> matchingRules = new TreeMap<>();
        for (final MatchingRule matchingRule : schema.getMatchingRules()) {
            if (isOpenDSOID(matchingRule.getOID()) || isCollationMatchingRule(matchingRule.getOID())) {
                continue;
            }

            final String name = matchingRule.getNameOrOID().replaceAll("Match$", "");
            final String fieldName = splitNameIntoWords(name).concat("_MATCHING_RULE");
            matchingRules.put(fieldName, matchingRule);
        }

        final SortedMap<String, AttributeType> attributeTypes = new TreeMap<>();
        for (final AttributeType attributeType : schema.getAttributeTypes()) {
            if (isOpenDSOID(attributeType.getOID())) {
                continue;
            }
            final String name = attributeType.getNameOrOID();
            final String fieldName = splitNameIntoWords(name).concat("_ATTRIBUTE_TYPE");
            attributeTypes.put(fieldName, attributeType);
        }

        final SortedMap<String, ObjectClass> objectClasses = new TreeMap<>();
        for (final ObjectClass objectClass : schema.getObjectClasses()) {
            if (isOpenDSOID(objectClass.getOID())) {
                continue;
            }
            final String name = objectClass.getNameOrOID();
            final String fieldName = splitNameIntoWords(name.replace("-", "")).concat("_OBJECT_CLASS");

            objectClasses.put(fieldName, objectClass);
        }

        final PrintStream out = System.out;
        out.println("/*");
        out.println(" * The contents of this file are subject to the terms of the Common Development and");
        out.println(" * Distribution License (the License). You may not use this file except in compliance with the");
        out.println(" * License.");
        out.println(" *");
        out.println(" * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the");
        out.println(" * specific language governing permission and limitations under the License.");
        out.println(" *");
        out.println(" * When distributing Covered Software, include this CDDL Header Notice in each file and include");
        out.println(" * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL");
        out.println(" * Header, with the fields enclosed by brackets [] replaced by your own identifying");
        out.println(" * information: \"Portions Copyright [year] [name of copyright owner]\".");
        out.println(" *");
        out.println(" * Copyright 2009 Sun Microsystems, Inc.");
        out.println(" * Portions copyright 2014-" + Calendar.getInstance().get(Calendar.YEAR) + " ForgeRock AS.");
        out.println(" */");
        out.println("package org.forgerock.opendj.ldap.schema;");
        out.println();
        out.println();
        out.println("// DON'T EDIT THIS FILE!");
        out.println("// It is automatically generated using GenerateCoreSchema class.");
        out.println();
        out.println("/**");
        out.println(" * The OpenDJ SDK core schema contains standard LDAP "
                + "RFC schema elements. These include:");
        out.println(" * <ul>");
        out.println(" * <li><a href=\"http://tools.ietf.org/html/rfc4512\">RFC 4512 -");
        out
                .println(" * Lightweight Directory Access Protocol (LDAP): Directory Information");
        out.println(" * Models </a>");
        out.println(" * <li><a href=\"http://tools.ietf.org/html/rfc4517\">RFC 4517 -");
        out
                .println(" * Lightweight Directory Access Protocol (LDAP): Syntaxes and Matching");
        out.println(" * Rules </a>");
        out.println(" * <li><a href=\"http://tools.ietf.org/html/rfc4519\">RFC 4519 -");
        out.println(" * Lightweight Directory Access Protocol (LDAP): Schema for User");
        out.println(" * Applications </a>");
        out.println(" * <li><a href=\"http://tools.ietf.org/html/rfc4530\">RFC 4530 -");
        out
                .println(" * Lightweight Directory Access Protocol (LDAP): entryUUID Operational");
        out.println(" * Attribute </a>");
        out
                .println(" * <li><a href=\"http://tools.ietf.org/html/rfc3045\">RFC 3045 - Storing");
        out.println(" * Vendor Information in the LDAP Root DSE </a>");
        out.println(" * <li><a href=\"http://tools.ietf.org/html/rfc3112\">RFC 3112 - LDAP");
        out.println(" * Authentication Password Schema </a>");
        out.println(" * </ul>");
        out.println(" * <p>");
        out.println(" * The core schema is non-strict: attempts to retrieve");
        out.println(" * non-existent Attribute Types will return a temporary");
        out.println(" * Attribute Type having the Octet String syntax.");
        out.println(" */");
        out.println("public final class CoreSchema {");

        out.println("    // Core Syntaxes");
        for (final Map.Entry<String, Syntax> syntax : syntaxes.entrySet()) {
            out.println("    private static final Syntax " + syntax.getKey() + " =");
            out.println("        CoreSchemaImpl.getInstance().getSyntax(\""
                    + syntax.getValue().getOID() + "\");");
        }

        out.println();
        out.println("    // Core Matching Rules");
        for (final Map.Entry<String, MatchingRule> matchingRule : matchingRules.entrySet()) {
            out.println("    private static final MatchingRule " + matchingRule.getKey()
                    + " =");
            out.println("        CoreSchemaImpl.getInstance().getMatchingRule(\""
                    + matchingRule.getValue().getOID() + "\");");
        }

        out.println();
        out.println("    // Core Attribute Types");
        for (final Map.Entry<String, AttributeType> attributeType : attributeTypes.entrySet()) {
            out.println("    private static final AttributeType " + attributeType.getKey()
                    + " =");
            out.println("        CoreSchemaImpl.getInstance().getAttributeType(\""
                    + attributeType.getValue().getOID() + "\");");
        }

        out.println();
        out.println("    // Core Object Classes");
        for (final Map.Entry<String, ObjectClass> objectClass : objectClasses.entrySet()) {
            out.println("    private static final ObjectClass " + objectClass.getKey() + " =");
            out.println("        CoreSchemaImpl.getInstance().getObjectClass(\""
                    + objectClass.getValue().getOID() + "\");");
        }

        out.println();
        out.println("    // Prevent instantiation");
        out.println("    private CoreSchema() {");
        out.println("      // Nothing to do.");
        out.println("    }");

        out.println();
        out.println("    /**");
        out.println("     * Returns a reference to the singleton core schema.");
        out.println("     *");
        out.println("     * @return The core schema.");
        out.println("     */");
        out.println("    public static Schema getInstance() {");
        out.println("        return CoreSchemaImpl.getInstance();");
        out.println("    }");

        for (final Map.Entry<String, Syntax> syntax : syntaxes.entrySet()) {
            out.println();

            final String description =
                    toCodeJavaDoc(syntax.getValue().getDescription().replaceAll(" Syntax$", "")
                            + " Syntax");
            out.println("    /**");
            out.println("     * Returns a reference to the " + description);
            out.println("     * which has the OID "
                    + toCodeJavaDoc(syntax.getValue().getOID()) + ".");
            out.println("     *");
            out.println("     * @return A reference to the " + description + ".");

            out.println("     */");
            out.println("    public static Syntax get" + toJavaName(syntax.getKey()) + "() {");
            out.println("        return " + syntax.getKey() + ";");
            out.println("    }");
        }

        for (final Map.Entry<String, MatchingRule> matchingRule : matchingRules.entrySet()) {
            out.println();

            final String description = toCodeJavaDoc(matchingRule.getValue().getNameOrOID());
            out.println("    /**");
            out.println("     * Returns a reference to the " + description + " Matching Rule");
            out.println("     * which has the OID "
                    + toCodeJavaDoc(matchingRule.getValue().getOID()) + ".");
            out.println("     *");
            out.println("     * @return A reference to the " + description + " Matching Rule.");

            out.println("     */");
            out.println("    public static MatchingRule get" + toJavaName(matchingRule.getKey()) + "() {");
            out.println("        return " + matchingRule.getKey() + ";");
            out.println("    }");
        }

        for (final Map.Entry<String, AttributeType> attributeType : attributeTypes.entrySet()) {
            out.println();

            final String description = toCodeJavaDoc(attributeType.getValue().getNameOrOID());
            out.println("    /**");
            out.println("     * Returns a reference to the " + description + " Attribute Type");
            out.println("     * which has the OID "
                    + toCodeJavaDoc(attributeType.getValue().getOID()) + ".");
            out.println("     *");
            out.println("     * @return A reference to the " + description + " Attribute Type.");

            out.println("     */");
            out.println("    public static AttributeType get"
                    + toJavaName(attributeType.getKey()) + "() {");
            out.println("        return " + attributeType.getKey() + ";");
            out.println("    }");
        }

        for (final Map.Entry<String, ObjectClass> objectClass : objectClasses.entrySet()) {
            out.println();

            final String description = toCodeJavaDoc(objectClass.getValue().getNameOrOID());
            out.println("    /**");
            out.println("     * Returns a reference to the " + description + " Object Class");
            out.println("     * which has the OID "
                    + toCodeJavaDoc(objectClass.getValue().getOID()) + ".");
            out.println("     *");
            out.println("     * @return A reference to the " + description + " Object Class.");

            out.println("     */");
            out.println("    public static ObjectClass get" + toJavaName(objectClass.getKey())
                    + "() {");
            out.println("        return " + objectClass.getKey() + ";");
            out.println("    }");
        }

        out.println("}");
    }

    private static boolean isOpenDSOID(final String oid) {
        return oid.startsWith(SchemaConstants.OID_OPENDS_SERVER_BASE + ".");
    }

    private static boolean isCollationMatchingRule(final String oid) {
        return oid.startsWith("1.3.6.1.4.1.42.2.27.9.4.");
    }

    private static String splitNameIntoWords(final String name) {
        String splitName = name.replaceAll("([A-Z][a-z])", "_$1");
        splitName = splitName.replaceAll("([a-z])([A-Z])", "$1_$2");
        splitName = splitName.replaceAll("[-.]", "");

        return splitName.toUpperCase(Locale.ENGLISH);
    }

    private static void testSplitNameIntoWords() {
        final String[][] values =
                new String[][] { { "oneTwoThree", "ONE_TWO_THREE" },
                    { "oneTWOThree", "ONE_TWO_THREE" }, { "oneX500Three", "ONE_X500_THREE" },
                    { "oneTwoX500", "ONE_TWO_X500" }, { "oneTwoX500", "ONE_TWO_X500" },
                    { "x500TwoThree", "X500_TWO_THREE" }, };

        for (final String[] test : values) {
            final String actual = splitNameIntoWords(test[0]);
            final String expected = test[1];
            if (!actual.equals(expected)) {
                System.out.println("Test Split Failure: " + test[0] + " -> " + actual + " != "
                        + expected);
            }
        }
    }

    private static String toCodeJavaDoc(final String text) {
        return String.format("{@code %s}", text);
    }

    private static String toJavaName(final String splitName) {
        final StringBuilder builder = new StringBuilder();
        for (final String word : splitName.split("_")) {
            if (ABBREVIATIONS.contains(word)) {
                builder.append(word);
            } else {
                builder.append(word.charAt(0));
                if (word.length() > 1) {
                    builder.append(word.substring(1).toLowerCase(Locale.ENGLISH));
                }
            }
        }
        return builder.toString();
    }

    private GenerateCoreSchema() {
        // Prevent instantiation.
    }
}
