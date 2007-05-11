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

package org.opends.quicksetup.upgrader.ui;

import org.opends.quicksetup.UserData;
import org.opends.quicksetup.event.BrowseActionListener;
import org.opends.quicksetup.ui.*;
import org.opends.quicksetup.upgrader.Build;
import org.opends.quicksetup.upgrader.RemoteBuildManager;
import org.opends.quicksetup.upgrader.Upgrader;
import org.opends.quicksetup.util.BackgroundTask;
import org.opends.quicksetup.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This panel allows the user to select a remote or local build for upgrade.
 */
public class ChooseVersionPanel extends QuickSetupStepPanel {

  static private final Logger LOG =
          Logger.getLogger(ChooseVersionPanel.class.getName());

  static private final long serialVersionUID = -6941309163077121917L;

  private JLabel lblCurrentVersion = null;
  private JRadioButton rbRemote = null;
  private JRadioButton rbLocal = null;
  private ButtonGroup grpRemoteLocal = null;
  private JComboBox cboBuild = null;
  private JLabel lblFile = null;
  private JTextField tfFile = null;
  private JButton butBrowse = null;
  private boolean loadBuildListAttempted = false;
  private RemoteBuildListComboBoxModelCreator bld = null;

  /**
   * Creates an instance.
   *
   * @param application this panel represents.
   */
  public ChooseVersionPanel(GuiApplication application) {
    super(application);
    getBuildLoader();
  }

  /**
   * {@inheritDoc}
   */
  public boolean blockingBeginDisplay() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public void beginDisplay(UserData data) {
    super.beginDisplay(data);

    if (!loadBuildListAttempted) {

      // Begin display is called outside the UI
      // thread.  loadBuildList must be called
      // inside the UI thread in order to properly
      // set up the ProgressListenerInputStream
      // displayed while downloading the build list.
      // The enclosing thread and sleep statement is
      // there to allow the card layout to switch to
      // this panel before setting up the downloading.
      int delay = 100;
      ActionListener loadPerformer = new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
          rbLocal.setSelected(true);
          rbRemote.setEnabled(false);
          setComponentEnablement();
          cboBuild.setRenderer(new BuildListLoadingComboBoxRenderer());
          try {
            loadBuildList();
          } catch (IOException e) {
            LOG.log(Level.INFO, "error", e);
          }
        }
      };
      Timer t = new Timer(delay, loadPerformer);
      t.setRepeats(false);
      t.start();
    }

    lblCurrentVersion.setText(
            Utils.getBuildString(getApplication().getInstallation()));

  }

  /**
   * {@inheritDoc}
   */
  public Object getFieldValue(FieldName fieldName) {
    Object value = null;
    if (FieldName.UPGRADE_DOWNLOAD.equals(fieldName)) {
      value = new Boolean(rbRemote.isSelected());
    } else if (FieldName.UPGRADE_BUILD_TO_DOWNLOAD.equals(fieldName)) {
      value = cboBuild.getSelectedItem();
    } else if (FieldName.UPGRADE_FILE.equals(fieldName)) {
      String s = tfFile.getText();
      if (s != null && s.length() > 0) {
        value = new File(tfFile.getText());
      }
    }
    return value;
  }

  /**
   * {@inheritDoc}
   */
  protected Component createInputPanel() {
    Component c;

    JPanel p = UIFactory.makeJPanel();

    LabelFieldDescriptor currentVersionDescriptor = new LabelFieldDescriptor(
      getMsg("upgrade-review-panel-old-version-label"),
      getMsg("upgrade-review-panel-old-version-tooltip"),
      LabelFieldDescriptor.FieldType.READ_ONLY,
      LabelFieldDescriptor.LabelType.PRIMARY,
      0
    );

    lblCurrentVersion = UIFactory.makeJLabel(
            UIFactory.IconType.NO_ICON,
            "", UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    rbRemote = UIFactory.makeJRadioButton(
            getMsg("upgrade-choose-version-remote-label"),
            getMsg("upgrade-choose-version-remote-tooltip"),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    rbLocal = UIFactory.makeJRadioButton(
            getMsg("upgrade-choose-version-local-label"),
            getMsg("upgrade-choose-version-local-tooltip"),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    grpRemoteLocal = new ButtonGroup();
    grpRemoteLocal.add(rbRemote);
    grpRemoteLocal.add(rbLocal);
    grpRemoteLocal.setSelected(rbRemote.getModel(), true);

    cboBuild = UIFactory.makeJComboBox();
    cboBuild.setEditable(false);

    // TODO: use UIFactory
    tfFile = new JTextField();
    tfFile.setColumns(20);

    butBrowse =
            UIFactory.makeJButton(getMsg("browse-button-label"),
            getMsg("browse-button-tooltip"));

    BrowseActionListener l =
            new BrowseActionListener(tfFile,
                    BrowseActionListener.BrowseType.OPEN_ZIP_FILE,
                    getMainWindow());
    butBrowse.addActionListener(l);

    lblFile = UIFactory.makeJLabel(null,
                    getMsg("upgrade-choose-version-local-path"),
                    UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    JPanel pnlBrowse = Utilities.createBrowseButtonPanel(
            lblFile,
            tfFile,
            butBrowse);

    ActionListener radioListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setComponentEnablement();
      }
    };

    rbRemote.addActionListener(radioListener);
    rbLocal.addActionListener(radioListener);

    p.setLayout(new GridBagLayout());
    // p.setBorder(BorderFactory.createLineBorder(Color.RED));
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.FIRST_LINE_START;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    p.add(UIFactory.makeJLabel(currentVersionDescriptor), gbc);

    gbc.gridx = 1;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.FIRST_LINE_START;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    p.add(lblCurrentVersion, gbc);

    gbc.gridx = 0;
    gbc.gridy++;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.top = UIFactory.TOP_INSET_RADIOBUTTON + 15;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.anchor = GridBagConstraints.FIRST_LINE_START;
    p.add(rbRemote, gbc);

    gbc.gridy++;
    gbc.gridwidth = 2;
    gbc.insets.top = UIFactory.TOP_INSET_RADIO_SUBORDINATE;
    gbc.insets.left = UIFactory.LEFT_INSET_RADIO_SUBORDINATE +
            UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.anchor = GridBagConstraints.LINE_START;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 2;
    p.add(cboBuild, gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.5;
    gbc.anchor = GridBagConstraints.LINE_START;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = UIFactory.getEmptyInsets();
    JPanel fill = UIFactory.makeJPanel();
    // fill.setBorder(BorderFactory.createLineBorder(Color.BLUE));
    p.add(fill, gbc);

    gbc.gridy++;
    gbc.gridx = 0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    p.add(rbLocal, gbc);

    gbc.gridy++;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = UIFactory.TOP_INSET_RADIO_SUBORDINATE;
    gbc.insets.left = UIFactory.LEFT_INSET_RADIO_SUBORDINATE +
            UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 2;
    gbc.fill = GridBagConstraints.BOTH;
    // pnlBrowse.setBorder(BorderFactory.createLineBorder(Color.BLUE));
    p.add(pnlBrowse, gbc);

    gbc.gridy++;
    gbc.weighty = 1.0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.LINE_START;
    JPanel fill2 = UIFactory.makeJPanel();
    //fill.setBorder(BorderFactory.createLineBorder(Color.BLUE));
    p.add(fill2, gbc);

    c = p;
    return c;
  }

  /**
   * {@inheritDoc}
   */
  protected String getTitle() {
    return getMsg("upgrade-choose-version-panel-title");
  }

  /**
   * {@inheritDoc}
   */
  protected String getInstructions() {
    return getMsg("upgrade-choose-version-panel-instructions");
  }

  private RemoteBuildListComboBoxModelCreator getBuildLoader() {
    if (bld == null) {
      RemoteBuildManager rbm =
              ((Upgrader) getApplication()).getRemoteBuildManager();
      try {
        bld = new RemoteBuildListComboBoxModelCreator(rbm);
      } catch (IOException e) {
        LOG.log(Level.INFO, "error", e);
      }
    }
    return bld;
  }

  private void loadBuildList() throws IOException {
    bld.startBackgroundTask();
  }

  /**
   * Renders the combo box when there has been an error downloading
   * the build information.
   */
  private class BuildListErrorComboBoxRenderer extends JLabel
          implements ListCellRenderer {

    /**
     * The serial version identifier required to satisfy the compiler because
     * this * class extends a class that implements the
     * {@code java.io.Serializable} interface.  This value was generated using
     * the {@code serialver} command-line utility included with the Java SDK.
     */
    private static final long serialVersionUID = -7075573664472711599L;

    /**
     * Creates a default instance.
     */
    public BuildListErrorComboBoxRenderer() {
      super("Unable to access remote build information",
              UIFactory.getImageIcon(UIFactory.IconType.WARNING),
              SwingConstants.LEFT);
      UIFactory.setTextStyle(this, UIFactory.TextStyle.SECONDARY_STATUS);
      setOpaque(true);
      setBackground(Color.WHITE);
    }

    /**
     * {@inheritDoc}
     */
    public Component getListCellRendererComponent(JList jList,
                                                  Object object,
                                                  int i,
                                                  boolean b,
                                                  boolean b1) {
      return this;
    }
  }

  /**
   * Renders the combo box when there has been an error downloading
   * the build information.
   */
  private class BuildListLoadingComboBoxRenderer extends JLabel
          implements ListCellRenderer {

    /**
     * The serial version identifier required to satisfy the compiler because
     * this * class extends a class that implements the
     * {@code java.io.Serializable} interface.  This value was generated using
     * the {@code serialver} command-line utility included with the Java SDK.
     */
    private static final long serialVersionUID = -7075573664472711599L;

    /**
     * Creates a default instance.
     */
    public BuildListLoadingComboBoxRenderer() {
      super("Loading remote build information...",
              UIFactory.getImageIcon(UIFactory.IconType.WAIT_TINY),
              SwingConstants.LEFT);
      UIFactory.setTextStyle(this, UIFactory.TextStyle.SECONDARY_STATUS);
      setOpaque(true);
      setBackground(Color.WHITE);
    }

    /**
     * {@inheritDoc}
     */
    public Component getListCellRendererComponent(JList jList,
                                                  Object object,
                                                  int i,
                                                  boolean b,
                                                  boolean b1) {
      return this;
    }
  }

  /**
   * Uses the remote build manager is a separate thread to create
   * and populate the combo box model with build information.  Contains
   * the loop and dialog prompting that happens if there is a problem
   * accessing the remote build repository.
   */
  private class RemoteBuildListComboBoxModelCreator
          extends BackgroundTask<java.util.List<Build>> {

    private RemoteBuildManager rbm = null;
    private InputStream in = null;

    public RemoteBuildListComboBoxModelCreator(RemoteBuildManager rbm)
      throws IOException
    {
      this.rbm = rbm;

      // This is a lengthy operation that must be
      // performed in the event thread.  So try
      // to do this work now during construction
      // rather than when the panel becomes visible
      // for the first time.  If we fail we'll try
      // again later.
      try {
        getInputStream();
      } catch (IOException e) {
        LOG.log(Level.INFO, "Error obtaining build list input stream", e);
      }
    }

    /**
     * {@inheritDoc}
     */
    public java.util.List<Build> processBackgroundTask() throws Exception {
      return rbm.listBuilds(getInputStream());
    }

    /**
     * {@inheritDoc}
     */
    public void backgroundTaskCompleted(java.util.List<Build> buildList,
                                        Throwable throwable) {
      ComboBoxModel cbm = null;
      if (throwable == null) {
        cbm = new DefaultComboBoxModel(buildList.toArray());
      } else {
        try {
        String[] options = { "Retry", "Close" };
        int i = JOptionPane.showOptionDialog(getMainWindow(),
                new BuildListDownloadErrorPanel(rbm,
                        throwable),
                "Network Error",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                options,
                null);
        if (i == JOptionPane.NO_OPTION ||
                i == JOptionPane.CLOSED_OPTION) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              cboBuild.setRenderer(new BuildListErrorComboBoxRenderer());
              // Disable the remote widgets
              cboBuild.setEnabled(false);
              rbLocal.setSelected(true);
              rbRemote.setSelected(false);
              // grpRemoteLocal.setSelected(rbRemote.getModel(), false);
              rbRemote.setEnabled(false);
              setComponentEnablement();
            }
          });
        } else {
          loadBuildList();
        }
        } catch (Throwable t) {
          t.printStackTrace();
        }
      }
      final ComboBoxModel cbmFinal = cbm;
      if (cbm != null) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            loadBuildListAttempted = true;
            cboBuild.setModel(cbmFinal);
            cboBuild.setRenderer(new DefaultListCellRenderer());
            // Disable the remote widgets
            rbLocal.setSelected(false);
            rbRemote.setSelected(true);
            // grpRemoteLocal.setSelected(rbRemote.getModel(), false);
            rbRemote.setEnabled(true);
            setComponentEnablement();
          }
        });
      }
    }

    private InputStream getInputStream() throws IOException {
      if (this.in == null) {
        this.in = rbm.getDailyBuildsInputStream(getMainWindow(),
                "Reading build information");
      }
      return this.in;
    }
  }

  private void setComponentEnablement() {
    cboBuild.setEnabled(rbRemote.isSelected());
    lblFile.setEnabled(rbLocal.isSelected());
    tfFile.setEnabled(rbLocal.isSelected());
    butBrowse.setEnabled((rbLocal.isSelected()));
  }

//  public static void main(String[] args) {
//    final UserData ud = new UpgradeUserData();
//    ud.setServerLocation("XXX/XXXXX/XX/XXXXXXXXXXXX/XXXX");
//    Upgrader app = new Upgrader();
//    app.setUserData(ud);
//    final ChooseVersionPanel p = new ChooseVersionPanel(app);
//    p.initialize();
//    JFrame frame = new JFrame();
//    frame.getContentPane().add(p);
//    frame.addComponentListener(new ComponentAdapter() {
//      public void componentHidden(ComponentEvent componentEvent) {
//        System.exit(0);
//      }
//    });
//    frame.pack();
//    frame.setVisible(true);
//    new Thread(new Runnable() {
//      public void run() {
//        p.beginDisplay(ud);
//      }
//    }).start();
//
//  }

}
