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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;

import org.opends.guitools.controlpanel.datamodel.BasicMonitoringAttributes;
import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.ConnectionHandlerDescriptor;
import org.opends.guitools.controlpanel.datamodel.
 ConnectionHandlersMonitoringTableModel;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.MonitoringAttributes;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.datamodel.ConnectionHandlerDescriptor.
 Protocol;
import org.opends.guitools.controlpanel.datamodel.ConnectionHandlerDescriptor.
 State;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.StatusGenericPanel.
 IgnoreItemListener;
import org.opends.guitools.controlpanel.ui.renderer.CustomListCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.guitools.controlpanel.util.ViewPositions;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

/**
 * Class that displays the monitoring information of connection handlers.
 *
 */
public class ConnectionHandlerMonitoringPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -6462932160985559830L;

  private MonitoringAttributesViewPanel<MonitoringAttributes>
  operationViewPanel;
  private GenericDialog operationViewDlg;

  private JComboBox connectionHandlers;

  private JTable connectionHandlersTable;
  private JScrollPane connectionHandlersScroll;
  private ConnectionHandlersMonitoringTableModel connectionHandlersTableModel;

  private JLabel lNoConnectionHandlers = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NO_CONNECTION_HANDLER_FOUND.get());

  private boolean firstRealDataSet = false;

  private JCheckBoxMenuItem showAveragesMenu;

  private ConnectionHandlerMonitoringMenuBar menuBar;

  private LinkedHashSet<MonitoringAttributes> chOperations =
    new LinkedHashSet<MonitoringAttributes>();
  {
    chOperations.add(BasicMonitoringAttributes.ADD_REQUESTS);
    chOperations.add(BasicMonitoringAttributes.BIND_REQUESTS);
    chOperations.add(BasicMonitoringAttributes.DELETE_REQUESTS);
    chOperations.add(BasicMonitoringAttributes.MOD_REQUESTS);
    chOperations.add(BasicMonitoringAttributes.MOD_DN_REQUESTS);
    chOperations.add(BasicMonitoringAttributes.SEARCH_REQUESTS);
  }
  private LinkedHashSet<MonitoringAttributes> allowedChOperations =
    new LinkedHashSet<MonitoringAttributes>();
  {
    allowedChOperations.addAll(chOperations);
    allowedChOperations.add(BasicMonitoringAttributes.ADD_RESPONSES);
    allowedChOperations.add(BasicMonitoringAttributes.BIND_RESPONSES);
    allowedChOperations.add(BasicMonitoringAttributes.COMPARE_RESPONSES);
    allowedChOperations.add(BasicMonitoringAttributes.DELETE_RESPONSES);
    allowedChOperations.add(BasicMonitoringAttributes.EXTENDED_REQUESTS);
    allowedChOperations.add(BasicMonitoringAttributes.EXTENDED_RESPONSES);
    allowedChOperations.add(BasicMonitoringAttributes.MOD_DN_RESPONSES);
    allowedChOperations.add(BasicMonitoringAttributes.MOD_RESPONSES);
    allowedChOperations.add(BasicMonitoringAttributes.SEARCH_DONE);
    allowedChOperations.add(BasicMonitoringAttributes.UNBIND_REQUESTS);
  }

  private Message ALL_CONNECTION_HANDLERS =
    INFO_CTRL_PANEL_ALL_CONNECTION_HANDLERS.get();

  /**
   * Default constructor.
   *
   */
  public ConnectionHandlerMonitoringPanel()
  {
    super();
    createLayout();
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = 1;
    gbc.weightx = 1.0;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    addErrorPane(gbc);
    gbc.weighty = 1.0;
    JPanel viewOptions = new JPanel(new GridBagLayout());
    viewOptions.setBackground(ColorAndFontConstants.greyBackground);
    viewOptions.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
        ColorAndFontConstants.defaultBorderColor));
    gbc.gridwidth = 1;
    JLabel l = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_CONNECTION_HANDLERS_LABEL.get());
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    gbc.insets = new Insets(10, 10, 10, 0);
    viewOptions.add(l, gbc);
    gbc.insets.left = 5;
    gbc.insets.right = 10;
    connectionHandlers = new JComboBox(
        new DefaultComboBoxModel(new String[]{"fakeconnectionhandlername"}));
    connectionHandlers.addItemListener(
        new IgnoreItemListener(connectionHandlers));
    connectionHandlers.addItemListener(new ItemListener()
    {
      public void itemStateChanged(ItemEvent ev)
      {
        if (ev.getStateChange() == ItemEvent.SELECTED)
        {
          updateConnectionHandlersPanel(
              getInfo().getServerDescriptor(),
              errorPane.isVisible());
        }
      }
    });
    connectionHandlers.setRenderer(
        new CustomComboBoxCellRenderer(connectionHandlers));
    gbc.gridx ++;
    viewOptions.add(connectionHandlers, gbc);
    gbc.gridx ++;
    gbc.weightx = 1.0;
    gbc.insets.left = 0;
    gbc.insets.right = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    viewOptions.add(Box.createHorizontalGlue(), gbc);

    gbc.gridy = 1;
    gbc.gridx = 0;
    gbc.gridwidth = 1;
    gbc.insets.left = 0;
    gbc.weighty = 0.0;
    gbc.insets = new Insets(0, 0, 0, 0);
    add(viewOptions, gbc);

    connectionHandlersTableModel = new ConnectionHandlersMonitoringTableModel();
    // Add some fake data.
    String[] names = {"First Connection Handler", "Second Connection Handler",
        "Third Connection Handler", "Fourth Connection Handler",
        "Fifth Connection Handler", "Connection Handler with a long name"};

    Set<ConnectionHandlerDescriptor> fakeData =
      new HashSet<ConnectionHandlerDescriptor>();
    connectionHandlersTableModel.setAttributes(chOperations, false);
    try
    {
      Set<InetAddress> addresses = new HashSet<InetAddress>();
      addresses.add(InetAddress.getLocalHost());
      Set<CustomSearchResult> emptySet = Collections.emptySet();
      for (String name : names)
      {
        fakeData.add(new ConnectionHandlerDescriptor(
            addresses, 1389, Protocol.LDAP, State.ENABLED, name, emptySet));
      }
      connectionHandlersTableModel.setData(fakeData, 0);
    }
    catch (Throwable t)
    {
      // Ignore
    }
    connectionHandlersTable = Utilities.createSortableTable(
        connectionHandlersTableModel,
        new DefaultTableCellRenderer());
    connectionHandlersScroll = Utilities.createScrollPane(
        connectionHandlersTable);
    gbc.insets = new Insets(10, 10, 10, 10);
    gbc.gridy ++;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    add(connectionHandlersScroll, gbc);
    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.anchor = GridBagConstraints.CENTER;
    add(lNoConnectionHandlers, gbc);
    updateTableSizes();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_CONNECTION_HANDLER_MONITORING_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public JMenuBar getMenuBar()
  {
    if (menuBar == null)
    {
      menuBar =
        new ConnectionHandlerMonitoringMenuBar(getInfo());
    }
    return menuBar;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final ServerDescriptor server = ev.getNewDescriptor();
    LinkedHashSet<Object> newElements = new LinkedHashSet<Object>();

    newElements.add(new CategorizedComboBoxElement(ALL_CONNECTION_HANDLERS,
        CategorizedComboBoxElement.Type.REGULAR));
    Set<ConnectionHandlerDescriptor> chs = server.getConnectionHandlers();

    SortedSet<ConnectionHandlerDescriptor> sortedChs =
      new TreeSet<ConnectionHandlerDescriptor>(
          new Comparator<ConnectionHandlerDescriptor>()
          {
            public int compare(ConnectionHandlerDescriptor desc1,
                ConnectionHandlerDescriptor desc2)
            {
              int compare = 0;
              if (desc1.getAddresses().equals(desc2.getAddresses()))
              {
                Integer port1 = new Integer(desc1.getPort());
                Integer port2 = new Integer(desc2.getPort());
                compare = port1.compareTo(port2);
              }
              else
              {
                compare = getConnectionHandlerLabel(desc1).compareTo(
                    getConnectionHandlerLabel(desc2));
              }
              return compare;
            }
          });
    for (ConnectionHandlerDescriptor ch : chs)
    {
      if (ch.getProtocol() != ConnectionHandlerDescriptor.Protocol.LDIF)
      {
        sortedChs.add(ch);
      }
    }
    // Add the administration connector
    sortedChs.add(server.getAdminConnector());

    newElements.add(COMBO_SEPARATOR);

    for (ConnectionHandlerDescriptor ch : sortedChs)
    {
      String connectionHandlerLabel = getConnectionHandlerLabel(ch);
      newElements.add(new CategorizedComboBoxElement(
          connectionHandlerLabel, CategorizedComboBoxElement.Type.REGULAR));
    }
    updateComboBoxModel(newElements,
        (DefaultComboBoxModel)connectionHandlers.getModel());

    boolean displayErrorPane = false;
    Message errorTitle = Message.EMPTY;
    Message errorDetails = Message.EMPTY;
    ServerDescriptor.ServerStatus status = server.getStatus();
    if (status == ServerDescriptor.ServerStatus.STARTED)
    {
      if (!server.isAuthenticated())
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(
   INFO_CTRL_PANEL_AUTH_REQUIRED_TO_SEE_TRAFFIC_MONITORING_SUMMARY.
   get());
        mb.append("<br><br>"+getAuthenticateHTML());
        errorDetails = mb.toMessage();
        errorTitle = INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_SUMMARY.get();

        displayErrorPane = true;
      }
    }
    else
    {
      errorTitle = INFO_CTRL_PANEL_SERVER_NOT_RUNNING_SUMMARY.get();
      MessageBuilder mb = new MessageBuilder();
      mb.append(
          INFO_CTRL_PANEL_SERVER_MUST_RUN_TO_SEE_TRAFFIC_MONITORING_SUMMARY.
          get());
      mb.append("<br><br>");
      mb.append(getStartServerHTML());
      errorDetails = mb.toMessage();
      displayErrorPane = true;
    }
    final boolean fDisplayErrorPane = displayErrorPane;
    final Message fErrorTitle = errorTitle;
    final Message fErrorDetails = errorDetails;
    SwingUtilities.invokeLater(new Runnable()
    {
      /**
       * {@inheritDoc}
       */
      public void run()
      {
        ViewPositions pos = Utilities.getViewPositions(
            ConnectionHandlerMonitoringPanel.this);
        errorPane.setVisible(fDisplayErrorPane);
        if (fDisplayErrorPane)
        {
          updateErrorPane(errorPane, fErrorTitle,
              ColorAndFontConstants.errorTitleFont, fErrorDetails,
              ColorAndFontConstants.defaultFont);
        }
        // TODO: complete update the other table
        if (!firstRealDataSet)
        {
          updateTableSizes();
          firstRealDataSet = true;
        }
        updateConnectionHandlersPanel(server, fDisplayErrorPane);
        Utilities.updateViewPositions(pos);
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return connectionHandlers;
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    // No ok button
  }

  /**
   * {@inheritDoc}
   */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.CLOSE;
  }


  /**
   * {@inheritDoc}
   */
  public boolean requiresBorder()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public boolean requiresScroll()
  {
    return false;
  }

  private void setChOperationsToDisplay(
      LinkedHashSet<MonitoringAttributes> operations,
      boolean showAverages)
  {
    connectionHandlersTableModel.setAttributes(operations, showAverages);
    connectionHandlersTableModel.forceDataStructureChange();
  }

  private String getConnectionHandlerLabel(ConnectionHandlerDescriptor ch)
  {
    StringBuilder sb = new StringBuilder();
    if (ch.getProtocol() == Protocol.ADMINISTRATION_CONNECTOR)
    {
      sb.append(INFO_CTRL_PANEL_ADMINISTRATION_CONNECTOR_NAME.get(
          ch.getPort()));
    }
    else
    {
      sb.append(ch.getPort());
      sb.append(" - ");
      switch (ch.getProtocol())
      {
      case OTHER:
        sb.append(ch.getName());
        break;
      default:
        sb.append(ch.getProtocol().getDisplayMessage().toString());
      break;
      }
    }
    return sb.toString();
  }

  /**
   * Displays a dialog allowing the user to select which operations to display.
   *
   */
  private void operationViewClicked()
  {
    if (operationViewDlg == null)
    {
      operationViewPanel =
        MonitoringAttributesViewPanel.createMonitoringAttributesInstance(
            allowedChOperations);
      operationViewDlg = new GenericDialog(Utilities.getFrame(this),
          operationViewPanel);
      Utilities.centerGoldenMean(operationViewDlg,
          Utilities.getParentDialog(this));
      operationViewDlg.setModal(true);
    }
    operationViewPanel.setSelectedAttributes(chOperations);
    operationViewDlg.setVisible(true);
    if (!operationViewPanel.isCancelled())
    {
      boolean showAverages = showAveragesMenu.isSelected();
      chOperations = operationViewPanel.getAttributes();
      setChOperationsToDisplay(chOperations, showAverages);
      updateTableSizes();
    }
  }

  /**
   * Updates the contents of the tables depending on whether the averages
   * must be displayed or not.
   *
   */
  private void showAverageClicked()
  {
    boolean showAverages = showAveragesMenu.isSelected();
    setChOperationsToDisplay(chOperations, showAverages);
    updateTableSizes();
  }

  private void updateTableSizes()
  {
    Utilities.updateTableSizes(connectionHandlersTable, 8);
    Utilities.updateScrollMode(connectionHandlersScroll,
        connectionHandlersTable);
  }

  private void updateConnectionHandlersPanel(ServerDescriptor server,
      boolean errorOccurred)
  {
    Set<ConnectionHandlerDescriptor> cch =
      getFilteredConnectionHandlers(server);
    connectionHandlersTableModel.setData(cch, server.getRunningTime());
    connectionHandlersScroll.setVisible(!cch.isEmpty() && !errorOccurred);
    lNoConnectionHandlers.setVisible(cch.isEmpty() && !errorOccurred);
  }

  private Set<ConnectionHandlerDescriptor> getFilteredConnectionHandlers(
      ServerDescriptor server)
  {
    Set<ConnectionHandlerDescriptor> cchs =
      new HashSet<ConnectionHandlerDescriptor>();
    if (server != null)
    {
      Object o = connectionHandlers.getSelectedItem();
      if (o instanceof CategorizedComboBoxElement)
      {
        Object value = ((CategorizedComboBoxElement)o).getValue();
        if (value.equals(ALL_CONNECTION_HANDLERS))
        {
          for (ConnectionHandlerDescriptor ch : server.getConnectionHandlers())
          {
            if (ch.getProtocol() != Protocol.LDIF)
            {
              cchs.add((ConnectionHandlerDescriptor)ch);
            }
          }
          cchs.add(server.getAdminConnector());
        }
        else
        {
          String name = value.toString();
          for (ConnectionHandlerDescriptor ch : server.getConnectionHandlers())
          {
            if (getConnectionHandlerLabel(ch).equals(name))
            {
              cchs.add((ConnectionHandlerDescriptor)ch);
              break;
            }
          }
          if (cchs.isEmpty())
          {
            ConnectionHandlerDescriptor adminCh =
              server.getAdminConnector();
            if (getConnectionHandlerLabel(adminCh).equals(name))
            {
              cchs.add(adminCh);
            }
          }
        }
      }
    }
    return cchs;
  }

  /**
   * The specific menu bar of this panel.
   *
   */
  class ConnectionHandlerMonitoringMenuBar extends MainMenuBar
  {
    private static final long serialVersionUID = 505187831116443370L;

    /**
     * Constructor.
     * @param info the control panel info.
     */
    public ConnectionHandlerMonitoringMenuBar(ControlPanelInfo info)
    {
      super(info);
    }

    /**
     * {@inheritDoc}
     */
    protected void addMenus()
    {
      add(createViewMenuBar());
      add(createHelpMenuBar());
    }

    /**
     * Creates the view menu bar.
     * @return the view menu bar.
     */
    protected JMenu createViewMenuBar()
    {
      JMenu menu = Utilities.createMenu(
          INFO_CTRL_PANEL_CONNECTION_HANDLER_VIEW_MENU.get(),
          INFO_CTRL_PANEL_CONNECTION_HANDLER_VIEW_MENU_DESCRIPTION.get());
      menu.setMnemonic(KeyEvent.VK_V);
      final JMenuItem viewOperations = Utilities.createMenuItem(
          INFO_CTRL_PANEL_OPERATIONS_VIEW.get());
      menu.add(viewOperations);
      viewOperations.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          operationViewClicked();
        }
      });
      showAveragesMenu = new JCheckBoxMenuItem(
          INFO_CTRL_PANEL_SHOW_AVERAGES.get().toString());
      showAveragesMenu.setSelected(false);
      menu.add(showAveragesMenu);
      showAveragesMenu.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          showAverageClicked();
        }
      });
      return menu;
    }
  }

  /**
   *  This class is used simply to avoid an inset on the left for the
   *  elements of the combo box.
   *  Since this item is a CategorizedComboBoxElement of type
   *  CategorizedComboBoxElement.Type.REGULAR, it has by default an inset on
   *  the left.
   */
  class CustomComboBoxCellRenderer extends CustomListCellRenderer
  {
    /**
     * The constructor.
     * @param combo the combo box to be rendered.
     */
    CustomComboBoxCellRenderer(JComboBox combo)
    {
      super(combo);
    }

    /**
     * {@inheritDoc}
     */
    public Component getListCellRendererComponent(JList list, Object value,
        int index, boolean isSelected, boolean cellHasFocus)
    {
      Component comp = super.getListCellRendererComponent(list, value, index,
          isSelected, cellHasFocus);
      if (value instanceof CategorizedComboBoxElement)
      {
        CategorizedComboBoxElement element = (CategorizedComboBoxElement)value;
        String name = getStringValue(element);
        ((JLabel)comp).setText(name);
      }
      comp.setFont(defaultFont);
      return comp;
    }
  }
}

