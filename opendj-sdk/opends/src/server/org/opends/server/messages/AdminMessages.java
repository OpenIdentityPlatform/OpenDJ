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

  /**
   * The message ID for the message that will be used as the description for the
   * dsservice tool.  This does not take any arguments.
   */
  public static final int MSGID_ADMIN_TOOL_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 18;

  /**
   * The message ID for the message that will be used as the description for the
   * create-group subcommand part of dsservice tool.
   * This does not take any arguments.
   */
  public static final int MSGID_ADMIN_SUBCMD_CREATE_GROUP_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 19;


  /**
   * The message ID for the message that will be used as the description of the
   * "description" argument.  This does take one argument.
   */
  public static final int MSGID_ADMIN_ARG_DESCRIPTION_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 20;

  /**
   * The message ID for the message that will be used as the description for the
   * modify-group subcommand part of dsservice tool.
   * This does not take any arguments.
   */
  public static final int MSGID_ADMIN_SUBCMD_MODIFY_GROUP_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 21;

  /**
   * The message ID for the message that will be used as the description of the
   * new "description" argument.  This does not take any arguments.
   */
  public static final int MSGID_ADMIN_ARG_NEW_DESCRIPTION_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 22;


  /**
   * The message ID for the message that will be used as the description of the
   * new "groupid" argument.  This does not take any arguments.
   */
  public static final int MSGID_ADMIN_ARG_NEW_GROUPNAME_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 23;

  /**
   * The message ID for the message that will be used as the description for the
   * delete-group subcommand part of dsservice tool.
   * This does not take any arguments.
   */
  public static final int MSGID_ADMIN_SUBCMD_DELETE_GROUP_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 24;

  /**
   * The message ID for the message that will be used as the description for the
   * list-groups subcommand part of dsservice tool.
   * This does not take any arguments.
   */
  public static final int MSGID_ADMIN_SUBCMD_LIST_GROUPS_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 25;

  /**
   * The message ID for the message that will be used as the description for the
   * add-to-group subcommand part of dsservice tool.
   * This does not take any arguments.
   */
  public static final int MSGID_ADMIN_SUBCMD_ADD_TO_GROUP_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 26;

  /**
   * The message ID for the message that will be used as the description of the
   * added "member-id" argument.  This does not take any arguments.
   */
  public static final int MSGID_ADMIN_ARG_ADD_MEMBERNAME_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 27;

  /**
   * The message ID for the message that will be used as the description for the
   * remove-from-group subcommand part of dsservice tool.
   * This does not take any arguments.
   */
  public static final int MSGID_ADMIN_SUBCMD_REMOVE_FROM_GROUP_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 28;

  /**
   * The message ID for the message that will be used as the description of the
   * removed "member-id" argument.  This does not take any arguments.
   */
  public static final int MSGID_ADMIN_ARG_REMOVE_MEMBERNAME_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 29;

  /**
   * The message ID for the message that will be used as the description for the
   * list-members subcommand part of dsservice tool.
   * This does not take any arguments.
   */
  public static final int MSGID_ADMIN_SUBCMD_LIST_MEMBERS_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 30;

  /**
   * The message ID for the message that will be used as the description for the
   * list-members subcommand part of dsservice tool.
   * This does not take any arguments.
   */
  public static final int MSGID_ADMIN_SUBCMD_LIST_MEMBERSHIP_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 31;

  /**
   * The message ID for the message that will be used if the
   * client CLI cannot contact the ADS. This does one take argument.
   */
  public static final int MSGID_ADMIN_CANNOT_CONNECT_TO_ADS =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 32;

  /**
   * The message ID for the message that will be used as the description for the
   * create-ads subcommand part of dsservice tool.
   * This does not take any arguments.
   */
  public static final int MSGID_ADMIN_SUBCMD_CREATE_ADS_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 33;

  /**
   * The message ID for the message that will be used as the description for the
   * delete-ads subcommand part of dsservice tool.
   * This does not take any arguments.
   */
  public static final int MSGID_ADMIN_SUBCMD_DELETE_ADS_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 34;

  /**
   * The message ID for the message that will be used if the hostname of
   * the ADS is missing.
   */
  public static final int MSGID_ADMIN_MISSING_HOSTNAME=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 35;

  /**
   * The message ID for the message that will be used if the hostname of
   * the ADS is not valid.
   */
  public static final int MSGID_ADMIN_NOVALID_HOSTNAME=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 36;

  /**
   * The message ID for the message that will be used if the ipath of
   * the ADS is missing.
   */
  public static final int MSGID_ADMIN_MISSING_IPATH=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 37;

  /**
   * The message ID for the message that will be used if the ipath of
   * the ADS is not valid.
   */
  public static final int MSGID_ADMIN_NOVALID_IPATH=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 38;

  /**
   * The message ID for the message that will be used if we have an
   * access permission error.
   */
  public static final int MSGID_ADMIN_ACCESS_PERMISSION=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 39;

  /**
   * The message ID for the message that will be used if the element is
   * already registered in the ADS.
   */
  public static final int MSGID_ADMIN_ALREADY_REGISTERED=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 40;

  /**
   * The message ID for the message that will be used if the ADS is
   * bot valid.
   */
  public static final int MSGID_ADMIN_BROKEN_INSTALL=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 41;

  /**
   * The message ID for the message that will be used if ADS is not
   * defined in the instance and if we try to perform an admin operation.
   */
  public static final int MSGID_ADMIN_NOT_YET_REGISTERED=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 42;

  /**
   * The message ID for the message that will be used if the port of
   * the ADS is missing.
   */
  public static final int MSGID_ADMIN_MISSING_PORT=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 43;

  /**
   * The message ID for the message that will be used if the port of
   * the ADS is not valid.
   */
  public static final int MSGID_ADMIN_NOVALID_PORT=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 44;

  /**
   * The message ID for the message that will be used if the name of
   * an element is missing.
   */
  public static final int MSGID_ADMIN_MISSING_NAME=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 45;

  /**
   * The message ID for the message that will be used if the admin uid name
   * is missing.
   */
  public static final int MSGID_ADMIN_MISSING_ADMIN_UID=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 46;

  /**
   * The message ID for the message that will be used if the admin password
   * is missing.
   */
  public static final int MSGID_ADMIN_MISSING_ADMIN_PASSWORD=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 47;

  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs.
   */
  public static final int MSGID_ADMIN_ERROR_UNEXPECTED=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_FATAL_ERROR | 48;

  /**
   * The message ID for the message that will be used to indicate that the
   * message is an error message.
   */
  public static final int MSGID_ADMIN_ERROR=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 49;

  /**
   * The message ID for the message that will be used to indicate that the
   * operation is successful.
   */
  public static final int MSGID_ADMIN_SUCCESSFUL=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 50;

  /**
   * The message ID for the message that will be used to indicate that the
   * operation is successful, but nothing was performed.
   */
  public static final int MSGID_ADMIN_SUCCESSFUL_NOP=
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 51;

  /**
   * The message ID which indicate that no message is required.
   */
  public static final int MSGID_ADMIN_NO_MESSAGE =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_SEVERE_ERROR | 52;

  /**
   * The message ID for the message that will be used as the
   * description of the "groupName" argument of the create-group
   * subcommand. This does take one argument.
   */
  public static final int MSGID_ADMIN_ARG_CREATE_GROUP_GROUPNAME_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 53;

  /**
   * The message ID for the message that will be used as the description of the
   * "groupName" argument.  This does take one argument.
   */
  public static final int MSGID_ADMIN_ARG_GROUPNAME_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 54;

  /**
   * The message ID for the message that will be used as the description of the
   * "memberName" argument.  This does take one argument.
   */
  public static final int MSGID_ADMIN_ARG_MEMBERNAME_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 55;

  /**
   * The message ID for the message that will be used as the description of the
   * "backend-name" argument.  This does take one argument.
   */
  public static final int MSGID_ADMIN_ARG_BACKENDNAME_DESCRIPTION =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_INFORMATIONAL | 56;

  /**
   * The message ID for the message that will be used if an add or
   * delete listener cannot be registered because the base entry does
   * not exist and it does not have any ancestor entries. This takes a
   * single argument which is the name of the base entry.
   */
  public static final int MSGID_ADMIN_UNABLE_TO_REGISTER_LISTENER =
    CATEGORY_MASK_ADMIN | SEVERITY_MASK_SEVERE_ERROR | 57;

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
            + "retrieve relation configuration entry %s: %s");

    registerMessage(MSGID_ADMIN_LISTENER_BASE_DOES_NOT_EXIST,
        "The relation entry %s does not appear to exist in the "
            + "Directory Server configuration. This is a required entry");

    registerMessage(MSGID_ADMIN_CANNOT_GET_MANAGED_OBJECT,
        "An error occurred while trying to "
            + "retrieve the managed object configuration entry %s: %s");

    registerMessage(MSGID_ADMIN_MANAGED_OBJECT_DOES_NOT_EXIST,
        "The managed object configuration entry %s does not "
            + "appear to exist in the Directory Server "
            + "configuration. This is a required entry");

    registerMessage(MSGID_ADMIN_MANAGED_OBJECT_DECODING_PROBLEM,
        "An error occurred while trying to "
            + "decode the managed object configuration entry %s: %s");

    registerMessage(MSGID_ADMIN_CANNOT_INSTANTIATE_CLASS,
        "The Directory Server was unable to load class %s and "
            + "use it to create a component instance as "
            + "defined in configuration entry %s.  The error that "
            + "occurred was:  %s.  This component will be " + "disabled");

    registerMessage(MSGID_ADMIN_CANNOT_INITIALIZE_COMPONENT,
        "An error occurred while trying to initialize a " +
        "component instance loaded from class %s with the " +
        "information in configuration entry %s:  %s.  This " +
        "component will be disabled");

    registerMessage(MSGID_ADMIN_COMPONENT_DISABLED,
        "The Directory Server component configured in " +
        "entry %s has been disabled");

    registerMessage(MSGID_ADMIN_CANNOT_OPEN_JAR_FILE,
        "The Directory Server jar file %s in directory %s cannot be " +
        "loaded because an unexpected error occurred while " +
        "trying to open the file for reading:  %s");

    registerMessage(MSGID_ADMIN_NO_EXTENSIONS_DIR,
        "The extensions directory %s does not exist, therefore no " +
        "extensions will be loaded");

    registerMessage(MSGID_ADMIN_EXTENSIONS_DIR_NOT_DIRECTORY,
        "Unable to read the Directory Server extensions " +
        "because the extensions directory %s exists but is not a " +
        "directory");

    registerMessage(MSGID_ADMIN_EXTENSIONS_CANNOT_LIST_FILES,
        "Unable to read the Directory Server extensions " +
        "from directory %s because an unexpected error occurred " +
        "while trying to list the files in that directory:  %s");

    registerMessage(MSGID_ADMIN_CANNOT_LOAD_CLASS_FROM_CORE_MANIFEST,
        "A core configuration definition class could not be loaded " +
        "from the core manifest file %s because an unexpected error " +
        "occurred while trying to initialize it:  %s");

    registerMessage(MSGID_ADMIN_CANNOT_LOAD_CLASS_FROM_EXTENSION_MANIFEST,
        "A configuration definition class could not be loaded " +
        "from the extension manifest file %s in extensions %s because an " +
        "unexpected error occurred while trying to initialize it:  %s");

    registerMessage(MSGID_ADMIN_CANNOT_FIND_CORE_MANIFEST,
        "The core administration manifest file %s cannot be located");

    registerMessage(MSGID_ADMIN_CANNOT_READ_CORE_MANIFEST,
        "The core administration manifest file %s cannot be " +
        "loaded because an unexpected error occurred while " +
        "trying to read it:  %s");

    registerMessage(MSGID_ADMIN_CANNOT_READ_EXTENSION_MANIFEST,
        "The administration manifest file %s associated with the " +
        "extension %s cannot be loaded because an unexpected error " +
        "occurred while trying to read it:  %s");

    registerMessage(MSGID_ADMIN_TOOL_DESCRIPTION,
        "This utility may be used to perform " +
        "operations in the Directory Server administration framework");
    registerMessage(MSGID_ADMIN_SUBCMD_CREATE_GROUP_DESCRIPTION,
        "Create a new server group");
    registerMessage(MSGID_ADMIN_ARG_DESCRIPTION_DESCRIPTION,
        "The server group description. If not specified, " +
        "the description will be empty");
    registerMessage(MSGID_ADMIN_SUBCMD_MODIFY_GROUP_DESCRIPTION,
        "Modify a server group's properties");
    registerMessage(MSGID_ADMIN_ARG_NEW_DESCRIPTION_DESCRIPTION,
        "If specified, the new description");
    registerMessage(MSGID_ADMIN_ARG_NEW_GROUPNAME_DESCRIPTION,
        "If specified, the new server group's identifier");
    registerMessage(MSGID_ADMIN_SUBCMD_DELETE_GROUP_DESCRIPTION,
        "Delete an existing server group" );
    registerMessage(MSGID_ADMIN_SUBCMD_LIST_GROUPS_DESCRIPTION,
        "List server groups that have been defined" );
    registerMessage(MSGID_ADMIN_SUBCMD_ADD_TO_GROUP_DESCRIPTION,
        "Add a server to a server group" );
    registerMessage(MSGID_ADMIN_ARG_ADD_MEMBERNAME_DESCRIPTION,
        "The server to add. This is a required argument" );
    registerMessage(MSGID_ADMIN_SUBCMD_REMOVE_FROM_GROUP_DESCRIPTION,
        "Remove a server from a server group" );
    registerMessage(MSGID_ADMIN_ARG_REMOVE_MEMBERNAME_DESCRIPTION,
        "The server to remove. This is a required argument" );
    registerMessage(MSGID_ADMIN_SUBCMD_LIST_MEMBERS_DESCRIPTION,
        "List servers of the specified server group" );
    registerMessage(MSGID_ADMIN_SUBCMD_LIST_MEMBERSHIP_DESCRIPTION,
        "List server groups in which the specified server is a member" );
    registerMessage(MSGID_ADMIN_CANNOT_CONNECT_TO_ADS,
        "Could not connect to %s. Check that the "+
        "server is running and that the provided credentials are valid");
    registerMessage(MSGID_ADMIN_SUBCMD_CREATE_ADS_DESCRIPTION,
        "Create a new ADS DN");
    registerMessage(MSGID_ADMIN_SUBCMD_DELETE_ADS_DESCRIPTION,
         "Delete an existing ADS DN");
    registerMessage(MSGID_ADMIN_MISSING_HOSTNAME,
        "The host name is missing");
    registerMessage(MSGID_ADMIN_NOVALID_HOSTNAME,
        "The host name is not valid");
    registerMessage(MSGID_ADMIN_MISSING_IPATH,
        "The installation path is missing");
    registerMessage(MSGID_ADMIN_NOVALID_IPATH,
        "The installation path is not valid");
    registerMessage(MSGID_ADMIN_ACCESS_PERMISSION,
        "An access permission error occurs");
    registerMessage(MSGID_ADMIN_ALREADY_REGISTERED,
        "The entity is already registered");
    registerMessage(MSGID_ADMIN_BROKEN_INSTALL,
        "The administrative repository is broken");
    registerMessage(MSGID_ADMIN_NOT_YET_REGISTERED,
        "The entity is not yet registered");
    registerMessage(MSGID_ADMIN_MISSING_PORT,
        "The port is missing");
    registerMessage(MSGID_ADMIN_NOVALID_PORT,
        "The port is not valid");
    registerMessage(MSGID_ADMIN_MISSING_NAME,
        "The name is missing");
    registerMessage(MSGID_ADMIN_MISSING_ADMIN_UID,
        "The administration UID is missing");
    registerMessage(MSGID_ADMIN_MISSING_ADMIN_PASSWORD,
        "The administratior password is missing");
    registerMessage(MSGID_ADMIN_ERROR_UNEXPECTED,
        "An unexpected error occurs");
    registerMessage(MSGID_ADMIN_ERROR,
        "[error] ");
    registerMessage(MSGID_ADMIN_SUCCESSFUL,
        "The operation has been successfully completed");
    registerMessage(MSGID_ADMIN_SUCCESSFUL_NOP,
       "The operation has been successfully completed, "+
       "but no action was required");
    registerMessage(MSGID_ADMIN_NO_MESSAGE,"");
    registerMessage(MSGID_ADMIN_ARG_CREATE_GROUP_GROUPNAME_DESCRIPTION,
       "The new group's identifier. This is a required argument");
    registerMessage(MSGID_ADMIN_ARG_GROUPNAME_DESCRIPTION,
       "The group's identifier. This is a required argument");
    registerMessage(MSGID_ADMIN_ARG_MEMBERNAME_DESCRIPTION,
       "The member's identifier. This is a required argument");
    registerMessage(MSGID_ADMIN_ARG_BACKENDNAME_DESCRIPTION,
        "The name of the backend in which the admin data will be stored. " +
        "This is a required argument");
    registerMessage(MSGID_ADMIN_UNABLE_TO_REGISTER_LISTENER,
        "Unable to register an add/delete listener against the entry \"%s\" " +
        "because it does not exist in the configuration");
  }
}
