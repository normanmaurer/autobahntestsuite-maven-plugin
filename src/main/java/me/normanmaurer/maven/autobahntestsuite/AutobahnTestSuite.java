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

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.python.core.PyArray;
import org.python.core.PyBoolean;
import org.python.core.PyDictionary;
import org.python.core.PyString;
import org.python.util.PythonInterpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Allows to run the fuzzingclient of the <a href="http://autobahn.ws/testsuite/" >autobahntestsuite</a>.
 */
public class AutobahnTestSuite {

    static final String OUTDIR = "target/autobahntestsuite-reports";
    private static final OutputStream DEV_NULL = new DevNullOutputStream();
    public static List<FuzzingCaseResult> runFuzzingClient(String agent, String url, Map options,
                                        List<String> cases, List<String> excludeCases) {
        PythonInterpreter interp =
                new PythonInterpreter();
        interp.setErr(DEV_NULL);
        interp.exec("import sys");
        interp.exec("from autobahntestsuite import wstest");
        PyDictionary opts = new PyDictionary();
        opts.__setitem__(new PyString("mode"), new PyString("fuzzingclient"));
        interp.set("opts", opts);

        PyDictionary spec = createFuzzingClientSpec(agent, url, options, cases, excludeCases);
        interp.set("spec", spec);
        interp.exec("wstest.start(opts, spec)");

        try {
            List<FuzzingCaseResult> results = parseResults(agent);
            if (results == null) {
                throw new IllegalStateException("No results");
            }
            return results;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static PyDictionary createFuzzingClientSpec(String agent, String url, Map options,
                                           List<String> cases, List<String> excludeCases) {
        PyDictionary dict = new PyDictionary();

        dict.__setitem__(new PyString("failByDrop"), new PyBoolean(false));
        dict.__setitem__(new PyString("outdir"), new PyString(OUTDIR));

        PyDictionary server = new PyDictionary();
        server.__setitem__(new PyString("agent"), new PyString(agent));
        server.__setitem__(new PyString("url"), new PyString(url));

        dict.__setitem__(new PyString("servers"), new PyArray(PyDictionary.class, new PyDictionary[] { server }));

        PyDictionary opts = new PyDictionary();
        opts.putAll(options);
        dict.__setitem__(new PyString("options"), opts);

        dict.__setitem__(new PyString("cases"), new PyArray(String.class, cases.toArray(new String[cases.size()])));
        dict.__setitem__(new PyString("exclude-cases"), new PyArray(PyString.class, excludeCases.toArray(new String[excludeCases.size()])));
        return dict;
    }

    private static List<FuzzingCaseResult> parseResults(String agentString) throws IOException, ParseException {
        List<FuzzingCaseResult> results = new ArrayList<FuzzingCaseResult>();
        JSONParser parser = new JSONParser();
        InputStreamReader reader = new InputStreamReader(new FileInputStream(OUTDIR + "/index.json"));
        JSONObject object = (JSONObject) parser.parse(reader);
        JSONObject agent = (JSONObject) object.get(agentString);

        if (agent == null) {
            return null;
        }
        for (Object cases: agent.keySet()) {
            JSONObject c = (JSONObject) agent.get(cases);
            String behavior = (String) c.get("behavior");
            String behaviorClose = (String) c.get("behaviorClose");
            Number duration = (Number) c.get("duration");
            Number remoteCloseCode = (Number) c.get("remoteCloseCode");

            Long code;
            if (remoteCloseCode == null) {
                code = null;
            } else {
                code = remoteCloseCode.longValue();
            }
            String reportfile = (String) c.get("reportfile");
            FuzzingCaseResult result = new FuzzingCaseResult(cases.toString(),
                    FuzzingCaseResult.Behavior.parse(behavior), FuzzingCaseResult.Behavior.parse(behaviorClose),
                    duration.longValue(), code, OUTDIR + "/" + reportfile);

            results.add(result);
        }
        return results;
    }

    private final static class DevNullOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            // > /dev/null
        }
    }
}
