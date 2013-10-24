package me.normanmaurer.maven.autobahntestsuite;


public class FuzzingCaseResult {

    enum Behavior {
        FAILED,
        OK,
        NON_STRICT,
        WRONG_CODE,
        UNCLEAN,
        FAILED_BY_CLIENT,
        INFORMATIONAL,
        UNIMPLEMENTED
    }
    private final String caseName;
    private final Behavior behavior;
    private final Behavior behaviorClose;
    private final long duration;
    private final long remoteCloseCode;
    private final String reportFile;

    FuzzingCaseResult(String caseName, Behavior behavior, Behavior behaviorClose, long duration, long remoteCloseCode, String reportFile) {
        this.caseName = caseName;
        this.behavior = behavior;
        this.behaviorClose = behaviorClose;
        this.duration = duration;
        this.remoteCloseCode = remoteCloseCode;
        this.reportFile = reportFile;
    }

    public String caseName() {
        return caseName;
    }

    public Behavior behavior() {
        return behavior;
    }

    public Behavior behaviorClose() {
        return behaviorClose;
    }

    public long duration() {
        return duration;
    }

    public Long remoteCloseCode() {
        return remoteCloseCode;
    }

    public String reportFile() {
        return reportFile;
    }

    @Override
    public String toString() {
        return "case=" + caseName + ", behavior=" + behavior.name() + ", behaviorClose=" + behaviorClose.name() +
                ", duration=" + duration + ", remoteCloseCode=" + remoteCloseCode + ", reportFile=" + reportFile;
    }
}
