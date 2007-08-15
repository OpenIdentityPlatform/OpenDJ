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

package org.opends.guitools.uninstaller.ui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
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

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.guitools.statuspanel.ConfigFromFile;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.Step;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.UserDataCertificateException;
import org.opends.quicksetup.ApplicationReturnCode.ReturnCode;
import org.opends.quicksetup.event.MinimumSizeComponentListener;
import org.opends.quicksetup.ui.CertificateDialog;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.Utilities;
import org.opends.quicksetup.util.BackgroundTask;
import org.opends.quicksetup.util.Utils;

import org.opends.messages.Message;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;

/**
 * This class is a dialog that appears when the user must provide authentication
 * to connect to the Directory Server in order to be able to display
 * information.
 */
public class LoginDialog extends JDialog
{
  private static final long serialVersionUID = 9049409381101152000L;

  private JFrame parent;

  private JLabel lHostName;
  private JLabel lUid;
  private JLabel lPwd;

  private JTextField tfHostName;
  private JTextField tfUid;
  private JTextField tfPwd;

  private JButton cancelButton;
  private JButton okButton;

  private boolean isCancelled = true;

  private ApplicationTrustManager trustManager;

  private InitialLdapContext ctx;

  private String usedUrl;

  private static final Logger LOG =
    Logger.getLogger(LoginDialog.class.getName());

  /**
   * Constructor of the LoginDialog.
   * @param parent the parent frame for this dialog.
   * @param trustManager the trust manager to be used for the secure
   * connections.
   */
  public LoginDialog(JFrame parent, ApplicationTrustManager trustManager)
  {
    super(parent);
    setTitle(INFO_LOGIN_DIALOG_TITLE.get().toString());
    this.parent = parent;
    getContentPane().add(createPanel());
    if (trustManager == null)
    {
      throw new IllegalArgumentException("The trustmanager cannot be null.");
    }
    this.trustManager = trustManager;
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
      UIFactory.setTextStyle(lHostName,
          UIFactory.TextStyle.PRIMARY_FIELD_VALID);
      UIFactory.setTextStyle(lUid,
          UIFactory.TextStyle.PRIMARY_FIELD_VALID);
      UIFactory.setTextStyle(lPwd,
          UIFactory.TextStyle.PRIMARY_FIELD_VALID);
      getRootPane().setDefaultButton(okButton);
    }
    super.setVisible(visible);
  }

  /**
   * Returns the Host Name as is referenced in other servers.
   * @return the Host Name as is referenced in other servers.
   */
  public String getHostName()
  {
    return tfHostName.getText();
  }

  /**
   * Returns the Administrator UID provided by the user.
   * @return the Administrator UID provided by the user.
   */
  public String getAdministratorUid()
  {
    return tfUid.getText();
  }

  /**
   * Returns the Administrator password provided by the user.
   * @return the Administrator password provided by the user.
   */
  public String getAdministratorPwd()
  {
    return tfPwd.getText();
  }

  /**
   * Returns the connection we got with the provided authentication.
   * @return the connection we got with the provided authentication.
   */
  public InitialLdapContext getContext()
  {
    return ctx;
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
    Message msg = INFO_UNINSTALL_LOGIN_DIALOG_MSG.get();

    JTextComponent textPane =
      UIFactory.makeHtmlPane(msg, UIFactory.INSTRUCTIONS_FONT);
    textPane.setOpaque(false);
    textPane.setEditable(false);
    p1.add(textPane, gbc);

    JPanel p2 = new JPanel(new GridBagLayout());
    p2.setOpaque(false);

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    lHostName = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_UNINSTALL_LOGIN_HOST_NAME_LABEL.get(),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    p2.add(lHostName, gbc);
    gbc.weightx = 1.0;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    UserData uData = new UserData();
    tfHostName = UIFactory.makeJTextField(Message.raw(uData.getHostName()),
        INFO_UNINSTALL_LOGIN_HOST_NAME_TOOLTIP.get(),
        UIFactory.HOST_FIELD_SIZE, UIFactory.TextStyle.TEXTFIELD);
    p2.add(tfHostName, gbc);

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.insets.left = 0;
    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    lUid = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_GLOBAL_ADMINISTRATOR_UID_LABEL.get(),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    p2.add(lUid, gbc);
    gbc.weightx = 1.0;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    tfUid = UIFactory.makeJTextField(Message.raw(Constants.GLOBAL_ADMIN_UID),
        INFO_UNINSTALL_LOGIN_UID_TOOLTIP.get(),
        UIFactory.DN_FIELD_SIZE, UIFactory.TextStyle.TEXTFIELD);
    p2.add(tfUid, gbc);

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.insets.left = 0;
    lPwd = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_GLOBAL_ADMINISTRATOR_PWD_LABEL.get(),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    p2.add(lPwd, gbc);
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    JPanel p3 = new JPanel(new GridBagLayout());
    p3.setOpaque(false);
    tfPwd = UIFactory.makeJPasswordField(null,
        INFO_UNINSTALL_LOGIN_PWD_TOOLTIP.get(),
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
    gbc.insets.right = UIFactory.getCurrentStepPanelInsets().right;
    p1.add(p2, gbc);
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.insets.bottom = UIFactory.getCurrentStepPanelInsets().bottom;
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
      UIFactory.makeJButton(INFO_OK_BUTTON_LABEL.get(),
          INFO_UNINSTALL_LOGIN_OK_BUTTON_TOOLTIP.get());
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
      UIFactory.makeJButton(INFO_CANCEL_BUTTON_LABEL.get(),
          INFO_UNINSTALL_LOGIN_CANCEL_BUTTON_TOOLTIP.get());
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
      public Object processBackgroundTask() throws NamingException,
      ApplicationException
      {
        Boolean isServerRunning = Boolean.TRUE;
        ctx = null;
        try
        {
          ConfigFromFile conf = new ConfigFromFile();
          conf.readConfiguration();
          String ldapUrl = conf.getLDAPURL();
          String startTlsUrl = conf.getStartTLSURL();
          String ldapsUrl = conf.getLDAPSURL();
          String dn = ADSContext.getAdministratorDN(tfUid.getText());
          if (ldapsUrl != null)
          {
            usedUrl = ldapsUrl;
            ctx = Utils.createLdapsContext(ldapsUrl, dn, tfPwd.getText(),
                Utils.getDefaultLDAPTimeout(), null, getTrustManager());
          }
          else if (startTlsUrl != null)
          {
            usedUrl = startTlsUrl;
            ctx = Utils.createStartTLSContext(startTlsUrl, dn, tfPwd.getText(),
                Utils.getDefaultLDAPTimeout(), null, getTrustManager(), null);
          }
          else if (ldapUrl != null)
          {
            usedUrl = ldapUrl;
            ctx = Utils.createLdapContext(ldapUrl, dn, tfPwd.getText(),
                Utils.getDefaultLDAPTimeout(), null);
          }
          else
          {
            throw new ApplicationException(ReturnCode.APPLICATION_ERROR,
                INFO_COULD_NOT_FIND_VALID_LDAPURL.get(), null);
          }
        } catch (NamingException ne)
        {
          if (isServerRunning())
          {
            throw ne;
          }
          isServerRunning = Boolean.FALSE;
        } catch (ApplicationException e)
        {
          throw e;
        } catch (IllegalStateException ise)
        {
          throw ise;

        } catch (Throwable t)
        {
          throw new IllegalStateException("Unexpected throwable.", t);
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
              Message msg = Utils.getThrowableMsg(
                  INFO_ERROR_CONNECTING_TO_LOCAL.get(), throwable);
              displayError(msg, INFO_ERROR_TITLE.get());
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
              new UserDataCertificateException(Step.REPLICATION_OPTIONS,
                  INFO_CERTIFICATE_EXCEPTION.get(h, String.valueOf(p)),
                  throwable, h, p,
                  getTrustManager().getLastRefusedChain(),
                  getTrustManager().getLastRefusedAuthType(), excType);

              handleCertificateException(udce);
            }
          }
          else if (throwable instanceof NamingException)
          {
            boolean uidInvalid = false;
            boolean pwdInvalid = false;

            String uid = tfUid.getText();
            ArrayList<Message> possibleCauses = new ArrayList<Message>();
            if ("".equals(uid.trim()))
            {
              uidInvalid = true;
              possibleCauses.add(INFO_EMPTY_ADMINISTRATOR_UID.get());
            }

            if ("".equals(tfPwd.getText()))
            {
              pwdInvalid = true;
              possibleCauses.add(INFO_EMPTY_PWD.get());
            }
            if (uidInvalid)
            {
              UIFactory.setTextStyle(lUid,
                UIFactory.TextStyle.PRIMARY_FIELD_INVALID);
            }
            else
            {
              UIFactory.setTextStyle(lUid,
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
              displayError(
                  INFO_CANNOT_CONNECT_TO_LOGIN_WITH_CAUSE.get(
                          Utils.getMessageFromCollection(possibleCauses, "\n")),
                  INFO_ERROR_TITLE.get());
            }
            else
            {
              // Generic message
              displayError(
                  INFO_CANNOT_CONNECT_TO_LOGIN_WITHOUT_CAUSE.get(),
                  INFO_ERROR_TITLE.get());
            }
          }
          else if (throwable instanceof ApplicationException)
          {
            displayError(((ApplicationException)throwable).getMessageObject(),
                    INFO_ERROR_TITLE.get());
          }
          else
          {
            // This is a bug
            throwable.printStackTrace();
            displayError(
                Utils.getThrowableMsg(INFO_BUG_MSG.get(), throwable),
                INFO_ERROR_TITLE.get());
          }
          cancelButton.setEnabled(true);
          okButton.setEnabled(true);
        } else
        {
          if (Boolean.FALSE.equals(returnValue))
          {
            displayInformationMessage(
                INFO_LOGIN_DIALOG_SERVER_NOT_RUNNING_MSG.get(),
                INFO_LOGIN_DIALOG_SERVER_NOT_RUNNING_TITLE.get());
          }
          else
          {
            String hostName = tfHostName.getText();
            if ((hostName == null) || (hostName.trim().length() == 0))
            {
              displayError(INFO_EMPTY_REMOTE_HOST.get(),
                  INFO_ERROR_TITLE.get());
              UIFactory.setTextStyle(lHostName,
                  UIFactory.TextStyle.PRIMARY_FIELD_INVALID);
            }
            else
            {
              UIFactory.setTextStyle(lHostName,
                UIFactory.TextStyle.PRIMARY_FIELD_VALID);
            }
          }
          UIFactory.setTextStyle(lUid,
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
  private void displayError(Message msg, Message title)
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
  private void displayInformationMessage(Message msg, Message title)
  {
    Utilities.displayInformationMessage(parent, msg, title);
    toFront();
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
  }

  /**
   * Method written for testing purposes.
   * @param args the arguments to be passed to the test program.
   */
  public static void main(String[] args)
  {
    try
    {
      LoginDialog dlg = new LoginDialog(new JFrame(),
          new ApplicationTrustManager(null));
      dlg.pack();
      dlg.setVisible(true);
    } catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }
}
