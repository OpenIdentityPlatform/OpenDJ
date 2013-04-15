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
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ErrorResultIOException;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;

public final class JDBCMapper {
	
	final private java.sql.Connection jdbcConnection;
	final private Connection ldapConnection;
	final private ArrayList<String> tables = new ArrayList<String>();
	final private Map<String, ArrayList<String>> tableColumnsMap = new HashMap<String, ArrayList<String>>();
	final private Map<String, String> tableColumnNullableMap = new HashMap<String, String>();
	final private Map<String, String> tableColumnDataTypeMap = new HashMap<String, String>();
	final private ArrayList <String> organizationalUnits = new ArrayList<String>(); 
	final private Map<String, ArrayList<String>> organizationalUnitAttributesMap = new HashMap<String, ArrayList<String>>();
	private Map<String, String> SQLToLDAPMap = new HashMap<String, String>();
	
	JDBCMapper(final Connection jdbcconnection, final Connection ldapconnection) {
        this.jdbcConnection = ((JDBCConnection) jdbcconnection).getSqlConnection();
        this.ldapConnection = ldapconnection;
    }
	
	public void closeConnections() throws SQLException{
		this.jdbcConnection.close();
		this.ldapConnection.close();
	}
	
	public void fillMaps() throws ErrorResultException, ErrorResultIOException, SearchResultReferenceIOException, SQLException{
		fillTablesList();
		fillTableColumnsMap();
		fillOrganizationalUnitsList();
		fillOrganizationalUnitAttributesMap();
	}
    
    private void fillTablesList() throws SQLException{
    	final String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE " +
    			"TABLE_TYPE = 'BASE TABLE' AND TABLE_SCHEMA='opendj_db'";
		final Statement st = jdbcConnection.createStatement();
		final ResultSet rs = st.executeQuery(sql);
		
		while(rs.next()){
			tables.add(rs.getString(1));
	    }
    }
    
    private void fillTableColumnsMap() throws SQLException, ErrorResultIOException, SearchResultReferenceIOException{
    	for(int i =0; i < tables.size(); i++){
    		final String tableName = tables.get(i);
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
		return tables;
    }
    
    public ArrayList<String> getTableColumns(String tableName) throws SQLException{
    	final ArrayList<String> tableColumnsList = tableColumnsMap.get(tableName);
		return tableColumnsList;
    }   
    
    public boolean getTableColumnNullable(String tableName, String columnName){
    	String mappingKey = tableName + ":" + columnName;
    	String nullable = tableColumnNullableMap.get(mappingKey);
    	if(nullable.equals("NO")) return false;
    	else return true;
    }
    
    public Object getTableColumnDataType(String tableName, String columnName){
    	String mappingKey = tableName + ":" + columnName;
		String mappingValue = tableColumnDataTypeMap.get(mappingKey);
		if(mappingValue.equals("int")) return Integer.class;
		return String.class;
    }
    
    public ArrayList<String> getOrganizationalUnits(){
    	return organizationalUnits;
    }
    
    public ArrayList<String> getOrganizationalUnitAttributes(String organizationalUnitName){
    	final ArrayList<String> organizationalUnitAttributesList = organizationalUnitAttributesMap.get(organizationalUnitName);
    	return organizationalUnitAttributesList;
    }
    
    private void fillOrganizationalUnitsList() throws ErrorResultException, ErrorResultIOException, SearchResultReferenceIOException{
        ConnectionEntryReader reader = ldapConnection.search("dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "ou=*");

        while (reader.hasNext()) {
        	final SearchResultEntry entry = reader.readEntry();
            final Iterator<Attribute> it = entry.getAllAttributes().iterator();
            organizationalUnits.add(it.next().firstValueAsString());
        }
    }
    
    private void fillOrganizationalUnitAttributesMap() throws ErrorResultIOException, SearchResultReferenceIOException{
    	for(int i=0; i < organizationalUnits.size(); i++){
        	final String organizationalUnitName = organizationalUnits.get(i);
        	final ConnectionEntryReader reader = ldapConnection.search("ou=" + organizationalUnitName + ",dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "uid=*");
        	
        	if(reader.hasNext()){
        		final Iterator<Attribute> it = reader.readEntry().getAllAttributes().iterator();
        		final ArrayList <String> attributesList = new ArrayList <String>();
        		
            	while(it.hasNext()){
            		final Attribute att = it.next();
                	attributesList.add(att.getAttributeDescriptionAsString());
            	}            
            	organizationalUnitAttributesMap.put(organizationalUnitName, attributesList);
        	}
        }
    }
    
    public void addCurrentMapToMapping(String tableName, String[] columnNames, String OUName, String[] attributeNames){
    	String mappingKey, mappingValue;
    	
    	for(int i = 0; i < columnNames.length; i++){
    		mappingKey = tableName + ":" + columnNames[i];
    		mappingValue = OUName + ":" + attributeNames[i];
        	SQLToLDAPMap.put(mappingKey, mappingValue);
    	}
    }
    
    public Map<String, String> getMapping(){
    	return SQLToLDAPMap;
    }

	public Map<String, String> loadCurrentMapFromMapping(String tableName) {
		String mappingKey, mappingValue;
		final ArrayList<String> tableColumnsList = tableColumnsMap.get(tableName);
    	Map<String, String> currentMap = new HashMap<String, String>();
		
    	for(int i = 0; i < tableColumnsList.size(); i++){
    		mappingKey = tableName + ":" + tableColumnsList.get(i);
    		if(!SQLToLDAPMap.containsKey(mappingKey)) continue;
    		mappingValue = SQLToLDAPMap.get(mappingKey);
    		currentMap.put(mappingKey, mappingValue);
    	}
    	return currentMap;
	}
	
	public void loadMappingConfig(Map<String, String> m){
		this.SQLToLDAPMap = m;
	}

	public String getTableNameFromMapping(String organizationalUnit) {
		for (Entry<String, String> entry : SQLToLDAPMap.entrySet()) {
			String mappingValue = entry.getValue();
	        if (mappingValue.contains(organizationalUnit)) {
	        	String mappingKey = entry.getKey();
	        	String stringSplitter[] = mappingKey.split(":");
	        	String tableName = stringSplitter[0];
	            return tableName;
	        }
	    }
		return null;
	}

	public String getColumnNameFromMapping(String tableName, String organizationalUnitName, String attributeName) {
		for (Entry<String, String> entry : SQLToLDAPMap.entrySet()) {
			String mappingValue = entry.getValue();
	        if (mappingValue.equals(organizationalUnitName + ":" + attributeName)) {
	        	String mappingKey = entry.getKey();
	        	if(mappingKey.contains(tableName)){
	        		String stringSplitter[] = mappingKey.split(":");
		        	String columnName = stringSplitter[1];
		            return columnName;
	        	}
	        }
	    }
		return null;
	}
}


   