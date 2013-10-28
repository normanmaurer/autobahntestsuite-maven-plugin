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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
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
    private static final Map<String, Object> OPTIONS;
    static {
        Map<String, Object> opts = new HashMap<String, Object>();
        opts.put("version", 18);
        OPTIONS = Collections.unmodifiableMap(opts);
    }

    /**
     * The name of the agent which will be used in the generated reports.
     */
    @Parameter(defaultValue = "autobahntestsuite-agent", property="agent", required = true)
    private String agent;

    /**
     * The ipaddress of the host on which the Server will run.
     */
    @Parameter(defaultValue = "127.0.0.1", property="host", required = true)
    private String host;

    /**
     * The port on which the Server will listen.
     */
    @Parameter(defaultValue = "9001", property="port", required = true)
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
    @Parameter(property = "mainClass")
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

    @Component
    private MavenProject project;

    @SuppressWarnings("unchecked")
    private ClassLoader getClassLoader() throws MojoExecutionException {
        try {
            List<String> classpathElements = project.getCompileClasspathElements();
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
        Thread.currentThread().setContextClassLoader(getClassLoader());

        final AtomicReference<Exception> error = new AtomicReference<Exception>();
        Thread runner = null;
        try {
            if (mainClass != null) {
                runner = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
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
                        socket.connect( new InetSocketAddress("127.0.0.1", port));
                        break;
                    } catch (IOException e) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException ignore) {
                            // ignore
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
            }


            if (cases == null || cases.isEmpty()) {
                cases = ALL_CASES;
            }
            if (excludeCases == null) {
                excludeCases = Collections.emptyList();
            }
            List<FuzzingCaseResult> results = AutobahnTestSuite.runFuzzingClient(
                    agent, "ws://" + host + ":" + port,  OPTIONS, cases, excludeCases);
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
}
