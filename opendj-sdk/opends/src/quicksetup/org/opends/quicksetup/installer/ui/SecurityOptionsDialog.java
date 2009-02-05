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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.installer.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.security.KeyStoreException;
import java.util.ArrayList;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.opends.quicksetup.SecurityOptions;
import org.opends.quicksetup.event.BrowseActionListener;
import org.opends.quicksetup.event.MinimumSizeComponentListener;
import org.opends.quicksetup.installer.Installer;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.Utilities;
import org.opends.quicksetup.util.BackgroundTask;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.CertificateManager;
import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

/**
 * This class is a dialog that appears when the user wants to configure
 * security parameters for the new OpenDS instance.
 */
public class SecurityOptionsDialog extends JDialog
{
  private static final long serialVersionUID = 4083707346899442215L;

  private JCheckBox cbEnableSSL;
  private JCheckBox cbEnableStartTLS;
  private JTextField tfPort;
  private JRadioButton rbUseSelfSignedCertificate;
  private JRadioButton rbUseExistingCertificate;
  private JLabel lKeystoreType;
  private JRadioButton rbPKCS11;
  private JRadioButton rbJKS;
  private JRadioButton rbJCEKS;
  private JRadioButton rbPKCS12;
  private JLabel lKeystorePath;
  private JTextField tfKeystorePath;
  private JButton browseButton;
  private JLabel lKeystorePwd;
  private JPasswordField tfKeystorePwd;

  private JButton cancelButton;
  private JButton okButton;

  private SelectAliasDialog aliasDlg;

  private boolean isCancelled = true;

  private SecurityOptions securityOptions;

  private String[] aliases;
  private String selectedAlias;

  private final int DEFAULT_PORT = 636;

  /**
   * Constructor of the SecurityOptionsDialog.
   * @param parent the parent frame for this dialog.
   * @param options the SecurityOptions used to populate this dialog.
   * @throws IllegalArgumentException if options is null.
   */
  public SecurityOptionsDialog(JFrame parent, SecurityOptions options)
  throws IllegalArgumentException
  {
    super(parent);
    setTitle(INFO_SECURITY_OPTIONS_DIALOG_TITLE.get().toString());
    securityOptions = options;
    getContentPane().add(createPanel());
    pack();

    updateContents();

    int minWidth = (int) getPreferredSize().getWidth();
    int minHeight = (int) getPreferredSize().getHeight();
    addComponentListener(new MinimumSizeComponentListener(this, minWidth,
        minHeight));
    getRootPane().setDefaultButton(okButton);

    addWindowListener(new WindowAdapter()
    {
      @Override
      public void windowClosing(WindowEvent e)
      {
        cancelClicked();
      }
    });
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

    Utilities.centerOnComponent(this, parent);
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
   * Displays this dialog and populates its contents with the provided
   * SecurityOptions object.
   * @param options the SecurityOptions used to populate this dialog.
   * @throws IllegalArgumentException if options is null.
   */
  public void display(SecurityOptions options) throws IllegalArgumentException
  {
    if (options == null)
    {
      throw new IllegalArgumentException("options parameter cannot be null.");
    }
    UIFactory.setTextStyle(cbEnableSSL,
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    UIFactory.setTextStyle(lKeystorePath,
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    UIFactory.setTextStyle(lKeystorePwd,
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    securityOptions = options;
    updateContents();

    isCancelled = true;

    setVisible(true);
  }

  /**
   * Returns the security options object representing the input of the user
   * in this panel.
   * @return the security options object representing the input of the user
   * in this panel.
   */
  public SecurityOptions getSecurityOptions()
  {
    SecurityOptions ops;

    boolean enableSSL = cbEnableSSL.isSelected();
    boolean enableStartTLS = cbEnableStartTLS.isSelected();
    if (enableSSL || enableStartTLS)
    {
      int sslPort = -1;
      try
      {
        sslPort = Integer.parseInt(tfPort.getText());
      }
      catch (Throwable t)
      {
      }
      if (rbUseSelfSignedCertificate.isSelected())
      {
        ops = SecurityOptions.createSelfSignedCertificateOptions(
            enableSSL, enableStartTLS, sslPort);
      }
      else if (rbJKS.isSelected())
      {
        ops = SecurityOptions.createJKSCertificateOptions(
            tfKeystorePath.getText(),
            String.valueOf(tfKeystorePwd.getPassword()), enableSSL,
            enableStartTLS, sslPort, selectedAlias);
      }
      else if (rbJCEKS.isSelected())
      {
        ops = SecurityOptions.createJCEKSCertificateOptions(
            tfKeystorePath.getText(),
            String.valueOf(tfKeystorePwd.getPassword()), enableSSL,
            enableStartTLS, sslPort, selectedAlias);
      }
      else if (rbPKCS11.isSelected())
      {
        ops = SecurityOptions.createPKCS11CertificateOptions(
            String.valueOf(tfKeystorePwd.getPassword()), enableSSL,
            enableStartTLS, sslPort, selectedAlias);
      }
      else if (rbPKCS12.isSelected())
      {
        ops = SecurityOptions.createPKCS12CertificateOptions(
            tfKeystorePath.getText(),
            String.valueOf(tfKeystorePwd.getPassword()), enableSSL,
            enableStartTLS, sslPort, selectedAlias);
      }
      else
      {
        throw new IllegalStateException("No certificate options selected.");
      }
    }
    else
    {
      ops = SecurityOptions.createNoCertificateOptions();
    }
    return ops;
  }

  /**
   * Creates and returns the panel of the dialog.
   * @return the panel of the dialog.
   */
  private JPanel createPanel()
  {
    GridBagConstraints gbc = new GridBagConstraints();

    JPanel contentPanel = new JPanel(new GridBagLayout());
    contentPanel.setBackground(UIFactory.DEFAULT_BACKGROUND);
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;

    JPanel topPanel = new JPanel(new GridBagLayout());
    topPanel.setBorder(UIFactory.DIALOG_PANEL_BORDER);
    topPanel.setBackground(UIFactory.CURRENT_STEP_PANEL_BACKGROUND);
    Insets insets = UIFactory.getCurrentStepPanelInsets();

    gbc.weighty = 0.0;
    insets.bottom = 0;
    gbc.insets = insets;
    topPanel.add(createTitlePanel(), gbc);
    gbc.insets.top = UIFactory.TOP_INSET_INSTRUCTIONS_SUBPANEL;
    topPanel.add(createInstructionsPane(), gbc);
    gbc.insets.top = UIFactory.TOP_INSET_INPUT_SUBPANEL;
    gbc.insets.bottom = UIFactory.TOP_INSET_INPUT_SUBPANEL;
    topPanel.add(createInputPanel(), gbc);
    gbc.weighty = 1.0;
    gbc.insets = UIFactory.getEmptyInsets();
    topPanel.add(Box.createVerticalGlue(), gbc);
    contentPanel.add(topPanel, gbc);
    gbc.weighty = 0.0;
    gbc.insets = UIFactory.getButtonsPanelInsets();
    contentPanel.add(createButtonsPanel(), gbc);

    return contentPanel;
  }

  /**
   * Creates and returns the title sub panel.
   * @return the title sub panel.
   */
  private Component createTitlePanel()
  {
    JPanel titlePanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    titlePanel.setOpaque(false);
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 0.0;
    gbc.gridwidth = GridBagConstraints.RELATIVE;

    Message title = INFO_SECURITY_OPTIONS_TITLE.get();
    JLabel l =
        UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, title,
            UIFactory.TextStyle.TITLE);
    l.setOpaque(false);
    titlePanel.add(l, gbc);

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.left = 0;
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    titlePanel.add(Box.createHorizontalGlue(), gbc);

    return titlePanel;
  }

  /**
   * Creates and returns the instructions sub panel.
   * @return the instructions sub panel.
   */
  private Component createInstructionsPane()
  {
    Message instructions = INFO_SECURITY_OPTIONS_INSTRUCTIONS.get();

    JTextComponent instructionsPane =
      UIFactory.makeHtmlPane(instructions, UIFactory.INSTRUCTIONS_FONT);
    instructionsPane.setOpaque(false);
    instructionsPane.setEditable(false);

    return instructionsPane;
  }

  /**
   * Creates and returns the input sub panel: the panel with all the widgets
   * that are used to define the security options.
   * @return the input sub panel.
   */
  private Component createInputPanel()
  {
    JPanel inputPanel = new JPanel(new GridBagLayout());
    inputPanel.setOpaque(false);

    ActionListener l = new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        updateEnablingState();
      }
    };

    cbEnableSSL = UIFactory.makeJCheckBox(INFO_ENABLE_SSL_LABEL.get(),
        INFO_ENABLE_SSL_TOOLTIP.get(), UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    cbEnableSSL.addActionListener(l);
    String sPort = "";
    int port = securityOptions.getSslPort();
    if (port > 0)
    {
      sPort = String.valueOf(port);
    }
    tfPort = UIFactory.makeJTextField(Message.raw(sPort),
        INFO_SSL_PORT_TEXTFIELD_TOOLTIP.get(), UIFactory.PORT_FIELD_SIZE,
        UIFactory.TextStyle.TEXTFIELD);
    cbEnableStartTLS = UIFactory.makeJCheckBox(INFO_ENABLE_STARTTLS_LABEL.get(),
        INFO_ENABLE_STARTTLS_TOOLTIP.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    cbEnableStartTLS.addActionListener(l);
    rbUseSelfSignedCertificate = UIFactory.makeJRadioButton(
        INFO_USE_SELF_SIGNED_LABEL.get(),
        INFO_USE_SELF_SIGNED_TOOLTIP.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    rbUseSelfSignedCertificate.addActionListener(l);
    rbUseExistingCertificate = UIFactory.makeJRadioButton(
        INFO_USE_EXISTING_CERTIFICATE_LABEL.get(),
        INFO_USE_EXISTING_CERTIFICATE_TOOLTIP.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    rbUseExistingCertificate.addActionListener(l);
    ButtonGroup group1 = new ButtonGroup();
    group1.add(rbUseSelfSignedCertificate);
    group1.add(rbUseExistingCertificate);

    lKeystoreType = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_KEYSTORE_TYPE_LABEL.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    lKeystoreType.setOpaque(false);
    rbJKS = UIFactory.makeJRadioButton(
        INFO_JKS_CERTIFICATE_LABEL.get(),
        INFO_JKS_CERTIFICATE_TOOLTIP.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    rbJKS.addActionListener(l);
    rbJCEKS = UIFactory.makeJRadioButton(
        INFO_JCEKS_CERTIFICATE_LABEL.get(),
        INFO_JCEKS_CERTIFICATE_TOOLTIP.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    rbJCEKS.addActionListener(l);
    rbPKCS11 = UIFactory.makeJRadioButton(
        INFO_PKCS11_CERTIFICATE_LABEL.get(),
        INFO_PKCS11_CERTIFICATE_TOOLTIP.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    rbPKCS11.addActionListener(l);
    rbPKCS12 = UIFactory.makeJRadioButton(
        INFO_PKCS12_CERTIFICATE_LABEL.get(),
        INFO_PKCS12_CERTIFICATE_TOOLTIP.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    rbPKCS12.addActionListener(l);
    ButtonGroup group2 = new ButtonGroup();
    group2.add(rbJKS);
    group2.add(rbJCEKS);
    group2.add(rbPKCS11);
    group2.add(rbPKCS12);
    lKeystoreType.setLabelFor(rbJKS);

    lKeystorePath = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_KEYSTORE_PATH_LABEL.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    lKeystorePath.setOpaque(false);
    tfKeystorePath = UIFactory.makeJTextField(Message.EMPTY,
        INFO_KEYSTORE_PATH_TOOLTIP.get(),
        UIFactory.HOST_FIELD_SIZE, UIFactory.TextStyle.TEXTFIELD);
    lKeystorePath.setLabelFor(tfKeystorePath);
    browseButton =
      UIFactory.makeJButton(INFO_BROWSE_BUTTON_LABEL.get(),
          INFO_BROWSE_BUTTON_TOOLTIP.get());

    BrowseActionListener browseListener =
      new BrowseActionListener(tfKeystorePath,
          BrowseActionListener.BrowseType.GENERIC_FILE,
          this);
    browseButton.addActionListener(browseListener);

    lKeystorePwd = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_KEYSTORE_PWD_LABEL.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    lKeystorePwd.setOpaque(false);
    tfKeystorePwd = UIFactory.makeJPasswordField(Message.EMPTY,
        INFO_KEYSTORE_PWD_TOOLTIP.get(),
        UIFactory.PASSWORD_FIELD_SIZE, UIFactory.TextStyle.PASSWORD_FIELD);
    lKeystorePwd.setLabelFor(tfKeystorePwd);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 0.0;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    inputPanel.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_SSL_ACCESS_LABEL.get(), UIFactory.TextStyle.PRIMARY_FIELD_VALID),
        gbc);

    JPanel auxPanel = new JPanel(new GridBagLayout());
    auxPanel.setOpaque(false);
    gbc.gridwidth = 4;
    gbc.fill = GridBagConstraints.NONE;
    auxPanel.add(cbEnableSSL, gbc);
    gbc.gridwidth--;
    gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
    auxPanel.add(tfPort, gbc);
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    auxPanel.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        getPortHelpMessage(), UIFactory.TextStyle.SECONDARY_FIELD_VALID), gbc);
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    auxPanel.add(Box.createHorizontalGlue(), gbc);

    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.weightx = 1.0;
    inputPanel.add(auxPanel, gbc);

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    inputPanel.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_STARTTLS_ACCESS_LABEL.get(),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID),
        gbc);
    auxPanel = new JPanel(new GridBagLayout());
    auxPanel.setOpaque(false);
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.insets = UIFactory.getEmptyInsets();
    auxPanel.add(cbEnableStartTLS, gbc);
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    auxPanel.add(Box.createHorizontalGlue(), gbc);
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    inputPanel.add(auxPanel, gbc);

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    JLabel lCertificate = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_CERTIFICATE_LABEL.get(), UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    int additionalInset = Math.abs(lCertificate.getPreferredSize().height -
        rbUseSelfSignedCertificate.getPreferredSize().height) / 2;
    gbc.insets.top += additionalInset;
    inputPanel.add(lCertificate, gbc);
    auxPanel = new JPanel(new GridBagLayout());
    auxPanel.setOpaque(false);
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    inputPanel.add(auxPanel, gbc);

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.anchor = GridBagConstraints.WEST;
    JPanel aux2Panel = new JPanel(new GridBagLayout());
    aux2Panel.setOpaque(false);
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    aux2Panel.add(rbUseSelfSignedCertificate, gbc);
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    aux2Panel.add(Box.createHorizontalGlue(), gbc);
    auxPanel.add(aux2Panel, gbc);

    aux2Panel = new JPanel(new GridBagLayout());
    aux2Panel.setOpaque(false);
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.weightx = 0.0;
    aux2Panel.add(rbUseExistingCertificate, gbc);
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    aux2Panel.add(Box.createHorizontalGlue(), gbc);
    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    auxPanel.add(aux2Panel, gbc);

    additionalInset = Math.abs(lKeystoreType.getPreferredSize().height -
        rbJKS.getPreferredSize().height) / 2;
    aux2Panel = new JPanel(new GridBagLayout());
    aux2Panel.setOpaque(false);
    gbc.insets.top -= additionalInset;
    gbc.insets.left = UIFactory.LEFT_INSET_RADIO_SUBORDINATE;
    auxPanel.add(aux2Panel, gbc);

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.insets.top = additionalInset;
    aux2Panel.add(lKeystoreType, gbc);
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.top = 0;
    aux2Panel.add(rbJKS, gbc);

    gbc.insets.top = UIFactory.TOP_INSET_RADIOBUTTON;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    aux2Panel.add(Box.createHorizontalGlue(), gbc);
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    aux2Panel.add(rbJCEKS, gbc);
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    aux2Panel.add(Box.createHorizontalGlue(), gbc);
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    aux2Panel.add(rbPKCS12, gbc);
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    aux2Panel.add(Box.createHorizontalGlue(), gbc);
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    aux2Panel.add(rbPKCS11, gbc);

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.insets.left = 0;
    gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.WEST;
    aux2Panel.add(lKeystorePath, gbc);
    JPanel aux3Panel = new JPanel(new GridBagLayout());
    aux3Panel.setOpaque(false);
    gbc.weightx = 1.0;
    gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    aux2Panel.add(aux3Panel, gbc);
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    aux3Panel.add(tfKeystorePath, gbc);
    gbc.insets.left = UIFactory.LEFT_INSET_BROWSE;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 0.0;
    aux3Panel.add(browseButton, gbc);

    gbc.insets.left = 0;
    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.WEST;
    aux2Panel.add(lKeystorePwd, gbc);
    gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.fill = GridBagConstraints.NONE;
    aux2Panel.add(tfKeystorePwd, gbc);

    return inputPanel;
  }

  /**
   * Creates and returns the buttons OK/CANCEL sub panel.
   * @return the buttons OK/CANCEL sub panel.
   */
  private Component createButtonsPanel()
  {
    JPanel buttonsPanel = new JPanel(new GridBagLayout());
    buttonsPanel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = 4;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.left = UIFactory.getCurrentStepPanelInsets().left;
    buttonsPanel.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        null, UIFactory.TextStyle.NO_STYLE), gbc);
    gbc.weightx = 1.0;
    gbc.gridwidth--;
    gbc.insets.left = 0;
    buttonsPanel.add(Box.createHorizontalGlue(), gbc);
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0.0;
    okButton =
      UIFactory.makeJButton(INFO_OK_BUTTON_LABEL.get(),
          INFO_SECURITY_OPTIONS_OK_BUTTON_TOOLTIP.get());
    buttonsPanel.add(okButton, gbc);
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
          INFO_SECURITY_OPTIONS_CANCEL_BUTTON_TOOLTIP.get());
    buttonsPanel.add(cancelButton, gbc);
    cancelButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        cancelClicked();
      }
    });

    return buttonsPanel;
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
      @Override
      public Object processBackgroundTask()
      {
        ArrayList<Message> errorMsgs = new ArrayList<Message>();

        errorMsgs.addAll(checkPort());

        errorMsgs.addAll(checkKeystore());

        return errorMsgs;
      }

      @Override
      public void backgroundTaskCompleted(Object returnValue,
          Throwable throwable)
      {
        if (throwable != null)
        {
          // Bug
          throwable.printStackTrace();
          displayError(
              Utils.getThrowableMsg(INFO_BUG_MSG.get(), throwable),
              INFO_ERROR_TITLE.get());
          cancelButton.setEnabled(true);
          okButton.setEnabled(true);
        }
        else
        {
          cancelButton.setEnabled(true);
          okButton.setEnabled(true);
          ArrayList ar = (ArrayList)returnValue;

          if (ar.size() > 0)
          {
            ArrayList<Message> errorMsgs = new ArrayList<Message>();
            for (Object o: ar)
            {
              errorMsgs.add((Message)o);
            }
            displayError(Utils.getMessageFromCollection(errorMsgs, "\n"),
                INFO_ERROR_TITLE.get());
          }
          else
          {
            if (rbUseExistingCertificate.isSelected() &&
                (cbEnableSSL.isSelected() || cbEnableStartTLS.isSelected()))
            {
              if (aliases.length > 1)
              {
                if (aliasDlg == null)
                {
                  aliasDlg = new SelectAliasDialog(SecurityOptionsDialog.this);
                }
                aliasDlg.display(aliases);

                if (!aliasDlg.isCancelled())
                {
                  selectedAlias = aliasDlg.getSelectedAlias();
                  isCancelled = false;
                  dispose();
                }
              }
              else
              {
                selectedAlias = aliases[0];
                isCancelled = false;
                dispose();
              }
            }
            else
            {
              isCancelled = false;
              dispose();
            }
          }
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
    Utilities.displayError(this, msg, title);
    toFront();
  }

  /**
   * Updates the widgets on the dialog with the contents of the securityOptions
   * object.
   *
   */
  private void updateContents()
  {
    cbEnableSSL.setSelected(securityOptions.getEnableSSL());
    cbEnableStartTLS.setSelected(securityOptions.getEnableStartTLS());
    if (securityOptions.getEnableSSL())
    {
      int port = securityOptions.getSslPort();
      if (port > 0)
      {
        tfPort.setText(String.valueOf(port));
      }
    }

    switch (securityOptions.getCertificateType())
    {
    case NO_CERTIFICATE:
      // Nothing else to do
      break;

    case SELF_SIGNED_CERTIFICATE:
      rbUseSelfSignedCertificate.setSelected(true);
      break;

    case JKS:
      rbUseExistingCertificate.setSelected(true);
      rbJKS.setSelected(true);
      tfKeystorePath.setText(securityOptions.getKeystorePath());
      tfKeystorePwd.setText(securityOptions.getKeystorePassword());
      break;

    case JCEKS:
      rbUseExistingCertificate.setSelected(true);
      rbJCEKS.setSelected(true);
      tfKeystorePath.setText(securityOptions.getKeystorePath());
      tfKeystorePwd.setText(securityOptions.getKeystorePassword());
      break;

    case PKCS11:
      rbUseExistingCertificate.setSelected(true);
      rbPKCS11.setSelected(true);
      tfKeystorePwd.setText(securityOptions.getKeystorePassword());
      break;

    case PKCS12:
      rbUseExistingCertificate.setSelected(true);
      rbPKCS12.setSelected(true);
      tfKeystorePath.setText(securityOptions.getKeystorePath());
      tfKeystorePwd.setText(securityOptions.getKeystorePassword());
      break;

    default:
      throw new IllegalStateException("Unknown certificate type.");
    }

    updateEnablingState();
  }

  /**
   * Enables/disables and makes visible/invisible the objects according to what
   * the user selected.
   */
  private void updateEnablingState()
  {
    boolean enableSSL = cbEnableSSL.isSelected();
    boolean enableStartTLS = cbEnableStartTLS.isSelected();

    boolean useSSL = enableSSL || enableStartTLS;

    if (useSSL && !rbUseSelfSignedCertificate.isSelected() &&
        !rbUseExistingCertificate.isSelected())
    {
      rbUseSelfSignedCertificate.setSelected(true);
    }

    if (useSSL && rbUseExistingCertificate.isSelected() &&
        !rbJKS.isSelected() && !rbJCEKS.isSelected() &&
        !rbPKCS11.isSelected() && !rbPKCS12.isSelected())
    {
      rbJKS.setSelected(true);
    }
    tfPort.setEnabled(enableSSL);

    rbUseSelfSignedCertificate.setEnabled(useSSL);

    rbUseExistingCertificate.setEnabled(useSSL);
    lKeystoreType.setEnabled(
        rbUseExistingCertificate.isSelected() && useSSL);
    rbJKS.setEnabled(rbUseExistingCertificate.isSelected() && useSSL);
    rbJCEKS.setEnabled(rbUseExistingCertificate.isSelected() && useSSL);
    rbPKCS11.setEnabled(rbUseExistingCertificate.isSelected() && useSSL);
    rbPKCS12.setEnabled(rbUseExistingCertificate.isSelected() && useSSL);

    lKeystorePath.setEnabled(
        rbUseExistingCertificate.isSelected() && useSSL);
    tfKeystorePath.setEnabled(
        rbUseExistingCertificate.isSelected() && useSSL);
    browseButton.setEnabled(rbUseExistingCertificate.isSelected() && useSSL);
    lKeystorePwd.setEnabled(
        rbUseExistingCertificate.isSelected() && useSSL);
    tfKeystorePwd.setEnabled(
        rbUseExistingCertificate.isSelected() && useSSL);

    lKeystorePath.setVisible(!rbPKCS11.isSelected());
    tfKeystorePath.setVisible(!rbPKCS11.isSelected());
    browseButton.setVisible(!rbPKCS11.isSelected());
  }

  /**
   * Returns the port help message that we display when we cannot use the
   * default port (636).
   * @return the port help message that we display when we cannot use the
   * default port (636).
   */
  private Message getPortHelpMessage()
  {
    Message s = Message.EMPTY;
    if (securityOptions.getSslPort() != DEFAULT_PORT)
    {
      s = INFO_CANNOT_USE_DEFAULT_SECURE_PORT.get();
    }
    return s;
  }

  /**
   * Checks the port.
   * @return the error messages found while checking the port.
   */
  private ArrayList<Message> checkPort()
  {
    ArrayList<Message> errorMsgs = new ArrayList<Message>();

    if (cbEnableSSL.isSelected())
    {
      /* Check the port. */
      String sPort = tfPort.getText();
      int port = -1;
      try
      {
        port = Integer.parseInt(sPort);
        if ((port < Installer.MIN_PORT_VALUE) ||
            (port > Installer.MAX_PORT_VALUE))
        {
          errorMsgs.add(INFO_INVALID_SECURE_PORT_VALUE_RANGE.get(
                  String.valueOf(Installer.MIN_PORT_VALUE),
                  String.valueOf(Installer.MAX_PORT_VALUE)));

        }
        else if (!Utils.canUseAsPort(port))
        {
          if (Utils.isPriviledgedPort(port))
          {
            errorMsgs.add(INFO_CANNOT_BIND_PRIVILEDGED_PORT.get(
              String.valueOf(port)));
          }
          else
          {
            errorMsgs.add(INFO_CANNOT_BIND_PORT.get(
              String.valueOf(port)));
          }

        }

      }
      catch (NumberFormatException nfe)
      {
        errorMsgs.add(INFO_INVALID_SECURE_PORT_VALUE_RANGE.get(
                String.valueOf(Installer.MIN_PORT_VALUE),
                String.valueOf(Installer.MAX_PORT_VALUE)));
      }
    }
    setValidLater(cbEnableSSL, errorMsgs.size() == 0);
    return errorMsgs;
  }

  /**
   * Checks the existing keystore parameters.
   * @return the error messages found while checking existing keystore
   * parameters.
   */
  private ArrayList<Message> checkKeystore()
  {
    ArrayList<Message> errorMsgs = new ArrayList<Message>();

    boolean pathValid = true;
    boolean pwdValid = true;

    if (rbUseExistingCertificate.isSelected() &&
        (cbEnableSSL.isSelected() || cbEnableStartTLS.isSelected()))
    {
      String path = tfKeystorePath.getText();
      if (rbJKS.isSelected() || rbJCEKS.isSelected() || rbPKCS12.isSelected())
      {
        /* Check the path */
        if ((path == null) || (path.length() == 0))
        {
          errorMsgs.add(INFO_KEYSTORE_PATH_NOT_PROVIDED.get());
        }
        else
        {
          File f = new File(path);
          if (!f.exists())
          {
            errorMsgs.add(INFO_KEYSTORE_PATH_DOES_NOT_EXIST.get());
          }
          else if (!f.isFile())
          {
            errorMsgs.add(INFO_KEYSTORE_PATH_NOT_A_FILE.get());
          }
        }

        pathValid = errorMsgs.size() == 0;
      }

      /* Check the password */
      String pwd = String.valueOf(tfKeystorePwd.getPassword());
      if ((pwd == null) || (pwd.length() == 0))
      {
        errorMsgs.add(INFO_KEYSTORE_PWD_EMPTY.get());
        pwdValid = false;
      }

      if (pathValid && pwdValid)
      {
        try
        {
          CertificateManager certManager;
          if (rbJKS.isSelected())
          {
            certManager = new CertificateManager(
                path,
                CertificateManager.KEY_STORE_TYPE_JKS,
                pwd);
          }
          else if (rbJCEKS.isSelected())
          {
            certManager = new CertificateManager(
                path,
                CertificateManager.KEY_STORE_TYPE_JCEKS,
                pwd);
          }
          else if (rbPKCS12.isSelected())
          {
            certManager = new CertificateManager(
                path,
                CertificateManager.KEY_STORE_TYPE_PKCS12,
                pwd);
          }
          else if (rbPKCS11.isSelected())
          {
            certManager = new CertificateManager(
                CertificateManager.KEY_STORE_PATH_PKCS11,
                CertificateManager.KEY_STORE_TYPE_PKCS11,
                pwd);
          }
          else
          {
            throw new IllegalStateException("No keystore type selected.");
          }
          aliases = certManager.getCertificateAliases();
          if ((aliases == null) || (aliases.length == 0))
          {
            // Could not retrieve any certificate
            if (rbPKCS11.isSelected())
            {
              errorMsgs.add(INFO_PKCS11_KEYSTORE_DOES_NOT_EXIST.get());
            }
            else
            {
              if (rbJKS.isSelected())
              {
                errorMsgs.add(INFO_JKS_KEYSTORE_DOES_NOT_EXIST.get());
              }
              else if (rbJCEKS.isSelected())
              {
                errorMsgs.add(INFO_JCEKS_KEYSTORE_DOES_NOT_EXIST.get());
              }
              else
              {
                errorMsgs.add(INFO_PKCS12_KEYSTORE_DOES_NOT_EXIST.get());
              }
              pathValid = false;
            }
          }
        }
        catch (KeyStoreException ke)
        {
          pwdValid = false;
          if (!rbPKCS11.isSelected())
          {
            pathValid = false;
          }
          // Could not access to the keystore: because the password is no good,
          // because the provided file is not a valid keystore, etc.
          if (rbPKCS11.isSelected())
          {
            errorMsgs.add(INFO_ERROR_ACCESSING_PKCS11_KEYSTORE.get());
          }
          else
          {
            if (rbJKS.isSelected())
            {
              errorMsgs.add(INFO_ERROR_ACCESSING_JKS_KEYSTORE.get());
            }
            else if (rbJCEKS.isSelected())
            {
              errorMsgs.add(INFO_ERROR_ACCESSING_JCEKS_KEYSTORE.get());
            }
            else
            {
              errorMsgs.add(INFO_ERROR_ACCESSING_PKCS12_KEYSTORE.get());
            }
            pathValid = false;
          }
        }
      }
    }

    setValidLater(lKeystorePath, pathValid);
    setValidLater(lKeystorePwd, pwdValid);

    return errorMsgs;
  }

  /**
   * Method that updates the text style of a provided component by calling
   * SwingUtilities.invokeLater.  This method is aimed to be called outside
   * the event thread (calling it from the event thread will also work though).
   * @param comp the component to be updated.
   * @param valid whether to use a TextStyle to mark the component as valid
   * or as invalid.
   */
  private void setValidLater(final JComponent comp, final boolean valid)
  {
    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        UIFactory.setTextStyle(comp,
            valid ? UIFactory.TextStyle.SECONDARY_FIELD_VALID :
              UIFactory.TextStyle.SECONDARY_FIELD_INVALID);
      }
    });
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
      SecurityOptionsDialog dlg = new SecurityOptionsDialog(new JFrame(),
          SecurityOptions.createNoCertificateOptions());
      dlg.pack();
      dlg.setVisible(true);
    } catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }
}
