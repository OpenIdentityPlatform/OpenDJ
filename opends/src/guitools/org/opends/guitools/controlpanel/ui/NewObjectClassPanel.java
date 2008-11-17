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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.NamingException;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.OfflineUpdateException;
import org.opends.guitools.controlpanel.task.OnlineUpdateException;
import org.opends.guitools.controlpanel.task.SchemaTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.BasicExpander;
import org.opends.guitools.controlpanel.ui.components.DoubleAddRemovePanel;
import
org.opends.guitools.controlpanel.ui.renderer.SchemaElementComboBoxCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.config.ConfigConstants;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.CommonSchemaElements;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ObjectClassType;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Schema;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/**
 * The panel displayed when the user wants to define a new object class in the
 * schema.
 *
 */
public class NewObjectClassPanel extends StatusGenericPanel
{
 private static final long serialVersionUID = -4956885827963184571L;
  private JLabel lName = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_OBJECTCLASS_NAME_LABEL.get());
  private JLabel lParent = Utilities.createPrimaryLabel(
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

  private JLabel[] labels = {lName, lParent, lOID, lAliases, lOrigin, lFile,
      lDescription, lType, lAttributes
  };

  private JTextField name = Utilities.createMediumTextField();
  private JComboBox parent = Utilities.createComboBox();
  private JComboBox type = Utilities.createComboBox();
  private JTextField oid = Utilities.createMediumTextField();
  private JTextField description = Utilities.createLongTextField();
  private JTextField origin = Utilities.createLongTextField();
  private JTextField file = Utilities.createLongTextField();
  private JCheckBox obsolete = Utilities.createCheckBox(
      INFO_CTRL_PANEL_OBJECTCLASS_OBSOLETE_LABEL.get());
  private DoubleAddRemovePanel<AttributeType> attributes;

  private Schema schema;

  private Component relativeComponent;

  /**
   * Constructor of the new object class panel.
   * @param relativeComponent the component relative to which the dialog
   * containing this panel must be centered.
   */
  public NewObjectClassPanel(Component relativeComponent)
  {
    super();
    this.relativeComponent = relativeComponent;
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_NEW_OBJECTCLASS_PANEL_TITLE.get();
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
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final ServerDescriptor desc = ev.getNewDescriptor();
    Schema s = desc.getSchema();

    final boolean firstSchema = schema == null;
    final boolean[] repack = {firstSchema};
    final boolean[] error = {false};

    if (s != null)
    {
      schema = s;

      HashMap<String, ObjectClass> objectClassNameMap = new HashMap<String,
      ObjectClass>();
      for (String key : schema.getObjectClasses().keySet())
      {
        ObjectClass oc = schema.getObjectClass(key);
        objectClassNameMap.put(oc.getNameOrOID(), oc);
      }
      SortedSet<String> orderedKeys = new TreeSet<String>();
      orderedKeys.addAll(objectClassNameMap.keySet());
      ArrayList<Object> newParents = new ArrayList<Object>();
      for (String key : orderedKeys)
      {
        newParents.add(objectClassNameMap.get(key));
      }
      updateComboBoxModel(newParents,
          ((DefaultComboBoxModel)parent.getModel()));
    }
    else
    {
      updateErrorPane(errorPane,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_SUMMARY.get(),
          ColorAndFontConstants.errorTitleFont,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_DETAILS.get(),
          ColorAndFontConstants.defaultFont);
      repack[0] = true;
      error[0] = true;
    }
    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        setEnabledOK(!error[0]);
        errorPane.setVisible(error[0]);
        if (schema != null)
        {
          if (firstSchema)
          {
            parent.setSelectedItem(schema.getObjectClass("top"));
          }
          updateAttributes();
        }
        if (repack[0])
        {
          packParentDialog();
          if (relativeComponent != null)
          {
            Utilities.centerGoldenMean(
                Utilities.getParentDialog(NewObjectClassPanel.this),
                relativeComponent);
          }
        }
      }
    });
    if (!error[0])
    {
      updateErrorPaneAndOKButtonIfAuthRequired(desc,
   INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_TO_CREATE_OBJECTCLASS_SUMMARY.get());
    }
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    for (JLabel label : labels)
    {
      setPrimaryValid(label);
    }
    String n = getObjectClassName();
    MessageBuilder err = new MessageBuilder();
    if (n.length() == 0)
    {
      errors.add(ERR_CTRL_PANEL_OBJECTCLASS_NAME_REQUIRED.get());
    }
    else if (!StaticUtils.isValidSchemaElement(n, 0, n.length(), err))
    {
      errors.add(ERR_CTRL_PANEL_INVALID_OBJECTCLASS_NAME.get(err.toString()));
      err = new MessageBuilder();
    }
    else
    {
      Message elementType = NewAttributePanel.getSchemaElementType(n, schema);
      if (elementType != null)
      {
        errors.add(ERR_CTRL_PANEL_OBJECTCLASS_NAME_ALREADY_IN_USE.get(n,
            elementType.toString()));
      }
    }

    n = oid.getText().trim();
    if (n.length() > 0)
    {
      if (!StaticUtils.isValidSchemaElement(n, 0, n.length(), err))
      {
        errors.add(ERR_CTRL_PANEL_OID_NOT_VALID.get(err.toString()));
        err = new MessageBuilder();
      }
      else
      {
        Message elementType = NewAttributePanel.getSchemaElementType(n, schema);
        if (elementType != null)
        {
          errors.add(ERR_CTRL_PANEL_OID_ALREADY_IN_USE.get(n,
              elementType.toString()));
        }
      }
    }

    if (aliases.getText().trim().length() > 0)
    {
      String[] al = aliases.getText().split(",");
      if (al.length > 0)
      {
        for (String alias : al)
        {
          if (alias.trim().length() == 0)
          {
            errors.add(ERR_CTRL_PANEL_EMPTY_ALIAS.get());
          }
          else
          {
            Message elementType = NewAttributePanel.getSchemaElementType(
                alias, schema);
            if (elementType != null)
            {
              errors.add(ERR_CTRL_PANEL_ALIAS_ALREADY_IN_USE.get(n,
                  elementType.toString()));
            }
          }
        }
      }
    }

    ProgressDialog dlg = new ProgressDialog(
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_NEW_OBJECTCLASS_PANEL_TITLE.get(), getInfo());
    NewObjectClassTask newTask = null;
    if (errors.size() == 0)
    {
      newTask = new NewObjectClassTask(getInfo(), dlg);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
    }
    if (errors.size() == 0)
    {
      String ocName = getObjectClassName();
      launchOperation(newTask,
          INFO_CTRL_PANEL_CREATING_OBJECTCLASS_SUMMARY.get(ocName),
          INFO_CTRL_PANEL_CREATING_OBJECTCLASS_COMPLETE.get(),
          INFO_CTRL_PANEL_CREATING_OBJECTCLASS_SUCCESSFUL.get(ocName),
          ERR_CTRL_PANEL_CREATING_OBJECTCLASS_ERROR_SUMMARY.get(),
          ERR_CTRL_PANEL_CREATING_OBJECTCLASS_ERROR_DETAILS.get(ocName),
          null,
          dlg);
      dlg.setVisible(true);
      name.setText("");
      oid.setText("");
      description.setText("");
      aliases.setText("");
      name.grabFocus();
      Utilities.getParentDialog(this).setVisible(false);
    }
    else
    {
      displayErrorDialog(errors);
    }
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

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();

    Utilities.setRequiredIcon(lName);

    gbc.gridwidth = 2;
    gbc.gridy = 0;
    addErrorPane(gbc);

    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.weighty = 0.0;
    gbc.gridx = 1;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.fill = GridBagConstraints.NONE;
    JLabel requiredLabel = createRequiredLabel();
    gbc.insets.bottom = 10;
    add(requiredLabel, gbc);

    gbc.gridy ++;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets.bottom = 0;

    SchemaElementComboBoxCellRenderer renderer = new
    SchemaElementComboBoxCellRenderer(parent);
    DefaultComboBoxModel model = new DefaultComboBoxModel();
    parent.setModel(model);
    parent.setRenderer(renderer);
    ItemListener itemListener = new ItemListener()
    {
      /**
       * {@inheritDoc}
       */
      public void itemStateChanged(ItemEvent ev)
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
        ObjectClass p = (ObjectClass)parent.getSelectedItem();
        while (p != null)
        {
          for (AttributeType attr : p.getRequiredAttributeChain())
          {
            inheritedRequiredAttributes.add(attr);
          }
          for (AttributeType attr : p.getOptionalAttributeChain())
          {
            inheritedOptionalAttributes.add(attr);
          }
          p = p.getSuperiorClass();
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
        attributes.getAvailableListModel().fireContentsChanged(
            attributes.getAvailableList(), 0,
            attributes.getAvailableList().getModel().getSize() - 1);
        attributes.getSelectedListModel1().fireContentsChanged(
            attributes.getSelectedList1(), 0,
            attributes.getSelectedList1().getModel().getSize() - 1);
        attributes.getSelectedListModel2().fireContentsChanged(
            attributes.getSelectedList2(), 0,
            attributes.getSelectedList2().getModel().getSize() - 1);

        Collection<AttributeType> unmovableItems =
          new ArrayList<AttributeType>(inheritedRequiredAttributes);
        unmovableItems.addAll(inheritedOptionalAttributes);
        attributes.setUnmovableItems(unmovableItems);
      }
    };
    parent.addItemListener(itemListener);

    model = new DefaultComboBoxModel();
    for (ObjectClassType t : ObjectClassType.values())
    {
      model.addElement(t);
    }
    type.setModel(model);
    type.setSelectedItem(ObjectClassType.STRUCTURAL);
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
        return attr1.getNameOrOID().compareTo(attr2.getNameOrOID());
      }
    };
    attributes.getAvailableListModel().setComparator(comparator);
    attributes.getSelectedListModel1().setComparator(comparator);
    attributes.getSelectedListModel2().setComparator(comparator);

    Component[] basicComps = {name, oid, description, parent};
    JLabel[] basicLabels = {lName, lOID, lDescription, lParent};
    JLabel[] basicInlineHelp = new JLabel[] {null, null, null, null};
    add(basicLabels, basicComps, basicInlineHelp, this, gbc);

    gbc.gridx = 0;
    gbc.weightx = 0.0;
    gbc.insets.left = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    add(lAttributes, gbc);

    gbc.gridx ++;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.insets.left = 10;
    add(attributes, gbc);
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
    add(explanation, gbc);

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
    add(expander, gbc);
    final JPanel p = new JPanel(new GridBagLayout());
    gbc.insets.left = 15;
    gbc.gridy ++;
    add(p, gbc);
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
        packParentDialog();
      }
    };
    expander.addChangeListener(changeListener);
    expander.setSelected(false);
    changeListener.stateChanged(null);

    file.setText(ConfigConstants.FILE_USER_SCHEMA_ELEMENTS);
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

  private ObjectClass getSuperior()
  {
    return (ObjectClass)parent.getSelectedItem();
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

  private ArrayList<String> getAliases()
  {
    ArrayList<String> al = new ArrayList<String>();
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

  private ArrayList<String> getAllNames()
  {
    ArrayList<String> al = new ArrayList<String>();
    al.add(getObjectClassName());
    al.addAll(getAliases());
    return al;
  }

  private ObjectClass getObjectClass()
  {
    ObjectClass oc = new ObjectClass("", getObjectClassName(), getAllNames(),
        getOID(), description.getText().trim(),
        getSuperior(),
        getRequiredAttributes(),
        getAllowedAttributes(),
        getObjectClassType(),
        obsolete.isSelected(),
        getExtraProperties());

    return oc;
  }

  private ObjectClassType getObjectClassType()
  {
    return (ObjectClassType)type.getSelectedItem();
  }

  private Set<AttributeType> getRequiredAttributes()
  {
    HashSet<AttributeType> attrs = new HashSet<AttributeType>();
    attrs.addAll(attributes.getSelectedListModel1().getData());
    return attrs;
  }

  private Set<AttributeType> getAllowedAttributes()
  {
    HashSet<AttributeType> attrs = new HashSet<AttributeType>();
    attrs.addAll(attributes.getSelectedListModel2().getData());
    return attrs;
  }

  /**
   * The task in charge of creating the object class.
   *
   */
  protected class NewObjectClassTask extends SchemaTask
  {
    private ObjectClass oc;
    private String ocName;
    private String ocDefinition;
    private String ocWithoutFileDefinition;

    /**
     * The constructor of the task.
     * @param info the control panel info.
     * @param dlg the progress dialog that shows the progress of the task.
     */
    public NewObjectClassTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
      ocName = getObjectClassName();
      ocDefinition = getSchemaElement().toString();
      ObjectClass oc = getObjectClass();
      oc.setExtraProperty(ServerConstants.SCHEMA_PROPERTY_FILENAME,
          (String)null);
      ocWithoutFileDefinition = oc.toString();
    }

    /**
     * {@inheritDoc}
     */
    public Type getType()
    {
      return Type.NEW_OBJECTCLASS;
    }

    /**
     * {@inheritDoc}
     */
    protected CommonSchemaElements getSchemaElement()
    {
      if (oc == null)
      {
        oc = getObjectClass();
      }
      return oc;
    }

    /**
     * {@inheritDoc}
     */
    public Message getTaskDescription()
    {
      return INFO_CTRL_PANEL_NEW_OBJECTCLASS_TASK_DESCRIPTION.get(ocName);
    }

    /**
     * {@inheritDoc}
     */
    protected String getSchemaFileAttributeName()
    {
      return "objectClasses";
    }

    /**
     * {@inheritDoc}
     */
    protected String getSchemaFileAttributeValue()
    {
      if (isServerRunning())
      {
        return ocDefinition;
      }
      else
      {
        return ocWithoutFileDefinition;
      }
    }

    /**
     * {@inheritDoc}
     */
    protected void updateSchema() throws OpenDsException
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          printEquivalentCommandToAdd();
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressWithPoints(
                  INFO_CTRL_PANEL_CREATING_OBJECTCLASS_PROGRESS.get(ocName),
                  ColorAndFontConstants.progressFont));
        }
      });

      if (isServerRunning())
      {
        try
        {
          BasicAttribute attr =
            new BasicAttribute(getSchemaFileAttributeName());
          attr.add(getSchemaFileAttributeValue());
          ModificationItem mod = new ModificationItem(DirContext.ADD_ATTRIBUTE,
              attr);
          getInfo().getDirContext().modifyAttributes(
              ConfigConstants.DN_DEFAULT_SCHEMA_ROOT,
              new ModificationItem[]  { mod });
        }
        catch (NamingException ne)
        {
          throw new OnlineUpdateException(
              ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(ne.toString()), ne);
        }
      }
      else
      {
        updateSchemaFile();
      }
      notifyConfigurationElementCreated(oc);
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressDone(ColorAndFontConstants.progressFont));
        }
      });
    }

    /**
     * Updates the contents of the schema file.
     * @throws OpenDsException if an error occurs updating the schema file.
     */
    private void updateSchemaFile() throws OpenDsException
    {
      if (isSchemaFileDefined)
      {
        LDIFExportConfig exportConfig =
          new LDIFExportConfig(schemaFile,
                               ExistingFileBehavior.OVERWRITE);
        LDIFReader reader = null;
        Entry schemaEntry = null;
        try
        {
          reader = new LDIFReader(new LDIFImportConfig(schemaFile));
          schemaEntry = reader.readEntry();

          Modification mod = new Modification(ModificationType.ADD,
              Attributes.create(getSchemaFileAttributeName().toLowerCase(),
                  getSchemaFileAttributeValue()));
          schemaEntry.applyModification(mod);
          LDIFWriter writer = new LDIFWriter(exportConfig);
          writer.writeEntry(schemaEntry);
          exportConfig.getWriter().newLine();
        }
        catch (Throwable t)
        {
        }
        finally
        {
          if (reader != null)
          {
            try
            {
              reader.close();
            }
            catch (Throwable t)
            {
            }
          }
          if (exportConfig != null)
          {
            try
            {
              exportConfig.close();
            }
            catch (Throwable t)
            {
            }
          }
        }
      }
      else
      {
        LDIFExportConfig exportConfig =
          new LDIFExportConfig(schemaFile,
                               ExistingFileBehavior.FAIL);
        try
        {
          ArrayList<String> lines = getSchemaEntryLines();
          for (String line : lines)
          {
            LDIFWriter.writeLDIFLine(new StringBuilder(line),
                exportConfig.getWriter(), exportConfig.getWrapColumn() > 1,
                exportConfig.getWrapColumn());
          }
          exportConfig.getWriter().newLine();
        }
        catch (Throwable t)
        {
          throw new OfflineUpdateException(
              ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(t.toString()), t);
        }
        finally
        {
          if (exportConfig != null)
          {
            try
            {
              exportConfig.close();
            }
            catch (Throwable t)
            {
            }
          }
        }
      }
    }
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
