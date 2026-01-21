package simpledb;

import java.util.*;

/**
 * SeqScan is an implementation of a sequential scan access method that reads
 * each tuple of a table in no particular order (e.g., as they are laid out on
 * disk).
 */
public class SeqScan implements OpIterator {

    private static final long serialVersionUID = 1L;
    private final TransactionId tid;
    private int tableid;
    private String tableAlias;
    private boolean isOpen;
    private DbFileIterator dbFileIterator;
    

    /**
     * Creates a sequential scan over the specified table as a part of the
     * specified transaction.
     *
     * @param tid
     *            The transaction this scan is running as a part of.
     * @param tableid
     *            the table to scan.
     * @param tableAlias
     *            the alias of this table (needed by the parser); the returned
     *            tupleDesc should have fields with name tableAlias.fieldName
     *            (note: this class is not responsible for handling a case where
     *            tableAlias or fieldName are null. It shouldn't crash if they
     *            are, but the resulting name can be null.fieldName,
     *            tableAlias.null, or null.null).
     */
    public SeqScan(TransactionId tid, int tableid, String tableAlias) {
        // some code goes here
        this.tid = tid;
        this.tableid = tableid;
        this.tableAlias = tableAlias;
        this.isOpen = false;
    }

    /**
     * @return
     *       return the table name of the table the operator scans. This should
     *       be the actual name of the table in the catalog of the database
     * */
    public String getTableName() {
        return Database.getCatalog().getTableName(tableid);
    }

    /**
     * @return Return the alias of the table this operator scans.
     * */
    public String getAlias()
    {
        // some code goes here
        return tableAlias;
    }

    /**
     * Reset the tableid, and tableAlias of this operator.
     * @param tableid
     *            the table to scan.
     * @param tableAlias
     *            the alias of this table (needed by the parser); the returned
     *            tupleDesc should have fields with name tableAlias.fieldName
     *            (note: this class is not responsible for handling a case where
     *            tableAlias or fieldName are null. It shouldn't crash if they
     *            are, but the resulting name can be null.fieldName,
     *            tableAlias.null, or null.null).
     */
    public void reset(int tableid, String tableAlias) {
        // some code goes here
        if (isOpen) {
            close();
        }
        this.tableid = tableid;
        this.tableAlias = tableAlias;
        try {
            open();
        } catch (Exception ignored) {

        }
    }

    public SeqScan(TransactionId tid, int tableId) {
        this(tid, tableId, Database.getCatalog().getTableName(tableId));
    }

    // Opens the iterator using the transaction id and table id to get the DbFile
    public void open() throws DbException, TransactionAbortedException {
        // some code goes here
        // If it is already open, throw exception
        if (isOpen) {
            throw new IllegalStateException("The operator is open");
        }
        DbFile dbFile = Database.getCatalog().getDatabaseFile(tableid);
        dbFileIterator = dbFile.iterator(tid);
        dbFileIterator.open();
        isOpen = true;
    }

    /**
     * Returns the TupleDesc with field names from the underlying HeapFile,
     * prefixed with the tableAlias string from the constructor. This prefix
     * becomes useful when joining tables containing a field(s) with the same
     * name.  The alias and name should be separated with a "." character
     * (e.g., "alias.fieldName").
     *
     * @return the TupleDesc with field names from the underlying HeapFile,
     *         prefixed with the tableAlias string from the constructor.
     */
    
    public TupleDesc getTupleDesc() {
        // some code goes here
        TupleDesc tupleDesc = Database.getCatalog().getTupleDesc(tableid);
        // Create new arrays for types and names
        int numFields = tupleDesc.numFields();
        Type[] types = new Type[numFields];
        String[] names = new String[numFields];
        // populte the arrays with the prefixed names
        for (int i = 0; i < numFields; i++) {
            types[i] = tupleDesc.getFieldType(i);
            String fieldName = tupleDesc.getFieldName(i);
            String prefix = (tableAlias == null) ? "null" : tableAlias;
            names[i] = prefix + "." + (fieldName == null ? "null" : fieldName);
        }
        return new TupleDesc(types, names);
    }

    public boolean hasNext() throws TransactionAbortedException, DbException {
        // some code goes here
        // if the operator isn't open, throw exception
        if (!isOpen) {
            throw new IllegalStateException("The operator is not open!");
        }
        return dbFileIterator.hasNext();
    }

    public Tuple next() throws NoSuchElementException,
            TransactionAbortedException, DbException {
        // some code goes here
        // if the operator isn't open, throw exception
        if (!isOpen) {
            throw new IllegalStateException("The operator is not open!");
        }
        // if there is no more tuples, throw exception
        if (!hasNext()) {
            throw new NoSuchElementException("No more tuples");
        }
        return dbFileIterator.next();
    }

    public void close() {
        // some code goes here
        if (dbFileIterator != null) {
            dbFileIterator.close();
        }
        isOpen = false;
    }

    public void rewind() throws DbException, NoSuchElementException,
            TransactionAbortedException {
        // some code goes here
        // if the operator isn't open, throw exception
        if (!isOpen) {
            throw new IllegalStateException("The operator is not open!");
        }
        dbFileIterator.rewind();
    }
}
