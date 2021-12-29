package Worker;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Worker implements Callable<Boolean> {

    AtomicBoolean StopRequested = new AtomicBoolean();
    private Thread thread;

    public abstract Boolean run(Thread curThread) throws Exception;
    public abstract boolean put(Object data);

    public void RequestStop() {
        if(!StopRequested.compareAndSet(false, true)) {
            return;
        }
        thread.interrupt();
    }

    @Override
    public Boolean call() throws Exception {
        thread = Thread.currentThread();
        return run(thread);
    }
}
