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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import org.opends.guitools.controlpanel.browser.BasicNodeError;
import org.opends.guitools.controlpanel.browser.ReferralLimitExceededException;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.quicksetup.util.Utils;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.OpenDsException;

/**
 * The panel that is displayed when there is an error searching an entry.
 *
 */
public class ErrorSearchingEntryPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -8460172599072631973L;

  /**
   * Default constructor.
   *
   */
  public ErrorSearchingEntryPanel()
  {
    super();
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

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return errorPane;
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_ERROR_SEARCHING_ENTRY_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
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
    Message title = INFO_CTRL_PANEL_ERROR_SEARCHING_ENTRY_TITLE.get();
    Message details;
    if (t instanceof OpenDsException)
    {
      details = ERR_CTRL_PANEL_ERROR_SEARCHING_ENTRY.get(dn,
      ((OpenDsException)t).getMessageObject().toString());
    }
    else
    {
      details = ERR_CTRL_PANEL_ERROR_SEARCHING_ENTRY.get(dn,
          t.toString());
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
    Message title = INFO_CTRL_PANEL_ERROR_RESOLVING_REFERRAL_TITLE.get();
    MessageBuilder details = new MessageBuilder();
    StringBuilder sb = new StringBuilder();
    for (String ref: referrals)
    {
      if (sb.length() > 0)
      {
        sb.append("<br>");
      }
      sb.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"+ref);
    }
    details.append(INFO_CTRL_PANEL_ERROR_RESOLVING_REFERRAL_MSG.get(dn, sb));
    Exception ex = error.getException();
    if (ex instanceof NamingException)
    {
      Object arg = error.getArg();
      Message msg = null;
      if (arg != null)
      {
        // Maybe is the LDAPURL
        try
        {
          LDAPURL url = LDAPURL.decode(arg.toString(), false);
          if (url.getHost() != null)
          {
            String hostPort = url.getHost()+":"+url.getPort();
            if (ex instanceof ReferralLimitExceededException)
            {
              msg = Message.raw(ex.getLocalizedMessage());
            }
            else if (ex instanceof NameNotFoundException)
            {
              msg =
                ERR_CTRL_PANEL_COULD_NOT_FIND_PROVIDED_ENTRY_IN_REFERRAL.get(
                    arg.toString(), hostPort);
            }
            else
            {
              msg = Utils.getMessageForException((NamingException)ex, hostPort);
            }
          }
          else
          {
            if (ex instanceof ReferralLimitExceededException)
            {
              msg = Message.raw(ex.getLocalizedMessage());
            }
            else if (ex instanceof NameNotFoundException)
            {
              msg =
           ERR_CTRL_PANEL_COULD_NOT_FIND_PROVIDED_ENTRY_IN_REFERRAL_NO_HOST.get(
                    arg.toString());
            }
            else
            {
              msg = Utils.getMessageForException((NamingException)ex);
            }
          }
        }
        catch (Throwable t)
        {
        }
      }

      if (msg == null)
      {
        if (ex instanceof ReferralLimitExceededException)
        {
          msg = Message.raw(ex.getLocalizedMessage());
        }
        else
        {
          msg = Utils.getMessageForException((NamingException)ex);
        }
      }
      if (arg != null)
      {
        details.append("<br><br>"+
            ERR_CTRL_PANEL_RESOLVING_REFERRAL_DETAILS.get(arg.toString(),
                msg));
      }
      else
      {
        details.append("<br><br>"+INFO_CTRL_PANEL_DETAILS_THROWABLE.get(msg));
      }
    }
    else if (ex != null)
    {
      String msg = ex.getLocalizedMessage();
      if (msg == null)
      {
        msg = ex.toString();
      }
      details.append("<br><br>"+INFO_CTRL_PANEL_DETAILS_THROWABLE.get(msg));
    }
    details.append("<br><br>"+INFO_CTRL_PANEL_HOW_TO_EDIT_REFERRALS.get());
    updateErrorPane(errorPane, title, ColorAndFontConstants.errorTitleFont,
        details.toMessage(), ColorAndFontConstants.defaultFont);
  }
}
