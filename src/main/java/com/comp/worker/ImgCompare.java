package com.comp.worker;

// todo:
public class ImgCompare extends ChainedWorker {
    protected ImgCompare(final String name, final ChainedWorker nextChainedWorker) {
        super(name, nextChainedWorker, 0, null);
    }
}
