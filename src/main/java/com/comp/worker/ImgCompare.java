package com.comp.worker;

import com.comp.exif.ImgFileData;
import com.comp.exif.Parser;
import com.comp.phototidy.Options;
import dev.brachtendorf.jimagehash.hash.Hash;
import dev.brachtendorf.jimagehash.hashAlgorithms.AverageHash;
import dev.brachtendorf.jimagehash.hashAlgorithms.HashingAlgorithm;
import dev.brachtendorf.jimagehash.hashAlgorithms.PerceptiveHash;
import dev.brachtendorf.jimagehash.matcher.exotic.SingleImageMatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;

public class ImgCompare extends ChainedWorker {
    private static final Logger logger = LogManager.getLogger(ImgCompare.class);

    public ImgCompare(final Options opts, final ChainedWorker nextChainedWorker) {
        super("Image deduplicate", nextChainedWorker, 1024, logger);
    }
/*
    private boolean isDuplicate(File dstFile, ImgFileData srcImage) throws IOException {
        final BasicFileAttributes attr = Files.readAttributes(dstFile.toPath(), BasicFileAttributes.class);
        final ImgFileData tgtImage = new ImgFileData(dstFile.toPath(), attr);
        if (Parser.parseExif(tgtImage)) {
            if (srcImage.getExifImageDate() != null &&
                    tgtImage.getExifImageDate().compareTo(srcImage.getExifImageDate()) == 0) {
                return true;
            }
        }
        if (tgtImage.getFileModifiedDate().compareTo(srcImage.getFileModifiedDate()) != 0) {
            return false;
        }
        final long srcSize = srcImage.getPath().toFile().length();
        final long dstSize = dstFile.length();
        if (srcSize != dstSize) {
            return false;
        }
        return Arrays.equals(tgtImage.getHash(), srcImage.getHash());
    }

    // https://github.com/KilianB/JImageHash
    private void check() throws IOException {
        File img0 = new File("path/to/file.png");
        File img1 = new File("path/to/secondFile.jpg");

        HashingAlgorithm hasher = new PerceptiveHash(32);

        Hash hash0 = hasher.hash(img0);
        Hash hash1 = hasher.hash(img1);

        double similarityScore = hash0.normalizedHammingDistance(hash1);

        if(similarityScore < .2) {
            //Considered a duplicate in this particular case
        }

//Chaining multiple matcher for single image comparison

        SingleImageMatcher matcher = new SingleImageMatcher();
        matcher.addHashingAlgorithm(new AverageHash(64),.3);
        matcher.addHashingAlgorithm(new PerceptiveHash(32),.2);

        if(matcher.checkSimilarity(img0,img1)) {
            //Considered a duplicate in this particular case
        }
    }


 */
}
