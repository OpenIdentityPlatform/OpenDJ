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

package org.opends.guitools.controlpanel.event;

import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.text.JTextComponent;

import org.opends.quicksetup.util.ExtensionFileFilter;

/**
 * This is a class that automates the update of a text field with what the user
 * selects in a file chooser.  The class is not in charge of creating the
 * components or of updating the layout, it simply adds the required listeners
 * in the buttons and text fields so that a file chooser will be displayed
 * when the user clicks on the button and if the user chooses a file or a
 * directory the text field will be updated accordingly.
 *
 */
public class BrowseActionListener implements ActionListener
{
  private JFileChooser fc;

  private JTextComponent field;

  private Component parent;

  private BrowseType type;

  /**
   * Enumeration used to specify which kind of file browser dialog must be
   * displayed.
   *
   */
  public enum BrowseType
  {
    /**
     * The Browser is used to retrieve a directory.
     */
    LOCATION_DIRECTORY,
    /**
     * The Browser is used to retrieve an LDIF file.
     */
    OPEN_LDIF_FILE,
    /**
     * The Browser is used to retrieve a .zip file.
     */
    OPEN_ZIP_FILE,
    /**
     * The Browser is used to retrieve a generic file.
     */
    OPEN_GENERIC_FILE,
    /**
     * The Browser is used to create a generic file.
     */
    CREATE_GENERIC_FILE,
    /**
     * The Browser is used to create an LDIF file.
     */
    CREATE_LDIF_FILE,
    /**
     * The Browser is used to create a generic directory.
     */
    CREATE_DIRECTORY
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
      fc.setDialogTitle("Choose Directory");
      break;

    case CREATE_DIRECTORY:
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      fc.setDialogType(JFileChooser.SAVE_DIALOG);
      fc.setDialogTitle("Choose Directory");
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

    case CREATE_LDIF_FILE:
      fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
      fc.setDialogType(JFileChooser.SAVE_DIALOG);
      fc.setDialogTitle(INFO_OPEN_LDIF_FILE_DIALOG_TITLE.get().toString());
      ldifFiles = new ExtensionFileFilter("ldif",
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

    case OPEN_GENERIC_FILE:
      fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
      fc.setDialogType(JFileChooser.OPEN_DIALOG);
      fc.setDialogTitle(INFO_OPEN_GENERIC_FILE_DIALOG_TITLE.get().toString());

      break;

    case CREATE_GENERIC_FILE:
      fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
      fc.setDialogType(JFileChooser.SAVE_DIALOG);
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
   *
   */
  public void actionPerformed(ActionEvent e)
  {
    int returnVal;

    /* If we can get the current field parent directory set to it */
    String path = field.getText();
    if (path != null)
    {
      if (path.trim().length() > 0)
      {
        File f = new File(path);
        while ((f != null) && !f.isDirectory())
        {
          f = f.getParentFile();
        }
        if (f != null)
        {
          fc.setCurrentDirectory(f);
        }
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

    case OPEN_GENERIC_FILE:
      returnVal = fc.showOpenDialog(parent);
      break;
    case CREATE_GENERIC_FILE:
      returnVal = fc.showSaveDialog(parent);
      break;

    case CREATE_LDIF_FILE:
      returnVal = fc.showSaveDialog(parent);
      break;

    case CREATE_DIRECTORY:
      returnVal = fc.showSaveDialog(parent);
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
      fieldUpdated();
    }
  }

  /**
   * The method that is called after the text field is updated.
   *
   */
  protected void fieldUpdated()
  {
  }
}
