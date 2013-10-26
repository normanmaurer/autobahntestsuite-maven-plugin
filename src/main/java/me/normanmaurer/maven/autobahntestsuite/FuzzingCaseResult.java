/*
 * Copyright 2013 Norman Maurer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        UNIMPLEMENTED;

        static Behavior parse(String value) {
            if (value.equals("NON-STRICT")) {
                return NON_STRICT;
            } else if (value.equals("WRONG CODE")) {
                return WRONG_CODE;
            } else if (value.equals("FAILED BY CLIENT")) {
                return FAILED_BY_CLIENT;
            }
            return valueOf(value);
        }
    }
    private final String caseName;
    private final Behavior behavior;
    private final Behavior behaviorClose;
    private final long duration;
    private final Long remoteCloseCode;
    private final String reportFile;

    FuzzingCaseResult(String caseName, Behavior behavior, Behavior behaviorClose, long duration, Long remoteCloseCode, String reportFile) {
        this.caseName = caseName;
        this.behavior = behavior;
        this.behaviorClose = behaviorClose;
        this.duration = duration;
        this.remoteCloseCode = remoteCloseCode;
        this.reportFile = reportFile.replace(".json", ".html");
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
        return "[" + caseName + "] behavior: " + behavior.name() + ", behaviorClose: " + behaviorClose.name() +
                ", duration: " + duration + "ms, remoteCloseCode: " + remoteCloseCode + ", reportFile: " + reportFile;
    }
}
