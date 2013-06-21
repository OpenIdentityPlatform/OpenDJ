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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ErrorResultIOException;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.ldif.EntryReader;

/**
 * The mapping for the JDBCConnection which holds information about the database
 * and directory structures.
 */
public final class JDBCMapper 
{
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

  /**
   * Creates a new JDBC mapping.
   *
   * @param jdbcconnection
   *            The JDBCConnection for the Database Server.
   * @param ldapconnection
   *            The LDAPConnection for the Directory Server.
   */
  JDBCMapper(final Connection jdbcconnection, final Connection ldapconnection) 
  {
    this.jdbcConnection = ((JDBCConnection) jdbcconnection).getSqlConnection();
    this.ldapConnection = ldapconnection;
  }

  /**
   * Sets the SQL database name for this mapping.
   *
   * @param DBName
   *            The database name.            
   */
  public void setDatabaseName(String DBName)
  {
    this.dbName = DBName;
  }

  /**
   * Releases this Connection object's database/directory and JDBC/LDAP resources immediately instead of 
   * waiting for them to be automatically released. 
   * 
   * Calling the method close on a Connection object that is already closed is a no-op. 
   *
   * @throws SQLException
   *            If a database access error occurs.            
   */
  public void closeConnections() throws SQLException
  {
    jdbcConnection.close();
    ldapConnection.close();
  }

  /**
   * Fills this mapping with the structure of the currently connected database.
   *
   * @throws ErrorResultException
   *            If the result code indicates that the request failed for some
   *            reason.
   * @throws SQLException
   *            If a database access error occurs.      
   * @throws IOException
   *            If an I/O exception error occurs.                 
   */
  public void fillMaps() throws ErrorResultException, SQLException, IOException
  {
    fillBaseDNList();
    fillTablesList();
    fillTableColumnsMap();
    fillOrganizationalUnitsAndAttributesMap();
  }

  /**
   * Fills this mapping with the base distinguished names of the currently connected directory.
   * 
   * @throws IOException
   *            If an I/O exception error occurs.                 
   */
  private void fillBaseDNList() throws IOException
  {
    final EntryReader reader = ldapConnection.search(" ", SearchScope.SINGLE_LEVEL, "objectClass=*");
    while(reader.hasNext()){
      baseDNList.add(reader.readEntry().getName().toString());
    }
  }

  /**
   * Fills this mapping with the tables of the currently connected database.
   * 
   * @throws SQLException
   *            If a database access error occurs.                
   */
  private void fillTablesList() throws SQLException
  {
    /*For connection to h2 database, use "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE " +
    "TABLE_TYPE = 'TABLE' AND TABLE_SCHEMA = 'PUBLIC'";*/
    final String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE " +
        "TABLE_TYPE = 'BASE TABLE' AND TABLE_SCHEMA = '" + this.dbName + "'";
    final Statement st = jdbcConnection.createStatement();
    final ResultSet rs = st.executeQuery(sql);

    while(rs.next()){
      tablesList.add(rs.getString(1));
    }
  }

  /**
   * Fills this mapping with the columns for each table of the currently connected database. Also checks
   * the column's data type and not null definition.
   * 
   * @throws SQLException
   *            If the result code indicates that the request failed for some
   *            reason.
   * @throws ErrorResultIOException
   *            If an I/O exception error occurs in the result code. 
   * @throws SearchResultReferenceIOException
   *            If an iteration over a set of search results using a ConnectionEntryReader encounters 
   *            a SearchResultReference.                  
   */
  private void fillTableColumnsMap() throws SQLException, ErrorResultIOException, SearchResultReferenceIOException
  {
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

  /**
   * Fills this mapping with the organizational units and attributes for for each organizational unit
   * of the currently connected directory.
   * 
   * @throws ErrorResultException
   *            If a database access error occurs. 
   * @throws ErrorResultIOException
   *            If an I/O exception error occurs in the result code. 
   * @throws SearchResultReferenceIOException
   *            If an iteration over a set of search results using a ConnectionEntryReader encounters 
   *            a SearchResultReference.                  
   */
  private void fillOrganizationalUnitsAndAttributesMap() throws ErrorResultException, ErrorResultIOException, SearchResultReferenceIOException
  {
    for(int i = 0; i < baseDNList.size(); i++){
      final String baseDN = baseDNList.get(i);
      final ConnectionEntryReader baseDNReader = ldapConnection.search(baseDN, SearchScope.SINGLE_LEVEL, "objectClass=*");

      ArrayList<String> organizationalUnitsList = new ArrayList<String>();
      while (baseDNReader.hasNext()) {
        final SearchResultEntry entry = baseDNReader.readEntry();
        final String organizationalUnitDNName = entry.getName().toString();
        final String organizationalUnitName = DN.valueOf(organizationalUnitDNName).rdn().getFirstAVA().getAttributeValue().toString();
        organizationalUnitsList.add(organizationalUnitName);

        Schema.readSchemaForEntry(ldapConnection, DN.valueOf(organizationalUnitDNName));
        final Collection<AttributeType> attributeTypes = Schema.getCoreSchema().getAttributeTypes();   
        final ArrayList<String> attributesList;

        if(attributeTypes.iterator().hasNext()){
          final Iterator<AttributeType> it = attributeTypes.iterator();
          attributesList = new ArrayList<String>();

          while(it.hasNext()){
            final AttributeType at = it.next();
            attributesList.add(at.getNameOrOID());
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

  /**
   * Returns a list of table names of the currently connected database.
   *
   * @return A list of table names.
   * @throws SQLException
   *            If a database access error occurs.    
   */
  public ArrayList<String> getTables() throws SQLException
  {
    return tablesList;
  }

  /**
   * Returns a list of base distinguished names of the currently connected directory.
   *
   * @return A list of base distinguished names.
   * @throws SQLException
   *            If a database access error occurs.    
   */
  public ArrayList<String> getBaseDNs()
  {
    return baseDNList;
  }

  /**
   * Returns a list of column names in the provided table name of the currently connected database.
   *
   * @param tableName
   *            The table name of the columns to retrieve.
   * @return A list of column names.
   * @throws SQLException
   *            If a database access error occurs.    
   */
  public ArrayList<String> getTableColumns(final String tableName) throws SQLException
  {
    final ArrayList<String> tableColumnsList = tableColumnsMap.get(tableName);
    return tableColumnsList;
  }   

  /**
   * Returns a boolean value which indicates whether the specified column name has NOT NULL defined 
   * in the provided table name of the currently connected database.
   *
   * @param tableName
   *            The table name in which to search.
   * @param columnName
   *            The column name in which to search.
   * @return A boolean value to indicate the value of NOT NULL in the column.
   */
  public boolean getTableColumnNullable(final String tableName, final String columnName)
  {
    final String mappingKey = tableName + ":" + columnName;
    final String nullable = tableColumnNullableMap.get(mappingKey);
    final String nullableString = "NO";
    if(nullable.equals(nullableString)) return false;
    else return true;
  }

  /**
   * Returns an object which indicates the data type of the specified column name in 
   * the provided table name of the currently connected database.
   *
   * @param tableName
   *            The table name in which to search.
   * @param columnName
   *            The column name in which to search.
   * @return A boolean value to indicate the value of NOT NULL in the column.
   */
  public Object getTableColumnDataType(final String tableName, final String columnName)
  {
    final String mappingKey = tableName + ":" + columnName;
    final String mappingValue = tableColumnDataTypeMap.get(mappingKey);
    final String objectTypeString = "int";
    if(mappingValue.equals(objectTypeString)) return Integer.class;
    return String.class;
  }

  /**
   * Returns a list of organizational unit names in the provided base distinguished name 
   * of the currently connected directory.
   *
   * @param baseDN
   *            The base distinguished name of the organizational units to retrieve.
   * @return A list of organizational unit names.
   */
  public ArrayList<String> getOrganizationalUnits(final String baseDN)
  {
    return organizationalUnitsMap.get(baseDN);
  }

  /**
   * Returns a list of attributes within the specified organizational unit name in 
   * the provided base distinguished name of the currently connected directory.
   *
   * @param baseDN
   *            The base distinguished name in which to search.
   * @param organizationalUnitName
   *            The organizational unit name in which to search.
   * @return A a list of attributes.
   */
  public ArrayList<String> getOrganizationalUnitAttributes(final String baseDN, final String organizationalUnitName)
  {
    return organizationalUnitAttributesMap.get(baseDN + ":" + organizationalUnitName);
  }

  /**
   * Create a map with the provided parameters and save it to the mapping.
   *
   * @param tableName
   *            The database table name to save.
   * @param columnNames
   *            The database column names to save.
   * @param baseDN
   *            The directory base distinguished name to save.
   * @param OUName
   *            The directory organizational unit name to save.
   * @param attributeNames
   *            The directory attribute name to save.
   */
  public void addCurrentMapToMapping(final String tableName, final String[] columnNames, final String baseDN, final String OUName, final String[] attributeNames)
  {
    String mappingKey, mappingValue;

    for(int i = 0; i < columnNames.length; i++){
      mappingKey = tableName + ":" + columnNames[i];
      mappingValue = baseDN + ":" + OUName + ":" + attributeNames[i];
      SQLToLDAPMap.put(mappingKey, mappingValue);
    }
  }

  /**
   * Returns a the full mapping of the directory structure to the database structure.
   *
   * @return A full mapping of directory to database structure.
   */
  public Map<String, String> getMapping()
  {
    return SQLToLDAPMap;
  }

  /**
   * Returns a map which holds the provided parameters.
   *
   * @param tableName
   *            The database table name in which to search.
   * @param baseDN
   *            The directory base distinguished name in which to search.
   * @param organizationalUnit
   *            The directory organizational unit to search for.          
   * @return A a list of attributes.
   */
  public Map<String, String> loadCurrentMapFromMapping(final String tableName, String baseDN, String organizationalUnit) 
  {
    baseDN = baseDN.replace(" ", "");
    String mappingKey, mappingValue;
    final ArrayList<String> tableColumnsList = tableColumnsMap.get(tableName);
    final Map<String, String> currentMap = new HashMap<String, String>();

    for(int i = 0; i < tableColumnsList.size(); i++){
      mappingKey = tableName + ":" + tableColumnsList.get(i);
      if(!SQLToLDAPMap.containsKey(mappingKey)) continue;
      mappingValue = SQLToLDAPMap.get(mappingKey);
      if(mappingValue.contains(baseDN + ":" + organizationalUnit)) currentMap.put(mappingKey, mappingValue);
    }
    return currentMap;
  }

  /**
   * Sets the full mapping to the mapping provided.
   *
   * @param mapping
   *            The mapping to which to set the full mapping to.
   */
  public void loadMappingConfig(Map<String, String> mapping)
  {
    this.SQLToLDAPMap = mapping;
  }

  /**
   * Returns the table name from the mapping that corresponds to the parameters provided.
   *
   * @param baseDN
   *            The directory base distinguished name in which to search.
   * @param organizationalUnit
   *            The directory organizational unit to search for.      
   * @return The table name that is mapped to the provided distinguished name.
   */
  public String getTableNameFromMapping(String baseDN, final String organizationalUnit) 
  {
    baseDN = baseDN.replace(" ", "");
    for (Entry<String, String> entry : SQLToLDAPMap.entrySet()) {
      final String mappingValue = entry.getValue();
      if (mappingValue.contains(baseDN + ":" + organizationalUnit)) {
        final String mappingKey = entry.getKey();
        final String stringSplitter[] = mappingKey.split(":");
        final String tableName = stringSplitter[0];
        return tableName;
      }
    }
    return null;
  }

  /**
   * Returns the column name from the mapping that corresponds to the parameters provided.
   *
   * @param tableName
   *            The database table name in which to search.
   * @param baseDN
   *            The directory base distinguished name in which to search.
   * @param organizationalUnitName
   *            The directory organizational unit name in which to search.    
   * @param attributeName
   *            The directory attribute name to search for.     
   * @return The column name that is mapped to the provided attribute name.
   */
  public String getColumnNameFromMapping(final String tableName, String baseDN, final String organizationalUnitName, final String attributeName) 
  {
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
