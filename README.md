Maven Autobahn Testsuite Plugin
==============================

Maven plugin which allows to run the ([Autobahn Testsuite](http://autobahn.ws/testsuite/)) as part of your maven build.

# Requirements
If you want to have the Testsuite executed as part of your build you need to provide a Main class which will
startup a WebSockets server which needs to behave like:

* Echo back Text/Binary frames
* Echo back Ping frames as Pong frames
* Ignore Pong frames
* Handle everything else like stated in the ([rfc6455](http://tools.ietf.org/html/rfc6455))


# Adding it to your build
Adding the Testsuite and so make it part of your build is as easy as adding it to the pom.xml file of your
maven project:

    <build>
      <plugin>
        <groupId>me.normanmaurer.maven.autobahntestsuite</groupId>
        <artifactId>autobahntestsuite-maven-plugin</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <configuration>
          <host>127.0.0.1</host>
          <port>9001</port>
          <mainClass>io.netty.testsuite.websockets.autobahn.AutobahnServer</mainClass>
          <cases>
            <case>*</case>
          </cases>
          <excludeCases></excludeCases>
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


