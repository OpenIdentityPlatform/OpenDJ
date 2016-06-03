/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.types.CommonSchemaElements.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.SchemaUtils.*;

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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.ObjectClassType;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.datamodel.SomeSchemaElement;
import org.opends.guitools.controlpanel.datamodel.SortableListModel;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.ConfigurationElementCreatedListener;
import org.opends.guitools.controlpanel.event.ScrollPaneBorderListener;
import org.opends.guitools.controlpanel.event.SuperiorObjectClassesChangedEvent;
import org.opends.guitools.controlpanel.event.SuperiorObjectClassesChangedListener;
import org.opends.guitools.controlpanel.task.DeleteSchemaElementsTask;
import org.opends.guitools.controlpanel.task.ModifyObjectClassTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.BasicExpander;
import org.opends.guitools.controlpanel.ui.components.DoubleAddRemovePanel;
import org.opends.guitools.controlpanel.ui.components.SuperiorObjectClassesEditor;
import org.opends.guitools.controlpanel.ui.components.TitlePanel;
import org.opends.guitools.controlpanel.ui.renderer.SchemaElementComboBoxCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.types.Schema;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/** The panel that displays a custom object class definition. */
public class CustomObjectClassPanel extends SchemaElementPanel
{
  private static final long serialVersionUID = 2105520588901380L;
  private JButton delete;
  private JButton saveChanges;
  private ObjectClass objectClass;
  private String ocName;
  private ScrollPaneBorderListener scrollListener;

  private TitlePanel titlePanel = new TitlePanel(LocalizableMessage.EMPTY, LocalizableMessage.EMPTY);
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

  private Set<AttributeType> inheritedOptionalAttributes = new HashSet<>();
  private Set<AttributeType> inheritedRequiredAttributes = new HashSet<>();

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
  private Set<String> lastAliases = new LinkedHashSet<>();

  private boolean ignoreChangeEvents;

  /** Default constructor of the panel. */
  public CustomObjectClassPanel()
  {
    super();
    createLayout();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_CUSTOM_OBJECTCLASS_TITLE.get();
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
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
      @Override
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
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        ArrayList<LocalizableMessage> errors = new ArrayList<>();
        saveChanges(errors);
      }
    });
  }

  /**
   * Creates the basic layout of the panel.
   * @param c the container where all the components will be layed out.
   * @param gbc the grid bag constraints.
   */
  private void createBasicLayout(Container c, GridBagConstraints gbc)
  {
    SuperiorObjectClassesChangedListener listener =
      new SuperiorObjectClassesChangedListener()
    {
      @Override
      public void parentObjectClassesChanged(
          SuperiorObjectClassesChangedEvent ev)
      {
        if (ignoreChangeEvents)
        {
          return;
        }
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

    attributes = new DoubleAddRemovePanel<>(0, AttributeType.class);
    Comparator<AttributeType> comparator = new Comparator<AttributeType>()
    {
      @Override
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
      @Override
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
      @Override
      public void insertUpdate(DocumentEvent ev)
      {
        checkEnableSaveChanges();
      }

      @Override
      public void removeUpdate(DocumentEvent ev)
      {
        checkEnableSaveChanges();
      }

      @Override
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
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        checkEnableSaveChanges();
      }
    };

    type.addActionListener(actionListener);

    ListDataListener dataListener = new ListDataListener()
    {
      @Override
      public void contentsChanged(ListDataEvent e)
      {
        checkEnableSaveChanges();
      }
      @Override
      public void intervalAdded(ListDataEvent e)
      {
        checkEnableSaveChanges();
      }
      @Override
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
    if (oc == null || schema == null)
    {
      // Ignore: this is called to get an initial panel size.
      return;
    }
    String n = oc.getNameOrOID();
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    titlePanel.setDetails(LocalizableMessage.raw(n));
    name.setText(n);

    SortableListModel<AttributeType> modelRequired = attributes.getSelectedListModel1();
    SortableListModel<AttributeType> modelAvailable = attributes.getSelectedListModel2();
    SortableListModel<AttributeType> availableModel = attributes.getAvailableListModel();
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

    for (AttributeType attr : oc.getDeclaredRequiredAttributes())
    {
      availableModel.remove(attr);
      modelRequired.add(attr);
    }
    for (AttributeType attr : oc.getDeclaredOptionalAttributes())
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

    String sOrigin = new SomeSchemaElement(oc).getOrigin();
    if (sOrigin == null)
    {
      sOrigin = "";
    }
    origin.setText(sOrigin);

    String sFile = getSchemaFile(oc);
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

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final ServerDescriptor desc = ev.getNewDescriptor();
    Schema s = desc.getSchema();
    final boolean schemaChanged = schemaChanged(s);
    if (schemaChanged)
    {
      schema = s;

      updateErrorPaneIfAuthRequired(desc, isLocal()
          ? INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_OBJECTCLASS_EDIT.get()
          : INFO_CTRL_PANEL_CANNOT_CONNECT_TO_REMOTE_DETAILS.get(desc.getHostname()));
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
      @Override
      public void run()
      {
        final boolean enabled = !authenticationRequired(desc) && schema != null;
        delete.setEnabled(enabled);
        checkEnableSaveChanges();
        saveChanges.setEnabled(enabled && saveChanges.isEnabled());
        if (schemaChanged && schema != null)
        {
          superiors.setSchema(schema);
          updateAttributes();
        }
      }
    });
  }

  private boolean schemaChanged(Schema s)
  {
    if (s != null)
    {
      return schema == null || !ServerDescriptor.areSchemasEqual(s, schema);
    }
    return false;
  }

  @Override
  public boolean mustCheckUnsavedChanges()
  {
    return saveChanges.isEnabled();
  }

  @Override
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
      List<LocalizableMessage> errors = new ArrayList<>();
      saveChanges(errors);
      if (!errors.isEmpty())
      {
        result = UnsavedChangesDialog.Result.CANCEL;
      }
    }

    return result;
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return name;
  }

  @Override
  public void okClicked()
  {
  }

  private void deleteObjectclass()
  {
    ArrayList<LocalizableMessage> errors = new ArrayList<>();
    ProgressDialog dlg = new ProgressDialog(
        Utilities.createFrame(),
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_DELETE_OBJECTCLASS_TITLE.get(), getInfo());
    LinkedHashSet<ObjectClass> ocsToDelete = new LinkedHashSet<>();
    ocsToDelete.add(objectClass);
    LinkedHashSet<AttributeType> attrsToDelete = new LinkedHashSet<>(0);

    DeleteSchemaElementsTask newTask = new DeleteSchemaElementsTask(getInfo(),
        dlg, ocsToDelete, attrsToDelete);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    Schema schema = getInfo().getServerDescriptor().getSchema();
    ArrayList<String> childClasses = new ArrayList<>();
    if (schema != null)
    {
      for (ObjectClass o : schema.getObjectClasses())
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
    if (errors.isEmpty())
    {
      LocalizableMessageBuilder mb = new LocalizableMessageBuilder();

      if (!childClasses.isEmpty())
      {
        mb.append(INFO_OBJECTCLASS_IS_SUPERIOR.get(
            ocName,
            Utilities.getStringFromCollection(childClasses, ", ")));
        mb.append("<br>");
      }
      LocalizableMessage confirmationMessage =
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

  private void saveChanges(List<LocalizableMessage> errors)
  {
    for (JLabel label : labels)
    {
      setPrimaryValid(label);
    }
    String n = getObjectClassName();
    LocalizableMessageBuilder err = new LocalizableMessageBuilder();
    if (n.length() == 0)
    {
      errors.add(ERR_CTRL_PANEL_OBJECTCLASS_NAME_REQUIRED.get());
      setPrimaryInvalid(lName);
    }
    else if (!n.equalsIgnoreCase(objectClass.getNameOrOID()))
    {
      if (!StaticUtils.isValidSchemaElement(n, 0, n.length(), err))
      {
        errors.add(ERR_CTRL_PANEL_INVALID_OBJECTCLASS_NAME.get(err));
        setPrimaryInvalid(lName);
        err = new LocalizableMessageBuilder();
      }
      else
      {
        LocalizableMessage elementType = NewAttributePanel.getSchemaElementType(n, schema);
        if (elementType != null)
        {
          errors.add(ERR_CTRL_PANEL_OBJECTCLASS_NAME_ALREADY_IN_USE.get(n, elementType));
          setPrimaryInvalid(lName);
        }
      }
    }
    n = oid.getText().trim();
    if (n.length() > 0 && !n.equalsIgnoreCase(objectClass.getOID()))
    {
      if (!StaticUtils.isValidSchemaElement(n, 0, n.length(), err))
      {
        errors.add(ERR_CTRL_PANEL_OID_NOT_VALID.get(err));
        setPrimaryInvalid(lOID);
        err = new LocalizableMessageBuilder();
      }
      else
      {
        LocalizableMessage elementType = NewAttributePanel.getSchemaElementType(n, schema);
        if (elementType != null)
        {
          errors.add(ERR_CTRL_PANEL_OID_ALREADY_IN_USE.get(n, elementType));
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
          boolean notPreviouslyDefined = !containsIgnoreCase(oldAliases, alias);
          if (notPreviouslyDefined)
          {
            LocalizableMessage elementType =
              NewAttributePanel.getSchemaElementType(alias, schema);
            if (elementType != null)
            {
              errors.add(ERR_CTRL_PANEL_ALIAS_ALREADY_IN_USE.get(n, elementType));
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

    if (errors.isEmpty())
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
      if (errors.isEmpty())
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

  private boolean containsIgnoreCase(Collection<String> col, String toFind)
  {
    for (String s : col)
    {
      if (s.equalsIgnoreCase(toFind))
      {
        return true;
      }
    }
    return false;
  }

  private void validateSuperiority(ObjectClass superior, List<LocalizableMessage> errors)
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
    if (!ignoreChangeEvents)
    {
      saveChanges.setEnabled(hasChanged());
    }
  }

  private boolean hasChanged()
  {
    if (objectClass != null)
    {
      try
      {
        return !objectClass.toString().equals(getNewObjectClass().toString());
      }
      catch (Throwable t)
      {
        return true;
      }
    }
    return false;
  }

  private Set<String> getAliases()
  {
    Set<String> al = new LinkedHashSet<>();
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
    Map<String, List<String>> map = new HashMap<>();
    String f = file.getText().trim();
    if (f.length() > 0)
    {
      map.put(ServerConstants.SCHEMA_PROPERTY_FILENAME, newArrayList(f));
    }
    String or = origin.getText().trim();
    if (or.length() > 0)
    {
      map.put(ServerConstants.SCHEMA_PROPERTY_ORIGIN, newArrayList(or));
    }
    return map;
  }

  private ArrayList<String> getAllNames()
  {
    ArrayList<String> al = new ArrayList<>();
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
    return intersect(attributes.getSelectedListModel1().getData(), inheritedRequiredAttributes);
  }

  private Set<AttributeType> getOptionalAttributes()
  {
    return intersect(attributes.getSelectedListModel2().getData(), inheritedOptionalAttributes);
  }

  private Set<AttributeType> intersect(Set<AttributeType> set1, Set<AttributeType> set2)
  {
    HashSet<AttributeType> attrs = new HashSet<>(set1);
    attrs.removeAll(set2);
    return attrs;
  }

  private ObjectClass getNewObjectClass()
  {
    return new SchemaBuilder(schema.getSchemaNG()).buildObjectClass(getOID())
        .names(getAllNames())
        .description(getDescription())
        .superiorObjectClasses(getNameOrOIDsForOCs(getObjectClassSuperiors()))
        .requiredAttributes(getNameOrOIDsForATs(getRequiredAttributes()))
        .optionalAttributes(getNameOrOIDsForATs(getOptionalAttributes()))
        .type(getObjectClassType())
        .obsolete(obsolete.isSelected())
        .extraProperties(getExtraProperties())
        .addToSchema()
        .toSchema()
        .getObjectClass(getOID());
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
    Collection<AttributeType> allAttrs = schema.getAttributeTypes();
    attributes.getAvailableListModel().addAll(allAttrs);

    HashSet<AttributeType> toDelete = new HashSet<>();
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

    toDelete = new HashSet<>();
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
        ArrayList<Integer> indexes = new ArrayList<>();
        for (int element : sel)
        {
          if (element < lists[i].getModel().getSize())
          {
            indexes.add(element);
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
      inheritedRequiredAttributes.addAll(p.getRequiredAttributes());
      inheritedOptionalAttributes.addAll(p.getOptionalAttributes());
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

    Collection<AttributeType> unmovableItems = new ArrayList<>(inheritedRequiredAttributes);
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
   */
  private class AttributeTypeCellRenderer implements ListCellRenderer
  {
    private ListCellRenderer defaultRenderer;

    /** Renderer constructor. */
    public AttributeTypeCellRenderer()
    {
      defaultRenderer = attributes.getAvailableList().getCellRenderer();
    }

    @Override
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
