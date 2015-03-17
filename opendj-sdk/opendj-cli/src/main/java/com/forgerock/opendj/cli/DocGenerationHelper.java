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
 *      Copyright 2015 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;

/**
 * This class provides utility functions to help generate reference documentation.
 */
public final class DocGenerationHelper {

    /** Prevent instantiation. */
    private DocGenerationHelper() {
        // Do nothing.
    }

    /** FreeMarker template configuration. */
    private static Configuration configuration;

    /**
     * Gets a FreeMarker configuration for applying templates.
     *
     * @return              A FreeMarker configuration.
     */
    private static Configuration getConfiguration() {
        if (configuration == null) {
            configuration = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
            configuration.setClassForTemplateLoading(DocGenerationHelper.class, "/templates");
            configuration.setDefaultEncoding("UTF-8");
            configuration.setTemplateExceptionHandler(TemplateExceptionHandler.DEBUG_HANDLER);
        }
        return configuration;
    }

    /**
     * Appends the String result from applying a FreeMarker template.
     *
     * @param builder       Append the result to this.
     * @param template      The name of a template file found in {@code resources/templates/}.
     * @param map           The map holding the data to use in the template.
     */
    public static void applyTemplate(StringBuilder builder, final String template, final Map<String, Object> map) {
        // FixMe: This method is public so it can be used by the SubCommandUsageHandler
        // in org.forgerock.opendj.config.dsconfig.DSConfig.

        // FreeMarker requires a configuration to find the template.
        configuration = getConfiguration();

        // FreeMarker takes the data and a Writer to process the template.
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Writer writer = new OutputStreamWriter(outputStream);
        try {
            Template configurationTemplate = configuration.getTemplate(template);
            configurationTemplate.process(map, writer);
            builder.append(outputStream.toString());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            org.forgerock.util.Utils.closeSilently(writer, outputStream);
        }
    }

    /**
     * Returns an option synopsis.
     *
     * <br>
     *
     * Note: The synopsis might contain characters that must be escaped in XML.
     *
     * @param argument  The argument option.
     * @return          A synopsis.
     */
    static String getOptionSynopsis(final Argument argument) {
        StringBuilder builder = new StringBuilder();

        final Character shortID = argument.getShortIdentifier();
        if (shortID != null) {
            builder.append("-").append(shortID.charValue());
        }
        final String longID = argument.getLongIdentifier();
        if (shortID != null && longID != null) {
            builder.append(" | ");
        }
        if (longID != null) {
            builder.append("--").append(longID);
        }
        if (argument.needsValue()) {
            builder.append(" ").append(argument.getValuePlaceholder());
        }

        return builder.toString();
    }

    /**
     * Returns true when the argument handles properties.
     *
     * @param argument  The argument.
     * @return True if the argument handles properties.
     */
    public static boolean doesHandleProperties(final Argument argument) {
        // FixMe: This method is public so it can be used by the SubCommandUsageHandler
        // in org.forgerock.opendj.config.dsconfig.DSConfig.

        final String id = argument.getLongIdentifier();
        return ("add".equals(id) || "remove".equals(id) || "reset".equals(id) || "set".equals(id));
    }
}
