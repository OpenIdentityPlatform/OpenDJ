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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;

import org.opends.guitools.controlpanel.datamodel.Action;
import org.opends.guitools.controlpanel.datamodel.Category;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.ActionButton;
import org.opends.guitools.controlpanel.ui.components.CategoryPanel;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * The panel on the left side of the main Control Center dialog.  It contains
 * all the actions on the pane divided in categories.
 *
 */
public class MainActionsPane extends StatusGenericPanel
{
  private static final long serialVersionUID = 7616418700758530191L;

  /**
   * Default constructor.
   *
   */
  public MainActionsPane()
  {
    super();

    setBackground(ColorAndFontConstants.greyBackground);
    GridBagConstraints gbc1 = new GridBagConstraints();
    gbc1.gridx = 0;
    gbc1.gridy = 0;
    gbc1.fill = GridBagConstraints.HORIZONTAL;
    gbc1.weightx = 1;
    ArrayList<Category> categories = createCategories();
    ButtonGroup group = new ButtonGroup();
    int maxWidth = 0;
    final Map<Action, GenericDialog> dialogs =
      new HashMap<Action, GenericDialog>();
    ArrayList<ActionButton> actions = new ArrayList<ActionButton>();
    for(Category category: categories)
    {
      JPanel categoryPanel = new JPanel(new GridBagLayout());
      GridBagConstraints gbc2 = new GridBagConstraints();
      gbc2.gridx = 0;
      gbc2.gridy = 0;
      gbc2.weightx = 1;
      gbc2.fill = GridBagConstraints.HORIZONTAL;
      for (Action action : category.getActions())
      {
        final ActionButton b = new ActionButton(action);
        actions.add(b);
        b.addActionListener(new ActionListener()
        {
          /**
           * {@inheritDoc}
           */
          public void actionPerformed(ActionEvent ev)
          {
            // Constructs the panels using reflection.
            Action action = b.getActionObject();
            GenericDialog dlg = dialogs.get(action);
            if (dlg == null)
            {
              Class<? extends StatusGenericPanel> panelClass =
                action.getAssociatedPanelClass();
              try
              {
                Constructor<? extends StatusGenericPanel> constructor =
                  panelClass.getDeclaredConstructor();
                StatusGenericPanel panel = constructor.newInstance();
                if (getInfo() != null)
                {
                  panel.setInfo(getInfo());
                }
                dlg = createDialog(panel);

                dialogs.put(action, dlg);
                Utilities.centerGoldenMean(dlg,
                    Utilities.getFrame(MainActionsPane.this));
              }
              catch (Throwable t)
              {
                // Bug
                t.printStackTrace();
              }
            }
            if (!dlg.isVisible())
            {
              dlg.setVisible(true);
            }
            else
            {
              dlg.toFront();
            }
          }
        });
        categoryPanel.add(b, gbc2);
        gbc2.gridy++;
        group.add(b);
        maxWidth = Math.max(maxWidth, b.getPreferredSize().width);
      }
      CategoryPanel p = new CategoryPanel(categoryPanel, category);
      maxWidth = Math.max(maxWidth, p.getPreferredSize().width);
      p.setExpanded(false);
      add(p, gbc1);
      gbc1.gridy++;

      if (category.getName().equals(
          INFO_CTRL_PANEL_CATEGORY_DIRECTORY_DATA.get()))
      {
        p.setExpanded(true);
      }
    }
    add(Box.createHorizontalStrut(maxWidth), gbc1);
    gbc1.gridy ++;
    gbc1.weighty = 1.0;
    add(Box.createVerticalGlue(), gbc1);
    createActionButtonListeners(actions);
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return null;
  }

  /**
   * Creates the dialog to be displayed using the provided panel.
   * @param panel the panel that will be contained in the dialog.
   * @return the dialog to be displayed using the provided panel.
   */
  protected GenericDialog createDialog(StatusGenericPanel panel)
  {
    return new GenericDialog(
        Utilities.getFrame(MainActionsPane.this),
        panel);
  }

  /**
   * Creates the categories contained by this panel.
   * @return the categories contained by this panel.
   */
  protected ArrayList<Category> createCategories()
  {
    ArrayList<Category> categories = new ArrayList<Category>();
    Message[][] labels;
    if (Utilities.isWindows())
    {
      labels = new Message[][] {
          {
            INFO_CTRL_PANEL_CATEGORY_DIRECTORY_DATA.get(),
            INFO_CTRL_PANEL_ACTION_MANAGE_ENTRIES.get(),
            INFO_CTRL_PANEL_ACTION_NEW_BASEDN.get(),
            INFO_CTRL_PANEL_ACTION_IMPORT_LDIF.get(),
            INFO_CTRL_PANEL_ACTION_EXPORT_LDIF.get(),
            INFO_CTRL_PANEL_ACTION_BACKUP.get(),
            INFO_CTRL_PANEL_ACTION_RESTORE.get()
          },
          {
          INFO_CTRL_PANEL_CATEGORY_SCHEMA.get(),
          INFO_CTRL_PANEL_ACTION_MANAGE_SCHEMA.get()
          },
          {
          INFO_CTRL_PANEL_CATEGORY_INDEXES.get(),
          INFO_CTRL_PANEL_ACTION_MANAGE_INDEXES.get(),
          INFO_CTRL_PANEL_ACTION_VERIFY_INDEXES.get(),
          INFO_CTRL_PANEL_ACTION_REBUILD_INDEXES.get()
          },
          {
          INFO_CTRL_PANEL_CATEGORY_RUNTIME_OPTIONS.get(),
          INFO_CTRL_PANEL_ACTION_JAVA_SETTINGS.get(),
          INFO_CTRL_PANEL_ACTION_WINDOWS_SERVICE.get()
          }
      };
    }
    else
    {
      labels = new Message[][] {
          {
            INFO_CTRL_PANEL_CATEGORY_DIRECTORY_DATA.get(),
            INFO_CTRL_PANEL_ACTION_MANAGE_ENTRIES.get(),
            INFO_CTRL_PANEL_ACTION_NEW_BASEDN.get(),
            INFO_CTRL_PANEL_ACTION_IMPORT_LDIF.get(),
            INFO_CTRL_PANEL_ACTION_EXPORT_LDIF.get(),
            INFO_CTRL_PANEL_ACTION_BACKUP.get(),
            INFO_CTRL_PANEL_ACTION_RESTORE.get()
          },
          {
          INFO_CTRL_PANEL_CATEGORY_SCHEMA.get(),
          INFO_CTRL_PANEL_ACTION_MANAGE_SCHEMA.get()
          },
          {
          INFO_CTRL_PANEL_CATEGORY_INDEXES.get(),
          INFO_CTRL_PANEL_ACTION_MANAGE_INDEXES.get(),
          INFO_CTRL_PANEL_ACTION_VERIFY_INDEXES.get(),
          INFO_CTRL_PANEL_ACTION_REBUILD_INDEXES.get()
          },
          {
          INFO_CTRL_PANEL_CATEGORY_RUNTIME_OPTIONS.get(),
          INFO_CTRL_PANEL_ACTION_JAVA_SETTINGS.get()
          }
      };
    }
    ArrayList<Class<? extends StatusGenericPanel>> classes =
      new ArrayList<Class<? extends StatusGenericPanel>>();
    classes.add(BrowseEntriesPanel.class);
    classes.add(NewBaseDNPanel.class);
    classes.add(ImportLDIFPanel.class);
    classes.add(ExportLDIFPanel.class);
    classes.add(BackupPanel.class);
    classes.add(RestorePanel.class);
    classes.add(BrowseSchemaPanel.class);
    classes.add(BrowseIndexPanel.class);
    classes.add(VerifyIndexPanel.class);
    classes.add(RebuildIndexPanel.class);
    classes.add(JavaPropertiesPanel.class);
    if (Utilities.isWindows())
    {
      classes.add(WindowsServicePanel.class);
    }
    int classIndex = 0;
    for (int i=0; i<labels.length; i++)
    {
      Category category = new Category();
      category.setName(labels[i][0]);
      for (int j=1; j<labels[i].length; j++)
      {
        Action action = new Action();
        action.setName(labels[i][j]);
        action.setAssociatedPanel(classes.get(classIndex));
        classIndex ++;

        category.getActions().add(action);

      }
      categories.add(category);
    }
    return categories;
  }

  /**
   * This is required because in some desktops we might encounter a case
   * where several actions are highlighted.
   * @param actions the actions
   */
  private void createActionButtonListeners(
      final Collection<ActionButton> actions)
  {
    ActionListener actionListener = new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        for (ActionButton button : actions)
        {
          if (ev.getSource() == button)
          {
            button.actionPerformed(ev);
            break;
          }
        }
      }
    };

    MouseAdapter mouseListener = new MouseAdapter()
    {
      /**
       * {@inheritDoc}
       */
      public void mousePressed(MouseEvent ev)
      {
        for (ActionButton button : actions)
        {
          if (ev.getSource() == button)
          {
            button.mousePressed(ev);
            break;
          }
        }
      }

      /**
       * {@inheritDoc}
       */
      public void mouseReleased(MouseEvent ev)
      {
        for (ActionButton button : actions)
        {
          if (ev.getSource() == button)
          {
            button.mouseReleased(ev);
            break;
          }
        }
      }

      /**
       * {@inheritDoc}
       */
      public void mouseExited(MouseEvent ev)
      {
        for (ActionButton button : actions)
        {
          if (ev.getSource() == button)
          {
            button.mouseExited(ev);
            break;
          }
        }
      }

      /**
       * {@inheritDoc}
       */
      public void mouseEntered(MouseEvent ev)
      {
        for (ActionButton button : actions)
        {
          if (ev.getSource() == button)
          {
            button.mouseEntered(ev);
          }
          else
          {
            button.mouseExited(ev);
          }
        }
      }
    };

    for (ActionButton button : actions)
    {
      button.addActionListener(actionListener);
      button.addMouseListener(mouseListener);
    }
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return null;
  }


  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
  }
}
