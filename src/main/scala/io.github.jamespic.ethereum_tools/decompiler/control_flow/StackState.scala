package io.github.jamespic.ethereum_tools.decompiler.control_flow

import io.github.jamespic.ethereum_tools._
import Bytecode._
/**
 * A relatively simple data flow model for control flow analysis - we need to
 * distinguish jump dests that are either constant, passed on stack, or calculated.
 */
case class StackState(vars: List[Expr] = Nil, thenIndex: Int = 0) {
  def pop: (Expr, StackState) = vars match {
    case head :: tail => (head, copy(vars = tail))
    case Nil => (StackVar(thenIndex), copy(thenIndex = thenIndex + 1))
  }

  def pop(n: Int): (List[Expr], StackState) = {
    var (vars, newStack) = (0 until n).foldLeft((List.empty[Expr], this)){
      case ((vars, stack), i) =>
        val (e, newStack) = stack.pop
        (e :: vars, newStack)
    }
    (vars.reverse, newStack)
  }

  def push(exprs: Expr*): StackState = {
    (exprs :\ this)((exp, s) => s.copy(vars = exp :: s.vars))
  }

  def apply(n: Int) = {
    def find(n: Int, vars: List[Expr]): Expr = (n, vars) match {
      case (0, head :: tail) => head
      case (n, head :: tail) => find(n - 1, tail)
      case (n, Nil) => StackVar(n + thenIndex)
    }
    find(n, vars)
  }

  def progress(op: Bytecode): StackState = op match {
    case PUSH(_, n) => push(ConstExpr(n))
    case DUP(n) =>
      val (vars, newStack) = pop(n)
      newStack.push(vars.last +: vars: _*)
    case SWAP(n) =>
      val (vars :+ last, newStack) = pop(n + 1)
      newStack.push(last +: vars: _*)
    case _ =>
      val (consumed, newStack) = pop(op.inputs)
      newStack.push(Seq.fill(op.outputs)(CalculatedExpr): _*)
  }

  def progress(instructions: InstList): StackState = (this /: instructions)(_ progress _._2)

  def ensureDepth(n: Int) = {
    val (vars, newStack) = pop(n)
    newStack.push(vars: _*)
  }

  lazy val height = vars.length - thenIndex
}

sealed trait Expr
case class StackVar(index: Int) extends Expr
case class ConstExpr(const: BigInt) extends Expr
case object CalculatedExpr extends Expr
