package com.fsck.k9.backend.eas;


import com.fsck.k9.protocol.eas.Eas;


class EasSyncWindow implements SyncWindow {
    private final int value;
    private final SyncWindowUnit unit;


    public static EasSyncWindow newInstance(int syncWindow) {
        return new EasSyncWindow(syncWindow);
    }

    private EasSyncWindow(int syncWindow) {
        switch (syncWindow) {
            case Eas.FILTER_1_DAY: {
                value = 1;
                unit = SyncWindowUnit.DAYS;
                break;
            }
            case Eas.FILTER_3_DAYS: {
                value = 3;
                unit = SyncWindowUnit.DAYS;
                break;
            }
            case Eas.FILTER_1_WEEK: {
                value = 1;
                unit = SyncWindowUnit.WEEKS;
                break;
            }
            case Eas.FILTER_2_WEEKS: {
                value = 2;
                unit = SyncWindowUnit.WEEKS;
                break;
            }
            case Eas.FILTER_1_MONTH: {
                value = 1;
                unit = SyncWindowUnit.MONTHS;
                break;
            }
            case Eas.FILTER_ALL: {
                value = 0;
                unit = SyncWindowUnit.ALL;
                break;
            }
            default: {
                throw new IllegalStateException("Invalid value for syncWindow: " + syncWindow);
            }
        }
    }


    @Override
    public int getValue() {
        return value;
    }

    @Override
    public SyncWindowUnit getUnit() {
        return unit;
    }
}
