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
package org.opends.server.tasks;



import java.io.File;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;

import org.opends.server.admin.std.server.SynchronizationProviderCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockManager;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;
import org.opends.server.types.Schema;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.TaskMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of a Directory Server task that can be
 * used to add the contents of a new schema file into the server schema.
 */
public class AddSchemaFileTask
       extends Task
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The list of files to be added to the server schema.
  TreeSet<String> filesToAdd;



  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeTask()
         throws DirectoryException
  {
    // If the client connection is available, then make sure the associated
    // client has the UPDATE_SCHEMA privilege.
    Operation operation = getOperation();
    if (operation != null)
    {
      ClientConnection clientConnection = operation.getClientConnection();
      if (! clientConnection.hasPrivilege(Privilege.UPDATE_SCHEMA, operation))
      {
        int    msgID   = MSGID_TASK_ADDSCHEMAFILE_INSUFFICIENT_PRIVILEGES;
        String message = getMessage(msgID);
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message, msgID);
      }
    }


    // Get the attribute that specifies which schema file(s) to add.
    Entry taskEntry = getTaskEntry();
    AttributeType attrType = DirectoryServer.getAttributeType(
                                  ATTR_TASK_ADDSCHEMAFILE_FILENAME, true);
    List<Attribute> attrList = taskEntry.getAttribute(attrType);
    if ((attrList == null) || attrList.isEmpty())
    {
      int    msgID   = MSGID_TASK_ADDSCHEMAFILE_NO_FILENAME;
      String message = getMessage(msgID, ATTR_TASK_ADDSCHEMAFILE_FILENAME,
                                  String.valueOf(taskEntry.getDN()));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }


    // Get the name(s) of the schema files to add and make sure they exist in
    // the schema directory.
    String schemaDirectory = SchemaConfigManager.getSchemaDirectoryPath();
    filesToAdd = new TreeSet<String>();
    for (Attribute a : attrList)
    {
      for (AttributeValue v  : a.getValues())
      {
        String filename = v.getStringValue();
        filesToAdd.add(filename);

        try
        {
          File schemaFile = new File(schemaDirectory, filename);
          if ((! schemaFile.exists()) ||
              (! schemaFile.getParent().equals(schemaDirectory)))
          {
            int    msgID   = MSGID_TASK_ADDSCHEMAFILE_NO_SUCH_FILE;
            String message = getMessage(msgID, filename, schemaDirectory);
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message, msgID);
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_TASK_ADDSCHEMAFILE_ERROR_CHECKING_FOR_FILE;
          String message = getMessage(msgID, filename, schemaDirectory,
                                      getExceptionMessage(e));
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                       message, msgID, e);
        }
      }
    }


    // Create a new dummy schema and make sure that we can add the contents of
    // all the schema files into it.  Even though this duplicates work we'll
    // have to do later, it will be good to do it now as well so we can reject
    // the entry immediately which will fail the attempt by the client to add it
    // to the server, rather than having to check its status after the fact.
    Schema schema = DirectoryServer.getSchema().duplicate();
    for (String schemaFile : filesToAdd)
    {
      try
      {
        SchemaConfigManager.loadSchemaFile(schema, schemaFile);
      }
      catch (ConfigException ce)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ce);
        }

        int    msgID   = MSGID_TASK_ADDSCHEMAFILE_ERROR_LOADING_SCHEMA_FILE;
        String message = getMessage(msgID, String.valueOf(schemaFile),
                                    ce.getMessage());
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, ce);
      }
      catch (InitializationException ie)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ie);
        }

        int    msgID   = MSGID_TASK_ADDSCHEMAFILE_ERROR_LOADING_SCHEMA_FILE;
        String message = getMessage(msgID, String.valueOf(schemaFile),
                                    ie.getMessage());
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, ie);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  protected TaskState runTask()
  {
    // Obtain a write lock on the server schema so that we can be sure nothing
    // else tries to write to it at the same time.
    DN schemaDN = DirectoryServer.getSchemaDN();
    Lock schemaLock = LockManager.lockWrite(schemaDN);
    for (int i=0; ((schemaLock == null) && (i < 3)); i++)
    {
      schemaLock = LockManager.lockWrite(schemaDN);
    }

    if (schemaLock == null)
    {
      int    msgID   = MSGID_TASK_ADDSCHEMAFILE_CANNOT_LOCK_SCHEMA;
      String message = getMessage(msgID, String.valueOf(schemaDN));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return TaskState.STOPPED_BY_ERROR;
    }

    try
    {
      LinkedList<Modification> mods = new LinkedList<Modification>();
      Schema schema = DirectoryServer.getSchema().duplicate();
      for (String schemaFile : filesToAdd)
      {
        try
        {
          List<Modification> modList =
               SchemaConfigManager.loadSchemaFile(schema, schemaFile);
          for (Modification m : modList)
          {
            Attribute a = m.getAttribute();
            LinkedHashSet<AttributeValue> valuesWithFileElement =
                 new LinkedHashSet<AttributeValue>();
            for (AttributeValue v : a.getValues())
            {
              String s = v.getStringValue();
              if (s.indexOf(SCHEMA_PROPERTY_FILENAME) < 0)
              {
                if (s.endsWith(" )"))
                {
                 s = s.substring(0, s.length()-1) + SCHEMA_PROPERTY_FILENAME +
                     " '" + schemaFile + "' )";
                }
                else if (s.endsWith(")"))
                {
                 s = s.substring(0, s.length()-1) + " " +
                     SCHEMA_PROPERTY_FILENAME + " '" + schemaFile + "' )";
                }
              }

              valuesWithFileElement.add(new AttributeValue(a.getAttributeType(),
                                                           s));
            }

            Attribute attrWithFile = new Attribute(a.getAttributeType(),
                                                   a.getName(),
                                                   valuesWithFileElement);
            mods.add(new Modification(m.getModificationType(), attrWithFile));
          }
        }
        catch (ConfigException ce)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, ce);
          }

          int    msgID   = MSGID_TASK_ADDSCHEMAFILE_ERROR_LOADING_SCHEMA_FILE;
          String message = getMessage(msgID, String.valueOf(schemaFile),
                                      ce.getMessage());
          logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return TaskState.STOPPED_BY_ERROR;
        }
        catch (InitializationException ie)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, ie);
          }

          int    msgID   = MSGID_TASK_ADDSCHEMAFILE_ERROR_LOADING_SCHEMA_FILE;
          String message = getMessage(msgID, String.valueOf(schemaFile),
                                      ie.getMessage());
          logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return TaskState.STOPPED_BY_ERROR;
        }
      }

      if (! mods.isEmpty())
      {
        for (SynchronizationProvider<SynchronizationProviderCfg> provider :
             DirectoryServer.getSynchronizationProviders())
        {
          try
          {
            provider.processSchemaChange(mods);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            int msgID = MSGID_TASK_ADDSCHEMAFILE_CANNOT_NOTIFY_SYNC_PROVIDER;
            String message = getMessage(msgID, provider.getClass().getName(),
                                        getExceptionMessage(e));
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
                     message, msgID);
          }
        }

        Schema.writeConcatenatedSchema();
      }

      schema.setYoungestModificationTime(System.currentTimeMillis());
      DirectoryServer.setSchema(schema);
      return TaskState.COMPLETED_SUCCESSFULLY;
    }
    finally
    {
      LockManager.unlock(schemaDN, schemaLock);
    }
  }
}

