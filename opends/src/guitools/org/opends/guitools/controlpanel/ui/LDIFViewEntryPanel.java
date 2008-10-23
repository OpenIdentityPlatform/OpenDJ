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
import java.awt.Point;
import java.io.IOException;
import java.io.StringReader;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.TreePath;

import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.task.OfflineUpdateException;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.Base64;
import org.opends.server.util.LDIFReader;

/**
 * The panel displaying an LDIF view of an entry.
 *
 */
public class LDIFViewEntryPanel extends ViewEntryPanel
{
  private static final long serialVersionUID = 2775960608128921072L;
  private JScrollPane editableScroll;
  private JScrollPane readOnlyScroll;
  private JTextArea editableAttributes;
  private JTextArea readOnlyAttributes;
  private CustomSearchResult searchResult;

  /**
   * Default constructor.
   *
   */
  public LDIFViewEntryPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return editableAttributes;
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   *
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 1.0;

    addTitlePanel(this, gbc);

    gbc.gridy ++;
    gbc.insets.top = 10;

    editableAttributes = Utilities.createTextArea(Message.EMPTY, 20, 30);
    editableAttributes.getDocument().addDocumentListener(new DocumentListener()
    {
      public void insertUpdate(DocumentEvent ev)
      {
        notifyListeners();
      }

      public void changedUpdate(DocumentEvent ev)
      {
        notifyListeners();
      }

      public void removeUpdate(DocumentEvent ev)
      {
        notifyListeners();
      }
    });
    gbc.weighty = 0.6;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridy ++;
    editableScroll = Utilities.createScrollPane(editableAttributes);
    add(editableScroll, gbc);


    gbc.weighty = 0.0;
    gbc.insets.top = 10;
    JLabel lReadOnly = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_NON_EDITABLE_ATTRIBUTES.get());
    gbc.gridy ++;
    add(lReadOnly, gbc);
    gbc.insets.top = 5;
    readOnlyAttributes = Utilities.createNonEditableTextArea(Message.EMPTY, 10,
        30);
    gbc.weightx = 1.0;
    gbc.weighty = 0.4;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridy ++;
    readOnlyScroll = Utilities.createScrollPane(readOnlyAttributes);
    add(readOnlyScroll, gbc);
  }

  /**
   * {@inheritDoc}
   */
  public void update(CustomSearchResult sr, boolean isReadOnly, TreePath path)
  {
    boolean sameEntry = false;
    if ((searchResult != null) && (sr != null))
    {
      sameEntry = searchResult.getDN().equals(sr.getDN());
    }

    searchResult = sr;

    updateTitle(sr, path);

    StringBuilder sb = new StringBuilder();

    sb.append("dn: "+sr.getDN());

    if (isReadOnly)
    {
      editableScroll.setVisible(false);
      for (String attrName : sr.getAttributeNames())
      {
        Set<Object> values = sr.getAttributeValues(attrName);
        for (Object o : values)
        {
          sb.append("\n"+ getLDIFLine(attrName, o));
        }
      }
      final Point p1 = sameEntry ?
          readOnlyScroll.getViewport().getViewPosition() : new Point(0, 0);
      readOnlyAttributes.setText(sb.toString());
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          if ((p1 != null) && (readOnlyScroll.getViewport().contains(p1)))
          {
            readOnlyScroll.getViewport().setViewPosition(p1);
          }
        }
      });
    }
    else
    {
      editableScroll.setVisible(true);

      for (String attrName : sr.getAttributeNames())
      {
        if (!schemaReadOnlyAttributesLowerCase.contains(attrName.toLowerCase()))
        {
          Set<Object> values = sr.getAttributeValues(attrName);
          for (Object o : values)
          {
            sb.append("\n"+ getLDIFLine(attrName, o));
          }
        }
      }
      final Point p1 = sameEntry ?
          editableScroll.getViewport().getViewPosition() : new Point(0, 0);
      ignoreEntryChangeEvents = true;
      editableAttributes.setText(sb.toString());
      ignoreEntryChangeEvents = false;

      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          if ((p1 != null) && (editableScroll.getViewport().contains(p1)))
          {
            editableScroll.getViewport().setViewPosition(p1);
          }
        }
      });
      // Read-only attributes
      boolean oneLineAdded = false;
      sb = new StringBuilder();
      for (String attrName : schemaReadOnlyAttributes)
      {
        Set<Object> values = sr.getAttributeValues(attrName);
        for (Object o : values)
        {
          if (oneLineAdded)
          {
            sb.append("\n");
          }
          oneLineAdded = true;
          sb.append(getLDIFLine(attrName, o));
        }
      }
      final Point p2 = sameEntry ?
          readOnlyScroll.getViewport().getViewPosition() : new Point(0, 0);
      readOnlyAttributes.setText(sb.toString());
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          if ((p2 != null) && (readOnlyScroll.getViewport().contains(p2)))
          {
            readOnlyScroll.getViewport().setViewPosition(p2);
          }
        }
      });
    }
  }

  /**
   * {@inheritDoc}
   */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }


  /**
   * {@inheritDoc}
   */
  protected String getDisplayedDN()
  {
    String dn = null;
    // Do it fast, this is called to update the dn displayed in the title.
    String ldif = getLDIF();
    int index = ldif.toLowerCase().indexOf("dn: ");
    if (index != -1)
    {
      int index2 = ldif.indexOf("\n", index);
      if (index2 != -1)
      {
        dn = ldif.substring(index + 3, index2).trim();
      }
    }
    return dn;
  }

  /**
   * {@inheritDoc}
   */
  protected Set<Object> getValues(String attrName)
  {
    throw new IllegalStateException("This method should not be called.");
  }

  /**
   * {@inheritDoc}
   */
  public Entry getEntry() throws OpenDsException
  {
    Entry entry = null;
    LDIFImportConfig ldifImportConfig = null;
    try
    {
      String ldif = getLDIF();

      ldifImportConfig = new LDIFImportConfig(new StringReader(ldif));
      LDIFReader reader = new LDIFReader(ldifImportConfig);
      entry = reader.readEntry(checkSchema());
      addValuesInRDN(entry);
    }
    catch (IOException ioe)
    {
      throw new OfflineUpdateException(
          ERR_CTRL_PANEL_ERROR_CHECKING_ENTRY.get(ioe.toString()), ioe);
    }
    finally
    {
      if (ldifImportConfig != null)
      {
        ldifImportConfig.close();
      }
    }
    return entry;
  }

  /**
   * Returns the LDIF representation of the entry, only returns the editable
   * attributes.
   * @return the LDIF representation of the entry.
   */
  private String getLDIF()
  {
    StringBuilder sb = new StringBuilder();
    sb.append(editableAttributes.getText());

    return sb.toString();
  }

  /**
   * Returns the equivalent LDIF line for a given attribute and value.
   * @param attrName the attribute name.
   * @param o the value.
   * @return the equivalent LDIF line for the provided attribute and value.
   */
  private String getLDIFLine(String attrName, Object o)
  {
    String attrValue;
    if (o instanceof String)
    {
      attrValue = (String)o;
    }
    else if (o instanceof byte[])
    {
      attrValue = Base64.encode((byte[])o);
      // To indicate that is base64 encoded
      attrName = attrName+":";
    }
    else
    {
      attrValue = String.valueOf(o);
    }

    return attrName+": "+ attrValue;
  }
}
