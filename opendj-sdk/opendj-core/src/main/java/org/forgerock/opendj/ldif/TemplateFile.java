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
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldif;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.TemplateTag.AttributeValueTag;
import org.forgerock.opendj.ldif.TemplateTag.DNTag;
import org.forgerock.opendj.ldif.TemplateTag.FileTag;
import org.forgerock.opendj.ldif.TemplateTag.FirstNameTag;
import org.forgerock.opendj.ldif.TemplateTag.GUIDTag;
import org.forgerock.opendj.ldif.TemplateTag.IfAbsentTag;
import org.forgerock.opendj.ldif.TemplateTag.IfPresentTag;
import org.forgerock.opendj.ldif.TemplateTag.LastNameTag;
import org.forgerock.opendj.ldif.TemplateTag.ListTag;
import org.forgerock.opendj.ldif.TemplateTag.ParentDNTag;
import org.forgerock.opendj.ldif.TemplateTag.PresenceTag;
import org.forgerock.opendj.ldif.TemplateTag.RDNTag;
import org.forgerock.opendj.ldif.TemplateTag.RandomTag;
import org.forgerock.opendj.ldif.TemplateTag.SequentialTag;
import org.forgerock.opendj.ldif.TemplateTag.StaticTextTag;
import org.forgerock.opendj.ldif.TemplateTag.TagResult;
import org.forgerock.opendj.ldif.TemplateTag.UnderscoreDNTag;
import org.forgerock.opendj.ldif.TemplateTag.UnderscoreParentDNTag;
import org.forgerock.util.Pair;
import org.forgerock.util.Reject;
import org.forgerock.util.Utils;

/**
 * A template file allow to generate entries from a collection of constant
 * definitions, branches, and templates.
 *
 * @see EntryGenerator
 */
final class TemplateFile {

    /** Default resource path used if no resource path is provided. */
    private static final String DEFAULT_RESOURCES_PATH = "org/forgerock/opendj/ldif";

    /** Default template path used if no template file is provided. */
    private static final String DEFAULT_TEMPLATE_PATH = "example.template";

    /** The name of the file holding the list of first names. */
    private static final String FIRST_NAME_FILE = "first.names";

    /** The name of the file holding the list of last names. */
    private static final String LAST_NAME_FILE = "last.names";

    /** Default value for infinite number of entries. */
    private static final int INFINITE_ENTRIES = -1;

    /**
     * A map of the contents of various text files used during the parsing
     * process, mapped from absolute path to the array of lines in the file.
     */
    private final Map<String, String[]> fileLines = new HashMap<>();

    /** The index of the next first name value that should be used. */
    private int firstNameIndex;

    /** The index of the next last name value that should be used. */
    private int lastNameIndex;

    /**
     * A counter used to keep track of the number of times that the larger of
     * the first/last name list has been completed.
     */
    private int nameLoopCounter;

    /**
     * A counter that will be used in case we have exhausted all possible first
     * and last name combinations.
     */
    private int nameUniquenessCounter = 1;

    /** The set of branch definitions for this template file. */
    private final Map<DN, Branch> branches = new LinkedHashMap<>();

    /** The set of constant definitions for this template file. */
    private final Map<String, String> constants;

    /** The set of registered tags for this template file. */
    private final Map<String, TemplateTag> registeredTags = new LinkedHashMap<>();

    /** The set of template definitions for this template file. */
    private final Map<String, Template> templates = new LinkedHashMap<>();

    /** The random number generator for this template file. */
    private final Random random;

    /** The next first name that should be used. */
    private String firstName;

    /** The next last name that should be used. */
    private String lastName;

    /** Indicates whether branch entries should be generated. */
    private boolean generateBranches;

    /**
     * The resource path to use for filesystem elements that cannot be found
     * anywhere else.
     */
    private String resourcePath;

    /** The set of first names to use when generating the LDIF. */
    private String[] firstNames = new String[0];

    /** The set of last names to use when generating the LDIF. */
    private String[] lastNames = new String[0];

    /** Schema used to create attributes. */
    private final Schema schema;

    /**
     * Creates a new, empty template file structure.
     *
     * @param schema
     *            LDAP Schema to use.
     * @param constants
     *            Constants to use, override any constant defined in the
     *            template file. May be {@code null}.
     * @param resourcePath
     *            The path to the directory that may contain additional resource
     *            files needed during the generation process. May be
     *            {@code null}.
     * @throws IOException
     *             if a problem occurs when initializing
     */
    TemplateFile(Schema schema, Map<String, String> constants, String resourcePath) throws IOException {
        this(schema, constants, resourcePath, new Random(), true);
    }

    /**
     * Creates a new, empty template file structure.
     *
     * @param schema
     *            LDAP Schema to use.
     * @param constants
     *            Constants to use, override any constant defined in the
     *            template file. May be {@code null}.
     * @param resourcePath
     *            The path to the directory that may contain additional resource
     *            files needed during the generation process. May be
     *            {@code null}.
     * @param random
     *            The random number generator for this template file.
     * @param generateBranches
     *            Indicates whether branch entries should be generated.
     * @throws IOException
     *             if a problem occurs when initializing
     */
    TemplateFile(Schema schema, Map<String, String> constants, String resourcePath,
                    Random random, boolean generateBranches)
            throws IOException {
        Reject.ifNull(schema, random);
        this.generateBranches = generateBranches;
        this.schema = schema;
        this.constants = constants != null ? constants : new HashMap<String, String>();
        this.resourcePath = resourcePath;
        this.random = random;
        registerDefaultTags();
        retrieveFirstAndLastNames();
    }

    TemplateTag getTag(String lowerName) {
        return registeredTags.get(lowerName);
    }

    /**
     * Registers the set of tags that will always be available for use in
     * templates.
     */
    private void registerDefaultTags() {
        Class<?>[] defaultTagClasses = new Class<?>[] { AttributeValueTag.class, DNTag.class, FileTag.class,
            FirstNameTag.class, GUIDTag.class, IfAbsentTag.class, IfPresentTag.class, LastNameTag.class,
            ListTag.class, ParentDNTag.class, PresenceTag.class, RandomTag.class, RDNTag.class,
            SequentialTag.class, StaticTextTag.class, UnderscoreDNTag.class, UnderscoreParentDNTag.class };

        for (final Class<?> c : defaultTagClasses) {
            try {
                final TemplateTag t = (TemplateTag) c.newInstance();
                registeredTags.put(t.getName().toLowerCase(), t);
            } catch (Exception e) {
                // this is a programming error
                throw new RuntimeException(ERR_ENTRY_GENERATOR_CANNOT_INSTANTIATE_TAG.get(c.getName()).toString(), e);
            }
        }
    }

    Random getRandom() {
        return random;
    }

    private void retrieveFirstAndLastNames() throws IOException {
        BufferedReader first = null;
        try {
            first = getReader(FIRST_NAME_FILE);
            if (first == null) {
                throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_COULD_NOT_FIND_NAME_FILE.get(FIRST_NAME_FILE));
            }
            final List<String> names = readLines(first);
            firstNames = names.toArray(new String[names.size()]);
        } finally {
            Utils.closeSilently(first);
        }

        BufferedReader last = null;
        try {
            last = getReader(LAST_NAME_FILE);
            if (last == null) {
                throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_COULD_NOT_FIND_NAME_FILE.get(LAST_NAME_FILE));
            }
            final List<String> names = readLines(last);
            lastNames = names.toArray(new String[names.size()]);
        } finally {
            Utils.closeSilently(first);
        }
    }

    /**
     * Updates the first and last name indexes to choose new values. The
     * algorithm used is designed to ensure that the combination of first and
     * last names will never be repeated. It depends on the number of first
     * names and the number of last names being relatively prime. This method
     * should be called before beginning generation of each template entry.
     */
    void nextFirstAndLastNames() {
        firstName = firstNames[firstNameIndex++];
        lastName = lastNames[lastNameIndex++];

        // If we've already exhausted every possible combination
        // then append an integer to the last name.
        if (nameUniquenessCounter > 1) {
            lastName += nameUniquenessCounter;
        }

        if (firstNameIndex >= firstNames.length) {
            // We're at the end of the first name list, so start over.
            // If the first name list is larger than the last name list,
            // then we'll also need to set the last name index
            // to the next loop counter position.
            firstNameIndex = 0;
            if (firstNames.length > lastNames.length) {
                lastNameIndex = ++nameLoopCounter;
                if (lastNameIndex >= lastNames.length) {
                    lastNameIndex = 0;
                    nameUniquenessCounter++;
                }
            }
        }

        if (lastNameIndex >= lastNames.length) {
            // We're at the end of the last name list, so start over.
            // If the last name list is larger than the first name list,
            // then we'll also need to set the first name index
            // to the next loop counter position.
            lastNameIndex = 0;
            if (lastNames.length > firstNames.length) {
                firstNameIndex = ++nameLoopCounter;
                if (firstNameIndex >= firstNames.length) {
                    firstNameIndex = 0;
                    nameUniquenessCounter++;
                }
            }
        }
    }

    String getFirstName() {
        return firstName;
    }

    String getLastName() {
        return lastName;
    }

    /**
     * Parses the contents of the default template file definition, that will be
     * used to generate entries.
     *
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @throws IOException
     *             If a problem occurs while attempting to read data from the
     *             default template file.
     * @throws DecodeException
     *             If any other problem occurs while parsing the template file.
     */
    void parse(List<LocalizableMessage> warnings) throws IOException, DecodeException {
        parse(DEFAULT_TEMPLATE_PATH, warnings);
    }

    /**
     * Parses the contents of the provided file as an entry generator template
     * file definition.
     *
     * @param templateFilename
     *            The name of the file containing the template data.
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @throws IOException
     *             If a problem occurs while attempting to read data from the
     *             specified file.
     * @throws DecodeException
     *             If any other problem occurs while parsing the template file.
     */
    void parse(String templateFilename, List<LocalizableMessage> warnings) throws IOException, DecodeException {
        BufferedReader templateReader = null;
        try {
            templateReader = getReader(templateFilename);
            if (templateReader == null) {
                throw DecodeException.fatalError(
                        ERR_ENTRY_GENERATOR_COULD_NOT_FIND_TEMPLATE_FILE.get(templateFilename));
            }
            if (resourcePath == null) {
                // Use the template file directory as resource path
                final File file = getFile(templateFilename);
                if (file != null) {
                    resourcePath = file.getCanonicalFile().getParentFile().getAbsolutePath();
                }
            }
            final List<String> fileLines = readLines(templateReader);
            final String[] lines = fileLines.toArray(new String[fileLines.size()]);
            parse(lines, warnings);
        } finally {
            Utils.closeSilently(templateReader);
        }
    }

    /**
     * Parses the contents of the provided input stream as an entry generator
     * template file definition.
     *
     * @param inputStream
     *            The input stream from which to read the template file data.
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @throws IOException
     *             If a problem occurs while attempting to read data from the
     *             provided input stream.
     * @throws DecodeException
     *             If any other problem occurs while parsing the template.
     */
    void parse(InputStream inputStream, List<LocalizableMessage> warnings) throws IOException, DecodeException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(inputStream));
            final List<String> fileLines = readLines(reader);
            final String[] lines = fileLines.toArray(new String[fileLines.size()]);
            parse(lines, warnings);
        } finally {
            Utils.closeSilently(reader);
        }
    }

    private static final String INCLUDE_LABEL = "include ";
    private static final String DEFINE_LABEL = "define ";
    private static final String BRANCH_LABEL = "branch: ";
    private static final String TEMPLATE_LABEL = "template: ";
    private static final String SUBORDINATE_TEMPLATE_LABEL = "subordinatetemplate: ";
    private static final String RDNATTR_LABEL = "rdnattr: ";
    private static final String EXTENDS_LABEL = "extends: ";

    /**
     * Structure to hold template data during parsing of the template.
     */
    private static class TemplateData {
        final Map<String, TemplateTag> tags = new LinkedHashMap<>();
        final Map<DN, Branch> branches = new LinkedHashMap<>();
        final Map<String, Template> templates = new LinkedHashMap<>();
    }

    /**
     * Enumeration of elements that act as "container" of other elements.
     */
    private enum Element {
        BRANCH, TEMPLATE;

        String getLabel() {
            return toString().toLowerCase();
        }
    }

    /**
     * Parses the provided lines as an entry generator template file definition.
     *
     * @param lines
     *            The lines that make up the template file.
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @throws DecodeException
     *             If any other problem occurs while parsing the template lines.
     */
    void parse(final String[] lines, final List<LocalizableMessage> warnings) throws DecodeException {
        TemplateData templateData = new TemplateData();

        for (int lineNumber = 0; lineNumber < lines.length; lineNumber++) {
            final String line = replaceConstants(lines[lineNumber], lineNumber, constants, warnings);

            final String lowerLine = line.toLowerCase();
            if (line.length() == 0 || line.startsWith("#")) {
                // This is a comment or a blank line, so we'll ignore it.
                continue;
            } else if (lowerLine.startsWith(INCLUDE_LABEL)) {
                parseInclude(line, templateData.tags);
            } else if (lowerLine.startsWith(DEFINE_LABEL)) {
                parseDefine(lineNumber, line, constants, warnings);
            } else if (lowerLine.startsWith(BRANCH_LABEL)) {
                lineNumber = parseBranch(lineNumber, line, lines, templateData, warnings);
            } else if (lowerLine.startsWith(TEMPLATE_LABEL)) {
                lineNumber = parseTemplate(lineNumber, line, lines, templateData, warnings);
            } else {
                throw DecodeException.fatalError(
                        ERR_ENTRY_GENERATOR_UNEXPECTED_TEMPLATE_FILE_LINE.get(line, lineNumber));
            }
        }

        // Finalize the branch and template definitions
        // and then update the template file variables.
        for (Branch b : templateData.branches.values()) {
            b.completeBranchInitialization(templateData.templates, generateBranches);
        }

        for (Template t : templateData.templates.values()) {
            t.completeTemplateInitialization(templateData.templates);
        }

        registeredTags.putAll(templateData.tags);
        branches.putAll(templateData.branches);
        templates.putAll(templateData.templates);

        // Initialize iterator on branches and current branch used
        // to read entries
        if (branchesIterator == null) {
            branchesIterator = branches.values().iterator();
            if (branchesIterator.hasNext()) {
                currentBranch = branchesIterator.next();
            }
        }
    }

    private void parseInclude(final String line, final Map<String, TemplateTag> templateFileIncludeTags)
            throws DecodeException {
        // The next element should be the name of the class.
        // Load and instantiate it and make sure there are no conflicts.
        final String className = line.substring(INCLUDE_LABEL.length()).trim();

        Class<?> tagClass = null;
        try {
            tagClass = Class.forName(className);
        } catch (Exception e) {
            final LocalizableMessage message = ERR_ENTRY_GENERATOR_CANNOT_LOAD_TAG_CLASS.get(className);
            throw DecodeException.fatalError(message, e);
        }

        TemplateTag tag;
        try {
            tag = (TemplateTag) tagClass.newInstance();
        } catch (Exception e) {
            final LocalizableMessage message = ERR_ENTRY_GENERATOR_CANNOT_INSTANTIATE_TAG.get(className);
            throw DecodeException.fatalError(message, e);
        }

        String lowerName = tag.getName().toLowerCase();
        if (registeredTags.containsKey(lowerName) || templateFileIncludeTags.containsKey(lowerName)) {
            final LocalizableMessage message = ERR_ENTRY_GENERATOR_CONFLICTING_TAG_NAME.get(className, tag.getName());
            throw DecodeException.fatalError(message);
        }

        templateFileIncludeTags.put(lowerName, tag);
    }

    private void parseDefine(final int lineNumber, final String line, final Map<String, String> templateFileConstants,
            final List<LocalizableMessage> warnings) throws DecodeException {
        // The rest of the line should contain the constant name,
        // an equal sign, and the constant value.
        final int equalPos = line.indexOf('=', DEFINE_LABEL.length());
        if (equalPos < 0) {
            throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_DEFINE_MISSING_EQUALS.get(lineNumber));
        }

        final String name = line.substring(DEFINE_LABEL.length(), equalPos).trim();
        if (name.length() == 0) {
            throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_DEFINE_NAME_EMPTY.get(lineNumber));
        }

        final String value = line.substring(equalPos + 1);
        if (value.length() == 0) {
            warnings.add(ERR_ENTRY_GENERATOR_WARNING_DEFINE_VALUE_EMPTY.get(name, lineNumber));
        }

        final String lowerName = name.toLowerCase();
        if (!templateFileConstants.containsKey(lowerName)) {
            templateFileConstants.put(lowerName, value);
        }
    }

    /**
     * Parses the complete branch and returns the current line number at the
     * end.
     */
    private int parseBranch(final int startLineNumber, final String startLine, final String[] lines,
            final TemplateData templateData, final List<LocalizableMessage> warnings) throws DecodeException {
        final String[] branchLines =
                parseLinesUntilEndOfBlock(startLineNumber, startLine, lines, warnings);
        final Branch branch = parseBranchDefinition(branchLines, startLineNumber, templateData.tags, warnings);
        final DN branchDN = branch.getBranchDN();
        if (templateData.branches.containsKey(branchDN)) {
            throw DecodeException.fatalError(
                    ERR_ENTRY_GENERATOR_CONFLICTING_BRANCH_DN.get(String.valueOf(branchDN), startLineNumber));
        }
        templateData.branches.put(branchDN, branch);
        // position to next line after end of branch
        return startLineNumber + branchLines.length;
    }

    /**
     * Parses the complete template and returns the current line number at the
     * end.
     */
    private int parseTemplate(final int startLineNumber, final String startLine, final String[] lines,
            final TemplateData templateData, final List<LocalizableMessage> warnings) throws DecodeException {
        final String[] templateLines =
                parseLinesUntilEndOfBlock(startLineNumber, startLine, lines, warnings);
        final Template template =
                parseTemplateDefinition(startLineNumber, templateLines, templateData, warnings);
        final String lowerName = template.getName().toLowerCase();
        if (templateData.templates.containsKey(lowerName)) {
            throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_CONFLICTING_TEMPLATE_NAME.get(
                    String.valueOf(template.getName()), startLineNumber));
        }
        templateData.templates.put(lowerName, template);
        // position to next line after end of template
        return startLineNumber + templateLines.length;
    }

    /**
     * Parses lines of a block until the block ends (with an empty line) or
     * lines ends.
     *
     * @param startLineNumber
     *            Line number at beginning of block.
     * @param startLine
     *            First line of block.
     * @param lines
     *            The list of all lines in the template.
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @return The lines of the block
     */
    private String[] parseLinesUntilEndOfBlock(final int startLineNumber, final String startLine,
            final String[] lines, final List<LocalizableMessage> warnings) {
        final List<String> lineList = new ArrayList<>();
        String line = startLine;
        lineList.add(line);

        int lineNumber = startLineNumber;
        while (true) {
            lineNumber++;
            if (lineNumber >= lines.length) {
                break;
            }
            line = lines[lineNumber];
            if (line.length() == 0) {
                break;
            }
            line = replaceConstants(line, lineNumber, constants, warnings);
            lineList.add(line);
        }
        return lineList.toArray(new String[lineList.size()]);
    }

    /**
     * Parse a line and replace all constants within [ ] with their values.
     *
     * @param line
     *            The line to parse.
     * @param lineNumber
     *            The line number in the template file.
     * @param constants
     *            The set of constants defined in the template file.
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @return The line in which all constant variables have been replaced with
     *         their value
     */
    private String replaceConstants(final String line, final int lineNumber, final Map<String, String> constants,
            final List<LocalizableMessage> warnings) {
        String newLine = line;
        int closePos = line.lastIndexOf(']');
        // Loop until we've scanned all closing brackets
        do {
            // Skip escaped closing brackets
            while (closePos > 0 && newLine.charAt(closePos - 1) == '\\') {
                closePos = newLine.lastIndexOf(']', closePos - 1);
            }
            if (closePos > 0) {
                final StringBuilder lineBuffer = new StringBuilder(newLine);
                int openPos = newLine.lastIndexOf('[', closePos);
                // Find the opening bracket.
                // If it's escaped, then it's not a constant
                if ((openPos > 0 && newLine.charAt(openPos - 1) != '\\') || (openPos == 0)) {
                    final String constantName = newLine.substring(openPos + 1, closePos).toLowerCase();
                    final String constantValue = constants.get(constantName);
                    if (constantValue != null) {
                        lineBuffer.replace(openPos, closePos + 1, constantValue);
                    } else {
                        warnings.add(WARN_ENTRY_GENERATOR_WARNING_UNDEFINED_CONSTANT.get(constantName, lineNumber));
                    }
                }
                if (openPos >= 0) {
                    closePos = openPos;
                }
                newLine = lineBuffer.toString();
                closePos = newLine.lastIndexOf(']', closePos);
            }
        } while (closePos > 0);
        return newLine;
    }

    /**
     * Parses the information contained in the provided set of lines as a branch
     * definition.
     *
     * @param branchLines
     *            The set of lines containing the branch definition.
     * @param startLineNumber
     *            The line number in the template file on which the first of the
     *            branch lines appears.
     * @param tags
     *            The set of defined tags from the template file. Note that this
     *            does not include the tags that are always registered by
     *            default.
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @return The decoded branch definition.
     * @throws DecodeException
     *             If a problem occurs during initializing any of the branch
     *             elements or during processing.
     */
    private Branch parseBranchDefinition(final String[] branchLines, final int startLineNumber,
            final Map<String, TemplateTag> tags, final List<LocalizableMessage> warnings) throws DecodeException {
        // The first line must be "branch: " followed by the branch DN.
        final String dnString = branchLines[0].substring(BRANCH_LABEL.length()).trim();
        DN branchDN;
        try {
            branchDN = DN.valueOf(dnString, schema);
        } catch (Exception e) {
            throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_CANNOT_DECODE_BRANCH_DN.get(
                    dnString, startLineNumber));
        }

        final Branch branch = new Branch(this, branchDN, schema);

        for (int i = 1; i < branchLines.length; i++) {
            final String line = branchLines[i];
            final String lowerLine = line.toLowerCase();
            final int lineNumber = startLineNumber + i;

            if (lowerLine.startsWith("#")) {
                // It's a comment, so we should ignore it.
                continue;
            } else if (lowerLine.startsWith(SUBORDINATE_TEMPLATE_LABEL)) {
                final Pair<String, Integer> pair =
                        parseSubordinateTemplate(lineNumber, line, Element.BRANCH, dnString, warnings);
                final String templateName = pair.getFirst();
                final int numEntries = pair.getSecond();
                branch.addSubordinateTemplate(templateName, numEntries);
            } else {
                final TemplateLine templateLine =
                        parseTemplateLine(line, lineNumber, branch, null, Element.BRANCH, tags, warnings);
                branch.addExtraLine(templateLine);
            }
        }
        return branch;
    }

    /**
     * Parses the information contained in the provided set of lines as a
     * template definition.
     *
     * @param startLineNumber
     *            The line number in the template file on which the first of the
     *            template lines appears.
     * @param templateLines
     *            The set of lines containing the template definition.
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @return The decoded template definition.
     * @throws DecodeException
     *             If a problem occurs during initializing any of the template
     *             elements or during processing.
     */
    private Template parseTemplateDefinition(final int startLineNumber, final String[] templateLines,
            final TemplateData templateData, final List<LocalizableMessage> warnings) throws DecodeException {
        final Map<String, TemplateTag> tags = templateData.tags;
        final Map<String, Template> definedTemplates = templateData.templates;

        // The first line must be "template: " followed by the template name.
        final String templateName = templateLines[0].substring(TEMPLATE_LABEL.length()).trim();

        // The next line may be with an "extends", a rdn attribute, or
        // a subordinate template. Keep reading until we find something
        // that's not one of those.
        int lineCount = 1;
        Template parentTemplate = null;
        final List<AttributeType> rdnAttributes = new ArrayList<>();
        final List<String> subordinatesTemplateNames = new ArrayList<>();
        final List<Integer> numberOfentriesPerTemplate = new ArrayList<>();

        for (; lineCount < templateLines.length; lineCount++) {
            final int lineNumber = startLineNumber + lineCount;
            final String line = templateLines[lineCount];
            final String lowerLine = line.toLowerCase();

            if (lowerLine.startsWith("#")) {
                // It's a comment. Ignore it.
                continue;
            } else if (lowerLine.startsWith(EXTENDS_LABEL)) {
                final String parentTemplateName = line.substring(EXTENDS_LABEL.length()).trim();
                parentTemplate = definedTemplates.get(parentTemplateName.toLowerCase());
                if (parentTemplate == null) {
                    throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_TEMPLATE_INVALID_PARENT_TEMPLATE.get(
                            parentTemplateName, lineNumber, templateName));
                }
            } else if (lowerLine.startsWith(RDNATTR_LABEL)) {
                // This is the set of RDN attributes. If there are multiple,
                // they may be separated by plus signs.
                final String rdnAttrNames = lowerLine.substring(RDNATTR_LABEL.length()).trim();
                final StringTokenizer tokenizer = new StringTokenizer(rdnAttrNames, "+");
                while (tokenizer.hasMoreTokens()) {
                    rdnAttributes.add(schema.getAttributeType(tokenizer.nextToken()));
                }
            } else if (lowerLine.startsWith(SUBORDINATE_TEMPLATE_LABEL)) {
                final Pair<String, Integer> pair =
                        parseSubordinateTemplate(lineNumber, line, Element.BRANCH, templateName, warnings);
                subordinatesTemplateNames.add(pair.getFirst());
                numberOfentriesPerTemplate.add(pair.getSecond());
            } else {
                // Not recognized, it must be a template line.
                break;
            }
        }

        final List<TemplateLine> parentLines =
                (parentTemplate == null) ? new ArrayList<TemplateLine>() : parentTemplate.getTemplateLines();

        final Template template = new Template(this, templateName, rdnAttributes, subordinatesTemplateNames,
                numberOfentriesPerTemplate, parentLines);

        // Add lines to template
        for (; lineCount < templateLines.length; lineCount++) {
            final String line = templateLines[lineCount];
            final String lowerLine = line.toLowerCase();

            if (lowerLine.startsWith("#")) {
                // It's a comment, ignore it.
                continue;
            } else {
                final int lineNumber = startLineNumber + lineCount;
                final TemplateLine templateLine =
                        parseTemplateLine(line, lineNumber, null, template, Element.TEMPLATE, tags, warnings);
                template.addTemplateLine(templateLine);
            }
        }

        return template;
    }

    /**
     * Parses a subordinate template for a template or a branch.
     * <p>
     * A subordinate template has a name and a number of entries.
     *
     * @param lineNumber
     *            Line number of definition.
     * @param line
     *            Line containing the definition.
     * @param element
     *            indicates the kind of element to use in error messages.
     * @param elementName
     *            Name of the branch or template.
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @return the pair (template name, number of entries in template)
     */
    private Pair<String, Integer> parseSubordinateTemplate(final int lineNumber, final String line,
            final Element element, final String elementName, final List<LocalizableMessage> warnings)
            throws DecodeException {
        // It's a subordinate template, so we'll want to parse the template name
        // and the number of entries if it is provided.
        final int colonPos = line.indexOf(':', SUBORDINATE_TEMPLATE_LABEL.length());
        final String templateName;
        int numEntries = INFINITE_ENTRIES;

        if (colonPos <= SUBORDINATE_TEMPLATE_LABEL.length()) {
            //No number of entries provided, generator will provides an infinite number of entries
            templateName = line.substring(SUBORDINATE_TEMPLATE_LABEL.length(), line.length()).trim();
        } else {
            templateName = line.substring(SUBORDINATE_TEMPLATE_LABEL.length(), colonPos).trim();

            try {
                numEntries = Integer.parseInt(line.substring(colonPos + 1).trim());
                if (numEntries == 0) {
                    warnings.add(WARN_ENTRY_GENERATOR_SUBORDINATE_ZERO_ENTRIES.get(
                            lineNumber, element.getLabel(), elementName, templateName));
                }
            } catch (NumberFormatException nfe) {
                throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_SUBORDINATE_CANT_PARSE_NUMENTRIES.get(
                        templateName, lineNumber, element.getLabel(), elementName));
            }
        }

        return Pair.of(templateName, numEntries);
    }

    private static final int PARSING_STATIC_TEXT = 0;
    private static final int PARSING_REPLACEMENT_TAG = 1;
    private static final int PARSING_ATTRIBUTE_TAG = 2;
    private static final int PARSING_ESCAPED_CHAR = 3;

    /**
     * Parses the provided line as a template line. Note that exactly one of the
     * branch or template arguments must be non-null and the other must be null.
     *
     * @param line
     *            The text of the template line.
     * @param lineNumber
     *            The line number on which the template line appears.
     * @param branch
     *            The branch with which the template line is associated.
     * @param template
     *            The template with which the template line is associated.
     * @param tags
     *            The set of defined tags from the template file. Note that this
     *            does not include the tags that are always registered by
     *            default.
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @return The template line that has been parsed.
     * @throws DecodeException
     *             If a problem occurs during initializing any of the template
     *             elements or during processing.
     */
    private TemplateLine parseTemplateLine(final String line, final int lineNumber, final Branch branch,
            final Template template, final Element element, final Map<String, TemplateTag> tags,
            final List<LocalizableMessage> warnings) throws DecodeException {
        final String elementName = element == Element.BRANCH ? branch.getBranchDN().toString() : template.getName();

        // The first component must be the attribute type, followed by a colon.
        final String lowerLine = line.toLowerCase();
        final int colonPos = lowerLine.indexOf(':');
        if (colonPos < 0) {
            throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_NO_COLON_IN_TEMPLATE_LINE.get(
                    lineNumber, element.getLabel(), elementName));
        } else if (colonPos == 0) {
            throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_NO_ATTR_IN_TEMPLATE_LINE.get(
                    lineNumber, element.getLabel(), elementName));
        }

        final AttributeType attributeType = schema.getAttributeType(lowerLine.substring(0, colonPos));

        // First, check whether the value is an URL value: <attrName>:< <url>
        final int length = line.length();
        int pos = colonPos + 1;
        boolean valueIsURL = false;
        boolean valueIsBase64 = false;
        if (pos < length) {
            if (lowerLine.charAt(pos) == '<') {
                valueIsURL = true;
                pos++;
            } else if (lowerLine.charAt(pos) == ':') {
                valueIsBase64 = true;
                pos++;
            }
        }
        // Then, find the position of the first non-blank character in the line.
        while (pos < length && lowerLine.charAt(pos) == ' ') {
            pos++;
        }

        if (pos >= length) {
            // We've hit the end of the line with no value.
            // We'll allow it, but add a warning.
            warnings.add(WARN_ENTRY_GENERATOR_NO_VALUE_IN_TEMPLATE_LINE.get(
                    lineNumber, element.getLabel(), elementName));
        }

        int phase = PARSING_STATIC_TEXT;
        int previousPhase = PARSING_STATIC_TEXT;

        final List<TemplateTag> tagList = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (; pos < length; pos++) {
            char c = line.charAt(pos);
            switch (phase) {
            case PARSING_STATIC_TEXT:
                switch (c) {
                case '\\':
                    phase = PARSING_ESCAPED_CHAR;
                    previousPhase = PARSING_STATIC_TEXT;
                    break;
                case '<':
                    if (buffer.length() > 0) {
                        StaticTextTag t = new StaticTextTag();
                        String[] args = new String[] { buffer.toString() };
                        t.initializeForBranch(schema, this, branch, args, lineNumber, warnings);
                        tagList.add(t);
                        buffer = new StringBuilder();
                    }

                    phase = PARSING_REPLACEMENT_TAG;
                    break;
                case '{':
                    if (buffer.length() > 0) {
                        StaticTextTag t = new StaticTextTag();
                        String[] args = new String[] { buffer.toString() };
                        t.initializeForBranch(schema, this, branch, args, lineNumber, warnings);
                        tagList.add(t);
                        buffer = new StringBuilder();
                    }

                    phase = PARSING_ATTRIBUTE_TAG;
                    break;
                default:
                    buffer.append(c);
                }
                break;

            case PARSING_REPLACEMENT_TAG:
                switch (c) {
                case '\\':
                    phase = PARSING_ESCAPED_CHAR;
                    previousPhase = PARSING_REPLACEMENT_TAG;
                    break;
                case '>':
                    TemplateTag t =
                        parseReplacementTag(buffer.toString(), branch, template, lineNumber, tags, warnings);
                    tagList.add(t);
                    buffer = new StringBuilder();
                    phase = PARSING_STATIC_TEXT;
                    break;
                default:
                    buffer.append(c);
                    break;
                }
                break;

            case PARSING_ATTRIBUTE_TAG:
                switch (c) {
                case '\\':
                    phase = PARSING_ESCAPED_CHAR;
                    previousPhase = PARSING_ATTRIBUTE_TAG;
                    break;
                case '}':
                    TemplateTag t = parseAttributeTag(buffer.toString(), branch, template, lineNumber, warnings);
                    tagList.add(t);
                    buffer = new StringBuilder();

                    phase = PARSING_STATIC_TEXT;
                    break;
                default:
                    buffer.append(c);
                    break;
                }
                break;

            default: // PARSING_ESCAPED_CHAR:
                buffer.append(c);
                phase = previousPhase;
                break;
            }
        }

        if (phase == PARSING_STATIC_TEXT) {
            if (buffer.length() > 0) {
                StaticTextTag t = new StaticTextTag();
                String[] args = new String[] { buffer.toString() };
                t.initializeForBranch(schema, this, branch, args, lineNumber, warnings);
                tagList.add(t);
            }
        } else {
            throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_INCOMPLETE_TAG.get(lineNumber));
        }

        return new TemplateLine(attributeType, lineNumber, tagList, valueIsURL, valueIsBase64);
    }

    /**
     * Parses the provided string as a replacement tag. Exactly one of the
     * branch or template must be null, and the other must be non-null.
     *
     * @param tagString
     *            The string containing the encoded tag.
     * @param branch
     *            The branch in which this tag appears.
     * @param template
     *            The template in which this tag appears.
     * @param lineNumber
     *            The line number on which this tag appears in the template
     *            file.
     * @param tags
     *            The set of defined tags from the template file. Note that this
     *            does not include the tags that are always registered by
     *            default.
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @return The replacement tag parsed from the provided string.
     * @throws DecodeException
     *             If some problem occurs during processing.
     */
    private TemplateTag parseReplacementTag(final String tagString, final Branch branch, final Template template,
            final int lineNumber, final Map<String, TemplateTag> tags, final List<LocalizableMessage> warnings)
            throws DecodeException {
        // The components of the replacement tag will be separated by colons,
        // with the first being the tag name and the remainder being arguments.
        final StringTokenizer tokenizer = new StringTokenizer(tagString, ":");
        final String tagName = tokenizer.nextToken().trim();
        final String lowerTagName = tagName.toLowerCase();

        TemplateTag tag = getTag(lowerTagName);
        if (tag == null) {
            tag = tags.get(lowerTagName);
            if (tag == null) {
                throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_NO_SUCH_TAG.get(tagName, lineNumber));
            }
        }

        final List<String> args = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            args.add(tokenizer.nextToken().trim());
        }
        final String[] arguments = args.toArray(new String[args.size()]);

        TemplateTag newTag;
        try {
            newTag = tag.getClass().newInstance();
        } catch (Exception e) {
            throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_CANNOT_INSTANTIATE_NEW_TAG.get(
                    tagName, lineNumber, String.valueOf(e)), e);
        }

        if (branch == null) {
            newTag.initializeForTemplate(schema, this, template, arguments, lineNumber, warnings);
        } else if (newTag.allowedInBranch()) {
            newTag.initializeForBranch(schema, this, branch, arguments, lineNumber, warnings);
        } else {
            throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_TAG_NOT_ALLOWED_IN_BRANCH.get(newTag.getName(),
                    lineNumber));
        }
        return newTag;
    }

    /**
     * Parses the provided string as an attribute tag. Exactly one of the branch
     * or template must be null, and the other must be non-null.
     *
     * @param tagString
     *            The string containing the encoded tag.
     * @param branch
     *            The branch in which this tag appears.
     * @param template
     *            The template in which this tag appears.
     * @param lineNumber
     *            The line number on which this tag appears in the template
     *            file.
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @return The attribute tag parsed from the provided string.
     * @throws DecodeException
     *             If some other problem occurs during processing.
     */
    private TemplateTag parseAttributeTag(final String tagString, final Branch branch,
            final Template template, final int lineNumber, final List<LocalizableMessage> warnings)
            throws DecodeException {
        // The attribute tag must have at least one argument, which is the name
        // of the attribute to reference. It may have a second argument, which
        // is the number of characters to use from the attribute value. The
        // arguments will be delimited by colons.
        final StringTokenizer tokenizer = new StringTokenizer(tagString, ":");
        final List<String> args = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            args.add(tokenizer.nextToken());
        }
        final String[] arguments = args.toArray(new String[args.size()]);

        final AttributeValueTag tag = new AttributeValueTag();
        if (branch != null) {
            tag.initializeForBranch(schema, this, branch, arguments, lineNumber, warnings);
        } else {
            tag.initializeForTemplate(schema, this, template, arguments, lineNumber, warnings);
        }
        return tag;
    }

    /**
     * Retrieves a file based on the provided path.
     * <p>
     * To allow retrieval of a file located in a jar, you must use
     * {@code getReader()} method instead of this one.
     * <p>
     * File is searched successively in two locations :
     * <ul>
     * <li>Using the provided path as is.</li>
     * <li>Using resource path + provided path.</li>
     * </ul>
     *
     * @param filePath
     *            The path provided for the file, which can be absolute or
     *            relative.
     * @return the file, or <code>null</code> if it could not be found.
     */
    private File getFile(final String filePath) {
        File file = new File(filePath);
        // try raw path first
        if (file.exists()) {
            return file;
        }
        // try using resource path
        if (resourcePath != null) {
            file = new File(resourcePath + File.separator + filePath);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    /**
     * Retrieves a reader based on the provided path.
     * <p>
     * The path represent a file path either on the file system or in a jar.
     * File is searched successively in three locations :
     * <ul>
     * <li>Using the provided path on the file system.</li>
     * <li>Using resource path + provided path on the file system.</li>
     * <li>Using default resources path + provided path on the file system or in
     * a jar.</li>
     * </ul>
     *
     * @param filePath
     *            The path provided for the file, which can be absolute or
     *            relative.
     * @return A reader on the file, or <code>null</code> if it could not be
     *         found. It is the responsibility of caller to close the returned
     *         reader.
     */
    BufferedReader getReader(final String filePath) {
        BufferedReader reader = null;
        File file = new File(filePath);
        try {
            if (file.exists()) {
                // try raw path first
                reader = new BufferedReader(new FileReader(file));
            } else if (resourcePath != null) {
                // try using resource path
                file = new File(resourcePath + File.separator + filePath);
                if (file.exists()) {
                    reader = new BufferedReader(new FileReader(file));
                }
            }
            if (reader == null) {
                // try to find in default resources provided
                final InputStream stream = TemplateFile.class.getClassLoader()
                        .getResourceAsStream(DEFAULT_RESOURCES_PATH + "/" + filePath);
                if (stream != null) {
                    reader = new BufferedReader(new InputStreamReader(stream));
                }
            }
        } catch (FileNotFoundException e) {
            // Should never happen as we test file existence first.
            // In any case, nothing to do as we want to return null
        }
        return reader;
    }

    /**
     * Retrieves the lines of the provided reader, possibly reading them from
     * memory cache.
     * <p>
     * Lines are retrieved from reader at the first call, then cached in memory
     * for next calls, using the provided identifier.
     * <p>
     * Use {@code readFile()} method to avoid caching.
     *
     * @param reader
     *            Reader to parse for lines.
     * @return a list of lines
     * @throws IOException
     *             If a problem occurs while reading the file.
     */
    String[] getLines(String identifier, final BufferedReader reader) throws IOException {
        String[] lines = fileLines.get(identifier);
        if (lines == null) {
            lines = readLines(reader).toArray(new String[] {});
            fileLines.put(identifier, lines);
        }
        return lines;
    }

    /**
     * Retrieves the lines from the provided reader.
     *
     * @param reader
     *            The reader containing the lines.
     * @return a list of lines
     * @throws IOException
     *             If a problem occurs while reading the lines.
     */
    private List<String> readLines(final BufferedReader reader) throws IOException {
        final List<String> lines = new ArrayList<>();
        while (true) {
            final String line = reader.readLine();
            if (line == null) {
                break;
            }
            lines.add(line);
        }
        return lines;
    }

    /** Iterator on branches that are used to read entries. */
    private Iterator<Branch> branchesIterator;

    /** Branch from which entries are currently read. */
    private Branch currentBranch;

    /** Entry to return when calling {@code nextEntry} method. */
    private TemplateEntry nextEntry;

    /**
     * Returns {@code true} if there is another generated entry
     * to return.
     *
     * @return {@code true} if another entry can be returned.
     */
    boolean hasNext() {
        if (nextEntry != null) {
            return true;
        }
        while (currentBranch != null) {
            if (currentBranch.hasNext()) {
                nextEntry = currentBranch.nextEntry();
                return true;
            }
            currentBranch = branchesIterator.hasNext() ? branchesIterator.next() : null;
        }
        return false;
    }

    /**
     * Returns the next generated entry.
     *
     * @return The next entry.
     * @throws NoSuchElementException
     *             If this reader does not contain any more entries.
     */
    Entry nextEntry() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        final Entry entry = nextEntry.toEntry();
        nextEntry = null;
        return entry;
    }

    /**
     * Represents a branch that should be included in the generated results. A
     * branch may or may not have subordinate entries.
     */
    static final class Branch {
        /** The DN for this branch entry. */
        private final DN branchDN;

        /**
         * The number of entries that should be created below this branch for
         * each subordinate template.
         */
        private final List<Integer> numEntriesPerTemplate;

        /** The names of the subordinate templates for this branch. */
        private final List<String> subordinateTemplateNames;

        /** The set of subordinate templates for this branch. */
        private List<Template> subordinateTemplates;

        /** The set of template lines that correspond to the RDN components. */
        private final List<TemplateLine> rdnLines;

        /** The set of extra lines that should be included in this branch entry. */
        private final List<TemplateLine> extraLines;

        /** Entry to return when calling {@code nextEntry} method. */
        private TemplateEntry nextEntry;

        /** Index of subordinate template currently read. */
        private int currentSubTemplateIndex;

        /**
         * Creates a new branch with the provided information.
         *
         * @param templateFile
         *            The template file in which this branch appears.
         * @param branchDN
         *            The DN for this branch entry.
         * @param schema
         *            schema used to create attribute
         * @throws DecodeException
         *             if a problem occurs during initialization
         */
        Branch(final TemplateFile templateFile, final DN branchDN, final Schema schema) throws DecodeException {
            this(templateFile, branchDN, schema, new ArrayList<String>(), new ArrayList<Integer>(),
                    new ArrayList<TemplateLine>());
        }

        /**
         * Creates a new branch with the provided information.
         *
         * @param templateFile
         *            The template file in which this branch appears.
         * @param branchDN
         *            The DN for this branch entry.
         * @param schema
         *            schema used to create attributes
         * @param subordinateTemplateNames
         *            The names of the subordinate templates used to generate
         *            entries below this branch.
         * @param numEntriesPerTemplate
         *            The number of entries that should be created below this
         *            branch for each subordinate template.
         * @param extraLines
         *            The set of extra lines that should be included in this
         *            branch entry.
         * @throws DecodeException
         *             if a problem occurs during initialization
         */
        Branch(final TemplateFile templateFile, final DN branchDN, final Schema schema,
                final List<String> subordinateTemplateNames, final List<Integer> numEntriesPerTemplate,
                final List<TemplateLine> extraLines) throws DecodeException {
            this.branchDN = branchDN;
            this.subordinateTemplateNames = subordinateTemplateNames;
            this.numEntriesPerTemplate = numEntriesPerTemplate;
            this.extraLines = extraLines;

            // The RDN template lines are based on the DN.
            final List<LocalizableMessage> warnings = new ArrayList<>();
            rdnLines = new ArrayList<>();
            for (final AVA ava : branchDN.rdn()) {
                final Attribute attribute = ava.toAttribute();
                for (final ByteString value : attribute.toArray()) {
                    final List<TemplateTag> tags =
                            buildTagListForValue(value.toString(), templateFile, schema, warnings);
                    rdnLines.add(new TemplateLine(attribute.getAttributeDescription().getAttributeType(), 0, tags));
                }
            }
        }

        private List<TemplateTag> buildTagListForValue(final String value, final TemplateFile templateFile,
                final Schema schema, final List<LocalizableMessage> warnings) throws DecodeException {
            final StaticTextTag tag = new StaticTextTag();
            tag.initializeForBranch(schema, templateFile, this, new String[] { value }, 0, warnings);
            final List<TemplateTag> tags = new ArrayList<>();
            tags.add(tag);
            return tags;
        }

        /**
         * Performs any necessary processing to ensure that the branch
         * initialization is completed. In particular, it should make sure that
         * all referenced subordinate templates actually exist in the template
         * file.
         */
        private void completeBranchInitialization(final Map<String, Template> templates,
                            boolean generateBranches) throws DecodeException {
            subordinateTemplates = new ArrayList<>();
            for (int i = 0; i < subordinateTemplateNames.size(); i++) {
                subordinateTemplates.add(templates.get(subordinateTemplateNames.get(i).toLowerCase()));
                if (subordinateTemplates.get(i) == null) {
                    throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_UNDEFINED_BRANCH_SUBORDINATE.get(
                            branchDN.toString(), subordinateTemplateNames.get(i)));
                }
            }

            nextEntry = buildBranchEntry(generateBranches);
        }

        DN getBranchDN() {
            return branchDN;
        }

        /**
         * Adds a new subordinate template to this branch. Note that this should
         * not be used after <CODE>completeBranchInitialization</CODE> has been
         * called.
         *
         * @param name
         *            The name of the template to use to generate the entries.
         * @param numEntries
         *            The number of entries to create based on the template.
         */
        void addSubordinateTemplate(final String name, final int numEntries) {
            subordinateTemplateNames.add(name);
            numEntriesPerTemplate.add(numEntries);
        }

        /**
         * Adds the provided template line to the set of extra lines for this
         * branch.
         *
         * @param line
         *            The line to add to the set of extra lines for this branch.
         */
        void addExtraLine(final TemplateLine line) {
            extraLines.add(line);
        }

        /**
         * Indicates whether this branch contains a reference to the specified
         * attribute type, either in the RDN components of the DN or in the
         * extra lines.
         *
         * @param attributeType
         *            The attribute type for which to make the determination.
         * @return <code>true</code> if the branch does contain the specified
         *         attribute type, or <code>false</code> if it does not.
         */
        boolean hasAttribute(final AttributeType attributeType) {
            if (branchDN.rdn().getAttributeValue(attributeType) != null) {
                return true;
            }
            for (final TemplateLine line : extraLines) {
                if (line.getAttributeType().equals(attributeType)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns the entry corresponding to this branch.
         */
        private TemplateEntry buildBranchEntry(boolean generateBranches) {
            final TemplateEntry entry = new TemplateEntry(this);
            final List<TemplateLine> lines = new ArrayList<>(rdnLines);
            lines.addAll(extraLines);
            for (final TemplateLine line : lines) {
                line.generateLine(entry);
            }
            for (int i = 0; i < subordinateTemplates.size(); i++) {
                subordinateTemplates.get(i).reset(entry.getDN(), numEntriesPerTemplate.get(i));
            }

            if (!generateBranches) {
                return null;
            }

            return entry;
        }

        /**
         * Returns {@code true} if there is another generated entry to return.
         *
         * @return {@code true} if another entry can be returned.
         */
        boolean hasNext() {
            if (nextEntry != null) {
                return true;
            }
            // get the next entry from current subtemplate
            if (nextEntry == null) {
                for (; currentSubTemplateIndex < subordinateTemplates.size(); currentSubTemplateIndex++) {
                    if (subordinateTemplates.get(currentSubTemplateIndex).hasNext()) {
                        nextEntry = subordinateTemplates.get(currentSubTemplateIndex).nextEntry();
                        if (nextEntry != null) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        /**
         * Returns the next generated entry.
         *
         * @return The next entry.
         * @throws NoSuchElementException
         *             If this reader does not contain any more entries.
         */
        TemplateEntry nextEntry() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            final TemplateEntry entry = nextEntry;
            nextEntry = null;
            return entry;
        }
    }

    /**
     * Represents a template, which is a pattern that may be used to generate
     * entries. A template may be used either below a branch or below another
     * template.
     */
    static class Template {
        /**
         * The attribute types that are used in the RDN for entries generated
         * using this template.
         */
        private final List<AttributeType> rdnAttributes;

        /** The number of entries to create for each subordinate template. */
        private final List<Integer> numEntriesPerTemplate;

        /** The name for this template. */
        private final String name;

        /** The names of the subordinate templates below this template. */
        private final List<String> subTemplateNames;

        /** The subordinate templates below this template. */
        private List<Template> subTemplates;

        /** The template file that contains this template. */
        private final TemplateFile templateFile;

        /** The set of template lines for this template. */
        private final List<TemplateLine> templateLines;

        /**
         * Creates a new template with the provided information.
         *
         * @param templateFile
         *            The template file that contains this template.
         * @param name
         *            The name for this template.
         * @param rdnAttributes
         *            The set of attribute types that are used in the RDN for
         *            entries generated using this template.
         * @param subordinateTemplateNames
         *            The names of the subordinate templates below this
         *            template.
         * @param numEntriesPerTemplate
         *            The number of entries to create below each subordinate
         *            template.
         * @param templateLines
         *            The set of template lines for this template.
         */
        Template(final TemplateFile templateFile, final String name, final List<AttributeType> rdnAttributes,
                final List<String> subordinateTemplateNames, final List<Integer> numEntriesPerTemplate,
                final List<TemplateLine> templateLines) {
            this.templateFile = templateFile;
            this.name = name;
            this.rdnAttributes = rdnAttributes;
            this.subTemplateNames = subordinateTemplateNames;
            this.numEntriesPerTemplate = numEntriesPerTemplate;
            this.templateLines = templateLines;
        }

        /**
         * Performs any necessary processing to ensure that the template
         * initialization is completed. In particular, it should make sure that
         * all referenced subordinate templates actually exist in the template
         * file, and that all of the RDN attributes are contained in the
         * template lines.
         *
         * @param templates
         *            The set of templates defined in the template file.
         * @throws DecodeException
         *             If any of the subordinate templates are not defined in
         *             the template file.
         */
        void completeTemplateInitialization(final Map<String, Template> templates) throws DecodeException {
            subTemplates = new ArrayList<>();
            for (final String subordinateName : subTemplateNames) {
                final Template template = templates.get(subordinateName.toLowerCase());
                if (template == null) {
                    throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_UNDEFINED_TEMPLATE_SUBORDINATE.get(
                            this.name, subordinateName));
                }
                subTemplates.add(template);
            }
            ensureAllRDNAttributesAreDefined();
        }

        private void ensureAllRDNAttributesAreDefined() throws DecodeException {
            Set<AttributeType> rdnAttrs = new HashSet<>(rdnAttributes);
            List<AttributeType> templateAttrs = new ArrayList<>();
            for (TemplateLine line : templateLines) {
                templateAttrs.add(line.getAttributeType());
            }
            rdnAttrs.removeAll(templateAttrs);
            if (!rdnAttrs.isEmpty()) {
                AttributeType t = rdnAttrs.iterator().next();
                throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_TEMPLATE_MISSING_RDN_ATTR.get(
                        name, t.getNameOrOID()));
            }
        }

        String getName() {
            return name;
        }

        List<AttributeType> getRDNAttributes() {
            return rdnAttributes;
        }

        List<TemplateLine> getTemplateLines() {
            return templateLines;
        }

        void addTemplateLine(final TemplateLine line) {
            templateLines.add(line);
        }

        /**
         * Indicates whether this template contains any template lines that
         * reference the provided attribute type.
         *
         * @param attributeType
         *            The attribute type for which to make the determination.
         * @return <CODE>true</CODE> if this template contains one or more
         *         template lines that reference the provided attribute type, or
         *         <CODE>false</CODE> if not.
         */
        boolean hasAttribute(final AttributeType attributeType) {
            for (final TemplateLine line : templateLines) {
                if (line.getAttributeType().equals(attributeType)) {
                    return true;
                }
            }
            return false;
        }

        /** Parent DN of entries to generate for this template. */
        private DN parentDN;

        /**
         * Number of entries to generate for this template.
         * Negative number means infinite generation.
         */
        private int numberOfEntries;

        /** Current count of generated entries for this template. */
        private int entriesCount;

        /** Indicates if current entry has been initialized. */
        private boolean currentEntryIsInitialized;

        /** Index of current subordinate template to use for current entry. */
        private int subTemplateIndex;

        /** Entry to return when calling {@code nextEntry} method. */
        private TemplateEntry nextEntry;

        /**
         * Reset this template with provided parentDN and number of entries to
         * generate.
         * <p>
         * After a reset, the template can be used again to generate some
         * entries with a different parent DN and number of entries.
         *
         * @param parentDN
         *            The parent DN of entires to generate for this template.
         * @param numberOfEntries
         *            The number of entries to generate for this template.
         */
        void reset(final DN parentDN, final int numberOfEntries) {
            this.parentDN = parentDN;
            this.numberOfEntries = numberOfEntries;
            entriesCount = 0;
            currentEntryIsInitialized = false;
            subTemplateIndex = 0;
            nextEntry = null;
        }

        /**
         * Returns an entry for this template.
         *
         * @return the entry
         */
        private TemplateEntry buildTemplateEntry() {
            templateFile.nextFirstAndLastNames();
            final TemplateEntry templateEntry = new TemplateEntry(this, parentDN);
            for (final TemplateLine line : templateLines) {
                line.generateLine(templateEntry);
            }
            for (int i = 0; i < subTemplates.size(); i++) {
                subTemplates.get(i).reset(templateEntry.getDN(), numEntriesPerTemplate.get(i));
            }
            return templateEntry;
        }

        /**
         * Returns {@code true} if there is another generated entry to return.
         *
         * @return {@code true} if another entry can be returned.
         */
        boolean hasNext() {
            if (nextEntry != null) {
                return true;
            }
            while (entriesCount < numberOfEntries || generateForever()) {
                // get the template entry
                if (!currentEntryIsInitialized) {
                    nextEntry = buildTemplateEntry();
                    currentEntryIsInitialized = true;
                    return true;
                }
                // get the next entry from current subtemplate
                if (nextEntry == null) {
                    for (; subTemplateIndex < subTemplates.size(); subTemplateIndex++) {
                        if (subTemplates.get(subTemplateIndex).hasNext()) {
                            nextEntry = subTemplates.get(subTemplateIndex).nextEntry();
                            if (nextEntry != null) {
                                return true;
                            }
                        }
                    }
                }
                // reset for next template entry
                entriesCount++;
                currentEntryIsInitialized = false;
                subTemplateIndex = 0;
            }
            return false;
        }

        private boolean generateForever() {
            return numberOfEntries < 0;
        }

        /**
         * Returns the next generated entry.
         *
         * @return The next entry.
         * @throws NoSuchElementException
         *             If this reader does not contain any more entries.
         */
        TemplateEntry nextEntry() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            final TemplateEntry entry = nextEntry;
            nextEntry = null;
            return entry;
        }
    }

    /**
     * Represents an entry that is generated using a branch or a template.
     */
    static class TemplateEntry {

        /** Template entry that represents a null object. */
        static final TemplateEntry NULL_TEMPLATE_ENTRY = new TemplateEntry(null, null);

        /** The DN for this template entry, if it is known. */
        private DN dn;

        /**
         * The DN of the parent entry for this template entry, if it is
         * available.
         */
        private final DN parentDN;

        /**
         * The set of attributes associated with this template entry, mapped
         * from the lowercase name of the attribute to the list of generated
         * values.
         * A list of template values is never empty in the map, it always has
         * at least one element.
         */
        private final LinkedHashMap<AttributeType, List<TemplateValue>> attributes = new LinkedHashMap<>();

        /**
         * The template used to generate this entry if it is associated with a
         * template.
         */
        private final Template template;

        /**
         * Creates a new template entry that will be associated with the
         * provided branch.
         *
         * @param branch
         *            The branch to use when creating this template entry.
         */
        TemplateEntry(final Branch branch) {
            dn = branch.getBranchDN();
            template = null;
            parentDN = null;
        }

        /**
         * Creates a new template entry that will be associated with the
         * provided template.
         *
         * @param template
         *            The template used to generate this entry.
         * @param parentDN
         *            The DN of the parent entry for this template entry.
         */
        TemplateEntry(final Template template, final DN parentDN) {
            this.template = template;
            this.parentDN = parentDN;
        }

        DN getParentDN() {
            return parentDN;
        }

        /**
         * Retrieves the DN for this template entry, if it is known.
         *
         * @return The DN for this template entry if it is known, or
         *         <CODE>null</CODE> if it cannot yet be determined.
         */
        DN getDN() {
            if (dn == null) {
                final Collection<AVA> avas = new ArrayList<>();
                for (final AttributeType attrType : template.getRDNAttributes()) {
                    final TemplateValue templateValue = getValue(attrType);
                    if (templateValue == null) {
                        return null;
                    }
                    avas.add(new AVA(attrType, templateValue.getValueAsString()));
                }
                dn = parentDN.child(new RDN(avas));
            }
            return dn;
        }

        /**
         * Retrieves the value for the specified attribute, if defined. If the
         * specified attribute has multiple values, then the first will be
         * returned.
         *
         * @param attributeType
         *            The attribute type for which to retrieve the value.
         * @return The value for the specified attribute, or <CODE>null</CODE>
         *         if there are no values for that attribute type.
         */
        TemplateValue getValue(final AttributeType attributeType) {
            final List<TemplateValue> values = attributes.get(attributeType);
            if (values != null) {
                return values.get(0);
            }
            return null;
        }

        /**
         * Retrieves the set of values for the specified attribute, if defined.
         *
         * @param attributeType
         *            The attribute type for which to retrieve the set of
         *            values.
         * @return The set of values for the specified attribute, or
         *         <CODE>null</CODE> if there are no values for that attribute
         *         type.
         */
        List<TemplateValue> getValues(AttributeType attributeType) {
            return attributes.get(attributeType);
        }

        void addValue(TemplateValue value) {
            List<TemplateValue> values = attributes.get(value.getAttributeType());
            if (values == null) {
                values = new ArrayList<>();
                attributes.put(value.getAttributeType(), values);
            }
            values.add(value);
        }

        /**
         * Returns an entry built from this template entry.
         *
         * @return an entry
         */
        Entry toEntry() {
            final Entry entry = new LinkedHashMapEntry(getDN());

            for (final AttributeType attributeType : attributes.keySet()) {
                final List<TemplateValue> valueList = attributes.get(attributeType);
                final Attribute newAttribute =
                        new LinkedAttribute(AttributeDescription.create(attributeType));
                for (final TemplateValue value : valueList) {
                    newAttribute.add(value.getValueAsString());
                }
                entry.addAttribute(newAttribute);
            }
            return entry;
        }
    }

    /**
     * Represents a line that may appear in a template or branch. It may contain
     * any number of tags to be evaluated.
     */
    static class TemplateLine {

        /** The attribute type to which this template line corresponds. */
        private final AttributeType attributeType;

        /**
         * The line number on which this template line appears in the template
         * file.
         */
        @SuppressWarnings("unused")
        private final int lineNumber;

        /** The set of tags for this template line. */
        private final List<TemplateTag> tags;

        /** Whether this line corresponds to an URL value or not. */
        @SuppressWarnings("unused")
        private final boolean isURL;

        /** Whether this line corresponds to a base64 encoded value or not. */
        @SuppressWarnings("unused")
        private final boolean isBase64;

        /**
         * Creates a new template line.
         *
         * @param attributeType
         *            The attribute type for this template line.
         * @param lineNumber
         *            The line number on which this template line appears in the
         *            template file.
         * @param tags
         *            The set of tags for this template line.
         */
        TemplateLine(final AttributeType attributeType, final int lineNumber, final List<TemplateTag> tags) {
            this(attributeType, lineNumber, tags, false, false);
        }

        /**
         * Creates a new template line with URL and base64 flags.
         *
         * @param attributeType
         *            The attribute type for this template line.
         * @param lineNumber
         *            The line number on which this template line appears in the
         *            template file.
         * @param tags
         *            The set of tags for this template line.
         * @param isURL
         *            Whether this template line's value is an URL or not.
         * @param isBase64
         *            Whether this template line's value is Base64 encoded or
         *            not.
         */
        TemplateLine(final AttributeType attributeType, final int lineNumber, final List<TemplateTag> tags,
                final boolean isURL, final boolean isBase64) {
            this.attributeType = attributeType;
            this.lineNumber = lineNumber;
            this.tags = tags;
            this.isURL = isURL;
            this.isBase64 = isBase64;
        }

        AttributeType getAttributeType() {
            return attributeType;
        }

        /**
         * Generates the content for this template line and places it in the
         * provided template entry.
         *
         * @param templateEntry
         *            The template entry being generated.
         * @return The result of generating the template line.
         */
        TagResult generateLine(final TemplateEntry templateEntry) {
            final TemplateValue value = new TemplateValue(this);
            for (final TemplateTag tag : tags) {
                final TagResult result = tag.generateValue(templateEntry, value);
                if (result != TagResult.SUCCESS) {
                    return result;
                }
            }
            templateEntry.addValue(value);
            return TagResult.SUCCESS;
        }
    }

    /**
     * Represents a value generated from a template line.
     */
    static class TemplateValue {

        /** The generated template value. */
        private final StringBuilder templateValue;

        /** The template line used to generate this value. */
        private final TemplateLine templateLine;

        TemplateValue(final TemplateLine templateLine) {
            this.templateLine = templateLine;
            templateValue = new StringBuilder();
        }

        AttributeType getAttributeType() {
            return templateLine.getAttributeType();
        }

        /** Returns the generated value as String. */
        String getValueAsString() {
            return templateValue.toString();
        }

        /** Appends the provided string to this template value. */
        void append(final String s) {
            templateValue.append(s);
        }

        /**
         * Appends the string representation of the provided object to this
         * template value.
         */
        void append(final Object o) {
            templateValue.append(o);
        }
    }
}
