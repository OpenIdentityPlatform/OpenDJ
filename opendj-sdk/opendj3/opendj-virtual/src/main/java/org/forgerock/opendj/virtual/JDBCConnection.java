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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.forgerock.opendj.ldap.AbstractSynchronousConnection;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
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
  private final String driverName = "com.mysql.jdbc.Driver";
  private java.sql.Connection connection;
  private String connectionUrl;
  private String userName;
  private String userPass;
  private JDBCMapper jdbcm;
  private MappingConfigurationManager mcm;

  JDBCConnection(final String connectionURL) {
    this.connectionUrl = connectionURL;
  }   

  public void initializeMapper(JDBCMapper jdbcmapper) throws SQLException, ErrorResultException, IOException{
    jdbcm = jdbcmapper;
    jdbcm.fillMaps();
    mcm = new MappingConfigurationManager(jdbcm);
    jdbcm.loadMappingConfig(mcm.loadMapping());
  }

  private Map<String, Object> getValuesMap(AddRequest request, String tableName, String OUName){
    final String baseDN = request.getName().toString();
    Iterable<Attribute> attributesCollection = request.getAllAttributes();
    Iterator<Attribute> attributeIter = attributesCollection.iterator();
    Map<String, Object> map = new HashMap<String, Object>();

    while(attributeIter.hasNext()){
      Attribute att = attributeIter.next();
      Iterator<ByteString> valueIter = att.iterator();
      String attributeName = att.getAttributeDescriptionAsString();
      String columnName = jdbcm.getColumnNameFromMapping(tableName, baseDN, OUName, attributeName);
      String columnValue = "";

      if (columnName == null) continue;

      while(valueIter.hasNext()){
        columnValue = columnValue.concat(valueIter.next().toString());
      }
      map.put(columnName, columnValue);
    }
    return map;
  }

  private ArrayList<String> getSQLVariablesStrings(String tableName, Map<String, Object> columnValuesMap){
    ArrayList<String>columnList = null;
    try {
      columnList = jdbcm.getTableColumns(tableName);
    } catch (SQLException e1) {
      e1.printStackTrace();
    }
    String columnNamesString = " (";
    String columnValuesString = " (";

    for(int i = 0; i < columnList.size(); i++){
      if (i > 0){
        columnNamesString = columnNamesString.concat(", ");
        columnValuesString = columnValuesString.concat(", ");
      }
      String columnName = columnList.get(i);
      Object columnValue = columnValuesMap.get(columnName);
      Object dataType = jdbcm.getTableColumnDataType(tableName, columnName);
      if(columnValue == null){
        if(dataType.equals(Integer.class)) columnValue = "0";
        else columnValue = "Default Value";
      }
      if(dataType.equals(Integer.class)) columnValue = Integer.parseInt(columnValue.toString());

      columnNamesString = columnNamesString.concat(columnName);
      columnValuesString = columnValuesString.concat("'" + columnValue + "'");
    }
    columnNamesString = columnNamesString.concat(")");
    columnValuesString = columnValuesString.concat(")");

    ArrayList<String> newlist = new ArrayList<String>();
    newlist.add(columnNamesString);
    newlist.add(columnValuesString);

    return newlist;
  }

  public java.sql.Connection getSqlConnection(){
    return connection;
  }

  @Override
  public Result add(AddRequest request) throws ErrorResultException
  {
    Result r;
    try {
      final String baseDN = request.getName().toString();
      String[] stringSplitter = baseDN.split("ou=");
      stringSplitter = stringSplitter[1].split(",");
      final String organizationalUnitName = stringSplitter[0];
      final String tableName = jdbcm.getTableNameFromMapping(baseDN, organizationalUnitName);
      final Map<String, Object> columnValuesMap = getValuesMap(request, tableName, organizationalUnitName);
      final ArrayList<String> SQLStringList = getSQLVariablesStrings(tableName, columnValuesMap);
      String columnNamesString = SQLStringList.get(0), columnValuesString = SQLStringList.get(1);

      Statement st = connection.createStatement();
      String sql = "INSERT INTO " + tableName + columnNamesString + " VALUES" + columnValuesString;
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
    this.userName = request.getName();
    byte[] password = null;
    if (request instanceof SimpleBindRequest) {
      password = ((SimpleBindRequest) request).getPassword();
    } else {
      r = Responses.newBindResult(ResultCode.PROTOCOL_ERROR);
      return r;
    }
    this.userPass = new String(password);

    try {
      Class.forName(driverName);
      this.connection = DriverManager
          .getConnection(this.connectionUrl,this.userName,this.userPass);
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
    try
    {
      this.connection.close();
    }
    catch (SQLException e)
    {
      e.printStackTrace();
    }
  }

  @Override
  public CompareResult compare(CompareRequest request)
      throws ErrorResultException
      {
    // TODO Auto-generated method stub
    return null;
      }

  @Override
  public Result delete(DeleteRequest request) throws ErrorResultException
  {
    Result r = null;
    try{
      final String baseDN = request.getName().toString();
      String[] stringSplitter = baseDN.split("ou=");
      stringSplitter = stringSplitter[1].split(",");
      final String organizationalUnitName = stringSplitter[0];
      stringSplitter = baseDN.split("cn=");
      stringSplitter = stringSplitter[1].split(",");
      final String commonName = stringSplitter[0];
      final String tableName = jdbcm.getTableNameFromMapping(baseDN, organizationalUnitName);
      String columnName = jdbcm.getColumnNameFromMapping(tableName, baseDN, organizationalUnitName, "cn");

      Statement st = connection.createStatement();
      String sql = "DELETE FROM " + tableName + " WHERE " + columnName + "='" + commonName + "'";
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
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isValid()
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public Result modify(ModifyRequest request) throws ErrorResultException
  {
    // TODO Auto-generated method stub
    return null;
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

  @Override
  public Result search(SearchRequest request, SearchResultHandler handler)
      throws ErrorResultException
      {
    // TODO Auto-generated method stub
    return null;
      }

  @Override
  public String toString()
  {
    // TODO Auto-generated method stub
    return null;
  }

}
