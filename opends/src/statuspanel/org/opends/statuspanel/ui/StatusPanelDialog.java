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

package org.opends.statuspanel.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import org.opends.quicksetup.event.MinimumSizeComponentListener;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.HtmlProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;

import org.opends.statuspanel.DatabaseDescriptor;
import org.opends.statuspanel.BaseDNDescriptor;
import org.opends.statuspanel.ServerStatusDescriptor;
import org.opends.statuspanel.event.StatusPanelButtonListener;
import org.opends.statuspanel.i18n.ResourceProvider;

/**
 * This panel is used to display basic information about the server status.
 *
 */
public class StatusPanelDialog extends JFrame
{
  private static final long serialVersionUID = 6832422469078074151L;

  private ServerStatusDescriptor lastDescriptor;
  private ServerStatusDescriptor lastPackDescriptor;

  private HashSet<StatusPanelButtonListener> listeners =
    new HashSet<StatusPanelButtonListener>();

  private JButton quitButton;
  private JButton authenticateButton;

  private JLabel lServerStatus;
  private JLabel lCurrentConnections;
  private JLabel lAdministrativeUsers;
  private JLabel lInstallPath;
  private JLabel lOpenDSVersion;
  private JLabel lJavaVersion;
  private JLabel lDbTableEmpty;
  private JLabel lListenersTableEmpty;
  private JEditorPane lError;

  private JButton stopButton;
  private JButton startButton;
  private JButton restartButton;

  private HtmlProgressMessageFormatter formatter =
    new HtmlProgressMessageFormatter();

  private HashSet<JLabel> subsectionLabels = new HashSet<JLabel>();

  private DatabasesTableModel dbTableModelWithReplication;
  private DatabasesTableModel dbTableModelWithoutReplication;
  private JTable dbTableWithReplication;
  private JTable dbTableWithoutReplication;

  private ListenersTableModel listenersTableModel;
  private JTable listenersTable;

  private InstantaneousToolTipManager toolTipManager;

  private final String NOT_AVAILABLE = getMsg("not-available-label");

  /**
   * ProgressDialog constructor.
   */
  public StatusPanelDialog()
  {
    super();
    setTitle(getMsg("statuspanel-dialog-title"));
    createLayout();

    addWindowListener(new WindowAdapter()
    {
      public void windowClosing(WindowEvent e)
      {
        quitClicked();
      }
    });
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    UIFactory.IconType ic;
    if (Utils.isMacOS())
    {
      ic = UIFactory.IconType.MINIMIZED_MAC;
    } else
    {
      ic = UIFactory.IconType.MINIMIZED;
    }
    setIconImage(UIFactory.getImageIcon(ic).getImage());
  }

  /**
   * Packs and displays this dialog.
   *
   */
  public void packAndShow()
  {
    pack();
    int packedMinWidth = (int) getPreferredSize().getWidth();
    int packedMinHeight = (int) getPreferredSize().getHeight();

    int minWidth = Math.min(packedMinWidth, getMaximalWidth());
    int minHeight = Math.min(packedMinHeight, getMaximalHeight());

    addComponentListener(new MinimumSizeComponentListener(this,
        minWidth, minHeight));
    if ((minWidth != packedMinWidth) || (minHeight != packedMinHeight))
    {
      setPreferredSize(new Dimension(minWidth, minHeight));
      pack();
    }
    Utils.centerOnScreen(this);

    lastPackDescriptor = lastDescriptor;

    setVisible(true);
  }

  /**
   * Updates the contents displaying with what is specified in the provided
   * ServerStatusDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerStatusDescriptor object.
   */
  public void updateContents(ServerStatusDescriptor desc)
  {
    lastDescriptor = desc;

    updateStatusContents(desc);

    updateCurrentConnectionContents(desc);

    updateAdministrativeUserContents(desc);

    updateInstallPathContents(desc);

    updateVersionContents(desc);

    updateJavaVersionContents(desc);

    updateListenerContents(desc);

    updateDatabaseContents(desc);

    updateErrorContents(desc);

    boolean mustRepack;
    if (lastPackDescriptor == null)
    {
      mustRepack = true;
    }
    else
    {
      boolean lastSmall =
       (lastPackDescriptor.getListeners().size() == 0) &&
       (lastPackDescriptor.getDatabases().size() == 0);
      boolean currentBig =
        (lastDescriptor.getListeners().size() > 0) ||
        (lastDescriptor.getDatabases().size() > 0);
      mustRepack = lastSmall && currentBig;
    }
    if (mustRepack)
    {
      pack();
      int packedMinWidth = (int) getPreferredSize().getWidth();
      int packedMinHeight = (int) getPreferredSize().getHeight();

      int minWidth = Math.min(packedMinWidth, getMaximalWidth());
      int minHeight = Math.min(packedMinHeight, getMaximalHeight());

      if ((minWidth != packedMinWidth) || (minHeight != packedMinHeight))
      {
        setPreferredSize(new Dimension(minWidth, minHeight));
        pack();
      }

      lastPackDescriptor = lastDescriptor;
    }
  }

  /**
   * Adds a StatusPanelButtonListener that will be notified of clicks in
   * the control panel dialog.
   * @param l the StatusPanelButtonListener to be added.
   */
  public void addButtonListener(StatusPanelButtonListener l)
  {
    listeners.add(l);
  }

  /**
   * Removes a StatusPanelButtonListener.
   * @param l the StatusPanelButtonListener to be removed.
   */
  public void removeButtonListener(StatusPanelButtonListener l)
  {
    listeners.remove(l);
  }

  /**
   * Sets the enable state of the authenticate button.
   * @param enable whether to enable or disable the button.
   */
  public void setAuthenticateButtonEnabled(boolean enable)
  {
    authenticateButton.setEnabled(enable);
  }

  /**
   * Sets the enable state of the start button.
   * @param enable whether to enable or disable the button.
   */
  public void setStartButtonEnabled(boolean enable)
  {
    startButton.setEnabled(enable);
  }

  /**
   * Sets the enable state of the stop button.
   * @param enable whether to enable or disable the button.
   */
  public void setStopButtonEnabled(boolean enable)
  {
    stopButton.setEnabled(enable);
  }

  /**
   * Sets the enable state of the restart button.
   * @param enable whether to enable or disable the button.
   */
  public void setRestartButtonEnabled(boolean enable)
  {
    restartButton.setEnabled(enable);
  }

  /**
   * Creates the layout of the dialog panel.
   *
   */
  private void createLayout()
  {
    toolTipManager = new InstantaneousToolTipManager();

    /* Create input panel. */
    JPanel inputPanel = new JPanel(new GridBagLayout());
    inputPanel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    gbc.insets.bottom = UIFactory.BOTTOM_INSET_PROGRESS_BAR;
    lError = UIFactory.makeHtmlPane("", UIFactory.PROGRESS_FONT);
    lError.setOpaque(false);
    lError.setEditable(false);
    inputPanel.add(lError, gbc);
    gbc.insets.bottom = 0;
    gbc.insets.top = UIFactory.TOP_INSET_CONTROL_PANEL_SUBSECTION -
    UIFactory.getCurrentStepPanelInsets().top;
    inputPanel.add(createServerStatusPanel(), gbc);

    inputPanel.add(createServerDetailsPanel(), gbc);

    inputPanel.add(createListenersPanel(), gbc);

    inputPanel.add(createDatabasesPanel(), gbc);

    gbc.weighty = 1.0;
    inputPanel.add(Box.createVerticalGlue(), gbc);

    /* Create buttons panel */
    JPanel buttonsPanel = new JPanel(new GridBagLayout());
    buttonsPanel.setOpaque(false);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.SOUTH;
    gbc.gridwidth = 4;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.left = UIFactory.getCurrentStepPanelInsets().left;
    buttonsPanel.add(UIFactory.makeJLabel(UIFactory.IconType.OPENDS_SMALL,
        null, UIFactory.TextStyle.NO_STYLE), gbc);
    gbc.weightx = 1.0;
    gbc.gridwidth--;
    gbc.insets.left = 0;
    buttonsPanel.add(Box.createHorizontalGlue(), gbc);

    authenticateButton =
      UIFactory.makeJButton(getMsg("authenticate-button-label"),
          getMsg("authenticate-status-panel-button-tooltip"));
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0.0;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    buttonsPanel.add(authenticateButton, gbc);
    authenticateButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        authenticateClicked();
      }
    });

    quitButton =
        UIFactory.makeJButton(getMsg("quit-button-label"),
            getMsg("quit-status-panel-button-tooltip"));
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.left = UIFactory.HORIZONTAL_INSET_BETWEEN_BUTTONS;
    buttonsPanel.add(quitButton, gbc);
    quitButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        quitClicked();
      }
    });

    JPanel p = new JPanel(new GridBagLayout());
    p.setBackground(UIFactory.DEFAULT_BACKGROUND);
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    JPanel p1 = new JPanel(new GridBagLayout());
    p1.setBorder(UIFactory.DIALOG_PANEL_BORDER);
    p1.setBackground(UIFactory.CURRENT_STEP_PANEL_BACKGROUND);
    gbc.insets = UIFactory.getCurrentStepPanelInsets();
    p1.add(inputPanel, gbc);
    gbc.insets = UIFactory.getEmptyInsets();
    p.add(new JScrollPane(p1), gbc);
    gbc.weighty = 0.0;
    gbc.insets = UIFactory.getButtonsPanelInsets();
    p.add(buttonsPanel, gbc);

    getContentPane().add(p);

    /* Update the preferred sizes of labels */
    int maxWidth = 0;
    for (JLabel l : subsectionLabels)
    {
      int width = (int) l.getPreferredSize().getWidth();

      if (maxWidth <= width)
      {
        maxWidth = width;
      }
    }

    for (JLabel l : subsectionLabels)
    {
      int height = (int) l.getPreferredSize().getHeight();

      l.setPreferredSize(new Dimension(maxWidth, height));
    }
  }


  /**
   * Method called when start button is clicked.
   */
  private void startClicked()
  {
    for (StatusPanelButtonListener l : listeners)
    {
      l.startClicked();
    }
  }

  /**
   * Method called when quit button is clicked.
   */
  private void quitClicked()
  {
    for (StatusPanelButtonListener l : listeners)
    {
      l.quitClicked();
    }
  }

  /**
   * Method called when authenticate button is clicked.
   */
  private void authenticateClicked()
  {
    for (StatusPanelButtonListener l : listeners)
    {
      l.authenticateClicked();
    }
  }

  /**
   * Method called when stop button is clicked.
   */
  private void stopClicked()
  {
    for (StatusPanelButtonListener l : listeners)
    {
      l.stopClicked();
    }
  }

  /**
   * Method called when restart button is clicked.
   */
  private void restartClicked()
  {
    for (StatusPanelButtonListener l : listeners)
    {
      l.restartClicked();
    }
  }

  /**
   * Method usedto create the subsection title with two decoration icons
   * surrounding the text.
   * @param title the title of the subsection.
   */
  private JPanel createSubsectionTitle(String title)
  {
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridwidth = 5;
    gbc.weightx  = 0.5;
    p.add(Box.createHorizontalGlue(), gbc);
    gbc.weightx = 0.0;
    gbc.gridwidth--;
    p.add(UIFactory.makeJLabel(UIFactory.IconType.SUBSECTION_LEFT, null,
        UIFactory.TextStyle.NO_STYLE), gbc);
    gbc.weightx = 0.0;
    gbc.gridwidth--;
    gbc.insets.left = UIFactory.HORIZONTAL_INSET_CONTROL_PANEL_SUBSECTION;
    JLabel l = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, title,
        UIFactory.TextStyle.TITLE);
    l.setHorizontalAlignment(SwingConstants.CENTER);
    subsectionLabels.add(l);
    p.add(l, gbc);
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    p.add(UIFactory.makeJLabel(UIFactory.IconType.SUBSECTION_RIGHT, null,
        UIFactory.TextStyle.NO_STYLE), gbc);
    gbc.weightx = 0.5;
    gbc.insets.left = 0;
    p.add(Box.createHorizontalGlue(), gbc);

    return p;
  }

  /**
   * Creates the server status subsection panel.
   * @return the server status subsection panel.
   */
  private JPanel createServerStatusPanel()
  {
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.insets = UIFactory.getEmptyInsets();

    p.add(createSubsectionTitle(getMsg("server-status-title")), gbc);

    JPanel auxPanel = new JPanel(new GridBagLayout());
    auxPanel.setOpaque(false);
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    auxPanel.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        getMsg("server-status-label"), UIFactory.TextStyle.PRIMARY_FIELD_VALID),
        gbc);

    JPanel statusPanel = new JPanel(new GridBagLayout());
    statusPanel.setOpaque(false);
    gbc.gridwidth = 6;
    gbc.weightx = 0.0;
    lServerStatus = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        NOT_AVAILABLE, UIFactory.TextStyle.READ_ONLY);
    statusPanel.add(lServerStatus, gbc);
    toolTipManager.registerComponent(lServerStatus);
    gbc.gridwidth--;

    stopButton = UIFactory.makeJButton(getMsg("stop-button-label"),
        getMsg("stop-button-tooltip"));
    stopButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        stopClicked();
      }
    });
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    statusPanel.add(stopButton, gbc);

    gbc.gridwidth--;
    gbc.insets.left = UIFactory.HORIZONTAL_INSET_BETWEEN_BUTTONS;
    startButton = UIFactory.makeJButton(getMsg("start-button-label"),
        getMsg("start-button-tooltip"));
    statusPanel.add(startButton, gbc);
    startButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        startClicked();
      }
    });

    gbc.gridwidth--;
    restartButton = UIFactory.makeJButton(getMsg("restart-button-label"),
        getMsg("restart-button-tooltip"));
    restartButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        restartClicked();
      }
    });
    statusPanel.add(restartButton, gbc);

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 1.0;
    gbc.insets.left = 0;
    statusPanel.add(Box.createHorizontalGlue(), gbc);

    int maxButtonHeight = 0;
    maxButtonHeight = Math.max(maxButtonHeight,
        (int)startButton.getPreferredSize().getHeight());
    maxButtonHeight = Math.max(maxButtonHeight,
        (int)restartButton.getPreferredSize().getHeight());
    maxButtonHeight = Math.max(maxButtonHeight,
        (int)stopButton.getPreferredSize().getHeight());

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 0.0;
    statusPanel.add(Box.createVerticalStrut(maxButtonHeight), gbc);

    gbc.weightx = 1.0;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    auxPanel.add(statusPanel, gbc);

    gbc.insets.left = 0;
    gbc.weightx = 0.0;
    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    auxPanel.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        getMsg("connections-label"), UIFactory.TextStyle.PRIMARY_FIELD_VALID),
        gbc);
    lCurrentConnections = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        NOT_AVAILABLE, UIFactory.TextStyle.READ_ONLY);
    toolTipManager.registerComponent(lCurrentConnections);

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    auxPanel.add(lCurrentConnections, gbc);

    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    gbc.insets.left = 0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    p.add(auxPanel, gbc);

    return p;
  }

  /**
   * Creates the server details subsection panel.
   * @return the server details subsection panel.
   */
  private JPanel createServerDetailsPanel()
  {
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;

    p.add(createSubsectionTitle(getMsg("server-details-title")), gbc);

    JPanel auxPanel = new JPanel(new GridBagLayout());
    auxPanel.setOpaque(false);
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.weightx = 0.0;
    JLabel[] leftLabels =
      {
        UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
            getMsg("administrative-users-label"),
            UIFactory.TextStyle.PRIMARY_FIELD_VALID),
        UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
            getMsg("installation-path-label"),
            UIFactory.TextStyle.PRIMARY_FIELD_VALID),
        UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
            getMsg("opends-version-label"),
            UIFactory.TextStyle.PRIMARY_FIELD_VALID),
        UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
            getMsg("java-version-label"),
            UIFactory.TextStyle.PRIMARY_FIELD_VALID)
      };

    lAdministrativeUsers = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        NOT_AVAILABLE, UIFactory.TextStyle.READ_ONLY);
    lInstallPath = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        NOT_AVAILABLE, UIFactory.TextStyle.READ_ONLY);
    lOpenDSVersion = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        NOT_AVAILABLE, UIFactory.TextStyle.READ_ONLY);
    lJavaVersion = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        NOT_AVAILABLE, UIFactory.TextStyle.READ_ONLY);

    JLabel[] rightLabels =
      {
        lAdministrativeUsers, lInstallPath, lOpenDSVersion, lJavaVersion
      };


    for (int i=0; i<leftLabels.length; i++)
    {
      gbc.insets.left = 0;
      if (i != 0)
      {
        gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
      }
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      auxPanel.add(leftLabels[i], gbc);

      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
      auxPanel.add(rightLabels[i], gbc);
      toolTipManager.registerComponent(rightLabels[i]);
    }

    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    gbc.insets.left = 0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    p.add(auxPanel, gbc);

    return p;
  }

  /**
   * Creates the server listeners subsection panel.
   * @return the server listeners subsection panel.
   */
  private JPanel createListenersPanel()
  {
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    p.add(createSubsectionTitle(getMsg("listeners-title")), gbc);

    listenersTableModel = new ListenersTableModel();
    listenersTable = UIFactory.makeSortableTable(listenersTableModel,
        new ListenersCellRenderer(),
        UIFactory.makeHeaderRenderer());
    listenersTable.setFocusable(false);

    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    p.add(listenersTable.getTableHeader(), gbc);
    int height = (int)
    listenersTable.getTableHeader().getPreferredSize().getHeight();
    listenersTable.setRowHeight(height);
    gbc.insets.top = 0;
    p.add(listenersTable, gbc);

    lListenersTableEmpty = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, "",
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    p.add(lListenersTableEmpty, gbc);
    lListenersTableEmpty.setVisible(false);
    toolTipManager.registerComponent(lListenersTableEmpty);
    return p;
  }

  /**
   * Creates the server databases subsection panel.
   * @return the server databases subsection panel.
   */
  private JPanel createDatabasesPanel()
  {
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    p.add(createSubsectionTitle(getMsg("databases-title")), gbc);

    dbTableModelWithReplication = new DatabasesTableModel(true);
    dbTableModelWithoutReplication = new DatabasesTableModel(false);
    dbTableWithReplication =
      UIFactory.makeSortableTable(dbTableModelWithReplication,
        new DatabasesCellRenderer(),
        UIFactory.makeHeaderRenderer());
    dbTableWithReplication.setFocusable(false);
    toolTipManager.registerComponent(dbTableWithReplication);
    dbTableWithoutReplication =
      UIFactory.makeSortableTable(dbTableModelWithoutReplication,
        new DatabasesCellRenderer(),
        UIFactory.makeHeaderRenderer());
    dbTableWithoutReplication.setFocusable(false);
    toolTipManager.registerComponent(dbTableWithoutReplication);

    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    p.add(dbTableWithReplication.getTableHeader(), gbc);
    int height = (int)dbTableWithReplication.getTableHeader().
    getPreferredSize().getHeight();
    dbTableWithReplication.setRowHeight(height);
    gbc.insets.top = 0;
    p.add(dbTableWithReplication, gbc);

    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    p.add(dbTableWithoutReplication.getTableHeader(), gbc);
    height = (int)dbTableWithoutReplication.getTableHeader().
    getPreferredSize().getHeight();
    dbTableWithoutReplication.setRowHeight(height);
    gbc.insets.top = 0;
    p.add(dbTableWithoutReplication, gbc);
    dbTableWithoutReplication.setVisible(false);

    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    lDbTableEmpty = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, "",
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    p.add(lDbTableEmpty, gbc);
    lDbTableEmpty.setVisible(false);
    toolTipManager.registerComponent(lDbTableEmpty);
    return p;
  }


  /**
   * Sets the not available text to a label and associates a help icon and
   * a tooltip explaining that the data is not available because the server is
   * down.
   * @param l the label.
   */
  private void setNotAvailableBecauseServerIsDown(JLabel l)
  {
    l.setText(NOT_AVAILABLE);
    l.setIcon(UIFactory.getImageIcon(UIFactory.IconType.HELP_SMALL));
    l.setToolTipText(getMsg("not-available-server-down-tooltip"));
    l.setHorizontalTextPosition(SwingConstants.LEFT);
  }

  /**
   * Sets the not available text to a label and associates a help icon and
   * a tooltip explaining that the data is not available because authentication
   * is required.
   * @param l the label.
   */
  private void setNotAvailableBecauseAuthenticationIsRequired(JLabel l)
  {
    l.setText(NOT_AVAILABLE);
    l.setIcon(UIFactory.getImageIcon(UIFactory.IconType.HELP_SMALL));
    l.setToolTipText(getMsg("not-available-authentication-required-tooltip"));
    l.setHorizontalTextPosition(SwingConstants.LEFT);
  }

  /**
   * Sets the not available text to a label with no icon nor tooltip.
   * @param l the label.
   */
  private void setNotAvailable(JLabel l)
  {
    l.setText(NOT_AVAILABLE);
    l.setIcon(null);
    l.setToolTipText(null);
  }

  /**
   * Sets the a text to a label with no icon nor tooltip.
   * @param l the label.
   */
  private void setTextValue(JLabel l, String text)
  {
    l.setText(text);
    l.setIcon(null);
    l.setToolTipText(null);
  }

  /**
   * Updates the status contents displaying with what is specified in the
   * provided ServerStatusDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerStatusDescriptor object.
   */
  private void updateStatusContents(ServerStatusDescriptor desc)
  {
    String status;
    switch (desc.getStatus())
    {
    case STARTED:
      status = getMsg("server-started-label");
      startButton.setVisible(false);
      restartButton.setVisible(true);
      stopButton.setVisible(true);
      break;

    case STOPPED:
      status = getMsg("server-stopped-label");
      startButton.setVisible(true);
      restartButton.setVisible(false);
      stopButton.setVisible(false);
      break;

    case STARTING:
      status = getMsg("server-starting-label");
      startButton.setVisible(false);
      restartButton.setVisible(false);
      stopButton.setVisible(false);
      break;

    case STOPPING:
      status = getMsg("server-stopping-label");
      startButton.setVisible(false);
      restartButton.setVisible(false);
      stopButton.setVisible(false);
      break;

    case UNKNOWN:
      status = getMsg("server-unknown-status-label");
      startButton.setVisible(false);
      restartButton.setVisible(true);
      stopButton.setVisible(true);
      break;

    default:
      throw new IllegalStateException("Unknown status: "+desc.getStatus());
    }
    lServerStatus.setText(status);

    /* Enable authenticate button only if the server is started AND we have
     * no authentication (or the authentication we have does not seem to work
     * because we get an error).
     */
    authenticateButton.setVisible(
        (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED) &&
        (!desc.isAuthenticated() || (desc.getErrorMessage() != null)));
  }

  /**
   * Updates the current connection contents displaying with what is specified
   * in the provided ServerStatusDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerStatusDescriptor object.
   */
  private void updateCurrentConnectionContents(ServerStatusDescriptor desc)
  {
    if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
    {
      int nConn = desc.getOpenConnections();
      if (nConn >= 0)
      {
        setTextValue(lCurrentConnections, String.valueOf(nConn));
      }
      else
      {
        if (!desc.isAuthenticated())
        {
          setNotAvailableBecauseAuthenticationIsRequired(lCurrentConnections);
        }
        else
        {
          setNotAvailable(lCurrentConnections);
        }
      }
    }
    else
    {
      setNotAvailableBecauseServerIsDown(lCurrentConnections);
    }
  }

  /**
   * Updates the admiinistrative user contents displaying with what is specified
   * in the provided ServerStatusDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerStatusDescriptor object.
   */
  private void updateAdministrativeUserContents(ServerStatusDescriptor desc)
  {
    Set<String> administrators = desc.getAdministrativeUsers();
    if (administrators.size() > 0)
    {
      TreeSet<String> ordered = new TreeSet<String>();
      for (String name: administrators)
      {
        ordered.add(formatter.getFormattedText(name));
      }

      setTextValue(lAdministrativeUsers,"<html>"+
          UIFactory.applyFontToHtml(
              Utils.getStringFromCollection(ordered, "<br>"),
              UIFactory.READ_ONLY_FONT));
    }
    else
    {
      if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
      {
        if (!desc.isAuthenticated())
        {
          setNotAvailableBecauseAuthenticationIsRequired(lAdministrativeUsers);
        }
        else
        {
          setNotAvailable(lAdministrativeUsers);
        }
      }
      else
      {
        setNotAvailable(lAdministrativeUsers);
      }
    }
  }

  /**
   * Updates the install path contents displaying with what is specified in the
   * provided ServerStatusDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerStatusDescriptor object.
   */
  private void updateInstallPathContents(ServerStatusDescriptor desc)
  {
    File path = desc.getInstallPath();
    lInstallPath.setText(path.toString());
  }

  /**
   * Updates the server version contents displaying with what is specified in
   * the provided ServerStatusDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerStatusDescriptor object.
   */
  private void updateVersionContents(ServerStatusDescriptor desc)
  {
    String openDSVersion = desc.getOpenDSVersion();
    lOpenDSVersion.setText(openDSVersion);
  }

  /**
   * Updates the java version contents displaying with what is specified in
   * the provided ServerStatusDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerStatusDescriptor object.
   */
  private void updateJavaVersionContents(ServerStatusDescriptor desc)
  {
    if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
    {
      String javaVersion = desc.getJavaVersion();
      if (javaVersion != null)
      {
        setTextValue(lJavaVersion, javaVersion);

      }
      else
      {
        if (!desc.isAuthenticated())
        {
          setNotAvailableBecauseAuthenticationIsRequired(lJavaVersion);
        }
        else
        {
          setNotAvailable(lJavaVersion);
        }
      }
    }
    else
    {
      setNotAvailableBecauseServerIsDown(lJavaVersion);
    }
  }

  /**
   * Updates the listeners contents displaying with what is specified in
   * the provided ServerStatusDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerStatusDescriptor object.
   */
  private void updateListenerContents(ServerStatusDescriptor desc)
  {
    listenersTableModel.setData(desc.getListeners());

    if (listenersTableModel.getRowCount() == 0)
    {
      listenersTable.setVisible(false);
      listenersTable.getTableHeader().setVisible(false);
      lListenersTableEmpty.setVisible(true);
      if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
      {
        if (!desc.isAuthenticated())
        {
          setNotAvailableBecauseAuthenticationIsRequired(lListenersTableEmpty);
        }
        else
        {
          setTextValue(lListenersTableEmpty, getMsg("no-listeners-found"));
        }
      }
      else
      {
        setTextValue(lListenersTableEmpty, getMsg("no-listeners-found"));
      }
    }
    else
    {
      listenersTable.setVisible(true);
      listenersTable.getTableHeader().setVisible(true);
      lListenersTableEmpty.setVisible(false);
    }
  }

  /**
   * Updates the databases contents displaying with what is specified in
   * the provided ServerStatusDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerStatusDescriptor object.
   */
  private void updateDatabaseContents(ServerStatusDescriptor desc)
  {
    Set<BaseDNDescriptor> replicas = new HashSet<BaseDNDescriptor>();
    Set<DatabaseDescriptor> dbs = desc.getDatabases();
    for (DatabaseDescriptor db: dbs)
    {
      replicas.addAll(db.getBaseDns());
    }
    dbTableModelWithReplication.setData(replicas);
    dbTableModelWithoutReplication.setData(replicas);

    if (dbTableModelWithReplication.getRowCount() == 0)
    {

      dbTableWithoutReplication.setVisible(false);
      dbTableWithoutReplication.getTableHeader().setVisible(false);
      dbTableWithReplication.setVisible(false);
      dbTableWithReplication.getTableHeader().setVisible(false);
      lDbTableEmpty.setVisible(true);
      if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
      {
        if (!desc.isAuthenticated())
        {
          setNotAvailableBecauseAuthenticationIsRequired(lDbTableEmpty);
        }
        else
        {
          setTextValue(lDbTableEmpty, getMsg("no-dbs-found"));
        }
      }
      else
      {
        setTextValue(lDbTableEmpty, getMsg("no-dbs-found"));
      }
    }
    else
    {
      boolean replicated = false;
      for (BaseDNDescriptor suffix: replicas)
      {
        if (suffix.getType() == BaseDNDescriptor.Type.REPLICATED)
        {
          replicated = true;
        }
      }

      updateTableSizes(dbTableWithoutReplication);
      updateTableSizes(dbTableWithReplication);
      dbTableWithoutReplication.setVisible(!replicated);
      dbTableWithoutReplication.getTableHeader().setVisible(!replicated);
      dbTableWithReplication.setVisible(replicated);
      dbTableWithReplication.getTableHeader().setVisible(replicated);
      lDbTableEmpty.setVisible(false);
    }
  }

  /**
   * Updates the error label contents displaying with what is specified in
   * the provided ServerStatusDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerStatusDescriptor object.
   */
  private void updateErrorContents(ServerStatusDescriptor desc)
  {
    String errorMsg = desc.getErrorMessage();
    if (errorMsg == null)
    {
      lError.setVisible(false);
    }
    else
    {

      lError.setVisible(true);
      lError.setText(formatter.getFormattedError(errorMsg, false));
    }
  }

  /**
   * The following three methods are just commodity methods to get localized
   * messages.
   */
  private String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  private ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }

  /**
   * Updates the size of the table rows according to the size of the
   * rendered component.
   * @param table the table to handle.
   */
  private void updateTableSizes(JTable table)
  {
    updateTableColumnWidth(table);
    updateTableRowHeight(table);

    /*
    int totalWidth = 0;
    int colMargin = table.getColumnModel().getColumnMargin();

    int totalHeight = 0;

    TableColumn tcol = table.getColumnModel().getColumn(0);
    TableCellRenderer renderer = tcol.getHeaderRenderer();
    Component comp = renderer.getTableCellRendererComponent(table,
        table.getModel().getColumnName(0), false, false, 0, 0);
    totalHeight = (int)comp.getPreferredSize().getHeight();
    for (int row=0; row<table.getRowCount(); row++)
    {
      totalHeight += table.getRowHeight(row);
    }

    for (int col=0; col<table.getColumnCount(); col++)
    {
      tcol = table.getColumnModel().getColumn(col);
      totalWidth += tcol.getPreferredWidth() + colMargin;
    }

    table.setPreferredScrollableViewportSize(
        new Dimension(totalWidth, totalHeight));
        */
  }

  /**
   * Updates the height of the table rows according to the size of the
   * rendered component.
   * @param table the table to handle.
   */
  private void updateTableRowHeight(JTable table)
  {
    int headerMaxHeight = 0;

    for (int col=0; col<table.getColumnCount(); col++)
    {
      TableColumn tcol = table.getColumnModel().getColumn(col);
      TableCellRenderer renderer = tcol.getHeaderRenderer();
      Component comp = renderer.getTableCellRendererComponent(table,
          table.getModel().getColumnName(col), false, false, 0, col);
      int colHeight = (int)comp.getPreferredSize().getHeight();
      headerMaxHeight = Math.max(headerMaxHeight, colHeight);
    }
    JTableHeader header = table.getTableHeader();
    header.setPreferredSize(new Dimension(
        (int)header.getPreferredSize().getWidth(),
        headerMaxHeight));

    for (int row=0; row<table.getRowCount(); row++)
    {
      int rowMaxHeight = table.getRowHeight();
      for (int col=0; col<table.getColumnCount(); col++)
      {
        TableCellRenderer renderer = table.getCellRenderer(row, col);
        Component comp = table.prepareRenderer(renderer, row, col);
        int colHeight = (int)comp.getPreferredSize().getHeight();
        rowMaxHeight = Math.max(rowMaxHeight, colHeight);
      }
      table.setRowHeight(row, rowMaxHeight);
    }
  }

  /**
   * Updates the height of the table columns according to the size of the
   * rendered component.
   * @param table the table to handle.
   */
  private void updateTableColumnWidth(JTable table)
  {

    int margin = table.getIntercellSpacing().width;
    for (int col=0; col<table.getColumnCount(); col++)
    {
      int colMaxWidth;
      TableColumn tcol = table.getColumnModel().getColumn(col);
      TableCellRenderer renderer = tcol.getHeaderRenderer();
      Component comp = renderer.getTableCellRendererComponent(table,
          table.getModel().getColumnName(col), false, false, 0, col);
      colMaxWidth = (int)comp.getPreferredSize().getWidth();
      for (int row=0; row<table.getRowCount(); row++)
      {
        renderer = table.getCellRenderer(row, col);
        comp = table.prepareRenderer(renderer, row, col);
        int colWidth = (int)comp.getPreferredSize().getWidth() + (2 * margin);
        colMaxWidth = Math.max(colMaxWidth, colWidth);
      }
      tcol.setPreferredWidth(colMaxWidth);
    }
  }

  private int getMaximalWidth()
  {
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

    boolean multipleScreen = screenSize.width / screenSize.height >= 2;

    if (multipleScreen)
    {
      return Math.min((screenSize.width/2) - 100, 1000);
    }
    else
    {
      return Math.min(screenSize.width - 100, 1000);
    }
  }

  /**
   * Returns the maximum height we allow this dialog to have after pack.
   * @return the maximum height we allow this dialog to have after pack.
   */
  private int getMaximalHeight()
  {
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

    return Math.min(screenSize.height - 100, 800);
  }
  /**
   * Method written for testing purposes.
   * @param args the arguments to be passed to the test program.
   */
  public static void main(String[] args)
  {
    try
    {
      UIFactory.initialize();
      StatusPanelDialog dlg = new StatusPanelDialog();
      dlg.packAndShow();
    } catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }

  /**
   * Class used to render the databases table cells.
   */
  class DatabasesCellRenderer extends JLabel implements TableCellRenderer
  {
    private static final long serialVersionUID = -256719167426289735L;

    /**
     * Default constructor.
     */
    public DatabasesCellRenderer()
    {
      super();
      UIFactory.setTextStyle(this, UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    }

    /**
     * {@inheritDoc}
     */
    public Component getTableCellRendererComponent(JTable table, Object value,
        boolean isSelected, boolean hasFocus, int row, int column) {
      if (value instanceof String)
      {
        setTextValue(this, (String)value);
      }
      else if (value instanceof Set)
      {
        LinkedHashSet<String> baseDns = new LinkedHashSet<String>();
        for (Object v : (Set)value)
        {
          baseDns.add((String)v);
        }
        setTextValue(this, "<html>" +
            UIFactory.applyFontToHtml(Utils.getStringFromCollection(
                baseDns, "<br>"),
                UIFactory.SECONDARY_FIELD_VALID_FONT));
      }
      else
      {
        /* Is the number of entries: check if it is available or not */
        if (lastDescriptor.getStatus() ==
          ServerStatusDescriptor.ServerStatus.STARTED)
        {
          int nEntries = (Integer)value;
          if (nEntries >= 0)
          {
            setTextValue(this, String.valueOf(nEntries));
          }
          else
          {
            if (!lastDescriptor.isAuthenticated())
            {
              setNotAvailableBecauseAuthenticationIsRequired(this);
            }
            else
            {
              setNotAvailable(this);
            }
          }
        }
        else
        {
          setNotAvailableBecauseServerIsDown(this);
        }
      }
      if (column == 0)
      {
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0,
                UIFactory.PANEL_BORDER_COLOR),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
      }
      else
      {
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
      }
      return this;
    }
  }

  /**
   * Class used to render the listeners table cells.
   */
  class ListenersCellRenderer extends JLabel implements TableCellRenderer
  {
    private static final long serialVersionUID = -256719167426289735L;

    /**
     * Default constructor.
     */
    public ListenersCellRenderer()
    {
      super();
      UIFactory.setTextStyle(this, UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    }

    /**
     * {@inheritDoc}
     */
    public Component getTableCellRendererComponent(JTable table, Object value,
        boolean isSelected, boolean hasFocus, int row, int column) {

      setTextValue(this, (String)value);
      if (column == 0)
      {
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0,
                UIFactory.PANEL_BORDER_COLOR),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
      }
      else
      {
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
      }
      return this;
    }
  }
}

/**
 * This class is used to be able to have an instantaneous tooltip displayed
 * in the 'not available' labels.  It replaces the default ToolTipManager class.
 *
 */
class InstantaneousToolTipManager extends MouseAdapter
implements MouseMotionListener
{
  private ToolTipManager ttipManager = ToolTipManager.sharedInstance();
  private Popup tipWindow;
  private boolean isVisible;

  /**
   * The default constructor.
   */
  public InstantaneousToolTipManager()
  {
  }

  /**
   * Register a component that will use this tool tip manager to display tool
   * tips.
   * @param comp the component to be registered.
   */
  public void registerComponent(JComponent comp)
  {
    ttipManager.unregisterComponent(comp);
    comp.removeMouseListener(this);
    comp.addMouseListener(this);
    comp.removeMouseMotionListener(this);
    comp.addMouseMotionListener(this);
  }

  /**
   * Unregisters a component.  Calling this method makes the component to be
   * registered by the default tool tip manager.
   * @param comp the component to be unregistered.
   */
  public void unregisterComponent(JComponent comp)
  {
    ttipManager.registerComponent(comp);
    comp.removeMouseListener(this);
    comp.removeMouseMotionListener(this);
  }

  /**
   * {@inheritDoc}
   */
  public void mouseDragged(MouseEvent event)
  {
  }

  /**
   * {@inheritDoc}
   */
  public void mouseEntered(MouseEvent event)
  {
    displayToolTip(event);
  }

  /**
   * {@inheritDoc}
   */
  public void mouseExited(MouseEvent event)
  {
    hideToolTip(event);
  }

  /**
   * {@inheritDoc}
   */
  public void mouseMoved(MouseEvent event)
  {
    hideToolTip(event);
    displayToolTip(event);
  }

  /**
   * {@inheritDoc}
   */
  public void mousePressed(MouseEvent event)
  {
    if (isVisible)
    {
      hideToolTip(event);
    }
    else
    {
      hideToolTip(event);
      displayToolTip(event);
    }
  }

  /**
   * Displays a tooltip depending on the MouseEvent received.
   * @param event the mouse event.
   */
  private void displayToolTip(MouseEvent event)
  {
    JComponent component = (JComponent)event.getSource();
    String toolTipText = component.getToolTipText(event);
    if (toolTipText != null)
    {
      Point preferredLocation = component.getToolTipLocation(event);
      Rectangle sBounds = component.getGraphicsConfiguration().
      getBounds();

      JToolTip tip = component.createToolTip();
      tip.setTipText(toolTipText);
      Dimension size = tip.getPreferredSize();
      Point location = new Point();

      Point screenLocation = component.getLocationOnScreen();
      if(preferredLocation != null)
      {
        location.x = screenLocation.x + preferredLocation.x;
        location.y = screenLocation.y + preferredLocation.y;
      }
      else
      {
        location.x = screenLocation.x + event.getX();
        location.y = screenLocation.y + event.getY() + 20;
      }

      if (location.x < sBounds.x) {
        location.x = sBounds.x;
      }
      else if (location.x - sBounds.x + size.width > sBounds.width) {
        location.x = sBounds.x + Math.max(0, sBounds.width - size.width);
      }
      if (location.y < sBounds.y) {
        location.y = sBounds.y;
      }
      else if (location.y - sBounds.y + size.height > sBounds.height) {
        location.y = sBounds.y + Math.max(0, sBounds.height - size.height);
      }

      PopupFactory popupFactory = PopupFactory.getSharedInstance();
      tipWindow = popupFactory.getPopup(component, tip, location.x, location.y);
      tipWindow.show();
    }
    isVisible = true;
  }

  /**
   * Hides the tooltip if we are displaying it.
   * @param event the mouse event.
   */
  private void hideToolTip(MouseEvent event)
  {
    if (tipWindow != null)
    {
      tipWindow.hide();
      tipWindow = null;
    }
    isVisible = false;
  }
}
