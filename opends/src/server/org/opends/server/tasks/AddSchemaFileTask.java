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
package org.opends.server.tasks;
import org.opends.messages.Message;



import java.io.File;
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
import org.opends.server.types.*;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.TaskMessages.*;
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
  public Message getDisplayName() {
    return INFO_TASK_ADD_SCHEMA_FILE_NAME.get();
  }

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
        Message message = ERR_TASK_ADDSCHEMAFILE_INSUFFICIENT_PRIVILEGES.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message);
      }
    }


    // Get the attribute that specifies which schema file(s) to add.
    Entry taskEntry = getTaskEntry();
    AttributeType attrType = DirectoryServer.getAttributeType(
                                  ATTR_TASK_ADDSCHEMAFILE_FILENAME, true);
    List<Attribute> attrList = taskEntry.getAttribute(attrType);
    if ((attrList == null) || attrList.isEmpty())
    {
      Message message = ERR_TASK_ADDSCHEMAFILE_NO_FILENAME.get(
          ATTR_TASK_ADDSCHEMAFILE_FILENAME, String.valueOf(taskEntry.getDN()));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }


    // Get the name(s) of the schema files to add and make sure they exist in
    // the schema directory.
    String schemaInstallDirectory  =
      SchemaConfigManager.getSchemaDirectoryPath(false);
    String schemaInstanceDirectory =
      SchemaConfigManager.getSchemaDirectoryPath(true);
    filesToAdd = new TreeSet<String>();
    for (Attribute a : attrList)
    {
      for (AttributeValue v  : a)
      {
        String filename = v.getValue().toString();
        filesToAdd.add(filename);

        try
        {
          File schemaFile = new File(schemaInstallDirectory, filename);
          if ((! schemaFile.exists()) ||
              (! schemaFile.getParent().equals(schemaInstallDirectory)))
          {
            // try in the instance
            schemaFile = new File(schemaInstanceDirectory, filename);
            if (! schemaFile.exists())
            {
            Message message = ERR_TASK_ADDSCHEMAFILE_NO_SUCH_FILE.get(
                filename, schemaInstallDirectory, schemaInstanceDirectory);
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message);
            }
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message = ERR_TASK_ADDSCHEMAFILE_ERROR_CHECKING_FOR_FILE.get(
              filename, schemaInstallDirectory, schemaInstanceDirectory,
              getExceptionMessage(e));
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                       message, e);
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

        Message message = ERR_TASK_ADDSCHEMAFILE_ERROR_LOADING_SCHEMA_FILE.get(
            String.valueOf(schemaFile), ce.getMessage());
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, ce);
      }
      catch (InitializationException ie)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ie);
        }

        Message message = ERR_TASK_ADDSCHEMAFILE_ERROR_LOADING_SCHEMA_FILE.get(
            String.valueOf(schemaFile), ie.getMessage());
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, ie);
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
      Message message = ERR_TASK_ADDSCHEMAFILE_CANNOT_LOCK_SCHEMA.get(
          String.valueOf(schemaDN));
      logError(message);
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
            AttributeBuilder builder = new AttributeBuilder(a
                .getAttributeType(), a.getName());
            for (AttributeValue v : a)
            {
              String s = v.getValue().toString();
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

              builder.add(AttributeValues.create(a.getAttributeType(), s));
            }

            mods.add(new Modification(m.getModificationType(), builder
                .toAttribute()));
          }
        }
        catch (ConfigException ce)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, ce);
          }

          Message message = ERR_TASK_ADDSCHEMAFILE_ERROR_LOADING_SCHEMA_FILE.
              get(String.valueOf(schemaFile), ce.getMessage());
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }
        catch (InitializationException ie)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, ie);
          }

          Message message = ERR_TASK_ADDSCHEMAFILE_ERROR_LOADING_SCHEMA_FILE.
              get(String.valueOf(schemaFile), ie.getMessage());
          logError(message);
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

            Message message =
                ERR_TASK_ADDSCHEMAFILE_CANNOT_NOTIFY_SYNC_PROVIDER.
                  get(provider.getClass().getName(), getExceptionMessage(e));
            logError(message);
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

