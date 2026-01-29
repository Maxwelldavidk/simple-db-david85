package simpledb;

import java.util.*;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;
    private int gbfield;
    private Type gbfieldtype;
    private int afield;
    private Op what;
    Map<Field, Integer> aggregateValues;
    Map<Field, Integer> groupedBY;
    private TupleDesc td;

    /**
     * Aggregate constructor
     *
     * @param gbfield
     *            the 0-based index of the group-by field in the tuple, or
     *            NO_GROUPING if there is no grouping
     * @param gbfieldtype
     *            the type of the group by field (e.g., Type.INT_TYPE), or null
     *            if there is no grouping
     * @param afield
     *            the 0-based index of the aggregate field in the tuple
     * @param what
     *            the aggregation operator
     */

    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        // some code goes here
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.afield = afield;
        this.what = what;
        this.aggregateValues = new HashMap<>();
        this.groupedBY = new HashMap<>();
        td = null;
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     *
     * @param tup
     *            the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // some code goes here
        // Determine the key
        if (td == null) {
            Type[] tps = (gbfield == Aggregator.NO_GROUPING) ? new Type[]{Type.INT_TYPE} :
                    new Type[]{gbfieldtype, Type.INT_TYPE};
            String aggName = what.toString() + "(" + tup.getTupleDesc().getFieldName(afield) + ")";
            String[] names = (gbfield == Aggregator.NO_GROUPING) ?
                    new String[]{aggName} :
                    new String[]{tup.getTupleDesc().getFieldName(gbfield), aggName};
            td = new TupleDesc(tps,names);
        }
        Field key = (gbfield == Aggregator.NO_GROUPING) ? null : tup.getField(gbfield);

        // Get the value to aggregate
        int newValue = ((IntField) tup.getField(afield)).getValue();

        // If it's the first time we see this group, initialize it
        if (!groupedBY.containsKey(key)) {
            if (what == Aggregator.Op.COUNT) {
                groupedBY.put(key, 1);
            } else {
                groupedBY.put(key, newValue);
            }
            if (what == Aggregator.Op.AVG) {
                aggregateValues.put(key, 1);
            }
            return;
        }

        // Update the existing value based on the operation
        int oldValue = groupedBY.get(key);

        switch (what) {
            case MIN:
                if (newValue < oldValue) groupedBY.put(key, newValue);
                break;
            case MAX:
                if (newValue > oldValue) groupedBY.put(key, newValue);
                break;
            case SUM:
                groupedBY.put(key, oldValue + newValue);
                break;
            case COUNT:
                groupedBY.put(key, oldValue + 1);
                break;
            case AVG:
                groupedBY.put(key, oldValue + newValue); // Accumulate sum
                aggregateValues.put(key, aggregateValues.get(key) + 1); // Accumulate count
                break;
        }
    }

    /**
     * Create a OpIterator over group aggregate results.
     *
     * @return a OpIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public OpIterator iterator() {
        // some code goes here
        return new OpIterator() {
            private Iterator<Tuple> iterator;
            List<Tuple> results;
            private boolean isOpen;

            @Override
            public void open() throws DbException, TransactionAbortedException {
                isOpen = true;
                results = new ArrayList<>();
                for (Field key : groupedBY.keySet()) {
                    Tuple t = new Tuple(td);
                    int finalVal = groupedBY.get(key);

                    // If we were doing AVG, calculate it now
                    if (what == Op.AVG) {
                        finalVal = finalVal / aggregateValues.get(key);
                    }

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
