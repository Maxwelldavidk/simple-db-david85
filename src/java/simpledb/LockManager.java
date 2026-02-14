package simpledb;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



public class LockManager {

    private enum LockType { SHARED, EXCLUSIVE }


    private static class LockState {

        LockType type;
        Set<TransactionId> holders;

        LockState(LockType type) {
            this.type = type;
            this.holders = new HashSet<>();
        } 
    }

    private final Map<PageId, LockState> pageLocks = new HashMap<>();
    private final Map<TransactionId, Set<PageId>> txnToPages = new HashMap<>();


    public synchronized void getLock(TransactionId tid, PageId pid, Permissions perm)
            throws TransactionAbortedException {

        LockType requested = (perm == Permissions.READ_ONLY) ? LockType.SHARED : LockType.EXCLUSIVE;

        while (!canGrantLock(tid, pid, requested)) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new TransactionAbortedException();
            }
        }

        LockState state = pageLocks.get(pid);

        if (state == null) {
            state = new LockState(requested);
            pageLocks.put(pid, state);
        } else {

            if (state.type == LockType.SHARED && requested == LockType.EXCLUSIVE) {
                state.type = LockType.EXCLUSIVE;
            }
        }
        state.holders.add(tid);

        Set<PageId> pages = txnToPages.get(tid);
        if (pages == null) {
            pages = new HashSet<>();
            txnToPages.put(tid, pages);
        }
        pages.add(pid);



    }

    public synchronized void releaseLock(TransactionId tid, PageId pid) {
        LockState state = pageLocks.get(pid);
        if (state == null) return;

        if (!state.holders.contains(tid)) return;

        state.holders.remove(tid);

        Set<PageId> held = txnToPages.get(tid);
        if (held != null) {
            held.remove(pid);
            if (held.isEmpty()) {
                txnToPages.remove(tid);
            }
        }

        if (state.holders.isEmpty()) {
            pageLocks.remove(pid);
        }

        notifyAll();
    }

    public synchronized void releaseAllLocks(TransactionId tid) {
        Set<PageId> held = txnToPages.get(tid);
        if (held == null) return;

        Set<PageId> copy = new HashSet<>(held);
        for (PageId pid : copy) {
            releaseLock(tid, pid);
        }
    }

     public synchronized boolean holdingLock(TransactionId tid, PageId pid) {
        LockState state = pageLocks.get(pid);
        return state != null && state.holders.contains(tid);
    }


    private boolean canGrantLock(TransactionId tid, PageId pid, LockType requested) {
        LockState state = pageLocks.get(pid);

        if (state == null) return true;

        if (state.type == LockType.EXCLUSIVE) {
            return state.holders.contains(tid);
        }

        if (requested == LockType.SHARED) {
            return true;
        }

        return (state.holders.size() == 1 && state.holders.contains(tid));

    }

}



