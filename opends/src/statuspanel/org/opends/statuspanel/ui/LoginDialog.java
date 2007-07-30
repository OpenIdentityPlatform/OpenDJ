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

package org.opends.statuspanel.ui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.InitialLdapContext;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.Step;
import org.opends.quicksetup.UserDataCertificateException;
import org.opends.quicksetup.event.MinimumSizeComponentListener;
import org.opends.quicksetup.ui.CertificateDialog;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.Utilities;
import org.opends.quicksetup.util.BackgroundTask;
import org.opends.quicksetup.util.Utils;

import org.opends.statuspanel.ConfigException;
import org.opends.statuspanel.ConfigFromFile;
import org.opends.statuspanel.ConnectionProtocolPolicy;
import org.opends.statuspanel.i18n.ResourceProvider;

/**
 * This class is a dialog that appears when the user must provide authentication
 * to connect to the Directory Server in order to be able to display
 * information.
 */
public class LoginDialog extends JDialog
{
  private static final long serialVersionUID = 9049606381601152500L;

  private JFrame parent;

  private JLabel lDn;
  private JLabel lPwd;

  private JTextField tfDn;
  private JTextField tfPwd;

  private JButton cancelButton;
  private JButton okButton;

  private boolean isCancelled = true;

  private ConfigFromFile conf;

  private ApplicationTrustManager trustManager;
  private ConnectionProtocolPolicy policy;

  private String usedUrl;

  private static final Logger LOG =
    Logger.getLogger(LoginDialog.class.getName());

  /**
   * Constructor of the LoginDialog.
   * @param parent the parent frame for this dialog.
   * @param trustManager the trust manager to be used for the secure
   * connections.
   * @param policy the configuration policy to be used (whether we prefer the
   * most secure, the less secure, a specific method...).
   */
  public LoginDialog(JFrame parent, ApplicationTrustManager trustManager,
      ConnectionProtocolPolicy policy)
  {
    super(parent);
    setTitle(getMsg("login-dialog-title"));
    this.parent = parent;
    getContentPane().add(createPanel());
    if (trustManager == null)
    {
      throw new IllegalArgumentException("The trustmanager cannot be null.");
    }
    this.trustManager = trustManager;
    this.policy = policy;
    /*
     * TODO: find a way to calculate this dynamically.  This is done to avoid
     * all the text in a single line.
     */
    setPreferredSize(new Dimension(500, 250));
    addComponentListener(new MinimumSizeComponentListener(this, 500, 250));
    getRootPane().setDefaultButton(okButton);
  }

  /**
   * Returns <CODE>true</CODE> if the user clicked on cancel and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the user clicked on cancel and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isCancelled()
  {
    return isCancelled;
  }

  /**
   * {@inheritDoc}
   *
   */
  public void setVisible(boolean visible)
  {
    cancelButton.setEnabled(true);
    okButton.setEnabled(true);
    if (visible)
    {
      tfPwd.setText("");
      tfPwd.requestFocusInWindow();
      UIFactory.setTextStyle(lDn,
          UIFactory.TextStyle.PRIMARY_FIELD_VALID);
      UIFactory.setTextStyle(lPwd,
          UIFactory.TextStyle.PRIMARY_FIELD_VALID);
      getRootPane().setDefaultButton(okButton);
    }
    super.setVisible(visible);
  }

  /**
   * Returns the Directory Manager DN provided by the user.
   * @return the Directory Manager DN provided by the user.
   */
  public String getDirectoryManagerDn()
  {
    return tfDn.getText();
  }

  /**
   * Returns the Directory Manager password provided by the user.
   * @return the Directory Manager password provided by the user.
   */
  public String getDirectoryManagerPwd()
  {
    return tfPwd.getText();
  }

  /**
   * Creates and returns the panel of the dialog.
   * @return the panel of the dialog.
   */
  private JPanel createPanel()
  {
    JPanel p1 = new JPanel(new GridBagLayout());
    p1.setBackground(UIFactory.CURRENT_STEP_PANEL_BACKGROUND);
    p1.setBorder(UIFactory.DIALOG_PANEL_BORDER);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.insets = UIFactory.getCurrentStepPanelInsets();
    p1.add(UIFactory.makeJLabel(UIFactory.IconType.INFORMATION_LARGE, null,
        UIFactory.TextStyle.NO_STYLE), gbc);
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.left = 0;
    String msg = getMsg("login-dialog-msg");

    JTextComponent textPane =
      UIFactory.makeHtmlPane(msg, UIFactory.INSTRUCTIONS_FONT);
    textPane.setOpaque(false);
    textPane.setEditable(false);
    p1.add(textPane, gbc);

    JPanel p2 = new JPanel(new GridBagLayout());
    p2.setOpaque(false);
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    gbc.insets.left = 0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    lDn = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        getMsg("login-dn-label"),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    p2.add(lDn, gbc);
    gbc.weightx = 1.0;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    tfDn = UIFactory.makeJTextField(getProposedAdministrativeUserDn(),
        getMsg("login-dn-tooltip"),
        UIFactory.DN_FIELD_SIZE, UIFactory.TextStyle.TEXTFIELD);
    p2.add(tfDn, gbc);

    gbc.insets.top = 0;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.insets.left = 0;
    lPwd = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        getMsg("login-pwd-label"),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    p2.add(lPwd, gbc);
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    JPanel p3 = new JPanel(new GridBagLayout());
    p3.setOpaque(false);
    tfPwd = UIFactory.makeJPasswordField(null,
        getMsg("login-pwd-tooltip"),
        UIFactory.PASSWORD_FIELD_SIZE, UIFactory.TextStyle.PASSWORD_FIELD);
    p2.add(p3, gbc);
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    p3.add(tfPwd, gbc);
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    p3.add(Box.createHorizontalGlue(), gbc);


    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.insets.top = 0;
    p1.add(Box.createHorizontalGlue(), gbc);
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    p1.add(p2, gbc);
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.VERTICAL;
    p1.add(Box.createVerticalGlue(), gbc);

    JPanel buttonPanel = new JPanel(new GridBagLayout());
    buttonPanel.setOpaque(false);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = 3;
    buttonPanel.add(Box.createHorizontalGlue(), gbc);
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0.0;
    okButton =
      UIFactory.makeJButton(getMsg("ok-button-label"),
          getMsg("login-ok-button-tooltip"));
    buttonPanel.add(okButton, gbc);
    okButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        okClicked();
      }
    });

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.left = UIFactory.HORIZONTAL_INSET_BETWEEN_BUTTONS;
    cancelButton =
      UIFactory.makeJButton(getMsg("cancel-button-label"),
          getMsg("login-cancel-button-tooltip"));
    buttonPanel.add(cancelButton, gbc);
    cancelButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        cancelClicked();
      }
    });

    JPanel p = new JPanel(new GridBagLayout());
    p.setBackground(UIFactory.DEFAULT_BACKGROUND);
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    p.add(p1, gbc);
    gbc.weighty = 0.0;
    gbc.insets = UIFactory.getButtonsPanelInsets();
    p.add(buttonPanel, gbc);

    return p;
  }

  /**
   * Returns the first administrative user DN found in the configuration file.
   * @return the first administrative user DN found in the configuration file.
   */
  private String getProposedAdministrativeUserDn()
  {
    String dn;
    Set<String> dns = getAdministrativeUserDns();
    if (dns.size() > 0)
    {
      dn = dns.iterator().next();
    }
    else
    {
      dn = null;
    }
    return dn;
  }

  /**
   * Method called when user clicks on cancel.
   *
   */
  private void cancelClicked()
  {
    isCancelled = true;
    dispose();
  }

  /**
   * Method called when user clicks on OK.
   *
   */
  private void okClicked()
  {
    BackgroundTask worker = new BackgroundTask()
    {
      public Object processBackgroundTask() throws NamingException
      {
        Boolean isServerRunning = Boolean.TRUE;
        InitialLdapContext ctx = null;
        try
        {
          String ldapUrl = getConfig().getLDAPURL();
          String startTlsUrl = getConfig().getStartTLSURL();
          String ldapsUrl = getConfig().getLDAPSURL();
          switch (policy)
          {
          case USE_STARTTLS:
            if (startTlsUrl != null)
            {
              usedUrl = startTlsUrl;
              ctx = Utils.createStartTLSContext(startTlsUrl, tfDn.getText(),
                  tfPwd.getText(), Utils.getDefaultLDAPTimeout(), null,
                  getTrustManager(), null);
            }
            else
            {
              throw new ConfigException(getMsg("could-not-find-valid-ldapurl"));
            }
            break;
          case USE_LDAPS:
            if (ldapsUrl != null)
            {
              usedUrl = ldapsUrl;
              ctx = Utils.createLdapsContext(ldapsUrl, tfDn.getText(),
                  tfPwd.getText(), Utils.getDefaultLDAPTimeout(), null,
                  getTrustManager());
            }
            else
            {
              throw new ConfigException(getMsg("could-not-find-valid-ldapurl"));
            }
            break;
          case USE_LDAP:
            if (ldapUrl != null)
            {
              usedUrl = ldapUrl;
              ctx = Utils.createLdapContext(ldapUrl, tfDn.getText(),
                    tfPwd.getText(), Utils.getDefaultLDAPTimeout(), null);
            }
            else
            {
              throw new ConfigException(getMsg("could-not-find-valid-ldapurl"));
            }
            break;
          case USE_MOST_SECURE_AVAILABLE:
            if (ldapsUrl != null)
            {
              usedUrl = ldapsUrl;
              ctx = Utils.createLdapsContext(ldapsUrl, tfDn.getText(),
                  tfPwd.getText(), Utils.getDefaultLDAPTimeout(), null,
                  getTrustManager());
            }
            else if (startTlsUrl != null)
            {
              usedUrl = startTlsUrl;
              ctx = Utils.createStartTLSContext(startTlsUrl, tfDn.getText(),
                  tfPwd.getText(), Utils.getDefaultLDAPTimeout(), null,
                  getTrustManager(), null);
            }
            else if (ldapUrl != null)
            {
              usedUrl = ldapUrl;
              ctx = Utils.createLdapContext(ldapUrl, tfDn.getText(),
                    tfPwd.getText(), Utils.getDefaultLDAPTimeout(), null);
            }
            else
            {
              throw new ConfigException(getMsg("could-not-find-valid-ldapurl"));
            }
            break;
          case USE_LESS_SECURE_AVAILABLE:
            if (ldapUrl != null)
            {
              usedUrl = ldapUrl;
              ctx = Utils.createLdapContext(ldapUrl, tfDn.getText(),
                    tfPwd.getText(), Utils.getDefaultLDAPTimeout(), null);
            }
            else if (ldapsUrl != null)
            {
              usedUrl = ldapsUrl;
              ctx = Utils.createLdapsContext(ldapsUrl, tfDn.getText(),
                  tfPwd.getText(), Utils.getDefaultLDAPTimeout(), null,
                  getTrustManager());
            }
            else
            {
              throw new ConfigException(getMsg("could-not-find-valid-ldapurl"));
            }
            break;
            default:
              throw new IllegalStateException("Unknown connection policy: "+
                  policy);
          }

          /*
           * Search for the config to check that it is the directory manager.
           */
          SearchControls searchControls = new SearchControls();
          searchControls.setCountLimit(1);
          searchControls.setSearchScope(
          SearchControls. OBJECT_SCOPE);
          searchControls.setReturningAttributes(
          new String[] {"dn"});
          ctx.search("cn=config", "objectclass=*", searchControls);

        } catch (NamingException ne)
        {
          if (isServerRunning())
          {
            throw ne;
          }
          isServerRunning = Boolean.FALSE;
        } catch (Error e)
        {
          throw e;
        } catch (IllegalStateException ise)
        {
          throw ise;

        } catch (Throwable t)
        {
          throw new IllegalStateException("Unexpected throwable.", t);
        }
        finally
        {
          if (ctx != null)
          try
          {
            ctx.close();
          }
          catch (Throwable t)
          {
          }
        }
        return isServerRunning;
      }

      public void backgroundTaskCompleted(Object returnValue,
          Throwable throwable)
      {
        if (throwable != null)
        {
          LOG.log(Level.INFO, "Error connecting: " + throwable, throwable);
          if (Utils.isCertificateException(throwable))
          {
            ApplicationTrustManager.Cause cause =
              trustManager.getLastRefusedCause();

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
              String msg = Utils.getThrowableMsg(getI18n(),
                  "error-connecting-to-local", null, throwable);
              displayError(msg, getMsg("error-title"));
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
                h = getMsg("not-available-label");
                p = -1;
              }
              UserDataCertificateException udce =
              new UserDataCertificateException(Step.REPLICATION_OPTIONS,
                  getMsg("certificate-exception",
                      new String[] {h, String.valueOf(p)}),
                  throwable, h, p,
                  getTrustManager().getLastRefusedChain(),
                  getTrustManager().getLastRefusedAuthType(), excType);

              handleCertificateException(udce);
            }
          }
          else if (throwable instanceof NamingException)
          {
            boolean dnInvalid = false;
            boolean pwdInvalid = false;

            String dn = tfDn.getText();
            ArrayList<String> possibleCauses = new ArrayList<String>();
            if ("".equals(dn.trim()))
            {
              dnInvalid = true;
              possibleCauses.add(getMsg("empty-directory-manager-dn"));
            }
            else if (!Utils.isDn(dn))
            {
              dnInvalid = true;
              possibleCauses.add(getMsg("not-a-directory-manager-dn"));
            }
            else
            {
              boolean found = false;
              Iterator<String> it = getAdministrativeUserDns().iterator();
              while (it.hasNext() && !found)
              {
                found = Utils.areDnsEqual(dn, it.next());
              }
              if (!found)
              {
                dnInvalid = true;
                possibleCauses.add(getMsg("not-a-directory-manager-in-config"));
              }
            }

            if ("".equals(tfPwd.getText()))
            {
              pwdInvalid = true;
              possibleCauses.add(getMsg("empty-pwd"));
            }
            if (dnInvalid)
            {
              UIFactory.setTextStyle(lDn,
                UIFactory.TextStyle.PRIMARY_FIELD_INVALID);
            }
            else
            {
              UIFactory.setTextStyle(lDn,
                  UIFactory.TextStyle.PRIMARY_FIELD_VALID);
              pwdInvalid = true;
            }
            if (pwdInvalid)
            {
              UIFactory.setTextStyle(lPwd,
                UIFactory.TextStyle.PRIMARY_FIELD_INVALID);
            }
            else
            {
              UIFactory.setTextStyle(lPwd,
                  UIFactory.TextStyle.PRIMARY_FIELD_VALID);
            }
            if (possibleCauses.size() > 0)
            {
              // Message with causes
              String[] arg = {
                  Utils.getStringFromCollection(possibleCauses, "\n")
              };
              displayError(
                  getMsg("cannot-connect-to-login-with-cause", arg),
                  getMsg("error-title"));
            }
            else
            {
              // Generic message
              displayError(
                  getMsg("cannot-connect-to-login-without-cause"),
                  getMsg("error-title"));
            }
          }
          else if (throwable instanceof ConfigException)
          {
            displayError(throwable.getMessage(), getMsg("error-title"));
          }
          else
          {
            // This is a bug
            throwable.printStackTrace();
            displayError(
                Utils.getThrowableMsg(getI18n(), "bug-msg", null, throwable),
                getMsg("error-title"));
          }
          cancelButton.setEnabled(true);
          okButton.setEnabled(true);
        } else
        {
          if (Boolean.FALSE.equals(returnValue))
          {
            displayInformationMessage(
                getMsg("login-dialog-server-not-running-msg"),
                getMsg("login-dialog-server-not-running-title"));
          }
          UIFactory.setTextStyle(lDn,
              UIFactory.TextStyle.PRIMARY_FIELD_VALID);
          UIFactory.setTextStyle(lPwd,
              UIFactory.TextStyle.PRIMARY_FIELD_VALID);
          isCancelled = false;
          cancelButton.setEnabled(true);
          okButton.setEnabled(true);
          dispose();
        }
      }
    };
    cancelButton.setEnabled(false);
    okButton.setEnabled(false);
    worker.startBackgroundTask();
  }

  /**
   * Displays an error message dialog.
   *
   * @param msg
   *          the error message.
   * @param title
   *          the title for the dialog.
   */
  private void displayError(String msg, String title)
  {
    Utilities.displayError(parent, msg, title);
    toFront();

  }

  /**
   * Displays an information message dialog.
   *
   * @param msg
   *          the information message.
   * @param title
   *          the title for the dialog.
   */
  private void displayInformationMessage(String msg, String title)
  {
    Utilities.displayInformationMessage(parent, msg, title);
    toFront();
  }

  /**
   * Returns the administrative user DNs found in the config file.
   * @return the administrative user DNs found in the config file.
   */
  private Set<String> getAdministrativeUserDns()
  {
    return getConfig().getAdministrativeUsers();
  }

  /**
   * Returns whether the server is running or not.
   * @return <CODE>true</CODE> if the server is running and <CODE>false</CODE>
   * otherwise.
   */
  private boolean isServerRunning()
  {
    return Installation.getLocal().getStatus().isServerRunning();
  }

  /**
   * Returns the ConfigFromFile object that contains the configuration read
   * from the config file.
   * @return the ConfigFromFile object that contains the configuration read
   * from the config file.
   */
  private ConfigFromFile getConfig()
  {
    if (conf == null)
    {
      conf = new ConfigFromFile();
      conf.readConfiguration();
    }
    return conf;
  }

  /* The following three methods are just commodity methods to retrieve
   * localized messages */
  private String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  private String getMsg(String key, String[] args)
  {
    return getI18n().getMsg(key, args);
  }

  private ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }

  /**
   * Returns the trust manager that can be used to establish secure connections.
   * @return the trust manager that can be used to establish secure connections.
   */
  private ApplicationTrustManager getTrustManager()
  {
    return trustManager;
  }

  /**
   * Displays a dialog asking the user to accept a certificate if the user
   * accepts it, we update the trust manager and simulate a click on "OK" to
   * re-check the authentication.
   * This method assumes that we are being called from the event thread.
   */
  private void handleCertificateException(UserDataCertificateException ce)
  {
    CertificateDialog dlg = new CertificateDialog(parent, ce);
    dlg.pack();
    dlg.setVisible(true);
    if (dlg.isAccepted())
    {
      X509Certificate[] chain = ce.getChain();
      String authType = ce.getAuthType();
      String host = ce.getHost();

      if ((chain != null) && (authType != null) && (host != null))
      {
        LOG.log(Level.INFO, "Accepting certificate presented by host "+host);
        getTrustManager().acceptCertificate(chain, authType, host);
        /* Simulate a click on the OK */
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            okButton.doClick();
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
  }

  /**
   * Method written for testing purposes.
   * @param args the arguments to be passed to the test program.
   */
  public static void main(String[] args)
  {
    try
    {
      // UIFactory.initialize();
      LoginDialog dlg = new LoginDialog(new JFrame(),
          new ApplicationTrustManager(null),
          ConnectionProtocolPolicy.USE_MOST_SECURE_AVAILABLE);
      dlg.pack();
      dlg.setVisible(true);
    } catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }
}
