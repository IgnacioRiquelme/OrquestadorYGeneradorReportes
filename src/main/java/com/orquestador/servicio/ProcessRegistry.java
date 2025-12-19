package com.orquestador.servicio;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ProcessRegistry {

    private static final ProcessRegistry INSTANCE = new ProcessRegistry();

    private final Set<Process> processes = Collections.synchronizedSet(new HashSet<>());

    private ProcessRegistry() {}

    public static ProcessRegistry getInstance() {
        return INSTANCE;
    }

    public void register(Process p) {
        if (p != null) processes.add(p);
    }

    public void unregister(Process p) {
        if (p != null) processes.remove(p);
    }

    public void killAll() {
        synchronized (processes) {
            for (Process p : processes.toArray(new Process[0])) {
                try {
                    p.destroyForcibly();
                } catch (Exception e) {
                }
            }
            processes.clear();
        }
    }

    public boolean isEmpty() {
        return processes.isEmpty();
    }

    public boolean waitForEmpty(long timeoutMs) {
        long start = System.currentTimeMillis();
        while (!isEmpty()) {
            if (System.currentTimeMillis() - start > timeoutMs) return false;
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return true;
    }
}
