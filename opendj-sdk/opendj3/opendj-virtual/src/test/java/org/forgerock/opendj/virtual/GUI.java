/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.virtual;

import java.awt.Font;
import java.awt.event.*;
import java.sql.SQLException;

import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ErrorResultIOException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;

public class GUI implements ActionListener{
  //Definition of global values and items that are part of the GUI.
  static JFrame frame = new JFrame("Login Screen");
  
  private JPanel totalGUI;
  private JLabel label, label_1, label_2, label_3, label_4, label_5, label_6, lblBasedn, label_8, label_9, lblDirectory, lblDatabase;
  private JPanel buttonPane;
  private JButton btnLogin, btnCancel;
  private Border loweredetched;
  
  //login pane Normal
  private JPanel loginPane;
  private JTextField txtHost, txtPort, txtDatabaseName, txtUsername;
  private JPasswordField txtPassword;

  //login pane Ldap
  private JPanel loginPaneLdap;
  private JTextField txtHostLdap, txtPortLdap,txtBaseDN,txtUsernameLdap;
  private JPasswordField txtPasswordLdap;

  public JPanel createContentPane (){
    totalGUI = new JPanel();
    totalGUI.setLayout(null);
    
    loweredetched = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
    
    buttonPane = new JPanel();
    buttonPane.setLayout(null);
    buttonPane.setBounds(295, 221, 284, 30);
    totalGUI.add(buttonPane);

    btnLogin = new JButton("Login");
    btnLogin.setBounds(14, 0, 120, 30);
    btnLogin.addActionListener(this);
    buttonPane.add(btnLogin);

    btnCancel = new JButton("Cancel");
    btnCancel.setBounds(148, 0, 120, 30);
    btnCancel.addActionListener(this);
    buttonPane.add(btnCancel);

    loginPane = new JPanel();
    loginPane.setLayout(null);
    loginPane.setBounds(6, 6, 285, 203);
    loginPane.setBorder(loweredetched);
    totalGUI.add(loginPane);

    label = new JLabel("Host");
    label.setBounds(11, 43, 99, 30);
    loginPane.add(label);

    txtHost = new JTextField();
    txtHost.setColumns(10);
    txtHost.setBounds(114, 43, 165, 30);
    loginPane.add(txtHost);

    label_1 = new JLabel("Port");
    label_1.setBounds(11, 74, 99, 30);
    loginPane.add(label_1);

    txtPort = new JTextField();
    txtPort.setColumns(10);
    txtPort.setBounds(114, 73, 165, 30);
    loginPane.add(txtPort);

    label_2 = new JLabel("DatabaseName");
    label_2.setBounds(11, 104, 99, 30);
    loginPane.add(label_2);

    txtDatabaseName = new JTextField();
    txtDatabaseName.setColumns(10);
    txtDatabaseName.setBounds(114, 103, 165, 30);
    loginPane.add(txtDatabaseName);

    label_3 = new JLabel("Username");
    label_3.setBounds(11, 135, 99, 30);
    loginPane.add(label_3);

    txtUsername = new JTextField();
    txtUsername.setColumns(10);
    txtUsername.setBounds(114, 133, 165, 30);
    loginPane.add(txtUsername);

    label_4 = new JLabel("Password");
    label_4.setBounds(11, 166, 99, 30);
    loginPane.add(label_4);

    txtPassword = new JPasswordField();
    txtPassword.setBounds(114, 163, 165, 30);
    loginPane.add(txtPassword);

    loginPaneLdap = new JPanel();
    loginPaneLdap.setLayout(null);
    loginPaneLdap.setBounds(295, 6, 285, 203);
    loginPaneLdap.setBorder(loweredetched);
    totalGUI.add(loginPaneLdap);

    label_5 = new JLabel("Host");
    label_5.setHorizontalAlignment(SwingConstants.LEFT);
    label_5.setBounds(11, 43, 90, 30);
    loginPaneLdap.add(label_5);

    txtHostLdap = new JTextField();
    txtHostLdap.setColumns(10);
    txtHostLdap.setBounds(114, 43, 165, 30);
    loginPaneLdap.add(txtHostLdap);

    label_6 = new JLabel("Port");
    label_6.setHorizontalAlignment(SwingConstants.LEFT);
    label_6.setBounds(11, 74, 90, 30);
    loginPaneLdap.add(label_6);

    txtPortLdap = new JTextField();
    txtPortLdap.setColumns(10);
    txtPortLdap.setBounds(114, 73, 165, 30);
    loginPaneLdap.add(txtPortLdap);

    lblBasedn = new JLabel("BaseDN");
    lblBasedn.setHorizontalAlignment(SwingConstants.LEFT);
    lblBasedn.setBounds(11, 104, 90, 30);
    loginPaneLdap.add(lblBasedn);

    txtBaseDN = new JTextField();
    txtBaseDN.setColumns(10);
    txtBaseDN.setBounds(114, 103, 165, 30);
    loginPaneLdap.add(txtBaseDN);

    label_8 = new JLabel("Username");
    label_8.setHorizontalAlignment(SwingConstants.LEFT);
    label_8.setBounds(11, 135, 90, 30);
    loginPaneLdap.add(label_8);

    txtUsernameLdap = new JTextField();
    txtUsernameLdap.setColumns(10);
    txtUsernameLdap.setBounds(114, 133, 165, 30);
    loginPaneLdap.add(txtUsernameLdap);

    label_9 = new JLabel("Password");
    label_9.setHorizontalAlignment(SwingConstants.LEFT);
    label_9.setBounds(11, 166, 90, 30);
    loginPaneLdap.add(label_9);

    txtPasswordLdap = new JPasswordField();
    txtPasswordLdap.setBounds(114, 163, 165, 30);
    loginPaneLdap.add(txtPasswordLdap);
    
    lblDatabase = new JLabel("Database");
    lblDatabase.setHorizontalAlignment(SwingConstants.CENTER);
    lblDatabase.setFont(lblDatabase.getFont().deriveFont(lblDatabase.getFont().getStyle() | Font.BOLD, lblDatabase.getFont().getSize() + 2f));
    lblDatabase.setBounds(1, 6, 270, 16);
    loginPane.add(lblDatabase);
    
    lblDirectory = new JLabel("Directory");
    lblDirectory.setHorizontalAlignment(SwingConstants.CENTER);
    lblDirectory.setFont(lblDirectory.getFont().deriveFont(lblDirectory.getFont().getStyle() | Font.BOLD, lblDirectory.getFont().getSize() + 2f));
    lblDirectory.setBounds(1, 6, 270, 16);
    loginPaneLdap.add(lblDirectory);

    txtHost.setText("localhost");
    txtPort.setText("3306");
    txtUsername.setText("root");
    txtDatabaseName.setText("opendj_db");
    txtHostLdap.setText("localhost");
    txtPortLdap.setText("389");
    txtBaseDN.setText("dc=example,dc=com");
    txtUsernameLdap.setText("cn=Directory Manager");
    
    return totalGUI;
  }
  
  public void actionPerformed(ActionEvent e) {
    Object source = e.getSource();
    JDBCConnectionFactory JDBC = null;
    LDAPConnectionFactory LDAP = null;
    if((source == btnLogin))
    {
      String aHost = txtHost.getText(); //read aHost from the screen as a string
      String lHost = txtHostLdap.getText();
      String aPort = txtPort.getText();
      String lPort = txtPortLdap.getText();
      Integer intaPort = null;
      Integer intlPort = null;
      String aDatabase = txtDatabaseName.getText();
      String lBaseDN = txtBaseDN.getText();
      String aUsername = txtUsername.getText();
      String lUsername = txtUsernameLdap.getText();
      try {
        // aHost = txtHost.getText();  
        if  ((aHost.isEmpty()) || (lHost.isEmpty()))
          //if entry is made then throw error
          throw new IllegalArgumentException("Enter Host address");
        else if ((aPort.isEmpty()) || (lPort.isEmpty()))
        {
          throw new IllegalArgumentException("Enter port number");
        }
        else if ((aDatabase.isEmpty()) || (lBaseDN.isEmpty()))
        {
          throw new IllegalArgumentException("Enter database name");
        }
        else if ((aUsername.isEmpty()) || lUsername.isEmpty())
        {
          throw new IllegalArgumentException("Enter username number");
        }
        //otherwise setup connection
        else
        {
          try{
            intaPort = Integer.parseInt(aPort);
            intlPort = Integer.parseInt(lPort);
            
            JDBC = new JDBCConnectionFactory(aHost, intaPort, aDatabase);
            final Connection jdbcconnection = JDBC.getConnection(); 
                    jdbcconnection.bind(aUsername, txtPassword.getPassword());
                    
                    LDAP = new LDAPConnectionFactory(lHost, intlPort);
                final Connection ldapconnection = LDAP.getConnection();
                ldapconnection.bind(lUsername, txtPasswordLdap.getPassword());
                
            //TODO aanpassen connection failed
            frame.dispose();
            new GUIMap(JDBC, LDAP);
          } catch (NumberFormatException ex){
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
          } catch (ErrorResultException e1) {
                        e1.printStackTrace();
                } catch (ErrorResultIOException e1) {
                        e1.printStackTrace();
                } catch (SearchResultReferenceIOException e1) {
                        e1.printStackTrace();
                } catch (SQLException e1) {
                        e1.printStackTrace();
                }
        }
      }
      catch (IllegalArgumentException x)   {   // catch the error
        JOptionPane.showMessageDialog(frame, x.getMessage(), "Warning", JOptionPane.ERROR_MESSAGE);
      }
    }
    else if(source == btnCancel)
    {
      System.exit(0);
    }
  }
  private static void createAndShowGUI() {

    // JFrame.setDefaultLookAndFeelDecorated(true); 
    try {
      for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
        //System.out.println(info.getName());
        if ("Nimbus".equals(info.getName())) {
          UIManager.setLookAndFeel(info.getClassName());
          //System.out.println("Used = " + info.getName());
          break;
       } else {
         UIManager.setLookAndFeel(info.getClassName());
         }
      }
    } catch (Exception e) {
      //System.out.println(e.toString());
    }
    //Create and set up the content pane.
    GUI gui = new GUI();
    frame.setContentPane(gui.createContentPane());
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(585, 281);
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    frame.setResizable(false);  
    frame.setEnabled(true);
  }
  public static void main(String[] args) {
    //Schedule a job for the event-dispatching thread:
    //creating and showing this application's GUI.
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        createAndShowGUI();
      }
    });
  }
}
