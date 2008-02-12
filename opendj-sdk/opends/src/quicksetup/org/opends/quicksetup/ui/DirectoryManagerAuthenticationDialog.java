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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;

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
import javax.swing.text.JTextComponent;

import org.opends.quicksetup.Installation;
import org.opends.quicksetup.event.MinimumSizeComponentListener;

import org.opends.quicksetup.util.BackgroundTask;
import static org.opends.quicksetup.util.Utils.*;
import static org.opends.quicksetup.ui.Utilities.*;
import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;


/**
 * This class is a dialog that appears when the user must provide authentication
 * of Directory Manager to connect to the Directory Server in order to be
 * able to do a shutdown.
 */
public class DirectoryManagerAuthenticationDialog extends JDialog
{
  private static final Logger LOG =
          Logger.getLogger(
                  DirectoryManagerAuthenticationDialog.class.getName());

  private static final long serialVersionUID = 9049409381101152000L;

  private JFrame parent;

  private JLabel lDn;
  private JLabel lPwd;

  private JTextField tfDn;
  private JTextField tfPwd;

  private JButton cancelButton;
  private JButton shutDownButton;

  private boolean isCancelled;

  private boolean isServerRunning;

  /**
   * Constructor of the DirectoryManagerAuthenticationDialog.
   * @param parent the parent frame for this dialog.
   * status.
   */
  public DirectoryManagerAuthenticationDialog(JFrame parent)
  {
    super(parent);
    setTitle(INFO_SHUTDOWN_DIRECTORY_MANAGER_DIALOG_TITLE.get().toString());
    this.parent = parent;
    getContentPane().add(createPanel());
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
   * Packs and displays this dialog.
   *
   */
  public void packAndShow()
  {
    /*
     * TODO: find a way to calculate this dynamically.  This is done to avoid
     * all the text in a single line.
     */
    setPreferredSize(new Dimension(500, 300));
    addComponentListener(new MinimumSizeComponentListener(this,
        500, 300));
    getRootPane().setDefaultButton(shutDownButton);
    pack();
    Utilities.centerOnComponent(this, parent);
    tfPwd.requestFocusInWindow();
    setVisible(true);
  }

  /**
   * Tells whether we must stop the server or not.
   * @return <CODE>true</CODE> if we must stop the server and <CODE>false</CODE>
   * otherwise
   */
  public boolean getStopServer()
  {
    return isServerRunning;
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
    Message msg = INFO_SHUTDOWN_DIRECTORY_MANAGER_DIALOG_MSG.get();
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
        INFO_SHUTDOWN_DIRECTORY_MANAGER_DN_LABEL.get(),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    p2.add(lDn, gbc);
    gbc.weightx = 1.0;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    tfDn = UIFactory.makeJTextField(
            Message.raw(getProposedDirectoryManagerDn()),
            INFO_SHUTDOWN_DIRECTORY_MANAGER_DN_TOOLTIP.get(),
            UIFactory.DN_FIELD_SIZE, UIFactory.TextStyle.TEXTFIELD);
    p2.add(tfDn, gbc);

    gbc.insets.top = 0;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.insets.left = 0;
    lPwd = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_SHUTDOWN_DIRECTORY_MANAGER_PWD_LABEL.get(),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    p2.add(lPwd, gbc);
    gbc.weightx = 1.0;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    JPanel p3 = new JPanel(new GridBagLayout());
    p3.setOpaque(false);
    tfPwd = UIFactory.makeJPasswordField(null,
        INFO_SHUTDOWN_DIRECTORY_MANAGER_PWD_TOOLTIP.get(),
        UIFactory.PASSWORD_FIELD_SIZE, UIFactory.TextStyle.PASSWORD_FIELD);
    p2.add(tfPwd, gbc);
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
    shutDownButton =
      UIFactory.makeJButton(INFO_SHUTDOWN_BUTTON_LABEL.get(),
          INFO_SHUTDOWN_DIRECTORY_MANAGER_SHUTDOWN_BUTTON_TOOLTIP.get());
    buttonPanel.add(shutDownButton, gbc);
    shutDownButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        shutDownClicked();
      }
    });

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.left = UIFactory.HORIZONTAL_INSET_BETWEEN_BUTTONS;
    cancelButton =
      UIFactory.makeJButton(INFO_CANCEL_BUTTON_LABEL.get(),
          INFO_SHUTDOWN_DIRECTORY_MANAGER_CANCEL_BUTTON_TOOLTIP.get());
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

  private String getProposedDirectoryManagerDn()
  {
    String dn;
    Set<String> dns = null;
    try {
      dns = Installation.getLocal().getCurrentConfiguration().
              getDirectoryManagerDns();
    } catch (IOException ioe) {
      LOG.log(Level.INFO, "error obtaining dirmanager DNs", ioe);
    }
    if (dns != null && dns.size() > 0)
    {
      dn = dns.iterator().next();
    }
    else
    {
      dn = null;
    }
    return dn;
  }

  private void cancelClicked()
  {
    isCancelled = true;
    dispose();
  }

  private void shutDownClicked()
  {
    isCancelled = false;
    BackgroundTask worker = new BackgroundTask()
    {
      public Object processBackgroundTask() throws NamingException
      {
        Boolean isServerRunning = Boolean.TRUE;
        try
        {
          Installation installation = Installation.getLocal();
          int port = installation.getCurrentConfiguration().getPort();
          String ldapUrl = "ldap://localhost:"+port;
          InitialLdapContext ctx =
            createLdapContext(ldapUrl, tfDn.getText(),
              tfPwd.getText(), getDefaultLDAPTimeout(), null);

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
          if (Installation.getLocal().getStatus().isServerRunning())
          {
            throw ne;
          }
          isServerRunning = Boolean.FALSE;
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
          if (throwable instanceof NamingException)
          {
            boolean dnInvalid = false;
            boolean pwdInvalid = false;

            String dn = tfDn.getText();
            ArrayList<Message> possibleCauses = new ArrayList<Message>();
            if ("".equals(dn.trim()))
            {
              dnInvalid = true;
              possibleCauses.add(INFO_EMPTY_DIRECTORY_MANAGER_DN.get());
            }
            else if (!isDn(dn))
            {
              dnInvalid = true;
              possibleCauses.add(INFO_NOT_A_DIRECTORY_MANAGER_DN.get());
            }
            else
            {
              boolean found = false;
              try {
                Set<String> dns;
                dns = Installation.getLocal().getCurrentConfiguration()
                        .getDirectoryManagerDns();
                Iterator<String> it = dns.iterator();
                while (it.hasNext() && !found)
                {
                  found = areDnsEqual(dn, it.next());
                }
              } catch (IOException ioe) {
                LOG.log(Level.INFO, "error obtaining dirmanager DNs", ioe);
              }
              if (!found)
              {
                dnInvalid = true;
                possibleCauses.add(
                        INFO_NOT_A_DIRECTORY_MANAGER_IN_CONFIG.get());
              }
            }

            if ("".equals(tfPwd.getText()))
            {
              pwdInvalid = true;
              possibleCauses.add(INFO_EMPTY_PWD.get());
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
              displayError(parent,
                  INFO_CANNOT_CONNECT_TO_SHUTDOWN_WITH_CAUSE.get(
                          getMessageFromCollection(possibleCauses, "\n")),
                  INFO_ERROR_TITLE.get());
            }
            else
            {
              // Generic message
              displayError(parent,
                  INFO_CANNOT_CONNECT_TO_SHUTDOWN_WITHOUT_CAUSE.get(),
                  INFO_ERROR_TITLE.get());
            }
          }
          else
          {
            // This is a bug
            throwable.printStackTrace();
            displayError(parent,
                getThrowableMsg(INFO_BUG_MSG.get(), throwable),
                INFO_ERROR_TITLE.get());
          }
          cancelButton.setEnabled(true);
          shutDownButton.setEnabled(true);
        } else
        {
          isServerRunning = Boolean.TRUE.equals(returnValue);
          if (!isServerRunning)
          {
            displayInformationMessage(parent, INFO_SERVER_NOT_RUNNING_MSG.get(),
                INFO_SERVER_NOT_RUNNING_TITLE.get());
          }
          isCancelled = false;
          dispose();
        }
      }
    };
    cancelButton.setEnabled(false);
    shutDownButton.setEnabled(false);
    worker.startBackgroundTask();
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
      DirectoryManagerAuthenticationDialog dlg =
          new DirectoryManagerAuthenticationDialog(new JFrame());
      dlg.packAndShow();
    } catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }
}
