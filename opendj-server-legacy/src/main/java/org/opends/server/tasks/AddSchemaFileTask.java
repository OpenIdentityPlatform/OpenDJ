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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.tasks;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.std.server.SynchronizationProviderCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SchemaConfigManager;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.*;
import org.opends.server.types.LockManager.DNLock;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;

import static org.opends.messages.TaskMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.getSchemaDN;
import static org.opends.server.core.DirectoryServer.getServerErrorResultCode;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class provides an implementation of a Directory Server task that can be
 * used to add the contents of a new schema file into the server schema.
 */
public class AddSchemaFileTask
       extends Task
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The list of files to be added to the server schema. */
  private TreeSet<String> filesToAdd;

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getDisplayName() {
    return INFO_TASK_ADD_SCHEMA_FILE_NAME.get();
  }

  /** {@inheritDoc} */
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
        LocalizableMessage message = ERR_TASK_ADDSCHEMAFILE_INSUFFICIENT_PRIVILEGES.get();
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
      LocalizableMessage message = ERR_TASK_ADDSCHEMAFILE_NO_FILENAME.get(
          ATTR_TASK_ADDSCHEMAFILE_FILENAME, taskEntry.getName());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }


    // Get the name(s) of the schema files to add and make sure they exist in
    // the schema directory.
    String schemaInstanceDirectory =
      SchemaConfigManager.getSchemaDirectoryPath();
    filesToAdd = new TreeSet<String>();
    for (Attribute a : attrList)
    {
      for (ByteString v  : a)
      {
        String filename = v.toString();
        filesToAdd.add(filename);

        try
        {
          File schemaFile = new File(schemaInstanceDirectory, filename);
          if (! schemaFile.exists())
          {
            LocalizableMessage message = ERR_TASK_ADDSCHEMAFILE_NO_SUCH_FILE.get(
                filename, schemaInstanceDirectory);
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message);
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);

          LocalizableMessage message = ERR_TASK_ADDSCHEMAFILE_ERROR_CHECKING_FOR_FILE.get(
              filename, schemaInstanceDirectory,
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
        SchemaConfigManager.loadSchemaFile(getServerContext(), schema, schemaFile);
      }
      catch (ConfigException | InitializationException e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_TASK_ADDSCHEMAFILE_ERROR_LOADING_SCHEMA_FILE.get(schemaFile, e.getMessage());
        throw new DirectoryException(getServerErrorResultCode(), message, e);
      }
    }
  }



  /** {@inheritDoc} */
  @Override
  protected TaskState runTask()
  {
    // Obtain a write lock on the server schema so that we can be sure nothing
    // else tries to write to it at the same time.
    final DNLock schemaLock = DirectoryServer.getLockManager().tryWriteLockEntry(getSchemaDN());
    if (schemaLock == null)
    {
      logger.error(ERR_TASK_ADDSCHEMAFILE_CANNOT_LOCK_SCHEMA, getSchemaDN());
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
               SchemaConfigManager.loadSchemaFile(getServerContext(), schema, schemaFile);
          for (Modification m : modList)
          {
            Attribute a = m.getAttribute();
            AttributeBuilder builder = new AttributeBuilder(a
                .getAttributeType(), a.getName());
            for (ByteString v : a)
            {
              String s = v.toString();
              if (!s.contains(SCHEMA_PROPERTY_FILENAME))
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

              builder.add(s);
            }

            mods.add(new Modification(m.getModificationType(), builder
                .toAttribute()));
          }
        }
        catch (ConfigException | InitializationException e)
        {
          logger.traceException(e);
          logger.error(ERR_TASK_ADDSCHEMAFILE_ERROR_LOADING_SCHEMA_FILE, schemaFile, e.getMessage());
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
            logger.traceException(e);

            logger.error(ERR_TASK_ADDSCHEMAFILE_CANNOT_NOTIFY_SYNC_PROVIDER,
                provider.getClass().getName(), getExceptionMessage(e));
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
      schemaLock.unlock();
    }
  }
}

