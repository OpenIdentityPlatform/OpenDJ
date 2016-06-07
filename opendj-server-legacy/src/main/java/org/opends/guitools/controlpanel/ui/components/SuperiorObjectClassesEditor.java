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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui.components;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.guitools.controlpanel.event.SuperiorObjectClassesChangedEvent;
import org.opends.guitools.controlpanel.event.SuperiorObjectClassesChangedListener;
import org.opends.guitools.controlpanel.ui.GenericDialog;
import org.opends.guitools.controlpanel.ui.SelectObjectClassesPanel;
import org.opends.guitools.controlpanel.ui.renderer.SchemaElementComboBoxCellRenderer;
import org.opends.guitools.controlpanel.util.LowerCaseComparator;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.types.Schema;

/** A panel that can be used to select one (or several) object classes. */
public class SuperiorObjectClassesEditor extends JPanel
{
  private static final long serialVersionUID = 123123973933568L;

  private final Set<ObjectClass> toExclude = new HashSet<>();
  private final JComboBox<ObjectClass> singleSuperior = Utilities.createComboBox();
  private final JLabel multipleSuperiors = Utilities.createDefaultLabel();
  private final JButton bSpecifyMultiple = Utilities.createButton(
      INFO_CTRL_PANEL_SPECIFY_MULTIPLE_SUPERIORS_LABEL.get());
  private final JButton bUpdateMultiple = Utilities.createButton(
      INFO_CTRL_PANEL_UPDATE_MULTIPLE_SUPERIORS_LABEL.get());

  private SelectObjectClassesPanel superiorsPanel;
  private GenericDialog superiorsDialog;

  private static final String MULTIPLE = "Multiple";
  private static final String SINGLE = "Single";

  private final CardLayout cardLayout;

  private boolean isMultiple;

  private final Set<ObjectClass> selectedMultipleSuperiors = new HashSet<>();
  private final Set<SuperiorObjectClassesChangedListener> listeners = new HashSet<>();

  private Schema schema;

  /** Default constructor for this panel. */
  public SuperiorObjectClassesEditor()
  {
    super(new CardLayout());
    cardLayout = (CardLayout)getLayout();
    setOpaque(false);
    createLayout();
  }

  /** Creates the layout of this panel. */
  private void createLayout()
  {
    bSpecifyMultiple.setToolTipText(
        INFO_CTRL_PANEL_SPECIFY_MULTIPLE_SUPERIORS_TOOLTIP.get().toString());
    bSpecifyMultiple.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        specifyMultipleClicked();
      }
    });
    bUpdateMultiple.setToolTipText(
        INFO_CTRL_PANEL_UPDATE_MULTIPLE_SUPERIORS_TOOLTIP.get().toString());
    bUpdateMultiple.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        updateMultipleClicked();
      }
    });
    SchemaElementComboBoxCellRenderer renderer = new
    SchemaElementComboBoxCellRenderer(singleSuperior);
    singleSuperior.setModel(new DefaultComboBoxModel<ObjectClass>());
    singleSuperior.setRenderer(renderer);
    ItemListener itemListener = new ItemListener()
    {
      @Override
      public void itemStateChanged(ItemEvent ev)
      {
        notifyListeners();
      }
    };
    singleSuperior.addItemListener(itemListener);

    JPanel singlePanel = new JPanel(new GridBagLayout());
    singlePanel.setOpaque(false);
    JPanel multiplePanel = new JPanel(new GridBagLayout());
    multiplePanel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;
    singlePanel.add(singleSuperior, gbc);
    multiplePanel.add(multipleSuperiors, gbc);

    gbc.gridx ++;
    gbc.insets.left = 5;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.NONE;

    singlePanel.add(bSpecifyMultiple, gbc);
    multiplePanel.add(bUpdateMultiple, gbc);

    gbc.gridx ++;
    gbc.insets.left = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 0.1;
    singlePanel.add(Box.createHorizontalGlue(), gbc);
    multiplePanel.add(Box.createHorizontalGlue(), gbc);

    add(singlePanel, SINGLE);
    add(multiplePanel, MULTIPLE);

    Set<ObjectClass> empty = Collections.emptySet();
    setSelectedSuperiors(empty);
  }

  /**
   * Sets the list of object classes that this panel should not display
   * (mainly used to not display the object class for which we are editing
   * the superior object classes).
   * @param toExclude the list of object classes to exclude.
   */
  public void setObjectClassesToExclude(Set<ObjectClass> toExclude)
  {
    this.toExclude.clear();
    this.toExclude.addAll(toExclude);
    updateWithSchema(schema);
    if (superiorsPanel != null)
    {
      superiorsPanel.setObjectClassesToExclude(toExclude);
    }
  }

  /**
   * Returns the list of object classes that this panel will not display.
   * @return the list of object classes that this panel will not display.
   */
  public Set<ObjectClass> getObjectClassToExclude()
  {
    return Collections.unmodifiableSet(toExclude);
  }

  /**
   * Sets the list of superior object classes that must be displayed by
   * this panel.
   * @param objectClasses the list of superior object classes to be displayed.
   */
  public void setSelectedSuperiors(Set<ObjectClass> objectClasses)
  {
    isMultiple = objectClasses.size() > 1;
    if (isMultiple)
    {
      cardLayout.show(this, MULTIPLE);
      selectedMultipleSuperiors.clear();
      selectedMultipleSuperiors.addAll(objectClasses);
      updateMultipleSuperiorsLabel(selectedMultipleSuperiors);
    }
    else
    {
      if (objectClasses.size() == 1)
      {
        singleSuperior.setSelectedItem(objectClasses.iterator().next());
      }
      cardLayout.show(this, SINGLE);
    }
  }

  private void updateMultipleSuperiorsLabel(
      Set<ObjectClass> superiors)
  {
    SortedSet<String> orderedOcs = new TreeSet<>(new LowerCaseComparator());
    for (ObjectClass oc : superiors)
    {
      orderedOcs.add(oc.getNameOrOID());
    }
    String s = Utilities.getStringFromCollection(orderedOcs, ", ");
    multipleSuperiors.setText(s);
  }

  /**
   * Returns the list of superior object classes displayed by this panel.
   * @return the list of superior object classes displayed by this panel.
   */
  public Set<ObjectClass> getSelectedSuperiors()
  {
    if (isMultiple)
    {
      return Collections.unmodifiableSet(selectedMultipleSuperiors);
    }

    ObjectClass oc = (ObjectClass)singleSuperior.getSelectedItem();
    if (oc != null && !oc.isPlaceHolder())
    {
      return Collections.singleton(oc);
    }
    return Collections.emptySet();
  }

  /**
   * Sets the schema to be used by this panel.  This method assumes that it
   * is being called from the event thread.
   * @param schema the schema to be used by this panel.
   */
  public void setSchema(Schema schema)
  {
    updateWithSchema(schema);
    if (superiorsPanel != null)
    {
      superiorsPanel.setSchema(schema);
    }
  }

  private void updateWithSchema(Schema schema)
  {
    HashMap<String, ObjectClass> objectClassNameMap = new HashMap<>();
    for (ObjectClass oc : schema.getObjectClasses())
    {
      if (!toExclude.contains(oc))
      {
        objectClassNameMap.put(oc.getNameOrOID(), oc);
      }
    }
    SortedSet<String> orderedKeys = new TreeSet<>(new LowerCaseComparator());
    orderedKeys.addAll(objectClassNameMap.keySet());
    ArrayList<Object> newParents = new ArrayList<>();
    for (String key : orderedKeys)
    {
      newParents.add(objectClassNameMap.get(key));
    }
    Utilities.updateComboBoxModel(newParents, (DefaultComboBoxModel<ObjectClass>) singleSuperior.getModel());

    if (this.schema == null)
    {
      singleSuperior.setSelectedItem(CoreSchema.getTopObjectClass());
    }
    this.schema = schema;
  }

  /**
   * Adds a listener that will receive events when a change is made in the
   * displayed superior object classes.
   * @param listener the listener to be added.
   */
  public void addParentObjectClassesChangedListener(
      SuperiorObjectClassesChangedListener listener)
  {
    listeners.add(listener);
  }

  private void specifyMultipleClicked()
  {
    updateMultipleClicked();
  }

  private void updateMultipleClicked()
  {
     Set<ObjectClass> selectedObjectClasses = getSelectedSuperiors();

     // Display the panel with all the stuff.
     if (superiorsPanel == null)
     {
       superiorsPanel = new SelectObjectClassesPanel();
       superiorsPanel.setSchema(schema);
       if (!toExclude.isEmpty())
       {
         superiorsPanel.setObjectClassesToExclude(toExclude);
       }
       superiorsDialog = new GenericDialog(Utilities.getFrame(this),
           superiorsPanel);
       Utilities.centerGoldenMean(superiorsDialog,
           Utilities.getParentDialog(this));
       superiorsDialog.setModal(true);
       superiorsDialog.pack();
     }
     superiorsPanel.setSelectedObjectClasses(selectedObjectClasses);
     superiorsDialog.setVisible(true);
     if (!superiorsPanel.isCanceled())
     {
       setSelectedSuperiors(superiorsPanel.getSelectedObjectClasses());
       notifyListeners();
     }
  }

  private void notifyListeners()
  {
    SuperiorObjectClassesChangedEvent ev =
      new SuperiorObjectClassesChangedEvent(this, getSelectedSuperiors());
    for (SuperiorObjectClassesChangedListener listener : listeners)
    {
      listener.parentObjectClassesChanged(ev);
    }
  }
}
