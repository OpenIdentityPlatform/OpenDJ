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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import static com.forgerock.opendj.ldap.AdminMessages.*;
import static com.forgerock.opendj.ldap.ExtensionMessages.NOTE_LOG_EXTENSION_INFORMATION;
import static com.forgerock.opendj.util.StaticUtils.EOL;
import static com.forgerock.opendj.util.StaticUtils.stackTraceToSingleLineString;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.config.meta.RootCfgDefn;
import org.forgerock.util.Reject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.forgerock.opendj.ldap.AdminMessages;

/**
 * This class is responsible for managing the configuration framework including:
 * <ul>
 * <li>loading core components during application initialization
 * <li>loading extensions during and after application initialization
 * <li>changing the property validation strategy based on whether the
 * application is a client or server.
 * </ul>
 * This class defines a class loader which will be used for loading components.
 * For extensions which define their own extended configuration definitions, the
 * class loader will make sure that the configuration definition classes are
 * loaded and initialized.
 * <p>
 * Initially the configuration framework is disabled, and calls to the
 * {@link #getClassLoader()} will return the system default class loader.
 * <p>
 * Applications <b>MUST NOT</b> maintain persistent references to the class
 * loader as it can change at run-time.
 */
public final class ConfigurationFramework {
    /**
     * Private URLClassLoader implementation. This is only required so that we
     * can provide access to the addURL method.
     */
    private static final class MyURLClassLoader extends URLClassLoader {

        /**
         * Create a class loader with the default parent class loader.
         */
        public MyURLClassLoader() {
            super(new URL[0]);
        }

        /**
         * Create a class loader with the provided parent class loader.
         *
         * @param parent
         *            The parent class loader.
         */
        public MyURLClassLoader(final ClassLoader parent) {
            super(new URL[0], parent);
        }

        /**
         * Add a Jar file to this class loader.
         *
         * @param jarFile
         *            The name of the Jar file.
         * @throws MalformedURLException
         *             If a protocol handler for the URL could not be found, or
         *             if some other error occurred while constructing the URL.
         * @throws SecurityException
         *             If a required system property value cannot be accessed.
         */
        public void addJarFile(final File jarFile) throws MalformedURLException {
            addURL(jarFile.toURI().toURL());
        }

    }

    private static final String MANIFEST =
            "/META-INF/services/org.forgerock.opendj.config.AbstractManagedObjectDefinition";

    private static final LocalizedLogger adminLogger = LocalizedLogger
            .getLocalizedLogger(AdminMessages.resourceName());
    private static final Logger debugLogger = LoggerFactory.getLogger(ConfigurationFramework.class);

    /** The name of the lib directory. */
    private static final String LIB_DIR = "lib";

    /** The name of the extensions directory. */
    private static final String EXTENSIONS_DIR = "extensions";

    /** The singleton instance. */
    private static final ConfigurationFramework INSTANCE = new ConfigurationFramework();

    /** Attribute name in jar's MANIFEST corresponding to the revision number. */
    private static final String REVISION_NUMBER = "Revision-Number";

    /**
     * The attribute names for build information is name, version and revision
     * number.
     */
    private static final String[] BUILD_INFORMATION_ATTRIBUTE_NAMES = new String[] {
        Attributes.Name.EXTENSION_NAME.toString(),
        Attributes.Name.IMPLEMENTATION_VERSION.toString(), REVISION_NUMBER };

    /**
     * Returns the single application wide configuration framework instance.
     *
     * @return The single application wide configuration framework instance.
     */
    public static ConfigurationFramework getInstance() {
        return INSTANCE;
    }

    /** Set of registered Jar files. */
    private Set<File> jarFiles = new HashSet<>();

    /**
     * Underlying class loader used to load classes and resources (null
     * if disabled).
     * <p>
     * We contain a reference to the URLClassLoader rather than
     * sub-class it so that it is possible to replace the loader at
     * run-time. For example, when removing or replacing extension Jar
     * files (the URLClassLoader only supports adding new URLs, not removal).
     */
    private MyURLClassLoader loader;

    private boolean isClient = true;
    private String installPath;
    private String instancePath;
    private ClassLoader parent;

    /** Private constructor. */
    private ConfigurationFramework() {
        // No implementation required.
    }

    /**
     * Loads the named extensions into the configuration framework.
     *
     * @param extensions
     *            The names of the extensions to be loaded. The names should not
     *            contain any path elements and must be located within the
     *            extensions folder.
     * @throws ConfigException
     *             If one of the extensions could not be loaded and initialized.
     * @throws IllegalStateException
     *             If the configuration framework has not yet been initialized.
     * @throws IllegalArgumentException
     *             If one of the extension names was not a single relative path
     *             name element or was an absolute path.
     */
    public synchronized void addExtension(final String... extensions) throws ConfigException {
        Reject.ifNull(extensions);
        ensureInitialized();

        final File libPath = new File(instancePath, LIB_DIR);
        final File extensionsPath = new File(libPath, EXTENSIONS_DIR);

        final ArrayList<File> files = new ArrayList<>(extensions.length);
        for (final String extension : extensions) {
            final File file = new File(extensionsPath, extension);

            // For security reasons we need to make sure that the file name
            // passed in did not contain any path elements and names a file
            // in the extensions folder.

            // Can handle potential null parent.
            if (!extensionsPath.equals(file.getParentFile())) {
                throw new IllegalArgumentException("Illegal file name: " + extension);
            }

            // The file is valid.
            files.add(file);
        }

        // Add the extensions.
        addExtension(files.toArray(new File[files.size()]));
    }

    /**
     * Returns the class loader which should be used for loading classes and
     * resources. When this configuration framework is disabled, the system
     * default class loader will be returned by default.
     * <p>
     * Applications <b>MUST NOT</b> maintain persistent references to the class
     * loader as it can change at run-time.
     *
     * @return Returns the class loader which should be used for loading classes
     *         and resources.
     */
    public synchronized ClassLoader getClassLoader() {
        if (loader != null) {
            return loader;
        } else {
            return ClassLoader.getSystemClassLoader();
        }
    }

    /**
     * Initializes the configuration framework using the application's class
     * loader as the parent class loader, and the current working directory as
     * the install and instance path.
     *
     * @return The configuration framework.
     * @throws ConfigException
     *             If the configuration framework could not initialize
     *             successfully.
     * @throws IllegalStateException
     *             If the configuration framework has already been initialized.
     */
    public ConfigurationFramework initialize() throws ConfigException {
        return initialize(null);
    }

    /**
     * Initializes the configuration framework using the application's class
     * loader as the parent class loader, and the provided install/instance
     * path.
     *
     * @param installAndInstancePath
     *            The path where application binaries and data are located.
     * @return The configuration framework.
     * @throws ConfigException
     *             If the configuration framework could not initialize
     *             successfully.
     * @throws IllegalStateException
     *             If the configuration framework has already been initialized.
     */
    public ConfigurationFramework initialize(final String installAndInstancePath)
            throws ConfigException {
        return initialize(installAndInstancePath, installAndInstancePath);
    }

    /**
     * Initializes the configuration framework using the application's class
     * loader as the parent class loader, and the provided install and instance
     * paths.
     *
     * @param installPath
     *            The path where application binaries are located.
     * @param instancePath
     *            The path where application data are located.
     * @return The configuration framework.
     * @throws ConfigException
     *             If the configuration framework could not initialize
     *             successfully.
     * @throws IllegalStateException
     *             If the configuration framework has already been initialized.
     */
    public ConfigurationFramework initialize(final String installPath, final String instancePath)
            throws ConfigException {
        return initialize(installPath, instancePath, RootCfgDefn.class.getClassLoader());
    }

    /**
     * Initializes the configuration framework using the provided parent class
     * loader and install and instance paths.
     *
     * @param installPath
     *            The path where application binaries are located.
     * @param instancePath
     *            The path where application data are located.
     * @param parent
     *            The parent class loader.
     * @return The configuration framework.
     * @throws ConfigException
     *             If the configuration framework could not initialize
     *             successfully.
     * @throws IllegalStateException
     *             If the configuration framework has already been initialized.
     */
    public synchronized ConfigurationFramework initialize(final String installPath,
            final String instancePath, final ClassLoader parent) throws ConfigException {
        if (loader != null) {
            throw new IllegalStateException("configuration framework already initialized.");
        }
        this.installPath = installPath == null ? System.getenv("INSTALL_ROOT") : installPath;
        if (instancePath != null) {
            this.instancePath = instancePath;
        } else {
            this.instancePath = System.getenv("INSTANCE_ROOT") != null ? System.getenv("INSTANCE_ROOT")
                    : this.installPath;
        }
        this.parent = parent;
        initialize0();
        return this;
    }

    /**
     * Returns {@code true} if the configuration framework is being used within
     * a client application. Client applications will perform less property
     * value validation than server applications because they do not have
     * resources available such as the server schema.
     *
     * @return {@code true} if the configuration framework is being used within
     *         a client application.
     */
    public boolean isClient() {
        return isClient;
    }

    /**
     * Returns {@code true} if the configuration framework has been initialized.
     *
     * @return {@code true} if the configuration framework has been initialized.
     */
    public synchronized boolean isInitialized() {
        return loader != null;
    }

    /**
     * Prints out all information about extensions.
     *
     * @return A string representing all information about extensions;
     *         <code>null</code> if there is no information available.
     */
    public String printExtensionInformation() {
        final File extensionsPath =
                new File(installPath + File.separator + LIB_DIR + File.separator + EXTENSIONS_DIR);

        if (!extensionsPath.exists() || !extensionsPath.isDirectory()) {
            // no extensions' directory
            return null;
        }

        final File[] extensions = extensionsPath.listFiles(new FileFilter() {
            @Override
            public boolean accept(final File pathname) {
                // only files with names ending with ".jar"
                return pathname.isFile() && pathname.getName().endsWith(".jar");
            }
        });

        if (extensions.length == 0) {
            return null;
        }

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(baos);
        // prints:
        // --
        // Name Build number Revision number
        ps.printf("--%s           %-20s %-20s %-20s%s", EOL, "Name", "Build number",
                "Revision number", EOL);

        for (final File extension : extensions) {
            // retrieve MANIFEST entry and display name, build number and
            // revision number
            try {
                final JarFile jarFile = new JarFile(extension);
                final JarEntry entry = jarFile.getJarEntry(MANIFEST);
                if (entry == null) {
                    continue;
                }

                final String[] information = getBuildInformation(jarFile);

                ps.append("Extension: ");
                boolean addBlank = false;
                for (final String name : information) {
                    if (addBlank) {
                        ps.append(addBlank ? " " : ""); // add blank if not
                                                        // first append
                    } else {
                        addBlank = true;
                    }

                    ps.printf("%-20s", name);
                }
                ps.append(EOL);
            } catch (final Exception e) {
                // ignore extra information for this extension
            }
        }

        return baos.toString();
    }

    /**
     * Reloads the configuration framework.
     *
     * @throws ConfigException
     *             If the configuration framework could not initialize
     *             successfully.
     * @throws IllegalStateException
     *             If the configuration framework has not yet been initialized.
     */
    public synchronized void reload() throws ConfigException {
        ensureInitialized();
        loader = null;
        jarFiles = new HashSet<>();
        initialize0();
    }

    /**
     * Specifies whether or not the configuration framework is being used within
     * a client application. Client applications will perform less property
     * value validation than server applications because they do not have
     * resources available such as the server schema.
     *
     * @param isClient
     *            {@code true} if the configuration framework is being used
     *            within a client application.
     * @return The configuration framework.
     */
    public ConfigurationFramework setIsClient(final boolean isClient) {
        this.isClient = isClient;
        return this;
    }

    private void addExtension(final File... extensions) throws ConfigException {
        // First add the Jar files to the class loader.
        final List<JarFile> jars = new LinkedList<>();
        for (final File extension : extensions) {
            if (jarFiles.contains(extension)) {
                // Skip this file as it is already loaded.
                continue;
            }

            // Attempt to load it.
            jars.add(loadJarFile(extension));

            // Register the Jar file with the class loader.
            try {
                loader.addJarFile(extension);
            } catch (final Exception e) {
                debugLogger.trace("Unable to register the jar file with the class loader", e);
                final LocalizableMessage message =
                        ERR_ADMIN_CANNOT_OPEN_JAR_FILE.get(extension.getName(), extension
                                .getParent(), stackTraceToSingleLineString(e, true));
                throw new ConfigException(message);
            }
            jarFiles.add(extension);
        }

        // Now forcefully load the configuration definition classes.
        for (final JarFile jar : jars) {
            initializeExtension(jar);
        }
    }

    private void ensureInitialized() {
        if (loader == null) {
            throw new IllegalStateException("configuration framework is disabled.");
        }
    }

    /**
     * Returns a String array with the following information : <br>
     * index 0: the name of the extension. <br>
     * index 1: the build number of the extension. <br>
     * index 2: the revision number of the extension.
     *
     * @param extension
     *            the jar file of the extension
     * @return a String array containing the name, the build number and the
     *         revision number of the extension given in argument
     * @throws java.io.IOException
     *             thrown if the jar file has been closed.
     */
    private String[] getBuildInformation(final JarFile extension) throws IOException {
        final String[] result = new String[3];

        // retrieve MANIFEST entry and display name, version and revision
        final Manifest manifest = extension.getManifest();

        if (manifest != null) {
            final Attributes attributes = manifest.getMainAttributes();

            int index = 0;
            for (final String name : BUILD_INFORMATION_ATTRIBUTE_NAMES) {
                String value = attributes.getValue(name);
                if (value == null) {
                    value = "<unknown>";
                }
                result[index++] = value;
            }
        }

        return result;
    }

    private void initialize0() throws ConfigException {
        if (parent != null) {
            loader = new MyURLClassLoader(parent);
        } else {
            loader = new MyURLClassLoader();
        }

        // Forcefully load all configuration definition classes in
        // OpenDS.jar.
        initializeCoreComponents();

        // Put extensions jars into the class loader and load all
        // configuration definition classes in that they contain.
        // First load the extension from the install directory, then
        // from the instance directory.
        File libDir;
        File installExtensionsPath;
        File instanceExtensionsPath;

        // load install dir extension
        libDir = new File(installPath, LIB_DIR);
        try {
            installExtensionsPath = new File(libDir, EXTENSIONS_DIR).getCanonicalFile();
        } catch (final Exception e) {
            installExtensionsPath = new File(libDir, EXTENSIONS_DIR);
        }
        initializeAllExtensions(installExtensionsPath);

        // load instance dir extension
        libDir = new File(instancePath, LIB_DIR);
        try {
            instanceExtensionsPath = new File(libDir, EXTENSIONS_DIR).getCanonicalFile();
        } catch (final Exception e) {
            instanceExtensionsPath = new File(libDir, EXTENSIONS_DIR);
        }
        if (!installExtensionsPath.getAbsolutePath().equals(
                instanceExtensionsPath.getAbsolutePath())) {
            initializeAllExtensions(instanceExtensionsPath);
        }
    }

    /**
     * Put extensions jars into the class loader and load all configuration
     * definition classes in that they contain.
     *
     * @param extensionsPath
     *            Indicates where extensions are located.
     * @throws ConfigException
     *             If the extensions folder could not be accessed or if a
     *             extension jar file could not be accessed or if one of the
     *             configuration definition classes could not be initialized.
     */
    private void initializeAllExtensions(final File extensionsPath) throws ConfigException {
        try {
            if (!extensionsPath.exists()) {
                // The extensions directory does not exist. This is not a
                // critical problem.
                adminLogger.error(ERR_ADMIN_NO_EXTENSIONS_DIR, String.valueOf(extensionsPath));
                return;
            }

            if (!extensionsPath.isDirectory()) {
                // The extensions directory is not a directory. This is more
                // critical.
                final LocalizableMessage message =
                        ERR_ADMIN_EXTENSIONS_DIR_NOT_DIRECTORY.get(String.valueOf(extensionsPath));
                throw new ConfigException(message);
            }

            // Get each extension file name.
            final FileFilter filter = new FileFilter() {

                /**
                 * Must be a Jar file.
                 */
                @Override
                public boolean accept(final File pathname) {
                    if (!pathname.isFile()) {
                        return false;
                    }

                    final String name = pathname.getName();
                    return name.endsWith(".jar");
                }

            };

            // Add and initialize the extensions.
            addExtension(extensionsPath.listFiles(filter));
        } catch (final ConfigException e) {
            debugLogger.trace("Unable to initialize all extensions", e);
            throw e;
        } catch (final Exception e) {
            debugLogger.trace("Unable to initialize all extensions", e);
            final LocalizableMessage message =
                    ERR_ADMIN_EXTENSIONS_CANNOT_LIST_FILES.get(String.valueOf(extensionsPath),
                            stackTraceToSingleLineString(e, true));
            throw new ConfigException(message, e);
        }
    }

    /**
     * Make sure all core configuration definitions are loaded.
     *
     * @throws ConfigException
     *             If the core manifest file could not be read or if one of the
     *             configuration definition classes could not be initialized.
     */
    private void initializeCoreComponents() throws ConfigException {
        final InputStream is = RootCfgDefn.class.getResourceAsStream(MANIFEST);
        if (is == null) {
            final LocalizableMessage message = ERR_ADMIN_CANNOT_FIND_CORE_MANIFEST.get(MANIFEST);
            throw new ConfigException(message);
        }
        try {
            loadDefinitionClasses(is);
        } catch (final ConfigException e) {
            debugLogger.trace("Unable to initialize core components", e);
            final LocalizableMessage message =
                    ERR_CLASS_LOADER_CANNOT_LOAD_CORE.get(MANIFEST, stackTraceToSingleLineString(e,
                            true));
            throw new ConfigException(message);
        }
    }

    /**
     * Make sure all the configuration definition classes in a extension are
     * loaded.
     *
     * @param jarFile
     *            The extension's Jar file.
     * @throws ConfigException
     *             If the extension jar file could not be accessed or if one of
     *             the configuration definition classes could not be
     *             initialized.
     */
    private void initializeExtension(final JarFile jarFile) throws ConfigException {
        final JarEntry entry = jarFile.getJarEntry(MANIFEST);
        if (entry != null) {
            InputStream is;
            try {
                is = jarFile.getInputStream(entry);
            } catch (final Exception e) {
                debugLogger.trace("Unable to get input stream from jar", e);
                final LocalizableMessage message =
                        ERR_ADMIN_CANNOT_READ_EXTENSION_MANIFEST.get(MANIFEST, jarFile.getName(),
                                stackTraceToSingleLineString(e, true));
                throw new ConfigException(message);
            }

            try {
                loadDefinitionClasses(is);
            } catch (final ConfigException e) {
                debugLogger.trace("Unable to load classes from input stream", e);
                final LocalizableMessage message =
                        ERR_CLASS_LOADER_CANNOT_LOAD_EXTENSION.get(jarFile.getName(), MANIFEST,
                                stackTraceToSingleLineString(e, true));
                throw new ConfigException(message);
            }
            try {
                // Log build information of extensions in the error log
                final String[] information = getBuildInformation(jarFile);
                final LocalizableMessage message =
                        NOTE_LOG_EXTENSION_INFORMATION.get(jarFile.getName(), information[1],
                                information[2]);
                LocalizedLogger.getLocalizedLogger(message.resourceName()).error(message);
            } catch (final Exception e) {
                // Do not log information for that extension
            }
        }
    }

    /**
     * Forcefully load configuration definition classes named in a manifest
     * file.
     *
     * @param is
     *            The manifest file input stream.
     * @throws ConfigException
     *             If the definition classes could not be loaded and
     *             initialized.
     */
    private void loadDefinitionClasses(final InputStream is) throws ConfigException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        final List<AbstractManagedObjectDefinition<?, ?>> definitions = new LinkedList<>();
        while (true) {
            String className;
            try {
                className = reader.readLine();
            } catch (final IOException e) {
                final LocalizableMessage msg =
                        ERR_CLASS_LOADER_CANNOT_READ_MANIFEST_FILE.get(String.valueOf(e
                                .getMessage()));
                throw new ConfigException(msg, e);
            }

            // Break out when the end of the manifest is reached.
            if (className == null) {
                break;
            }

            // Skip blank lines.
            className = className.trim();
            if (className.length() == 0) {
                continue;
            }

            // Skip lines beginning with #.
            if (className.startsWith("#")) {
                continue;
            }

            debugLogger.trace("Loading class " + className);

            // Load the class and get an instance of it if it is a definition.
            Class<?> theClass;
            try {
                theClass = Class.forName(className, true, loader);
            } catch (final Exception e) {
                final LocalizableMessage msg =
                        ERR_CLASS_LOADER_CANNOT_LOAD_CLASS.get(className, String.valueOf(e
                                .getMessage()));
                throw new ConfigException(msg, e);
            }
            if (AbstractManagedObjectDefinition.class.isAssignableFrom(theClass)) {
                // We need to instantiate it using its getInstance() static
                // method.
                Method method;
                try {
                    method = theClass.getMethod("getInstance");
                } catch (final Exception e) {
                    final LocalizableMessage msg =
                            ERR_CLASS_LOADER_CANNOT_FIND_GET_INSTANCE_METHOD.get(className, String
                                    .valueOf(e.getMessage()));
                    throw new ConfigException(msg, e);
                }

                // Get the definition instance.
                AbstractManagedObjectDefinition<?, ?> d;
                try {
                    d = (AbstractManagedObjectDefinition<?, ?>) method.invoke(null);
                } catch (final Exception e) {
                    final LocalizableMessage msg =
                            ERR_CLASS_LOADER_CANNOT_INVOKE_GET_INSTANCE_METHOD.get(className,
                                    String.valueOf(e.getMessage()));
                    throw new ConfigException(msg, e);
                }
                definitions.add(d);
            }
        }

        // Initialize any definitions that were loaded.
        for (final AbstractManagedObjectDefinition<?, ?> d : definitions) {
            try {
                d.initialize();
            } catch (final Exception e) {
                final LocalizableMessage msg =
                        ERR_CLASS_LOADER_CANNOT_INITIALIZE_DEFN.get(d.getName(), d.getClass()
                                .getName(), String.valueOf(e.getMessage()));
                throw new ConfigException(msg, e);
            }
        }
    }

    private JarFile loadJarFile(final File jar) throws ConfigException {
        try {
            // Load the extension jar file.
            return new JarFile(jar);
        } catch (final Exception e) {
            debugLogger.trace("Unable to load jar file: " + jar, e);

            final LocalizableMessage message =
                    ERR_ADMIN_CANNOT_OPEN_JAR_FILE.get(jar.getName(), jar.getParent(),
                            stackTraceToSingleLineString(e, true));
            throw new ConfigException(message);
        }
    }

    /**
     * Returns the installation path.
     *
     * @return The installation path of this instance.
     */
    public String getInstallPath() {
        return installPath;
    }

    /**
     * Returns the instance path.
     *
     * @return The instance path.
     */
    public String getInstancePath() {
        return instancePath;
    }

}
