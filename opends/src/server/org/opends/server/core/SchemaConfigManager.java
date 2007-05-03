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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.api.ConfigHandler;
import org.opends.server.api.MatchingRule;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.schema.AttributeTypeSyntax;
import org.opends.server.schema.DITContentRuleSyntax;
import org.opends.server.schema.DITStructureRuleSyntax;
import org.opends.server.schema.MatchingRuleUseSyntax;
import org.opends.server.schema.NameFormSyntax;
import org.opends.server.schema.ObjectClassSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DITContentRule;
import org.opends.server.types.DITStructureRule;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.MatchingRuleUse;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.NameForm;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ResultCode;
import org.opends.server.types.Schema;
import org.opends.server.util.LDIFReader;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the interaction with
 * the Directory Server schema.  It will be used to initially load all of the
 * matching rules and attribute syntaxes that have been defined in the
 * configuration, and will then read the actual schema definitions.  At present,
 * only attribute types and objectclasses are supported in the schema config
 * files.  Other components like DIT content rules, DIT structure rules, name
 * forms, and matching rule use definitions will be ignored.
 */
public class SchemaConfigManager
       implements ConfigChangeListener, ConfigAddListener, ConfigDeleteListener
{
  // The schema that has been parsed from the server configuration.
  private Schema schema;

  // The configuration handler for the Directory Server.
  private ConfigHandler configHandler;



  /**
   * Creates a new instance of this schema config manager.
   */
  public SchemaConfigManager()
  {
    configHandler = DirectoryServer.getConfigHandler();

    schema = new Schema();
  }



  /**
   * Retrieves the path to the directory containing the server schema files.
   *
   * @return  The path to the directory containing the server schema files.
   */
  public static String getSchemaDirectoryPath()
  {
    String schemaDirPath = System.getProperty(PROPERTY_SCHEMA_DIRECTORY);
    if ((schemaDirPath == null) || (schemaDirPath.length() == 0))
    {
      schemaDirPath = DirectoryServer.getServerRoot() + File.separator +
                      PATH_SCHEMA_DIR;
    }

    return schemaDirPath;
  }



  /**
   * Retrieves a reference to the schema information that has been read from the
   * server configuration.  Note that this information will not be complete
   * until the <CODE>initializeMatchingRules</CODE>,
   * <CODE>initializeAttributeSyntaxes</CODE>, and
   * <CODE>initializeAttributeTypesAndObjectClasses</CODE> methods have been
   * called.
   *
   * @return  A reference to the schema information that has been read from the
   *          server configuration.
   */
  public Schema getSchema()
  {
    return schema;
  }



  /**
   * Initializes all the matching rules defined in the Directory Server
   * configuration.  This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the matching
   *                           rule initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the matching rules that is not related to
   *                                   the server configuration.
   */
  public void initializeMatchingRules()
         throws ConfigException, InitializationException
  {
    // First, get the matching rule configuration base entry.
    ConfigEntry matchingRuleBaseEntry;
    try
    {
      DN matchingRuleBaseDN = DN.decode(DN_MATCHING_RULE_CONFIG_BASE);
      matchingRuleBaseEntry = configHandler.getConfigEntry(matchingRuleBaseDN);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_GET_MR_BASE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new ConfigException(msgID, message, e);
    }

    if (matchingRuleBaseEntry == null)
    {
      // The matching rule base entry does not exist.  This is not acceptable,
      // so throw an exception.
      int    msgID   = MSGID_CONFIG_SCHEMA_MR_BASE_DOES_NOT_EXIST;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    // Register add and delete listeners with the matching rule base entry.  We
    // don't care about modifications to it.
    matchingRuleBaseEntry.registerAddListener(this);
    matchingRuleBaseEntry.registerDeleteListener(this);


    // See if the matching rule base has any children.  If not, then this is
    // very bad, since we won't know how to make any comparisons.
    if (! matchingRuleBaseEntry.hasChildren())
    {
      int    msgID   = MSGID_CONFIG_SCHEMA_NO_MATCHING_RULES;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    // Iterate through the child entries and process them as matching rule
    // entries.
    for (ConfigEntry childEntry : matchingRuleBaseEntry.getChildren().values())
    {
      DN mrEntryDN = childEntry.getDN();


      // Register as a change listener for this matching rule entry so that we
      // will be notified of any changes that may be made to it.
      childEntry.registerChangeListener(this);


      // Check to see if this entry appears to contain a matching rule
      // configuration.  If not, log a warning and skip it.
      if (! childEntry.hasObjectClass(OC_MATCHING_RULE))
      {
        int    msgID   = MSGID_CONFIG_SCHEMA_ENTRY_DOES_NOT_HAVE_MR_CONFIG;
        String message = getMessage(msgID, String.valueOf(mrEntryDN));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        continue;
      }


      // See if the entry contains an attribute that indicates whether the
      // matching rule should be enabled.  If it does not, or if it is not set
      // to "true", then skip it.
      int msgID = MSGID_CONFIG_SCHEMA_MR_ATTR_DESCRIPTION_ENABLED;
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_MATCHING_RULE_ENABLED,
                                      getMessage(msgID), false);
      try
      {
        BooleanConfigAttribute enabledAttr =
             (BooleanConfigAttribute)
             childEntry.getConfigAttribute(enabledStub);
        if (enabledAttr == null)
        {
          // The attribute is not present, so this matching rule will be
          // disabled.  Log a message and continue.
          msgID = MSGID_CONFIG_SCHEMA_MR_NO_ENABLED_ATTR;
          String message = getMessage(msgID, String.valueOf(mrEntryDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          continue;
        }
        else if (! enabledAttr.activeValue())
        {
          // The matching rule is explicitly disabled.  Log a mild warning and
          // continue.
          msgID = MSGID_CONFIG_SCHEMA_MR_DISABLED;
          String message = getMessage(msgID, String.valueOf(mrEntryDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.INFORMATIONAL, message, msgID);
          continue;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SCHEMA_MR_UNABLE_TO_DETERMINE_ENABLED_STATE;
        String message = getMessage(msgID, String.valueOf(mrEntryDN),
                                    getExceptionMessage(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // See if the entry contains an attribute that specifies the class name
      // for the matching rule implementation.  If it does, then load it and
      // make sure that it's a valid matching rule implementation.  If there is
      // no such attribute, the specified class cannot be loaded, or it does not
      // contain a valid matching rule implementation, then log an error and
      // skip it.
      String className;
      msgID = MSGID_CONFIG_SCHEMA_MR_ATTR_DESCRIPTION_CLASS;
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_MATCHING_RULE_CLASS,
                                     getMessage(msgID), true, false, true);
      try
      {
        StringConfigAttribute classAttr =
             (StringConfigAttribute)
             childEntry.getConfigAttribute(classStub);
        if (classAttr == null)
        {
          msgID = MSGID_CONFIG_SCHEMA_MR_NO_CLASS_ATTR;
          String message = getMessage(msgID, String.valueOf(mrEntryDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR, message, msgID);
          continue;
        }
        else
        {
          className = classAttr.activeValue();
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SCHEMA_MR_CANNOT_GET_CLASS;
        String message = getMessage(msgID, String.valueOf(mrEntryDN),
                                    getExceptionMessage(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }

      MatchingRule matchingRule;
      try
      {
        Class matchingRuleClass = DirectoryServer.loadClass(className);
        matchingRule = (MatchingRule) matchingRuleClass.newInstance();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SCHEMA_MR_CANNOT_INSTANTIATE;
        String message = getMessage(msgID, String.valueOf(className),
                                    String.valueOf(mrEntryDN),
                                    getExceptionMessage(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // Perform the necessary initialization for the matching rule.
      try
      {
        matchingRule.initializeMatchingRule(childEntry);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SCHEMA_MR_CANNOT_INITIALIZE;
        String message = getMessage(msgID, String.valueOf(className),
                                    String.valueOf(mrEntryDN),
                                    getExceptionMessage(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // Register the matching rule with the server schema.
      try
      {
        schema.registerMatchingRule(matchingRule, false);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, de);
        }

        msgID = MSGID_CONFIG_SCHEMA_MR_CONFLICTING_MR;
        String message = getMessage(msgID, String.valueOf(mrEntryDN),
                                    de.getErrorMessage());
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        continue;
      }
    }
  }



  /**
   * Initializes all the attribute syntaxes defined in the Directory Server
   * configuration.  This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the syntax
   *                           initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the syntaxes that is not related to the
   *                                   server configuration.
   */
  public void initializeAttributeSyntaxes()
         throws ConfigException, InitializationException
  {
    // First, get the attribute syntax configuration base entry.
    ConfigEntry syntaxBaseEntry;
    try
    {
      DN syntaxBaseDN = DN.decode(DN_SYNTAX_CONFIG_BASE);
      syntaxBaseEntry = configHandler.getConfigEntry(syntaxBaseDN);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_GET_SYNTAX_BASE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new ConfigException(msgID, message, e);
    }

    if (syntaxBaseEntry == null)
    {
      // The syntax base entry does not exist.  This is not acceptable, so throw
      // an exception.
      int    msgID   = MSGID_CONFIG_SCHEMA_SYNTAX_BASE_DOES_NOT_EXIST;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    // Register add and delete listeners with the syntax base entry.  We don't
    // care about modifications to it.
    syntaxBaseEntry.registerAddListener(this);
    syntaxBaseEntry.registerDeleteListener(this);


    // See if the syntax base has any children.  If not, then this is very
    // bad, since we won't know how to deal with any attribute types.
    if (! syntaxBaseEntry.hasChildren())
    {
      int    msgID   = MSGID_CONFIG_SCHEMA_NO_SYNTAXES;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    // Iterate through the child entries and process them as syntax entries.
    for (ConfigEntry childEntry : syntaxBaseEntry.getChildren().values())
    {
      DN syntaxEntryDN = childEntry.getDN();


      // Register as a change listener for this syntax entry so that we will be
      // notified of any changes that may be made to it.
      childEntry.registerChangeListener(this);


      // Check to see if this entry appears to contain an attribute syntax
      // configuration.  If not, log a warning and skip it.
      if (! childEntry.hasObjectClass(OC_ATTRIBUTE_SYNTAX))
      {
        int    msgID   = MSGID_CONFIG_SCHEMA_ENTRY_DOES_NOT_HAVE_SYNTAX_CONFIG;
        String message = getMessage(msgID, String.valueOf(syntaxEntryDN));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        continue;
      }


      // See if the entry contains an attribute that indicates whether the
      // syntax should be enabled.  If it does not, or if it is not set to
      // "true", then skip it.
      int msgID = MSGID_CONFIG_SCHEMA_SYNTAX_ATTR_DESCRIPTION_ENABLED;
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_SYNTAX_ENABLED, getMessage(msgID),
                                      false);
      try
      {
        BooleanConfigAttribute enabledAttr =
             (BooleanConfigAttribute)
             childEntry.getConfigAttribute(enabledStub);
        if (enabledAttr == null)
        {
          // The attribute is not present, so this syntax will be disabled.  Log
          // a message and continue.
          msgID = MSGID_CONFIG_SCHEMA_SYNTAX_NO_ENABLED_ATTR;
          String message = getMessage(msgID, String.valueOf(syntaxEntryDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          continue;
        }
        else if (! enabledAttr.activeValue())
        {
          // The syntax is explicitly disabled.  Log a mild warning and
          // continue.
          msgID = MSGID_CONFIG_SCHEMA_SYNTAX_DISABLED;
          String message = getMessage(msgID, String.valueOf(syntaxEntryDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.INFORMATIONAL, message, msgID);
          continue;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SCHEMA_SYNTAX_UNABLE_TO_DETERMINE_ENABLED_STATE;
        String message = getMessage(msgID, String.valueOf(syntaxEntryDN),
                                    getExceptionMessage(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // See if the entry contains an attribute that specifies the class name
      // for the syntax implementation.  If it does, then load it and make sure
      // that it's a valid syntax implementation.  If there is no such
      // attribute, the specified class cannot be loaded, or it does not contain
      // a valid syntax implementation, then log an error and skip it.
      String className;
      msgID = MSGID_CONFIG_SCHEMA_SYNTAX_ATTR_DESCRIPTION_CLASS;
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_SYNTAX_CLASS, getMessage(msgID),
                                     true, false, true);
      try
      {
        StringConfigAttribute classAttr =
             (StringConfigAttribute)
             childEntry.getConfigAttribute(classStub);
        if (classAttr == null)
        {
          msgID = MSGID_CONFIG_SCHEMA_SYNTAX_NO_CLASS_ATTR;
          String message = getMessage(msgID, String.valueOf(syntaxEntryDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR, message, msgID);
          continue;
        }
        else
        {
          className = classAttr.activeValue();
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SCHEMA_SYNTAX_CANNOT_GET_CLASS;
        String message = getMessage(msgID, String.valueOf(syntaxEntryDN),
                                    getExceptionMessage(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }

      AttributeSyntax syntax;
      try
      {
        Class syntaxClass = DirectoryServer.loadClass(className);
        syntax = (AttributeSyntax) syntaxClass.newInstance();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SCHEMA_SYNTAX_CANNOT_INSTANTIATE;
        String message = getMessage(msgID, String.valueOf(className),
                                    String.valueOf(syntaxEntryDN),
                                    getExceptionMessage(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // Perform the necessary initialization for the syntax.
      try
      {
        syntax.initializeSyntax(childEntry);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_CONFIG_SCHEMA_SYNTAX_CANNOT_INITIALIZE;
        String message = getMessage(msgID, String.valueOf(className),
                                    String.valueOf(syntaxEntryDN),
                                    getExceptionMessage(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // Register the syntax with the server schema.
      try
      {
        schema.registerSyntax(syntax, false);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, de);
        }

        msgID = MSGID_CONFIG_SCHEMA_SYNTAX_CONFLICTING_SYNTAX;
        String message = getMessage(msgID, String.valueOf(syntaxEntryDN),
                                    de.getErrorMessage());
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        continue;
      }
    }
  }



  /**
   * Initializes all the attribute type, object class, name form, DIT content
   * rule, DIT structure rule, and matching rule use definitions by reading the
   * server schema files.  These files will be located in a single directory and
   * will be processed in lexicographic order.  However, to make the order
   * easier to understand, they may be prefixed with a two digit number (with a
   * leading zero if necessary) so that they will be read in numeric order.
   * This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the schema
   *                           element initialization to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the schema elements that is not related
   *                                   to the server configuration.
   */
  public void initializeSchemaFromFiles()
         throws ConfigException, InitializationException
  {
    // Construct the path to the directory that should contain the schema files
    // and make sure that it exists and is a directory.  Get a list of the files
    // in that directory sorted in alphabetic order.
    String schemaDirPath          = getSchemaDirectoryPath();
    File schemaDir                = new File(schemaDirPath);
    long oldestModificationTime   = -1L;
    long youngestModificationTime = -1L;
    String[] fileNames;

    try
    {
      if (! schemaDir.exists())
      {
        int    msgID   = MSGID_CONFIG_SCHEMA_NO_SCHEMA_DIR;
        String message = getMessage(msgID, schemaDirPath);
        throw new InitializationException(msgID, message);
      }
      else if (! schemaDir.isDirectory())
      {
        int    msgID   = MSGID_CONFIG_SCHEMA_DIR_NOT_DIRECTORY;
        String message = getMessage(msgID, schemaDirPath);
        throw new InitializationException(msgID, message);
      }

      File[] schemaDirFiles = schemaDir.listFiles();
      ArrayList<String> fileList = new ArrayList<String>(schemaDirFiles.length);
      for (File f : schemaDirFiles)
      {
        if (f.isFile())
        {
          fileList.add(f.getName());
        }

        long modificationTime = f.lastModified();
        if ((oldestModificationTime <= 0L) ||
            (modificationTime < oldestModificationTime))
        {
          oldestModificationTime = modificationTime;
        }

        if ((youngestModificationTime <= 0) ||
            (modificationTime > youngestModificationTime))
        {
          youngestModificationTime = modificationTime;
        }
      }

      fileNames = new String[fileList.size()];
      fileList.toArray(fileNames);
      Arrays.sort(fileNames);
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ie);
      }

      throw ie;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_LIST_FILES;
      String message = getMessage(msgID, schemaDirPath,
                                  getExceptionMessage(e));
      throw new InitializationException(msgID, message, e);
    }


    // If the oldest and youngest modification timestamps didn't get set for
    // some reason, then set them to the current time.
    if (oldestModificationTime <= 0)
    {
      oldestModificationTime = System.currentTimeMillis();
    }

    if (youngestModificationTime <= 0)
    {
      youngestModificationTime = oldestModificationTime;
    }

    schema.setOldestModificationTime(oldestModificationTime);
    schema.setYoungestModificationTime(youngestModificationTime);


    // Iterate through the schema files and read them as an LDIF file containing
    // a single entry.  Then get the attributeTypes and objectClasses attributes
    // from that entry and parse them to initialize the server schema.
    for (String schemaFile : fileNames)
    {
      loadSchemaFile(schema, schemaFile, false);
    }
  }



  /**
   * Loads the contents of the specified schema file into the provided schema.
   *
   * @param  schema      The schema in which the contents of the schema file are
   *                     to be loaded.
   * @param  schemaFile  The name of the schema file to be loaded into the
   *                     provided schema.
   *
   * @return  A list of the modifications that could be performed in order to
   *          obtain the contents of the file.
   *
   * @throws  ConfigException  If a configuration problem causes the schema
   *                           element initialization to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the schema elements that is not related
   *                                   to the server configuration.
   */
  public static List<Modification> loadSchemaFile(Schema schema,
                                                  String schemaFile)
         throws ConfigException, InitializationException
  {
    return loadSchemaFile(schema, schemaFile, true);
  }



  /**
   * Loads the contents of the specified schema file into the provided schema.
   *
   * @param  schema       The schema in which the contents of the schema file
   *                      are to be loaded.
   * @param  schemaFile   The name of the schema file to be loaded into the
   *                      provided schema.
   * @param  failOnError  If {@code true}, indicates that this method should
   *                      throw an exception if certain kinds of errors occur.
   *                      If {@code false}, indicates that this method should
   *                      log an error message and return without an exception.
   *                      This should only be {@code false} when called from
   *                      {@code initializeSchemaFromFiles}.
   *
   * @return  A list of the modifications that could be performed in order to
   *          obtain the contents of the file, or {@code null} if a problem
   *          occurred and {@code failOnError} is {@code false}.
   *
   * @throws  ConfigException  If a configuration problem causes the schema
   *                           element initialization to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the schema elements that is not related
   *                                   to the server configuration.
   */
  private static List<Modification> loadSchemaFile(Schema schema,
                                                   String schemaFile,
                                                   boolean failOnError)
         throws ConfigException, InitializationException
  {
    // Create an LDIF reader to use when reading the files.
    String schemaDirPath = getSchemaDirectoryPath();
    LDIFReader reader;
    try
    {
      File f = new File(schemaDirPath, schemaFile);
      reader = new LDIFReader(new LDIFImportConfig(f.getAbsolutePath()));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_OPEN_FILE;
      String message = getMessage(msgID, schemaFile, schemaDirPath,
                                  getExceptionMessage(e));

      if (failOnError)
      {
        throw new ConfigException(msgID, message);
      }
      else
      {
        logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return null;
      }
    }


    // Read the LDIF entry from the file and close the file.
    Entry entry;
    try
    {
      entry = reader.readEntry(false);

      if (entry == null)
      {
        // The file was empty -- skip it.
        return new LinkedList<Modification>();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_READ_LDIF_ENTRY;
      String message = getMessage(msgID, schemaFile, schemaDirPath,
                                  getExceptionMessage(e));

      if (failOnError)
      {
        throw new InitializationException(msgID, message, e);
      }
      else
      {
        logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return null;
      }
    }

    try
    {
      reader.close();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }


    // Get the attributeTypes attribute from the entry.
    LinkedList<Modification> mods = new LinkedList<Modification>();
    AttributeTypeSyntax attrTypeSyntax;
    try
    {
      attrTypeSyntax = (AttributeTypeSyntax)
                       schema.getSyntax(SYNTAX_ATTRIBUTE_TYPE_OID);
      if (attrTypeSyntax == null)
      {
        attrTypeSyntax = new AttributeTypeSyntax();
        attrTypeSyntax.initializeSyntax(null);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      attrTypeSyntax = new AttributeTypeSyntax();
      attrTypeSyntax.initializeSyntax(null);
    }

    AttributeType attributeAttrType =
         schema.getAttributeType(ATTR_ATTRIBUTE_TYPES_LC);
    if (attributeAttrType == null)
    {
      attributeAttrType =
           DirectoryServer.getDefaultAttributeType(ATTR_ATTRIBUTE_TYPES,
                                                   attrTypeSyntax);
    }

    List<Attribute> attrList = entry.getAttribute(attributeAttrType);
    if ((attrList != null) && (! attrList.isEmpty()))
    {
      for (Attribute a : attrList)
      {
        mods.add(new Modification(ModificationType.ADD, a.duplicate()));
      }
    }


    // Get the objectClasses attribute from the entry.
    ObjectClassSyntax ocSyntax;
    try
    {
      ocSyntax = (ObjectClassSyntax) schema.getSyntax(SYNTAX_OBJECTCLASS_OID);
      if (ocSyntax == null)
      {
        ocSyntax = new ObjectClassSyntax();
        ocSyntax.initializeSyntax(null);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      ocSyntax = new ObjectClassSyntax();
      ocSyntax.initializeSyntax(null);
    }

    AttributeType objectclassAttrType =
         schema.getAttributeType(ATTR_OBJECTCLASSES_LC);
    if (objectclassAttrType == null)
    {
      objectclassAttrType =
           DirectoryServer.getDefaultAttributeType(ATTR_OBJECTCLASSES,
                                                   ocSyntax);
    }

    List<Attribute> ocList = entry.getAttribute(objectclassAttrType);
    if ((ocList != null) && (! ocList.isEmpty()))
    {
      for (Attribute a : ocList)
      {
        mods.add(new Modification(ModificationType.ADD, a.duplicate()));
      }
    }


    // Get the name forms attribute from the entry.
    NameFormSyntax nfSyntax;
    try
    {
      nfSyntax = (NameFormSyntax) schema.getSyntax(SYNTAX_NAME_FORM_OID);
      if (nfSyntax == null)
      {
        nfSyntax = new NameFormSyntax();
        nfSyntax.initializeSyntax(null);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      nfSyntax = new NameFormSyntax();
      nfSyntax.initializeSyntax(null);
    }

    AttributeType nameFormAttrType =
         schema.getAttributeType(ATTR_NAME_FORMS_LC);
    if (nameFormAttrType == null)
    {
      nameFormAttrType =
           DirectoryServer.getDefaultAttributeType(ATTR_NAME_FORMS, nfSyntax);
    }

    List<Attribute> nfList = entry.getAttribute(nameFormAttrType);
    if ((nfList != null) && (! nfList.isEmpty()))
    {
      for (Attribute a : nfList)
      {
        mods.add(new Modification(ModificationType.ADD, a.duplicate()));
      }
    }


    // Get the DIT content rules attribute from the entry.
    DITContentRuleSyntax dcrSyntax;
    try
    {
      dcrSyntax = (DITContentRuleSyntax)
                  schema.getSyntax(SYNTAX_DIT_CONTENT_RULE_OID);
      if (dcrSyntax == null)
      {
        dcrSyntax = new DITContentRuleSyntax();
        dcrSyntax.initializeSyntax(null);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      dcrSyntax = new DITContentRuleSyntax();
      dcrSyntax.initializeSyntax(null);
    }

    AttributeType dcrAttrType =
         schema.getAttributeType(ATTR_DIT_CONTENT_RULES_LC);
    if (dcrAttrType == null)
    {
      dcrAttrType =
           DirectoryServer.getDefaultAttributeType(ATTR_DIT_CONTENT_RULES,
                                                   dcrSyntax);
    }

    List<Attribute> dcrList = entry.getAttribute(dcrAttrType);
    if ((dcrList != null) && (! dcrList.isEmpty()))
    {
      for (Attribute a : dcrList)
      {
        mods.add(new Modification(ModificationType.ADD, a.duplicate()));
      }
    }


    // Get the DIT structure rules attribute from the entry.
    DITStructureRuleSyntax dsrSyntax;
    try
    {
      dsrSyntax = (DITStructureRuleSyntax)
                  schema.getSyntax(SYNTAX_DIT_STRUCTURE_RULE_OID);
      if (dsrSyntax == null)
      {
        dsrSyntax = new DITStructureRuleSyntax();
        dsrSyntax.initializeSyntax(null);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      dsrSyntax = new DITStructureRuleSyntax();
      dsrSyntax.initializeSyntax(null);
    }

    AttributeType dsrAttrType =
         schema.getAttributeType(ATTR_DIT_STRUCTURE_RULES_LC);
    if (dsrAttrType == null)
    {
      dsrAttrType =
           DirectoryServer.getDefaultAttributeType(ATTR_DIT_STRUCTURE_RULES,
                                                   dsrSyntax);
    }

    List<Attribute> dsrList = entry.getAttribute(dsrAttrType);
    if ((dsrList != null) && (! dsrList.isEmpty()))
    {
      for (Attribute a : dsrList)
      {
        mods.add(new Modification(ModificationType.ADD, a.duplicate()));
      }
    }


    // Get the matching rule uses attribute from the entry.
    MatchingRuleUseSyntax mruSyntax;
    try
    {
      mruSyntax = (MatchingRuleUseSyntax)
                  schema.getSyntax(SYNTAX_MATCHING_RULE_USE_OID);
      if (mruSyntax == null)
      {
        mruSyntax = new MatchingRuleUseSyntax();
        mruSyntax.initializeSyntax(null);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      mruSyntax = new MatchingRuleUseSyntax();
      mruSyntax.initializeSyntax(null);
    }

    AttributeType mruAttrType =
         schema.getAttributeType(ATTR_MATCHING_RULE_USE_LC);
    if (mruAttrType == null)
    {
      mruAttrType =
           DirectoryServer.getDefaultAttributeType(ATTR_MATCHING_RULE_USE,
                                                   mruSyntax);
    }

    List<Attribute> mruList = entry.getAttribute(mruAttrType);
    if ((mruList != null) && (! mruList.isEmpty()))
    {
      for (Attribute a : mruList)
      {
        mods.add(new Modification(ModificationType.ADD, a.duplicate()));
      }
    }


    AttributeType synchronizationStateType =
      schema.getAttributeType(ATTR_SYNCHRONIZATION_STATE_LC);
    if (synchronizationStateType == null)
    {
      synchronizationStateType =
        DirectoryServer.getDefaultAttributeType(ATTR_SYNCHRONIZATION_STATE_LC,
            new MatchingRuleUseSyntax());
    }

    List<Attribute> synchronizationState =
      entry.getAttribute(synchronizationStateType);
    if (synchronizationState != null && !(synchronizationState.isEmpty()))
      schema.setSynchronizationState(synchronizationState.get(0).getValues());

    // Parse the attribute type definitions if there are any.
    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a.getValues())
        {
          // Parse the attribute type.
          AttributeType attrType;
          try
          {
            attrType = attrTypeSyntax.decodeAttributeType(v.getValue(),
                                                          schema, false);
            attrType.setExtraProperty(SCHEMA_PROPERTY_FILENAME, (String) null);
            attrType.setSchemaFile(schemaFile);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, de);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_PARSE_ATTR_TYPE;
            String message = getMessage(msgID, schemaFile,
                                        de.getErrorMessage());

            if (failOnError)
            {
              throw new ConfigException(msgID, message, de);
            }
            else
            {
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
              continue;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_PARSE_ATTR_TYPE;
            String message = getMessage(msgID, schemaFile,
                                        v.getStringValue() + ":  " +
                                        getExceptionMessage(e));
            if (failOnError)
            {
              throw new ConfigException(msgID, message, e);
            }
            else
            {
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
              continue;
            }
          }

          // Register it with the schema.  We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          try
          {
            schema.registerAttributeType(attrType, failOnError);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, de);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CONFLICTING_ATTR_TYPE;
            String message = getMessage(msgID, schemaFile,
                                        de.getErrorMessage());

            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                     message, msgID);

            try
            {
              schema.registerAttributeType(attrType, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }
        }
      }
    }


    // Parse the objectclass definitions if there are any.
    if (ocList != null)
    {
      for (Attribute a : ocList)
      {
        for (AttributeValue v : a.getValues())
        {
          // Parse the objectclass.
          ObjectClass oc;
          try
          {
            oc = ocSyntax.decodeObjectClass(v.getValue(), schema, false);
            oc.setExtraProperty(SCHEMA_PROPERTY_FILENAME, (String) null);
            oc.setSchemaFile(schemaFile);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, de);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_PARSE_OC;
            String message = getMessage(msgID, schemaFile,
                                        de.getErrorMessage());

            if (failOnError)
            {
              throw new ConfigException(msgID, message, de);
            }
            else
            {
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
              continue;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_PARSE_OC;
            String message = getMessage(msgID, schemaFile,
                                        v.getStringValue() + ":  " +
                                        getExceptionMessage(e));

            if (failOnError)
            {
              throw new ConfigException(msgID, message, e);
            }
            else
            {
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
              continue;
            }
          }

          // Register it with the schema.  We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          try
          {
            schema.registerObjectClass(oc, failOnError);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, de);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CONFLICTING_OC;
            String message = getMessage(msgID, schemaFile,
                                        de.getErrorMessage());
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                     message, msgID);

            try
            {
              schema.registerObjectClass(oc, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }
        }
      }
    }


    // Parse the name form definitions if there are any.
    if (nfList != null)
    {
      for (Attribute a : nfList)
      {
        for (AttributeValue v : a.getValues())
        {
          // Parse the name form.
          NameForm nf;
          try
          {
            nf = nfSyntax.decodeNameForm(v.getValue(), schema, false);
            nf.getExtraProperties().remove(SCHEMA_PROPERTY_FILENAME);
            nf.setSchemaFile(schemaFile);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, de);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_PARSE_NAME_FORM;
            String message = getMessage(msgID, schemaFile,
                                        de.getErrorMessage());

            if (failOnError)
            {
              throw new ConfigException(msgID, message, de);
            }
            else
            {
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
              continue;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_PARSE_NAME_FORM;
            String message = getMessage(msgID, schemaFile,
                                        v.getStringValue() + ":  " +
                                        getExceptionMessage(e));

            if (failOnError)
            {
              throw new ConfigException(msgID, message, e);
            }
            else
            {
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
              continue;
            }
          }

          // Register it with the schema.  We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          try
          {
            schema.registerNameForm(nf, failOnError);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, de);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CONFLICTING_NAME_FORM;
            String message = getMessage(msgID, schemaFile,
                                        de.getErrorMessage());
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                     message, msgID);

            try
            {
              schema.registerNameForm(nf, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }
        }
      }
    }


    // Parse the DIT content rule definitions if there are any.
    if (dcrList != null)
    {
      for (Attribute a : dcrList)
      {
        for (AttributeValue v : a.getValues())
        {
          // Parse the DIT content rule.
          DITContentRule dcr;
          try
          {
            dcr = dcrSyntax.decodeDITContentRule(v.getValue(), schema, false);
            dcr.getExtraProperties().remove(SCHEMA_PROPERTY_FILENAME);
            dcr.setSchemaFile(schemaFile);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, de);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_PARSE_DCR;
            String message = getMessage(msgID, schemaFile,
                                        de.getErrorMessage());

            if (failOnError)
            {
              throw new ConfigException(msgID, message, de);
            }
            else
            {
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
              continue;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_PARSE_DCR;
            String message = getMessage(msgID, schemaFile,
                                        v.getStringValue() + ":  " +
                                        getExceptionMessage(e));

            if (failOnError)
            {
              throw new ConfigException(msgID, message, e);
            }
            else
            {
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
              continue;
            }
          }

          // Register it with the schema.  We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          try
          {
            schema.registerDITContentRule(dcr, failOnError);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, de);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CONFLICTING_DCR;
            String message = getMessage(msgID, schemaFile,
                                        de.getErrorMessage());
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                     message, msgID);

            try
            {
              schema.registerDITContentRule(dcr, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }
        }
      }
    }


    // Parse the DIT structure rule definitions if there are any.
    if (dsrList != null)
    {
      for (Attribute a : dsrList)
      {
        for (AttributeValue v : a.getValues())
        {
          // Parse the DIT content rule.
          DITStructureRule dsr;
          try
          {
            dsr = dsrSyntax.decodeDITStructureRule(v.getValue(), schema,
                                                   false);
            dsr.getExtraProperties().remove(SCHEMA_PROPERTY_FILENAME);
            dsr.setSchemaFile(schemaFile);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, de);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_PARSE_DSR;
            String message = getMessage(msgID, schemaFile,
                                        de.getErrorMessage());

            if (failOnError)
            {
              throw new ConfigException(msgID, message, de);
            }
            else
            {
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
              continue;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_PARSE_DSR;
            String message = getMessage(msgID, schemaFile,
                                        v.getStringValue() + ":  " +
                                        getExceptionMessage(e));

            if (failOnError)
            {
              throw new ConfigException(msgID, message, e);
            }
            else
            {
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
              continue;
            }
          }

          // Register it with the schema.  We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          try
          {
            schema.registerDITStructureRule(dsr, failOnError);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, de);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CONFLICTING_DSR;
            String message = getMessage(msgID, schemaFile,
                                        de.getErrorMessage());
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                     message, msgID);

            try
            {
              schema.registerDITStructureRule(dsr, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }
        }
      }
    }


    // Parse the matching rule use definitions if there are any.
    if (mruList != null)
    {
      for (Attribute a : mruList)
      {
        for (AttributeValue v : a.getValues())
        {
          // Parse the matching rule use definition.
          MatchingRuleUse mru;
          try
          {
            mru = mruSyntax.decodeMatchingRuleUse(v.getValue(), schema,
                                                  false);
            mru.getExtraProperties().remove(SCHEMA_PROPERTY_FILENAME);
            mru.setSchemaFile(schemaFile);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, de);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_PARSE_MRU;
            String message = getMessage(msgID, schemaFile,
                                        de.getErrorMessage());

            if (failOnError)
            {
              throw new ConfigException(msgID, message, de);
            }
            else
            {
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
              continue;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CANNOT_PARSE_MRU;
            String message = getMessage(msgID, schemaFile,
                                        v.getStringValue() + ":  " +
                                        getExceptionMessage(e));

            if (failOnError)
            {
              throw new ConfigException(msgID, message, e);
            }
            else
            {
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
              continue;
            }
          }

          // Register it with the schema.  We will allow duplicates, with the
          // later definition overriding any earlier definition, but we want
          // to trap them and log a warning.
          try
          {
            schema.registerMatchingRuleUse(mru, failOnError);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, de);
            }

            int    msgID   = MSGID_CONFIG_SCHEMA_CONFLICTING_MRU;
            String message = getMessage(msgID, schemaFile,
                                        de.getErrorMessage());
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                     message, msgID);

            try
            {
              schema.registerMatchingRuleUse(mru, true);
            }
            catch (Exception e)
            {
              // This should never happen.
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }
        }
      }
    }


    return mods;
  }



  /**
   * Indicates whether the configuration entry that will result from a proposed
   * modification is acceptable to this change listener.
   *
   * @param  configEntry         The configuration entry that will result from
   *                             the requested update.
   * @param  unacceptableReason  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed change is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry contains an acceptable
   *          configuration, or <CODE>false</CODE> if it does not.
   */
  public boolean configChangeIsAcceptable(ConfigEntry configEntry,
                                          StringBuilder unacceptableReason)
  {
    // NYI

    // If we've gotten here then the monitor entry appears to be acceptable.
    return true;
  }



  /**
   * Attempts to apply a new configuration to this Directory Server component
   * based on the provided changed entry.
   *
   * @param  configEntry  The configuration entry that containing the updated
   *                      configuration for this component.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyConfigurationChange(ConfigEntry configEntry)
  {
    DN                configEntryDN       = configEntry.getDN();
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // NYI


    // If we've gotten here, then there haven't been any changes to anything
    // that we care about.
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Indicates whether the configuration entry that will result from a proposed
   * add is acceptable to this add listener.
   *
   * @param  configEntry         The configuration entry that will result from
   *                             the requested add.
   * @param  unacceptableReason  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed entry is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry contains an acceptable
   *          configuration, or <CODE>false</CODE> if it does not.
   */
  public boolean configAddIsAcceptable(ConfigEntry configEntry,
                                       StringBuilder unacceptableReason)
  {
    // NYI

    // If we've gotten here then the monitor entry appears to be acceptable.
    return true;
  }



  /**
   * Attempts to apply a new configuration based on the provided added entry.
   *
   * @param  configEntry  The new configuration entry that contains the
   *                      configuration to apply.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyConfigurationAdd(ConfigEntry configEntry)
  {
    DN                configEntryDN       = configEntry.getDN();
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();

    // NYI

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Indicates whether it is acceptable to remove the provided configuration
   * entry.
   *
   * @param  configEntry         The configuration entry that will be removed
   *                             from the configuration.
   * @param  unacceptableReason  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed delete is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry may be removed from the
   *          configuration, or <CODE>false</CODE> if not.
   */
  public boolean configDeleteIsAcceptable(ConfigEntry configEntry,
                                          StringBuilder unacceptableReason)
  {
    // NYI -- Should we allow deletes of elements with this as superior?

    return true;
  }



  /**
   * Attempts to apply a new configuration based on the provided deleted entry.
   *
   * @param  configEntry  The new configuration entry that has been deleted.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyConfigurationDelete(ConfigEntry configEntry)
  {
    DN         configEntryDN       = configEntry.getDN();
    ResultCode resultCode          = ResultCode.SUCCESS;
    boolean    adminActionRequired = false;


    // NYI

    return new ConfigChangeResult(resultCode, adminActionRequired);
  }
}

