package me.normanmaurer.maven.autobahntestsuite;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: norman
 * Date: 24.10.13
 * Time: 13:55
 * To change this template use File | Settings | File Templates.
 */
public class Test {

    public static void main(String args[]) {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("version", 18);
        List<FuzzingCaseResult> results = AutobahnTestSuite.runFuzzingClient("netty", "ws://127.0.0.1:9000", options, Arrays.asList("*"), Collections.<String>emptyList());
        for (FuzzingCaseResult result: results) {
            System.out.println(result);
        }
    }
}
