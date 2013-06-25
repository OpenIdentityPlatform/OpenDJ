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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.datamodel.SortableListModel;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.
 ConfigurationElementCreatedListener;
import org.opends.guitools.controlpanel.event.SuperiorObjectClassesChangedEvent;
import org.opends.guitools.controlpanel.event.
 SuperiorObjectClassesChangedListener;
import org.opends.guitools.controlpanel.event.ScrollPaneBorderListener;
import org.opends.guitools.controlpanel.task.DeleteSchemaElementsTask;
import org.opends.guitools.controlpanel.task.ModifyObjectClassTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.BasicExpander;
import org.opends.guitools.controlpanel.ui.components.DoubleAddRemovePanel;
import org.opends.guitools.controlpanel.ui.components.
 SuperiorObjectClassesEditor;
import org.opends.guitools.controlpanel.ui.components.TitlePanel;
import org.opends.guitools.controlpanel.ui.renderer.
 SchemaElementComboBoxCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ObjectClassType;
import org.opends.server.types.Schema;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/**
 * The panel that displays a custom object class definition.
 *
 */
public class CustomObjectClassPanel extends SchemaElementPanel
{
  private static final long serialVersionUID = 2105520588901380L;
  private JButton delete;
  private JButton saveChanges;
  private ObjectClass objectClass;
  private String ocName;
  private ScrollPaneBorderListener scrollListener;

  private TitlePanel titlePanel = new TitlePanel(Message.EMPTY, Message.EMPTY);
  private JLabel lName = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_OBJECTCLASS_NAME_LABEL.get());
  private JLabel lSuperior = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_OBJECTCLASS_PARENT_LABEL.get());
  private JLabel lOID = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_OBJECTCLASS_OID_LABEL.get());
  private JLabel lAliases = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_OBJECTCLASS_ALIASES_LABEL.get());
  private JLabel lOrigin = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_OBJECTCLASS_ORIGIN_LABEL.get());
  private JLabel lFile = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_OBJECTCLASS_FILE_LABEL.get());
  private JTextField aliases = Utilities.createLongTextField();
  private JLabel lDescription = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_OBJECTCLASS_DESCRIPTION_LABEL.get());
  private JLabel lType = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_OBJECTCLASS_TYPE_LABEL.get());
  private JLabel lAttributes = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_OBJECTCLASS_ATTRIBUTES_LABEL.get());

  private Set<AttributeType> inheritedOptionalAttributes =
    new HashSet<AttributeType>();
  private Set<AttributeType> inheritedRequiredAttributes =
    new HashSet<AttributeType>();

  private JLabel[] labels = {lName, lSuperior, lOID, lAliases, lOrigin, lFile,
      lDescription, lType, lAttributes
  };

  private JTextField name = Utilities.createMediumTextField();
  private SuperiorObjectClassesEditor superiors =
    new SuperiorObjectClassesEditor();
  private JComboBox type = Utilities.createComboBox();
  private JTextField oid = Utilities.createMediumTextField();
  private JTextField description = Utilities.createLongTextField();
  private JTextField origin = Utilities.createLongTextField();
  private JTextField file = Utilities.createLongTextField();
  private JCheckBox obsolete = Utilities.createCheckBox(
      INFO_CTRL_PANEL_OBJECTCLASS_OBSOLETE_LABEL.get());
  private DoubleAddRemovePanel<AttributeType> attributes;

  private Schema schema;
  private Set<String> lastAliases = new LinkedHashSet<String>();

  private boolean ignoreChangeEvents;


  /**
   * Default constructor of the panel.
   *
   */
  public CustomObjectClassPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_CUSTOM_OBJECTCLASS_TITLE.get();
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  protected void createLayout()
  {
    JPanel p = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    p.setOpaque(false);
    p.setBorder(PANEL_BORDER);
    createBasicLayout(p, gbc);
    gbc = new GridBagConstraints();
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridx = 0;
    gbc.gridy = 0;
    JScrollPane scroll = Utilities.createBorderLessScrollBar(p);
    scrollListener =
      ScrollPaneBorderListener.createBottomBorderListener(scroll);
    add(scroll, gbc);

    gbc.gridy ++;
    gbc.weighty = 0.0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets = new Insets(10, 10, 10, 10);
    gbc.gridwidth = 1;
    delete = Utilities.createButton(
        INFO_CTRL_PANEL_DELETE_OBJECTCLASS_BUTTON.get());
    delete.setOpaque(false);
    add(delete, gbc);
    delete.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        deleteObjectclass();
      }
    });

    gbc.anchor = GridBagConstraints.EAST;
    gbc.gridx ++;
    saveChanges =
      Utilities.createButton(INFO_CTRL_PANEL_SAVE_CHANGES_LABEL.get());
    saveChanges.setOpaque(false);
    add(saveChanges, gbc);
    saveChanges.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        ArrayList<Message> errors = new ArrayList<Message>();
        saveChanges(false, errors);
      }
    });
  }

  /**
   * Creates the basic layout of the panel.
   * @param c the container where all the components will be layed out.
   * @param gbc the grid bag constraints.
   */
  protected void createBasicLayout(Container c, GridBagConstraints gbc)
  {
    SuperiorObjectClassesChangedListener listener =
      new SuperiorObjectClassesChangedListener()
    {
      /**
       * {@inheritDoc}
       */
      public void parentObjectClassesChanged(
          SuperiorObjectClassesChangedEvent ev)
      {
        if (ignoreChangeEvents) return;
        updateAttributesWithParent(true);
        checkEnableSaveChanges();
        if (ev.getNewObjectClasses().size() > 1)
        {
          lSuperior.setText(
              INFO_CTRL_PANEL_OBJECTCLASS_PARENTS_LABEL.get().toString());
        }
        else
        {
          lSuperior.setText(
              INFO_CTRL_PANEL_OBJECTCLASS_PARENT_LABEL.get().toString());
        }
      }
    };
    superiors.addParentObjectClassesChangedListener(listener);

    DefaultComboBoxModel model = new DefaultComboBoxModel();
    for (ObjectClassType t : ObjectClassType.values())
    {
      model.addElement(t);
    }
    type.setModel(model);
    type.setSelectedItem(ObjectClassType.STRUCTURAL);
    SchemaElementComboBoxCellRenderer renderer = new
    SchemaElementComboBoxCellRenderer(type);
    type.setRenderer(renderer);

    attributes =
      new DoubleAddRemovePanel<AttributeType>(0, AttributeType.class);
    Comparator<AttributeType> comparator = new Comparator<AttributeType>()
    {
      /**
       * {@inheritDoc}
       */
      public int compare(AttributeType attr1, AttributeType attr2)
      {
        return attr1.getNameOrOID().toLowerCase().compareTo(
            attr2.getNameOrOID().toLowerCase());
      }
    };
    attributes.getAvailableListModel().setComparator(comparator);
    attributes.getSelectedListModel1().setComparator(comparator);
    attributes.getSelectedListModel2().setComparator(comparator);

    gbc.gridy = 0;
    gbc.gridwidth = 2;
    addErrorPane(c, gbc);
    gbc.gridy ++;

    gbc.anchor = GridBagConstraints.WEST;
    titlePanel.setTitle(INFO_CTRL_PANEL_OBJECTCLASS_DETAILS.get());
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets.top = 5;
    gbc.insets.bottom = 7;
    c.add(titlePanel, gbc);

    gbc.insets.bottom = 0;
    gbc.insets.top = 8;
    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    Component[] basicComps = {name, oid, description, superiors};
    JLabel[] basicLabels = {lName, lOID, lDescription, lSuperior};
    JLabel[] basicInlineHelp = new JLabel[] {null, null, null, null};
    add(basicLabels, basicComps, basicInlineHelp, c, gbc);

    gbc.gridx = 0;
    gbc.weightx = 0.0;
    gbc.insets.left = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    c.add(lAttributes, gbc);

    gbc.gridx ++;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.insets.left = 10;
    c.add(attributes, gbc);
    attributes.getAvailableLabel().setText(
        INFO_CTRL_PANEL_ADDREMOVE_AVAILABLE_ATTRIBUTES.get().toString());
    attributes.getSelectedLabel1().setText(
        INFO_CTRL_PANEL_ADDREMOVE_REQUIRED_ATTRIBUTES.get().toString());
    attributes.getSelectedLabel2().setText(
        INFO_CTRL_PANEL_ADDREMOVE_OPTIONAL_ATTRIBUTES.get().toString());
    AttributeTypeCellRenderer listRenderer = new AttributeTypeCellRenderer();
    attributes.getAvailableList().setCellRenderer(listRenderer);
    attributes.getSelectedList1().setCellRenderer(listRenderer);
    attributes.getSelectedList2().setCellRenderer(listRenderer);

    gbc.gridy ++;
    gbc.weighty = 0.0;
    gbc.insets.top = 3;
    JLabel explanation = Utilities.createInlineHelpLabel(
        INFO_CTRL_PANEL_INHERITED_ATTRIBUTES_HELP.get());
    gbc.insets.top = 3;
    c.add(explanation, gbc);

    final BasicExpander expander = new BasicExpander(
        INFO_CTRL_PANEL_EXTRA_OPTIONS_EXPANDER.get());

    obsolete.setText("Obsolete");

    Component[] comps = {aliases, origin, file, type, obsolete};
    JLabel[] labels = {lAliases, lOrigin, lFile, lType, null};
    JLabel[] inlineHelps = {
        Utilities.createInlineHelpLabel(
            INFO_CTRL_PANEL_SEPARATED_WITH_COMMAS_HELP.get()), null,
        Utilities.createInlineHelpLabel(
            INFO_CTRL_PANEL_SCHEMA_FILE_OBJECTCLASS_HELP.get(File.separator)),
            null, null};
    gbc.gridwidth = 2;
    gbc.gridx = 0;
    gbc.weighty = 0.0;
    gbc.insets.left = 0;
    gbc.gridy ++;
    c.add(expander, gbc);
    final JPanel p = new JPanel(new GridBagLayout());
    gbc.insets.left = 15;
    gbc.gridy ++;
    c.add(p, gbc);
    gbc.gridy ++;
    p.setOpaque(false);

    GridBagConstraints gbc1 = new GridBagConstraints();
    gbc1.fill = GridBagConstraints.HORIZONTAL;
    gbc1.gridy = 0;

    add(labels, comps, inlineHelps, p, gbc1);
    ChangeListener changeListener = new ChangeListener()
    {
      /**
       * {@inheritDoc}
       */
      public void stateChanged(ChangeEvent e)
      {
        p.setVisible(expander.isSelected());
      }
    };
    expander.addChangeListener(changeListener);
    expander.setSelected(false);
    changeListener.stateChanged(null);

    DocumentListener docListener = new DocumentListener()
    {
      /**
       * {@inheritDoc}
       */
      public void insertUpdate(DocumentEvent ev)
      {
        checkEnableSaveChanges();
      }

      /**
       * {@inheritDoc}
       */
      public void removeUpdate(DocumentEvent ev)
      {
        checkEnableSaveChanges();
      }

      /**
       * {@inheritDoc}
       */
      public void changedUpdate(DocumentEvent arg0)
      {
        checkEnableSaveChanges();
      }
    };
    JTextField[] tfs = {name, description, oid, aliases, origin, file};
    for (JTextField tf : tfs)
    {
      tf.getDocument().addDocumentListener(docListener);
    }

    ActionListener actionListener = new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        checkEnableSaveChanges();
      }
    };

    type.addActionListener(actionListener);

    ListDataListener dataListener = new ListDataListener()
    {
      /**
       * {@inheritDoc}
       */
      public void contentsChanged(ListDataEvent e)
      {
        checkEnableSaveChanges();
      }
      /**
       * {@inheritDoc}
       */
      public void intervalAdded(ListDataEvent e)
      {
        checkEnableSaveChanges();
      }
      /**
       * {@inheritDoc}
       */
      public void intervalRemoved(ListDataEvent e)
      {
        checkEnableSaveChanges();
      }
    };
    SortableListModel<AttributeType> list1 = attributes.getSelectedListModel1();
    SortableListModel<AttributeType> list2 = attributes.getSelectedListModel2();
    list1.addListDataListener(dataListener);
    list2.addListDataListener(dataListener);

    obsolete.addActionListener(actionListener);
  }

  /**
   * Updates the contents of the panel with the provided object class.
   * @param oc the object class.
   * @param schema the schema.
   */
  public void update(ObjectClass oc, Schema schema)
  {
    ignoreChangeEvents = true;

    objectClass = oc;
    if ((oc == null) || (schema == null))
    {
      // Ignore: this is called to get an initial panel size.
      return;
    }
    String n = oc.getPrimaryName();
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    titlePanel.setDetails(Message.raw(n));
    name.setText(n);

    SortableListModel<AttributeType> modelRequired =
      attributes.getSelectedListModel1();
    SortableListModel<AttributeType> modelAvailable =
      attributes.getSelectedListModel2();
    SortableListModel<AttributeType> availableModel =
      attributes.getAvailableListModel();
    availableModel.addAll(modelRequired.getData());
    availableModel.addAll(modelAvailable.getData());
    modelRequired.clear();
    modelAvailable.clear();

    superiors.setSelectedSuperiors(oc.getSuperiorClasses());
    superiors.setObjectClassesToExclude(Collections.singleton(oc));
    if (oc.getSuperiorClasses().size() > 1)
    {
      lSuperior.setText(
          INFO_CTRL_PANEL_OBJECTCLASS_PARENTS_LABEL.get().toString());
    }
    else
    {
      lSuperior.setText(
          INFO_CTRL_PANEL_OBJECTCLASS_PARENT_LABEL.get().toString());
    }

    updateAttributesWithParent(false);

    for (AttributeType attr : oc.getRequiredAttributes())
    {
      availableModel.remove(attr);
      modelRequired.add(attr);
    }
    for (AttributeType attr : oc.getOptionalAttributes())
    {
      availableModel.remove(attr);
      modelAvailable.add(attr);
    }
    notifyAttributesChanged();

    oid.setText(oc.getOID());
    n = oc.getDescription();
    if (n == null)
    {
      n = "";
    }
    description.setText(n);

    Set<String> aliases = getAliases(oc);
    lastAliases.clear();
    lastAliases.addAll(aliases);
    this.aliases.setText(Utilities.getStringFromCollection(aliases, ", "));

    String sOrigin = Utilities.getOrigin(oc);
    if (sOrigin == null)
    {
      sOrigin = "";
    }
    origin.setText(sOrigin);

    String sFile = oc.getSchemaFile();
    if (sFile == null)
    {
      sFile = "";
    }
    file.setText(sFile);

    type.setSelectedItem(oc.getObjectClassType());

    obsolete.setSelected(oc.isObsolete());

    ocName = objectClass.getNameOrOID();
    scrollListener.updateBorder();
    for (JLabel label : labels)
    {
      setPrimaryValid(label);
    }
    saveChanges.setEnabled(false);
    ignoreChangeEvents = false;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final ServerDescriptor desc = ev.getNewDescriptor();
    Schema s = desc.getSchema();
    final boolean schemaChanged;
    if (schema != null && s != null)
    {
      schemaChanged = !ServerDescriptor.areSchemasEqual(s, schema);
    }
    else if (schema == null && s != null)
    {
      schemaChanged = true;
    }
    else if (s == null && schema != null)
    {
      schemaChanged = false;
    }
    else
    {
      schemaChanged = false;
    }
    if (schemaChanged)
    {
      schema = s;

      updateErrorPaneIfAuthRequired(desc,
          isLocal() ?
        INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_OBJECTCLASS_EDIT.get() :
      INFO_CTRL_PANEL_CANNOT_CONNECT_TO_REMOTE_DETAILS.get(desc.getHostname()));
    }
    else if (schema == null)
    {
      updateErrorPane(errorPane,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_SUMMARY.get(),
          ColorAndFontConstants.errorTitleFont,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_DETAILS.get(),
          ColorAndFontConstants.defaultFont);
    }
    SwingUtilities.invokeLater(new Runnable()
    {
      /**
       * {@inheritDoc}
       */
      public void run()
      {
        delete.setEnabled(!authenticationRequired(desc)
            && !authenticationRequired(desc)
            && schema != null);
        checkEnableSaveChanges();
        saveChanges.setEnabled(saveChanges.isEnabled() &&
            !authenticationRequired(desc)
            && !authenticationRequired(desc)
            && schema != null);
        if (schemaChanged && schema != null)
        {
          superiors.setSchema(schema);
          updateAttributes();
        }
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  public boolean mustCheckUnsavedChanges()
  {
    return saveChanges.isEnabled();
  }

  /**
   * {@inheritDoc}
   */
  public UnsavedChangesDialog.Result checkUnsavedChanges()
  {
    UnsavedChangesDialog.Result result;
    UnsavedChangesDialog unsavedChangesDlg = new UnsavedChangesDialog(
          Utilities.getParentDialog(this), getInfo());
    unsavedChangesDlg.setMessage(INFO_CTRL_PANEL_UNSAVED_CHANGES_SUMMARY.get(),
        INFO_CTRL_PANEL_UNSAVED_OBJECTCLASS_CHANGES_DETAILS.get(
           objectClass.getNameOrOID()));
    Utilities.centerGoldenMean(unsavedChangesDlg,
          Utilities.getParentDialog(this));
    unsavedChangesDlg.setVisible(true);
    result = unsavedChangesDlg.getResult();
    if (result == UnsavedChangesDialog.Result.SAVE)
    {
      ArrayList<Message> errors = new ArrayList<Message>();
      saveChanges(true, errors);
      if (!errors.isEmpty())
      {
        result = UnsavedChangesDialog.Result.CANCEL;
      }
    }

    return result;
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return name;
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
  }

  private void deleteObjectclass()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    ProgressDialog dlg = new ProgressDialog(
        Utilities.createFrame(),
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_DELETE_OBJECTCLASS_TITLE.get(), getInfo());
    LinkedHashSet<ObjectClass> ocsToDelete = new LinkedHashSet<ObjectClass>();
    ocsToDelete.add(objectClass);
    LinkedHashSet<AttributeType> attrsToDelete =
      new LinkedHashSet<AttributeType>(0);

    DeleteSchemaElementsTask newTask = new DeleteSchemaElementsTask(getInfo(),
        dlg, ocsToDelete, attrsToDelete);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    Schema schema = getInfo().getServerDescriptor().getSchema();
    ArrayList<String> childClasses = new ArrayList<String>();
    if (schema != null)
    {
      for (ObjectClass o : schema.getObjectClasses().values())
      {
        for (ObjectClass superior : o.getSuperiorClasses())
        {
          if (objectClass.equals(superior))
          {
            childClasses.add(o.getNameOrOID());
          }
        }
      }
    }
    else
    {
      errors.add(ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_DETAILS.get());
    }
    if (errors.size() == 0)
    {
      MessageBuilder mb = new MessageBuilder();

      if (!childClasses.isEmpty())
      {
        mb.append(INFO_OBJECTCLASS_IS_SUPERIOR.get(
            ocName,
            Utilities.getStringFromCollection(childClasses, ", ")));
        mb.append("<br>");
      }
      Message confirmationMessage =
        INFO_CTRL_PANEL_CONFIRMATION_DELETE_OBJECTCLASS_DETAILS.get(
            ocName);
      mb.append(confirmationMessage);
      if (displayConfirmationDialog(
          INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
          confirmationMessage))
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_DELETING_OBJECTCLASS_SUMMARY.get(ocName),
            INFO_CTRL_PANEL_DELETING_OBJECTCLASS_COMPLETE.get(),
            INFO_CTRL_PANEL_DELETING_OBJECTCLASS_SUCCESSFUL.get(ocName),
            ERR_CTRL_PANEL_DELETING_OBJECTCLASS_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_DELETING_OBJECTCLASS_ERROR_DETAILS.get(ocName),
            null,
            dlg);
        dlg.setVisible(true);
      }
    }
    else
    {
      displayErrorDialog(errors);
    }
  }

  private void saveChanges(boolean modal, ArrayList<Message> errors)
  {
    for (JLabel label : labels)
    {
      setPrimaryValid(label);
    }
    String n = getObjectClassName();
    MessageBuilder err = new MessageBuilder();
    if (n.length() == 0)
    {
      errors.add(ERR_CTRL_PANEL_OBJECTCLASS_NAME_REQUIRED.get());
      setPrimaryInvalid(lName);
    }
    else if (!n.equalsIgnoreCase(objectClass.getNameOrOID()))
    {
      if (!StaticUtils.isValidSchemaElement(n, 0, n.length(), err))
      {
        errors.add(ERR_CTRL_PANEL_INVALID_OBJECTCLASS_NAME.get(err.toString()));
        setPrimaryInvalid(lName);
        err = new MessageBuilder();
      }
      else
      {
        Message elementType = NewAttributePanel.getSchemaElementType(n, schema);
        if (elementType != null)
        {
          errors.add(ERR_CTRL_PANEL_OBJECTCLASS_NAME_ALREADY_IN_USE.get(n,
              elementType.toString()));
          setPrimaryInvalid(lName);
        }
      }
    }
    n = oid.getText().trim();
    if (n.length() > 0 && !n.equalsIgnoreCase(objectClass.getOID()))
    {
      if (!StaticUtils.isValidSchemaElement(n, 0, n.length(), err))
      {
        errors.add(ERR_CTRL_PANEL_OID_NOT_VALID.get(err.toString()));
        setPrimaryInvalid(lOID);
        err = new MessageBuilder();
      }
      else
      {
        Message elementType = NewAttributePanel.getSchemaElementType(n, schema);
        if (elementType != null)
        {
          errors.add(ERR_CTRL_PANEL_OID_ALREADY_IN_USE.get(n,
              elementType.toString()));
          setPrimaryInvalid(lOID);
        }
      }
    }

    Collection<String> aliases = getAliases();
    Collection<String> oldAliases = getAliases(objectClass);

    if (!aliases.equals(oldAliases))
    {
      for (String alias : aliases)
      {
        if (alias.trim().length() == 0)
        {
          errors.add(ERR_CTRL_PANEL_EMPTY_ALIAS.get());
          setPrimaryInvalid(lAliases);
        }
        else
        {
          boolean notPreviouslyDefined = true;
          for (String oldAlias : oldAliases)
          {
            if (oldAlias.equalsIgnoreCase(alias))
            {
              notPreviouslyDefined = false;
              break;
            }
          }
          if (notPreviouslyDefined)
          {
            Message elementType =
              NewAttributePanel.getSchemaElementType(alias, schema);
            if (elementType != null)
            {
              errors.add(ERR_CTRL_PANEL_ALIAS_ALREADY_IN_USE.get(n,
                  elementType.toString()));
              setPrimaryInvalid(lAliases);
            }
          }
        }
      }
    }


   //validate the superiority.
    for(ObjectClass superior : getObjectClassSuperiors())
    {
      validateSuperiority(superior, errors);
    }
    checkCompatibleSuperiors(getObjectClassSuperiors(), getObjectClassType(),
        errors);

    if (errors.size() == 0)
    {
      ProgressDialog dlg = new ProgressDialog(
          Utilities.createFrame(),
          Utilities.getParentDialog(this),
          INFO_CTRL_PANEL_MODIFY_ATTRIBUTE_TITLE.get(), getInfo());

      ModifyObjectClassTask newTask = new ModifyObjectClassTask(getInfo(),
          dlg, objectClass, getNewObjectClass());
      for (ConfigurationElementCreatedListener listener :
        getConfigurationElementCreatedListeners())
      {
        newTask.addConfigurationElementCreatedListener(listener);
      }
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      if (errors.size() == 0)
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_MODIFYING_OBJECTCLASS_SUMMARY.get(ocName),
            INFO_CTRL_PANEL_MODIFYING_OBJECTCLASS_COMPLETE.get(),
            INFO_CTRL_PANEL_MODIFYING_OBJECTCLASS_SUCCESSFUL.get(ocName),
            ERR_CTRL_PANEL_MODIFYING_OBJECTCLASS_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_MODIFYING_OBJECTCLASS_ERROR_DETAILS.get(ocName),
            null,
            dlg);
        dlg.setVisible(true);
      }
    }

    if (!errors.isEmpty())
    {
      displayErrorDialog(errors);
    }
  }


  private void validateSuperiority(ObjectClass superior,
          ArrayList<Message> errors)
  {
    if(superior.getNameOrOID().equalsIgnoreCase(objectClass.getNameOrOID()))
    {
      errors.add(ERR_CTRL_PANEL_OBJECTCLASS_CANNOT_BE_ITS_SUPERIOR.get());
      setPrimaryInvalid(lSuperior);
      return;
    }
    for (ObjectClass obj : superior.getSuperiorClasses())
    {
      if (superior.getNameOrOID().equalsIgnoreCase(obj.getNameOrOID()))
      {
         errors.add(
                ERR_CTRL_PANEL_OBJECTCLASS_IS_SUPERIOR_OF_SUPERIOR.get(
                obj.getNameOrOID()));
            setPrimaryInvalid(lSuperior);
        return;
      }
      validateSuperiority(obj,errors);
    }
  }

  private void checkEnableSaveChanges()
  {
    if (ignoreChangeEvents) return;
    boolean changed;

    if (objectClass != null)
    {
      try
      {
        changed = !objectClass.toString().equals(
            getNewObjectClass().toString());
      }
      catch (Throwable t)
      {
        changed = true;
      }
    }
    else
    {
      changed = false;
    }
    saveChanges.setEnabled(changed);
  }

  private Set<String> getAliases()
  {
    Set<String> al = new LinkedHashSet<String>();
    String s = aliases.getText().trim();
    if (s.length() > 0)
    {
      String[] a = s.split(",");
      for (String alias : a)
      {
        al.add(alias.trim());
      }
    }
    return al;
  }

  private String getObjectClassName()
  {
    return name.getText().trim();
  }

  private String getOID()
  {
    String o = oid.getText().trim();
    if (o.length() == 0)
    {
      o = getObjectClassName()+"-oid";
    }
    return o;
  }

  private Map<String, List<String>> getExtraProperties()
  {
    Map<String, List<String>> map = new HashMap<String, List<String>>();
    String f = file.getText().trim();
    if (f.length() > 0)
    {
      ArrayList<String> list = new ArrayList<String>();
      list.add(f);
      map.put(ServerConstants.SCHEMA_PROPERTY_FILENAME, list);
    }
    String or = origin.getText().trim();
    if (or.length() > 0)
    {
      ArrayList<String> list = new ArrayList<String>();
      list.add(or);
      map.put(ServerConstants.SCHEMA_PROPERTY_ORIGIN, list);
    }
    return map;
  }

  private ArrayList<String> getAllNames()
  {
    ArrayList<String> al = new ArrayList<String>();
    al.add(getObjectClassName());
    al.addAll(getAliases());
    return al;
  }

  private String getDescription()
  {
    return description.getText().trim();
  }

  private Set<ObjectClass> getObjectClassSuperiors()
  {
    return superiors.getSelectedSuperiors();
  }

  private ObjectClassType getObjectClassType()
  {
    return (ObjectClassType)type.getSelectedItem();
  }

  private Set<AttributeType> getRequiredAttributes()
  {
    HashSet<AttributeType> attrs = new HashSet<AttributeType>();
    attrs.addAll(attributes.getSelectedListModel1().getData());
    attrs.removeAll(inheritedRequiredAttributes);
    return attrs;
  }

  private Set<AttributeType> getOptionalAttributes()
  {
    HashSet<AttributeType> attrs = new HashSet<AttributeType>();
    attrs.addAll(attributes.getSelectedListModel2().getData());
    attrs.removeAll(inheritedOptionalAttributes);
    return attrs;
  }

  private ObjectClass getNewObjectClass()
  {
    ObjectClass newObjectClass = new ObjectClass("",
        getObjectClassName(),
        getAllNames(),
        getOID(),
        getDescription(),
        getObjectClassSuperiors(),
        getRequiredAttributes(),
        getOptionalAttributes(),
        getObjectClassType(),
        obsolete.isSelected(),
        getExtraProperties());

    return newObjectClass;
  }

  private void updateAttributes()
  {
    int[][] selected =
    {
      attributes.getAvailableList().getSelectedIndices(),
      attributes.getSelectedList1().getSelectedIndices(),
      attributes.getSelectedList2().getSelectedIndices()
    };
    JList[] lists =
    {
        attributes.getAvailableList(),
        attributes.getSelectedList1(),
        attributes.getSelectedList2()
    };
    attributes.getAvailableListModel().clear();
    Collection<AttributeType> allAttrs =
      schema.getAttributeTypes().values();
    attributes.getAvailableListModel().addAll(allAttrs);


    HashSet<AttributeType> toDelete = new HashSet<AttributeType>();
    for (AttributeType attr : attributes.getSelectedListModel1().getData())
    {
      if (!allAttrs.contains(attr))
      {
        toDelete.add(attr);
      }
      else
      {
        attributes.getAvailableListModel().remove(attr);
      }
    }
    for (AttributeType attr : toDelete)
    {
      attributes.getSelectedListModel1().remove(attr);
    }

    toDelete = new HashSet<AttributeType>();
    for (AttributeType attr : attributes.getSelectedListModel2().getData())
    {
      if (!allAttrs.contains(attr))
      {
        toDelete.add(attr);
      }
      else
      {
        attributes.getAvailableListModel().remove(attr);
      }
    }
    for (AttributeType attr : toDelete)
    {
      attributes.getSelectedListModel1().remove(attr);
    }

    int i = 0;
    for (int[] sel : selected)
    {
      if (sel != null)
      {
        ArrayList<Integer> indexes = new ArrayList<Integer>();
        for (int j=0; j<sel.length; j++)
        {
          if (sel[j] < lists[i].getModel().getSize())
          {
            indexes.add(sel[j]);
          }
        }
        int[] newSelection = new int[indexes.size()];
        for (int j=0; j<newSelection.length; j++)
        {
          newSelection[j] = indexes.get(j);
        }
        lists[i].setSelectedIndices(newSelection);
      }
      i++;
    }
  }

  private void updateAttributesWithParent(boolean notify)
  {
 // Remove the previous inherited attributes.
    for (AttributeType attr : inheritedRequiredAttributes)
    {
      attributes.getAvailableListModel().add(attr);
      attributes.getSelectedListModel1().remove(attr);
    }
    for (AttributeType attr : inheritedOptionalAttributes)
    {
      attributes.getAvailableListModel().add(attr);
      attributes.getSelectedListModel2().remove(attr);
    }

    inheritedOptionalAttributes.clear();
    inheritedRequiredAttributes.clear();
    for (ObjectClass p : getObjectClassSuperiors())
    {
      for (AttributeType attr : p.getRequiredAttributeChain())
      {
        inheritedRequiredAttributes.add(attr);
      }
      for (AttributeType attr : p.getOptionalAttributeChain())
      {
        inheritedOptionalAttributes.add(attr);
      }
    }
    for (AttributeType attr : inheritedRequiredAttributes)
    {
      attributes.getAvailableListModel().remove(attr);
      attributes.getSelectedListModel1().add(attr);
    }
    for (AttributeType attr : inheritedOptionalAttributes)
    {
      attributes.getAvailableListModel().remove(attr);
      attributes.getSelectedListModel2().add(attr);
    }

    Collection<AttributeType> unmovableItems =
      new ArrayList<AttributeType>(inheritedRequiredAttributes);
    unmovableItems.addAll(inheritedOptionalAttributes);
    attributes.setUnmovableItems(unmovableItems);

    if (notify)
    {
      notifyAttributesChanged();
    }
  }

  private void notifyAttributesChanged()
  {
    attributes.getAvailableListModel().fireContentsChanged(
        attributes.getAvailableList(), 0,
        attributes.getAvailableListModel().getSize() - 1);
    attributes.getSelectedListModel1().fireContentsChanged(
        attributes.getSelectedList1(), 0,
        attributes.getSelectedListModel1().getSize() - 1);
    attributes.getSelectedListModel2().fireContentsChanged(
        attributes.getSelectedList2(), 0,
        attributes.getSelectedListModel2().getSize() - 1);
  }


  /**
   * A renderer for the attribute lists.  The renderer basically marks the
   * inherited attributes with an asterisk.
   *
   */
  private class AttributeTypeCellRenderer implements ListCellRenderer
  {
    private ListCellRenderer defaultRenderer;

    /**
     * Renderer constructor.
     *
     */
    public AttributeTypeCellRenderer()
    {
      defaultRenderer = attributes.getAvailableList().getCellRenderer();
    }

    /**
     * {@inheritDoc}
     */
    public Component getListCellRendererComponent(JList list, Object value,
        int index, boolean isSelected, boolean cellHasFocus)
    {
      if (value instanceof AttributeType)
      {
        AttributeType attr = (AttributeType)value;
        if (inheritedOptionalAttributes.contains(value) ||
            inheritedRequiredAttributes.contains(value))
        {
          value = attr.getNameOrOID()+ " (*)";
        }
        else
        {
          value = attr.getNameOrOID();
        }
      }
      return defaultRenderer.getListCellRendererComponent(list, value, index,
          isSelected, cellHasFocus);
    }
  }
}
