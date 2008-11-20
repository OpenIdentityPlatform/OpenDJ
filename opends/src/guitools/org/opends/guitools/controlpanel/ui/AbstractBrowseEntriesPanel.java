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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.INFO_CERTIFICATE_EXCEPTION;
import static org.opends.messages.QuickSetupMessages.INFO_NOT_AVAILABLE_LABEL;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.ConfigReadException;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.BrowserEvent;
import org.opends.guitools.controlpanel.event.BrowserEventListener;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.FilterTextField;
import org.opends.guitools.controlpanel.ui.components.TreePanel;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.ui.renderer.CustomListCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.quicksetup.UserDataCertificateException;
import org.opends.quicksetup.ui.CertificateDialog;
import org.opends.quicksetup.util.UIKeyStore;
import org.opends.quicksetup.util.Utils;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.LDAPException;
import org.opends.server.types.SearchFilter;

/**
 * The abstract class used to refactor some code.  The classes that extend this
 * class are the 'Browse Entries' panel and the panel of the dialog we display
 * when the user can choose a set of entries (for instance when the user adds
 * a member to a group in the 'New Group' dialog).
 *
 */
public abstract class AbstractBrowseEntriesPanel extends StatusGenericPanel
{
  private JComboBox baseDNs;
  /**
   * The combo box containing the different filter types.
   */
  protected JComboBox filterAttribute;

  /**
   * The text field of the filter.
   */
  protected FilterTextField filter;

  private JButton applyButton;

  private JButton okButton;
  private JButton cancelButton;
  private JButton closeButton;

  private JLabel lBaseDN;
  private JLabel lFilter;
  private JLabel lLimit;

  private JLabel lNumberOfEntries;

  private JLabel lNoMatchFound;

  private InitialLdapContext createdUserDataCtx;

  /**
   * The tree pane contained in this panel.
   */
  protected TreePanel treePane;

  /**
   * The browser controller used to update the LDAP entry tree.
   */
  protected BrowserController controller;

  private NumberOfEntriesUpdater numberEntriesUpdater;

  private BaseDNPanel otherBaseDNPanel;
  private GenericDialog otherBaseDNDlg;

  private boolean firstTimeDisplayed = true;

  private Object lastSelectedBaseDN = null;
  private boolean ignoreBaseDNEvents = false;

  /**
   * LDAP filter message.
   */
  protected static final Message LDAP_FILTER =
    INFO_CTRL_PANEL_LDAP_FILTER.get();

  /**
   * User filter message.
   */
  protected static final Message USER_FILTER =
    INFO_CTRL_PANEL_USERS_FILTER.get();

  /**
   * Group filter message.
   */
  protected static final Message GROUP_FILTER =
    INFO_CTRL_PANEL_GROUPS_FILTER.get();

  private final Message OTHER_BASE_DN =
    INFO_CTRL_PANEL_OTHER_BASE_DN.get();

  private ArrayList<DN> otherBaseDns = new ArrayList<DN>();

  private static final String ALL_BASE_DNS = "All Base DNs";

  private static final int MAX_NUMBER_ENTRIES = 5000;

  private static final int MAX_NUMBER_OTHER_BASE_DNS = 10;

  private final String[] CONTAINER_CLASSES = {
      "organization",
      "organizationalUnit"
  };

  private static final Logger LOG =
    Logger.getLogger(AbstractBrowseEntriesPanel.class.getName());

  /**
   * Default constructor.
   *
   */
  public AbstractBrowseEntriesPanel()
  {
    super();
    createLayout();
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

  /**
   * {@inheritDoc}
   */
  public boolean callConfigurationChangedInBackground()
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public void setInfo(ControlPanelInfo info)
  {
    if (controller == null)
    {
      createBrowserController(info);
    }
    super.setInfo(info);
    treePane.setInfo(info);
  }

  /**
   * {@inheritDoc}
   */
  public final GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }

  /**
   * Since these panel has a special layout, we cannot use the layout of the
   * GenericDialog and we return ButtonType.NO_BUTTON in the method
   * getButtonType.  We use this method to be able to add some progress
   * information to the left of the buttons.
   * @return the button type of the panel.
   */
  protected abstract GenericDialog.ButtonType getBrowseButtonType();

  /**
   * {@inheritDoc}
   */
  public void toBeDisplayed(boolean visible)
  {
    super.toBeDisplayed(visible);
    ((GenericDialog)Utilities.getParentDialog(this)).getRootPane().
    setDefaultButton(null);
  }

  /**
   * {@inheritDoc}
   */
  protected void setEnabledOK(boolean enable)
  {
    okButton.setEnabled(enable);
  }

  /**
   * {@inheritDoc}
   */
  protected void setEnabledCancel(boolean enable)
  {
    cancelButton.setEnabled(enable);
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   *
   */
  private void createLayout()
  {
    setBackground(ColorAndFontConstants.greyBackground);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 7;
    addErrorPane(gbc);
    Message title = INFO_CTRL_PANEL_SERVER_NOT_RUNNING_SUMMARY.get();
    MessageBuilder mb = new MessageBuilder();
    mb.append(INFO_CTRL_PANEL_SERVER_NOT_RUNNING_DETAILS.get());
    mb.append("<br><br>");
    mb.append(getStartServerHTML());
    Message details = mb.toMessage();
    updateErrorPane(errorPane, title, ColorAndFontConstants.errorTitleFont,
        details,
        ColorAndFontConstants.defaultFont);
    errorPane.setVisible(true);
    errorPane.setFocusable(true);

    gbc.insets = new Insets(10, 10, 0, 10);
    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    lBaseDN = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_BASE_DN_LABEL.get());
    gbc.gridx = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.right = 0;
    add(lBaseDN, gbc);
    gbc.insets.left = 5;
    baseDNs = Utilities.createComboBox();

    DefaultComboBoxModel model = new DefaultComboBoxModel();
    model.addElement("dc=dn to be displayed");
    baseDNs.setModel(model);
    baseDNs.setRenderer(new CustomComboBoxCellRenderer(baseDNs));
    baseDNs.addItemListener(new ItemListener()
    {
      public void itemStateChanged(ItemEvent ev)
      {
        if (ignoreBaseDNEvents || (ev.getStateChange() != ItemEvent.SELECTED))
        {
          return;
        }
        Object o = baseDNs.getSelectedItem();
        if (isCategory(o))
        {
          if (lastSelectedBaseDN == null)
          {
            // Look for the first element that is not a category
            for (int i=0; i<baseDNs.getModel().getSize(); i++)
            {
              Object item = baseDNs.getModel().getElementAt(i);
              if (item instanceof CategorizedComboBoxElement)
              {
                if (!isCategory(item))
                {
                  lastSelectedBaseDN = item;
                  break;
                }
              }
            }
            if (lastSelectedBaseDN != null)
            {
              baseDNs.setSelectedItem(lastSelectedBaseDN);
            }
          }
          else
          {
            ignoreBaseDNEvents = true;
            baseDNs.setSelectedItem(lastSelectedBaseDN);
            ignoreBaseDNEvents = false;
          }
        }
        else if (COMBO_SEPARATOR.equals(o))
        {
          ignoreBaseDNEvents = true;
          baseDNs.setSelectedItem(lastSelectedBaseDN);
          ignoreBaseDNEvents = false;
        }
        else if (!OTHER_BASE_DN.equals(o))
        {
          lastSelectedBaseDN = o;
          if (lastSelectedBaseDN != null)
          {
            applyButtonClicked();
          }
        }
        else
        {
          if (otherBaseDNDlg == null)
          {
            otherBaseDNPanel = new BaseDNPanel();
            otherBaseDNDlg = new GenericDialog(
                Utilities.getFrame(AbstractBrowseEntriesPanel.this),
                otherBaseDNPanel);
            otherBaseDNDlg.setModal(true);
            Utilities.centerGoldenMean(otherBaseDNDlg,
                Utilities.getParentDialog(AbstractBrowseEntriesPanel.this));
          }
          otherBaseDNDlg.setVisible(true);
          String newBaseDn = otherBaseDNPanel.getBaseDn();
          DefaultComboBoxModel model = (DefaultComboBoxModel)baseDNs.getModel();
          if (newBaseDn != null)
          {
            Object newElement = null;

            try
            {
              DN dn = DN.decode(newBaseDn);
              newElement = new CategorizedComboBoxElement(
                  Utilities.unescapeUtf8(dn.toString()),
                  CategorizedComboBoxElement.Type.REGULAR);
              if (!otherBaseDns.contains(dn))
              {
                otherBaseDns.add(0, dn);

                if (otherBaseDns.size() > MAX_NUMBER_OTHER_BASE_DNS)
                {
                  ignoreBaseDNEvents = true;
                  for (int i=otherBaseDns.size() - 1;
                  i >= MAX_NUMBER_OTHER_BASE_DNS; i--)
                  {
                    DN dnToRemove = otherBaseDns.get(i);
                    otherBaseDns.remove(i);
                    Object elementToRemove = new CategorizedComboBoxElement(
                        Utilities.unescapeUtf8(dnToRemove.toString()),
                        CategorizedComboBoxElement.Type.REGULAR);
                    model.removeElement(elementToRemove);
                  }
                  ignoreBaseDNEvents = false;
                }
              }
              if (model.getIndexOf(newElement) == -1)
              {
                int index = model.getIndexOf(COMBO_SEPARATOR);
                model.insertElementAt(newElement, index + 1);
                if (otherBaseDns.size() == 1)
                {
                  model.insertElementAt(COMBO_SEPARATOR, index + 2);
                }
              }
            }
            catch (Throwable t)
            {
              throw new IllegalStateException("Unexpected error decoding dn "+
                  newBaseDn, t);
            }
            if (newElement != null)
            {
              model.setSelectedItem(newElement);
            }
          }
          else
          {
            if (lastSelectedBaseDN != null)
            {
              ignoreBaseDNEvents = true;
              model.setSelectedItem(lastSelectedBaseDN);
              ignoreBaseDNEvents = false;
            }
          }
        }
      }
    });
    gbc.gridx ++;
    add(baseDNs, gbc);

    gbc.gridx ++;
    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.insets.left = 10;
    add(new JSeparator(SwingConstants.VERTICAL), gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    lFilter = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_FILTER_LABEL.get());
    gbc.gridx ++;
    add(lFilter, gbc);

    filterAttribute = Utilities.createComboBox();
    filterAttribute.setModel(
        new DefaultComboBoxModel(new Object[]{
            USER_FILTER,
            GROUP_FILTER,
            COMBO_SEPARATOR,
            "attributetobedisplayed",
            COMBO_SEPARATOR,
            LDAP_FILTER}));
    filterAttribute.setRenderer(new CustomListCellRenderer(filterAttribute));
    filterAttribute.addItemListener(new IgnoreItemListener(filterAttribute));
    gbc.gridx ++;
    gbc.insets.left = 5;
    add(filterAttribute, gbc);

    filter = new FilterTextField();
    filter.setToolTipText(
        INFO_CTRL_PANEL_SUBSTRING_SEARCH_INLINE_HELP.get().toString());
    filter.addKeyListener(new KeyAdapter()
    {
      public void keyReleased(KeyEvent e)
      {
        if ((e.getKeyCode() == KeyEvent.VK_ENTER) && applyButton.isEnabled())
        {
          filter.displayRefreshIcon(true);
          applyButtonClicked();
        }
      }
    });
    filter.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        filter.displayRefreshIcon(true);
        applyButtonClicked();
      }
    });

    gbc.weightx = 1.0;
    gbc.gridx ++;
    add(filter, gbc);

    gbc.insets.top = 10;
    applyButton =
      Utilities.createButton(INFO_CTRL_PANEL_APPLY_BUTTON_LABEL.get());
    gbc.insets.right = 10;
    gbc.gridx ++;
    gbc.weightx = 0.0;
    add(applyButton, gbc);
    applyButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        applyButtonClicked();
      }
    });
    gbc.insets = new Insets(10, 0, 0, 0);
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = 7;
    add(createMainPanel(), gbc);

//  The button panel
    gbc.gridy ++;
    gbc.weighty = 0.0;
    gbc.insets = new Insets(0, 0, 0, 0);
    add(createButtonsPanel(), gbc);
  }

  /**
   * Returns the panel that contains the buttons of type OK, CANCEL, etc.
   * @return the panel that contains the buttons of type OK, CANCEL, etc.
   */
  private JPanel createButtonsPanel()
  {
    JPanel buttonsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = 1;
    gbc.gridy = 0;
    lLimit = Utilities.createDefaultLabel();
    Utilities.setWarningLabel(lLimit,
       INFO_CTRL_PANEL_MAXIMUM_CHILDREN_DISPLAYED.get(MAX_NUMBER_ENTRIES));
    gbc.weighty = 0.0;
    gbc.gridy ++;
    lLimit.setVisible(false);
    lNumberOfEntries = Utilities.createDefaultLabel();
    gbc.insets = new Insets(10, 10, 10, 10);
    buttonsPanel.add(lNumberOfEntries, gbc);
    buttonsPanel.add(lLimit, gbc);
    gbc.weightx = 1.0;
    gbc.gridx ++;
    buttonsPanel.add(Box.createHorizontalGlue(), gbc);
    buttonsPanel.setOpaque(true);
    buttonsPanel.setBackground(ColorAndFontConstants.greyBackground);
    gbc.gridx ++;
    gbc.weightx = 0.0;
    if (getBrowseButtonType() == GenericDialog.ButtonType.CLOSE)
    {
      closeButton =
        Utilities.createButton(INFO_CTRL_PANEL_CLOSE_BUTTON_LABEL.get());
      closeButton.setOpaque(false);
      buttonsPanel.add(closeButton, gbc);
      closeButton.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          closeClicked();
        }
      });
    }
    else if (getBrowseButtonType() == GenericDialog.ButtonType.OK)
    {
      okButton = Utilities.createButton(INFO_CTRL_PANEL_OK_BUTTON_LABEL.get());
      okButton.setOpaque(false);
      buttonsPanel.add(okButton, gbc);
      okButton.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          okClicked();
        }
      });
    }
    if (getBrowseButtonType() == GenericDialog.ButtonType.OK_CANCEL)
    {
      okButton = Utilities.createButton(INFO_CTRL_PANEL_OK_BUTTON_LABEL.get());
      okButton.setOpaque(false);
      gbc.insets.right = 0;
      buttonsPanel.add(okButton, gbc);
      okButton.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          okClicked();
        }
      });
      cancelButton =
        Utilities.createButton(INFO_CTRL_PANEL_CANCEL_BUTTON_LABEL.get());
      cancelButton.setOpaque(false);
      gbc.insets.right = 10;
      gbc.insets.left = 5;
      gbc.gridx ++;
      buttonsPanel.add(cancelButton, gbc);
      cancelButton.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          cancelClicked();
        }
      });
    }


    buttonsPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
        ColorAndFontConstants.defaultBorderColor));

    return buttonsPanel;
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return baseDNs;
  }

  /**
   * {@inheritDoc}
   */
  public void cancelClicked()
  {
    setPrimaryValid(lBaseDN);
    setSecondaryValid(lFilter);
    super.cancelClicked();
  }

  /**
   * The method that is called when the user clicks on Apply.  Basically it
   * will update the BrowserController with the new base DN and filter specified
   * by the user.  The method assumes that is being called from the event
   * thread.
   *
   */
  protected void applyButtonClicked()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    setPrimaryValid(lFilter);
    String s = getBaseDN();
    boolean displayAll = false;
    if (s != null)
    {
      displayAll = s.equals(ALL_BASE_DNS);
      if (!displayAll)
      {
        try
        {
          DN.decode(s);
        }
        catch (Throwable t)
        {
          errors.add(INFO_CTRL_PANEL_INVALID_DN_DETAILS.get(s, t.toString()));
        }
      }
    }
    else
    {
      errors.add(INFO_CTRL_PANEL_NO_BASE_DN_SELECTED.get());
    }
    String filterValue = getFilter();
    try
    {
      LDAPFilter.decode(filterValue);
    }
    catch (LDAPException le)
    {
      errors.add(INFO_CTRL_PANEL_INVALID_FILTER_DETAILS.get(
          le.getMessageObject().toString()));
      setPrimaryInvalid(lFilter);
    }
    if (errors.size() == 0)
    {
      lLimit.setVisible(false);
      lNumberOfEntries.setVisible(true);
      controller.removeAllUnderRoot();
      controller.setFilter(filterValue);
      controller.setAutomaticExpand(!filterValue.equals(
          BrowserController.ALL_OBJECTS_FILTER));
      if (controller.getConfigurationConnection() != null)
      {
        treePane.getTree().setRootVisible(displayAll);
        treePane.getTree().setShowsRootHandles(!displayAll);
        boolean isBaseDN = false;
        for (BackendDescriptor backend :
          getInfo().getServerDescriptor().getBackends())
        {
          for (BaseDNDescriptor baseDN : backend.getBaseDns())
          {
            String dn = Utilities.unescapeUtf8(baseDN.getDn().toString());
            if (displayAll)
            {
              controller.addSuffix(dn, null);
            }
            else if (s.equals(dn))
            {
              controller.addSuffix(dn, null);
              isBaseDN = true;
            }
          }
        }
        if (!isBaseDN && !displayAll)
        {
          BasicNode rootNode =
            (BasicNode)controller.getTree().getModel().getRoot();
          if (controller.findChildNode(rootNode, s) == -1)
          {
            controller.addNodeUnderRoot(s);
          }
        }
      }
      else
      {
        controller.getTree().setRootVisible(false);
        controller.removeAllUnderRoot();
      }
    }
    else
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * Returns the LDAP filter built based in the parameters provided by the user.
   * @return the LDAP filter built based in the parameters provided by the user.
   */
  private String getFilter()
  {
    String returnValue;
    String s = filter.getText();
    if (s.length() == 0)
    {
      returnValue = BrowserController.ALL_OBJECTS_FILTER;
    }
    else
    {
      Object attr = filterAttribute.getSelectedItem();
      if (LDAP_FILTER.equals(attr))
      {
        s = s.trim();
        if (s.length() == 0)
        {
          returnValue = BrowserController.ALL_OBJECTS_FILTER;
        }
        else
        {
          returnValue = s;
        }
      }
      else if (USER_FILTER.equals(attr))
      {
        if (s.equals("*"))
        {
          returnValue = "(objectClass=person)";
        }
        else
        {
          returnValue = "(&(objectClass=person)(|"+
          "(cn="+s+")(sn="+s+")(uid="+s+")))";
        }
      }
      else if (GROUP_FILTER.equals(attr))
      {
        if (s.equals("*"))
        {
          returnValue =
            "(|(objectClass=groupOfUniqueNames)(objectClass=groupOfURLs))";
        }
        else
        {
          returnValue =
            "(&(|(objectClass=groupOfUniqueNames)(objectClass=groupOfURLs))"+
            "(cn="+s+"))";
        }
      }
      else if (attr != null)
      {
        try
        {
          LDAPFilter ldapFilter =
            new LDAPFilter(SearchFilter.createFilterFromString(
              "("+attr+"="+s+")"));
          returnValue = ldapFilter.toString();
        }
        catch (DirectoryException de)
        {
          // Try this alternative:
          AttributeType attrType =
            getInfo().getServerDescriptor().getSchema().getAttributeType(
                attr.toString().toLowerCase());
          LDAPFilter ldapFilter =
            new LDAPFilter(SearchFilter.createEqualityFilter(
              attrType, new AttributeValue(attrType, s)));
          returnValue = ldapFilter.toString();
        }
      }
      else
      {
        returnValue = BrowserController.ALL_OBJECTS_FILTER;
      }
    }
    return returnValue;
  }

  /**
   * Returns the component that will be displayed between the filtering options
   * and the buttons panel.  This component must contain the tree panel.
   * @return the component that will be displayed between the filtering options
   * and the buttons panel.
   */
  protected abstract Component createMainPanel();

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final ServerDescriptor desc = ev.getNewDescriptor();

    updateCombos(desc);

    updateBrowserControllerAndErrorPane(desc);
  }

  /**
   * Creates and returns the tree panel.
   * @return the tree panel.
   */
  protected JComponent createTreePane()
  {
    treePane = new TreePanel();

    lNoMatchFound = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_NO_MATCHES_FOUND_LABEL.get());
    lNoMatchFound.setVisible(false);

    // Calculate default size
    JTree tree = treePane.getTree();
    DefaultMutableTreeNode root = new DefaultMutableTreeNode(
        "myserver.mydomain.com:389");
    DefaultTreeModel model = new DefaultTreeModel(root);
    tree.setModel(model);
    tree.setShowsRootHandles(false);
    tree.expandPath(new TreePath(root));
    JPanel p = new JPanel(new GridBagLayout());
    p.setBackground(ColorAndFontConstants.background);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    Utilities.setBorder(treePane, new EmptyBorder(10, 0, 10, 0));
    p.add(treePane, gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    Utilities.setBorder(lNoMatchFound, new EmptyBorder(15, 15, 15, 15));
    p.add(lNoMatchFound, gbc);

    if ((getInfo() != null) && (controller == null))
    {
      createBrowserController(getInfo());
    }
    numberEntriesUpdater = new NumberOfEntriesUpdater();
    numberEntriesUpdater.start();

    return p;
  }


  /**
   * Creates the browser controller object.
   * @param info the ControlPanelInfo to be used to create the browser
   * controller.
   */
  protected void createBrowserController(ControlPanelInfo info)
  {
    controller = new BrowserController(treePane.getTree(),
        info.getConnectionPool(),
        info.getIconPool());
    controller.setContainerClasses(CONTAINER_CLASSES);
    controller.setShowContainerOnly(false);
    controller.setMaxChildren(MAX_NUMBER_ENTRIES);
    controller.addBrowserEventListener(new BrowserEventListener()
    {
      /**
       * {@inheritDoc}
       */
      public void processBrowserEvent(BrowserEvent ev)
      {
        if (ev.getType() == BrowserEvent.Type.SIZE_LIMIT_REACHED)
        {
          lLimit.setVisible(true);
          lNumberOfEntries.setVisible(false);
        }
      }
    });
    controller.getTreeModel().addTreeModelListener(new TreeModelListener()
    {
      /**
       * {@inheritDoc}
       */
      public void treeNodesChanged(TreeModelEvent e)
      {
      }
      /**
       * {@inheritDoc}
       */
      public void treeNodesInserted(TreeModelEvent e)
      {
        checkRootNode();
      }
      /**
       * {@inheritDoc}
       */
      public void treeNodesRemoved(TreeModelEvent e)
      {
        checkRootNode();
      }
      /**
       * {@inheritDoc}
       */
      public void treeStructureChanged(TreeModelEvent e)
      {
        checkRootNode();
      }
    });
  }

  final static String[] systemIndexes = {"aci", "dn2id", "ds-sync-hist",
    "entryUUID", "id2children", "id2subtree"};
  private static boolean displayIndex(String name)
  {
    boolean displayIndex = true;
    for (String systemIndex : systemIndexes)
    {
      if (systemIndex.equalsIgnoreCase(name))
      {
        displayIndex = false;
        break;
      }
    }
    return displayIndex;
  }

  /**
   * Updates the contents of the combo boxes with the provided ServerDescriptor.
   * @param desc the server descriptor to be used to update the combo boxes.
   */
  private void updateCombos(ServerDescriptor desc)
  {
    final SortedSet<String> newElements = new TreeSet<String>();
    for (BackendDescriptor backend : desc.getBackends())
    {
      for (IndexDescriptor index : backend.getIndexes())
      {
        String indexName = index.getName();
        if (displayIndex(indexName))
        {
          newElements.add(indexName);
        }
      }
    }
    final DefaultComboBoxModel model =
      (DefaultComboBoxModel)filterAttribute.getModel();
    boolean changed = newElements.size() != model.getSize() - 2;
    if (!changed)
    {
      int i = 0;
      for (String newElement : newElements)
      {
        changed = !newElement.equals(model.getElementAt(i));
        if (changed)
        {
          break;
        }
        i++;
      }
    }
    if (changed)
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          Object selected = filterAttribute.getSelectedItem();
          model.removeAllElements();
          model.addElement(USER_FILTER);
          model.addElement(GROUP_FILTER);
          model.addElement(COMBO_SEPARATOR);
          for (String newElement : newElements)
          {
            model.addElement(newElement);
          }
          // If there are not backends, we get no indexes to set.
          if (newElements.size() > 0)
          {
            model.addElement(COMBO_SEPARATOR);
          }
          model.addElement(LDAP_FILTER);
          if (selected != null)
          {
            if (model.getIndexOf(selected) != -1)
            {
              model.setSelectedItem(selected);
            }
            else
            {
              model.setSelectedItem(model.getElementAt(0));
            }
          }
        }
      });
    }

    LinkedHashSet<Object> baseDNNewElements = new LinkedHashSet<Object>();
    SortedSet<String> backendIDs = new TreeSet<String>();
    HashMap<String, SortedSet<String>> hmBaseDNs =
      new HashMap<String, SortedSet<String>>();

    boolean allAdded = false;
    HashMap<String, BaseDNDescriptor> hmBaseDNWithEntries =
      new HashMap<String, BaseDNDescriptor>();

    BaseDNDescriptor baseDNWithEntries = null;
    for (BackendDescriptor backend : desc.getBackends())
    {
      if (displayBackend(backend))
      {
        String backendID = backend.getBackendID();
        backendIDs.add(backendID);
        SortedSet<String> baseDNs = new TreeSet<String>();
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          try
          {
            baseDNs.add(Utilities.unescapeUtf8(baseDN.getDn().toString()));
          }
          catch (Throwable t)
          {
            throw new IllegalStateException("Unexpected error: "+t, t);
          }
          if (baseDN.getEntries() > 0)
          {
            hmBaseDNWithEntries.put(
                Utilities.unescapeUtf8(baseDN.getDn().toString()), baseDN);
          }
        }
        hmBaseDNs.put(backendID, baseDNs);
        if (backendID.equalsIgnoreCase("userRoot"))
        {
          for (String baseDN : baseDNs)
          {
            baseDNWithEntries = hmBaseDNWithEntries.get(baseDN);
            if (baseDNWithEntries != null)
            {
              break;
            }
          }
        }
      }
    }

    if (!allAdded)
    {
      baseDNNewElements.add(new CategorizedComboBoxElement(ALL_BASE_DNS,
          CategorizedComboBoxElement.Type.REGULAR));
      allAdded = true;
    }
    for (String backendID : backendIDs)
    {
      baseDNNewElements.add(new CategorizedComboBoxElement(backendID,
          CategorizedComboBoxElement.Type.CATEGORY));
      SortedSet<String> baseDNs = hmBaseDNs.get(backendID);
      for (String baseDN : baseDNs)
      {
        baseDNNewElements.add(new CategorizedComboBoxElement(baseDN,
            CategorizedComboBoxElement.Type.REGULAR));
        if (baseDNWithEntries == null)
        {
          baseDNWithEntries = hmBaseDNWithEntries.get(baseDN);
        }
      }
    }
    for (DN dn : otherBaseDns)
    {
      if (allAdded)
      {
        baseDNNewElements.add(COMBO_SEPARATOR);
      }
      baseDNNewElements.add(new CategorizedComboBoxElement(
          Utilities.unescapeUtf8(dn.toString()),
          CategorizedComboBoxElement.Type.REGULAR));
    }
    if (allAdded)
    {
      baseDNNewElements.add(COMBO_SEPARATOR);
      baseDNNewElements.add(OTHER_BASE_DN);
    }
    if (firstTimeDisplayed && (baseDNWithEntries != null))
    {
      ignoreBaseDNEvents = true;
    }
    updateComboBoxModel(baseDNNewElements,
        (DefaultComboBoxModel)baseDNs.getModel());
    // Select the element in the combo box.
    if (firstTimeDisplayed && (baseDNWithEntries != null))
    {
      final Object toSelect = new CategorizedComboBoxElement(
          Utilities.unescapeUtf8(baseDNWithEntries.getDn().toString()),
          CategorizedComboBoxElement.Type.REGULAR);
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          // After this updateBrowseController is called.
          ignoreBaseDNEvents = true;
          baseDNs.setSelectedItem(toSelect);
          ignoreBaseDNEvents = false;
        }
      });
    }
    if (getInfo().getServerDescriptor().isAuthenticated())
    {
      firstTimeDisplayed = false;
    }
  }

  /**
   * Updates the contents of the error pane and the browser controller with the
   * provided ServerDescriptor.  It checks that the server is running and that
   * we are authenticated, that the connection to the server has not changed,
   * etc.
   * @param desc the server descriptor to be used to update the error pane and
   * browser controller.
   */
  private void updateBrowserControllerAndErrorPane(ServerDescriptor desc)
  {
    boolean displayNodes = false;
    boolean displayErrorPane = false;
    Message errorTitle = Message.EMPTY;
    Message errorDetails = Message.EMPTY;
    ServerDescriptor.ServerStatus status = desc.getStatus();
    if (status == ServerDescriptor.ServerStatus.STARTED)
    {
      if (!desc.isAuthenticated())
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(
            INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_TO_BROWSE_SUMMARY.get());
        mb.append("<br><br>"+getAuthenticateHTML());
        errorDetails = mb.toMessage();
        errorTitle = INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_SUMMARY.get();

        displayErrorPane = true;
      }
      else
      {
        try
        {
          InitialLdapContext ctx = getInfo().getDirContext();
          InitialLdapContext ctx1 = controller.getConfigurationConnection();
          boolean setConnection = ctx != ctx1;
          if (setConnection)
          {
            if (getInfo().getUserDataDirContext() == null)
            {
              InitialLdapContext ctxUserData =
                createUserDataDirContext(ConnectionUtils.getBindDN(ctx),
                    ConnectionUtils.getBindPassword(ctx));
              getInfo().setUserDataDirContext(ctxUserData);
            }
            final NamingException[] fNe = {null};
            Runnable runnable = new Runnable()
            {
              /**
               * {@inheritDoc}
               */
              public void run()
              {
                try
                {
                  controller.setConnections(getInfo().getDirContext(),
                        getInfo().getUserDataDirContext());
                  applyButtonClicked();
                }
                catch (NamingException ne)
                {
                  fNe[0] = ne;
                }
              }
            };
            if (!SwingUtilities.isEventDispatchThread())
            {
              try
              {
                SwingUtilities.invokeAndWait(runnable);
              }
              catch (Throwable t)
              {
              }
            }
            else
            {
              runnable.run();
            }

            if (fNe[0] != null)
            {
              throw fNe[0];
            }
          }
          displayNodes = true;
        }
        catch (NamingException ne)
        {
          errorTitle = INFO_CTRL_PANEL_ERROR_CONNECT_BROWSE_DETAILS.get();
          errorDetails = INFO_CTRL_PANEL_ERROR_CONNECT_BROWSE_SUMMARY.get(
              ne.toString());
          displayErrorPane = true;
        }
        catch (ConfigReadException cre)
        {
          errorTitle = INFO_CTRL_PANEL_ERROR_CONNECT_BROWSE_DETAILS.get();
          errorDetails = INFO_CTRL_PANEL_ERROR_CONNECT_BROWSE_SUMMARY.get(
              cre.getMessageObject().toString());
          displayErrorPane = true;
        }
      }
    }
    else
    {
      errorTitle = INFO_CTRL_PANEL_SERVER_NOT_RUNNING_SUMMARY.get();
      MessageBuilder mb = new MessageBuilder();
      mb.append(
        INFO_CTRL_PANEL_AUTHENTICATION_SERVER_MUST_RUN_TO_BROWSE_SUMMARY.get());
      mb.append("<br><br>");
      mb.append(getStartServerHTML());
      errorDetails = mb.toMessage();
      displayErrorPane = true;
    }

    final boolean fDisplayNodes = displayNodes;
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
        applyButton.setEnabled(!fDisplayErrorPane);
        errorPane.setVisible(fDisplayErrorPane);
        if (fDisplayErrorPane)
        {
          updateErrorPane(errorPane, fErrorTitle,
              ColorAndFontConstants.errorTitleFont, fErrorDetails,
              ColorAndFontConstants.defaultFont);
        }
        else if (fDisplayNodes)
        {
          // Update the browser controller with the potential new suffixes.
          String s = getBaseDN();
          DN theDN = null;
          boolean displayAll = false;
          if (s != null)
          {
            displayAll = s.equals(ALL_BASE_DNS);
            if (!displayAll)
            {
              try
              {
                theDN = DN.decode(s);
              }
              catch (Throwable t)
              {
                s = null;
              }
            }
          }
          treePane.getTree().setRootVisible(displayAll);
          treePane.getTree().setShowsRootHandles(!displayAll);
          if (s != null)
          {
            boolean isBaseDN = false;
            for (BackendDescriptor backend :
              getInfo().getServerDescriptor().getBackends())
            {
              for (BaseDNDescriptor baseDN : backend.getBaseDns())
              {
                String dn = Utilities.unescapeUtf8(baseDN.getDn().toString());
                if ((theDN != null) && baseDN.getDn().equals(theDN))
                {
                  isBaseDN = true;
                }
                if (baseDN.getEntries() > 0)
                {
                  if (!controller.hasSuffix(dn))
                  {
                    if (displayAll)
                    {
                      controller.addSuffix(dn, null);
                    }
                    else if (s.equals(dn))
                    {
                      controller.addSuffix(dn, null);
                    }
                  }
                }
              }
              if (!isBaseDN && !displayAll)
              {
                BasicNode rootNode =
                  (BasicNode)controller.getTree().getModel().getRoot();
                if (controller.findChildNode(rootNode, s) == -1)
                {
                  controller.addNodeUnderRoot(s);
                }
              }
            }
          }
        }


        if (!fDisplayNodes)
        {
          controller.removeAllUnderRoot();
          treePane.getTree().setRootVisible(false);
        }
      }
    });
  }

  /**
   * Returns the base DN specified by the user.
   * @return the base DN specified by the user.
   */
  private String getBaseDN()
  {
    String dn;
    Object o = baseDNs.getSelectedItem();
    if (o instanceof String)
    {
      dn = (String)o;
    }
    else if (o instanceof CategorizedComboBoxElement)
    {
      dn = ((CategorizedComboBoxElement)o).getValue().toString();
    }
    else
    {
      dn = null;
    }
    if (dn != null)
    {
      if (dn.trim().length() == 0)
      {
        dn = ALL_BASE_DNS;
      }
      else if (OTHER_BASE_DN.equals(dn))
      {
        dn = null;
      }
    }
    else
    {
      dn = null;
    }
    return dn;
  }

  /**
   * Creates the context to be used to retrieve user data for some given
   * credentials.
   * @param bindDN the bind DN.
   * @param bindPassword the bind password.
   * @return the context to be used to retrieve user data for some given
   * credentials.
   * @throws NamingException if an error occurs connecting to the server.
   * @throws ConfigReadException if an error occurs reading the configuration.
   */
  private InitialLdapContext createUserDataDirContext(
      final String bindDN, final String bindPassword)
  throws NamingException, ConfigReadException
  {
    createdUserDataCtx = null;
    try
    {
      createdUserDataCtx = Utilities.getUserDataDirContext(getInfo(),
          bindDN, bindPassword);
    }
    catch (NamingException ne)
    {
      if (Utils.isCertificateException(ne))
      {
        ApplicationTrustManager.Cause cause =
          getInfo().getTrustManager().getLastRefusedCause();

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

        if (excType != null)
        {
          String h;
          int p;
          try
          {
            URI uri = new URI(getInfo().getAdminConnectorURL());
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
          final UserDataCertificateException udce =
            new UserDataCertificateException(null,
                INFO_CERTIFICATE_EXCEPTION.get(h, String.valueOf(p)),
                ne, h, p,
                getInfo().getTrustManager().getLastRefusedChain(),
                getInfo().getTrustManager().getLastRefusedAuthType(),
                excType);

          if (SwingUtilities.isEventDispatchThread())
          {
            handleCertificateException(udce, bindDN, bindPassword);
          }
          else
          {
            final ConfigReadException[] fcre = {null};
            final NamingException[] fne = {null};
            try
            {
              SwingUtilities.invokeAndWait(new Runnable()
              {
                public void run()
                {
                  try
                  {
                    handleCertificateException(udce, bindDN, bindPassword);
                  }
                  catch (ConfigReadException cre)
                  {
                    fcre[0] = cre;
                  }
                  catch (NamingException ne)
                  {
                    fne[0] = ne;
                  }
                }
              });
            }
            catch (Throwable t)
            {
              throw new IllegalArgumentException("Unexpected error: "+t, t);
            }
            if (fcre[0] != null)
            {
              throw fcre[0];
            }
            if (fne[0] != null)
            {
              throw fne[0];
            }
          }
        }
      }
      else
      {
        throw ne;
      }
    }
    return createdUserDataCtx;
  }

  /**
   * Displays a dialog asking the user to accept a certificate if the user
   * accepts it, we update the trust manager and simulate a click on "OK" to
   * re-check the authentication.
   * This method assumes that we are being called from the event thread.
   * @param bindDN the bind DN.
   * @param bindPassword the bind password.
   */
  private void handleCertificateException(UserDataCertificateException ce,
      String bindDN, String bindPassword)
  throws NamingException, ConfigReadException
  {
    CertificateDialog dlg = new CertificateDialog(null, ce);
    dlg.pack();
    Utilities.centerGoldenMean(dlg, Utilities.getParentDialog(this));
    dlg.setVisible(true);
    if (dlg.getUserAnswer() !=
      CertificateDialog.ReturnType.NOT_ACCEPTED)
    {
      X509Certificate[] chain = ce.getChain();
      String authType = ce.getAuthType();
      String host = ce.getHost();

      if ((chain != null) && (authType != null) && (host != null))
      {
        LOG.log(Level.INFO, "Accepting certificate presented by host "+host);
        getInfo().getTrustManager().acceptCertificate(chain, authType, host);
        createdUserDataCtx = createUserDataDirContext(bindDN, bindPassword);
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
    if (dlg.getUserAnswer() ==
      CertificateDialog.ReturnType.ACCEPTED_PERMANENTLY)
    {
      X509Certificate[] chain = ce.getChain();
      if (chain != null)
      {
        try
        {
          UIKeyStore.acceptCertificate(chain);
        }
        catch (Throwable t)
        {
          LOG.log(Level.WARNING, "Error accepting certificate: "+t, t);
        }
      }
    }
  }

  /**
   *  This class is used simply to avoid an inset on the left for the
   *  'All Base DNs' item.
   *  Since this item is a CategorizedComboBoxElement of type
   *  CategorizedComboBoxElement.Type.REGULAR, it has by default an inset on
   *  the left.  The class simply handles this particular case to not to have
   *  that inset for the 'All Base DNs' item.
   */
  class CustomComboBoxCellRenderer extends CustomListCellRenderer
  {
    private Message ALL_BASE_DNS_STRING = INFO_CTRL_PANEL_ALL_BASE_DNS.get();
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
        if (ALL_BASE_DNS.equals(name))
        {
          ((JLabel)comp).setText(ALL_BASE_DNS_STRING.toString());
        }
      }
      comp.setFont(defaultFont);
      return comp;
    }
  }

  /**
   * Checks that the root node has some children.  It it has no children the
   * message 'No Match Found' is displayed instead of the tree panel.
   *
   */
  private void checkRootNode()
  {
    DefaultMutableTreeNode root =
      (DefaultMutableTreeNode)controller.getTreeModel().getRoot();
    boolean visible = root.getChildCount() > 0;
    if (visible != treePane.isVisible())
    {
      treePane.setVisible(visible);
      lNoMatchFound.setVisible(!visible);
      lNumberOfEntries.setVisible(visible);
    }
    numberEntriesUpdater.recalculate();
  }

  /**
   * This is a class that simply checks the number of entries that the browser
   * contains and updates a counter with the new number of entries.
   * It is basically a thread that sleeps and checks whether some
   * calculation must be made: when we know that something is updated in the
   * browser the method recalculate() is called.  We could use a more
   * sofisticated code (like use a wait() call that would get notified when
   * recalculate() is called) but this is not required and it might have an
   * impact on the reactivity of the UI if recalculate gets called too often.
   * We can afford to wait 400 miliseconds before updating the number of
   * entries and with this approach there is hardly no impact on the reactivity
   * of the UI.
   *
   */
  protected class NumberOfEntriesUpdater extends Thread implements Runnable
  {
    private boolean recalculate;

    /**
     * Constructor.
     *
     */
    public NumberOfEntriesUpdater()
    {
    }

    /**
     * Notifies that the number of entries in the browser has changed.
     *
     */
    public void recalculate()
    {
      recalculate = true;
    }

    /**
     * Executes the updater.
     */
    public void run()
    {
      while (true)
      {
        try
        {
          Thread.sleep(400);
        }
        catch (Throwable t)
        {
        }
        if (recalculate)
        {
          recalculate = false;
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              int nEntries = 0;
              // This recursive algorithm is fast enough to use it on the
              // event thread.  Running it here we avoid issues with concurrent
              // access to the node children
              if (controller.getTree().isRootVisible())
              {
                nEntries ++;
              }
              DefaultMutableTreeNode root =
                (DefaultMutableTreeNode)controller.getTreeModel().getRoot();

              nEntries += getChildren(root);
              lNumberOfEntries.setText("Number of entries: "+nEntries);
            }
          });
        }
        if (controller != null)
        {
          final boolean mustDisplayRefreshIcon = controller.getQueueSize() > 0;
          if (mustDisplayRefreshIcon != filter.isRefreshIconDisplayed())
          {
            SwingUtilities.invokeLater(new Runnable()
            {
              public void run()
              {
                filter.displayRefreshIcon(mustDisplayRefreshIcon);
              }
            });
          }
        }
      }
    }

    /**
     * Returns the number of children for a given node.
     * @param node the node.
     * @return the number of children for the node.
     */
    private int getChildren(DefaultMutableTreeNode node)
    {
      int nEntries = 0;

      if (!node.isLeaf())
      {
        Enumeration en = node.children();
        while (en.hasMoreElements())
        {
          nEntries ++;
          nEntries += getChildren((DefaultMutableTreeNode)en.nextElement());
        }
      }
      return nEntries;
    }
  }
}
