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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.upgrader;
import org.opends.messages.Message;

import org.opends.quicksetup.BuildInformation;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.UserInteraction;

import org.opends.server.util.VersionCompatibilityIssue;
import org.opends.server.util.BuildVersion;
import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.server.util.VersionCompatibilityIssue.*;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * This class can answer questions important upgrade/reversion questions
 * like 'can I upgrade from verion X to version Y?' and 'if not then why?'.
 * This is also responsible for obtaining and translating any applicable
 * {@link org.opends.server.util.VersionCompatibilityIssue}s and
 * interacting with the user to inform them of an actions or information
 * that they dictate.
 */
public abstract class VersionIssueNotifier {

  static private final Logger LOG =
          Logger.getLogger(VersionIssueNotifier.class.getName());

  /**
   * End Of Line.
   */
  public static String EOL = System.getProperty("line.separator");


  /** Descriptor for a directive. */
  protected enum DirectiveType {

    /** Causes the tools to refuse to continue. */
    NOT_SUPPORTED,

    /** Causes the tools to display an action dialog. */
    ACTION,

    /** Causes the tools to display an informational dialog. */
    INFO,

    /** Causes the tools to display a warning dialog. */
    WARNING

  }

  /**
   * Holds information that directs tool behavior.
   */
  protected class Directive {

    DirectiveType type;
    Message msg;

    /**
     * Creates a parameterized instance.
     *
     * @param type of directive
     * @param localizedMsg for displaying to the user
     */
    public Directive(DirectiveType type, Message localizedMsg) {
      this.type = type;
      this.msg = localizedMsg;
    }

    /**
     * Gets the type of issue.
     * @return type of issue
     */
    public DirectiveType getType() {
      return this.type;
    }

    /**
     * Gets the issue's message.
     * @return string message
     */

    public Message getMessage() {
      return this.msg;
    }

  }

  /** Used for interacting with the user. */
  protected UserInteraction ui;

  /** Version issues applicable to this operation. */
  protected List<Directive> directives;

  /** Information about the current build version. */
  protected BuildInformation currentBuildInfo;

  /** Information about the proposed new build version. */
  protected BuildInformation newBuildInfo;

  private boolean isSupported = true;
  private boolean noServerStart = false;

  /**
   * Creates a parameterized instance.
   * @param ui for interacting with the user
   * @param current build version
   * @param neu build version
   */
  public VersionIssueNotifier(UserInteraction ui,
                       BuildInformation current,
                       BuildInformation neu) {
    this.ui = ui;
    this.currentBuildInfo = current;
    this.newBuildInfo = neu;

    // Get the list of possible version incompatibility events (aka flag days)
    List<VersionCompatibilityIssue> compatibilityIssues;
    Set<Integer> excludeIds ;
    boolean isUpgrade = neu.compareTo(current) >= 0;
    if (isUpgrade)
    {
      excludeIds = current.getIncompatibilityEventIds();
    }
    else
    {
      excludeIds = neu.getIncompatibilityEventIds();
    }
    if (excludeIds != null) {
      if (isUpgrade)
      {
        compatibilityIssues = getEvents(excludeIds, current, neu);
      }
      else
      {
        compatibilityIssues = getEvents(excludeIds, neu, current);
      }
    } else {
      // This method is only used as a fallback for pre 1.0.0 servers which
      // do not advertise incompatible version events.
      LOG.log(Level.INFO, "Legacy method for obtaining compatibility issues");
      BuildVersion bv = new BuildVersion(
              current.getMajorVersion(),
              current.getMinorVersion(),
              current.getPointVersion(),
              current.getRevisionNumber());
      compatibilityIssues = getEvents(bv);
    }
    directives = processEvents(compatibilityIssues);
  }

  /**
   * Interacts with the user to let them know about any
   * version issues applicable to operations between the
   * builds supplied in the constructor.
   *
   * @throws ApplicationException if something goes wrong or
   *         the user cancels the operation.
   */
  public abstract void notifyUser() throws ApplicationException;

  /**
   * Indicates whether or not this operation would be considered an
   * upgrade (as opposed to a reversion).
   *
   * @return boolean where true indicates that this would be an upgrade;
   *         false indicates that this would be a reversion.
   */
  public boolean isUpgrade() {
    return currentBuildInfo.compareTo(newBuildInfo) < 0;
  }

  /**
   * Indicates whether or not this operation would be considered an
   * reversion (as opposed to an upgrade).
   *
   * @return boolean where true indicates that this would be a reversion;
   *         false indicates that this would be an upgrade.
   */
  public boolean isReversion() {
    return currentBuildInfo.compareTo(newBuildInfo) > 0;
  }

  /**
   * Returns whether or not this operation is supported.
   * @return true to indicate this operation is supported; false otherwise
   */
  public boolean isSupported() {
    return isSupported;
  }

  /**
   * Indicates whether the set of version issues dictates that the server
   * not be restarted afterward.
   *
   * @return true meaning the server won't be restarted; false otherwise
   */
  public boolean noServerStartFollowingOperation() {
    return noServerStart;
  }

  /**
   * Gets a list of issues applicable to this operation.
   * @return list of issues
   */
  protected List<Directive> getIssues() {
    return directives;
  }

  /**
   * Indicates whether or not there are issues with this operation.
   * @return true indicating there are issues; false otherwise
   */
  protected boolean hasIssues() {
    return (directives != null && directives.size() > 0);
  }

  /**
   * Given a particular cause return a detail message appropriate
   * for this operation.
   *
   * @param cause of issue
   * @return message for presenting to the user
   */
  protected abstract Message getLocalizedDetailMessage(
          VersionCompatibilityIssue.Cause cause);

  /**
   * Given a particular cause indicates whether or not the user
   * will be confronted with verbage explaining that they will
   * have to perform extra actions for this operation.
   *
   * @param cause of issue
   * @return message for presenting to the user
   */
  protected abstract boolean isActionRequired(
          VersionCompatibilityIssue.Cause cause);

  /**
   * Given a particular cause indicates whether or not this
   * operation should be allowed to continue.
   *
   * @param cause of issue
   * @return message for presenting to the user
   */
  protected abstract boolean isUnsupported(
          VersionCompatibilityIssue.Cause cause);

  /**
   * Given a particular cause indicates whether or not this
   * the user will be shown a warning dialog containing
   * a warning message regarding this operation.
   *
   * @param cause of issue
   * @return message for presenting to the user
   */
  protected abstract boolean isWarning(
          VersionCompatibilityIssue.Cause cause);

  /**
   * Given a particular cause indicates whether or not this
   * the user will be shown some verbage that may contain
   * information about this operation.
   *
   * @param cause of issue
   * @return message for presenting to the user
   */
  protected boolean isNotification(Cause cause) {
    boolean isNotification = false;
    if (cause != null) {
      Message msg = getLocalizedDetailMessage(cause);
      if (msg != null && !isWarning(cause) && !isActionRequired(cause)) {
        isNotification = true;
      }
    }
    return isNotification;
  }

  /**
   * Gets a list of strings representing the steps neccessary
   * to export and then reimport the data.
   *
   * @return List containing strings representing intruction steps
   */
  protected List<Message> getExportImportInstructions() {
    List<Message> instructions = new ArrayList<Message>();
    if ((ui == null) || (ui.isCLI()))
    {
      instructions.add(INFO_ORACLE_EI_ACTION_STEP1_CLI.get());
    }
    else
    {
      instructions.add(INFO_ORACLE_EI_ACTION_STEP1.get());
    }
    instructions.add(INFO_ORACLE_EI_ACTION_STEP2.get());
    instructions.add(INFO_ORACLE_EI_ACTION_STEP3.get());
    instructions.add(INFO_ORACLE_EI_ACTION_STEP4.get());
    return instructions;
  }

  /**
   * Converts a set of compatibility issues into a set of set of
   * action oriented issues for directing tool behavior.
   *
   * @param compatibilityIssues list of issues
   * @return list of directives
   */
  private List<Directive> processEvents(
          List<VersionCompatibilityIssue> compatibilityIssues)
  {
    List<Directive> directives = new ArrayList<Directive>();
    if (compatibilityIssues != null) {
      for (VersionCompatibilityIssue evt : compatibilityIssues) {
        VersionCompatibilityIssue.Cause cause = evt.getCause();
        Set<Effect> effects = cause.getEffects();
        Message msg = getLocalizedDetailMessage(cause);
        if (isUnsupported(cause)) {
          isSupported = false;
          directives.add(new Directive(DirectiveType.NOT_SUPPORTED, msg));
        } else if (isActionRequired(cause)) {
          directives.add(new Directive(DirectiveType.ACTION, msg));
        } else if (isWarning(cause)) {
          directives.add(new Directive(DirectiveType.WARNING, msg));
        } else if (isNotification(cause)) {
          directives.add(new Directive(DirectiveType.INFO, msg));
        }
        if ((effects.contains(
                Effect.NO_SERVER_RESTART_FOLLOWING_REVERSION) ||
                effects.contains(
                        Effect.REVERSION_DATA_EXPORT_AND_REIMPORT_REQUIRED) ||
                effects.contains(
                        Effect.UPGRADE_DATA_EXPORT_AND_REIMPORT_REQUIRED)))

        {
          noServerStart = true;
        }
      }
    }
    return Collections.unmodifiableList(directives);
  }

  /**
   * Creates a list appropriate for the presentation implementation.
   *
   * @param list to format
   * @return String representing the list
   */
  protected String createUnorderedList(List list) {
    StringBuilder sb = new StringBuilder();
    if (list != null) {
      for (Object o : list) {
        sb.append(/*bullet=*/"* ");
        sb.append(o.toString());
        sb.append(Constants.LINE_SEPARATOR);
      }
    }
    return sb.toString();
  }

}
