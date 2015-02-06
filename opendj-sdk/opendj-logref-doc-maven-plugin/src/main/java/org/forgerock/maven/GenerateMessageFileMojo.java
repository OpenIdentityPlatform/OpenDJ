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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.forgerock.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.forgerock.util.Utils;

/**
 * Generates xml files containing representations of messages found in
 * properties files.
 * <p>
 * There is a single goal that generates xml files.
 * <p>
 */
@Mojo(defaultPhase = LifecyclePhase.PRE_SITE, name = "generate-xml-messages-doc")
public class GenerateMessageFileMojo extends AbstractMojo {

    /**
     * The Maven Project.
     */
    @Parameter(property = "project", readonly = true, required = true)
    private MavenProject project;

    /**
     * The path to the directory containing the message properties files.
     */
    @Parameter(required = true)
    private String messagesDirectory;

    /**
     * The path to the directory where xml reference files should be written.
     * This path must be relative to ${project.build.directory}.
     */
    @Parameter(required = true)
    private String outputDirectory;

    /**
     * A list which contains all file names, the extension is not needed.
     */
    @Parameter(required = true)
    private List<String> messageFileNames;

    /**
     * The path and file name of the log message reference file path which will
     * be copied in the output directory with generated log reference files.
     */
    @Parameter(required = true)
    private String logMessageReferenceFilePath;

    /**
     * If the plugin is supposed to overwrite existing generated xml files.
     */
    @Parameter(required = true, defaultValue = "false")
    private boolean overwrite;

    /** The end-of-line character for this platform. */
    public static final String EOL = System.getProperty("line.separator");

    /**
     * The registry filename is the result of the concatenation of the location
     * of where the source are generated, the package name and the
     * DESCRIPTORS_REG value.
     */
    private static String registryFileName;

    /**
     * One-line descriptions for log reference categories.
     */
    private static final HashMap<String, String> CATEGORY_DESCRIPTIONS = new HashMap<String, String>();

    static {
        CATEGORY_DESCRIPTIONS.put("ACCESS_CONTROL", "Access Control.");
        CATEGORY_DESCRIPTIONS.put("ADMIN", "the administration framework.");
        CATEGORY_DESCRIPTIONS.put("ADMIN_TOOL", "the tool like the offline" + " installer and uninstaller.");
        CATEGORY_DESCRIPTIONS.put("BACKEND", "generic backends.");
        CATEGORY_DESCRIPTIONS.put("CONFIG", "configuration handling.");
        CATEGORY_DESCRIPTIONS.put("CORE", "the core server.");
        CATEGORY_DESCRIPTIONS.put("DSCONFIG", "the dsconfig administration tool.");
        CATEGORY_DESCRIPTIONS.put("EXTENSIONS", "server extensions for example,"
                + " extended operations, SASL mechanisms, password storage"
                + " schemes, password validators, and so on).");
        CATEGORY_DESCRIPTIONS.put("JEB", "the JE backend.");
        CATEGORY_DESCRIPTIONS.put("LOG", "the server loggers.");
        CATEGORY_DESCRIPTIONS.put("PLUGIN", "plugin processing.");
        CATEGORY_DESCRIPTIONS.put("PROTOCOL", "connection and protocol handling" + " (for example, ASN.1 and LDAP).");
        CATEGORY_DESCRIPTIONS.put("QUICKSETUP", "quicksetup tools.");
        CATEGORY_DESCRIPTIONS.put("RUNTIME_INFORMATION", "the runtime" + " information.");
        CATEGORY_DESCRIPTIONS.put("SCHEMA", "the server schema elements.");
        CATEGORY_DESCRIPTIONS.put("SYNC", "replication.");
        CATEGORY_DESCRIPTIONS.put("TASK", "tasks.");
        CATEGORY_DESCRIPTIONS.put("THIRD_PARTY", "third-party (including" + " user-defined) modules.");
        CATEGORY_DESCRIPTIONS.put("TOOLS", "tools.");
        CATEGORY_DESCRIPTIONS.put("USER_DEFINED", "user-defined modules.");
        CATEGORY_DESCRIPTIONS.put("UTIL", "the general server utilities.");
        CATEGORY_DESCRIPTIONS.put("VERSION", "version information.");
    }

    private static final String DESCRIPTORS_REG = "descriptors.reg";

    /** Message giving formatting rules for string keys. */
    public static final String KEY_FORM_MSG = ".\n\nOpenDJ message property keys must be of the form\n\n"
            + "\t\'[CATEGORY]_[SEVERITY]_[DESCRIPTION]_[ORDINAL]\'\n\n";

    private static final String ERROR_SEVERITY_IDENTIFIER_STRING = "ERR_";
    private static final String ERROR_SEVERITY_PRINTABLE = "ERROR";

    /**
     * Represents a log reference entry for an individual message.
     */
    private static class MessageRefEntry implements Comparable<MessageRefEntry> {
        private Integer ordinal;
        private String xmlId;
        private String formatString;

        /**
         * Build log reference entry for an log message.
         */
        public MessageRefEntry(final String msgPropKey, final Integer ordinal, final String formatString) {
            this.formatString = formatString;
            this.ordinal = ordinal;
            xmlId = getXmlId(msgPropKey);
        }

        private String getXmlId(final String messagePropertyKey) {
            // XML IDs must be unique, and must begin with a letter ([A-Za-z])
            // and may be followed by any number of letters, digits ([0-9]),
            // hyphens ("-"), underscores ("_"), colons (":"), and periods
            // (".").

            final String invalidChars = "[^A-Za-z0-9\\-_:\\.]";
            return messagePropertyKey.replaceAll(invalidChars, "-");
        }

        /**
         * Return a DocBook XML &lt;varlistentry&gt; of this log reference
         * entry. This implementation copies the message string verbatim, and
         * does not interpret format specifiers.
         *
         * @return DocBook XML &lt;varlistentry&gt;.
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("  <varlistentry xml:id=\"log-ref-").append(xmlId).append("\">").append(EOL);
            if (ordinal != null) {
                builder.append("   <term>ID: ").append(ordinal).append("</term>").append(EOL);
            }
            builder.append("   <listitem>").append(EOL);
            builder.append("    <para>Severity: ").append(ERROR_SEVERITY_PRINTABLE).append("</para>").append(EOL);
            builder.append("    <para>Message: ").append(formatString).append("</para>").append(EOL);
            builder.append("   </listitem>").append(EOL);
            builder.append("  </varlistentry>").append(EOL);

            return builder.toString();
        }

        /**
         * Calls {@link #toString()}.
         */
        public String toXML() {
            return toString();
        }

        /**
         * Compare message entries by unique identifier.
         *
         * @return See {@link java.lang.Comparable#compareTo(Object)}.
         */
        @Override
        public int compareTo(MessageRefEntry mre) {
            if (this.ordinal != null && mre.ordinal != null) {
                return this.ordinal.compareTo(mre.ordinal);
            }
            return 0;
        }
    }

    /** Represents a log reference list of messages for a category. */
    private static class MessageRefCategory {
        private String category;
        private TreeSet<MessageRefEntry> messages;

        MessageRefCategory(final String category, final TreeSet<MessageRefEntry> messages) {
            this.category = category;
            this.messages = messages;
        }

        /**
         * Return a DocBook XML &lt;variablelist&gt; of this log reference
         * category.
         *
         * @return DocBook XML &lt;variablelist&gt;
         */
        @Override
        public String toString() {
            StringBuilder entries = new StringBuilder();
            for (MessageRefEntry entry : messages) {
                entries.append(entry.toXML());
            }
            return getVariablelistHead() + entries + getVariablelistTail();
        }

        /**
         * Calls {@link #toString()}.
         */
        public String toXML() {
            return toString();
        }

        private String getXMLPreamble() {
            DateFormat df = new SimpleDateFormat("yyyy");
            Date now = new Date();
            String year = df.format(now);

            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + EOL + "<!--" + EOL + "  ! CCPL HEADER START" + EOL
                    + "  !" + EOL + "  ! This work is licensed under the Creative Commons" + EOL
                    + "  ! Attribution-NonCommercial-NoDerivs 3.0 Unported License." + EOL
                    + "  ! To view a copy of this license, visit" + EOL
                    + "  ! http://creativecommons.org/licenses/by-nc-nd/3.0/" + EOL
                    + "  ! or send a letter to Creative Commons, 444 Castro Street," + EOL
                    + "  ! Suite 900, Mountain View, California, 94041, USA." + EOL + "  !" + EOL
                    + "  ! See the License for the specific language governing permissions" + EOL
                    + "  ! and limitations under the License." + EOL + "  !" + EOL
                    + "  ! If applicable, add the following below this CCPL HEADER, with the fields" + EOL
                    + "  ! enclosed by brackets \"[]\" replaced with your own identifying information:" + EOL
                    + "  !      Portions Copyright [yyyy] [name of copyright owner]" + EOL + "  !" + EOL
                    + "  ! CCPL HEADER END" + EOL + "  !" + EOL + "  !      Copyright " + year + " ForgeRock AS"
                    + EOL + "  !" + EOL + "-->" + EOL;
        }

        private String getBaseElementAttrs() {
            return "xmlns='http://docbook.org/ns/docbook'" + " version='5.0' xml:lang='en'"
                    + " xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'"
                    + " xsi:schemaLocation='http://docbook.org/ns/docbook"
                    + " http://docbook.org/xml/5.0/xsd/docbook.xsd'" + " xmlns:xlink='http://www.w3.org/1999/xlink'"
                    + " xmlns:xinclude='http://www.w3.org/2001/XInclude'";
        }

        private String getVariablelistHead() {
            return getXMLPreamble()
                + " <variablelist xml:id=\"log-ref-" + this.category + "\" " + getBaseElementAttrs() + ">" + EOL
                + "  <title>Log Message Category: " + category + "</title>" + EOL;
        }

        private String getVariablelistTail() {
            return " </variablelist>" + EOL;
        }

    }

    private static class MessagePropertyKey implements Comparable<MessagePropertyKey> {
        private String description;
        private Integer ordinal;

        /**
         * Creates a message property key from a string value.
         *
         * @param key
         *            from properties file
         * @return MessagePropertyKey created from string
         */
        public static MessagePropertyKey parseString(String key) {
            int li = key.lastIndexOf("_");
            if (li == -1) {
                throw new IllegalArgumentException("Incorrectly formatted key " + key);
            }

            final String description = key.substring(0, li).toUpperCase();
            Integer ordinal = null;
            try {
                String ordString = key.substring(li + 1);
                ordinal = Integer.parseInt(ordString);
            } catch (Exception nfe) {
                // Ignore exception, the message has no ordinal.
            }
            return new MessagePropertyKey(description, ordinal);
        }

        /**
         * Creates a parameterized instance.
         *
         * @param description
         *            of this key
         * @param ordinal
         *            of this key
         */
        public MessagePropertyKey(String description, Integer ordinal) {
            this.description = description;
            this.ordinal = ordinal;
        }

        /**
         * Gets the ordinal of this key.
         *
         * @return ordinal of this key
         */
        public Integer getOrdinal() {
            return this.ordinal;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            if (ordinal != null) {
                return description + "_" + ordinal;
            }
            return description;
        }

        /** {@inheritDoc} */
        @Override
        public int compareTo(MessagePropertyKey k) {
            if (ordinal == k.ordinal) {
                return description.compareTo(k.description);
            } else {
                return ordinal.compareTo(k.ordinal);
            }
        }
    }

    /**
     * For maven exec plugin execution. Generates for all included message files
     * (sample.properties), a xml log ref file (log-ref-sample.xml)
     *
     * @throws MojoExecutionException
     *          if a problem occurs
     * @throws MojoFailureException
     *          if a problem occurs
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String projectBuildDir = project.getBuild().getDirectory();

        if (!outputDirectory.contains(projectBuildDir)) {
            String errorMsg = String.format("outputDirectory parameter (%s) must be included "
                    + "in ${project.build.directory} (%s)", outputDirectory, projectBuildDir);
            getLog().error(errorMsg);
            throw new MojoExecutionException(errorMsg);
        }

        for (String messageFileName : messageFileNames) {
            File source = new File(messagesDirectory, messageFileName + ".properties");
            File dest = new File(outputDirectory, "log-ref-" + messageFileName.replace("_", "-") + ".xml");
            try {
                generateLogReferenceFile(source, dest, messageFileName.toUpperCase());
            } catch (MojoExecutionException e) {
                getLog().error("Impossible to generate " + dest.getAbsolutePath() + ": " + e.getMessage());
                throw e;
            }
        }
        copyLogMessageReferenceFile();
    }

    private void generateLogReferenceFile(File source, File dest, String globalCategory)
            throws MojoExecutionException {
        PrintWriter destWriter = null;
        try {
            // Decide whether to generate messages based on modification times
            // and print status messages.
            if (!source.exists()) {
                throw new Exception("file " + source.getName() + " does not exist");
            }
            if (!isOverwriteNeeded(source, dest)) {
                return;
            }

            destWriter = new PrintWriter(dest, "UTF-8");
            Properties properties = new Properties();
            properties.load(new FileInputStream(source));
            Map<MessagePropertyKey, String> errorMessages = loadErrorProperties(properties);
            TreeSet<MessageRefEntry> messageRefEntries = new TreeSet<MessageRefEntry>();
            Set<Integer> usedOrdinals = new HashSet<Integer>();

            for (MessagePropertyKey msgKey : errorMessages.keySet()) {
                String formatString = errorMessages.get(msgKey).replaceAll("<", "&lt;");
                Integer ordinal = msgKey.getOrdinal();
                if (ordinal != null && usedOrdinals.contains(ordinal)) {
                    throw new Exception("The ordinal value \'" + ordinal + "\' in key " + msgKey
                            + " has been previously defined in " + source + KEY_FORM_MSG);
                }
                usedOrdinals.add(ordinal);
                messageRefEntries.add(new MessageRefEntry(msgKey.toString(), msgKey.getOrdinal(), formatString));
            }

            destWriter.println(messageRefEntries.isEmpty() ? "<!-- No message for this category -->"
                    : new MessageRefCategory(globalCategory, messageRefEntries).toXML());
            getLog().info(dest.getPath() + " has been successfully generated");
            getLog().debug("Message Generated: " + errorMessages.size());
        } catch (Exception e) {
            // Delete malformed file.
            if (dest.exists()) {
                dest.deleteOnExit();
            }
            throw new MojoExecutionException(e.getMessage());
        } finally {
            Utils.closeSilently(destWriter);
        }
    }

    private Map<MessagePropertyKey, String> loadErrorProperties(Properties properties) throws Exception {
        Map<MessagePropertyKey, String> errorMessage = new TreeMap<MessagePropertyKey, String>();
        for (Object propO : properties.keySet()) {
            String propKey = propO.toString();
            try {
                // Document only ERROR messages.
                if (propKey.startsWith(ERROR_SEVERITY_IDENTIFIER_STRING)) {
                    MessagePropertyKey key = MessagePropertyKey.parseString(propKey);
                    String formatString = properties.getProperty(propKey);
                    errorMessage.put(key, formatString);
                }
            } catch (IllegalArgumentException iae) {
                throw new Exception("invalid property key " + propKey + ": " + iae.getMessage() + KEY_FORM_MSG);
            }
        }

        return errorMessage;
    }

    private boolean isOverwriteNeeded(File source, File dest) {
        boolean needsOverwrite = this.overwrite || source.lastModified() > dest.lastModified();
        if (dest.exists() && needsOverwrite) {
            dest.delete();
            getLog().info("Regenerating " + dest.getName() + " from " + source.getName());
        } else if (dest.exists() && !needsOverwrite) {
            // Fail fast - nothing to do.
            getLog().info(dest.getName() + " is up to date");
            return false;
        } else {
            File javaGenDir = dest.getParentFile();
            if (!javaGenDir.exists()) {
                javaGenDir.mkdirs();
            }
        }

        return true;
    }

    private void copyLogMessageReferenceFile() throws MojoExecutionException {
        File msgReferenceSourceFile = new File(logMessageReferenceFilePath);
        File msgReferenceDestFile = new File(outputDirectory, msgReferenceSourceFile.getName());
        if (!isOverwriteNeeded(msgReferenceSourceFile, msgReferenceDestFile)) {
            return;
        }
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new FileInputStream(msgReferenceSourceFile);
            output = new FileOutputStream(msgReferenceDestFile);
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buf)) > 0) {
                output.write(buf, 0, bytesRead);
            }
            getLog().info("log message reference file has been successfully generated");
        } catch (Exception e) {
            throw new MojoExecutionException("Impossible to copy log reference message file into output directory: "
                    + e.getMessage());
        } finally {
            Utils.closeSilently(input, output);
        }
    }

    /**
     * Sets the file that will be generated containing declarations of messages
     * from <code>source</code>.
     *
     * @param dest
     *            File destination
     * @throws Exception
     *          If a problem occurs
     */
    public void checkDestJava(File dest) throws Exception {
        File descriptorsRegFile = new File(dest.getParentFile(), DESCRIPTORS_REG);
        if (registryFileName != null) {
            // if REGISTRY_FILE_NAME is already set, ensure that we computed the
            // same one
            File prevDescriptorsRegFile = new File(registryFileName);
            if (!prevDescriptorsRegFile.equals(descriptorsRegFile)) {
                throw new Exception("Error processing " + dest
                        + ": all messages must be located in the same package thus "
                        + "name of the source file should be "
                        + new File(prevDescriptorsRegFile.getParent(), dest.getName()));
            }
        } else {
            registryFileName = descriptorsRegFile.getCanonicalPath();
        }
    }
}
