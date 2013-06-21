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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.ldap.schema;

import java.util.Arrays;
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
    private static final Set<String> ABBREVIATIONS = new HashSet<String>(Arrays.asList("SASL",
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

        final SortedMap<String, Syntax> syntaxes = new TreeMap<String, Syntax>();
        for (final Syntax syntax : schema.getSyntaxes()) {
            if (isOpenDSOID(syntax.getOID())) {
                continue;
            }

            final String name = syntax.getDescription().replaceAll(" Syntax$", "");
            final String fieldName =
                    name.replace(" ", "_").toUpperCase(Locale.ENGLISH).concat("_SYNTAX");
            syntaxes.put(fieldName, syntax);
        }

        final SortedMap<String, MatchingRule> matchingRules = new TreeMap<String, MatchingRule>();
        for (final MatchingRule matchingRule : schema.getMatchingRules()) {
            if (isOpenDSOID(matchingRule.getOID())) {
                continue;
            }

            final String name = matchingRule.getNameOrOID().replaceAll("Match$", "");
            final String fieldName = splitNameIntoWords(name).concat("_MATCHING_RULE");
            matchingRules.put(fieldName, matchingRule);
        }

        final SortedMap<String, AttributeType> attributeTypes =
                new TreeMap<String, AttributeType>();
        for (final AttributeType attributeType : schema.getAttributeTypes()) {
            if (isOpenDSOID(attributeType.getOID())) {
                continue;
            }
            final String name = attributeType.getNameOrOID();
            final String fieldName = splitNameIntoWords(name).concat("_ATTRIBUTE_TYPE");
            attributeTypes.put(fieldName, attributeType);
        }

        final SortedMap<String, ObjectClass> objectClasses = new TreeMap<String, ObjectClass>();
        for (final ObjectClass objectClass : schema.getObjectClasses()) {
            if (isOpenDSOID(objectClass.getOID())) {
                continue;
            }
            final String name = objectClass.getNameOrOID();
            final String fieldName = splitNameIntoWords(name).concat("_OBJECT_CLASS");

            objectClasses.put(fieldName, objectClass);
        }

        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println("package org.forgerock.opendj.ldap.schema;");
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println("/**");
        System.out.println(" * The OpenDJ SDK core schema contains standard LDAP "
                + "RFC schema elements. These include:");
        System.out.println(" * <ul>");
        System.out.println(" * <li><a href=\"http://tools.ietf.org/html/rfc4512\">RFC 4512 -");
        System.out
                .println(" * Lightweight Directory Access Protocol (LDAP): Directory Information");
        System.out.println(" * Models </a>");
        System.out.println(" * <li><a href=\"http://tools.ietf.org/html/rfc4517\">RFC 4517 -");
        System.out
                .println(" * Lightweight Directory Access Protocol (LDAP): Syntaxes and Matching");
        System.out.println(" * Rules </a>");
        System.out.println(" * <li><a href=\"http://tools.ietf.org/html/rfc4519\">RFC 4519 -");
        System.out.println(" * Lightweight Directory Access Protocol (LDAP): Schema for User");
        System.out.println(" * Applications </a>");
        System.out.println(" * <li><a href=\"http://tools.ietf.org/html/rfc4530\">RFC 4530 -");
        System.out
                .println(" * Lightweight Directory Access Protocol (LDAP): entryUUID Operational");
        System.out.println(" * Attribute </a>");
        System.out
                .println(" * <li><a href=\"http://tools.ietf.org/html/rfc3045\">RFC 3045 - Storing");
        System.out.println(" * Vendor Information in the LDAP Root DSE </a>");
        System.out.println(" * <li><a href=\"http://tools.ietf.org/html/rfc3112\">RFC 3112 - LDAP");
        System.out.println(" * Authentication Password Schema </a>");
        System.out.println(" * </ul>");
        System.out.println(" * <p>");
        System.out.println(" * The core schema is non-strict: attempts to retrieve");
        System.out.println(" * non-existent Attribute Types will return a temporary");
        System.out.println(" * Attribute Type having the Octet String syntax.");
        System.out.println(" */");
        System.out.println("public final class CoreSchema");
        System.out.println("{");

        System.out.println("  // Core Syntaxes");
        for (final Map.Entry<String, Syntax> syntax : syntaxes.entrySet()) {
            System.out.println("  private static final Syntax " + syntax.getKey() + " =");
            System.out.println("    CoreSchemaImpl.getInstance().getSyntax(\""
                    + syntax.getValue().getOID() + "\");");
        }

        System.out.println();
        System.out.println("  // Core Matching Rules");
        for (final Map.Entry<String, MatchingRule> matchingRule : matchingRules.entrySet()) {
            System.out.println("  private static final MatchingRule " + matchingRule.getKey()
                    + " =");
            System.out.println("    CoreSchemaImpl.getInstance().getMatchingRule(\""
                    + matchingRule.getValue().getOID() + "\");");
        }

        System.out.println();
        System.out.println("  // Core Attribute Types");
        for (final Map.Entry<String, AttributeType> attributeType : attributeTypes.entrySet()) {
            System.out.println("  private static final AttributeType " + attributeType.getKey()
                    + " =");
            System.out.println("    CoreSchemaImpl.getInstance().getAttributeType(\""
                    + attributeType.getValue().getOID() + "\");");
        }

        System.out.println();
        System.out.println("  // Core Object Classes");
        for (final Map.Entry<String, ObjectClass> objectClass : objectClasses.entrySet()) {
            System.out.println("  private static final ObjectClass " + objectClass.getKey() + " =");
            System.out.println("    CoreSchemaImpl.getInstance().getObjectClass(\""
                    + objectClass.getValue().getOID() + "\");");
        }

        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println("  // Prevent instantiation");
        System.out.println("  private CoreSchema()");
        System.out.println("  {");
        System.out.println("    // Nothing to do.");
        System.out.println("  }");

        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println("  /**");
        System.out.println("   * Returns a reference to the singleton core schema.");
        System.out.println("   *");
        System.out.println("   * @return The core schema.");
        System.out.println("   */");
        System.out.println("  public static Schema getInstance()");
        System.out.println("  {");
        System.out.println("    return CoreSchemaImpl.getInstance();");
        System.out.println("  }");

        for (final Map.Entry<String, Syntax> syntax : syntaxes.entrySet()) {
            System.out.println();
            System.out.println();
            System.out.println();

            final String description =
                    toCodeJavaDoc(syntax.getValue().getDescription().replaceAll(" Syntax$", "")
                            + " Syntax");
            System.out.println("  /**");
            System.out.println("   * Returns a reference to the " + description);
            System.out.println("   * which has the OID "
                    + toCodeJavaDoc(syntax.getValue().getOID()) + ".");
            System.out.println("   *");
            System.out.println("   * @return A reference to the " + description + ".");

            System.out.println("   */");
            System.out.println("  public static Syntax get" + toJavaName(syntax.getKey()) + "()");
            System.out.println("  {");
            System.out.println("    return " + syntax.getKey() + ";");
            System.out.println("  }");
        }

        for (final Map.Entry<String, MatchingRule> matchingRule : matchingRules.entrySet()) {
            System.out.println();
            System.out.println();
            System.out.println();

            final String description = toCodeJavaDoc(matchingRule.getValue().getNameOrOID());
            System.out.println("  /**");
            System.out.println("   * Returns a reference to the " + description + " Matching Rule");
            System.out.println("   * which has the OID "
                    + toCodeJavaDoc(matchingRule.getValue().getOID()) + ".");
            System.out.println("   *");
            System.out
                    .println("   * @return A reference to the " + description + " Matching Rule.");

            System.out.println("   */");
            System.out.println("  public static MatchingRule get"
                    + toJavaName(matchingRule.getKey()) + "()");
            System.out.println("  {");
            System.out.println("    return " + matchingRule.getKey() + ";");
            System.out.println("  }");
        }

        for (final Map.Entry<String, AttributeType> attributeType : attributeTypes.entrySet()) {
            System.out.println();
            System.out.println();
            System.out.println();

            final String description = toCodeJavaDoc(attributeType.getValue().getNameOrOID());
            System.out.println("  /**");
            System.out
                    .println("   * Returns a reference to the " + description + " Attribute Type");
            System.out.println("   * which has the OID "
                    + toCodeJavaDoc(attributeType.getValue().getOID()) + ".");
            System.out.println("   *");
            System.out.println("   * @return A reference to the " + description
                    + " Attribute Type.");

            System.out.println("   */");
            System.out.println("  public static AttributeType get"
                    + toJavaName(attributeType.getKey()) + "()");
            System.out.println("  {");
            System.out.println("    return " + attributeType.getKey() + ";");
            System.out.println("  }");
        }

        for (final Map.Entry<String, ObjectClass> objectClass : objectClasses.entrySet()) {
            System.out.println();
            System.out.println();
            System.out.println();

            final String description = toCodeJavaDoc(objectClass.getValue().getNameOrOID());
            System.out.println("  /**");
            System.out.println("   * Returns a reference to the " + description + " Object Class");
            System.out.println("   * which has the OID "
                    + toCodeJavaDoc(objectClass.getValue().getOID()) + ".");
            System.out.println("   *");
            System.out.println("   * @return A reference to the " + description + " Object Class.");

            System.out.println("   */");
            System.out.println("  public static ObjectClass get" + toJavaName(objectClass.getKey())
                    + "()");
            System.out.println("  {");
            System.out.println("    return " + objectClass.getKey() + ";");
            System.out.println("  }");
        }

        System.out.println("}");
    }

    private static boolean isOpenDSOID(final String oid) {
        return oid.startsWith(SchemaConstants.OID_OPENDS_SERVER_BASE + ".");
    }

    private static String splitNameIntoWords(final String name) {
        String splitName = name.replaceAll("([A-Z][a-z])", "_$1");
        splitName = splitName.replaceAll("([a-z])([A-Z])", "$1_$2");

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
