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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.forgerock.opendj.ldap.AbstractSynchronousConnection;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;

public final class JDBCConnection extends AbstractSynchronousConnection {
  // For connection to h2 database, use driverName = "org.h2.driver";
  private final String driverName = "com.mysql.jdbc.Driver";
  private java.sql.Connection connection;
  private String connectionUrl;
  private JDBCMapper jdbcm;
  private MappingConfigurationManager mcm;
  private List<Entry> searchEntries = new ArrayList<Entry>();

  JDBCConnection(final String connectionURL) 
  {
    this.connectionUrl = connectionURL;
  }   

  public void initializeMapper(JDBCMapper jdbcmapper) throws SQLException, ErrorResultException, IOException
  {
    jdbcm = jdbcmapper;
    jdbcm.fillMaps();
    mcm = new MappingConfigurationManager(jdbcm);
    jdbcm.loadMappingConfig(mcm.loadMapping());
  }

  public java.sql.Connection getSqlConnection()
  {
    return connection;
  }

  @Override
  public Result add(AddRequest request) throws ErrorResultException
  {
    Result r;
    try {
      final DN DN = request.getName();
      final RDN OU = DN.parent().rdn();
      final String organizationalUnitName = OU.getFirstAVA().getAttributeValue().toString();
      final String baseDN = DN.parent(2).toString();
      final String tableName = jdbcm.getTableNameFromMapping(baseDN, organizationalUnitName);
      final Map<String, Object> columnValuesMap = new HashMap<String, Object>();
      final Iterable<Attribute> attributesCollection = request.getAllAttributes();
      final Iterator<Attribute> attributeIter = attributesCollection.iterator();

      while(attributeIter.hasNext()){
        final Attribute att = attributeIter.next();
        final Iterator<ByteString> valueIter = att.iterator();
        final String attributeName = att.getAttributeDescriptionAsString();
        final String columnName = jdbcm.getColumnNameFromMapping(tableName, baseDN, organizationalUnitName, attributeName);
        String columnValue = "";

        if (columnName == null) continue;

        while(valueIter.hasNext()){
          columnValue = columnValue.concat(valueIter.next().toString());
        }
        columnValuesMap.put(columnName, columnValue);
      }
      final ArrayList<String>columnList = jdbcm.getTableColumns(tableName);
      String columnNamesString = " (";
      String columnValuesString = " (";

      for(int i = 0; i < columnList.size(); i++){

        if (i > 0){
          columnNamesString = columnNamesString.concat(", ");
          columnValuesString = columnValuesString.concat(", ");
        }
        final String columnName = columnList.get(i);
        final Object columnDataType = jdbcm.getTableColumnDataType(tableName, columnName);
        Object columnValue = columnValuesMap.get(columnName);

        if(columnValue == null){

          if(columnDataType.equals(Integer.class)) columnValue = "0";
          else columnValue = "Default Value";
        }

        if(columnDataType.equals(Integer.class)) columnValue = Integer.parseInt(columnValue.toString());

        columnNamesString = columnNamesString.concat(columnName);
        columnValuesString = columnValuesString.concat("'" + columnValue + "'");
      }
      columnNamesString = columnNamesString.concat(")");
      columnValuesString = columnValuesString.concat(")");

      final Statement st = connection.createStatement();
      final String sql = "INSERT INTO " + tableName + columnNamesString + " VALUES" + columnValuesString;
      st.executeUpdate(sql);
      r = Responses.newResult(ResultCode.SUCCESS);

    } catch (SQLException e) {
      System.out.println(e.toString());
      r = Responses.newResult(ResultCode.OPERATIONS_ERROR);
    }
    return r;
  }

  @Override
  public void addConnectionEventListener(ConnectionEventListener listener)
  {
    // TODO Auto-generated method stub
  }

  @Override
  public BindResult bind(BindRequest request) throws ErrorResultException
  {
    BindResult r;
    final String userName = request.getName();
    final String userPass;
    byte[] password = null;

    if (request instanceof SimpleBindRequest) {
      password = ((SimpleBindRequest) request).getPassword();
    } else {
      r = Responses.newBindResult(ResultCode.PROTOCOL_ERROR);
      return r;
    }
    userPass = new String(password);

    try {
      Class.forName(driverName);
      this.connection = DriverManager
          .getConnection(this.connectionUrl,userName , userPass);
    } catch (ClassNotFoundException e) {
      System.out.println(e.toString());
      r = Responses.newBindResult(ResultCode.OTHER);
      return r;
    } catch (SQLException e) {
      System.out.println(e.toString());
      r = Responses.newBindResult(ResultCode.CLIENT_SIDE_CONNECT_ERROR);
      return r;
    }
    r = Responses.newBindResult(ResultCode.SUCCESS);
    return r;
  }

  @Override
  public void close(UnbindRequest request, String reason)
  {
    if(connection != null){
      try
      {
        connection.close();
      }
      catch (SQLException e)
      {
        e.printStackTrace();
      }
    }
  }

  @Override
  public CompareResult compare(CompareRequest request) throws ErrorResultException
  {
    CompareResult cr;
    try
    {
      final DN DN = request.getName();
      final RDN rDN = DN.rdn();
      final String filterAttributeName = rDN.getFirstAVA().getAttributeType().getNameOrOID();
      final String filterAttributeValue = rDN.getFirstAVA().getAttributeValue().toString();
      final RDN OU = DN.parent(1).rdn();
      final String OUName = OU.getFirstAVA().getAttributeValue().toString();
      final String baseDN = DN.parent(2).toString();
      final String tableName = jdbcm.getTableNameFromMapping(baseDN, OUName);
      final String columnName = jdbcm.getColumnNameFromMapping(tableName, baseDN, OUName, filterAttributeName);  
      final String compareAttributeName = request.getAttributeDescription().toString();
      final String compareAttributeValue = request.getAssertionValueAsString();
      final String compareColumnName = jdbcm.getColumnNameFromMapping(tableName, baseDN, OUName, compareAttributeName);

      final Statement st = connection.createStatement();
      final String sql = "SELECT * FROM " + tableName + " WHERE " + columnName + "='" + filterAttributeValue + "' AND " + compareColumnName + "='" +  compareAttributeValue + "'";
      final ResultSet rs = st.executeQuery(sql);
      if(rs.first()) cr = Responses.newCompareResult(ResultCode.COMPARE_TRUE);
      else cr = Responses.newCompareResult(ResultCode.COMPARE_FALSE);
    }
    catch (SQLException e)
    {
      cr = Responses.newCompareResult(ResultCode.OPERATIONS_ERROR);
      e.printStackTrace();
    }
    return cr;
  }

  @Override
  public Result delete(DeleteRequest request) throws ErrorResultException
  {
    Result r;
    try{
      final DN DN = request.getName();
      final RDN rDN = DN.rdn();
      final String filterAttributeName = rDN.getFirstAVA().getAttributeType().getNameOrOID();
      final String filterAttributeValue = rDN.getFirstAVA().getAttributeValue().toString();
      final RDN OU = DN.parent(1).rdn();
      final String OUName = OU.getFirstAVA().getAttributeValue().toString();
      final String baseDN = DN.parent(2).toString();
      final String tableName = jdbcm.getTableNameFromMapping(baseDN, OUName);
      final String columnName = jdbcm.getColumnNameFromMapping(tableName, baseDN, OUName, filterAttributeName);

      final Statement st = connection.createStatement();
      final String sql = "DELETE FROM " + tableName + " WHERE " + columnName + "='" + filterAttributeValue + "'";
      st.executeUpdate(sql);
      r = Responses.newResult(ResultCode.SUCCESS);
    } catch (SQLException e) {
      System.out.println(e.toString());
      r = Responses.newResult(ResultCode.OPERATIONS_ERROR);
    }
    return r;
  }

  @Override
  public <R extends ExtendedResult> R extendedRequest(
      ExtendedRequest<R> request, IntermediateResponseHandler handler)
          throws ErrorResultException
          {
    // TODO Auto-generated method stub
    return null;
          }

  @Override
  public boolean isClosed()
  {
    try
    {
      return connection.isClosed();
    }
    catch (SQLException e)
    {
      e.printStackTrace();
      return true;
    }
  }

  @Override
  public boolean isValid()
  {
    try
    {
      return connection.isValid(0);
    }
    catch (SQLException e)
    {
      e.printStackTrace();
      return false;
    }
  }

  @Override
  public Result modify(ModifyRequest request)
  { 
    Result r;
    try{
      final DN DN = request.getName();
      final RDN rDN = DN.rdn();
      final String filterAttributeName = rDN.getFirstAVA().getAttributeType().getNameOrOID();
      final String filterAttributeValue = rDN.getFirstAVA().getAttributeValue().toString();
      final RDN OU = DN.parent(1).rdn();
      final String OUName = OU.getFirstAVA().getAttributeValue().toString();
      final String baseDN = DN.parent(2).toString();
      final String tableName = jdbcm.getTableNameFromMapping(baseDN, OUName);
      final String columnName = jdbcm.getColumnNameFromMapping(tableName, baseDN, OUName, filterAttributeName);
      final List<Modification> modificationList = request.getModifications();
      final ListIterator<Modification> listIter = modificationList.listIterator();
      String modificationString = "";

      while(listIter.hasNext()){
        final Modification modification = listIter.next();
        final ModificationType modificationType = modification.getModificationType();
        final Attribute modificationAttribute = modification.getAttribute();
        final String modificationAttributeName = modificationAttribute.getAttributeDescription().toString();
        final String modificationColumnName = jdbcm.getColumnNameFromMapping(tableName, baseDN, OUName, modificationAttributeName);
        String modificationAttributeValue = "";

        if(modificationType == ModificationType.ADD){
          final Iterator<ByteString> iter = modificationAttribute.iterator();

          int counter = 0;
          while (iter.hasNext()){
            if(counter > 0) modificationAttributeValue = modificationAttributeValue.concat(", ");
            modificationAttributeValue = modificationAttributeValue.concat(iter.next().toString());
            counter++;
          }
          final Object classType = jdbcm.getTableColumnDataType(tableName, modificationColumnName);
          if(classType == Integer.class) modificationAttributeValue = "(case when (" + modificationAttributeName + " = 0) THEN ' "
              + modificationAttributeValue + "' ELSE concat(" + modificationAttributeName + ", ', " + modificationAttributeValue + "') END)";
          else modificationAttributeValue = "(case when (" + modificationAttributeName + " = 'Default Value') THEN ' "
              + modificationAttributeValue + "' ELSE concat(" + modificationAttributeName + ", ', " + modificationAttributeValue + "') END)";
        }        
        else if(modificationType == ModificationType.REPLACE){
          final Iterator<ByteString> iter = modificationAttribute.iterator();

          while (iter.hasNext()){
            modificationAttributeValue = "'" + iter.next().toString() + "'";
          }
        }
        else{
          final boolean nullable = jdbcm.getTableColumnNullable(tableName, modificationColumnName);

          if(nullable == false) throw new SQLException("Cannot delete data from not-nullable column.");
          final Object classType = jdbcm.getTableColumnDataType(tableName, modificationColumnName);

          if(classType == Integer.class) modificationAttributeValue = "'" + Integer.toString(0) + "'";
          else modificationAttributeValue = "'Default Value'";
        }
        modificationString = modificationString.concat(modificationColumnName + "=" + modificationAttributeValue + ", ");
      }
      modificationString = modificationString.substring(0, modificationString.length() - 2);
      final String sql = "UPDATE " + tableName + " SET " + modificationString + " WHERE " + columnName + "='" + filterAttributeValue + "'";

      final Statement st = connection.createStatement();
      st.executeUpdate(sql);
      r = Responses.newResult(ResultCode.SUCCESS);
    }catch (SQLException e){
      System.out.println(e.toString());
      r = Responses.newResult(ResultCode.OPERATIONS_ERROR);
    }
    return r;
  }

  @Override
  public Result modifyDN(ModifyDNRequest request) throws ErrorResultException
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void removeConnectionEventListener(ConnectionEventListener listener)
  {
    // TODO Auto-generated method stub
  }

  private String convertSearchFilter(final Filter filter, final String baseDN, String OUName)
  {
    String filterString = filter.toString();
    int stringIndex = 0;
    int subStringCount = 0;

    while(filterString.charAt(stringIndex) == '('){
      if(filterString.charAt(stringIndex + 2) == '('){
        subStringCount++;
      }
      stringIndex += 2;
    }
    int subStringStartIndex = 2 * subStringCount;
    int subStringEndIndex = 0;
    final String[] subStrings = new String[subStringCount];
    final char[] operationChars = new char[subStringCount];

    while(subStringStartIndex > 0){
      final char operationChar = filterString.charAt(subStringStartIndex - 1);
      subStringEndIndex = filterString.indexOf("))") + 1;
      String subString = filterString.substring(subStringStartIndex, subStringEndIndex);
      subString = subString.replace("()","");

      operationChars[subStringCount - subStringStartIndex / 2] = operationChar;
      subStrings[subStringCount - subStringStartIndex / 2] = subString;

      final String replaceString = filterString.substring(subStringStartIndex - 1, subStringEndIndex);
      filterString = filterString.replace(replaceString, "");

      subStringStartIndex-=2;
    }
    String convertedFilterString = "";

    for(int i = 0; i < subStringCount; i++){
      final char operationChar = operationChars[i];

      if(operationChar == '!'){
        String subString = subStrings[i];

        if(subString.isEmpty()){
          subString = subStrings[i-1];
          subString = subString.replace(">=", ">");
          subString = subString.replace("<=", "<");
          subString = subString.replace("=", "!=");
          subString = subString.replace(">", ">=");
          subString = subString.replace("<", "<=");
          subStrings[i-1] = subString;
        }
        else{
          subString = subString.replace(">=", ">");
          subString = subString.replace("<=", "<");
          subString = subString.replace("=", "!=");
          subString = subString.replace(">", ">=");
          subString = subString.replace("<", "<=");
          subStrings[i] = subString;
        }
      }
    }
    boolean multipleSubStrings = false;

    if(subStringCount > 1) multipleSubStrings = true;

    for(int i = 0; i < subStringCount; i++){
      final char operationChar = operationChars[i];
      String operationString = "";

      if(operationChar == '&'){
        operationString = " AND ";
      }
      else if (operationChar == '|'){
        operationString = " OR ";
      }

      String subString = subStrings[i];
      if (subString.isEmpty()) continue;

      subString = subString.substring(1, subString.length() - 1);
      String[] subStringSplitter = subString.split("\\)\\(");

      for(int j = 0; j < subStringSplitter.length; j++){
        String subStringFilter = subStringSplitter[j];
        final String[] subStringFilterSplitter;

        subStringFilter = subStringFilter.replace("\\02a", "*");
        subStringFilter = subStringFilter.replace("\\028", "(");
        subStringFilter = subStringFilter.replace("\\029", ")");

        if(subStringFilter.contains("!=")) subStringFilterSplitter = subStringFilter.split("!=");
        else if(subStringFilter.contains("<=")) subStringFilterSplitter = subStringFilter.split("<=");
        else if(subStringFilter.contains(">=")) subStringFilterSplitter = subStringFilter.split(">=");
        else subStringFilterSplitter = subStringFilter.split("=");

        final String filterAttributeName = subStringFilterSplitter[0];
        String filterColumnName = null;;
        Object columnDataType = null;

        if(OUName == null){
          final List<String> OUList = jdbcm.getOrganizationalUnits(baseDN);

          for(Iterator<String> iter = OUList.iterator(); iter.hasNext();){
            OUName = iter.next();
            final String tableName = jdbcm.getTableNameFromMapping(baseDN, OUName);
            filterColumnName = jdbcm.getColumnNameFromMapping(tableName, baseDN, OUName, filterAttributeName);

            if(filterColumnName == null) continue;
            else columnDataType = jdbcm.getTableColumnDataType(tableName, filterColumnName);
            break;
          }
        }
        else{
          final String tableName = jdbcm.getTableNameFromMapping(baseDN, OUName);
          filterColumnName = jdbcm.getColumnNameFromMapping(tableName, baseDN, OUName, filterAttributeName);     
          columnDataType = jdbcm.getTableColumnDataType(tableName, filterColumnName);
        }

        if(columnDataType != Integer.class){
          String filterAttributeValue = subStringFilterSplitter[1];
          String filterColumnValue = "'" + filterAttributeValue + "'";
          filterAttributeValue = filterAttributeValue.replace("*", "\\*");
          filterAttributeValue = filterAttributeValue.replace("(", "\\(");
          filterAttributeValue = filterAttributeValue.replace(")", "\\)");

          if(filterColumnValue.length() > 3 && filterColumnValue.contains("*")){
            filterColumnValue = filterColumnValue.replace("*", "%");

            if(subStringFilter.contains("!=")) subStringFilter = subStringFilter.replace("!=", " not like ");
            else subStringFilter = subStringFilter.replace("=", " like ");
          }
          subStringFilter = subStringFilter.replaceFirst(filterAttributeValue, filterColumnValue);
        }

        if(filterColumnName == null) continue;
        subStringFilter = subStringFilter.replaceFirst(filterAttributeName, filterColumnName);

        if(j != 0 || i != 0) convertedFilterString = convertedFilterString.concat(operationString);
        convertedFilterString = convertedFilterString.concat(subStringFilter);
      }

      if(multipleSubStrings && i < subStringCount -1) convertedFilterString = "(" + convertedFilterString + ")";
    }
    return convertedFilterString;
  }

  public List<Entry> getSearchEntries()
  {
    return searchEntries;
  }

  @Override
  public Result search(SearchRequest request, SearchResultHandler handler) throws ErrorResultException
  {
    Result r;
    try{
      final DN DN = request.getName();
      final int DNSize = DN.size();
      final String baseDN = DN.parent(DNSize - 2).toString();
      RDN OU = null;
      RDN rDN = null;
      String OUName = "";
      String rDNAttributeName = "";
      String rDNAttributeValue = "";

      if(DNSize > 3){
        OU = DN.parent(1).rdn();
        OUName = OU.getFirstAVA().getAttributeValue().toString();
        rDN = DN.parent(0).rdn();
        rDNAttributeName = rDN.getFirstAVA().getAttributeType().getNameOrOID();
        rDNAttributeValue = rDN.getFirstAVA().getAttributeValue().toString();
      }
      else if(DNSize > 2){
        OU = DN.parent(0).rdn();
        OUName = OU.getFirstAVA().getAttributeValue().toString();
      }


      final List<String> returnAttributeNames = request.getAttributes();
      final List<String> returnColumnNames = new ArrayList<String>();

      final Filter searchFilter = request.getFilter();
      final String convertedFilterString = convertSearchFilter(searchFilter, baseDN, OUName);

      if(returnAttributeNames != null){

        for(Iterator<String> iter = returnAttributeNames.iterator();iter.hasNext();){

          if(OU == null){
            final String returnAttributeName = iter.next();
            final List<String> OUList = jdbcm.getOrganizationalUnits(baseDN);

            for(Iterator<String> iterOU = OUList.iterator(); iterOU.hasNext();){
              OUName = iterOU.next();
              final String tableName = jdbcm.getTableNameFromMapping(baseDN, OUName);
              final String returnColumnName = jdbcm.getColumnNameFromMapping(tableName, baseDN, OUName, returnAttributeName);

              if(returnColumnName == null) continue;
              else returnColumnNames.add(returnColumnName);
              break;
            }
          }
          else {
            final String returnAttributeName = iter.next();
            final String tableName = jdbcm.getTableNameFromMapping(baseDN, OUName);
            final String returnColumnName = jdbcm.getColumnNameFromMapping(tableName, baseDN, OUName, returnAttributeName);
            returnColumnNames.add(returnColumnName);
          }
        }
      }
      String selectString = "";
      String fromString = "";
      String whereString = convertedFilterString;

      if(returnColumnNames.isEmpty()) selectString = "*";

      if(OU == null){
        final List<String> OUList = jdbcm.getOrganizationalUnits(baseDN);

        for(int i = 0; i < OUList.size(); i++){
          final String currentOU = OUList.get(i);
          final String currentTable = jdbcm.getTableNameFromMapping(baseDN, currentOU);

          if(currentTable == null) continue;
          final List<String> currentTableColumnNames = jdbcm.getTableColumns(currentTable);

          if(!fromString.isEmpty()) fromString = fromString.concat(",");
          fromString = fromString.concat(currentTable);

          for(int j = 0; j < returnColumnNames.size(); j++){
            final String returnColumnName = returnColumnNames.get(j);

            if(currentTableColumnNames.contains(returnColumnName)){

              if(!selectString.isEmpty()) selectString = selectString.concat(",");
              selectString = selectString.concat(currentTable + "." + returnColumnName);
            }
          }
        }
      }
      else{
        final String selectTable = jdbcm.getTableNameFromMapping(baseDN, OUName);
        fromString = fromString.concat(selectTable);

        for(int j = 0; j < returnColumnNames.size(); j++){
          final String returnColumnName = returnColumnNames.get(j);

          if(returnColumnName == null) continue;

          if(!selectString.isEmpty()) selectString = selectString.concat(",");
          selectString = selectString.concat(returnColumnName);
        }

        if(rDN != null){
          final String tableName = jdbcm.getTableNameFromMapping(baseDN, OUName);
          final String columnName = jdbcm.getColumnNameFromMapping(tableName, baseDN, OUName, rDNAttributeName);
          whereString = columnName + "='" + rDNAttributeValue + "'";
        }
      }

      String sql = "SELECT " + selectString + " FROM " + fromString;

      if(!whereString.isEmpty()) sql = sql.concat(" WHERE " + whereString);

      final Statement st = connection.createStatement();
      ResultSet rs = st.executeQuery(sql);
      ResultSetMetaData rsmd = rs.getMetaData();

      searchEntries.clear();
      while (rs.next()) {
        Entry e = new LinkedHashMapEntry();

        for(int i = 1; i <= rsmd.getColumnCount(); i++){
          e.addAttribute(rsmd.getTableName(i) + "_" + rsmd.getColumnName(i), rs.getObject(i));
        }
        searchEntries.add(e);
      }
      r = Responses.newResult(ResultCode.SUCCESS);
    }catch (SQLException e){
      System.out.println(e.toString());
      r = Responses.newResult(ResultCode.OPERATIONS_ERROR);
    }
    return r;
  }

  @Override
  public String toString()
  {
    return connection.toString();
  }
}
