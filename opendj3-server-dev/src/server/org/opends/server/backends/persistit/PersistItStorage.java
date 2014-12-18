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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.persistit;

import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.admin.std.server.PersistitBackendCfg;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableStorage;
import org.opends.server.types.DN;

import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.Persistit;
import com.persistit.Transaction;
import com.persistit.Tree;
import com.persistit.TreeBuilder;
import com.persistit.Value;
import com.persistit.Volume;
import com.persistit.exception.PersistitException;
import com.persistit.exception.RollbackException;

@SuppressWarnings("javadoc")
public final class PersistItStorage implements Storage {
    private final class ImporterImpl implements Importer {
        private final Map<TreeName, Tree> trees = new HashMap<TreeName, Tree>();
        private final TreeBuilder importer = new TreeBuilder(db);
        private final Key importKey = new Key(db);
        private final Value importValue = new Value(db);

        @Override
        public void createTree(TreeName treeName) {
            try {
                final Tree tree = getVolume(treeName).getTree(treeName.toString(), true);
                trees.put(treeName, tree);
            } catch (PersistitException e) {
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public void put(TreeName treeName, ByteSequence key, ByteSequence value) {
            try {
                final Tree tree = trees.get(treeName);
                byte[] keyBytes = key.toByteArray();
                importKey.clear().appendByteArray(keyBytes, 0, keyBytes.length);
                importValue.clear().putByteArray(value.toByteArray());
                importer.store(tree, importKey, importValue);
            } catch (Exception e) {
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public void close() {
            try {
                importer.merge();
            } catch (Exception e) {
                throw new StorageRuntimeException(e);
            } finally {
                PersistItStorage.this.close();
            }
        }
    }

    private final class StorageImpl implements WriteableStorage {
        private final Map<TreeName, Exchange> exchanges = new HashMap<TreeName, Exchange>();

        private void release() {
            for (Exchange ex : exchanges.values()) {
                db.releaseExchange(ex);
            }
        }

        private Exchange getExchange(TreeName treeName) throws PersistitException {
            Exchange exchange = exchanges.get(treeName);
            if (exchange == null) {
                exchange = getExchange0(treeName, false);
                exchanges.put(treeName, exchange);
            }
            return exchange;
        }

        @Override
        public ByteString read(TreeName treeName, ByteSequence key) {
            try {
                final Exchange ex = getExchange(treeName);
                ex.getKey().clear().append(key.toByteArray());
                ex.fetch();
                final Value value = ex.getValue();
                if (value.isDefined()) {
                    return ByteString.wrap(value.getByteArray());
                }
                return null;
            } catch (PersistitException e) {
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public ByteString getRMW(TreeName treeName, ByteSequence key) {
            return read(treeName, key);
        }

        @Override
        public void create(TreeName treeName, ByteSequence key, ByteSequence value) {
            try {
                final Exchange ex = getExchange(treeName);
                ex.getKey().clear().append(key.toByteArray());
                ex.getValue().clear().putByteArray(value.toByteArray());
                ex.store();
            } catch (Exception e) {
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public boolean putIfAbsent(TreeName treeName, ByteSequence key, ByteSequence value) {
            try {
                final Exchange ex = getExchange(treeName);
                ex.getKey().clear().append(key.toByteArray());
                ex.fetch();
                // FIXME poor man's CAS: this will not work under high volume,
                // but PersistIt does not provide APIs for this use case.
                if (ex.isValueDefined()) {
                    return false;
                }
                ex.getValue().clear().putByteArray(value.toByteArray());
                ex.store();
                return true;
            } catch (Exception e) {
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public void update(TreeName treeName, ByteSequence key, UpdateFunction f)
        {
          try
          {
            final Exchange ex = getExchange(treeName);
            ex.getKey().clear().append(key.toByteArray());
            ex.fetch();
            final Value value = ex.getValue();
            final ByteSequence oldValue = value.isDefined() ? ByteString.wrap(value
                .getByteArray()) : null;
            final ByteSequence newValue = f.computeNewValue(oldValue);
            ex.getValue().clear().putByteArray(newValue.toByteArray());
            ex.store();
          }
          catch (Exception e)
          {
            throw new StorageRuntimeException(e);
          }
        }

        @Override
        public boolean remove(TreeName treeName, ByteSequence key) {
            try {
                final Exchange ex = getExchange(treeName);
                ex.getKey().clear().append(key.toByteArray());
                return ex.remove();
            } catch (PersistitException e) {
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public void delete(TreeName treeName, ByteSequence key) {
            try {
                final Exchange ex = getExchange(treeName);
                ex.getKey().clear().append(key.toByteArray());
                ex.remove();
            } catch (PersistitException e) {
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public Cursor openCursor(TreeName treeName) {
            try {
                return new CursorImpl(getExchange(treeName));
            } catch (PersistitException e) {
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public void openTree(TreeName treeName) {
            Exchange ex = null;
            try {
                ex = getExchange0(treeName, true);
            } catch (PersistitException e) {
                throw new StorageRuntimeException(e);
            } finally {
                db.releaseExchange(ex);
            }
        }

        @Override
        public void truncateTree(TreeName treeName) {
            try {
                getVolume(treeName).truncate();
            } catch (PersistitException e) {
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public void renameTree(TreeName oldTreeName, TreeName newTreeName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteTree(TreeName treeName) {
            Exchange ex = null;
            try {
                ex = getExchange(treeName);
                ex.removeTree();
            } catch (PersistitException e) {
                throw new StorageRuntimeException(e);
            } finally {
                db.releaseExchange(ex);
            }
        }
    }

    private final class CursorImpl implements Cursor {

        private final Exchange ex;
        private boolean useCurrentKeyForNext = false;

        public CursorImpl(Exchange exchange) {
            this.ex = exchange;
        }

        @Override
        public boolean positionToKey(ByteSequence key) {
            ex.getKey().clear().append(key.toByteArray());
            try {
                ex.fetch();
                useCurrentKeyForNext = ex.getValue().isDefined();
                return useCurrentKeyForNext;
            } catch (PersistitException e) {
                useCurrentKeyForNext = false;
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public boolean positionToKeyOrNext(ByteSequence key) {
            ex.getKey().clear().append(key.toByteArray());
            try {
                ex.fetch();
                if (ex.getValue().isDefined()) {
                    useCurrentKeyForNext = true;
                } else {
                    // provided key does not exist, look for next key
                    useCurrentKeyForNext = ex.next();
                }
                return useCurrentKeyForNext;
            } catch (PersistitException e) {
                useCurrentKeyForNext = false;
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public boolean positionToLastKey() {
            try {
                ex.getKey().to(Key.AFTER);
                useCurrentKeyForNext = ex.previous() && ex.getValue().isDefined();
                return useCurrentKeyForNext;
            } catch (PersistitException e) {
                useCurrentKeyForNext = false;
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public boolean next() {
            if (useCurrentKeyForNext) {
                useCurrentKeyForNext = false;
                return true;
            }
            try {
                return ex.next();
            } catch (PersistitException e) {
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public boolean previous() {
            try {
                return ex.previous();
            } catch (PersistitException e) {
                throw new StorageRuntimeException(e);
            }
        }

        @Override
        public ByteString getKey() {
            return ByteString.wrap(ex.getKey().decodeByteArray());
        }

        @Override
        public ByteString getValue() {
            return ByteString.wrap(ex.getValue().getByteArray());
        }

        @Override
        public void close() {
            // Exchange is released by StorageImpl.release()
            // once the Read/Write Operation is closed
        }
    }



    private File backendDirectory;
    private PersistitBackendCfg config;
    private Persistit db;
    private final ConcurrentMap<String, Volume> volumes = new ConcurrentHashMap<String, Volume>();
    private Properties properties;

    private Volume getVolume(TreeName treeName) {
        return volumes.get(treeName.getBaseDN());
    }

    @Override
  public void initialize(PersistitBackendCfg cfg)
  {
    this.config = cfg;
    this.backendDirectory = new File(getFileForPath(config.getDBDirectory()),
        config.getBackendId());

        properties = new Properties();
        properties.setProperty("datapath", backendDirectory.toString());
        properties.setProperty("logpath", backendDirectory.toString());
        properties.setProperty("logfile", "${logpath}/dj_${timestamp}.log");
        properties.setProperty("buffer.count.16384", "64K");
        properties.setProperty("journalpath", "${datapath}/dj_journal");
        int i = 1;
        for (DN baseDN : config.getBaseDN()) {
            // TODO use VolumeSpecification  Configuration.setVolumeList()?
            properties.setProperty("volume." + i++,
                "${datapath}/" + toSuffixName(baseDN.toString())
                    + ",create,pageSize:16K"
                    + ",initialSize:50M"
                    + ",extensionSize:1M"
                    + ",maximumSize:10G");
        }
    }

    /**
     * Replace persistit reserved comma character with an underscore character.
     */
    public String toSuffixName(String prefix) {
        return prefix.replaceAll("[,=]", "_");
    }

    @Override
    public void open() {
        try {
            db = new Persistit(properties);
            db.initialize();
            for (DN baseDN : config.getBaseDN()) {
                final String volumeName = toSuffixName(baseDN.toString());
                volumes.put(volumeName, db.loadVolume(volumeName));
            }
        } catch (PersistitException e) {
            throw new StorageRuntimeException(e);
        }
    }

    @Override
    public void close() {
        if (db != null) {
            try {
                db.close();
                db = null;
            } catch (PersistitException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static void clearAndCreateDbDir(final File dbDir) {
        if (dbDir.exists()) {
            for (final File child : dbDir.listFiles()) {
                child.delete();
            }
        } else {
            dbDir.mkdirs();
        }
    }

    @Override
    public Importer startImport() {
        clearAndCreateDbDir(backendDirectory);
        open();
        return new ImporterImpl();
    }

    @Override
    public <T> T read(ReadOperation<T> operation) throws Exception {
        final Transaction txn = db.getTransaction();
        for (;;) {
            txn.begin();
            try {
                final StorageImpl storageImpl = new StorageImpl();
                try {
                    final T result = operation.run(storageImpl);
                    txn.commit();
                    return result;
                } catch (StorageRuntimeException e) {
                    throw (Exception) e.getCause();
                } finally {
                    storageImpl.release();
                }
            } catch (RollbackException e) {
                // retry
            } catch (Exception e) {
                txn.rollback();
                throw e;
            } finally {
                txn.end();
            }
        }
    }

    @Override
    public void write(WriteOperation operation) throws Exception {
        final Transaction txn = db.getTransaction();
        for (;;) {
            txn.begin();
            try {
                final StorageImpl storageImpl = new StorageImpl();
                try {
                    operation.run(storageImpl);
                    txn.commit();
                    return;
                } catch (StorageRuntimeException e) {
                    throw (Exception) e.getCause();
                } finally {
                    storageImpl.release();
                }
            } catch (RollbackException e) {
                // retry
            } catch (Exception e) {
                txn.rollback();
                throw e;
            } finally {
                txn.end();
            }
        }
    }

    private Exchange getExchange0(TreeName treeName, boolean create) throws PersistitException {
        return db.getExchange(getVolume(treeName), treeName.toString(), create);
    }

    @Override
    public void closeTree(TreeName treeName) {
        // nothing to do, in persistit you close the volume itself
    }

    @Override
    public boolean isValid() {
        return !db.isFatal();
    }
}
