package simpledb;

import java.util.*;
import java.io.*;

/**
 * Each instance of HeapPage stores data for one page of HeapFiles and 
 * implements the Page interface that is used by BufferPool.
 *
 * @see HeapFile
 * @see BufferPool
 *
 */
public class HeapPage implements Page {

    final HeapPageId pid;
    final TupleDesc td;
    final byte header[];
    final Tuple tuples[];
    final int numSlots;

    byte[] oldData;
    private final Byte oldDataLock=new Byte((byte)0);

    /**
     * Create a HeapPage from a set of bytes of data read from disk.
     * The format of a HeapPage is a set of header bytes indicating
     * the slots of the page that are in use, some number of tuple slots.
     *  Specifically, the number of tuples is equal to: <p>
     *          floor((BufferPool.getPageSize()*8) / (tuple size * 8 + 1))
     * <p> where tuple size is the size of tuples in this
     * database table, which can be determined via {@link Catalog#getTupleDesc}.
     * The number of 8-bit header words is equal to:
     * <p>
     *      ceiling(no. tuple slots / 8)
     * <p>
     * @see Database#getCatalog
     * @see Catalog#getTupleDesc
     * @see BufferPool#getPageSize()
     */
    public HeapPage(HeapPageId id, byte[] data) throws IOException {
        this.pid = id;
        this.td = Database.getCatalog().getTupleDesc(id.getTableId());
        this.numSlots = getNumTuples();
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));

        // allocate and read the header slots of this page
        header = new byte[getHeaderSize()];
        for (int i=0; i<header.length; i++)
            header[i] = dis.readByte();
        
        tuples = new Tuple[numSlots];
            // allocate and read the actual records of this page
        for (int i=0; i<tuples.length; i++)
            tuples[i] = readNextTuple(dis,i);
        
        dis.close();

        setBeforeImage();
    }

    /** Retrieve the number of tuples on this page.
        @return the number of tuples on this page
    */
    private int getNumTuples() {        
        // some code goes here
        // This is the max num of tuples that can fit on a page.
        int pageSize = BufferPool.getPageSize();
        int tupleSize = td.getSize();
        int numTuples = (pageSize * 8) / (tupleSize * 8 + 1);
        return numTuples;
    }

    

    /**
     * Computes the number of bytes in the header of a page in a HeapFile with each tuple occupying tupleSize bytes
     * @return the number of bytes in the header of a page in a HeapFile with each tuple occupying tupleSize bytes
     */
    private int getHeaderSize() {        
        
        // some code goes here
        // just call page size from bufferpool.
        int headerSize = (int) Math.ceil(getNumTuples() / 8.0);
        return headerSize;
                 
    }
    
    /** Return a view of this page before it was modified
        -- used by recovery */
    public HeapPage getBeforeImage(){
        try {
            byte[] oldDataRef = null;
            synchronized(oldDataLock)
            {
                oldDataRef = oldData;
            }
            return new HeapPage(pid,oldDataRef);
        } catch (IOException e) {
            e.printStackTrace();
            //should never happen -- we parsed it OK before!
            System.exit(1);
        }
        return null;
    }
    
    public void setBeforeImage() {
        synchronized(oldDataLock)
        {
        oldData = getPageData().clone();
        }
    }

    /**
     * @return the PageId associated with this page.
     */
    public HeapPageId getId() {
        // some code goes here
        return this.pid;
    }
    

    /**
     * Suck up tuples from the source file.
     */
    private Tuple readNextTuple(DataInputStream dis, int slotId) throws NoSuchElementException {
        // if associated bit is not set, read forward to the next tuple, and
        // return null.
        if (!isSlotUsed(slotId)) {
            for (int i=0; i<td.getSize(); i++) {
                try {
                    dis.readByte();
                } catch (IOException e) {
                    throw new NoSuchElementException("error reading empty tuple");
                }
            }
            return null;
        }

        // read fields in the tuple
        Tuple t = new Tuple(td);
        RecordId rid = new RecordId(pid, slotId);
        t.setRecordId(rid);
        try {
            for (int j=0; j<td.numFields(); j++) {
                Field f = td.getFieldType(j).parse(dis);
                t.setField(j, f);
            }
        } catch (java.text.ParseException e) {
            e.printStackTrace();
            throw new NoSuchElementException("parsing error!");
        }

        return t;
    }

    /**
     * Generates a byte array representing the contents of this page.
     * Used to serialize this page to disk.
     * <p>
     * The invariant here is that it should be possible to pass the byte
     * array generated by getPageData to the HeapPage constructor and
     * have it produce an identical HeapPage object.
     *
     * @see #HeapPage
     * @return A byte array correspond to the bytes of this page.
     */
    public byte[] getPageData() {
        int len = BufferPool.getPageSize();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(len);
        DataOutputStream dos = new DataOutputStream(baos);

        // create the header of the page
        for (int i=0; i<header.length; i++) {
            try {
                dos.writeByte(header[i]);
            } catch (IOException e) {
                // this really shouldn't happen
                e.printStackTrace();
            }
        }

        // create the tuples
        for (int i=0; i<tuples.length; i++) {

            // empty slot
            if (!isSlotUsed(i)) {
                for (int j=0; j<td.getSize(); j++) {
                    try {
                        dos.writeByte(0);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
                continue;
            }

            // non-empty slot
            for (int j=0; j<td.numFields(); j++) {
                Field f = tuples[i].getField(j);
                try {
                    f.serialize(dos);
                
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // padding
        int zerolen = BufferPool.getPageSize() - (header.length + td.getSize() * tuples.length); //- numSlots * td.getSize();
        byte[] zeroes = new byte[zerolen];
        try {
            dos.write(zeroes, 0, zerolen);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            dos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return baos.toByteArray();
    }

    /**
     * Static method to generate a byte array corresponding to an empty
     * HeapPage.
     * Used to add new, empty pages to the file. Passing the results of
     * this method to the HeapPage constructor will create a HeapPage with
     * no valid tuples in it.
     *
     * @return The returned ByteArray.
     */
    public static byte[] createEmptyPageData() {
        int len = BufferPool.getPageSize();
        return new byte[len]; //all 0
    }

    /**
     * Delete the specified tuple from the page; the corresponding header bit should be updated to reflect
     *   that it is no longer stored on any page.
     * @throws DbException if this tuple is not on this page, or tuple slot is
     *         already empty.
     * @param t The tuple to delete
     */
    public void deleteTuple(Tuple t) throws DbException {
        // some code goes here
        // not necessary for lab1
    }

    /**
     * Adds the specified tuple to the page;  the tuple should be updated to reflect
     *  that it is now stored on this page.
     * @throws DbException if the page is full (no empty slots) or tupledesc
     *         is mismatch.
     * @param t The tuple to add.
     */
    public void insertTuple(Tuple t) throws DbException {
        // some code goes here
        // not necessary for lab1
    }

    /**
     * Marks this page as dirty/not dirty and record that transaction
     * that did the dirtying
     */
    public void markDirty(boolean dirty, TransactionId tid) {
        // some code goes here
	// not necessary for lab1
    }

    /**
     * Returns the tid of the transaction that last dirtied this page, or null if the page is not dirty
     */
    public TransactionId isDirty() {
        // some code goes here
	// Not necessary for lab1
        return null;      
    }

    /**
     * Returns the number of empty slots on this page.
     */
    public int getNumEmptySlots() {
        // some code goes here
        int count = 0;
        for (int i = 0; i < numSlots; i++) {
            if (!isSlotUsed(i)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns true if associated slot on this page is filled.
     */
    // each page has a header, which is an array of bytes.
    // each bit in the header indicates whether the corresponding slot is used.
    // to check if slot i is used, we find the byte in the header that contains the bit for slot i,
    // then we check the specific bit within that byte
    // we do this because i is the slot index, and each byte has 8 bits
    // this means that the byte index is i / 8, and the bit offset within that byte is i % 8
    // if we want the tuple at index 12 then we would look at byte 1 (12 / 8 = 1) and bit 4 (12 % 8 = 4) of that byte
    // we then use a bitwise AND operation to check if that specific bit is set (1) or not (0)
    // we do that by shifting 1 to the left by the bit offset and ANDing it with the header byte
    // we shift by 1 because we want to create a mask that has a 1 in the position of the bit we are interested in
    // we do that because we want to isolate that bit and see if it is set or not
    // if the result of the AND operation is not 0, then the bit is set, meaning the slot is used    

    // to sum everything up in a simple way:
    // 1. find the byte in the header that contains the bit for slot i (i / 8)
    // 2. find the bit offset within that byte (i % 8)
    // 3. use a bitwise AND operation to check if that specific bit is set (1) or not (0)   

    public boolean isSlotUsed(int i) {
        // some code goes here
        int byteIndex = i / 8;
        int bitOffset = i % 8;
        int mask = 1 << bitOffset; // shift 1 to the left by (7 - bitOffset) to create the mask
        return (header[byteIndex] & mask) != 0;
    }

    /**
     * Abstraction to fill or clear a slot on this page.
     */
    private void markSlotUsed(int i, boolean value) {
        // some code goes here
        // not necessary for lab1
    }

    /**
     * @return an iterator over all tuples on this page (calling remove on this iterator throws an UnsupportedOperationException)
     * (note that this iterator shouldn't return tuples in empty slots!)
     */
    // We need to implement an iterator that goes through the tuples array and only returns the tuples in used slots.
    // We can do this by creating an anonymous inner class that implements the Iterator<Tuple> interface.
    // The hasNext() method will check if there are more used slots, and the next() method will return the next tuple in a used slot.
    // We will maintain a current index to keep track of our position in the tuples array.
    // When hasNext() is called, we will advance the current index until we find a used slot or reach the end of the array.
    // When next() is called, we will return the tuple at the current index and then advance the index to the next position.
    // If there are no more used slots, hasNext() will return false and next() will throw a NoSuchElementException.
    // The remove() method will throw an UnsupportedOperationException as specified.
    // This way, we can iterate over only the valid tuples in the page.
    // This implementation ensures that we only return tuples that are actually present in the page, skipping over any empty slots.
    // This is important for efficiency and correctness when working with database pages.
    // We also need to handle the case where there are no used slots gracefully.
    // Overall, this iterator provides a clean and efficient way to access the tuples stored in a HeapPage.
    // This is crucial for database operations that need to read or manipulate the data stored in these pages.
    // By implementing this iterator, we facilitate easy traversal of the tuples while adhering to the constraints of the page structure.
    // This design follows the principles of encapsulation and abstraction, allowing users to interact with the data without needing to understand the underlying storage details.
    // This iterator will be used in various database operations, such as query execution and data retrieval.
    // It is a fundamental component of the database system's architecture.
    public Iterator<Tuple> iterator() {
        // some code goes here
        return new Iterator<Tuple>() {
            private int currentIndex = 0;

            @Override
            public boolean hasNext() {
                while (currentIndex < numSlots) {
                    if (isSlotUsed(currentIndex)) {
                        return true;
                    }
                    currentIndex++;
                }
                return false;
            }

            @Override
            public Tuple next() {
                while (currentIndex < numSlots) {
                    if (isSlotUsed(currentIndex)) {
                        return tuples[currentIndex++];
                    }
                    currentIndex++;
                }
                throw new NoSuchElementException("No more tuples");
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove not supported");
            }
        };
    }
}

