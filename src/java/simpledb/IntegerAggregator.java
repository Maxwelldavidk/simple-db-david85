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
    private final Map<Field, List<Integer>> valuesOfGroup = new HashMap<>();

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
        Field key;
        if (gbfield == NO_GROUPING) {
            key = null;
        } else {
            key = tup.getField(gbfield);
        }

        IntField afieldValue = (IntField) tup.getField(afield);
        int value = afieldValue.getValue();

        List<Integer> list = valuesOfGroup.get(key);

        if (list == null) {
            list = new ArrayList<>();
            valuesOfGroup.put(key, list);
        }
        list.add(value);
       
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
        for (Field key :valuesOfGroup.keySet()) {
            List<Integer> values = valuesOfGroup.get(key);

            if (values == null || values.size() == 0) {
                continue;
            }

            int avalue = 0;

            if (what == Op.COUNT) {
                avalue = values.size();

            } else if (what == Op.SUM) {
                int sum = 0;
                for (int value : values) {
                    sum += value;
                }
                avalue = sum;
            } else if (what == Op.AVG) {
                int sum = 0;
                for (int value : values) {
                    sum += value; 
                }
                avalue = sum / values.size();
            } else if (what == Op.MIN) {
                int min = values.get(0);
                for (int value : values) {
                    if ( value < min) {
                        min = value;
                    }
                }
                avalue = min;
            } else if (what == Op.MAX) {
                int max = values.get(0);
                for (int value : values) {
                    if (value > max) {
                        max = value;
                    }
                }
                avalue = max;
            }
            Tuple returnTuple = new Tuple(td);

            if (gbfield == NO_GROUPING) {
                returnTuple.setField(0, new IntField(avalue));
            } else {
                returnTuple.setField(0, key);
                returnTuple.setField(1, new IntField(avalue));
            }
            output.add(returnTuple);
        }


        return new TupleIterator(td, output);
    }

}
