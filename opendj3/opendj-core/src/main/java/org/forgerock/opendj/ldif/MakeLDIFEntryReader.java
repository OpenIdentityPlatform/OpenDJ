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
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.forgerock.opendj.ldif;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy;
import org.forgerock.opendj.ldif.TemplateFile.EntryWriter;
import org.forgerock.opendj.ldif.TemplateFile.TemplateEntry;

import com.forgerock.opendj.util.Validator;

/**
 * This reader generates entries from a template file, which can be provided
 * as a file, a list of lines, an array of lines, or an input stream.
 */
public final class MakeLDIFEntryReader implements EntryReader {

    private static final TemplateEntry POISON_ENTRY = TemplateFile.TemplateEntry.NULL_TEMPLATE_ENTRY;

    /** Template file that contains directives for generation of entries. */
    private final TemplateFile templateFile;

    /** Queue used to hold generated entries until they can be read. */
    private final BlockingQueue<TemplateEntry> entryQueue;

    /** The next available entry. */
    private TemplateEntry nextEntry;

    private final List<LocalizableMessage> warnings;

    private volatile IOException ioException;

    private volatile boolean generationIsFinished = false;

    private volatile boolean isClosed = false;

    /** Thread that provides generation of entries. */
    private Thread generationThread;

    /**
     * Creates a reader.
     *
     * @param templateFile
     *            contains definition of entries to generate. It must have
     *            already been parsed
     * @param warnings
     *            list of warnings that were issued when parsing the template
     *            file
     * @param entryQueue
     *            used to hold generated entries and block generation until
     *            entries are read
     */
    private MakeLDIFEntryReader(TemplateFile templateFile, LinkedList<LocalizableMessage> warnings,
            BlockingQueue<TemplateEntry> entryQueue) {
        this.templateFile = templateFile;
        this.warnings = warnings;
        this.entryQueue = entryQueue;
    }

    /**
     * Returns a builder to create a reader based on a template file given by
     * the provided path.
     *
     * @param templatePath
     *            path of the template file
     * @return a builder allowing to create the reader
     */
    public static Builder newReader(final String templatePath) {
        return new Builder(templatePath);
    }

    /**
     * Returns a builder to create a reader based on a template file given by
     * the provided lines.
     *
     * @param templateLines
     *            lines defining the template file
     * @return a builder allowing to create the reader
     */
    public static Builder newReader(final String... templateLines) {
        return new Builder(templateLines);
    }

    /**
     * Returns a builder to create a reader based on a template file given by
     * the provided lines.
     *
     * @param templateLines
     *            lines defining the template file
     * @return a builder allowing to create the reader
     */
    public static Builder newReader(final List<String> templateLines) {
        return new Builder(templateLines.toArray(new String[templateLines.size()]));
    }

    /**
     * Returns a builder to create a reader based on a template file given by
     * the provided stream.
     *
     * @param templateStream
     *            input stream to read the template file
     * @return a builder allowing to create the reader
     */
    public static Builder newReader(final InputStream templateStream) {
        return new Builder(templateStream);
    }

    /**
     * Builder of {@code MakeLDIFEntryReader readers}.
     * <p>
     *
     * To build a reader with all default values:
     * <pre>
     * {@code reader = MakeLDIFEntryReader.newReader(...).build() }
     * </pre>
     * <p>
     *
     * To build a reader with some custom values, using the
     * <code>set</code> methods:
     * <pre>
     * {@code reader = MakeLDIFEntryReader.newReader(...).
     *    setResourcePath(path).
     *    setSchema(schema).
     *    build() }
     * </pre>
     */
    public static final class Builder {

        private static final int DEFAULT_QUEUE_SIZE = 100;
        private static final int DEFAULT_RANDOM_SEED = 1;
        private static final String DEFAULT_RESOURCE_PATH = ".";

        private String templatePath;
        private String[] templateLines;
        private InputStream templateStream;

        private TemplateFile templateFile;
        private int maxNumberOfEntriesInQueue = DEFAULT_QUEUE_SIZE;
        private int randomSeed = DEFAULT_RANDOM_SEED;
        private String resourcePath = DEFAULT_RESOURCE_PATH;
        private Schema schema;
        private SchemaValidationPolicy schemaValidationPolicy;

        private Builder(String templatePath) {
            this.templatePath = templatePath;
        }

        private Builder(String[] templateLines) {
            this.templateLines = templateLines;
        }

        private Builder(InputStream templateStream) {
            this.templateStream = templateStream;
        }

        /**
         * Sets the capacity of the queue holding generated entries.
         *
         * @param max
         *            capacity of the queue that holds generated entries
         * @return A reference to this {@code MakeLDIFEntryReader.Builder}.
         */
        public Builder setMaxNumberOfEntriesInQueue(final int max) {
            Validator.ensureTrue(max > 0, "queue capacity must be strictly superior to zero");
            maxNumberOfEntriesInQueue = max;
            return this;
        }

        /**
         * Sets the random seed to use when generating entries.
         *
         * @param seed
         *            seed to use
         * @return A reference to this {@code MakeLDIFEntryReader.Builder}.
         */
        public Builder setRandomSeed(final int seed) {
            randomSeed = seed;
            return this;
        }

        /**
         * Sets the resource path, used to looks for resources files like first
         * names, last names, or other custom resources.
         *
         * @param path
         *            resource path
         * @return A reference to this {@code MakeLDIFEntryReader.Builder}.
         */
        public Builder setResourcePath(final String path) {
            Validator.ensureNotNull(path);
            resourcePath = path;
            return this;
        }

        /**
         * Sets the schema which should be when generating entries. The default
         * schema is used if no other is specified.
         *
         * @param schema
         *            The schema which should be used for generating entries.
         * @return A reference to this {@code MakeLDIFEntryReader.Builder}.
         */
        public Builder setSchema(final Schema schema) {
            this.schema = schema;
            return this;
        }

        /**
         * Specifies the schema validation which should be used when generating
         * entries. If attribute value validation is enabled then all checks
         * will be performed.
         * <p>
         * Schema validation is disabled by default.
         * <p>
         * <b>NOTE:</b> this method copies the provided policy so changes made
         * to it after this method has been called will have no effect.
         *
         * @param policy
         *            The schema validation which should be used when generating
         *            entries.
         * @return A reference to this {@code MakeLDIFEntryReader.Builder}.
         */
        public Builder setSchemaValidationPolicy(final SchemaValidationPolicy policy) {
            this.schemaValidationPolicy = SchemaValidationPolicy.copyOf(policy);
            return this;
        }

        /**
         * Return an instance of reader.
         *
         * @return a new instance of reader
         * @throws IOException
         *             If an error occurs while reading template file.
         * @throws DecodeException
         *             If some other problem occurs during initialization
         */
        public MakeLDIFEntryReader build() throws IOException, DecodeException {
            if (schema == null) {
                schema = Schema.getDefaultSchema();
            }
            if (schemaValidationPolicy != null) {
                schema = schemaValidationPolicy.checkAttributesAndObjectClasses().needsChecking() ? schema
                        .asStrictSchema() : schema.asNonStrictSchema();
            }
            templateFile = new TemplateFile(schema, resourcePath, new Random(randomSeed));
            LinkedList<LocalizableMessage> warnings = new LinkedList<LocalizableMessage>();
            try {
                if (templatePath != null) {
                    templateFile.parse(templatePath, warnings);
                } else if (templateLines != null) {
                    templateFile.parse(templateLines, warnings);
                } else if (templateStream != null) {
                    templateFile.parse(templateStream, warnings);
                } else {
                    // this should never happen
                    throw DecodeException.fatalError(ERR_MAKELDIF_MISSING_TEMPLATE_FILE.get());
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw DecodeException.fatalError(ERR_MAKELDIF_EXCEPTION_DURING_PARSE.get(e.getMessage()), e);
            }
            MakeLDIFEntryReader reader = new MakeLDIFEntryReader(templateFile,
                    warnings, new LinkedBlockingQueue<TemplateEntry>(maxNumberOfEntriesInQueue));
            reader.startEntriesGeneration();
            return reader;
        }
    }

    /**
     * Start generation of entries by launching a separate thread.
     */
    private void startEntriesGeneration() {
        generationThread =
                new Thread(new EntriesGenerator(new MakeEntryWriter(), templateFile), "MakeLDIF Generator Thread");
        generationThread.start();
    }

    /**
     * Checks if there are some warning(s) after the parsing of template file.
     *
     * @return true if there is at least one warning
     */
    public boolean hasWarning() {
        return !warnings.isEmpty();
    }

    /**
     * Returns the warnings generated by the parsing of template file.
     *
     * @return the list of warnings, which is empty if there is no warning
     */
    public List<LocalizableMessage> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    @Override
    public void close() {
        isClosed = true;
        ioException = null;
        try {
            generationThread.join(0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean hasNext() throws IOException {
        if (isClosed) {
            return false;
        } else if (ioException != null) {
            throw ioException;
        } else if (nextEntry != null) {
            return true;
        } else if (generationIsFinished) {
            nextEntry = entryQueue.poll();
        } else {
            try {
                nextEntry = entryQueue.take();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        if (nextEntry == POISON_ENTRY) {
            nextEntry = null;
        }
        return nextEntry != null;
    }

    @Override
    public Entry readEntry() throws IOException {
        if (!hasNext()) {
            // LDIF reader has completed successfully.
            throw new NoSuchElementException();
        } else {
            final Entry entry = nextEntry.toEntry();
            nextEntry = null;
            return entry;
        }
    }

    /**
     * Entry writer that store entries into the entry queue of this reader, and
     * record close and exception events.
     */
    private final class MakeEntryWriter implements EntryWriter {

        @Override
        public boolean writeEntry(final TemplateEntry entry) {
            while (!isClosed) {
                try {
                    if (entryQueue.offer(entry, 500, TimeUnit.MILLISECONDS)) {
                        return true;
                    }
                } catch (InterruptedException ie) {
                    // nothing to do
                }
            }
            return false;
        }

        @Override
        public void closeEntryWriter() {
            generationIsFinished = true;
            writeEntry(POISON_ENTRY);
        }

        public void setIOException(final IOException ioe) {
            ioException = ioe;
        }
    }


    /**
     * Generator of entries, that writes entries to a provided
     * {@code EntryWriter writer}.
     */
    private static final class EntriesGenerator implements Runnable {

        private final MakeEntryWriter entryWriter;

        private final TemplateFile templateFile;

        /**
         * Creates a generator that writes to provided writer using the provided
         * template file.
         *
         * @param entryWriter
         * @param templateFile
         */
        EntriesGenerator(final MakeEntryWriter entryWriter, final TemplateFile templateFile) {
            this.entryWriter = entryWriter;
            this.templateFile = templateFile;
        }

        /**
         * Run the generation of entries.
         */
        public void run() {
            generate();
        }

        /**
         * Generates entries to the entry writer.
         */
        void generate() {
            try {
                templateFile.generateEntries(entryWriter);
            } catch (IOException e) {
                entryWriter.setIOException(e);
                entryWriter.closeEntryWriter();
            }
        }
    }

}
