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

public class MappingConfigurationManager 
{
  private JDBCMapper JDBCM;
  private Properties prop;

  public MappingConfigurationManager(JDBCMapper jdbcm){
    prop = new Properties();
    JDBCM = jdbcm;
  }

  public void saveMapping(Map<String, String> mapper){
    String mappingKey, mappingValue;
    Set<String> mapperKeySet = mapper.keySet();
    try {       
      for(Iterator<String> i = mapperKeySet.iterator(); i.hasNext(); ){
        mappingKey = i.next();
        mappingValue = mapper.get(mappingKey);
        prop.setProperty(mappingKey, mappingValue);
      }
      prop.store(new FileOutputStream("MappingConfig.properties"), null);

    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  public Map<String, String> loadMapping() throws SQLException{
    try {
      prop.load(new FileInputStream("MappingConfig.properties"));
      ArrayList<String> tableNames = JDBCM.getTables();
      Map<String, String> mapper = new HashMap<String, String>();

      for(int i = 0; i < tableNames.size(); i++){
        String columnName, mappingKey, mappingValue, tableName = tableNames.get(i);
        ArrayList<String> columnNames = JDBCM.getTableColumns(tableName);

        for(Iterator<String> j = columnNames.iterator(); j.hasNext(); ) {
          columnName = j.next();
          mappingKey = tableName + ":" + columnName;
          mappingValue = prop.getProperty(tableName + ":" + columnName);
          if(mappingValue != null)mapper.put(mappingKey, mappingValue);
        }
      }
      return mapper;                    

    } catch (IOException ex) {
      ex.printStackTrace();
      return null;
    }
  }
}