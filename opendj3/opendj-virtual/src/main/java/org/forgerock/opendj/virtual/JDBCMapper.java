/*
 * CDDL HEADER START
 * 
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
 * CDDL HEADER END
 *
 * Copyright 2013 ForgeRock AS.
 * Portions Copyright 2013 IS4U.
 */

package org.forgerock.opendj.virtual;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ErrorResultIOException;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.ldif.EntryReader;

public final class JDBCMapper {

  final private java.sql.Connection jdbcConnection;
  final private Connection ldapConnection;
  private String dbName;
  final private ArrayList<String> tablesList = new ArrayList<String>();
  final private ArrayList<String> baseDNList = new ArrayList<String>();
  final private Map<String, ArrayList<String>> tableColumnsMap = new HashMap<String, ArrayList<String>>();
  final private Map<String, String> tableColumnNullableMap = new HashMap<String, String>();
  final private Map<String, String> tableColumnDataTypeMap = new HashMap<String, String>();
  final private Map<String, ArrayList<String>> organizationalUnitsMap = new HashMap<String, ArrayList<String>>();
  final private Map<String, ArrayList<String>> organizationalUnitAttributesMap = new HashMap<String, ArrayList<String>>();
  private Map<String, String> SQLToLDAPMap = new HashMap<String, String>();

  JDBCMapper(final Connection jdbcconnection, final Connection ldapconnection) {
    this.jdbcConnection = ((JDBCConnection) jdbcconnection).getSqlConnection();
    this.ldapConnection = ldapconnection;
  }

  public void setDbName(String DBName){
    this.dbName = DBName;
  }

  public void closeConnections() throws SQLException{
    this.jdbcConnection.close();
    this.ldapConnection.close();
  }

  public void fillMaps() throws ErrorResultException, SQLException, IOException{
    fillBaseDNList();
    fillTablesList();
    fillTableColumnsMap();
    fillOrganizationalUnitsAndAttributesMap();
  }

  private void fillBaseDNList() throws IOException{
    final EntryReader reader = this.ldapConnection.search(" ", SearchScope.SINGLE_LEVEL, "objectClass=*");
    while(reader.hasNext()){
      baseDNList.add(reader.readEntry().getName().toString());
    }
  }

  private void fillTablesList() throws SQLException{
    final String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE " +
        "TABLE_TYPE = 'BASE TABLE' AND TABLE_SCHEMA = '" + this.dbName + "'";
    final Statement st = this.jdbcConnection.createStatement();
    final ResultSet rs = st.executeQuery(sql);

    while(rs.next()){
      tablesList.add(rs.getString(1));
    }
  }

  private void fillTableColumnsMap() throws SQLException, ErrorResultIOException, SearchResultReferenceIOException{
    for(int i =0; i < tablesList.size(); i++){
      final String tableName = tablesList.get(i);
      final String sql = "SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + tableName + "'";
      final Statement st = jdbcConnection.createStatement();
      final ResultSet rs = st.executeQuery(sql);
      final ArrayList<String> columnsList = new ArrayList<String>();

      String columnName = "", columnNullable = "", columnDataType = "";
      while(rs.next()){
        columnName = rs.getString(4);
        columnNullable = rs.getString(7);
        columnDataType = rs.getString(8);
        columnsList.add(columnName);
        tableColumnNullableMap.put(tableName + ":" + columnName, columnNullable);
        tableColumnDataTypeMap.put(tableName + ":" + columnName, columnDataType);
      }
      tableColumnsMap.put(tableName, columnsList);
    }
  }

  public ArrayList<String> getTables() throws SQLException{
    return tablesList;
  }

  public ArrayList<String> getBaseDNs(){
    return baseDNList;
  }

  public ArrayList<String> getTableColumns(final String tableName) throws SQLException{
    final ArrayList<String> tableColumnsList = tableColumnsMap.get(tableName);
    return tableColumnsList;
  }   

  public boolean getTableColumnNullable(final String tableName, final String columnName){
    final String mappingKey = tableName + ":" + columnName;
    final String nullable = tableColumnNullableMap.get(mappingKey);
    final String nullableString = "NO";
    if(nullable.equals(nullableString)) return false;
    else return true;
  }

  public Object getTableColumnDataType(final String tableName, final String columnName){
    final String mappingKey = tableName + ":" + columnName;
    final String mappingValue = tableColumnDataTypeMap.get(mappingKey);
    final String objectTypeString = "int";
    if(mappingValue.equals(objectTypeString)) return Integer.class;
    return String.class;
  }

  public ArrayList<String> getOrganizationalUnits(final String baseDN){
    return organizationalUnitsMap.get(baseDN);
  }

  public ArrayList<String> getOrganizationalUnitAttributes(final String baseDN, final String organizationalUnitName){
    return organizationalUnitAttributesMap.get(baseDN + ":" + organizationalUnitName);
  }

  private void fillOrganizationalUnitsAndAttributesMap() throws ErrorResultException, ErrorResultIOException, SearchResultReferenceIOException{
    for(int i = 0; i < baseDNList.size(); i++){
      final String baseDN = baseDNList.get(i);
      final ConnectionEntryReader baseDNReader = ldapConnection.search(baseDN, SearchScope.SINGLE_LEVEL, "objectClass=*");

      ArrayList<String> organizationalUnitsList = new ArrayList<String>();
      while (baseDNReader.hasNext()) {
        final SearchResultEntry entry = baseDNReader.readEntry();
        final String organizationalUnitDNName = entry.getName().toString();
        final String organizationalUnitName = DN.valueOf(organizationalUnitDNName).rdn().getFirstAVA().getAttributeValue().toString();
        organizationalUnitsList.add(organizationalUnitName);

        final ConnectionEntryReader DNReader = ldapConnection.search(organizationalUnitDNName, SearchScope.SINGLE_LEVEL, "objectClass=*");
        final ArrayList<String> attributesList;

        if(DNReader.hasNext()){
          final Iterator<Attribute> it = DNReader.readEntry().getAllAttributes().iterator();
          attributesList = new ArrayList<String>();

          while(it.hasNext()){
            final Attribute att = it.next();
            attributesList.add(att.getAttributeDescriptionAsString());
          }
        }
        else{
          attributesList = new ArrayList<String>();
          attributesList.add("");
        }
        organizationalUnitAttributesMap.put(baseDN + ":" + organizationalUnitName, attributesList);
      }
      organizationalUnitsMap.put(baseDN, organizationalUnitsList);
    }
  }

  public void addCurrentMapToMapping(final String tableName, final String[] columnNames, final String baseDN, final String OUName, final String[] attributeNames){
    String mappingKey, mappingValue;

    for(int i = 0; i < columnNames.length; i++){
      mappingKey = tableName + ":" + columnNames[i];
      mappingValue = baseDN + ":" + OUName + ":" + attributeNames[i];
      SQLToLDAPMap.put(mappingKey, mappingValue);
    }
  }

  public Map<String, String> getMapping(){
    return SQLToLDAPMap;
  }

  public Map<String, String> loadCurrentMapFromMapping(final String tableName, String baseDN, String DN) {
    baseDN = baseDN.replace(" ", "");
    String mappingKey, mappingValue;
    final ArrayList<String> tableColumnsList = tableColumnsMap.get(tableName);
    final Map<String, String> currentMap = new HashMap<String, String>();

    for(int i = 0; i < tableColumnsList.size(); i++){
      mappingKey = tableName + ":" + tableColumnsList.get(i);
      if(!SQLToLDAPMap.containsKey(mappingKey)) continue;
      mappingValue = SQLToLDAPMap.get(mappingKey);
      if(mappingValue.contains(baseDN + ":" + DN)) currentMap.put(mappingKey, mappingValue);
    }
    return currentMap;
  }

  public void loadMappingConfig(Map<String, String> m){
    this.SQLToLDAPMap = m;
  }

  public String getTableNameFromMapping(String DN, final String organizationalUnit) {
    DN = DN.replace(" ", "");
    for (Entry<String, String> entry : SQLToLDAPMap.entrySet()) {
      final String mappingValue = entry.getValue();
      if (mappingValue.contains(DN + ":" + organizationalUnit)) {
        final String mappingKey = entry.getKey();
        final String stringSplitter[] = mappingKey.split(":");
        final String tableName = stringSplitter[0];
        return tableName;
      }
    }
    return null;
  }

  public String getColumnNameFromMapping(final String tableName, String baseDN, final String organizationalUnitName, final String attributeName) {
    baseDN = baseDN.replace(" ", "");
    for (Entry<String, String> entry : SQLToLDAPMap.entrySet()) {
      final String mappingValue = entry.getValue();
      if (mappingValue.equals(baseDN + ":" + organizationalUnitName + ":" + attributeName)) {
        final String mappingKey = entry.getKey();
        if(mappingKey.contains(tableName)){
          final String stringSplitter[] = mappingKey.split(":");
          final String columnName = stringSplitter[1];
          return columnName;
        }
      }
    }
    return null;
  }
}


