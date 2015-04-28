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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldif;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.schema.Schema;

import org.forgerock.util.Reject;

/**
 * A template driven entry generator, as used by the make-ldif tool.
 * <p>
 * To build a generator with default values, including default template file,
 * use the empty constructor:
 *
 * <pre>
 * generator = new EntryGenerator();
 * </pre>
 * <p>
 * To build a generator with some custom values, use the non-empty constructor
 * and the <code>set</code> methods:
 *
 * <pre>
 * generator = new EntryGenerator(templatePath).setResourcePath(path).setSchema(schema)
 * </pre>
 */
public final class EntryGenerator implements EntryReader {

    private static final int DEFAULT_RANDOM_SEED = 1;

    /** Template file that contains directives for generation of entries. */
    private TemplateFile templateFile;

    /** Warnings issued by the parsing of the template file. */
    private final List<LocalizableMessage> warnings = new LinkedList<>();

    /** Indicates if the generator is closed. */
    private boolean isClosed;

    /** Indicates if the generator is initialized, which means template file has been parsed. */
    private boolean isInitialized;

    /** Random seed is used to generate random data. */
    private int randomSeed = DEFAULT_RANDOM_SEED;

    /**
     * Path to the directory that may contain additional resource files needed
     * during the generation process. It may be {@code null}.
     */
    private String resourcePath;

    /**
     * Schema is used to create attributes. If not provided, the default schema
     * is used.
     */
    private Schema schema;

    /**
     * Path of template file, can be {@code null} if template file has been
     * provided through another way.
     */
    private String templatePath;

    /**
     * Lines of template file, can be {@code null} if template file has been
     * provided through another way.
     */
    private String[] templateLines;

    /**
     * Input stream containing template file, can be {@code null} if template
     * file has been provided through another way.
     */
    private InputStream templateStream;

    /** Indicates whether branch entries should be generated.
     *
     *  Default is {@code true}
     */
    private boolean generateBranches = true;

    /** Dictionary of constants to use in the template file. */
    private Map<String, String> constants = new HashMap<>();

    /**
     * Creates a generator using default values.
     * <p>
     * The default template file will be used to generate entries.
     */
    public EntryGenerator() {
        // nothing to do
    }

    /**
     * Creates a generator from the provided template path.
     *
     * @param templatePath
     *            Path of the template file.
     */
    public EntryGenerator(final String  templatePath) {
        Reject.ifNull(templatePath);
        this.templatePath = templatePath;
    }

    /**
     * Creates a generator from the provided template lines.
     *
     * @param templateLines
     *            Lines defining the template file.
     */
    public EntryGenerator(final String... templateLines) {
        Reject.ifNull(templateLines);
        this.templateLines = templateLines;
    }

    /**
     * Creates a generator from the provided template lines.
     *
     * @param templateLines
     *            Lines defining the template file.
     */
    public EntryGenerator(final List<String> templateLines) {
        Reject.ifNull(templateLines);
        this.templateLines = templateLines.toArray(new String[templateLines.size()]);
    }

    /**
     * Creates a generator from the provided input stream.
     *
     * @param templateStream
     *            Input stream to read the template file.
     */
    public EntryGenerator(final InputStream templateStream) {
        Reject.ifNull(templateStream);
        this.templateStream = templateStream;
    }

    /**
     * Sets the random seed to use when generating entries.
     *
     * @param seed
     *            The random seed to use.
     * @return A reference to this {@code EntryGenerator}.
     */
    public EntryGenerator setRandomSeed(final int seed) {
        randomSeed = seed;
        return this;
    }

    /**
     * Sets the resource path, used to looks for resources files like first
     * names, last names, or other custom resources.
     *
     * @param path
     *            The resource path.
     * @return A reference to this {@code EntryGenerator}.
     */
    public EntryGenerator setResourcePath(final String path) {
        Reject.ifNull(path);
        resourcePath = path;
        return this;
    }

    /**
     * Sets the schema which should be when generating entries. The default
     * schema is used if no other is specified.
     *
     * @param schema
     *            The schema which should be used for generating entries.
     * @return A reference to this {@code EntryGenerator}.
     */
    public EntryGenerator setSchema(final Schema schema) {
        this.schema = schema;
        return this;
    }

    /**
     * Sets a constant to use in template file. It overrides the constant set in
     * the template file.
     *
     * @param name
     *            The name of the constant.
     * @param value
     *            The value of the constant.
     * @return A reference to this {@code EntryGenerator}.
     */
    public EntryGenerator setConstant(String name, Object value) {
        constants.put(name, value.toString());
        return this;
    }

    /**
     * Sets the flag which indicates whether branch entries should be generated.
     *
     * The default is {@code true}.
     *
     * @param generateBranches
     *              Indicates whether or not the branches DN entries has to be generated.
     * @return A reference to this {@code EntryGenerator}.
     */
    public EntryGenerator setGenerateBranches(boolean generateBranches) {
        this.generateBranches = generateBranches;
        return this;
    }

    /**
     * Checks if there are some warning(s) after parsing the template file.
     * <p>
     * Warnings are available only after the first call to {@code hasNext()} or
     * {@code readEntry()} methods.
     *
     * @return {@code true} if there is at least one warning.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Returns the warnings generated by the parsing of template file.
     * <p>
     * Warnings are available only after the first call to {@code hasNext()} or
     * {@code readEntry()} methods.
     *
     * @return The list of warnings, which is empty if there are no warnings.
     */
    public List<LocalizableMessage> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    @Override
    public void close() {
        isClosed = true;
    }

    @Override
    public boolean hasNext() throws IOException {
        if (isClosed) {
            return false;
        }
        ensureGeneratorIsInitialized();
        return templateFile.hasNext();
    }

    @Override
    public Entry readEntry() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException();
        } else {
            return templateFile.nextEntry();
        }
    }

    /**
     * Check that generator is initialized, and initialize it
     * if it has not been initialized.
     */
    private void ensureGeneratorIsInitialized() throws IOException {
        if (!isInitialized) {
            isInitialized = true;
            initialize();
        }
    }

    /**
     * Initializes the generator, by retrieving template file and parsing it.
     */
    private void initialize() throws IOException {
        if (schema == null) {
            schema = Schema.getDefaultSchema();
        }
        templateFile = new TemplateFile(schema, constants, resourcePath, new Random(randomSeed), generateBranches);
        try {
            if (templatePath != null) {
                templateFile.parse(templatePath, warnings);
            } else if (templateLines != null) {
                templateFile.parse(templateLines, warnings);
            } else if (templateStream != null) {
                templateFile.parse(templateStream, warnings);
            } else {
                // use default template file
                templateFile.parse(warnings);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw DecodeException.fatalError(ERR_ENTRY_GENERATOR_EXCEPTION_DURING_PARSE.get(e.getMessage()), e);
        }
    }

}
