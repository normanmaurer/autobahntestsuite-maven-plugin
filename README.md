Maven Autobahn Testsuite Plugin
==============================

Maven plugin which allows to run the [Autobahn Testsuite](http://autobahn.ws/testsuite/) as part of your maven build.

# Requirements
If you want to have the Testsuite executed as part of your build you need to provide a Main class which will
startup a WebSockets server which needs to behave like:

* Echo back Text/Binary messages
* Handle everything else like stated in the [rfc6455](http://tools.ietf.org/html/rfc6455)


# Adding it to your build
Adding the Testsuite and so make it part of your build is as easy as adding it to the pom.xml file of your
maven project:

    <build>
      <plugin>
        <groupId>me.normanmaurer.maven.autobahntestsuite</groupId>
        <artifactId>autobahntestsuite-maven-plugin</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <configuration>
          <!-- The class which contains a main method that accept the port as paramater and startup the -->
          <!-- the server. -->
          <mainClass>io.netty.testsuite.websockets.autobahn.AutobahnServer</mainClass>

          <!-- Optional configuration -->
          <!-- ---------------------- -->
          <!-- The port to bind the server on. Default is to choose a random free port. -->
          <port>-1</port>

          <!-- The number of milliseconds to wait for the server to startup. Default is 10000 ms. -->
          <waitTime>10000</waitTime>

          <!-- Specify if a JUnit compatible Xml file will be generated. This can be used by most CI's. -->
          <!-- Default is true -->
          <generateJUnitXml>true</generateJUnitXml>

          <!-- A list of cases to execute. Default is to execute all via *.-->
          <cases>
            <case>*</case>
          </cases>

          <!-- A list of cases to exclude. Default is none. --> 
          <excludeCases></excludeCases>

          <!-- Specify if the plugin should fail on non strict behaviour. Default is false. -->
          <failOnNonStrict>false</failOnNonStrict>
        </configuration>
        <executions>
          <execution>
            <phase>test</phase>
            <goals>
              <goal>fuzzingclient</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </build>


This will execute the fuzzingclient tests as part of the test phase and fail the phase if one of the test cases
fails.

After the run was complete you will find test-reports in the `target/autobahntestsuite-report`, which contains all
the details about every test case.


