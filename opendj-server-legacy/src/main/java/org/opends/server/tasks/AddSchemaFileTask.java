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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.tasks;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.server.config.server.SynchronizationProviderCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.schema.SchemaHandler;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockManager.DNLock;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.util.SchemaUtils;

import static org.opends.messages.TaskMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.*;
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

  @Override
  public LocalizableMessage getDisplayName() {
    return INFO_TASK_ADD_SCHEMA_FILE_NAME.get();
  }

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
    Iterable<Attribute> attrList = taskEntry.getAllAttributes(ATTR_TASK_ADDSCHEMAFILE_FILENAME);
    if (!attrList.iterator().hasNext())
    {
      LocalizableMessage message = ERR_TASK_ADDSCHEMAFILE_NO_FILENAME.get(
          ATTR_TASK_ADDSCHEMAFILE_FILENAME, taskEntry.getName());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    // Get the name(s) of the schema files to add and make sure they exist in
    // the schema directory.
    File schemaInstanceDirectory = getSchemaDirectory();
    filesToAdd = new TreeSet<>();
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

    // Make sure that we can add the contents of all the schema files into the schema.
    // Even though this duplicates work we'll have to do later,
    // it will be good to do it now as well so we can reject
    // the entry immediately which will fail the attempt by the client to add it
    // to the server, rather than having to check its status after the fact.
    final SchemaHandler schemaHandler = getServerContext().getSchemaHandler();
    final Schema currentSchema = schemaHandler.getSchema();
    final SchemaBuilder schemaBuilder = new SchemaBuilder(currentSchema);
    for (String schemaFile : filesToAdd)
    {
      try
      {
        File file = new File(schemaInstanceDirectory, schemaFile);
        schemaHandler.loadSchemaFileIntoSchemaBuilder(file, schemaBuilder, currentSchema);
      }
      catch (ConfigException | InitializationException e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_TASK_ADDSCHEMAFILE_ERROR_LOADING_SCHEMA_FILE.get(schemaFile, e.getMessage());
        throw new DirectoryException(getCoreConfigManager().getServerErrorResultCode(), message, e);
      }
    }
  }

  private File getSchemaDirectory() throws DirectoryException
  {
    try
    {
      return getServerContext().getSchemaHandler().getSchemaDirectoryPath();
    }
    catch (InitializationException e)
    {
      logger.traceException(e);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,  e.getMessageObject());
    }
  }

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
      LinkedList<Modification> mods = new LinkedList<>();
      final SchemaHandler schemaHandler = getServerContext().getSchemaHandler();
      final Schema currentSchema = schemaHandler.getSchema();
      final SchemaBuilder schemaBuilder = new SchemaBuilder(currentSchema);
      for (String schemaFile : filesToAdd)
      {
        try
        {
          File file = new File(getSchemaDirectory(), schemaFile);
          List<Modification> modList =
              schemaHandler.loadSchemaFileIntoSchemaBuilderAndReturnModifications(file, schemaBuilder, currentSchema);
          for (Modification m : modList)
          {
            Attribute a = m.getAttribute();
            AttributeBuilder builder = new AttributeBuilder(a.getAttributeDescription());
            for (ByteString v : a)
            {
              builder.add(SchemaUtils.addSchemaFileToElementDefinitionIfAbsent(v.toString(), schemaFile));
            }

            mods.add(new Modification(m.getModificationType(), builder.toAttribute()));
          }
        }
        catch (DirectoryException | ConfigException | InitializationException e)
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

        final Schema newSchema = schemaBuilder.toSchema();
        try
        {
          schemaHandler.updateSchemaAndConcatenatedSchemaFile(newSchema);
        }
        catch (DirectoryException e)
        {
          // This is very unlikely to happen because each schema file was previously loaded without error
          logger.traceException(e);
          logger.error(ERR_TASK_ADDSCHEMAFILE_SCHEMA_VALIDATION_ERROR, e.getMessage());
          return TaskState.STOPPED_BY_ERROR;
        }
      }

      return TaskState.COMPLETED_SUCCESSFULLY;
    }
    finally
    {
      schemaLock.unlock();
    }
  }
}
