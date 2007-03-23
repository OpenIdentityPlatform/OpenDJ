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
package org.opends.server.messages;



import static org.opends.server.messages.MessageHandler.*;



/**
 * This class defines the set of message IDs and default format strings for
 * messages associated with the server-side administration framework.
 * <p>
 * FIXME: At the moment these error messages are just temporary and will be
 * replaced in time with more specific detailed messages.
 */
public final class AdminMessages {

  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to get the base entry for a configuration listener. This takes two
   * arguments: the DN of the base entry, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_ADMIN_CANNOT_GET_LISTENER_BASE =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_SEVERE_ERROR | 1;

  /**
   * The message ID for the message that will be used if the base entry for a
   * configuration listener does not exist in the server. This takes a single
   * argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_ADMIN_LISTENER_BASE_DOES_NOT_EXIST =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_SEVERE_ERROR | 2;

  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to get the configuration entry associated with a managed object.
   * This takes two arguments: the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_ADMIN_CANNOT_GET_MANAGED_OBJECT =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_SEVERE_ERROR | 3;

  /**
   * The message ID for the message that will be used if the configuration entry
   * associated with a managed object does not exist in the server. This takes a
   * single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_ADMIN_MANAGED_OBJECT_DOES_NOT_EXIST =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_SEVERE_ERROR | 4;

  /**
   * The message ID for the message that will be used if a managed cannot be
   * successfully decoded from its associated configuration entry. This takes
   * two arguments: the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_ADMIN_MANAGED_OBJECT_DECODING_PROBLEM =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_SEVERE_ERROR | 5;

  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to load and instantiate a configuration object. This takes three
   * arguments, which are the name of the class, the DN of the configuration
   * entry, and a string representation of the exception that was caught.
   */
  public static final int MSGID_ADMIN_CANNOT_INSTANTIATE_CLASS =
       CATEGORY_MASK_ADMIN | SEVERITY_MASK_SEVERE_ERROR | 6;

  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize a configuration object. This takes three
   * arguments, which are the name of the class, the DN of the configuration
   * entry, and a string representation of the exception that was caught.
   */
  public static final int MSGID_ADMIN_CANNOT_INITIALIZE_COMPONENT =
       CATEGORY_MASK_ADMIN | SEVERITY_MASK_SEVERE_ERROR | 7;

  /**
   * The message ID for the message that will be used if a component
   * has been explicitly disabled.  This takes a single argument,
   * which is the DN of the configuration entry.
   */
  public static final int MSGID_ADMIN_COMPONENT_DISABLED =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_MILD_WARNING | 8;

  /**
   * The message ID for the message that will be used if an error
   * occurs while trying to open a file for reading. This takes three
   * arguments, which are the name of the jar file, the path to the
   * jar directory, and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_ADMIN_CANNOT_OPEN_JAR_FILE =
       CATEGORY_MASK_ADMIN | SEVERITY_MASK_SEVERE_ERROR | 9;

  /**
   * The message ID for the message that will be used if an error
   * occurs while trying to load a core administration configuration
   * definition class. This takes two arguments, which are the name of
   * the manifest file, and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_ADMIN_CANNOT_LOAD_CLASS_FROM_CORE_MANIFEST =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 10;

  /**
   * The message ID for the message that will be used if an error
   * occurs while trying to load a administration configuration
   * definition class from an extension. This takes three arguments,
   * which are the name of the manifest file, the name of the
   * extension jar file, and a string representation of the exception
   * that was caught.
   */
  public static final int
      MSGID_ADMIN_CANNOT_LOAD_CLASS_FROM_EXTENSION_MANIFEST =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_SEVERE_ERROR | 11;

  /**
   * The message ID for the message that will be used if the
   * extensions directory does not exist. This takes a single
   * argument, which is the path to the extensions directory.
   */
  public static final int MSGID_ADMIN_NO_EXTENSIONS_DIR =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_MILD_ERROR | 12;

  /**
   * The message ID for the message that will be used if the
   * extensions directory is not a directory. This takes a single
   * argument, which is the path to the extensions directory.
   */
  public static final int MSGID_ADMIN_EXTENSIONS_DIR_NOT_DIRECTORY =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_SEVERE_ERROR | 13;

  /**
   * The message ID for the message that will be used if an error
   * occurs while trying to list the files in the extensions
   * directory. This takes two arguments, which are the path to the
   * extensions directory and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_ADMIN_EXTENSIONS_CANNOT_LIST_FILES =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_SEVERE_ERROR | 14;

  /**
   * The message ID for the message that will be used if the core
   * administration configuration definition manifest file cannot be
   * located. This takes a single argument, which is the name of the
   * manifest file.
   */
  public static final int MSGID_ADMIN_CANNOT_FIND_CORE_MANIFEST =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 15;

  /**
   * The message ID for the message that will be used if the core
   * administration configuration definition manifest file cannot be
   * read. This takes two arguments, which are the name of the
   * manifest file and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_ADMIN_CANNOT_READ_CORE_MANIFEST =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 16;

  /**
   * The message ID for the message that will be used if the
   * administration configuration definition manifest file contained
   * in a extension cannot be read. This takes three arguments, which
   * are the name of the manifest file, the name of the extension jar
   * file, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_ADMIN_CANNOT_READ_EXTENSION_MANIFEST =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_SEVERE_ERROR | 17;

  // Prevent instantiation.
  private AdminMessages() {
    // Do nothing.
  }



  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages() {
    registerMessage(MSGID_ADMIN_CANNOT_GET_LISTENER_BASE,
        "An error occurred while trying to "
            + "retrieve relation configuration entry %s: %s.");

    registerMessage(MSGID_ADMIN_LISTENER_BASE_DOES_NOT_EXIST,
        "The relation entry %s does not appear to exist in the "
            + "Directory Server configuration. This is a required entry.");

    registerMessage(MSGID_ADMIN_CANNOT_GET_MANAGED_OBJECT,
        "An error occurred while trying to "
            + "retrieve the managed object configuration entry %s: %s.");

    registerMessage(MSGID_ADMIN_MANAGED_OBJECT_DOES_NOT_EXIST,
        "The the managed object configuration entry %s does not "
            + "appear to exist in the Directory Server "
            + "configuration. This is a required entry.");

    registerMessage(MSGID_ADMIN_MANAGED_OBJECT_DECODING_PROBLEM,
        "An error occurred while trying to "
            + "decode the managed object configuration entry %s: %s.");

    registerMessage(MSGID_ADMIN_CANNOT_INSTANTIATE_CLASS,
        "The Directory Server was unable to load class %s and "
            + "use it to create a component instance as "
            + "defined in configuration entry %s.  The error that "
            + "occurred was:  %s.  This component will be " + "disabled.");

    registerMessage(MSGID_ADMIN_CANNOT_INITIALIZE_COMPONENT,
        "An error occurred while trying to initialize a " +
        "component instance loaded from class %s with the " +
        "information in configuration entry %s:  %s.  This " +
        "component will be disabled.");

    registerMessage(MSGID_ADMIN_COMPONENT_DISABLED,
        "The Directory Server component configured in " +
        "entry %s has been disabled.");

    registerMessage(MSGID_ADMIN_CANNOT_OPEN_JAR_FILE,
        "The Directory Server jar file %s in directory %s cannot be " +
        "loaded because an unexpected error occurred while " +
        "trying to open the file for reading:  %s.");

    registerMessage(MSGID_ADMIN_NO_EXTENSIONS_DIR,
        "The extensions directory %s does not exist, therefore no " +
        "extensions will be loaded.");

    registerMessage(MSGID_ADMIN_EXTENSIONS_DIR_NOT_DIRECTORY,
        "Unable to read the Directory Server extensions " +
        "because the extensions directory %s exists but is not a " +
        "directory.");

    registerMessage(MSGID_ADMIN_EXTENSIONS_CANNOT_LIST_FILES,
        "Unable to read the Directory Server extensions " +
        "from directory %s because an unexpected error occurred " +
        "while trying to list the files in that directory:  %s.");

    registerMessage(MSGID_ADMIN_CANNOT_LOAD_CLASS_FROM_CORE_MANIFEST,
        "A core configuration definition class could not be loaded " +
        "from the core manifest file %s because an unexpected error " +
        "occurred while trying to initialize it:  %s.");

    registerMessage(MSGID_ADMIN_CANNOT_LOAD_CLASS_FROM_EXTENSION_MANIFEST,
        "A configuration definition class could not be loaded " +
        "from the extension manifest file %s in extensions %s because an " +
        "unexpected error occurred while trying to initialize it:  %s.");

    registerMessage(MSGID_ADMIN_CANNOT_FIND_CORE_MANIFEST,
        "The core administration manifest file %s cannot be located.");

    registerMessage(MSGID_ADMIN_CANNOT_READ_CORE_MANIFEST,
        "The core administration manifest file %s cannot be " +
        "loaded because an unexpected error occurred while " +
        "trying to read it:  %s.");

    registerMessage(MSGID_ADMIN_CANNOT_READ_EXTENSION_MANIFEST,
        "The administration manifest file %s associated with the " +
        "extension %s cannot be loaded because an unexpected error " +
        "occurred while trying to read it:  %s.");
  }
}
