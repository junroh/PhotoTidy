package Worker;

import Exif.ImgFileData;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class MetaParser extends Worker {

    private static final Logger logger = Logger.getLogger(MetaParser.class);

    private final BlockingQueue<ImgFileData> q;
    private final Worker nextWorker;
    private int processed;
    private int invalid;

    public MetaParser(Worker nextWorker) {
        this.nextWorker = nextWorker;
        q = new ArrayBlockingQueue<>(1024);
        processed = 0;
        invalid = 0;
    }

    @Override
    public boolean put(Object data) {
        return q.offer((ImgFileData) data);
    }

    @Override
    public Boolean run(Thread curThread) throws Exception {
        logger.debug("MetaParser started");

        long workingTime = 0;
        while (true) {
            if (q.isEmpty() && StopRequested.get()) {
                break;
            }
            ImgFileData imgFileData = null;
            long startTime = 0;
            try {
                imgFileData = q.take();
                startTime = System.currentTimeMillis();
                processed++;
            } catch (InterruptedException e) {
                if (!StopRequested.get()) {
                    curThread.interrupt();
                    break;
                }
            }
            if (imgFileData != null) {
                try {
                    if(!imgFileData.parseExif()) {
                        invalid++;
                    }
                } catch (IOException e) {
                    logger.error("Failed to parse meta data due to " + e);
                    break;
                }
                if (!handleData(imgFileData, curThread)) {
                    break;
                }
                workingTime += (System.currentTimeMillis() - startTime);
            }
        }
        System.out.printf("Exif parse completed. Parsed %d, Invalid %d. %.2fsec\n",
                processed, invalid, workingTime/1000.);
        nextWorker.RequestStop();
        logger.debug("MetaParser completed");
        return true;
    }

    private boolean handleData(ImgFileData imgFileData, Thread curThread) {
        try {
            while (!nextWorker.put(imgFileData)) {
                Thread.sleep(1);
            }
        } catch (InterruptedException e) {
            curThread.interrupt();
            return false;
        } catch (IllegalStateException e) {
            logger.error("Next worker completed already");
            return false;
        }
        return true;
    }
}
