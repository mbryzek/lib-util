package com.bryzek.util.log

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.{CompletionException, ExecutionException}

/** The text a failure is logged under: the thing that went wrong, never only the box it arrived in.
  *
  * A Scala `Future` completes with `ExecutionException("Boxed Exception", cause)` whenever what failed it is an `Error`
  * (a `NotImplementedError` from a `???`, an `AssertionError`), an `InterruptedException` or a `ControlThrowable`. Its
  * message is the same two words whatever the cause, so a line rendered from `getMessage` reads "Boxed Exception" and
  * names nothing but the stack trace field does. ISS-127225
  *
  * [[of]] looks through every such box to the cause. A wrapper with a cause, and an `Error` (whose message, when it has
  * one, reads as a sentence about nothing in particular: "an implementation is missing"), are named by class AND
  * message. Every other exception keeps its own message alone, so an ordinary failure's line reads exactly as before.
  *
  * play-core's `core.util.FailureMessage` is the same rule for task errors; lib-util cannot depend on play-core, so it
  * holds its own copy.
  */
object FailureMessage {

  /** How deep a chain of boxes is followed: past any real nesting, short of a cycle. */
  private val MaxDepth: Int = 16

  def of(e: Throwable): String = {
    val cause = unboxed(e)
    if ((cause ne e) || cause.isInstanceOf[Error]) named(cause)
    else Option(e.getMessage).getOrElse(e.getClass.getName)
  }

  /** The first throwable in `e`'s chain that is not a box around another. */
  def unboxed(e: Throwable): Throwable =
    Iterator
      .iterate(e)(_.getCause)
      .take(MaxDepth)
      .takeWhile(_ != null)
      .find(t => !isBox(t))
      .getOrElse(e)

  private def isBox(t: Throwable): Boolean =
    t.getCause != null && (t match {
      case _: ExecutionException | _: CompletionException | _: InvocationTargetException => true
      case _ => false
    })

  private def named(t: Throwable): String =
    Option(t.getMessage).filter(_.nonEmpty).fold(t.getClass.getName)(m => s"${t.getClass.getName}: $m")
}
