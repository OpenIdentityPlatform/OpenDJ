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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.opends.quicksetup.event;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.text.JTextComponent;

import org.opends.quicksetup.util.ExtensionFileFilter;
import static org.opends.messages.QuickSetupMessages.*;

/**
 * This class is an action listener used to update a text component. When the
 * class receives an ActionEvent it will display a File Browser Dialog and will
 * update the text field depending on what the user chooses in the browser
 * dialog.
 *
 * The class is used generally by adding it as ActionListener of a 'Browse'
 * button.
 */
public class BrowseActionListener implements ActionListener
{
  private JFileChooser fc;

  private JTextComponent field;

  private Component parent;

  private BrowseType type;

  /** Enumeration used to specify which kind of file browser dialog must be displayed. */
  public enum BrowseType
  {
    /** The Browser is used to retrieve a directory. */
    LOCATION_DIRECTORY,
    /** The Browser is used to retrieve an LDIF file. */
    OPEN_LDIF_FILE,
    /** The Browser is used to retrieve a .zip file. */
    OPEN_ZIP_FILE,
    /** The Browser is used to retrieve a generic file. */
    GENERIC_FILE
  }

  /**
   * Constructor for the BrowseActionListener.
   *
   * @param field
   *          the text component that will be updated when the user selects
   *          something in the file browser dialog.
   * @param type
   *          the type of file browse dialog that will be displayed.
   * @param parent
   *          component that will be used as reference to display the file
   *          browse dialog.
   */
  public BrowseActionListener(JTextComponent field, BrowseType type,
      Component parent)
  {
    this.field = field;
    this.type = type;
    this.parent = parent;

    fc = new JFileChooser();
    switch (type)
    {
    case LOCATION_DIRECTORY:
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      fc.setDialogType(JFileChooser.OPEN_DIALOG);
      fc.setDialogTitle(INFO_OPEN_SERVER_LOCATION_DIALOG_TITLE
              .get().toString());
      break;

    case OPEN_LDIF_FILE:
      fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
      fc.setDialogType(JFileChooser.OPEN_DIALOG);
      fc.setDialogTitle(INFO_OPEN_LDIF_FILE_DIALOG_TITLE.get().toString());
      ExtensionFileFilter ldifFiles =
          new ExtensionFileFilter("ldif",
              INFO_LDIF_FILES_DESCRIPTION.get().toString());

      fc.addChoosableFileFilter(ldifFiles);
      fc.setFileFilter(ldifFiles);
      break;

    case OPEN_ZIP_FILE:
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogType(JFileChooser.OPEN_DIALOG);
        fc.setDialogTitle(INFO_OPEN_ZIP_FILE_DIALOG_TITLE.get().toString());
        ExtensionFileFilter zipFiles =
            new ExtensionFileFilter("zip",
                INFO_ZIP_FILES_DESCRIPTION.get().toString());

        fc.addChoosableFileFilter(zipFiles);
        fc.setFileFilter(zipFiles);
        break;

    case GENERIC_FILE:
      fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
      fc.setDialogType(JFileChooser.OPEN_DIALOG);
      fc.setDialogTitle(INFO_OPEN_GENERIC_FILE_DIALOG_TITLE.get().toString());

      break;

    default:
      throw new IllegalArgumentException("Unknown BrowseType: " + type);
    }
  }

  /**
   * ActionListener implementation. It will display a file browser dialog and
   * then will update the text component if the user selects something on the
   * dialog.
   *
   * @param e the ActionEvent we receive.
   */
  @Override
  public void actionPerformed(ActionEvent e)
  {
    int returnVal;

    /* If we can get the current field parent directory set to it */
    String path = field.getText();
    if (path != null && path.trim().length() > 0)
    {
      File f = new File(path);
      while (f != null && !f.isDirectory())
      {
        f = f.getParentFile();
      }
      if (f != null)
      {
        fc.setCurrentDirectory(f);
      }
    }

    switch (type)
    {
    case LOCATION_DIRECTORY:
      returnVal = fc.showOpenDialog(parent);
      break;

    case OPEN_LDIF_FILE:
      returnVal = fc.showOpenDialog(parent);
      break;

    case OPEN_ZIP_FILE:
      returnVal = fc.showOpenDialog(parent);
      break;

    case GENERIC_FILE:
      returnVal = fc.showOpenDialog(parent);
      break;

    default:
      throw new IllegalStateException("Unknown type: " + type);
    }

    if (returnVal == JFileChooser.APPROVE_OPTION)
    {
      File file = fc.getSelectedFile();
      field.setText(file.getAbsolutePath());
      field.requestFocusInWindow();
      field.selectAll();
    }
  }
}
