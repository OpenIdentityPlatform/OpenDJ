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
 * Copyright 2024 3A Systems, LLC.
 */
package org.opends.server.backends.jdbc;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.server.config.server.JDBCBackendCfg;
import org.opends.server.backends.pluggable.spi.*;
import org.opends.server.core.ServerContext;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.BackupManager;

import java.sql.*;
import java.util.*;

import static org.opends.server.backends.pluggable.spi.StorageUtils.addErrorMessage;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

public class Storage implements org.opends.server.backends.pluggable.spi.Storage, ConfigurationChangeListener<JDBCBackendCfg>{
	
	private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    private JDBCBackendCfg config;
	  
	public Storage(JDBCBackendCfg cfg, ServerContext serverContext) {
        this.config = cfg;
	    cfg.addJDBCChangeListener(this);
	}

	//config
	@Override
	public boolean isConfigurationChangeAcceptable(JDBCBackendCfg configuration,List<LocalizableMessage> unacceptableReasons) {
		return true;
	}

	@Override
	public ConfigChangeResult applyConfigurationChange(JDBCBackendCfg cfg) {
		final ConfigChangeResult ccr = new ConfigChangeResult();
	    try
	    {
	    	this.config = cfg;
	    }
	    catch (Exception e)
	    {
	      addErrorMessage(ccr, LocalizableMessage.raw(stackTraceToSingleLineString(e)));
	    }
	    return ccr;
	}

	ResultSet executeResultSet(PreparedStatement statement) throws SQLException {
		if (logger.isTraceEnabled()) {
			logger.trace(LocalizableMessage.raw("jdbc: %s",statement));
		}
		return statement.executeQuery();
	}

	boolean execute(PreparedStatement statement) throws SQLException {
		if (logger.isTraceEnabled()) {
			logger.trace(LocalizableMessage.raw("jdbc: %s",statement));
		}
		return statement.execute();
	}

    Connection getConnection() throws Exception {
		final Connection con=CachedConnection.getConnection(config.getDBDirectory());
		return con;
	}


	AccessMode accessMode=AccessMode.READ_ONLY;
	@Override
	public void open(AccessMode accessMode) throws Exception {
		try (final Connection con=getConnection()) {
			this.accessMode = accessMode;
			storageStatus = StorageStatus.working();
		}
	}

	private StorageStatus storageStatus = StorageStatus.lockedDown(LocalizableMessage.raw("closed"));
	@Override
	public StorageStatus getStorageStatus() {
		return storageStatus;
	}
	
	@Override
	public void close() {
		storageStatus = StorageStatus.lockedDown(LocalizableMessage.raw("closed"));
	}

	String getTableName(TreeName treeName) {
		return "\"OpenDJ"+treeName.toString()+"\"";
	}

	@Override
	public void removeStorageFiles() throws StorageRuntimeException {
		final boolean isOpen=getStorageStatus().isWorking();
		if (!isOpen) {
			try {
				open(AccessMode.READ_WRITE);
			}catch (Exception e) {
				throw new StorageRuntimeException(e);
			}
		}
		final Set<TreeName> trees=listTrees();
		if (!trees.isEmpty()) {
			try (final Connection con = getConnection()) {
				try {
					for (final TreeName treeName : trees) {
						try (final PreparedStatement statement = con.prepareStatement("drop table " + getTableName(treeName))) {
							execute(statement);
						}
					}
					con.commit();
				} catch (SQLException e) {
					try {
						con.rollback();
					} catch (SQLException e2) {}
					throw new StorageRuntimeException(e);
				}
			} catch (Exception e) {
				throw new StorageRuntimeException(e);
			}
		}
		if (!isOpen) {
			close();
		}
	}
	
	//operation
	@Override
	public <T> T read(ReadOperation<T> readOperation) throws Exception {
		try(final Connection con=getConnection()) {
			return readOperation.run(new ReadableTransactionImpl(con));
		}
	}

	@Override
	public void write(WriteOperation writeOperation) throws Exception {
		try (final Connection con=getConnection()) {
			try {
				writeOperation.run(new WriteableTransactionTransactionImpl(con));
				con.commit();
			} catch (Exception e) {
				try {
					con.rollback();
				} catch (SQLException ex) {}
				throw e;
			}
		}
	}

	private class ReadableTransactionImpl implements ReadableTransaction {
		final Connection con;
		boolean isReadOnly=true;

		public ReadableTransactionImpl(Connection con) {
			this.con=con;
		}

		@Override
		public ByteString read(TreeName treeName, ByteSequence key) {
			try (final PreparedStatement statement=con.prepareStatement("select v from "+getTableName(treeName)+" where k=?")){
				statement.setBytes(1,key.toByteArray());
				try(ResultSet rc=executeResultSet(statement)) {
					return rc.next() ? ByteString.wrap(rc.getBytes("v")) : null;
				}
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public Cursor<ByteString, ByteString> openCursor(TreeName treeName) {
			return new CursorImpl(isReadOnly,con,treeName);
		}

		@Override
		public long getRecordCount(TreeName treeName) {
			try (final PreparedStatement statement=con.prepareStatement("select count(*) from "+getTableName(treeName));
				 final ResultSet rc=executeResultSet(statement)){
				return rc.next() ? rc.getLong(1) : 0;
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}
	}
	private final class WriteableTransactionTransactionImpl extends ReadableTransactionImpl implements WriteableTransaction {
		
		public WriteableTransactionTransactionImpl(Connection con) {
			super(con);
			if (!accessMode.isWriteable()) {
				throw new ReadOnlyStorageException();
			}
			isReadOnly=false;
		}

		@Override
		public void openTree(TreeName treeName, boolean createOnDemand) {
			if (createOnDemand) {
				try (final PreparedStatement statement=con.prepareStatement("create table if not exists "+getTableName(treeName)+" (k bytea primary key,v bytea)")){
					execute(statement);
					con.commit();
				}catch (SQLException e) {
					throw new RuntimeException(e);
				}
			}
		}
		
		public void clearTree(TreeName treeName) {
			try (final PreparedStatement statement=con.prepareStatement("delete from "+getTableName(treeName))){
				execute(statement);
				con.commit();
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void deleteTree(TreeName treeName) {
			try (final PreparedStatement statement=con.prepareStatement("drop table "+getTableName(treeName))){
				execute(statement);
				con.commit();
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void put(TreeName treeName, ByteSequence key, ByteSequence value) {
			delete(treeName,key);
			try (final PreparedStatement statement=con.prepareStatement("insert into "+getTableName(treeName)+" (k,v) values(?,?) ")){
				statement.setBytes(1,key.toByteArray());
				statement.setBytes(2,value.toByteArray());
				execute(statement);
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public boolean update(TreeName treeName, ByteSequence key, UpdateFunction f) {
			final ByteString oldValue=read(treeName,key);
			final ByteSequence newValue=f.computeNewValue(oldValue);
			if (Objects.equals(newValue, oldValue))
	        {
				return false;
	        }
	        if (newValue == null)
	        {
	        	delete(treeName, key);
	        	return true;
	        }
	        put(treeName,key,newValue);
			return true;
		}

		@Override
		public boolean delete(TreeName treeName, ByteSequence key) {
			try (final PreparedStatement statement=con.prepareStatement("delete from "+getTableName(treeName)+" where k=?")){
				statement.setBytes(1,key.toByteArray());
				execute(statement);
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
			return true;
		}
	}
	
	private final class CursorImpl implements Cursor<ByteString, ByteString> {
		final TreeName treeName;

		final PreparedStatement statement;
		final ResultSet rc;
		final boolean isReadOnly;
		public CursorImpl(boolean isReadOnly, Connection con, TreeName treeName) {
			this.treeName=treeName;
			this.isReadOnly=isReadOnly;
			try {
				statement=con.prepareStatement("select k,v from "+getTableName(treeName)+" order by k",
						isReadOnly?ResultSet.TYPE_SCROLL_INSENSITIVE:ResultSet.TYPE_SCROLL_SENSITIVE,
						isReadOnly?ResultSet.CONCUR_READ_ONLY:ResultSet.CONCUR_UPDATABLE);
				rc=executeResultSet(statement);
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public boolean next() {
			try {
				return rc.next();
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public boolean isDefined() {
			try{
				return rc.getRow()>0;
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public ByteString getKey() throws NoSuchElementException {
			if (!isDefined()) {
				throw new NoSuchElementException();
			}
			try{
				return ByteString.wrap(rc.getBytes("k"));
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public ByteString getValue() throws NoSuchElementException {
			if (!isDefined()) {
				throw new NoSuchElementException();
			}
			try{
				return ByteString.wrap(rc.getBytes("v"));
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void delete() throws NoSuchElementException, UnsupportedOperationException {
			if (!isDefined()) {
				throw new NoSuchElementException();
			}
			try{
				rc.deleteRow();
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void close() {
			try{
				rc.close();
				statement.close();
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}


		@Override
		public boolean positionToKeyOrNext(ByteSequence key) {
			if (!isDefined() || key.compareTo(getKey())<0) { //restart iterator
				try{
					rc.first();
				}catch (SQLException e) {
					throw new RuntimeException(e);
				}
			}
			try{
				if (!isDefined()){
					return false;
				}
				do {
					if (key.compareTo(getKey())<=0) {
						return true;
					}
				}while(rc.next());
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
			return false;
		}
		
		@Override
		public boolean positionToKey(ByteSequence key) {
			if (!isDefined() || key.compareTo(getKey())<0) {  //restart iterator
				try{
					rc.first();
				}catch (SQLException e) {
					throw new RuntimeException(e);
				}
			}
			if (!isDefined()){
				return false;
			}
			if (isDefined() && key.compareTo(getKey())==0) {
				return true;
			}
			try{
				do {
					if (key.compareTo(getKey())==0) {
						return true;
					}
				}while(rc.next());
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
			return false;
		}

		
		@Override
		public boolean positionToLastKey() {
			try{
				return rc.last();
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public boolean positionToIndex(int index) {
			try{
				rc.first();
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
			if (!isDefined()){
				return false;
			}
			int ct=0;
			try{
				do {
					if (ct==index) {
						return true;
					}
					ct++;
				}while(rc.next());
			}catch (SQLException e) {
				throw new RuntimeException(e);
			}
			return false;
		}
	}
	
	@Override
	public Set<TreeName> listTrees() {
		final Set<TreeName> res=new HashSet<>();
		try(final Connection con=getConnection();
			final ResultSet rs = con.getMetaData().getTables(null, null, "OpenDJ%", new String[]{"TABLE"})) {
			while (rs.next()) {
				res.add(TreeName.valueOf(rs.getString("TABLE_NAME").substring(6)));
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return res;
	}
	

	private final class ImporterImpl implements Importer {
		final Connection con;
		final ReadableTransactionImpl txr;
		final WriteableTransactionTransactionImpl txw;

		final Boolean isOpen;
		
		public ImporterImpl() {
			isOpen=getStorageStatus().isWorking();
			if (!isOpen) {
				try {
					open(AccessMode.READ_WRITE);
				}catch (Exception e) {
					throw new StorageRuntimeException(e);
				}
			}
			try {
				con = getConnection();
			}catch (Exception e){
				throw new RuntimeException(e);
			}
			txr =new ReadableTransactionImpl(con);
			txw =new WriteableTransactionTransactionImpl(con);
		}
		
		@Override
		public void close() {
			try {
				con.commit();
				con.close();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
			if (!isOpen) {
				Storage.this.close();
			}
		}
		
		@Override
		public void clearTree(TreeName name) {
			txw.clearTree(name);
		}
		
		@Override
		public void put(TreeName treeName, ByteSequence key, ByteSequence value) {
			txw.put(treeName, key, value);
		}
		
		@Override
		public ByteString read(TreeName treeName, ByteSequence key) {
			return txr.read(treeName, key);
		}
		
		@Override
		public SequentialCursor<ByteString, ByteString> openCursor(TreeName treeName) {
			return txr.openCursor(treeName);
		}
	}
	
	//import
	@Override
	public Importer startImport() throws ConfigException, StorageRuntimeException {
		return new ImporterImpl();
	}
	
	//backup
	@Override
	public boolean supportsBackupAndRestore() {
		return true;
	}

	@Override
	public void createBackup(BackupConfig backupConfig) throws DirectoryException
	{
		// TODO backup over snapshot or SQL export
		//new BackupManager(config.getBackendId()).createBackup(this, backupConfig);
	}

	@Override
	public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
	{
		new BackupManager(config.getBackendId()).removeBackup(backupDirectory, backupID);
	}

	@Override
	public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
	{
		// TODO restore over snapshot or SQL export
		//new BackupManager(config.getBackendId()).restoreBackup(this, restoreConfig);
	}

}
