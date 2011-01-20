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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.ControlPanelArgumentParser;
import org.opends.guitools.controlpanel.datamodel.ConfigReadException;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.OnlineUpdateException;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.UserDataCertificateException;
import org.opends.quicksetup.ui.CertificateDialog;
import org.opends.quicksetup.util.UIKeyStore;
import org.opends.quicksetup.util.Utils;
import org.opends.server.monitors.VersionMonitorProvider;
import org.opends.server.types.DN;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.DynamicConstants;

/**
 * The panel that appears when the user is asked to provide authentication.
 *
 */
public class LocalOrRemotePanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 5051556513294844797L;

  private JComboBox combo;
  private JLabel portLabel;
  private JTextField hostName;
  private JTextField port;
  private JPasswordField pwd;
  private JTextField dn;
  private JLabel pwdLabel;
  private JLabel dnLabel;
  private String usedUrl;
  private JLabel localInstallLabel;
  private JEditorPane localInstall;

  private JLabel localNotRunning;

  private boolean isLocalServerRunning;

  private boolean callOKWhenVisible;

  private static final Logger LOG =
    Logger.getLogger(LocalOrRemotePanel.class.getName());

  /**
   * Default constructor.
   *
   */
  public LocalOrRemotePanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_LOCAL_OR_REMOTE_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.OK_CANCEL;
  }

  /**
   * Returns the displayed host name.
   * @return the displayed host name.
   */
  public String getHostName()
  {
    return hostName.getText();
  }

  /**
   * Returns the displayed administration port.
   * @return the displayed administration port.
   */
  public int getPort()
  {
    int port = -1;
    try
    {
      port = new Integer(this.port.getText().trim());
    }
    catch (Exception ex)
    {
      // Ignore
    }
    return port;
  }

  /**
   * Returns the displayed bind DN.
   * @return the displayed bind DN.
   */
  public String getBindDN()
  {
    return dn.getText();
  }

  /**
   * Returns the displayed password.
   * @return the displayed password.
   */
  public char[] getBindPassword()
  {
    return pwd.getPassword();
  }

  /**
   * Returns whether the panel displays the remote or the local server.
   * @return whether the panel displays the remote or the local server.
   */
  public boolean isRemote()
  {
    int index = combo.getSelectedIndex();
    return index == 1;
  }

  /**
   * Sets the displayed host name.
   * @param hostName the host name.
   */
  public void setHostName(String hostName)
  {
    this.hostName.setText(hostName);
  }

  /**
   * Sets the displayed administration port.
   * @param port the displayed administration port.
   */
  public void setPort(int port)
  {
    this.port.setText(String.valueOf(port));
  }

  /**
   * Sets the displayed bind DN.
   * @param bindDN the displayed bind DN.
   */
  public void setBindDN(String bindDN)
  {
    this.dn.setText(bindDN);
  }

  /**
   * Sets the displayed password.
   * @param pwd the password.
   */
  public void setBindPassword(char[] pwd)
  {
    this.pwd.setText(new String(pwd));
  }

  /**
   * Sets whether the panel should display the remote or the local server.
   * @param remote whether the panel should display the remote or the local
   * server.
   */
  public void setRemote(boolean remote)
  {
    int index = remote ? 1 : 0;
    combo.setSelectedIndex(index);
    updateComponentState();
  }

  /**
   * Method to be called when we want the panel to call automatically okClicked
   * method when the panel is made visible.
   * @param callOKWhenVisible whether okClicked must be called automatically
   * when the panel is made visible or not.
   */
  public void setCallOKWhenVisible(boolean callOKWhenVisible)
  {
    this.callOKWhenVisible = callOKWhenVisible;
  }

  /**
   * Returns whether okClicked must be called automatically when the panel is
   * made visible or not.
   * @return whether okClicked must be called automatically when the panel is
   * made visible or not.
   */
  public boolean isCallOKWhenVisible()
  {
    return callOKWhenVisible;
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
    gbc.weighty = 0.0;
    String localServerInstallPath;
    File instancePath = Installation.getLocal().getInstanceDirectory();
    try
    {
      localServerInstallPath = instancePath.getCanonicalPath();
    }
    catch (IOException ioe)
    {
      localServerInstallPath = instancePath.getAbsolutePath();
    }
    combo = Utilities.createComboBox();
    combo.setModel(new DefaultComboBoxModel(
        new Object[] {INFO_CTRL_PANEL_LOCAL_SERVER.get(),
            INFO_CTRL_PANEL_REMOTE_SERVER.get()}));
    combo.setSelectedIndex(0);
    gbc.gridwidth = 2;
    JLabel l = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_LOCAL_OR_REMOTE.get());
    add(l, gbc);
    gbc.gridwidth = 1;
    gbc.insets.top = 10;
    gbc.gridy ++;
    add(combo, gbc);
    l.setLabelFor(combo);
    gbc.gridx = 1;

    localNotRunning = Utilities.createDefaultLabel();
    Utilities.setWarningLabel(localNotRunning,
        INFO_CTRL_PANEL_LOCAL_SERVER_NOT_RUNNING.get());
    gbc.insets.left = 10;
    add(localNotRunning, gbc);
    localNotRunning.setFocusable(true);
    hostName = Utilities.createMediumTextField();
    hostName.setText(UserData.getDefaultHostName());
    hostName.setToolTipText(
        INFO_CTRL_PANEL_REMOTE_SERVER_TOOLTIP.get().toString());
    add(hostName, gbc);
    gbc.insets.top = 10;
    gbc.gridy ++;
    gbc.insets.left = 0;
    gbc.weightx = 0.0;
    gbc.insets.right = 0;
    gbc.gridx = 0;

    ActionListener actionListener = new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        updateComponentState();
      }
    };
    combo.addActionListener(actionListener);

    gbc.gridx = 0;
    gbc.gridwidth = 1;


    localInstallLabel = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_INSTANCE_PATH_LABEL.get());
    gbc.insets.left = 0;
    add(localInstallLabel, gbc);
    gbc.gridx = 1;
    gbc.insets.left = 10;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 0.1;
    localInstall = Utilities.makeHtmlPane(localServerInstallPath,
        ColorAndFontConstants.defaultFont);
    add(localInstall, gbc);
    localInstallLabel.setLabelFor(localInstall);
    gbc.gridx ++;
    gbc.weightx = 1.0;
    gbc.insets.left = 0;
    add(Box.createHorizontalGlue(), gbc);

    gbc.gridy ++;
    gbc.insets.top = 10;
    gbc.insets.left = 0;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    portLabel = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_ADMINISTRATION_PORT.get());
    add(portLabel, gbc);
    gbc.gridx = 1;
    gbc.insets.left = 10;
    port = Utilities.createMediumTextField();
    port.setText(String.valueOf(
        ControlPanelArgumentParser.getDefaultAdministrationPort()));
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(port, gbc);
    portLabel.setLabelFor(port);

    gbc.gridy ++;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets.left = 0;
    dnLabel = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_BIND_DN_LABEL.get());
    add(dnLabel, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    dn = Utilities.createTextField(
        ControlPanelArgumentParser.getDefaultBindDN(), 20);
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.left = 10;
    add(dn, gbc);
    dnLabel.setLabelFor(dn);
    gbc.insets.top = 10;
    gbc.insets.left = 0;

    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    pwdLabel = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_BIND_PASSWORD_LABEL.get());
    gbc.insets.left = 0;
    add(pwdLabel, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    pwd = Utilities.createPasswordField();
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(pwd, gbc);
    pwdLabel.setLabelFor(pwd);

    addBottomGlue(gbc);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Component getPreferredFocusComponent()
  {
    if (pwd.isVisible())
    {
      return pwd;
    }
    else
    {
      return combo;
    }
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
  @Override
  public void toBeDisplayed(boolean visible)
  {
    super.toBeDisplayed(visible);
    if (visible)
    {
      // Do it outside the event thread if the panel requires it.
      BackgroundTask<Void> worker = new BackgroundTask<Void>()
      {
        @Override
        public Void processBackgroundTask() throws Throwable
        {
          try
          {
            Thread.sleep(200);
          }
          catch (Throwable t)
          {
          }
          File instancePath = Installation.getLocal().getInstanceDirectory();
          isLocalServerRunning = Utilities.isServerRunning(instancePath);
          return null;
        }


        @Override
        public void backgroundTaskCompleted(Void returnValue,
            Throwable t)
        {
          updateComponentState();
          displayMainPanel();
          Component comp = getPreferredFocusComponent();
          if (comp != null)
          {
            comp.requestFocusInWindow();
          }
          if (isCallOKWhenVisible())
          {
            okClicked();
          }
        }
      };
      displayMessage(INFO_CTRL_PANEL_LOADING_PANEL_SUMMARY.get());
      worker.startBackgroundTask();
      if (!isCallOKWhenVisible())
      {
        pwd.setText("");
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void okClicked()
  {
    setPrimaryValid(portLabel);
    setPrimaryValid(dnLabel);
    setPrimaryValid(pwdLabel);
    final LinkedHashSet<Message> errors = new LinkedHashSet<Message>();

    boolean dnInvalid = false;
    boolean pwdInvalid = false;

    final boolean isLocal = combo.getSelectedIndex() == 0;

    boolean doChecks = !isLocal || isLocalServerRunning;
    if (doChecks)
    {
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

      if (!isLocal)
      {
        if ("".equals(hostName.getText().trim()))
        {
          errors.add(INFO_EMPTY_REMOTE_HOST_NAME.get());
        }

        try
        {
          int p = Integer.parseInt(port.getText());
          if ((p <= 0) || (p > 65535))
          {
            errors.add(INFO_INVALID_REMOTE_SERVER_PORT.get(0, 65535));
          }
        }
        catch (Throwable t)
        {
          errors.add(INFO_INVALID_REMOTE_SERVER_PORT.get(0, 65535));
        }
      }
    }

    if (errors.isEmpty())
    {
      setEnabledOK(false);
      displayMessage(INFO_CTRL_PANEL_VERIFYING_AUTHENTICATION_SUMMARY.get());

      BackgroundTask<InitialLdapContext> worker =
        new BackgroundTask<InitialLdapContext>()
      {
        /**
         * {@inheritDoc}
         */
        @Override
        public InitialLdapContext processBackgroundTask() throws Throwable
        {
          getInfo().stopPooling();
          if (isLocal)
          {
            // At least load the local information.
            SwingUtilities.invokeLater(new Runnable()
            {
              public void run()
              {
                displayMessage(
                    INFO_CTRL_PANEL_READING_CONFIGURATION_SUMMARY.get());
              }
            });
            if (getInfo().isLocal() != isLocal)
            {
              closeInfoConnections();
            }
            getInfo().setIsLocal(isLocal);
            getInfo().regenerateDescriptor();
            if (!isLocalServerRunning)
            {
              return null;
            }
          }
          InitialLdapContext ctx = null;
          try
          {
            if (isLocal)
            {
              usedUrl = getInfo().getAdminConnectorURL();
              ctx = Utilities.getAdminDirContext(getInfo(), dn.getText(),
                  String.valueOf(pwd.getPassword()));
            }
            else
            {
              usedUrl = ConnectionUtils.getLDAPUrl(hostName.getText().trim(),
                  new Integer(port.getText().trim()), true);
              ctx = Utils.createLdapsContext(usedUrl, dn.getText(),
                  String.valueOf(pwd.getPassword()),
                  getInfo().getConnectTimeout(), null,
                  getInfo().getTrustManager());
              checkVersion(ctx);
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
            closeInfoConnections();
            getInfo().setIsLocal(isLocal);
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
        @Override
        public void backgroundTaskCompleted(InitialLdapContext ctx,
            Throwable throwable)
        {
          boolean handleCertificateException = false;

          boolean localServerErrorConnecting = false;

          if (throwable != null)
          {
            LOG.log(Level.INFO, "Error connecting: " + throwable, throwable);

            if (isVersionException(throwable))
            {
              errors.add(((OpenDsException)throwable).getMessageObject());
            }
            else if (Utils.isCertificateException(throwable))
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
              if (isLocal)
              {
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
                localServerErrorConnecting = true;
              }
              else
              {
                String hostPort = ServerDescriptor.getServerRepresentation(
                    hostName.getText().trim(),
                    new Integer(port.getText().trim()));
                NamingException ne = (NamingException)throwable;
                errors.add(Utils.getMessageForException(ne, hostPort));
                setPrimaryInvalid(portLabel);
              }
              setPrimaryInvalid(dnLabel);
              setPrimaryInvalid(pwdLabel);
            }
            else if (throwable instanceof ConfigReadException)
            {
              LOG.log(Level.WARNING,
                  "Error reading configuration: "+throwable, throwable);
              errors.add(((ConfigReadException)throwable).getMessageObject());
            }
            else
            {
              // This is a bug
              LOG.log(Level.SEVERE,
                  "Unexpected error: "+throwable, throwable);
              errors.add(Utils.getThrowableMsg(INFO_BUG_MSG.get(), throwable));
            }
          }
          displayMainPanel();
          setEnabledOK(true);
          if (!errors.isEmpty())
          {
            if (!localServerErrorConnecting)
            {
              displayErrorDialog(errors);
            }
            else
            {
              ArrayList<String> stringErrors = new ArrayList<String>();
              for (Message err : errors)
              {
                stringErrors.add(err.toString());
              }
              String msg = Utilities.getStringFromCollection(stringErrors,
                  "<br>");
              if (displayConfirmationDialog(
                  INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
                  INFO_CTRL_PANEL_ERROR_CONNECTING_TO_LOCAL.get(msg)))
              {
                Utilities.getParentDialog(
                    LocalOrRemotePanel.this).setVisible(false);
              }
            }
            pwd.setSelectionStart(0);
            pwd.setSelectionEnd(pwd.getPassword().length);
            pwd.requestFocusInWindow();
          }
          else if (!handleCertificateException)
          {
            Utilities.getParentDialog(
                LocalOrRemotePanel.this).setVisible(false);
          }

          if (!handleCertificateException)
          {
            startPooling();
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
  @Override
  public void cancelClicked()
  {
    setPrimaryValid(dnLabel);
    setPrimaryValid(pwdLabel);
    setPrimaryValid(portLabel);
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

  private void updateComponentState()
  {
    boolean isLocal = combo.getSelectedIndex() == 0;
    hostName.setVisible(!isLocal);
    port.setVisible(!isLocal);
    portLabel.setVisible(!isLocal);
    localInstall.setVisible(isLocal);
    localInstallLabel.setVisible(isLocal);

    boolean displayAuthentication = !isLocal || isLocalServerRunning;
    dn.setVisible(displayAuthentication);
    dnLabel.setVisible(displayAuthentication);
    pwd.setVisible(displayAuthentication);
    pwdLabel.setVisible(displayAuthentication);

    localNotRunning.setVisible(isLocal && !isLocalServerRunning);
  }

  private void startPooling()
  {
    // The server descriptor has been already retrieved.
    // startPooling tries to retrieve immediately the server descriptor, so
    // sleep the pooling period before calling it.
    Thread t = new Thread(new Runnable()
    {
      public void run()
      {
        try
        {
          Thread.sleep(getInfo().getPoolingPeriod());
        }
        catch (Throwable t)
        {
        }
        getInfo().startPooling();
      }
    });
    t.start();
  }

  private void checkVersion(InitialLdapContext ctx) throws OpenDsException
  {
    Message msg = null;
    try
    {
      /*
       * Search for the version on the remote server.
       */
      SearchControls searchControls = new SearchControls();
      searchControls.setSearchScope(
      SearchControls.OBJECT_SCOPE);
      searchControls.setReturningAttributes(
      new String[] {
          VersionMonitorProvider.ATTR_PRODUCT_NAME,
          VersionMonitorProvider.ATTR_MAJOR_VERSION,
          VersionMonitorProvider.ATTR_POINT_VERSION,
          VersionMonitorProvider.ATTR_MINOR_VERSION
          });
      NamingEnumeration<SearchResult> en =
        ctx.search("cn=Version,cn=monitor", "objectclass=*",
          searchControls);
      SearchResult sr = null;
      try
      {
        while (en.hasMore())
        {
          sr = en.next();
        }
      }
      finally
      {
        en.close();
      }
      CustomSearchResult csr =
        new CustomSearchResult(sr, "cn=Version,cn=monitor");

      String hostName = ConnectionUtils.getHostName(ctx);

      String productName = String.valueOf(Utilities.getFirstMonitoringValue(csr,
          VersionMonitorProvider.ATTR_PRODUCT_NAME));
      String major = String.valueOf(Utilities.getFirstMonitoringValue(csr,
          VersionMonitorProvider.ATTR_MAJOR_VERSION));
      String point = String.valueOf(Utilities.getFirstMonitoringValue(csr,
          VersionMonitorProvider.ATTR_POINT_VERSION));
      String minor = String.valueOf(Utilities.getFirstMonitoringValue(csr,
          VersionMonitorProvider.ATTR_MINOR_VERSION));
      // Be strict, control panel is only compatible with exactly the same
      // version.
      if (!productName.equalsIgnoreCase(DynamicConstants.PRODUCT_NAME))
      {
        msg = ERR_NOT_SAME_PRODUCT_IN_REMOTE_SERVER_NOT_FOUND.get(hostName,
            productName, DynamicConstants.PRODUCT_NAME);
      }
      else
      {
        if (!String.valueOf(DynamicConstants.MAJOR_VERSION).equals(major) ||
            !String.valueOf(DynamicConstants.MINOR_VERSION).equals(minor) ||
            !String.valueOf(DynamicConstants.POINT_VERSION).equals(point))
        {
          msg = ERR_INCOMPATIBLE_VERSION_IN_REMOTE_SERVER.get(hostName,
              major, minor, point, DynamicConstants.MAJOR_VERSION,
              DynamicConstants.MINOR_VERSION, DynamicConstants.POINT_VERSION);
        }
      }
    }
    catch (Throwable t)
    {
      msg = ERR_VERSION_IN_REMOTE_SERVER_NOT_FOUND.get();
    }
    if (msg != null)
    {
      throw new OnlineUpdateException(msg, null);
    }
  }

  private boolean isVersionException(Throwable t)
  {
    boolean isVersionException = false;
    if (t instanceof OpenDsException)
    {
      OpenDsException oe = (OpenDsException)t;
      if (oe.getMessageObject() != null)
      {
        if (oe.getMessageObject().getDescriptor().equals
            (ERR_INCOMPATIBLE_VERSION_IN_REMOTE_SERVER) ||
            oe.getMessageObject().getDescriptor().equals
            (ERR_VERSION_IN_REMOTE_SERVER_NOT_FOUND) ||
            oe.getMessageObject().getDescriptor().equals
            (ERR_NOT_SAME_PRODUCT_IN_REMOTE_SERVER_NOT_FOUND))
        {
          isVersionException = true;
        }
      }
    }
    return isVersionException;
  }

  private void closeInfoConnections()
  {
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
  }
}
