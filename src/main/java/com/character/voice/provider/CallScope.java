package com.character.voice.provider;

import okhttp3.Call;
import java.util.HashSet;
import java.util.Set;

/** Lifetime of all outbound calls belonging to one conversational turn. */
public final class CallScope implements AutoCloseable {
    private final Set<Call> calls = new HashSet<>();
    private boolean cancelled;

    public synchronized void register(Call call) {
        if (cancelled) call.cancel();
        else calls.add(call);
    }
    public synchronized void unregister(Call call) { calls.remove(call); }
    public synchronized void cancel() {
        cancelled = true;
        for (Call call : calls) call.cancel();
        calls.clear();
    }
    public synchronized boolean isCancelled() { return cancelled; }
    @Override public void close() { cancel(); }
}
