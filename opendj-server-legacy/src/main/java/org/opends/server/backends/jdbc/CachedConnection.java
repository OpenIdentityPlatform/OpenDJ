/*
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
 * Copyright 2024-2025 3A Systems, LLC.
 */
package org.opends.server.backends.jdbc;

import com.google.common.cache.*;

import java.sql.*;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.*;

public class CachedConnection implements Connection {
    final Connection parent;

    static LoadingCache<String, BlockingQueue<CachedConnection>> cached= CacheBuilder.newBuilder()
            .expireAfterAccess(Long.parseLong(System.getProperty("org.openidentityplatform.opendj.jdbc.ttl","15000")), TimeUnit.MILLISECONDS)
            .removalListener(new RemovalListener<String, BlockingQueue<CachedConnection>>() {
                @Override
                public void onRemoval(RemovalNotification<String, BlockingQueue<CachedConnection>> notification) {
                    assert notification.getValue() != null;
                    for (CachedConnection con: notification.getValue()) {
                            try {
                                if (!con.isClosed()) {
                                    con.parent.close();
                                }
                            } catch (SQLException e) {
                            }
                        }
                }
            })
            .build(new CacheLoader<String, BlockingQueue<CachedConnection>>() {
                @Override
                public BlockingQueue<CachedConnection> load(String connectionString) throws Exception {
                    return new LinkedBlockingQueue<>();
                }
            });

    final String connectionString;
    public CachedConnection(String connectionString,Connection parent) {
        this.connectionString=connectionString;
        this.parent = parent;
    }

    static Connection getConnection(String connectionString) throws Exception {
        return getConnection(connectionString,0);
    }

    static Connection getConnection(String connectionString, final int waitTime) throws Exception {
        CachedConnection con=cached.get(connectionString).poll(waitTime,TimeUnit.MILLISECONDS);
        while(con!=null) {
            if (!con.isValid(0)) {
                try {
                    con.parent.close();
                } catch (SQLException e) {
                    con=null;
                }
                con=cached.get(connectionString).poll();
            }else{
                return con;
            }
        }
        try {
            final Connection conNew= DriverManager.getConnection(connectionString);
            conNew.setAutoCommit(false);
            conNew.setTransactionIsolation(TRANSACTION_READ_COMMITTED);
            return new CachedConnection(connectionString, conNew);
        }catch (SQLException e) { //max_connection server error: try recursion for reuse connection
            return getConnection(connectionString,(waitTime==0)?1:waitTime*2);
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        return parent.createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return parent.prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        return parent.prepareCall(sql);
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return parent.nativeSQL(sql);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        parent.setAutoCommit(autoCommit);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return parent.getAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        parent.commit();
    }

    @Override
    public void rollback() throws SQLException {
        parent.rollback();
    }

    @Override
    public void close() throws SQLException {
        rollback();
        try {
            cached.get(connectionString).add(this);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        return parent.isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return parent.getMetaData();
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        parent.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return parent.isReadOnly();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        parent.setCatalog(catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        return parent.getCatalog();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        parent.setTransactionIsolation(level);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return parent.getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return parent.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        parent.clearWarnings();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return parent.createStatement(resultSetType, resultSetConcurrency);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return parent.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return parent.prepareCall(sql, resultSetType, resultSetConcurrency) ;
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return parent.getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        parent.setTypeMap(map);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        parent.setHoldability(holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        return parent.getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return parent.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        return parent.setSavepoint(name);
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        parent.rollback(savepoint);
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        parent.releaseSavepoint(savepoint);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return parent.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return parent.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return parent.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return parent.prepareStatement(sql, autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return parent.prepareStatement(sql, columnIndexes);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return parent.prepareStatement(sql, columnNames);
    }

    @Override
    public Clob createClob() throws SQLException {
        return parent.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return parent.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return parent.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return parent.createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return parent.isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        parent.setClientInfo(name, value);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        parent.setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return parent.getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return parent.getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return parent.createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return parent.createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        parent.setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return parent.getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        parent.abort(executor);
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        parent.setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return parent.getNetworkTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return parent.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return parent.isWrapperFor(iface);
    }
}
