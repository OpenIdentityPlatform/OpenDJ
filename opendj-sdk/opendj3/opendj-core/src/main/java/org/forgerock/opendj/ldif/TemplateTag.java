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

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.TemplateFile.Branch;
import org.forgerock.opendj.ldif.TemplateFile.Template;
import org.forgerock.opendj.ldif.TemplateFile.TemplateEntry;
import org.forgerock.opendj.ldif.TemplateFile.TemplateValue;

/**
 * Represents a tag that may be used in a template line when generating entries.
 * It can be used to generate content.
 *
 * @see TemplateFile
 * @see MakeLDIFEntryReader
 */
abstract class TemplateTag {

    /**
     * Retrieves the name for this tag.
     *
     * @return The name for this tag.
     */
    public abstract String getName();

    /**
     * Indicates whether this tag is allowed for use in the extra lines for
     * branches.
     *
     * @return <CODE>true</CODE> if this tag may be used in branch definitions,
     *         or <CODE>false</CODE> if not.
     */
    public abstract boolean allowedInBranch();

    /**
     * Performs any initialization for this tag that may be needed while parsing
     * a branch definition.
     *
     * @param schema
     *            schema used to create attributes
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
     * @throws MakeLDIFException
     *             if a problem occurs
     */
    public void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
            int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
        // No implementation required by default.
    }

    /**
     * Performs any initialization for this tag that may be needed while parsing
     * a template definition.
     *
     * @param schema
     *            schema used to create attributes
     * @param templateFile
     *            The template file in which this tag is used.
     * @param template
     *            The template in which this tag is used.
     * @param arguments
     *            The set of arguments provided for this tag.
     * @param lineNumber
     *            The line number on which this tag appears in the template
     *            file.
     * @param warnings
     *            A list into which any appropriate warning messages may be
     *            placed.
     * @throws MakeLDIFException
     *             if a problem occurs
     */
    public abstract void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
            String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException;

    /**
     * Performs any initialization for this tag that may be needed when starting
     * to generate entries below a new parent.
     *
     * @param parentEntry
     *            The entry below which the new entries will be generated.
     */
    public void initializeForParent(TemplateEntry parentEntry) {
        // No implementation required by default.
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
    public abstract TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue);

    /**
     * Provides information about the result of tag processing.
     */
    static final class TagResult {
        /**
         * A tag result in which all components have a value of
         * <CODE>true</CODE>.
         */
        public static final TagResult SUCCESS_RESULT = new TagResult(true, true, true, true);

        /**
         * A tag result that indicates the value should not be included in the
         * entry, but all other processing should continue.
         */
        public static final TagResult OMIT_FROM_ENTRY = new TagResult(false, true, true, true);

        /**
         * A tag result in whihc all components have a value of
         * <CODE>false</CODE>.
         */
        public static final TagResult STOP_PROCESSING = new TagResult(false, false, false, false);

        /** Indicates whether to keep processing the associated line. */
        private boolean keepProcessingLine;

        /** Indicates whether to keep processing the associated entry. */
        private boolean keepProcessingEntry;

        /**
         * Indicates whether to keep processing entries below the associated
         * parent.
         */
        private boolean keepProcessingParent;

        /** Indicates whether to keep processing entries for the template file. */
        private boolean keepProcessingTemplateFile;

        /**
         * Creates a new tag result object with the provided information.
         *
         * @param keepProcessingLine
         *            Indicates whether to continue processing for the current
         *            line. If not, then the line will not be included in the
         *            entry.
         * @param keepProcessingEntry
         *            Indicates whether to continue processing for the current
         *            entry. If not, then the entry will not be included in the
         *            data.
         * @param keepProcessingParent
         *            Indicates whether to continue processing entries below the
         *            current parent in the template file.
         * @param keepProcessingTemplateFile
         *            Indicates whether to continue processing entries for the
         *            template file.
         */
        private TagResult(boolean keepProcessingLine, boolean keepProcessingEntry, boolean keepProcessingParent,
                boolean keepProcessingTemplateFile) {
            this.keepProcessingLine = keepProcessingLine;
            this.keepProcessingEntry = keepProcessingEntry;
            this.keepProcessingParent = keepProcessingParent;
            this.keepProcessingTemplateFile = keepProcessingTemplateFile;
        }

        /**
         * Indicates whether to continue processing for the current line. If
         * this is <CODE>false</CODE>, then the current line will not be
         * included in the entry. It will have no impact on whehter the entry
         * itself is included in the generated LDIF.
         *
         * @return <CODE>true</CODE> if the line should be included in the
         *         entry, or <CODE>false</CODE> if not.
         */
        public boolean keepProcessingLine() {
            return keepProcessingLine;
        }

        /**
         * Indicates whether to continue processing for the current entry. If
         * this is <CODE>false</CODE>, then the current entry will not be
         * included in the generated LDIF, and processing will resume with the
         * next entry below the current parent.
         *
         * @return <CODE>true</CODE> if the entry should be included in the
         *         generated LDIF, or <CODE>false</CODE> if not.
         */
        public boolean keepProcessingEntry() {
            return keepProcessingEntry;
        }

        /**
         * Indicates whether to continue processing entries below the current
         * parent. If this is <CODE>false</CODE>, then the current entry will
         * not be included, and processing will resume below the next parent in
         * the template file.
         *
         * @return <CODE>true</CODE> if processing for the current parent should
         *         continue, or <CODE>false</CODE> if not.
         */
        public boolean keepProcessingParent() {
            return keepProcessingParent;
        }

        /**
         * Indicates whether to keep processing entries for the template file.
         * If this is <CODE>false</CODE>, then LDIF processing will end
         * immediately (and the current entry will not be included).
         *
         * @return <CODE>true</CODE> if processing for the template file should
         *         continue, or <CODE>false</CODE> if not.
         */
        public boolean keepProcessingTemplateFile() {
            return keepProcessingTemplateFile;
        }
    }

    /**
     * This class defines a tag that is used to reference the value of a
     * specified attribute already defined in the entry.
     */
    static class AttributeValueTag extends TemplateTag {
        /** The attribute type that specifies which value should be used. */
        private AttributeType attributeType;

        /** The maximum number of characters to include from the value. */
        private int numCharacters;

        /**
         * Creates a new instance of this attribute value tag.
         */
        public AttributeValueTag() {
            attributeType = null;
            numCharacters = 0;
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "AttributeValue";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return true;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a branch definition.
         *
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
         */
        @Override
        public void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            if ((arguments.length < 1) || (arguments.length > 2)) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(), lineNumber,
                        1, 2, arguments.length);
                throw new MakeLDIFException(message);
            }

            String lowerName = arguments[0].toLowerCase();
            attributeType = schema.getAttributeType(lowerName);
            if (!branch.hasAttribute(attributeType)) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_UNDEFINED_ATTRIBUTE.get(arguments[0], lineNumber);
                throw new MakeLDIFException(message);
            }

            if (arguments.length == 2) {
                try {
                    numCharacters = Integer.parseInt(arguments[1]);
                    if (numCharacters < 0) {
                        LocalizableMessage message = ERR_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND.get(numCharacters, 0,
                                getName(), lineNumber);
                        throw new MakeLDIFException(message);
                    }
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[1], getName(),
                            lineNumber);
                    throw new MakeLDIFException(message);
                }
            } else {
                numCharacters = 0;
            }
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            if ((arguments.length < 1) || (arguments.length > 2)) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(), lineNumber,
                        1, 2, arguments.length);
                throw new MakeLDIFException(message);
            }

            String lowerName = arguments[0].toLowerCase();
            attributeType = schema.getAttributeType(lowerName);
            if (!template.hasAttribute(attributeType)) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_UNDEFINED_ATTRIBUTE.get(arguments[0], lineNumber);
                throw new MakeLDIFException(message);
            }

            if (arguments.length == 2) {
                try {
                    numCharacters = Integer.parseInt(arguments[1]);
                    if (numCharacters < 0) {
                        LocalizableMessage message = ERR_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND.get(numCharacters, 0,
                                getName(), lineNumber);
                        throw new MakeLDIFException(message);
                    }
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[1], getName(),
                            lineNumber);
                    throw new MakeLDIFException(message);
                }
            } else {
                numCharacters = 0;
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            TemplateValue v = templateEntry.getValue(attributeType);
            if (v == null) {
                // This is fine -- we just won't append anything.
                return TagResult.SUCCESS_RESULT;
            }

            if (numCharacters > 0) {
                String valueString = v.getValueAsString();
                if (valueString.length() > numCharacters) {
                    templateValue.append(valueString.substring(0, numCharacters));
                } else {
                    templateValue.append(valueString);
                }
            } else {
                templateValue.append(v.getValue());
            }

            return TagResult.SUCCESS_RESULT;
        }
    }

    /**
     * This class defines a tag that is used to include the DN of the current
     * entry in the attribute value.
     */
    static class DNTag extends TemplateTag {
        /** The number of DN components to include. */
        private int numComponents;

        /**
         * Creates a new instance of this DN tag.
         */
        public DNTag() {
            numComponents = 0;
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "DN";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return true;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a branch definition.
         *
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
         */
        @Override
        public void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            initializeInternal(templateFile, arguments, lineNumber);
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            initializeInternal(templateFile, arguments, lineNumber);
        }

        /**
         * Performs any initialization for this tag that may be needed for this
         * tag.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @throws MakeLDIFException
         *             If a problem occurs while initializing this tag.
         */
        private void initializeInternal(TemplateFile templateFile, String[] arguments, int lineNumber)
                throws MakeLDIFException {
            if (arguments.length == 0) {
                numComponents = 0;
            } else if (arguments.length == 1) {
                try {
                    numComponents = Integer.parseInt(arguments[0]);
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[0], getName(),
                            lineNumber);
                    throw new MakeLDIFException(message);
                }
            } else {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(), lineNumber,
                        0, 1, arguments.length);
                throw new MakeLDIFException(message);
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            DN dn = templateEntry.getDN();
            if ((dn == null) || dn.isRootDN()) {
                return TagResult.SUCCESS_RESULT;
            }

            if (numComponents == 0) {
                templateValue.append(dn.toNormalizedString());
            } else if (numComponents > 0) {
                int count = Math.min(numComponents, dn.size());
                templateValue.append(dn.rdn());
                for (int i = 1; i < count; i++) {
                    templateValue.append(",");
                    templateValue.append(dn.parent(i).rdn());
                }
            } else {
                int size = dn.size();
                int count = Math.min(Math.abs(numComponents), size);

                templateValue.append(dn.parent(size - count).rdn());
                for (int i = 1; i < count; i++) {
                    templateValue.append(",");
                    templateValue.append(dn.parent(size - count + i).rdn());
                }
            }

            return TagResult.SUCCESS_RESULT;
        }
    }

    /**
     * This class defines a tag that is used provide values from a text file.
     * The file should have one value per line. Access to the values may be
     * either at random or in sequential order.
     */
    static class FileTag extends TemplateTag {
        /**
         * Indicates whether the values should be selected sequentially or at
         * random.
         */
        private boolean sequential;

        /** The file containing the data. */
        private File dataFile;

        /** The index used for sequential access. */
        private int nextIndex;

        /** The random number generator for this tag. */
        private Random random;

        /** The array of lines read from the file. */
        private String[] fileLines;

        /**
         * Creates a new instance of this file tag.
         */
        public FileTag() {
            sequential = false;
            dataFile = null;
            nextIndex = 0;
            random = null;
            fileLines = null;
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "File";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return true;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a branch definition.
         *
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
         */
        @Override
        public void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            initializeInternal(templateFile, arguments, lineNumber, warnings);
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            initializeInternal(templateFile, arguments, lineNumber, warnings);
        }

        /**
         * Performs any initialization for this tag that may be needed.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         * @throws MakeLDIFException
         *             If a problem occurs while initializing this tag.
         */
        private void initializeInternal(TemplateFile templateFile, String[] arguments, int lineNumber,
                List<LocalizableMessage> warnings) throws MakeLDIFException {
            random = templateFile.getRandom();

            // There must be at least one argument, and possibly two.
            if ((arguments.length < 1) || (arguments.length > 2)) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(), lineNumber,
                        1, 2, arguments.length);
                throw new MakeLDIFException(message);
            }

            // The first argument should be the path to the file.
            dataFile = templateFile.getFile(arguments[0]);
            if ((dataFile == null) || (!dataFile.exists())) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_FIND_FILE.get(arguments[0], getName(), lineNumber);
                throw new MakeLDIFException(message);
            }

            // If there is a second argument, then it should be either
            // "sequential" or "random". If there isn't one, then we should assume "random".
            if (arguments.length == 2) {
                if (arguments[1].equalsIgnoreCase("sequential")) {
                    sequential = true;
                    nextIndex = 0;
                } else if (arguments[1].equalsIgnoreCase("random")) {
                    sequential = false;
                } else {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_FILE_ACCESS_MODE.get(arguments[1], getName(),
                            lineNumber);
                    throw new MakeLDIFException(message);
                }
            } else {
                sequential = false;
            }

            // See if the file has already been read into memory. If not, then
            // read it.
            try {
                fileLines = templateFile.getFileLines(dataFile);
            } catch (IOException ioe) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_READ_FILE.get(arguments[0], getName(), lineNumber,
                        String.valueOf(ioe));
                throw new MakeLDIFException(message, ioe);
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            if (sequential) {
                templateValue.append(fileLines[nextIndex++]);
                if (nextIndex >= fileLines.length) {
                    nextIndex = 0;
                }
            } else {
                templateValue.append(fileLines[random.nextInt(fileLines.length)]);
            }

            return TagResult.SUCCESS_RESULT;
        }
    }

    /**
     * This class defines a tag that is used to include a first name in the
     * attribute value.
     */
    static class FirstNameTag extends TemplateTag {
        /** The template file with which this tag is associated. */
        private TemplateFile templateFile;

        /**
         * Creates a new instance of this first name tag.
         */
        public FirstNameTag() {
            // No implementation required.
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "First";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return false;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            this.templateFile = templateFile;

            if (arguments.length != 0) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber, 0,
                        arguments.length);
                throw new MakeLDIFException(message);
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            templateValue.append(templateFile.getFirstName());
            return TagResult.SUCCESS_RESULT;
        }
    }

    /**
     * This class defines a tag that is used to include a GUID in the attribute
     * value.
     */
    static class GUIDTag extends TemplateTag {
        /**
         * Creates a new instance of this GUID tag.
         */
        public GUIDTag() {
            // No implementation required.
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "GUID";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return true;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a branch definition.
         *
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
         */
        @Override
        public void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            if (arguments.length != 0) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber, 0,
                        arguments.length);
                throw new MakeLDIFException(message);
            }
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            if (arguments.length != 0) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber, 0,
                        arguments.length);
                throw new MakeLDIFException(message);
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            templateValue.append(UUID.randomUUID().toString());
            return TagResult.SUCCESS_RESULT;
        }
    }

    /**
     * This class defines a tag that is used to base presence of one attribute
     * on the absence of another attribute and/or attribute value.
     */
    static class IfAbsentTag extends TemplateTag {
        /** The attribute type for which to make the determination. */
        private AttributeType attributeType;

        /** The value for which to make the determination. */
        private String assertionValue;

        /**
         * Creates a new instance of this ifabsent tag.
         */
        public IfAbsentTag() {
            attributeType = null;
            assertionValue = null;
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "IfAbsent";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return true;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a branch definition.
         *
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
         */
        @Override
        public void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            if ((arguments.length < 1) || (arguments.length > 2)) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(), lineNumber,
                        1, 2, arguments.length);
                throw new MakeLDIFException(message);
            }

            String lowerName = arguments[0].toLowerCase();
            AttributeType t = schema.getAttributeType(lowerName);
            if (!branch.hasAttribute(t)) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_UNDEFINED_ATTRIBUTE.get(arguments[0], lineNumber);
                throw new MakeLDIFException(message);
            }

            if (arguments.length == 2) {
                assertionValue = arguments[1];
            } else {
                assertionValue = null;
            }
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            if ((arguments.length < 1) || (arguments.length > 2)) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(), lineNumber,
                        1, 2, arguments.length);
                throw new MakeLDIFException(message);
            }

            String lowerName = arguments[0].toLowerCase();
            attributeType = schema.getAttributeType(lowerName);
            if (!template.hasAttribute(attributeType)) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_UNDEFINED_ATTRIBUTE.get(arguments[0], lineNumber);
                throw new MakeLDIFException(message);
            }

            if (arguments.length == 2) {
                assertionValue = arguments[1];
            } else {
                assertionValue = null;
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            List<TemplateValue> values = templateEntry.getValues(attributeType);
            if ((values == null) || values.isEmpty()) {
                return TagResult.SUCCESS_RESULT;
            }

            if (assertionValue == null) {
                return TagResult.OMIT_FROM_ENTRY;
            } else {
                for (TemplateValue v : values) {
                    if (assertionValue.equals(v.getValueAsString())) {
                        return TagResult.OMIT_FROM_ENTRY;
                    }
                }

                return TagResult.SUCCESS_RESULT;
            }
        }
    }

    /**
     * This class defines a tag that is used to base presence of one attribute
     * on the presence of another attribute and/or attribute value.
     */
    static class IfPresentTag extends TemplateTag {
        /** The attribute type for which to make the determination. */
        private AttributeType attributeType;

        /** The value for which to make the determination. */
        private String assertionValue;

        /**
         * Creates a new instance of this ifpresent tag.
         */
        public IfPresentTag() {
            attributeType = null;
            assertionValue = null;
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "IfPresent";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return true;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a branch definition.
         *
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
         */
        @Override
        public void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            if ((arguments.length < 1) || (arguments.length > 2)) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(), lineNumber,
                        1, 2, arguments.length);
                throw new MakeLDIFException(message);
            }

            String lowerName = arguments[0].toLowerCase();
            AttributeType t = schema.getAttributeType(lowerName);
            if (!branch.hasAttribute(t)) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_UNDEFINED_ATTRIBUTE.get(arguments[0], lineNumber);
                throw new MakeLDIFException(message);
            }

            if (arguments.length == 2) {
                assertionValue = arguments[1];
            } else {
                assertionValue = null;
            }
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            if ((arguments.length < 1) || (arguments.length > 2)) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(), lineNumber,
                        1, 2, arguments.length);
                throw new MakeLDIFException(message);
            }

            String lowerName = arguments[0].toLowerCase();
            attributeType = schema.getAttributeType(lowerName);
            if (!template.hasAttribute(attributeType)) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_UNDEFINED_ATTRIBUTE.get(arguments[0], lineNumber);
                throw new MakeLDIFException(message);
            }

            if (arguments.length == 2) {
                assertionValue = arguments[1];
            } else {
                assertionValue = null;
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            List<TemplateValue> values = templateEntry.getValues(attributeType);
            if ((values == null) || values.isEmpty()) {
                return TagResult.OMIT_FROM_ENTRY;
            }

            if (assertionValue == null) {
                return TagResult.SUCCESS_RESULT;
            } else {
                for (TemplateValue v : values) {
                    if (assertionValue.equals(v.getValueAsString())) {
                        return TagResult.SUCCESS_RESULT;
                    }
                }

                return TagResult.OMIT_FROM_ENTRY;
            }
        }
    }

    /**
     * This class defines a tag that is used to include a last name in the
     * attribute value.
     */
    static class LastNameTag extends TemplateTag {
        /** The template file with which this tag is associated. */
        private TemplateFile templateFile;

        /**
         * Creates a new instance of this last name tag.
         */
        public LastNameTag() {
            // No implementation required.
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "Last";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return false;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            this.templateFile = templateFile;

            if (arguments.length != 0) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber, 0,
                        arguments.length);
                throw new MakeLDIFException(message);
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            templateValue.append(templateFile.getLastName());
            return TagResult.SUCCESS_RESULT;
        }
    }

    /**
     * This class defines a tag that may be used to select a value from a
     * pre-defined list, optionally defining weights for each value that can
     * impact the likelihood of a given item being selected.
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

        /**
         * Creates a new instance of this list tag.
         */
        public ListTag() {
            // No implementation required.
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "List";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return true;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a branch definition.
         *
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
         */
        @Override
        public void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            initializeInternal(templateFile, arguments, lineNumber, warnings);
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            initializeInternal(templateFile, arguments, lineNumber, warnings);
        }

        /**
         * Performs any initialization for this tag that may be needed for this
         * tag.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         * @throws MakeLDIFException
         *             If a problem occurs while initializing this tag.
         */
        private void initializeInternal(TemplateFile templateFile, String[] arguments, int lineNumber,
                List<LocalizableMessage> warnings) throws MakeLDIFException {
            if (arguments.length == 0) {
                throw new MakeLDIFException(ERR_MAKELDIF_TAG_LIST_NO_ARGUMENTS.get(lineNumber));
            }

            valueStrings = new String[arguments.length];
            valueWeights = new int[arguments.length];
            cumulativeWeight = 0;
            random = templateFile.getRandom();

            for (int i = 0; i < arguments.length; i++) {
                String s = arguments[i];

                int weight = 1;
                int semicolonPos = s.lastIndexOf(';');
                if (semicolonPos >= 0) {
                    try {
                        weight = Integer.parseInt(s.substring(semicolonPos + 1));
                        s = s.substring(0, semicolonPos);
                    } catch (Exception e) {
                        warnings.add(WARN_MAKELDIF_TAG_LIST_INVALID_WEIGHT.get(lineNumber, s));
                    }
                }

                cumulativeWeight += weight;
                valueStrings[i] = s;
                valueWeights[i] = cumulativeWeight;
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            int selectedWeight = random.nextInt(cumulativeWeight) + 1;
            for (int i = 0; i < valueWeights.length; i++) {
                if (selectedWeight <= valueWeights[i]) {
                    templateValue.append(valueStrings[i]);
                    break;
                }
            }

            return TagResult.SUCCESS_RESULT;
        }
    }

    /**
     * This class defines a tag that is used to include the DN of the parent
     * entry in the attribute value.
     */
    static class ParentDNTag extends TemplateTag {
        /**
         * Creates a new instance of this parent DN tag.
         */
        public ParentDNTag() {
            // No implementation required.
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "ParentDN";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return false;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            if (arguments.length != 0) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber, 0,
                        arguments.length);
                throw new MakeLDIFException(message);
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            DN parentDN = templateEntry.getParentDN();
            if ((parentDN == null) || parentDN.isRootDN()) {
                return TagResult.SUCCESS_RESULT;
            }

            templateValue.append(parentDN);
            return TagResult.SUCCESS_RESULT;
        }
    }

    /**
     * This class defines a tag that is used to indicate that a value should
     * only be included in a percentage of the entries.
     */
    static class PresenceTag extends TemplateTag {
        /** The percentage of the entries in which this attribute value should */
        /** appear. */
        private int percentage;

        /** The random number generator for this tag. */
        private Random random;

        /**
         * Creates a new instance of this presence tag.
         */
        public PresenceTag() {
            percentage = 100;
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "Presence";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return true;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a branch definition.
         *
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
         */
        @Override
        public void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            initializeInternal(templateFile, arguments, lineNumber);
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            initializeInternal(templateFile, arguments, lineNumber);
        }

        /**
         * Performs any initialization for this tag that may be needed for this
         * tag.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @throws MakeLDIFException
         *             If a problem occurs while initializing this tag.
         */
        private void initializeInternal(TemplateFile templateFile, String[] arguments, int lineNumber)
                throws MakeLDIFException {
            random = templateFile.getRandom();

            if (arguments.length != 1) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber, 1,
                        arguments.length);
                throw new MakeLDIFException(message);
            }

            try {
                percentage = Integer.parseInt(arguments[0]);

                if (percentage < 0) {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND.get(percentage, 0,
                            getName(), lineNumber);
                    throw new MakeLDIFException(message);
                } else if (percentage > 100) {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_INTEGER_ABOVE_UPPER_BOUND.get(percentage, 100,
                            getName(), lineNumber);
                    throw new MakeLDIFException(message);
                }
            } catch (NumberFormatException nfe) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[0], getName(),
                        lineNumber);
                throw new MakeLDIFException(message);
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            int intValue = random.nextInt(100);
            if (intValue < percentage) {
                return TagResult.SUCCESS_RESULT;
            } else {
                return TagResult.OMIT_FROM_ENTRY;
            }
        }
    }

    /**
     * This class defines a tag that may be used to generate random values. It
     * has a number of subtypes based on the type of information that should be
     * generated, including:
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
        /**
         * The value that indicates that the value is to be generated from a
         * fixed number of characters from a given character set.
         */
        public static final int RANDOM_TYPE_CHARS_FIXED = 1;

        /**
         * The value that indicates that the value is to be generated from a
         * variable number of characters from a given character set.
         */
        public static final int RANDOM_TYPE_CHARS_VARIABLE = 2;

        /**
         * The value that indicates that the value should be a random number.
         */
        public static final int RANDOM_TYPE_NUMERIC = 3;

        /**
         * The value that indicates that the value should be a random month.
         */
        public static final int RANDOM_TYPE_MONTH = 4;

        /**
         * The value that indicates that the value should be a telephone number.
         */
        public static final int RANDOM_TYPE_TELEPHONE = 5;

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

        /** The number of characters between the minimum and maximum length */
        /** (inclusive). */
        private int lengthRange;

        /** The maximum number of characters to include in the value. */
        private int maxLength;

        /** The minimum number of characters to include in the value. */
        private int minLength;

        /** The type of random value that should be generated. */
        private int randomType;

        /** The maximum numeric value that should be generated. */
        private long maxValue;

        /** The minimum numeric value that should be generated. */
        private long minValue;

        /**
         * The number of values between the minimum and maximum value
         * (inclusive).
         */
        private long valueRange;

        /** The random number generator for this tag. */
        private Random random;

        /**
         * Creates a new instance of this random tag.
         */
        public RandomTag() {
            characterSet = null;
            decimalFormat = null;
            lengthRange = 1;
            maxLength = 0;
            minLength = 0;
            randomType = 0;
            maxValue = 0L;
            minValue = 0L;
            valueRange = 1L;
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "Random";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return true;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a branch definition.
         *
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
         */
        @Override
        public void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            initializeInternal(templateFile, arguments, lineNumber, warnings);
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            initializeInternal(templateFile, arguments, lineNumber, warnings);
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing either a branch or template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         * @throws MakeLDIFException
         *             If a problem occurs while initializing this tag.
         */
        private void initializeInternal(TemplateFile templateFile, String[] arguments, int lineNumber,
                List<LocalizableMessage> warnings) throws MakeLDIFException {
            random = templateFile.getRandom();

            // There must be at least one argument, to specify the type of
            // random value to generate.
            if ((arguments == null) || (arguments.length == 0)) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_NO_RANDOM_TYPE_ARGUMENT.get(lineNumber);
                throw new MakeLDIFException(message);
            }

            int numArgs = arguments.length;
            String randomTypeString = arguments[0].toLowerCase();

            if (randomTypeString.equals("alpha")) {
                characterSet = ALPHA_CHARS;
                decodeLength(arguments, 1, lineNumber, warnings);
            } else if (randomTypeString.equals("numeric")) {
                if (numArgs == 2) {
                    randomType = RANDOM_TYPE_CHARS_FIXED;
                    characterSet = NUMERIC_CHARS;

                    try {
                        minLength = Integer.parseInt(arguments[1]);

                        if (minLength < 0) {
                            LocalizableMessage message = ERR_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND.get(minLength, 0,
                                    getName(), lineNumber);
                            throw new MakeLDIFException(message);
                        } else if (minLength == 0) {
                            LocalizableMessage message = WARN_MAKELDIF_TAG_WARNING_EMPTY_VALUE.get(lineNumber);
                            warnings.add(message);
                        }
                    } catch (NumberFormatException nfe) {
                        LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[1],
                                getName(), lineNumber);
                        throw new MakeLDIFException(message, nfe);
                    }
                } else if ((numArgs == 3) || (numArgs == 4)) {
                    randomType = RANDOM_TYPE_NUMERIC;

                    if (numArgs == 4) {
                        try {
                            decimalFormat = new DecimalFormat(arguments[3]);
                        } catch (Exception e) {
                            LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_FORMAT_STRING.get(arguments[3],
                                    getName(), lineNumber);
                            throw new MakeLDIFException(message, e);
                        }
                    } else {
                        decimalFormat = null;
                    }

                    try {
                        minValue = Long.parseLong(arguments[1]);
                    } catch (NumberFormatException nfe) {
                        LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[1],
                                getName(), lineNumber);
                        throw new MakeLDIFException(message, nfe);
                    }

                    try {
                        maxValue = Long.parseLong(arguments[2]);
                        if (maxValue < minValue) {
                            LocalizableMessage message = ERR_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND.get(maxValue,
                                    minValue, getName(), lineNumber);
                            throw new MakeLDIFException(message);
                        }

                        valueRange = maxValue - minValue + 1;
                    } catch (NumberFormatException nfe) {
                        LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[2],
                                getName(), lineNumber);
                        throw new MakeLDIFException(message, nfe);
                    }
                } else {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(),
                            lineNumber, 2, 4, numArgs);
                    throw new MakeLDIFException(message);
                }
            } else if (randomTypeString.equals("alphanumeric")) {
                characterSet = ALPHANUMERIC_CHARS;
                decodeLength(arguments, 1, lineNumber, warnings);
            } else if (randomTypeString.equals("chars")) {
                if ((numArgs < 3) || (numArgs > 4)) {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(),
                            lineNumber, 3, 4, numArgs);
                    throw new MakeLDIFException(message);
                }

                characterSet = arguments[1].toCharArray();
                decodeLength(arguments, 2, lineNumber, warnings);
            } else if (randomTypeString.equals("hex")) {
                characterSet = HEX_CHARS;
                decodeLength(arguments, 1, lineNumber, warnings);
            } else if (randomTypeString.equals("base64")) {
                characterSet = BASE64_CHARS;
                decodeLength(arguments, 1, lineNumber, warnings);
            } else if (randomTypeString.equals("month")) {
                randomType = RANDOM_TYPE_MONTH;

                if (numArgs == 1) {
                    maxLength = 0;
                } else if (numArgs == 2) {
                    try {
                        maxLength = Integer.parseInt(arguments[1]);
                        if (maxLength <= 0) {
                            LocalizableMessage message = ERR_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND.get(maxLength, 1,
                                    getName(), lineNumber);
                            throw new MakeLDIFException(message);
                        }
                    } catch (NumberFormatException nfe) {
                        LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[1],
                                getName(), lineNumber);
                        throw new MakeLDIFException(message, nfe);
                    }
                } else {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(),
                            lineNumber, 1, 2, numArgs);
                    throw new MakeLDIFException(message);
                }
            } else if (randomTypeString.equals("telephone")) {
                randomType = RANDOM_TYPE_TELEPHONE;
            } else {
                LocalizableMessage message = ERR_MAKELDIF_TAG_UNKNOWN_RANDOM_TYPE.get(lineNumber, randomTypeString);
                throw new MakeLDIFException(message);
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
                throws MakeLDIFException {
            int numArgs = arguments.length - startPos + 1;

            if (numArgs == 2) {
                // There is a fixed number of characters in the value.
                randomType = RANDOM_TYPE_CHARS_FIXED;

                try {
                    minLength = Integer.parseInt(arguments[startPos]);

                    if (minLength < 0) {
                        LocalizableMessage message = ERR_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND.get(minLength, 0,
                                getName(), lineNumber);
                        throw new MakeLDIFException(message);
                    } else if (minLength == 0) {
                        LocalizableMessage message = WARN_MAKELDIF_TAG_WARNING_EMPTY_VALUE.get(lineNumber);
                        warnings.add(message);
                    }
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[startPos],
                            getName(), lineNumber);
                    throw new MakeLDIFException(message, nfe);
                }
            } else if (numArgs == 3) {
                // There are minimum and maximum lengths.
                randomType = RANDOM_TYPE_CHARS_VARIABLE;

                try {
                    minLength = Integer.parseInt(arguments[startPos]);

                    if (minLength < 0) {
                        LocalizableMessage message = ERR_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND.get(minLength, 0,
                                getName(), lineNumber);
                        throw new MakeLDIFException(message);
                    }
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[startPos],
                            getName(), lineNumber);
                    throw new MakeLDIFException(message, nfe);
                }

                try {
                    maxLength = Integer.parseInt(arguments[startPos + 1]);
                    lengthRange = maxLength - minLength + 1;

                    if (maxLength < minLength) {
                        LocalizableMessage message = ERR_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND.get(maxLength,
                                minLength, getName(), lineNumber);
                        throw new MakeLDIFException(message);
                    } else if (maxLength == 0) {
                        LocalizableMessage message = WARN_MAKELDIF_TAG_WARNING_EMPTY_VALUE.get(lineNumber);
                        warnings.add(message);
                    }
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[startPos + 1],
                            getName(), lineNumber);
                    throw new MakeLDIFException(message, nfe);
                }
            } else {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(), lineNumber,
                        startPos + 1, startPos + 2, numArgs);
                throw new MakeLDIFException(message);
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            switch (randomType) {
            case RANDOM_TYPE_CHARS_FIXED:
                for (int i = 0; i < minLength; i++) {
                    templateValue.append(characterSet[random.nextInt(characterSet.length)]);
                }
                break;

            case RANDOM_TYPE_CHARS_VARIABLE:
                int numChars = random.nextInt(lengthRange) + minLength;
                for (int i = 0; i < numChars; i++) {
                    templateValue.append(characterSet[random.nextInt(characterSet.length)]);
                }
                break;

            case RANDOM_TYPE_NUMERIC:
                long randomValue = ((random.nextLong() & 0x7FFFFFFFFFFFFFFFL) % valueRange) + minValue;
                if (decimalFormat == null) {
                    templateValue.append(randomValue);
                } else {
                    templateValue.append(decimalFormat.format(randomValue));
                }
                break;

            case RANDOM_TYPE_MONTH:
                String month = MONTHS[random.nextInt(MONTHS.length)];
                if ((maxLength == 0) || (month.length() <= maxLength)) {
                    templateValue.append(month);
                } else {
                    templateValue.append(month.substring(0, maxLength));
                }
                break;

            case RANDOM_TYPE_TELEPHONE:
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

            return TagResult.SUCCESS_RESULT;
        }
    }

    /**
     * This class defines a tag that is used to include the RDN of the current
     * entry in the attribute value.
     */
    static class RDNTag extends TemplateTag {
        /**
         * Creates a new instance of this RDN tag.
         */
        public RDNTag() {
            // No implementation required.
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "RDN";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return true;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a branch definition.
         *
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
         */
        @Override
        public void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            if (arguments.length != 0) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber, 0,
                        arguments.length);
                throw new MakeLDIFException(message);
            }
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            if (arguments.length != 0) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber, 0,
                        arguments.length);
                throw new MakeLDIFException(message);
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            DN dn = templateEntry.getDN();
            if ((dn == null) || dn.isRootDN()) {
                return TagResult.SUCCESS_RESULT;
            } else {
                templateValue.append(dn.rdn());
                return TagResult.SUCCESS_RESULT;
            }
        }
    }

    /**
     * This class defines a tag that is used to include a
     * sequentially-incrementing integer in the generated values.
     */
    static class SequentialTag extends TemplateTag {
        /** Indicates whether to reset for each parent. */
        private boolean resetOnNewParents;

        /** The initial value in the sequence. */
        private int initialValue;

        /** The next value in the sequence. */
        private int nextValue;

        /**
         * Creates a new instance of this sequential tag.
         */
        public SequentialTag() {
            // No implementation required.
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "Sequential";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return true;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a branch definition.
         *
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
         */
        @Override
        public void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            initializeInternal(templateFile, arguments, lineNumber);
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            initializeInternal(templateFile, arguments, lineNumber);
        }

        /**
         * Performs any initialization for this tag that may be needed for this
         * tag.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @throws MakeLDIFException
         *             If a problem occurs while initializing this tag.
         */
        private void initializeInternal(TemplateFile templateFile, String[] arguments, int lineNumber)
                throws MakeLDIFException {
            switch (arguments.length) {
            case 0:
                initialValue = 0;
                nextValue = 0;
                resetOnNewParents = true;
                break;
            case 1:
                try {
                    initialValue = Integer.parseInt(arguments[0]);
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[0], getName(),
                            lineNumber);
                    throw new MakeLDIFException(message);
                }

                nextValue = initialValue;
                resetOnNewParents = true;
                break;
            case 2:
                try {
                    initialValue = Integer.parseInt(arguments[0]);
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[0], getName(),
                            lineNumber);
                    throw new MakeLDIFException(message);
                }

                if (arguments[1].equalsIgnoreCase("true")) {
                    resetOnNewParents = true;
                } else if (arguments[1].equalsIgnoreCase("false")) {
                    resetOnNewParents = false;
                } else {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_BOOLEAN.get(arguments[1], getName(),
                            lineNumber);
                    throw new MakeLDIFException(message);
                }

                nextValue = initialValue;
                break;
            default:
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(), lineNumber,
                        0, 2, arguments.length);
                throw new MakeLDIFException(message);
            }
        }

        /**
         * Performs any initialization for this tag that may be needed when
         * starting to generate entries below a new parent.
         *
         * @param parentEntry
         *            The entry below which the new entries will be generated.
         */
        public void initializeForParent(TemplateEntry parentEntry) {
            if (resetOnNewParents) {
                nextValue = initialValue;
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            templateValue.append(nextValue++);
            return TagResult.SUCCESS_RESULT;
        }
    }

    /**
     * This class defines a tag that is used to hold static text (i.e., text
     * that appears outside of any tag).
     */
    static class StaticTextTag extends TemplateTag {

        /** The static text to include in the LDIF. */
        private String text;

        /**
         * Creates a new instance of this static text tag.
         */
        public StaticTextTag() {
            text = "";
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "StaticText";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return true;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a branch definition.
         *
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
         */
        @Override
        public void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            if (arguments.length != 1) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber, 1,
                        arguments.length);
                throw new MakeLDIFException(message);
            }

            text = arguments[0];
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            if (arguments.length != 1) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber, 1,
                        arguments.length);
                throw new MakeLDIFException(message);
            }

            text = arguments[0];
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            templateValue.append(text);
            return TagResult.SUCCESS_RESULT;
        }
    }

    /**
     * This class defines a tag that is used to include the DN of the current
     * entry in the attribute value, with underscores in place of the commas.
     */
    static class UnderscoreDNTag extends TemplateTag {
        /** The number of DN components to include. */
        private int numComponents;

        /**
         * Creates a new instance of this DN tag.
         */
        public UnderscoreDNTag() {
            numComponents = 0;
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "_DN";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return true;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a branch definition.
         *
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
         */
        @Override
        public void initializeForBranch(Schema schema, TemplateFile templateFile, Branch branch, String[] arguments,
                int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            initializeInternal(templateFile, arguments, lineNumber);
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            initializeInternal(templateFile, arguments, lineNumber);
        }

        /**
         * Performs any initialization for this tag that may be needed for this
         * tag.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @throws MakeLDIFException
         *             TODO
         */
        private void initializeInternal(TemplateFile templateFile, String[] arguments, int lineNumber)
                throws MakeLDIFException {
            if (arguments.length == 0) {
                numComponents = 0;
            } else if (arguments.length == 1) {
                try {
                    numComponents = Integer.parseInt(arguments[0]);
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER.get(arguments[0], getName(),
                            lineNumber);
                    throw new MakeLDIFException(message);
                }
            } else {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT.get(getName(), lineNumber,
                        0, 1, arguments.length);
                throw new MakeLDIFException(message);
            }
        }

        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            DN dn = templateEntry.getDN();
            if ((dn == null) || dn.isRootDN()) {
                return TagResult.SUCCESS_RESULT;
            }

            if (numComponents == 0) {
                templateValue.append(dn.rdn());
                for (int i = 1; i < dn.size(); i++) {
                    templateValue.append("_");
                    templateValue.append(dn.parent(i).rdn());
                }
            } else if (numComponents > 0) {
                int count = Math.min(numComponents, dn.size());
                templateValue.append(dn.rdn());
                for (int i = 1; i < count; i++) {
                    templateValue.append(",");
                    templateValue.append(dn.parent(i).rdn());
                }
            } else {
                int size = dn.size();
                int count = Math.min(Math.abs(numComponents), size);

                templateValue.append(dn.parent(size - count).rdn());
                for (int i = 1; i < count; i++) {
                    templateValue.append(",");
                    templateValue.append(dn.parent(size - count + i).rdn());
                }
            }

            return TagResult.SUCCESS_RESULT;
        }
    }

    /**
     * This class defines a tag that is used to include the DN of the parent
     * entry in the attribute value, with underscores in place of commas.
     */
    static class UnderscoreParentDNTag extends TemplateTag {
        /**
         * Creates a new instance of this underscore parent DN tag.
         */
        public UnderscoreParentDNTag() {
            // No implementation required.
        }

        /**
         * Retrieves the name for this tag.
         *
         * @return The name for this tag.
         */
        public String getName() {
            return "_ParentDN";
        }

        /**
         * Indicates whether this tag is allowed for use in the extra lines for
         * branches.
         *
         * @return <CODE>true</CODE> if this tag may be used in branch
         *         definitions, or <CODE>false</CODE> if not.
         */
        public boolean allowedInBranch() {
            return false;
        }

        /**
         * Performs any initialization for this tag that may be needed while
         * parsing a template definition.
         *
         * @param templateFile
         *            The template file in which this tag is used.
         * @param template
         *            The template in which this tag is used.
         * @param arguments
         *            The set of arguments provided for this tag.
         * @param lineNumber
         *            The line number on which this tag appears in the template
         *            file.
         * @param warnings
         *            A list into which any appropriate warning messages may be
         *            placed.
         */
        @Override
        public void initializeForTemplate(Schema schema, TemplateFile templateFile, Template template,
                String[] arguments, int lineNumber, List<LocalizableMessage> warnings) throws MakeLDIFException {
            if (arguments.length != 0) {
                LocalizableMessage message = ERR_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT.get(getName(), lineNumber, 0,
                        arguments.length);
                throw new MakeLDIFException(message);
            }
        }


        /**
         * Generates the content for this tag by appending it to the provided
         * tag.
         *
         * @param templateEntry
         *            The entry for which this tag is being generated.
         * @param templateValue
         *            The template value to which the generated content should
         *            be appended.
         * @return The result of generating content for this tag.
         */
        public TagResult generateValue(TemplateEntry templateEntry, TemplateValue templateValue) {
            DN parentDN = templateEntry.getParentDN();
            if ((parentDN == null) || parentDN.isRootDN()) {
                return TagResult.SUCCESS_RESULT;
            }
            templateValue.append(parentDN.rdn());
            for (int i = 1; i < parentDN.size(); i++) {
                templateValue.append("_");
                templateValue.append(parentDN.parent(i).rdn());
            }

            return TagResult.SUCCESS_RESULT;
        }
    }
}
