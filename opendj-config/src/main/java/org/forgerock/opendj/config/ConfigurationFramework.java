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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import static com.forgerock.opendj.ldap.config.ConfigMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.forgerock.opendj.ldap.config.ConfigMessages;

/**
 * This class is responsible for managing the configuration framework including:
 * <ul>
 * <li>loading core components during application initialization
 * <li>loading extensions during and after application initialization
 * <li>changing the property validation strategy based on whether the application is a client or server.
 * </ul>
 * This class defines a class loader which will be used for loading components.
 * For extensions which define their own extended configuration definitions, the class loader
 * will make sure that the configuration definition classes are loaded and initialized.
 * <p>
 * Initially the configuration framework is disabled, and calls to the {@link #getClassLoader()}
 * will return the system default class loader.
 * <p>
 * Applications <b>MUST NOT</b> maintain persistent references to the class loader as it can change at run-time.
 */
public final class ConfigurationFramework {
    /**
     * Private URLClassLoader implementation. This is only required so that we can provide access to the addURL method.
     */
    private static final class MyURLClassLoader extends URLClassLoader {

        /** Create a class loader with the default parent class loader. */
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

    /** Relative path must be used to retrieve the manifest as a jar entry from the jars. */
    private static final String MANIFEST_RELATIVE_PATH =
        "META-INF/services/org.forgerock.opendj.config.AbstractManagedObjectDefinition";

    /** Absolute path must be used to retrieve the manifest as a resource stream. */
    private static final String MANIFEST_ABSOLUTE_PATH = "/" + MANIFEST_RELATIVE_PATH;

    private static final LocalizedLogger adminLogger = LocalizedLogger
            .getLocalizedLogger(ConfigMessages.resourceName());
    private static final Logger debugLogger = LoggerFactory.getLogger(ConfigurationFramework.class);

    /** The name of the lib directory. */
    private static final String LIB_DIR = "lib";

    /** The name of the extensions directory. */
    private static final String EXTENSIONS_DIR = "extensions";

    /** The singleton instance. */
    private static final ConfigurationFramework INSTANCE = new ConfigurationFramework();

    /** Attribute name in jar's MANIFEST corresponding to the revision number. */
    private static final String REVISION_NUMBER = "Revision-Number";

    /** The attribute names for build information is name, version and revision number. */
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

    /**
     * Returns a string representing all information about extensions.
     *
     * @param installPath
     *            The path where application binaries are located.
     * @param instancePath
     *            The path where application data are located.
     *
     * @return A string representing all information about extensions;
     *         <code>null</code> if there is no information available.
     */
    public static String getPrintableExtensionInformation(final String installPath, final String instancePath) {
        final File extensionsPath = buildExtensionDir(installPath);

        final List<File> extensions = new ArrayList<>();

        if (extensionsPath.exists() && extensionsPath.isDirectory()) {
            extensions.addAll(listFiles(extensionsPath));
        }

        File instanceExtensionsPath = buildExtensionDir(instancePath);
        if (!extensionsPath.getAbsolutePath().equals(instanceExtensionsPath.getAbsolutePath())) {
            extensions.addAll(listFiles(instanceExtensionsPath));
        }

        if (extensions.isEmpty()) {
            return null;
        }

        final StringBuilder sb = new StringBuilder();
        printExtensionDetailsHeader(sb);

        for (final File extension : extensions) {
            printExtensionDetails(sb, extension);
        }

        return sb.toString();
    }

    private static void printExtensionDetailsHeader(final StringBuilder sb) {
        // Leave space at start of the line for "Extension:"
        sb.append("--")
            .append(EOL)
            .append("           Name                 Build number         Revision number")
            .append(EOL);
    }

    private static File buildExtensionDir(String directory)  {
        final File libDir = new File(directory, LIB_DIR);
        final File extensionDir = new File(libDir, EXTENSIONS_DIR);
        try {
            return extensionDir.getCanonicalFile();
        } catch (Exception e) {
            return extensionDir;
        }
    }

    private static List<File> listFiles(File path) {
        if (path.exists() && path.isDirectory()) {
            return Arrays.asList(path.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    // only files with names ending with ".jar"
                    return pathname.isFile() && pathname.getName().endsWith(".jar");
                }
            }));
        }
        return Collections.emptyList();
    }

    private static void printExtensionDetails(final StringBuilder sb, final File extension) {
        // retrieve MANIFEST entry and display name, build number and revision number
        try (JarFile jarFile = new JarFile(extension)) {
            JarEntry entry = jarFile.getJarEntry(MANIFEST_RELATIVE_PATH);
            if (entry == null) {
                return;
            }

            String[] information = getBuildInformation(jarFile);
            sb.append("Extension:");
            for (final String name : information) {
                sb.append(" ").append(String.format("%-20s", name));
            }
            sb.append(EOL);
        } catch (final IOException ignored) {
            // ignore extra information for this extension
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
     * @return a String array containing the name, the build number and the revision number
     *            of the extension given in argument
     * @throws java.io.IOException
     *             thrown if the jar file has been closed.
     */
    private static String[] getBuildInformation(final JarFile extension) throws IOException {
        final String[] result = new String[3];

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

    /** Set of registered Jar files. */
    private Set<File> jarFiles = new HashSet<>();

    /**
     * Underlying class loader used to load classes and resources (null if disabled).
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
     * Returns the class loader which should be used for loading classes and resources. When this configuration
     * framework is disabled, the system default class loader will be returned by default.
     * <p>
     * Applications <b>MUST NOT</b> maintain persistent references to the class loader as it can change at run-time.
     *
     * @return Returns the class loader which should be used for loading classes and resources.
     */
    public synchronized ClassLoader getClassLoader() {
        if (loader != null) {
            return loader;
        } else {
            return ClassLoader.getSystemClassLoader();
        }
    }

    /**
     * Initializes the configuration framework using the application's class loader as the parent class loader,
     * and the current working directory as the install and instance path.
     *
     * @return The configuration framework.
     * @throws ConfigException
     *             If the configuration framework could not initialize successfully.
     * @throws IllegalStateException
     *             If the configuration framework has already been initialized.
     */
    public ConfigurationFramework initialize() throws ConfigException {
        return initialize(null);
    }

    /**
     * Initializes the configuration framework using the application's class loader
     * as the parent class loader, and the provided install/instance path.
     *
     * @param installAndInstancePath
     *            The path where application binaries and data are located.
     * @return The configuration framework.
     * @throws ConfigException
     *             If the configuration framework could not initialize successfully.
     * @throws IllegalStateException
     *             If the configuration framework has already been initialized.
     */
    public ConfigurationFramework initialize(final String installAndInstancePath)
            throws ConfigException {
        return initialize(installAndInstancePath, installAndInstancePath);
    }

    /**
     * Initializes the configuration framework using the application's class loader
     * as the parent class loader, and the provided install and instance paths.
     *
     * @param installPath
     *            The path where application binaries are located.
     * @param instancePath
     *            The path where application data are located.
     * @return The configuration framework.
     * @throws ConfigException
     *             If the configuration framework could not initialize successfully.
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
     *             If the configuration framework could not initialize successfully.
     * @throws IllegalStateException
     *             If the configuration framework has already been initialized.
     */
    public synchronized ConfigurationFramework initialize(final String installPath,
            final String instancePath, final ClassLoader parent) throws ConfigException {
        if (loader != null) {
            throw new IllegalStateException("configuration framework already initialized.");
        }
        this.installPath = installPath != null ? installPath : System.getenv("INSTALL_ROOT");
        if (instancePath != null) {
            this.instancePath = instancePath;
        } else {
            String instanceRoot = System.getenv("INSTANCE_ROOT");
            this.instancePath = instanceRoot != null ? instanceRoot : this.installPath;
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
     * @return {@code true} if the configuration framework is being used within a client application.
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
     * Reloads the configuration framework.
     *
     * @throws ConfigException
     *             If the configuration framework could not initialize successfully.
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
     * Specifies whether the configuration framework is being used within
     * a client application. Client applications will perform less property
     * value validation than server applications because they do not have
     * resources available such as the server schema.
     *
     * @param isClient
     *            {@code true} if the configuration framework is being used within a client application.
     * @return The configuration framework.
     */
    public ConfigurationFramework setIsClient(final boolean isClient) {
        this.isClient = isClient;
        return this;
    }

    private void addExtension(final List<File> extensions) throws ConfigException {
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

    private void initialize0() throws ConfigException {
        if (parent != null) {
            loader = new MyURLClassLoader(parent);
        } else {
            loader = new MyURLClassLoader();
        }

        // Forcefully load all configuration definition classes in OpenDS.jar.
        initializeCoreComponents();

        // Put extensions jars into the class loader and load all
        // configuration definition classes in that they contain.
        // First load the extension from the install directory, then
        // from the instance directory.
        File installExtensionsPath  = buildExtensionDir(installPath);
        File instanceExtensionsPath = buildExtensionDir(instancePath);

        initializeAllExtensions(installExtensionsPath);

        if (!installExtensionsPath.getAbsolutePath().equals(instanceExtensionsPath.getAbsolutePath())) {
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
                // The extensions directory does not exist. This is not a critical problem.
                adminLogger.warn(WARN_ADMIN_NO_EXTENSIONS_DIR, extensionsPath);
                return;
            }

            if (!extensionsPath.isDirectory()) {
                // The extensions directory is not a directory. This is more critical.
                throw new ConfigException(ERR_ADMIN_EXTENSIONS_DIR_NOT_DIRECTORY.get(extensionsPath));
            }

            // Add and initialize the extensions.
            addExtension(listFiles(extensionsPath));
        } catch (final ConfigException e) {
            debugLogger.trace("Unable to initialize all extensions", e);
            throw e;
        } catch (final Exception e) {
            debugLogger.trace("Unable to initialize all extensions", e);
            final LocalizableMessage message = ERR_ADMIN_EXTENSIONS_CANNOT_LIST_FILES.get(
                extensionsPath, stackTraceToSingleLineString(e, true));
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
        final InputStream is = RootCfgDefn.class.getResourceAsStream(MANIFEST_ABSOLUTE_PATH);
        if (is == null) {
            throw new ConfigException(ERR_ADMIN_CANNOT_FIND_CORE_MANIFEST.get(MANIFEST_ABSOLUTE_PATH));
        }
        try {
            loadDefinitionClasses(is);
        } catch (final ConfigException e) {
            debugLogger.trace("Unable to initialize core components", e);
            throw new ConfigException(ERR_CLASS_LOADER_CANNOT_LOAD_CORE.get(
                MANIFEST_ABSOLUTE_PATH, stackTraceToSingleLineString(e, true)));
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
        final JarEntry entry = jarFile.getJarEntry(MANIFEST_RELATIVE_PATH);
        if (entry != null) {
            InputStream is;
            try {
                is = jarFile.getInputStream(entry);
            } catch (final Exception e) {
                debugLogger.trace("Unable to get input stream from jar", e);
                final LocalizableMessage message =
                        ERR_ADMIN_CANNOT_READ_EXTENSION_MANIFEST.get(MANIFEST_RELATIVE_PATH, jarFile.getName(),
                                stackTraceToSingleLineString(e, true));
                throw new ConfigException(message);
            }

            try {
                loadDefinitionClasses(is);
            } catch (final ConfigException e) {
                debugLogger.trace("Unable to load classes from input stream", e);
                final LocalizableMessage message = ERR_CLASS_LOADER_CANNOT_LOAD_EXTENSION.get(
                    jarFile.getName(), MANIFEST_RELATIVE_PATH, stackTraceToSingleLineString(e, true));
                throw new ConfigException(message);
            }
            try {
                // Log build information of extensions in the error log
                final String[] information = getBuildInformation(jarFile);
                final LocalizableMessage message = NOTE_LOG_EXTENSION_INFORMATION.get(
                    jarFile.getName(), information[1], information[2]);
                LocalizedLogger.getLocalizedLogger(message.resourceName()).info(message);
            } catch (final Exception e) {
                // Do not log information for that extension
            }
        }
    }

    /**
     * Forcefully load configuration definition classes named in a manifest file.
     *
     * @param is
     *            The manifest file input stream.
     * @throws ConfigException
     *             If the definition classes could not be loaded and initialized.
     */
    private void loadDefinitionClasses(final InputStream is) throws ConfigException {
        // Cannot use ServiceLoader because constructors are private
        final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        final List<AbstractManagedObjectDefinition<?, ?>> definitions = new LinkedList<>();
        while (true) {
            String className;
            try {
                className = reader.readLine();
            } catch (final IOException e) {
                final LocalizableMessage msg =
                        ERR_CLASS_LOADER_CANNOT_READ_MANIFEST_FILE.get(e.getMessage());
                throw new ConfigException(msg, e);
            }

            // Break out when the end of the manifest is reached.
            if (className == null) {
                break;
            }

            className = className.trim();
            // Skip blank lines or lines beginning with #.
            if (className.isEmpty() || className.startsWith("#")) {
                continue;
            }

            debugLogger.trace("Loading class " + className);

            // Load the class and get an instance of it if it is a definition.
            Class<?> theClass;
            try {
                theClass = Class.forName(className, true, loader);
            } catch (final Exception e) {
                final LocalizableMessage msg =
                        ERR_CLASS_LOADER_CANNOT_LOAD_CLASS.get(className, e.getMessage());
                throw new ConfigException(msg, e);
            }
            if (AbstractManagedObjectDefinition.class.isAssignableFrom(theClass)) {
                // We need to instantiate it using its getInstance() static method.
                Method method;
                try {
                    method = theClass.getMethod("getInstance");
                } catch (final Exception e) {
                    final LocalizableMessage msg =
                            ERR_CLASS_LOADER_CANNOT_FIND_GET_INSTANCE_METHOD.get(className, e.getMessage());
                    throw new ConfigException(msg, e);
                }

                // Get the definition instance.
                AbstractManagedObjectDefinition<?, ?> d;
                try {
                    d = (AbstractManagedObjectDefinition<?, ?>) method.invoke(null);
                } catch (final Exception e) {
                    final LocalizableMessage msg =
                            ERR_CLASS_LOADER_CANNOT_INVOKE_GET_INSTANCE_METHOD.get(className, e.getMessage());
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
                final LocalizableMessage msg = ERR_CLASS_LOADER_CANNOT_INITIALIZE_DEFN.get(
                    d.getName(), d.getClass().getName(), e.getMessage());
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
