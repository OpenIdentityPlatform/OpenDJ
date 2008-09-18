/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin;



import static org.opends.messages.AdminMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.opends.messages.Message;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.InitializationException;
import org.opends.server.util.Validator;



/**
 * Manages the class loader which should be used for loading
 * configuration definition classes and associated extensions.
 * <p>
 * For extensions which define their own extended configuration
 * definitions, the class loader will make sure that the configuration
 * definition classes are loaded and initialized.
 * <p>
 * Initially the class loader provider is disabled, and calls to the
 * {@link #getClassLoader()} will return the system default class
 * loader.
 * <p>
 * Applications <b>MUST NOT</b> maintain persistent references to the
 * class loader as it can change at run-time.
 */
public final class ClassLoaderProvider {

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Private URLClassLoader implementation. This is only required so
   * that we can provide access to the addURL method.
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
     *          The parent class loader.
     */
    public MyURLClassLoader(ClassLoader parent) {
      super(new URL[0], parent);
    }



    /**
     * Add a Jar file to this class loader.
     *
     * @param jarFile
     *          The name of the Jar file.
     * @throws MalformedURLException
     *           If a protocol handler for the URL could not be found,
     *           or if some other error occurred while constructing
     *           the URL.
     * @throws SecurityException
     *           If a required system property value cannot be
     *           accessed.
     */
    public void addJarFile(File jarFile) throws SecurityException,
        MalformedURLException {
      addURL(jarFile.toURI().toURL());
    }

  }

  // The name of the manifest file listing the core configuration
  // definition classes.
  private static final String CORE_MANIFEST = "core.manifest";

  // The name of the manifest file listing a extension's configuration
  // definition classes.
  private static final String EXTENSION_MANIFEST = "extension.manifest";

  // The name of the lib directory.
  private static final String LIB_DIR = "lib";

  // The name of the extensions directory.
  private static final String EXTENSIONS_DIR = "extensions";

  // The singleton instance.
  private static final ClassLoaderProvider INSTANCE = new ClassLoaderProvider();



  /**
   * Get the single application wide class loader provider instance.
   *
   * @return Returns the single application wide class loader provider
   *         instance.
   */
  public static ClassLoaderProvider getInstance() {
    return INSTANCE;
  }

  // Set of registered Jar files.
  private Set<File> jarFiles = new HashSet<File>();

  // Underlying class loader used to load classes and resources (null
  // if disabled).
  //
  // We contain a reference to the URLClassLoader rather than
  // sub-class it so that it is possible to replace the loader at
  // run-time. For example, when removing or replacing extension Jar
  // files (the URLClassLoader only supports adding new
  // URLs, not removal).
  private MyURLClassLoader loader = null;



  // Private constructor.
  private ClassLoaderProvider() {
    // No implementation required.
  }



  /**
   * Add the named extensions to this class loader provider.
   *
   * @param extensions
   *          The names of the extensions to be loaded. The names
   *          should not contain any path elements and must be located
   *          within the extensions folder.
   * @throws InitializationException
   *           If one of the extensions could not be loaded and
   *           initialized.
   * @throws IllegalStateException
   *           If this class loader provider is disabled.
   * @throws IllegalArgumentException
   *           If one of the extension names was not a single relative
   *           path name element or was an absolute path.
   */
  public synchronized void addExtension(String... extensions)
      throws InitializationException, IllegalStateException,
      IllegalArgumentException {
    Validator.ensureNotNull(extensions);

    if (loader == null) {
      throw new IllegalStateException(
          "Class loader provider is disabled.");
    }

    File libPath = new File(DirectoryServer.getInstanceRoot(), LIB_DIR);
    File extensionsPath = new File(libPath, EXTENSIONS_DIR);

    ArrayList<File> files = new ArrayList<File>(extensions.length);
    for (String extension : extensions) {
      File file = new File(extensionsPath, extension);

      // For security reasons we need to make sure that the file name
      // passed in did not contain any path elements and names a file
      // in the extensions folder.

      // Can handle potential null parent.
      if (!extensionsPath.equals(file.getParentFile())) {
        throw new IllegalArgumentException("Illegal file name: "
            + extension);
      }

      // The file is valid.
      files.add(file);
    }

    // Add the extensions.
    addExtension(files.toArray(new File[files.size()]));
  }



  /**
   * Disable this class loader provider and removed any registered
   * extensions.
   *
   * @throws IllegalStateException
   *           If this class loader provider is already disabled.
   */
  public synchronized void disable() throws IllegalStateException {
    if (loader == null) {
      throw new IllegalStateException(
          "Class loader provider already disabled.");
    }
    loader = null;
    jarFiles = new HashSet<File>();
  }



  /**
   * Enable this class loader provider using the application's
   * class loader as the parent class loader.
   *
   * @throws InitializationException
   *           If the class loader provider could not initialize
   *           successfully.
   * @throws IllegalStateException
   *           If this class loader provider is already enabled.
   */
  public synchronized void enable() throws InitializationException,
      IllegalStateException {
    enable(RootCfgDefn.class.getClassLoader());
  }



  /**
   * Enable this class loader provider using the provided parent class
   * loader.
   *
   * @param parent
   *          The parent class loader.
   * @throws InitializationException
   *           If the class loader provider could not initialize
   *           successfully.
   * @throws IllegalStateException
   *           If this class loader provider is already enabled.
   */
  public synchronized void enable(ClassLoader parent)
      throws InitializationException, IllegalStateException {
    if (loader != null) {
      throw new IllegalStateException(
          "Class loader provider already enabled.");
    }

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
    initializeAllExtensions();
  }



  /**
   * Gets the class loader which should be used for loading classes
   * and resources. When this class loader provider is disabled, the
   * system default class loader will be returned by default.
   * <p>
   * Applications <b>MUST NOT</b> maintain persistent references to
   * the class loader as it can change at run-time.
   *
   * @return Returns the class loader which should be used for loading
   *         classes and resources.
   */
  public synchronized ClassLoader getClassLoader() {
    if (loader != null) {
      return loader;
    } else {
      return ClassLoader.getSystemClassLoader();
    }
  }



  /**
   * Indicates whether this class loader provider is enabled.
   *
   * @return Returns <code>true</code> if this class loader provider
   *         is enabled.
   */
  public synchronized boolean isEnabled() {
    return loader != null;
  }



  /**
   * Add the named extensions to this class loader.
   *
   * @param extensions
   *          The names of the extensions to be loaded.
   * @throws InitializationException
   *           If one of the extensions could not be loaded and
   *           initialized.
   */
  private synchronized void addExtension(File... extensions)
      throws InitializationException {
    // First add the Jar files to the class loader.
    List<JarFile> jars = new LinkedList<JarFile>();
    for (File extension : extensions) {
      if (jarFiles.contains(extension)) {
        // Skip this file as it is already loaded.
        continue;
      }

      // Attempt to load it.
      jars.add(loadJarFile(extension));

      // Register the Jar file with the class loader.
      try {
        loader.addJarFile(extension);
      } catch (Exception e) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_ADMIN_CANNOT_OPEN_JAR_FILE.
            get(extension.getName(), extension.getParent(),
                stackTraceToSingleLineString(e));
        throw new InitializationException(message);
      }
      jarFiles.add(extension);
    }

    // Now forcefully load the configuration definition classes.
    for (JarFile jar : jars) {
      initializeExtension(jar);
    }
  }



  /**
   * Put extensions jars into the class loader and load all
   * configuration definition classes in that they contain.
   *
   * @throws InitializationException
   *           If the extensions folder could not be accessed or if a
   *           extension jar file could not be accessed or if one of
   *           the configuration definition classes could not be
   *           initialized.
   */
  private void initializeAllExtensions()
      throws InitializationException {
    File libPath = new File(DirectoryServer.getInstanceRoot(), LIB_DIR);
    File extensionsPath = new File(libPath, EXTENSIONS_DIR);

    try {
      if (!extensionsPath.exists()) {
        // The extensions directory does not exist. This is not a
        // critical problem.
        Message message = ERR_ADMIN_NO_EXTENSIONS_DIR.get(
                String.valueOf(extensionsPath));
        logError(message);
        return;
      }

      if (!extensionsPath.isDirectory()) {
        // The extensions directory is not a directory. This is more
        // critical.
        Message message =
            ERR_ADMIN_EXTENSIONS_DIR_NOT_DIRECTORY.get(
                    String.valueOf(extensionsPath));
        throw new InitializationException(message);
      }

      // Get each extension file name.
      FileFilter filter = new FileFilter() {

        /**
         * Must be a Jar file.
         */
        public boolean accept(File pathname) {
          if (!pathname.isFile()) {
            return false;
          }

          String name = pathname.getName();
          return name.endsWith(".jar");
        }

      };

      // Add and initialize the extensions.
      addExtension(extensionsPath.listFiles(filter));
    } catch (InitializationException e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw e;
    } catch (Exception e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_ADMIN_EXTENSIONS_CANNOT_LIST_FILES.get(
          String.valueOf(extensionsPath), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }



  /**
   * Make sure all core configuration definitions are loaded.
   *
   * @throws InitializationException
   *           If the core manifest file could not be read or if one
   *           of the configuration definition classes could not be
   *           initialized.
   */
  private void initializeCoreComponents()
      throws InitializationException {
    InputStream is = RootCfgDefn.class.getResourceAsStream("/admin/"
        + CORE_MANIFEST);

    if (is == null) {
      Message message = ERR_ADMIN_CANNOT_FIND_CORE_MANIFEST.get(CORE_MANIFEST);
      throw new InitializationException(message);
    }

    try {
      loadDefinitionClasses(is);
    } catch (InitializationException e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CLASS_LOADER_CANNOT_LOAD_CORE.get(CORE_MANIFEST,
          stackTraceToSingleLineString(e));
      throw new InitializationException(message);
    }
  }



  /**
   * Make sure all the configuration definition classes in a extension
   * are loaded.
   *
   * @param jarFile
   *          The extension's Jar file.
   * @throws InitializationException
   *           If the extension jar file could not be accessed or if
   *           one of the configuration definition classes could not
   *           be initialized.
   */
  private void initializeExtension(JarFile jarFile)
      throws InitializationException {
    JarEntry entry = jarFile.getJarEntry("admin/"
        + EXTENSION_MANIFEST);
    if (entry != null) {
      InputStream is;
      try {
        is = jarFile.getInputStream(entry);
      } catch (Exception e) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_ADMIN_CANNOT_READ_EXTENSION_MANIFEST.get(
            EXTENSION_MANIFEST, jarFile.getName(),
            stackTraceToSingleLineString(e));
        throw new InitializationException(message);
      }

      try {
        loadDefinitionClasses(is);
      } catch (InitializationException e) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_CLASS_LOADER_CANNOT_LOAD_EXTENSION.get(jarFile
            .getName(), EXTENSION_MANIFEST, stackTraceToSingleLineString(e));
        throw new InitializationException(message);
      }
    }
  }



  /**
   * Forcefully load configuration definition classes named in a
   * manifest file.
   *
   * @param is
   *          The manifest file input stream.
   * @throws InitializationException
   *           If the definition classes could not be loaded and
   *           initialized.
   */
  private void loadDefinitionClasses(InputStream is)
      throws InitializationException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(
        is));
    List<AbstractManagedObjectDefinition<?, ?>> definitions =
      new LinkedList<AbstractManagedObjectDefinition<?,?>>();
    while (true) {
      String className;
      try {
        className = reader.readLine();
      } catch (IOException e) {
        Message msg = ERR_CLASS_LOADER_CANNOT_READ_MANIFEST_FILE.get(String
            .valueOf(e.getMessage()));
        throw new InitializationException(msg, e);
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

      TRACER.debugMessage(DebugLogLevel.INFO, "Loading class " + className);

      // Load the class and get an instance of it if it is a definition.
      Class<?> theClass;
      try {
        theClass = Class.forName(className, true, loader);
      } catch (Exception e) {
        Message msg = ERR_CLASS_LOADER_CANNOT_LOAD_CLASS.get(className, String
            .valueOf(e.getMessage()));
        throw new InitializationException(msg, e);
      }
      if (AbstractManagedObjectDefinition.class.isAssignableFrom(theClass)) {
        // We need to instantiate it using its getInstance() static method.
        Method method;
        try {
          method = theClass.getMethod("getInstance");
        } catch (Exception e) {
          Message msg = ERR_CLASS_LOADER_CANNOT_FIND_GET_INSTANCE_METHOD.get(
              className, String.valueOf(e.getMessage()));
          throw new InitializationException(msg, e);
        }

        // Get the definition instance.
        AbstractManagedObjectDefinition<?, ?> d;
        try {
          d = (AbstractManagedObjectDefinition<?, ?>) method.invoke(null);
        } catch (Exception e) {
          Message msg = ERR_CLASS_LOADER_CANNOT_INVOKE_GET_INSTANCE_METHOD.get(
              className, String.valueOf(e.getMessage()));
          throw new InitializationException(msg, e);
        }
        definitions.add(d);
      }
    }

    // Initialize any definitions that were loaded.
    for (AbstractManagedObjectDefinition<?, ?> d : definitions) {
      try {
        d.initialize();
      } catch (Exception e) {
        Message msg = ERR_CLASS_LOADER_CANNOT_INITIALIZE_DEFN.get(d.getName(),
            d.getClass().getName(), String.valueOf(e.getMessage()));
        throw new InitializationException(msg, e);
      }
    }
  }



  /**
   * Load the named Jar file.
   *
   * @param jar
   *          The name of the Jar file to load.
   * @return Returns the loaded Jar file.
   * @throws InitializationException
   *           If the Jar file could not be loaded.
   */
  private JarFile loadJarFile(File jar)
      throws InitializationException {
    JarFile jarFile;

    try {
      // Load the extension jar file.
      jarFile = new JarFile(jar);
    } catch (Exception e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_ADMIN_CANNOT_OPEN_JAR_FILE.get(
          jar.getName(), jar.getParent(), stackTraceToSingleLineString(e));
      throw new InitializationException(message);
    }
    return jarFile;
  }

}
