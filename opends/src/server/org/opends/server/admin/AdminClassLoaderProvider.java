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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.admin;



import static org.opends.server.loggers.Error.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.debugMessage;
import static org.opends.server.messages.AdminMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.opends.server.admin.std.meta.RootConfigurationDefinition;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;



/**
 * Manages the class loader which should be used for loading
 * configuration definition classes and associated extensions.
 * <p>
 * For extensions which define their own extended configuration
 * definitions, the class loader will make sure that the configuration
 * definition classes are loaded and initialized.
 */
public final class AdminClassLoaderProvider {

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
  private static final AdminClassLoaderProvider INSTANCE =
    new AdminClassLoaderProvider();



  /**
   * Gets the application-wide administration framework class loader.
   *
   * @return Returns the application-wide administration framework
   *         class loader.
   */
  public static AdminClassLoaderProvider getInstance() {
    return INSTANCE;
  }

  // Flag indicating whether one-off initialization has been
  // performed.
  private boolean initialized = false;

  // Set of registered Jar files.
  private Set<File> jarFiles = new HashSet<File>();

  // Underlying class loader used to load classes and resources.
  //
  // We contain a reference to the URLClassLoader rather than
  // sub-class it so that it is possible to replace the loader at
  // run-time. For example, when removing or replacing extension Jar
  // files (the URLClassLoader only supports adding new
  // URLs, not removal).
  private MyURLClassLoader loader = new MyURLClassLoader();



  // Private constructor.
  private AdminClassLoaderProvider() {
    // No additional implementation required.
  }



  /**
   * Add the named extensions to this class loader.
   *
   * @param extensions
   *          The names of the extensions to be loaded.
   * @throws InitializationException
   *           If one of the extensions could not be loaded and
   *           initialized.
   * @throws IllegalStateException
   *           If this class loader provider has not been initialized.
   */
  public synchronized void addExtension(File... extensions)
      throws InitializationException, IllegalStateException {
    if (!initialized) {
      throw new IllegalStateException(
          "The class loader provider is not initialized");
    }

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
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_ADMIN_CANNOT_OPEN_JAR_FILE;
        String message = getMessage(msgID, extension.getName(), extension
            .getParent(), stackTraceToSingleLineString(e));

        throw new InitializationException(msgID, message);
      }
      jarFiles.add(extension);
    }

    // Now forcefully load the configuration definition classes.
    for (JarFile jar : jars) {
      initializeExtension(jar);
    }
  }



  /**
   * Gets the class loader which should be used for loading classes
   * and resources.
   *
   * @return Returns the class loader which should be used for loading
   *         classes and resources.
   * @throws IllegalStateException
   *           If this class loader provider has not been initialized.
   */
  public synchronized ClassLoader getClassLoader()
      throws IllegalStateException {
    if (!initialized) {
      throw new IllegalStateException(
          "The class loader provider is not initialized");
    }

    return loader;
  }



  /**
   * Initialize this class loader provider using the default parent
   * class loader.
   *
   * @throws InitializationException
   *           If the administration class loader could not initialize
   *           successfully.
   * @throws IllegalStateException
   *           If this class loader provider is already initialized.
   */
  public synchronized void initialize()
      throws InitializationException, IllegalStateException {
    initialize(null);
  }



  /**
   * Initialize this class loader provider using the provided parent
   * class loader.
   *
   * @param parent
   *          The parent class loader.
   * @throws InitializationException
   *           If the administration class loader could not initialize
   *           successfully.
   * @throws IllegalStateException
   *           If this class loader provider is already initialized.
   */
  public synchronized void initialize(ClassLoader parent)
      throws InitializationException, IllegalStateException {
    if (initialized) {
      throw new IllegalStateException(
          "The class loader provider is already initialized");
    }

    // Prevent multiple initialization.
    initialized = true;

    // Create the new loader.
    if (parent == null) {
      loader = new MyURLClassLoader();
    } else {
      loader = new MyURLClassLoader(parent);
    }

    // Forcefully load all configuration definition classes in
    // OpenDS.jar.
    initializeCoreComponents();

    // Put extensions jars into the class loader and load all
    // configuration definition classes in that they contain.
    initializeAllExtensions();
  }



  /**
   * Put extensions jars into the class loader and load all configuration
   * definition classes in that they contain.
   *
   * @throws InitializationException
   *           If the extensions folder could not be accessed or if a
   *           extension jar file could not be accessed or if one of the
   *           configuration definition classes could not be
   *           initialized.
   */
  private void initializeAllExtensions() throws InitializationException {
    File libPath = new File(DirectoryServer.getServerRoot(), LIB_DIR);
    File extensionsPath = new File(libPath, EXTENSIONS_DIR);

    try {
      if (!extensionsPath.exists()) {
        // The extensions directory does not exist. This is not a
        // critical problem.
        int msgID = MSGID_ADMIN_NO_EXTENSIONS_DIR;
        String message = getMessage(msgID, extensionsPath);

        logError(ErrorLogCategory.EXTENSIONS,
            ErrorLogSeverity.MILD_ERROR, message, msgID);
        return;
      }

      if (!extensionsPath.isDirectory()) {
        // The extensions directory is not a directory. This is more
        // critical.
        int msgID = MSGID_ADMIN_EXTENSIONS_DIR_NOT_DIRECTORY;
        String message = getMessage(msgID, extensionsPath);

        throw new InitializationException(msgID, message);
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
        debugCaught(DebugLogLevel.ERROR, e);
      }
      throw e;
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_ADMIN_EXTENSIONS_CANNOT_LIST_FILES;
      String message = getMessage(msgID, extensionsPath,
          stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
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
    InputStream is = RootConfigurationDefinition.class
        .getResourceAsStream("/admin/" + CORE_MANIFEST);

    if (is == null) {
      int msgID = MSGID_ADMIN_CANNOT_FIND_CORE_MANIFEST;
      String message = getMessage(msgID, CORE_MANIFEST);
      throw new InitializationException(msgID, message);
    }

    try {
      loadDefinitionClasses(is);
    } catch (IOException e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_ADMIN_CANNOT_READ_CORE_MANIFEST;
      String message = getMessage(msgID, CORE_MANIFEST,
          stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message);
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_ADMIN_CANNOT_LOAD_CLASS_FROM_CORE_MANIFEST;
      String message = getMessage(msgID, CORE_MANIFEST,
          stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message);
    }
  }



  /**
   * Make sure all the configuration definition classes in a extension
   * are loaded.
   *
   * @param jarFile
   *          The extension's Jar file.
   * @throws InitializationException
   *           If the extension jar file could not be accessed or if one
   *           of the configuration definition classes could not be
   *           initialized.
   */
  private void initializeExtension(JarFile jarFile)
      throws InitializationException {
    JarEntry entry = jarFile.getJarEntry("admin/" + EXTENSION_MANIFEST);
    if (entry != null) {
      InputStream is;
      try {
        is = jarFile.getInputStream(entry);
      } catch (Exception e) {
        if (debugEnabled()) {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_ADMIN_CANNOT_READ_EXTENSION_MANIFEST;
        String message = getMessage(msgID, EXTENSION_MANIFEST, jarFile
            .getName(), stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message);
      }

      try {
        loadDefinitionClasses(is);
      } catch (IOException e) {
        if (debugEnabled()) {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_ADMIN_CANNOT_READ_EXTENSION_MANIFEST;
        String message = getMessage(msgID, EXTENSION_MANIFEST, jarFile
            .getName(), stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message);
      } catch (Exception e) {
        if (debugEnabled()) {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_ADMIN_CANNOT_LOAD_CLASS_FROM_EXTENSION_MANIFEST;
        String message = getMessage(msgID, EXTENSION_MANIFEST, jarFile
            .getName(), stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message);
      }
    }
  }



  /**
   * Forcefully load configuration definition classes named in a
   * manifest file.
   *
   * @param is
   *          The manifest file input stream.
   * @throws IOException
   *           If an IO error occurred whilst reading the manifest
   *           file.
   * @throws ClassNotFoundException
   *           If an IO error occurred whilst reading the manifest
   *           file.
   * @throws LinkageError
   *           If the linkage fails.
   * @throws ExceptionInInitializerError
   *           If the initialization provoked by this method fails.
   */
  private void loadDefinitionClasses(InputStream is)
      throws IOException, ClassNotFoundException, LinkageError,
      ExceptionInInitializerError {
    BufferedReader reader = new BufferedReader(new InputStreamReader(
        is));
    while (true) {
      String className = reader.readLine();

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

      debugMessage(DebugLogLevel.INFO, "Loading class " + className);

      // Use the underlying loader.
      Class.forName(className, true, loader);
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
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_ADMIN_CANNOT_OPEN_JAR_FILE;
      String message = getMessage(msgID, jar.getName(), jar
          .getParent(), stackTraceToSingleLineString(e));

      throw new InitializationException(msgID, message);
    }
    return jarFile;
  }

}
