/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.foundationdb.server.store;

import com.foundationdb.ais.model.ForeignKey;
import com.foundationdb.ais.model.Index;
import com.foundationdb.qp.storeadapter.PersistitAdapter;
import com.foundationdb.server.rowdata.RowData;
import com.foundationdb.server.types.service.TypesRegistryService;
import com.foundationdb.server.service.ServiceManager;
import com.foundationdb.server.service.config.ConfigurationService;
import com.foundationdb.server.service.session.Session;
import com.foundationdb.server.store.format.PersistitStorageDescription;

import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.exception.PersistitException;
import com.persistit.exception.RollbackException;

public class PersistitConstraintHandler extends ConstraintHandler<PersistitStore,Exchange,PersistitStorageDescription>
{
    public PersistitConstraintHandler(PersistitStore store, ConfigurationService config, TypesRegistryService typesRegistryService, ServiceManager serviceManager) {
        super(store, config, typesRegistryService, serviceManager);
    }

    @Override
    protected void checkReferencing(Session session, Index index, Exchange exchange,
                                    RowData row, ForeignKey foreignKey, String action) {
        // At present, a unique index has the rest of the index entry
        // in the value, so the passed in key will match exactly.
        assert index.isUnique() : index;
        try {
            if (!exchange.traverse(Key.Direction.EQ, true)) {
                notReferencing(session, index, exchange, row, foreignKey, action);
            }
            // Avoid write skew from concurrent insert referencing and delete referenced.
            exchange.lock();
        }
        catch (PersistitException | RollbackException e) {
            throw PersistitAdapter.wrapPersistitException(session, e);
        }
    }

    @Override
    protected void checkNotReferenced(Session session, Index index, Exchange exchange,
                                      RowData row, ForeignKey foreignKey, String action) {
        try {
            if (row == null) {
                // Scan all (after null), filling exchange for error report.
                while (exchange.traverse(Key.Direction.GT, true)) {
                    if (!keyHasNullSegments(exchange.getKey(), index)) {
                        stillReferenced(session, index, exchange, row, foreignKey, action);
                    }
                }
            }
            else {
                if (exchange.hasChildren()) {
                    stillReferenced(session, index, exchange, row, foreignKey, action);
                }
            }
        }
        catch (PersistitException | RollbackException e) {
            throw PersistitAdapter.wrapPersistitException(session, e);
        }
    }

}
