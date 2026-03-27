package org.dbsyncer.biz.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FieldDifferenceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<FieldDiffItem> addedFields = new ArrayList<>();

    private List<FieldDiffItem> missingFields = new ArrayList<>();

    private List<FieldDiffItem> typeMismatched = new ArrayList<>();

    private List<FieldDiffItem> lengthMismatched = new ArrayList<>();

    public List<FieldDiffItem> getAddedFields() {
        return addedFields;
    }

    public void setAddedFields(List<FieldDiffItem> addedFields) {
        this.addedFields = addedFields;
    }

    public List<FieldDiffItem> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<FieldDiffItem> missingFields) {
        this.missingFields = missingFields;
    }

    public List<FieldDiffItem> getTypeMismatched() {
        return typeMismatched;
    }

    public void setTypeMismatched(List<FieldDiffItem> typeMismatched) {
        this.typeMismatched = typeMismatched;
    }

    public List<FieldDiffItem> getLengthMismatched() {
        return lengthMismatched;
    }

    public void setLengthMismatched(List<FieldDiffItem> lengthMismatched) {
        this.lengthMismatched = lengthMismatched;
    }

    public boolean isHasDifference() {
        return !addedFields.isEmpty()
                || !missingFields.isEmpty()
                || !typeMismatched.isEmpty()
                || !lengthMismatched.isEmpty();
    }

    @Override
    public String toString() {
        return "FieldDifferenceVO{" +
                "addedFields=" + addedFields.size() +
                ", missingFields=" + missingFields.size() +
                ", typeMismatched=" + typeMismatched.size() +
                ", lengthMismatched=" + lengthMismatched.size() +
                ", hasDifference=" + isHasDifference() +
                '}';
    }
}
