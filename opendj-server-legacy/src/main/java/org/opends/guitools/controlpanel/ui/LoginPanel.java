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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.LinkedHashSet;

import javax.naming.NamingException;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.datamodel.ConfigReadException;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.UserDataCertificateException;
import org.opends.quicksetup.ui.CertificateDialog;
import org.opends.quicksetup.util.UIKeyStore;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.StaticUtils;

import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;

/** The panel that appears when the user is asked to provide authentication. */
public class LoginPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 5051556513294844797L;
  private JPasswordField pwd;
  private JTextField dn;
  private JLabel pwdLabel;
  private JLabel dnLabel;
  private String usedUrl;

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Default constructor. */
  public LoginPanel()
  {
    super();
    createLayout();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_LOGIN_PANEL_TITLE.get();
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
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

  @Override
  public Component getPreferredFocusComponent()
  {
    return pwd;
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  @Override
  public void toBeDisplayed(boolean visible)
  {
    super.toBeDisplayed(visible);
    if (visible)
    {
      pwd.setText("");
    }
  }

  @Override
  public void okClicked()
  {
    setPrimaryValid(dnLabel);
    setPrimaryValid(pwdLabel);
    final LinkedHashSet<LocalizableMessage> errors = new LinkedHashSet<>();

    boolean dnInvalid = false;
    boolean pwdInvalid = false;

    if ("".equals(dn.getText().trim()))
    {
      dnInvalid = true;
      errors.add(INFO_EMPTY_DIRECTORY_MANAGER_DN.get());
    }
    else if (!isDN(dn.getText()))
    {
      dnInvalid = true;
      errors.add(INFO_NOT_A_DIRECTORY_MANAGER_DN.get());
    }

    if (pwd.getPassword().length == 0)
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

      BackgroundTask<ConnectionWrapper> worker = new BackgroundTask<ConnectionWrapper>()
      {
        @Override
        public ConnectionWrapper processBackgroundTask() throws Throwable
        {
          ConnectionWrapper conn = null;
          try
          {
            usedUrl = getInfo().getAdminConnectorURL();
            conn = Utilities.getAdminDirContext(getInfo(), dn.getText(), String.valueOf(pwd.getPassword()));

            if (getInfo().getConnection() != null)
            {
              try
              {
                getInfo().getConnection().close();
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
              @Override
              public void run()
              {
                displayMessage(
                    INFO_CTRL_PANEL_READING_CONFIGURATION_SUMMARY.get());
              }
            });
            getInfo().setConnection(conn);
            getInfo().setUserDataDirContext(null);
            getInfo().regenerateDescriptor();
            return conn;
          } catch (Throwable t)
          {
            StaticUtils.close(conn);
            throw t;
          }
        }

        @Override
        public void backgroundTaskCompleted(ConnectionWrapper conn, Throwable throwable)
        {
          boolean handleCertificateException = false;
          if (throwable != null)
          {
            logger.info(LocalizableMessage.raw("Error connecting: " + throwable, throwable));

            if (isCertificateException(throwable))
            {
              ApplicationTrustManager.Cause cause =
                getInfo().getTrustManager().getLastRefusedCause();

              logger.info(LocalizableMessage.raw("Certificate exception cause: "+cause));
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
                LocalizableMessage msg = getThrowableMsg(
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
                  logger.warn(LocalizableMessage.raw(
                      "Error parsing ldap url of ldap url.", t));
                  h = INFO_NOT_AVAILABLE_LABEL.get().toString();
                  p = -1;
                }
                UserDataCertificateException udce =
                  new UserDataCertificateException(null,
                      INFO_CERTIFICATE_EXCEPTION.get(h, p),
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
                errors.add(Utils.getMessageForException(
                    (NamingException)throwable));
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
              errors.add(getThrowableMsg(INFO_BUG_MSG.get(), throwable));
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

  @Override
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

      if (chain != null && authType != null && host != null)
      {
        logger.info(LocalizableMessage.raw("Accepting certificate presented by host "+host));
        getInfo().getTrustManager().acceptCertificate(chain, authType, host);
        /* Simulate a click on the OK by calling in the okClicked method. */
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
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
          logger.warn(LocalizableMessage.raw(
              "The chain is null for the UserDataCertificateException"));
        }
        if (authType == null)
        {
          logger.warn(LocalizableMessage.raw(
              "The auth type is null for the UserDataCertificateException"));
        }
        if (host == null)
        {
          logger.warn(LocalizableMessage.raw(
              "The host is null for the UserDataCertificateException"));
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
          logger.warn(LocalizableMessage.raw("Error accepting certificate: "+t, t));
        }
      }
    }
  }
}
