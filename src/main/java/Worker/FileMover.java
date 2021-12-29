package Worker;

import Exif.ImgFileData;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileMover extends Worker {

    private static final Logger logger =  Logger.getLogger(FileMover.class);

    private final BlockingQueue<ImgFileData> q;
    private final AtomicBoolean isAcceptable;
    private final Options opts;

    private int processed;
    private int moved;
    private final List<ResultData> doneList;
    private int dupCount;
    private final List<ResultData> dupList;
    private final List<ResultData> failedList;
    private int noExifCnt;
    private final List<String> noExifList;

    public FileMover(Options opts) {
        this.opts = opts;
        q = new ArrayBlockingQueue<>(1024);
        isAcceptable = new AtomicBoolean(true);
        doneList = new LinkedList<>();
        dupCount = 0;
        dupList = new LinkedList<>();
        failedList = new LinkedList<>();
        noExifList = new LinkedList<>();
    }

    @Override
    public boolean put(Object data) {
        if(!isAcceptable.get()) {
            throw new IllegalStateException();
        }
        return q.offer((ImgFileData)data);
    }

    @Override
    public Boolean run(Thread curThread) {
        logger.debug("FileMover started");
        long workingTime = 0;
        boolean ret = true;
        while(true) {
            if(q.isEmpty() && StopRequested.get()) {
                break;
            }
            ImgFileData imgFile = null;
            long start = 0;
            try {
                 imgFile = q.take();
                 start = System.currentTimeMillis();
            } catch (InterruptedException e) {
                if(!StopRequested.get()) {
                    curThread.interrupt();
                    break;
                }
            }
            if(imgFile!=null) {
                System.out.printf("Processing %s                         \r", imgFile.getPath());
                logger.debug(String.format("processing %s", imgFile.getPath()));
                processed++;
                String candidateName = getCandidateName(imgFile);
                if (candidateName != null) {
                    try {
                        process(imgFile, candidateName);
                    } catch (IOException e) {
                        logger.error("Failed to move image/video due to " + e);
                        ret = false;
                        break;
                    }
                }
                workingTime += (System.currentTimeMillis() - start);
            }
        }
        isAcceptable.set(false);
        System.out.printf("Move/Copy completed. Handled %d, Moved %d, Duplicated %d, NoExif %d, Failed %d. %.2fsec\n",
                processed, moved, dupCount, noExifCnt, failedList.size(), workingTime/1000.);
        logger.debug("FileMover completed");
        return ret;
    }

    public List<ResultData> getDupList() {
        return dupList;
    }

    public List<ResultData> getDoneList() {
        return doneList;
    }

    private String getCandidateName(ImgFileData imgFile) {
        String orgName = imgFile.getPath().getFileName().toString();
        int index = orgName.lastIndexOf('.');
        if(index>=orgName.length()) {
            return null;
        }
        String dstDir = opts.dstBaseDir;
        String fileExt = orgName.substring(index + 1);
        Date date;
        if(!opts.modifiedDateOnly) {
            date = imgFile.getImageDate();
            if (date == null) {
                logger.debug(String.format("No exif file found %s", orgName));
                noExifCnt++;
                noExifList.add(orgName);
                switch (opts.noExifDir) {
                    case SKIP:
                        return null;
                    case FIXED_DIR:
                        dstDir += (File.separator + opts.noExifDirName);
                        return dstDir + File.separator + orgName;
                    case FILE_DATE:
                        date = imgFile.getFileModifiedDate();
                }
            }
        } else {
            date = imgFile.getFileModifiedDate();
        }
        dstDir += (File.separator + opts.dirNameFormatter.format(date));
        String nameCandidate = opts.fileNameFormatter.format(date) + "." + fileExt;
        return dstDir + File.separator + nameCandidate;
    }

    private void process(ImgFileData src, String dstCandidate) throws IOException {
        int index = dstCandidate.lastIndexOf(File.separator);
        if(index >= dstCandidate.length()) {
            throw new IOException("Invalid destination directory " + dstCandidate);
        }
        String dstDir = dstCandidate.substring(0, index);
        if (!createDir(dstDir)) {
            throw new IOException("Failed to create directory " + dstDir);
        }
        dstCandidate = dstCandidate.substring(index+1);
        String newDstName = getDstFileName(src, dstDir, dstCandidate);
        if (newDstName != null) {
            moveFile(src.getPath(), newDstName);
        }
    }

    private boolean createDir(String dstDir) {
        File file = new File(dstDir);
        if(file.exists()) {
            if(file.isDirectory()) {
                return true;
            }
            logger.error("Failed to create dir " + dstDir + " due to that the same name of file exists");
            return false;
        }
        if(!opts.dryRun) {
            if (file.mkdirs()) {
                return true;
            }
            logger.error("Failed to create dir " + dstDir);
            return false;
        }
        return true;
    }

    private String getDstFileName(ImgFileData src, String dstDir, String dstName) throws IOException {
        int index = dstName.lastIndexOf('.');
        String baseName = dstName.substring(0, index);
        String baseExt = dstName.substring(index + 1);
        int startIdx = 0;
        while(true) {
            String newFileName;
            if(startIdx==0) {
                newFileName = dstDir + File.separator + dstName;
            } else {
                newFileName = String.format("%s%s_%03d.%s",  dstDir + File.separator, baseName, startIdx, baseExt);
            }
            logger.debug("New name is " + newFileName);
            File existFile = new File(newFileName);
            if(!existFile.exists()) {
                return newFileName;
            }
            // same original date and same size
            if(isDuplicate(existFile, src)) {
                long srcSize = src.getPath().toFile().length();
                long dstSize = existFile.length();
                boolean isSizeSame = srcSize == dstSize;
                if(!opts.fileSizeSame) {
                    isSizeSame = srcSize <= dstSize;
                }
                if(isSizeSame) {
                    if(opts.showDupLists) {
                        dupList.add(new ResultData(src.getPath().toString(), newFileName));
                    }
                    dupCount++;
                    return null;
                }
            }
            startIdx++;
        }
    }

    private boolean isDuplicate(File dstFile, ImgFileData src) throws IOException {
        BasicFileAttributes attr = Files.readAttributes(dstFile.toPath(), BasicFileAttributes.class);
        Date lastModifiedDate = new Date(attr.lastModifiedTime().toMillis());
        Date lastCreatedDate = new Date(attr.creationTime().toMillis());
        ImgFileData tgt = new ImgFileData(dstFile.toPath(), lastModifiedDate, lastCreatedDate);
        if(tgt.parseExif()) {
            if(src.getImageDate()!=null && tgt.getImageDate().compareTo(src.getImageDate()) == 0) {
                return true;
            }
        }
        if(tgt.getFileModifiedDate().compareTo(src.getFileModifiedDate())!=0) {
            return false;
        }
        return Arrays.equals(tgt.getHash(), src.getHash());
    }

    private void moveFile(Path orgFile, String newName) {
        File newDstFile = new File(newName);
        logger.debug(String.format("%s to %s", orgFile, newDstFile.getAbsolutePath()));
        try {
            if(!opts.dryRun) {
                if (opts.moveFiles) {
                    Files.move(orgFile, newDstFile.toPath());
                } else {
                    Files.copy(orgFile, newDstFile.toPath());
                }
            }
            doneList.add(new ResultData(orgFile.toString(), newName));
            moved++;
        } catch (IOException e) {
            logger.error(String.format("Failed to move %s due to %s", orgFile, e));
            failedList.add(new ResultData(orgFile.toString(), newName));
        }
    }
}
