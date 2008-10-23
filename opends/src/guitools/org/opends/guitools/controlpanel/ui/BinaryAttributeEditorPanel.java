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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;

import org.opends.guitools.controlpanel.datamodel.BinaryValue;
import org.opends.guitools.controlpanel.event.BrowseActionListener;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.Schema;

/**
 * Panel that is displayed in the dialog where the user can specify the value
 * of a binary attribute.
 *
 */
public class BinaryAttributeEditorPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -877248486446244170L;
  private JRadioButton useFile;
  private JRadioButton useBase64;
  private JTextField file;
  private JButton browse;
  private JLabel lFile;
  private JTextField base64;
  private JLabel imagePreview;
  private JButton refreshButton;
  private JLabel lImage = Utilities.createDefaultLabel();
  private JLabel attrName;

  private BinaryValue value;

  private boolean valueChanged;

  private final static int MAX_IMAGE_HEIGHT = 300;
  private final static int MAX_BASE64_TO_DISPLAY = 3 * 1024;

  private static final Logger LOG =
    Logger.getLogger(BinaryAttributeEditorPanel.class.getName());

  /**
   * Default constructor.
   *
   */
  public BinaryAttributeEditorPanel()
  {
    super();
    createLayout();
  }

  /**
   * Sets the value to be displayed in the panel.
   * @param attrName the attribute name.
   * @param value the binary value.
   */
  public void setValue(final String attrName,
      final BinaryValue value)
  {
    final boolean launchBackground = this.value != value;
//  Read the file or encode the base 64 content.
    BackgroundTask<Void> worker = new BackgroundTask<Void>()
    {
      /**
       * {@inheritDoc}
       */
      public Void processBackgroundTask() throws Throwable
      {
        try
        {
          Thread.sleep(1000);
        }
        catch (Throwable t)
        {
        }
        valueChanged = false;
        BinaryAttributeEditorPanel.this.attrName.setText(attrName);
        if (hasImageSyntax(attrName))
        {
          if (value != null)
          {
            BinaryAttributeEditorPanel.updateImage(lImage, value.getBytes());
          }
          else
          {
            lImage.setIcon(null);
            lImage.setText(
                INFO_CTRL_PANEL_NO_VALUE_SPECIFIED.get().toString());
          }
          setImageVisible(true);
          useFile.setSelected(true);
          base64.setText("");
        }
        else
        {
          lImage.setIcon(null);
          lImage.setText("");
          setImageVisible(false);

          if (value != null)
          {
            BinaryAttributeEditorPanel.updateBase64(base64, value.getBytes());
          }
        }

        if (value != null)
        {
          if (value.getType() == BinaryValue.Type.BASE64_STRING)
          {
            file.setText("");
          }
          else
          {
            file.setText(value.getFile().getAbsolutePath());
            useFile.setSelected(true);
          }
        }
        else
        {
          base64.setText("");
          file.setText("");
          useFile.setSelected(true);
        }

        BinaryAttributeEditorPanel.this.value = value;

        return null;
      }

      /**
       * {@inheritDoc}
       */
      public void backgroundTaskCompleted(Void returnValue,
          Throwable t)
      {
        setPrimaryValid(useFile);
        setPrimaryValid(useBase64);
        BinaryAttributeEditorPanel.this.attrName.setText(attrName);
        setEnabledOK(true);
        displayMainPanel();
        updateEnabling();
        packParentDialog();
        if (t != null)
        {
          LOG.log(Level.WARNING, "Error reading binary contents: "+t, t);
        }
      }
    };
    if (launchBackground)
    {
      setEnabledOK(false);
      displayMessage(INFO_CTRL_PANEL_READING_SUMMARY.get());
      worker.startBackgroundTask();
    }
    else
    {
      setPrimaryValid(lFile);
      setPrimaryValid(useFile);
      setPrimaryValid(useBase64);
      BinaryAttributeEditorPanel.this.attrName.setText(attrName);
      setEnabledOK(true);
      boolean isImage = hasImageSyntax(attrName);
      setImageVisible(isImage);
      if (value == null)
      {
        if (isImage)
        {
          useFile.setSelected(true);
        }
        else
        {
          useBase64.setSelected(true);
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return file;
  }

  /**
   * {@inheritDoc}
   */
  public void cancelClicked()
  {
    valueChanged = false;
    super.cancelClicked();
  }

  /**
   * Returns the binary value displayed in the panel.
   * @return the binary value displayed in the panel.
   */
  public BinaryValue getBinaryValue()
  {
    return value;
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    refresh(true, false);
  }

  /**
   * Refresh the contents in the panel.
   * @param closeAndUpdateValue whether the dialog must be closed and the value
   * updated at the end of the method or not.
   * @param updateImage whether the displayed image must be updated or not.
   */
  private void refresh(final boolean closeAndUpdateValue,
      final boolean updateImage)
  {
    final ArrayList<Message> errors = new ArrayList<Message>();

    setPrimaryValid(useFile);
    setPrimaryValid(useBase64);

    final BinaryValue oldValue = value;

    if (closeAndUpdateValue)
    {
      value = null;
    }

    if (useFile.isSelected())
    {
      String f = file.getText();
      if (f.trim().length() == 0)
      {
        if (hasImageSyntax(attrName.getText()) && (oldValue != null) &&
            !updateImage)
        {
          // Do nothing.  We do not want to regenerate the image and we
          // are on the case where the user simply did not change the image.
        }
        else
        {
          errors.add(ERR_CTRL_PANEL_FILE_NOT_PROVIDED.get());
          setPrimaryInvalid(useFile);
          setPrimaryInvalid(lFile);
        }
      }
      else
      {
        File theFile = new File(f);
        if (!theFile.exists())
        {
          errors.add(ERR_CTRL_PANEL_FILE_DOES_NOT_EXIST.get(f));
          setPrimaryInvalid(useFile);
          setPrimaryInvalid(lFile);
        }
        else if (theFile.isDirectory())
        {
          errors.add(ERR_CTRL_PANEL_PATH_IS_A_DIRECTORY.get(f));
          setPrimaryInvalid(useFile);
          setPrimaryInvalid(lFile);
        }
        else if (!theFile.canRead())
        {
          errors.add(ERR_CTRL_PANEL_CANNOT_READ_FILE.get(f));
          setPrimaryInvalid(useFile);
          setPrimaryInvalid(lFile);
        }
      }
    }
    else
    {
      String b = base64.getText();
      if (b.length() == 0)
      {
        errors.add(ERR_CTRL_PANEL_VALUE_IN_BASE_64_REQUIRED.get());
        setPrimaryInvalid(useBase64);
      }
    }
    if (errors.size() == 0)
    {
      // Read the file or encode the base 64 content.
      BackgroundTask<BinaryValue> worker = new BackgroundTask<BinaryValue>()
      {
        /**
         * {@inheritDoc}
         */
        public BinaryValue processBackgroundTask() throws Throwable
        {
          try
          {
            Thread.sleep(1000);
          }
          catch (Throwable t)
          {
          }
          BinaryValue returnValue;
          if (useBase64.isSelected())
          {
            returnValue = BinaryValue.createBase64(base64.getText());
          }
          else if (file.getText().trim().length() > 0)
          {
            File f = new File(file.getText());
            FileInputStream in = null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] bytes = new byte[2 * 1024];
            try
            {
              in = new FileInputStream(f);
              boolean done = false;
              while (!done)
              {
                int len = in.read(bytes);
                if (len == -1)
                {
                  done = true;
                }
                else
                {
                  out.write(bytes, 0, len);
                }
              }
              returnValue = BinaryValue.createFromFile(out.toByteArray(), f);
            }
            finally
            {
              if (in != null)
              {
                in.close();
              }
              out.close();
            }
          }
          else
          {
            //  We do not want to regenerate the image and we
            // are on the case where the user simply did not change the image.
            returnValue = oldValue;
          }
          if (closeAndUpdateValue)
          {
            valueChanged = !returnValue.equals(oldValue);
          }
          if (updateImage)
          {
            updateImage(lImage, returnValue.getBytes());
          }
          return returnValue;
        }
        /**
         * {@inheritDoc}
         */
        public void backgroundTaskCompleted(BinaryValue returnValue,
            Throwable t)
        {
          setEnabledOK(true);
          displayMainPanel();
          if (closeAndUpdateValue)
          {
            value = returnValue;
          }
          else
          {
            packParentDialog();
          }
          if (t != null)
          {
            if (useFile.isSelected())
            {
              errors.add(ERR_CTRL_PANEL_ERROR_READING_FILE.get(t.toString()));
            }
            else
            {
              errors.add(
                  ERR_CTRL_PANEL_ERROR_DECODING_BASE_64.get(t.toString()));
            }
            displayErrorDialog(errors);
          }
          else
          {
            if (closeAndUpdateValue)
            {
              Utilities.getParentDialog(BinaryAttributeEditorPanel.this).
              setVisible(false);
            }
          }
        }
      };
      setEnabledOK(false);
      displayMessage(INFO_CTRL_PANEL_READING_SUMMARY.get());
      worker.startBackgroundTask();
    }
    else
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_EDIT_BINARY_ATTRIBUTE_TITLE.get();
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
  public boolean valueChanged()
  {
    return valueChanged;
  }

  /**
   * {@inheritDoc}
   */
  public boolean requiresScroll()
  {
    return true;
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
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;

    gbc.gridwidth = 1;
    JLabel l = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_ATTRIBUTE_NAME_LABEL.get());
    add(l, gbc);
    gbc.gridx ++;
    gbc.insets.left = 10;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    attrName = Utilities.createDefaultLabel();
    gbc.gridwidth = 2;
    add(attrName, gbc);

    gbc.insets.top = 10;
    gbc.insets.left = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    useFile = Utilities.createRadioButton(
        INFO_CTRL_PANEL_USE_CONTENTS_OF_FILE.get());
    lFile = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_USE_CONTENTS_OF_FILE.get());
    useFile.setFont(ColorAndFontConstants.primaryFont);
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.gridwidth = 1;
    add(useFile, gbc);
    add(lFile, gbc);
    gbc.gridx ++;
    file = Utilities.createLongTextField();
    gbc.weightx = 1.0;
    gbc.insets.left = 10;
    add(file, gbc);
    gbc.gridx ++;
    gbc.weightx = 0.0;
    browse = Utilities.createButton(INFO_CTRL_PANEL_BROWSE_BUTTON_LABEL.get());
    browse.addActionListener(
        new CustomBrowseActionListener(file,
            BrowseActionListener.BrowseType.OPEN_GENERIC_FILE,  this));
    browse.setOpaque(false);
    add(browse, gbc);
    gbc.gridy ++;
    gbc.gridx = 0;
    gbc.insets.left = 0;
    gbc.gridwidth = 3;
    useBase64 = Utilities.createRadioButton(
        INFO_CTRL_PANEL_USE_CONTENTS_IN_BASE_64.get());
    useBase64.setFont(ColorAndFontConstants.primaryFont);
    add(useBase64, gbc);

    gbc.gridy ++;
    gbc.insets.left = 30;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    base64 = Utilities.createLongTextField();
    add(base64, gbc);

    imagePreview =
      Utilities.createPrimaryLabel(INFO_CTRL_PANEL_IMAGE_PREVIEW_LABEL.get());
    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    add(imagePreview, gbc);

    refreshButton = Utilities.createButton(
        INFO_CTRL_PANEL_REFRESH_BUTTON_LABEL.get());
    gbc.gridx ++;
    gbc.insets.left = 5;
    gbc.fill = GridBagConstraints.NONE;
    add(refreshButton, gbc);
    gbc.insets.left = 0;
    gbc.weightx = 1.0;
    add(Box.createHorizontalGlue(), gbc);
    refreshButton.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        refreshButtonClicked();
      }
    });

    gbc.gridy ++;
    gbc.gridwidth = 3;
    gbc.insets.top = 5;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    add(lImage, gbc);

    addBottomGlue(gbc);
    ButtonGroup group = new ButtonGroup();
    group.add(useFile);
    group.add(useBase64);

    ActionListener listener = new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        updateEnabling();
      }
    };
    useFile.addActionListener(listener);
    useBase64.addActionListener(listener);
  }

  /**
   * Updates the enabling state of all the components in the panel.
   *
   */
  private void updateEnabling()
  {
    base64.setEnabled(useBase64.isSelected());
    file.setEnabled(useFile.isSelected());
    browse.setEnabled(useFile.isSelected());
    refreshButton.setEnabled(useFile.isSelected());
  }

  /**
   * Updates the provided component with the base 64 representation of the
   * provided binary array.
   * @param base64 the text component to be updated.
   * @param bytes the byte array.
   */
  static void updateBase64(JTextComponent base64, byte[] bytes)
  {
    if (bytes.length < MAX_BASE64_TO_DISPLAY)
    {
      BinaryValue value = BinaryValue.createBase64(bytes);
      base64.setText(value.getBase64());
    }
    else
    {
      base64.setText(
          INFO_CTRL_PANEL_SPECIFY_CONTENTS_IN_BASE_64.get().toString());
    }
  }

  /**
   * Updates a label, by displaying the image in the provided byte array.
   * @param lImage the label to be updated.
   * @param bytes the array of bytes containing the image.
   */
  static void updateImage(JLabel lImage, byte[] bytes)
  {
    Icon icon = Utilities.createImageIcon(bytes,
        BinaryAttributeEditorPanel.MAX_IMAGE_HEIGHT,
        INFO_CTRL_PANEL_IMAGE_OF_ATTRIBUTE_LABEL.get(), false);
    if (icon.getIconHeight() > 0)
    {
      lImage.setIcon(icon);
      lImage.setText("");
    }
    else
    {
      Utilities.setWarningLabel(lImage,
          INFO_CTRL_PANEL_PREVIEW_NOT_AVAILABLE_LABEL.get());
    }
  }

  /**
   * Updates the visibility of the components depending on whether the image
   * must be made visible or not.
   * @param visible whether the image must be visible or not.
   */
  private void setImageVisible(boolean visible)
  {
    imagePreview.setVisible(visible);
    refreshButton.setVisible(visible);
    lFile.setVisible(visible);
    useFile.setVisible(!visible);
    useBase64.setVisible(!visible);
    base64.setVisible(!visible);
    lImage.setVisible(visible);
  }

  /**
   * Class used to refresh automatically the contents in the panel after the
   * user provides a path value through the JFileChooser associated with the
   * browse button.
   *
   */
  class CustomBrowseActionListener extends BrowseActionListener
  {
    /**
     * Constructor of this listener.
     * @param field the text field.
     * @param type the type of browsing (file, directory, etc.)
     * @param parent the parent component to be used as reference to display
     * the file chooser dialog.
     */
    public CustomBrowseActionListener(JTextComponent field, BrowseType type,
        Component parent)
    {
      super(field, type, parent);
    }

    /**
     * {@inheritDoc}
     */
    protected void fieldUpdated()
    {
      super.fieldUpdated();
      if (refreshButton.isVisible())
      {
        // The file field is updated, if refreshButton is visible it means
        // that we can have a preview.
        refreshButtonClicked();
      }
    }
  }

  /**
   * Called when the refresh button is clicked by the user.
   *
   */
  private void refreshButtonClicked()
  {
    refresh(false, true);
  }

  /**
   * Returns <CODE>true</CODE> if the attribute has an image syntax and
   * <CODE>false</CODE> otherwise.
   * @param attrName the attribute name.
   * @return <CODE>true</CODE> if the attribute has an image syntax and
   * <CODE>false</CODE> otherwise.
   */
  private boolean hasImageSyntax(String attrName)
  {
    Schema schema = getInfo().getServerDescriptor().getSchema();
    return Utilities.hasImageSyntax(attrName, schema);
  }
}
