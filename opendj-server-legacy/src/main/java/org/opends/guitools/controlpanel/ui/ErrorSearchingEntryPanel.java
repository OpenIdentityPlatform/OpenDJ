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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.guitools.controlpanel.browser.BasicNodeError;
import org.opends.guitools.controlpanel.browser.ReferralLimitExceededException;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.quicksetup.util.Utils;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.OpenDsException;

import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.AdminToolMessages.*;

/** The panel that is displayed when there is an error searching an entry. */
public class ErrorSearchingEntryPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -8460172599072631973L;

  /** Default constructor. */
  public ErrorSearchingEntryPanel()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.gridheight = 1;
    gbc.weightx = 1.0;
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new Insets(20, 20, 0, 20);
    createErrorPane();
    add(errorPane, gbc);
    errorPane.setVisible(true);
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return errorPane;
  }

  @Override
  public void okClicked()
  {
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_ERROR_SEARCHING_ENTRY_TITLE.get();
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * Sets the error to be displayed in the panel.
   * @param dn the DN of the entry that caused a problem.
   * @param t the Throwable that occurred when searching the entry.
   */
  public void setError(String dn, Throwable t)
  {
    LocalizableMessage title = INFO_CTRL_PANEL_ERROR_SEARCHING_ENTRY_TITLE.get();
    LocalizableMessage details;
    if (t instanceof OpenDsException)
    {
      details = ERR_CTRL_PANEL_ERROR_SEARCHING_ENTRY.get(dn,
      ((OpenDsException)t).getMessageObject());
    }
    else
    {
      details = ERR_CTRL_PANEL_ERROR_SEARCHING_ENTRY.get(dn, t);
    }
    updateErrorPane(errorPane, title, ColorAndFontConstants.errorTitleFont,
        details, ColorAndFontConstants.defaultFont);
  }

  /**
   * Sets the error to be displayed in the panel.
   * @param dn the DN of the local entry.
   * @param referrals the list of referrals defined in the entry.
   * @param error the error that occurred resolving the referral.
   */
  public void setReferralError(String dn, String[] referrals,
      BasicNodeError error)
  {
    LocalizableMessage title = INFO_CTRL_PANEL_ERROR_RESOLVING_REFERRAL_TITLE.get();
    LocalizableMessageBuilder details = new LocalizableMessageBuilder();
    StringBuilder sb = new StringBuilder();
    for (String ref: referrals)
    {
      if (sb.length() > 0)
      {
        sb.append("<br>");
      }
      sb.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;").append(ref);
    }
    details.append(INFO_CTRL_PANEL_ERROR_RESOLVING_REFERRAL_MSG.get(dn, sb));
    Exception ex = error.getException();
    if (ex instanceof NamingException)
    {
      Object arg = error.getArg();
      LocalizableMessage msg = getErrorMsg(ex, arg);
      if (arg != null)
      {
        details.append("<br><br>").append(ERR_CTRL_PANEL_RESOLVING_REFERRAL_DETAILS.get(arg, msg));
      }
      else
      {
        details.append("<br><br>").append(INFO_CTRL_PANEL_DETAILS_THROWABLE.get(msg));
      }
    }
    else if (ex != null)
    {
      String msg = ex.getLocalizedMessage();
      if (msg == null)
      {
        msg = ex.toString();
      }
      details.append("<br><br>").append(INFO_CTRL_PANEL_DETAILS_THROWABLE.get(msg));
    }
    details.append("<br><br>").append(INFO_CTRL_PANEL_HOW_TO_EDIT_REFERRALS.get());
    updateErrorPane(errorPane, title, ColorAndFontConstants.errorTitleFont,
        details.toMessage(), ColorAndFontConstants.defaultFont);
  }

  private LocalizableMessage getErrorMsg(Exception ex, Object arg)
  {
    LocalizableMessage msg = getErrorMsg0(ex, arg);
    if (msg != null)
    {
      return msg;
    }
    else if (ex instanceof ReferralLimitExceededException)
    {
      return LocalizableMessage.raw(ex.getLocalizedMessage());
    }
    else
    {
      return Utils.getMessageForException((NamingException) ex);
    }
  }

  private LocalizableMessage getErrorMsg0(Exception ex, Object arg)
  {
    if (arg == null)
    {
      return null;
    }

    // Maybe arg is an LDAPURL
    try
    {
      LDAPURL url = LDAPURL.decode(arg.toString(), false);
      if (url.getHost() != null)
      {
        String hostPort = url.getHost() + ":" + url.getPort();
        if (ex instanceof ReferralLimitExceededException)
        {
          return LocalizableMessage.raw(ex.getLocalizedMessage());
        }
        else if (ex instanceof NameNotFoundException)
        {
          return ERR_CTRL_PANEL_COULD_NOT_FIND_PROVIDED_ENTRY_IN_REFERRAL.get(arg, hostPort);
        }
        else
        {
          return getMessageForException((NamingException) ex, hostPort);
        }
      }
      else if (ex instanceof ReferralLimitExceededException)
      {
        return LocalizableMessage.raw(ex.getLocalizedMessage());
      }
      else if (ex instanceof NameNotFoundException)
      {
        return ERR_CTRL_PANEL_COULD_NOT_FIND_PROVIDED_ENTRY_IN_REFERRAL_NO_HOST.get(arg);
      }
      else
      {
        return Utils.getMessageForException((NamingException) ex);
      }
    }
    catch (Throwable t)
    {
      return null;
    }
  }
}
