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

package org.opends.quicksetup.upgrader;

import org.opends.quicksetup.BuildInformation;
import org.opends.quicksetup.UserInteraction;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Constants;
import org.opends.server.util.VersionCompatibilityIssue;
import static org.opends.server.util.VersionCompatibilityIssue.*;

import java.util.Set;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * {@link org.opends.quicksetup.upgrader.VersionIssueNotifier} specific
 * to upgrade tools.
 */
public class UpgradeIssueNotifier extends VersionIssueNotifier {

  static private final Logger LOG =
          Logger.getLogger(UpgradeIssueNotifier.class.getName());

  /**
   * Creates a new instance that can analyze a hypothetical upgrade/reversion
   * operation from one version to another.
   * @param ui UserInteraction for relaying information to the user
   * @param current BuildInformation representing the current version
   * @param neu BuildInformation representing the proposed next version
   */
  public UpgradeIssueNotifier(UserInteraction ui,
                       BuildInformation current,
                       BuildInformation neu) {
    super(ui, current, neu);
  }

  /**
   * {@inheritDoc}
   */
  public void notifyUser() throws ApplicationException {
    String[] args = { currentBuildInfo.toString(), newBuildInfo.toString() };
    String cont = getMsg("oracle-action-prompt-continue");
    String cancel = getMsg("oracle-action-prompt-cancel");
    if (hasIssues()) {
      List<Directive> issues = getIssues();
      if (!isSupported()) {
        if (issues != null) {
          for (VersionIssueNotifier.Directive directive : issues) {
            LOG.log(Level.INFO, "Unsupported upgrade details: " +
                    directive.getMessage());
          }
        }
        throw new ApplicationException(ApplicationException.Type.APPLICATION,
                getMsg("upgrade-oracle-unsupported", args), null);
      } else {
        if (ui != null) {
          for (VersionIssueNotifier.Directive directive : issues) {
            String title;
            String summary;
            String details;
            String defaultAction;
            UserInteraction.MessageType msgType;
            switch (directive.getType()) {
              case ACTION:
                title = getMsg("general-action-required");
                summary = getMsg("upgrade-oracle-action", args);
                details = directive.getMessage() +
                        Constants.HTML_LINE_BREAK +
                        Constants.HTML_LINE_BREAK +
                        getMsg("oracle-action-prompt");
                msgType = UserInteraction.MessageType.WARNING;
                defaultAction = cancel;
                break;
              case INFO:
                title = getMsg("general-info");
                summary = getMsg("upgrade-oracle-info");
                details = directive.getMessage() +
                        Constants.HTML_LINE_BREAK +
                        Constants.HTML_LINE_BREAK +
                        getMsg("oracle-info-prompt");
                msgType = UserInteraction.MessageType.INFORMATION;
                defaultAction = cont;
                break;
              case WARNING:
                title = getMsg("general-warning");
                summary = getMsg("upgrade-oracle-warning");
                details = directive.getMessage() +
                        Constants.HTML_LINE_BREAK +
                        Constants.HTML_LINE_BREAK +
                        getMsg("oracle-info-prompt");
                msgType = UserInteraction.MessageType.WARNING;
                defaultAction = cont;
                break;
              default:
                LOG.log(Level.INFO, "Unexpected issue type " +
                        directive.getType());
                title = "";
                summary = "";
                details = directive.getMessage();
                msgType = UserInteraction.MessageType.WARNING;
                defaultAction = cont;
            }
            if (cancel.equals(ui.confirm(
                    summary,
                    details,
                    title,
                    msgType,
                    new String[]{cont, cancel},
                    defaultAction))) {
              throw new ApplicationException(
                      ApplicationException.Type.CANCEL,
                      getMsg("upgrade-canceled"), null);
            }
          }
        } else {
          throw new ApplicationException(ApplicationException.Type.APPLICATION,
                  getMsg("oracle-no-silent"), null);
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  protected String getLocalizedDetailMessage(
          VersionCompatibilityIssue.Cause cause)
  {
    String msg = cause.getLocalizedUpgradeMessage();

    // See if we need to supply a generic message
    Set<VersionCompatibilityIssue.Effect> effects = cause.getEffects();

    // If the import/export effect is present, append the detailed
    // instructions.
    if (effects.contains(Effect.UPGRADE_DATA_EXPORT_AND_REIMPORT_REQUIRED)) {
      if (msg == null) msg = "";
      msg = msg + Constants.HTML_LINE_BREAK +
              ui.createUnorderedList(getExportImportInstructions());
    }
    return msg;
  }

  /**
   * {@inheritDoc}
   */
  protected boolean isActionRequired(VersionCompatibilityIssue.Cause cause) {
    boolean isAction = false;
    if (cause != null) {
      Set<VersionCompatibilityIssue.Effect> effects = cause.getEffects();
      isAction =
              effects.contains(
                      Effect.UPGRADE_DATA_EXPORT_AND_REIMPORT_REQUIRED) ||
                      (effects.contains(
                              Effect.UPGRADE_MANUAL_ACTION_REQUIRED) &&
                              cause.getLocalizedUpgradeMessage() != null);
    }
    return isAction;
  }

  /**
   * {@inheritDoc}
   */
  protected boolean isWarning(VersionCompatibilityIssue.Cause cause) {
    boolean isWarning = false;
    if (cause != null && !isActionRequired(cause)) {
      Set<VersionCompatibilityIssue.Effect> effects = cause.getEffects();
      isWarning = effects.contains(Effect.UPGRADE_SHOW_WARNING_MESSAGE) &&
              cause.getLocalizedUpgradeMessage() != null;
    }
    return isWarning;
  }

  /**
   * {@inheritDoc}
   */
  protected boolean isUnsupported(VersionCompatibilityIssue.Cause cause) {
    boolean isUnsupported = false;
    if (cause != null) {
      Set<VersionCompatibilityIssue.Effect> effects = cause.getEffects();
      for (VersionCompatibilityIssue.Effect effect : effects) {
        switch (effect) {
          case UPGRADE_NOT_POSSIBLE:
            isUnsupported = true; break;
          default:
            // assume not an tion;
        }
      }
    }
    return isUnsupported;
  }

}
