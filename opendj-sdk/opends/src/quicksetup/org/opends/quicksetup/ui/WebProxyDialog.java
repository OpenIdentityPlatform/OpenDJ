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

package org.opends.quicksetup.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.util.Properties;
import java.net.SocketAddress;
import java.net.InetSocketAddress;

/**
 * Dialog allowing the user to specify a host, port, user name and passoword
 * for accessing a web proxy.
 */
public class WebProxyDialog extends JDialog
        implements PropertyChangeListener, ActionListener {

  private static final long serialVersionUID = 4402474441754399992L;

  private JTextField tfHost;
  private JTextField tfPort;
  private JCheckBox chkRequiresAuth;
  private JTextField tfUserName;
  private JPasswordField tfPassword;

  private JOptionPane optionPane;

  /**
   * Creates an instance loading values from system properties.
   * @param parent of this dialog
   */
  public WebProxyDialog(Frame parent) {
    this(parent, null, null, null, null);
    loadSystemProperties();
  }

  /**
   * Creates an instance loading values from system properties.
   * @param parent of this dialog
   */
  public WebProxyDialog(Dialog parent) {
    this(parent, null, null, null, null);
    loadSystemProperties();
  }

  /**
   * Creates an instance.
   * @param parent of this dialog
   * @param host default value for host field
   * @param port default value for port field
   * @param user default value for user field
   * @param pw default value for password field
   */
  public WebProxyDialog(Frame parent, String host, Integer port,
                        String user, char[] pw) {
    super(parent, /*modal=*/true);
    init(host, port, user, pw);
  }

  /**
   * Creates an instance.
   * @param parent of this dialog
   * @param host default value for host field
   * @param port default value for port field
   * @param user default value for user field
   * @param pw default value for password field
   */
  public WebProxyDialog(Dialog parent, String host, Integer port,
                        String user, char[] pw) {
    super(parent, /*modal=*/true);
    init(host, port, user, pw);
  }

  private void init(String host, Integer port, String user, char[] pw) {
    setTitle("Proxy Configuration");
    optionPane = createContentPane(host, port, user, pw);
    optionPane.addPropertyChangeListener(this);
    setContentPane(optionPane);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    //Ensure the text field always gets the first focus.
    addComponentListener(new ComponentAdapter() {
      public void componentShown(ComponentEvent ce) {
        tfHost.requestFocusInWindow();
      }
    });
    pack();
  }

  /**
   * Creates a SocketAddress from current data.
   * @return newly created SocketAddress from the current value of the
   * host and port fields
   */
  public SocketAddress getSocketAddress() {
    SocketAddress addr = null;
    String host = getHost();
    Integer port = getPort();
    if (host != null && port != null) {
      addr = new InetSocketAddress(host, port);
    }
    return addr;
  }

  private JOptionPane createContentPane(String host, Integer port,
                                        String user, char[] pw) {
    JOptionPane pane = new JOptionPane(createPanel(host, port, user, pw),
            JOptionPane.INFORMATION_MESSAGE,
            JOptionPane.OK_CANCEL_OPTION);
    return pane;
  }

  /**
   * {@inheritDoc}
   */
  public void propertyChange(PropertyChangeEvent e) {
    String prop = e.getPropertyName();

    if (isVisible()
            && (e.getSource() == optionPane)
            && (JOptionPane.VALUE_PROPERTY.equals(prop) ||
            JOptionPane.INPUT_VALUE_PROPERTY.equals(prop))) {
      Object value = optionPane.getValue();

      if (value == JOptionPane.UNINITIALIZED_VALUE) {
        //ignore reset
        return;
      }

      //Reset the JOptionPane's value.
      //If you don't do this, then if the user
      //presses the same button next time, no
      //property change event will be fired.
      optionPane.setValue(
              JOptionPane.UNINITIALIZED_VALUE);

      if (value.equals(JOptionPane.OK_OPTION)) {
        if (validateUserData()) {
          setVisible(false);
        }
      } else if (value.equals(JOptionPane.CANCEL_OPTION)) {
        setVisible(false);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void actionPerformed(ActionEvent actionEvent) {
    optionPane.setValue(JOptionPane.OK_OPTION);
  }

  /**
   * Gets the current value of the host field.
   * @return String representing the host value
   */
  public String getHost() {
    String v = tfHost.getText();
    if (v != null && v.trim().length() == 0) v = null;
    return v;
  }

  /**
   * Gets the current value of the port field.
   * @return String representing the port value
   */
  public Integer getPort() {
    Integer i = null;
    String v = tfPort.getText();
    if (v != null && v.trim().length() == 0) v = null;
    try {
      i = Integer.parseInt(v);
    } catch (NumberFormatException e) {
      // do nothing;
    }
    return i;
  }

  /**
   * Gets the current value of the user field.
   * @return String representing the user value
   */
  public String getUserName() {
    String v = tfUserName.getText();
    if ((v != null && v.trim().length() == 0) ||
          !chkRequiresAuth.isSelected()) v = null;
    return v;
  }

  /**
   * Gets the current value of the password field.
   * @return char[] representing the password value
   */
  public char[] getPassword() {
    char[] v = tfPassword.getPassword();
    if ((v != null && v.length == 0) ||
          !chkRequiresAuth.isSelected()) v = null;
    return v;
  }

  /**
   * Writes the current values to system properties.
   */
  public void applySystemProperties() {
    Properties systemSettings = System.getProperties();

    String v = tfHost.getText();
    if (v != null && v.trim().length() == 0) v = null;
    systemSettings.put("http.proxyHost", v);
    systemSettings.put("https.proxyHost", v);

    v = tfPort.getText();
    if (v != null && v.trim().length() == 0) v = null;
    systemSettings.put("http.proxyPort", v);
    systemSettings.put("https.proxyPort", v);

    System.setProperties(systemSettings);
  }

  private void loadSystemProperties() {
    Properties systemSettings = System.getProperties();

    Object v = systemSettings.get("http.proxyHost");
    tfHost.setText(v != null ? v.toString() : "");

    v = systemSettings.get("http.proxyPort");
    tfPort.setText(v != null ? v.toString() : "");
  }

  private JPanel createPanel(String host, Integer port,
                             String user, char[] pw) {
    JPanel p = new JPanel();
    p.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    final JLabel lblUser = UIFactory.makeJLabel(null, "User:",
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    final JLabel lblPassword = UIFactory.makeJLabel(null, "Password:",
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.FIRST_LINE_START;
    gbc.fill = GridBagConstraints.NONE;
    p.add(new JLabel("Proxy Host:"), gbc);

    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    p.add(tfHost = new JTextField(host), gbc);

    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    p.add(new JLabel("Proxy Port:"), gbc);

    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    p.add(tfPort = new JTextField(port != null?port.toString():""), gbc);

    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets.top = 7; // I don't understand why this is necesary
    p.add(new JLabel("Auhentication:"), gbc);

    gbc.gridx = 1;
    gbc.gridy = 2;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.top = 0;
    p.add(chkRequiresAuth =
            UIFactory.makeJCheckBox("Required by proxy","",
                    UIFactory.TextStyle.SECONDARY_FIELD_VALID
            ), gbc);
    chkRequiresAuth.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        tfUserName.setEnabled(chkRequiresAuth.isSelected());
        tfPassword.setEnabled(chkRequiresAuth.isSelected());
        lblUser.setEnabled(chkRequiresAuth.isSelected());
        lblPassword.setEnabled(chkRequiresAuth.isSelected());
      }
    });

    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
    p.add(lblUser, gbc);

    gbc.gridx = 1;
    gbc.gridy = 3;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.left = 0;
    p.add(tfUserName = new JTextField(user), gbc);

    gbc.gridx = 0;
    gbc.gridy = 4;
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
    p.add(lblPassword, gbc);

    gbc.gridx = 1;
    gbc.gridy = 4;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.left = 0;
    gbc.weighty = 1.0;
    p.add(tfPassword = new JPasswordField(pw != null ? new String(pw) : ""),
            gbc);

    // By default, proxy does not require auth
    chkRequiresAuth.setSelected(false);
    tfUserName.setEnabled(false);
    tfPassword.setEnabled(false);
    lblPassword.setEnabled(false);
    lblUser.setEnabled(false);

    // For automatic closure
    tfHost.addActionListener(this);
    tfPort.addActionListener(this);
    tfUserName.addActionListener(this);
    tfPassword.addActionListener(this);

    return p;
  }

  private boolean validateUserData() {
    String errorMsg = null;
    String portString = tfPort.getText();

    //TODO better port number verification
    try {
      Integer.parseInt(portString);
    } catch (NumberFormatException e) {
      errorMsg = "Illegal port value " + portString;
    }

    if (errorMsg != null) {
      JOptionPane.showMessageDialog(this, errorMsg);
    }
    return (errorMsg == null);
  }

//  /**
//   * For testing.
//   * @param args cl args
//   */
//  public static void main(String[] args) {
//    JDialog dlg = new WebProxyDialog(new JFrame());
//    dlg.addComponentListener(new ComponentAdapter() {
//      public void componentHidden(ComponentEvent componentEvent) {
//        System.exit(0);
//      }
//    });
//    dlg.setVisible(true);
//  }

}
