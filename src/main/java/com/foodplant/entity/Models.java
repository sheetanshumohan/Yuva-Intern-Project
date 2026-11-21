package com.foodplant.entity;

public final class Models {
    private Models() {}
    public enum Role { ADMIN, STAFF, VIEWER }
    public enum Shift { DAY, EVENING, NIGHT }
    public enum BatchStatus { PLANNED, IN_PROGRESS, COMPLETED, QUARANTINED }
    public enum QualityResult { PASS, FAIL, PENDING }
}
