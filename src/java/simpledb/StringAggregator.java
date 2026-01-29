package simpledb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Knows how to compute some aggregate over a set of StringFields.
 */
public class StringAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;
    private int gbfield;
    private Type gbfieldtype;
    private int afield;
    private Op what;
    private final Map<Field, Integer> valuesOfGroup = new HashMap<>();



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
        this.afield = afield;
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.what = what;

        if (what != Op.COUNT) {
            throw new IllegalArgumentException("StringAggregator will only support COUNT");
        }
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the constructor
     * @param tup the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // some code goes here
        Field key;
        if (gbfield == NO_GROUPING) {
            key = null;
        } else {
            key = tup.getField(gbfield);
        }

        Integer currentCount = valuesOfGroup.get(key);
        if (currentCount == null) {
            currentCount = 0;
        }
        currentCount = currentCount + 1;
        valuesOfGroup.put(key, currentCount);
       
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
        TupleDesc td;
        if (gbfield == NO_GROUPING) {
            Type[] types = new Type[1];
            types[0] = Type.INT_TYPE;
            td = new TupleDesc(types);
        } else {
            Type[] types = new Type[2];
            types[0] = gbfieldtype;
            types[1] = Type.INT_TYPE;
            td = new TupleDesc(types);
        }
        List<Tuple> output = new ArrayList<Tuple>();

        for (Field key : valuesOfGroup.keySet()) {
            int count = valuesOfGroup.get(key);
            Tuple returnTuple = new Tuple(td);

            if (gbfield == NO_GROUPING) {
                returnTuple.setField(0, new IntField(count));
            } else {
                returnTuple.setField(0, key);
                returnTuple.setField(1, new IntField(count));
            }
            output.add(returnTuple);
        }


        return new TupleIterator(td, output);
    }
}
