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

package org.opends.quicksetup.upgrader;

import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.UserInteraction;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.util.ExternalTools;
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
    if (installation.getStatus().schemaHasBeenModified()) {
      LOG.log(Level.INFO, "Schema contains customizations that will " +
              "be migrated");
      try {
        ldifDiff(installation.getBaseSchemaFile(),
                installation.getSchemaConcatFile(),
                getCustomSchemaDiffFile());
      } catch (ApplicationException ae) {
        throw ae;
      } catch (Exception e) {
        throw ApplicationException.createFileSystemException(
                getMsg("error-determining-custom-schema"), e);
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
                  getMsg("error-determining-custom-config"), e);
        }
      } else {
        LOG.log(Level.INFO, "No configuration customizations to migrate");
      }
    } catch (IOException e) {
      throw ApplicationException.createFileSystemException(
              getMsg("error-determining-custom-config"), e);
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
      String msg = getMsg("error-applying-custom-config");
      LOG.log(Level.INFO, msg, e);
      throw new ApplicationException(ApplicationException.Type.IMPORT_ERROR,
              msg, e);
    }
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
      String msg = getMsg("error-applying-custom-schema");
      LOG.log(Level.INFO, msg, e);
      throw new ApplicationException(ApplicationException.Type.IMPORT_ERROR,
              msg, e);
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
    return installation.getStatus().schemaHasBeenModified();
  }

  /**
   * Returns a value to indicate whether the installation associated
   * with this class contains configuration customizations.  A call to
   * {@link #calculateConfigCustomizations()} must first be made in
   * order for this value to be valid.
   *
   * @return boolean where true indicates schema customization
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
          String cancel = getMsg("cancel-button-label");
          String cont = getMsg("continue-button-label");
          String retry = getMsg("retry-button-label");
          Object r = ui.confirm(
                  getModificationErrorSummary(component),
                  getModificationErrorMessage(cre),
                  e.getLocalizedMessage(),
                  getMsg("error-upgrade-migration"),
                  UserInteraction.MessageType.ERROR,
                  new String[]{cancel, cont, retry},
                  cancel, null);
          if (cont.equals(r)) {
            cre = ldifReader.readChangeRecord(false);
          } else if (retry.equals(r)) {
            // do nothing; will retry;
          } else {
            throw new ApplicationException(ApplicationException.Type.CANCEL,
                    getMsg("upgrade-canceled"), e);
          }
        } else {
          throw e;
        }
      }
    }
  }

  private String getModificationErrorSummary(Component c) {
    String summary;
    switch(c) {
      case SCHEMA:
        summary = getMsg("error-upgrade-migration-schema");
        break;
      case CONFIGURATION:
        summary = getMsg("error-upgrade-migration-config");
        break;
      default:
        summary = "";
    }
    return summary;
  }

  private String getModificationErrorMessage(ChangeRecordEntry cre) {
    StringBuilder msg = new StringBuilder();
    msg.append(Constants.HTML_LINE_BREAK);
    if (cre != null) {
      switch (cre.getChangeOperationType()) {
        case MODIFY:
          msg.append(getMsg("error-upgrade-migration-modify",
                  cre.getDN().toNormalizedString()));
          break;
        case ADD:
          msg.append(getMsg("error-upgrade-migration-add",
                  cre.getDN().toNormalizedString()));
          break;
        case DELETE:
          msg.append(getMsg("error-upgrade-migration-delete",
                  cre.getDN().toNormalizedString()));
          break;
        default:
          LOG.log(Level.INFO, "Unexpected change operation type " +
                cre.getChangeOperationType());
          msg.append(getMsg("error-upgrade-migration-unexpected",
                  cre.getDN().toNormalizedString()));
          break;
      }
    }
    msg.append(Constants.HTML_LINE_BREAK);
    msg.append(Constants.HTML_LINE_BREAK);
    msg.append(getMsg("error-upgrade-migration-note",
            Utils.getPath(backupDir)));
    return msg.toString();
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
              ApplicationException.Type.TOOL_ERROR,
              getMsg("error-ldif-diff-tool-return-code",
                      Integer.toString(ret)),
              null);
    }
  }

  private File getCustomConfigDiffFile() throws IOException {
    return new File(backupDir, "config.custom.diff");
  }

  private File getCustomSchemaDiffFile() throws IOException {
    return new File(backupDir, "schema.custom.diff");
  }

  static private String getMsg(String key, String... args) {
    return ResourceProvider.getInstance().getMsg(key, args);
  }

}
