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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.virtual;

import java.awt.event.*;
import java.awt.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.table.*;

import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ErrorResultIOException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("serial")
class MyTable extends AbstractTableModel {

  public MyTable(){}

  private String[] columnNames = {"Columns",
    "Attributes"
  };

  public Object[][] data = {};

  public int getColumnCount() {
    return columnNames.length;
  }
  public int getRowCount() {
    return data.length;
  }
  public String getColumnName(int col) {
    return columnNames[col];
  }
  public Object getValueAt(int row, int col) {
    return data[row][col];
  }

  public boolean isCellEditable(int row, int col) {
    if (col == 1) {
      return true;
    } else {
      return false;
    }
  }       

  public void setValueAt(Object value, int row, int col) {
    data[row][col] = value;
    fireTableCellUpdated(row, col);
  }
}

@SuppressWarnings("serial")
public class GUIMap extends JPanel implements ActionListener {
  //Definition of global values and items that are part of the GUI.
  static JFrame frame = new JFrame("Mapping configuration");
  private JPanel totalGUI, inputPane, dataPane,btnPane;
  private JComboBox<Object> tableList, directoryOUList;
  private JButton btnSetMap, btnResetMap, btnSave, btnQuitConnection;
  private JLabel lblDatabaseTables, lblSelectOu, lblEditDataSource;
  private JDBCMapper JDBCM;
  private JTable table; 
  private JScrollPane scrollPane;
  private MappingConfigurationManager mcm;
  static DefaultTableModel model;
  static String[] tableNameList;
  static String[] OUNameList;
  private Border loweredetched;

  public GUIMap(JDBCConnectionFactory jdbc, LDAPConnectionFactory ldap) throws ErrorResultException, ErrorResultIOException, SearchResultReferenceIOException, SQLException
  {
    JDBCM = new JDBCMapper(jdbc.getConnection(), ldap.getConnection());
    JDBCM.fillMaps();
    List<String> tableStringList = JDBCM.getTables();
    List<String> OUStringList = JDBCM.getOrganizationalUnits();
    tableNameList = new String[tableStringList.size()];
    OUNameList = new String[OUStringList.size()];
    mcm = new MappingConfigurationManager(JDBCM);
    JDBCM.loadMappingConfig(mcm.loadMapping());

    for (int i = 0; i < tableStringList.size(); i++){
      tableNameList[i] = tableStringList.get(i);
    }

    for (int i = 0; i < OUStringList.size(); i++){
      OUNameList[i] = OUStringList.get(i);
    }

    frame.setResizable(false);
    frame.setContentPane(createContentPane());    
    frame.setEnabled(true);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(524, 505);
    frame.setLocationRelativeTo(null);
    frame.setVisible(true); 
  }

  /**
   * @return
   * @throws SQLException 
   */
   public JPanel createContentPane () throws SQLException{
     totalGUI = new JPanel();
     totalGUI.setLayout(null);
     loweredetched = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);

     lblEditDataSource = new JLabel("Edit data source");
     lblEditDataSource.setFont(lblEditDataSource.getFont().deriveFont(lblEditDataSource.getFont().getStyle() | Font.BOLD, lblEditDataSource.getFont().getSize() + 2f));
     lblEditDataSource.setBounds(16, 6, 134, 16);
     totalGUI.add(lblEditDataSource);

     inputPane = new JPanel();
     inputPane.setBounds(6, 26, 507, 61);
     inputPane.setBorder(loweredetched);
     totalGUI.add(inputPane);
     inputPane.setLayout(null);

     tableList = new JComboBox<Object>(tableNameList);
     tableList.setToolTipText("Click to change table");
     tableList.setMaximumRowCount(4);
     tableList.setBounds(6,29,230,27);
     tableList.addActionListener(this);
     inputPane.add(tableList);   

     directoryOUList = new JComboBox<Objects>(OUNameList);
     directoryOUList.setToolTipText("Click to change OU");
     directoryOUList.setMaximumRowCount(4);
     directoryOUList.setBounds(271, 29, 230, 27);
     inputPane.add(directoryOUList);

     lblDatabaseTables = new JLabel("Select table");
     lblDatabaseTables.setBounds(11, 6, 128, 16);
     inputPane.add(lblDatabaseTables);

     lblSelectOu = new JLabel("Select OU\n");
     lblSelectOu.setBounds(276, 6, 128, 16);
     inputPane.add(lblSelectOu);
     directoryOUList.addActionListener(this);

     dataPane = new JPanel();
     dataPane.setBounds(6, 99, 511, 308);
     totalGUI.add(dataPane);
     dataPane.setLayout(new GridLayout(0, 1, 0, 0));

     //Create the scroll pane and add the table to it.
     scrollPane = new JScrollPane();
     fillTable();
     UpdateTable();
     dataPane.add(scrollPane);

     table.setPreferredScrollableViewportSize(new Dimension(500, 70));
     table.setFillsViewportHeight(true);
     table.getTableHeader().setReorderingAllowed(false);
     table.getTableHeader().setResizingAllowed(false);

     btnPane = new JPanel();
     btnPane.setLayout(null);
     btnPane.setBounds(6, 411, 512, 68);
     totalGUI.add(btnPane);

     btnSetMap = new JButton("Save current mapping");
     btnSetMap.setBounds(87, 3, 169, 28);
     btnSetMap.addActionListener(this);
     btnPane.add(btnSetMap);

     btnResetMap = new JButton("Reset current mapping");
     btnResetMap.setBounds(270, 3, 169, 28);
     btnResetMap.addActionListener(this);
     btnPane.add(btnResetMap);

     btnSave = new JButton("Save to file");
     btnSave.setBounds(127, 36, 129, 28);
     btnSave.addActionListener(this);
     btnPane.add(btnSave);

     btnQuitConnection = new JButton("Close");
     btnQuitConnection.setBounds(270, 36, 129, 28);
     btnQuitConnection.addActionListener(this);
     btnPane.add(btnQuitConnection);

     return totalGUI;
   }

   private void fillTable() throws SQLException {
     String tableName = tableList.getSelectedItem().toString();
     ArrayList<String> columnsList = JDBCM.getTableColumns(tableName);
     Object[][] rowData = new Object[columnsList.size()][2];

     for(int i = 0; i < columnsList.size(); i++){
       String columnName = columnsList.get(i);

       if(!JDBCM.getTableColumnNullable(tableName, columnName)){
         columnName = columnName.concat("(*)");
       }
       rowData[i][0] = columnName;
       rowData[i][1] = "----";
     }

     MyTable myTable = new MyTable();
     myTable.data = rowData;
     table = new JTable(myTable);
     scrollPane.setViewportView(table);

     createTableComboBoxList();
   }
   private void createTableComboBoxList(){
     JComboBox<String> comboBox = new JComboBox<String>();
     ArrayList<String> attributesList = JDBCM.getOrganizationalUnitAttributes(directoryOUList.getSelectedItem().toString());

     for(int i = 0; i < attributesList.size(); i++){
       comboBox.addItem(attributesList.get(i));
     }
     TableColumn directoryColumn = table.getColumnModel().getColumn(1);
     directoryColumn.setCellEditor(new DefaultCellEditor(comboBox));
     DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
     directoryColumn.setCellRenderer(renderer);
   }

   public void UpdateTable(){
     String tableName = tableList.getSelectedItem().toString();
     Map<String, String> currentMap = JDBCM.loadCurrentMapFromMapping(tableName);

     if(!currentMap.isEmpty()){
       String mappingKey, mappingValue;
       MyTable mt = (MyTable) table.getModel();

       for(int i = 0; i < mt.data.length; i++){
         mappingKey = tableName + ":" + mt.data[i][0].toString();
         mappingKey = mappingKey.replace("(*)", "");
         mappingValue = currentMap.get(mappingKey);
         String[] stringSplitter = mappingValue.split((":"));
         mt.data[i][1] = stringSplitter[1];
       }
     }
   }

   public void actionPerformed(ActionEvent e) {
     Object source = e.getSource();
     if((source == tableList) || (source == directoryOUList))
     {
       try {
         fillTable();
         createTableComboBoxList();
         if(source == tableList){
           UpdateTable();
         }
       } catch (SQLException e1) {
         e1.printStackTrace();
       }
     }
     else if(source == btnSetMap){
       MyTable mt = (MyTable) table.getModel();
       String tableName = tableList.getSelectedItem().toString();
       String OUName = directoryOUList.getSelectedItem().toString();
       String [] columnStrings = new String[mt.data.length], attributeStrings = new String[mt.data.length];
       boolean success = true;
       String columnName, attributeName;

       for(int i = 0; i < mt.data.length; i++){
         columnName = mt.data[i][0].toString();
         attributeName = mt.data[i][1].toString();

         if(columnName.contains("(*)") && attributeName.equals("----")){
           success = false;
           JOptionPane.showMessageDialog(frame, "Map all the required fields!", "Error", JOptionPane.ERROR_MESSAGE);
           break;
         }
         columnStrings[i] = columnName.replace("(*)", "");
         attributeStrings[i] = attributeName;
       }
       if(success) JDBCM.addCurrentMapToMapping(tableName, columnStrings, OUName, attributeStrings);
     }
     else if(source == btnSave){
       mcm.saveMapping(JDBCM.getMapping());
     }
     else if(source == btnResetMap){
       try {
         fillTable();
       } catch (SQLException e1) {
         e1.printStackTrace();
       }
     }
     else if(source == btnQuitConnection){
       try {
         JDBCM.closeConnections();
       } catch (SQLException e1) {
         e1.printStackTrace();
       }
       System.exit(0);
     }
   }
}