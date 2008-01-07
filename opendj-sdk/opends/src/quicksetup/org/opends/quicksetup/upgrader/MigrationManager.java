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
 *      Portions Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.upgrader;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.UserInteraction;
import org.opends.quicksetup.Constants;

import org.opends.quicksetup.util.ExternalTools;
import org.opends.quicksetup.util.FileManager;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.OperationOutput;
import org.opends.quicksetup.util.InProcessServerController;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.ChangeRecordEntry;
import org.opends.server.types.LDIFImportConfig;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class handles migration of both schema and configuration
 * for an installation that is in the process of being upgraded.
 *
 * This class is intented to be used in two differnt stages of the
 * upgrade process:  first, before installation's filesystem bits
 * have been upgraded, a call to both {@link #calculateConfigCustomizations()}
 * and {@link #calculateSchemaCustomizations()} should be made in
 * order to capture what changes will need to be made to the server
 * later on.  Then once the filesystem bit have been upgraded calls to
 * {@link #migrateConfiguration()} and {@link #migrateSchema()} are
 * made to actually perform the migration.
 */
public class MigrationManager {

  static private final Logger LOG =
          Logger.getLogger(MigrationManager.class.getName());

  /** Describes a particular component to be migrated. */
  private enum Component { SCHEMA, CONFIGURATION }

  private Installation installation;
  private File backupDir;
  private UserInteraction ui;
  private boolean isSchemaCustomized;

  /**
   * Creates a new parameterized instance.
   * @param installation describing the file layout of the installation
   *        to be migrated
   * @param backupDir directory where diffs between the base configuration
   *        and schema are to be stored
   * @param ui user interaction allowing this class to prompt the user
   *        for feedback if necessary.  This object can be null in which
   *        case this class will throw an exception rather than prompt
   *        the user for more information if necessary.
   */
  public MigrationManager(Installation installation, File backupDir,
                          UserInteraction ui)
  {
    if (installation == null) {
      throw new NullPointerException("installation cannot be null");
    }
    if (backupDir == null) {
      throw new NullPointerException("backup directory cannot be null");
    }
    this.installation = installation;
    this.backupDir = backupDir;
    this.ui = ui;
  }

  /**
   * Diffs the current and base schema for the
   * <code>Installation</code> associated with this class and
   * generates a diff file that can later be used to migrate
   * an upgraded server.
   *
   * @throws ApplicationException if there is a problem performing
   *         the diff operation
   */
  public void calculateSchemaCustomizations() throws ApplicationException {
    isSchemaCustomized = false;
    if (installation.getStatus().schemaHasBeenModified()) {
      LOG.log(Level.INFO, "Schema contains customizations that will " +
              "be migrated");
      try {
        ldifDiff(installation.getBaseSchemaFile(),
                installation.getSchemaConcatFile(),
                getCustomSchemaDiffFile());
        isSchemaCustomized = true;
      } catch (ApplicationException ae) {
        throw ae;
      } catch (Exception e) {
        throw ApplicationException.createFileSystemException(
                INFO_ERROR_DETERMINING_CUSTOM_SCHEMA.get(), e);
      }
    } else {
      LOG.log(Level.INFO, "No schema customizations to migrate");
    }
  }

  /**
   * Diffs the current and base configuration for the
   * <code>Installation</code> associated with this class and
   * generates a diff file that can later be used to migrate
   * an upgraded server.
   *
   * @throws ApplicationException if there is a problem performing
   *         the diff operation
   */
  public void calculateConfigCustomizations() throws ApplicationException {
    try {
      if (installation.getCurrentConfiguration().hasBeenModified()) {
        LOG.log(Level.INFO, "Configuration contains customizations that will " +
                "be migrated");
        try {
          ldifDiff(installation.getBaseConfigurationFile(),
                  installation.getCurrentConfigurationFile(),
                  getCustomConfigDiffFile());
        } catch (ApplicationException ae) {
          throw ae;
        } catch (Exception e) {
          throw ApplicationException.createFileSystemException(
                  INFO_ERROR_DETERMINING_CUSTOM_CONFIG.get(), e);
        }
      } else {
        LOG.log(Level.INFO, "No configuration customizations to migrate");
      }
    } catch (IOException e) {
      throw ApplicationException.createFileSystemException(
              INFO_ERROR_DETERMINING_CUSTOM_CONFIG.get(), e);
    }
  }

  /**
   * Migrates a configuration using the diff file generated by a call
   * to {@link MigrationManager#calculateConfigCustomizations()}.
   * @throws ApplicationException if there is an error migrating the
   *         configuration
   */
  public void migrateConfiguration() throws ApplicationException {
    try {
      File configDiff = getCustomConfigDiffFile();
      if (configDiff.exists()) {
        modify(configDiff, Component.CONFIGURATION);
      }
    } catch (ApplicationException ae) {
      throw ae;
    } catch (Exception e) {
      Message msg = INFO_ERROR_APPLYING_CUSTOM_CONFIG.get();
      LOG.log(Level.INFO, msg.toString(), e);
      throw new ApplicationException(
          ReturnCode.IMPORT_ERROR, msg, e);
    }
  }

  /**
   * Tells whether the ADS backend file must be migrated or not.
   * This method assumes that the ADS backend file in the original install
   * has not been overwritten.
   * @return <CODE>true</CODE> if the tools properties file must be migrated
   * and <CODE>false</CODE> otherwise.
   * @throws IOException if there is an error accessing the installation
   * information.
   */
  public boolean mustMigrateADS() throws IOException {
    return !installation.getADSBackendFile().exists();
  }

  /**
   * Migrates the ADS backend file.
   * @param newADSFile the new ADS backend file.
   * @throws ApplicationException if there is an error copying the new
   * ADS backend file.
   */
  public void migrateADS(File newADSFile) throws ApplicationException
  {
    FileManager fileManager = new FileManager();
    fileManager.copy(newADSFile, installation.getConfigurationDirectory(),
        true);
  }


  /**
   * Tells whether the tools properties file must be migrated or not.
   * This method assumes that the tools properties file in the original install
   * has not been overwritten.
   * @return <CODE>true</CODE> if the tools properties file must be migrated
   * and <CODE>false</CODE> otherwise.
   */
  public boolean mustMigrateToolProperties()
  {
   return !installation.getToolsPropertiesFile().exists();
  }

  /**
   * Migrates the tools properties file.
   * @param newPropertiesFile the new properties file.
   * @throws ApplicationException if there is an error copying the new
   * properties file.
   */
  public void migrateToolPropertiesFile(File newPropertiesFile)
  throws ApplicationException
  {
    FileManager fileManager = new FileManager();
    fileManager.copy(newPropertiesFile,
        installation.getConfigurationDirectory(), true);
  }

  /**
   * Migrates a schema using the diff file generated by a call
   * to {@link MigrationManager#calculateSchemaCustomizations()}.
   * @throws ApplicationException if there is an error migrating the
   *         schema
   */
  public void migrateSchema() throws ApplicationException {
    try {
      File schemaDiff = getCustomSchemaDiffFile();
      if (schemaDiff.exists()) {
        modify(schemaDiff, Component.SCHEMA);
      }
    } catch (ApplicationException ae) {
      throw ae;
    } catch (Exception e) {
      Message msg = INFO_ERROR_APPLYING_CUSTOM_SCHEMA.get();
      LOG.log(Level.INFO, msg.toString(), e);
      throw new ApplicationException(
          ReturnCode.IMPORT_ERROR, msg, e);
    }
  }

  /**
   * Returns a value to indicate whether the installation associated
   * with this class contains schema customizations.  A call to
   * {@link #calculateSchemaCustomizations()} must first be made in
   * order for this value to be valid.
   *
   * @return boolean where true indicates schema customization
   */
  public boolean isSchemaCustomized() {
    return isSchemaCustomized;
  }

  /**
   * Returns a value to indicate whether the installation associated
   * with this class contains configuration customizations.  A call to
   * {@link #calculateConfigCustomizations()} must first be made in
   * order for this value to be valid.
   *
   * @return boolean where true indicates configuration customization
   * @throws java.io.IOException if there was a problem reading the
   *         current configuration file.
   */
  public boolean isConfigurationCustomized() throws IOException {
    return installation.getCurrentConfiguration().hasBeenModified();
  }


  /**
   * Applies modifications contained in an LDIF file to the server.
   *
   * @param ldifFile LDIF file to apply
   * @param component being modified
   * @throws Exception if something goes wrong. This may be an
   *         ApplicationException if the user cancels the application
   *         altogether or an LDIFException or IOException should other
   *         problems occur.
   */
  private void modify(File ldifFile, Component component)
          throws Exception {
    InProcessServerController ipsc =
            new InProcessServerController(installation);
    LDIFImportConfig importCfg =
            new LDIFImportConfig(
                    Utils.getPath(ldifFile));
    LDIFReader ldifReader =
            new LDIFReader(importCfg);
    ChangeRecordEntry cre = ldifReader.readChangeRecord(false);
    while (cre != null) {
      try {
        ipsc.modify(cre);
        cre = ldifReader.readChangeRecord(false);
      } catch (Exception e) {
        if (ui != null) {
          Message cancel = INFO_CANCEL_BUTTON_LABEL.get();
          Message cont = INFO_CONTINUE_BUTTON_LABEL.get();
          Message retry = INFO_RETRY_BUTTON_LABEL.get();
          Object r = ui.confirm(
                  getModificationErrorSummary(component),
                  getModificationErrorMessage(cre),
                  Message.raw(e.getLocalizedMessage()),
                  INFO_ERROR_UPGRADE_MIGRATION.get(),
                  UserInteraction.MessageType.ERROR,
                  new Message[]{cancel, cont, retry},
                  cancel, null);
          if (cont.equals(r)) {
            cre = ldifReader.readChangeRecord(false);
          } else if (retry.equals(r)) {
            // do nothing; will retry;
          } else {
            throw new ApplicationException(
                ReturnCode.CANCELLED,
                INFO_UPGRADE_CANCELED.get(), e);
          }
        } else {
          throw e;
        }
      }
    }
  }

  private Message getModificationErrorSummary(Component c) {
    Message summary;
    switch(c) {
      case SCHEMA:
        summary = INFO_ERROR_UPGRADE_MIGRATION_SCHEMA.get();
        break;
      case CONFIGURATION:
        summary = INFO_ERROR_UPGRADE_MIGRATION_CONFIG.get();
        break;
      default:
        summary = Message.EMPTY;
    }
    return summary;
  }

  private Message getModificationErrorMessage(ChangeRecordEntry cre) {
    MessageBuilder msg = new MessageBuilder();
    if (Utils.isCli())
    {
      msg.append(Constants.LINE_SEPARATOR);
    }
    else
    {
      msg.append(Constants.HTML_LINE_BREAK);
    }
    if (cre != null) {
      switch (cre.getChangeOperationType()) {
        case MODIFY:
          if (Utils.isCli())
          {
            msg.append(INFO_ERROR_UPGRADE_MIGRATION_MODIFY_CLI.get(
                cre.getDN().toNormalizedString()));
          }
          else
          {
            msg.append(INFO_ERROR_UPGRADE_MIGRATION_MODIFY.get(
                cre.getDN().toNormalizedString()));
          }
          break;
        case ADD:
          if (Utils.isCli())
          {
            msg.append(INFO_ERROR_UPGRADE_MIGRATION_ADD_CLI.get(
                cre.getDN().toNormalizedString()));
          }
          else
          {
            msg.append(INFO_ERROR_UPGRADE_MIGRATION_ADD.get(
                cre.getDN().toNormalizedString()));
          }
          break;
        case DELETE:
          if (Utils.isCli())
          {
            msg.append(INFO_ERROR_UPGRADE_MIGRATION_DELETE_CLI.get(
                cre.getDN().toNormalizedString()));
          }
          else
          {
            msg.append(INFO_ERROR_UPGRADE_MIGRATION_DELETE.get(
                cre.getDN().toNormalizedString()));
          }
          break;
        default:
          LOG.log(Level.INFO, "Unexpected change operation type " +
              cre.getChangeOperationType());
        if (Utils.isCli())
        {
          msg.append(INFO_ERROR_UPGRADE_MIGRATION_UNEXPECTED_CLI.get(
              cre.getDN().toNormalizedString()));
        }
        else
        {
          msg.append(INFO_ERROR_UPGRADE_MIGRATION_UNEXPECTED.get(
              cre.getDN().toNormalizedString()));
        }
        break;
      }
    }
    if (Utils.isCli())
    {
      msg.append(Constants.LINE_SEPARATOR);
      msg.append(Constants.LINE_SEPARATOR);
      msg.append(INFO_ERROR_UPGRADE_MIGRATION_NOTE_CLI.get(
          Utils.getPath(backupDir)));
    }
    else
    {
      msg.append(Constants.HTML_LINE_BREAK);
      msg.append(Constants.HTML_LINE_BREAK);
      msg.append(INFO_ERROR_UPGRADE_MIGRATION_NOTE.get(
          Utils.getPath(backupDir)));
    }
    return msg.toMessage();
  }

  private void ldifDiff(File source, File target, File output)
          throws ApplicationException, IOException, InterruptedException {
    ExternalTools et = new ExternalTools(installation);
    String[] args = new String[]{
            "-o", Utils.getPath(output),
            "-O",
    };
    OperationOutput oo = et.ldifDiff(source, target, args);
    int ret = oo.getReturnCode();
    if (ret != 0) {
      throw new ApplicationException(
          ReturnCode.TOOL_ERROR,
              INFO_ERROR_LDIF_DIFF_TOOL_RETURN_CODE.get(Integer.toString(ret)),
              null);
    }
  }

  private File getCustomConfigDiffFile() throws IOException {
    return new File(backupDir, "config.custom.diff");
  }

  private File getCustomSchemaDiffFile() throws IOException {
    return new File(backupDir, "schema.custom.diff");
  }

  private File getCustomADSDiffFile() throws IOException {
    return new File(backupDir, "admin-backend.custom.diff");
  }
}
