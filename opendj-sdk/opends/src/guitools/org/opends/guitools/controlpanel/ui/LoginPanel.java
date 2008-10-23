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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.guitools.controlpanel.datamodel.ConfigReadException;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.quicksetup.UserDataCertificateException;
import org.opends.quicksetup.ui.CertificateDialog;
import org.opends.quicksetup.util.UIKeyStore;
import org.opends.quicksetup.util.Utils;
import org.opends.server.types.DN;

/**
 * The panel that appears when the user is asked to provide authentication.
 *
 */
public class LoginPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 5051556513294844797L;
  private JPasswordField pwd;
  private JTextField dn;
  private JLabel pwdLabel;
  private JLabel dnLabel;
  private String usedUrl;

  private static final Logger LOG =
    Logger.getLogger(LoginPanel.class.getName());

  /**
   * Default constructor.
   *
   */
  public LoginPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_LOGIN_PANEL_TITLE.get();
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridx = 0;
    gbc.gridy = 0;

    gbc.weightx = 0.0;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.NONE;
    dnLabel = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_BIND_DN_LABEL.get());
    add(dnLabel, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    dn = Utilities.createTextField("cn=Directory Manager", 20);
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(dn, gbc);
    gbc.insets.top = 10;
    gbc.insets.left = 0;

    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.weightx = 0.0;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.NONE;
    pwdLabel = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_BIND_PASSWORD_LABEL.get());
    add(pwdLabel, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    pwd = Utilities.createPasswordField();
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(pwd, gbc);

    addBottomGlue(gbc);
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return pwd;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * {@inheritDoc}
   */
  public void toBeDisplayed(boolean visible)
  {
    super.toBeDisplayed(visible);
    if (visible)
    {
      pwd.setText("");
    }
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    setPrimaryValid(dnLabel);
    setPrimaryValid(pwdLabel);
    final LinkedHashSet<Message> errors = new LinkedHashSet<Message>();

    boolean dnInvalid = false;
    boolean pwdInvalid = false;

    if ("".equals(dn.getText().trim()))
    {
      dnInvalid = true;
      errors.add(INFO_EMPTY_DIRECTORY_MANAGER_DN.get());
    }
    else if (!Utils.isDn(dn.getText()))
    {
      dnInvalid = true;
      errors.add(INFO_NOT_A_DIRECTORY_MANAGER_DN.get());
    }

    if ("".equals(pwd.getPassword().length == 0))
    {
      pwdInvalid = true;
      errors.add(INFO_EMPTY_PWD.get());
    }
    if (dnInvalid)
    {
      setPrimaryInvalid(dnLabel);
    }

    if (pwdInvalid)
    {
      setPrimaryInvalid(pwdLabel);
    }

    if (errors.isEmpty())
    {
      setEnabledOK(false);
      setEnabledCancel(false);
      displayMessage(INFO_CTRL_PANEL_VERIFYING_AUTHENTICATION_SUMMARY.get());

      BackgroundTask<InitialLdapContext> worker =
        new BackgroundTask<InitialLdapContext>()
      {
        /**
         * {@inheritDoc}
         */
        public InitialLdapContext processBackgroundTask() throws Throwable
        {
          InitialLdapContext ctx = null;
          try
          {
            usedUrl = getInfo().getAdminConnectorURL();
            ctx = Utilities.getAdminDirContext(getInfo(), dn.getText(),
                String.valueOf(pwd.getPassword()));

            if (getInfo().getDirContext() != null)
            {
              try
              {
                getInfo().getDirContext().close();
              }
              catch (Throwable t)
              {
              }
            }
            if (getInfo().getUserDataDirContext() != null)
            {
              try
              {
                getInfo().getUserDataDirContext().close();
              }
              catch (Throwable t)
              {
              }
            }
            try
            {
              Thread.sleep(500);
            }
            catch (Throwable t)
            {
            }
            SwingUtilities.invokeLater(new Runnable()
            {
              public void run()
              {
                displayMessage(
                    INFO_CTRL_PANEL_READING_CONFIGURATION_SUMMARY.get());
              }
            });
            getInfo().setDirContext(ctx);
            getInfo().setUserDataDirContext(null);
            getInfo().regenerateDescriptor();
            return ctx;
          } catch (Throwable t)
          {
            if (ctx != null)
            {
              try
              {
                ctx.close();
              }
              catch (Throwable t1)
              {
              }
            }
            throw t;
          }
        }

        /**
         * {@inheritDoc}
         */
        public void backgroundTaskCompleted(InitialLdapContext ctx,
            Throwable throwable)
        {
          boolean handleCertificateException = false;
          if (throwable != null)
          {
            LOG.log(Level.INFO, "Error connecting: " + throwable, throwable);

            if (Utils.isCertificateException(throwable))
            {
              ApplicationTrustManager.Cause cause =
                getInfo().getTrustManager().getLastRefusedCause();

              LOG.log(Level.INFO, "Certificate exception cause: "+cause);
              UserDataCertificateException.Type excType = null;
              if (cause == ApplicationTrustManager.Cause.NOT_TRUSTED)
              {
                excType = UserDataCertificateException.Type.NOT_TRUSTED;
              }
              else if (cause ==
                ApplicationTrustManager.Cause.HOST_NAME_MISMATCH)
              {
                excType = UserDataCertificateException.Type.HOST_NAME_MISMATCH;
              }
              else
              {
                Message msg = Utils.getThrowableMsg(
                    INFO_ERROR_CONNECTING_TO_LOCAL.get(), throwable);
                errors.add(msg);
              }

              if (excType != null)
              {
                String h;
                int p;
                try
                {
                  URI uri = new URI(usedUrl);
                  h = uri.getHost();
                  p = uri.getPort();
                }
                catch (Throwable t)
                {
                  LOG.log(Level.WARNING,
                      "Error parsing ldap url of ldap url.", t);
                  h = INFO_NOT_AVAILABLE_LABEL.get().toString();
                  p = -1;
                }
                UserDataCertificateException udce =
                  new UserDataCertificateException(null,
                      INFO_CERTIFICATE_EXCEPTION.get(h, String.valueOf(p)),
                      throwable, h, p,
                      getInfo().getTrustManager().getLastRefusedChain(),
                      getInfo().getTrustManager().getLastRefusedAuthType(),
                      excType);

                handleCertificateException(udce);
                handleCertificateException = true;
              }
            }
            else if (throwable instanceof NamingException)
            {
              boolean found = false;
              String providedDn = dn.getText();
              Iterator<DN> it = getInfo().getServerDescriptor().
              getAdministrativeUsers().iterator();
              while (it.hasNext() && !found)
              {
                found = Utils.areDnsEqual(providedDn, it.next().toString());
              }
              if (!found)
              {
                errors.add(INFO_NOT_A_DIRECTORY_MANAGER_IN_CONFIG.get());
              }
              else
              {
                errors.add(ERR_CANNOT_CONNECT_TO_LOGIN_WITHOUT_CAUSE.get());
              }

              setPrimaryInvalid(dnLabel);
              setPrimaryInvalid(pwdLabel);
            }
            else if (throwable instanceof ConfigReadException)
            {
              errors.add(((ConfigReadException)throwable).getMessageObject());
            }
            else
            {
              // This is a bug
              throwable.printStackTrace();
              errors.add(Utils.getThrowableMsg(INFO_BUG_MSG.get(), throwable));
            }
          }
          displayMainPanel();
          setEnabledCancel(true);
          setEnabledOK(true);
          if (!errors.isEmpty())
          {
            displayErrorDialog(errors);
            pwd.setSelectionStart(0);
            pwd.setSelectionEnd(pwd.getPassword().length);
            pwd.requestFocusInWindow();
          }
          else if (!handleCertificateException)
          {
            Utilities.getParentDialog(LoginPanel.this).setVisible(false);
          }
        }
      };
      worker.startBackgroundTask();
    }
    else
    {
      displayErrorDialog(errors);
      if (dnInvalid)
      {
        dn.setSelectionStart(0);
        dn.setSelectionEnd(dn.getText().length());
        dn.requestFocusInWindow();
      }
      if (pwdInvalid)
      {
        pwd.setSelectionStart(0);
        pwd.setSelectionEnd(pwd.getPassword().length);
        pwd.requestFocusInWindow();
      }

    }
  }

  /**
   * {@inheritDoc}
   */
  public void cancelClicked()
  {
    setPrimaryValid(dnLabel);
    setPrimaryValid(pwdLabel);
    pwd.setText(null);
    super.cancelClicked();
  }

  /**
   * Displays a dialog asking the user to accept a certificate if the user
   * accepts it, we update the trust manager and simulate a click on "OK" to
   * re-check the authentication.
   * This method assumes that we are being called from the event thread.
   */
  private void handleCertificateException(UserDataCertificateException ce)
  {
    CertificateDialog dlg = new CertificateDialog(null, ce);
    dlg.pack();
    Utilities.centerGoldenMean(dlg, Utilities.getParentDialog(this));
    dlg.setVisible(true);
    if (dlg.getUserAnswer() !=
      CertificateDialog.ReturnType.NOT_ACCEPTED)
    {
      X509Certificate[] chain = ce.getChain();
      String authType = ce.getAuthType();
      String host = ce.getHost();

      if ((chain != null) && (authType != null) && (host != null))
      {
        LOG.log(Level.INFO, "Accepting certificate presented by host "+host);
        getInfo().getTrustManager().acceptCertificate(chain, authType, host);
        /* Simulate a click on the OK by calling in the okClicked method. */
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            okClicked();
          }
        });
      }
      else
      {
        if (chain == null)
        {
          LOG.log(Level.WARNING,
              "The chain is null for the UserDataCertificateException");
        }
        if (authType == null)
        {
          LOG.log(Level.WARNING,
              "The auth type is null for the UserDataCertificateException");
        }
        if (host == null)
        {
          LOG.log(Level.WARNING,
              "The host is null for the UserDataCertificateException");
        }
      }
    }
    if (dlg.getUserAnswer() ==
      CertificateDialog.ReturnType.ACCEPTED_PERMANENTLY)
    {
      X509Certificate[] chain = ce.getChain();
      if (chain != null)
      {
        try
        {
          UIKeyStore.acceptCertificate(chain);
        }
        catch (Throwable t)
        {
          LOG.log(Level.WARNING, "Error accepting certificate: "+t, t);
        }
      }
    }
  }
}
