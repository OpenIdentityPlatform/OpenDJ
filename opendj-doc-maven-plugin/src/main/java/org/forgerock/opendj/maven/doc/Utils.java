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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.maven.doc;

import static org.forgerock.util.Utils.*;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Provides utility methods for generating documentation.
 */
public final class Utils {

    /** Line separator. */
    static final String EOL = System.getProperty("line.separator");

    /**
     * Creates a directory unless it already exists.
     * @param directory     The directory to create.
     * @throws IOException  Failed to create directory.
     */
    static void createDirectory(final String directory) throws IOException {
        File dir = new File(directory);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Failed to create directory: " + directory);
            }
        }
    }

    /**
     * Returns the path to the current Java executable.
     * @return The path to the current Java executable.
     */
    static String getJavaCommand() {
        return System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    }

    /**
     * Copies the content of the original file to the copy.
     * @param original      The original file.
     * @param copy          The copy.
     * @throws IOException  Failed to make the copy.
     */
    static void copyFile(File original, File copy) throws IOException {
        copyInputStreamToFile(new FileInputStream(original), copy);
    }

    /**
     * Copies the content of the original input stream to the copy.
     * @param original      The original input stream.
     * @param copy          The copy.
     * @throws IOException  Failed to make the copy.
     */
    static void copyInputStreamToFile(InputStream original, File copy) throws IOException {
        if (original == null) {
            throw new IOException("Could not read input to copy.");
        }
        createFile(copy);
        try (OutputStream outputStream = new FileOutputStream(copy)) {
            int bytesRead;
            byte[] buffer = new byte[4096];
            while ((bytesRead = original.read(buffer)) > 0) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } finally {
            closeSilently(original);
        }
    }

    /**
     * Writes a string to a file.
     * @param string    The string to write
     * @param file      The file to write to
     * @throws IOException  The file did not exist, or could not be created for writing.
     */
    static void writeStringToFile(final String string, final File file) throws IOException {
        createFile(file);
        try (PrintWriter printWriter = new PrintWriter(file)) {
            printWriter.print(string);
        }
    }

    /**
     * Creates a file including parent directories if it does not yet exist.
     * @param file          The file to create
     * @throws IOException  Failed to create the file
     */
    private static void createFile(File file) throws IOException {
        if (!file.exists()) {
            createDirectory(file.getParent());
            if (!file.createNewFile()) {
                throw new IOException("Failed to create " + file.getPath());
            }
        }
    }

    /** FreeMarker template configuration. */
    static Configuration configuration;

    /**
     * Returns a FreeMarker configuration for applying templates.
     * @return A FreeMarker configuration for applying templates.
     */
    static Configuration getConfiguration() {
        if (configuration == null) {
            configuration = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
            configuration.setClassForTemplateLoading(Utils.class, "/templates");
            configuration.setDefaultEncoding("UTF-8");
            configuration.setTemplateExceptionHandler(TemplateExceptionHandler.DEBUG_HANDLER);
        }
        return configuration;
    }

    /**
     * Returns the String result from applying a FreeMarker template.
     * @param template The name of a template file found in {@code resources/templates/}.
     * @param map      The map holding the data to use in the template.
     * @return The String result from applying a FreeMarker template.
     */
    static String applyTemplate(final String template, final Map<String, Object> map) {
        // FreeMarker requires a configuration to find the template.
        configuration = getConfiguration();

        // FreeMarker takes the data and a Writer to process the template.
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                Writer writer = new OutputStreamWriter(outputStream)) {
            Template configurationTemplate = configuration.getTemplate(template);
            configurationTemplate.process(map, writer);
            return outputStream.toString();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    static String getServerClasspath(final String archivePath) {
        return Paths.get(archivePath, "lib", "bootstrap.jar").toString();
    }

    static String getToolkitClasspath(final String archivePath) {
        File folder = Paths.get(archivePath, "lib").toFile();
        final List<String> jarFiles = Arrays.asList(folder.list(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                return name.endsWith(".jar");
            }
        }));

        final List<String> absoluteJarFilePaths = new ArrayList<>();
        for (final String fileName : jarFiles) {
            try {
                absoluteJarFilePaths.add(new File(folder, fileName).getCanonicalPath());
            } catch (final IOException ignored) {
                // Should never append.
            }
        }
        return joinAsString(File.pathSeparator, absoluteJarFilePaths);
    }

    private Utils() {
        // Not used.
    }
}
