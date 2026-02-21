package simpledb;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;



public class LockManager {

    // Max time (ms) to wait for a lock before aborting
    private static final long LOCK_WAIT_TIMEOUT_MS = 5000;

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
    private final Map<TransactionId, Set<TransactionId>> waits_For = new HashMap<>();


    public synchronized void getLock(TransactionId tid, PageId pid, Permissions perm)
            throws TransactionAbortedException {

        LockType requested = (perm == Permissions.READ_ONLY) ? LockType.SHARED : LockType.EXCLUSIVE;

        long waitStartMs = System.currentTimeMillis();
        while (!canGrantLock(tid, pid, requested)) {
            try {
                LockState state = pageLocks.get(pid);
                if (state != null) {
                    Set<TransactionId> deps = new HashSet<>();
                    for (TransactionId holder : state.holders) {
                        if (!holder.equals(tid)) {
                            deps.add(holder);
                        }
                    }
                    waits_For.put(tid, deps);
                }
                if (detectDeadLock(tid)) {
                    waits_For.remove(tid);
                    throw new TransactionAbortedException();
                }
                long elapsed = System.currentTimeMillis() - waitStartMs;
                if (elapsed >= LOCK_WAIT_TIMEOUT_MS) {
                    waits_For.remove(tid);
                    throw new TransactionAbortedException();
                }
                long remaining = Math.min(LOCK_WAIT_TIMEOUT_MS - elapsed, 100);
                wait(remaining);
            } catch (InterruptedException e) {
                waits_For.remove(tid);
                throw new TransactionAbortedException();
            }
        }
        waits_For.remove(tid);
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

    // Returns true iff there is a cycle in the wait-for graph that includes currentTid.
    // We only run DFS from currentTid so we only care about deadlocks that involve the caller
    private boolean detectDeadLock(TransactionId currentTid) {
        Set<TransactionId> visited = new HashSet<>();
        Stack<TransactionId> stack = new Stack<>();

        return dfsCycle(currentTid, currentTid, visited, stack);
    }


     // DFS for a cycle that includes currentTid. stack holds the current path
     // so we can tell if currentTid is in the cycle when we find a back edge.
    private boolean dfsCycle(TransactionId cur, TransactionId currentTid,
                            Set<TransactionId> visited,
                            Stack<TransactionId> stack) {
        int backEdgeIndex = stack.indexOf(cur);
        if (backEdgeIndex >= 0) {
            for (int i = backEdgeIndex; i < stack.size(); i++) {
                if (stack.get(i).equals(currentTid)) {
                    return true;
                }
            }
            return false;
        }
        if (visited.contains(cur)) {
            return false;
        }

        visited.add(cur);
        stack.push(cur);

        Set<TransactionId> neighbors = waits_For.get(cur);
        if (neighbors != null) {
            for (TransactionId next : neighbors) {
                if (dfsCycle(next, currentTid, visited, stack)) {
                    return true;
                }
            }
        }
        stack.pop();
        return false;
    }

}



