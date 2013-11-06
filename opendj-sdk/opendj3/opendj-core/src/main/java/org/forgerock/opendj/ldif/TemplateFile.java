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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.AttributeFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.ObjectClass;
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

import com.forgerock.opendj.util.StaticUtils;

/**
 * A template file allow to generate entries from a collection of constant
 * definitions, branches, and templates.
 *
 * @see EntryGenerator
 */
class TemplateFile {
    /**
     * The name of the file holding the list of first names.
     */
    public static final String FIRST_NAME_FILE = "first.names";

    /**
     * The name of the file holding the list of last names.
     */
    public static final String LAST_NAME_FILE = "last.names";

    /**
     * A map of the contents of various text files used during the parsing
     * process, mapped from absolute path to the array of lines in the file.
     */
    private Map<String, String[]> fileLines;

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
    private int nameUniquenessCounter;

    /** The set of branch definitions for this template file. */
    private Map<DN, Branch> branches;

    /** The set of constant definitions for this template file. */
    private Map<String, String> constants;

    /** The set of registered tags for this template file. */
    private Map<String, TemplateTag> registeredTags;

    /** The set of template definitions for this template file. */
    private Map<String, Template> templates;

    /** The random number generator for this template file. */
    private Random random;

    /** The next first name that should be used. */
    private String firstName;

    /** The next last name that should be used. */
    private String lastName;

    /**
     * The resource path to use for filesystem elements that cannot be found
     * anywhere else.
     */
    private String resourcePath;

    /** The path to the directory containing the template file, if available. */
    private String templatePath;

    /** The set of first names to use when generating the LDIF. */
    private String[] firstNames;

    /** The set of last names to use when generating the LDIF. */
    private String[] lastNames;

    /** Schema used to create attributes. */
    private final Schema schema;

    /**
     * Creates a new, empty template file structure.
     *
     * @param schema
     *            LDAP Schema to use
     * @param resourcePath
     *            The path to the directory that may contain additional resource
     *            files needed during the generation process.
     */
    public TemplateFile(Schema schema, String resourcePath) {
        this(schema, resourcePath, new Random());
    }

    /**
     * Creates a new, empty template file structure.
     *
     * @param schema
     *            used to create attributes
     * @param resourcePath
     *            The path to the directory that may contain additional resource
     *            files needed during the generation process.
     * @param random
     *            The random number generator for this template file.
     */
    public TemplateFile(Schema schema, String resourcePath, Random random) {
        this.schema = schema;
        this.resourcePath = resourcePath;
        this.random = random;

        fileLines = new HashMap<String, String[]>();
        branches = new LinkedHashMap<DN, Branch>();
        constants = new LinkedHashMap<String, String>();
        registeredTags = new LinkedHashMap<String, TemplateTag>();
        templates = new LinkedHashMap<String, Template>();
        templatePath = null;
        firstNames = new String[0];
        lastNames = new String[0];
        firstName = null;
        lastName = null;
        firstNameIndex = 0;
        lastNameIndex = 0;
        nameLoopCounter = 0;
        nameUniquenessCounter = 1;

        registerDefaultTags();

        readNames();
    }

    /**
     * Retrieves the set of tags that have been registered. They will be in the
     * form of a mapping between the name of the tag (in all lowercase
     * characters) and the corresponding tag implementation.
     *
     * @return The set of tags that have been registered.
     */
    public Map<String, TemplateTag> getTags() {
        return registeredTags;
    }

    /**
     * Retrieves the tag with the specified name.
     *
     * @param lowerName
     *            The name of the tag to retrieve, in all lowercase characters.
     * @return The requested tag, or <CODE>null</CODE> if no such tag has been
     *         registered.
     */
    public TemplateTag getTag(String lowerName) {
        return registeredTags.get(lowerName);
    }

    /**
     * Registers the specified class as a tag that may be used in templates.
     *
     * @param tagClass
     *            The fully-qualified name of the class to register as a tag.
     * @throws DecodeException
     *             If a problem occurs while attempting to register the
     *             specified tag.
     */
    public void registerTag(String tagClass) throws DecodeException {
        Class<?> c;
        try {
            c = Class.forName(tagClass);
        } catch (Exception e) {
            final LocalizableMessage message = ERR_ENTRY_GENERATOR_CANNOT_LOAD_TAG_CLASS.get(tagClass);
            throw DecodeException.fatalError(message, e);
        }

        TemplateTag t;
        try {
            t = (TemplateTag) c.newInstance();
        } catch (Exception e) {
            final LocalizableMessage message = ERR_ENTRY_GENERATOR_CANNOT_INSTANTIATE_TAG.get(tagClass);
            throw DecodeException.fatalError(message, e);
        }

        String lowerName = t.getName().toLowerCase();
        if (registeredTags.containsKey(lowerName)) {
            final LocalizableMessage message = ERR_ENTRY_GENERATOR_CONFLICTING_TAG_NAME.get(tagClass, t.getName());
            throw DecodeException.fatalError(message);
        } else {
            registeredTags.put(lowerName, t);
        }
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

        for (Class<?> c : defaultTagClasses) {
            try {
                TemplateTag t = (TemplateTag) c.newInstance();
                registeredTags.put(t.getName().toLowerCase(), t);
            } catch (Exception e) {
                // this should never happen
                StaticUtils.DEFAULT_LOG.error(ERR_ENTRY_GENERATOR_CANNOT_INSTANTIATE_TAG.get(c.getName()).toString());
            }
        }
    }

    /**
     * Retrieves the set of constants defined for this template file.
     *
     * @return The set of constants defined for this template file.
     */
    public Map<String, String> getConstants() {
        return constants;
    }

    /**
     * Retrieves the value of the constant with the specified name.
     *
     * @param lowerName
     *            The name of the constant to retrieve, in all lowercase
     *            characters.
     * @return The value of the constant with the specified name, or
     *         <CODE>null</CODE> if there is no such constant.
     */
    public String getConstant(String lowerName) {
        return constants.get(lowerName);
    }

    /**
     * Registers the provided constant for use in the template.
     *
     * @param name
     *            The name for the constant.
     * @param value
     *            The value for the constant.
     */
    public void registerConstant(String name, String value) {
        constants.put(name.toLowerCase(), value);
    }

    /**
     * Retrieves the set of branches defined in this template file.
     *
     * @return The set of branches defined in this template file.
     */
    public Map<DN, Branch> getBranches() {
        return branches;
    }

    /**
     * Retrieves the branch registered with the specified DN.
     *
     * @param branchDN
     *            The DN for which to retrieve the corresponding branch.
     * @return The requested branch, or <CODE>null</CODE> if no such branch has
     *         been registered.
     */
    public Branch getBranch(DN branchDN) {
        return branches.get(branchDN);
    }

    /**
     * Registers the provided branch in this template file.
     *
     * @param branch
     *            The branch to be registered.
     */
    public void registerBranch(Branch branch) {
        branches.put(branch.getBranchDN(), branch);
    }

    /**
     * Retrieves the set of templates defined in this template file.
     *
     * @return The set of templates defined in this template file.
     */
    public Map<String, Template> getTemplates() {
        return templates;
    }

    /**
     * Retrieves the template with the specified name.
     *
     * @param lowerName
     *            The name of the template to retrieve, in all lowercase
     *            characters.
     * @return The requested template, or <CODE>null</CODE> if there is no such
     *         template.
     */
    public Template getTemplate(String lowerName) {
        return templates.get(lowerName);
    }

    /**
     * Registers the provided template for use in this template file.
     *
     * @param template
     *            The template to be registered.
     */
    public void registerTemplate(Template template) {
        templates.put(template.getName().toLowerCase(), template);
    }

    /**
     * Retrieves the random number generator for this template file.
     *
     * @return The random number generator for this template file.
     */
    public Random getRandom() {
        return random;
    }

    /**
     * Reads the first and last names in standard files or use default values if
     * files can't be read.
     */
    private void readNames() {
        firstNames = readNamesFromFile(FIRST_NAME_FILE);
        if (firstNames == null) {
            firstNames = new String[] { "Christophe", "Gael", "Gary", "Jean-Noel", "Laurent", "Ludovic", "Mark",
                "Matthew", "Nicolas", "Violette" };
        }
        lastNames = readNamesFromFile(LAST_NAME_FILE);
        if (lastNames == null) {
            lastNames = new String[] { "Maahs", "Maas", "Mabes", "Mabson", "Mabuchi", "Mac", "Mac Maid", "MacAdams",
                "MacArthur", "MacCarthy" };
        }
    }

    /**
     * Returns an array of names read in the provided file, or null if a problem
     * occurs.
     */
    private String[] readNamesFromFile(String fileName) {
        String[] names = null;
        File file = getFile(fileName);
        if (file != null) {
            try {
                List<String> nameList = readDataFile(file);
                names = new String[nameList.size()];
                nameList.toArray(names);
            } catch (IOException e) {
                // TODO : I18N
                StaticUtils.DEFAULT_LOG.error("Unable to read names file {}", fileName);
            }
        }
        return names;
    }

    /**
     * Read a file of data, and return a list containing one item per line.
     */
    private List<String> readDataFile(File file) throws FileNotFoundException, IOException {
        List<String> data = new ArrayList<String>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                } else {
                    data.add(line);
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return data;
    }

    /**
     * Updates the first and last name indexes to choose new values. The
     * algorithm used is designed to ensure that the combination of first and
     * last names will never be repeated. It depends on the number of first
     * names and the number of last names being relatively prime. This method
     * should be called before beginning generation of each template entry.
     */
    public void nextFirstAndLastNames() {
        firstName = firstNames[firstNameIndex++];
        lastName = lastNames[lastNameIndex++];

        // If we've already exhausted every possible combination, then append an
        // integer to the last name.
        if (nameUniquenessCounter > 1) {
            lastName += nameUniquenessCounter;
        }

        if (firstNameIndex >= firstNames.length) {
            // We're at the end of the first name list, so start over. If the
            // first
            // name list is larger than the last name list, then we'll also need
            // to
            // set the last name index to the next loop counter position.
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
            // We're at the end of the last name list, so start over. If the
            // last
            // name list is larger than the first name list, then we'll also
            // need to
            // set the first name index to the next loop counter position.
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

    /**
     * Retrieves the first name value that should be used for the current entry.
     *
     * @return The first name value that should be used for the current entry.
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Retrieves the last name value that should be used for the current entry.
     *
     * @return The last name value that should be used for the current entry.
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * Parses the contents of the specified file as a MakeLDIF template file
     * definition.
     *
     * @param filename
     *            The name of the file containing the template data.
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @throws IOException
     *             If a problem occurs while attempting to read data from the
     *             specified file.
     * @throws DecodeException
     *             If any other problem occurs while parsing the template file.
     */
    public void parse(String filename, List<LocalizableMessage> warnings) throws IOException, DecodeException {
        ArrayList<String> fileLines = new ArrayList<String>();

        templatePath = null;
        File f = getFile(filename);
        if ((f == null) || (!f.exists())) {
            LocalizableMessage message = ERR_ENTRY_GENERATOR_COULD_NOT_FIND_TEMPLATE_FILE.get(filename);
            throw new IOException(message.toString());
        } else {
            templatePath = f.getParentFile().getAbsolutePath();
        }

        BufferedReader reader = new BufferedReader(new FileReader(f));
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            } else {
                fileLines.add(line);
            }
        }

        reader.close();

        String[] lines = new String[fileLines.size()];
        fileLines.toArray(lines);
        parse(lines, warnings);
    }

    /**
     * Parses the data read from the provided input stream as a MakeLDIF
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
    public void parse(InputStream inputStream, List<LocalizableMessage> warnings) throws IOException, DecodeException {
        ArrayList<String> fileLines = new ArrayList<String>();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            } else {
                fileLines.add(line);
            }
        }

        reader.close();

        String[] lines = new String[fileLines.size()];
        fileLines.toArray(lines);
        parse(lines, warnings);
    }

    /**
     * Parses the provided data as a MakeLDIF template file definition.
     *
     * @param lines
     *            The lines that make up the template file.
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @throws DecodeException
     *             If any other problem occurs while parsing the template lines.
     */
    public void parse(String[] lines, List<LocalizableMessage> warnings) throws DecodeException {
        // Create temporary variables that will be used to hold the data read.
        LinkedHashMap<String, TemplateTag> templateFileIncludeTags = new LinkedHashMap<String, TemplateTag>();
        LinkedHashMap<String, String> templateFileConstants = new LinkedHashMap<String, String>();
        LinkedHashMap<DN, Branch> templateFileBranches = new LinkedHashMap<DN, Branch>();
        LinkedHashMap<String, Template> templateFileTemplates = new LinkedHashMap<String, Template>();

        for (int lineNumber = 0; lineNumber < lines.length; lineNumber++) {
            String line = lines[lineNumber];

            line = replaceConstants(line, lineNumber, templateFileConstants, warnings);

            String lowerLine = line.toLowerCase();
            if ((line.length() == 0) || line.startsWith("#")) {
                // This is a comment or a blank line, so we'll ignore it.
                continue;
            } else if (lowerLine.startsWith("include ")) {
                // This should be an include definition. The next element should
                // be the
                // name of the class. Load and instantiate it and make sure
                // there are
                // no conflicts.
                String className = line.substring(8).trim();

                Class<?> tagClass;
                try {
                    tagClass = Class.forName(className);
                } catch (Exception e) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_CANNOT_LOAD_TAG_CLASS.get(className);
                    throw DecodeException.fatalError(message, e);
                }

                TemplateTag tag;
                try {
                    tag = (TemplateTag) tagClass.newInstance();
                } catch (Exception e) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_CANNOT_INSTANTIATE_TAG.get(className);
                    throw DecodeException.fatalError(message, e);
                }

                String lowerName = tag.getName().toLowerCase();
                if (registeredTags.containsKey(lowerName) || templateFileIncludeTags.containsKey(lowerName)) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_CONFLICTING_TAG_NAME.get(className, tag.getName());
                    throw DecodeException.fatalError(message);
                }

                templateFileIncludeTags.put(lowerName, tag);
            } else if (lowerLine.startsWith("define ")) {
                // This should be a constant definition. The rest of the line
                // should
                // contain the constant name, an equal sign, and the constant
                // value.
                int equalPos = line.indexOf('=', 7);
                if (equalPos < 0) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_DEFINE_MISSING_EQUALS.get(lineNumber);
                    throw DecodeException.fatalError(message);
                }

                String name = line.substring(7, equalPos).trim();
                if (name.length() == 0) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_DEFINE_NAME_EMPTY.get(lineNumber);
                    throw DecodeException.fatalError(message);
                }

                String lowerName = name.toLowerCase();
                if (templateFileConstants.containsKey(lowerName)) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_CONFLICTING_CONSTANT_NAME.get(name, lineNumber);
                    throw DecodeException.fatalError(message);
                }

                String value = line.substring(equalPos + 1);
                if (value.length() == 0) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_WARNING_DEFINE_VALUE_EMPTY.get(name, lineNumber);
                    warnings.add(message);
                }

                templateFileConstants.put(lowerName, value);
            } else if (lowerLine.startsWith("branch: ")) {
                int startLineNumber = lineNumber;
                ArrayList<String> lineList = new ArrayList<String>();
                lineList.add(line);
                while (true) {
                    lineNumber++;
                    if (lineNumber >= lines.length) {
                        break;
                    }

                    line = lines[lineNumber];
                    if (line.length() == 0) {
                        break;
                    } else {
                        line = replaceConstants(line, lineNumber, templateFileConstants, warnings);
                        lineList.add(line);
                    }
                }

                String[] branchLines = new String[lineList.size()];
                lineList.toArray(branchLines);

                Branch b = parseBranchDefinition(branchLines, lineNumber, templateFileIncludeTags, warnings);
                DN branchDN = b.getBranchDN();
                if (templateFileBranches.containsKey(branchDN)) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_CONFLICTING_BRANCH_DN.get(
                            String.valueOf(branchDN), startLineNumber);
                    throw DecodeException.fatalError(message);
                } else {
                    templateFileBranches.put(branchDN, b);
                }
            } else if (lowerLine.startsWith("template: ")) {
                int startLineNumber = lineNumber;
                ArrayList<String> lineList = new ArrayList<String>();
                lineList.add(line);
                while (true) {
                    lineNumber++;
                    if (lineNumber >= lines.length) {
                        break;
                    }

                    line = lines[lineNumber];
                    if (line.length() == 0) {
                        break;
                    } else {
                        line = replaceConstants(line, lineNumber, templateFileConstants, warnings);
                        lineList.add(line);
                    }
                }

                String[] templateLines = new String[lineList.size()];
                lineList.toArray(templateLines);

                Template t = parseTemplateDefinition(templateLines, startLineNumber, templateFileIncludeTags,
                        templateFileTemplates, warnings);
                String lowerName = t.getName().toLowerCase();
                if (templateFileTemplates.containsKey(lowerName)) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_CONFLICTING_TEMPLATE_NAME.get(
                            String.valueOf(t.getName()), startLineNumber);
                    throw DecodeException.fatalError(message);
                } else {
                    templateFileTemplates.put(lowerName, t);
                }
            } else {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_UNEXPECTED_TEMPLATE_FILE_LINE.get(line, lineNumber);
                throw DecodeException.fatalError(message);
            }
        }

        // If we've gotten here, then we're almost done. We just need to
        // finalize
        // the branch and template definitions and then update the template file
        // variables.
        for (Branch b : templateFileBranches.values()) {
            b.completeBranchInitialization(templateFileTemplates);
        }

        for (Template t : templateFileTemplates.values()) {
            t.completeTemplateInitialization(templateFileTemplates);
        }

        registeredTags.putAll(templateFileIncludeTags);
        constants.putAll(templateFileConstants);
        branches.putAll(templateFileBranches);
        templates.putAll(templateFileTemplates);
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
    private String replaceConstants(String line, int lineNumber, Map<String, String> constants,
            List<LocalizableMessage> warnings) {
        int closePos = line.lastIndexOf(']');
        // Loop until we've scanned all closing brackets
        do {
            // Skip escaped closing brackets
            while (closePos > 0 && line.charAt(closePos - 1) == '\\') {
                closePos = line.lastIndexOf(']', closePos - 1);
            }
            if (closePos > 0) {
                StringBuilder lineBuffer = new StringBuilder(line);
                int openPos = line.lastIndexOf('[', closePos);
                // Find the opening bracket. If it's escaped, then it's not a
                // constant
                if ((openPos > 0 && line.charAt(openPos - 1) != '\\') || (openPos == 0)) {
                    String constantName = line.substring(openPos + 1, closePos).toLowerCase();
                    String constantValue = constants.get(constantName);
                    if (constantValue == null) {
                        LocalizableMessage message = WARN_ENTRY_GENERATOR_WARNING_UNDEFINED_CONSTANT.get(constantName,
                                lineNumber);
                        warnings.add(message);
                    } else {
                        lineBuffer.replace(openPos, closePos + 1, constantValue);
                    }
                }
                if (openPos >= 0) {
                    closePos = openPos;
                }
                line = lineBuffer.toString();
                closePos = line.lastIndexOf(']', closePos);
            }
        } while (closePos > 0);
        return line;
    }

    /**
     * Parses the information contained in the provided set of lines as a
     * MakeLDIF branch definition.
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
    private Branch parseBranchDefinition(String[] branchLines, int startLineNumber, Map<String, TemplateTag> tags,
            List<LocalizableMessage> warnings) throws DecodeException {
        // The first line must be "branch: " followed by the branch DN.
        String dnString = branchLines[0].substring(8).trim();
        DN branchDN;
        try {
            branchDN = DN.valueOf(dnString, schema);
        } catch (Exception e) {
            LocalizableMessage message = ERR_ENTRY_GENERATOR_CANNOT_DECODE_BRANCH_DN.get(dnString, startLineNumber);
            throw DecodeException.fatalError(message);
        }

        // Create a new branch that will be used for the verification process.
        Branch branch = new Branch(this, branchDN, null);

        for (int i = 1; i < branchLines.length; i++) {
            String line = branchLines[i];
            String lowerLine = line.toLowerCase();
            int lineNumber = startLineNumber + i;

            if (lowerLine.startsWith("#")) {
                // It's a comment, so we should ignore it.
                continue;
            } else if (lowerLine.startsWith("subordinatetemplate: ")) {
                // It's a subordinate template, so we'll want to parse the name
                // and the
                // number of entries.
                int colonPos = line.indexOf(':', 21);
                if (colonPos <= 21) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_BRANCH_SUBORDINATE_TEMPLATE_NO_COLON.get(
                            lineNumber, dnString);
                    throw DecodeException.fatalError(message);
                }

                String templateName = line.substring(21, colonPos).trim();

                int numEntries;
                try {
                    numEntries = Integer.parseInt(line.substring(colonPos + 1).trim());
                    if (numEntries < 0) {
                        LocalizableMessage message = ERR_ENTRY_GENERATOR_BRANCH_SUBORDINATE_INVALID_NUM_ENTRIES.get(
                                lineNumber, dnString, numEntries, templateName);
                        throw DecodeException.fatalError(message);
                    } else if (numEntries == 0) {
                        LocalizableMessage message = WARN_ENTRY_GENERATOR_BRANCH_SUBORDINATE_ZERO_ENTRIES.get(
                                lineNumber, dnString, templateName);
                        warnings.add(message);
                    }

                    branch.addSubordinateTemplate(templateName, numEntries);
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_BRANCH_SUBORDINATE_CANT_PARSE_NUMENTRIES.get(
                            templateName, lineNumber, dnString);
                    throw DecodeException.fatalError(message);
                }
            } else {
                TemplateLine templateLine =
                        parseTemplateLine(line, lowerLine, lineNumber, branch, null, tags, warnings);
                branch.addExtraLine(templateLine);
            }
        }

        return branch;
    }

    /**
     * Parses the information contained in the provided set of lines as a
     * MakeLDIF template definition.
     *
     * @param templateLines
     *            The set of lines containing the template definition.
     * @param startLineNumber
     *            The line number in the template file on which the first of the
     *            template lines appears.
     * @param tags
     *            The set of defined tags from the template file. Note that this
     *            does not include the tags that are always registered by
     *            default.
     * @param definedTemplates
     *            The set of templates already defined in the template file.
     * @param warnings
     *            A list into which any warnings identified may be placed.
     * @return The decoded template definition.
     * @throws DecodeException
     *             If a problem occurs during initializing any of the template
     *             elements or during processing.
     */
    private Template parseTemplateDefinition(String[] templateLines, int startLineNumber,
            Map<String, TemplateTag> tags, Map<String, Template> definedTemplates, List<LocalizableMessage> warnings)
            throws DecodeException {
        // The first line must be "template: " followed by the template name.
        String templateName = templateLines[0].substring(10).trim();

        // The next line may start with either "extends: ", "rdnAttr: ", or
        // "subordinateTemplate: ". Keep reading until we find something that's
        // not one of those.
        int arrayLineNumber = 1;
        Template parentTemplate = null;
        AttributeType[] rdnAttributes = null;
        ArrayList<String> subTemplateNames = new ArrayList<String>();
        ArrayList<Integer> entriesPerTemplate = new ArrayList<Integer>();
        for (; arrayLineNumber < templateLines.length; arrayLineNumber++) {
            int lineNumber = startLineNumber + arrayLineNumber;
            String line = templateLines[arrayLineNumber];
            String lowerLine = line.toLowerCase();

            if (lowerLine.startsWith("#")) {
                // It's a comment. Ignore it.
                continue;
            } else if (lowerLine.startsWith("extends: ")) {
                String parentTemplateName = line.substring(9).trim();
                parentTemplate = definedTemplates.get(parentTemplateName.toLowerCase());
                if (parentTemplate == null) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TEMPLATE_INVALID_PARENT_TEMPLATE.get(
                            parentTemplateName, lineNumber, templateName);
                    throw DecodeException.fatalError(message);
                }
            } else if (lowerLine.startsWith("rdnattr: ")) {
                // This is the set of RDN attributes. If there are multiple,
                // they may
                // be separated by plus signs.
                ArrayList<AttributeType> attrList = new ArrayList<AttributeType>();
                String rdnAttrNames = lowerLine.substring(9).trim();
                StringTokenizer tokenizer = new StringTokenizer(rdnAttrNames, "+");
                while (tokenizer.hasMoreTokens()) {
                    attrList.add(schema.getAttributeType(tokenizer.nextToken()));
                }

                rdnAttributes = new AttributeType[attrList.size()];
                attrList.toArray(rdnAttributes);
            } else if (lowerLine.startsWith("subordinatetemplate: ")) {
                // It's a subordinate template, so we'll want to parse the name
                // and the
                // number of entries.
                int colonPos = line.indexOf(':', 21);
                if (colonPos <= 21) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TEMPLATE_SUBORDINATE_TEMPLATE_NO_COLON.get(
                            lineNumber, templateName);
                    throw DecodeException.fatalError(message);
                }

                String subTemplateName = line.substring(21, colonPos).trim();

                int numEntries;
                try {
                    numEntries = Integer.parseInt(line.substring(colonPos + 1).trim());
                    if (numEntries < 0) {
                        LocalizableMessage message = ERR_ENTRY_GENERATOR_TEMPLATE_SUBORDINATE_INVALID_NUM_ENTRIES.get(
                                lineNumber, templateName, numEntries, subTemplateName);
                        throw DecodeException.fatalError(message);
                    } else if (numEntries == 0) {
                        LocalizableMessage message = WARN_ENTRY_GENERATOR_TEMPLATE_SUBORDINATE_ZERO_ENTRIES.get(
                                lineNumber, templateName, subTemplateName);
                        warnings.add(message);
                    }

                    subTemplateNames.add(subTemplateName);
                    entriesPerTemplate.add(numEntries);
                } catch (NumberFormatException nfe) {
                    LocalizableMessage message = ERR_ENTRY_GENERATOR_TEMPLATE_SUBORDINATE_CANT_PARSE_NUMENTRIES.get(
                            subTemplateName, lineNumber, templateName);
                    throw DecodeException.fatalError(message);
                }
            } else {
                // It's something we don't recognize, so it must be a template
                // line.
                break;
            }
        }

        // Create a new template that will be used for the verification process.
        String[] subordinateTemplateNames = new String[subTemplateNames.size()];
        subTemplateNames.toArray(subordinateTemplateNames);

        int[] numEntriesPerTemplate = new int[entriesPerTemplate.size()];
        for (int i = 0; i < numEntriesPerTemplate.length; i++) {
            numEntriesPerTemplate[i] = entriesPerTemplate.get(i);
        }

        TemplateLine[] parsedLines;
        if (parentTemplate == null) {
            parsedLines = new TemplateLine[0];
        } else {
            TemplateLine[] parentLines = parentTemplate.getTemplateLines();
            parsedLines = new TemplateLine[parentLines.length];
            System.arraycopy(parentLines, 0, parsedLines, 0, parentLines.length);
        }

        Template template = new Template(this, templateName, rdnAttributes, subordinateTemplateNames,
                numEntriesPerTemplate, parsedLines);

        for (; arrayLineNumber < templateLines.length; arrayLineNumber++) {
            String line = templateLines[arrayLineNumber];
            String lowerLine = line.toLowerCase();
            int lineNumber = startLineNumber + arrayLineNumber;

            if (lowerLine.startsWith("#")) {
                // It's a comment, so we should ignore it.
                continue;
            } else {
                TemplateLine templateLine = parseTemplateLine(line, lowerLine, lineNumber, null, template, tags,
                        warnings);
                template.addTemplateLine(templateLine);
            }
        }

        return template;
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
     * @param lowerLine
     *            The template line in all lowercase characters.
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
    private TemplateLine parseTemplateLine(String line, String lowerLine, int lineNumber, Branch branch,
            Template template, Map<String, TemplateTag> tags, List<LocalizableMessage> warnings)
            throws DecodeException {
        // The first component must be the attribute type, followed by a colon.
        int colonPos = lowerLine.indexOf(':');
        if (colonPos < 0) {
            if (branch == null) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_NO_COLON_IN_TEMPLATE_LINE.get(lineNumber,
                        template.getName());
                throw DecodeException.fatalError(message);
            } else {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_NO_COLON_IN_BRANCH_EXTRA_LINE.get(lineNumber,
                        String.valueOf(branch.getBranchDN()));
                throw DecodeException.fatalError(message);
            }
        } else if (colonPos == 0) {
            if (branch == null) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_NO_ATTR_IN_TEMPLATE_LINE.get(lineNumber,
                        template.getName());
                throw DecodeException.fatalError(message);
            } else {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_NO_ATTR_IN_BRANCH_EXTRA_LINE.get(lineNumber,
                        String.valueOf(branch.getBranchDN()));
                throw DecodeException.fatalError(message);
            }
        }

        AttributeType attributeType = schema.getAttributeType(lowerLine.substring(0, colonPos));

        // First, check whether the value is an URL value: <attrName>:< <url>
        int length = line.length();
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
        while ((pos < length) && (lowerLine.charAt(pos) == ' ')) {
            pos++;
        }

        if (pos >= length) {
            // We've hit the end of the line with no value. We'll allow it, but
            // add a
            // warning.
            if (branch == null) {
                LocalizableMessage message = WARN_ENTRY_GENERATOR_NO_VALUE_IN_TEMPLATE_LINE.get(lineNumber,
                        template.getName());
                warnings.add(message);
            } else {
                LocalizableMessage message = WARN_ENTRY_GENERATOR_NO_VALUE_IN_BRANCH_EXTRA_LINE.get(lineNumber,
                        String.valueOf(branch.getBranchDN()));
                warnings.add(message);
            }
        }

        int phase = PARSING_STATIC_TEXT;
        int previousPhase = PARSING_STATIC_TEXT;

        ArrayList<TemplateTag> tagList = new ArrayList<TemplateTag>();
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

            case PARSING_ESCAPED_CHAR:
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
            LocalizableMessage message = ERR_ENTRY_GENERATOR_INCOMPLETE_TAG.get(lineNumber);
            throw DecodeException.fatalError(message);
        }

        TemplateTag[] tagArray = new TemplateTag[tagList.size()];
        tagList.toArray(tagArray);
        return new TemplateLine(attributeType, lineNumber, tagArray, valueIsURL, valueIsBase64);
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
    private TemplateTag parseReplacementTag(String tagString, Branch branch, Template template, int lineNumber,
            Map<String, TemplateTag> tags, List<LocalizableMessage> warnings) throws DecodeException {
        // The components of the replacement tag will be separated by colons,
        // with
        // the first being the tag name and the remainder being arguments.
        StringTokenizer tokenizer = new StringTokenizer(tagString, ":");
        String tagName = tokenizer.nextToken().trim();
        String lowerTagName = tagName.toLowerCase();

        TemplateTag t = getTag(lowerTagName);
        if (t == null) {
            t = tags.get(lowerTagName);
            if (t == null) {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_NO_SUCH_TAG.get(tagName, lineNumber);
                throw DecodeException.fatalError(message);
            }
        }

        ArrayList<String> argList = new ArrayList<String>();
        while (tokenizer.hasMoreTokens()) {
            argList.add(tokenizer.nextToken().trim());
        }

        String[] args = new String[argList.size()];
        argList.toArray(args);

        TemplateTag newTag;
        try {
            newTag = t.getClass().newInstance();
        } catch (Exception e) {
            LocalizableMessage message = ERR_ENTRY_GENERATOR_CANNOT_INSTANTIATE_NEW_TAG.get(tagName, lineNumber,
                    String.valueOf(e));
            throw DecodeException.fatalError(message, e);
        }

        if (branch == null) {
            newTag.initializeForTemplate(schema, this, template, args, lineNumber, warnings);
        } else {
            if (newTag.allowedInBranch()) {
                newTag.initializeForBranch(schema, this, branch, args, lineNumber, warnings);
            } else {
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TAG_NOT_ALLOWED_IN_BRANCH.get(newTag.getName(),
                        lineNumber);
                throw DecodeException.fatalError(message);
            }
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
    private TemplateTag parseAttributeTag(String tagString, Branch branch, Template template, int lineNumber,
            List<LocalizableMessage> warnings) throws DecodeException {
        // The attribute tag must have at least one argument, which is the name
        // of
        // the attribute to reference. It may have a second argument, which is
        // the
        // number of characters to use from the attribute value. The arguments
        // will
        // be delimited by colons.
        StringTokenizer tokenizer = new StringTokenizer(tagString, ":");
        ArrayList<String> argList = new ArrayList<String>();
        while (tokenizer.hasMoreTokens()) {
            argList.add(tokenizer.nextToken());
        }

        String[] args = new String[argList.size()];
        argList.toArray(args);

        AttributeValueTag tag = new AttributeValueTag();
        if (branch == null) {
            tag.initializeForTemplate(schema, this, template, args, lineNumber, warnings);
        } else {
            tag.initializeForBranch(schema, this, branch, args, lineNumber, warnings);
        }

        return tag;
    }

    /**
     * Retrieves a File object based on the provided path. If the given path is
     * absolute, then that absolute path will be used. If it is relative, then
     * it will first be evaluated relative to the current working directory. If
     * that path doesn't exist, then it will be evaluated relative to the
     * resource path. If that path doesn't exist, then it will be evaluated
     * relative to the directory containing the template file.
     *
     * @param path
     *            The path provided for the file.
     * @return The File object for the specified path, or <CODE>null</CODE> if
     *         the specified file could not be found.
     */
    public File getFile(String path) {
        // First, see if the file exists using the given path. This will work if
        // the file is absolute, or it's relative to the current working
        // directory.
        File f = new File(path);
        if (f.exists()) {
            return f;
        }

        // If the provided path was absolute, then use it anyway, even though we
        // couldn't find the file.
        if (f.isAbsolute()) {
            return f;
        }

        // Try a path relative to the resource directory.
        String newPath = resourcePath + File.separator + path;
        f = new File(newPath);
        if (f.exists()) {
            return f;
        }

        // Try a path relative to the template directory, if it's available.
        if (templatePath != null) {
            newPath = templatePath = File.separator + path;
            f = new File(newPath);
            if (f.exists()) {
                return f;
            }
        }

        return null;
    }

    /**
     * Retrieves the lines of the specified file as a string array. If the
     * result is already cached, then it will be used. If the result is not
     * cached, then the file data will be cached so that the contents can be
     * re-used if there are multiple references to the same file.
     *
     * @param file
     *            The file for which to retrieve the contents.
     * @return An array containing the lines of the specified file.
     * @throws IOException
     *             If a problem occurs while reading the file.
     */
    public String[] getFileLines(File file) throws IOException {
        String absolutePath = file.getAbsolutePath();
        String[] lines = fileLines.get(absolutePath);
        if (lines == null) {
            ArrayList<String> lineList = new ArrayList<String>();

            BufferedReader reader = new BufferedReader(new FileReader(file));
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                } else {
                    lineList.add(line);
                }
            }

            reader.close();

            lines = new String[lineList.size()];
            lineList.toArray(lines);
            lineList.clear();
            fileLines.put(absolutePath, lines);
        }

        return lines;
    }

    /**
     * Generates the entries and writes them to the provided entry writer.
     *
     * @param entryWriter
     *            The entry writer that should be used to write the entries.
     * @return The result that indicates whether processing should continue.
     * @throws IOException
     *             If an error occurs while writing the entry.
     * @throws DecodeException
     *             If some other problem occurs.
     */
    public TagResult generateEntries(EntryWriter entryWriter) throws IOException, DecodeException {
        for (Branch b : branches.values()) {
            TagResult result = b.writeEntries(entryWriter);
            if (!(result.keepProcessingTemplateFile())) {
                return result;
            }
        }

        entryWriter.closeEntryWriter();
        return TagResult.SUCCESS_RESULT;
    }

    /**
     * Writer of generated entries.
     */
    public interface EntryWriter {
        /**
         * Writes the provided entry to the appropriate target.
         *
         * @param entry
         *            The entry to be written.
         * @return <CODE>true</CODE> if the entry writer will accept additional
         *         entries, or <CODE>false</CODE> if no more entries should be
         *         written.
         * @throws IOException
         *             If a problem occurs while writing the entry to its
         *             intended destination.
         * @throws DecodeException
         *             If some other problem occurs.
         */
        public boolean writeEntry(TemplateEntry entry) throws IOException, DecodeException;

        /**
         * Notifies the entry writer that no more entries will be provided and
         * that any associated cleanup may be performed.
         */
        public void closeEntryWriter();
    }

    /**
     * Represents a branch that should be included in the generated results. A
     * branch may or may not have subordinate entries.
     */
    static class Branch {
        /** The DN for this branch entry. */
        private DN branchDN;

        /**
         * The number of entries that should be created below this branch for
         * each subordinate template.
         */
        private int[] numEntriesPerTemplate;

        /** The names of the subordinate templates for this branch. */
        private String[] subordinateTemplateNames;

        /** The set of subordinate templates for this branch. */
        private Template[] subordinateTemplates;

        /** The set of template lines that correspond to the RDN components. */
        private TemplateLine[] rdnLines;

        /** The set of extra lines that should be included in this branch entry. */
        private TemplateLine[] extraLines;

        private Schema schema;

        /**
         * Creates a new branch with the provided information.
         *
         * @param templateFile
         *            The template file in which this branch appears.
         * @param branchDN
         *            The DN for this branch entry.
         * @param schema
         *            schema used to create attribute
         */
        public Branch(TemplateFile templateFile, DN branchDN, Schema schema) {
            this(templateFile, branchDN, schema, new String[0], new int[0], new TemplateLine[0]);
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
         */
        public Branch(TemplateFile templateFile, DN branchDN, Schema schema, String[] subordinateTemplateNames,
                int[] numEntriesPerTemplate, TemplateLine[] extraLines) {
            this.branchDN = branchDN;
            this.schema = schema;
            this.subordinateTemplateNames = subordinateTemplateNames;
            this.numEntriesPerTemplate = numEntriesPerTemplate;
            this.extraLines = extraLines;

            subordinateTemplates = null;

            // Get the RDN template lines based just on the entry DN.
            Entry entry = LinkedHashMapEntry.FACTORY.newEntry(branchDN);

            ArrayList<LocalizableMessage> warnings = new ArrayList<LocalizableMessage>();
            ArrayList<TemplateLine> lineList = new ArrayList<TemplateLine>();

            for (ObjectClass objectClass : Entries.getObjectClasses(entry, schema)) {
                try {
                    String[] valueStrings = new String[] { objectClass.getNameOrOID() };
                    TemplateTag[] tags = new TemplateTag[1];
                    tags[0] = new StaticTextTag();
                    tags[0].initializeForBranch(schema, templateFile, this, valueStrings, 0, warnings);

                    TemplateLine l = new TemplateLine(CoreSchema.getObjectClassAttributeType(), 0, tags);
                    lineList.add(l);
                } catch (Exception e) {
                    // This should never happen.
                    e.printStackTrace();
                }
            }

            for (Attribute attribute : entry.getAllAttributes()) {
                for (String value : attribute.toArray(new String[attribute.size()])) {
                    try {
                        String[] valueStrings = new String[] { value };
                        TemplateTag[] tags = new TemplateTag[1];
                        tags[0] = new StaticTextTag();
                        tags[0].initializeForBranch(schema, templateFile, this, valueStrings, 0, warnings);
                        lineList.add(
                                new TemplateLine(attribute.getAttributeDescription().getAttributeType(), 0, tags));
                    } catch (Exception e) {
                        // This should never happen.
                        e.printStackTrace();
                    }
                }
            }

            rdnLines = new TemplateLine[lineList.size()];
            lineList.toArray(rdnLines);
        }

        /**
         * Performs any necessary processing to ensure that the branch
         * initialization is completed. In particular, it should make sure that
         * all referenced subordinate templates actually exist in the template
         * file.
         *
         * @param templates
         *            The set of templates defined in the template file.
         * @throws DecodeException
         *             If any of the subordinate templates are not defined in
         *             the template file.
         */
        public void completeBranchInitialization(Map<String, Template> templates) throws DecodeException {
            if (subordinateTemplateNames == null) {
                subordinateTemplateNames = new String[0];
                subordinateTemplates = new Template[0];
            } else {
                subordinateTemplates = new Template[subordinateTemplateNames.length];
                for (int i = 0; i < subordinateTemplates.length; i++) {
                    subordinateTemplates[i] = templates.get(subordinateTemplateNames[i].toLowerCase());
                    if (subordinateTemplates[i] == null) {
                        LocalizableMessage message = ERR_ENTRY_GENERATOR_UNDEFINED_BRANCH_SUBORDINATE.get(
                                branchDN.toString(), subordinateTemplateNames[i]);
                        throw DecodeException.fatalError(message);
                    }
                }
            }
        }

        /**
         * Retrieves the DN for this branch entry.
         *
         * @return The DN for this branch entry.
         */
        public DN getBranchDN() {
            return branchDN;
        }

        /**
         * Retrieves the names of the subordinate templates for this branch.
         *
         * @return The names of the subordinate templates for this branch.
         */
        public String[] getSubordinateTemplateNames() {
            return subordinateTemplateNames;
        }

        /**
         * Retrieves the set of subordinate templates used to generate entries
         * below this branch. Note that the subordinate templates will not be
         * available until the <CODE>completeBranchInitialization</CODE> method
         * has been called.
         *
         * @return The set of subordinate templates used to generate entries
         *         below this branch.
         */
        public Template[] getSubordinateTemplates() {
            return subordinateTemplates;
        }

        /**
         * Retrieves the number of entries that should be created below this
         * branch for each subordinate template.
         *
         * @return The number of entries that should be created below this
         *         branch for each subordinate template.
         */
        public int[] getNumEntriesPerTemplate() {
            return numEntriesPerTemplate;
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
        public void addSubordinateTemplate(String name, int numEntries) {
            String[] newNames = new String[subordinateTemplateNames.length + 1];
            int[] newCounts = new int[numEntriesPerTemplate.length + 1];

            System.arraycopy(subordinateTemplateNames, 0, newNames, 0, subordinateTemplateNames.length);
            System.arraycopy(numEntriesPerTemplate, 0, newCounts, 0, numEntriesPerTemplate.length);

            newNames[subordinateTemplateNames.length] = name;
            newCounts[numEntriesPerTemplate.length] = numEntries;

            subordinateTemplateNames = newNames;
            numEntriesPerTemplate = newCounts;
        }

        /**
         * Retrieves the set of extra lines that should be included in this
         * branch entry.
         *
         * @return The set of extra lines that should be included in this branch
         *         entry.
         */
        public TemplateLine[] getExtraLines() {
            return extraLines;
        }

        /**
         * Adds the provided template line to the set of extra lines for this
         * branch.
         *
         * @param line
         *            The line to add to the set of extra lines for this branch.
         */
        public void addExtraLine(TemplateLine line) {
            TemplateLine[] newExtraLines = new TemplateLine[extraLines.length + 1];
            System.arraycopy(extraLines, 0, newExtraLines, 0, extraLines.length);
            newExtraLines[extraLines.length] = line;

            extraLines = newExtraLines;
        }

        /**
         * Indicates whether this branch contains a reference to the specified
         * attribute type, either in the RDN components of the DN or in the
         * extra lines.
         *
         * @param attributeType
         *            The attribute type for which to make the determination.
         * @return <CODE>true</CODE> if the branch does contain the specified
         *         attribute type, or <CODE>false</CODE> if it does not.
         */
        public boolean hasAttribute(AttributeType attributeType) {
            if (branchDN.rdn().getAttributeValue(attributeType) != null) {
                return true;
            }

            for (TemplateLine l : extraLines) {
                if (l.getAttributeType().equals(attributeType)) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Writes the entry for this branch, as well as all appropriate
         * subordinate entries.
         *
         * @param entryWriter
         *            The entry writer to which the entries should be written.
         * @return The result that indicates whether processing should continue.
         * @throws IOException
         *             If a problem occurs while attempting to write to the LDIF
         *             writer.
         * @throws DecodeException
         *             If some other problem occurs.
         */
        public TagResult writeEntries(EntryWriter entryWriter) throws IOException, DecodeException {
            // Create a new template entry and populate it based on the RDN
            // attributes and extra lines.
            TemplateEntry entry = new TemplateEntry(this);

            for (TemplateLine l : rdnLines) {
                TagResult r = l.generateLine(entry);
                if (!(r.keepProcessingEntry() && r.keepProcessingParent() && r.keepProcessingTemplateFile())) {
                    return r;
                }
            }

            for (TemplateLine l : extraLines) {
                TagResult r = l.generateLine(entry);
                if (!(r.keepProcessingEntry() && r.keepProcessingParent() && r.keepProcessingTemplateFile())) {
                    return r;
                }
            }

            if (!entryWriter.writeEntry(entry)) {
                return TagResult.STOP_PROCESSING;
            }

            for (int i = 0; i < subordinateTemplates.length; i++) {
                TagResult r = subordinateTemplates[i].writeEntries(entryWriter, branchDN, numEntriesPerTemplate[i]);
                if (!(r.keepProcessingParent() && r.keepProcessingTemplateFile())) {
                    if (r.keepProcessingTemplateFile()) {
                        // We don't want to propagate a "stop processing parent"
                        // all the way up the chain.
                        return TagResult.SUCCESS_RESULT;
                    }

                    return r;
                }
            }

            return TagResult.SUCCESS_RESULT;
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
        private org.forgerock.opendj.ldap.schema.AttributeType[] rdnAttributes;

        /** The number of entries to create for each subordinate template. */
        private int[] numEntriesPerTemplate;

        /** The name for this template. */
        private String name;

        /** The names of the subordinate templates below this template. */
        private String[] subordinateTemplateNames;

        /** The subordinate templates below this template. */
        private Template[] subordinateTemplates;

        /** The template file that contains this template. */
        private TemplateFile templateFile;

        /** The set of template lines for this template. */
        private TemplateLine[] templateLines;

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
         */
        public Template(TemplateFile templateFile, String name, AttributeType[] rdnAttributes,
                String[] subordinateTemplateNames, int[] numEntriesPerTemplate) {
            this.templateFile = templateFile;
            this.name = name;
            this.rdnAttributes = rdnAttributes;
            this.subordinateTemplateNames = subordinateTemplateNames;
            this.numEntriesPerTemplate = numEntriesPerTemplate;

            templateLines = new TemplateLine[0];
            subordinateTemplates = null;
        }

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
        public Template(TemplateFile templateFile, String name, AttributeType[] rdnAttributes,
                String[] subordinateTemplateNames, int[] numEntriesPerTemplate, TemplateLine[] templateLines) {
            this.templateFile = templateFile;
            this.name = name;
            this.rdnAttributes = rdnAttributes;
            this.subordinateTemplateNames = subordinateTemplateNames;
            this.numEntriesPerTemplate = numEntriesPerTemplate;
            this.templateLines = templateLines;

            subordinateTemplates = null;
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
        public void completeTemplateInitialization(Map<String, Template> templates) throws DecodeException {
            // Make sure that all of the specified subordinate templates exist.
            if (subordinateTemplateNames == null) {
                subordinateTemplateNames = new String[0];
                subordinateTemplates = new Template[0];
            } else {
                subordinateTemplates = new Template[subordinateTemplateNames.length];
                for (int i = 0; i < subordinateTemplates.length; i++) {
                    subordinateTemplates[i] = templates.get(subordinateTemplateNames[i].toLowerCase());
                    if (subordinateTemplates[i] == null) {
                        LocalizableMessage message = ERR_ENTRY_GENERATOR_UNDEFINED_TEMPLATE_SUBORDINATE.get(
                                subordinateTemplateNames[i], name);
                        throw DecodeException.fatalError(message);
                    }
                }
            }

            // Make sure that all of the RDN attributes are defined.
            HashSet<AttributeType> rdnAttrs = new HashSet<AttributeType>(rdnAttributes.length);
            for (AttributeType t : rdnAttributes) {
                rdnAttrs.add(t);
            }

            for (TemplateLine l : templateLines) {
                if (rdnAttrs.remove(l.getAttributeType())) {
                    if (rdnAttrs.isEmpty()) {
                        break;
                    }
                }
            }

            if (!rdnAttrs.isEmpty()) {
                AttributeType t = rdnAttrs.iterator().next();
                LocalizableMessage message = ERR_ENTRY_GENERATOR_TEMPLATE_MISSING_RDN_ATTR.get(name, t.getNameOrOID());
                throw DecodeException.fatalError(message);
            }
        }

        /**
         * Retrieves the name for this template.
         *
         * @return The name for this template.
         */
        public String getName() {
            return name;
        }

        /**
         * Retrieves the set of attribute types that are used in the RDN for
         * entries generated using this template.
         *
         * @return The set of attribute types that are used in the RDN for
         *         entries generated using this template.
         */
        public AttributeType[] getRDNAttributes() {
            return rdnAttributes;
        }

        /**
         * Retrieves the names of the subordinate templates used to generate
         * entries below entries created by this template.
         *
         * @return The names of the subordinate templates used to generate
         *         entries below entries created by this template.
         */
        public String[] getSubordinateTemplateNames() {
            return subordinateTemplateNames;
        }

        /**
         * Retrieves the subordinate templates used to generate entries below
         * entries created by this template.
         *
         * @return The subordinate templates used to generate entries below
         *         entries created by this template.
         */
        public Template[] getSubordinateTemplates() {
            return subordinateTemplates;
        }

        /**
         * Retrieves the number of entries that should be created for each
         * subordinate template.
         *
         * @return The number of entries that should be created for each
         *         subordinate template.
         */
        public int[] getNumEntriesPerTemplate() {
            return numEntriesPerTemplate;
        }

        /**
         * Retrieves the set of template lines for this template.
         *
         * @return The set of template lines for this template.
         */
        public TemplateLine[] getTemplateLines() {
            return templateLines;
        }

        /**
         * Adds the provided template line to this template.
         *
         * @param line
         *            The template line to add to this template.
         */
        public void addTemplateLine(TemplateLine line) {
            TemplateLine[] newTemplateLines = new TemplateLine[templateLines.length + 1];
            System.arraycopy(templateLines, 0, newTemplateLines, 0, templateLines.length);
            newTemplateLines[templateLines.length] = line;
            templateLines = newTemplateLines;
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
        public boolean hasAttribute(AttributeType attributeType) {
            for (TemplateLine l : templateLines) {
                if (l.getAttributeType().equals(attributeType)) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Writes the entry for this template, as well as all appropriate
         * subordinate entries.
         *
         * @param entryWriter
         *            The entry writer that will be used to write the entries.
         * @param parentDN
         *            The DN of the entry below which the subordinate entries
         *            should be generated.
         * @param count
         *            The number of entries to generate based on this template.
         * @return The result that indicates whether processing should continue.
         * @throws IOException
         *             If a problem occurs while attempting to write to the LDIF
         *             writer.
         * @throws DecodeException
         *             If some other problem occurs.
         */
        public TagResult writeEntries(EntryWriter entryWriter, DN parentDN, int count) throws IOException,
                DecodeException {
            for (int i = 0; i < count; i++) {
                templateFile.nextFirstAndLastNames();
                TemplateEntry templateEntry = new TemplateEntry(this, parentDN);

                for (TemplateLine l : templateLines) {
                    TagResult r = l.generateLine(templateEntry);
                    if (!(r.keepProcessingEntry() && r.keepProcessingParent() && r.keepProcessingTemplateFile())) {
                        return r;
                    }
                }

                if (!entryWriter.writeEntry(templateEntry)) {
                    return TagResult.STOP_PROCESSING;
                }

                for (int j = 0; j < subordinateTemplates.length; j++) {
                    TagResult r = subordinateTemplates[j].writeEntries(entryWriter, templateEntry.getDN(),
                            numEntriesPerTemplate[j]);
                    if (!(r.keepProcessingParent() && r.keepProcessingTemplateFile())) {
                        if (r.keepProcessingTemplateFile()) {
                            // We don't want to propagate a
                            // "stop processing parent"
                            // all the way up the chain.
                            return TagResult.SUCCESS_RESULT;
                        }

                        return r;
                    }
                }
            }

            return TagResult.SUCCESS_RESULT;
        }
    }

    /**
     * Represents an entry that is generated using a branch or a template.
     */
    static class TemplateEntry {

        /** Template entry that represents a null object. */
        static final TemplateEntry NULL_TEMPLATE_ENTRY = new TemplateEntry(null, null);

        /**
         * The branch used to generate this entry (if it is associated with a
         * branch).
         */
        private Branch branch;

        /** The DN for this template entry, if it is known. */
        private DN dn;

        /**
         * The DN of the parent entry for this template entry, if it is
         * available.
         */
        private DN parentDN;

        /**
         * The set of attributes associated with this template entry, mapped
         * from the lowercase name of the attribute to the list of generated
         * values.
         */
        private LinkedHashMap<AttributeType, ArrayList<TemplateValue>> attributes;

        /**
         * The template used to generate this entry (if it is associated with a
         * template).
         */
        private Template template;

        /**
         * Creates a new template entry that will be associated with the
         * provided branch.
         *
         * @param branch
         *            The branch to use when creating this template entry.
         */
        public TemplateEntry(Branch branch) {
            this.branch = branch;

            dn = branch.getBranchDN();
            template = null;
            parentDN = null;
            attributes = new LinkedHashMap<AttributeType, ArrayList<TemplateValue>>();
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
        public TemplateEntry(Template template, DN parentDN) {
            this.template = template;
            this.parentDN = parentDN;

            dn = null;
            branch = null;
            attributes = new LinkedHashMap<AttributeType, ArrayList<TemplateValue>>();
        }

        /**
         * Retrieves the branch used to generate this entry.
         *
         * @return The branch used to generate this entry, or <CODE>null</CODE>
         *         if it is associated with a template instead of a branch.
         */
        public Branch getBranch() {
            return branch;
        }

        /**
         * Retrieves the template used to generate this entry.
         *
         * @return The template used to generate this entry, or
         *         <CODE>null</CODE> if it is associated with a branch instead
         *         of a template.
         */
        public Template getTemplate() {
            return template;
        }

        /**
         * Retrieves the DN of the parent entry for this template entry.
         *
         * @return The DN of the parent entry for this template entry, or
         *         <CODE>null</CODE> if there is no parent DN.
         */
        public DN getParentDN() {
            return parentDN;
        }

        /**
         * Retrieves the DN for this template entry, if it is known.
         *
         * @return The DN for this template entry if it is known, or
         *         <CODE>null</CODE> if it cannot yet be determined.
         */
        public DN getDN() {
            // TODO : building to review, particularly building RN with multiple
            // AVA
            // using StringBuilder because no facility using other way
            if (dn == null) {
                RDN rdn;
                AttributeType[] rdnAttrs = template.getRDNAttributes();
                if (rdnAttrs.length == 1) {
                    AttributeType type = rdnAttrs[0];
                    TemplateValue templateValue = getValue(type);
                    if (templateValue == null) {
                        return null;
                    }
                    rdn = new RDN(type, templateValue.getValueAsString());
                } else {
                    StringBuilder rdnString = new StringBuilder();
                    for (int i = 0; i < rdnAttrs.length; i++) {
                        AttributeType type = rdnAttrs[i];
                        TemplateValue templateValue = getValue(type);
                        if (templateValue == null) {
                            return null;
                        }
                        if (i > 0) {
                            rdnString.append("+");
                        }
                        rdnString.append(new AVA(type, templateValue.getValueAsString()).toString());
                    }
                    rdn = RDN.valueOf(rdnString.toString());
                }
                dn = parentDN.child(rdn);
            }
            return dn;
        }

        /**
         * Indicates whether this entry contains one or more values for the
         * specified attribute type.
         *
         * @param attributeType
         *            The attribute type for which to make the determination.
         * @return <CODE>true</CODE> if this entry contains one or more values
         *         for the specified attribute type, or <CODE>false</CODE> if
         *         not.
         */
        public boolean hasAttribute(AttributeType attributeType) {
            return attributes.containsKey(attributeType);
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
        public TemplateValue getValue(AttributeType attributeType) {
            ArrayList<TemplateValue> valueList = attributes.get(attributeType);
            if ((valueList == null) || valueList.isEmpty()) {
                return null;
            } else {
                return valueList.get(0);
            }
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
        public List<TemplateValue> getValues(AttributeType attributeType) {
            ArrayList<TemplateValue> valueList = attributes.get(attributeType);
            return valueList;
        }

        /**
         * Adds the provided template value to this entry.
         *
         * @param value
         *            The value to add to this entry.
         */
        public void addValue(TemplateValue value) {
            ArrayList<TemplateValue> valueList = attributes.get(value.getAttributeType());
            if (valueList == null) {
                valueList = new ArrayList<TemplateValue>();
                valueList.add(value);
                attributes.put(value.getAttributeType(), valueList);
            } else {
                valueList.add(value);
            }
        }

        /**
         * Returns an entry from this template entry.
         *
         * @return an entry
         */
        public Entry toEntry() {
            Entry entry = LinkedHashMapEntry.FACTORY.newEntry(getDN());
            AttributeFactory attributeFactory = LinkedAttribute.FACTORY;

            for (AttributeType attributeType : attributes.keySet()) {
                ArrayList<TemplateValue> valueList = attributes.get(attributeType);
                Attribute newAttribute = attributeFactory.newAttribute(AttributeDescription.create(attributeType));
                for (TemplateValue value : valueList) {
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
        /** The attribute type for this template line. */
        private AttributeType attributeType;

        /**
         * The line number on which this template line appears in the template
         * file.
         */
        private int lineNumber;

        /** The set of tags for this template line. */
        private TemplateTag[] tags;

        /** Whether this line corresponds to an URL value or not. */
        private boolean isURL;

        /** Whether this line corresponds to a base64 encoded value or not. */
        private boolean isBase64;

        /**
         * Retrieves the attribute type for this template line.
         *
         * @return The attribute type for this template line.
         */
        public AttributeType getAttributeType() {
            return attributeType;
        }

        /**
         * Retrieves the line number on which this template line appears in the
         * template file.
         *
         * @return The line number on which this template line appears in the
         *         template file.
         */
        public int getLineNumber() {
            return lineNumber;
        }

        /**
         * Returns whether the value of this template line corresponds to an URL
         * or not.
         *
         * @return <CODE>true</CODE> if the value of this template line
         *         corresponds to an URL and <CODE>false</CODE> otherwise.
         */
        public boolean isURL() {
            return isURL;
        }

        /**
         * Returns whether the value of this template line corresponds to a
         * Base64 encoded value or not.
         *
         * @return <CODE>true</CODE> if the value of this template line
         *         corresponds to a Base64 encoded value and <CODE>false</CODE>
         *         otherwise.
         */
        public boolean isBase64() {
            return isBase64;
        }

        /**
         * Creates a new template line with the provided information.
         *
         * @param attributeType
         *            The attribute type for this template line.
         * @param lineNumber
         *            The line number on which this template line appears in the
         *            template file.
         * @param tags
         *            The set of tags for this template line.
         */
        public TemplateLine(AttributeType attributeType, int lineNumber, TemplateTag[] tags) {
            this(attributeType, lineNumber, tags, false, false);
        }

        /**
         * Creates a new template line with the provided information.
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
        public TemplateLine(AttributeType attributeType, int lineNumber, TemplateTag[] tags, boolean isURL,
                boolean isBase64) {
            this.attributeType = attributeType;
            this.lineNumber = lineNumber;
            this.tags = tags;
            this.isURL = isURL;
            this.isBase64 = isBase64;
        }

        /**
         * Generates the content for this template line and places it in the
         * provided template entry.
         *
         * @param templateEntry
         *            The template entry being generated.
         * @return The result of generating the template line.
         */
        public TagResult generateLine(TemplateEntry templateEntry) {
            TemplateValue value = new TemplateValue(this);

            for (TemplateTag t : tags) {
                TagResult result = t.generateValue(templateEntry, value);
                if (!(result.keepProcessingLine() && result.keepProcessingEntry()
                        && result.keepProcessingParent() && result.keepProcessingTemplateFile())) {
                    return result;
                }
            }

            templateEntry.addValue(value);
            return TagResult.SUCCESS_RESULT;
        }
    }

    /**
     * Represents a value generated from a template line.
     */
    static class TemplateValue {
        /** The generated template value. */
        private StringBuilder templateValue;

        /** The template line used to generate this value. */
        private TemplateLine templateLine;

        /**
         * Creates a new template value with the provided information.
         *
         * @param templateLine
         *            The template line used to generate this value.
         */
        public TemplateValue(TemplateLine templateLine) {
            this.templateLine = templateLine;
            templateValue = new StringBuilder();
        }

        /**
         * Retrieves the template line used to generate this value.
         *
         * @return The template line used to generate this value.
         */
        public TemplateLine getTemplateLine() {
            return templateLine;
        }

        /**
         * Retrieves the attribute type for this template value.
         *
         * @return The attribute type for this template value.
         */
        public AttributeType getAttributeType() {
            return templateLine.getAttributeType();
        }

        /**
         * Retrieves the generated value.
         *
         * @return The generated value.
         */
        public StringBuilder getValue() {
            return templateValue;
        }

        /**
         * Retrieves the generated value as String.
         *
         * @return The generated value.
         */
        public String getValueAsString() {
            return templateValue.toString();
        }

        /**
         * Appends the provided string to this template value.
         *
         * @param s
         *            The string to append.
         */
        public void append(String s) {
            templateValue.append(s);
        }

        /**
         * Appends the string representation of the provided object to this
         * template value.
         *
         * @param o
         *            The object to append.
         */
        public void append(Object o) {
            templateValue.append(String.valueOf(o));
        }
    }
}
