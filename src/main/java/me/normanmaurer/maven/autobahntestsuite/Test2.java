package me.normanmaurer.maven.autobahntestsuite;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.python.core.PyDictionary;
import org.python.core.PyString;
import org.python.util.PythonInterpreter;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Test2 {
    private static final String OUTDIR = "target/autobahntestsuite-report";

    public static void main(String args[]) throws Exception {

        List<FuzzingCaseResult> results = new ArrayList<FuzzingCaseResult>();
        JSONParser parser = new JSONParser();
        InputStreamReader reader = new InputStreamReader(new FileInputStream(OUTDIR + "/index.json"));
        JSONObject object = (JSONObject) parser.parse(reader);
        JSONObject agent = (JSONObject) object.get("netty");

        for (Object cases: agent.keySet()) {
            JSONObject c = (JSONObject) agent.get(cases);
            String behavior = (String) c.get("behavior");
            String behaviorClose = (String) c.get("behaviorClose");
            Number duration = (Number) c.get("duration");
            Number remoteCloseCode = (Number) c.get("remoteCloseCode");
            String reportfile = (String) c.get("reportfile");
            System.out.println(new FuzzingCaseResult(cases.toString(),
                    FuzzingCaseResult.Behavior.valueOf(behavior), FuzzingCaseResult.Behavior.valueOf(behaviorClose),
                    duration.longValue(), remoteCloseCode.longValue(), reportfile).toString());
        }

    }
}
