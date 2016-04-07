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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.ControlPanelArgumentParser;
import org.opends.guitools.controlpanel.datamodel.ConfigReadException;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.OnlineUpdateException;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.UserDataCertificateException;
import org.opends.quicksetup.ui.CertificateDialog;
import org.opends.quicksetup.util.UIKeyStore;
import org.opends.quicksetup.util.Utils;
import org.opends.server.monitors.VersionMonitorProvider;
import org.opends.server.types.HostPort;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.StaticUtils;

import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.admin.ads.util.PreferredConnection.Type.*;
import static org.opends.guitools.controlpanel.util.Utilities.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.server.monitors.VersionMonitorProvider.*;

/** The panel that appears when the user is asked to provide authentication. */
public class LocalOrRemotePanel extends StatusGenericPanel
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private static final long serialVersionUID = 5051556513294844797L;

  private JComboBox<LocalizableMessage> combo;
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

  /** Default constructor. */
  public LocalOrRemotePanel()
  {
    super();
    createLayout();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_LOCAL_OR_REMOTE_PANEL_TITLE.get();
  }

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
    try
    {
      return Integer.valueOf(this.port.getText().trim());
    }
    catch (Exception ignored)
    {
      return -1;
    }
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

  /** Creates the layout of the panel (but the contents are not populated here). */
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
    combo.setModel(new DefaultComboBoxModel<LocalizableMessage>(
        new LocalizableMessage[] {INFO_CTRL_PANEL_LOCAL_SERVER.get(),
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
      @Override
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

  @Override
  public Component getPreferredFocusComponent()
  {
    if (pwd.isVisible())
    {
      return pwd;
    }
    return combo;
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
      // Do it outside the event thread if the panel requires it.
      BackgroundTask<Void> worker = new BackgroundTask<Void>()
      {
        @Override
        public Void processBackgroundTask() throws Throwable
        {
          StaticUtils.sleep(200);
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

  @Override
  public void okClicked()
  {
    setPrimaryValid(portLabel);
    setPrimaryValid(dnLabel);
    setPrimaryValid(pwdLabel);
    final LinkedHashSet<LocalizableMessage> errors = new LinkedHashSet<>();

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

      if (!isLocal)
      {
        if ("".equals(hostName.getText().trim()))
        {
          errors.add(INFO_EMPTY_REMOTE_HOST_NAME.get());
        }

        try
        {
          int p = Integer.parseInt(port.getText());
          if (p <= 0 || p > 65535)
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

      BackgroundTask<ConnectionWrapper> worker = new BackgroundTask<ConnectionWrapper>()
      {
        @Override
        public ConnectionWrapper processBackgroundTask() throws Throwable
        {
          final ControlPanelInfo info = getInfo();
          info.stopPooling();
          if (isLocal)
          {
            // At least load the local information.
            SwingUtilities.invokeLater(new Runnable()
            {
              @Override
              public void run()
              {
                displayMessage(
                    INFO_CTRL_PANEL_READING_CONFIGURATION_SUMMARY.get());
              }
            });
            if (info.isLocal() != isLocal)
            {
              closeInfoConnections();
            }
            info.setIsLocal(isLocal);
            info.regenerateDescriptor();
            if (!isLocalServerRunning)
            {
              return null;
            }
          }
          ConnectionWrapper conn = null;
          try
          {
            if (isLocal)
            {
              usedUrl = info.getAdminConnectorURL();
              conn = Utilities.getAdminDirContext(info, dn.getText(), String.valueOf(pwd.getPassword()));
            }
            else
            {
              HostPort hostPort = new HostPort(hostName.getText().trim(), Integer.valueOf(port.getText().trim()));
              usedUrl = ConnectionUtils.getLDAPUrl(hostPort, true);
              conn = new ConnectionWrapper(hostPort, LDAPS, dn.getText(), String.valueOf(pwd.getPassword()),
                  info.getConnectTimeout(), info.getTrustManager());
              checkVersion(conn.getLdapContext());
            }

            StaticUtils.sleep(500);
            SwingUtilities.invokeLater(new Runnable()
            {
              @Override
              public void run()
              {
                displayMessage(INFO_CTRL_PANEL_READING_CONFIGURATION_SUMMARY.get());
              }
            });
            closeInfoConnections();
            info.setIsLocal(isLocal);
            info.setConnection(conn);
            info.setUserDataDirContext(null);
            info.regenerateDescriptor();
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
          boolean localServerErrorConnecting = false;

          if (throwable != null)
          {
            logger.info(LocalizableMessage.raw("Error connecting: " + throwable, throwable));

            final ControlPanelInfo info = getInfo();
            if (isVersionException(throwable))
            {
              errors.add(((OpenDsException)throwable).getMessageObject());
            }
            else if (isCertificateException(throwable))
            {
              ApplicationTrustManager.Cause cause =
                info.getTrustManager().getLastRefusedCause();

              logger.info(LocalizableMessage.raw("Certificate exception cause: "+cause));
              UserDataCertificateException.Type excType = null;
              if (cause == ApplicationTrustManager.Cause.NOT_TRUSTED)
              {
                excType = UserDataCertificateException.Type.NOT_TRUSTED;
              }
              else if (cause == ApplicationTrustManager.Cause.HOST_NAME_MISMATCH)
              {
                excType = UserDataCertificateException.Type.HOST_NAME_MISMATCH;
              }
              else
              {
                errors.add(getThrowableMsg(INFO_ERROR_CONNECTING_TO_LOCAL.get(), throwable));
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
                ApplicationTrustManager trustMgr = info.getTrustManager();
                UserDataCertificateException udce =
                  new UserDataCertificateException(null,
                      INFO_CERTIFICATE_EXCEPTION.get(h, p),
                      throwable, h, p,
                      trustMgr.getLastRefusedChain(),
                      trustMgr.getLastRefusedAuthType(),
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
                Iterator<DN> it = info.getServerDescriptor().
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
                HostPort hostPort = new HostPort(
                    hostName.getText().trim(),
                    Integer.valueOf(port.getText().trim()));
                NamingException ne = (NamingException)throwable;
                errors.add(getMessageForException(ne, hostPort.toString()));
                setPrimaryInvalid(portLabel);
              }
              setPrimaryInvalid(dnLabel);
              setPrimaryInvalid(pwdLabel);
            }
            else if (throwable instanceof ConfigReadException)
            {
              logger.warn(LocalizableMessage.raw(
                  "Error reading configuration: "+throwable, throwable));
              errors.add(((ConfigReadException)throwable).getMessageObject());
            }
            else
            {
              // This is a bug
              logger.error(LocalizableMessage.raw(
                  "Unexpected error: "+throwable, throwable));
              errors.add(getThrowableMsg(INFO_BUG_MSG.get(), throwable));
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
              ArrayList<String> stringErrors = new ArrayList<>();
              for (LocalizableMessage err : errors)
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
      @Override
      public void run()
      {
        StaticUtils.sleep(getInfo().getPoolingPeriod());
        getInfo().startPooling();
      }
    });
    t.start();
  }

  private void checkVersion(InitialLdapContext ctx) throws OpenDsException
  {
    LocalizableMessage msg = null;
    try
    {
      // Search for the version on the remote server.
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
        ctx.search("cn=Version,cn=monitor", "objectclass=*", searchControls);
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

      CustomSearchResult csr = new CustomSearchResult(sr, "cn=Version,cn=monitor");

      String hostName = ConnectionUtils.getHostName(ctx);

      String productName = getFirstValueAsString(csr, ATTR_PRODUCT_NAME);
      String major = getFirstValueAsString(csr, ATTR_MAJOR_VERSION);
      String point = getFirstValueAsString(csr, ATTR_POINT_VERSION);
      String minor = getFirstValueAsString(csr, ATTR_MINOR_VERSION);
      // Be strict, control panel is only compatible with exactly the same version
      if (!productName.equalsIgnoreCase(DynamicConstants.PRODUCT_NAME))
      {
        msg = ERR_NOT_SAME_PRODUCT_IN_REMOTE_SERVER_NOT_FOUND.get(hostName,
            productName, DynamicConstants.PRODUCT_NAME);
      }
      else if (!String.valueOf(DynamicConstants.MAJOR_VERSION).equals(major)
          || !String.valueOf(DynamicConstants.MINOR_VERSION).equals(minor)
          || !String.valueOf(DynamicConstants.POINT_VERSION).equals(point))
      {
        msg = ERR_INCOMPATIBLE_VERSION_IN_REMOTE_SERVER.get(hostName,
            major, minor, point, DynamicConstants.MAJOR_VERSION,
            DynamicConstants.MINOR_VERSION, DynamicConstants.POINT_VERSION);
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
    if (t instanceof OpenDsException)
    {
      OpenDsException oe = (OpenDsException)t;
      if (oe.getMessageObject() != null)
      {
        LocalizableMessage msg = oe.getMessageObject();
        if (StaticUtils.hasDescriptor(msg, ERR_INCOMPATIBLE_VERSION_IN_REMOTE_SERVER) ||
            StaticUtils.hasDescriptor(msg, ERR_VERSION_IN_REMOTE_SERVER_NOT_FOUND) ||
            StaticUtils.hasDescriptor(msg, ERR_NOT_SAME_PRODUCT_IN_REMOTE_SERVER_NOT_FOUND))
        {
          return true;
        }
      }
    }
    return false;
  }

  private void closeInfoConnections()
  {
    StaticUtils.close(getInfo().getConnection());
    StaticUtils.close(getInfo().getUserDataDirContext());
  }
}
