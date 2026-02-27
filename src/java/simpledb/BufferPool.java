package simpledb;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 *
 * @Threadsafe, all fields are final
 */
public class BufferPool {
    /** Bytes per page, including header. */
    private static final int DEFAULT_PAGE_SIZE = 4096;

    private static int pageSize = DEFAULT_PAGE_SIZE;

    /** Default number of pages passed to the constructor. This is used by
     other classes. BufferPool should use the numPages argument to the
     constructor instead. */
    public static final int DEFAULT_PAGES = 50;

    private final Map<PageId, Page> pages;
    // private Map<TransactionId, PageId> locks;
    private final int numOfPages;
    private final LockManager lockManager;



    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        // some code goes here
        numOfPages = numPages;
        // locks = new ConcurrentHashMap<TransactionId, PageId>();
        pages = new ConcurrentHashMap<PageId, Page>();
        lockManager = new LockManager();
    }

    public static int getPageSize() {
        return pageSize;
    }

    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void setPageSize(int pageSize) {
        BufferPool.pageSize = pageSize;
    }

    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void resetPageSize() {
        BufferPool.pageSize = DEFAULT_PAGE_SIZE;
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, a page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid the ID of the transaction requesting the page
     * @param pid the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public  Page getPage(TransactionId tid, PageId pid, Permissions perm)
            throws TransactionAbortedException, DbException {
        // some code goes here
        try {
            lockManager.getLock(tid, pid, perm);
        } catch (TransactionAbortedException e) {
            throw new TransactionAbortedException();
        }

        if (!pages.containsKey(pid)) {
            if (pages.size() >= numOfPages) {
                evictPage();
            }
            DbFile file = Database.getCatalog().getDatabaseFile(pid.getTableId());
            Page newPage = file.readPage(pid);
            pages.put(pid, newPage);
        }
        return pages.get(pid);
    }

    /**
     * Releases the lock on a page.
     * Calling this is very risky, and may result in wrong behavior. Think hard
     * about who needs to call this and why, and why they can run the risk of
     * calling it.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param pid the ID of the page to unlock
     */
    public void releasePage(TransactionId tid, PageId pid) {
        // some code goes here
        // not necessary for lab1|lab2
        lockManager.releaseLock(tid, pid);
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
        transactionComplete(tid, true);
    }

    /** Return true if the specified transaction has a lock on the specified page */
    public boolean holdsLock(TransactionId tid, PageId p) {
        // some code goes here
        // not necessary for lab1|lab2
        return lockManager.holdingLock(tid, p);
        
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit)
            throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
        ArrayList<PageId> toFlush = new ArrayList<>();
        for (PageId pid : pages.keySet()) {
            Page p = pages.get(pid);
            if (p.isDirty() != null && p.isDirty().equals(tid)) {
                toFlush.add(pid);
            }
        }
        if (commit) {
            for (PageId pid : toFlush) {
                Page p = pages.get(pid);
                Database.getLogFile().logWrite(tid, p.getBeforeImage(), p);
                Database.getLogFile().force();
                p.setBeforeImage();
            }
        } else {
            for (PageId pid : toFlush) {
                discardPage(pid);
            }
        }
        lockManager.releaseAllLocks(tid);
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other
     * pages that are updated (Lock acquisition is not needed for lab2).
     * May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1

        DbFile f = Database.getCatalog().getDatabaseFile(tableId);
        ArrayList<Page> dirty = f.insertTuple(tid, t);

        for (Page p : dirty) {
            p.markDirty(true, tid);
            pages.put(p.getId(), p);
        }
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from and any
     * other pages that are updated. May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid the transaction deleting the tuple.
     * @param t the tuple to delete
     */
    public  void deleteTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        DbFile file = Database.getCatalog().getDatabaseFile(t.getRecordId().getPageId().getTableId());
        ArrayList<Page> dirty = file.deleteTuple(tid, t);
        for (Page p : dirty) {
            p.markDirty(true, tid);
            pages.put(p.getId(), p);
        }

    }

    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     *     break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {
        // some code goes here
        // not necessary for lab1
        for (PageId p : pages.keySet()) {
            flushPage(p);
        }
    }

    /** Remove the specific page id from the buffer pool.
     Needed by the recovery manager to ensure that the
     buffer pool doesn't keep a rolled back page in its
     cache.

     Also used by B+ tree files to ensure that deleted pages
     are removed from the cache so they can be reused safely
     */
    public synchronized void discardPage(PageId pid) {
        // some code goes here
        // not necessary for lab1
        pages.remove(pid);
    }

    /**
     * Flushes a certain page to disk
     * @param pid an ID indicating the page to flush
     */
    private synchronized  void flushPage(PageId pid) throws IOException {
        // some code goes here
        // not necessary for lab1
        Page p = pages.get(pid);
        if (p.isDirty() != null ) {
            DbFile file = Database.getCatalog().getDatabaseFile(pid.getTableId());
             // append an update record to the log, with
            // a before-image and after-image.
            TransactionId dirtier = p.isDirty();
            if (dirtier != null && Database.getLogFile().tidToFirstLogRecord.containsKey(dirtier.getId())) {
                Database.getLogFile().logWrite(dirtier, p.getBeforeImage(), p);
                Database.getLogFile().force();
            }

            file.writePage(p);
            p.markDirty(false, null);

        }
    }

    /** Write all pages of the specified transaction to disk.
     */
    public synchronized  void flushPages(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
        for (PageId pid : pages.keySet()) {
            Page p = pages.get(pid);
            if (p.isDirty() != null && p.isDirty().equals(tid)) {
                flushPage(pid);
            }
        }

    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized  void evictPage() throws DbException {
        // some code goes here
        // not necessary for lab1
        if (pages.isEmpty()) {
            throw new DbException("Cannot evict from empty buffer pool");
        }
        // Try to find first non-dirty page
        PageId evictId = null;
        for (Page page : pages.values()) {
            if (page.isDirty() == null) {
                evictId = page.getId();
                break;
            }
        }
        // If all pages are dirty, just pick the first one. *** changes to eviction strategy here. We now flush the dirty page before eviction.***
        if (evictId == null) {
            evictId = pages.values().iterator().next().getId();
        }

        // Flush the page to disk (handles dirty pages)
        try {
            flushPage(evictId);
        } catch (IOException e) {
            throw new DbException("Error flushing page during eviction: " + e.getMessage());
        }

        // Remove the page from buffer pool
        discardPage(evictId);
    }

}
