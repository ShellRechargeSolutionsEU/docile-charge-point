# docile-charge-point [![Codacy Badge](https://api.codacy.com/project/badge/Grade/1a97f5dd32e24653b9b45d5c8b4a14b7)](https://www.codacy.com/app/reinierl/docile-charge-point)

<img src="logos/docile-charge-point-zoom-circle.256x.png" alt="Logo" align="right">

A scriptable [OCPP](https://www.openchargealliance.org/protocols/ocpp-20/)
charge point simulator. Supports OCPP 1.5, 1.6 and 2.0 using JSON over
WebSocket as the transport.

Not as continuously ill-tempered as
[abusive-charge-point](https://github.com/chargegrid/abusive-charge-point), but
it can be mean if you script it to be.

The aims for this thing:

 * Simulated charge point behaviors expressed as simple scripts, that can be redistributed separately from the simulator that executes them

 * Simulate lots of charge points at once that follow given behavior scripts

 * Checkable test assertions in the scripts

 * Non-interactive command line interface, which combined with the test assertions makes it useful for use in CI/CD pipelines

Scripts are expressed as Scala files, in which you can use predefined functions
to send OCPP messages, make expectations about incoming messages or declare the
test case failed. And next to that, all of Scala is at your disposal! Examples
of behavior scripts it can run already are a simple heartbeat script ([OCPP
1.x](examples/ocpp1x/heartbeat.scala) / [OCPP
2.0](examples/ocpp20/heartbeat.scala)) and a full simulation of a charge
session ([OCPP 1.x](examples/ocpp1x/do-a-transaction.scala) / [OCPP
2.0](examples/ocpp20/do-a-transaction.scala)). The full set of OCPP and testing
specific functions can be found in
[CoreOps](core/src/main/scala/chargepoint/docile/dsl/CoreOps.scala),
[expectations.Ops](core/src/main/scala/chargepoint/docile/dsl/expectations/Ops.scala)
and `shortsend.Ops` ([OCPP
1.x](core/src/main/scala/chargepoint/docile/dsl/shortsend/OpsV1X.scala) / [OCPP
2.0](core/src/main/scala/chargepoint/docile/dsl/shortsend/OpsV20.scala)). For OCPP
2.0, there is also a [special set of
operations](core/src/main/scala/chargepoint/docile/dsl/ocpp20transactions/Ops.scala)
to deal with the complicated stateful transaction management.

There are by now four ways to run the simulator:

  * On the command line, with a behavior script given as a file

  * On the command line, directly controlling the charge point behavior using an interactive prompt

  * In a Docker container, which allows you to have a simulated charge point execute a behavior script somewhere in the cloud, testing your system continuously with as little as possible work on your part

  * As a library dependency of another application, which allows you to combine it with other test tools, and write tests that interface with other network services besides just OCPP central systems.

## Running on the command line

The simplest way to run docile-charge-point is on the command line so we will discuss that first.

To run the simulator, you first have to compile it with this command:

```bash
sbt assembly
```

When that completes successfully, you can run the simulator like this, from the root directory of the project:

```bash
java -jar cmd/target/scala-2.12/docile.jar -c <charge point ID> -v <OCPP version> <Central System endpoint URL> <test scripts...>
```

so e.g.:

```bash
java -jar cmd/target/scala-2.12/docile.jar -c chargepoint0123 -v 1.6 ws://example.org/ocpp-j-endpoint examples/ocpp1x/heartbeat.scala
```

See `java -jar cmd/target/scala-2.12/docile.jar --help` for more options.

If you're looking for a Central System to run docile-charge-point against, check [SteVe](https://github.com/RWTH-i5-IDSG/steve) or [OCPP 1.6 Backend](https://github.com/gertjana/ocpp16-backend).

## Script structure

The charge point scripts that you specify on the command line are ordinary
Scala files, that will be loaded and executed at runtime by
docile-charge-point. To write these files, besides the normal standard Scala
library, you can use a DSL in which you can send OCPP messages and expect a
certain behavior in return from the Central System.

### Simple one-line scripts

As a very simple example of the DSL, consider this script:

```scala
heartbeat()
```

Simple as it looks, this script already does two things:

 * It sends an OCPP heartbeat request
 * It asserts that the Central System responds with an OCPP heartbeat response

In fact, there are such functions doing these two things for every request in OCPP 1.6 that a Charge Point can send to a Central System. There is a `statusNotification` that will, indeed, send a StatusNotification request.

Where these requests contain fields with data, these data can be given by supplying values for certain named arguments of those functions. By default, `statusNotification()` will mark the charge point as _Available_. To make it seem _Charging_ instead, do this:

```
statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Charging)))
```

Ouch, that's quite some code to express the OCPP 1.6 charging state. Let me
digress to explain it. The reason for it is that this tool uses the case class
hierarchy from the [NewMotion OCPP library](https://github.com/NewMotion/ocpp/)
to express OCPP messages and their constituents parts. Those case classes
however are different from the data types found in the OCPP specification, to
encode the information in a more type-safe manner and to provide an abstract
representation that can be transmitted in either a 1.6 or a 1.5 format. Here it
is the compatibility with 1.5 that means that we can't just write
`ChargePointStatus.Charging`, because that couldn't be serialized for 1.5.
`ChargePointStatus.Occupied(Some(OccupancyKind.Charging))` means: _Occupied_ for
1.5 terms, but if you want to encode it for 1.6, you can be more precise and
make it _Charging_.

This kind of abstraction from version-specific messages can be a useful feature
in some scenarios: you can now easily test that a back-office handles a certain
behavior correctly both with 1.5 and 1.6.

An interactive session with tab completion (see below) can come in handy to
explore the ways you can specify OCPP messages.

### Stringing operations together

As an example of how you can string DSL operations together to specify a
meaningful behavior, let's look at the "do a transaction" example in its full
glory. There are comments explaining what happens where:

```scala
// The idTag which can be used to look up who started the transaction
// This is ordinary Scala defining a string value; no docile DSL so far.
val chargeTokenId = "01234567"

// Now let's send an authorize request to the Central System and expect a
// response. If the expected response comes, it is returned from the
// `authorize` function.
// This response objet then contains an `idTag` field with the authorization
// information from the Central System. We call that authorization info `auth`.
val auth = authorize(chargeTokenId).idTag

// We check whether the charge token we sent is authorized. This is just plain
// Scala again.
if (auth.status == AuthorizationStatus.Accepted) {

  // If it's authorized, we start a transaction. Starting a transaction in OCPP
  // means: first set the status to Preparing...
  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Preparing)))

  // ...then start a transaction. The startTransaction function again returns
  // the StartTransaction response from the Central System, from which we take
  // the transaction ID and assign it the name `transId`
  val transId = startTransaction(meterStart = 300, idTag = chargeTokenId).transactionId

  // ... and then, we notify the Central System that this charge point has
  // started charging.
  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Charging)))

  // Another DSL operation: prompt. This allows us to prompt the user for some
  // keyboard input. Here, we just want him to press ENTER when
  // docile-charge-point should stop the transaction.
  // This `prompt` function will block until the user presses ENTER.
  prompt("Press ENTER to stop charging")

  // Okay, the user has apparently pressed ENTER. Let's stop.
  // First notify that the status of our connector is going to Finishing...
  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Finishing)))

  // ...then send a StopTransaction request...
  stopTransaction(transactionId = transId, idTag = Some(chargeTokenId))

  // ...and notify that our connector is Available again
  statusNotification(status = ChargePointStatus.Available())

// Oh yeah, it's also possible that the Central System does not authorize the
// transaction
} else {
  // In that case we consider this script failed.
  fail("Not authorized")
}
```

The important take-aways here are:

  * DSL operations are just Scala function calls

  * Typically, DSL operations will block until the user input or Central System
    response has come, and will return this result as from the function call

### Writing scripts with autocomplete in your favourite IDE

I admit it's quite inconvenient that in the script files you have the full power of Scala, but you don't have IDE support to help you suggest methods to call or highlight mistakes. So I've added a project in which you can edit your docile-charge-point script with IDE support.

How does that work? Well, docile-charge-point actually loads its script files by adding a bunch of imports and other boilerplate before and after the script file contents before the code is compiled. So you would have IDE support if you would be editing the file with the boilerplate in place. I don't want to make it the standard way of working to add this boilerplate in the scripts though, because that would make it harder to read the scripts for outsiders and it would lead to more version compatibility troubles between versions of docile-charge-point.

To still allow you to have your autocomplete, there is a special template project in which you can edit a script with the boilerplate included and also execute it. In that project, docile is loaded as a library so that it is possible to run the interpreter on your compiled code without running the script file loader that adds the boilerplate.

To use it, open the template project in the [autocomplete-template-project](autocomplete-template-project/) directory. The project already has an sbt file setting up the library dependencies
on the docile-charge-point DSL core. IntelliJ IDEA will import this project just fine. You'll then find a file [TestScript.scala](autocomplete-template-project/src/main/scala/TestScript.scala) that contains all the boilerplate and a comment that says `// INSERT SCRIPT HERE`. If you start editing at the place where that comment is, you can type anything that you can also type in a script file without the boilerplate. IDEA will offer you all its suggestion magic. To run your code, just execute `TestScript` as a main class in IDEA. To distribute your code as a reusable docile-charge-point script, just copy the part you added out of the surrounding boilerplate and put it in a file of its own.

### Scripts with expectations

The semi-final line is interesting: `fail("Not authorized")`.

This shows that docile-charge-point scripts don't just run. They run, and in the
end docile-charge-point will consider them either _failed_ or _passed_. Also, if
a script inadvertently fails to run at all, docile-charge-point will consider
the outcome an _error_.

If I for instance run both the example heartbeat script and the example
do-a-transaction script, against a back-office that does not authorize the
transaction, I will see that one script failed and the other one passed. In the
console, that looks like this:

```
java -jar cmd/target/scala-2.12/docile.jar -c '03000001' ws://example.com/ocpp examples/heartbeat.scala examples/do-a-transaction.scala
Loading settings from plugins.sbt ...
Loading project definition from /Users/reinier/Documents/Programs/docile-charge-point/project
Loading settings from build.sbt ...
Set current project to docile-charge-point (in build file:/Users/reinier/Documents/Programs/docile-charge-point/)
Credentials file /Users/reinier/.ivy2/.credentials does not exist
Packaging /Users/reinier/Documents/Programs/docile-charge-point/target/scala-2.11/docile-charge-point_2.11-0.1-SNAPSHOT.jar ...
Done packaging.
Running (fork) chargepoint.docile.Main -c 03000001 ws://example.com/ocpp examples/heartbeat.scala examples/do-a-transaction.scala
Going to run heartbeat
>> HeartbeatReq
<< HeartbeatRes(2018-04-02T20:38:13.342Z[UTC])
Going to run do-a-transaction
>> AuthorizeReq(01234567)
<< AuthorizeRes(IdTagInfo(IdTagInvalid,None,Some(01234567)))
heartbeat: ✅
do-a-transaction: ❌  Not authorized
```

So docile-charge-point will show that the heartbeat script passed, and the
do-a-transaction script failed with the message "Not authorized".

Also, the command will return success (exit status 0) if all scripts passed, and
failure (exit status 1) otherwise.

It is now also time to come back to the statement about `statusNotification()`,
saying that this simple call did two things. In fact, this function will send
the message, and then wait for the response, and make the script fail if the
first incoming message is not a StatusNotification response. This is usually
useful in order to get a response object to work with, but sometimes you'd
want your script to be more flexible about how the Central System can respond.

For those cases you have the `send` and `expectIncoming` functions in the DSL.
`send` sends a message to the Central System, and immediately returns without
waiting for a response. `expectIncoming` in turn looks if a message has been
received from the Central System, and if not, will block until one arrives.

The `statusNotification()` call turns out to be equivalent to:

```
send(StatusNotificationReq(
  scope = ConnectorScope(0),
  status = ChargePointStatus.Available(),
  timestamp = Some(ZonedDateTime.now()),
  vendorId = None
))
expectIncoming(matching { case res@StatusNotificationRes => res })
```

So this `expectIncoming(matching ...)` line is in the end also an expression that
returns the response that was just received.

What `expectIncoming` does comes down to:

 * Get the first incoming message that has not been expected before by the script, waiting for it if there is no such incoming message yet

 * See if this message matches the partial function that's given after
   `matching`

 * If so, return the result of the partial function. If not, fail the script.


In order to feed `expectIncoming`, docile-charge-point keeps a queue of messages
that have been received. The `expectIncoming` call is always evaluated against
the head of the queue. So you _have_ to expect every message the Central System
sends to you, in the order in which they arrive!

To make this order requirement easier to deal with, you can also expect
multiple messages at once, and docile-charge-point will accept them no matter
in which order they arrive:

```
expectInAnyOrder(
  remoteStartTransactionReq.respondingWith(RemoteStartTransactionRes(true)),
  changeConfigurationReq.respondingWith(ChangeConfigurationRes(ConfigurationStatus.Accepted))
)
```

As a variant of the `expectIncoming(matching ...)` idiom, there is also an
`expectIncoming requestMatching ...` variant, that lets you expect incoming
requests from the Central System, and respond to them, like so:

```
      expectIncoming(
        requestMatching({case r: RemoteStopTransactionReq => r.transactionId == transId})
          .respondingWith(RemoteStopTransactionRes(_))
      )
```

This bit waits for an incoming StopTransaction request, and fails if the next
incoming message is not a StopTransaction request. If it is, it returns whether
the transaction ID in that message matches the `transId` value. Also, it
responds to the Central System with a RemoteStopTransaction response.

The argument to `respondingWith` can either be a literal value, or it can be a
function from the result of the partial function to a response. Here the latter
option is used in order to tell the Central System whether the remote stop
request is accepted, based on whether the remote stop request's transaction ID
matched the one that the script had started.

See the remote start/stop example [for OCPP 1.x](examples/ocpp1x/remote-transaction.scala) or [for OCPP 2.0](examples/ocpp20/remote-transaction.scala) for the
full script using all these features.

As you can see in the handling of the remote start request there, there is also
a shorthand for expecting an incoming request of a certain type, without caring
more about the specific message contents. So this bit:

```
expectIncoming(remoteStartTransactionReq.respondingWith(RemoteStartTransactionRes(true)))
```

is equivalent to:

```
expectIncoming(
  requestMatching({case r: StartTransactionReq => r})
    .respondingWith(RemoteStartTransactionRes(true))
)
```

### Setting up default handlers of certain messages

Sometimes Central Systems will send a charge point messages that it just doesn't
see coming. Two back-offices of major Dutch charge point vendors tend to probe
charge points periodically for their configuration settings using
`GetConfigurationReq` messages. In order to keep such unanticipated incoming
messages from getting in your incoming message queue and making your tests fail,
you can catch them with `handlingIncomingMessages`. An example of its use is given
[here](examples/ocpp1x/handling-incoming-messages.scala).


## Running on the command line with an interactive prompt

You can also go into an interactive testing session on the command line. To do that, pass the `-i` command line flag:

```
java -jar cmd/target/scala-2.12/docile.jar -i -v 1.6 -c chargepoint0123 ws://example.com/ocpp
```

The `-i` option here tells `docile-charge-point` to go into interactive mode.

The app will start and something write this to the console:

```
[info, chargepoint.docile.test.InteractiveRunner] Going to run Interactive test
Compiling (synthetic)/ammonite/predef/interpBridge.sc
Compiling (synthetic)/ammonite/predef/replBridge.sc
Compiling (synthetic)/ammonite/predef/DefaultPredef.sc
Compiling (synthetic)/ammonite/predef/ArgsPredef.sc
Compiling (synthetic)/ammonite/predef/CodePredef.sc
Welcome to the Ammonite Repl 1.0.3
(Scala 2.11.11 Java 1.8.0_144)
If you like Ammonite, please support our development at www.patreon.com/lihaoyi
@
```

The `@` sign on that last line is your prompt. You can now type expressions in the `docile-charge-point` DSL, like:

```
statusNotification()
```

and you'll see the docile-charge-point and the back-office exchange messages:

```
[info, chargepoint.docile.test.InteractiveOcppTest$$anon$1] >> StatusNotificationReq(ConnectorScope(0),Occupied(Some(Charging),None),Some(2018-01-01T15:12:43.251+01:00[Europe/Paris]),None)
[info, chargepoint.docile.test.InteractiveOcppTest$$anon$1] << StatusNotificationRes
```

let's see what happens if we send a timestamp from before the epoch...

```
statusNotification(timestamp = Some(ZonedDateTime.of(1959, 1, 1, 12, 0, 0, 0, ZoneId.of("Z"))))
```

turns out it works surprisingly well :-):

```
[info, chargepoint.docile.test.InteractiveOcppTest$$anon$1] >> StatusNotificationReq(ConnectorScope(0),Available(None),Some(1959-01-01T12:00Z),None)
[info, chargepoint.docile.test.InteractiveOcppTest$$anon$1] << StatusNotificationRes
```

You'll also see that the interactive mode prints something like this:

```
res0: StatusNotificationRes.type = StatusNotificationRes
```

That's the return value of the expression you entered, which in this case, is the StatusNotification response object. And because you're in a full-fledged Scala REPL using [Ammonite](ammonite.io), nothing is stopping you from doing fancy stuff with that. So you can for instance values from responses in subsequent requests:

```
@ startTransaction(idTag = "ABCDEF01")
[info, chargepoint.docile.test.InteractiveOcppTest$$anon$1] >> StartTransactionReq(ConnectorScope(0),ABCDEF01,2018-01-01T15:22:30.122+01:00[Europe/Paris],0,None)
[info, chargepoint.docile.test.InteractiveOcppTest$$anon$1] << StartTransactionRes(177,IdTagInfo(Accepted,None,Some(ABCDEF01)))
res3: StartTransactionRes = StartTransactionRes(177, IdTagInfo(Accepted, None, Some("ABCDEF01")))

@ stopTransaction(transactionId = res3.tr
transactionId
@ stopTransaction(transactionId = res3.transactionId)
[info, chargepoint.docile.test.InteractiveOcppTest$$anon$1] >> StopTransactionReq(177,Some(ABCDEF01),2018-01-01T15:22:50.457+01:00[Europe/Paris],16000,Local,List())
[info, chargepoint.docile.test.InteractiveOcppTest$$anon$1] << StopTransactionRes(Some(IdTagInfo(Accepted,None,Some(ABCDEF01))))
res4: StopTransactionRes = StopTransactionRes(Some(IdTagInfo(Accepted, None, Some("ABCDEF01"))))
```

Note also that between those two requests, I used tab completion to look up the name of the `transactionId` field in the StopTransaction request.

## Running in a Docker container

There is now a Dockerfile included, so you can run it in Docker if you want. Also you can use the Docker image as a basis for your own images that encode certain charge point behaviors.

To run it in Docker, do:

```
$ sbt assembly

$ docker build -t docile-charge-point:latest .

$ docker run --rm -it docile-charge-point:latest
```

The Docker container will execute docile-charge-point, executing a script that
waits for OCPP remote start and remote stop requests and reports charge
transactions accordingly.

See [the Dockerfile](Dockerfile) for available environment variables to control the image.

## Running inside another app, embedded as a library

For maximum flexibility, you can embed docile-charge-point as a library dependency in your own Scala code. In that case,
 you can use the docile-charge-point DSL while also calling other libraries and code of your own.

 To make docile-charge-point a dependency of your Scala project, add this to your library dependencies in your `build.sbt`:

```scala
"com.newmotion" %% "docile-charge-point" % "0.5.1"
```

and make sure that the NewMotion Nexus repository is in your sources by adding this to your `project/plugins.sbt`:

```scala
resolvers += "TNM" at "http://nexus.thenewmotion.com/content/groups/public"
```

Then, in your code:
  1. Create tests as instances of `chargepoint.docile.dsl.OcppTest` in your code
  1. Combine them with a testcase name to be a [`chargepoint.docile.test.TestCase`](core/src/main/scala/chargepoint/docile/test/TestCase.scala)
  1. Instantiate a [`chargepoint.docile.test.Runner`](core/src/main/scala/chargepoint/docile/test/Runner.scala) wrapping the test cases
  1. Call the `.run()` method on the `Runner`, passing a [`chargepoint.docile.test.RunnerConfig`](core/src/main/scala/chargepoint/docile/test/Runner.scala) to specify how you'd like the test to be executed

### Loading test cases distributed separately as files

To load text files as test cases, you need another library as a dependency:

```scala
"com.newmotion" %% "docile-charge-point-loader" % "0.5.1"
```

and of course also in this case the resolver in your `project/plugins.sbt`:

```scala
resolvers += "TNM" at "http://nexus.thenewmotion.com/content/groups/public"
```

Then you'll, besides all the classes for defining and running test cases
mentioned above, also have a [`chargepoint.docile.test.Loader`](loader/src/main/scala/chargepoint/docile/test/Loader.scala)
that has a few methods all called `runnerFor` that will give you a `Runner`
instance based on a file, `String` or `Array[Byte]` for a test case.

One example where this is done is the AWS Lambda and S3 integration in the [lambda](aws-lambda/) subproject in this repository. Run `sbt lambda/run` to compile and run that code.

At the moment, unfortunately, the only documentation is this and the [source code](aws-lambda/src/main/scala/chargepoint/docile/Lambda.scala).

Also, at the moment the library is only published for Scala 2.11 and 2.12. The
reason is that docile-charge-point depends on Ammonite and Ammonite is a very
finicky thing when it comes to dependency versioning. We are aware and if we
keep working on docile-charge-point we will split the library and the
interactive executable, so that we can also build the library for Scala 2.13
and later versions.

## TODOs

It's far from finished now. The next steps I plan to develop:

 * Make it able to take both Charging Station Management System and Charging Station roles

 * Nicer syntax for constructing OCPP messages to send or expect

 * For OCPP 2.0, build some autonomous, stateful management of
   components, variables, transaction management. This stuff is too
   complicated to manage in interactive mode otherwise. This autonomous
   default behavior should be overrideable of course.

 * Show incoming messages that caused errors parsing or processing
   (this might entail changes to the NewMotion OCPP library)

 * Add a command line flag to drop to interactive mode, instead of exit,
   when an assertion fails in a script

 * Add a command in interactive mode to run a script from a file or URL

 * Messages of OCPP 2.0 that seem to be in demand:
   * ChangeAvailability
   * Reset

## Other ideas

 * Web interface: click together test: 150 CPs behaving like this, 300 like that, ..., GO!

 * Live demo on the web?

## Legal

The contents of this repository are © 2017-2019 The New Motion B.V. and other
contributors, licensed under the terms of the [GNU General Public License version 3](LICENSE).
