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
 *      Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.maven;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

/**
 * Generate configuration classes from XML definition files for OpenDJ server.
 * <p>
 * There is a single goal that generate java sources, manifest files, I18N
 * messages and cli/ldap profiles. Resources will be looked for in the following
 * places depending on whether the plugin is executing for the core config or an
 * extension:
 * <table border="1">
 * <tr>
 * <th></th>
 * <th>Location</th>
 * </tr>
 * <tr>
 * <th align="left">XSLT stylesheets</th>
 * <td>Internal: /config/stylesheets</td>
 * </tr>
 * <tr>
 * <th align="left">XML core definitions</th>
 * <td>Internal: /config/xml</td>
 * </tr>
 * <tr>
 * <th align="left">XML extension definitions</th>
 * <td>${basedir}/src/main/java</td>
 * </tr>
 * <tr>
 * <th align="left">Generated Java APIs</th>
 * <td>${project.build.directory}/generated-sources/config</td>
 * </tr>
 * <tr>
 * <th align="left">Generated I18N messages</th>
 * <td>${project.build.outputDirectory}/config/messages</td>
 * </tr>
 * <tr>
 * <th align="left">Generated profiles</th>
 * <td>${project.build.outputDirectory}/config/profiles/${profile}</td>
 * </tr>
 * <tr>
 * <th align="left">Generated manifest</th>
 * <td>${project.build.outputDirectory}/META-INF/services/org.forgerock.opendj.
 * config.AbstractManagedObjectDefinition</td>
 * </tr>
 * </table>
 *
 * @Checkstyle:ignoreFor 3
 * @goal generate
 * @phase generate-sources
 * @requiresDependencyResolution compile+runtime
 */
public final class GenerateConfigMojo extends AbstractMojo {
    private static interface StreamSourceFactory {
        StreamSource newStreamSource() throws IOException;
    }

    /**
     * The Maven Project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * Package name for which artifacts are generated.
     * <p>
     * This relative path is used to locate xml definition files and to locate
     * generated artifacts.
     *
     * @parameter
     * @required
     */
    private String packageName;

    /**
     * Package name for which artifacts are generated.
     * <p>
     * This relative path is used to locate xml definition files and to locate
     * generated artifacts.
     *
     * @parameter default-value="true"
     * @required
     */
    private Boolean isExtension;

    private final Map<String, StreamSourceFactory> componentDescriptors =
            new LinkedHashMap<String, StreamSourceFactory>();
    private TransformerFactory stylesheetFactory;
    private Templates stylesheetMetaJava;
    private Templates stylesheetServerJava;
    private Templates stylesheetClientJava;
    private Templates stylesheetMetaPackageInfo;
    private Templates stylesheetServerPackageInfo;
    private Templates stylesheetClientPackageInfo;
    private Templates stylesheetProfileLDAP;
    private Templates stylesheetProfileCLI;
    private Templates stylesheetMessages;
    private Templates stylesheetManifest;
    private final List<Future<?>> tasks = new LinkedList<Future<?>>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final URIResolver resolver = new URIResolver() {

        @Override
        public synchronized Source resolve(final String href, final String base)
                throws TransformerException {
            if (href.endsWith(".xsl")) {
                final String stylesheet;
                if (href.startsWith("../")) {
                    stylesheet = "/config/stylesheets/" + href.substring(3);
                } else {
                    stylesheet = "/config/stylesheets/" + href;
                }
                getLog().debug("#### Resolved stylesheet " + href + " to " + stylesheet);
                return new StreamSource(getClass().getResourceAsStream(stylesheet));
            } else if (href.endsWith(".xml")) {
                if (href.startsWith("org/forgerock/opendj/server/config/")) {
                    final String coreXML = "/config/xml/" + href;
                    getLog().debug("#### Resolved core XML definition " + href + " to " + coreXML);
                    return new StreamSource(getClass().getResourceAsStream(coreXML));
                } else {
                    final String extXML = getXMLDirectory() + "/" + href;
                    getLog().debug(
                            "#### Resolved extension XML definition " + href + " to " + extXML);
                    return new StreamSource(new File(extXML));
                }
            } else {
                throw new TransformerException("Unable to resolve URI " + href);
            }
        }
    };

    @Override
    public final void execute() throws MojoExecutionException {
        if (getPackagePath() == null) {
            throw new MojoExecutionException("<packagePath> must be set.");
        } else if (!isXMLPackageDirectoryValid()) {
            throw new MojoExecutionException("The XML definition directory \""
                    + getXMLPackageDirectory() + "\" does not exist.");
        } else if (getClass().getResource(getStylesheetDirectory()) == null) {
            throw new MojoExecutionException("The XSLT stylesheet directory \""
                    + getStylesheetDirectory() + "\" does not exist.");
        }

        // Validate and transform.
        try {
            initializeStylesheets();
            loadXMLDescriptors();
            executeValidateXMLDefinitions();
            executeTransformXMLDefinitions();
            getLog().info(
                    "Adding source directory \"" + getGeneratedSourcesDirectory()
                            + "\" to build path...");
            project.addCompileSourceRoot(getGeneratedSourcesDirectory());
        } catch (final Exception e) {
            throw new MojoExecutionException("XSLT configuration transformation failed", e);
        } finally {
            executor.shutdown();
        }

    }

    private void createTransformTask(final StreamSource input, final StreamResult output,
            final Templates stylesheet, final ExecutorService executor, final String... parameters)
            throws Exception {
        final Transformer transformer = stylesheet.newTransformer();
        transformer.setURIResolver(resolver);
        for (int i = 0; i < parameters.length; i += 2) {
            transformer.setParameter(parameters[i], parameters[i + 1]);
        }
        final Future<Void> future = executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                transformer.transform(input, output);
                return null;
            }
        });
        tasks.add(future);
    }

    private void createTransformTask(final StreamSource input, final String outputFileName,
            final Templates stylesheet, final String... parameters) throws Exception {
        final File outputFile = new File(outputFileName);
        outputFile.getParentFile().mkdirs();
        final StreamResult output = new StreamResult(outputFile);
        createTransformTask(input, output, stylesheet, executor, parameters);
    }

    private void executeTransformXMLDefinitions() throws Exception {
        getLog().info("Transforming XML definitions...");

        /*
         * The manifest is a single file containing the concatenated output of
         * many transformations. Therefore we must ensure that output is
         * serialized by using a single threaded executor.
         */
        final ExecutorService sequentialExecutor = Executors.newSingleThreadExecutor();
        final File manifestFile = new File(getGeneratedManifestFile());
        manifestFile.getParentFile().mkdirs();
        final FileOutputStream manifestFileOutputStream = new FileOutputStream(manifestFile);
        final StreamResult manifest = new StreamResult(manifestFileOutputStream);

        /*
         * Generate Java classes and resources for each XML definition.
         */
        final String javaDir = getGeneratedSourcesDirectory() + "/" + getPackagePath() + "/";
        final String metaDir = javaDir + "meta/";
        final String serverDir = javaDir + "server/";
        final String clientDir = javaDir + "client/";

        final String ldapProfileDir =
                getGeneratedProfilesDirectory("ldap") + "/" + getPackagePath() + "/meta/";
        final String cliProfileDir =
                getGeneratedProfilesDirectory("cli") + "/" + getPackagePath() + "/meta/";
        final String i18nDir = getGeneratedMessagesDirectory() + "/" + getPackagePath() + "/meta/";

        for (final Map.Entry<String, StreamSourceFactory> entry : componentDescriptors.entrySet()) {
            final String meta = metaDir + entry.getKey() + "CfgDefn.java";
            createTransformTask(entry.getValue().newStreamSource(), meta, stylesheetMetaJava);

            final String server = serverDir + entry.getKey() + "Cfg.java";
            createTransformTask(entry.getValue().newStreamSource(), server, stylesheetServerJava);

            final String client = clientDir + entry.getKey() + "CfgClient.java";
            createTransformTask(entry.getValue().newStreamSource(), client, stylesheetClientJava);

            final String ldap = ldapProfileDir + entry.getKey() + "CfgDefn.properties";
            createTransformTask(entry.getValue().newStreamSource(), ldap, stylesheetProfileLDAP);

            final String cli = cliProfileDir + entry.getKey() + "CfgDefn.properties";
            createTransformTask(entry.getValue().newStreamSource(), cli, stylesheetProfileCLI);

            final String i18n = i18nDir + entry.getKey() + "CfgDefn.properties";
            createTransformTask(entry.getValue().newStreamSource(), i18n, stylesheetMessages);

            createTransformTask(entry.getValue().newStreamSource(), manifest, stylesheetManifest,
                    sequentialExecutor);
        }

        // Generate package-info.java files.
        final Map<String, Templates> profileMap = new LinkedHashMap<String, Templates>();
        profileMap.put("meta", stylesheetMetaPackageInfo);
        profileMap.put("server", stylesheetServerPackageInfo);
        profileMap.put("client", stylesheetClientPackageInfo);
        for (final Map.Entry<String, Templates> entry : profileMap.entrySet()) {
            final StreamSource source;
            if (isExtension) {
                source = new StreamSource(new File(getXMLPackageDirectory() + "/Package.xml"));
            } else {
                source =
                        new StreamSource(getClass().getResourceAsStream(
                                "/" + getXMLPackageDirectory() + "/Package.xml"));
            }
            final String profile = javaDir + "/" + entry.getKey() + "/package-info.java";
            createTransformTask(source, profile, entry.getValue(), "type", entry.getKey());
        }

        // Wait for all transformations to complete and cleanup.
        for (final Future<?> task : tasks) {
            task.get();
        }
        sequentialExecutor.shutdown();
        manifestFileOutputStream.close();
    }

    private void executeValidateXMLDefinitions() {
        // TODO:
        getLog().info("Validating XML definitions...");
    }

    private String getBaseDir() {
        return project.getBasedir().toString();
    }

    private String getGeneratedManifestFile() {
        return project.getBuild().getOutputDirectory()
                + "/META-INF/services/org.forgerock.opendj.config.AbstractManagedObjectDefinition";
    }

    private String getGeneratedMessagesDirectory() {
        return project.getBuild().getOutputDirectory() + "/config/messages";
    }

    private String getGeneratedProfilesDirectory(final String profileName) {
        return project.getBuild().getOutputDirectory() + "/config/profiles/" + profileName;
    }

    private String getGeneratedSourcesDirectory() {
        return project.getBuild().getDirectory() + "/generated-sources/config";
    }

    private String getPackagePath() {
        return packageName.replace('.', '/');
    }

    private String getStylesheetDirectory() {
        return "/config/stylesheets";
    }

    private String getXMLDirectory() {
        if (isExtension) {
            return getBaseDir() + "/src/main/java";
        } else {
            return "config/xml";
        }
    }

    private String getXMLPackageDirectory() {
        return getXMLDirectory() + "/" + getPackagePath();
    }

    private void initializeStylesheets() throws TransformerConfigurationException {
        getLog().info("Loading XSLT stylesheets...");
        stylesheetFactory = TransformerFactory.newInstance();
        stylesheetFactory.setURIResolver(resolver);
        stylesheetMetaJava = loadStylesheet("metaMO.xsl");
        stylesheetMetaPackageInfo = loadStylesheet("package-info.xsl");
        stylesheetServerJava = loadStylesheet("serverMO.xsl");
        stylesheetServerPackageInfo = loadStylesheet("package-info.xsl");
        stylesheetClientJava = loadStylesheet("clientMO.xsl");
        stylesheetClientPackageInfo = loadStylesheet("package-info.xsl");
        stylesheetProfileLDAP = loadStylesheet("ldapMOProfile.xsl");
        stylesheetProfileCLI = loadStylesheet("cliMOProfile.xsl");
        stylesheetMessages = loadStylesheet("messagesMO.xsl");
        stylesheetManifest = loadStylesheet("manifestMO.xsl");
    }

    private boolean isXMLPackageDirectoryValid() {
        if (isExtension) {
            return new File(getXMLPackageDirectory()).isDirectory();
        } else {
            // Not an extension, so always valid.
            return true;
        }
    }

    private Templates loadStylesheet(final String stylesheet)
            throws TransformerConfigurationException {
        final Source xslt =
                new StreamSource(getClass().getResourceAsStream(
                        getStylesheetDirectory() + "/" + stylesheet));
        return stylesheetFactory.newTemplates(xslt);
    }

    private void loadXMLDescriptors() throws IOException {
        getLog().info("Loading XML descriptors...");
        final String parentPath = getXMLPackageDirectory();
        if (isExtension) {
            final File dir = new File(parentPath);
            dir.listFiles(new FileFilter() {
                @Override
                public boolean accept(final File path) {
                    final String name = path.getName();
                    if (path.isFile() && name.endsWith("Configuration.xml")) {
                        final String key =
                                name.substring(0, name.length() - "Configuration.xml".length());
                        componentDescriptors.put(key, new StreamSourceFactory() {
                            @Override
                            public StreamSource newStreamSource() {
                                return new StreamSource(path);
                            }
                        });
                    }
                    return true; // Don't care about the result.
                }
            });
        } else {
            final URL dir = getClass().getClassLoader().getResource(parentPath);
            final JarURLConnection connection = (JarURLConnection) dir.openConnection();
            final JarFile jar = connection.getJarFile();
            final Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                final String name = entry.getName();
                if (name.startsWith(parentPath) && name.endsWith("Configuration.xml")) {
                    final int startPos = name.lastIndexOf('/') + 1;
                    final int endPos = name.length() - "Configuration.xml".length();
                    final String key = name.substring(startPos, endPos);
                    componentDescriptors.put(key, new StreamSourceFactory() {
                        @Override
                        public StreamSource newStreamSource() throws IOException {
                            return new StreamSource(jar.getInputStream(entry));
                        }
                    });
                }
            }
        }
        getLog().info("Found " + componentDescriptors.size() + " XML descriptors");
    }

}
