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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.tools.upgrade;

import static org.opends.server.util.SchemaUtils.addSchemaFileToElementDefinitionIfAbsent;

import static java.nio.charset.StandardCharsets.*;
import static java.nio.file.StandardOpenOption.*;
import static javax.security.auth.callback.ConfirmationCallback.NO;
import static javax.security.auth.callback.ConfirmationCallback.YES;
import static javax.security.auth.callback.TextOutputCallback.*;
import static org.forgerock.util.Utils.joinAsString;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.upgrade.FileManager.copyRecursively;
import static org.opends.server.tools.upgrade.UpgradeUtils.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.security.auth.callback.TextOutputCallback;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.ldif.EntryReader;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.tools.RebuildIndex;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.ChangeOperationType;
import org.opends.server.util.StaticUtils;

import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ReturnCode;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

/** Factory methods for create new upgrade tasks. */
final class UpgradeTasks
{
  /** Logger for the upgrade. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** An errors counter in case of ignore errors mode. */
  static int countErrors;

  /** Contains all the indexes to rebuild. */
  private static final Set<String> indexesToRebuild = new LinkedHashSet<>();

  /** A flag to avoid rebuild single indexes if 'rebuild all' is selected. */
  private static boolean isRebuildAllIndexesIsPresent;
  /** A flag for marking 'rebuild all' task accepted by user. */
  private static boolean isRebuildAllIndexesTaskAccepted;

  private static final List<String> SUPPORTED_LOCALES_FOR_3_0_0 = Arrays.asList(
      "ca_ES", "de", "es", "fr", "ja", "ko", "pl", "zh_CN", "zh_TW");

  /**
   * Returns a new upgrade task which adds a config entry to the underlying
   * config file.
   *
   * @param summary
   *          The summary of this upgrade task.
   * @param ldif
   *          The LDIF record which will be applied to matching entries.
   * @return A new upgrade task which applies an LDIF record to all
   *         configuration entries matching the provided filter.
   */
  static UpgradeTask addConfigEntry(final LocalizableMessage summary,
      final String... ldif)
  {
    return updateConfigEntry(summary, null, ChangeOperationType.ADD, ldif);
  }

  /**
   * Returns a new upgrade task which adds a config entry to the underlying
   * config file. No summary message will be output.
   *
   * @param ldif
   *          The LDIF record which will be applied to matching entries.
   * @return A new upgrade task which applies an LDIF record to all
   *         configuration entries matching the provided filter.
   */
  static UpgradeTask addConfigEntry(final String... ldif)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        try
        {
          final int changeCount = updateConfigFile(configFile, null, ChangeOperationType.ADD, ldif);
          displayChangeCount(configFile, changeCount);
        }
        catch (final Exception e)
        {
          countErrors++;
          throw new ClientException(ReturnCode.ERROR_UNEXPECTED, LocalizableMessage.raw(e.getMessage()));
        }
      }

      @Override
      public String toString()
      {
        return "Add entry " + ldif[0];
      }
    };
  }

  /**
   * This task copies the file placed in parameter within the config / schema
   * folder. If the file already exists, it's overwritten.
   *
   * @param fileName
   *          The name of the file which need to be copied.
   * @return A task which copy the the file placed in parameter within the
   *         config / schema folder. If the file already exists, it's
   *         overwritten.
   */
  static UpgradeTask copySchemaFile(final String fileName)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        final LocalizableMessage msg = INFO_UPGRADE_TASK_REPLACE_SCHEMA_FILE.get(fileName);
        logger.debug(msg);

        final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, msg, 0);

        final File schemaFileTemplate =
            new File(templateConfigSchemaDirectory, fileName);

        try
        {
          context.notifyProgress(pnc.setProgress(20));
          if (!schemaFileTemplate.exists() || schemaFileTemplate.length() == 0)
          {
            throw new IOException(ERR_UPGRADE_CORRUPTED_TEMPLATE
                .get(schemaFileTemplate.getPath()).toString());
          }
          copyRecursively(schemaFileTemplate, configSchemaDirectory, true);
          context.notifyProgress(pnc.setProgress(100));
        }
        catch (final IOException e)
        {
          throw unexpectedException(context, pnc, ERR_UPGRADE_COPYSCHEMA_FAILS.get(
              schemaFileTemplate.getName(), e.getMessage()));
        }
      }

      @Override
      public String toString()
      {
        return INFO_UPGRADE_TASK_REPLACE_SCHEMA_FILE.get(fileName).toString();
      }
    };
  }

  /**
   * This task copies the file placed in parameter within the config folder. If
   * the file already exists, it's overwritten.
   *
   * @param fileName
   *          The name of the file which need to be copied.
   * @return A task which copy the the file placed in parameter within the
   *         config folder. If the file already exists, it's overwritten.
   */
  static UpgradeTask addConfigFile(final String fileName)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        final LocalizableMessage msg = INFO_UPGRADE_TASK_ADD_CONFIG_FILE.get(fileName);
        logger.debug(msg);

        final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, msg, 0);

        final File configFile = new File(templateConfigDirectory, fileName);

        try
        {
          context.notifyProgress(pnc.setProgress(20));

          copyRecursively(configFile, configDirectory, true);
          context.notifyProgress(pnc.setProgress(100));
        }
        catch (final IOException e)
        {
          throw unexpectedException(context, pnc, ERR_UPGRADE_ADD_CONFIG_FILE_FAILS.get(
              configFile.getName(), e.getMessage()));
        }
      }

      @Override
      public String toString()
      {
        return INFO_UPGRADE_TASK_ADD_CONFIG_FILE.get(fileName).toString();
      }
    };
  }

  /**
   * Returns a new upgrade task which deletes a config entry from the underlying config file.
   *
   * @param summary
   *          The summary of this upgrade task.
   * @param dnsInLDIF
   *          The dns to delete in the form of LDIF.
   * @return A new upgrade task which applies an LDIF record to all configuration entries matching
   *         the provided filter.
   */
  static UpgradeTask deleteConfigEntry(final LocalizableMessage summary, final String... dnsInLDIF)
  {
    return updateConfigEntry(summary, null, ChangeOperationType.DELETE, dnsInLDIF);
  }

  /**
   * Returns a new upgrade task which applies an LDIF record to all
   * configuration entries matching the provided filter.
   *
   * @param summary
   *          The summary of this upgrade task.
   * @param filter
   *          The LDAP filter which configuration entries must match.
   * @param ldif
   *          The LDIF record which will be applied to matching entries.
   * @return A new upgrade task which applies an LDIF record to all
   *         configuration entries matching the provided filter.
   */
  static UpgradeTask modifyConfigEntry(final LocalizableMessage summary,
      final String filter, final String... ldif)
  {
    return updateConfigEntry(summary, filter, ChangeOperationType.MODIFY, ldif);
  }

  /**
   * This task adds or updates an attribute type (must exist in the original file)
   * to the file specified in {@code fileName}. The destination must be a file
   * contained in the config/schema folder. The attribute type is updated if an
   * attribute with the same OID exists.
   *
   * e.g : This example adds a new attribute type named 'etag' in the 00-core.ldif.
   * The 'etag' attribute already exists in the 00-core.ldif template schema file.
   *
   * <pre>
   * register(&quot;2.5.0&quot;,
   *   newAttributeTypes(LocalizableMessage.raw(&quot;New attribute etag&quot;),
   *   false, &quot;00-core.ldif&quot;,
   *   &quot;1.3.6.1.4.1.36733.2.1.1.59&quot;));
   * </pre>
   *
   * @param summary
   *          The summary of the task.
   * @param fileName
   *          The file where to add the new definitions. This file must be
   *          contained in the configuration/schema folder.
   * @param attributeOids
   *          The OIDs of the attributes to add or update.
   * @return An upgrade task which adds or updates attribute types, defined
   *         previously in the configuration template files, reads the
   *         definition and adds it onto the file specified in {@code fileName}
   */
  static UpgradeTask newAttributeTypes(final LocalizableMessage summary,
      final String fileName, final String... attributeOids)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        logger.debug(summary);

        final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, summary, 20);
        context.notifyProgress(pnc);

        final File schemaFileTemplate = new File(templateConfigSchemaDirectory, fileName);
        final File pathDestination = new File(configSchemaDirectory, fileName);
        try
        {
          final int changeCount = updateSchemaFile(schemaFileTemplate, pathDestination, attributeOids, null);
          displayChangeCount(pathDestination, changeCount);
          context.notifyProgress(pnc.setProgress(100));
        }
        catch (final IOException | IllegalStateException e)
        {
          throw unexpectedException(context, pnc, ERR_UPGRADE_ADDATTRIBUTE_FAILS.get(
              schemaFileTemplate.getName(), e.getMessage()));
        }
      }

      @Override
      public String toString()
      {
        return String.valueOf(summary);
      }
    };
  }

  /**
   * This task adds or updates an object class (must exist in the original file)
   * to the file specified in {@code fileName}. The destination must be a file
   * contained in the config/schema folder. The object class will be updated if
   * a definition with the same OID exists, and added otherwise.
   *
   * @param summary
   *          The summary of the task.
   * @param fileName
   *          The file where to add the new definitions. This file must be
   *          contained in the configuration/schema folder.
   * @param objectClassesOids
   *          The OIDs of the object classes to add or update.
   * @return An upgrade task which adds or updates object classes, defined
   *         previously in the configuration template files, reads the
   *         definition and adds it onto the file specified in {@code fileName}
   */
  public static UpgradeTask newObjectClasses(final LocalizableMessage summary,
      final String fileName, final String... objectClassesOids)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        logger.debug(summary);

        final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, summary, 20);
        context.notifyProgress(pnc);

        final File schemaFileTemplate = new File(templateConfigSchemaDirectory, fileName);
        final File pathDestination = new File(configSchemaDirectory, fileName);
        context.notifyProgress(pnc.setProgress(20));

        try
        {
          final int changeCount = updateSchemaFile(schemaFileTemplate, pathDestination, null, objectClassesOids);
          displayChangeCount(pathDestination, changeCount);
          context.notifyProgress(pnc.setProgress(100));
        }
        catch (final IOException e)
        {
          throw unexpectedException(context, pnc, ERR_UPGRADE_ADDOBJECTCLASS_FAILS.get(
              schemaFileTemplate.getName(), e.getMessage()));
        }
        catch (final IllegalStateException e)
        {
          throw unexpectedException(context, pnc, ERR_UPGRADE_ADDATTRIBUTE_FAILS.get(
              schemaFileTemplate.getName(), e.getMessage()));
        }
      }

      @Override
      public String toString()
      {
        return String.valueOf(summary);
      }
    };
  }

  /**
   * Creates a group of tasks which will only be invoked if the current version
   * is more recent than the provided version. This may be useful in cases where
   * a regression was introduced in version X and resolved in a later version Y.
   * In this case, the provided upgrade tasks will only be invoked if the
   * current version is between X (inclusive) and Y (exclusive).
   *
   * @param versionString
   *          The lower bound version. The upgrade tasks will not be applied if
   *          the current version is older than this version.
   * @param tasks
   *          The group of tasks to invoke if the current version is equal to or
   *          more recent than {@code versionString}.
   * @return An upgrade task which will only be invoked if the current version
   *         is more recent than the provided version.
   */
  static UpgradeTask regressionInVersion(final String versionString, final UpgradeTask... tasks)
  {
    final BuildVersion version = BuildVersion.valueOf(versionString);
    return conditionalUpgradeTasks(new UpgradeCondition()
    {
      @Override
      public boolean shouldPerformUpgradeTasks(final UpgradeContext context) throws ClientException
      {
        return context.getFromVersion().compareTo(version) >= 0;
      }

      @Override
      public String toString()
      {
        return "Regression in version \"" + versionString + "\"";
      }
    }, tasks);
  }

  /**
   * Creates a group of tasks which will only be invoked if the user confirms agreement. This may be
   * useful in cases where a feature is deprecated and the upgrade is capable of migrating the
   * configuration to the new replacement feature.
   *
   * @param message
   *          The confirmation message.
   * @param tasks
   *          The group of tasks to invoke if the user agrees.
   * @return An upgrade task which will only be invoked if the user confirms agreement.
   */
  static UpgradeTask requireConfirmation(
          final LocalizableMessage message, final int defaultResponse, final UpgradeTask... tasks)
  {
    return conditionalUpgradeTasks(new UpgradeCondition()
    {
      @Override
      public boolean shouldPerformUpgradeTasks(final UpgradeContext context) throws ClientException
      {
        return context.confirmYN(INFO_UPGRADE_TASK_NEEDS_USER_CONFIRM.get(message), defaultResponse) == YES;
      }

      @Override
      public String toString()
      {
        return INFO_UPGRADE_TASK_NEEDS_USER_CONFIRM.get(message).toString();
      }
    }, tasks);
  }

  /** Determines whether conditional tasks should be performed. */
  interface UpgradeCondition
  {
    boolean shouldPerformUpgradeTasks(UpgradeContext context) throws ClientException;
  }

  static UpgradeTask conditionalUpgradeTasks(final UpgradeCondition condition, final UpgradeTask... tasks)
  {
    return new AbstractUpgradeTask()
    {
      private boolean shouldPerformUpgradeTasks = true;

      @Override
      public void prepare(final UpgradeContext context) throws ClientException
      {
        shouldPerformUpgradeTasks = condition.shouldPerformUpgradeTasks(context);
        if (shouldPerformUpgradeTasks)
        {
          for (UpgradeTask task : tasks)
          {
            task.prepare(context);
          }
        }
      }

      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        if (shouldPerformUpgradeTasks)
        {
          for (UpgradeTask task : tasks)
          {
            try
            {
              task.perform(context);
            }
            catch (ClientException e)
            {
              handleClientException(context, e);
            }
          }
        }
      }

      @Override
      public void postUpgrade(UpgradeContext context) throws ClientException
      {
        if (shouldPerformUpgradeTasks)
        {
          boolean isOk = true;
          for (final UpgradeTask task : tasks)
          {
            if (isOk)
            {
              try
              {
                task.postUpgrade(context);
              }
              catch (ClientException e)
              {
                logger.error(e.getMessageObject());
                isOk = false;
              }
            }
            else
            {
              task.postponePostUpgrade(context);
            }
          }
        }
      }

      @Override
      public String toString()
      {
        final StringBuilder sb = new StringBuilder();
        sb.append(condition).append(" = ").append(shouldPerformUpgradeTasks).append('\n');
        sb.append('[');
        joinAsString(sb, "\n", (Object[]) tasks);
        sb.append(']');
        return sb.toString();
      }
    };
  }

  /**
   * Creates a rebuild all indexes task.
   *
   * @param summary
   *          The summary of this upgrade task.
   * @return An Upgrade task which rebuild all the indexes.
   */
  static UpgradeTask rebuildAllIndexes(final LocalizableMessage summary)
  {
    return new AbstractUpgradeTask()
    {
      private boolean isATaskToPerform;

      @Override
      public void prepare(UpgradeContext context) throws ClientException
      {
        Upgrade.needToRunPostUpgradePhase();
        // Requires answer from the user.
        isATaskToPerform = context.confirmYN(summary, NO) == YES;
        isRebuildAllIndexesIsPresent = true;
        isRebuildAllIndexesTaskAccepted = isATaskToPerform;
      }

      @Override
      public void postUpgrade(final UpgradeContext context) throws ClientException
      {
        if (!isATaskToPerform)
        {
          postponePostUpgrade(context);
        }
      }

      @Override
      public void postponePostUpgrade(UpgradeContext context) throws ClientException
      {
        context.notify(INFO_UPGRADE_ALL_REBUILD_INDEX_DECLINED.get(), TextOutputCallback.WARNING);
      }

      @Override
      public String toString()
      {
        return String.valueOf(summary);
      }
    };
  }

  /**
   * Creates a rebuild index task for a given single index. As this task is
   * possibly lengthy, it's considered as a post upgrade task. This task is not
   * mandatory; e.g not require user interaction, but could be required to get a
   * fully functional server. <br />
   * The post upgrade task just register the task. The rebuild indexes tasks are
   * completed at the end of the upgrade process.
   *
   * @param summary
   *          A message describing why the index needs to be rebuilt and asking
   *          them whether they wish to perform this task after the upgrade.
   * @param indexNames
   *          The indexes to rebuild.
   * @return The rebuild index task.
   */
  static UpgradeTask rebuildIndexesNamed(final LocalizableMessage summary, final String... indexNames)
  {
    return new AbstractUpgradeTask()
    {
      private boolean isATaskToPerform;

      @Override
      public void prepare(UpgradeContext context) throws ClientException
      {
        Upgrade.needToRunPostUpgradePhase();
        // Requires answer from the user.
        isATaskToPerform = context.confirmYN(summary, NO) == YES;
      }

      @Override
      public void postUpgrade(final UpgradeContext context) throws ClientException
      {
        if (isATaskToPerform)
        {
          Collections.addAll(indexesToRebuild, indexNames);
        }
        else
        {
          postponePostUpgrade(context);
        }
      }

      @Override
      public void postponePostUpgrade(UpgradeContext context) throws ClientException
      {
        if (!isRebuildAllIndexesIsPresent)
        {
          context.notify(INFO_UPGRADE_REBUILD_INDEXES_DECLINED.get(joinAsString(", ", indexNames)),
              TextOutputCallback.WARNING);
        }
      }

      @Override
      public String toString()
      {
        return String.valueOf(summary);
      }
    };
  }

  /**
   * This task is processed at the end of the upgrade, rebuilding indexes. If a
   * rebuild all indexes has been registered before, it takes the flag
   * relatively to single rebuild index.
   *
   * @return The post upgrade rebuild indexes task.
   */
  static UpgradeTask postUpgradeRebuildIndexes()
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void postUpgrade(final UpgradeContext context) throws ClientException
      {
        if (!isRebuildAllIndexesIsPresent && indexesToRebuild.isEmpty())
        {
          return;
        }

        final Map<String, Set<String>> baseDNsForBackends = UpgradeUtils.getBaseDNsPerBackendsFromConfig();
        if (isRebuildAllIndexesTaskAccepted)
        {
          final Set<String> allBaseDNs = new HashSet<>();
          for (final Set<String> baseDNsForBackend : baseDNsForBackends.values())
          {
            allBaseDNs.addAll(baseDNsForBackend);
          }
          rebuildIndex(INFO_UPGRADE_REBUILD_ALL.get(), context, allBaseDNs, Collections.singletonList("--rebuildAll"));
        }
        else
        {
          for (final Map.Entry<String, Set<String>> backendEntry : baseDNsForBackends.entrySet())
          {
            final String backend = backendEntry.getKey();
            if (indexesToRebuild.isEmpty())
            {
              logger.debug(INFO_UPGRADE_NO_INDEX_TO_REBUILD_FOR_BACKEND.get(backend));
              continue;
            }

            final List<String> args = new ArrayList<>();
            for (final String indexToRebuild : indexesToRebuild)
            {
              args.add("--index");
              args.add(indexToRebuild);
            }
            final Set<String> baseDNs = backendEntry.getValue();
            rebuildIndex(INFO_UPGRADE_REBUILD_INDEX_STARTS.get(indexesToRebuild, baseDNs), context, baseDNs, args);
          }
        }
      }

      private void rebuildIndex(final LocalizableMessage infoMsg, final UpgradeContext context,
          final Set<String> baseDNs, final List<String> baseArgs) throws ClientException
      {
        final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, infoMsg, 25);
        logger.debug(infoMsg);
        context.notifyProgress(pnc);

        List<String> args = new ArrayList<>(baseArgs);
        args.add("--configFile");
        args.add(configFile.getAbsolutePath());
        for (final String be : baseDNs)
        {
          args.add("--baseDN");
          args.add(be);
        }
        logger.debug(INFO_UPGRADE_REBUILD_INDEX_ARGUMENTS, args);

        final int result = new RebuildIndex().rebuildIndexesWithinMultipleBackends(
            true, UpgradeLog.getPrintStream(), args);
        if (result != 0)
        {
          final LocalizableMessage msg = ERR_UPGRADE_PERFORMING_POST_TASKS_FAIL.get();
          context.notifyProgress(pnc.setProgress(-100));
          throw new ClientException(ReturnCode.ERROR_UNEXPECTED, msg);
        }

        logger.debug(INFO_UPGRADE_REBUILD_INDEX_ENDS);
        context.notifyProgress(pnc.setProgress(100));
      }

      @Override
      public String toString()
      {
        return "Post upgrade rebuild indexes task";
      }
    };
  }

  /**
   * Creates a file object representing config/upgrade/schema.ldif.current which
   * the server creates the first time it starts if there are schema
   * customizations.
   *
   * @return An upgrade task which upgrade the config/upgrade folder, creating a
   *         new schema.ldif.rev which is needed after schema customization for
   *         starting correctly the server.
   */
  static UpgradeTask updateConfigUpgradeFolder()
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        final LocalizableMessage msg = INFO_UPGRADE_TASK_REFRESH_UPGRADE_DIRECTORY.get();
        logger.debug(msg);

        final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, msg, 20);
        context.notifyProgress(pnc);

        try
        {
          String toRevision = context.getToVersion().getRevision();
          updateConfigUpgradeSchemaFile(configSchemaDirectory, toRevision);

          context.notifyProgress(pnc.setProgress(100));
        }
        catch (final Exception ex)
        {
          throw unexpectedException(context, pnc, ERR_UPGRADE_CONFIG_ERROR_UPGRADE_FOLDER.get(ex.getMessage()));
        }
      }

      @Override
      public String toString()
      {
        return INFO_UPGRADE_TASK_REFRESH_UPGRADE_DIRECTORY.get().toString();
      }
    };
  }

  /**
   * Removes the specified file from the file-system.
   *
   * @param file
   *          The file to be removed.
   * @return An upgrade task which removes the specified file from the file-system.
   */
  static UpgradeTask deleteFile(final File file)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(UpgradeContext context) throws ClientException
      {
        LocalizableMessage msg = INFO_UPGRADE_TASK_DELETE_FILE.get(file);
        ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, msg, 0);
        context.notifyProgress(pnc);
        try
        {
          FileManager.deleteRecursively(file);
          context.notifyProgress(pnc.setProgress(100));
        }
        catch (Exception e)
        {
          throw unexpectedException(context, pnc, LocalizableMessage.raw(e.getMessage()));
        }
      }

      @Override
      public String toString()
      {
        return INFO_UPGRADE_TASK_DELETE_FILE.get(file).toString();
      }
    };
  }

  /**
   * Creates an upgrade task which is responsible for preparing local-db backend JE databases for a full rebuild once
   * they have been converted to pluggable JE backends.
   *
   * @return An upgrade task which is responsible for preparing local-db backend JE databases.
   */
  static UpgradeTask migrateLocalDBBackendsToJEBackends() {
    return new AbstractUpgradeTask() {
      /** Properties of JE backends to be migrated. */
      class Backend {
        final String id;
        final boolean isEnabled;
        final Set<DN> baseDNs;
        final File envDir;
        final Map<String, String> renamedDbs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        private Backend(Entry config) {
          id = config.parseAttribute("ds-cfg-backend-id").asString();
          isEnabled = config.parseAttribute("ds-cfg-enabled").asBoolean(false);
          baseDNs = config.parseAttribute("ds-cfg-base-dn").asSetOfDN();
          String dbDirectory = config.parseAttribute("ds-cfg-db-directory").asString();
          File backendParentDirectory = new File(dbDirectory);
          if (!backendParentDirectory.isAbsolute()) {
            backendParentDirectory = new File(getInstancePath(), dbDirectory);
          }
          envDir = new File(backendParentDirectory, id);
          for (String db : Arrays.asList("compressed_attributes", "compressed_object_classes")) {
            renamedDbs.put(db, new TreeName("compressed_schema", db).toString());
          }
          for (DN baseDN : baseDNs) {
            renamedDbs.put(oldName(baseDN), newName(baseDN));
          }
        }
      }

      private final List<Backend> backends = new LinkedList<>();

      /**
       * Finds all the existing JE backends and determines if they can be migrated or not. It will not be possible to
       * migrate a JE backend if the id2entry database name cannot easily be determined, which may happen because
       * matching rules have changed significantly in 3.0.0.
       */
      @Override
      public void prepare(final UpgradeContext context) throws ClientException {
        // Requires answer from the user.
        if (context.confirmYN(INFO_UPGRADE_TASK_MIGRATE_JE_DESCRIPTION.get(), NO) != YES) {
          throw new ClientException(ReturnCode.ERROR_USER_CANCELLED,
                                    INFO_UPGRADE_TASK_MIGRATE_JE_CANCELLED.get());
        }

        final SearchRequest sr = Requests.newSearchRequest("", SearchScope.WHOLE_SUBTREE,
                                                           "(objectclass=ds-cfg-local-db-backend)");
        try (final EntryReader entryReader = searchConfigFile(sr)) {
          // Abort the upgrade if there are JE backends but no JE library.
          if (entryReader.hasNext() && !isJeLibraryAvailable()) {
            throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, INFO_UPGRADE_TASK_MIGRATE_JE_NO_JE_LIB.get());
          }
          while (entryReader.hasNext()) {
            Backend backend = new Backend(entryReader.readEntry());
            if (backend.isEnabled) {
              abortIfBackendCannotBeMigrated(backend);
            }
            backends.add(backend);
          }
        } catch (IOException e) {
          throw new ClientException(ReturnCode.APPLICATION_ERROR, INFO_UPGRADE_TASK_MIGRATE_CONFIG_READ_FAIL.get(), e);
        }
      }

      private void abortIfBackendCannotBeMigrated(final Backend backend) throws ClientException {
        Set<String> existingDatabases = JEHelper.listDatabases(backend.envDir);
        for (DN baseDN : backend.baseDNs) {
          final String oldName = oldName(baseDN);
          if (!existingDatabases.contains(oldName)) {
            LocalizableMessage msg = INFO_UPGRADE_TASK_MIGRATE_JE_UGLY_DN.get(backend.id, baseDN);
            throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, msg);
          }
        }
      }

      /**
       * Renames the compressed schema indexes and id2entry in a 2.x environment to
       * the naming scheme used in 3.0.0. Before 3.0.0 JE databases were named as follows:
       *
       * 1) normalize the base DN
       * 2) replace all non-alphanumeric characters with '_'
       * 3) append '_'
       * 4) append the index name.
       *
       * For example, id2entry in the base DN dc=white space,dc=com would be named
       * dc_white_space_dc_com_id2entry. In 3.0.0 JE databases are named as follows:
       *
       * 1) normalize the base DN and URL encode it (' '  are converted to %20)
       * 2) format as '/' + URL encoded base DN + '/' + index name.
       *
       * The matching rules in 3.0.0 are not compatible with previous versions, so we need
       * to do a best effort attempt to figure out the old database name from a given base DN.
       */
      @Override
      public void perform(final UpgradeContext context) throws ClientException {
        if (!isJeLibraryAvailable()) {
          return;
        }

        for (Backend backend : backends) {
          if (backend.isEnabled) {
            ProgressNotificationCallback pnc = new ProgressNotificationCallback(
                    INFORMATION, INFO_UPGRADE_TASK_MIGRATE_JE_SUMMARY_1.get(backend.id), 0);
            context.notifyProgress(pnc);
            try {
              JEHelper.migrateDatabases(backend.envDir, backend.renamedDbs);
              context.notifyProgress(pnc.setProgress(100));
            } catch (ClientException e) {
              throw unexpectedException(context, pnc, e.getMessageObject());
            }
          } else {
            // Skip backends which have been disabled.
            final ProgressNotificationCallback pnc = new ProgressNotificationCallback(
                    INFORMATION, INFO_UPGRADE_TASK_MIGRATE_JE_SUMMARY_5.get(backend.id), 0);
            context.notifyProgress(pnc);
            context.notifyProgress(pnc.setProgress(100));
          }
        }
      }

      private boolean isJeLibraryAvailable() {
        return isClassAvailable("com.sleepycat.je.Environment");
      }

      private String newName(final DN baseDN) {
        return new TreeName(baseDN.toNormalizedUrlSafeString(), "id2entry").toString();
      }

      private String oldName(final DN baseDN) {
        String s = baseDN.toString();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
          char c = s.charAt(i);
          builder.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        builder.append("_id2entry");
        return builder.toString();
      }

      @Override
      public String toString()
      {
        return INFO_UPGRADE_TASK_MIGRATE_JE_SUMMARY_1.get("%s").toString();
      }
    };
  }

  /**
   * Creates backups of the local DB backends directories by renaming adding them a ".bak" suffix.
   * e.g "userRoot" would become "userRoot.bak"
   *
   * @param backendObjectClass
   *          The backend object class name.
   */
  static UpgradeTask renameLocalDBBackendDirectories(final String backendObjectClass)
  {
    return new AbstractUpgradeTask()
    {
      private boolean reimportRequired;

      @Override
      public void perform(UpgradeContext context) throws ClientException
      {
        try
        {
          Filter filter = Filter.equality("objectclass", backendObjectClass);
          SearchRequest findLocalDBBackends = Requests.newSearchRequest(DN.rootDN(), SearchScope.WHOLE_SUBTREE, filter);
          try (final EntryReader jeBackends = searchConfigFile(findLocalDBBackends))
          {
            while (jeBackends.hasNext())
            {
              Upgrade.needToRunPostUpgradePhase();
              reimportRequired = true;

              Entry jeBackend = jeBackends.readEntry();
              File dbParent = UpgradeUtils.getFileForPath(jeBackend.parseAttribute("ds-cfg-db-directory").asString());
              String id = jeBackend.parseAttribute("ds-cfg-backend-id").asString();

              // Use canonical paths so that the progress message is more readable.
              File dbDirectory = new File(dbParent, id).getCanonicalFile();
              File dbDirectoryBackup = new File(dbParent, id + ".bak").getCanonicalFile();
              if (dbDirectory.exists() && !dbDirectoryBackup.exists())
              {
                LocalizableMessage msg = INFO_UPGRADE_TASK_RENAME_JE_DB_DIR.get(dbDirectory, dbDirectoryBackup);
                ProgressNotificationCallback pnc = new ProgressNotificationCallback(0, msg, 0);
                context.notifyProgress(pnc);
                boolean renameSucceeded = dbDirectory.renameTo(dbDirectoryBackup);
                context.notifyProgress(pnc.setProgress(renameSucceeded ? 100 : -1));
              }
            }
          }
        }
        catch (Exception e)
        {
          logger.error(LocalizableMessage.raw(e.getMessage()));
        }
      }

      @Override
      public void postUpgrade(UpgradeContext context) throws ClientException
      {
        postponePostUpgrade(context);
      }

      @Override
      public void postponePostUpgrade(UpgradeContext context) throws ClientException
      {
        if (reimportRequired)
        {
          context.notify(INFO_UPGRADE_TASK_RENAME_JE_DB_DIR_WARNING.get(), TextOutputCallback.WARNING);
        }
      }

      @Override
      public String toString()
      {
        return INFO_UPGRADE_TASK_RENAME_JE_DB_DIR.get("%s", "%s").toString();
      }
    };
  }

  /** This inner classes causes JE to be lazily linked and prevents runtime errors if JE is not in the classpath. */
  private static final class JEHelper {
    private static ClientException clientException(final File backendDirectory, final DatabaseException e) {
      logger.error(LocalizableMessage.raw(StaticUtils.stackTraceToString(e)));
      return new ClientException(ReturnCode.CONSTRAINT_VIOLATION,
                                 INFO_UPGRADE_TASK_MIGRATE_JE_ENV_UNREADABLE.get(backendDirectory), e);
    }

    private static Set<String> listDatabases(final File backendDirectory) throws ClientException {
      try (Environment je = new Environment(backendDirectory, null)) {
        Set<String> databases = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        databases.addAll(je.getDatabaseNames());
        return databases;
      } catch (DatabaseException e) {
        throw clientException(backendDirectory, e);
      }
    }

    private static void migrateDatabases(final File envDir, final Map<String, String> renamedDbs)
          throws ClientException {
      EnvironmentConfig config = new EnvironmentConfig().setTransactional(true).setConfigParam("je.freeDisk",String.valueOf(50*1024*1024));
      try (Environment je = new Environment(envDir, config)) {
        final Transaction txn = je.beginTransaction(null, new TransactionConfig());
        try {
          for (String dbName : je.getDatabaseNames()) {
            String newDbName = renamedDbs.get(dbName);
            if (newDbName != null) {
              // id2entry or compressed schema should be kept
              je.renameDatabase(txn, dbName, newDbName);
            } else {
              // This index will need rebuilding
              je.removeDatabase(txn, dbName);
            }
          }
          txn.commit();
        } finally {
          txn.abort();
        }
      } catch (DatabaseException e) {
        throw JEHelper.clientException(envDir, e);
      }
    }
  }

  private static void displayChangeCount(final File configfile, final int changeCount)
  {
    String fileName = configfile.getAbsolutePath();
    if (changeCount != 0)
    {
      logger.debug(INFO_UPGRADE_CHANGE_DONE_IN_SPECIFIC_FILE, fileName, changeCount);
    }
    else
    {
      logger.debug(INFO_UPGRADE_NO_CHANGE_DONE_IN_SPECIFIC_FILE, fileName);
    }
  }

  private static void displayTaskLogInformation(final LocalizableMessage summary,
      final String filter, final String... ldif)
  {
    logger.debug(summary);
    if (filter != null)
    {
      logger.debug(LocalizableMessage.raw(filter));
    }
    if (ldif != null)
    {
      logger.debug(LocalizableMessage.raw(Arrays.toString(ldif)));
    }
  }

  private static ClientException unexpectedException(final UpgradeContext context,
      final ProgressNotificationCallback pnc, final LocalizableMessage message) throws ClientException
  {
    countErrors++;
    context.notifyProgress(pnc.setProgress(-100));
    return new ClientException(ReturnCode.ERROR_UNEXPECTED, message);
  }

  static void handleClientException(final UpgradeContext context, ClientException e) throws ClientException
  {
    logger.error(e.getMessageObject());
    if (!context.isIgnoreErrorsMode())
    {
      throw e;
    }
  }

  private static UpgradeTask updateConfigEntry(final LocalizableMessage summary, final String filter,
      final ChangeOperationType changeOperationType, final String... ldif)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        performConfigFileUpdate(summary, filter, changeOperationType, context, ldif);
      }

      @Override
      public String toString()
      {
        return String.valueOf(summary);
      }
    };
  }

  private static void performConfigFileUpdate(final LocalizableMessage summary, final String filter,
      final ChangeOperationType changeOperationType, final UpgradeContext context, final String... ldif)
      throws ClientException
  {
    displayTaskLogInformation(summary, filter, ldif);

    final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, summary, 20);
    context.notifyProgress(pnc);

    try
    {
      final Filter filterVal = filter != null ? Filter.valueOf(filter) : null;
      final int changeCount = updateConfigFile(configFile, filterVal, changeOperationType, ldif);
      displayChangeCount(configFile, changeCount);

      context.notifyProgress(pnc.setProgress(100));
    }
    catch (final Exception e)
    {
      throw unexpectedException(context, pnc, LocalizableMessage.raw(e.getMessage()));
    }
  }

  static UpgradeTask clearReplicationDbDirectory()
  {
    return new AbstractUpgradeTask()
    {
      private File replicationDbDir;

      @Override
      public void prepare(final UpgradeContext context) throws ClientException
      {
        String replDbDir = readReplicationDbDirFromConfig();
        if (replDbDir != null
            && context.confirmYN(INFO_UPGRADE_TASK_MIGRATE_CHANGELOG_DESCRIPTION.get(), NO) == YES)
        {
          replicationDbDir = new File(getInstancePath(), replDbDir).getAbsoluteFile();
        }
        // if replDbDir == null, then this is not an RS, there is no changelog DB to clear
      }

      private String readReplicationDbDirFromConfig() throws ClientException
      {
        final SearchRequest sr = Requests.newSearchRequest(
            DN.valueOf("cn=replication server,cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config"),
            SearchScope.BASE_OBJECT, Filter.alwaysTrue());
        try (final EntryReader entryReader = searchConfigFile(sr))
        {
          if (entryReader.hasNext())
          {
            final Entry replServerCfg = entryReader.readEntry();
            return replServerCfg.parseAttribute("ds-cfg-replication-db-directory").asString();
          }
          return null;
        }
        catch (IOException e)
        {
          LocalizableMessage msg = INFO_UPGRADE_TASK_MIGRATE_CONFIG_READ_FAIL.get();
          throw new ClientException(ReturnCode.APPLICATION_ERROR, msg, e);
        }
      }

      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        if (replicationDbDir == null)
        {
          // there is no changelog DB to clear
          return;
        }

        LocalizableMessage msg = INFO_UPGRADE_TASK_DELETE_CHANGELOG_SUMMARY.get(replicationDbDir);
        ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, msg, 0);
        context.notifyProgress(pnc);
        try
        {
          FileManager.deleteRecursively(replicationDbDir);
          context.notifyProgress(pnc.setProgress(100));
        }
        catch (ClientException e)
        {
          throw unexpectedException(context, pnc, e.getMessageObject());
        }
        catch (Exception e)
        {
          throw unexpectedException(context, pnc, LocalizableMessage.raw(e.getLocalizedMessage()));
        }
      }

      @Override
      public String toString()
      {
        return INFO_UPGRADE_TASK_DELETE_CHANGELOG_SUMMARY.get(replicationDbDir).toString();
      }
    };
  }

  /** Removes server and localized jars from previous version since names have changed. */
  static UpgradeTask removeOldJarFiles()
  {
    return new AbstractUpgradeTask()
    {

      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        final ProgressNotificationCallback pnc = new ProgressNotificationCallback(
            INFORMATION, INFO_UPGRADE_TASK_REMOVE_OLD_JARS.get(), 0);
        context.notifyProgress(pnc);

        final boolean fileSystemIsCaseSensitive = fileSystemIsCaseSensitive(context);

        deleteJarFilesIfFileSystemIsCaseSensitive(fileSystemIsCaseSensitive, "OpenDJ");
        for (final String locale : SUPPORTED_LOCALES_FOR_3_0_0)
        {
          deleteJarFiles("OpenDJ-" + locale);
          deleteJarFilesIfFileSystemIsCaseSensitive(fileSystemIsCaseSensitive, "OpenDJ_" + locale);
        }
        // Jar files from 2.6.x
        deleteJarFiles("jackson-core-asl", "jackson-mapper-asl", "json-fluent", "json-resource-servlet",
            "mail", "opendj-ldap-sdk", "opendj-rest2ldap-servlet", "opendj-server2x-adapter");
        context.notifyProgress(pnc.setProgress(100));
      }

      private void deleteJarFilesIfFileSystemIsCaseSensitive(
          final boolean fileSystemIsCaseSensitive, final String... jarFileNames)
      {
        if (fileSystemIsCaseSensitive)
        {
          deleteJarFiles(jarFileNames);
        }
      }

      private void deleteJarFiles(final String... jarFileNames)
      {
        for (final String jarFileName : jarFileNames)
        {
          deleteFileIfExists(new File(libDirectory, jarFileName + ".jar"));
        }
      }

      /** Used to know if we have to remove old "camel case" OpenDJ[_]*.jar(see OPENDJ-2692). */
      private boolean fileSystemIsCaseSensitive(final UpgradeContext context) throws ClientException
      {
        final File openDJCamelCaseJar = new File(libDirectory, "OpenDJ.jar");
        try
        {
          // getCanonicalPath() will return the new "opendj.jar" on case insensitive file systems
          return openDJCamelCaseJar.getCanonicalPath().equals(openDJCamelCaseJar.getAbsolutePath());
        }
        catch (final IOException unlikely)
        {
          // Warn the user that he may have some old camel case jars to remove
          context.notify(INFO_UPGRADE_TASK_UNABLE_TO_REMOVE_OLD_JARS.get());
          Upgrade.needToExitWithErrorCode();
          return false;
        }
      }
    };
  }

  /**
   * This task exists because OpenDJ 3.0.0 added an attribute type definition for
   * {@code ds-cfg-csv-delimiter-char}, but unfortunately trailing spaces existed after the closing
   * parenthesis. As a consequence, this definition was not added to the concatenated schema.
   * <p>
   * This task restores this definition in the concatenated schema using the following algorithm:
   * <p>
   * If {@code ds-cfg-csv-delimiter-char} attribute type definition exists in 02-config.ldif,
   * but not in the concatenated schema then append its definition to the concatenated schema,
   * omitting the trailing spaces.
   *
   * See OPENDJ-3081
   *
   * @return The relevant upgrade task
   */
  static UpgradeTask restoreCsvDelimiterAttributeTypeInConcatenatedSchemaFile()
  {
    return new AbstractUpgradeTask()
    {
      private boolean shouldRunTask;

      @Override
      public void prepare(UpgradeContext context) throws ClientException
      {
        shouldRunTask = concatenatedSchemaFile.exists();
      }

      @Override
      public void perform(UpgradeContext context) throws ClientException
      {
        if (!shouldRunTask)
        {
          return;
        }
        final ProgressNotificationCallback pnc = new ProgressNotificationCallback(INFORMATION, getSummary(), 0);

        final File configFile = new File(configSchemaDirectory, "02-config.ldif");
        AttributeType configCsvCharAT = readCsvDelimiterCharAttributeType(configFile, context, pnc);
        context.notifyProgress(pnc.setProgress(33));

        AttributeType concatenatedCsvCharAT = readCsvDelimiterCharAttributeType(concatenatedSchemaFile, context, pnc);
        context.notifyProgress(pnc.setProgress(66));

        if (!configCsvCharAT.isPlaceHolder() && concatenatedCsvCharAT.isPlaceHolder())
        {
          final String csvCharAttrTypeDefinition = configCsvCharAT.toString().trim();
          try (BufferedWriter writer = Files.newBufferedWriter(concatenatedSchemaFile.toPath(), UTF_8, APPEND))
          {
            writer.append(CoreSchema.getAttributeTypesAttributeType().getNameOrOID());
            writer.append(": ");
            writer.append(addSchemaFileToElementDefinitionIfAbsent(csvCharAttrTypeDefinition, "02-config.ldif"));
            writer.newLine();
          }
          catch (IOException e)
          {
            throw unexpectedException(context, pnc, INFO_UPGRADE_TASK_CANNOT_WRITE_TO_CONCATENATED_SCHEMA_FILE.get(
                concatenatedSchemaFile.toPath(), stackTraceToSingleLineString(e)));
          }
        }
        context.notifyProgress(pnc.setProgress(100));
      }

      private AttributeType readCsvDelimiterCharAttributeType(final File schemaFile,
          final UpgradeContext context, final ProgressNotificationCallback pnc) throws ClientException
      {
        final Schema coreSchema = Schema.getCoreSchema();
        try (EntryReader entryReader = new LDIFEntryReader(new FileReader(schemaFile)))
        {
          final Entry schemaEntry = entryReader.readEntry();
          final SchemaBuilder builder = new SchemaBuilder();
          for (Syntax syntax : coreSchema.getSyntaxes())
          {
            builder.buildSyntax(syntax).addToSchema();
          }
          for (MatchingRule rule : coreSchema.getMatchingRules())
          {
            builder.buildMatchingRule(rule).addToSchema();
          }
          return builder
              .addSchema(schemaEntry, false)
              .toSchema()
              .asNonStrictSchema()
              .getAttributeType("ds-cfg-csv-delimiter-char");
        }
        catch (IOException e)
        {
          throw unexpectedException(context, pnc, INFO_UPGRADE_TASK_CANNOT_READ_SCHEMA_FILE.get(
              schemaFile.getAbsolutePath(), stackTraceToSingleLineString(e)));
        }
      }

      private LocalizableMessage getSummary()
      {
        return INFO_UPGRADE_TASK_SUMMARY_RESTORE_CSV_DELIMITER_CHAR.get();
      }

      @Override
      public String toString()
      {
        return getSummary().toString();
      }
    };
  }

  /** Move subordinate-base-dn attribute from Root DSE config to Global config. */
  static UpgradeTask moveSubordinateBaseDnToGlobalConfiguration()
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(UpgradeContext context) throws ClientException
      {
        final SearchRequest sr = Requests.newSearchRequest("cn=Root DSE,cn=config", SearchScope.BASE_OBJECT,
                "(objectclass=ds-cfg-root-dse-backend)");
        final Set<String> subordinateDns = new HashSet<>();
        try (final EntryReader entryReader = searchConfigFile(sr))
        {
          if (!entryReader.hasNext())
          {
            return;
          }
          Entry entry = entryReader.readEntry();
          Attribute attribute = entry.getAttribute("ds-cfg-subordinate-base-dn");
          if (attribute == null || attribute.isEmpty())
          {
            return;
          }
          for (ByteString value : attribute)
          {
              subordinateDns.add(value.toString());
          }
        }
        catch (IOException e)
        {
          throw new ClientException(ReturnCode.APPLICATION_ERROR, INFO_UPGRADE_TASK_MIGRATE_CONFIG_READ_FAIL.get(), e);
        }
        modifyConfigEntry(INFO_UPGRADE_TASK_DELETE_SUBORDINATE_BASE_DN_FROM_ROOT_DSE.get(),
              "(objectClass=ds-cfg-root-dse-backend)",
              "delete: ds-cfg-subordinate-base-dn");
        List<String> ldif = new ArrayList<>();
        ldif.add("add: ds-cfg-subordinate-base-dn");
        for (String sub : subordinateDns)
        {
          ldif.add("ds-cfg-subordinate-base-dn: " + sub);
        }
        modifyConfigEntry(INFO_UPGRADE_TASK_ADD_SUBORDINATE_BASE_DN_TO_GLOBAL_CONFIG.get(),
            "(objectClass=ds-cfg-root-config)", ldif.toArray(new String[0]));
      }
    };
  }

  static UpgradeTask removeTools(final String... toolNames)
  {
    return new AbstractUpgradeTask() {
      @Override
      public void perform(final UpgradeContext context) throws ClientException {
        for (final String toolName : toolNames)
        {
          deleteFileIfExists(new File(binDirectory, toolName));
          deleteFileIfExists(new File(batDirectory, toolName +  ".bat"));
        }
      }
    };
  }

  /** Prevent instantiation. */
  private UpgradeTasks()
  {
    // Do nothing.
  }
}
