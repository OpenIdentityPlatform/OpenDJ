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
 *      Copyright 2013 ForgeRock AS
 */

package org.opends.server.tools.upgrade;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.OPTION_LONG_FORCE_UPGRADE;
import static org.opends.server.tools.ToolConstants.OPTION_LONG_NO_PROMPT;
import static org.opends.server.tools.upgrade.FileManager.copy;
import static org.opends.server.tools.upgrade.Upgrade.*;
import static org.opends.server.tools.upgrade.UpgradeUtils.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.callback.ConfirmationCallback;

import org.opends.messages.Message;
import org.opends.server.controls.PersistentSearchChangeType;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.tools.ClientException;
import org.opends.server.tools.upgrade.UpgradeTask.TaskType;
import org.opends.server.util.BuildVersion;

/**
 * Factory methods for create new upgrade tasks.
 */
public final class UpgradeTasks
{
  /**
   * An errors counter in case of ignore errors mode.
   */
  static int countErrors = 0;

  /**
   * Upgrade's logger.
   */
  static private final Logger LOG = Logger
      .getLogger(UpgradeCli.class.getName());

  /**
   * Returns a new upgrade task which applies an LDIF record to all
   * configuration entries matching the provided filter.
   *
   * @param summary
   *          The summary of this upgrade task.
   * @param ldif
   *          The LDIF record which will be applied to matching entries.
   * @return A new upgrade task which applies an LDIF record to all
   *         configuration entries matching the provided filter.
   */
  public static UpgradeTask addConfigEntry(final Message summary,
      final String... ldif)
  {
    return addConfigEntry0(summary, summary, false, ldif);
  }

  /**
   * Returns a new upgrade task which applies an LDIF record to all
   * configuration entries matching the provided filter.
   *
   * @param summary
   *          The summary of this upgrade task.
   * @param description
   *          The detailed description of this upgrade task.
   * @param ldif
   *          The LDIF record which will be applied to matching entries.
   * @return A new upgrade task which applies an LDIF record to all
   *         configuration entries matching the provided filter.
   */
  public static UpgradeTask addConfigEntryOptional(final Message summary,
      final Message description, final String... ldif)
  {
    return addConfigEntry0(summary, description, true, ldif);
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
  public static UpgradeTask copySchemaFile(final String fileName)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        final Message msg = INFO_UPGRADE_TASK_REPLACE_SCHEMA_FILE.get(fileName);
        LOG.log(Level.INFO, msg.toString());

        final ProgressNotificationCallback pnc =
            new ProgressNotificationCallback(0, msg, 0);

        final File schemaFileTemplate =
            new File(templateConfigSchemaDirectory, fileName);

        try
        {
          context.notifyProgress(pnc.changeProgress(20));

          copy(schemaFileTemplate, configSchemaDirectory, true);
          context.notifyProgress(pnc.changeProgress(100));
        }
        catch (final IOException e)
        {
          manageTaskException(context, ERR_UPGRADE_COPYSCHEMA_FAILS.get(
              schemaFileTemplate.getName(), e.getMessage()), pnc);
        }
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
  public static UpgradeTask addConfigFile(final String fileName)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        final Message msg = INFO_UPGRADE_TASK_ADD_CONFIG_FILE.get(fileName);
        LOG.log(Level.INFO, msg.toString());

        final ProgressNotificationCallback pnc =
            new ProgressNotificationCallback(0, msg, 0);

        final File configFile = new File(templateConfigDirectory, fileName);

        try
        {
          context.notifyProgress(pnc.changeProgress(20));

          copy(configFile, configDirectory, true);
          context.notifyProgress(pnc.changeProgress(100));
        }
        catch (final IOException e)
        {
          manageTaskException(context, ERR_UPGRADE_ADD_CONFIG_FILE_FAILS.get(
              configFile.getName(), e.getMessage()), pnc);
        }
      }
    };
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
  public static UpgradeTask modifyConfigEntry(final Message summary,
      final String filter, final String... ldif)
  {
    return modifyConfigEntry(summary, summary, false, filter, ldif);
  }

  /**
   * Returns a new upgrade task which applies an LDIF record to all
   * configuration entries matching the provided filter.
   *
   * @param summary
   *          The summary of this upgrade task.
   * @param description
   *          The detailed description of this upgrade task.
   * @param filter
   *          The LDAP filter which configuration entries must match.
   * @param ldif
   *          The LDIF record which will be applied to matching entries.
   * @return A new upgrade task which applies an LDIF record to all
   *         configuration entries matching the provided filter.
   */
  public static UpgradeTask modifyConfigEntryOptional(final Message summary,
      final Message description, final String filter, final String... ldif)
  {
    return modifyConfigEntry(summary, description, true, filter, ldif);
  }

  /**
   * This task adds a new attribute type (must exists in the original file) to
   * the specified file placed in parameter. The destination must be a file
   * contained in the config/schema folder. E.g : This example adds a new
   * attribute type named 'etag' in the 00.core.ldif. The 'etag' attribute
   * already exists in the 00-core.ldif template schema file.
   *
   * <pre>
   * register(&quot;2.5.0.7192&quot;,
   *   newAttributeTypes(Message.raw(&quot;New attribute etag&quot;),
   *   false, &quot;00-core.ldif&quot;, &quot;etag&quot;));
   * </pre>
   *
   * @param summary
   *          The summary of the task.
   * @param fileName
   *          The file where to add the new attribute types. This file must be
   *          contained in the config/schema folder.
   * @param names
   *          The names of the new attributes to add to.
   * @return An upgrade task which adds new attribute types, defined previously
   *         in the config template files, reads the definition and adds it onto
   *         the specified file in parameter.
   */
  public static UpgradeTask newAttributeTypes(final Message summary,
      final String fileName, final String... names)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        LOG.log(Level.INFO, summary.toString());

        final ProgressNotificationCallback pnc =
            new ProgressNotificationCallback(0, summary, 20);
        context.notifyProgress(pnc);

        final File schemaFileTemplate =
            new File(templateConfigSchemaDirectory, fileName);

        final File pathDestination = new File(configSchemaDirectory, fileName);
        try
        {
          final int changeCount =
              updateSchemaFile(schemaFileTemplate, pathDestination,
                  names, null);

          displayChangeCount(pathDestination.getPath(), changeCount);

          context.notifyProgress(pnc.changeProgress(100));
        }
        catch (final IOException e)
        {
          manageTaskException(context, ERR_UPGRADE_ADDATTRIBUTE_FAILS.get(
              schemaFileTemplate.getName(), e.getMessage()), pnc);
        }
      }
    };
  }

  /**
   * This task adds a new object class (must exists in the original file) to the
   * specified file placed in parameter. The destination must be a file
   * contained in the config/schema folder.
   *
   * @param summary
   *          The summary of the task.
   * @param fileName
   *          The file where to add the new object classes. This file must be
   *          contained in the config/schema folder.
   * @param names
   *          The names of the new object classes to add to.
   * @return An upgrade task which adds new object classes, defined previously
   *         in the config template files, reads the definition and adds it onto
   *         the specified file in parameter.
   */
  public static UpgradeTask newObjectClasses(final Message summary,
      final String fileName, final String... names)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        LOG.log(Level.INFO, summary.toString());

        final ProgressNotificationCallback pnc =
            new ProgressNotificationCallback(0, summary, 20);
        context.notifyProgress(pnc);

        final File schemaFileTemplate =
            new File(templateConfigSchemaDirectory, fileName);

        final File pathDestination = new File(configSchemaDirectory, fileName);

        context.notifyProgress(pnc.changeProgress(20));

        try
        {
          final int changeCount =
              updateSchemaFile(schemaFileTemplate, pathDestination,
                  null, names);

          displayChangeCount(pathDestination.getPath(), changeCount);

          context.notifyProgress(pnc.changeProgress(100));
        }
        catch (final IOException e)
        {
          manageTaskException(context, ERR_UPGRADE_ADDOBJECTCLASS_FAILS.get(
              schemaFileTemplate.getName(), e.getMessage()), pnc);
        }
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
  public static UpgradeTask regressionInVersion(final String versionString,
      final UpgradeTask... tasks)
  {
    final BuildVersion version = BuildVersion.valueOf(versionString);
    return new AbstractUpgradeTask()
    {

      @Override
      public void verify(UpgradeContext context) throws ClientException
      {
        if (currentVersionEqualToOrMoreRecentThan(context, version))
        {
          for (UpgradeTask task : tasks)
          {
            task.verify(context);
          }
        }
      }

      @Override
      public void interact(UpgradeContext context) throws ClientException
      {
        if (currentVersionEqualToOrMoreRecentThan(context, version))
        {
          for (UpgradeTask task : tasks)
          {
            task.interact(context);
          }
        }
      }

      @Override
      public void start(UpgradeContext context) throws ClientException
      {
        if (currentVersionEqualToOrMoreRecentThan(context, version))
        {
          for (UpgradeTask task : tasks)
          {
            task.start(context);
          }
        }
      }

      @Override
      public void perform(UpgradeContext context) throws ClientException
      {
        if (currentVersionEqualToOrMoreRecentThan(context, version))
        {
          for (UpgradeTask task : tasks)
          {
            task.perform(context);
          }
        }
      }

      @Override
      public void end(UpgradeContext context) throws ClientException
      {
        if (currentVersionEqualToOrMoreRecentThan(context, version))
        {
          for (UpgradeTask task : tasks)
          {
            task.end(context);
          }
        }
      }



      private boolean currentVersionEqualToOrMoreRecentThan(
          UpgradeContext context, final BuildVersion version)
      {
        return context.getFromVersion().compareTo(version) >= 0;
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
  public static UpgradeTask rebuildAllIndexes(final Message summary)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        // TODO
      }

      @Override
      public void start(final UpgradeContext context) throws ClientException
      {
        context.notify(summary);
      }

      @Override
      public void verify(final UpgradeContext context) throws ClientException
      {
        verifyTaskType(TaskType.MANDATORY_USER_INTERACTION, context);
      }
    };
  }



  /**
   * Creates a rebuild index task for a single index. At the moment this is
   * implemented as a simple stub which displays a message which should prompt
   * the user to rebuild the index manually once the upgrade has completed.
   * <p>
   * In future this task should register the index to be rebuilt in a table. A
   * subsequent task executed at the end of the upgrade process will then obtain
   * the set of indexes to be rebuilt, optimize it (e.g. removing duplicates),
   * and perform the rebuild.
   *
   * @param summary
   *          A message describing why the index needs to be rebuilt and asking
   *          them whether or not they wish to continue.
   * @return The rebuild index task.
   */
  public static UpgradeTask rebuildSingleIndex(final Message summary)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void verify(final UpgradeContext context) throws ClientException
      {
        verifyTaskType(TaskType.MANDATORY_USER_INTERACTION, context);
      }

      @Override
      public void interact(UpgradeContext context) throws ClientException
      {
        // Require acknowledgment from the user.
        final int answer = context.confirmYN(summary, ConfirmationCallback.NO);

        // The user refused to perform this task.
        if (answer == ConfirmationCallback.NO)
        {
          throw new ClientException(EXIT_CODE_MANUAL_INTERVENTION,
              INFO_UPGRADE_ABORTED_BY_USER.get());
        }
      }

      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        // TODO: automatic rebuild is not supported yet.
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
  public static UpgradeTask updateConfigUpgradeFolder()
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        final Message msg = INFO_UPGRADE_TASK_REFRESH_UPGRADE_DIRECTORY.get();
        LOG.log(Level.INFO, msg.toString());

        final ProgressNotificationCallback pnc =
            new ProgressNotificationCallback(0, msg, 20);
        context.notifyProgress(pnc);

        try
        {
          updateConfigUpgradeSchemaFile(configSchemaDirectory, String
              .valueOf(context.getToVersion().getRevisionNumber()));

          context.notifyProgress(pnc.changeProgress(100));
        }
        catch (final Exception ex)
        {
          manageTaskException(context, ERR_UPGRADE_CONFIG_ERROR_UPGRADE_FOLDER
              .get(ex.getMessage()), pnc);
        }
      }
    };
  }

  /**
   * Renames the SNMP security config file if it exists. Since 2.5.0.7466 this
   * file has been renamed.
   *
   * @param summary
   *          The summary of this upgrade task.
   * @return An upgrade task which renames the old SNMP security config file if
   *         it exists.
   */
  public static UpgradeTask renameSnmpSecurityConfig(final Message summary)
  {
    return new AbstractUpgradeTask()
    {
      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        /*
         * Snmp config file contains old name in old version(like 2.4.5), in
         * order to make sure the process will still work after upgrade, we need
         * to rename it - only if it exists.
         */
        if (UpgradeUtils.configSnmpSecurityDirectory.exists())
        {
          ProgressNotificationCallback pnc =
              new ProgressNotificationCallback(0, summary, 0);
          try
          {
            final File oldSnmpConfig =
                new File(UpgradeUtils.configSnmpSecurityDirectory
                    + File.separator + "opends-snmp.security");
            if (oldSnmpConfig.exists())
            {
              context.notifyProgress(pnc.changeProgress(20));
              LOG.log(Level.INFO, summary.toString());

              final File snmpConfig =
                  new File(UpgradeUtils.configSnmpSecurityDirectory
                      + File.separator + "opendj-snmp.security");

              FileManager.rename(oldSnmpConfig, snmpConfig);

              context.notifyProgress(pnc.changeProgress(100));
            }
          }
          catch (final Exception ex)
          {
            manageTaskException(context,
                ERR_UPGRADE_RENAME_SNMP_SECURITY_CONFIG_FILE.get(ex
                    .getMessage()), pnc);
          }
        }
      }
    };
  }

  private static UpgradeTask addConfigEntry0(final Message summary,
      final Message description, final boolean needsUserConfirmation,
      final String... ldif)
  {
    return new AbstractUpgradeTask()
    {
      private boolean userConfirmation = true;

      @Override
      public void interact(final UpgradeContext context) throws ClientException
      {
        if (needsUserConfirmation)
        {
          // Process needs to have user's response to perform the current
          // modification.
          final int answer =
              context.confirmYN(INFO_UPGRADE_TASK_NEEDS_USER_CONFIRM
                  .get(description), ConfirmationCallback.YES);

          // The user refuses to perform this task.
          if (answer == ConfirmationCallback.NO)
          {
            userConfirmation = false;
          }
        }
      }

      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        if (userConfirmation)
        {
          displayTaskLogInformation(summary.toString(), null, ldif);

          final ProgressNotificationCallback pnc =
              new ProgressNotificationCallback(0, summary, 20);

          context.notifyProgress(pnc);

          try
          {
            // TODO change the directory to the config if it exists.
            final File configFile =
                new File(configDirectory,
                    Installation.CURRENT_CONFIG_FILE_NAME);

            final int changeCount =
                updateConfigFile(configFile.getPath(), null,
                    PersistentSearchChangeType.ADD, ldif);

            displayChangeCount(configFile.getPath(), changeCount);

            context.notifyProgress(pnc.changeProgress(100));
          }
          catch (final Exception e)
          {
            manageTaskException(context, Message.fromObject(e.getMessage()),
                pnc);
          }
        }
      }
    };
  }

  private static void displayChangeCount(final String fileName,
      final int changeCount)
  {
    if (changeCount != 0)
    {
      LOG.log(Level.INFO, INFO_UPGRADE_CHANGE_DONE_IN_SPECIFIC_FILE.get(
          fileName, String.valueOf(changeCount)).toString());
    }
    else
    {
      LOG.log(Level.INFO, INFO_UPGRADE_NO_CHANGE_DONE_IN_SPECIFIC_FILE.get(
          fileName).toString());
    }
  }

  private static void displayTaskLogInformation(final String summary,
      final String filter, final String... ldif)
  {
    LOG.log(Level.INFO, summary);
    if (filter != null)
    {
      LOG.log(Level.INFO, filter.toString());
    }
    if (ldif != null)
    {
      LOG.log(Level.INFO, Arrays.asList(ldif).toString());
    }
  }

  private static void manageTaskException(final UpgradeContext context,
      final Message message, final ProgressNotificationCallback pnc)
      throws ClientException
  {
    countErrors++;
    context.notifyProgress(pnc.changeProgress(-100));
    LOG.log(Level.SEVERE, message.toString());
    if (!context.isIgnoreErrorsMode())
    {
      throw new ClientException(EXIT_CODE_ERROR, message);
    }
  }

  private static UpgradeTask modifyConfigEntry(final Message summary,
      final Message description, final boolean needsUserConfirmation,
      final String filter, final String... ldif)
  {
    return new AbstractUpgradeTask()
    {
      private boolean userConfirmation = true;

      @Override
      public void interact(final UpgradeContext context) throws ClientException
      {
        if (needsUserConfirmation)
        {
          // Process needs to have user's response to perform the current
          // modification.
          final int answer =
              context.confirmYN(INFO_UPGRADE_TASK_NEEDS_USER_CONFIRM
                  .get(description), ConfirmationCallback.YES);

          // The user refuses to perform this task.
          if (answer == ConfirmationCallback.NO)
          {
            userConfirmation = false;
          }
        }
      }

      @Override
      public void perform(final UpgradeContext context) throws ClientException
      {
        if (userConfirmation)
        {
          displayTaskLogInformation(summary.toString(), filter, ldif);

          final ProgressNotificationCallback pnc =
              new ProgressNotificationCallback(0, summary, 20);

          context.notifyProgress(pnc);

          try
          {
            final File configFile =
                new File(configDirectory,
                    Installation.CURRENT_CONFIG_FILE_NAME);

            final int changeCount =
                updateConfigFile(configFile.getPath(), LDAPFilter
                    .decode(filter), PersistentSearchChangeType.MODIFY, ldif);

            displayChangeCount(configFile.getPath(), changeCount);

            context.notifyProgress(pnc.changeProgress(100));
          }
          catch (final Exception e)
          {
            manageTaskException(context, Message.fromObject(e.getMessage()),
                pnc);
          }
        }
      }
    };
  }

  @SuppressWarnings("fallthrough")
  private static void verifyTaskType(final TaskType type,
      final UpgradeContext context) throws ClientException
  {
    /*
     * Checks CLI/GUI options via context. The process will stop
     * if user has selected conflicting options.
     */
    switch (type)
    {
    case NEED_USER_INTERACTION:
    {
      // Nothing to do.
      break;
    }
    case MANDATORY_USER_INTERACTION:
    case TAKE_LONG_TIME_TO_COMPLETE:
    case CANNOT_BE_REVERTED:
      // The option is not present ? Stops the process.
      if (!context.isInteractiveMode() && !context.isForceUpgradeMode())
      {
        context
            .notify(Message.raw(" "), FormattedNotificationCallback.BREAKLINE);
        context.notify(ERR_UPGRADE_USER_INTERACTION_REQUIRED.get(
            OPTION_LONG_NO_PROMPT, OPTION_LONG_FORCE_UPGRADE),
            FormattedNotificationCallback.NOTICE_CALLBACK);
        throw new ClientException(EXIT_CODE_MANUAL_INTERVENTION,
            ERR_UPGRADE_INVALID_USER_OPTIONS_SELECTED.get());
      }
    default:
      break;
    }
  }

  // Prevent instantiation.
  private UpgradeTasks()
  {
    // Do nothing.
  }
}
