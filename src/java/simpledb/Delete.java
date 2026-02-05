package simpledb;

import java.io.IOException;

/**
 * The delete operator. Delete reads tuples from its child operator and removes
 * them from the table they belong to.
 */
public class Delete extends Operator {

    private static final long serialVersionUID = 1L;
    private final TransactionId transactionId;
    private OpIterator child;
    private final TupleDesc td;
    private int rowsAffected;

    /**
     * Constructor specifying the transaction that this delete belongs to as
     * well as the child to read from.
     * 
     * @param t
     *            The transaction this delete runs in
     * @param child
     *            The child operator from which to read tuples for deletion
     */
    public Delete(TransactionId t, OpIterator child) {
        // some code goes here
        this.transactionId = t;
        this.child = child;
        this.td = new TupleDesc(new Type[]{Type.INT_TYPE}, new String[]{"Rows affected"});
        this.rowsAffected = -1;
    }

    public TupleDesc getTupleDesc() {
        // some code goes here
        return td;
    }

    public void open() throws DbException, TransactionAbortedException {
        // some code goes here
        super.open();
        child.open();
        rowsAffected = 0;
        while (child.hasNext()) {
            try{
                Database.getBufferPool().deleteTuple(transactionId, child.next());
                rowsAffected++;
            } catch (Exception e){
                throw new DbException("Delete failed: " + e.getMessage());
            }
        }
    }

    public void close() {
        // some code goes here
        super.close();
        rowsAffected = -1;
        child.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        // some code goes here
        child.rewind();
        this.rowsAffected = -1;
    }

    /**
     * Deletes tuples as they are read from the child operator. Deletes are
     * processed via the buffer pool (which can be accessed via the
     * Database.getBufferPool() method.
     * 
     * @return A 1-field tuple containing the number of deleted records.
     * @see Database#getBufferPool
     * @see BufferPool#deleteTuple
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        // some code goes here
        if (rowsAffected >= 0) {
            Tuple t = new Tuple(td);
            t.setField(0, new IntField(rowsAffected));
            rowsAffected = -1;
            return t;
        }
        return null;
    }

    @Override
    public OpIterator[] getChildren() {
        // some code goes here
        return new OpIterator[] { this.child };
    }

    @Override
    public void setChildren(OpIterator[] children) {
        // some code goes here
        if (this.child != children[0]) {
            this.child = children[0];
        }
    }

}
