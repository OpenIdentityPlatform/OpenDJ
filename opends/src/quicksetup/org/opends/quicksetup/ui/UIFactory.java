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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.opends.quicksetup.i18n.ResourceProvider;

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

  /**
   * Specifies the horizontal insets between buttons.
   */
  public static final int HORIZONTAL_INSET_BETWEEN_BUTTONS = 5;

  /**
   * Specifies the top inset for the steps.
   */
  public static final int TOP_INSET_STEP = 15;

  /**
   * Specifies the left inset for the steps.
   */
  public static final int LEFT_INSET_STEP = 5;

  /**
   * Specifies the top inset for the instructions sub panel.
   */
  public static final int TOP_INSET_INSTRUCTIONS_SUBPANEL = 10;

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
  public static final int LEFT_INSET_RADIO_SUBORDINATE = 20;

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
  public static final Color DEFAULT_BACKGROUND = new Color(236, 236, 236);

  /**
   * Specifies the current step background color.
   */
  public static final Color CURRENT_STEP_PANEL_BACKGROUND =
    Color.WHITE;

  /**
   * Specifies the default label color.
   */
  public static final Color DEFAULT_LABEL_COLOR = Color.BLACK;

  /**
   * Specifies the valid field color.
   */
  public static final Color FIELD_VALID_COLOR = Color.BLACK;

  /**
   * Specifies the invalid field color.
   */
  public static final Color FIELD_INVALID_COLOR = Color.RED;

  /**
   * Specifies the read only text color.
   */
  public static final Color READ_ONLY_COLOR = Color.BLACK;

  /**
   * Specifies the check box text color.
   */
  public static final Color CHECKBOX_COLOR = Color.BLACK;

  /**
   * Specifies the progress text color.
   */
  public static final Color PROGRESS_COLOR = Color.BLACK;

  /**
   * Specifies the text field text color.
   */
  public static final Color TEXTFIELD_COLOR = Color.BLACK;

  /**
   * Specifies the password field text color.
   */
  public static final Color PASSWORD_FIELD_COLOR = Color.BLACK;

  /**
   * Specifies the current step panel border.
   */
  public static final Border CURRENT_STEP_PANEL_BORDER =
    BorderFactory.createMatteBorder(0, 2, 2, 0, new Color(204, 204, 204));

  /**
   * Specifies the text area border.
   */
  public static final Border TEXT_AREA_BORDER =
    BorderFactory.createMatteBorder(1, 1, 1, 1, Color.BLACK);

  /**
   * Specifies the dialog border.
   */
  public static final Border DIALOG_PANEL_BORDER =
    BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(204, 204, 204));

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
    Font.decode("Arial-PLAIN-14");

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
  public static final Font BROWSER_ERROR_DIALOG_FONT =
      Font.decode("Arial-PLAIN-12");

  private static final String SPAN_CLOSE = "</span>";

  private static final String DIV_CLOSE = "</div>";

  private static final String DIV_OPEN_ERROR_BACKGROUND =
      "<div style=\"color:#000;background-color:#FFFFCC;"
          + "padding:10px 10px 10px 10px;"
          + "border-style:solid;border-width:3px;border-color:#E1E1A7;"
          + "vertical-align:middle;text-align:left\">";

  private static final String DIV_OPEN_WARNING_BACKGROUND =
      DIV_OPEN_ERROR_BACKGROUND;

  private static final String DIV_OPEN_SUCCESSFUL_BACKGROUND =
      "<div style=\"color:#000;background-color:#FFFFCC;"
          + "padding:10px 10px 10px 10px;"
          + "border-style:solid;border-width:3px;border-color:#E1E1A7;"
          + "vertical-align:middle;text-align:left\">";

  /**
   * An HTML separator text that can be used in the progress panel.
   */
  public static final String HTML_SEPARATOR =
      "<div style=\"font-size:1px;background-color:#666666;"
          + "margin:10px 5px 10px 5px;\"></div>";

  private static final HashMap<IconType, ImageIcon> hmIcons =
      new HashMap<IconType, ImageIcon>();

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
     * The error icon.
     */
    ERROR,
    /**
     * The warning large icon.
     */
    WARNING_LARGE,
    /**
     * The information icon.
     */
    INFORMATION,
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
   * Creates a JButton with the given label and tooltip.
   * @param label the text of the button.
   * @param tooltip the tooltip of the button.
   * @return a JButton with the given label and tooltip.
   */
  public static JButton makeJButton(String label, String tooltip)
  {
    JButton b = new JButton();

    if (label != null)
    {
      b.setText(label);
    }

    if (tooltip != null)
    {
      b.setToolTipText(tooltip);
    }

    return b;
  }

  /**
   * Creates a JLabel with the given icon, text and text style.
   * @param iconName the icon.
   * @param text the label text.
   * @param style the text style.
   * @return a JLabel with the given icon, text and text style.
   */
  public static JLabel makeJLabel(IconType iconName, String text,
      TextStyle style)
  {
    JLabel l = new JLabel();

    if (text != null)
    {
      l.setText(text);
    }

    ImageIcon icon = getImageIcon(iconName);
    l.setIcon(icon);
    String tooltip = getIconTooltip(iconName);

    if (tooltip != null)
    {
      l.setToolTipText(tooltip);
    }

    setTextStyle(l, style);
    return l;
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
  public static JTextField makeJTextField(String text, String tooltip,
      int size, TextStyle style)
  {
    JTextField f = new JTextField();
    updateTextFieldComponent(f, text, tooltip, size, style);
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
  public static JPasswordField makeJPasswordField(String text, String tooltip,
      int size, TextStyle style)
  {
    JPasswordField f = new JPasswordField();
    updateTextFieldComponent(f, text, tooltip, size, style);
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
  public static JRadioButton makeJRadioButton(String text, String tooltip,
      TextStyle style)
  {
    JRadioButton rb = new JRadioButton();

    if (text != null)
    {
      rb.setText(text);
    }

    if (tooltip != null)
    {
      rb.setToolTipText(tooltip);
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
  public static JCheckBox makeJCheckBox(String text, String tooltip,
      TextStyle style)
  {
    JCheckBox cb = new JCheckBox();

    if (text != null)
    {
      cb.setText(text);
    }

    if (tooltip != null)
    {
      cb.setToolTipText(tooltip);
    }

    setTextStyle(cb, style);
    return cb;
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

    case TEXTFIELD:
      l.setFont(UIFactory.TEXTFIELD_FONT);
      l.setForeground(TEXTFIELD_COLOR);
      break;

    case PASSWORD_FIELD:
      l.setFont(UIFactory.PASSWORD_FIELD_FONT);
      l.setForeground(PASSWORD_FIELD_COLOR);
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
    String description = getIconDescription(iconType);
    String title = getIconTooltip(iconType);
    return "<img src=\"" + url + "\" alt=\"" + description + " title=\""
        + title + "\"/>";
  }

  /**
   * Returns an ImageIcon object for the provided IconType.
   * @param iconType the IconType for which we want to obtain the ImageIcon.
   * @return the ImageIcon.
   */
  public static ImageIcon getImageIcon(IconType iconType)
  {
    ImageIcon icon = hmIcons.get(iconType);
    if ((icon == null) && (iconType != IconType.NO_ICON))
    {
      String path = getIconPath(iconType);
      String description = getIconDescription(iconType);
      try
      {
        Image im =
            Toolkit.getDefaultToolkit().createImage(
                UIFactory.class.getClassLoader().getResource(path));
        icon = new ImageIcon(im);
        icon.setDescription(description);

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
  public static JEditorPane makeHtmlPane(String text, Font font)
  {
    JEditorPane pane =
        new JEditorPane("text/html", applyFontToHtmlWithDiv(text, font));
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
  public static JEditorPane makeTextPane(String text, TextStyle style)
  {
    JEditorPane pane = new JEditorPane("text/plain", text);
    setTextStyle(pane, style);
    pane.setEditable(false);
    pane.setBorder(new EmptyBorder(0, 0, 0, 0));
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
    StringBuffer buf = new StringBuffer();

    buf.append("<span style=\"").append(getFontStyle(font)).append("\">")
        .append(html).append(SPAN_CLOSE);

    return buf.toString();
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
    StringBuffer buf = new StringBuffer();

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
    StringBuffer buf = new StringBuffer();

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
  private static void updateTextFieldComponent(JTextField field, String text,
      String tooltip, int size, TextStyle textStyle)
  {
    field.setColumns(size);
    if (text != null)
    {
      field.setText(text);
    }
    if (tooltip != null)
    {
      field.setToolTipText(tooltip);
    }
    if (textStyle != null)
    {
      setTextStyle(field, textStyle);
    }
  }

  /* Some commodity methods to retrieve localized messages */
  private static ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }

  private static String getMsg(String key)
  {
    return getI18n().getMsg(key);
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
    String key = null;
    switch (iconType)
    {
    case CURRENT_STEP:
      key = "current-step-icon";
      break;

    case SPLASH:
      key = "splash-icon";
      break;

    case BACKGROUND:
      key = "background-icon";
      break;

    case MINIMIZED:
      key = "minimized-icon";
      break;

    case MINIMIZED_MAC:
      key = "minimized-mac-icon";
      break;

    case WARNING:
      key = "warning-icon";
      break;

    case WARNING_LARGE:
      key = "warning-large-icon";
      break;

    case INFORMATION:
      key = "information-icon";
      break;

    case ERROR:
      key = "error-icon";
      break;

    default:
      throw new IllegalArgumentException("Unknow iconName: " + iconType);
    }
    return getParentPackagePath() + "/" + getMsg(key);
  }

  /**
   * Returns the icon description for the given IconType.
   * @param iconType the IconType for which we want to get the description.
   * @return the icon description for the given IconType.
   */
  private static String getIconDescription(IconType iconType)
  {
    String description = null;
    switch (iconType)
    {
    case CURRENT_STEP:
      description = getMsg("current-step-icon-description");
      break;

    case SPLASH:
      description = getMsg("splash-icon-description");
      break;

    case BACKGROUND:
      description = getMsg("background-icon-description");
      break;

    case MINIMIZED:
      description = getMsg("minimized-icon-description");
      break;

    case MINIMIZED_MAC:
      description = getMsg("minimized-icon-description");
      break;

    case WARNING:
      description = "warning-icon-description";
      break;

    case WARNING_LARGE:
      description = "warning-icon-description";
      break;

    case ERROR:
      description = "error-icon-description";
      break;

    case INFORMATION:
      description = "information-icon-description";
      break;

    case NO_ICON:
      description = null;
      break;

    default:
      throw new IllegalArgumentException("Unknow iconName: " + iconType);
    }
    return description;
  }

  /**
   * Returns the icon tooltip text for the given IconType.
   * @param iconType the IconType for which we want to get the tooltip text.
   * @return the icon tooltip text for the given IconType.
   */
  private static String getIconTooltip(IconType iconType)
  {
    String tooltip;
    switch (iconType)
    {
    case CURRENT_STEP:
      tooltip = getMsg("current-step-icon-tooltip");
      break;

    case SPLASH:
      tooltip = getMsg("splash-icon-tooltip");
      break;

    case BACKGROUND:
      tooltip = getMsg("background-icon-tooltip");
      break;

    case MINIMIZED:
      tooltip = getMsg("minimized-icon-tooltip");
      break;

    case MINIMIZED_MAC:
      tooltip = getMsg("minimized-mac-icon-tooltip");
      break;

    case WARNING:
      tooltip = "warning-icon-tooltip";
      break;

    case WARNING_LARGE:
      tooltip = "warning-icon-tooltip";
      break;

    case ERROR:
      tooltip = "error-icon-tooltip";
      break;

    case INFORMATION:
      tooltip = "information-icon-tooltip";
      break;

    case NO_ICON:
      tooltip = null;
      break;

    default:
      throw new IllegalArgumentException("Unknow iconName: " + iconType);
    }
    return tooltip;
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
