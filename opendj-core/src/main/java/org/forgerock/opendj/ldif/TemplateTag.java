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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.forgerock.opendj.ldif;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.TemplateFile.Branch;
import org.forgerock.opendj.ldif.TemplateFile.Template;
import org.forgerock.opendj.ldif.TemplateFile.TemplateEntry;
import org.forgerock.opendj.ldif.TemplateFile.TemplateValue;
import org.forgerock.util.Utils;

/**
 * Represents a tag that may be used in a template line when generating entries.
 * It can be used to generate content.
 *
 * @see EntryGenerator
 */
abstract class TemplateTag {

    /**
     * Retrieves the name for this tag.
     */
    abstract String getName();

    /**
     * Indicates whether this tag is allowed for use in the extra lines for
     * branches.
     */
    abstract boolean allowedInBranch();

    /**
     * Performs any initialization for this tag that may be needed while parsing
     * a branch definition.
     *
     * @param schema
     *            The schema used to create attributes.
     * @param templateFile
     *            The template file in which this tag is used.
     * @param branch
     *            The branch in which this tag is used.
     * @param arguments
     *            The set of arguments provided for this tag.
     * @param lineNumber
     *            The line number on which this tag appears in the template
     *            file.
     * @param warnings
     *            A list into which any appropriate warning messages may be
     *            placed.
     * @throws DecodeException
     *             If tag cannot be initialized.
     */
    void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
            int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
        // No implementation required by default.
    }

    /**
     * Performs any initialization for this tag that may be needed while parsing
     * a template definition.
     *
     * @param schema
     *            The schema used to create attributes.
     * @param templateFile
     *            The template file in which this tag is used.
     * @param template
     *            The template in which this tag is used.
     * @param tagArguments
     *            The set of arguments provided for this tag.
     * @param lineNumber
     *            The line number on which this tag appears in the template
     *            file.
     * @param warnings
     *            A list into which any appropriate warning messages may be
     *            placed.
     */
    abstract void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
            String[] tagArguments, int lineNumber, List<LocalizableMessage> warnings) throws DecodeException;

    /**
     * Performs any initialization for this tag that may be needed when starting
     * to generate entries below a new parent.
     *
     * @param parentEntry
     *            The entry below which the new entries will be generated.
     */
    void initializeForParent(TemplateEntry parentEntry) {
        // No implementation required by default.
    }

    /**
     * Check for an attribute type in a branch or in a template.
     *
     * @param attrType
     *            The attribute type to check for.
     * @param branch
     *            The branch that contains the type, or {@code null}
     * @param template
     *            The template that contains the type, or {@code null}
     * @return true if either the branch or the template has the provided
     *         attribute
     */
    final boolean hasAttributeTypeInBranchOrTemplate(AttributeType attrType, Branch branch,
            Template template) {
        return (branch != null && branch.hasAttribute(attrType))
                || (template != null && template.hasAttribute(attrType));
    }

    /**
     * Generates the content for this tag by appending it to the provided tag.
     *
     * @param templateEntry
     *            The entry for which this tag is being generated.
     * @param templateValue
     *            The template value to which the generated content should be
     *            appended.
     * @return The result of generating content for this tag.
     */
    abstract TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue);

    /**
     * Represents the result of tag generation.
     */
    static enum TagResult {
        SUCCESS, FAILURE
    }

    /**
     * Tag used to reference the value of a specified attribute
     * already defined in the entry.
     */
    static class AttributeValueTag extends TemplateTag {

        /** The attribute type that specifies which value should be used. */
        private AttributeType attributeType;

        /** The maximum number of characters to include from the value. */
        private int numCharacters;

        AttributeValueTag() {
            attributeType = null;
            numCharacters = 0;
        }

        @Override
        String getName() {
            return "AttributeValue";
        }

        @Override
        boolean allowedInBranch() {
            return true;
        }

        @Override
        void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(schema, branch, null, arguments, lineNumber);
        }

        @Override
        void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(schema, null, template, arguments, lineNumber);
        }

        private void initialize(Schema schema, Branch branch, Template template, String[] arguments, int lineNumber)
                throws DecodeException {
            if (arguments.length < 1 || arguments.length > 2) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(),
                        lineNumber, 1, 2, arguments.length);
                throw DecodeException.fatalError(message);
            }

            attributeType = schema.getAttributeType(arguments[0].toLowerCase());
            if (!hasAttributeTypeInBranchOrTemplate(attributeType, branch, template)) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_UNDEFINED_ATTRIBUTE.get(arguments[0], lineNumber);
                throw DecodeException.fatalError(message);
            }

            if (arguments.length == 2) {
                try {
                    numCharacters = Integer.parseInt(arguments[1]);
                    if (numCharacters < 0) {
                        LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INTEGER_BELOW_LOWER_BOUND.get(
                                numCharacters, 0, getName(), lineNumber);
                        throw DecodeException.fatalError(message);
                    }
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[1],
                            getName(), lineNumber);
                    throw DecodeException.fatalError(message);
                }
            } else {
                numCharacters = 0;
            }
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            TemplateValue value = templateEntry.getValue(attributeType);
            if (value == null) {
                // This is fine -- we just won't append anything.
                return TagResult.SUCCESS;
            }

            if (numCharacters > 0) {
                String valueString = value.getValueAsString();
                if (valueString.length() > numCharacters) {
                    templateValue.append(valueString.substring(0, numCharacters));
                } else {
                    templateValue.append(valueString);
                }
            } else {
                templateValue.append(value.getValueAsString());
            }

            return TagResult.SUCCESS;
        }
    }

    /**
     * Tag used to include the DN of the current entry in the attribute value.
     */
    static class DNTag extends TemplateTag {

        /** The number of DN components to include. */
        private int numComponents;

        @Override
        String getName() {
            return "DN";
        }

        @Override
        final boolean allowedInBranch() {
            return true;
        }

        @Override
        final void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(arguments, lineNumber);
        }

        @Override
        final void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(arguments, lineNumber);
        }

        private void initialize(String[] arguments, int lineNumber)
                throws DecodeException {
            if (arguments.length == 0) {
                numComponents = 0;
            } else if (arguments.length == 1) {
                try {
                    numComponents = Integer.parseInt(arguments[0]);
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[0],
                            getName(), lineNumber);
                    throw DecodeException.fatalError(message);
                }
            } else {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(),
                        lineNumber, 0, 1, arguments.length);
                throw DecodeException.fatalError(message);
            }
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            return generateValue(templateEntry, templateValue, ",");
        }

        final TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue,
                String separator) {
            DN dn = templateEntry.getDN();
            if (dn == null || dn.isRootDN()) {
                return TagResult.SUCCESS;
            }

            String dnAsString = "";
            if (numComponents == 0) {
                // Return the DN of the entry
                dnAsString = dn.toString();
            } else if (numComponents > 0) {
                // Return the first numComponents RDNs of the DN
                dnAsString = dn.localName(numComponents).toString();
            } else {
                // numComponents is negative
                // Return the last numComponents RDNs of the DN
                dnAsString = dn.parent(dn.size() - Math.abs(numComponents)).toString();
            }
            // If expected separator is not standard separator
            // Then substitute expected to standard
            if (!separator.equals(",")) {
                dnAsString = dnAsString.replaceAll(",", separator);
            }
            templateValue.append(dnAsString);

            return TagResult.SUCCESS;
        }
    }

    /**
     * Tag used to provide values from a text file. The file should have one
     * value per line. Access to the values may be either at random or in
     * sequential order.
     */
    static class FileTag extends TemplateTag {
        /**
         * Indicates whether the values should be selected sequentially or at
         * random.
         */
        private boolean isSequential;

        /** The index used for sequential access. */
        private int nextIndex;

        /** The random number generator for this tag. */
        private Random random;

        /** The lines read from the file. */
        private String[] fileLines;

        @Override
        String getName() {
            return "File";
        }

        @Override
        boolean allowedInBranch() {
            return true;
        }

        @Override
        void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(templateFile, arguments, lineNumber);
        }

        @Override
        void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(templateFile, arguments, lineNumber);
        }

        private void initialize(TemplateFile templateFile, String[] arguments, int lineNumber)
                throws DecodeException {
            random = templateFile.getRandom();

            // There must be at least one argument, and possibly two.
            if (arguments.length < 1 || arguments.length > 2) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(),
                        lineNumber, 1, 2, arguments.length);
                throw DecodeException.fatalError(message);
            }

            // The first argument should be the path to the file.
            final String filePath = arguments[0];
            BufferedReader dataReader = null;
            try {
                dataReader = templateFile.getReader(filePath);
                if (dataReader == null) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_CANNOT_FIND_FILE.get(filePath, getName(),
                            lineNumber);
                    throw DecodeException.fatalError(message);
                }

                // See if the file has already been read into memory. If not, then
                // read it.
                try {
                    fileLines = templateFile.getLines(filePath, dataReader);
                } catch (IOException ioe) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_CANNOT_READ_FILE.get(filePath, getName(),
                            lineNumber, String.valueOf(ioe));
                    throw DecodeException.fatalError(message, ioe);
                }
            } finally {
                Utils.closeSilently(dataReader);
            }

            // If there is a second argument, then it should be either
            // "sequential" or "random". If there isn't one, then we should
            // assume "random".
            if (arguments.length == 2) {
                if ("sequential".equalsIgnoreCase(arguments[1])) {
                    isSequential = true;
                    nextIndex = 0;
                } else if ("random".equalsIgnoreCase(arguments[1])) {
                    isSequential = false;
                } else {
                    throw DecodeException.fatalError(
                        ERR_ENTRY_GENERATOR_TAG_INVALID_FILE_ACCESS_MODE.get(arguments[1], getName(), lineNumber));
                }
            } else {
                isSequential = false;
            }
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            if (isSequential) {
                templateValue.append(fileLines[nextIndex++]);
                if (nextIndex >= fileLines.length) {
                    nextIndex = 0;
                }
            } else {
                templateValue.append(fileLines[random.nextInt(fileLines.length)]);
            }

            return TagResult.SUCCESS;
        }
    }

    /**
     * Tag used to include a first name in the attribute value.
     */
    static class FirstNameTag extends NameTag {
        @Override
        String getName() {
            return "First";
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            templateValue.append(templateFile.getFirstName());
            return TagResult.SUCCESS;
        }
    }

    /**
     * Tag used to include a GUID in the attribute value.
     */
    static class GUIDTag extends TemplateTag {

        @Override
        String getName() {
            return "GUID";
        }

        @Override
        boolean allowedInBranch() {
            return true;
        }

        @Override
        void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(arguments, lineNumber);
        }


        @Override
        void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(arguments, lineNumber);
        }

        private void initialize(String[] arguments, int lineNumber) throws DecodeException {
            if (arguments.length != 0) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber,
                        0, arguments.length);
                throw DecodeException.fatalError(message);
            }
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            templateValue.append(UUID.randomUUID().toString());
            return TagResult.SUCCESS;
        }
    }

    /**
     * Base tag to use to base presence of one attribute on the absence/presence
     * of another attribute and/or attribute value.
     */
    static abstract class IfTag extends TemplateTag {
        /** The attribute type for which to make the determination. */
        AttributeType attributeType;

        /** The value for which to make the determination. */
        String assertionValue;

        @Override
        final boolean allowedInBranch() {
            return true;
        }

        @Override
        final void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(schema, branch, null, arguments, lineNumber);
        }

        @Override
        final void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(schema, null, template, arguments, lineNumber);
        }

        private void initialize(Schema schema, Branch branch, Template template, String[] arguments, int lineNumber)
                throws DecodeException {
            if (arguments.length < 1 || arguments.length > 2) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(),
                        lineNumber, 1, 2, arguments.length);
                throw DecodeException.fatalError(message);
            }

            attributeType = schema.getAttributeType(arguments[0].toLowerCase());
            if (!hasAttributeTypeInBranchOrTemplate(attributeType, branch, template)) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_UNDEFINED_ATTRIBUTE.get(arguments[0], lineNumber);
                throw DecodeException.fatalError(message);
            }

            if (arguments.length == 2) {
                assertionValue = arguments[1];
            } else {
                assertionValue = null;
            }
        }
    }

    /**
     * Tag used to base presence of one attribute on the absence of another
     * attribute and/or attribute value.
     */
    static class IfAbsentTag extends IfTag {
        @Override
        String getName() {
            return "IfAbsent";
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            List<TemplateValue> values = templateEntry.getValues(attributeType);
            if (values == null) {
                return TagResult.SUCCESS;
            }
            if (assertionValue == null) {
                return TagResult.FAILURE;
            }
            for (TemplateValue v : values) {
                if (assertionValue.equals(v.getValueAsString())) {
                    return TagResult.FAILURE;
                }
            }
            return TagResult.SUCCESS;
        }
    }

    /**
     * Tag used to base presence of one attribute on the presence of another
     * attribute and/or attribute value.
     */
    static class IfPresentTag extends IfTag {
        @Override
        String getName() {
            return "IfPresent";
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            List<TemplateValue> values = templateEntry.getValues(attributeType);
            if (values == null || values.isEmpty()) {
                return TagResult.FAILURE;
            }

            if (assertionValue == null) {
                return TagResult.SUCCESS;
            }
            for (TemplateValue v : values) {
                if (assertionValue.equals(v.getValueAsString())) {
                    return TagResult.SUCCESS;
                }
            }
            return TagResult.FAILURE;
        }
    }

    /**
     * Tag used to include a last name in the attribute value.
     */
    static class LastNameTag extends NameTag {
        @Override
        String getName() {
            return "Last";
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            templateValue.append(templateFile.getLastName());
            return TagResult.SUCCESS;
        }
    }

    /**
     * Tag used to select a value from a pre-defined list, optionally defining
     * weights for each value that can impact the likelihood of a given item
     * being selected.
     * <p>
     * The items to include in the list should be specified as arguments to the
     * tag. If the argument ends with a semicolon followed by an integer, then
     * that will be the weight for that particular item. If no weight is given,
     * then the weight for that item will be assumed to be one.
     */
    static class ListTag extends TemplateTag {
        /** The ultimate cumulative weight. */
        private int cumulativeWeight;

        /** The set of cumulative weights for the list items. */
        private int[] valueWeights;

        /** The set of values in the list. */
        private String[] valueStrings;

        /** The random number generator for this tag. */
        private Random random;

        @Override
        String getName() {
            return "List";
        }

        @Override
        boolean allowedInBranch() {
            return true;
        }

        @Override
        void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(templateFile, arguments, lineNumber, warnings);
        }

        @Override
        void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(templateFile, arguments, lineNumber, warnings);
        }

        private void initialize(TemplateFile templateFile, String[] arguments, int lineNumber,
                List<LocalizableMessage> warnings) throws DecodeException {
            if (arguments.length == 0) {
                throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_TAG_LIST_NO_ARGUMENTS.get(lineNumber));
            }
            valueStrings = new String[arguments.length];
            valueWeights = new int[arguments.length];
            cumulativeWeight = 0;
            random = templateFile.getRandom();

            for (int i = 0; i < arguments.length; i++) {
                String value = arguments[i];
                int weight = 1;
                int semicolonPos = value.lastIndexOf(';');
                if (semicolonPos >= 0) {
                    try {
                        weight = Integer.parseInt(value.substring(semicolonPos + 1));
                        value = value.substring(0, semicolonPos);
                    } catch (NumberFormatException e) {
                        warnings.add(WARN_ENTRY_GENERATOR_TAG_LIST_INVALID_WEIGHT.get(lineNumber, value));
                    }
                }
                cumulativeWeight += weight;
                valueStrings[i] = value;
                valueWeights[i] = cumulativeWeight;
            }
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            int selectedWeight = random.nextInt(cumulativeWeight) + 1;
            for (int i = 0; i < valueWeights.length; i++) {
                if (selectedWeight <= valueWeights[i]) {
                    templateValue.append(valueStrings[i]);
                    break;
                }
            }
            return TagResult.SUCCESS;
        }
    }

    /**
     * Base tag to use to include a first name or last name in the attribute
     * value.
     */
    static abstract class NameTag extends TemplateTag {
        /** The template file with which this tag is associated. */
        TemplateFile templateFile;

        @Override
        final boolean allowedInBranch() {
            return false;
        }

        @Override
        final void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            this.templateFile = templateFile;

            if (arguments.length != 0) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber,
                        0, arguments.length);
                throw DecodeException.fatalError(message);
            }
        }
    }

    /**
     * Base tag to use to include the DN of the parent entry in the attribute value.
     */
    static abstract class ParentTag extends TemplateTag {

        @Override
        final boolean allowedInBranch() {
            return false;
        }

        @Override
        final void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            if (arguments.length != 0) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber,
                        0, arguments.length);
                throw DecodeException.fatalError(message);
            }
        }
    }

    /**
     * Tag used to include the DN of the parent entry in the attribute value.
     */
    static class ParentDNTag extends ParentTag {

        @Override
        String getName() {
            return "ParentDN";
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            DN parentDN = templateEntry.getParentDN();
            if (parentDN == null || parentDN.isRootDN()) {
                return TagResult.SUCCESS;
            }
            templateValue.append(parentDN);
            return TagResult.SUCCESS;
        }
    }

    /**
     * Tag used to indicate that a value should only be included in a percentage
     * of the entries.
     */
    static class PresenceTag extends TemplateTag {
        /**
         * The percentage of the entries in which this attribute value should
         * appear.
         */
        private int percentage;

        /** The random number generator for this tag. */
        private Random random;

        @Override
        String getName() {
            return "Presence";
        }

        @Override
        boolean allowedInBranch() {
            return true;
        }

        @Override
        void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(templateFile, arguments, lineNumber);
        }

        @Override
        void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(templateFile, arguments, lineNumber);
        }

        private void initialize(TemplateFile templateFile, String[] arguments, int lineNumber)
                throws DecodeException {
            random = templateFile.getRandom();

            if (arguments.length != 1) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber,
                        1, arguments.length);
                throw DecodeException.fatalError(message);
            }

            try {
                percentage = Integer.parseInt(arguments[0]);
                if (percentage < 0) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INTEGER_BELOW_LOWER_BOUND.get(percentage,
                            0, getName(), lineNumber);
                    throw DecodeException.fatalError(message);
                }
                if (percentage > 100) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INTEGER_ABOVE_UPPER_BOUND.get(percentage,
                            100, getName(), lineNumber);
                    throw DecodeException.fatalError(message);
                }
            } catch (NumberFormatException nfe) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[0],
                        getName(), lineNumber);
                throw DecodeException.fatalError(message);
            }
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            int intValue = random.nextInt(100);
            if (intValue < percentage) {
                return TagResult.SUCCESS;
            } else {
                return TagResult.FAILURE;
            }
        }
    }

    /**
     * Tag used to generate random values. It has a number of subtypes based on
     * the type of information that should be generated, including:
     * <UL>
     * <LI>alpha:length</LI>
     * <LI>alpha:minlength:maxlength</LI>
     * <LI>numeric:length</LI>
     * <LI>numeric:minvalue:maxvalue</LI>
     * <LI>numeric:minvalue:maxvalue:format</LI>
     * <LI>alphanumeric:length</LI>
     * <LI>alphanumeric:minlength:maxlength</LI>
     * <LI>chars:characters:length</LI>
     * <LI>chars:characters:minlength:maxlength</LI>
     * <LI>hex:length</LI>
     * <LI>hex:minlength:maxlength</LI>
     * <LI>base64:length</LI>
     * <LI>base64:minlength:maxlength</LI>
     * <LI>month</LI>
     * <LI>month:maxlength</LI>
     * <LI>telephone</LI>
     * </UL>
     */
    static class RandomTag extends TemplateTag {

        static enum RandomType {
            /**
             * Generates values from a fixed number of characters from a given
             * character set.
             */
            CHARS_FIXED,
            /**
             * Generates values from a variable number of characters from a given
             * character set.
             */
            CHARS_VARIABLE,
            /**
             * Generates numbers.
             */
            NUMERIC,
            /**
             * Generates months (as text).
             */
            MONTH,
            /**
             * Generates telephone numbers.
             */
            TELEPHONE
        }

        /**
         * The character set that will be used for alphabetic characters.
         */
        public static final char[] ALPHA_CHARS = "abcdefghijklmnopqrstuvwxyz".toCharArray();

        /**
         * The character set that will be used for numeric characters.
         */
        public static final char[] NUMERIC_CHARS = "01234567890".toCharArray();

        /**
         * The character set that will be used for alphanumeric characters.
         */
        public static final char[] ALPHANUMERIC_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

        /**
         * The character set that will be used for hexadecimal characters.
         */
        public static final char[] HEX_CHARS = "01234567890abcdef".toCharArray();

        /**
         * The character set that will be used for base64 characters.
         */
        public static final char[] BASE64_CHARS = ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
                + "01234567890+/").toCharArray();

        /**
         * The set of month names that will be used.
         */
        public static final String[] MONTHS = { "January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December" };

        /** The character set that should be used to generate the values. */
        private char[] characterSet;

        /** The decimal format used to format numeric values. */
        private DecimalFormat decimalFormat;

        /** The number of characters between the minimum and maximum length (inclusive). */
        private int lengthRange = 1;

        /** The maximum number of characters to include in the value. */
        private int maxLength;

        /** The minimum number of characters to include in the value. */
        private int minLength;

        /** The type of random value that should be generated. */
        private RandomType randomType;

        /** The maximum numeric value that should be generated. */
        private long maxValue;

        /** The minimum numeric value that should be generated. */
        private long minValue;

        /**
         * The number of values between the minimum and maximum value
         * (inclusive).
         */
        private long valueRange = 1L;

        /** The random number generator for this tag. */
        private Random random;

        @Override
        String getName() {
            return "Random";
        }

        @Override
        boolean allowedInBranch() {
            return true;
        }

        @Override
        void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(templateFile, arguments, lineNumber, warnings);
        }

        @Override
        void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(templateFile, arguments, lineNumber, warnings);
        }

        private void initialize(TemplateFile templateFile, String[] arguments, int lineNumber,
                List<LocalizableMessage> warnings) throws DecodeException {
            random = templateFile.getRandom();

            // There must be at least one argument, to specify the type of
            // random value to generate.
            if (arguments == null || arguments.length == 0) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_NO_RANDOM_TYPE_ARGUMENT.get(lineNumber);
                throw DecodeException.fatalError(message);
            }

            int numArgs = arguments.length;
            String randomTypeString = arguments[0].toLowerCase();

            if ("alpha".equals(randomTypeString)) {
                characterSet = ALPHA_CHARS;
                decodeLength(arguments, 1, lineNumber, warnings);
            } else if ("numeric".equals(randomTypeString)) {
                if (numArgs == 2) {
                    randomType = RandomType.CHARS_FIXED;
                    characterSet = NUMERIC_CHARS;

                    try {
                        minLength = Integer.parseInt(arguments[1]);

                        if (minLength < 0) {
                            LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INTEGER_BELOW_LOWER_BOUND.get(
                                    minLength, 0, getName(), lineNumber);
                            throw DecodeException.fatalError(message);
                        } else if (minLength == 0) {
                            LocalizableMessage message = WARN_ENTRY_GENERATOR_TAG_WARNING_EMPTY_VALUE.get(lineNumber);
                            warnings.add(message);
                        }
                    } catch (NumberFormatException nfe) {
                        LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[1],
                                getName(), lineNumber);
                        throw DecodeException.fatalError(message, nfe);
                    }
                } else if (numArgs == 3 || numArgs == 4) {
                    randomType = RandomType.NUMERIC;

                    if (numArgs == 4) {
                        try {
                            decimalFormat = new DecimalFormat(arguments[3]);
                        } catch (Exception e) {
                            LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_FORMAT_STRING.get(
                                    arguments[3], getName(), lineNumber);
                            throw DecodeException.fatalError(message, e);
                        }
                    } else {
                        decimalFormat = null;
                    }

                    try {
                        minValue = Long.parseLong(arguments[1]);
                    } catch (NumberFormatException nfe) {
                        LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[1],
                                getName(), lineNumber);
                        throw DecodeException.fatalError(message, nfe);
                    }

                    try {
                        maxValue = Long.parseLong(arguments[2]);
                        if (maxValue < minValue) {
                            LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INTEGER_BELOW_LOWER_BOUND.get(
                                    maxValue, minValue, getName(), lineNumber);
                            throw DecodeException.fatalError(message);
                        }

                        valueRange = maxValue - minValue + 1;
                    } catch (NumberFormatException nfe) {
                        LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[2],
                                getName(), lineNumber);
                        throw DecodeException.fatalError(message, nfe);
                    }
                } else {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(),
                            lineNumber, 2, 4, numArgs);
                    throw DecodeException.fatalError(message);
                }
            } else if ("alphanumeric".equals(randomTypeString)) {
                characterSet = ALPHANUMERIC_CHARS;
                decodeLength(arguments, 1, lineNumber, warnings);
            } else if ("chars".equals(randomTypeString)) {
                if (numArgs < 3 || numArgs > 4) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(),
                            lineNumber, 3, 4, numArgs);
                    throw DecodeException.fatalError(message);
                }

                characterSet = arguments[1].toCharArray();
                decodeLength(arguments, 2, lineNumber, warnings);
            } else if ("hex".equals(randomTypeString)) {
                characterSet = HEX_CHARS;
                decodeLength(arguments, 1, lineNumber, warnings);
            } else if ("base64".equals(randomTypeString)) {
                characterSet = BASE64_CHARS;
                decodeLength(arguments, 1, lineNumber, warnings);
            } else if ("month".equals(randomTypeString)) {
                randomType = RandomType.MONTH;

                if (numArgs == 1) {
                    maxLength = 0;
                } else if (numArgs == 2) {
                    try {
                        maxLength = Integer.parseInt(arguments[1]);
                        if (maxLength <= 0) {
                            LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INTEGER_BELOW_LOWER_BOUND.get(
                                    maxLength, 1, getName(), lineNumber);
                            throw DecodeException.fatalError(message);
                        }
                    } catch (NumberFormatException nfe) {
                        LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[1],
                                getName(), lineNumber);
                        throw DecodeException.fatalError(message, nfe);
                    }
                } else {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(),
                            lineNumber, 1, 2, numArgs);
                    throw DecodeException.fatalError(message);
                }
            } else if ("telephone".equals(randomTypeString)) {
                randomType = RandomType.TELEPHONE;
            } else {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_UNKNOWN_RANDOM_TYPE.get(lineNumber,
                        randomTypeString);
                throw DecodeException.fatalError(message);
            }
        }

        /**
         * Decodes the information in the provided argument list as either a
         * single integer specifying the number of characters, or two integers
         * specifying the minimum and maximum number of characters.
         *
         * @param arguments
         *            The set of arguments to be processed.
         * @param startPos
         *            The position at which the first legth value should appear
         *            in the argument list.
         * @param lineNumber
         *            The line number on which the tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        private void decodeLength(String[] arguments, int startPos, int lineNumber, List<LocalizableMessage> warnings)
                throws DecodeException {
            int numArgs = arguments.length - startPos + 1;

            if (numArgs == 2) {
                // There is a fixed number of characters in the value.
                randomType = RandomType.CHARS_FIXED;

                try {
                    minLength = Integer.parseInt(arguments[startPos]);

                    if (minLength < 0) {
                        LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INTEGER_BELOW_LOWER_BOUND.get(minLength,
                                0, getName(), lineNumber);
                        throw DecodeException.fatalError(message);
                    } else if (minLength == 0) {
                        LocalizableMessage message = WARN_ENTRY_GENERATOR_TAG_WARNING_EMPTY_VALUE.get(lineNumber);
                        warnings.add(message);
                    }
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_CANNOT_PARSE_AS_INTEGER.get(
                            arguments[startPos], getName(), lineNumber);
                    throw DecodeException.fatalError(message, nfe);
                }
            } else if (numArgs == 3) {
                // There are minimum and maximum lengths.
                randomType = RandomType.CHARS_VARIABLE;

                try {
                    minLength = Integer.parseInt(arguments[startPos]);

                    if (minLength < 0) {
                        LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INTEGER_BELOW_LOWER_BOUND.get(minLength,
                                0, getName(), lineNumber);
                        throw DecodeException.fatalError(message);
                    }
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_CANNOT_PARSE_AS_INTEGER.get(
                            arguments[startPos], getName(), lineNumber);
                    throw DecodeException.fatalError(message, nfe);
                }

                try {
                    maxLength = Integer.parseInt(arguments[startPos + 1]);
                    lengthRange = maxLength - minLength + 1;

                    if (maxLength < minLength) {
                        LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INTEGER_BELOW_LOWER_BOUND.get(maxLength,
                                minLength, getName(), lineNumber);
                        throw DecodeException.fatalError(message);
                    } else if (maxLength == 0) {
                        LocalizableMessage message = WARN_ENTRY_GENERATOR_TAG_WARNING_EMPTY_VALUE.get(lineNumber);
                        warnings.add(message);
                    }
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_CANNOT_PARSE_AS_INTEGER.get(
                            arguments[startPos + 1], getName(), lineNumber);
                    throw DecodeException.fatalError(message, nfe);
                }
            } else {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(),
                        lineNumber, startPos + 1, startPos + 2, numArgs);
                throw DecodeException.fatalError(message);
            }
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            switch (randomType) {
            case CHARS_FIXED:
                for (int i = 0; i < minLength; i++) {
                    templateValue.append(characterSet[random.nextInt(characterSet.length)]);
                }
                break;

            case CHARS_VARIABLE:
                int numChars = random.nextInt(lengthRange) + minLength;
                for (int i = 0; i < numChars; i++) {
                    templateValue.append(characterSet[random.nextInt(characterSet.length)]);
                }
                break;

            case NUMERIC:
                long randomValue = (random.nextLong() & 0x7FFFFFFFFFFFFFFFL) % valueRange + minValue;
                if (decimalFormat != null) {
                    templateValue.append(decimalFormat.format(randomValue));
                } else {
                    templateValue.append(randomValue);
                }
                break;

            case MONTH:
                String month = MONTHS[random.nextInt(MONTHS.length)];
                if ((maxLength == 0) || (month.length() <= maxLength)) {
                    templateValue.append(month);
                } else {
                    templateValue.append(month.substring(0, maxLength));
                }
                break;

            case TELEPHONE:
                templateValue.append("+1 ");
                for (int i = 0; i < 3; i++) {
                    templateValue.append(NUMERIC_CHARS[random.nextInt(NUMERIC_CHARS.length)]);
                }
                templateValue.append(' ');
                for (int i = 0; i < 3; i++) {
                    templateValue.append(NUMERIC_CHARS[random.nextInt(NUMERIC_CHARS.length)]);
                }
                templateValue.append(' ');
                for (int i = 0; i < 4; i++) {
                    templateValue.append(NUMERIC_CHARS[random.nextInt(NUMERIC_CHARS.length)]);
                }
                break;
            }

            return TagResult.SUCCESS;
        }
    }

    /**
     * Tag used to include the RDN of the current entry in the attribute value.
     */
    static class RDNTag extends TemplateTag {

        @Override
        String getName() {
            return "RDN";
        }

        @Override
        boolean allowedInBranch() {
            return true;
        }

        @Override
        void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(arguments, lineNumber);
        }

        @Override
        void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(arguments, lineNumber);
        }

        private void initialize(String[] arguments, int lineNumber) throws DecodeException {
            if (arguments.length != 0) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber,
                        0, arguments.length);
                throw DecodeException.fatalError(message);
            }
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            DN dn = templateEntry.getDN();
            if (dn != null && !dn.isRootDN()) {
                templateValue.append(dn.rdn());
            }
            return TagResult.SUCCESS;
        }
    }

    /**
     * Tag that is used to include a sequentially-incrementing integer in the
     * generated values.
     */
    static class SequentialTag extends TemplateTag {
        /** Indicates whether to reset for each parent. */
        private boolean resetOnNewParents = true;

        /** The initial value in the sequence. */
        private int initialValue;

        /** The next value in the sequence. */
        private int nextValue;

        @Override
        String getName() {
            return "Sequential";
        }

        @Override
        boolean allowedInBranch() {
            return true;
        }

        @Override
        void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(arguments, lineNumber);
        }

        @Override
        void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(arguments, lineNumber);
        }

        private void initialize(String[] arguments, int lineNumber) throws DecodeException {
            if (arguments.length < 0 || arguments.length > 2) {
                throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(
                        getName(), lineNumber, 0, 2, arguments.length));
            }
            if (arguments.length > 0) {
                initializeValue(arguments[0], lineNumber);
                if (arguments.length > 1) {
                    initializeReset(arguments[1], lineNumber);
                }
            }
        }

        private void initializeReset(String resetValue, int lineNumber) throws DecodeException {
            if ("true".equalsIgnoreCase(resetValue)) {
                resetOnNewParents = true;
            } else if ("false".equalsIgnoreCase(resetValue)) {
                resetOnNewParents = false;
            } else {
                throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_TAG_CANNOT_PARSE_AS_BOOLEAN.get(
                        resetValue, getName(), lineNumber));
            }
        }

        private void initializeValue(String value, int lineNumber) throws DecodeException {
            try {
                initialValue = Integer.parseInt(value);
            } catch (NumberFormatException nfe) {
                throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_TAG_CANNOT_PARSE_AS_INTEGER.get(
                        value, getName(), lineNumber));
            }
            nextValue = initialValue;
        }

        @Override
        void initializeForParent(TemplateEntry parentEntry) {
            if (resetOnNewParents) {
                nextValue = initialValue;
            }
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            templateValue.append(nextValue++);
            return TagResult.SUCCESS;
        }
    }

    /**
     * Tag that is used to hold static text (i.e., text that appears outside of
     * any tag).
     */
    static class StaticTextTag extends TemplateTag {

        /** The static text to include. */
        private String text = "";

        @Override
        String getName() {
            return "StaticText";
        }

        @Override
        boolean allowedInBranch() {
            return true;
        }

        @Override
        void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(arguments, lineNumber);
        }

        @Override
        void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws DecodeException {
            initialize(arguments, lineNumber);
        }

        private void initialize(String[] arguments, int lineNumber) throws DecodeException {
            if (arguments.length != 1) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber,
                        1, arguments.length);
                throw DecodeException.fatalError(message);
            }
            text = arguments[0];
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            templateValue.append(text);
            return TagResult.SUCCESS;
        }
    }

    /**
     * Tag used to include the DN of the current entry in the attribute value,
     * with underscores in place of the commas.
     */
    static class UnderscoreDNTag extends DNTag {

        @Override
        String getName() {
            return "_DN";
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            return generateValue(templateEntry, templateValue, "_");
        }
    }

    /**
     * Tag used to include the DN of the parent entry in the attribute value,
     * with underscores in place of commas.
     */
    static class UnderscoreParentDNTag extends ParentTag {

        @Override
        String getName() {
            return "_ParentDN";
        }

        @Override
        TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            DN parentDN = templateEntry.getParentDN();
            if (parentDN == null || parentDN.isRootDN()) {
                return TagResult.SUCCESS;
            }
            templateValue.append(parentDN.rdn());
            for (int i = 1; i < parentDN.size(); i++) {
                templateValue.append("_");
                templateValue.append(parentDN.parent(i).rdn());
            }

            return TagResult.SUCCESS;
        }
    }
}
