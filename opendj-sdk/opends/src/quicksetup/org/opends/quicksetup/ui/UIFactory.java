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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

import org.opends.messages.Message;

import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.BevelBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;


/**
 * This class provides constants an methods to create Swing objects and to
 * generate UI elements with a common look and feel.
 *
 * When we want to change a color, a background or a font this is the class
 * that should be modified.
 *
 */
public class UIFactory
{
  private static boolean initialized = false;

  private static String parentPackagePath;

  private static final Logger LOG = Logger.getLogger(UIFactory.class.getName());

  /**
   * Specifies the horizontal insets between buttons.
   */
  public static final int HORIZONTAL_INSET_BETWEEN_BUTTONS = 5;

  /**
   * Specifies the horizontal inset for the control panel sub section.
   */
  public static final int HORIZONTAL_INSET_CONTROL_PANEL_SUBSECTION = 20;

  /**
   * Specifies the top inset for the steps.
   */
  public static final int TOP_INSET_STEP = 15;

  /**
   * Specifies the left inset for the steps.
   */
  public static final int LEFT_INSET_STEP = 5;

  /**
   * Specifies the extra left inset for the sub-steps.
   */
  public static final int LEFT_INSET_SUBSTEP = 20;
  /**
   * Specifies the top inset for the instructions sub panel.
   */
  public static final int TOP_INSET_INSTRUCTIONS_SUBPANEL = 5;

  /**
   * Specifies the top inset for input subpanel.
   */
  public static final int TOP_INSET_INPUT_SUBPANEL = 10;

  /**
   * Specifies the top inset for a primary field.
   */
  public static final int TOP_INSET_PRIMARY_FIELD = 10;

  /**
   * Specifies the top inset for a secondary field.
   */
  public static final int TOP_INSET_SECONDARY_FIELD = 5;

  /**
   * Specifies the top inset for a radio button.
   */
  public static final int TOP_INSET_RADIOBUTTON = 0;

  /**
   * Specifies the top inset for a radio button subordinate panel.
   */
  public static final int TOP_INSET_RADIO_SUBORDINATE = 0;

  /**
   * Specifies the top inset for the progress bar.
   */
  public static final int TOP_INSET_PROGRESS_BAR = 5;

  /**
   * Specifies the top inset for the progress text area.
   */
  public static final int TOP_INSET_PROGRESS_TEXTAREA = 4;

  /**
   * Specifies the top inset for the background image.
   */
  public static final int TOP_INSET_BACKGROUND = 70;

  /**
   * Specifies the top inset for the error message.
   */
  public static final int TOP_INSET_ERROR_MESSAGE = 10;

  /**
   * Specifies the top inset for the browse button.
   */
  public static final int TOP_INSET_BROWSE = 5;

  /**
   * Specifies the top inset for the control panel sub section.
   */
  public static final int TOP_INSET_CONTROL_PANEL_SUBSECTION = 30;

  /**
   * Specifies the right inset for background image.
   */
  public static final int RIGHT_INSET_BACKGROUND = 20;

  /**
   * Specifies the left inset for the primary field.
   */
  public static final int LEFT_INSET_PRIMARY_FIELD = 10;

  /**
   * Specifies the left inset for the browse button.
   */
  public static final int LEFT_INSET_BROWSE = 10;

  /**
   * Specifies the left inset for radio subordinate panel.
   */
  public static final int LEFT_INSET_RADIO_SUBORDINATE = 35;

  /**
   * Specifies the left inset for the secondary field.
   */
  public static final int LEFT_INSET_SECONDARY_FIELD = 5;

  /**
   * Specifies the left inset for the background image.
   */
  public static final int LEFT_INSET_BACKGROUND = 20;

  /**
   * Specifies the left inset for the copy url button.
   */
  public static final int LEFT_INSET_COPY_BUTTON = 10;

  /**
   * Specifies the left inset for a subordinate subpanel.
   */
  public static final int LEFT_INSET_SUBPANEL_SUBORDINATE = 30;

  /**
   * Specifies the left inset for the progress bar.
   */
  public static final int BOTTOM_INSET_PROGRESS_BAR = 10;

  /**
   * Specifies the bottom inset for the background image.
   */
  public static final int BOTTOM_INSET_BACKGROUND = 30;

  /**
   * Specifies the number of columns of a text field for a path.
   */
  public static final int PATH_FIELD_SIZE = 20;

  /**
   * Specifies the number of columns of a text field for a relative path.
   */
  public static final int RELATIVE_PATH_FIELD_SIZE = 10;

  /**
   * Specifies the number of columns of a text field for a host name.
   */
  public static final int HOST_FIELD_SIZE = 20;

  /**
   * Specifies the number of columns of a text field for a UID.
   */
  public static final int UID_FIELD_SIZE = 15;

  /**
   * Specifies the number of columns of a text field for a port.
   */
  public static final int PORT_FIELD_SIZE = 5;

  /**
   * Specifies the number of columns of a text field for a dn.
   */
  public static final int DN_FIELD_SIZE = 20;

  /**
   * Specifies the number of columns of a text field for a password.
   */
  public static final int PASSWORD_FIELD_SIZE = 15;

  /**
   * Specifies the number of columns of a text field for the number of entries.
   */
  public static final int NUMBER_ENTRIES_FIELD_SIZE = 7;

  /**
   * Specifies the number of points for the width of the progress bar.
   */
  public static final int PROGRESS_BAR_SIZE = 220;

  /**
   * Specifies the number of extra points that we add to the minimum size of
   * the dialog.
   */
  public static final int EXTRA_DIALOG_HEIGHT = 75;

  private static final Insets BUTTONS_PANEL_INSETS = new Insets(5, 0, 5, 10);

  private static final Insets STEPS_PANEL_INSETS = new Insets(15, 10, 5, 10);

  private static final Insets CURRENT_STEP_PANEL_INSETS =
      new Insets(15, 15, 15, 15);

  private static final Insets EMPTY_INSETS = new Insets(0, 0, 0, 0);

  /**
   * Specifies the default background color.
   */
  public static final Color DEFAULT_BACKGROUND =
          getColor(INFO_DEFAULT_BACKGROUND_COLOR.get());

  /**
   * Specifies the current step background color.
   */
  public static final Color CURRENT_STEP_PANEL_BACKGROUND =
          getColor(INFO_CURRENT_STEP_PANEL_BACKGROUND_COLOR.get());

  /**
   * Specifies the default label color.
   */
  public static final Color DEFAULT_LABEL_COLOR =
          getColor(INFO_DEFAULT_LABEL_COLOR.get());

  /**
   * Specifies the valid field color.
   */
  public static final Color FIELD_VALID_COLOR =
          getColor(INFO_FIELD_VALID_COLOR.get());

  /**
   * Specifies the invalid field color.
   */
  public static final Color FIELD_INVALID_COLOR =
          getColor(INFO_FIELD_INVALID_COLOR.get());

  /**
   * Specifies the read only text color.
   */
  public static final Color READ_ONLY_COLOR =
          getColor(INFO_READ_ONLY_COLOR.get());

  /**
   * Specifies the check box text color.
   */
  public static final Color CHECKBOX_COLOR =
          getColor(INFO_CHECKBOX_COLOR.get());

  /**
   * Specifies the progress text color.
   */
  public static final Color PROGRESS_COLOR =
          getColor(INFO_PROGRESS_COLOR.get());

  /**
   * Specifies the instructions text color.
   */
  public static final Color INSTRUCTIONS_COLOR =
          getColor(INFO_INSTRUCTIONS_COLOR.get());

  /**
   * Specifies the text field text color.
   */
  public static final Color TEXTFIELD_COLOR =
          getColor(INFO_TEXTFIELD_COLOR.get());

  /**
   * Specifies the password field text color.
   */
  public static final Color PASSWORDFIELD_COLOR =
          getColor(INFO_PASSWORDFIELD_COLOR.get());

  /**
   * Specifies the panel border color.
   */
  public static final Color PANEL_BORDER_COLOR =
          getColor(INFO_PANEL_BORDER_COLOR.get());

  /**
   * Specifies the current step panel border.
   */
  public static final Border CURRENT_STEP_PANEL_BORDER =
          BorderFactory.createMatteBorder(0, 2, 2, 0, PANEL_BORDER_COLOR);

  /**
   * Specifies the text area border.
   */
  public static final Border TEXT_AREA_BORDER =
          BorderFactory.createMatteBorder(1, 1, 1, 1,
                  getColor(INFO_TEXT_AREA_BORDER_COLOR.get()));

  /**
   * Specifies the dialog border.
   */
  public static final Border DIALOG_PANEL_BORDER =
    BorderFactory.createMatteBorder(0, 0, 2, 0, PANEL_BORDER_COLOR);

  /**
   * Specifies the font for the step which is not the current one in the steps
   * panel.
   */
  public static final Font NOT_CURRENT_STEP_FONT =
    Font.decode("Arial-PLAIN-14");

  /**
   * Specifies the font for the step which is the current one in the steps
   * panel.
   */
  public static final Font CURRENT_STEP_FONT =
    Font.decode("Arial-BOLD-14");

  /**
   * Specifies the font for the title of the current panel.
   */
  public static final Font TITLE_FONT =
    Font.decode("Arial-BOLD-14");

  /**
   * Specifies the font for the instructions of the current panel.
   */
  public static final Font INSTRUCTIONS_FONT =
    Font.decode("Arial-PLAIN-12");

  /**
   * Specifies the font for the instructions of the current panel.
   */
  public static final Font INSTRUCTIONS_MONOSPACE_FONT =
    Font.decode("Monospaced-PLAIN-14");

  /**
   * Specifies the font for the primary valid field.
   */
  public static final Font PRIMARY_FIELD_VALID_FONT =
    Font.decode("Arial-BOLD-12");

  /**
   * Specifies the font for the secondary valid field.
   */
  public static final Font SECONDARY_FIELD_VALID_FONT =
    Font.decode("Arial-PLAIN-12");

  /**
   * Specifies the font for the primary invalid field.
   */
  public static final Font PRIMARY_FIELD_INVALID_FONT =
    Font.decode("Arial-BOLDITALIC-12");

  /**
   * Specifies the font for the secondary invalid field.
   */
  public static final Font SECONDARY_FIELD_INVALID_FONT =
    Font.decode("Arial-ITALIC-12");

  /**
   * Specifies the font for the secondary invalid field.
   */
  public static final Font SECONDARY_STATUS_FONT =
    Font.decode("Arial-ITALIC-12");

  /**
   * Specifies the font for read only text.
   */
  public static final Font READ_ONLY_FONT = Font.decode("Arial-PLAIN-12");

  /**
   * Specifies the font for the check box text.
   */
  public static final Font CHECKBOX_FONT = Font.decode("Arial-PLAIN-12");

  /**
   * Specifies the font for the progress text.
   */
  public static final Font PROGRESS_FONT = Font.decode("Arial-PLAIN-12");

  /**
   * Specifies the font for the text field text.
   */
  public static final Font TEXTFIELD_FONT = Font.decode("Arial-PLAIN-12");

  /**
   * Specifies the font for the password field text.
   */
  public static final Font PASSWORD_FIELD_FONT =
    Font.decode("Arial-PLAIN-12");

  /**
   * Specifies the font for the points '....' in the progress panel.
   */
  public static final Font PROGRESS_POINTS_FONT =
    Font.decode("Arial-BOLD-12");

  /**
   * Specifies the font for the done text 'Done' in the progress panel.
   */
  public static final Font PROGRESS_DONE_FONT = PROGRESS_POINTS_FONT;

  /**
   * Specifies the font for the log messages in the progress panel.
   */
  public static final Font PROGRESS_LOG_FONT =
      Font.decode("Monospaced-PLAIN-12");

  /**
   * Specifies the font for the error log messages in the progress panel.
   */
  public static final Font PROGRESS_LOG_ERROR_FONT =
      Font.decode("Monospaced-PLAIN-12");

  /**
   * Specifies the font for the error messages in the progress panel.
   */
  public static final Font PROGRESS_ERROR_FONT =
    Font.decode("Arial-BOLD-12");

  /**
   * Specifies the font for the warning messages in the progress panel.
   */
  public static final Font PROGRESS_WARNING_FONT =
    Font.decode("Arial-BOLD-12");

  /**
   * Specifies the font for the stack trace in the progress panel.
   */
  public static final Font STACK_FONT = Font.decode("Arial-PLAIN-12");

  /**
   * Specifies the font for the text in the WebBrowserErrorDialog.
   */
  public static final Font ERROR_DIALOG_FONT =
      Font.decode("Arial-PLAIN-12");

  private static final String SPAN_CLOSE = "</span>";

  private static final String DIV_CLOSE = "</div>";

  private static final String DIV_OPEN_ERROR_BACKGROUND =
    "<div style=\"color:#"+
    INFO_DIV_OPEN_ERROR_BACKGROUND_1_COLOR.get()+
    ";background-color:#"+
    INFO_DIV_OPEN_ERROR_BACKGROUND_2_COLOR.get()+
    ";padding:10px 10px 10px 10px;"+
    "border-style:solid;border-width:3px;border-color:#"+
    INFO_DIV_OPEN_ERROR_BACKGROUND_3_COLOR.get()+
    ";vertical-align:middle;text-align:left\">";

  private static final String DIV_OPEN_WARNING_BACKGROUND =
      DIV_OPEN_ERROR_BACKGROUND;

  private static final String DIV_OPEN_SUCCESSFUL_BACKGROUND =
    "<div style=\"color:#"+
    INFO_DIV_OPEN_SUCCESSFUL_BACKGROUND_1_COLOR.get()+
    ";background-color:#"+
    INFO_DIV_OPEN_SUCCESSFUL_BACKGROUND_2_COLOR.get()+
    ";padding:10px 10px 10px 10px;"+
    "border-style:solid;border-width:3px;border-color:#"+
    INFO_DIV_OPEN_SUCCESSFUL_BACKGROUND_3_COLOR.get()+
    ";vertical-align:middle;text-align:left\">";

  /**
   * An HTML separator text that can be used in the progress panel.
   */
  public static final String HTML_SEPARATOR =
    "<div style=\"font-size:1px;background-color:#"+
    INFO_HTML_SEPARATOR_COLOR.get()+
    ";margin:10px 5px 10px 5px;\"></div>";

  private static final HashMap<IconType, ImageIcon> hmIcons =
      new HashMap<IconType, ImageIcon>();

  static {
    try
    {
      UIManager.put("OptionPane.background",
          getColor(INFO_OPTIONPANE_BACKGROUND_COLOR.get()));
      UIManager.put("Panel.background",
          getColor(INFO_PANEL_BACKGROUND_COLOR.get()));
      UIManager.put("ComboBox.background",
          getColor(INFO_COMBOBOX_BACKGROUND_COLOR.get()));
    }
    catch (Throwable t)
    {
      // This might occur when we do not get the display
      LOG.log(Level.WARNING, "Error updating UIManager: "+t, t);
    }
  }

  /**
   * The following enumeration contains the different icons that we can have.
   *
   */
  public enum IconType
  {
    /**
     * Splash Icon.
     */
    SPLASH,
    /**
     * Current Step Icon.
     */
    CURRENT_STEP,
    /**
     * The icon displayed by the OS when the dialog is minimized.
     */
    MINIMIZED,
    /**
     * The icon displayed by the Mac OS when the dialog is minimized.
     */
    MINIMIZED_MAC,
    /**
     * The background icon.
     */
    BACKGROUND,
    /**
     * The warning icon.
     */
    WARNING,
    /**
     * The warning large icon.
     */
    WARNING_LARGE,
    /**
     * The error icon.
     */
    ERROR,
    /**
     * The error large icon.
     */
    ERROR_LARGE,
    /**
     * The information icon.
     */
    INFORMATION,
    /**
     * The information large icon.
     */
    INFORMATION_LARGE,
    /**
     * Icon of OpenDS.
     */
    OPENDS_SMALL,
    /**
     * Icon to create subsection title in Status Panel.
     */
    SUBSECTION_LEFT,
    /**
     * Icon to create subsection title in Status Panel.
     */
    SUBSECTION_RIGHT,
    /**
     * Question icon.
     */
    HELP_SMALL,

    /**
     * Hourglass to display when the user must wait.
     */
    WAIT,

    /**
     * 8 x 8 Hourglass to display when the user must wait.
     */
    WAIT_TINY,

    /**
     * No icon.
     */
    NO_ICON
  }

  /**
   * The following enumeration contains the different text styles that we can
   * have.  A text style basically specifies the font and color to be used to
   * render the text.
   *
   */
  public enum TextStyle
  {
    /**
     * Current Step label style for the steps panel.
     */
    CURRENT_STEP,
    /**
     * Not current Step label style for the steps panel.
     */
    NOT_CURRENT_STEP,
    /**
     * Title label style for the current step panel.
     */
    TITLE,
    /**
     * Primary field valid label style for the current step panel.
     */
    PRIMARY_FIELD_VALID,
    /**
     * Primary field invalid text style for the current step panel.
     */
    PRIMARY_FIELD_INVALID,
    /**
     * Secondary field valid text style for the current step panel.
     */
    SECONDARY_FIELD_VALID,
    /**
     * Secondary field invalid text style for the current step panel.
     */
    SECONDARY_FIELD_INVALID,

    /**
     * Status messages that appear near components.
     */
    SECONDARY_STATUS,

    /**
     * Textfield text style for the current step panel.
     */
    TEXTFIELD,
    /**
     * Password text style for the current step panel.
     */
    PASSWORD_FIELD,

    /**
     * Read only text style for the current step panel.
     */
    READ_ONLY,
    /**
     * Check box text text style for the current step panel.
     */
    CHECKBOX,
    /**
     * Progress messages text style for the current step panel.
     */
    PROGRESS,
    /**
     * Text style for the instructions.
     */
    INSTRUCTIONS,
    /**
     * No text style.
     */
    NO_STYLE
  }

  /**
   * This method initialize the look and feel and UI settings.
   */
  public static void initialize()
  {
    if (!initialized)
    {
      System.setProperty("swing.aatext", "true");
      try
      {
        UIManager.setLookAndFeel(
            UIManager.getSystemLookAndFeelClassName());

      } catch (Exception ex)
      {
        ex.printStackTrace();
      }
      JFrame.setDefaultLookAndFeelDecorated(false);

      initialized = true;
    }
  }

  /**
   * Creates a new JPanel.
   * @return JPanel newly created
   */
  public static JPanel makeJPanel() {
    JPanel pnl = new JPanel();
    pnl.setOpaque(false);
    return pnl;
  }

  /**
   * Creates a JComboBox.
   * @return JComboBox a new combo box
   */
  static public JComboBox makeJComboBox() {
    JComboBox cbo = new JComboBox();
    cbo.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
    return cbo;
  }

  /**
   * Creates a JButton with the given label and tooltip.
   * @param label the text of the button.
   * @param tooltip the tooltip of the button.
   * @return a JButton with the given label and tooltip.
   */
  public static JButton makeJButton(Message label, Message tooltip)
  {
    JButton b = new JButton();

    if (label != null)
    {
      b.setText(label.toString());
    }

    if (tooltip != null)
    {
      b.setToolTipText(tooltip.toString());
    }

    b.setOpaque(false);

    return b;
  }

  /**
   * Commodity method that returns a JLabel based on a LabelFieldDescriptor.
   * @param desc the LabelFieldDescriptor describing the JLabel.
   * @return a JLabel based on a LabelFieldDescriptor.
   */
  static public JLabel makeJLabel(LabelFieldDescriptor desc)
  {
    UIFactory.TextStyle style;
    if (desc.getLabelType() == LabelFieldDescriptor.LabelType.PRIMARY)
    {
      style = UIFactory.TextStyle.PRIMARY_FIELD_VALID;
    } else
    {
      style = UIFactory.TextStyle.SECONDARY_FIELD_VALID;
    }
    return makeJLabel(UIFactory.IconType.NO_ICON, desc.getLabel(), style);
  }

  /**
   * Creates a JLabel with the given icon, text and text style.
   * @param iconName the icon.
   * @param text the label text.
   * @param style the text style.
   * @return a JLabel with the given icon, text and text style.
   */
  public static JLabel makeJLabel(IconType iconName, Message text,
      TextStyle style)
  {
    JLabel l = new JLabel();

    if (text != null)
    {
      l.setText(text.toString());
    }

    ImageIcon icon = getImageIcon(iconName);
    l.setIcon(icon);
    Message tooltip = getIconTooltip(iconName);

    if (tooltip != null)
    {
      l.setToolTipText(tooltip.toString());
    }

    setTextStyle(l, style);
    return l;
  }

  /**
   * Commodity method that returns a JTextComponent based on a
   * LabelFieldDescriptor.
   * @param desc the LabelFieldDescriptor describing the JTextField.
   * @param defaultValue the default value used to initialize the
   * JTextComponent.
   * @return a JTextComponent based on a
   * LabelFieldDescriptor.
   */
  static public JTextComponent makeJTextComponent(LabelFieldDescriptor desc,
      String defaultValue)
  {
    JTextComponent field;
    switch (desc.getType())
    {
    case TEXTFIELD:

      field =
          makeJTextField(Message.raw(defaultValue), desc.getTooltip(), desc
              .getSize(), TextStyle.TEXTFIELD);
      break;

    case PASSWORD:

      field =
          makeJPasswordField(Message.raw(defaultValue), desc.getTooltip(), desc
              .getSize(), TextStyle.PASSWORD_FIELD);
      break;

    case READ_ONLY:

      field =
          makeTextPane(Message.raw(defaultValue), TextStyle.READ_ONLY);
      break;

    default:
      throw new IllegalArgumentException("Unknown type: " + desc.getType());
    }
    return field;
  }

  /**
   * Creates a JTextField with the given icon, tooltip text, size and text
   * style.
   * @param text the text.
   * @param tooltip the tooltip text.
   * @param size the number of columns of the JTextField.
   * @param style the text style.
   * @return a JTextField with the given icon, tooltip text, size and text
   * style.
   */
  public static JTextField makeJTextField(Message text, Message tooltip,
      int size, TextStyle style)
  {
    JTextField f = new JTextField();
    updateTextFieldComponent(f, text, tooltip, size, style);
    f.addFocusListener(new TextFieldFocusListener(f));
    return f;
  }

  /**
   * Creates a JPasswordField with the given icon, tooltip text, size and text
   * style.
   * @param text the text.
   * @param tooltip the tooltip text.
   * @param size the number of columns of the JPasswordField.
   * @param style the text style.
   * @return a JPasswordField with the given icon, tooltip text, size and text
   * style.
   */
  public static JPasswordField makeJPasswordField(Message text, Message tooltip,
      int size, TextStyle style)
  {
    JPasswordField f = new JPasswordField();
    updateTextFieldComponent(f, text, tooltip, size, style);
    f.addFocusListener(new TextFieldFocusListener(f));
    return f;
  }

  /**
   * Creates a JRadioButton with the given text, tooltip text and text
   * style.
   * @param text the text of the radio button.
   * @param tooltip the tooltip text.
   * @param style the text style.
   * @return a JRadioButton with the given text, tooltip text and text
   * style.
   */
  public static JRadioButton makeJRadioButton(Message text, Message tooltip,
      TextStyle style)
  {
    JRadioButton rb = new JRadioButton();
    rb.setOpaque(false);
    if (text != null)
    {
      rb.setText(text.toString());
    }

    if (tooltip != null)
    {
      rb.setToolTipText(tooltip.toString());
    }

    setTextStyle(rb, style);
    return rb;
  }

  /**
   * Creates a JCheckBox with the given text, tooltip text and text
   * style.
   * @param text the text of the radio button.
   * @param tooltip the tooltip text.
   * @param style the text style.
   * @return a JCheckBox with the given text, tooltip text and text
   * style.
   */
  public static JCheckBox makeJCheckBox(Message text, Message tooltip,
      TextStyle style)
  {
    JCheckBox cb = new JCheckBox();
    cb.setOpaque(false);
    if (text != null)
    {
      cb.setText(text.toString());
    }

    if (tooltip != null)
    {
      cb.setToolTipText(tooltip.toString());
    }

    setTextStyle(cb, style);
    return cb;
  }

  /**
   * Creates a JList.
   *
   * @param textStyle the style to be used for the renderer.
   * @return a JList.
   */
  public static JList makeJList(TextStyle textStyle)
  {
    JList list = new JList();
    list.setCellRenderer(makeCellRenderer(textStyle));
    return list;
  }

  /**
   * Sets the specified text style to the component passed as parameter.
   * @param l the component to update.
   * @param style the text style to use.
   */
  public static void setTextStyle(JComponent l, TextStyle style)
  {
    switch (style)
    {
    case NOT_CURRENT_STEP:
      l.setFont(UIFactory.NOT_CURRENT_STEP_FONT);
      l.setForeground(DEFAULT_LABEL_COLOR);
      break;

    case CURRENT_STEP:
      l.setFont(UIFactory.CURRENT_STEP_FONT);
      l.setForeground(DEFAULT_LABEL_COLOR);
      break;

    case TITLE:
      l.setFont(UIFactory.TITLE_FONT);
      l.setForeground(DEFAULT_LABEL_COLOR);
      break;

    case PRIMARY_FIELD_VALID:
      l.setFont(UIFactory.PRIMARY_FIELD_VALID_FONT);
      l.setForeground(FIELD_VALID_COLOR);
      break;

    case PRIMARY_FIELD_INVALID:
      l.setFont(UIFactory.PRIMARY_FIELD_INVALID_FONT);
      l.setForeground(FIELD_INVALID_COLOR);
      break;

    case SECONDARY_FIELD_VALID:
      l.setFont(UIFactory.SECONDARY_FIELD_VALID_FONT);
      l.setForeground(FIELD_VALID_COLOR);
      break;

    case SECONDARY_FIELD_INVALID:
      l.setFont(UIFactory.SECONDARY_FIELD_INVALID_FONT);
      l.setForeground(FIELD_INVALID_COLOR);
      break;

    case SECONDARY_STATUS:
      l.setFont(UIFactory.SECONDARY_STATUS_FONT);
      l.setForeground(FIELD_VALID_COLOR);
      break;

    case READ_ONLY:
      l.setFont(UIFactory.READ_ONLY_FONT);
      l.setForeground(READ_ONLY_COLOR);
      break;

    case CHECKBOX:
      l.setFont(UIFactory.CHECKBOX_FONT);
      l.setForeground(CHECKBOX_COLOR);
      break;

    case PROGRESS:
      l.setFont(UIFactory.PROGRESS_FONT);
      l.setForeground(PROGRESS_COLOR);
      break;

    case INSTRUCTIONS:
      l.setFont(INSTRUCTIONS_FONT);
      l.setForeground(INSTRUCTIONS_COLOR);
      break;

    case TEXTFIELD:
      l.setFont(UIFactory.TEXTFIELD_FONT);
      l.setForeground(TEXTFIELD_COLOR);
      break;

    case PASSWORD_FIELD:
      l.setFont(UIFactory.PASSWORD_FIELD_FONT);
      l.setForeground(PASSWORDFIELD_COLOR);
      break;

    case NO_STYLE:
      // Do nothing
      break;

    default:
      throw new IllegalArgumentException("Unknown textStyle: " + style);
    }
  }

  /**
   * Returns the HTML string representing the provided IconType.
   * @param iconType the IconType for which we want the HTML representation.
   * @return the HTML string representing the provided IconType.
   */
  public static String getIconHtml(IconType iconType)
  {
    String url =
        String.valueOf(UIFactory.class.getClassLoader().getResource(
            getIconPath(iconType)));
    Message description = getIconDescription(iconType);
    Message title = getIconTooltip(iconType);
    return "<img src=\"" + url + "\" alt=\"" + description +
    "\" align=\"middle\" title=\"" + title + "\" >";
  }

  /**
   * Returns an ImageIcon object for the provided IconType.
   * @param iconType the IconType for which we want to obtain the ImageIcon.
   * @return the ImageIcon.
   */
  public static ImageIcon getImageIcon(IconType iconType)
  {
    if (iconType == null) {
      iconType = IconType.NO_ICON;
    }
    ImageIcon icon = hmIcons.get(iconType);
    if ((icon == null) && (iconType != IconType.NO_ICON))
    {
      String path = getIconPath(iconType);
      Message description = getIconDescription(iconType);
      try
      {
        Image im =
            Toolkit.getDefaultToolkit().createImage(
                UIFactory.class.getClassLoader().getResource(path));
        icon = new ImageIcon(im);
        String ds = description != null ? description.toString() : null;
        icon.setDescription(ds);

        hmIcons.put(iconType, icon);

      } catch (Exception ex)
      {
        ex.printStackTrace(); // A bug: this should not happen
        throw new IllegalStateException("Could not load icon for path " + path,
            ex);
      }
    }

    return icon;
  }

  /**
   * Returns a JEditorPane that works with the provided scroll.
   * @see ProgressJEditorPane
   * @param scroll the scroll that will contain the JEditorPane.
   * @return a JEditorPane that works with the provided scroll.
   */
  public static JEditorPane makeProgressPane(JScrollPane scroll)
  {
    return new ProgressJEditorPane(scroll);
  }

  /**
   * Returns a read only JEditorPane containing the provided text with the
   * provided font.  The JEditorPane will assume that the text is HTML text.
   * @param text the text to be used to initialize the JEditorPane contents.
   * @param font the font to be used.
   * @return a read only JEditorPane containing the provided text with the
   * provided font.
   */
  public static JEditorPane makeHtmlPane(Message text, Font font)
  {
    return makeHtmlPane(text, null, font);
  }

  /**
   * Returns a read only JEditorPane containing the provided text with the
   * provided font.  The JEditorPane will assume that the text is HTML text.
   * @param text the text to be used to initialize the JEditorPane contents.
   * @param ek HTMLEditor kit used for the new HTML pane
   * @param font the font to be used.
   * @return a read only JEditorPane containing the provided text with the
   * provided font.
   */
  public static JEditorPane makeHtmlPane(Message text, HTMLEditorKit ek,
                                         Font font)
  {
    JEditorPane pane = new JEditorPane();
    if (ek != null) pane.setEditorKit(ek);
    pane.setContentType("text/html");
    String s = text != null ? String.valueOf(text) : null;
    pane.setText(applyFontToHtmlWithDiv(s, font));
    pane.setEditable(false);
    pane.setBorder(new EmptyBorder(0, 0, 0, 0));
    return pane;
  }

  /**
   * Returns a read only JEditorPane containing the provided text with the
   * provided TextStyle.  The JEditorPane will assume that the text is plain
   * text.
   * @param text the text to be used to initialize the JEditorPane contents.
   * @param style the TextStyle to be used.
   * @return a read only JEditorPane containing the provided text with the
   * provided TextStyle.
   */
  public static JEditorPane makeTextPane(Message text, TextStyle style)
  {
    String s = text != null ? String.valueOf(text) : null;
    JEditorPane pane = new JEditorPane("text/plain", s);
    setTextStyle(pane, style);
    pane.setEditable(false);
    pane.setBorder(new EmptyBorder(0, 0, 0, 0));
    pane.setOpaque(false);
    return pane;
  }

  /**
   * Return empty insets.
   * @return empty insets.
   */
  public static Insets getEmptyInsets()
  {
    return (Insets) EMPTY_INSETS.clone();
  }

  /**
   * Returns the insets to be used for the button panel.
   * @return the insets to be used for the button panel.
   */
  public static Insets getButtonsPanelInsets()
  {
    return (Insets) BUTTONS_PANEL_INSETS.clone();
  }

  /**
   * Returns the insets to be used for the steps panel.
   * @return the insets to be used for the steps panel.
   */
  public static Insets getStepsPanelInsets()
  {
    return (Insets) STEPS_PANEL_INSETS.clone();
  }

  /**
   * Returns the insets to be used for the current step panel.
   * @return the insets to be used for the current step panel.
   */
  public static Insets getCurrentStepPanelInsets()
  {
    return (Insets) CURRENT_STEP_PANEL_INSETS.clone();
  }

  /**
   * Returns a String that contains the html passed as parameter with a span
   * applied.  The span style corresponds to the Font specified as parameter.
   * The goal of this method is to be able to specify a font for an HTML string.
   *
   * @param html the original html text.
   * @param font the font to be used to generate the new HTML.
   * @return a string that represents the original HTML with the font specified
   * as parameter.
   */
  public static String applyFontToHtml(String html, Font font)
  {
    StringBuilder buf = new StringBuilder();

    buf.append("<span style=\"").append(getFontStyle(font)).append("\">")
        .append(html).append(SPAN_CLOSE);

    return buf.toString();
  }


  /**
   * Returns a table created with the provided model and renderers.
   * @param tableModel the table model.
   * @param renderer the cell renderer.
   * @param headerRenderer the header renderer.
   * @return a table created with the provided model and renderers.
   */
  public static JTable makeSortableTable(final SortableTableModel tableModel,
      TableCellRenderer renderer,
      TableCellRenderer headerRenderer)
  {
    final JTable table = new JTable(tableModel);
    table.setShowGrid(true);
    table.setGridColor(UIFactory.PANEL_BORDER_COLOR);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    table.setBackground(UIFactory.CURRENT_STEP_PANEL_BACKGROUND);
    table.getTableHeader().setBackground(UIFactory.DEFAULT_BACKGROUND);
    table.setRowMargin(0);

    for (int i=0; i<tableModel.getColumnCount(); i++)
    {
      TableColumn col = table.getColumn(table.getColumnName(i));
      col.setCellRenderer(renderer);
      col.setHeaderRenderer(headerRenderer);
    }
    MouseAdapter listMouseListener = new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        TableColumnModel columnModel = table.getColumnModel();
        int viewColumn = columnModel.getColumnIndexAtX(e.getX());
        int sortedBy = table.convertColumnIndexToModel(viewColumn);
        if (e.getClickCount() == 1 && sortedBy != -1) {
          tableModel.setSortAscending(!tableModel.isSortAscending());
          tableModel.setSortColumn(sortedBy);
          tableModel.forceResort();
        }
      }
    };
    table.getTableHeader().addMouseListener(listMouseListener);
    return table;
  }

  /**
   * Creates a header renderer for a JTable with our own look and feel.
   * @return a header renderer for a JTable with our own look and feel.
   */
  public static TableCellRenderer makeHeaderRenderer()
  {
    return new HeaderRenderer();
  }

  /**
   * Returns a String that contains the html passed as parameter with a div
   * applied.  The div style corresponds to the Font specified as parameter.
   * The goal of this method is to be able to specify a font for an HTML string.
   *
   * @param html the original html text.
   * @param font the font to be used to generate the new HTML.
   * @return a string that represents the original HTML with the font specified
   * as parameter.
   */
  public static String applyFontToHtmlWithDiv(String html, Font font)
  {
    StringBuilder buf = new StringBuilder();

    buf.append("<div style=\"").append(getFontStyle(font)).append("\">")
        .append(html).append(DIV_CLOSE);

    return buf.toString();
  }

  /**
   * Returns the HTML style representation for the given font.
   * @param font the font for which we want to get an HTML style representation.
   * @return the HTML style representation for the given font.
   */
  private static String getFontStyle(Font font)
  {
    StringBuilder buf = new StringBuilder();

    buf.append("font-family:" + font.getName()).append(
        ";font-size:" + font.getSize() + "pt");

    if (font.isItalic())
    {
      buf.append(";font-style:italic");
    }

    if (font.isBold())
    {
      buf.append(";font-weight:bold;");
    }

    return buf.toString();
  }

  /**
   * Returns the html text passed as parameter with the error background
   * applied to it.
   * @param html the original html.
   * @return the html text passed as parameter with the error background
   * applied to it.
   */
  public static String applyErrorBackgroundToHtml(String html)
  {
    return DIV_OPEN_ERROR_BACKGROUND + html + DIV_CLOSE;
  }

  /**
   * Returns the html text passed as parameter with the warning background
   * applied to it.
   * @param html the original html.
   * @return the html text passed as parameter with the warning background
   * applied to it.
   */
  public static String applyWarningBackgroundToHtml(String html)
  {
    return DIV_OPEN_WARNING_BACKGROUND + html + DIV_CLOSE;
  }


  /**
   * Returns the html text passed as parameter with the success background
   * applied to it.
   * @param html the original html.
   * @return the html text passed as parameter with the success background
   * applied to it.
   */
  public static String applySuccessfulBackgroundToHtml(String html)
  {
    return DIV_OPEN_SUCCESSFUL_BACKGROUND + html + DIV_CLOSE;
  }


  /**
   * Returns the html text passed as parameter with some added margin.
   * @param html the original html text.
   * @param top the top margin.
   * @param right the right margin.
   * @param bottom the bottom margin.
   * @param left the left margin.
   * @return the html text passed as parameter with some added margin.
   */
  public static String applyMargin(String html, int top, int right, int bottom,
      int left)
  {
    String result =
        "<div style=\"margin:" + top + "px " + right + "px " + bottom + "px "
            + left + "px;\">" + html + DIV_CLOSE;
    return result;
  }

  /**
   * Updates the provided field with all the other arguments.
   * @param field the field to be modified.
   * @param text the new text of the field.
   * @param tooltip the new tooltip text of the field.
   * @param size the new size of the field.
   * @param textStyle the new TextStyle of the field.
   */
  private static void updateTextFieldComponent(JTextField field, Message text,
      Message tooltip, int size, TextStyle textStyle)
  {
    field.setColumns(size);
    if (text != null)
    {
      field.setText(text.toString());
    }
    if (tooltip != null)
    {
      field.setToolTipText(tooltip.toString());
    }
    if (textStyle != null)
    {
      setTextStyle(field, textStyle);
    }
  }

  private static Color getColor(Message l)
  {
    String s = String.valueOf(l);
    String[] colors = s.split(",");
    int r = Integer.parseInt(colors[0].trim());
    int g = Integer.parseInt(colors[1].trim());
    int b = Integer.parseInt(colors[2].trim());

    return new Color(r, g, b);
  }

  /**
   * Returns the parent package path.  This is used to retrieve the icon
   * qualified names.
   * @return the parent package path.
   */
  private static String getParentPackagePath()
  {
    if (parentPackagePath == null)
    {
      String packageName = UIFactory.class.getPackage().getName();
      int lastDot = packageName.lastIndexOf('.');
      String parentPackage = packageName.substring(0, lastDot);
      parentPackagePath = parentPackage.replace(".", "/");
    }
    return parentPackagePath;
  }

  /**
   * Returns the path of the icon for the given IconType.
   * @param iconType the IconType for which we want to get the path.
   * @return the path of the icon for the given IconType.
   */
  private static String getIconPath(IconType iconType)
  {
    Message key = null;
    switch (iconType)
    {
    case CURRENT_STEP:
      key = INFO_CURRENT_STEP_ICON.get();
      break;

    case SPLASH:
      key = INFO_SPLASH_ICON.get();
      break;

    case BACKGROUND:
      key = INFO_BACKGROUND_ICON.get();
      break;

    case MINIMIZED:
      key = INFO_MINIMIZED_ICON.get();
      break;

    case MINIMIZED_MAC:
      key = INFO_MINIMIZED_MAC_ICON.get();
      break;

    case WARNING:
      key = INFO_WARNING_ICON.get();
      break;

    case WARNING_LARGE:
      key = INFO_WARNING_LARGE_ICON.get();
      break;

    case INFORMATION:
      key = INFO_INFORMATION_ICON.get();
      break;

    case INFORMATION_LARGE:
      key = INFO_INFORMATION_LARGE_ICON.get();
      break;

    case OPENDS_SMALL:
      key = INFO_OPENDS_SMALL_ICON.get();
      break;

    case SUBSECTION_LEFT:
      key = INFO_SUBSECTION_LEFT_ICON.get();
      break;

    case SUBSECTION_RIGHT:
      key = INFO_SUBSECTION_RIGHT_ICON.get();
      break;

    case HELP_SMALL:
      key = INFO_HELP_SMALL_ICON.get();
      break;

    case ERROR:
      key = INFO_ERROR_ICON.get();
      break;

    case ERROR_LARGE:
      key = INFO_ERROR_LARGE_ICON.get();
      break;

    case WAIT_TINY:
      key = INFO_WAIT_TINY.get();
      break;

    case WAIT:
      key = INFO_WAIT.get();
      break;

    default:
      throw new IllegalArgumentException("Unknown iconName: " + iconType);
    }
    return getParentPackagePath() + "/" + key.toString();
  }

  /**
   * Returns the icon description for the given IconType.
   * @param iconType the IconType for which we want to get the description.
   * @return the icon description for the given IconType.
   */
  private static Message getIconDescription(IconType iconType)
  {
    Message description = null;
    switch (iconType)
    {
    case CURRENT_STEP:
      description = INFO_CURRENT_STEP_ICON_DESCRIPTION.get();
      break;

    case SPLASH:
      description = INFO_SPLASH_ICON_DESCRIPTION.get();
      break;

    case BACKGROUND:
      description = INFO_BACKGROUND_ICON_DESCRIPTION.get();
      break;

    case MINIMIZED:
      description = INFO_MINIMIZED_ICON_DESCRIPTION.get();
      break;

    case MINIMIZED_MAC:
      description = INFO_MINIMIZED_ICON_DESCRIPTION.get();
      break;

    case WARNING:
      description = INFO_WARNING_ICON_DESCRIPTION.get();
      break;

    case WARNING_LARGE:
      description = INFO_WARNING_ICON_DESCRIPTION.get();
      break;

    case ERROR:
      description = INFO_ERROR_ICON_DESCRIPTION.get();
      break;

    case ERROR_LARGE:
      description = INFO_ERROR_ICON_DESCRIPTION.get();
      break;

    case INFORMATION:
      description = INFO_INFORMATION_ICON_DESCRIPTION.get();
      break;

    case INFORMATION_LARGE:
      description = INFO_INFORMATION_ICON_DESCRIPTION.get();
      break;

    case OPENDS_SMALL:
      description = INFO_OPENDS_SMALL_ICON_DESCRIPTION.get();
      break;

    case SUBSECTION_LEFT:
      description = INFO_SUBSECTION_LEFT_ICON_DESCRIPTION.get();
      break;

    case SUBSECTION_RIGHT:
      description = INFO_SUBSECTION_RIGHT_ICON_DESCRIPTION.get();
      break;

    case HELP_SMALL:
      description = INFO_HELP_SMALL_ICON_DESCRIPTION.get();
      break;

    case WAIT_TINY:
      description = INFO_HELP_WAIT_DESCRIPTION.get();
      break;

    case WAIT:
      description = INFO_HELP_WAIT_DESCRIPTION.get();
      break;

    case NO_ICON:
      description = null;
      break;

    default:
      throw new IllegalArgumentException("Unknown iconName: " + iconType);
    }

    return description;
  }

  /**
   * Returns the icon tooltip text for the given IconType.
   * @param iconType the IconType for which we want to get the tooltip text.
   * @return the icon tooltip text for the given IconType.
   */
  private static Message getIconTooltip(IconType iconType)
  {
    if (iconType == null) {
      iconType = IconType.NO_ICON;
    }
    Message tooltip;
    switch (iconType)
    {
    case CURRENT_STEP:
      tooltip = INFO_CURRENT_STEP_ICON_TOOLTIP.get();
      break;

    case SPLASH:
      tooltip = INFO_SPLASH_ICON_TOOLTIP.get();
      break;

    case BACKGROUND:
      tooltip = INFO_BACKGROUND_ICON_TOOLTIP.get();
      break;

    case MINIMIZED:
      tooltip = INFO_MINIMIZED_ICON_TOOLTIP.get();
      break;

    case MINIMIZED_MAC:
      tooltip = INFO_MINIMIZED_ICON_TOOLTIP.get();
      break;

    case WARNING:
      tooltip = INFO_WARNING_ICON_TOOLTIP.get();
      break;

    case WARNING_LARGE:
      tooltip = INFO_WARNING_ICON_TOOLTIP.get();
      break;

    case ERROR:
      tooltip = INFO_ERROR_ICON_TOOLTIP.get();
      break;

    case ERROR_LARGE:
      tooltip = INFO_ERROR_ICON_TOOLTIP.get();
      break;

    case INFORMATION:
      tooltip = INFO_INFORMATION_ICON_TOOLTIP.get();
      break;

    case INFORMATION_LARGE:
      tooltip = INFO_INFORMATION_ICON_TOOLTIP.get();
      break;

    case OPENDS_SMALL:
      tooltip = null;
      break;

    case SUBSECTION_LEFT:
      tooltip = null;
      break;

    case SUBSECTION_RIGHT:
      tooltip = null;
      break;

    case HELP_SMALL:
      tooltip = null;
      break;

    case WAIT_TINY:
      tooltip = null;
      break;

    case NO_ICON:
      tooltip = null;
      break;

    default:
      throw new IllegalArgumentException("Unknown iconName: " + iconType);
    }

    return tooltip;
  }

  private static ListCellRenderer makeCellRenderer(final TextStyle textStyle)
  {
    ListCellRenderer renderer = new ListCellRenderer()
    {
      public Component getListCellRendererComponent(JList list,
          Object value,
          int index,
          boolean isSelected,
          boolean cellHasFocus)
      {
        JLabel l = makeJLabel(IconType.NO_ICON, Message.fromObject(value),
                              textStyle);
        l.setBorder(new EmptyBorder(TOP_INSET_SECONDARY_FIELD, 0, 0, 0));
        return l;
      }
    };
    return renderer;
  }
}

/**
 * Class used to render the table headers.
 */
class HeaderRenderer extends JLabel implements TableCellRenderer
{
  private static final long serialVersionUID = -8604332267021523835L;

  /**
   * Default constructor.
   */
  public HeaderRenderer()
  {
    super();
    UIFactory.setTextStyle(this, UIFactory.TextStyle.PRIMARY_FIELD_VALID);
  }

  /**
   * {@inheritDoc}
   */
  public Component getTableCellRendererComponent(JTable table, Object value,
      boolean isSelected, boolean hasFocus, int row, int column) {
    setText((String)value);
    if (column == 0)
    {
      setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createMatteBorder(1, 1, 1, 1,
              UIFactory.PANEL_BORDER_COLOR),
              BorderFactory.createEmptyBorder(4, 4, 4, 4)));
    }
    else
    {
      setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createMatteBorder(1, 0, 1, 1,
              UIFactory.PANEL_BORDER_COLOR),
              BorderFactory.createEmptyBorder(4, 4, 4, 4)));
    }
    return this;
  }
}

/**
 * This class has been written to have a better behaviour with the scroll pane
 * than the one we have by default in the case of the progress panel.
 *
 * With the default scroll pane behaviour when we set a new text in a
 * JEditorPane the scroll bar goes systematically up.  With this implementation
 * the expected behaviour is:
 *
 * If the scroll bar is at the bottom we will display the latest text contained
 * in the pane.
 *
 * If the scroll bar is not at the bottom we will keep on displaying the same
 * thing that the user is viewing.
 *
 * This behaviour allows the user to check the log content even when the
 * installation/uninstallation is still running and sending new log messages.
 *
 */
class ProgressJEditorPane extends JEditorPane
{
  private static final long serialVersionUID = 1221976708322628818L;

  private JScrollPane scroll;

  private boolean ignoreScrollToVisible;

  /**
   * Constructor for the ProgressJEditorPane.
   * @param scroll the JScrollPane that will contain this editor pane.
   */
  public ProgressJEditorPane(JScrollPane scroll)
  {
    super("text/html", null);
    this.scroll = scroll;
    setEditable(false);
    setBorder(new EmptyBorder(3, 3, 3, 3));
  }

  /**
   * {@inheritDoc}
   */
  public void setText(String text)
  {
    // Scroll can be null in constructor
    if (scroll != null)
    {
      /* We apply the following policy: if the user is displaying the latest
       * part of the JTextArea we assume that when we add text (s)he wants
       * to see the text that is added, if not we assume that (s)he want to keep
       * viewing what is visible and so we ignore the next scrollRectToVisible
       * call (that will be done inside JTextArea.setText method).
       */
      JScrollBar vBar = scroll.getVerticalScrollBar();
      ignoreScrollToVisible =
          (vBar != null)
              && ((vBar.getValue() + vBar.getVisibleAmount()) < 0.97 * vBar
                  .getMaximum());
      super.setText(text);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void scrollRectToVisible(Rectangle rect)
  {
    if (!ignoreScrollToVisible)
    {
      super.scrollRectToVisible(rect);
      ignoreScrollToVisible = false;
    }
  }
}

/**
 * A class used to be able to select the contents of the text field when
 * it gets the focus.
 *
 */
class TextFieldFocusListener implements FocusListener
{
  private JTextField tf;
  /**
   * The constructor for this listener.
   * @param tf the text field associated with this listener.
   */
  TextFieldFocusListener(JTextField tf)
  {
    this.tf = tf;
  }
  /**
   * {@inheritDoc}
   */
  public void focusGained(FocusEvent e)
  {
    if ((tf.getText() == null) || "".equals(tf.getText()))
    {
      tf.setText(" ");
      tf.selectAll();
      tf.setText("");
    }
    else
    {
      tf.selectAll();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void focusLost(FocusEvent e)
  {
  }
}
