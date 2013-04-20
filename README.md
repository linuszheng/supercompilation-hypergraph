# Hypergraph-based Supercompilation

Hypergraph-based supercompilation is a kind of multi-result 
supercompilation based on hypergraph transformation. It is described
in the preprint ["Supercompilation by hypergraph transformation"](http://library.keldysh.ru/preprint.asp?id=2013-26&lg=e).
This project is a set of components for building such supercompilers,
currently limited to a lazy first-order functional language.
It also contains an experimental supercompiler that can prove equivalence of
functions.

## Experimental equivalence prover

Hypergraph-based supercompilation is good at proving equivalences.
To demonstrate this the project contains an equivalence prover.
It is written in Scala, you can build it using Simple Build Tool 
(see [here](http://typesafe.com/resources/typesafe-stack/downloading-installing.html) 
how to install it).
First build the project and create a jar file using sbt-onejar plugin:

    sbt one-jar

This command will create a jar with a name like this: `./target/scala-2.9.2/graphsc_2.9.2-0.1-SNAPSHOT-one-jar.jar`.
You can use it to launch the equivalence prover:

    java -jar ./target/scala-2.9.2/graphsc_2.9.2-0.1-SNAPSHOT-one-jar.jar

Here will name it just `eqprover`.

    alias eqprover="java -jar ./target/scala-2.9.2/graphsc_2.9.2-0.1-SNAPSHOT-one-jar.jar"

The equivalence prover should be provided with a file containing definitions and (optionally)
a task in the form `foo=bar`:

    eqprover -t constz=idle samples/idle

If the first line of the file contains a comment with a task (in the same format) then you can pass 
`auto` instead:

    eqprover -tauto samples/idle

You can use the flag `-v` to make the prover more verbose (for example, 
it will then print what expressions it is merging by graph isomorphism).
Note also that some examples may need adjustment of parameters:

    eqprover -tauto -a4 -v  samples/add-assoc

(`-a4` increases the arity limit to 4)

## Creating an eclipse project

If you want to create an eclipse project, use the following command:

    sbt eclipse


