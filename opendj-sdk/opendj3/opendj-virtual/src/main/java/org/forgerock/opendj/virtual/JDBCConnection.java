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
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
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
  private final String driverName = "com.mysql.jdbc.Driver";
  private java.sql.Connection connection;
  private String connectionUrl;
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

  public java.sql.Connection getSqlConnection(){
    return connection;
  }

  @Override
  public Result add(AddRequest request) throws ErrorResultException
  {
    Result r;
    try {
      final DN DN = request.getName();
      final RDN OU = DN.rdn();
      final String organizationalUnitName = OU.getFirstAVA().getAttributeValue().toString();
      final String baseDN = DN.parent(1).toString();
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
    try {
      this.connection.close();
    }
    catch (SQLException e){
      e.printStackTrace();
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
      return this.connection.isClosed();
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
      return this.connection.isValid(0);
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

        if(modificationType == ModificationType.ADD || modificationType == ModificationType.REPLACE){
          final Iterator<ByteString> iter = modificationAttribute.iterator();

          while (iter.hasNext()){
            modificationAttributeValue = iter.next().toString();
          }
        }
        else{
          final boolean nullable = jdbcm.getTableColumnNullable(tableName, modificationColumnName);

          if(nullable == false) throw new SQLException("Cannot delete data from not-nullable column.");
          final Object classType = jdbcm.getTableColumnDataType(tableName, modificationColumnName);

          if(classType == Integer.class) modificationAttributeValue = Integer.toString(0);
          else modificationAttributeValue = "Default Value";
        }
        modificationString = modificationString.concat(modificationColumnName + "='" + modificationAttributeValue + "', ");
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
  

  @Override
  public Result search(SearchRequest request, SearchResultHandler handler) throws ErrorResultException
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String toString()
  {
    return this.connection.toString();
  }
}
