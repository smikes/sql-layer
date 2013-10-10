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
package com.foundationdb.qp.row;

import com.foundationdb.qp.rowtype.CompoundRowType;
import com.foundationdb.qp.rowtype.RowType;
import com.foundationdb.server.types.value.ValueSource;
import com.foundationdb.server.types.value.ValueSources;

public class CompoundRow extends AbstractRow {

    @Override
    public RowType rowType() {
        return rowType;
    }

    @Override
    public HKey hKey() {
        return null;
    }

    @Override
    public ValueSource value(int i) {
        ValueSource source;
        if (i < firstRowFields) {
            source = firstRow == null ? nullValue(i) : firstRow.value(i);
        } else {
            source = secondRow == null ? nullValue(i) : secondRow.value(i - rowOffset);
        }
        return source;
    }
    
    @Override
    public Row subRow(RowType subRowType)
    {
        Row subRow;
        if (subRowType == rowType.first()) {
            subRow = firstRow;
        } else if (subRowType == rowType.second()) {
            subRow = secondRow;
        } else {
            // If the subRowType doesn't match leftType or rightType, then it might be buried deeper.
            subRow = firstRow.subRow(subRowType);
            if (subRow == null) {
                subRow = secondRow.subRow(subRowType);
            }
        }
        return subRow;
    }

    protected Row first() {
        return firstRow;
    }
    
    protected Row second() {
        return secondRow;
    }
    
    protected int firstRowFields() {
        return firstRowFields;
    }
    
    public CompoundRow (CompoundRowType type, Row firstRow, Row secondRow)
    {
        this.rowType = type;
        this.firstRow = firstRow;
        this.secondRow = secondRow;
        this.firstRowFields = type.first().nFields();
        this.rowOffset = type.first().nFields();
    }

    private ValueSource nullValue(int i) {
        return ValueSources.getNullSource(rowType.typeInstanceAt(i));
    }

    // Object state

    private final CompoundRowType rowType;
    private final Row firstRow;
    private final Row secondRow;
    private final int firstRowFields;
    protected int rowOffset; 

}
