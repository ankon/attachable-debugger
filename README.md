Attachable Debugger
===================

This is a simple experiment in byte-code manipulation. The idea is simple: rather than running
with JDWP debugging enabled all the time a Java agent is used to modify classes at runtime.

The main goal for now is to be able to add a 'dump point': whenever the program flow reaches that
place the contents of local variables, method arguments, and member variables are dumped to the client.

At a later stage even a step-by-step execution mode could be doable.
 
