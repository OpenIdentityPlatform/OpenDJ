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

package org.opends.quicksetup.ui;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.opends.quicksetup.UserDataCertificateException;
import org.opends.quicksetup.event.MinimumSizeComponentListener;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import static org.opends.messages.QuickSetupMessages.*;

/**
 * This class is used to present the user a certificate to the user in order
 * it to be accepted.
 */
public class CertificateDialog extends JDialog implements HyperlinkListener
{
  private static final long serialVersionUID = -8989965057591475064L;
  private boolean isAccepted;
  private UserDataCertificateException ce;
  private JButton cancelButton;
  private JButton okButton;
  private JComponent certificateDetails;
  private JEditorPane explanationPane;
  private boolean detailsAlreadyClicked;
  private String explanationWithHideDetails;
  private String explanationWithShowDetails;

  private static final Logger LOG = Logger.getLogger(
      CertificateDialog.class.getName());

  /**
   * Constructor of the certificate dialog.
   * @param parent the parent frame for this dialog.
   * @param ce the UserDataCertificateException we use to get the informations
   * about the certificate that was presented and the reason why it was
   * rejected.
   */
  public CertificateDialog(JFrame parent, UserDataCertificateException ce)
  {
    super(parent);
    this.ce = ce;
    setTitle(INFO_CERTIFICATE_DIALOG_TITLE.get().toString());
    getContentPane().add(createPanel());
    setModal(true);
    pack();
    if (getPreferredSize().width > parent.getWidth())
    {
      setPreferredSize(new Dimension(Math.max(parent.getWidth() - 20, 400),
          getPreferredSize().height));
    }
    pack();
    int minWidth = (int) getPreferredSize().getWidth();
    int minHeight = (int) getPreferredSize().getHeight();
    addComponentListener(new MinimumSizeComponentListener(this, minWidth,
        minHeight));
    getRootPane().setDefaultButton(cancelButton);

    addWindowListener(new WindowAdapter()
    {
      public void windowClosing(WindowEvent e)
      {
        cancelClicked();
      }
    });
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

    Utilities.centerOnComponent(this, parent);
  }

  /**
   * Wheter the user accepted the certificate or not.
   * @return <CODE>true</CODE> ir the user accepted the certificate and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isAccepted()
  {
    return isAccepted;
  }

  /**
   * Implements HyperlinkListener.  When the user clicks on a link we assume
   * that is the show details/hide details and we update the visible components
   * accordingly.
   *
   * @param e the HyperlinkEvent.
   */
  public void hyperlinkUpdate(HyperlinkEvent e)
  {
    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
    {
      boolean detailsVisible = !certificateDetails.isVisible();
      explanationPane.setText(detailsVisible?
          explanationWithHideDetails:explanationWithShowDetails);
      certificateDetails.setVisible(detailsVisible);
      if (detailsVisible && !detailsAlreadyClicked)
      {
        detailsAlreadyClicked = true;
        pack();
      }
    }
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
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;

    JPanel topPanel = new JPanel(new GridBagLayout());
    topPanel.setBorder(UIFactory.DIALOG_PANEL_BORDER);
    topPanel.setBackground(UIFactory.CURRENT_STEP_PANEL_BACKGROUND);

    gbc.weighty = 0.0;
    gbc.insets = UIFactory.getCurrentStepPanelInsets();
    topPanel.add(createTitlePanel(), gbc);
    gbc.insets.top = UIFactory.TOP_INSET_INSTRUCTIONS_SUBPANEL;
    topPanel.add(createTextPane(), gbc);
    certificateDetails = createCertificateDetailsPane();
    gbc.insets.top = 0;
    gbc.insets.bottom = 0;
    topPanel.add(Box.createHorizontalStrut(
        certificateDetails.getPreferredSize().width), gbc);
    gbc.insets.top = 0;
    gbc.weighty = 1.0;
    JPanel auxPanel = UIFactory.makeJPanel();
    auxPanel.setLayout(new GridBagLayout());
    gbc.weightx = 0.0;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    auxPanel.add(Box.createVerticalStrut(100), gbc);
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    auxPanel.add(certificateDetails, gbc);
    gbc.insets = UIFactory.getCurrentStepPanelInsets();
    gbc.insets.bottom = UIFactory.TOP_INSET_INPUT_SUBPANEL;
    topPanel.add(auxPanel, gbc);
    certificateDetails.setVisible(false);
    gbc.weighty = 0.2;
    gbc.insets = UIFactory.getEmptyInsets();
    topPanel.add(Box.createVerticalGlue(), gbc);
    contentPanel.add(topPanel, gbc);
    gbc.weighty = 0.0;
    gbc.insets = UIFactory.getButtonsPanelInsets();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    contentPanel.add(createButtonsPanel(), gbc);

    return contentPanel;
  }

  /**
   * Creates and returns the title sub panel.
   * @return the title sub panel.
   */
  private Component createTitlePanel()
  {
    JPanel titlePanel = UIFactory.makeJPanel();
    titlePanel.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 0.0;
    gbc.gridwidth = GridBagConstraints.RELATIVE;

    Message title = INFO_CERTIFICATE_TITLE.get();
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
   * Creates and returns the text sub panel.
   * @return the text sub panel.
   */
  private Component createTextPane()
  {
    Message text;
    if (ce.getType() == UserDataCertificateException.Type.NOT_TRUSTED)
    {
      text = INFO_CERTIFICATE_NOT_TRUSTED_TEXT.get(ce.getHost(),
          String.valueOf(ce.getPort()));
    }
    else
    {
      text = INFO_CERTIFICATE_NAME_MISMATCH_TEXT.get(
              ce.getHost(), String.valueOf(ce.getPort()));
    }
    JPanel p = UIFactory.makeJPanel();
    p.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    p.add(UIFactory.makeJLabel(UIFactory.IconType.WARNING_LARGE, null,
        UIFactory.TextStyle.NO_STYLE), gbc);
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.insets.bottom = 0;
    explanationPane = UIFactory.makeHtmlPane(null, UIFactory.INSTRUCTIONS_FONT);
    explanationPane.setOpaque(false);
    explanationPane.setEditable(false);
    explanationPane.addHyperlinkListener(this);
    p.add(explanationPane, gbc);
    if ((ce.getChain() != null) && (ce.getChain().length > 0))
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(text);
      mb.append(INFO_CERTIFICATE_SHOW_DETAILS_TEXT.get());
      explanationWithShowDetails = UIFactory.applyFontToHtml(
              mb.toString(), UIFactory.INSTRUCTIONS_FONT);
      MessageBuilder mb2 = new MessageBuilder();
      mb2.append(text);
      mb2.append(INFO_CERTIFICATE_SHOW_DETAILS_TEXT.get());
      explanationWithHideDetails = UIFactory.applyFontToHtml(
              mb2.toString(), UIFactory.INSTRUCTIONS_FONT);

      explanationPane.setText(explanationWithShowDetails);
    }
    else
    {
      explanationPane.setText(text.toString());
    }
    return p;
  }

  /**
   * Creates and returns the buttons OK/CANCEL sub panel.
   * @return the buttons OK/CANCEL sub panel.
   */
  private Component createButtonsPanel()
  {
    JPanel buttonsPanel = UIFactory.makeJPanel();
    buttonsPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = 4;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.left = UIFactory.getCurrentStepPanelInsets().left;
    buttonsPanel.add(UIFactory.makeJLabel(UIFactory.IconType.OPENDS_SMALL,
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
          INFO_CERTIFICATE_DIALOG_OK_BUTTON_TOOLTIP.get());
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
          INFO_CERTIFICATE_DIALOG_CANCEL_BUTTON_TOOLTIP.get());
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
   * Creates the panel containing a representation of the certificate chain.
   * @return the panel containing a representation of the certificate chain.
   */
  private JComponent createCertificateDetailsPane()
  {
    JPanel p = UIFactory.makeJPanel();
    p.setLayout(new GridBagLayout());
    if ((ce.getChain() != null) && (ce.getChain().length > 0))
    {
      final JComboBox combo = new JComboBox();
      combo.setToolTipText(
              INFO_CERTIFICATE_CHAIN_COMBO_TOOLTIP.get().toString());
      final CardLayout cl = new CardLayout();
      final JPanel cardPanel = new JPanel(cl);
      final Map<String, JPanel> hmPanels = new HashMap<String, JPanel>();

      Message[] labels =
      {
          INFO_CERTIFICATE_SUBJECT_LABEL.get(),
          INFO_CERTIFICATE_ISSUED_BY_LABEL.get(),
          INFO_CERTIFICATE_VALID_FROM_LABEL.get(),
          INFO_CERTIFICATE_EXPIRES_ON_LABEL.get(),
          INFO_CERTIFICATE_TYPE_LABEL.get(),
          INFO_CERTIFICATE_SERIAL_NUMBER_LABEL.get(),
          INFO_CERTIFICATE_SIGNATURE_LABEL.get(),
          INFO_CERTIFICATE_SIGNATURE_ALGORITHM_LABEL.get(),
          INFO_CERTIFICATE_VERSION_LABEL.get(),
          INFO_CERTIFICATE_PUBLIC_KEY_LABEL.get()
      };

      for (int i=0; i<ce.getChain().length; i++)
      {
        X509Certificate cert = ce.getChain()[i];
        JComponent[] components =
        {
            createSubjectComponent(cert),
            createIssuedByComponent(cert),
            createValidFromComponent(cert),
            createExpiresOnComponent(cert),
            createTypeComponent(cert),
            createSerialNumberComponent(cert),
            createSignatureComponent(cert),
            createSignatureAlgorithmComponent(cert),
            createVersionComponent(cert),
            createPublicKeyComponent(cert)
        };
        JPanel certPanel = UIFactory.makeJPanel();
        certPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        for (int j=0; j<labels.length; j++)
        {
          JLabel l = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
              labels[j], UIFactory.TextStyle.PRIMARY_FIELD_VALID);

          l.setLabelFor(components[j]);
          if (j > 0)
          {
            gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
          }
          gbc.gridwidth = GridBagConstraints.RELATIVE;
          gbc.weightx = 0.0;
          gbc.insets.left = 0;
          certPanel.add(l, gbc);
          gbc.gridwidth = GridBagConstraints.REMAINDER;
          gbc.weightx = 1.0;
          gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
          certPanel.add(components[j], gbc);
        }
        String name = getName(cert);
        hmPanels.put(name, certPanel);
        cardPanel.add(name, certPanel);
        combo.addItem(name);
      }
      GridBagConstraints gbc = new GridBagConstraints();
      if (ce.getChain().length == 1)
      {
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        p.add(cardPanel, gbc);

        gbc.weighty = 1.0;
        p.add(Box.createVerticalGlue(), gbc);
      }
      else
      {
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel auxPanel = UIFactory.makeJPanel();
        auxPanel.setLayout(new GridBagLayout());
        JLabel l = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
            INFO_CERTIFICATE_CHAIN_LABEL.get(),
            UIFactory.TextStyle.PRIMARY_FIELD_VALID);
        auxPanel.add(l, gbc);
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
        auxPanel.add(combo, gbc);
        l.setLabelFor(combo);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets.left = 0;
        gbc.weightx = 1.0;
        auxPanel.add(Box.createHorizontalGlue(), gbc);

        p.add(auxPanel, gbc);

        gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
        gbc.fill = GridBagConstraints.BOTH;
        p.add(cardPanel, gbc);

        gbc.weighty = 1.0;
        p.add(Box.createVerticalGlue(), gbc);
      }

      combo.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          String selectedItem = (String)combo.getSelectedItem();
          cl.show(hmPanels.get(selectedItem), selectedItem);
        }
      });
    }
    JScrollPane scroll = new JScrollPane(p);
    scroll.setViewportBorder(new EmptyBorder(0, 0, 0, 0));
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.setPreferredSize(new Dimension(scroll.getPreferredSize().width,
        175));
    return scroll;
  }

  private JComponent createSubjectComponent(X509Certificate cert)
  {
    Message dn = Message.raw(cert.getSubjectX500Principal().getName());
    return makeValueLabel(dn);
  }

  private JComponent createIssuedByComponent(X509Certificate cert)
  {
    Message dn = Message.raw(cert.getIssuerX500Principal().getName());
    return makeValueLabel(dn);
  }

  private JComponent createValidFromComponent(X509Certificate cert)
  {
    JComponent c;

    Date date = cert.getNotBefore();
    DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT,
    DateFormat.SHORT);
    Message value = Message.raw(df.format(date));
    boolean isNotValidYet = false;
    long t1 = System.currentTimeMillis();
    long t2 = date.getTime();
    isNotValidYet = t1 < t2;

    if (isNotValidYet)
    {
      c = UIFactory.makeJLabel(UIFactory.IconType.ERROR,
          INFO_CERTIFICATE_NOT_VALID_YET.get(value),
          UIFactory.TextStyle.SECONDARY_FIELD_INVALID);
    }
    else
    {
      c = makeValueLabel(value);
    }
    return c;
  }

  private JComponent createExpiresOnComponent(X509Certificate cert)
  {
    JComponent c;

    Date date = cert.getNotAfter();
    DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT,
    DateFormat.SHORT);
    Message value = Message.raw(df.format(date));
    boolean isExpired = false;
    long t1 = System.currentTimeMillis();
    long t2 = date.getTime();
    isExpired = t1 > t2;

    if (isExpired)
    {
      c = UIFactory.makeJLabel(UIFactory.IconType.ERROR,
          INFO_CERTIFICATE_EXPIRED.get(value),
          UIFactory.TextStyle.SECONDARY_FIELD_INVALID);
    }
    else
    {
      c = makeValueLabel(value);
    }
    return c;
  }

  private JComponent createTypeComponent(X509Certificate cert)
  {
    Message type = Message.raw(cert.getType());
    return makeValueLabel(type);
  }

  private JComponent createSerialNumberComponent(X509Certificate cert)
  {
    Message serialNumber = Message.raw(String.valueOf(cert.getSerialNumber()));
    return makeValueLabel(serialNumber);
  }

  private JComponent createSignatureComponent(X509Certificate cert)
  {
    byte[] sig = cert.getSignature();
    MessageBuilder sb = new MessageBuilder();
    for (int i = 0; i < sig.length; i++)
    {
      if (i > 0)
      {
        sb.append(":");
      }
      sb.append(Integer.toHexString(((int) sig[i]) & 0xFF));
    }
    return UIFactory.makeTextPane(sb.toMessage(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
  }

  private JComponent createSignatureAlgorithmComponent(X509Certificate cert)
  {
    Message signature = Message.raw(String.valueOf(cert.getSigAlgName()));
    return makeValueLabel(signature);
  }

  private JComponent createVersionComponent(X509Certificate cert)
  {
    Message version = Message.raw(String.valueOf(cert.getVersion()));
    return makeValueLabel(version);
  }

  private JComponent createPublicKeyComponent(X509Certificate cert)
  {
    return UIFactory.makeTextPane(Message.raw(cert.getPublicKey().toString()),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
  }

  private JLabel makeValueLabel(Message value)
  {
    if (value == null)
    {
      value = INFO_NOT_AVAILABLE_LABEL.get();
    }
    return UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, value,
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
  }

  private String getName(X509Certificate cert)
  {
    String name = cert.getSubjectX500Principal().getName();
    try
    {
      LdapName dn = new LdapName(name);
      Rdn rdn = dn.getRdn(0);
      name = rdn.getValue().toString();
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING, "Error parsing subject dn: "+
          cert.getSubjectX500Principal(), t);
    }
    return name;
  }

  /**
   * Method called when user clicks on ok.
   *
   */
  private void okClicked()
  {
    isAccepted = true;
    dispose();
  }

  /**
   * Method called when user clicks on cancel.
   *
   */
  private void cancelClicked()
  {
    isAccepted = false;
    dispose();
  }

  /**
   * Method written for testing purposes.
   * @param args the arguments to be passed to the test program.
   */
  /*
  public static void main(String[] args)
  {
    try
    {
      // TODO
    } catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }
  */
}

