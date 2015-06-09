/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.guitools.controlpanel.util;

import static org.opends.admin.ads.util.ConnectionUtils.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.quicksetup.Installation.*;
import static org.opends.server.types.CommonSchemaElements.*;

import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.util.OperatingSystem.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import javax.naming.CompositeName;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.ConfigurationFramework;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.guitools.controlpanel.ControlPanel;
import org.opends.guitools.controlpanel.browser.IconPool;
import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.ConfigReadException;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.MonitoringAttributes;
import org.opends.guitools.controlpanel.datamodel.SortableTableModel;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.event.ClickTooltipDisplayer;
import org.opends.guitools.controlpanel.event.ComboKeySelectionManager;
import org.opends.guitools.controlpanel.event.TextComponentFocusListener;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.components.LabelWithHelpIcon;
import org.opends.guitools.controlpanel.ui.components.SelectableLabelWithHelpIcon;
import org.opends.guitools.controlpanel.ui.renderer.AccessibleTableHeaderRenderer;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.Utils;
import org.opends.server.admin.ClassLoaderProvider;
import org.opends.server.api.ConfigHandler;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.types.AttributeType;
import org.opends.server.types.CommonSchemaElements;
import org.opends.server.types.DN;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.RDN;
import org.opends.server.types.Schema;
import org.opends.server.types.SchemaFileElement;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/**
 * A static class that provides miscellaneous functions.
 */
public class Utilities
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static File rootDirectory;
  private static File instanceRootDirectory;

  private static final String HTML_SPACE = "&nbsp;";
  private static final String[] attrsToObfuscate = { ServerConstants.ATTR_USER_PASSWORD };
  private static final String[] passwordSyntaxOIDs = { SchemaConstants.SYNTAX_USER_PASSWORD_OID };
  private static final String[] binarySyntaxOIDs = {
    SchemaConstants.SYNTAX_BINARY_OID,
    SchemaConstants.SYNTAX_JPEG_OID,
    SchemaConstants.SYNTAX_CERTIFICATE_OID,
    SchemaConstants.SYNTAX_OCTET_STRING_OID
  };

  private static ImageIcon warningIcon;
  private static ImageIcon requiredIcon;

  private final static LocalizableMessage NO_VALUE_SET = INFO_CTRL_PANEL_NO_MONITORING_VALUE.get();
  private final static LocalizableMessage NOT_IMPLEMENTED = INFO_CTRL_PANEL_NOT_IMPLEMENTED.get();

  /**
   * Creates a combo box.
   *
   * @param <T>
   *          The combo box data type.
   * @return a combo box.
   */
  public static <T> JComboBox<T> createComboBox()
  {
    JComboBox<T> combo = new JComboBox<>();
    if (isMacOS())
    {
      combo.setOpaque(false);
    }
    combo.setKeySelectionManager(new ComboKeySelectionManager(combo));
    return combo;
  }

  /**
   * Creates a frame.
   * @return a frame.
   */
  public static JFrame createFrame()
  {
    JFrame frame = new JFrame();
    frame.setResizable(true);
    org.opends.quicksetup.ui.Utilities.setFrameIcon(frame);
    return frame;
  }

  /**
   * Returns <CODE>true</CODE> if an attribute value must be obfuscated because
   * it contains sensitive information (like passwords) and <CODE>false</CODE>
   * otherwise.
   * @param attrName the attribute name.
   * @param schema the schema of the server.
   * @return <CODE>true</CODE> if an attribute value must be obfuscated because
   * it contains sensitive information (like passwords) and <CODE>false</CODE>
   * otherwise.
   */
  public static boolean mustObfuscate(String attrName, Schema schema)
  {
    if (schema != null)
    {
      return hasPasswordSyntax(attrName, schema);
    }
    for (String attr : attrsToObfuscate)
    {
      if (attr.equalsIgnoreCase(attrName))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Derives a color by adding the specified offsets to the base color's
   * hue, saturation, and brightness values.   The resulting hue, saturation,
   * and brightness values will be constrained to be between 0 and 1.
   * @param base the color to which the HSV offsets will be added
   * @param dH the offset for hue
   * @param dS the offset for saturation
   * @param dB the offset for brightness
   * @return Color with modified HSV values
   */
  public static Color deriveColorHSB(Color base, float dH, float dS, float dB)
  {
    float hsb[] = Color.RGBtoHSB(
        base.getRed(), base.getGreen(), base.getBlue(), null);

    hsb[0] += dH;
    hsb[1] += dS;
    hsb[2] += dB;
    return Color.getHSBColor(
        hsb[0] < 0? 0 : (hsb[0] > 1? 1 : hsb[0]),
            hsb[1] < 0? 0 : (hsb[1] > 1? 1 : hsb[1]),
                hsb[2] < 0? 0 : (hsb[2] > 1? 1 : hsb[2]));

  }

  /**
   * Displays an error dialog that contains a set of error messages.
   * @param parentComponent the parent component relative to which the dialog
   * will be displayed.
   * @param errors the set of error messages that the dialog must display.
   */
  public static void displayErrorDialog(Component parentComponent,
      Collection<LocalizableMessage> errors)
  {
    /*
    ErrorPanel panel = new ErrorPanel("Error", errors);
    GenericDialog dlg = new GenericDialog(null, panel);
    dlg.setModal(true);
    Utilities.centerGoldenMean(dlg, Utilities.getParentDialog(this));
    dlg.setVisible(true);
    */
    ArrayList<String> stringErrors = new ArrayList<String>();
    for (LocalizableMessage err : errors)
    {
      stringErrors.add(err.toString());
    }
    String msg = getStringFromCollection(stringErrors, "<br>");
    String plainText = msg.replaceAll("<br>", ServerConstants.EOL);
    String wrappedText = wrapText(plainText, 70);
    wrappedText = wrappedText.replaceAll(ServerConstants.EOL, "<br>");
    JOptionPane.showMessageDialog(
        parentComponent, "<html>"+wrappedText,
        INFO_CTRL_PANEL_ERROR_DIALOG_TITLE.get().toString(),
        JOptionPane.ERROR_MESSAGE);
  }

  /**
   * Displays a confirmation dialog.  Returns <CODE>true</CODE> if the user
   * accepts the message and <CODE>false</CODE> otherwise.
   * @param parentComponent the parent component relative to which the dialog
   * will be displayed.
   * @param title the title of the dialog.
   * @param msg the message to be displayed.
   * @return  <CODE>true</CODE> if the user accepts the message and
   * <CODE>false</CODE> otherwise.
   *
   */
  public static boolean displayConfirmationDialog(Component parentComponent,
      LocalizableMessage title, LocalizableMessage msg)
  {
    String plainText = msg.toString().replaceAll("<br>", ServerConstants.EOL);
    String wrappedText = wrapText(plainText, 70);
    wrappedText = wrappedText.replaceAll(ServerConstants.EOL, "<br>");
    return JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(
        parentComponent, "<html>"+wrappedText,
        title.toString(),
        JOptionPane.YES_NO_OPTION,
        JOptionPane.QUESTION_MESSAGE,
        null, // don't use a custom Icon
        null, // the titles of buttons
        null); // default button title
  }

  /**
   * Displays a warning dialog.
   * @param parentComponent the parent component relative to which the dialog
   * will be displayed.
   * @param title the title of the dialog.
   * @param msg the message to be displayed.
   */
  public static void displayWarningDialog(Component parentComponent,
      LocalizableMessage title, LocalizableMessage msg)
  {
    String plainText = msg.toString().replaceAll("<br>", ServerConstants.EOL);
    String wrappedText = wrapText(plainText, 70);
    wrappedText = wrappedText.replaceAll(ServerConstants.EOL, "<br>");
    JOptionPane.showMessageDialog(
        parentComponent, "<html>"+wrappedText,
        title.toString(),
        JOptionPane.WARNING_MESSAGE);
  }


  /**
   * Creates a JEditorPane that displays a message.
   * @param text the message of the editor pane in HTML format.
   * @param font the font to be used in the message.
   * @return a JEditorPane that displays a message.
   */
  public static JEditorPane makeHtmlPane(CharSequence text, Font font)
  {
    JEditorPane pane = new JEditorPane();
    pane.setContentType("text/html");
    pane.setFont(font);
    if (text != null)
    {
      pane.setText(applyFont(text, font));
    }
    pane.setEditable(false);
    pane.setBorder(new EmptyBorder(0, 0, 0, 0));
    pane.setOpaque(false);
    pane.setFocusCycleRoot(false);
    return pane;
  }

  /**
   * Creates a JEditorPane that displays a message.
   * @param text the message of the editor pane in plain text format.
   * @param font the font to be used in the message.
   * @return a JEditorPane that displays a message.
   */
  public static JEditorPane makePlainTextPane(String text, Font font)
  {
    JEditorPane pane = new JEditorPane();
    pane.setContentType("text/plain");
    if (text != null)
    {
      pane.setText(text);
    }
    pane.setFont(font);
    pane.setEditable(false);
    pane.setBorder(new EmptyBorder(0, 0, 0, 0));
    pane.setOpaque(false);
    pane.setFocusCycleRoot(false);
    return pane;
  }

  /**
   * Returns the HTML style representation for the given font.
   * @param font the font for which we want to get an HTML style representation.
   * @return the HTML style representation for the given font.
   */
  private static String getFontStyle(Font font)
  {
    StringBuilder buf = new StringBuilder();

    buf.append("font-family:").append(font.getName())
        .append(";font-size:").append(font.getSize()).append("pt");

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
   * Creates a titled border.
   * @param msg the message to be displayed in the titled border.
   * @return the created titled border.
   */
  public static Border makeTitledBorder(LocalizableMessage msg)
  {
    TitledBorder border = new TitledBorder(new EtchedBorder(),
        " "+msg+" ");
    border.setTitleFont(ColorAndFontConstants.titleFont);
    border.setTitleColor(ColorAndFontConstants.foreground);
    return border;
  }

  /**
   * Returns a JScrollPane that contains the provided component.  The scroll
   * pane will not contain any border.
   * @param comp the component contained in the scroll pane.
   * @return a JScrollPane that contains the provided component.  The scroll
   * pane will not contain any border.
   */
  public static JScrollPane createBorderLessScrollBar(Component comp)
  {
    JScrollPane scroll = new JScrollPane(comp);
    scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
    scroll.setViewportBorder(new EmptyBorder(0, 0, 0, 0));
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.getViewport().setBackground(ColorAndFontConstants.background);
    scroll.setBackground(ColorAndFontConstants.background);
    UIFactory.setScrollIncrementUnit(scroll);
    return scroll;
  }

  /**
   * Returns a JScrollPane that contains the provided component.
   * @param comp the component contained in the scroll pane.
   * @return a JScrollPane that contains the provided component.
   */
  public static JScrollPane createScrollPane(Component comp)
  {
    JScrollPane scroll = new JScrollPane(comp);
    scroll.getViewport().setOpaque(false);
    scroll.setOpaque(false);
    scroll.getViewport().setBackground(ColorAndFontConstants.background);
    scroll.setBackground(ColorAndFontConstants.background);
    UIFactory.setScrollIncrementUnit(scroll);
    return scroll;
  }

  /**
   * Creates a button.
   * @param text the message to be displayed by the button.
   * @return the created button.
   */
  public static JButton createButton(LocalizableMessage text)
  {
    JButton button = new JButton(text.toString());
    button.setOpaque(false);
    button.setForeground(ColorAndFontConstants.buttonForeground);
    button.getAccessibleContext().setAccessibleName(text.toString());
    return button;
  }

  /**
   * Creates a radio button.
   * @param text the message to be displayed by the radio button.
   * @return the created radio button.
   */
  public static JRadioButton createRadioButton(LocalizableMessage text)
  {
    JRadioButton button = new JRadioButton(text.toString());
    button.setOpaque(false);
    button.setForeground(ColorAndFontConstants.buttonForeground);
    button.getAccessibleContext().setAccessibleName(text.toString());
    return button;
  }

  /**
   * Creates a check box.
   * @param text the message to be displayed by the check box.
   * @return the created check box.
   */
  public static JCheckBox createCheckBox(LocalizableMessage text)
  {
    JCheckBox cb = new JCheckBox(text.toString());
    cb.setOpaque(false);
    cb.setForeground(ColorAndFontConstants.buttonForeground);
    cb.getAccessibleContext().setAccessibleName(text.toString());
    return cb;
  }

  /**
   * Creates a menu item with the provided text.
   * @param msg the text.
   * @return a menu item with the provided text.
   */
  public static JMenuItem createMenuItem(LocalizableMessage msg)
  {
    return new JMenuItem(msg.toString());
  }

  /**
   * Creates a menu with the provided text.
   * @param msg the text.
   * @param description the accessible description.
   * @return a menu with the provided text.
   */
  public static JMenu createMenu(LocalizableMessage msg, LocalizableMessage description)
  {
    JMenu menu = new JMenu(msg.toString());
    menu.getAccessibleContext().setAccessibleDescription(
        description.toString());
    return menu;
  }

  /**
   * Creates a label of type 'primary' (with bigger font than usual) with no
   * text.
   * @return the label of type 'primary' (with bigger font than usual) with no
   * text.
   */
  public static JLabel createPrimaryLabel()
  {
    return createPrimaryLabel(LocalizableMessage.EMPTY);
  }

  /**
   * Creates a label of type 'primary' (with bigger font than usual).
   * @param text the message to be displayed by the label.
   * @return the label of type 'primary' (with bigger font than usual).
   */
  public static JLabel createPrimaryLabel(LocalizableMessage text)
  {
    JLabel label = new JLabel(text.toString());
    label.setFont(ColorAndFontConstants.primaryFont);
    label.setForeground(ColorAndFontConstants.foreground);
    return label;
  }

  /**
   * Creates a label of type 'inline help' (with smaller font).
   * @param text the message to be displayed by the label.
   * @return the label of type 'inline help' (with smaller font).
   */
  public static JLabel createInlineHelpLabel(LocalizableMessage text)
  {
    JLabel label = new JLabel(text.toString());
    label.setFont(ColorAndFontConstants.inlineHelpFont);
    label.setForeground(ColorAndFontConstants.foreground);
    return label;
  }

  /**
   * Creates a label of type 'title' (with bigger font).
   * @param text the message to be displayed by the label.
   * @return the label of type 'title' (with bigger font).
   */
  public static JLabel createTitleLabel(LocalizableMessage text)
  {
    JLabel label = new JLabel(text.toString());
    label.setFont(ColorAndFontConstants.titleFont);
    label.setForeground(ColorAndFontConstants.foreground);
    return label;
  }

  /**
   * Creates a label (with default font) with no text.
   * @return the label (with default font) with no text.
   */
  public static JLabel createDefaultLabel()
  {
    return createDefaultLabel(LocalizableMessage.EMPTY);
  }

  /**
   * Creates a label (with default font).
   * @param text the message to be displayed by the label.
   * @return the label (with default font).
   */
  public static JLabel createDefaultLabel(LocalizableMessage text)
  {
    JLabel label = new JLabel(text.toString());
    label.setFont(ColorAndFontConstants.defaultFont);
    label.setForeground(ColorAndFontConstants.foreground);
    return label;
  }

  /**
   * Returns a table created with the provided model and renderers.
   * @param tableModel the table model.
   * @param renderer the cell renderer.
   * @return a table created with the provided model and renderers.
   */
  public static JTable createSortableTable(final SortableTableModel tableModel,
      TableCellRenderer renderer)
  {
    final JTable table = new JTable(tableModel);
    table.setShowGrid(true);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    table.setGridColor(ColorAndFontConstants.gridColor);
    if (isMacOS())
    {
      table.getTableHeader().setBorder(
          BorderFactory.createMatteBorder(1, 1, 0, 0,
              ColorAndFontConstants.gridColor));
    }
    if (isWindows())
    {
      table.getTableHeader().setBorder(
          BorderFactory.createMatteBorder(1, 1, 0, 1,
              ColorAndFontConstants.gridColor));
    }
    table.getTableHeader().setDefaultRenderer(
        new AccessibleTableHeaderRenderer(
            table.getTableHeader().getDefaultRenderer()));

    for (int i=0; i<tableModel.getColumnCount(); i++)
    {
      TableColumn col = table.getColumn(table.getColumnName(i));
      col.setCellRenderer(renderer);
    }
    MouseAdapter listMouseListener = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        TableColumnModel columnModel = table.getColumnModel();
        int viewColumn = columnModel.getColumnIndexAtX(e.getX());
        int sortedBy = table.convertColumnIndexToModel(viewColumn);
        if (e.getClickCount() == 1 && sortedBy != -1) {
          tableModel.setSortAscending(!tableModel.isSortAscending());
          tableModel.setSortColumn(sortedBy);
          tableModel.forceResort();
          updateTableSizes(table);
        }
      }
    };
    table.getTableHeader().addMouseListener(listMouseListener);
    return table;
  }

  /**
   * Creates a text area with borders similar to the ones of a text field.
   * @param text the text of the text area.
   * @param rows the rows of the text area.
   * @param cols the columns of the text area.
   * @return a text area with borders similar to the ones of a text field.
   */
  public static JTextArea createTextAreaWithBorder(LocalizableMessage text, int rows,
      int cols)
  {
    JTextArea ta = createTextArea(text, rows, cols);
    if (ColorAndFontConstants.textAreaBorder != null)
    {
      setBorder(ta, ColorAndFontConstants.textAreaBorder);
    }
    return ta;
  }

  /**
   * Creates a non-editable text area.
   * @param text the text of the text area.
   * @param rows the rows of the text area.
   * @param cols the columns of the text area.
   * @return a non-editable text area.
   */
  public static JTextArea createNonEditableTextArea(LocalizableMessage text, int rows,
      int cols)
  {
    JTextArea ta = createTextArea(text, rows, cols);
    ta.setEditable(false);
    ta.setOpaque(false);
    ta.setForeground(ColorAndFontConstants.foreground);
    return ta;
  }

  /**
   * Creates a text area.
   * @param text the text of the text area.
   * @param rows the rows of the text area.
   * @param cols the columns of the text area.
   * @return a text area.
   */
  public static JTextArea createTextArea(LocalizableMessage text, int rows,
      int cols)
  {
    JTextArea ta = new JTextArea(text.toString(), rows, cols);
    ta.setFont(ColorAndFontConstants.defaultFont);
    return ta;
  }

  /**
   * Creates a text field.
   * @param text the text of the text field.
   * @param cols the columns of the text field.
   * @return the created text field.
   */
  public static JTextField createTextField(String text, int cols)
  {
    JTextField tf = createTextField();
    tf.setText(text);
    tf.setColumns(cols);
    return tf;
  }

  /**
   * Creates a short text field.
   * @return the created text field.
   */
  public static JTextField createShortTextField()
  {
    JTextField tf = createTextField();
    tf.setColumns(10);
    return tf;
  }

  /**
   * Creates a medium sized text field.
   * @return the created text field.
   */
  public static JTextField createMediumTextField()
  {
    JTextField tf = createTextField();
    tf.setColumns(20);
    return tf;
  }

  /**
   * Creates a long text field.
   * @return the created text field.
   */
  public static JTextField createLongTextField()
  {
    JTextField tf = createTextField();
    tf.setColumns(30);
    return tf;
  }


  /**
   * Creates a text field with the default size.
   * @return the created text field.
   */
  public static JTextField createTextField()
  {
    JTextField tf = new JTextField();
    tf.addFocusListener(new TextComponentFocusListener(tf));
    tf.setFont(ColorAndFontConstants.defaultFont);
    return tf;
  }

  /**
   * Creates a pasword text field.
   * @return the created password text field.
   */
  public static JPasswordField createPasswordField()
  {
    JPasswordField pf = new JPasswordField();
    pf.addFocusListener(new TextComponentFocusListener(pf));
    pf.setFont(ColorAndFontConstants.defaultFont);
    return pf;
  }

  /**
   * Creates a pasword text field.
   * @param cols the columns of the password text field.
   * @return the created password text field.
   */
  public static JPasswordField createPasswordField(int cols)
  {
    JPasswordField pf = createPasswordField();
    pf.setColumns(cols);
    return pf;
  }


  /**
   * Sets the border in a given component.  If the component already has a
   * border, creates a compound border.
   * @param comp the component.
   * @param border the border to be set.
   */
  public static void setBorder(JComponent comp, Border border)
  {
    if (comp.getBorder() != null)
    {
      comp.setBorder(BorderFactory.createCompoundBorder(comp.getBorder(), border));
    }
    else
    {
      comp.setBorder(border);
    }
  }

  /**
   * Checks the size of the table and of the scroll bar where it is contained,
   * and depending on it updates the auto resize mode.
   * @param scroll the scroll pane containing the table.
   * @param table the table.
   */
  public static void updateScrollMode(JScrollPane scroll, JTable table)
  {
    int width1 = table.getPreferredScrollableViewportSize().width;
    int width2 = scroll.getViewport().getWidth();

    if (width1 > width2)
    {
      table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    }
    else
    {
      table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    }
  }

  /**
   * Updates the size of the table rows according to the size of the
   * rendered component.
   * @param table the table to handle.
   */
  public static void updateTableSizes(JTable table)
  {
    updateTableSizes(table, -1);
  }

  /**
   * Updates the size of the table rows according to the size of the
   * rendered component.
   * @param table the table to handle.
   * @param rows the maximum rows to be displayed (-1 for unlimited)
   */
  public static void updateTableSizes(JTable table, int rows)
  {
    int horizontalMargin = table.getIntercellSpacing().width;
    int verticalMargin = table.getIntercellSpacing().height;
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

    int headerMaxHeight = 5;
    int headerMaxWidth = 0;

    JTableHeader header = table.getTableHeader();
    if (header != null && header.isVisible())
    {
      for (int col=0; col<table.getColumnCount(); col++)
      {
        TableColumn tcol = table.getColumnModel().getColumn(col);
        TableCellRenderer renderer = tcol.getHeaderRenderer();
        if (renderer == null)
        {
          renderer = table.getTableHeader().getDefaultRenderer();
        }
        Component comp = renderer.getTableCellRendererComponent(table,
            table.getModel().getColumnName(col), false, false, 0, col);
        int colHeight = comp.getPreferredSize().height + 2 * verticalMargin;
        if (colHeight > screenSize.height)
        {
          // There are some issues on Mac OS and sometimes the preferred size
          // is too big.
          colHeight = 0;
        }
        headerMaxHeight = Math.max(headerMaxHeight, colHeight);
      }
    }

    for (int col=0; col<table.getColumnCount(); col++)
    {
      int colMaxWidth = 8;
      TableColumn tcol = table.getColumnModel().getColumn(col);
      TableCellRenderer renderer = tcol.getHeaderRenderer();

      if (renderer == null && header != null)
      {
        renderer = header.getDefaultRenderer();
      }

      if (renderer != null)
      {
        Component comp = renderer.getTableCellRendererComponent(table,
            table.getModel().getColumnName(col), false, false, 0, col);
        colMaxWidth = comp.getPreferredSize().width  + 2 * horizontalMargin + 8;
      }

      if (colMaxWidth > screenSize.width)
      {
        colMaxWidth = 8;
      }

      for (int row=0; row<table.getRowCount(); row++)
      {
        renderer = table.getCellRenderer(row, col);
        Component comp = table.prepareRenderer(renderer, row, col);
        int colWidth = comp.getPreferredSize().width + 2 * horizontalMargin;
        colMaxWidth = Math.max(colMaxWidth, colWidth);
      }
      tcol.setPreferredWidth(colMaxWidth);
      headerMaxWidth += colMaxWidth;
    }


    if (header != null && header.isVisible())
    {
      header.setPreferredSize(new Dimension(headerMaxWidth, headerMaxHeight));
    }


    int maxRow = table.getRowHeight();
    for (int row=0; row<table.getRowCount(); row++)
    {
      for (int col=0; col<table.getColumnCount(); col++)
      {
        TableCellRenderer renderer = table.getCellRenderer(row, col);
        Component comp = renderer.getTableCellRendererComponent(table,
            table.getModel().getValueAt(row, col), false, false, row, col);
        int colHeight = comp.getPreferredSize().height + 2 * verticalMargin;
        if (colHeight > screenSize.height)
        {
          colHeight = 0;
        }
        maxRow = Math.max(maxRow, colHeight);
      }
    }
    if (maxRow > table.getRowHeight())
    {
      table.setRowHeight(maxRow);
    }
    Dimension d1;
    if (rows == -1)
    {
      d1 = table.getPreferredSize();
    }
    else
    {
      d1 = new Dimension(table.getPreferredSize().width, rows * maxRow);
    }
    table.setPreferredScrollableViewportSize(d1);
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
  public static String applyFont(CharSequence html, Font font)
  {
    return "<span style=\"" + getFontStyle(font) + "\">" + html + "</span>";
  }


  /**
   * Returns an ImageIcon or <CODE>null</CODE> if the path was invalid.
   * @param path the path of the image.
   * @param loader the class loader to use to load the image.  If
   * <CODE>null</CODE> this class class loader will be used.
   * @return an ImageIcon or <CODE>null</CODE> if the path was invalid.
   */
  public static ImageIcon createImageIcon(String path, ClassLoader loader) {
    if (loader == null)
    {
      loader = ControlPanel.class.getClassLoader();
    }
    java.net.URL imgURL = loader.getResource(path);
    return imgURL != null ? new ImageIcon(imgURL) : null;
  }

  /**
   * Returns an ImageIcon or <CODE>null</CODE> if the path was invalid.
   * @param path the path of the image.
   * @return an ImageIcon or <CODE>null</CODE> if the path was invalid.
   */
  public static ImageIcon createImageIcon(String path) {
    return createImageIcon(path, null);
  }

  /**
   * Creates an image icon using an array of bytes that contain the image and
   * specifying the maximum height of the image.
   * @param bytes the byte array.
   * @param maxHeight the maximum height of the image.
   * @param description the description of the image.
   * @param useFast whether a fast algorithm must be used to transform the image
   * or an algorithm with a better result.
   * @return an image icon using an array of bytes that contain the image and
   * specifying the maximum height of the image.
   */
  public static ImageIcon createImageIcon(byte[] bytes, int maxHeight,
      LocalizableMessage description, boolean useFast)
  {
    ImageIcon icon = new ImageIcon(bytes, description.toString());
    if (maxHeight > icon.getIconHeight() || icon.getIconHeight() <= 0)
    {
      return icon;
    }
    int newHeight = maxHeight;
    int newWidth = (newHeight * icon.getIconWidth()) / icon.getIconHeight();
    int algo = useFast ? Image.SCALE_FAST : Image.SCALE_SMOOTH;
    Image scaledImage = icon.getImage().getScaledInstance(newWidth, newHeight, algo);
    return new ImageIcon(scaledImage);
  }

  /**
   * Updates the preferred size of an editor pane.
   * @param pane the panel to be updated.
   * @param nCols the number of columns that the panel must have.
   * @param plainText the text to be displayed (plain text).
   * @param font the font to be used.
   * @param applyBackground whether an error/warning background must be applied
   * to the text or not.
   */
  public static void updatePreferredSize(JEditorPane pane, int nCols,
      String plainText, Font font, boolean applyBackground)
  {
    String wrappedText = wrapText(plainText, nCols);
    wrappedText = wrappedText.replaceAll(ServerConstants.EOL, "<br>");
    if (applyBackground)
    {
      wrappedText = UIFactory.applyErrorBackgroundToHtml(
          Utilities.applyFont(wrappedText, font));
    }
    JEditorPane pane2 = makeHtmlPane(wrappedText, font);
    pane.setPreferredSize(pane2.getPreferredSize());
    JFrame frame = getFrame(pane);
    if (frame != null && frame.isVisible())
    {
      frame.getRootPane().revalidate();
      frame.getRootPane().repaint();
    }
  }

  /**
   * Strips any potential HTML markup from a given string.
   * @param s string to strip
   * @return resulting string
   */
  public static String stripHtmlToSingleLine(String s) {
    String o = null;
    if (s != null) {
      s = s.replaceAll("<br>", " ");
      // This is not a comprehensive solution but addresses
      // the few tags that we have in Resources.properties
      // at the moment.  Note that the following might strip
      // out more than is intended for non-tags like
      // '<your name here>' or for funky tags like
      // '<tag attr="1 > 0">'. See test class for cases that
      // might cause problems.
      o = s.replaceAll("\\<.*?\\>","");
    }
    return o;
  }

  /**
   * Wraps the contents of the provided message using the specified number of
   * columns.
   * @param msg the message to be wrapped.
   * @param nCols the number of columns.
   * @return the wrapped message.
   */
  public static LocalizableMessage wrapHTML(LocalizableMessage msg, int nCols)
  {
    String s = msg.toString();
    StringBuilder sb = new StringBuilder();
    StringBuilder lastLine = new StringBuilder();
    int lastOpenTag = -1;
    boolean inTag = false;
    int lastSpace = -1;
    int lastLineLengthInLastSpace = 0;
    int lastLineLength = 0;
    for (int i=0; i<s.length() ; i++)
    {
      boolean isNormalChar = false;
      char c = s.charAt(i);
      if (c == '<')
      {
        inTag = true;
        lastOpenTag = i;
        lastLine.append(c);
      }
      else if (c == '>')
      {
        if (lastOpenTag != -1)
        {
          inTag = false;
          String tag = s.substring(lastOpenTag, i+1);
          lastOpenTag = -1;
          lastLine.append(c);
          if (isLineBreakTag(tag))
          {
            sb.append(lastLine);
            lastLine.delete(0, lastLine.length());
            lastLineLength = 0;
            lastSpace = -1;
            lastLineLengthInLastSpace = 0;
          }
        }
        else
        {
          isNormalChar = true;
        }
      }
      else if (inTag)
      {
        lastLine.append(c);
      }
      else if (c == HTML_SPACE.charAt(0))
      {
        if (s.length() >= i + HTML_SPACE.length())
        {
          if (HTML_SPACE.equalsIgnoreCase(s.substring(i, i
              + HTML_SPACE.length())))
          {
            if (lastLineLength < nCols)
            {
              // Only count as 1 space
              lastLine.append(HTML_SPACE);
              lastSpace = lastLine.length() - HTML_SPACE.length();
              lastLineLength ++;
              lastLineLengthInLastSpace = lastLineLength;
              i += HTML_SPACE.length() - 1;
            }
            else
            {
              // Insert a line break
              sb.append(lastLine);
              sb.append("<br>");
              lastLine.delete(0, lastLine.length());
              lastLineLength = 0;
              lastSpace = -1;
              lastLineLengthInLastSpace = 0;
              i += HTML_SPACE.length() - 1;
            }
          }
          else
          {
            isNormalChar = true;
          }
        }
        else
        {
          isNormalChar = true;
        }
      }
      else if (c == ' ')
      {
        if (lastLineLength < nCols)
        {
          // Only count as 1 space
          lastLine.append(c);
          lastSpace = lastLine.length() - 1;
          lastLineLength ++;
          lastLineLengthInLastSpace = lastLineLength;
        }
        else
        {
          // Insert a line break
          sb.append(lastLine);
          sb.append("<br>");
          lastLine.delete(0, lastLine.length());
          lastLineLength = 0;
          lastSpace = -1;
          lastLineLengthInLastSpace = 0;
        }
      }
      else
      {
        isNormalChar = true;
      }

      if (isNormalChar)
      {
        if (lastLineLength < nCols)
        {
          lastLine.append(c);
          lastLineLength ++;
        }
        else
        {
          // Check where to insert a line break
          if (lastSpace != -1)
          {
            sb.append(lastLine, 0, lastSpace);
            sb.append("<br>");
            lastLine.delete(0, lastSpace + 1);
            lastLine.append(c);
            lastLineLength = lastLineLength - lastLineLengthInLastSpace + 1;
            lastLineLengthInLastSpace = 0;
            lastSpace = -1;
          }
          else
          {
            // Force the line break.
            sb.append(lastLine);
            sb.append("<br>");
            lastLine.delete(0, lastLine.length());
            lastLine.append(c);
            lastLineLength = 1;
          }
        }
      }
    }
    if (lastLine.length() > 0)
    {
      sb.append(lastLine);
    }
    return LocalizableMessage.raw(sb.toString());
  }

  private static boolean isLineBreakTag(String tag)
  {
    return "<br>".equalsIgnoreCase(tag) ||
    "</br>".equalsIgnoreCase(tag) ||
    "</div>".equalsIgnoreCase(tag) ||
    "<p>".equalsIgnoreCase(tag) ||
    "</p>".equalsIgnoreCase(tag);
  }

  /**
   * Center the component location based on its preferred size. The code
   * considers the particular case of 2 screens and puts the component on the
   * center of the left screen
   *
   * @param comp the component to be centered.
   */
  public static void centerOnScreen(Component comp)
  {
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

    int width = comp.getPreferredSize().width;
    int height = comp.getPreferredSize().height;

    boolean multipleScreen = screenSize.width / screenSize.height >= 2;

    if (multipleScreen)
    {
      comp.setLocation((screenSize.width / 4) - (width / 2),
          (screenSize.height - height) / 2);
    } else
    {
      comp.setLocation((screenSize.width - width) / 2,
          (screenSize.height - height) / 2);
    }
  }

  /**
   * Center the component location of the ref component.
   *
   * @param comp the component to be centered.
   * @param ref the component to be used as reference.
   *
   */
  public static void centerGoldenMean(Window comp, Component ref)
  {
    comp.setLocationRelativeTo(ref);
    // Apply the golden mean
    if (ref != null && ref.isVisible())
    {
      int refY = ref.getY();
      int refHeight = ref.getHeight();
      int compHeight = comp.getPreferredSize().height;

      int newY = refY + (int) (refHeight * 0.3819 - compHeight * 0.5);
      // Check that the new window will be fully visible
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      if (newY > 0 && screenSize.height > newY + compHeight)
      {
        comp.setLocation(comp.getX(), newY);
      }
    }
  }

  /**
   * Returns the parent frame of a component.  <CODE>null</CODE> if this
   * component is not contained in any frame.
   * @param comp the component.
   * @return the parent frame of a component.  <CODE>null</CODE> if this
   * component is not contained in any frame.
   */
  public static JFrame getFrame(Component comp)
  {
    Component parent = comp;
    while (parent != null && !(parent instanceof JFrame))
    {
      parent = parent.getParent();
    }
    return parent != null ? (JFrame) parent : null;
  }

  /**
   * Returns the parent dialog of a component.  <CODE>null</CODE> if this
   * component is not contained in any dialog.
   * @param comp the component.
   * @return the parent dialog of a component.  <CODE>null</CODE> if this
   * component is not contained in any dialog.
   */
  public static Window getParentDialog(Component comp)
  {
    Component parent = comp;
    while (parent != null)
    {
      if (parent instanceof JDialog || parent instanceof JFrame)
      {
        return (Window)parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  /**
   * Unescapes UTF-8 text and generates a String from it.
   * @param v the string in UTF-8 format.
   * @return the string with unescaped characters.
   */
  public static String unescapeUtf8(String v)
  {
    try
    {
      byte[] stringBytes = v.getBytes("UTF-8");
      byte[] decodedBytes = new byte[stringBytes.length];
      int pos = 0;
      for (int i = 0; i < stringBytes.length; i++)
      {
        if (stringBytes[i] == '\\'
                && i + 2 < stringBytes.length
                && StaticUtils.isHexDigit(stringBytes[i+1])
                && StaticUtils.isHexDigit(stringBytes[i+2]))
        {
          // Convert hex-encoded UTF-8 to 16-bit chars.
          byte b;

          byte escapedByte1 = stringBytes[++i];
          switch (escapedByte1)
          {
          case '0':
            b = (byte) 0x00;
            break;
          case '1':
            b = (byte) 0x10;
            break;
          case '2':
            b = (byte) 0x20;
            break;
          case '3':
            b = (byte) 0x30;
            break;
          case '4':
            b = (byte) 0x40;
            break;
          case '5':
            b = (byte) 0x50;
            break;
          case '6':
            b = (byte) 0x60;
            break;
          case '7':
            b = (byte) 0x70;
            break;
          case '8':
            b = (byte) 0x80;
            break;
          case '9':
            b = (byte) 0x90;
            break;
          case 'a':
          case 'A':
            b = (byte) 0xA0;
            break;
          case 'b':
          case 'B':
            b = (byte) 0xB0;
            break;
          case 'c':
          case 'C':
            b = (byte) 0xC0;
            break;
          case 'd':
          case 'D':
            b = (byte) 0xD0;
            break;
          case 'e':
          case 'E':
            b = (byte) 0xE0;
            break;
          case 'f':
          case 'F':
            b = (byte) 0xF0;
            break;
          default:
            throw new RuntimeException("Unexpected byte: "+escapedByte1);
          }

          byte escapedByte2 = stringBytes[++i];
          switch (escapedByte2)
          {
          case '0':
            break;
          case '1':
            b |= 0x01;
            break;
          case '2':
            b |= 0x02;
            break;
          case '3':
            b |= 0x03;
            break;
          case '4':
            b |= 0x04;
            break;
          case '5':
            b |= 0x05;
            break;
          case '6':
            b |= 0x06;
            break;
          case '7':
            b |= 0x07;
            break;
          case '8':
            b |= 0x08;
            break;
          case '9':
            b |= 0x09;
            break;
          case 'a':
          case 'A':
            b |= 0x0A;
            break;
          case 'b':
          case 'B':
            b |= 0x0B;
            break;
          case 'c':
          case 'C':
            b |= 0x0C;
            break;
          case 'd':
          case 'D':
            b |= 0x0D;
            break;
          case 'e':
          case 'E':
            b |= 0x0E;
            break;
          case 'f':
          case 'F':
            b |= 0x0F;
            break;
          default:
            throw new RuntimeException("Unexpected byte: "+escapedByte2);
          }

          decodedBytes[pos++] = b;
        }
        else {
          decodedBytes[pos++] = stringBytes[i];
        }
      }
      return new String(decodedBytes, 0, pos, "UTF-8");
    }
    catch (UnsupportedEncodingException uee)
    {
//    This is a bug, UTF-8 should be supported always by the JVM
      throw new RuntimeException("UTF-8 encoding not supported", uee);
    }
  }

  /**
   * Returns <CODE>true</CODE> if the the provided strings represent the same
   * DN and <CODE>false</CODE> otherwise.
   * @param dn1 the first dn to compare.
   * @param dn2 the second dn to compare.
   * @return <CODE>true</CODE> if the the provided strings represent the same
   * DN and <CODE>false</CODE> otherwise.
   */
  public static boolean areDnsEqual(String dn1, String dn2)
  {
    try
    {
      LdapName name1 = new LdapName(dn1);
      LdapName name2 = new LdapName(dn2);
      return name1.equals(name2);
    } catch (Exception ex)
    {
      return false;
    }
  }


  /**
   * Gets the RDN string for a given attribute name and value.
   * @param attrName the attribute name.
   * @param attrValue the attribute value.
   * @return the RDN string for the attribute name and value.
   */
  public static String getRDNString(String attrName, String attrValue)
  {
    AttributeType attrType = DirectoryServer.getDefaultAttributeType(attrName);
    RDN rdn = new RDN(attrType, attrName, ByteString.valueOf(attrValue));
    return rdn.toString();
  }

  /**
   * Returns the attribute name with no options (or subtypes).
   * @param attrName the complete attribute name.
   * @return the attribute name with no options (or subtypes).
   */

  public static String getAttributeNameWithoutOptions(String attrName)
  {
    int index = attrName.indexOf(";");
    if (index != -1)
    {
      attrName = attrName.substring(0, index);
    }
    return attrName;
  }

  /**
   * Strings any potential "separator" from a given string.
   * @param s string to strip
   * @param separator  the separator string to remove
   * @return resulting string
   */
  private static String stripStringToSingleLine(String s, String separator)
  {
    if (s != null)
    {
      return s.replaceAll(separator, "");
    }
    return null;
  }

  /** The pattern for control characters. */
  private final static Pattern cntrl_pattern = Pattern.compile("\\p{Cntrl}", Pattern.MULTILINE);

  /**
   * Checks if a string contains control characters.
   * @param s : the string to check
   * @return true if s contains control characters, false otherwise
   */
  public static boolean hasControlCharaters(String s)
  {
    return cntrl_pattern.matcher(s).find();
  }

  /**
   * This is a helper method that gets a String representation of the elements
   * in the Collection. The String will display the different elements separated
   * by the separator String.
   *
   * @param col
   *          the collection containing the String.
   * @param separator
   *          the separator String to be used.
   * @return the String representation for the collection.
   */
  public static String getStringFromCollection(Collection<String> col, String separator)
  {
    StringBuilder msg = new StringBuilder();
    for (String m : col)
    {
      if (msg.length() > 0)
      {
        msg.append(separator);
      }
      msg.append(stripStringToSingleLine(m, separator));
    }
    return msg.toString();
  }

  /**
   * Commodity method to get the Name object representing a dn.
   * It is preferable to use Name objects when doing JNDI operations to avoid
   * problems with the '/' character.
   * @param dn the DN as a String.
   * @return a Name object representing the DN.
   * @throws InvalidNameException if the provided DN value is not valid.
   *
   */
  public static Name getJNDIName(String dn) throws InvalidNameException
  {
    Name name = new CompositeName();
    if (dn != null && dn.length() > 0) {
      name.add(dn);
    }
    return name;
  }

  /**
   * Returns the HTML representation of the 'Done' string.
   * @param progressFont the font to be used.
   * @return the HTML representation of the 'Done' string.
   */
  public static String getProgressDone(Font progressFont)
  {
    return applyFont(INFO_CTRL_PANEL_PROGRESS_DONE.get(),
        progressFont.deriveFont(Font.BOLD));
  }

  /**
   * Returns the HTML representation of a message to which some points have
   * been appended.
   * @param plainText the plain text.
   * @param progressFont the font to be used.
   * @return the HTML representation of a message to which some points have
   * been appended.
   */
  public static String getProgressWithPoints(LocalizableMessage plainText,
      Font progressFont)
  {
    return applyFont(plainText.toString(), progressFont)+
    applyFont("&nbsp;.....&nbsp;",
        progressFont.deriveFont(Font.BOLD));
  }

  /**
   * Returns the HTML representation of an error for a given text.
   * @param title the title.
   * @param titleFont the font for the title.
   * @param details the details.
   * @param detailsFont the font to be used for the details.
   * @return the HTML representation of an error for the given text.
   */
  public static String getFormattedError(LocalizableMessage title, Font titleFont,
      LocalizableMessage details, Font detailsFont)
  {
    StringBuilder buf = new StringBuilder();
    buf.append(UIFactory.getIconHtml(UIFactory.IconType.ERROR_LARGE))
        .append(HTML_SPACE).append(HTML_SPACE)
        .append(applyFont(title.toString(), titleFont));
    if (details != null)
    {
      buf.append("<br><br>")
      .append(applyFont(details.toString(), detailsFont));
    }
    return "<form>"+UIFactory.applyErrorBackgroundToHtml(buf.toString())+
    "</form>";
  }

  /**
   * Returns the HTML representation of a success for a given text.
   * @param title the title.
   * @param titleFont the font for the title.
   * @param details the details.
   * @param detailsFont the font to be used for the details.
   * @return the HTML representation of a success for the given text.
   */
  public static String getFormattedSuccess(LocalizableMessage title, Font titleFont,
      LocalizableMessage details, Font detailsFont)
  {
    StringBuilder buf = new StringBuilder();
    buf.append(UIFactory.getIconHtml(UIFactory.IconType.INFORMATION_LARGE))
        .append(HTML_SPACE).append(HTML_SPACE)
        .append(applyFont(title.toString(), titleFont));
    if (details != null)
    {
      buf.append("<br><br>")
      .append(applyFont(details.toString(), detailsFont));
    }
    return "<form>"+UIFactory.applyErrorBackgroundToHtml(buf.toString())+
    "</form>";
  }

  /**
   * Returns the HTML representation of a confirmation for a given text.
   * @param title the title.
   * @param titleFont the font for the title.
   * @param details the details.
   * @param detailsFont the font to be used for the details.
   * @return the HTML representation of a confirmation for the given text.
   */
  public static String getFormattedConfirmation(LocalizableMessage title, Font titleFont,
      LocalizableMessage details, Font detailsFont)
  {
    StringBuilder buf = new StringBuilder();
    buf.append(UIFactory.getIconHtml(UIFactory.IconType.WARNING_LARGE))
        .append(HTML_SPACE).append(HTML_SPACE)
        .append(applyFont(title.toString(), titleFont));
    if (details != null)
    {
      buf.append("<br><br>")
      .append(applyFont(details.toString(), detailsFont));
    }
    return "<form>" + buf + "</form>";
  }


  /**
   * Returns the HTML representation of a warning for a given text.
   * @param title the title.
   * @param titleFont the font for the title.
   * @param details the details.
   * @param detailsFont the font to be used for the details.
   * @return the HTML representation of a success for the given text.
   */
  public static String getFormattedWarning(LocalizableMessage title, Font titleFont,
      LocalizableMessage details, Font detailsFont)
  {
    StringBuilder buf = new StringBuilder();
    buf.append(UIFactory.getIconHtml(UIFactory.IconType.WARNING_LARGE))
        .append(HTML_SPACE).append(HTML_SPACE)
        .append(applyFont(title.toString(), titleFont));
    if (details != null)
    {
      buf.append("<br><br>")
      .append(applyFont(details.toString(), detailsFont));
    }
    return "<form>"+UIFactory.applyErrorBackgroundToHtml(buf.toString())+
    "</form>";
  }

  /**
   * Sets the not available text to a label and associates a help icon and
   * a tooltip explaining that the data is not available because the server is
   * down.
   * @param l the label.
   */
  public static void setNotAvailableBecauseServerIsDown(LabelWithHelpIcon l)
  {
    l.setText(INFO_CTRL_PANEL_NOT_AVAILABLE_LONG_LABEL.get().toString());
    l.setHelpIconVisible(true);
    l.setHelpTooltip(INFO_NOT_AVAILABLE_SERVER_DOWN_TOOLTIP.get().toString());
  }

  /**
   * Sets the not available text to a label and associates a help icon and
   * a tooltip explaining that the data is not available because authentication
   * is required.
   * @param l the label.
   */
  public static void setNotAvailableBecauseAuthenticationIsRequired(
      LabelWithHelpIcon l)
  {
    l.setText(INFO_CTRL_PANEL_NOT_AVAILABLE_LONG_LABEL.get().toString());
    l.setHelpIconVisible(true);
    l.setHelpTooltip(INFO_NOT_AVAILABLE_AUTHENTICATION_REQUIRED_TOOLTIP.get().toString());
  }

  /**
   * Sets the not available text to a label and associates a help icon and
   * a tooltip explaining that the data is not available because the server is
   * down.
   * @param l the label.
   */
  public static void setNotAvailableBecauseServerIsDown(
      SelectableLabelWithHelpIcon l)
  {
    l.setText(INFO_CTRL_PANEL_NOT_AVAILABLE_LONG_LABEL.get().toString());
    l.setHelpIconVisible(true);
    l.setHelpTooltip(INFO_NOT_AVAILABLE_SERVER_DOWN_TOOLTIP.get().toString());
  }

  /**
   * Sets the not available text to a label and associates a help icon and
   * a tooltip explaining that the data is not available because authentication
   * is required.
   * @param l the label.
   */
  public static void setNotAvailableBecauseAuthenticationIsRequired(
      SelectableLabelWithHelpIcon l)
  {
    l.setText(INFO_CTRL_PANEL_NOT_AVAILABLE_LONG_LABEL.get().toString());
    l.setHelpIconVisible(true);
    l.setHelpTooltip(INFO_NOT_AVAILABLE_AUTHENTICATION_REQUIRED_TOOLTIP.get().toString());
  }

  /**
   * Updates a label by setting a warning icon and a text.
   * @param l the label to be updated.
   * @param text the text to be set on the label.
   */
  public static void setWarningLabel(JLabel l, LocalizableMessage text)
  {
    l.setText(text.toString());
    if (warningIcon == null)
    {
      warningIcon =
        createImageIcon("org/opends/quicksetup/images/warning_medium.gif");
      warningIcon.setDescription(
          INFO_WARNING_ICON_ACCESSIBLE_DESCRIPTION.get().toString());
      warningIcon.getAccessibleContext().setAccessibleName(
          INFO_WARNING_ICON_ACCESSIBLE_DESCRIPTION.get().toString());
    }
    l.setIcon(warningIcon);
    l.setToolTipText(text.toString());
    l.setHorizontalTextPosition(SwingConstants.RIGHT);
  }

  /**
   * Sets the not available text to a label with no icon nor tooltip.
   * @param l the label.
   */
  public static void setNotAvailable(LabelWithHelpIcon l)
  {
    l.setText(INFO_CTRL_PANEL_NOT_AVAILABLE_LONG_LABEL.get().toString());
    l.setHelpIconVisible(false);
    l.setHelpTooltip(null);
  }

  /**
   * Sets the a text to a label with no icon nor tooltip.
   * @param l the label.
   * @param text the text.
   */
  public static void setTextValue(LabelWithHelpIcon l, String text)
  {
    l.setText(text);
    l.setHelpIconVisible(false);
    l.setHelpTooltip(null);
  }

  /**
   * Sets the not available text to a label with no icon nor tooltip.
   * @param l the label.
   */
  public static void setNotAvailable(SelectableLabelWithHelpIcon l)
  {
    l.setText(INFO_CTRL_PANEL_NOT_AVAILABLE_LONG_LABEL.get().toString());
    l.setHelpIconVisible(false);
    l.setHelpTooltip(null);
  }

  /**
   * Sets the a text to a label with no icon nor tooltip.
   * @param l the label.
   * @param text the text.
   */
  public static void setTextValue(SelectableLabelWithHelpIcon l, String text)
  {
    l.setText(text);
    l.setHelpIconVisible(false);
    l.setHelpTooltip(null);
  }

  /**
   * Returns the server root directory (the path where the server is installed).
   * @return the server root directory (the path where the server is installed).
   */
  static File getServerRootDirectory()
  {
    if (rootDirectory == null)
    {
      // This allows testing of configuration components when the OpenDJ.jar
      // in the classpath does not necessarily point to the server's
      String installRoot = System.getProperty("org.opends.quicksetup.Root");

      if (installRoot == null) {
        installRoot = getInstallPathFromClasspath();
      }
      rootDirectory = new File(installRoot);
    }
    return rootDirectory;
  }

  /**
   * Returns the instance root directory (the path where the instance is
   * installed).
   * @param installPath The installRoot path.
   * @return the instance root directory (the path where the instance is
   *         installed).
   */
  public static File getInstanceRootDirectory(String installPath)
  {
    if (instanceRootDirectory == null)
    {
      instanceRootDirectory = new File(
        Utils.getInstancePathFromInstallPath(installPath));
    }
    return instanceRootDirectory;
  }

  /**
   * Returns the path of the installation of the directory server.  Note that
   * this method assumes that this code is being run locally.
   * @return the path of the installation of the directory server.
   */
  public static String getInstallPathFromClasspath()
  {
    String installPath = null;

    /* Get the install path from the Class Path */
    String sep = System.getProperty("path.separator");
    String[] classPaths = System.getProperty("java.class.path").split(sep);
    String path = getInstallPath(classPaths);
    if (path != null) {
      File f = new File(path).getAbsoluteFile();
      File librariesDir = f.getParentFile();

      /*
       * Do a best effort to avoid having a relative representation (for
       * instance to avoid having ../../../).
       */
      try
      {
        installPath = librariesDir.getParentFile().getCanonicalPath();
      }
      catch (IOException ioe)
      {
        // Best effort
        installPath = librariesDir.getParent();
      }
    }
    return installPath;
  }

  private static String getInstallPath(String[] classPaths)
  {
    for (String classPath : classPaths)
    {
      final String normPath = classPath.replace(File.separatorChar, '/');
      if (normPath.endsWith(OPENDJ_BOOTSTRAP_CLIENT_JAR_RELATIVE_PATH)
          || normPath.endsWith(OPENDJ_BOOTSTRAP_JAR_RELATIVE_PATH))
      {
        return classPath;
      }
    }
    return null;
  }

  /**
   * Returns <CODE>true</CODE> if the server located in the provided path
   * is running and <CODE>false</CODE> otherwise.
   * @param serverRootDirectory the path where the server is installed.
   * @return <CODE>true</CODE> if the server located in the provided path
   * is running and <CODE>false</CODE> otherwise.
   */
  public static boolean isServerRunning(File serverRootDirectory)
  {
    String lockFileName = ServerConstants.SERVER_LOCK_FILE_NAME + ServerConstants.LOCK_FILE_SUFFIX;
    String lockPathRelative = Installation.LOCKS_PATH_RELATIVE;
    File locksPath = new File(serverRootDirectory, lockPathRelative);
    String lockFile = new File(locksPath, lockFileName).getAbsolutePath();
    StringBuilder failureReason = new StringBuilder();
    try {
      if (LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        LockFileManager.releaseLock(lockFile, failureReason);
        return false;
      }
      return true;
    }
    catch (Throwable t) {
      // Assume that if we cannot acquire the lock file the
      // server is running.
      return true;
    }
  }

  private static final String VALID_SCHEMA_SYNTAX =
    "abcdefghijklmnopqrstuvwxyz0123456789-";

  /**
   * Returns <CODE>true</CODE> if the provided string can be used as objectclass
   * name and <CODE>false</CODE> otherwise.
   * @param s the string to be analyzed.
   * @return <CODE>true</CODE> if the provided string can be used as objectclass
   * name and <CODE>false</CODE> otherwise.
   */
  private static boolean isValidObjectclassName(String s)
  {
    if (s == null || s.length() == 0)
    {
      return false;
    }

    final StringCharacterIterator iter = new StringCharacterIterator(s, 0);
    char c = iter.first();
    while (c != CharacterIterator.DONE)
    {
      if (VALID_SCHEMA_SYNTAX.indexOf(Character.toLowerCase(c)) == -1)
      {
        return false;
      }
      c = iter.next();
    }
    return true;
  }

  /**
   * Returns <CODE>true</CODE> if the provided string can be used as attribute
   * name and <CODE>false</CODE> otherwise.
   * @param s the string to be analyzed.
   * @return <CODE>true</CODE> if the provided string can be used as attribute
   * name and <CODE>false</CODE> otherwise.
   */
  public static boolean isValidAttributeName(String s)
  {
    return isValidObjectclassName(s);
  }

  /**
   * Returns the representation of the VLV index as it must be used in the
   * command-line.
   * @param index the VLV index.
   * @return the representation of the VLV index as it must be used in the
   * command-line.
   */
  public static String getVLVNameInCommandLine(VLVIndexDescriptor index)
  {
    return "vlv."+index.getName();
  }

  /**
   * Returns a string representing the VLV index in a cell.
   * @param index the VLV index to be represented.
   * @return the string representing the VLV index in a cell.
   */
  public static String getVLVNameInCellRenderer(VLVIndexDescriptor index)
  {
    return INFO_CTRL_PANEL_VLV_INDEX_CELL.get(index.getName()).toString();
  }

  private static final String[] standardSchemaFileNames =
  {
      "00-core.ldif", "01-pwpolicy.ldif", "03-changelog.ldif",
      "03-uddiv3.ldif", "05-solaris.ldif"
  };

  private static final String[] configurationSchemaOrigins =
  {
      "OpenDJ Directory Server", "OpenDS Directory Server",
      "Sun Directory Server", "Microsoft Active Directory"
  };

  private static final String[] standardSchemaOrigins =
  {
      "Sun Java System Directory Server", "Solaris Specific", "X.501"
  };

  private static final String[] configurationSchemaFileNames =
  {
      "02-config.ldif", "06-compat.ldif"
  };

  /**
   * Returns <CODE>true</CODE> if the provided schema element is part of the
   * standard and <CODE>false</CODE> otherwise.
   * @param fileElement the schema element.
   * @return <CODE>true</CODE> if the provided schema element is part of the
   * standard and <CODE>false</CODE> otherwise.
   */
  public static boolean isStandard(SchemaFileElement fileElement)
  {
    final String fileName = getSchemaFile(fileElement);
    if (fileName != null)
    {
      return contains(standardSchemaFileNames, fileName) || fileName.toLowerCase().contains("-rfc");
    }
    else if (fileElement instanceof CommonSchemaElements)
    {
      String xOrigin = getOrigin(fileElement);
      if (xOrigin != null)
      {
        return contains(standardSchemaOrigins, xOrigin) || xOrigin.startsWith("RFC ") || xOrigin.startsWith("draft-");
      }
    }
    return false;
  }

  /**
   * Returns <CODE>true</CODE> if the provided schema element is part of the
   * configuration and <CODE>false</CODE> otherwise.
   * @param fileElement the schema element.
   * @return <CODE>true</CODE> if the provided schema element is part of the
   * configuration and <CODE>false</CODE> otherwise.
   */
  public static boolean isConfiguration(SchemaFileElement fileElement)
  {
    String fileName = getSchemaFile(fileElement);
    if (fileName != null)
    {
      return contains(configurationSchemaFileNames, fileName);
    }
    else if (fileElement instanceof CommonSchemaElements)
    {
      String xOrigin = getOrigin(fileElement);
      if (xOrigin != null)
      {
        return contains(configurationSchemaOrigins, xOrigin);
      }
    }
    return false;
  }

  private static boolean contains(String[] names, String toFind)
  {
    for (String name : names)
    {
      if (toFind.equals(name))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the origin of the provided schema element.
   * @param element the schema element.
   * @return the origin of the provided schema element.
   */
  public static String getOrigin(SchemaFileElement element)
  {
    return CommonSchemaElements.getSingleValueProperty(
        element, ServerConstants.SCHEMA_PROPERTY_ORIGIN);
  }

  /**
   * Returns the string representation of an attribute syntax.
   * @param syntax the attribute syntax.
   * @return the string representation of an attribute syntax.
   */
  public static String getSyntaxText(Syntax syntax)
  {
    String syntaxName = syntax.getName();
    String syntaxOID = syntax.getOID();
    if (syntaxName != null)
    {
      return syntaxName + " - " + syntaxOID;
    }
    return syntaxOID;
  }

  /**
   * Returns <CODE>true</CODE> if the provided attribute has image syntax and
   * <CODE>false</CODE> otherwise.
   * @param attrName the name of the attribute.
   * @param schema the schema.
   * @return <CODE>true</CODE> if the provided attribute has image syntax and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean hasImageSyntax(String attrName, Schema schema)
  {
    attrName = Utilities.getAttributeNameWithoutOptions(attrName).toLowerCase();
    if ("photo".equals(attrName))
    {
      return true;
    }
    // Check all the attributes that we consider binaries.
    if (schema != null)
    {
      AttributeType attr = schema.getAttributeType(attrName);
      if (attr != null)
      {
        String syntaxOID = attr.getSyntax().getOID();
        return SchemaConstants.SYNTAX_JPEG_OID.equals(syntaxOID);
      }
    }
    return false;
  }

  /**
   * Returns <CODE>true</CODE> if the provided attribute has binary syntax and
   * <CODE>false</CODE> otherwise.
   * @param attrName the name of the attribute.
   * @param schema the schema.
   * @return <CODE>true</CODE> if the provided attribute has binary syntax and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean hasBinarySyntax(String attrName, Schema schema)
  {
    return attrName.toLowerCase().contains(";binary")
        || hasAnySyntax(attrName, schema, binarySyntaxOIDs);
  }

  /**
   * Returns <CODE>true</CODE> if the provided attribute has password syntax and
   * <CODE>false</CODE> otherwise.
   * @param attrName the name of the attribute.
   * @param schema the schema.
   * @return <CODE>true</CODE> if the provided attribute has password syntax and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean hasPasswordSyntax(String attrName, Schema schema)
  {
    return hasAnySyntax(attrName, schema, passwordSyntaxOIDs);
  }

  private static boolean hasAnySyntax(String attrName, Schema schema, String[] oids)
  {
    if (schema != null)
    {
      attrName = Utilities.getAttributeNameWithoutOptions(attrName).toLowerCase();
      AttributeType attr = schema.getAttributeType(attrName);
      if (attr != null)
      {
        return contains(oids, attr.getSyntax().getOID());
      }
    }
    return false;
  }

  /**
   * Returns the string representation of a matching rule.
   * @param matchingRule the matching rule.
   * @return the string representation of a matching rule.
   */
  public static String getMatchingRuleText(MatchingRule matchingRule)
  {
    String nameOrOID = matchingRule.getNameOrOID();
    String oid = matchingRule.getOID();
    if (!nameOrOID.equals(oid))
    {
      // This is the name only
      return nameOrOID + " - " + oid;
    }
    return oid;
  }

  /**
   * Returns the InitialLdapContext to connect to the administration connector
   * of the server using the information in the ControlCenterInfo object (which
   * provides the host and administration connector port to be used) and some
   * LDAP credentials.
   * It also tests that the provided credentials have enough rights to read the
   * configuration.
   * @param controlInfo the object which provides the connection parameters.
   * @param bindDN the base DN to be used to bind.
   * @param pwd the password to be used to bind.
   * @return the InitialLdapContext connected to the server.
   * @throws NamingException if there was a problem connecting to the server
   * or the provided credentials do not have enough rights.
   * @throws ConfigReadException if there is an error reading the configuration.
   */
  public static InitialLdapContext getAdminDirContext(
      ControlPanelInfo controlInfo, String bindDN, String pwd)
  throws NamingException, ConfigReadException
  {
    String usedUrl = controlInfo.getAdminConnectorURL();
    if (usedUrl == null)
    {
      throw new ConfigReadException(
          ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
    }

    InitialLdapContext ctx = createLdapsContext(usedUrl,
        bindDN, pwd, controlInfo.getConnectTimeout(), null,
        controlInfo.getTrustManager(), null);
    // Search for the config to check that it is the directory manager.
    checkCanReadConfig(ctx);
    return ctx;
  }


  /**
   * Returns the InitialLdapContext to connect to the server using the
   * information in the ControlCenterInfo object (which provides the host, port
   * and protocol to be used) and some LDAP credentials.  It also tests that
   * the provided credentials have enough rights to read the configuration.
   * @param controlInfo the object which provides the connection parameters.
   * @param bindDN the base DN to be used to bind.
   * @param pwd the password to be used to bind.
   * @return the InitialLdapContext connected to the server.
   * @throws NamingException if there was a problem connecting to the server
   * or the provided credentials do not have enough rights.
   * @throws ConfigReadException if there is an error reading the configuration.
   */
  public static InitialLdapContext getUserDataDirContext(
      ControlPanelInfo controlInfo,
      String bindDN, String pwd) throws NamingException, ConfigReadException
  {
    InitialLdapContext ctx;
    String usedUrl;
    if (controlInfo.connectUsingStartTLS())
    {
      usedUrl = controlInfo.getStartTLSURL();
      if (usedUrl == null)
      {
        throw new ConfigReadException(
            ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
      }
      ctx = Utils.createStartTLSContext(usedUrl,
          bindDN, pwd, controlInfo.getConnectTimeout(), null,
          controlInfo.getTrustManager(), null);
    }
    else if (controlInfo.connectUsingLDAPS())
    {
      usedUrl = controlInfo.getLDAPSURL();
      if (usedUrl == null)
      {
        throw new ConfigReadException(
            ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
      }
      ctx = createLdapsContext(usedUrl,
          bindDN, pwd, controlInfo.getConnectTimeout(), null,
          controlInfo.getTrustManager(), null);
    }
    else
    {
      usedUrl = controlInfo.getLDAPURL();
      if (usedUrl == null)
      {
        throw new ConfigReadException(
            ERR_COULD_NOT_FIND_VALID_LDAPURL.get());
      }
      ctx = createLdapContext(usedUrl,
          bindDN, pwd, controlInfo.getConnectTimeout(), null);
    }

    checkCanReadConfig(ctx);
    return ctx;
  }

  /**
   * Checks that the provided connection can read cn=config.
   * @param ctx the connection to be tested.
   * @throws NamingException if an error occurs while reading cn=config.
   */
  private static void checkCanReadConfig(InitialLdapContext ctx)
  throws NamingException
  {
    // Search for the config to check that it is the directory manager.
    SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(SearchControls.OBJECT_SCOPE);
    searchControls.setReturningAttributes(new String[] { SchemaConstants.NO_ATTRIBUTES });
    NamingEnumeration<SearchResult> sr =
      ctx.search("cn=config", "objectclass=*", searchControls);
    try
    {
      while (sr.hasMore())
      {
        sr.next();
      }
    }
    finally
    {
      sr.close();
    }
  }

  /**
   * Ping the specified InitialLdapContext.
   * This method sends a search request on the root entry of the DIT
   * and forward the corresponding exception (if any).
   * @param ctx the InitialLdapContext to be "pinged".
   * @throws NamingException if the ping could not be performed.
   */
  public static void pingDirContext(InitialLdapContext ctx)
  throws NamingException {
    SearchControls sc = new SearchControls(
        SearchControls.OBJECT_SCOPE,
        0, // count limit
        0, // time limit
        new String[0], // No attributes
        false, // Don't return bound object
        false // Don't dereference link
    );
    ctx.search("", "objectClass=*", sc);
  }

  /**
   * Deletes a configuration subtree using the provided configuration handler.
   * @param confHandler the configuration handler to be used to delete the
   * subtree.
   * @param dn the DN of the subtree to be deleted.
   * @throws OpenDsException if an error occurs.
   * @throws ConfigException if an error occurs.
   */
  public static void deleteConfigSubtree(ConfigHandler confHandler, DN dn)
  throws OpenDsException, ConfigException
  {
    ConfigEntry confEntry = confHandler.getConfigEntry(dn);
    if (confEntry != null)
    {
      // Copy the values to avoid problems with this recursive method.
      ArrayList<DN> childDNs = new ArrayList<DN>(confEntry.getChildren().keySet());
      for (DN childDN : childDNs)
      {
        deleteConfigSubtree(confHandler, childDN);
      }
      confHandler.deleteEntry(dn, null);
    }
  }


  /**
   * Sets the required icon to the provided label.
   * @param label the label to be updated.
   */
  public static void setRequiredIcon(JLabel label)
  {
    if (requiredIcon == null)
    {
      requiredIcon =
        createImageIcon(IconPool.IMAGE_PATH+"/required.gif");
      requiredIcon.setDescription(
          INFO_REQUIRED_ICON_ACCESSIBLE_DESCRIPTION.get().toString());
      requiredIcon.getAccessibleContext().setAccessibleName(
          INFO_REQUIRED_ICON_ACCESSIBLE_DESCRIPTION.get().toString());
    }
    label.setIcon(requiredIcon);
    label.setHorizontalTextPosition(SwingConstants.LEADING);
  }


  /**
   * Updates the scrolls with the provided points.
   * This method uses SwingUtilities.invokeLater so it can be also called
   * outside the event thread.
   * @param pos the scroll and points.
   */
  public static void updateViewPositions(final ViewPositions pos)
  {
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        for (int i=0; i<pos.size(); i++)
        {
          pos.getScrollPane(i).getViewport().setViewPosition(pos.getPoint(i));
        }
      }
    });
  }

  /**
   * Gets the view positions object for the provided component.  This includes
   * all the scroll panes inside the provided component.
   * @param comp the component.
   * @return the view positions for the provided component.
   */
  public static ViewPositions getViewPositions(Component comp)
  {
    ViewPositions pos = new ViewPositions();
    if (comp instanceof Container)
    {
      updateContainedViewPositions((Container)comp, pos);
    }
    return pos;
  }

  /**
   * Returns the scrolpane where the provided component is contained.
   * <CODE>null</CODE> if the component is not contained in any scrolpane.
   * @param comp the component.
   * @return the scrolpane where the provided component is contained.
   */
  public static JScrollPane getContainingScroll(Component comp)
  {
    JScrollPane scroll = null;
    Container parent = comp.getParent();
    while (scroll == null && parent != null)
    {
      if (parent instanceof JScrollPane)
      {
        scroll = (JScrollPane)parent;
      }
      else
      {
        parent = parent.getParent();
      }
    }
    return scroll;
  }

  private static void updateContainedViewPositions(Container comp,
      ViewPositions pos)
  {
    if (comp instanceof JScrollPane)
    {
      JScrollPane scroll = (JScrollPane)comp;
      Point p = scroll.getViewport().getViewPosition();
      pos.add(scroll, p);
    }
    else
    {
      for (int i=0; i<comp.getComponentCount(); i++)
      {
        Component child = comp.getComponent(i);
        if (child instanceof Container)
        {
          updateContainedViewPositions((Container)child, pos);
        }
      }
    }
  }

  private static Object getFirstMonitoringValue(CustomSearchResult sr, String attrName)
  {
    if (sr != null)
    {
      List<Object> values = sr.getAttributeValues(attrName);
      if (values != null && values.size() > 0)
      {
        Object o = values.iterator().next();
        try
        {
          return Long.parseLong(o.toString());
        }
        catch (Throwable t1)
        {
          try
          {
            return Double.parseDouble(o.toString());
          }
          catch (Throwable t2)
          {
            // Cannot convert it, just return it
            return o;
          }
        }
      }
    }
    return null;
  }

  /**
   * Returns the first value as a String for a given attribute in the provided
   * entry.
   *
   * @param sr
   *          the entry. It may be <CODE>null</CODE>.
   * @param attrName
   *          the attribute name.
   * @return the first value as a String for a given attribute in the provided
   *         entry.
   */
  public static String getFirstValueAsString(CustomSearchResult sr, String attrName)
  {
    if (sr != null)
    {
      final List<Object> values = sr.getAttributeValues(attrName);
      if (values != null && !values.isEmpty())
      {
        final Object o = values.get(0);
        if (o != null)
        {
          return String.valueOf(o);
        }
      }
    }
    return null;
  }

  /**
   * Returns the monitoring value in a String form to be displayed to the user.
   * @param attr the attribute to analyze.
   * @param monitoringEntry the monitoring entry.
   * @return the monitoring value in a String form to be displayed to the user.
   */
  public static String getMonitoringValue(MonitoringAttributes attr,
      CustomSearchResult monitoringEntry)
  {
    String monitoringValue = getFirstValueAsString(monitoringEntry, attr.getAttributeName());
    if (monitoringValue == null)
    {
      return NO_VALUE_SET.toString();
    }
    else if (isNotImplemented(attr, monitoringEntry))
    {
      return NOT_IMPLEMENTED.toString();
    }
    else if (attr.isNumericDate())
    {
      if ("0".equals(monitoringValue))
      {
        return NO_VALUE_SET.toString();
      }
      Long l = Long.parseLong(monitoringValue);
      Date date = new Date(l);
      return ConfigFromDirContext.formatter.format(date);
    }
    else if (attr.isTime())
    {
      if ("-1".equals(monitoringValue))
      {
        return NO_VALUE_SET.toString();
      }
      return monitoringValue;
    }
    else if (attr.isGMTDate())
    {
      try
      {
        Date date = ConfigFromDirContext.utcParser.parse(monitoringValue);
        return ConfigFromDirContext.formatter.format(date);
      }
      catch (Throwable t)
      {
        return monitoringValue;
      }
    }
    else if (attr.isValueInBytes())
    {
      Long l = Long.parseLong(monitoringValue);
      long mb = l / (1024 * 1024);
      long kbs = (l - mb * 1024 * 1024) / 1024;
      return INFO_CTRL_PANEL_MEMORY_VALUE.get(mb, kbs).toString();
    }
    return monitoringValue;
  }

  /**
   * Returns <CODE>true</CODE> if the provided monitoring value represents the
   * non implemented label and <CODE>false</CODE> otherwise.
   * @param attr the attribute to analyze.
   * @param monitoringEntry the monitoring entry.
   * @return <CODE>true</CODE> if the provided monitoring value represents the
   * non implemented label and <CODE>false</CODE> otherwise.
   */
  private static boolean isNotImplemented(MonitoringAttributes attr,
      CustomSearchResult monitoringEntry)
  {
    String monitoringValue = getFirstValueAsString(monitoringEntry, attr.getAttributeName());
    if (attr.isNumeric() && monitoringValue != null)
    {
      try
      {
        Long.parseLong(monitoringValue);
        return false;
      }
      catch (Throwable t)
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Adds a click tool tip listener to the provided component.
   * @param comp the component.
   */
  public static void addClickTooltipListener(JComponent comp)
  {
    comp.addMouseListener(new ClickTooltipDisplayer());
  }

  /**
   * Updates a combo box model with a number of items.
   * The method assumes that is being called from the event thread.
   * @param newElements the new items for the combo box model.
   * @param model the combo box model to be updated.
   */
  public static void updateComboBoxModel(Collection<?> newElements,
      DefaultComboBoxModel model)
  {
    updateComboBoxModel(newElements, model, null);
  }

  /**
   * Updates a combo box model with a number of items.
   * The method assumes that is being called from the event thread.
   * @param newElements the new items for the combo box model.
   * @param model the combo box model to be updated.
   * @param comparator the object that will be used to compare the objects in
   * the model.  If <CODE>null</CODE>, the equals method will be used.
   */
  public static void updateComboBoxModel(Collection<?> newElements,
      DefaultComboBoxModel model,
      Comparator<Object> comparator)
  {
    boolean changed = newElements.size() != model.getSize();
    if (!changed)
    {
      int i = 0;
      for (Object newElement : newElements)
      {
        if (comparator != null)
        {
          changed =
            comparator.compare(newElement, model.getElementAt(i)) != 0;
        }
        else
        {
          changed = !newElement.equals(model.getElementAt(i));
        }
        if (changed)
        {
          break;
        }
        i++;
      }
    }
    if (changed)
    {
      Object selected = model.getSelectedItem();
      model.removeAllElements();
      boolean selectDefault = false;
      for (Object newElement : newElements)
      {
        model.addElement(newElement);
      }
      if (selected != null)
      {
        if (model.getIndexOf(selected) != -1)
        {
          model.setSelectedItem(selected);
        }
        else
        {
          selectDefault = true;
        }
      }
      else
      {
        selectDefault = true;
      }
      if (selectDefault)
      {
        for (int i=0; i<model.getSize(); i++)
        {
          Object o = model.getElementAt(i);
          if (o instanceof CategorizedComboBoxElement
              && ((CategorizedComboBoxElement)o).getType() == CategorizedComboBoxElement.Type.CATEGORY)
          {
            continue;
          }
          model.setSelectedItem(o);
          break;
        }
      }
    }
  }

  /**
   * Indicates if the provided matching rule is an equality matching rule.
   *
   * @param matchingRule
   *            The matching rule.
   * @return {@code true} if this matching rule is an equality mathing rule.
   */
  public static boolean isEqualityMatchingRule(MatchingRule matchingRule) {
    return false;
  }

  /**
   * Indicates if the provided matching rule is an approximate matching rule.
   *
   * @param matchingRule
   *            The matching rule.
   * @return {@code true} if this matching rule is an approximate mathing rule.
   */
  public static boolean isApproximateMatchingRule(MatchingRule matchingRule) {
    return false;
  }

  /**
   * Indicates if the provided matching rule is a substring matching rule.
   *
   * @param matchingRule
   *            The matching rule.
   * @return {@code true} if this matching rule is a substring mathing rule.
   */
  public static boolean isSubstringMatchingRule(MatchingRule matchingRule) {
    return false;
  }

  /**
   * Indicates if the provided matching rule is an ordering matching rule.
   *
   * @param matchingRule
   *            The matching rule.
   * @return {@code true} if this matching rule is an ordering mathing rule.
   */
  public static boolean isOrderingMatchingRule(MatchingRule matchingRule) {
    return false;
  }

  /**
   * Computes the possible comparison results for monitoring information.
   *
   * @param monitor1
   *          the first monitor to compare
   * @param monitor2
   *          the second monitor to compare
   * @param possibleResults
   *          where possible results are output
   * @param attrNames
   *          the names for which to compute possible comparison results
   */
  public static void computeMonitoringPossibleResults(CustomSearchResult monitor1, CustomSearchResult monitor2,
      ArrayList<Integer> possibleResults, Collection<String> attrNames)
  {
    for (String attrName : attrNames)
    {
      int possibleResult;
      if (monitor1 == null)
      {
        if (monitor2 == null)
        {
          possibleResult = 0;
        }
        else
        {
          possibleResult = -1;
        }
      }
      else if (monitor2 == null)
      {
        possibleResult = 1;
      }
      else
      {
        Object v1 = getFirstValue(monitor1, attrName);
        Object v2 = getFirstValue(monitor2, attrName);
        if (v1 == null)
        {
          if (v2 == null)
          {
            possibleResult = 0;
          }
          else
          {
            possibleResult = -1;
          }
        }
        else if (v2 == null)
        {
          possibleResult = 1;
        }
        else if (v1 instanceof Number)
        {
          if (v2 instanceof Number)
          {
            if ((v1 instanceof Double) || (v2 instanceof Double))
            {
              double n1 = ((Number) v1).doubleValue();
              double n2 = ((Number) v2).doubleValue();
              if (n1 > n2)
              {
                possibleResult = 1;
              }
              else if (n1 < n2)
              {
                possibleResult = -1;
              }
              else
              {
                possibleResult = 0;
              }
            }
            else
            {
              long n1 = ((Number) v1).longValue();
              long n2 = ((Number) v2).longValue();
              if (n1 > n2)
              {
                possibleResult = 1;
              }
              else if (n1 < n2)
              {
                possibleResult = -1;
              }
              else
              {
                possibleResult = 0;
              }
            }
          }
          else
          {
            possibleResult = 1;
          }
        }
        else if (v2 instanceof Number)
        {
          possibleResult = -1;
        }
        else
        {
          possibleResult = v1.toString().compareTo(v2.toString());
        }
      }
      possibleResults.add(possibleResult);
    }
  }

  private static Object getFirstValue(CustomSearchResult monitor, String attrName)
  {
    for (String attr : monitor.getAttributeNames())
    {
      if (attr.equalsIgnoreCase(attrName))
      {
        return getFirstMonitoringValue(monitor, attrName);
      }
    }
    return null;
  }

  /**
   * Throw the first exception of the list (if any).
   *
   * @param <E>
   *          The exception type
   * @param exceptions
   *          A list of exceptions.
   * @throws E
   *           The first element of the provided list (if the list is not
   *           empty).
   */
  public static <E extends Exception> void throwFirstFrom(List<? extends E> exceptions) throws E
  {
    if (!exceptions.isEmpty())
    {
      throw exceptions.get(0);
    }
  }

  /**
   * Initialize the configuration framework.
   */
  public static void initializeConfigurationFramework()
  {
    if (!ConfigurationFramework.getInstance().isInitialized())
    {
      try
      {
        ConfigurationFramework.getInstance().initialize();
      }
      catch (ConfigException e)
      {
        final LocalizableMessage message = ERROR_CTRL_PANEL_INITIALIZE_CONFIG_OFFLINE.get(e.getLocalizedMessage());
        logger.error(message);
        throw new RuntimeException(message.toString(), e);
      }
    }
  }

  /** Initialize the legacy configuration framework. */
  public static void initializeLegacyConfigurationFramework()
  {
    try
    {
      final ClassLoaderProvider provider = ClassLoaderProvider.getInstance();
      if (!provider.isEnabled())
      {
        provider.enable();
      }
    }
    catch (Exception e)
    {
      final LocalizableMessage message = ERROR_CTRL_PANEL_INITIALIZE_CONFIG_OFFLINE.get(e.getLocalizedMessage());
      logger.error(message);
      throw new RuntimeException(message.toString(), e);
    }

  }

}
