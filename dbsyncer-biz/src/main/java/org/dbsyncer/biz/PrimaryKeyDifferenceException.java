package org.dbsyncer.biz;

import org.dbsyncer.biz.vo.PrimaryKeyDifferenceVO;

public class PrimaryKeyDifferenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private PrimaryKeyDifferenceVO difference;

    public PrimaryKeyDifferenceException(PrimaryKeyDifferenceVO difference) {
        super("主键配置与目标表实际主键不一致");
        this.difference = difference;
    }

    public PrimaryKeyDifferenceVO getDifference() {
        return difference;
    }
}
