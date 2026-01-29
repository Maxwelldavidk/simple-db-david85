package simpledb;

import java.util.*;

/**
 * Knows how to compute some aggregate over a set of StringFields.
 */
public class StringAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;
    private final int gbfield;
    private final Type gbfieldtype;
    private final int afield;
    private final Op what;
    Map<Field, Integer> groupedBY;
    private TupleDesc td;

    /**
     * Aggregate constructor
     * @param gbfield the 0-based index of the group-by field in the tuple, or NO_GROUPING if there is no grouping
     * @param gbfieldtype the type of the group by field (e.g., Type.INT_TYPE), or null if there is no grouping
     * @param afield the 0-based index of the aggregate field in the tuple
     * @param what aggregation operator to use -- only supports COUNT
     * @throws IllegalArgumentException if what != COUNT
     */

    public StringAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        // some code goes here
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.afield = afield;
        this.what = what;
        this.groupedBY = new HashMap<>();
        td = null;
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the constructor
     * @param tup the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // some code goes here
        if (td == null) {
            Type[] tps = (gbfield == Aggregator.NO_GROUPING) ?
                    new Type[]{Type.INT_TYPE} :
                    new Type[]{gbfieldtype, Type.INT_TYPE};

            String aggName = what.toString() + "(" + tup.getTupleDesc().getFieldName(afield) + ")";
            String[] names = (gbfield == Aggregator.NO_GROUPING) ?
                    new String[]{aggName} :
                    new String[]{tup.getTupleDesc().getFieldName(gbfield), aggName};
            td = new TupleDesc(tps,names);
        }
        if (what != Aggregator.Op.COUNT) {
            return;
        }
        Field key = (gbfield == Aggregator.NO_GROUPING) ? null : tup.getField(gbfield);

        // Get the value to aggregate
        String newValue = ((StringField) tup.getField(afield)).getValue();

        // If it's the first time we see this group, initialize it
        if (!groupedBY.containsKey(key)) {
            groupedBY.put(key, 1);
            return;
        }
        // Update the existing value based on the operation
        groupedBY.put(key, groupedBY.get(key) + 1);
    }

    /**
     * Create a OpIterator over group aggregate results.
     *
     * @return a OpIterator whose tuples are the pair (groupVal,
     *   aggregateVal) if using group, or a single (aggregateVal) if no
     *   grouping. The aggregateVal is determined by the type of
     *   aggregate specified in the constructor.
     */
    public OpIterator iterator() {
        // some code goes here
        return new OpIterator() {
            private Iterator<Tuple> iterator;
            List<Tuple> results ;
            private boolean isOpen;

            @Override
            public void open() throws DbException, TransactionAbortedException {
                isOpen = true;
                results = new ArrayList<>();
                for (Field key : groupedBY.keySet()) {
                    Tuple t = new Tuple(td);
                    int finalVal = groupedBY.get(key);

                    if (gbfield == Aggregator.NO_GROUPING) {
                        // Only one column: the result
                        t.setField(0, new IntField(finalVal));
                    } else {
                        // Two columns: the key, then the result
                        t.setField(0, key);
                        t.setField(1, new IntField(finalVal));
                    }
                    results.add(t);
                }
                iterator = results.iterator();

            }

            @Override
            public boolean hasNext() throws DbException, TransactionAbortedException {
                if (!isOpen) {
                    throw new IllegalStateException("The operator is not open");
                }
                return iterator.hasNext();
            }

            @Override
            public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
                if (!isOpen) {
                    throw new IllegalStateException("The operator is not open");
                }
                return iterator.next();
            }

            @Override
            public void rewind() throws DbException, TransactionAbortedException {
                if (!isOpen) {
                    throw new IllegalStateException("The operator is not open");
                }
                iterator = results.iterator();
            }

            @Override
            public TupleDesc getTupleDesc() {
                return  td;
            }

            @Override
            public void close() {
                isOpen = false;
                results = null;
                iterator = null;
            }
        };
    }

}
