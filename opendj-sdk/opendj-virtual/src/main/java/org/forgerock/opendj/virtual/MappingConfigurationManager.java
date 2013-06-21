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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * The JDBC configuration manager for the JDBCMapper which can save mapping to and read mapping from the
 * MappingConfig.properties file.
 */
public class MappingConfigurationManager 
{
  private JDBCMapper jdbcMapper;
  private Properties prop;

  /**
   * Creates a new JDBC mapping configuration manager.
   *
   * @param jdbcmapper
   *            The JDBCMapper object to configure mapping for.
   */
  public MappingConfigurationManager(JDBCMapper jdbcmapper)
  {
    this.prop = new Properties();
    this.jdbcMapper = jdbcmapper;
  }

  /**
   * Saves the provided mapping to the MappingConfig.properties file.
   *
   * @param mapping
   *            The mapping to save to the MappingConfig.properties file.
   * @throws IOException
   *            If an I/O exception error occurs. 
   */
  public void saveMapping(Map<String, String> mapping)
  {
    String mappingKey, mappingValue;
    final Set<String> mapperKeySet = mapping.keySet();
    try 
    {  
      for(Iterator<String> i = mapperKeySet.iterator(); i.hasNext(); ){
        mappingKey = i.next();
        mappingValue = mapping.get(mappingKey);
        prop.setProperty(mappingKey, mappingValue);
      }
      prop.store(new FileOutputStream("MappingConfig.properties"), null);
    }catch (IOException e) {
      System.out.println(e.toString());
    }
  }

  /**
   * Load the mapping from the MappingConfig.properties file.
   *
   * @throws SQLException
   *            If the SQL query has an invalid format.
   * @throws IOException
   *            If an I/O exception error occurs. 
   */
  public Map<String, String> loadMapping() throws SQLException
  {
    try {
      prop.load(new FileInputStream("MappingConfig.properties"));
      final ArrayList<String> tableNames = jdbcMapper.getTables();
      final Map<String, String> mapper = new HashMap<String, String>();

      for(int i = 0; i < tableNames.size(); i++){
        String columnName, mappingKey, mappingValue, tableName = tableNames.get(i);
        final ArrayList<String> columnNames = jdbcMapper.getTableColumns(tableName);

        for(Iterator<String> j = columnNames.iterator(); j.hasNext(); ) {
          columnName = j.next();
          mappingKey = tableName + ":" + columnName;
          mappingValue = prop.getProperty(tableName + ":" + columnName);
          if(mappingValue != null)mapper.put(mappingKey, mappingValue);
        }
      }
      return mapper;                    

    } catch (IOException e) {
      System.out.println(e.toString());
      return null;
    }
  }
}
