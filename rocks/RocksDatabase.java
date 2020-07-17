/*
 * Copyright (C) 2020 Grakn Labs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package grakn.core.rocks;

import grakn.common.util.Pair;
import grakn.core.Grakn;
import grakn.core.common.exception.GraknException;
import grakn.core.graph.util.KeyGenerator;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.RocksDBException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Stream;

import static grakn.core.common.exception.Error.Internal.DIRTY_INITIALISATION;

public class RocksDatabase implements Grakn.Database {

    private final String name;
    private final RocksGrakn rocksGrakn;
    private final OptimisticTransactionDB rocksDB;
    private final KeyGenerator.Persisted keyGenerator;
    private final ConcurrentMap<UUID, Pair<RocksSession, Long>> sessions;
    private final StampedLock schemaLock;
    private final AtomicBoolean isOpen;

    private RocksDatabase(RocksGrakn rocksGrakn, String name) {
        this.name = name;
        this.rocksGrakn = rocksGrakn;
        keyGenerator = new KeyGenerator.Persisted();
        sessions = new ConcurrentHashMap<>();
        isOpen = new AtomicBoolean(false);
        schemaLock = new StampedLock();

        try {
            rocksDB = OptimisticTransactionDB.open(this.rocksGrakn.options(), directory().toString());
        } catch (RocksDBException e) {
            throw new GraknException(e);
        }
    }

    static RocksDatabase createNewAndOpen(RocksGrakn rocksGrakn, String name) {
        return new RocksDatabase(rocksGrakn, name).initialiseAndOpen();
    }

    static RocksDatabase loadExistingAndOpen(RocksGrakn rocksGrakn, String name) {
        return new RocksDatabase(rocksGrakn, name).loadAndOpen();
    }

    private RocksDatabase initialiseAndOpen() {
        try (RocksSession session = createAndOpenSession(Grakn.Session.Type.SCHEMA)) {
            try (RocksTransaction txn = session.transaction(Grakn.Transaction.Type.WRITE)) {
                if (txn.graph().isInitialised()) {
                    throw new GraknException(DIRTY_INITIALISATION);
                }
                txn.graph().initialise();
                txn.commit();
            }
        }
        isOpen.set(true);
        return this;
    }

    private RocksDatabase loadAndOpen() {
        try (RocksSession session = createAndOpenSession(Grakn.Session.Type.DATA)) {
            try (RocksTransaction txn = session.transaction(Grakn.Transaction.Type.READ)) {
                keyGenerator.sync(txn.storage());
            }
        }
        isOpen.set(true);
        return this;
    }

    RocksSession createAndOpenSession(Grakn.Session.Type type) {
        long schemaWriteLockStamp = 0;
        if (type.equals(Grakn.Session.Type.SCHEMA)) {
            schemaWriteLockStamp = schemaLock.writeLock();
        }
        RocksSession session = new RocksSession(this, type);
        sessions.put(session.uuid(), new Pair<>(session, schemaWriteLockStamp));
        return session;
    }

    private Path directory() {
        return rocksGrakn.directory().resolve(name);
    }

    OptimisticTransactionDB rocks() {
        return rocksDB;
    }

    KeyGenerator keyGenerator() {
        return keyGenerator;
    }

    long acquireSchemaReadLock() {
        return schemaLock.readLock();
    }

    void releaseSchemaReadLock(long stamp) {
        schemaLock.unlockRead(stamp);
    }

    void remove(RocksSession session) {
        long schemaWriteLockStamp = sessions.remove(session.uuid()).second();
        if (session.type().equals(Grakn.Session.Type.SCHEMA)) {
            schemaLock.unlockWrite(schemaWriteLockStamp);
        }
    }

    void close() {
        if (isOpen.compareAndSet(true, false)) {
            sessions.values().forEach(p -> p.first().close());
            rocksDB.close();
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean contains(UUID sessionID) {
        return sessions.containsKey(sessionID);
    }

    @Override
    public Grakn.Session get(UUID sessionID) {
        if (sessions.containsKey(sessionID)) return sessions.get(sessionID).first();
        else return null;
    }

    @Override
    public Stream<Grakn.Session> sessions() {
        return sessions.values().stream().map(Pair::first);
    }

    @Override
    public void delete() {
        close();
        rocksGrakn.databases().remove(this);
        try {
            Files.walk(directory()).sorted(Comparator.reverseOrder())
                    .map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            throw new GraknException(e);
        }
    }
}
