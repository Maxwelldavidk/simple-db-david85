package simpledb;

/** A class to represent a fixed-width histogram over a single integer-based field.
 */
public class IntHistogram {
    private int numBuckets;
    private int min;
    private int max;
    private int[] histogram;
    private double width;
    private int totalValues;


    /**
     * Create a new IntHistogram.
     * 
     * This IntHistogram should maintain a histogram of integer values that it receives.
     * It should split the histogram into "buckets" buckets.
     * 
     * The values that are being histogrammed will be provided one-at-a-time through the "addValue()" function.
     * 
     * Your implementation should use space and have execution time that are both
     * constant with respect to the number of values being histogrammed.  For example, you shouldn't 
     * simply store every value that you see in a sorted list.
     * 
     * @param buckets The number of buckets to split the input value into.
     * @param min The minimum integer value that will ever be passed to this class for histogramming
     * @param max The maximum integer value that will ever be passed to this class for histogramming
     */
    public IntHistogram(int buckets, int min, int max) {
    	// some code goes here
        	this.numBuckets = buckets;
            this.min = min;
            this.max = max;
            this.histogram = new int[buckets];
            this.totalValues = 0;
            this.width = Math.ceil((double) (max - min + 1) / buckets);
            
    }

    /**
     * Add a value to the set of values that you are keeping a histogram of.
     * @param v Value to add to the histogram
     */
    public void addValue(int v) {
    	// some code goes here
        int bucketIndex = (int) ((v - min) / width);

        if ( bucketIndex < 0) {
            bucketIndex = 0; // values smaller than min go to the first bucket
        } else if (bucketIndex >= numBuckets) {
            bucketIndex = numBuckets - 1; // values larger than max go to the last bucket
        }
        histogram[bucketIndex]++;
        totalValues++;
    }

    /**
     * Estimate the selectivity of a particular predicate and operand on this table.
     * 
     * For example, if "op" is "GREATER_THAN" and "v" is 5, 
     * return your estimate of the fraction of elements that are greater than 5.
     * 
     * @param op Operator
     * @param v Value
     * @return Predicted selectivity of this particular operator and value
     */
    public double estimateSelectivity(Predicate.Op op, int v) {

    	// some code goes here
        if (totalValues == 0) {
            return 0.0;
        }

        switch (op) {
            case EQUALS:
            case LIKE: {
                if (v < min || v > max) {
                    return 0.0;
                }

                int bucketIndex = (int) ((v - min) / width);
                if (bucketIndex < 0) {
                    bucketIndex = 0;
                } else if (bucketIndex >= numBuckets) {
                    bucketIndex = numBuckets - 1;
                }

                double bucketStart = min + bucketIndex * width;
                double bucketEnd = Math.min(max, bucketStart + width - 1);
                double actualWidth = bucketEnd - bucketStart + 1;

                return (histogram[bucketIndex] / actualWidth) / totalValues;
            }

            case NOT_EQUALS:
                return 1.0 - estimateSelectivity(Predicate.Op.EQUALS, v);

            case GREATER_THAN: {
                if (v < min) {
                    return 1.0;
                }
                if (v >= max) {
                    return 0.0;
                }

                int bucketIndex = (int) ((v - min) / width);
                if (bucketIndex < 0) {
                    bucketIndex = 0;
                } else if (bucketIndex >= numBuckets) {
                    bucketIndex = numBuckets - 1;
                }

                double countGreater = 0.0;

                for (int i = bucketIndex + 1; i < numBuckets; i++) {
                    countGreater += histogram[i];
                }

                double bucketStart = min + bucketIndex * width;
                double bucketEnd = Math.min(max, bucketStart + width - 1);
                double actualWidth = bucketEnd - bucketStart + 1;

                double bucketFraction = (bucketEnd - v) / actualWidth;
                countGreater += histogram[bucketIndex] * bucketFraction;

                return countGreater / totalValues;
            }

            case LESS_THAN: {
                if (v <= min) {
                    return 0.0;
                }
                if (v > max) {
                    return 1.0;
                }

                int bucketIndex = (int) ((v - min) / width);
                if (bucketIndex < 0) {
                    bucketIndex = 0;
                } else if (bucketIndex >= numBuckets) {
                    bucketIndex = numBuckets - 1;
                }

                double countLess = 0.0;

                for (int i = 0; i < bucketIndex; i++) {
                    countLess += histogram[i];
                }

                double bucketStart = min + bucketIndex * width;
                double bucketEnd = Math.min(max, bucketStart + width - 1);
                double actualWidth = bucketEnd - bucketStart + 1;

                double bucketFraction = (v - bucketStart) / actualWidth;
                countLess += histogram[bucketIndex] * bucketFraction;

                return countLess / totalValues;
            }

            case LESS_THAN_OR_EQ:
                return Math.min(1.0,
                        estimateSelectivity(Predicate.Op.LESS_THAN, v)
                        + estimateSelectivity(Predicate.Op.EQUALS, v));

            case GREATER_THAN_OR_EQ:
                return Math.min(1.0,
                        estimateSelectivity(Predicate.Op.GREATER_THAN, v)
                        + estimateSelectivity(Predicate.Op.EQUALS, v));
        }

        return 0.0;
    }
    
    /**
     * @return
     *     the average selectivity of this histogram.
     *     
     *     This is not an indispensable method to implement the basic
     *     join optimization. It may be needed if you want to
     *     implement a more efficient optimization
     * */
    public double avgSelectivity()
    {
        // some code goes here
        if ( max < min ) {
            return 0.0;
        }

        return 1.0 / (max - min + 1);     }
    
    /**
     * @return A string describing this histogram, for debugging purposes
     */
    public String toString() {
        // some code goes here
        String result = "IntHistogram: [";
        for (int i = 0; i < numBuckets; i++) {
            result += String.format("Bucket %d: %d, ", i, histogram[i]);
        }
        result += "]";
        return result;
    }
}
