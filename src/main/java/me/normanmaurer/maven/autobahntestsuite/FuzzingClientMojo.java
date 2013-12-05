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


import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mojo which execute the FuzzingClient which is part of the <a href="http://autobahn.ws/testsuite/">Autobahn Testsuite</a>
 */
@Mojo(name = "fuzzingclient", defaultPhase = LifecyclePhase.INTEGRATION_TEST,
requiresDependencyResolution = ResolutionScope.TEST)
public class FuzzingClientMojo
        extends AbstractMojo {
    private static final List<String> ALL_CASES = Collections.unmodifiableList(Arrays.asList("*"));
    private static final Map<String, Object> OPTIONS = Collections.<String, Object>singletonMap("version", 18);

    /**
     * The port on which the Server will listen.
     */
    @Parameter(defaultValue = "-1", property="port", required = true)
    private int port;

    /**
     * A list of cases to run during the test. Default is to run all cases.
     */
    @Parameter(property = "cases")
    private List<String> cases;

    /**
     * A list of cases to exclude during the test. Default is to exclude none.
     */
    @Parameter(property = "excludeCases")
    private List<String> excludeCases;

    /**
     * The class which is used to startup the Server. It will pass the port in as argument to the main(...) method.
     */
    @Parameter(property = "mainClass", required = true)
    private String mainClass;

    /**
     * The number of milliseconds to max wait for the server to startup. Default is 10000 ms
     */
    @Parameter(property = "waitTime")
    private long waitTime;

    /**
     * Configure if the Testsuite should be failed on non strict behaviour of the Server. Default is to not fail, as it
     * is still conform to the RFC.
     */
    @Parameter(property = "failOnNonStrict")
    private boolean failOnNonStrict;

    /**
     * Configure if the Testsuite should generate JUnit xml reports. Those reports are often used by CI Systems. Default
     * is true.
     */
    @Parameter(property = "generateJUnitXml", defaultValue = "true")
    private boolean generateJUnitXml;

    @Component
    private MavenProject project;

    @SuppressWarnings("unchecked")
    private ClassLoader getClassLoader() throws MojoExecutionException {
        try {
            List<String> classpathElements = project.getTestClasspathElements();
            classpathElements.add(project.getBuild().getOutputDirectory() );
            classpathElements.add(project.getBuild().getTestOutputDirectory() );
            URL urls[] = new URL[classpathElements.size()];

            for ( int i = 0; i < classpathElements.size(); i++) {
                urls[i] = new File(classpathElements.get(i)).toURI().toURL();
            }
            return new URLClassLoader(urls, getClass().getClassLoader());
        } catch (Exception e) {
            throw new MojoExecutionException("Couldn't create a classloader", e);
        }
    }

    @Override
    public void execute()
            throws MojoExecutionException, MojoFailureException {

        final AtomicReference<Exception> error = new AtomicReference<Exception>();
        Thread runner = null;
        try {
            String host;
            try {
                host = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                getLog().debug("Unable to detect localhost address, using 127.0.0.1 as fallback");
                host = "127.0.0.1";
            }
            if (port == -1) {
                // Get some random free port
                port = AutobahnUtils.getFreePort(host);
            }
            runner = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.currentThread().setContextClassLoader(getClassLoader());
                        Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(mainClass);
                        Method main = clazz.getMethod("main", String[].class);
                        main.invoke(null, (Object) new String[] { String.valueOf(port) });
                    } catch (Exception e) {
                        error.set(e);
                    }
                }
            });
            runner.setDaemon(true);
            runner.start();
            try {
                // wait for 50 milliseconds to give the server some time to startup
                Thread.sleep(500);
            } catch (InterruptedException ignore) {
                // ignore
            }
            if (waitTime <= 0) {
                // use 10 seconds as default
                waitTime = 10000;
            }

            // Wait until the server accepts connections
            long sleepTime = waitTime / 10;
            for (int i = 0; i < 10; i++) {
                Throwable cause = error.get();
                if (cause != null) {
                    throw new MojoExecutionException("Unable to start server", cause);
                }
                Socket socket = new Socket();
                try {
                    socket.connect( new InetSocketAddress(host, port));
                    break;
                } catch (IOException e) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ignore) {
                        // restore interrupt state
                        Thread.currentThread().interrupt();
                    }
                } finally {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
                if (i == 9) {
                    throw new MojoExecutionException("Unable to connect to server", error.get());
                }
            }

            if (cases == null || cases.isEmpty()) {
                cases = ALL_CASES;
            }
            if (excludeCases == null) {
                excludeCases = Collections.emptyList();
            }
            List<FuzzingCaseResult> results = AutobahnTestSuite.runFuzzingClient(
                    "autobahntestsuite-maven-plugin", "ws://" + host + ":" + port,  OPTIONS, cases, excludeCases);

            if (generateJUnitXml) {
                try {
                    writeJUnitXmlReport(results);
                } catch (Exception e) {
                    throw new MojoExecutionException("Unable to generate xml reports", e);
                }
            }
            List<FuzzingCaseResult> failed = new ArrayList<FuzzingCaseResult>();
            for (FuzzingCaseResult result: results) {
                FuzzingCaseResult.Behavior behavior = result.behavior();
                if (failOnNonStrict && behavior == FuzzingCaseResult.Behavior.NON_STRICT) {
                    failed.add(result);
                } else if (behavior!= FuzzingCaseResult.Behavior.OK
                        && behavior != FuzzingCaseResult.Behavior.INFORMATIONAL
                        && behavior != FuzzingCaseResult.Behavior.NON_STRICT) {
                    failed.add(result);
                }
            }
            if (!failed.isEmpty()) {
                StringBuilder sb = new StringBuilder("\nFailed test cases:\n");
                for (FuzzingCaseResult result: failed) {
                    sb.append("\t");
                    sb.append(result.toString());
                    sb.append("\n");
                }
                throw new MojoFailureException(sb.toString());
            } else {
                getLog().info("All test cases passed" );
            }
        } finally {
             if (runner != null) {
                 runner.interrupt();
             }
        }
    }


    private void writeJUnitXmlReport(List<FuzzingCaseResult> results)
            throws ParserConfigurationException, TransformerException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory .newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        String className = getClass().getName();
        int failures = 0;
        long suiteDuration = 0;
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("testsuite");
        rootElement.setAttribute("name", className);
        rootElement.setAttribute("tests", Integer.toString(results.size()));
        rootElement.setAttribute("errors", Integer.toString(0));
        rootElement.setAttribute("skipped", Integer.toString(0));

        for (FuzzingCaseResult r: results) {
            Element testcase = doc.createElement("testcase");
            testcase.setAttribute("classname", className);
            testcase.setAttribute("name", r.caseName());

            long duration = r.duration();
            suiteDuration += duration;
            testcase.setAttribute("time", Double.toString(duration / 1000.0));

            FuzzingCaseResult.Behavior behavior = r.behavior();
            if (failOnNonStrict && behavior == FuzzingCaseResult.Behavior.NON_STRICT) {
                testcase.appendChild(failure(doc, r));
                failures++;
            } else if (behavior!= FuzzingCaseResult.Behavior.OK
                    && behavior != FuzzingCaseResult.Behavior.INFORMATIONAL
                    && behavior != FuzzingCaseResult.Behavior.NON_STRICT) {
                testcase.appendChild(failure(doc, r));
                failures++;
            }

            rootElement.appendChild(testcase);
        }
        rootElement.setAttribute("failures", Integer.toString(failures));
        rootElement.setAttribute("time", Double.toString(suiteDuration / 1000.0));
        doc.appendChild(rootElement);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        String filename = AutobahnTestSuite.OUTDIR + "/TEST-" + className + ".xml";
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(filename));
        transformer.transform(source, result);
    }

    private Element failure(Document doc, FuzzingCaseResult result) {
        Element failure = doc.createElement("failure");
        failure.setAttribute("type", "behaviorMissmatch");
        failure.setNodeValue("Expected behavior=[" + FuzzingCaseResult.Behavior.OK + "] but was [" + result.behavior() + "]");
        return failure;
    }

}
